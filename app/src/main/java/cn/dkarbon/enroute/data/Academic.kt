package cn.dkarbon.enroute.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 考试 / 成绩 —— **2026-11-10 用 Playwright 在真实系统上实测出来的接口**。
 *
 * ## 实测结论（沈阳农业大学 URP，登录态下抓的）
 *
 * ```
 * 考试：GET /student/examinationManagement/examPlan/detail?start=YYYY-MM-DD&end=YYYY-MM-DD&_=ts
 *       → [{"title":"课程名\n10:00-11:40\n主校\n第二教学楼\n201\n",
 *           "id":"0","start":"2026-11-15 10:00","end":"2026-11-15 11:40","color":"#3a87ad"}]
 *
 * 成绩：两步（**路径里有一段每次都会变的随机 token**）
 *   ① GET /student/integratedQuery/scoreQuery/allTermScores/index        （HTML）
 *      从中正则抠出 token：/scoreQuery/([A-Za-z0-9]{10,14})/allTermScores/
 *   ② GET /student/integratedQuery/scoreQuery/<token>/allTermScores/data
 *      → {"list":{"pageContext":{"totalCount":55},"records":[{
 *            "KCM":"生态学基础","KCCJ":"73","XF":2,"DJM":"中",
 *            "ZXJXJHM":"2024-2025学年秋","KCSXMC":"限修课","YWKCM":"Fundamentals of Ecology", …}]}}
 * ```
 *
 * ## 为什么必须先取 index 页
 *
 * 同一套系统里各路由的 token 不同，而且**每次页面加载都会变**（实测一次是
 * `5zH5YvO7B8`，几分钟后变成 `C0127gB315`）。所以任何写死 token 的实现都会失效 ——
 * 只能在同一次会话里「取页面 → 抠 token → 立刻查数据」。
 *
 * 旧版路径（`/kwgl/kscx_*`、`/cjcx/cjcx_*`）在这个部署上实测是 **404**，已废弃。
 */
@Serializable
data class ExamItem(
    val name: String = "",
    /** 展示用时间：`2026-11-15 10:00-11:40` */
    val time: String = "",
    /** `主校 第二教学楼 201` */
    val place: String = "",
    val seat: String = "",
    val type: String = "",
    /** 原始 start（ISO 风格，排序用） */
    val startAt: String = "",
    val raw: String = "",
    // ── 结构化字段（2026-11-13 补齐）────────────────────────
    // 为什么要把 title 拆成字段：`title` 是「课程名\n时间\n校区\n教学楼\n教室」五行模板，
    // 上一版把后三行拼成一个 `place` 字符串再用空格 split —— 那是猜。模板固定就按行落位，
    // 界面上「主信息 / 次信息 / 原始」三层才有东西可分层。
    /** 开考日期 `2026-11-15`（取自 start） */
    val date: String = "",
    /** 开考时刻 `10:00` */
    val startClock: String = "",
    /** 结束时刻 `11:40` */
    val endClock: String = "",
    /** 时长（分钟）；起止解析不出来就是 0 —— 绝不按 100 分钟脑补 */
    val durationMin: Int = 0,
    /** 校区 `主校` */
    val campus: String = "",
    /** 教学楼 `第二教学楼` / `线上考场` */
    val building: String = "",
    /** 教室 `201` / `线上虚拟考场1` */
    val room: String = "",
    /** title 第 6 行及以后（目前没见过，但学校加字段时不能静默丢弃） */
    val extraLines: List<String> = emptyList(),
    /** 接口里的 id（URP 给的是 "0"/"1"，不是学号意义上的唯一号） */
    val apiId: String = "",
    /** 接口给的卡片色 `#3a87ad`（只存档，界面不用 —— 全 App 只有一套课程色） */
    val colorHex: String = "",
    /** 原始 key 全量：json 对象里出现过的每个键原样保留，供「看原始 JSON」自查 */
    val fields: Map<String, String> = emptyMap(),
)

