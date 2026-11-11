package cn.dkarbon.enroute.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import cn.dkarbon.enroute.ui.timetable.TermCalendar

/**
 * 单条上课提醒的实际执行者。
 *
 * 所有内容都在入队时（[Reminders.reschedule]）就写进 inputData 了，
 * 这里不读课表、不做计算 —— Worker 被系统叫醒时环境可能很苛刻，
 * 越少依赖越不容易失败。
 */
class ReminderWorker(
    ctx: Context,
    params: WorkerParameters,
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val name = inputData.getString(KEY_NAME).orEmpty()
        if (name.isBlank()) return Result.success()

        return runCatching {
            Reminders.show(
                ctx = applicationContext,
                name = name,
                room = inputData.getString(KEY_ROOM).orEmpty(),
                time = inputData.getString(KEY_TIME).orEmpty(),
                lead = inputData.getInt(KEY_LEAD, 0),
            )
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        const val KEY_NAME = "name"
        const val KEY_ROOM = "room"
        const val KEY_TEACHER = "teacher"
        const val KEY_TIME = "time"
        const val KEY_LEAD = "lead"
    }
}

/**
 * 每天跑一次，把未来 7 天的提醒重排一遍。
 *
 * 为什么需要它：WorkManager 的 OneTime 任务排完之后就没了，
 * 如果用户几天不开 App，滚动窗口会过期 → 提醒全静默。
 * 这个每日任务负责续命（[androidx.work.ExistingPeriodicWorkPolicy.KEEP] 保证只有一个）。
 */
class DailyRescheduleWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        val prefs = Prefs(app)
        if (!prefs.remindersEnabled) return Result.success()

        val tt = TimetableStore(app).load() ?: return Result.success()
        val week = TermCalendar(app).currentWeek()
        Reminders.ensureChannel(app)
        Reminders.reschedule(app, tt, week, enabled = true)
        return Result.success()
    }
}
