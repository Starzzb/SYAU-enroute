package cn.dkarbon.enroute.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cn.dkarbon.enroute.MainActivity
import cn.dkarbon.enroute.R
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 上课提醒。
 *
 * 设计口径（2026-11-10 主人定）：**课前 60 分钟 + 课前 30 分钟各响一次**，
 * 作息表沿用硬编码（[SchoolClock]），不做配置项。
 *
 * 为什么用 WorkManager 而不是 AlarmManager：
 *   · 免 `SCHEDULE_EXACT_ALARM` 权限（Android 13+ 要单独申请，还容易被用户关掉）
 *   · 课表提醒容忍几分钟误差，不需要精确闹钟
 * 代价是「国内 ROM 杀后台」可能漏 —— 所以额外做了两条兜底：
 *   ① 每次打开 App 都重排未来 7 天（[reschedule]）
 *   ② 每天一次周期性重排（[DailyRescheduleWorker]）
 */
object Reminders {

    const val CHANNEL_ID = "class_reminder"
    private const val UNIQUE_PREFIX = "reminder-"
    private const val DAILY_WORK = "reminder-daily"

    /** 提前量（分钟）：一小时 + 半小时 */
    val LEADS_MIN: List<Int> = listOf(60, 30)

    /** 一次排未来几天（滚动窗口，配合每日重排就够了） */
    private const val DAYS_AHEAD = 7

    // ── 通知渠道 ──────────────────────────────────────────

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "上课提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "课前 60 / 30 分钟提醒下一节课"
            },
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    // ── 排 / 清 ───────────────────────────────────────────

    /**
     * 重排未来 [DAYS_AHEAD] 天的所有提醒。
     *
     * 幂等：每次全清再全排（[ExistingWorkPolicy.REPLACE]），课表一改立刻覆盖旧的，
     * 不会出现「改了课表结果旧的提醒还在响」。
     */
    fun reschedule(ctx: Context, tt: Timetable, week: Int?, enabled: Boolean = true) {
        val wm = WorkManager.getInstance(ctx)
        wm.cancelAllWorkByTag(UNIQUE_PREFIX.trimEnd('-'))
        if (!enabled || tt.courses.isEmpty()) return

        val now = LocalDateTime.now()
        var scheduled = 0

        for (dayOffset in 0 until DAYS_AHEAD) {
            val date: LocalDate = now.toLocalDate().plusDays(dayOffset.toLong())
            val summer = SchoolClock.isSummerTime(date)
            // 未来某天的周次 = 今天的周次 + 天偏移所在的周差
            val weekOfDay = week?.let { w ->
                val deltaWeeks = (date.toEpochDay() - now.toLocalDate().toEpochDay()) / 7
                (w + deltaWeeks).toInt()
            }

            for (slot in ScheduleMath.slotsOf(tt, date, weekOfDay, summer)) {
                for (lead in LEADS_MIN) {
                    val triggerAt = date.atStartOfDay()
                        .plusMinutes(slot.startMin.toLong())
                        .minusMinutes(lead.toLong())
                    if (triggerAt.isBefore(now)) continue

                    val delay = Duration.between(now, triggerAt)
                    val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                        .setInitialDelay(delay)
                        .setInputData(
                            Data.Builder()
                                .putString(ReminderWorker.KEY_NAME, slot.course.name)
                                .putString(ReminderWorker.KEY_ROOM, slot.course.roomShort)
                                .putString(ReminderWorker.KEY_TEACHER, slot.course.teacher)
                                .putString(ReminderWorker.KEY_TIME, slot.rangeLabel)
                                .putInt(ReminderWorker.KEY_LEAD, lead)
                                .build(),
                        )
                        .addTag(UNIQUE_PREFIX.trimEnd('-'))
                        .build()

                    val id = "${UNIQUE_PREFIX}${date}_${slot.startMin}_$lead"
                    wm.enqueueUniqueWork(id, ExistingWorkPolicy.REPLACE, req)
                    scheduled++
                }
            }
        }
        android.util.Log.i("EnRouteReminder", "已排 $scheduled 条提醒（未来 $DAYS_AHEAD 天）")
    }

    /** 清掉所有已排的提醒 */
    fun cancelAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(UNIQUE_PREFIX.trimEnd('-'))
    }

    /** 起一个每天跑一次的重排任务（防 ROM 杀后台导致时间窗过期） */
    fun ensureDailyReschedule(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            DAILY_WORK,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<DailyRescheduleWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .addTag(UNIQUE_PREFIX.trimEnd('-'))
                .build(),
        )
    }

    // ── 发通知 ────────────────────────────────────────────

    fun show(ctx: Context, name: String, room: String, time: String, lead: Int) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)

        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = if (lead > 0) "$lead 分钟后上课 · $name" else "上课 · $name"
        val text = buildString {
            append(time)
            if (room.isNotBlank()) append(" · ").append(room)
        }

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify((name.hashCode() xor lead), n)
        }
    }
}
