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
)

@Serializable
data class ScoreItem(
    val name: String = "",
    val credit: String = "",
    /** 百分制分数，如 "73" */
    val score: String = "",
    /** 等级：优 / 良 / 中 / 及格 / 不及格（URP 只给等级，没有绩点算法） */
    val grade: String = "",
    val term: String = "",
    val raw: String = "",
)

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
    ): List<ExamItem> {
        // ⚠️ 必须先 GET 一次考试页，再请求 detail，否则服务端返回
        //    {"status":"203","data":""}（看起来像「没有考试」，其实是没建会话上下文）。
        //    2026-11-11 实测：同一个日期区间，不预热 = 203，预热后 = 4 场考试。
        runCatching { get(EXAM_INDEX, session) }

        val path = "$EXAM_DETAIL?start=${from.format(DASH)}&end=${to.format(DASH)}&_=${System.currentTimeMillis()}"
        val body = runCatching { get(path, session).second }.getOrNull() ?: return emptyList()
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
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
            // title 的格式是「课程名 \n 时间段 \n 校区 \n 楼 \n 教室」
            val parts = title.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val name = parts.getOrNull(0).orEmpty()
            val rawTime = parts.getOrNull(1).orEmpty()
            val place = parts.drop(2).joinToString(" ")

            val start = str(o, "start")   // 2026-11-15 10:00
            val end = str(o, "end")       // 2026-11-15 11:40
            val timeLabel = buildString {
                val day = start.substringBefore(' ').ifBlank { "" }
                val s = start.substringAfter(' ', "")
                val e = end.substringAfter(' ', "")
                if (day.isNotBlank()) append(day).append(' ')
                if (s.isNotBlank() && e.isNotBlank()) append("$s-$e") else append(rawTime)
            }.trim()

            ExamItem(
                name = name,
                time = timeLabel,
                place = place,
                startAt = start,
                raw = o.toString().take(400),
            )
        }.sortedBy { it.startAt }
    }

    // ── 成绩 ──────────────────────────────────────────────

    /**
     * 全部学期成绩。
     *
     * 两步走：先取 index 页抠 token，再打 data。**中间不能停顿太久** ——
     * token 跟页面会话绑着，隔太久可能失效，所以两请求紧挨着发。
     */
    suspend fun fetchScores(session: UrpClient.Session): List<ScoreItem> {
        val html = runCatching { get(SCORE_INDEX, session).second }.getOrNull() ?: return emptyList()
        val token = TOKEN_RE.find(html)?.groupValues?.get(1) ?: return emptyList()
        val path = "/student/integratedQuery/scoreQuery/$token/allTermScores/data"
        val body = runCatching { get(path, session).second }.getOrNull() ?: return emptyList()
        val el = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
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
                raw = o.toString().take(400),
            )
        }.filter { it.name.isNotBlank() }
    }

    // ── 诊断 ──────────────────────────────────────────────

    /**
     * 把真实用到的几条路径打一遍，给设置页「接口诊断」用。
     * 现在列出了真接口，所以它的作用变成「确认登录态还有效 / 学校换版本了没」。
     */
    suspend fun probe(session: UrpClient.Session): List<ProbeResult> {
        val out = mutableListOf<ProbeResult>()
        val today = LocalDate.now()
        val examPath = "$EXAM_DETAIL?start=${today.minusDays(100).format(DASH)}" +
            "&end=${today.plusDays(150).format(DASH)}&_=${System.currentTimeMillis()}"

        // ① 考试（先预热考试页，否则 detail 恒返回 203）
        runCatching { get(EXAM_INDEX, session) }
        out += runCatching {
            val (status, body) = get(examPath, session)
            ProbeResult(examPath, status, body.length, body.trimStart().startsWith("["), body.take(200))
        }.getOrElse { ProbeResult(examPath, -1, 0, false, "请求失败：${it.message}") }

        // ② 成绩首页（拿 token）
        var token: String? = null
        out += runCatching {
            val (status, body) = get(SCORE_INDEX, session)
            token = TOKEN_RE.find(body)?.groupValues?.get(1)
            ProbeResult(
                SCORE_INDEX, status, body.length, false,
                "token=" + (token ?: "未找到") + " · " + body.take(120).replace("\n", " "),
            )
        }.getOrElse { ProbeResult(SCORE_INDEX, -1, 0, false, "请求失败：${it.message}") }

        // ③ 成绩数据
        token?.let { tk ->
            val dataPath = "/student/integratedQuery/scoreQuery/$tk/allTermScores/data"
            out += runCatching {
                val (status, body) = get(dataPath, session)
                ProbeResult(dataPath, status, body.length, body.trimStart().startsWith("{"), body.take(200))
            }.getOrElse { ProbeResult(dataPath, -1, 0, false, "请求失败：${it.message}") }
        }

        return out
    }

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

    /** 供外部（设置页）显示用的「接口版本」标记 */
    fun apiVersion(): String = "urp-syau/2 · " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd"))
}
