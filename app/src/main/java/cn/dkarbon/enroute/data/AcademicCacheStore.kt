package cn.dkarbon.enroute.data

import android.content.Context
import cn.dkarbon.enroute.data.UrpHomeApi.BuildingOption
import cn.dkarbon.enroute.data.UrpHomeApi.CampusOption
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 缓存**三档分层**（票 24）。
 *
 * | 档 | 数据 | 判据 |
 * |---|---|---|
 * | 永久 [CacheTier.Permanent] | 学号/姓名/院系/层次/入学年月（学籍身份） | 只补空：**首次成功后永不自动重抓**，只有「清除所有数据」或换账号才失效 |
 * | 每日 [CacheTier.Daily] | 成绩与绩点（成绩统计）、考试、空闲教室的校区/教学楼列表 | 距上次**成功**抓取 ≥ 24h 才抓，否则直读缓存 |
 * | 每次 [CacheTier.Live] | 资讯流、地图补丁、更新检查、空闲教室的**教室明细** | 保持现状不动（各自已有 TTL/刷新策略） |
 *
 * ## 为什么规则要单独抽成纯函数
 *
 * 「隔多久算新鲜」「什么时候必须重抓」这两件事错了，表现是**界面给错数据**而不是崩溃 ——
 * 上真机看是看不出来的（尤其 24h 边界和考试过期）。抽成纯函数后用 JVM 单测钉死，
 * 与 `FacultyCache`（TTL 30 天）同一套做法，但**不复用它的判断**：
 * 教师资料 30 天不变，学籍更是几年不变，考试却每考一场就变一次 —— 三者混用一个 TTL 必然错一个。
 *
 * ## 纪律
 *
 * 1. **失败不覆盖**：抓失败就保留旧值旧时间戳（见 [AcademicCache.merge] 的 `null` 语义），
 *    界面宁可显示旧数据 + 「上次更新：X 小时前」，也不能因为断网就清空。
 * 2. **不复用 `UpdateManifest.shouldAutoCheck`**：那是「24h 窗口」的同一套判据，但它属于更新检查那条链路；
 *    这里要额外带「考试已过」这第二个条件，语义不同，硬合并会让两边都变得不好改。
 */
enum class CacheTier {
    /** 读一次就够 */
    Permanent,

    /** 一天一次（静默） */
    Daily,

    /** 每次都要新的（不进本缓存） */
    Live,
}

/** 三档判据（纯函数，单测直接打） */
object CachePolicy {

    /** 「每日档」的窗口：24 小时 */
    const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 缓存是否还新鲜（每日档）。
     *
     * - 从没抓过（`atMs <= 0`）→ 不新鲜；
     * - **时钟被往回调**（`now < at`，用户改时间/跨时区）→ 不新鲜，宁可多抓一次，
     *   否则「时间调回去」会让自动刷新永久停摆（这与 `UpdateManifest.shouldAutoCheck` 的判断一致）；
     * - 满 24 小时**整**就算不新鲜（`>=`，边界归「该抓」）。
     */
    fun isFresh(nowMs: Long, atMs: Long, windowMs: Long = DAY_MS): Boolean {
        if (atMs <= 0L) return false
        if (nowMs < atMs) return false
        return nowMs - atMs < windowMs
    }

    /** 每日档（成绩/绩点/校区列表）：不新鲜就该抓 */
    fun shouldFetchDaily(nowMs: Long, atMs: Long, windowMs: Long = DAY_MS): Boolean =
        !isFresh(nowMs, atMs, windowMs)

    /**
     * 每日档里「学分进度 + 绩点」两块**显示用的获取时刻**：取两块中较新的那次。
     *
     * 两块在抓取时是同一次请求、同一时间戳落盘，正常情况相等；只有其中一块抓失败时才分叉。
     * 分叉时取较新的，至少标的是「最近一次拿到新数据」的时刻。两块都没抓过 → 0（界面不显示假时间）。
     */
    fun dailyBlockAt(creditAt: Long, scoreStatsAt: Long): Long = maxOf(creditAt, scoreStatsAt)

