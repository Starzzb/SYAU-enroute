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
 * 但那句话掩盖了真实原因。这个日志把**每一步 HTTP 的状态码、跳转、耗时、响应原文**
 * 都记下来，用户点一下「复制」就能贴给我 —— 我不用猜、不用远程。
 *
 * ## 三条铁律
 *
 * 1. **绝不记密码**：登录 POST 的表单体里有加密后的账号密码和 lt 票据，
 *    这里只记「body xxx 字节（已隐去）」。
 * 2. **只在内存**：环形缓冲，最多 [MAX] 条，进程死就没了。不落盘、不上传。
 *    （票 20 起：**可选的本地落盘**见 [DebugLogFile]，落盘路径会额外脱敏；仍然不上传。）
 * 3. **只记诊断必需的信息**：状态码 / 长度 / 耗时 / 响应前 180 字。
 *
 * ## 票 20 的三处增强
 *
 * - **D1 耗时**：[http] 多了 `elapsedMs`，输出 `← 200 12.3KB 820ms GET …`；
 *   另有 [timed] 便捷包装（用 `nanoTime`，不受系统时间调整影响）。
 * - **D2 落盘**：[attachFile] 挂上 [DebugLogFile] 后，每条日志异步追加到本机文件。
 * - **D3 分级**：[Level] + **单独的 ERROR 保底缓冲**（最近 [MAX_ERRORS] 条），
 *   不被 FEED 的批量 HTTP 冲掉。过滤只在**读取侧**做，写入侧不丢数据。
 */
object DebugLog {

    /** 级别（写入时判定，读取侧过滤） */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val at: Long,
        val tag: String,
        val msg: String,
        val level: Level = Level.INFO,
    ) {
        fun timeLabel(): String = timeFmt.format(Date(at))
    }

    private const val MAX = 400
    private const val MAX_ERRORS = 50

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** 错误保底：最近 50 条 ERROR，**不随主缓冲滚动而丢**（D3） */
    private val _errors = MutableStateFlow<List<Entry>>(emptyList())
    val errors: StateFlow<List<Entry>> = _errors.asStateFlow()

    /** 落盘通道；null = 没挂（未初始化或用户关了开关） */
    @Volatile
    private var file: DebugLogFile? = null

    /** 挂上/摘掉落盘（设置页开关走这里；`null` = 关闭） */
    fun attachFile(sink: DebugLogFile?) {
        file?.flush()
        file = sink
    }

    fun isFileEnabled(): Boolean = file != null

    fun fileSink(): DebugLogFile? = file

    fun log(tag: String, msg: String) = write(Entry(System.currentTimeMillis(), tag, msg, Level.INFO))

    /** DEBUG 级（细枝末节的步骤，默认在调试页可以过滤掉） */
    fun debug(tag: String, msg: String) = write(Entry(System.currentTimeMillis(), tag, msg, Level.DEBUG))

    fun warn(tag: String, msg: String) = write(Entry(System.currentTimeMillis(), tag, msg, Level.WARN))

    fun error(tag: String, msg: String) = write(Entry(System.currentTimeMillis(), tag, msg, Level.ERROR))

    /**
     * **唯一写入口**。
     *
     * 脱敏在这里做（进内存之前），不是只在落盘时做 —— 这是真机复测暴露的洞：
     * 日志离开设备的机会**不止一次**：① 落盘文件被导出 ② 屏幕被人看到 ③ 复制粘贴到聊天窗
     * ④ 「导出文档」把内存缓冲写出去。只在落盘那层挡，②③④ 全是明文
     * （真机上 CAS 票据 `ST-277817-…` 就这么明晃晃显示在调试页上）。
     *
     * 代价：调试时看不到票据原文。这个代价认了 —— 安全优先，且键名仍保留（知道有票、拿不到票）。
     * 落盘那层的 `DebugLogFile.redact` 保留：幂等，作为纵深防御。
     */
    private fun write(e: Entry) {
        val safe = e.copy(msg = DebugLogFile.redact(e.msg))
        _entries.value = (_entries.value + safe).takeLast(MAX)
        if (safe.level == Level.ERROR) _errors.value = (_errors.value + safe).takeLast(MAX_ERRORS)
        // 落盘：失败必须吞掉（日志 I/O 绝不许影响业务）
        runCatching { file?.append(formatLine(safe)) }
    }

    fun clear() {
        _entries.value = emptyList()
        _errors.value = emptyList()
    }

    /** 一键复制用：把全部日志拼成纯文本（与落盘行格式**共用** [formatLine]） */
    fun dump(): String = buildString {
        appendLine("=== 沈农在途 调试日志 ===")
        appendLine("导出时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))
        appendLine("---")
        _entries.value.forEach { appendLine(formatLine(it)) }
    }

    /** **唯一**的行格式：内存导出与落盘都走它（避免两套格式） */
    fun formatLine(e: Entry): String = "${e.timeLabel()}  [${e.tag}] ${e.msg}"

    // ── 便捷方法 ────────────────────────────────────────────

    /**
     * 记一次 HTTP 往返。
     *
     * @param elapsedMs 耗时毫秒；`< 0` = 未计时（不输出耗时段）
     */
    fun http(
        tag: String,
        method: String,
        url: String,
        status: Int,
        bytes: Int,
        elapsedMs: Long = -1,
        preview: String = "",
        note: String = "",
    ) {
        val brief = buildString {
            append("← ").append(status.toString().padEnd(3))
            append(" ").append(sizeLabel(bytes)).append("  ")
            if (elapsedMs >= 0) append(elapsedMs).append("ms  ")
            append(method).append(' ').append(shortUrl(url))
            if (note.isNotBlank()) append("  (").append(note).append(')')
            if (preview.isNotBlank()) {
                append("\n      ").append(preview.replace(Regex("\\s+"), " ").take(180))
            }
        }
        write(Entry(System.currentTimeMillis(), tag, brief, levelOf(status)))
    }

    /** 状态码 → 级别：2xx INFO；4xx/5xx WARN；-1（异常/连不上）ERROR */
    fun levelOf(status: Int): Level = when {
        status in 200..299 -> Level.INFO
        status in 400..599 -> Level.WARN
        else -> Level.ERROR
    }

    private fun sizeLabel(bytes: Int): String = when {
        bytes <= 0 -> "0B"
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1fKB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2fMB", bytes / (1024.0 * 1024.0))
    }

    fun request(tag: String, method: String, url: String, note: String = "") {
        log(tag, "→ $method ${shortUrl(url)}" + if (note.isNotBlank()) "  ($note)" else "")
    }

    fun step(tag: String, msg: String) = debug(tag, "· $msg")

    /**
     * 计一段耗时并返回结果（用 `nanoTime`：不受系统时间被改的影响）。
     *
     * ```kotlin
     * val html = DebugLog.timed("FEED", "教务通知") { FeedHttp.get(url).body }
     * ```
     */
    inline fun <T> timed(tag: String, label: String, block: () -> T): T {
        val t0 = System.nanoTime()
        try {
            return block()
        } finally {
            val ms = (System.nanoTime() - t0) / 1_000_000
            step(tag, "$label 耗时 ${ms}ms")
        }
    }

    /** 只留路径 + 关键参数，避免把超长 query 全打出来 */
    private fun shortUrl(url: String): String {
        val noScheme = url.removePrefix("https://").removePrefix("http://")
        return if (noScheme.length <= 110) noScheme else noScheme.take(110) + "…"
    }
}
