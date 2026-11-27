package cn.dkarbon.enroute.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.net.ssl.HttpsURLConnection

/**
 * URP 教务系统客户端：登录 + 抓课程表。
 *
 * 登录是 CAS 三段式（详见 syau-cas-reverse 的 docs/02）：
 *   ① GET  /tpass/login?service=...  取登录页里的 lt（一次性令牌）
 *   ② POST /tpass/rsa                取 RSA 公钥
 *   ③ POST /tpass/login              提交 ul=RSA(账号) pl=RSA(密码) lt execution _eventId
 *
 * 只在**校园网**内可用（目标 10.2.0.47 是内网地址）。
 *
 * ⚠️ 凭据只在内存里用，不落盘。这点是刻意的 —— 密码明文存盘风险太大，宁可每次登录都手输。
 * 但**会话 cookie** 是另一回事：它是教务系统发给本机的、会自然过期的登录凭证，
 * 冷启动恢复它就能免掉「每天重登一次」（票 04）。所以 cookie 走加密存档
 * （[SessionStore]），密码永不落盘。
 */
object UrpClient {

    /**
     * 学校统一认证（CAS）地址 —— **凭据唯一允许发往的主机**（票 14 安全要求 §1）。
     *
     * 写成**一个字面量常量**，任何需要提交凭据的地方都只能引用它，
     * 不许把用户输入、配置、接口返回拼进这里（单测断言它的 host 就是 pass.syau.edu.cn）。
     */
    const val CAS_LOGIN_URL = "https://pass.syau.edu.cn"

    /** 校验用：凭据提交目标的 host，必须恒等于 [CAS_LOGIN_URL] 的 host */
    internal const val CAS_LOGIN_HOST = "pass.syau.edu.cn"

    /**
     * 拼出「带 service 回跳参数的 CAS 登录地址」。
     *
     * 只有 **service（回跳地址）** 参与拼接，主机部分永远是常量 ——
     * 这样即便将来有人把 service 换成可变值，凭据也只会发到学校认证页。
     */
    internal fun casLoginUrl(service: String): String =
        CAS_LOGIN_URL + "/tpass/login?service=" + java.net.URLEncoder.encode(service, "UTF-8")
    private const val URP = "http://10.2.0.47"

    /**
     * 验活用的只读接口：空闲教室的校区列表。
     *
     * 挑它的理由（对比另外几个候选）：
     *   · 只读、GET、无参数、无 token —— 不需要「先取页面抠 token」那种两步流；
     *   · 响应很小（几百字节 JSON 数组），不拉整页 HTML（学籍页 100KB+）；
     *   · 会话失效时 CAS 会 302 回登录页，信号明确。
     * 实测形态见 `docs/URP-学生端接口清单.md`（与 UrpHomeApi.CAMPUS_LIST 同一支）。
     */
    private const val CAMPUS_LIST = "/student/teachingResources/freeClassroom/queryCodeCampusList"

    /** `extraHeaders` 里用这个值表示「这次不发 Referer」（地址栏直达 / 302 送来的顶层导航） */
    private const val NO_REFERER = "<none>"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class LoginException(message: String) : Exception(message)

    /** 一次会话：持有 cookie，后续请求复用 */
    class Session internal constructor() {
        internal val cookies = mutableMapOf<String, String>()

        /**
         * 登录用户姓名（从 `/index` 页面的「欢迎您，XXX」里抠出来的）。
         *
         * 抠不到就是空串 —— 各校/各版本这个位置的 DOM 结构不一样，
         * 所以**只用它做展示**，取不到就显示「已登录」，不让它影响任何逻辑。
         */
        var displayName: String = ""
            internal set

        internal fun cookieHeader(): String =
            cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        /**
         * cookie 快照（**给加密存档用**）。
         *
         * 只有 [SessionStore] 该调它；返回副本，调用方改不动会话。
         * 注意：这是真凭证 —— 只许进加密存档，绝不许进日志/导出/分享。
         */
        internal fun cookiesSnapshot(): Map<String, String> = cookies.toMap()