    /**
     * 永久档（学籍身份）：**只补空**。
     *
     * 缓存里已经有身份就再也不自动重抓 —— 学号姓名院系入学年月不会变，
     * 每次冷启动为它打一次网络纯属浪费（这正是票 24 要治的体感）。
     * 只有「缓存是空的」（首次安装/清除所有数据后）才抓。
     */
    fun shouldFetchPermanent(cachedIsEmpty: Boolean): Boolean = cachedIsEmpty

    /**
     * **考试**的判据：两个条件取或（票 24 的关键设计之一）。
     *
     * ```
     * 距上次成功抓取 ≥ 24h  或  缓存里那场「下一场考试」已经开考/过去  →  重抓
     * ```
     *
     * 为什么必须有第二个条件：只看时间的话，考试当天考完之后的一整天里，
     * 缓存仍然「新鲜」，界面会继续拿一份含已考完科目的快照 —— 那是**错数据**，
     * 比不显示更糟。考完那一刻起，「下一场」这个概念就变了，必须重抓。
     *
     * @param nextExamAtMs 上次抓取时算出的「下一场考试开考时刻」（epoch ms）；
     *   `<= 0` 表示**当时就没有待考考试**（寒假/考完了），此时不触发考试条件，
     *   只按 24h 走 —— 否则「没有下一场」会被永远判成「该重抓」，变成每次冷启动都打网络。
     */
    fun shouldFetchExams(
        nowMs: Long,
        examsFetchedAt: Long,
        nextExamAtMs: Long,
        windowMs: Long = DAY_MS,
    ): Boolean {
        if (examsFetchedAt <= 0L) return true          // 从没抓过
        if (nowMs < examsFetchedAt) return true        // 时钟回拨 → 抓一次
        if (nowMs - examsFetchedAt >= windowMs) return true
        return nextExamAtMs > 0L && nowMs >= nextExamAtMs
    }

    /**
     * **总判据**：还有没有哪一块该抓？
     *
     * `false` = 冷启动**一个请求都不用发**（这正是票 24 要让学生感觉到的东西）。
     * 单独写成一支的理由：ViewModel 里"分块各判一次"很容易和这里漂移——
     * 一边改了窗口、另一边没改，界面就会莫名其妙多打一次网络。
     * 统一走这里，再用单测把「全新鲜 → false」钉死。
     *
     * @param profileIsEmpty 身份缓存是否为空（永久档只补空）
     */
    fun anyBlockStale(
        nowMs: Long,
        profileIsEmpty: Boolean,
        creditAt: Long,
        scoreStatsAt: Long,
        examsAt: Long,
        nextExamAtMs: Long,
        scoresAt: Long,
        windowMs: Long = DAY_MS,
    ): Boolean {
        if (shouldFetchPermanent(profileIsEmpty)) return true
        // 学分进度与绩点是同一支接口的两个面，任一为空/过期就一起补
        val progressAt = minOf(creditAt, scoreStatsAt).takeIf { it > 0L } ?: 0L
        if (shouldFetchDaily(nowMs, progressAt, windowMs)) return true
        if (shouldFetchExams(nowMs, examsAt, nextExamAtMs, windowMs)) return true
        return shouldFetchDaily(nowMs, scoresAt, windowMs)
    }

