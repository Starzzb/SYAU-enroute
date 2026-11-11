package cn.dkarbon.enroute.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地偏好（SharedPreferences）。
 *
 * 只放**视图设置**，不放课表数据本身、更不放账号密码：
 *   · 课表数据走 [TimetableStore]（filesDir 里的 JSON 文件，可导出可备份）
 *   · URP 账号密码**只存在于内存**，登录一次抓完即弃（见 UrpClient 的 KDoc）
 */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("enroute", Context.MODE_PRIVATE)

    /** 当前查看第几周；-1 表示用学期起止日估算 */
    var timetableWeek: Int
        get() = sp.getInt("tt_week", -1)
        set(v) = sp.edit().putInt("tt_week", v).apply()

    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色 */
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(v) = sp.edit().putInt("theme_mode", v).apply()

    /**
     * 上课提醒总开关。
     *
     * 默认**开**：这功能就是主诉求之一，让人再去翻设置打开不现实。
     * 关掉时 [Reminders.cancelAll] 会清掉已排的任务，不会留幽灵提醒。
     */
    var remindersEnabled: Boolean
        get() = sp.getBoolean("reminders_on", true)
        set(v) = sp.edit().putBoolean("reminders_on", v).apply()

    /**
     * 每日重排任务的门控：距上次排程超过 24h 才需要重新 enqueue。
     * 用途：冷启动时直接跳过 WorkManager 调用（首次 getInstance 要 30–120ms）。
     */
    fun shouldRescheduleDaily(): Boolean =
        System.currentTimeMillis() - sp.getLong("last_reschedule", 0L) > 24 * 60 * 60 * 1000L

    fun markDailyRescheduled() {
        sp.edit().putLong("last_reschedule", System.currentTimeMillis()).apply()
    }

    /** 电子木鱼的「功德」累计数（纯玩具，只存本机） */
    var merit: Int
        get() = sp.getInt("merit", 0)
        set(v) = sp.edit().putInt("merit", v).apply()

    /** 木鱼敲击声开关 */
    var meritSound: Boolean
        get() = sp.getBoolean("merit_sound", true)
        set(v) = sp.edit().putBoolean("merit_sound", v).apply()

    /** 清空本文件里的所有偏好（供 [DataWiper] 用） */
    fun clearAll() {
        sp.edit().clear().commit()
    }
}
