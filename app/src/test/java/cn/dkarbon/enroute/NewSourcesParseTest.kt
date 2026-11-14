package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedParser
import cn.dkarbon.enroute.data.feed.FeedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2026-11-13 新增源与解析族的回归测试。
 *
 * 样本全部是**当日真实抓取**（docs/_probe/cxcy/raw → src/test/resources/feed）：
 *   · cxcy_comp_list / _p2 / _p3   创新创业平台 科技竞赛（比赛中/已结束两态都有）
 *   · cxcy_dcplan_list             大创项目（一年一条立项通知）
 *   · cxcy_article_list            成果公示（day+year 日期块）
 *   · cxcy_comp_detail             竞赛详情（报名/比赛窗口）
 *   · tw_list                      校团委（日期拆两段 + 列表摘要）
 *   · cahe_list                    高教学会（date+year 拆段 + h6 标题）
 *   · jingsai_list                 我爱竞赛网（无日期 + img@title + 站点根链接）
 */
class NewSourcesParseTest {

    private fun html(name: String): String =
        javaClass.getResourceAsStream("/feed/$name")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val fixedNow = 1_700_000_000_000L

    // ── 创新创业平台：列表自带结构化字段 ──────────────────

    @Test
    fun `竞赛平台列表直接拿到报名窗口和主办方`() {
        val src = FeedSources.byKey("cxcycomp")!!
        val items = FeedParser.parseListCxcy(html("cxcy_comp_list.html"), src.listUrl, src, fixedNow)
        assertEquals("第一页应解析出 7 条", 7, items.size)
        val life = items.first { it.title.contains("生命科学") }
        assertEquals("2026-08-01", life.regStart)
        assertEquals("2027-10-31", life.deadline)
        // 不拿报名开始日冒充发布日（评审 B4）：publishedAt 留空，排序靠 firstSeenAt
        assertEquals("", life.publishedAt)
        assertTrue("主办方没拿到：${life.organizer}", life.organizer.contains("生命科学"))
        assertEquals("比赛中", life.platformState)
        assertTrue("摘要没拿到", life.summary.isNotBlank())
        assertTrue("链接形态不对：${life.url}", life.url.contains("/comp/front/comp/info?id=1542"))
        assertTrue("备用根路由没给", life.urlAlt.contains("/comp/info?id=1542"))
        assertTrue("主键重复", items.map { it.key }.toSet().size == items.size)
    }

    @Test
    fun `竞赛平台第二页与第一页不重复且都是进行中`() {
        val src = FeedSources.byKey("cxcycomp")!!
        val p1 = FeedParser.parseListCxcy(html("cxcy_comp_list.html"), src.listUrl, src, fixedNow)
        val p2 = FeedParser.parseListCxcy(html("cxcy_comp_list_p2.html"), src.listUrl + "?page=2", src, fixedNow)
        assertEquals(7, p2.size)
        assertTrue("两页主键有重叠", (p1.map { it.key } intersect p2.map { it.key }.toSet()).isEmpty())
        // 第二页有 11 月底截止的赛：报名窗口必须解析出来
        val bim = p2.first { it.title.contains("BIM") }
        assertEquals("2026-11-15", bim.deadline)
    }

    @Test
    fun `竞赛平台第三页是已结束状态`() {
        val src = FeedSources.byKey("cxcycomp")!!
        val p3 = FeedParser.parseListCxcy(html("cxcy_comp_list_p3.html"), src.listUrl + "?page=3", src, fixedNow)
        assertEquals(7, p3.size)
        assertTrue("第三页应全部已结束", p3.all { it.platformState == "已结束" })
        assertTrue(p3.any { it.title.contains("智慧农业") })
    }

    @Test
    fun `大创项目列表解析出历年立项与报名窗口`() {
        val src = FeedSources.byKey("cxcydc")!!
        val items = FeedParser.parseListCxcy(html("cxcy_dcplan_list.html"), src.listUrl, src, fixedNow)
        assertEquals("大创共 6 条", 6, items.size)
        val y2026 = items.first { it.title.contains("2026") }
        assertEquals("2026-05-13", y2026.regStart)
        assertEquals("2026-06-08", y2026.deadline)
        assertTrue("大创链接不对：${y2026.url}", y2026.url.contains("dcplan/front/dcplan/info?id=1417"))
        assertEquals("dcplan 只有 front 路由，不该给备用地址", "", y2026.urlAlt)
        assertTrue(items.all { it.key.startsWith("cxcydc|") })
    }

    @Test
    fun `成果公示日期从 day 加 year 块拼出`() {
        val src = FeedSources.byKey("cxcyart")!!
        val items = FeedParser.parseListCxcy(html("cxcy_article_list.html"), src.listUrl, src, fixedNow)
        assertEquals("成果公示共 4 条", 4, items.size)
        val first = items.first { it.title.contains("智能农业装备") }
        assertEquals("2025-12-08", first.publishedAt)
        assertTrue(first.url.contains("articledetail?id=163"))
        assertEquals("articledetail 根路由 404，不该给备用地址", "", first.urlAlt)
        // 成果没有报名窗口：deadline 必须为空，不能编
        assertTrue(items.all { it.deadline.isBlank() })
    }

