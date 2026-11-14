package cn.dkarbon.enroute.ui.feed

import cn.dkarbon.enroute.data.feed.FeedItem
import java.time.LocalDate

/**
 * 资讯列表的**派生行模型 + 纯函数管线**（2026-11-13 评审规格 §5.5 / §6 / §8）。
 *
 * 为什么单独一个文件：过滤 → 排序 → 分组 → 折叠 → 窗口切片这条管线
 * 原来散在 FeedScreen 的 pager 组合体里（每次重组全量重算 800 条 ×3 页）。
 * 现在上移到 VM 调用、纯函数实现 —— JVM 单测可以直接打真实数据，
 * 不用起 Android，也不用 Compose 测试规则。
 *
 * 行类型与 contentType 一一对应（Lazy 回收按 type 复用槽位）。
 */
sealed interface FeedRow {
    /** 分组头（sticky 吸顶；竞赛组可折叠） */
    data class Header(
        val label: String,
        val count: Int,
        val collapsible: Boolean,
        val collapsed: Boolean,
    ) : FeedRow

    data class Card(val item: FeedItem, val compact: Boolean) : FeedRow

    /** 分页脚：shown/total 是真实数字 */
    data class Footer(val shown: Int, val total: Int) : FeedRow

    val rowKey: String
        get() = when (this) {
            is Header -> "h:$label"
            is Card -> item.key
            is Footer -> "footer"
        }

    val contentType: String
        get() = when (this) {
            is Header -> "header"
            is Card -> if (compact) "card-compact" else "card-standard"
            is Footer -> "footer"
        }
}

/** 竞赛分组名（顺序即渲染顺序，规格 §8.1） */
object CompGroups {
    const val URGENT = "即将截止"
    const val OPEN = "报名中"
    const val UNKNOWN = "时间待定"
    const val CLOSED = "已截止"
    val ORDER = listOf(URGENT, OPEN, UNKNOWN, CLOSED)
}

object FeedRows {

    /** 每页渲染窗口：初始 30，步进 30（本地渲染分页，无网络） */
    const val PAGE = 30

    /** 关键词匹配：标题/摘要/来源，空格分词全部命中（与 FeedUiState.matchQuery 同规则） */
    fun matchQuery(item: FeedItem, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val hay = item.title + " " + item.summary + " " + item.sourceName
        return q.split(' ', '　').filter { it.isNotBlank() }.all { hay.contains(it, ignoreCase = true) }
    }