@Serializable
data class ScoreItem(
    val name: String = "",
    val credit: String = "",
    /** 百分制分数，如 "73"；等级制课程这里可能是「优秀」这类文字 */
    val score: String = "",
    /** 等级：优 / 良 / 中 / 及格 / 不及格（URP 只给等级，没有绩点算法） */
    val grade: String = "",
    val term: String = "",
    val raw: String = "",
    // ── 结构化字段（2026-11-13 补齐）────────────────────────
    // 上一版只吃了 KCM/KCCJ/XF/DJM/ZXJXJHM 五个键，接口里还有一堆字段被丢掉。
    /** 英文课程名 `Fundamentals of Ecology`（YWKCM） */
    val englishName: String = "",
    /** 课程属性 `限修课`（KCSXMC） */
    val category: String = "",
    /** 课程类型（KCLXMC，实测为空） */
    val courseType: String = "",
    /** 课程号（KCH） */
    val code: String = "",
    /** 课序号（KXH） */
    val classNo: String = "",
    /** 学时（XS） */
    val hours: String = "",
    /** 学期代码 `2024-2025-1-1`（ZXJXJHH），比中文学期名好排序 */
    val termCode: String = "",
    /** 考试日期 `20250101`（KSSJ） */
    val examDate: String = "",
    /** 原始 key 全量（同 [ExamItem.fields]） */
    val fields: Map<String, String> = emptyMap(),
) {
    /** 学分数值；缺失或非数字 → null（统计只认真实数字，见 [ScoreRows]） */
    val creditValue: Double?
        get() = credit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }

    /** 百分制分数字值；「优秀」「合格」这类文字 → null */
    val scoreValue: Double?
        get() = score.trim().toDoubleOrNull()?.takeIf { it in 0.0..150.0 }

    /**
     * 是否明确不及格。
     *
     * 只认两种**有据可查**的证据：分数 < 60，或等级写明「不及格」。
     * 「合格/不合格」考查课、空格子一律按「不知道」处理 —— 宁可少判，不误伤。
     */
    val failed: Boolean
        get() = scoreValue?.let { it < 60.0 } ?: grade.contains("不及格")
}

/** 诊断面板的一行 */
data class ProbeResult(
    val path: String,
    val status: Int,
    val bytes: Int,
    val looksLikeJson: Boolean,
    val sample: String,
)

object AcademicApi {

    /** URP 主机（校内地址）。与 [UrpClient] 里的私有常量保持一致 */
    const val URP_HOST = "10.2.0.47"

    /** 与 [UrpClient] 里的私有常量保持一致（那边没暴露，这里复制一份，改一处记得改两处） */
    private const val URP = "http://$URP_HOST"

    private val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val EXAM_INDEX = "/student/examinationManagement/examPlan/index"
private const val EXAM_DETAIL = "/student/examinationManagement/examPlan/detail"
    private const val SCORE_INDEX = "/student/integratedQuery/scoreQuery/allTermScores/index"

    /** 从成绩 index 页里抠随机 token；抠不到返回 null */
    private val TOKEN_RE = Regex("""scoreQuery/([A-Za-z0-9]{10,14})/allTermScores/""")

    private val DASH = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 接口里 start/end 的形状：`2026-11-15 10:00` */
    private val MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** title 里的座位行。这套部署暂不返回，先认着 —— 学校加了字段不用改代码 */
    private val SEAT_RE = Regex("""^(?:座位号|座位|座号)\s*[:：]?\s*(\S+)$""")

    // ── 考试 ──────────────────────────────────────────────

    /**
     * 考试安排。
     *
     * @param from/to 查询区间；默认「今天往前 100 天 ~ 往后 150 天」，覆盖一个学期。
     *                公开接口按区间查，所以区间给宽一点比较稳（实测服务器会自己截断）。
     */
    suspend fun fetchExams(
        session: UrpClient.Session,
        from: LocalDate = LocalDate.now().minusDays(100),
        to: LocalDate = LocalDate.now().plusDays(150),
    ): List<ExamItem> = fetchExamsOrNull(session, from, to).orEmpty()

    /**
     * 与 [fetchExams] 完全同一条链路，但**区分「真没有考试」和「没拿到」**（票 24）。
     *
     * 为什么非要多这一支：缓存分层要靠「这次抓成功了吗」决定**要不要覆盖旧值**
     * （断网冷启动绝不能把缓存里的考试抹掉）。而 [fetchExams] 把所有失败都压成
     * `emptyList()`，从外部看不出区别 —— 那是丢信息。
     *
     * @return 服务端答的是数据页 → 列表（可能合法为空，例如本学期还没排考试）；
     *   请求/解析/会话失效 → `null`（**没拿到，不是「没有」**）
     */
    suspend fun fetchExamsOrNull(
        session: UrpClient.Session,
        from: LocalDate = LocalDate.now().minusDays(100),
        to: LocalDate = LocalDate.now().plusDays(150),
    ): List<ExamItem>? {
        // ⚠️ 必须先 GET 一次考试页，再请求 detail，否则服务端返回
        //    {"status":"203","data":""}（看起来像「没有考试」，其实是没建会话上下文）。
        //    2026-11-11 实测：同一个日期区间，不预热 = 203，预热后 = 4 场考试。
        runCatching { get(EXAM_INDEX, session) }

        val path = "$EXAM_DETAIL?start=${from.format(DASH)}&end=${to.format(DASH)}&_=${System.currentTimeMillis()}"
        val body = runCatching { get(path, session).second }.getOrNull() ?: return null
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        // 「203/空 data」这类响应也是合法 JSON，但解析后没有任何考试条目 —— 那确实是「当前没有考试」
        return parseExams(el)
    }

