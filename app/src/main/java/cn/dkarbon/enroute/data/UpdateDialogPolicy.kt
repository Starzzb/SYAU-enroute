package cn.dkarbon.enroute.data

/**
 * 更新提醒弹窗的**纯决策层**（见 `docs/计划-更新提醒弹窗.md`）。
 *
 * 这里没有任何网络/文件/Android API —— 全部是「给定输入决定输出」的纯函数，
 * 所以弹窗的三条频率/版式语义能在 JVM 单测里钉死：
 *   1. 频率：`不再提醒` 只压**一个** versionCode，不是全局关提醒
 *   2. notes 切分：按 `；` / 换行切条目，`、` 不切分；空串 → 空列表（不编数据）
 *   3. notes 截断：最多 8 条，超出带剩余计数；正好 8 条不显示计数
 *   4. 强制更新时不显示「不再提醒」（可以催，不能锁死）
 *
 * 条目呈现是 `·` 前缀（不是编号）—— 方案 B「里程表」已定稿（文档 §2.3 第 5 点），
 * 所以这里**不产出序号**；`·` 前缀由 UI 层渲染。
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
}