        internal fun absorb(conn: java.net.URLConnection) {
            conn.headerFields?.entries
                ?.filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                ?.forEach { (_, values) ->
                    values?.forEach { raw ->
                        val pair = raw.substringBefore(';')
                        val i = pair.indexOf('=')
                        if (i > 0) {
                            cookies[pair.substring(0, i).trim()] = pair.substring(i + 1).trim()
                        }
                    }
                }
        }

        companion object {
            /**
             * 从**加密存档**里还原一个会话（冷启动用，票 04）。
             *
             * 走这里而不是让调用方 new：cookie 字典是 internal 的，
             * 还原入口收在一处，将来换存储格式只需改这里。
             */
            internal fun fromStored(cookies: Map<String, String>, displayName: String = ""): Session =
                Session().also {
                    it.cookies.putAll(cookies)
                    it.displayName = displayName
                }
        }
    }

    /**
     * 登录。
     * @return 会话对象，用于后续拉课表
     */
    suspend fun login(username: String, password: String): Session = withContext(Dispatchers.IO) {
        val session = Session()

        // ── ① 取登录页，抠 lt ────────────────────────────
        val loginUrl = casLoginUrl("$URP/login")
        val page = httpGet(
            session, loginUrl, secure = true, nav = true,
            extraHeaders = mapOf("Referer" to NO_REFERER),
        )
        val lt = Regex("""name="lt"\s+value="([^"]+)"""")
            .find(page)?.groupValues?.get(1)
            ?: Regex("""value="(LT-[^"]+)"""")
                .find(page)?.groupValues?.get(1)
            ?: throw LoginException("登录页里没找到 lt 令牌（可能页面改版）")
        DebugLog.step("URP", "拿到 lt 令牌（${lt.length} 字符）")

        val execution = Regex("""name="execution"\s+value="([^"]+)"""")
            .find(page)?.groupValues?.get(1) ?: "e1s1"

        // ── ② 取 RSA 公钥 ────────────────────────────────
        val keyBody = httpPost(
            session,
            CAS_LOGIN_URL + "/tpass/rsa",
            "",
            secure = true,
            extraHeaders = mapOf("Referer" to loginUrl),
        )
        val publicKey = Regex(""""publicKey"\s*:\s*"([^"]+)"""")
            .find(keyBody)?.groupValues?.get(1)
            ?: throw LoginException("没拿到 RSA 公钥")
        DebugLog.step("URP", "拿到 RSA 公钥（${publicKey.length} 字符）")

        // ── ③ 加密并提交 ─────────────────────────────────
        val encryptedUser = rsaEncrypt(publicKey, username)
        val encryptedPass = rsaEncrypt(publicKey, password)

        val form = buildString {
            append("ul=").append(enc(encryptedUser))
            append("&pl=").append(enc(encryptedPass))
            append("&lt=").append(enc(lt))
            append("&execution=").append(enc(execution))
            append("&_eventId=submit")
        }

        // 只记长度，不记内容：表单里有 RSA 密文账号密码 + lt 票据
        DebugLog.log("URP", "→ POST SSO 提交登录（表单 ${form.length} 字节，内容已隐去）")

        val resp = httpPost(
            session, casLoginUrl("$URP/login"),
            form, secure = true, nav = true,
            extraHeaders = mapOf("Referer" to NO_REFERER),
        )
        DebugLog.step(
            "URP",
            "SSO 返回 ${resp.length} 字节；含 errormsghide=${resp.contains("errormsghide")} 又回登录页=${resp.contains("loginForm")}",
        )

        // 关键：CAS 成功后是 302 → service?ticket=ST-xxx
        // 必须跟进去，URP 才会用 ticket 换到自己的 JSESSIONID。
        // httpPost 内部已经跟随 302，但 POST 的响应体为空，
        // 所以这里再主动访问一次 URP 首页确保会话建立。
        runCatching { httpGet(session, "$URP/index", nav = true) }.getOrNull()?.let { html ->
            session.displayName = parseDisplayName(html)
            DebugLog.step(
                "URP",
                "已回跳 URP/index（${html.length} 字节），cookie 数=${session.cookies.size}，" +
                    // ⚠️ 只记「识别到了没有」，姓名本身不进日志（红线：日志无姓名/学号）
                    "姓名=${if (session.displayName.isBlank()) "未识别" else "已识别"}",
            )
        }

        // 失败也是 200，页面里带错误信息
        if (resp.contains("errormsghide")) {
            val msg = Regex("""id="errormsghide"[^>]*>([^<]*)<""")
                .find(resp)?.groupValues?.get(1)?.trim()
            if (!msg.isNullOrBlank()) throw LoginException(msg)
        }
        if (resp.contains("loginForm") && resp.contains("name=\"lt\"")) {
            throw LoginException("账号或密码不对（登录页又回来了）")
        }

        session
    }

    /**
     * **验活**：这份会话现在还能不能用（票 04）。
     *
     * 冷启动从加密存档恢复 cookie 后**必须先问一句**，否则界面会拿着过期 cookie
     * 显示「已登录」，然后每个页面各自报一堆「没抓到数据」—— 学生根本看不懂发生了什么。
     *
     * 判定只认响应里**看得见**的东西（见 [judgeSession]），连不上就是连不上，
     * 不当成过期也不当成有效。
     */
    suspend fun verifySession(session: Session): SessionProbe = withContext(Dispatchers.IO) {
        val resp = runCatching {
            httpGetResult(session, "$URP$CAMPUS_LIST")
        }.getOrElse {
            // 超时/拒绝连接/不在校园网：会话可能还活着，我们只是问不到 —— 不能判过期
            DebugLog.step("SESSION", "验活未完成：${it.javaClass.simpleName}（多半不在校园网）")
            return@withContext SessionProbe.Unreachable
        }
        val probe = judgeResponse(resp)
        DebugLog.step(
            "SESSION",
            "验活：HTTP ${resp.code}/${resp.failure} ${resp.body.length} 字节 → $probe" +
                // 只看开头几个字符判断「是不是数据页」，不记内容（里面是校区名）
                "（首字符=${resp.body.trimStart().take(1).ifBlank { "空" }}）",
        )
        probe
    }

    /**
     * 把一次验活响应判成「活 / 过期 / 问不到」。
     *
     * 分流顺序是这次改造的重点（2026-11-27）：**先看状态码，再看正文**。
     * 5xx 维护页、4xx、跟不动的跳转一律是 [SessionProbe.Unreachable] ——
     * 服务器自己出了问题，不该让学生的会话跟着作废重登。
     */
    internal fun judgeResponse(resp: UrpResponse): SessionProbe = when (resp.failure) {
        UrpFailure.None -> judgeSession(resp.body)
        UrpFailure.Expired -> SessionProbe.Expired
        else -> SessionProbe.Unreachable
    }

    /**
     * 验活判定的**纯函数**部分（单测直接打）。
     *
     * - 拿到 JSON（`[` 或 `{`）→ [SessionProbe.Ok]：只读接口肯给数据，会话就是活的；
     * - 2xx 但不是 JSON（CAS 登录页那类 HTML）→ [SessionProbe.Expired]。
     *
     * ⚠️ 它只负责 **2xx 的正文**这一种判断。非 2xx 由 [judgeResponse] 按状态码分流：
     * 5xx 维护页曾经也走到这里被当成过期（学生被无故踢下线），现在归 [SessionProbe.Unreachable]。
     * 网络异常同样由 [verifySession] 映射成 [SessionProbe.Unreachable]。
     */
    internal fun judgeSession(body: String): SessionProbe {
        val t = body.trimStart()
        if (t.startsWith("[") || t.startsWith("{")) return SessionProbe.Ok
        return SessionProbe.Expired
    }

    /**
     * 拉本学期课表。
     * 返回原始 xkxx JSON（结构见 syau-cas-reverse docs/06 第二节）。
     */
    suspend fun fetchSchedule(session: Session): String = withContext(Dispatchers.IO) {        // 先访问门户建立应用会话
        runCatching { httpGet(session, "$URP/index") }

        val url = "$URP/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback"
        DebugLog.step("URP", "开始拉课表")
        httpGet(session, url)
    }

    /**
     * 历年课表：**按学期**取课表（票 11，2026-11-14 实测）。
     *
     * 关键点（`docs/URP-学生端接口清单.md` §6.4）：
     * - 是 **POST**，表单只有 `planCode`，且**开头要有一个 `&`**（首字段为空）——
     *   `GET ...?xnxq=` 与 `GET index?planCode=` 实测都无效；
     * - 返回 schema 与「本学期课表」**完全同构**，所以调用方直接复用 [parseSchedule]，
     *   不要再写第二个解析器。
     */
    suspend fun fetchScheduleForTerm(session: Session, planCode: String): String = withContext(Dispatchers.IO) {
        // 先回一次门户，确保会话处于「已进入选课管理」的状态（与 fetchSchedule 同款预热）
        runCatching { httpGet(session, "$URP/index") }
        val url = "$URP/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback"
        DebugLog.step("URP", "按学期拉课表：planCode=${planCode.trim()}")
        httpPost(
            session, url,
            body = HistorySemesters.scheduleBody(planCode),
            extraHeaders = mapOf(
                "Referer" to "$URP/student/courseSelect/calendarSemesterCurriculum/index",
            ),
        )
    }

    /**
     * 历年学期课表页面（只为读 `select[name="planCode"]` 里的学期候选）。
     *
     * 学期列表**不写死**：服务器按会话只给最近 5 个（含当前），所以必须从页面取。
     */
    suspend fun fetchSemesterCalendarPage(session: Session): String = withContext(Dispatchers.IO) {
        httpGet(
            session,
            "$URP/student/courseSelect/calendarSemesterCurriculum/index",
            nav = true,
            extraHeaders = mapOf("Referer" to "$URP/student/courseSelect/index"),
        )
    }

    /**
     * 把 URP 的 xkxx 响应转成 App 的 [Timetable]。
     *
     * ## ⚠️ 2026-11-11 修的致命 bug
     * 上一版**只读了课程名和老师**，完全没碰 `timeAndPlaceList` ——
     * 于是 `weekday / sections / weeks / room` 全是空值，7 门课全被判成「未排」，
     * 界面上「7 门课 0 节」。这不是学校数据的问题，是解析器漏了最关键的那一段。
     *
     * ## URP 的真实结构（2026-11-11 实测抓包）
     * ```json
     * "xkxx": [ { "902021102_01": {
     *     "courseName": "社会主义发展史",
     *     "attendClassTeacher": "李洋* ",
     *     "timeAndPlaceList": [            // ← 一定是**数组**，一门课可能有多段
     *        { "classDay": 6,              //   星期几，1=周一
     *          "classSessions": 9,         //   起始小节
     *          "continuingSession": 2,     //   连上几节
     *          "classWeek": "000000000000000000001000",  // 24 位位图，第 i 位=第 i+1 周
     *          "teachingBuildingName": "实践上课地点",
     *          "classroomName": "具体地点教师通知1",
     *          "weekDescription": "21周上" } ] } } ]
     * ```
     * 一门课有**多个** `timeAndPlaceList` 元素时（比如周二、周四各一次），
     * 要拆成多条 [Course] —— 课表按「星期 × 节次」定位，本来就不是一门课一行。
     */
    fun parseSchedule(raw: String): Timetable {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: throw LoginException("课表返回的不是 JSON（可能登录态失效）")

        val obj = root as? kotlinx.serialization.json.JsonObject
            ?: throw LoginException("课表响应结构异常")

        val xkxx = obj["xkxx"] as? kotlinx.serialization.json.JsonArray
            ?: throw LoginException("响应里没有 xkxx 字段")

        val courses = mutableListOf<Course>()
        var term = ""

        fun prim(o: kotlinx.serialization.json.JsonObject?, k: String): String {
            val p = o?.get(k) as? kotlinx.serialization.json.JsonPrimitive ?: return ""
            return if (p is kotlinx.serialization.json.JsonNull) "" else p.content
        }

        fun primInt(o: kotlinx.serialization.json.JsonObject?, k: String): Int =
            prim(o, k).trim().toIntOrNull() ?: 0

        for (item in xkxx) {
            val wrapper = item as? kotlinx.serialization.json.JsonObject ?: continue
            for ((key, v) in wrapper) {
                val c = v as? kotlinx.serialization.json.JsonObject ?: continue
                val id = c["id"] as? kotlinx.serialization.json.JsonObject

                val courseNo = prim(id, "coureNumber").ifBlank { key.substringBefore('_') }
                val seqNo = prim(id, "coureSequenceNumber").ifBlank { key.substringAfter('_', "") }
                val plan = prim(id, "executiveEducationPlanNumber")
                if (term.isBlank() && plan.isNotBlank()) {
                    // "2026-2027-1-1" → "2026-2027-1"
                    term = plan.split('-').take(3).joinToString("-")
                }

                val name = prim(c, "courseName").ifBlank { key }
                val teacher = prim(c, "attendClassTeacher").replace("*", " ").trim()
                val category = prim(c, "courseCategoryName")
                val examType = prim(c, "examTypeName")

                val slots = c["timeAndPlaceList"] as? kotlinx.serialization.json.JsonArray
                    ?: emptyList()

                if (slots.isEmpty()) {
                    // 真的没排时间（URP 里这门的 timeAndPlaceList 是 []），
                    // 保留成「未排」条目，不要丢掉 —— 学生要知道自己选了这门课
                    courses += Course(
                        courseNo = courseNo,
                        seqNo = seqNo,
                        name = name,
                        teacher = teacher,
                        category = category,
                        examType = examType,
                    )
                    continue
                }

                for (s in slots) {
                    val slot = s as? kotlinx.serialization.json.JsonObject ?: continue
                    val day = primInt(slot, "classDay")
                    val start = primInt(slot, "classSessions")
                    val span = primInt(slot, "continuingSession").coerceAtLeast(1)
                    val weeks = decodeWeekBitmap(prim(slot, "classWeek"))

                    val building = prim(slot, "teachingBuildingName")
                    val room = prim(slot, "classroomName")
                    val place = listOf(building, room)
                        .filter { it.isNotBlank() }
                        .joinToString(" / ")

                    courses += Course(
                        courseNo = courseNo,
                        seqNo = seqNo,
                        name = name,
                        teacher = teacher,
                        category = category,
                        examType = examType,
                        weeks = weeks,
                        weekday = day,
                        sections = (start until start + span).toList(),
                        room = place,
                        credit = prim(c, "unit").toDoubleOrNull(),
                    )
                }
            }
        }
        return Timetable(term = term, courses = courses)
    }

    /**
     * 解 URP 的周次位图：`"111010000000000000000000"` → `[1,2,3,5]`。
     *
     * 第 i 个字符对应第 i+1 周，`'1'` 表示那周有课。
     * 长度可能是 24/25/26（不同学校学期周数不同），所以按实际长度遍历，不写死。
     */
    internal fun decodeWeekBitmap(bitmap: String): List<Int> {
        if (bitmap.isBlank()) return emptyList()
        return bitmap.mapIndexedNotNull { i, ch -> if (ch == '1') i + 1 else null }
    }

    /**
     * 从门户首页 HTML 里抠姓名。
     *
     * 容错优先：**多套正则挨个试，全失败返回空串**。因为不同学校、不同版本的
     * 这块 DOM 完全不一样，而姓名只用于展示 —— 绝不该因为它取不到就让登录失败。
     */
    private fun parseDisplayName(html: String): String {
        // ① 「欢迎您，XXX」（中间可能夹标签/空白）
        Regex("""欢迎您[，,、\s]*(?:<[^>]*>\s*)*([\u4e00-\u9fa5]{2,8})""")
            .find(html)?.groupValues?.get(1)?.let { return it }

        // ② 常见的姓名容器 id
        for (id in listOf("yhxm", "userName", "xsxm", "user_name", "realName")) {
            Regex("""id=["']$id["'][^>]*>([\u4e00-\u9fa5]{2,8})<""")
                .find(html)?.groupValues?.get(1)?.let { return it }
        }
        return ""
    }

    // ── 底层 HTTP ────────────────────────────────────────

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(
        session: Session,
        url: String,
        secure: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap(),
        nav: Boolean = false,
    ): String = httpGetResult(session, url, secure, extraHeaders, nav).body

    /** 同 [httpGet]，但把状态码与最终地址一起带回来（判失败用，见 [UrpResponse]） */
    private fun httpGetResult(
        session: Session,
        url: String,
        secure: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap(),
        nav: Boolean = false,
    ): UrpResponse = request(session, url, "GET", null, secure, extraHeaders, nav)

    private fun httpPost(
        session: Session,
        url: String,
        body: String,
        secure: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap(),
        nav: Boolean = false,
    ): String = request(session, url, "POST", body, secure, extraHeaders, nav).body

    private fun request(
        session: Session,
        url: String,
        method: String,
        body: String?,
        secure: Boolean,
        extraHeaders: Map<String, String>,
        nav: Boolean = false,
    ): UrpResponse {
        var currentUrl = url
        var currentMethod = method
        var currentBody = body
        var hops = 0

        while (hops < 8) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 12000
            conn.readTimeout = 25000
            conn.requestMethod = currentMethod

            val path = runCatching { java.net.URI(currentUrl).rawPath }.getOrNull() ?: "/"
            UrpThrottle.await(conn.url.host)
            UrpHeaders.apply(
                conn = conn,
                path = path,
                method = currentMethod,
                nav = nav && hops == 0,
                referer = extraHeaders["Referer"],
                omitReferer = extraHeaders["Referer"] == NO_REFERER,
                logTag = if (hops == 0) "URP" else null,
            )
            if (session.cookies.isNotEmpty()) {
                conn.setRequestProperty("Cookie", session.cookieHeader())
            }
            extraHeaders.forEach { (k, v) ->
                if (k != "Referer") conn.setRequestProperty(k, v)
            }
            if (currentMethod == "POST") {
                conn.doOutput = true
                val bytes = (currentBody ?: "").toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }

            var code: Int
            var location: String? = null
            var text = ""
            try {
                session.absorb(conn)
                code = conn.responseCode
                location = conn.getHeaderField("Location")
                if (code in 200..299) {
                    text = conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                }
            } finally {
                runCatching { conn.disconnect() }
            }

            DebugLog.http(
                tag = "URP",
                method = currentMethod,
                url = currentUrl,
                status = code,
                bytes = text.length,
                preview = if (code in 200..299) text else "",
                note = location?.let { "→ " + it.take(90) } ?: "cookie=${session.cookies.size}",
            )

            // 302：跟过去（关键！CAS 回跳要靠它建立 URP 的 JSESSIONID）
            if (code in 300..399 && location != null) {
                currentUrl = URL(URL(currentUrl), location).toString()
                currentMethod = "GET"
                currentBody = null
                hops++
                continue
            }
            return UrpResponse(code = code, body = text, path = path, location = location)
        }
        // 跳转超过 8 跳：不能当成「没数据」，这是一次跟踪失败
        return UrpResponse(code = 0, body = "", path = url, location = null)
    }

    /** RSA 公钥加密（X.509 SubjectPublicKeyInfo，PKCS#1 v1.5 填充） */
    private fun rsaEncrypt(base64Key: String, plain: String): String {
        val der = Base64.decode(base64Key, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(der)
        val key = KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}

