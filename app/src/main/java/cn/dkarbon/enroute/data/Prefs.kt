package cn.dkarbon.enroute.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 本地偏好（SharedPreferences）。
 *
 * 只放**视图设置**与**加密后的会话存档**，不放课表数据本身、更不放账号密码：
 *   · 课表数据走 [TimetableStore]（filesDir 里的 JSON 文件，可导出可备份）
 *   · URP 账号密码**只存在于内存**，登录一次抓完即弃（见 UrpClient 的 KDoc）
 *   · 会话 cookie 以 AES/GCM 密文形式存本文件（见 [SessionStore]），
 *     明文只出现在解密后的内存里；**密码从不进这里**
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
     * 视觉风格：0 硬朗农业 / 1 学院纸本。
     *
     * 与 [themeMode] **正交**：风格管语汇（农业硬朗 / 学院纸本），主题管明暗。
     * 默认 0（硬朗农业）—— 换肤是选配，不能让老用户升级后忽然变样。
     */
    var styleSet: Int
        get() = sp.getInt("style_set", 0)
        set(v) = sp.edit().putInt("style_set", v).apply()

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

    // ── 资讯页（2026-11-13 评审规格 §10.3）─────────────────

    /** 上次成功刷新资讯的时间戳；0 = 从未成功过 */
    var feedLastSyncAt: Long
        get() = sp.getLong("feed_last_sync_at", 0L)
        set(v) = sp.edit().putLong("feed_last_sync_at", v).apply()

    /** 频道卡片密度：true = 紧凑。默认就业/新闻紧凑，其余标准 */
    fun feedCompact(channel: String): Boolean =
        sp.getBoolean("feed_density_$channel", channel == "就业" || channel == "新闻")

    fun setFeedCompact(channel: String, compact: Boolean) {
        sp.edit().putBoolean("feed_density_$channel", compact).apply()
    }

    /**
     * 截止提醒条的「今日已关闭」记录：当日日期 + 当时紧急条目 key 集合。
     * 同日同集合不再出现；出现新紧急条目或跨天 → 重新出现（关闭不是永久屏蔽）。
     */
    var deadlineDismissDay: String
        get() = sp.getString("feed_dl_dismiss_day", "").orEmpty()
        set(v) = sp.edit().putString("feed_dl_dismiss_day", v).apply()

    var deadlineDismissKeys: Set<String>
        get() = sp.getStringSet("feed_dl_dismiss_keys", emptySet()).orEmpty()
        set(v) = sp.edit().putStringSet("feed_dl_dismiss_keys", v).apply()

    // ── 登录会话存档（票 04）────────────────────────────────
    //
    // 三个键只由 [SessionStore] 读写，别的地方不要直接碰：
    //   ① 密文（`v1:Base64(IV‖密文)`，key 在 AndroidKeyStore 里）
    //   ② 存盘时间（只用于给用户看「上次登录：x 小时前」，不参与任何判断）
    //   ③ 过期标记（服务器已不认这份会话；cookie 已清，但这个事实要留到下次登录成功）

    /** 加密封装后的会话；空串 = 本机没存过会话 */
    var sessionSealed: String
        get() = sp.getString("session_sealed", "").orEmpty()
        set(v) = sp.edit().putString("session_sealed", v).apply()

    /** 会话存盘时间（epoch ms）；0 = 没存过 */
    var sessionSavedAt: Long
        get() = sp.getLong("session_saved_at", 0L)
        set(v) = sp.edit().putLong("session_saved_at", v).apply()

    /** true = 上次验活判定这份会话已过期（界面要说「已过期」而不是「未登录」） */
    var sessionExpired: Boolean
        get() = sp.getBoolean("session_expired", false)
        set(v) = sp.edit().putBoolean("session_expired", v).apply()

    // ── 静默续期凭据（票 14）────────────────────────────────
    //
    // 与上面的会话密文**分开**存放，键名按票面契约：`cred_sealed` / `cred_saved_at`。
    // 只由 [CredentialStore] 读写，别的代码不要直接碰。

    /** AES/GCM 加密后的「学号<TAB>密码」；空串 = 本机没存凭据 */
    var credSealed: String
        get() = sp.getString("cred_sealed", "").orEmpty()
        set(v) = sp.edit().putString("cred_sealed", v).apply()

    /** 凭据存盘时间（epoch ms）；0 = 没存过 */
    var credSavedAt: Long
        get() = sp.getLong("cred_saved_at", 0L)
        set(v) = sp.edit().putLong("cred_saved_at", v).apply()

    /** 用户是否开启「保持登录（自动续期）」—— 默认**关闭** */
    var credEnabled: Boolean
        get() = sp.getBoolean("cred_enabled", false)
        set(v) = sp.edit().putBoolean("cred_enabled", v).apply()

    /** 是否已经问过「要不要保持登录」（只问一次，问过就不再弹） */
    var credPrompted: Boolean
        get() = sp.getBoolean("cred_prompted", false)
        set(v) = sp.edit().putBoolean("cred_prompted", v).apply()

    /** 地图「共建引导」是否已自动弹过一次（票 12 §8.3；「清除所有数据」会一起清掉） */
    var mapGuideSeen: Boolean
        get() = sp.getBoolean("map_guide_seen", false)
        set(v) = sp.edit().putBoolean("map_guide_seen", v).apply()

    /** 连续自动续期失败次数（达到上限就停止自动尝试） */
    var credFailCount: Int
        get() = sp.getInt("cred_fail_count", 0)
        set(v) = sp.edit().putInt("cred_fail_count", v).apply()

    /** 清空本文件里的所有偏好（供 [DataWiper] 用；会话密文也在本文件里，一并清掉） */
    fun clearAll() {
        sp.edit().clear().commit()
    }
}
