package cn.dkarbon.enroute.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内存调试日志 —— **给「用户端登录到底卡在哪」用的**（2026-11-10 主人要求）。
 *
 * 为什么要有它：登录失败时界面只能给一句人话（「连不上教务」「账号密码不对」），
 * 但那句话掩盖了真实原因。这个日志把**每一步 HTTP 的状态码、跳转、响应原文**
 * 都记下来，用户点一下「复制」就能贴给我 —— 我不用猜、不用远程。
 *
 * ## 三条铁律
 *
 * 1. **绝不记密码**：登录 POST 的表单体里有加密后的账号密码和 lt 票据，
 *    这里只记「body xxx 字节（已隐去）」。
 * 2. **只在内存**：环形缓冲，最多 [MAX] 条，进程死就没了。不落盘、不上传。
 * 3. **只记诊断必需的信息**：状态码 / 长度 / 响应前 180 字。
 */
object DebugLog {

    data class Entry(
        val at: Long,
        val tag: String,
        val msg: String,
    ) {
        fun timeLabel(): String = timeFmt.format(Date(at))
    }

    private const val MAX = 400

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun log(tag: String, msg: String) {
        val e = Entry(System.currentTimeMillis(), tag, msg)
        _entries.value = (_entries.value + e).takeLast(MAX)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** 一键复制用：把全部日志拼成纯文本 */
    fun dump(): String = buildString {
        appendLine("=== 沈农在途 调试日志 ===")
        appendLine("导出时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))
        appendLine("---")
        _entries.value.forEach { e ->
            append(e.timeLabel()).append("  [").append(e.tag).append("] ")
            appendLine(e.msg)
        }
    }

    // ── 便捷方法 ────────────────────────────────────────────

    /** 记一次 HTTP 往返 */
    fun http(
        tag: String,
        method: String,
        url: String,
        status: Int,
        bytes: Int,
        preview: String = "",
        note: String = "",
    ) {
        val brief = buildString {
            append("← ").append(status.toString().padEnd(3))
            append(" ").append(bytes).append("B  ")
            append(method).append(' ').append(shortUrl(url))
            if (note.isNotBlank()) append("  (").append(note).append(')')
            if (preview.isNotBlank()) {
                append("\n      ").append(preview.replace(Regex("\\s+"), " ").take(180))
            }
        }
        log(tag, brief)
    }

    fun request(tag: String, method: String, url: String, note: String = "") {
        log(tag, "→ $method ${shortUrl(url)}" + if (note.isNotBlank()) "  ($note)" else "")
    }

    fun step(tag: String, msg: String) = log(tag, "· $msg")

    fun error(tag: String, msg: String) = log(tag, "✘ $msg")

    /** 只留路径 + 关键参数，避免把超长 query 全打出来 */
    private fun shortUrl(url: String): String {
        val noScheme = url.removePrefix("https://").removePrefix("http://")
        return if (noScheme.length <= 110) noScheme else noScheme.take(110) + "…"
    }
}