    /** 纯解析入口（单测用真实 payload 锁住 schema） */
    internal fun parseExamsJson(text: String): List<ExamItem> =
        runCatching { parseExams(json.parseToJsonElement(text)) }.getOrDefault(emptyList())

    /** 纯解析入口（单测用） */
    internal fun parseScoresJson(text: String): List<ScoreItem> =
        runCatching { parseScores(json.parseToJsonElement(text)) }.getOrDefault(emptyList())

    private fun parseExams(el: JsonElement): List<ExamItem> {
        val arr = el as? JsonArray ?: return emptyList()
        return arr.filterIsInstance<JsonObject>().map { o ->
            val title = str(o, "title")
            // title 的格式是「课程名 \n 时间段 \n 校区 \n 教学楼 \n 教室」（模板固定，逐行落位）
            val parts = title.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val name = parts.getOrNull(0).orEmpty()
            val rawTime = parts.getOrNull(1).orEmpty()

            val start = str(o, "start")   // 2026-11-15 10:00
            val end = str(o, "end")       // 2026-11-15 11:40
            val date = start.substringBefore(' ').trim()
            val startClock = start.substringAfter(' ', "").trim()
            val endClock = end.substringAfter(' ', "").trim()
            val timeLabel = buildString {
                if (date.isNotBlank()) append(date).append(' ')
                if (startClock.isNotBlank() && endClock.isNotBlank()) {
                    append("$startClock-$endClock")
                } else {
                    append(rawTime)
                }
            }.trim()

            // 第 3 行起是地点三件套；第 6 行以后是学校自己加的，不许丢
            val campus = parts.getOrNull(2).orEmpty()
            val building = parts.getOrNull(3).orEmpty()
            val roomLine = parts.getOrNull(4).orEmpty()
            val tail = parts.drop(5)

            // 座位号目前这套部署不返回；只有真出现「座位…」行才认，否则留空（不编）
            val seatLine = (listOf(roomLine) + tail).firstOrNull { SEAT_RE.matches(it) }
            val seat = seatLine?.let { SEAT_RE.find(it)?.groupValues?.get(1)?.trim() }.orEmpty()
            val extra = if (seatLine == null) tail else tail - seatLine

            // 「主校 第二教学楼 201」这个拼串是上一版的展示契约，AppState/Widget 还在用，保留
            val place = (listOf(campus, building, roomLine) + tail)
                .filter { it.isNotBlank() && it != seatLine }
                .joinToString(" ")

            ExamItem(
                name = name,
                time = timeLabel,
                place = place,
                seat = seat,
                startAt = start,
                raw = o.toString(),
                date = date,
                startClock = startClock,
                endClock = endClock,
                durationMin = minutesBetween(start, end),
                campus = campus,
                building = building,
                // 若这一行其实是座位行，教室就是空 —— 不把「座位 33」当教室
                room = if (seatLine != null && seatLine == roomLine) "" else roomLine,
                extraLines = extra,
                apiId = str(o, "id"),
                colorHex = str(o, "color"),
                fields = fieldsOf(o),
            )
        }.sortedBy { it.startAt }
    }

    /** 起止时刻算时长；任何一端解析不出来就返回 0（表示「不知道」，不是 100 分钟） */
    private fun minutesBetween(start: String, end: String): Int {
        val s = runCatching { LocalDateTime.parse(start, MINUTE_FMT) }.getOrNull() ?: return 0
        val e = runCatching { LocalDateTime.parse(end, MINUTE_FMT) }.getOrNull() ?: return 0
        val m = java.time.Duration.between(s, e).toMinutes()
        return if (m in 1..600) m.toInt() else 0
    }

    // ── 成绩 ──────────────────────────────────────────────

    /**
     * 全部学期成绩。
     *
     * 两步走：先取 index 页抠 token，再打 data。**中间不能停顿太久** ——
     * token 跟页面会话绑着，隔太久可能失效，所以两请求紧挨着发。
     */
    suspend fun fetchScores(session: UrpClient.Session): List<ScoreItem> =
        fetchScoresOrNull(session).orEmpty()

