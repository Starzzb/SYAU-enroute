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
 * ⚠️ 凭据只在内存里用，不落盘。这点是刻意的 —— App 没有安全的密钥库使用场景，
 * 明文存账号密码风险太大，宁可每次登录都手输。
 */
object UrpClient {

    private const val SSO = "https://pass.syau.edu.cn"
    private const val URP = "http://10.2.0.47"

    private val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

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
    }

    /**
     * 登录。
     * @return 会话对象，用于后续拉课表
     */
    suspend fun login(username: String, password: String): Session = withContext(Dispatchers.IO) {
        val session = Session()

        // ── ① 取登录页，抠 lt ────────────────────────────
        val loginUrl = "$SSO/tpass/login?service=" +
            URLEncoder.encode("$URP/login", "UTF-8")
        val page = httpGet(session, loginUrl, secure = true)
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
            "$SSO/tpass/rsa",
            "",
            secure = true,
            extraHeaders = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to loginUrl,
            ),
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

        val resp = httpPost(session, "$SSO/tpass/login?service=" + URLEncoder.encode("$URP/login", "UTF-8"),
            form, secure = true)
        DebugLog.step(
            "URP",
            "SSO 返回 ${resp.length} 字节；含 errormsghide=${resp.contains("errormsghide")} 又回登录页=${resp.contains("loginForm")}",
        )

        // 关键：CAS 成功后是 302 → service?ticket=ST-xxx
        // 必须跟进去，URP 才会用 ticket 换到自己的 JSESSIONID。
        // httpPost 内部已经跟随 302，但 POST 的响应体为空，
        // 所以这里再主动访问一次 URP 首页确保会话建立。
        runCatching { httpGet(session, "$URP/index") }.getOrNull()?.let { html ->
            session.displayName = parseDisplayName(html)
            DebugLog.step(
                "URP",
                "已回跳 URP/index（${html.length} 字节），cookie 数=${session.cookies.size}，" +
                    "姓名=${session.displayName.ifBlank { "未识别" }}",
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
     * 拉本学期课表。
     * 返回原始 xkxx JSON（结构见 syau-cas-reverse docs/06 第二节）。
     */
    suspend fun fetchSchedule(session: Session): String = withContext(Dispatchers.IO) {
        // 先访问门户建立应用会话
        runCatching { httpGet(session, "$URP/index") }

        val url = "$URP/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback"
        DebugLog.step("URP", "开始拉课表")
        httpGet(
            session, url,
            extraHeaders = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "$URP/student/courseSelect/thisSemesterCurriculum/index",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
            ),
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
    ): String = request(session, url, "GET", null, secure, extraHeaders)

    private fun httpPost(
        session: Session,
        url: String,
        body: String,
        secure: Boolean = false,
        extraHeaders: Map<String, String> = emptyMap(),
    ): String = request(session, url, "POST", body, secure, extraHeaders)

    private fun request(
        session: Session,
        url: String,
        method: String,
        body: String?,
        secure: Boolean,
        extraHeaders: Map<String, String>,
    ): String {
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

            conn.setRequestProperty("User-Agent", UA)
            if (session.cookies.isNotEmpty()) {
                conn.setRequestProperty("Cookie", session.cookieHeader())
            }
            extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }

            if (currentMethod == "POST") {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
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
            return text
        }
        return ""
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
