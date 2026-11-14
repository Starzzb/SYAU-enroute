package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.ui.feed.CompGroups
import cn.dkarbon.enroute.ui.feed.DeadlineTone
import cn.dkarbon.enroute.ui.feed.FeedRow
import cn.dkarbon.enroute.ui.feed.FeedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 派生行管线（FeedRows.build）的纯逻辑测试 —— 评审规格 §6/§8 的验收点。
 * 不起 Android、不起 Compose：过滤/排序/分组/折叠/窗口全是纯函数。
 */
class FeedRowsTest {

    private val today = LocalDate.of(2026, 11, 13)

    private fun item(
        key: String,
        category: String = "教务",
        published: String = "2026-11-10",
        deadline: String = "",
        read: Boolean = false,
        starred: Boolean = false,
        title: String = "关于某某事项的通知 $key",
    ) = FeedItem(
        key = key,
        sourceKey = "jwc",
        sourceName = "教务通知",
        category = category,
        title = title,
        publishedAt = published,
        deadline = deadline,
        readAt = if (read) 1L else null,
        starred = starred,
        firstSeenAt = key.hashCode().toLong(),
    )

    private val compItems = listOf(
        item("c1", "竞赛", deadline = "2026-11-15"),  // 剩2天 → 即将截止
        item("c2", "竞赛", deadline = "2026-11-13"),  // 今天截止
        item("c3", "竞赛", deadline = "2026-12-30"),  // 报名中
        item("c4", "竞赛", deadline = ""),            // 时间待定
        item("c5", "竞赛", deadline = "2026-11-01"),  // 已截止
        item("c6", "竞赛", deadline = "2026-11-10"),  // 已截止（更近）
    )

    @Test
    fun `竞赛分组顺序与组内排序`() {
        val (rows, total) = FeedRows.build(
            items = compItems, category = "竞赛", query = "", onlyUnread = false,
            starredOnly = false, compact = false, collapsed = emptySet(), window = 100, today = today,
        )
        assertEquals(6, total)
        val headers = rows.filterIsInstance<FeedRow.Header>().map { it.label }
        assertEquals(listOf(CompGroups.URGENT, CompGroups.OPEN, CompGroups.UNKNOWN, CompGroups.CLOSED), headers)

        // 即将截止组内：今天截止(c2) 在 剩2天(c1) 前；
        // 时间待定按 publishedAt 倒序、同日按 firstSeenAt 倒序（与日期桶同规则）
        val cards = rows.filterIsInstance<FeedRow.Card>().map { it.item.key }
        val unknownOrder = compItems.filter { it.deadline.isBlank() }
            .sortedWith(compareByDescending<FeedItem> { it.publishedAt }.thenByDescending { it.firstSeenAt })
            .map { it.key }
        assertEquals(listOf("c2", "c1", "c3") + unknownOrder + listOf("c6", "c5"), cards)
    }

    @Test
    fun `已截止组默认折叠且不计入窗口`() {
        val (rows, total) = FeedRows.build(
            items = compItems, category = "竞赛", query = "", onlyUnread = false,
            starredOnly = false, compact = false, collapsed = setOf(CompGroups.CLOSED), window = 100, today = today,
        )
        assertEquals(6, total)
        val cards = rows.filterIsInstance<FeedRow.Card>().map { it.item.key }
        val unknownOrder = compItems.filter { it.deadline.isBlank() }
            .sortedWith(compareByDescending<FeedItem> { it.publishedAt }.thenByDescending { it.firstSeenAt })
            .map { it.key }
        assertEquals(listOf("c2", "c1", "c3") + unknownOrder, cards)
        val closedHeader = rows.filterIsInstance<FeedRow.Header>().first { it.label == CompGroups.CLOSED }
        assertTrue(closedHeader.collapsed)
        assertEquals(2, closedHeader.count)
        // 分页脚 shown=4（折叠组不计）但 total=6
        val footer = rows.filterIsInstance<FeedRow.Footer>().single()
        assertEquals(4, footer.shown)
        assertEquals(6, footer.total)
    }

    @Test
    fun `渲染窗口截断且分页脚数字真实`() {
        val many = (1..50).map { item("n$it", published = "2026-11-%02d".format((it % 12) + 1)) }
        val (rows, total) = FeedRows.build(
            items = many, category = "全部", query = "", onlyUnread = false,
            starredOnly = false, compact = false, collapsed = emptySet(), window = 30, today = today,
        )
        assertEquals(50, total)
        assertEquals(30, rows.filterIsInstance<FeedRow.Card>().size)
        val footer = rows.filterIsInstance<FeedRow.Footer>().single()
        assertEquals(30, footer.shown)
        assertEquals(50, footer.total)
    }

