package cn.dkarbon.enroute.data.telemetry

import android.content.Context
import android.os.Build
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * 静默使用数据采集的唯一入口（`docs/规划-使用数据采集.md`）。
 *
 * ## 「静默」的四条实现承诺
 *
 * | 承诺 | 怎么做到 |
 * |---|---|
 * | 不影响操作响应 | [record] 只往内存 `ArrayDeque` 里塞，主线程开销 < 1 µs，**零 IO** |
 * | 不弹窗不请求权限 | 没有任何权限声明、没有同意框（开关默认开，说明写在关于页） |
 * | 失败一律静默 | 上传/落盘全 `runCatching`，失败只在 [DebugLog] 留一行 |
 * | 不常驻唤醒 | 只在启动后 30 秒、进后台、6 小时兜底 Worker 三个时机 flush |
 *
 * ## 队列上限
 *
 * 内存 200 条，满了丢最旧的；落盘 [TelemetryQueue] 512 KB 封顶。
 * 两者加起来意味着「长时间离线 + 大量操作」也不会攒出一个隐私库。
 *
 * ## 关掉之后
 *
 * [setEnabled] `false` 会同时清内存队列与磁盘队列 —— 用户关掉开关，
 * 之前攒着却还没发的东西也一并丢弃，不发「临别一笔」。
 */
object Telemetry {

    private var app: Context? = null
    private var prefs: Prefs? = null
    private var queue: TelemetryQueue? = null
    private val outbox = ArrayDeque<String>(MEM_MAX)
    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var session: String = ""
    private var startedAt: Long = 0L
    private var drained = false

    /** 内存队列上限 */
    private const val MEM_MAX = 200

    /** 攒到多少条主动 flush 一次（别等 Worker 六小时） */
    private const val FLUSH_EVERY = 20

    val isStarted: Boolean get() = app != null

    /**
     * 进程启动时调一次（[cn.dkarbon.enroute.EnRouteApp.onCreate]）。
     *
     * 只做初始化与「一条 app.start」，**不上传** —— 启动那一秒不发任何网络请求。
     */
    fun init(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        runCatching {
            val p = Prefs(c)
            app = c
            prefs = p
            queue = TelemetryQueue(File(c.filesDir, "telemetry"))
            session = TelemetryId.newSession()
            startedAt = System.currentTimeMillis()
            // 首次安装时间：只用来算「安装后第几天」的档位
            if (p.telemetryFirstSeenAt <= 0L) p.telemetryFirstSeenAt = startedAt
            if (p.telemetryInstallId.isBlank()) p.telemetryInstallId = TelemetryId.newInstallId()
            flushLater(30_000L)
        }.onFailure { DebugLog.log("telemetry", "init 失败：" + it.message) }
    }

    fun isEnabled(): Boolean = prefs?.telemetryEnabled ?: false

    /**
     * 开关。关掉时**立刻**清空内存与磁盘队列（不发「临别一笔」）。
     */
    fun setEnabled(on: Boolean) {
        val p = prefs ?: return
        p.telemetryEnabled = on
        if (!on) {
            outbox.clear()
            scope.launch { runCatching { queue?.clear() } }
            app?.let { TelemetryWorker.cancel(it) }
        } else {
            app?.let { TelemetryWorker.enqueue(it) }
        }
    }

    /**
     * 记一条事件。**非阻塞**，任何线程可调。
     *
     * 事件名或字段不在白名单里 → 静默丢弃（开发期看 [DebugLog]）。
     */
    fun record(event: String, props: Map<String, String> = emptyMap()) {
        val p = prefs ?: return
        if (!p.telemetryEnabled) return
        if (!TelemetrySchema.accepts(event)) {
            DebugLog.log("telemetry", "未知事件已丢弃：" + event)
            return
        }
        val clean = TelemetrySchema.sanitize(event, props) ?: return
        val c = app ?: return
        val line = buildLine(c, event, clean, session, p.telemetryInstallId)
        if (line.toByteArray(Charsets.UTF_8).size > TelemetrySchema.EVENT_MAX_BYTES) {
            // 正常不可能出现；出现就说明有人往 props 里塞了长串
            DebugLog.log("telemetry", "事件超长已丢弃：" + event)
            return
        }
        synchronized(outbox) {
            if (outbox.size >= MEM_MAX) outbox.removeFirst()
            outbox.addLast(line)
        }
        if (outbox.size >= FLUSH_EVERY) flushAsync("batch")
    }

    /** 到点自动 flush（启动后 30 秒） */
    private fun flushLater(ms: Long) {
        scope.launch {
            delay(ms)
            runCatching { flushNow("start+30s") }
        }
    }

    /** 进程启动到现在多少毫秒（首帧耗时用得上；未 [init] 时返回 0） */
    fun msSinceStart(): Long =
        if (startedAt <= 0L) 0L else (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)

    /** fire-and-forget 版本：界面/后台回调里用 */
    fun flushAsync(reason: String) {
        scope.launch { runCatching { flushNow(reason) } }
    }

    /**
     * 落盘 + 尝试上传一次。
     *
     * 调用方负责它跑在什么线程；内部自己切 IO。
     */
    suspend fun flushNow(reason: String): Boolean = withContext(Dispatchers.IO) {
        val c = app ?: return@withContext false
        val p = prefs ?: return@withContext false
        if (!p.telemetryEnabled) return@withContext false
        drainToDisk()
        val q = queue ?: return@withContext false
        TelemetryUploader(c, q).runOnce(reason).also { ok ->
            if (!ok) DebugLog.log("telemetry", "flush 未成功（" + reason + "），事件留在队列里")
        }
    }

