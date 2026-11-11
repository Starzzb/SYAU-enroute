package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedParser
import cn.dkarbon.enroute.data.feed.FeedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 博达 VSB 站群解析器的回归测试。
 *
 * HTML 片段是 2026-11-10 从真实站点抓下来的结构（去掉噪音保留骨架），
 * 两族差异见 [FeedParser] 的 KDoc：
 *   · Family A（www / news）：`<a title="全标题">`
 *   · Family B（jwc / xsc）：`<a><span>日期</span><em>[科室]标题…</em></a>`（标题被截断）
 */
class FeedParserTest {

    private val www = FeedSources.byKey("www")!!
    private val jwc = FeedSources.byKey("jwc")!!

    // Family A：标题在 title 属性里，完整
    private val htmlA = """
        <ul class="arc-list">
          <li class="item">
            <a target="_blank" href="../info/1144/10998.htm" title="关于开展2026年下半年师资博士后选聘工作的通知" class="box">
              <h6 class="title clamp">关于开展2026年下半年师资博士后选聘工作的通知</h6>
              <span class="date">2026-11-10</span>
            </a>
          </li>
          <li class="item">
            <a target="_blank" href="../info/1144/10990.htm" title="关于做好新学期工作的通知" class="box">
              <h6 class="title clamp">关于做好新学期工作的通知</h6>
              <span class="date">2026-10-28</span>
            </a>
          </li>
        </ul>
    """.trimIndent()

    // Family B：标题在 <em> 里、被截断，带 [科室] 前缀，日期在 <span>
    private val htmlB = """
        <div class="main_conRCb"><ul>
          <li id="line_u7_0"><a href="../info/1250/10666.htm">
            <span>2026-11-10</span><em><span style="color:#0b6cb8;float:none;">[专业建设与质量监控科]</span>关于开展2026年全国高等农林院校课程思政优秀教学案例征集与...</em></a></li>
          <li id="line_u7_1"><a href="../../content.jsp?urltype=news.NewsContentUrl&wbtreeid=1249&wbnewsid=10662">
            <span>2026-11-05</span><em>[考务与学籍科]关于2026-2027学年秋季学期期末考试安排的通知</em></a></li>
        </ul></div>
    """.trimIndent()

    // ── Family A ──────────────────────────────────────────

    @Test
    fun `www族 标题取 title 属性 完整不截断`() {
        val list = FeedParser.parseList(htmlA, "https://www.syau.edu.cn/index/tzgg.htm", www)
        assertEquals(2, list.size)
        assertEquals("关于开展2026年下半年师资博士后选聘工作的通知", list[0].title)
        assertEquals("2026-11-10", list[0].publishedAt)
        assertTrue(!list[0].titleTruncated)
    }

    @Test
    fun `相对链接被解析成绝对地址`() {
        val list = FeedParser.parseList(htmlA, "https://www.syau.edu.cn/index/tzgg.htm", www)
        assertEquals("https://www.syau.edu.cn/info/1144/10998.htm", list[0].url)
    }

    @Test
    fun `主键是 源_栏目_文章 而不是URL`() {
        val list = FeedParser.parseList(htmlA, "https://www.syau.edu.cn/index/tzgg.htm", www)
        assertEquals("www|1144|10998", list[0].key)
    }

    // ── Family B ──────────────────────────────────────────

    @Test
    fun `教务族 标题被截断时打标记`() {
        val list = FeedParser.parseList(htmlB, "https://jwc.syau.edu.cn/index/jwtz.htm", jwc)
        val first = list.first { it.key == "jwc|1250|10666" }
        assertEquals("关于开展2026年全国高等农林院校课程思政优秀教学案例征集与", first.title)
        assertTrue("列表标题带省略号应标记为截断", first.titleTruncated)
        assertEquals("2026-11-10", first.publishedAt)
    }

    @Test
    fun `科室前缀被拆到摘要里 标题保持干净`() {
        val list = FeedParser.parseList(htmlB, "https://jwc.syau.edu.cn/index/jwtz.htm", jwc)
        val first = list.first { it.key == "jwc|1250|10666" }
        assertTrue(!first.title.startsWith("["))
        assertEquals("［专业建设与质量监控科］", first.summary)
    }

    @Test
    fun `content_jsp 形态也能认出主键`() {
        val list = FeedParser.parseList(htmlB, "https://jwc.syau.edu.cn/index/jwtz.htm", jwc)
        val cjsp = list.first { it.key == "jwc|1249|10662" }
        assertEquals("关于2026-2027学年秋季学期期末考试安排的通知", cjsp.title)
        assertTrue(!cjsp.titleTruncated)
        // 兜底的动态地址一定存在
        assertTrue(cjsp.urlAlt.contains("wbnewsid=10662"))
    }

    @Test
    fun `同一页重复链接只留一条`() {
        val dup = htmlA + htmlA
        val list = FeedParser.parseList(dup, "https://www.syau.edu.cn/index/tzgg.htm", www)
        assertEquals(2, list.size)
    }

