package cn.dkarbon.enroute.data.telemetry

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.dkarbon.enroute.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * 兜底 flush：每 6 小时一次，**只在有网时**跑。
 *
 * ## 为什么还需要它
 *
 * 平时 flush 有三个时机（启动后 30 秒 / 进后台 / 攒够 20 条），
 * 足够覆盖「正常用一用」；但有一种情况会漏：用户一直在前台用、
 * 内存队列没攒够 20 条、又从不退出 —— 那几天都不发一条。
 * 这个 Worker 就是给这种情况兜底的，代价是每 6 小时一次 `getInstance`。
 *
 * 注意它**不是**主路径：电池优化把 WorkManager 掐了也不影响其它三条。
 */
class TelemetryWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!Prefs(app).telemetryEnabled) return Result.success()
        return runCatching {
            Telemetry.flushNow("periodic")
            Result.success()
        }.getOrElse { Result.success() }   // 永不让系统看见「采集任务失败」这个存在
    }

    companion object {
        private const val NAME = "telemetry-flush"

        /** 幂等入队（KEEP：已经排上就不动它） */
        fun enqueue(ctx: Context) {
            runCatching {
                val req = PeriodicWorkRequestBuilder<TelemetryWorker>(6, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build()
                WorkManager.getInstance(ctx.applicationContext)
                    .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
            }
        }

        /** 关掉采集时连兜底任务一起撤掉：不留下一个「还会醒来的空壳」 */
        fun cancel(ctx: Context) {
            runCatching {
                WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(NAME)
            }
        }
    }
}