    /** 内存队列落盘（幂等；队列空就什么都不做） */
    private suspend fun drainToDisk() = mutex.withLock {
        val q = queue ?: return@withLock
        val lines = synchronized(outbox) {
            val copy = outbox.toList()
            outbox.clear()
            copy
        }
        if (lines.isEmpty()) return@withLock
        runCatching { q.append(lines) }.onFailure {
            DebugLog.log("telemetry", "落盘失败，内存里的这批也丢了：" + it.message)
        }
    }

    /**
     * 只落盘、不联网，且有硬超时（毫秒）。
     *
     * 专门给**崩溃兜底**用：进程马上要死，网络来不及也不能等；
     * 但把内存里那几十行写进文件只要几毫秒，值得做 —— 崩溃那一笔恰恰最有价值。
     */
    fun flushToDiskBlocking(timeoutMs: Long = 400L) {
        runCatching {
            kotlinx.coroutines.runBlocking {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { drainToDisk() }
            }
        }
    }

    /** 进后台时用：算一次会话时长再 flush */
    fun recordBackground() {
        if (!isStarted) return
        val sec = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        record("app.background", mapOf("session_s" to bucketSession(sec)))
        flushAsync("background")
    }

    /** 开关状态的展示文案（设置页用） */
    fun statusLine(): String {
        val q = queue ?: return "采集未启动"
        val n = runCatching { q.peek(Int.MAX_VALUE).size }.getOrDefault(0)
        return if (n == 0) "还没有待发的事件" else "本机攒了 " + n + " 条，下次联网时发出"
    }

    // ── 分档工具（纯函数，单测直接打）────────────────────

    /** 会话时长 → 档位 */
    fun bucketSession(sec: Long): String = when {
        sec < 30 -> "0-30"
        sec < 120 -> "30-120"
        sec < 600 -> "120-600"
        else -> "600+"
    }

    /** 数量（面数 / 行数）→ 档位，与白名单里的 FACE_BUCKETS 同一口径 */
    fun bucketCount(n: Int): String = when {
        n <= 0 -> "0"
        n == 1 -> "1"
        n <= 5 -> "2-5"
        n <= 20 -> "6-20"
        else -> "20+"
    }

    /** 搜索结果数 → 档位 */
    fun bucketResults(n: Int): String = when {
        n <= 0 -> "0"
        n <= 5 -> "1-5"
        n <= 20 -> "6-20"
        else -> "20+"
    }

    /** 搜索词长度 → 档位（**永远不记原文**） */
    fun bucketQueryLength(len: Int): String = when {
        len <= 0 -> "0"
        len <= 2 -> "1-2"
        else -> "3+"
    }

    /** 毫秒 → 档位（首帧 / 资产读取 / 下载） */
    fun bucketMs(ms: Long): String = when {
        ms < 300 -> "<300"
        ms < 800 -> "300-800"
        ms < 1_500 -> "800-1500"
        else -> "1500+"
    }

    /** 秒 → 下载/读取档位（0-3 / 3-10 / 10-30 / 30+） */
    fun bucketSeconds(sec: Long): String = when {
        sec < 3 -> "0-3"
        sec < 10 -> "3-10"
        sec < 30 -> "10-30"
        else -> "30+"
    }

    /** 资产读取毫秒 → 档位（0-50 / 50-200 / 200-600 / 600+） */
    fun bucketAssetMs(ms: Long): String = when {
        ms < 50 -> "0-50"
        ms < 200 -> "50-200"
        ms < 600 -> "200-600"
        else -> "600+"
    }

    /** 进程启动到崩溃的毫秒 → 档位（与 schema 的 ms_since_start 同口径） */
    fun bucketSinceStart(ms: Long): String = when {
        ms < 5_000 -> "0-5"
        ms < 30_000 -> "5-30"
        ms < 120_000 -> "30-120"
        else -> "120+"
    }

    /** 安装后第几天 → 档位 */
    fun bucketDaysSinceInstall(days: Long): String = when {
        days <= 0 -> "0"
        days <= 7 -> "1-7"
        days <= 30 -> "8-30"
        days <= 90 -> "31-90"
        else -> "90+"
    }

    /** ABI → 档位（不报机型） */
    fun abiBucket(): String {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return when {
            abis.any { it.contains("arm64") } -> "arm64"
            abis.any { it.contains("armeabi") } -> "arm32"
            abis.any { it.contains("x86") } -> "x86"
            else -> "unknown"
        }
    }

    // ── 行构造（纯函数）────────────────────────────────

    /**
     * 一行事件的 JSON。
     *
     * 手写而不是走 kotlinx-serialization：结构固定且字段极少，手写更快也更省电；
     * 而且这里的值全部来自白名单（字母数字、减号、点、加号），转义只是兜底。
     */
    internal fun buildLine(
        ctx: Context,
        event: String,
        props: Map<String, String>,
        session: String,
        installId: String,
        now: Long = System.currentTimeMillis(),
    ): String {
        val sb = StringBuilder(160)
        sb.append("{\"v\":").append(TelemetrySchema.VERSION)
        sb.append(",\"e\":\"").append(escape(event)).append('"')
        sb.append(",\"t\":").append(now / 1000L)
        sb.append(",\"p\":{")
        var first = true
        for ((k, v) in props) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escape(k)).append("\":\"").append(escape(v)).append('"')
        }
        sb.append("}")
        val i = TelemetryId.install(installId)
        if (i.isNotBlank()) sb.append(",\"i\":\"").append(i).append('"')
        val d = TelemetryId.daily(installId, now, java.time.ZoneId.systemDefault())
        if (d.isNotBlank()) sb.append(",\"d\":\"").append(d).append('"')
        if (session.isNotBlank()) sb.append(",\"s\":\"").append(escape(session)).append('"')
        sb.append('}')
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")
}