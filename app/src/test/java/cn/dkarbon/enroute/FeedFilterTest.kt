package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.ui.feed.FeedUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资讯搜索 + 筛选的纯逻辑测试（2026-11-11）。
 *
 * 这段逻辑在 ViewModel 里，但全是纯函数式的过滤，所以在 JVM 上直接测最快。
 * 重点防两类回归：
 *   1. **关键词拆词**：用户会输入「竞赛 报名」这种多词，必须全部命中才算
 *   2. **筛选叠加**：频道 + 关键词 + 只看未读 三层是「与」关系，不能互相覆盖
 */
class FeedFilterTest {

    private fun item(
        title: String,
        summary: String = "",
        category: String = "教务",
        source: String = "教务通知",
        read: Boolean = false,
    ) = FeedItem(
        key = title,
        sourceKey = "jwc",
        sourceName = source,
        category = category,
        title = title,
        summary = summary,
        readAt = if (read) 1L else null,
    )

    private val sample = listOf(
        item("关于举办2026年大学生创新创业大赛的通知", "各学院：现将有关事项通知如下", category = "竞赛"),
        item("关于开展下学期选课工作的通知", "请同学们按时选课", category = "教务"),
        item("关于选课系统维护的通知", "维护时间为本周末", category = "教务", read = true),
        item("沈阳农业大学2026年招聘会安排", "共有115家单位参会", category = "就业", source = "就业信息网"),
    )

    @Test
    fun `空关键词返回全部`() {
        val ui = FeedUiState(items = sample)
        assertTrue(sample.all { ui.matchQuery(it) })
    }

    @Test
    fun `单词命中标题`() {
        val ui = FeedUiState(items = sample, query = "选课")
        val hit = sample.filter { ui.matchQuery(it) }
        assertTrue("应该命中 2 条选课相关", hit.size == 2)
    }

    @Test
    fun `命中摘要也算`() {
        val ui = FeedUiState(items = sample, query = "115")
        assertTrue(sample.filter { ui.matchQuery(it) }.single().category == "就业")
    }

    @Test
    fun `多关键词必须全部命中`() {
        val ui = FeedUiState(items = sample, query = "选课 维护")
        val hit = sample.filter { ui.matchQuery(it) }
        assertTrue("只有「选课系统维护」同时含两个词", hit.size == 1)
        assertTrue(hit.single().title.contains("维护"))
    }

    @Test
    fun `只看未读会筛掉已读`() {
        val ui = FeedUiState(items = sample, onlyUnread = true)
        assertTrue("已读那条要被筛掉", ui.visible.none { it.readAt != null })
        assertTrue(ui.visible.size == 3)
    }

    @Test
    fun `频道与关键词是叠加不是覆盖`() {
        val ui = FeedUiState(items = sample, filter = "教务", query = "选课")
        val v = ui.visible
        assertTrue("只剩教务频道的选课相关", v.size == 2)
        assertTrue("不能混进竞赛条目", v.none { it.category == "竞赛" })

        val narrow = ui.copy(query = "选课", onlyUnread = true)
        assertTrue("再叠未读只剩 1 条", narrow.visible.size == 1)
    }

    @Test
    fun `清空筛选后恢复全量`() {
        val dirty = FeedUiState(items = sample, query = "不存在的东西", onlyUnread = true)
        assertTrue(dirty.visible.isEmpty())
        assertTrue(dirty.hasFilter)

        val clean = dirty.copy(query = "", onlyUnread = false)
        assertTrue(clean.visible.size == sample.size)
        assertFalse(clean.hasFilter)
    }
}
