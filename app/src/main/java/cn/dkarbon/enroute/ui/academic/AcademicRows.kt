package cn.dkarbon.enroute.ui.academic

import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.ScoreItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 考试 / 成绩页的**派生行模型 + 纯函数管线**（2026-11-13）。
 *
 * 为什么单独一个文件（沿用 `ui/feed/FeedRows.kt` 的范式）：分桶、倒计时、学期分组、
 * 均分/加权这些算逻辑如果写在 Composable 里，就只能靠「跑起来看」验证。挪成纯函数后
 * JVM 单测可以直接喂真实 payload 打边界（字段缺失、日期解析不出来、跨周）。
 *
 * 三条纪律：
 *   1. **算不出来就返回 null**（不是 0、不是平均值凑数），界面拿 null 决定「不显示」。
 *   2. 只有真实现场证据才判「不及格」（分数 < 60 或等级写明不及格），其余按未知处理。
 *   3. 不做绩点：URP 这套接口不给绩点算法，自造一个就是编数据。
 */
enum class ExamBucket(val label: String) {
    TODAY("今天"),
    THIS_WEEK("本周"),
    LATER("更远"),
    PAST("已结束"),
}

/** 时间轴的一行；行类型与 Lazy 的 contentType 一一对应 */
sealed interface ExamRow {
    val rowKey: String
    val contentType: String

    data class Header(
        val bucket: ExamBucket,
        val count: Int,
        val collapsible: Boolean,
        val collapsed: Boolean,
    ) : ExamRow {
        override val rowKey: String get() = "h:${bucket.name}"
        override val contentType: String get() = "exam-header"
    }

    /** @param daysAhead 距今天几天（今天=0）；日期解析不出来 = null */
    data class Card(
        val exam: ExamItem,
        val daysAhead: Long?,
        val isNext: Boolean,
    ) : ExamRow {
        // key 必须全局唯一且可存进 Bundle：id 是接口里的 "0"/"1"，会重复，得拼上开始时间
        override val rowKey: String get() = "e:${exam.apiId}|${exam.startAt}|${exam.name}"
        override val contentType: String get() = if (isNext) "exam-card-next" else "exam-card"
    }
}

/** 工具条与统计用；全部字段可从 [ExamItem] 现数，没有一个常量是编的 */
data class ExamStats(
    val total: Int = 0,
    val today: Int = 0,
    val thisWeek: Int = 0,
    val later: Int = 0,
    val past: Int = 0,
    val next: ExamItem? = null,
    val nextDays: Long? = null,
)

object ExamRows {

    /** 渲染顺序：越近越前，已结束永远垫底 */
    val ORDER = listOf(ExamBucket.TODAY, ExamBucket.THIS_WEEK, ExamBucket.LATER, ExamBucket.PAST)

    private val MINUTE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val WEEKDAYS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    // ── 时间解析 ──────────────────────────────────────────