    /** 距截止天数；deadline 空或解析失败返回 null（不猜） */
    fun daysLeft(deadline: String, today: LocalDate): Long? = try {
        java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(deadline))
    } catch (_: Exception) {
        null
    }

    /** 竞赛分组判定（规格 §8.1）：d≤7 即将截止 / d>7 报名中 / 无日期 待定 / d<0 已截止 */
    fun compGroup(item: FeedItem, today: LocalDate): String {
        val d = daysLeft(item.deadline, today) ?: return CompGroups.UNKNOWN
        return when {
            d < 0 -> CompGroups.CLOSED
            d <= 7 -> CompGroups.URGENT
            else -> CompGroups.OPEN
        }
    }

    /** 日期分桶（非竞赛频道）：今天 / 昨天 / 本周 / 更早 */
    fun dateBucket(item: FeedItem, today: LocalDate): String {
        if (item.publishedAt.isBlank()) return "更早"
        return try {
            val d = LocalDate.parse(item.publishedAt)
            when {
                d == today -> "今天"
                d == today.minusDays(1) -> "昨天"
                d.isAfter(today.minusDays(7)) -> "本周"
                else -> "更早"
            }
        } catch (_: Exception) {
            "更早"
        }
    }

    /**
     * 管线本体：过滤 → 排序 → 分组 → 折叠 → 窗口。
     *
     * @param category 「全部」或频道名
     * @param collapsed 已折叠的组名集合（UI rememberSaveable 持有）
     * @param window 渲染窗口（可见条目上限；折叠组内条目不计入）
     * @return 行列表 + 过滤后总条数（分页脚用）
     */
    fun build(
        items: List<FeedItem>,
        category: String,
        query: String,
        onlyUnread: Boolean,
        starredOnly: Boolean,
        compact: Boolean,
        collapsed: Set<String>,
        window: Int,
        today: LocalDate,
    ): Pair<List<FeedRow>, Int> {
        val filtered = items.filter {
            (category == "全部" || it.category == category) &&
                matchQuery(it, query) &&
                (!onlyUnread || it.readAt == null) &&
                (!starredOnly || it.starred)
        }
        if (filtered.isEmpty()) return emptyList<FeedRow>() to 0

        val isComp = category == "竞赛"
        // 分组：竞赛按截止状态四组；其它按日期桶。LinkedHashMap 保序。
        val groups: Map<String, List<FeedItem>> = if (isComp) {
            val byGroup = filtered.groupBy { compGroup(it, today) }
            CompGroups.ORDER.mapNotNull { g ->
                byGroup[g]?.let { list ->
                    g to when (g) {
                        // 已截止：最近截止的排最前（修旧版正序 bug）
                        CompGroups.CLOSED -> list.sortedWith(
                            compareByDescending<FeedItem> { it.deadline }.thenByDescending { it.publishedAt },
                        )
                        CompGroups.UNKNOWN -> list.sortedWith(
                            compareByDescending<FeedItem> { it.publishedAt }.thenByDescending { it.firstSeenAt },
                        )
                        else -> list.sortedWith(
                            compareBy<FeedItem> { it.deadline }.thenByDescending { it.publishedAt },
                        )
                    }
                }
            }.toMap()
        } else {
            val order = listOf("今天", "昨天", "本周", "更早")
            val byBucket = filtered.groupBy { dateBucket(it, today) }
            order.mapNotNull { b ->
                byBucket[b]?.let { list ->
                    b to list.sortedWith(
                        compareByDescending<FeedItem> { it.publishedAt }.thenByDescending { it.firstSeenAt },
                    )
                }
            }.toMap()
        }

        val rows = mutableListOf<FeedRow>()
        var shown = 0
        for ((label, list) in groups) {
            val isCollapsed = label in collapsed
            rows += FeedRow.Header(
                label = label,
                count = list.size,
                collapsible = isComp,
                collapsed = isCollapsed,
            )
            if (isCollapsed) continue
            for (item in list) {
                if (shown >= window) break
                rows += FeedRow.Card(item, compact)
                shown++
            }
            if (shown >= window) break
        }
        rows += FeedRow.Footer(shown = shown, total = filtered.size)
        return rows to filtered.size
    }

    /**
     * 分页触发判定（评审 B13）：纯函数，UI 层只负责喂可见索引。
     * 「还有更多」纳入判定 —— 到底后不再空调；短列表全可见时首帧布局后即触发。
     */
    fun shouldLoadMore(lastVisibleIndex: Int, rowCount: Int, shown: Int, total: Int): Boolean =
        shown < total && lastVisibleIndex >= rowCount - 5

    /**
     * 倒计时文案 + 色阶档位（规格 §8.3）。
     * 只有解析出真实日期才返回；解析不到 = null（由「时间待定」组头表达）。
     */
    fun deadlineLabel(item: FeedItem, today: LocalDate): DeadlineLabel? {
        if (item.deadline.isBlank()) return null
        val d = daysLeft(item.deadline, today) ?: return null
        return when {
            d < 0 -> DeadlineLabel("已截止 ${item.deadline.substring(5)}", DeadlineTone.DEAD)
            d == 0L -> DeadlineLabel("今天截止", DeadlineTone.HOT)
            d <= 3 -> DeadlineLabel("剩 $d 天", DeadlineTone.HOT)
            d <= 7 -> DeadlineLabel("剩 $d 天", DeadlineTone.WARM)
            else -> DeadlineLabel("截止 ${item.deadline.substring(5)}", DeadlineTone.DEAD)
        }
    }
}

enum class DeadlineTone { HOT, WARM, DEAD }

data class DeadlineLabel(val text: String, val tone: DeadlineTone)