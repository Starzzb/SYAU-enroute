package cn.dkarbon.enroute.ui.user

import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.ui.home.HomeModel

/**
 * 「我的」页的数据派生（票 16）—— **纯函数，先有单测再改 UI**（票面边界就是这么要求的）。
 *
 * 三条纪律都来自评审与 AGENTS 第 1 条：
 * 1. **未知 ≠ 0**：课表没同步时写 `—`，不写「0 门课」（A2）；绩点缺失写「暂缺」，不写 0.00；
 * 2. **徽标只放状态**：`相对位置`／专业名／搜索结果位数都不是状态 → 一律留空（A8）；
 * 3. **折叠摘要同样诚实**：三种空（没登录 / 没同步 / 真的 0）不能压成一个符号。
 */
object MyPageModel {

    /** 未知值占位：所有「没数据」的地方都用它，不用 0、不用空格 */
    const val UNKNOWN = "—"

    // ── 本机组（数据来源 = 本机存储）──────────────────────

    /**
     * 课表门数（A2）。`null` = 本机还没同步过课表 → `—`；
     * 真的 0 门（同步过但一节课没有）才写「0 门课」。
     */
    fun courseCountLabel(count: Int?): String = when {
        count == null -> UNKNOWN
        count == 0 -> "0 门课"
        else -> "$count 门课"
    }

    /** 周次（同组「周次」行原本就是对的写法，这里收成一处） */
    fun weekLabel(week: Int?): String = if (week != null && week > 0) "第 $week 周" else "未校准"

    /**
     * 本机折叠头摘要：`课表 10 门 · 第 12 周`。
     *
     * 未知的部分各自写清楚（`课表 —` / `未校准`），**不合并成一个 `—`** ——
     * 否则用户看不出是「没同步」还是「没校准」。
     */
    fun localSummary(courseCount: Int?, week: Int?): String {
        val c = if (courseCount == null) UNKNOWN else "$courseCount 门"
        return "课表 $c · ${weekLabel(week)}"
    }

    // ── 教务组（数据来源 = URP，需要登录态）────────────────

    /** 培养方案磁贴：`64%`；需修学分为 0 时给 `—`（不显示 0%） */
    fun planPercentLabel(credit: CreditProgress): String =
        credit.percent?.let { "$it%" } ?: UNKNOWN

    /** 培养方案磁贴副行：`还差 42.5 学分` / `进度暂缺` */
    fun planRemainLabel(credit: CreditProgress): String {
        val remain = HomeModel.creditRemaining(credit) ?: return "进度暂缺"
        return "还差 ${HomeModel.trimNum(remain)} 学分"
    }

    /** 加权绩点磁贴主值：`3.42` / `暂缺`（**绝不给 0.00**） */
    fun gpaTileLabel(stats: ScoreStats): String =
        stats.weightedGpa?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: UNKNOWN

    /** 加权绩点磁贴副行：`24 门 · 14 学期` 这类解释，空则空 */
    fun gpaSubLabel(stats: ScoreStats): String = when {
        stats.total <= 0 -> "暂无成绩"
        stats.failed > 0 -> "${stats.total} 门 · ${stats.failed} 门未过"
        else -> "${stats.total} 门已过"
    }

    /** 空闲教室磁贴：`第 5 节 · 47 间`；没查过 → `—`（不写 `第 0 节 · 0 间`） */
    fun freeRoomsTile(session: Int, roomCount: Int): String =
        if (roomCount > 0 && session in 1..6) "第 $session 节 · $roomCount 间" else UNKNOWN

    // ── 教务组的状态前提（A7：常驻，不再条件插入）────────────

    /**
     * 教务组的状态前提行文案。**永远存在**（A7：续期成功那一刻不能让下方九行整体上移）。
     *
     * Active 时也留着，只是变成「已登录 · 重新登录」这种低干扰说法 ——
     * 它同时满足门禁 B2「每组第 1 行是该组的状态前提」。
     */
    fun sessionRowLabel(state: SessionState): String = when (state) {
        SessionState.None -> "未登录 · 点此登录教务"
        SessionState.Active -> "已登录（会话有效）· 重新登录"
        SessionState.Expired -> "会话已过期 · 点此续期"
    }

    /** 组头右侧的**短**状态（空间只有几个字，不能放整句） */
    fun sessionPremiseLabel(state: SessionState): String = when (state) {
        SessionState.None -> "未登录"
        SessionState.Active -> "已登录"
        SessionState.Expired -> "会话已过期"
    }

    /** 该行现在是不是「用户该动手」（未登录 / 过期）—— 决定用主色还是弱色 */
    fun sessionRowNeedsAction(state: SessionState): Boolean = state != SessionState.Active

    // ── 徽标列（A8：只允许真实状态或空）──────────────────

    /**
     * 教务组各行的徽标。**只有「真实的、会变的、有意义的量」才给值**，其余空串。
     *
     * 被评审判为噪声而删掉的三个：`相对位置`（是属性不是状态）、
     * 专业名（是上下文不是状态）、`N 位`（是上次搜索的结果数，一清空就变，没信息量）。
     */
    fun examBadge(count: Int, loading: Boolean): String = when {
        loading -> "读取中"
        count <= 0 -> ""       // 一场都没有 → 留空（不是 "0 场"）
        else -> "$count 场"
    }

    fun scoreBadge(count: Int, loading: Boolean): String = when {
        loading -> "读取中"
        count <= 0 -> ""
        else -> "$count 条"
    }

    /** 课程大纲：索引里有几门（= 本机已备的资产条目数，是真实状态） */
    fun syllabusBadge(indexSize: Int): String = if (indexSize > 0) "$indexSize 门" else ""

    /** 历年课表：当前查看的学期；没看过 → 空 */
    fun historyBadge(termLabel: String): String = termLabel.trim()

    /** 校园地图：**留空**（原来的「相对位置」是属性不是状态） */
    fun mapBadge(): String = ""

    /** 查教师：**留空**（原来的「N 位」是上次搜索结果数） */
    fun teacherBadge(): String = ""
}