    /** 开考日期；解析不出来 → null（界面显示「日期待定」，不做猜测） */
    fun dateOf(e: ExamItem): LocalDate? {
        val raw = e.date.ifBlank { e.startAt.substringBefore(' ').trim() }
        if (raw.length < 10) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    fun startOf(e: ExamItem): LocalDateTime? {
        runCatching { LocalDateTime.parse(e.startAt, MINUTE_FMT) }.getOrNull()?.let { return it }
        val d = dateOf(e) ?: return null
        if (e.startClock.isBlank()) return null
        return runCatching { LocalDateTime.parse("$d ${e.startClock}", MINUTE_FMT) }.getOrNull()
    }

    fun endOf(e: ExamItem): LocalDateTime? {
        val d = dateOf(e) ?: return null
        if (e.endClock.isBlank()) return null
        return runCatching { LocalDateTime.parse("$d ${e.endClock}", MINUTE_FMT) }.getOrNull()
    }

    /** 这场考试是否已经考完（按结束时刻判；连结束时刻都没有就退回按日期判） */
    fun isFinished(e: ExamItem, today: LocalDate, now: LocalDateTime): Boolean {
        endOf(e)?.let { return !now.isBefore(it) }
        val d = dateOf(e) ?: return false
        return d.isBefore(today)
    }

    // ── 分桶 ──────────────────────────────────────────────

    /**
     * 今天 / 本周 / 更远 / 已结束。
     *
     * 「本周」按**周一为首日**的日历周（中国习惯），不是「未来 7 天」——
     * 后者会把下周一也塞进「本周」，跟用户的周历心智对不上。
     */
    fun bucket(e: ExamItem, today: LocalDate): ExamBucket {
        val d = dateOf(e) ?: return ExamBucket.LATER
        if (d.isBefore(today)) return ExamBucket.PAST
        if (d == today) return ExamBucket.TODAY
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return if (d.isBefore(weekStart.plusDays(7))) ExamBucket.THIS_WEEK else ExamBucket.LATER
    }

    /** 距今天几天（今天 = 0，明天 = 1） */
    fun daysAhead(e: ExamItem, today: LocalDate): Long? {
        val d = dateOf(e) ?: return null
        return ChronoUnit.DAYS.between(today, d)
    }

    /** 倒计时文案；过去 / 未知 → null（界面不显示倒计时，而不是显示「-3 天」） */
    fun countdownLabel(days: Long?): String? = when {
        days == null -> null
        days < 0L -> null
        days == 0L -> "今天"
        days == 1L -> "明天"
        else -> "$days 天后"
    }

    /** 侧轴上的日期：`11-15` */
    fun dayLabel(e: ExamItem): String {
        val d = dateOf(e) ?: return "待定"
        return "%02d-%02d".format(d.monthValue, d.dayOfMonth)
    }

    fun weekdayLabel(e: ExamItem): String {
        val d = dateOf(e) ?: return ""
        return WEEKDAYS[d.dayOfWeek.value - 1]
    }

    /** `08:00-09:40`；缺就空串 */
    fun timeRangeLabel(e: ExamItem): String = when {
        e.startClock.isBlank() && e.endClock.isBlank() -> ""
        e.endClock.isBlank() -> e.startClock
        else -> "${e.startClock}-${e.endClock}"
    }

    /** 时长文案；不知道就是空串 */
    fun durationLabel(e: ExamItem): String =
        if (e.durationMin > 0) "${e.durationMin} 分钟" else ""

    /** 考场一句话：校区 · 楼 · 教室，缺哪段跳哪段，全缺为空串 */
    fun placeLabel(e: ExamItem): String =
        listOf(e.campus, e.building, e.room).filter { it.isNotBlank() }.joinToString(" · ")

    // ── 未来最近一场 ──────────────────────────────────────

    /** 未来最近一场（含正在进行的）；全部考完 = null */
    fun nextUpcoming(exams: List<ExamItem>, today: LocalDate, now: LocalDateTime): ExamItem? =
        exams.filterNot { isFinished(it, today, now) }
            .minByOrNull { startOf(it) ?: LocalDateTime.MAX }

    // ── 管线本体 ──────────────────────────────────────────

    /**
     * 分组 → 排序 → 折叠 → 行列表。
     *
     * @param collapsed 已折叠的桶（UI 用 rememberSaveable 持有）
     */
    fun build(
        exams: List<ExamItem>,
        today: LocalDate,
        now: LocalDateTime,
        collapsed: Set<ExamBucket>,
    ): List<ExamRow> {
        if (exams.isEmpty()) return emptyList()
        val next = nextUpcoming(exams, today, now)
        val byBucket = exams.groupBy { bucket(it, today) }
        val rows = ArrayList<ExamRow>(exams.size + 4)

        for (b in ORDER) {
            val group = byBucket[b] ?: continue
            // 已结束组倒序：最近考完的排最前，别让人先翻到去年
            val sorted = if (b == ExamBucket.PAST) {
                group.sortedWith(compareByDescending<ExamItem> { startOf(it) ?: LocalDateTime.MIN })
            } else {
                group.sortedWith(compareBy<ExamItem> { startOf(it) ?: LocalDateTime.MAX })
            }
            val isCollapsed = b in collapsed
            rows += ExamRow.Header(
                bucket = b,
                count = sorted.size,
                collapsible = b == ExamBucket.LATER || b == ExamBucket.PAST,
                collapsed = isCollapsed,
            )
            if (isCollapsed) continue
            for (e in sorted) {
                rows += ExamRow.Card(
                    exam = e,
                    daysAhead = daysAhead(e, today),
                    isNext = next != null && e === next,
                )
            }
        }
        return rows
    }

    fun stats(exams: List<ExamItem>, today: LocalDate, now: LocalDateTime): ExamStats {
        val byBucket = exams.groupBy { bucket(it, today) }
        val next = nextUpcoming(exams, today, now)
        return ExamStats(
            total = exams.size,
            today = byBucket[ExamBucket.TODAY]?.size ?: 0,
            thisWeek = byBucket[ExamBucket.THIS_WEEK]?.size ?: 0,
            later = byBucket[ExamBucket.LATER]?.size ?: 0,
            past = byBucket[ExamBucket.PAST]?.size ?: 0,
            next = next,
            nextDays = next?.let { daysAhead(it, today) },
        )
    }

    /**
     * 工具条左侧那句话（页内不再写「考试」这种标题字样，改由它承载「有几场 / 下一场何时」）。
     *
     * @param total 传入的总场次（筛选后要显示筛选后的数，所以不让它自己数）
     */
    fun toolbarLabel(stats: ExamStats, total: Int = stats.total): String {
        val next = stats.next
        return when {
            stats.total == 0 -> "0 场"
            next == null -> "$total 场 · 已全部考完"
            else -> {
                val d = dateOf(next)
                val md = if (d != null) "%02d-%02d".format(d.monthValue, d.dayOfMonth) else "日期待定"
                val cd = countdownLabel(stats.nextDays) ?: "日期待定"
                "$total 场 · 下一场 $md · $cd"
            }
        }
    }
}

// ── 成绩 ──────────────────────────────────────────────────

/**
 * 一个学期的统计。
 *
 * 三个数都可能是 null，因为**接口里这三个数各自会缺**：
 *   · [credits] 有任何一门课没有学分 → null
 *   · [average] 有任何一门课是等级制（无百分制分数）→ null（算术平均会把「优秀」当 0）
 *   · [weighted] 在上面的基础上再要求学分全齐 → null
 * 界面拿到 null 就不显示这一项 —— 宁可不给，不给假数。
 */
data class TermStats(
    val count: Int,
    val credits: Double?,
    val average: Double?,
    val weighted: Double?,
    val failed: Int,
)

sealed interface ScoreRow {
    val rowKey: String
    val contentType: String