    @Test
    fun `筛选是 AND 关系且作用于分组前`() {
        val items = compItems + listOf(
            item("j1", "教务", read = true),
            item("j2", "教务", starred = true),
        )
        val (rows, total) = FeedRows.build(
            items = items, category = "全部", query = "", onlyUnread = true,
            starredOnly = true, compact = false, collapsed = emptySet(), window = 100, today = today,
        )
        // 未读 AND 收藏：只有 j2
        assertEquals(1, total)
        assertEquals("j2", rows.filterIsInstance<FeedRow.Card>().single().item.key)
    }

    @Test
    fun `关键词多词全部命中`() {
        val items = listOf(
            item("a", title = "关于举办创新创业大赛的通知"),
            item("b", title = "关于选课系统维护的通知"),
        )
        val (_, total) = FeedRows.build(
            items = items, category = "全部", query = "大赛 创新", onlyUnread = false,
            starredOnly = false, compact = false, collapsed = emptySet(), window = 100, today = today,
        )
        assertEquals(1, total)
    }

    @Test
    fun `非竞赛频道按日期桶分组`() {
        val items = listOf(
            item("t1", published = "2026-11-13"),
            item("t2", published = "2026-11-12"),
            item("t3", published = "2026-11-09"),
            item("t4", published = "2026-10-01"),
            item("t5", published = ""),
        )
        val (rows, _) = FeedRows.build(
            items = items, category = "全部", query = "", onlyUnread = false,
            starredOnly = false, compact = false, collapsed = emptySet(), window = 100, today = today,
        )
        val headers = rows.filterIsInstance<FeedRow.Header>().map { it.label }
        assertEquals(listOf("今天", "昨天", "本周", "更早"), headers)
        // 日期桶头不可折叠（只有竞赛组可折叠）
        assertTrue(rows.filterIsInstance<FeedRow.Header>().none { it.collapsible })
    }

    @Test
    fun `倒计时色阶五档`() {
        fun label(deadline: String) = FeedRows.deadlineLabel(item("x", "竞赛", deadline = deadline), today)
        assertEquals("今天截止", label("2026-11-13")!!.text)
        assertEquals(DeadlineTone.HOT, label("2026-11-13")!!.tone)
        assertEquals(DeadlineTone.HOT, label("2026-11-16")!!.tone)   // 剩3天
        assertEquals(DeadlineTone.WARM, label("2026-11-17")!!.tone)  // 剩4天
        assertEquals(DeadlineTone.WARM, label("2026-11-20")!!.tone)  // 剩7天
        assertEquals(DeadlineTone.DEAD, label("2026-11-21")!!.tone)  // 剩8天 → 常规
        assertEquals("已截止 11-01", label("2026-11-01")!!.text)
        assertEquals(null, label(""))
        assertEquals(null, label("不是日期"))
    }

    @Test
    fun `空结果返回空行列表`() {
        val (rows, total) = FeedRows.build(
            items = compItems, category = "新闻", query = "", onlyUnread = false,
            starredOnly = false, compact = false, collapsed = emptySet(), window = 30, today = today,
        )
        assertEquals(0, total)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `行 key 全局唯一且 contentType 区分密度`() {
        val items = (1..10).map { item("k$it") }
        val (rows, _) = FeedRows.build(
            items = items, category = "全部", query = "", onlyUnread = false,
            starredOnly = false, compact = true, collapsed = emptySet(), window = 100, today = today,
        )
        assertEquals(rows.map { it.rowKey }.toSet().size, rows.size)
        assertTrue(rows.filterIsInstance<FeedRow.Card>().all { it.contentType == "card-compact" })
    }

    @Test
    fun `分页触发判定_还有更多且接近尾部才触发`() {
        // 窗口 30、总 45：滚到第 28 行（33 行含头脚）→ 触发
        assertTrue(FeedRows.shouldLoadMore(lastVisibleIndex = 28, rowCount = 33, shown = 30, total = 45))
        // 刚进页面只看到第 3 行 → 不触发
        assertTrue(!FeedRows.shouldLoadMore(lastVisibleIndex = 3, rowCount = 33, shown = 30, total = 45))
        // 已到底（shown==total）→ 永不触发，哪怕页脚可见
        assertTrue(!FeedRows.shouldLoadMore(lastVisibleIndex = 44, rowCount = 47, shown = 45, total = 45))
        // 短列表全可见（首帧布局后 lastVisible= rowCount-1）→ 立即触发补齐
        assertTrue(FeedRows.shouldLoadMore(lastVisibleIndex = 12, rowCount = 13, shown = 10, total = 45))
        // 首帧布局前（-1）→ 不触发
        assertTrue(!FeedRows.shouldLoadMore(lastVisibleIndex = -1, rowCount = 33, shown = 30, total = 45))
    }
}