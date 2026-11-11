package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedParser
import cn.dkarbon.enroute.data.feed.FeedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「通用解析族」的回归测试（2026-11-11 新增）。
 *
 * 样本是**真实页面存下来的**（`src/test/resources/feed/` 下的 html，2026-11-11 抓取）：
 *   · `lnjyt_list.html`    辽宁省教育厅 最新通知
 *   · `tzb_list.html`      挑战杯「挑战杯动态」
 *   · `job_list.html`      沈农学生创新创业与就业指导处 通知公告（VSB 老解析器负责）
 *   · `lnjyt_article.html` 省教育厅一篇竞赛通知的详情页
 *   · `tzb_article.html`   挑战杯一篇动态的详情页
 *
 * 为什么不用手写 HTML 片段：通用族的锚点是「像标题的链接 + 附近日期」，
 * 它**必须**跟真实页面里的导航/页脚/版权信息一起跑，才知道筛不筛得干净。
 */
class GenericFeedParserTest {

    private fun html(name: String): String =
        javaClass.getResourceAsStream("/feed/$name")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val lnjyt = FeedSources.byKey("lnjyt")!!
    private val tzb = FeedSources.byKey("tzb")!!
    private val job = FeedSources.byKey("job")!!

    // ── 列表 ──────────────────────────────────────────────

    @Test
    fun `省教育厅只留竞赛相关标题`() {
        val items = FeedParser.parseListGeneric(html("lnjyt_list.html"), lnjyt.listUrl, lnjyt)
        // 这条断言是为了防「主键塌缩」：曾经所有条目共用 /index.shtml 当键，解析出来只剩 1 条
        assertTrue("只解析出 ${items.size} 条，主键可能又塌缩了", items.size >= 3)
        assertTrue("主键有重复", items.map { it.key }.toSet().size == items.size)
        // 白名单：标题必须命中 source.titleInclude
        items.forEach { item ->
            assertTrue(
                "混进了非竞赛通知：${item.title}",
                lnjyt.titleInclude.any { item.title.contains(it) },
            )
        }
        // 「教师节」「信息化交流活动」这类应该被筛掉
        assertTrue(items.none { it.title.contains("教师节") })
    }

    @Test
    fun `省教育厅日期与链接都正确`() {
        val items = FeedParser.parseListGeneric(html("lnjyt_list.html"), lnjyt.listUrl, lnjyt)
        items.forEach { item ->
            assertTrue("日期格式不是 yyyy-MM-dd：${item.publishedAt}", Regex("""\d{4}-\d{2}-\d{2}""").matches(item.publishedAt))
            assertTrue("链接没转绝对：${item.url}", item.url.startsWith("https://jyt.ln.gov.cn/"))
            assertTrue("标题太短：${item.title}", item.title.length >= 8)
        }
        // 列表按日期倒序
        val dates = items.map { it.publishedAt }
        assertEquals("没有按日期倒序", dates.sortedDescending(), dates)
    }

    @Test
    fun `挑战杯列表能解析出条目且日期是点分隔`() {
        val items = FeedParser.parseListGeneric(html("tzb_list.html"), tzb.listUrl, tzb)
        assertTrue("挑战杯解析出 0 条", items.isNotEmpty())
        assertTrue(
            "没抓到「揭榜挂帅」那条（说明点了名的条目漏了）",
            items.any { it.title.contains("揭榜挂帅") },
        )
        items.forEach {
            assertTrue("日期没归一：${it.publishedAt}", Regex("""\d{4}-\d{2}-\d{2}""").matches(it.publishedAt))
            assertTrue("链接没转绝对：${it.url}", it.url.startsWith("https://") && it.url.contains("tiaozhanbei.net"))
        }
        // 导航里的「注册 / 首页 / 了解挑战杯」不该进来
        assertTrue(items.none { it.title == "注册" || it.title == "首页" })
    }

    @Test
    fun `校内创新创业处走 VSB 解析器也能出条目`() {
        val items = FeedParser.parseList(job.listUrl.let { html("job_list.html") }, job.listUrl, job)
        assertTrue("VSB 解析器在 job 站上失效了", items.size >= 5)
        assertTrue(
            "没抓到创新大赛那条",
            items.any { it.title.contains("创新大赛") || it.title.contains("职业规划大赛") },
        )
        items.forEach { assertEquals("竞赛", it.category) }
    }