    data class Header(
        val term: String,
        val stats: TermStats,
        val collapsed: Boolean,
    ) : ScoreRow {
        override val rowKey: String get() = "t:$term"
        override val contentType: String get() = "score-term"
    }

    data class Item(val score: ScoreItem) : ScoreRow {
        override val rowKey: String
            get() = "s:${score.code}|${score.classNo}|${score.term}|${score.name}"
        override val contentType: String get() = "score-item"
    }
}

object ScoreRows {

    private val YEAR_RE = Regex("""(\d{4})\s*[-—]\s*(\d{4})""")

    /**
     * 学期排序键：(学年起始年, 季次序)。秋 = 1、春 = 2、夏 = 3。
     * 秋在学年内排在春前面，所以倒序排列时春先出现 —— 这是「最近刚出的成绩在最上面」。
     */
    fun termSortKey(term: String): Pair<Int, Int>? {
        val year = YEAR_RE.find(term)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val season = when {
            term.contains("秋") -> 1
            term.contains("春") -> 2
            term.contains("夏") -> 3
            term.endsWith("-1") || term.contains("-1-") -> 1
            term.endsWith("-2") || term.contains("-2-") -> 2
            else -> 0
        }
        return year to season
    }

    /** 学期名倒序（新 → 旧）；解析不出学年的排最后，且保持原相对顺序 */
    fun sortTerms(terms: Collection<String>): List<String> =
        terms.distinct().sortedWith(
            compareByDescending<String> { termSortKey(it)?.first ?: Int.MIN_VALUE }
                .thenByDescending { termSortKey(it)?.second ?: 0 },
        )

    fun isFailed(s: ScoreItem): Boolean = s.failed

    /**
     * 单学期统计（规则见 [TermStats] 的 KDoc）。
     * 空列表返回全 0 / null，调用方自己判空。
     */
    fun termStats(items: List<ScoreItem>): TermStats {
        val n = items.size
        val creditsOk = items.mapNotNull { it.creditValue }
        val credits = if (n > 0 && creditsOk.size == n) creditsOk.sum() else null

        val scored = items.mapNotNull { it.scoreValue }
        // 只有每一门都是百分制，平均分才有意义
        val average = if (n > 0 && scored.size == n) scored.average() else null

        val weighted = if (average != null && credits != null && credits > 0.0) {
            items.sumOf { (it.scoreValue ?: 0.0) * (it.creditValue ?: 0.0) } / credits
        } else {
            null
        }

        return TermStats(
            count = n,
            credits = credits,
            average = average,
            weighted = weighted,
            failed = items.count { it.failed },
        )
    }

    /**
     * 学期分组 → 排序 → 折叠。
     *
     * @param onlyFailed 只看明确不及格（工具栏的筛选开关）
     * @param collapsed 已折叠的学期名
     */
    fun build(
        scores: List<ScoreItem>,
        onlyFailed: Boolean,
        collapsed: Set<String>,
    ): List<ScoreRow> {
        val filtered = if (onlyFailed) scores.filter { it.failed } else scores
        if (filtered.isEmpty()) return emptyList()
        val byTerm = filtered.groupBy { it.term.ifBlank { "学期未标注" } }
        val rows = ArrayList<ScoreRow>(filtered.size + byTerm.size)
        for (term in sortTerms(byTerm.keys)) {
            val list = byTerm[term] ?: continue
            val stats = termStats(list)
            val isCollapsed = term in collapsed
            rows += ScoreRow.Header(term = term, stats = stats, collapsed = isCollapsed)
            if (isCollapsed) continue
            for (s in list) rows += ScoreRow.Item(s)
        }
        return rows
    }

    /** 工具条左侧：真实门数 / 学期数 / 不及格数，全部现数 */
    fun toolbarLabel(scores: List<ScoreItem>, shown: List<ScoreItem>, onlyFailed: Boolean): String {
        if (scores.isEmpty()) return "0 门"
        val terms = scores.map { it.term.ifBlank { "学期未标注" } }.distinct().size
        val failed = scores.count { it.failed }
        return when {
            onlyFailed -> "只看不及格 · ${shown.size} 门"
            failed > 0 -> "${scores.size} 门 · $terms 学期 · $failed 门不及格"
            else -> "${scores.size} 门 · $terms 学期"
        }
    }

    /** 均分保留 1 位；null 原样返回 null（界面据此不显示）。定点用 Locale.US，避免某些区域逗号小数 */
    fun fmt(v: Double?): String? = v?.let { String.format(Locale.US, "%.1f", it) }
}