    /**
     * 与 [fetchScores] 同一条链路，但失败返回 `null`（票 24 的「没拿到 ≠ 没有成绩」）。
     * 理由同 [fetchExamsOrNull]：缓存要不要覆盖，取决于这次是不是真的抓到了。
     */
    suspend fun fetchScoresOrNull(session: UrpClient.Session): List<ScoreItem>? {
        val html = runCatching { get(SCORE_INDEX, session).second }.getOrNull() ?: return null
        val token = TOKEN_RE.find(html)?.groupValues?.get(1) ?: return null
        val path = "/student/integratedQuery/scoreQuery/$token/allTermScores/data"
        val body = runCatching { get(path, session).second }.getOrNull() ?: return null
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        return parseScores(el)
    }

    private fun parseScores(el: JsonElement): List<ScoreItem> {
        val records = ((el as? JsonObject)?.get("list") as? JsonObject)
            ?.get("records") as? JsonArray ?: return emptyList()

        return records.filterIsInstance<JsonObject>().map { o ->
            ScoreItem(
                name = str(o, "KCM"),
                credit = str(o, "XF"),
                score = str(o, "KCCJ"),
                grade = str(o, "DJM"),
                term = str(o, "ZXJXJHM"),
                raw = o.toString(),
                englishName = str(o, "YWKCM"),
                category = str(o, "KCSXMC"),
                courseType = str(o, "KCLXMC"),
                code = str(o, "KCH"),
                classNo = str(o, "KXH"),
                hours = str(o, "XS"),
                termCode = str(o, "ZXJXJHH"),
                examDate = str(o, "KSSJ"),
                fields = fieldsOf(o),
            )
        }.filter { it.name.isNotBlank() }
    }

    /**
     * json 对象的全部键原样收进 Map。
     *
     * 空值和 null 也保留键名：用户自查时「这个字段存在但是空的」和「根本没这个字段」
     * 是两条不同的结论，少一条就没法判断是不是学校改了 schema。
     */
    private fun fieldsOf(o: JsonObject): Map<String, String> {
        val m = LinkedHashMap<String, String>(o.size)
        o.forEach { (k, v) ->
            m[k] = if (v is JsonNull) "" else (v as? JsonPrimitive)?.content.orEmpty()
        }
        return m
    }

    // ── 诊断文案（不发请求，只拼给人看的地址）──────────────

    /** 考试数据实际打的地址（诊断浮层显示用） */
    fun examPathPreview(): String {
        val today = LocalDate.now()
        return "$EXAM_DETAIL?start=${today.minusDays(100).format(DASH)}" +
            "&end=${today.plusDays(150).format(DASH)}&_=<ts>"
    }

    /** 成绩两步流的地址形态（token 每次都变，所以只能给形态） */
    fun scorePathPreview(): String =
        "$SCORE_INDEX → /student/integratedQuery/scoreQuery/<token>/allTermScores/data"

    // ── 诊断 ──────────────────────────────────────────────