    @Test
    fun `校内竞赛成果栏目也能解析`() {
        val cxcy = FeedSources.byKey("cxcy")!!
        val items = FeedParser.parseList(html("cxcy_list.html"), cxcy.listUrl, cxcy)
        assertTrue("校内竞赛栏目解析出 0 条", items.isNotEmpty())
        // 这一栏是获奖新闻（「我校在…获…奖」），标题形态很稳定
        assertTrue("标题形态不对", items.any { it.title.contains("获") || it.title.contains("大赛") })
        items.forEach {
            assertEquals("竞赛", it.category)
            assertTrue("日期缺失：${it.title}", it.publishedAt.length == 10)
        }
    }

    // ── 就业信息网 ────────────────────────────────────────

    @Test
    fun `就业信息网只收文章页且带日期`() {
        val jy = FeedSources.byKey("jy")!!
        val items = FeedParser.parseListGeneric(html("jobsyau_list.html"), jy.listUrl, jy)
        assertTrue("就业信息网解析出 0 条", items.size >= 20)
        items.forEach {
            assertTrue("混进了非文章链接：${it.url}", it.url.contains("/news/view/aid/"))
            assertTrue("日期缺失：${it.title}", Regex("""\d{4}-\d{2}-\d{2}""").matches(it.publishedAt))
            assertEquals("就业", it.category)
        }
    }

    // ── 竞赛管理平台 ──────────────────────────────────────

    @Test
    fun `竞赛平台列表能拿到 id 和标题`() {
        val cxcycomp = FeedSources.byKey("cxcycomp")!!
        val items = FeedParser.parseListCxcy(html("cxcy_comp_list.html"), cxcycomp.listUrl, cxcycomp)
        assertTrue("竞赛平台列表解析出 0 条", items.size >= 5)
        items.forEach {
            assertTrue("链接不对：${it.url}", it.url.contains("comp/info?id="))
            assertTrue("主键不对：${it.key}", it.key.startsWith("cxcycomp|"))
            assertTrue("标题太短：${it.title}", it.title.length >= 4)
        }
        assertTrue("没抓到生命科学竞赛那条", items.any { it.title.contains("生命科学") })
    }

    @Test
    fun `竞赛详情页能抽出报名窗口`() {
        val info = FeedParser.parseCxcyDetail(html("cxcy_comp_detail.html"))
        assertEquals("2026-08-01", info.startAt)
        assertEquals("2027-10-31", info.deadline)
        assertEquals("2026-08-01", info.contestStart)
        assertTrue("主办方没抽到：${info.organizer}", info.organizer.contains("生命科学"))
    }

    @Test
    fun `详情页没有报名字段时不瞎编`() {
        val info = FeedParser.parseCxcyDetail("<html><body><p>什么都没有</p></body></html>")
        assertEquals("", info.startAt)
        assertEquals("", info.deadline)
    }

    // ── 正文 ──────────────────────────────────────────────

    @Test
    fun `省教育厅正文能抽出且不是导航噪音`() {
        val payload = FeedParser.parseBodyGeneric(
            html("lnjyt_article.html"),
            "https://jyt.ln.gov.cn/jyt/gk/zxtz/2026102110290659632/index.shtml",
        )
        assertTrue("正文太短（${payload.text.length} 字）", payload.text.length > 300)
        assertTrue("正文里没有标题关键词", payload.text.contains("职业规划大赛"))
        // 导航噪音的典型字样不该出现在正文开头
        assertTrue(
            "正文里混进了导航：${payload.text.take(60)}",
            !payload.text.take(60).contains("首页"),
        )
    }

    @Test
    fun `挑战杯正文能抽出`() {
        val payload = FeedParser.parseBodyGeneric(
            html("tzb_article.html"),
            "https://www.tiaozhanbei.net/article/15841/",
        )
        assertTrue("挑战杯正文太短（${payload.text.length} 字）", payload.text.length > 100)
        assertTrue("正文里没有「揭榜」", payload.text.contains("揭榜"))
    }

    // ── 截止日期 ──────────────────────────────────────────

    @Test
    fun `能从正文里捞报名截止日`() {
        assertEquals(
            "2026-11-20",
            FeedParser.extractDeadline("报名截止时间：2026年11月20日，逾期不再受理。"),
        )
        assertEquals(
            "2026-11-20",
            FeedParser.extractDeadline("请各学院于 2026-11-20 前完成提交。"),
        )
        assertEquals(
            "2026-12-01",
            FeedParser.extractDeadline("材料报送截止至2026.12.01。"),
        )
    }

    @Test
    fun `没写截止日就返回空串不瞎猜`() {
        assertEquals("", FeedParser.extractDeadline("本次大赛由教务处主办，欢迎同学踊跃参加。"))
        assertEquals("", FeedParser.extractDeadline(""))
    }
}
