package cn.dkarbon.enroute.data.feed

/**
 * 摘要清洗（纯函数，无 IO、无状态）。
 *
 * 背景（真机 bug）：学校 VSB 站群的正文如果是一份 PDF，`vsb_content` 里
 * 只有 PDF 嵌入器的一段提示语（「如果您无法在线浏览此 PDF 文件，则可以……」），
 * 后面还跟着一个孤零零的省略号行。这段提示被当成正文摘要抽了出来。
 *
 * 原则：**绝不编摘要** —— 清洗之后什么都没有就返回空串，让上层
 * `isNotBlank` 判断不显示摘要块；只删「明确是功能提示」的句子/行，
 * 正常正文一个字都不动。
 */
object FeedSummary {

    private const val MAX_CHARS = 100
    private const val MAX_LINES = 2

    /** PDF 嵌入器提示语（VSB 站群正文里嵌 PDF 时出现），英文用句号作句尾 */
    private data class Hint(val text: String, val sentenceEnd: Regex)

    private val PDF_HINTS = listOf(
        Hint("如果您无法在线浏览此 PDF 文件", Regex("""[。！？]""")),
        Hint("If you cannot view this PDF online", Regex("""[.!?]""")),
    )

    /** 省略号 / 分隔线这类纯符号行（无汉字、无字母、无数字） */
    private val SYMBOL_LINE = Regex("""^[\s.…·•*_~\-—–|/\\]*$""")

    /** 整行都是附件 / 下载功能文字才删（避免误伤正文里提到「附件」的句子） */
    private val FUNCTIONAL_LINE = setOf(
        "附件", "附件：", "附件:",
        "附件下载", "附件下载：", "附件下载:",
        "点击下载", "点击下载：", "点击下载:",
        "相关附件", "相关附件：", "相关附件:",
        "下载附件", "下载附件：", "下载附件:",
        "立即下载", "点击此处下载", "相关下载",
    )

    /**
     * 清洗并截断摘要。
     *
     * 顺序：拆行 → 逐行去 PDF 提示句 / 纯符号行 / 纯功能文字 → 取前 [MAX_LINES] 行
     *      → 压成单行 → 截断到 [MAX_CHARS] 字。
     * 结果为空串时说明没有可展示的正文，由调用方自行决定不显示。
     */
    fun clean(raw: String): String {
        if (raw.isBlank()) return ""

        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val kept = ArrayList<String>(lines.size)
        for (line in lines) {
            var l = line.trim()
            if (l.isBlank()) continue
            l = stripPdfHint(l)
            if (l.isBlank()) continue
            if (SYMBOL_LINE.matches(l)) continue
            if (l in FUNCTIONAL_LINE) continue
            kept += l
        }
        if (kept.isEmpty()) return ""

        val text = kept.take(MAX_LINES)
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (text.isBlank()) return ""
        return if (text.length > MAX_CHARS) text.take(MAX_CHARS).trimEnd() else text
    }

    /**
     * 删掉 PDF 提示**整句**：从提示语开头，删到句号 / 感叹号 / 问号或行尾。
     *
     * 提示语后面通常还跟着「，则可以……下载」，必须整句去掉 —— 只删前半句
     * 会留下「，则可以」这种没头没尾的碎片。
     */
    private fun stripPdfHint(line: String): String {
        var out = line
        for (hint in PDF_HINTS) {
            val i = out.lowercase().indexOf(hint.text.lowercase())
            if (i < 0) continue
            val rest = out.substring(i)
            val end = hint.sentenceEnd.find(rest)?.range?.first
            out = if (end == null) {
                out.substring(0, i)
            } else {
                out.substring(0, i) + rest.substring(end + 1)
            }
        }
        return out.trim()
    }
}