    /**
     * 逐条探 [ProbeCatalog.specs]，探完一条回调一条 —— UI 才能一条一条地放锁和钥匙。
     *
     * 顺序由 [ProbeCatalog.specs] 决定，不能打乱：成绩数据要用成绩首页给出的 token，
     * 培养方案明细要用方案页给出的 token。全部及格那条是**两步流**（先打
     * `allPassingScores/index` 抠 token，再打 `allPassingScores/callback`），
     * 行里报的是 callback 的结果 —— token 本身不上 UI。
     *
     * 只读：这 14 条全是 GET，不碰选课、退课、报名、评教。
     *
     * @param onStep (索引, 结果)，索引与 [ProbeCatalog.specs] 一一对应。
     */
    suspend fun probeEach(
        session: UrpClient.Session,
        onStep: (index: Int, result: ProbeResult) -> Unit,
    ) {
        val today = LocalDate.now()
        val examPath = "$EXAM_DETAIL?start=${today.minusDays(100).format(DASH)}" +
            "&end=${today.plusDays(150).format(DASH)}&_=${System.currentTimeMillis()}"

        var scoreToken: String? = null
        var planToken: String? = null

        ProbeCatalog.specs.forEachIndexed { i, spec ->
            val pair: Pair<ProbeResult, String> = when (spec.id) {
                // 考试页顺带当明细的预热：不先开这一页，detail 恒返回 203。
                "exam.index" -> once(examPath, session)

                "score.index" -> once(SCORE_INDEX, session).also { p ->
                    scoreToken = TOKEN_RE.find(p.second)?.groupValues?.get(1)
                }

                "score.data" -> scoreToken?.let {
                    once("/student/integratedQuery/scoreQuery/$it/allTermScores/data", session)
                } ?: noToken(spec.path, "成绩首页没给出 token")

                "score.pass" -> {
                    val tk = once(UrpHomeApi.PASS_INDEX, session).second.let { html ->
                        UrpHomeApi.PASS_TOKEN_RE.find(html)?.groupValues?.get(1)
                    }
                    tk?.let {
                        once("/student/integratedQuery/scoreQuery/$it/allPassingScores/callback", session)
                    } ?: noToken(spec.path, "全部及格页没给出 token")
                }

                "plan.index" -> once(UrpHomeApi.PLAN_INDEX, session).also { p ->
                    planToken = UrpHomeApi.PLAN_TOKEN_RE.find(p.second)?.groupValues?.get(1)
                }

                "plan.detail" -> planToken?.let {
                    once("/student/integratedQuery/instructionPlanQuery/$it/detail/callback", session)
                } ?: noToken(spec.path, "培养方案页没给出 token")

                // 查教师是 POST 表单接口，GET 会 405。参数取「空学期 + 空关键字 + 只要 1 条」：
                // 服务器照样回 JSON（`records: []`），足以说明这条接口还活着。
                "teacher.search" -> call(
                    spec.path, session,
                    listOf(
                        "executiveEducationPlanNumber" to "",
                        "departmentNum" to "",
                        "teacherName" to "",
                        "pageNum" to "1",
                        "pageSize" to "1",
                    ),
                )

                // 空闲教室是**会话粘滞**的两步：先 POST 选中楼栋，GET 才答得出那一栋。
                // 第一步只是选择视图（不写任何数据），第二步才是要报的那一条。
                "room.free" -> {
                    call(
                        "/student/teachingResources/freeClassroom/today", session,
                        listOf("position" to "001_1", "xqm" to ""),
                    )
                    once("/student/teachingResources/freeClassroom/today/11?dayplus=0", session)
                }

                else -> once(spec.path, session)
            }
            onStep(i, pair.first)
        }
    }

    /** 打一个地址（默认 GET，给了 [form] 就 POST 表单），返回结果和用来抠 token 的完整正文 */
    private fun call(
        path: String,
        session: UrpClient.Session,
        form: List<Pair<String, String>>? = null,
    ): Pair<ProbeResult, String> =
        runCatching { if (form == null) get(path, session) else post(path, form, session) }
            .map { (code, body) ->
                val head = body.trimStart()
                val json = head.startsWith("{") || head.startsWith("[")
                ProbeResult(path, code, body.length, json, head.take(200)) to body
            }
            .getOrElse {
                ProbeResult(path, -1, 0, false, "请求失败：${it.message}") to ""
            }

    private fun once(path: String, session: UrpClient.Session) = call(path, session)

    /** 前置页面没给出 token —— 请求根本没有发出去，如实记成失败，不伪造状态码 */
    private fun noToken(path: String, why: String): Pair<ProbeResult, String> =
        ProbeResult(path, -1, 0, false, why) to ""

    // ── 工具 ──────────────────────────────────────────────

    private fun str(o: JsonObject, key: String): String {
        val v = o[key]
        if (v == null || v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content?.trim().orEmpty()
    }

    private fun get(path: String, session: UrpClient.Session): Pair<Int, String> {
        DebugLog.request("API", "GET", path)
        val conn = URL(URP + path).openConnection() as java.net.HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Referer", "$URP/")
            val cookie = session.cookieHeader()
            if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            DebugLog.http("API", "GET", path, code, body.length, preview = body)
            code to body
        } catch (e: Exception) {
            DebugLog.error("API", "GET ${path.take(80)} 失败：${e.javaClass.simpleName} ${e.message}")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** 诊断用的一次 POST 表单请求。只给只读的两条（查教师、选空闲教室的楼栋）用 */
    private fun post(
        path: String,
        form: List<Pair<String, String>>,
        session: UrpClient.Session,
    ): Pair<Int, String> {
        DebugLog.request("API", "POST", path)
        val body = form.joinToString("&") {
            "${java.net.URLEncoder.encode(it.first, "UTF-8")}=" +
                java.net.URLEncoder.encode(it.second, "UTF-8")
        }
        val conn = URL(URP + path).openConnection() as java.net.HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            conn.setRequestProperty("Referer", "$URP/")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val cookie = session.cookieHeader()
            if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            DebugLog.http("API", "POST", path, code, text.length, preview = text)
            code to text
        } catch (e: Exception) {
            DebugLog.error("API", "POST ${path.take(80)} 失败：${e.javaClass.simpleName} ${e.message}")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** 供外部（设置页）显示用的「接口版本」标记 */
    fun apiVersion(): String = "urp-syau/2 · " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd"))
}