    /**
     * 一节考试的开考时刻。`date = 2026-11-15` + `startClock = 10:00` → epoch ms。
     *
     * **解析不出来就返回 `null`**（0 也是 null 的等价物），不猜：
     * 猜错时间会让 [shouldFetchExams] 在错误时刻触发重抓，或者永远不触发。
     */
    fun examStartMs(date: String, startClock: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val d = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return null
        val t = runCatching { LocalTime.parse(startClock.trim()) }.getOrNull() ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(d, t).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * 抓取那一刻的「下一场考试」开考时刻（epoch ms）；没有待考的、或时间都解析不出来 → `0`。
     *
     * 存进缓存的是**这个值**，而不是每次现场重算 —— 因为缓存里的考试列表本身就是快照，
     * 重算只会得到同一个答案；而「当时没有下一场」这件事必须作为一个明确的记录留下来。
     */
    fun nextExamAtMs(exams: List<ExamItem>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        exams.mapNotNull { examStartMs(it.date, it.startClock, zone) }
            .filter { it > nowMs }
            .minOrNull()
            ?: 0L

    /**
     * 「上次更新」的人话（给界面标在数据旁边）。
     *
     * `atMs <= 0` → 空串（**从没更新过就不显示**，不要写「0 分钟前」这种假时间）。
     */
    fun ageLabel(nowMs: Long, atMs: Long): String {
        if (atMs <= 0L) return ""
        if (nowMs < atMs) return "刚刚"           // 时钟回拨，别显示负数
        val mins = (nowMs - atMs) / 60_000L
        return when {
            mins < 1 -> "刚刚"
            mins < 60 -> "$mins 分钟前"
            mins < 60 * 24 -> "${mins / 60} 小时前"
            else -> "${mins / (60 * 24)} 天前"
        }
    }

    /**
     * 「数据取自」的**绝对时刻**：`11-15 14:32`；不足 1 分钟 → 「刚刚」；从没抓过 → 空串。
     *
     * 与 [ageLabel] 的分工：ageLabel 是「多久前」的相对说法（主页那行「更新于 …」还在用），
     * 这个是「几点几分」的绝对说法（页顶那行「数据取自 11-15 14:32」用）。
     * 两个都不猜：时间戳是 0 就是不显示。
     */
    fun ageStamp(nowMs: Long, atMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (atMs <= 0L) return ""
        if (nowMs < atMs) return "刚刚"
        if ((nowMs - atMs) < 60_000L) return "刚刚"
        return runCatching {
            java.time.Instant.ofEpochMilli(atMs).atZone(zone)
                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        }.getOrDefault("")
    }

    /** 页顶那行：`刚刚更新` / `数据取自 11-15 14:32` / 空串（没抓过不显示假时间） */
    fun fetchStamp(nowMs: Long, atMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val s = ageStamp(nowMs, atMs, zone)
        if (s.isBlank()) return ""
        return if (s == "刚刚") "刚刚更新" else "数据取自 $s"
    }
}

/**
 * 教务数据的**本地缓存快照**（票 24）。
 *
 * 一个文件、一个用户、一份快照。每块各带自己的「上次成功抓取时间」，
 * 所以「身份三天前抓的、成绩两小时前抓的」可以同时成立 —— 这正是分档的意义。
 *
 * 只存**解析后**的结构（不是原始 payload）：打开即用，不受学校改字段影响。
 */
@Serializable
data class AcademicCache(
    val version: Int = VERSION,

    // ── 永久档：学籍身份 ──
    val profile: StudentProfile = StudentProfile(),
    val profileAt: Long = 0L,

    // ── 每日档 ──
    /** 培养方案学分进度（学校端算好的需修/已得） */
    val credit: CreditProgress = CreditProgress(),
    val creditAt: Long = 0L,
    /** 成绩统计（按学校绩点加权 + 通过门数） */
    val scoreStats: ScoreStats = ScoreStats(),
    val scoreStatsAt: Long = 0L,
    /** 成绩明细（成绩查询页用的列表） */
    val scores: List<ScoreItem> = emptyList(),
    val scoresAt: Long = 0L,
    /** 考试安排 */
    val exams: List<ExamItem> = emptyList(),
    val examsAt: Long = 0L,
    /** 抓取那一刻的「下一场考试」开考时刻；0 = 当时没有待考考试（见 [CachePolicy.shouldFetchExams]） */
    val nextExamAtMs: Long = 0L,
    /** 空闲教室的校区/教学楼**列表**（教室明细是实时数据，故意不缓存） */
    val campuses: List<UrpHomeApi.CampusOption> = emptyList(),
    val buildings: List<UrpHomeApi.BuildingOption> = emptyList(),
    val roomsListsAt: Long = 0L,
    /** 缓存里的教学楼列表对应哪个校区号（换校区后旧楼列表不可用） */
    val buildingsCampusNo: String = "",

    // ── 永久档：历年课表学期候选 ──
    /** 服务器给的最近 5 个学期（含当前；历史学期不会再变，取一次就够） */
    val historyTerms: List<HistorySemesters.Option> = emptyList(),
    val historyTermsAt: Long = 0L,
) {

    val isEmpty: Boolean
        get() = profile.isEmpty && credit.isEmpty && scoreStats.isEmpty &&
            scores.isEmpty() && exams.isEmpty() && campuses.isEmpty()

    /**
     * 把一次抓取的结果合进快照（**纯函数**，单测直接打）。
     *
     * 参数用 `null` 表示「这次没抓 / 抓失败了」→ **保留旧值和旧时间戳**。
     * 传空集合表示「抓成功了，但服务器就是没有内容」（比如本学期还没排考试）→ 覆盖成空。
     *
     * 这个区分是「失败不覆盖」那条纪律的落地方式：调用方在失败分支传 `null`，
     * 就不可能因为一次断网把学生的成绩抹掉。
     */
    fun merge(
        nowMs: Long,
        profile: StudentProfile? = null,
        credit: CreditProgress? = null,
        scoreStats: ScoreStats? = null,
        scores: List<ScoreItem>? = null,
        exams: List<ExamItem>? = null,
        campuses: List<UrpHomeApi.CampusOption>? = null,
        buildings: List<UrpHomeApi.BuildingOption>? = null,
        buildingsCampusNo: String? = null,
        historyTerms: List<HistorySemesters.Option>? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AcademicCache = copy(
        profile = profile ?: this.profile,
        profileAt = if (profile != null) nowMs else this.profileAt,
        credit = credit ?: this.credit,
        creditAt = if (credit != null) nowMs else this.creditAt,
        scoreStats = scoreStats ?: this.scoreStats,
        scoreStatsAt = if (scoreStats != null) nowMs else this.scoreStatsAt,
        scores = scores ?: this.scores,
        scoresAt = if (scores != null) nowMs else this.scoresAt,
        exams = exams ?: this.exams,
        examsAt = if (exams != null) nowMs else this.examsAt,
        nextExamAtMs = if (exams != null) CachePolicy.nextExamAtMs(exams, nowMs, zone) else this.nextExamAtMs,
        campuses = campuses ?: this.campuses,
        buildings = buildings ?: this.buildings,
        roomsListsAt = if (campuses != null || buildings != null) nowMs else this.roomsListsAt,
        buildingsCampusNo = if (buildingsCampusNo != null) buildingsCampusNo else this.buildingsCampusNo,
        historyTerms = historyTerms ?: this.historyTerms,
        historyTermsAt = if (historyTerms != null) nowMs else this.historyTermsAt,
    )

    companion object {
        /** 序列化格式版本；读到别的版本一律当没有（不猜字段） */
        const val VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

        fun toJson(cache: AcademicCache): String = json.encodeToString(serializer(), cache)

        fun fromJson(text: String): AcademicCache? = runCatching {
            json.decodeFromString(serializer(), text)
        }.getOrNull()?.takeIf { it.version == VERSION }
    }
}

/**
 * 缓存落盘：`filesDir/academic/cache.json`，**原子写**（先 `.tmp` 再改名）。
 *
 * 为什么一个文件而不是一堆文件：这几块永远是整体读、整体写，体积只有几十 KB；
 * 一条一次 `writeText` 比五个文件五套错误处理简单得多。
 * （教师资料要按工号分开存，是因为它的键空间是「全校老师」；这里只有一个用户。）
 *
 * 「清除所有数据」由 [DataWiper] 连带清空（见该文件 ⑤f）。
 */
class AcademicCacheStore(ctx: Context) {

    private val dir = File(ctx.applicationContext.filesDir, "academic")
    private val file = File(dir, "cache.json")

    /** @return null = 没存过 / 读坏了 / 版本不认识（都当没有，不猜） */
    fun load(): AcademicCache? {
        if (!file.isFile) return null
        return runCatching { AcademicCache.fromJson(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun save(cache: AcademicCache): Boolean = runCatching {
        dir.mkdirs()
        val tmp = File(dir, "cache.json.tmp")
        tmp.writeText(AcademicCache.toJson(cache), Charsets.UTF_8)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }.getOrDefault(false)

    fun clear(): Boolean = runCatching {
        file.delete()
        dir.listFiles()?.forEach { it.delete() }
        true
    }.getOrDefault(false)

    fun exists(): Boolean = file.isFile

    fun sizeBytes(): Long = if (file.isFile) file.length() else 0L
}