    @Test
    fun `竞赛详情仍能抽出比赛窗口`() {
        val info = FeedParser.parseCxcyDetail(html("cxcy_comp_detail.html"))
        assertEquals("2026-08-01", info.startAt)
        assertEquals("2027-10-31", info.deadline)
        assertEquals("2026-08-01", info.contestStart)
        assertEquals("2027-10-31", info.contestEnd)
    }

    // ── 校团委：日期拆两段 ────────────────────────────────

    @Test
    fun `校团委日期拼接正确且带列表摘要`() {
        val src = FeedSources.byKey("tw")!!
        val items = FeedParser.parseListTw(html("tw_list.html"), src.listUrl, src, fixedNow)
        assertTrue("校团委解析出 ${items.size} 条，应 ≥10", items.size >= 10)
        val speech = items.first { it.title.contains("演讲比赛") }
        assertEquals("2026-11-05", speech.publishedAt)
        assertTrue("摘要应来自列表 <p>", speech.summary.contains("共青团"))
        assertTrue(speech.url.startsWith("https://tw.syau.edu.cn/info/1053/"))
        assertTrue(speech.urlAlt.contains("wbnewsid="))
        // 日期全部归一且倒序
        items.forEach { assertTrue("日期格式：${it.publishedAt}", Regex("""\d{4}-\d{2}-\d{2}""").matches(it.publishedAt)) }
        assertEquals(items.map { it.publishedAt }.sortedDescending(), items.map { it.publishedAt })
    }

    // ── 高教学会：h6 标题 + 白名单 ────────────────────────

    @Test
    fun `高教学会只留竞赛相关且日期拼对`() {
        val src = FeedSources.byKey("cahe")!!
        val items = FeedParser.parseListCahe(html("cahe_list.html"), src.listUrl, src, fixedNow)
        assertTrue("高教学会解析出 0 条", items.isNotEmpty())
        items.forEach { item ->
            assertTrue("混进非竞赛公告：${item.title}", src.titleInclude.any { item.title.contains(it) })
            assertTrue("日期格式：${item.publishedAt}", Regex("""\d{4}-\d{2}-\d{2}""").matches(item.publishedAt))
            assertTrue(item.url.startsWith("https://www.cahe.edu.cn/site/content/"))
        }
        // 招聘/声明类必须被筛掉
        assertTrue(items.none { it.title.contains("招聘") || it.title.contains("声明") })
        // 华灿奖那条应在（2019-11-28）
        val hc = items.first { it.title.contains("华灿") }
        assertEquals("2019-11-28", hc.publishedAt)
    }

    // ── 我爱竞赛网：无日期 + 全标题 + 站点根 ──────────────

    @Test
    fun `我爱竞赛网全标题来自 img title 且链接在站点根`() {
        val src = FeedSources.byKey("jingsai")!!
        val today = LocalDate.of(2026, 11, 13)
        val items = FeedParser.parseListJingsai(html("jingsai_list.html"), src.listUrl, src, fixedNow, today)
        assertEquals("主列表当前 4 条（只扫榜单主块，不扫侧栏热榜）", 4, items.size)
        assertTrue("主键重复（翻页重叠场景）", items.map { it.key }.toSet().size == items.size)
        items.forEach {
            assertTrue("链接必须在站点根：${it.url}", it.url.startsWith("https://www.52jingsai.com/article-"))
            assertTrue("列表无日期，publishedAt 必须留空", it.publishedAt.isBlank())
            assertTrue("标题太短：${it.title}", it.title.length >= 8)
        }
        // 可见文本被截断，全标题必须来自 img@title
        val ncevc = items.first { it.title.contains("NCEVC") }
        assertTrue("全标题没拿到：${ncevc.title}", ncevc.title.contains("报名通知"))
        // 标题写明截止日的要抽出来：【11月13日截止报名】→ 当天
        val nciecc = items.first { it.title.contains("NCIECC") }
        assertEquals("2026-11-13", nciecc.deadline)
        // 没写截止日的不能编
        assertTrue(items.any { it.deadline.isBlank() })
    }

    @Test
    fun `标题截止日跨年时按下一年算`() {
        val today = LocalDate.of(2026, 12, 20)
        assertEquals(
            "2027-01-10",
            FeedParser.deadlineFromTitle("【1月10日截止报名】2027年某大赛", today),
        )
        assertEquals(
            "2026-12-25",
            FeedParser.deadlineFromTitle("【12月25日截止】某大赛", today),
        )
        assertEquals("", FeedParser.deadlineFromTitle("没有日期的标题", today))
        assertEquals("", FeedParser.deadlineFromTitle("【13月40日截止】乱写的日期", today))
    }