    @Test
    fun `按日期倒序`() {
        val list = FeedParser.parseList(htmlA, "https://www.syau.edu.cn/index/tzgg.htm", www)
        assertTrue(list[0].publishedAt >= list[1].publishedAt)
    }

    @Test
    fun `导航链接不会混进来`() {
        val withNav = """
            <a href="tzgg.htm">通知公告</a>
            <a href="../info/1144/10998.htm" title="真正的通知标题在这里啊">真</a>
        """.trimIndent()
        val list = FeedParser.parseList(withNav, "https://www.syau.edu.cn/index/tzgg.htm", www)
        assertEquals(1, list.size)
        assertEquals("真正的通知标题在这里啊", list[0].title)
    }

    // ── 附件与链接 ────────────────────────────────────────

    @Test
    fun `正文里的附件被抽出来并转成绝对地址`() {
        val html = """
            <div id="vsb_content"><div class="v_news_content">
              <p>请下载附件填报。</p>
              <a href="/system/_content/download.jsp?urltype=news.DownloadAttachUrl&amp;owner=1584116291&amp;wbfileid=1234">
                 1.项目汇总表.xls</a>
              <a href="/system/_content/download.jsp?urltype=news.DownloadAttachUrl&amp;owner=1584116291&amp;wbfileid=5678">
                 2.申报指南.pdf</a>
            </div></div>
        """.trimIndent()
        val p = FeedParser.parseBody(html, "https://jwc.syau.edu.cn/info/1250/10666.htm")
        assertEquals(2, p.attachments.size)
        assertTrue(p.attachments[0].url.startsWith("https://jwc.syau.edu.cn/system/_content/download.jsp"))
        assertTrue(p.attachments[0].isAttachment)
        assertTrue(p.attachments[0].text.contains("汇总表.xls"))
    }

    @Test
    fun `普通超链接不算附件 单独归到文中链接`() {
        val html = """
            <div id="vsb_content">
              <p>详见<a href="https://www.syau.edu.cn/index/tzgg.htm">学校通知公告</a>。</p>
              <a href="/__local/F/C0/xxx_509FD.pdf?e=.pdf">PDF 文件</a>
            </div>
        """.trimIndent()
        val p = FeedParser.parseBody(html, "https://xsc.syau.edu.cn/info/1048/5435.htm")
        assertEquals(1, p.links.size)
        assertEquals("学校通知公告", p.links[0].text)
        assertEquals(1, p.attachments.size)
        assertTrue(p.attachments[0].url.contains("__local"))
    }

    @Test
    fun `javascript 和锚点链接被忽略`() {
        val html = """<div id="vsb_content"><a href="javascript:void(0)">打印</a><a href="#top">顶部</a><p>正文内容够长了吧</p></div>"""
        val p = FeedParser.parseBody(html, "https://x.syau.edu.cn/info/1/2.htm")
        assertEquals(0, p.links.size)
        assertEquals(0, p.attachments.size)
    }

    @Test
    fun `正文开头的标签残渣被切掉`() {
        val html = """<div id="vsb_content"><p>各单位：现通知如下。</p></div><div class="footer">版权</div>"""
        val p = FeedParser.parseBody(html, "https://x.syau.edu.cn/info/1/2.htm")
        assertTrue("不该残留标签文字", !p.text.contains("id="))
        assertTrue(!p.text.contains("版权"))
    }
    // ── 正文 ──────────────────────────────────────────────

    @Test
    fun `正文从 vsb_content 抽出来 段落变行`() {
        val html = """
            <html><body>
            <div class="nav">导航不该出现</div>
            <div id="vsb_content">
              <div class="v_news_content">
                <p>各单位：</p>
                <p>现将有关事项通知如下：</p>
                <p>一、报名时间：2026年11月10日至11月20日</p>
                <p>二、报名方式：登录系统提交&nbsp;即可。</p>
              </div>
            </div>
            <div class="footer">版权所有</div>
            </body></html>
        """.trimIndent()
        val text = FeedParser.parseArticle(html)
        assertTrue(text.contains("各单位"))
        assertTrue(text.contains("报名时间"))
        assertTrue(text.contains("登录系统提交"))
        assertTrue("页脚不该被抽进正文", !text.contains("版权所有"))
    }

    @Test
    fun `正文里的 html 实体被解码`() {
        val html = """<div id="vsb_content"><p>A&amp;B&nbsp;&nbsp;C&ldquo;引号&rdquo;</p></div>"""
        val text = FeedParser.parseArticle(html)
        assertTrue(text.contains("A&B"))
        assertTrue(text.contains("“引号”"))
    }

    @Test
    fun `没有正文容器时返回空串 而不是乱抓`() {
        assertEquals("", FeedParser.parseArticle("<html><body>登录页</body></html>"))
    }

    @Test
    fun `详情页标题能补全截断`() {
        val html = """<html><head><title>关于开展2026年全国高等农林院校课程思政优秀教学案例征集与教学改革研究项目申报工作的通知</title></head></html>"""
        val t = FeedParser.parseArticleTitle(html)
        assertTrue(t.length > 30)
        assertTrue(t.contains("教学改革研究项目"))
    }
}
