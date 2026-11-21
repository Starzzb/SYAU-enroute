package cn.dkarbon.enroute

import android.app.Application
import cn.dkarbon.enroute.data.Prefs
import cn.dkarbon.enroute.data.UpdateCenter
import cn.dkarbon.enroute.data.telemetry.Telemetry
import cn.dkarbon.enroute.data.telemetry.TelemetryWorker

/**
 * 进程级单例。
 *
 * 做三件事，全部**不占首帧**：
 *
 * 1. 启动时的静默更新检查（票 15）：每天最多一次、失败不打扰、绝不后台下载，见 [UpdateCenter.maybeAutoCheckAsync]。
 *    放这里而不是 MainActivity，因为它在 setContent 之前跑，也避免往 TimetableViewModel 里塞与课表无关的状态。
 * 2. 匿名使用统计初始化（m40）：[Telemetry.init] 只建队列、记一条 app.start，**不发任何网络请求**
 *    （真正的上传在启动 30 秒后）。
 * 3. 崩溃兜底：把「崩了一次、崩在哪一类、活了几秒」记一条 app.crash 并落盘。
 *    不记堆栈、不记消息原文 —— 那里面常常带着学号与课表内容。
 */
class EnRouteApp : Application() {

    private val startedAt: Long = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        runCatching { UpdateCenter(this).maybeAutoCheckAsync() }

        runCatching {
            Telemetry.init(this)
            val firstSeen = Prefs(this).telemetryFirstSeenAt
            if (Prefs(this).telemetryEnabled) TelemetryWorker.enqueue(this)
            Telemetry.record(
                "app.start",
                mapOf(
                    "cold" to "true",
                    "ver" to BuildConfig.VERSION_NAME,
                    "abi" to Telemetry.abiBucket(),
                    "ms_since_install" to Telemetry.bucketDaysSinceInstall(
                        (System.currentTimeMillis() - firstSeen) / 86_400_000L,
                    ),
                ),
            )
            installCrashReporter()
        }
    }

    /**
     * 崩溃兜底：**只有一个线程一个 handler**，装上之后把原来的接回去。
     *
     * [Thread.setDefaultUncaughtExceptionHandler] 是覆盖式的：不接回去，我们的 handler
     * 就会吃掉系统默认的崩溃处理，系统弹窗与 tombstone 都没了。
     */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, err ->
            runCatching {
                Telemetry.record(
                    "app.crash",
                    mapOf(
                        // 只记「崩在哪个层面」，**连异常类名都不记** ——
                        // 类名常常直接暴露业务（E.g. TimetableParseException），比堆栈更贴脸
                        "where" to crashWhere(err),
                        "ver" to BuildConfig.VERSION_NAME,
                        "ms_since_start" to Telemetry.bucketSinceStart(
                            System.currentTimeMillis() - startedAt,
                        ),
                    ),
                )
                // 进程马上要死：只落盘、不联网，400ms 封顶
                Telemetry.flushToDiskBlocking()
            }
            previous?.uncaughtException(thread, err)
        }
    }

    /** 把异常归到四个层面之一（schema 白名单就这四个 + other） */
    private fun crashWhere(err: Throwable): String {
        var t: Throwable? = err
        var depth = 0
        while (t != null && depth < 6) {
            val n = t.javaClass.name
            if (n.contains("IOException") || n.contains("JSONException")) return "io"
            if (n.contains("Socket") || n.contains("Http") || n.contains("UnknownHost")) return "net"
            if (n.contains("Parse") || n.contains("NumberFormat")) return "parse"
            t = t.cause
            depth++
        }
        return if (err.stackTrace.any { it.className.startsWith("androidx.compose") }) "ui" else "other"
    }
}