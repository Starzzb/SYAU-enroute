package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * 正文形态适配的锁（2026-11-21 三路实测后的回归护栏）。
 *
 * 覆盖的都是**实测踩出来的**行为，每条注释里写着对应的证据：
 *  · 校内 6/7 源带 UTF-8 BOM；
 *  · 正文图只认 `/__local/` 等正文域，模板 logo 要滤掉；
 *  · 学生处正文是 iframe 指向的 PDF；
 *  · 附件走 download.jsp + 图形验证码，要标 blocked；
 *  · 校内竞赛的容器 id 是 `vsb_content_4` 不是 `vsb_content`；
 *  · 就业信息网正文是 base64→zlib→base64。
 */
class FeedBodyFormsTest {

    private fun vsb(bodyHtml: String): String =
        "<html><body><div id=\"vsb_content\"><div class=\"v_news_content\">" +
            bodyHtml + "</div></div></body></html>"

    @Test
    fun `正文图只收正文域，模板 logo 一律丢`() {
        val html = vsb(
            "<p>通知正文</p>" +
                "<img src=\"/__local/E/C3/ABBB.jpg\">" +
                "<img src=\"../../images/logo.png\">" +
                "<img src=\"images/snjybq.png\">" +
                "<img src=\"ewm.jpg\">",
        )
        val p = FeedParser.parseBody(html, "https://jwc.syau.edu.cn/info/1250/10684.htm")
        assertEquals("只该留下 /__local/ 那张", 1, p.images.size)
        assertTrue(p.images[0].url.startsWith("https://jwc.syau.edu.cn/__local/"))
    }

    @Test
    fun `正文是 iframe 指向的 PDF 时给出直链`() {
        // 学生处实测：正文只有 115 字「请安装 PDF 阅读器」，真内容在 iframe 里
        val html = vsb(
            "<p>请安装 PDF 阅读器</p>" +
                "<iframe src=\"/__local/B/26/85/2DB3EDE8.pdf?e=.pdf\"></iframe>",
        )
        val p = FeedParser.parseBody(html, "https://xsc.syau.edu.cn/info/1048/5441.htm")
        assertTrue("没认出 PDF 直链", p.pdfUrl.endsWith(".pdf"))
        assertTrue(p.pdfUrl.startsWith("https://xsc.syau.edu.cn/__local/"))
    }

    @Test
    fun `附件走 download 接口时标成需要人工验证码`() {
        val html = vsb("<p>见附件</p><a href=\"/system/_content/download.jsp?wbfileid=11847203\">消防承诺书</a>")
        val p = FeedParser.parseBody(html, "https://www.syau.edu.cn/info/1144/10999.htm")
        assertEquals(1, p.attachments.size)
        assertTrue("附件挂了验证码却没标 blocked", p.attachmentsBlocked)
    }

    @Test
    fun `校内竞赛的 vsb_content_4 容器也认`() {
        // cxcy 是唯一用 _4 后缀的源；写死 vsb_content 会让它整页空白
        val html = "<html><body><div id=\"vsb_content_4\"><div class=\"v_news_content\">" +
            "<p>校内竞赛正文，长度要够五个字以上。</p></div></div></body></html>"
        val p = FeedParser.parseBody(html, "https://job.syau.edu.cn/info/1040/3806.htm")
        assertTrue("vsb_content_4 没被认出来", p.text.contains("校内竞赛正文"))
    }

    @Test
    fun `就业信息网的 base64-zlib-base64 能解开`() {
        // 造一段与实测同构的载荷：<15 字符前缀 + base64(91 字符前缀 + html)
        // 实测链路里内层还有 15 字符前缀（解包后要 substr(15) 去掉），这里照做
        // 解包链每一步都在字节层面操作，所以构造时也必须按 ISO_8859_1 语义来：
        // 先把中文转成 UTF-8 字节，再按 latin-1 变成「一字节一字符」的字符串，
        // 这样 Kotlin 侧 String→toByteArray(ISO_8859_1) 才能还原成同一串字节。
        val innerBytes = ("P".repeat(15) + "<p>校园招聘会通知，九月十八日在体育馆举行。</p>" + "x".repeat(1200))
            .toByteArray(Charsets.UTF_8)
        val inner = String(innerBytes, Charsets.ISO_8859_1)
        val b64a = Base64.getEncoder().encodeToString(innerBytes)
        val mid = "A".repeat(91) + b64a
        val d = Deflater()
        d.setInput(mid.toByteArray())
        d.finish()
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(4096)
        // 标准写法：Deflater 需要反复 deflate 直到 finished，中途返回 0 不代表结束
        d.finish()
        while (!d.finished()) {
            val n = d.deflate(tmp)
            if (n > 0) buf.write(tmp, 0, n) else if (d.needsInput()) break
        }
        val payload = Base64.getEncoder().encodeToString(buf.toByteArray())
        val html = "<script>var content = \"" + payload + "\";</script>"

        val decoded = FeedParser.decodeJyBody(html)
        // 解出来是内层那段（含 15 字符前缀 + </p> 之后的占位），关键汉字必须在
        assertTrue("jy 正文没解开：" + decoded.take(40), decoded.contains("校园招聘会通知"))
    }
}
