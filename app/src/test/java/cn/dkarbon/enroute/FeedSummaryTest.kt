package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 摘要清洗纯函数测试（真机 bug：PDF 嵌入器提示语被抽成摘要）。
 *
 * 关键防线：**正常摘要不能被误伤** —— 清洗只删「明确是功能提示」的句子/行。
 */
class FeedSummaryTest {

    // ── PDF 提示被过滤 ───────────────────────────────────

    @Test
    fun `中文 PDF 嵌入器提示整句被过滤`() {
        assertEquals(
            "",
            FeedSummary.clean("如果您无法在线浏览此 PDF 文件，则可以下载后查看。\n..."),
        )
    }

    @Test
    fun `英文 PDF 嵌入器提示整句被过滤`() {
        assertEquals(
            "",
            FeedSummary.clean("If you cannot view this PDF online, you can download it here.\n..."),
        )
    }

    @Test
    fun `PDF 提示句前有内容时保留前缀`() {
        // 提示语之前若有真实正文，只删提示句本身，前缀要留下来
        assertEquals("通知正文。", FeedSummary.clean("通知正文。如果您无法在线浏览此 PDF 文件，则可以下载后查看。"))
    }

    // ── 清洗后为空 ───────────────────────────────────────

    @Test
    fun `单独省略号行被过滤`() {
        assertEquals("", FeedSummary.clean("..."))
        assertEquals("", FeedSummary.clean("…"))
        assertEquals("", FeedSummary.clean(". . ."))
    }

    @Test
    fun `纯符号与空白行被过滤`() {
        assertEquals("", FeedSummary.clean("---\n***\n_ _ _"))
        assertEquals("", FeedSummary.clean("  \n\t\n　"))
    }

    @Test
    fun `附件下载类功能文字被过滤`() {
        assertEquals("", FeedSummary.clean("附件下载\n点击下载\n相关附件"))
        assertEquals("", FeedSummary.clean("附件"))
    }

    // ── 正常摘要不被误伤（最重要）────────────────────────

    @Test
    fun `正常中文摘要一个字都不动`() {
        val normal = "各学院请于2026年11月20日前将申报材料及附件报送教务处。"
        assertEquals(normal, FeedSummary.clean(normal))
    }

    @Test
    fun `句子里提到附件或下载不被误删`() {
        // 「附件」「下载」出现在句子中间，不是整行功能文字 —— 必须保留
        assertEquals(
            "请于截止日前下载并填写相关附件表格后提交。",
            FeedSummary.clean("请于截止日前下载并填写相关附件表格后提交。"),
        )
    }

    @Test
    fun `句子中间出现省略号不被误删`() {
        // 只有**单独成行**的省略号才删；句中省略号是正文的一部分
        assertEquals(
            "报名时间为2026年11月20日……以官方通知为准。",
            FeedSummary.clean("报名时间为2026年11月20日……以官方通知为准。"),
        )
    }

    // ── 清洗后截断 ───────────────────────────────────────

    @Test
    fun `超过 100 字截断到 100 字`() {
        val long = "各".repeat(150)
        val out = FeedSummary.clean(long)
        assertEquals(100, out.length)
        assertEquals(long.take(100), out)
    }

    @Test
    fun `最多取前两行`() {
        val out = FeedSummary.clean("第一行摘要内容。\n第二行摘要内容。\n第三行摘要内容。")
        assertEquals("第一行摘要内容。 第二行摘要内容。", out)
    }

    @Test
    fun `清洗后为空时返回空串而不是编摘要`() {
        assertTrue(FeedSummary.clean("").isEmpty())
        assertTrue(FeedSummary.clean("如果您无法在线浏览此 PDF 文件，则可以下载后查看。").isEmpty())
    }
}
