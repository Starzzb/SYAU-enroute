package cn.dkarbon.enroute.data

import java.util.Locale

/**
 * 更新提醒弹窗的**纯决策层**（见 `docs/计划-更新提醒弹窗.md` / `docs/规划-m31-交互改版.md` §2）。
 *
 * 这里没有任何网络/文件/Android API —— 全部是「给定输入决定输出」的纯函数，
 * 所以弹窗的频率/版式语义能在 JVM 单测里钉死：
 *   1. 频率：`不再提醒` 只压**一个** versionCode，不是全局关提醒
 *   2. notes 切分：按 `；` / 换行切条目，`、` 不切分；空串 → 空列表（不编数据）
 *   3. notes 截断：最多 8 条，超出带剩余计数；正好 8 条不显示计数
 *   4. 强制更新时不显示「不再提醒」（可以催，不能锁死）
 *
 * m31 起弹窗主体改读 [presentNotes]：`notes` 是**分条格式**（换行且行首为 `- ` / `1. `）
 * 就按条渲染，否则整段当一句话摘要。分条只在本地解析，**不请求网络拉 Release 正文**。
 */
object UpdateDialogPolicy {

    /** 折叠态最多显示的条目数（文档 §2.2 第 3 点） */
    const val MAX_NOTES = 8

    /**
     * 这个版本要不要弹：`available.versionCode == dismissedCode` → 不弹；否则弹。
     * dismissedCode 为 0（从没「不再提醒」过）时任何新版都弹。
     */
    fun shouldShow(info: UpdateInfo, dismissedCode: Long): Boolean =
        info.versionCode != dismissedCode

    /** 强制更新时**整块移除**「不再提醒本次更新」—— 不是变灰（文档 §1.3 / §1.5） */
    fun showsDismiss(mandatory: Boolean): Boolean = !mandatory

    /**
     * 把 `notes` 自由文本切成条目：按 `；` 与换行切，`、` **不切**（同一条里的顿号并列
     * 属于一条）。每条 trim，空段丢弃。空串 → 空列表（不编「暂无更新说明」）。
     *
     * m31 弹窗改用 [presentNotes]（要区分「一句话」与「分条」）；本函数保留，
     * 它是旧版 `；` 分隔口径的单测锚点，行为不变。
     */
    fun parseNotes(notes: String): List<String> =
        notes.split('；', '\n').map { it.trim() }.filter { it.isNotBlank() }

    /** 截断结果：折叠时显示 [items]，[overflow] 为被折叠的剩余条数（0 = 没折叠） */
    data class NotesSlice(val items: List<String>, val overflow: Int) {
        val overflowText: String get() = if (overflow > 0) "等 $overflow 项" else ""
    }

    /** 超过 [max] 条时取前 [max] 条并返回剩余计数；不超过时原样返回、不显示计数 */
    fun notesSlice(items: List<String>, max: Int = MAX_NOTES): NotesSlice =
        if (items.size > max) NotesSlice(items.take(max), items.size - max)
        else NotesSlice(items, 0)

    // ── m31：弹窗主体（公告）的展示模型 ─────────────────────────

    /**
     * 公告正文的两种形态：
     *   · [summary] 非空、[items] 为空 → 一句话，居中显示；
     *   · [items] 非空 → 分条显示，[summary] 可留一句引导（没有就空着）。
     */
    data class NotesBody(val summary: String, val items: List<String>) {
        /** 什么都没有：整块（含分隔线）不渲染 */
        val isEmpty: Boolean get() = summary.isBlank() && items.isEmpty()
    }

    /** 行首项目符号：`- ` / `* ` / `• ` / `1. ` / `1、`（半角空格可有可无） */
    private val BULLET = Regex("""^(?:[-*•]\s*|\d+[.、)]\s*)""")

    /**
     * 判定 `notes` 是「一句话」还是「分条」，并去掉行首项目符号。
     *
     * 判据（够用就好，不做 Markdown 解析）：
     *   · 至少两行，且**任意一行**以项目符号/编号开头 → 分条；
     *   · 其余行（bullet 之前的引导句）合起来当 [NotesBody.summary]；
     *   · 单行、或没有 bullet 行 → 整段当一句话（换行压成空格）。
     *
     * 空行、只有符号的行一律丢弃（不渲染空条目）。
     */
    fun presentNotes(notes: String): NotesBody {
        val raw = notes.trim()
        if (raw.isBlank()) return NotesBody("", emptyList())

        val lines = raw.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        val looksLikeList = lines.size >= 2 && lines.any { BULLET.containsMatchIn(it) }

        if (!looksLikeList) {
            return NotesBody(raw.replace('\n', ' ').replace(Regex("""\s{2,}"""), " ").trim(), emptyList())
        }

        val items = mutableListOf<String>()
        val lead = mutableListOf<String>()
        for (line in lines) {
            val match = BULLET.find(line)
            val text = (if (match != null) line.removeRange(match.range) else line).trim()
            if (text.isBlank()) continue
            if (match != null) items.add(text) else lead.add(text)
        }
        val summary = lead.joinToString(" ").trim()
        return if (items.isEmpty()) NotesBody(summary, emptyList()) else NotesBody(summary, items)
    }

    /**
     * `latest.json` 的 `size`（字节）→ 展示串：`8.1 MB` / `512.0 KB`。
     *
     * 统一一位小数（[UpdateInfo.sizeText] 是 `8.1 MB` / `512 KB` 两种口径）；
     * `0` 或负数 → 空串（服务器没给就不编数字）。
     */
    fun sizeLabel(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        else -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    }
}