    @Test
    fun `过期标题不造未来日期_年份写明时优先采信`() {
        // 年中陈旧条目：6月1日截止在 11 月看到 → 不显示（评审 B1）
        assertEquals(
            "",
            FeedParser.deadlineFromTitle("【6月1日截止报名】某大赛", LocalDate.of(2026, 11, 13)),
        )
        // 12 月看到 1 月截止、没写年 → 下一年（真实跨年场景）
        assertEquals(
            "2027-01-10",
            FeedParser.deadlineFromTitle("【1月10日截止报名】某大赛", LocalDate.of(2026, 12, 20)),
        )
        // 标题写明 2025 年 → 采信 2025，不推成 2027（评审 B2）
        assertEquals(
            "2025-03-01",
            FeedParser.deadlineFromTitle("【3月1日截止】2025年回顾展", LocalDate.of(2026, 11, 13)),
        )
        // 未来的月日 → 今年
        assertEquals(
            "2026-12-01",
            FeedParser.deadlineFromTitle("【12月1日截止】某大赛", LocalDate.of(2026, 11, 13)),
        )
    }

    @Test
    fun `报名窗口带时刻的格式也能解析`() {
        val src = FeedSources.byKey("cxcycomp")!!
        // 详情页形态「2026-11-06 00:00 至 2027-01-31 23:59」出现在列表时的容错（评审 B5）
        val html = """<ul><li class="clearfix"><div class="center">
            <a href="../comp/info?id=9999" target="_blank" title="测试竞赛带时刻格式">测试竞赛带时刻格式</a>
            <div class="txt">摘要</div>
            <div class="msg">报名时间：2026-11-06 00:00 至 2027-01-31 23:59</div>
            <div class="msg">主办方：某委员会</div>
        </div></li></ul>"""
        val items = FeedParser.parseListCxcy(html, src.listUrl, src, fixedNow)
        assertEquals(1, items.size)
        assertEquals("2026-11-06", items[0].regStart)
        assertEquals("2027-01-31", items[0].deadline)
    }

    @Test
    fun `提交截止不等于报名截止_不抽`() {
        // 评审 B3：作品提交/比赛截止不是报名截止，错标比不标更害人
        assertEquals("", FeedParser.extractDeadline("作品提交截止：2026年12月1日，逾期不候。"))
        assertEquals("", FeedParser.extractDeadline("比赛截止2026年11月30日。"))
        // 报名语境仍然要抽到
        assertEquals("2026-11-20", FeedParser.extractDeadline("报名截止时间：2026年11月20日。"))
        assertEquals("2026-11-20", FeedParser.extractDeadline("请各学院于2026年11月20日前完成报名。"))
    }

    @Test
    fun `我爱竞赛网正文只取 article_content 不混入侧栏推荐`() {
        // 2026-11-13 真机验收 bug：通用提取器撞到侧栏 class="content"，
        // 详情页正文变成一串别的比赛链接。这里钉死正确边界。
        val html = html("jingsai_article.html")
        val body = FeedParser.parseBodyJingsai(html, "https://www.52jingsai.com/article-23912-1.html")
        assertTrue("正文太短（${body.text.length} 字）", body.text.length > 200)
        assertTrue("正文没抓到大赛背景", body.text.contains("大赛背景"))
        assertTrue("正文没抓到主办单位", body.text.contains("主办单位"))
        // 侧栏/热榜里的其它比赛不能混进来
        assertTrue("正文混进了侧栏推荐：${body.text.take(80)}", !body.text.contains("温暖中国"))
        // 全角/半角都不该出现「数学竞赛」这种侧栏标题
        assertTrue("正文混进了热榜条目", !body.text.contains("华教杯数学竞赛"))
    }

    @Test
    fun `我爱竞赛网详情能补出声明的摘要与发布时间`() {
        val (summary, date) = FeedParser.parseJingsaiMeta(html("jingsai_article.html"))
        assertEquals("2026-10-15", date)
        assertTrue("摘要没拿到：$summary", summary.contains("NCIECC") || summary.contains("报名时间"))
        assertTrue("摘要长度异常：${summary.length}", summary.length in 10..130)
    }

    @Test
    fun `没有 article_content 时正文提取回退到通用规则`() {
        val body = FeedParser.parseBodyJingsai("<html><body><p>普通页面没有正文容器</p></body></html>")
        // 回退后要么拿到短文本要么空，但不能抛异常
        assertTrue(body.text.length < 200)
        assertEquals("", FeedParser.parseJingsaiMeta("<html></html>").second)
    }

    // ── 源表完整性 ────────────────────────────────────────

    @Test
    fun `新源都已注册且解析族正确`() {
        assertEquals("cxcy", FeedSources.byKey("cxcydc")!!.kind)
        assertEquals("cxcy", FeedSources.byKey("cxcyart")!!.kind)
        assertEquals("tw", FeedSources.byKey("tw")!!.kind)
        assertEquals("cahe", FeedSources.byKey("cahe")!!.kind)
        assertEquals("jingsai", FeedSources.byKey("jingsai")!!.kind)
        assertEquals("竞赛平台应有 2 个额外页", 2, FeedSources.byKey("cxcycomp")!!.extraPages.size)
        assertTrue(FeedSources.byKey("cxcycomp")!!.extraPages.all { it.contains("?page=") })
    }
}