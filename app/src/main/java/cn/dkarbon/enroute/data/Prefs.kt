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

    // ── 课表卡片（m39，2026-11-21）────────────────────────
    //
    // 这一组是「把老设计接回入口」：m34 拔掉主题风格开关后，`Form.current.editorial`
    // 那套账页格课表还在编进包里跑，但没人能切过去。现在拆成独立开关，
    // **默认值 = 今天的行为**，老用户升级后一个像素都不变。

    /** 卡片版式：0 = 侵蚀纸卡（默认）/ 1 = 账页格 */
    var ttCardLayout: Int
        get() = sp.getInt("tt_card_layout", 0)
        set(v) = sp.edit().putInt("tt_card_layout", v).apply()

    /** 底色来源：0 = 四色系（默认）/ 1 = 单墨 / 2 = 无色 */
    var ttCardTint: Int
        get() = sp.getInt("tt_card_tint", 0)
        set(v) = sp.edit().putInt("tt_card_tint", v).apply()

    /** 左侧竖脊 */
    var ttCardSpine: Boolean
        get() = sp.getBoolean("tt_card_spine", true)
        set(v) = sp.edit().putBoolean("tt_card_spine", v).apply()

    /** 上课进度侵蚀（下课走完，卡片从顶部掉色） */
    var ttCardErode: Boolean
        get() = sp.getBoolean("tt_card_erode", true)
        set(v) = sp.edit().putBoolean("tt_card_erode", v).apply()

    /** 小幽灵 */
    var ttCardGhost: Boolean
        get() = sp.getBoolean("tt_card_ghost", true)
        set(v) = sp.edit().putBoolean("tt_card_ghost", v).apply()

    /** 食物图标（未排课列表左侧） */
    var ttCardFood: Boolean
        get() = sp.getBoolean("tt_card_food", true)
        set(v) = sp.edit().putBoolean("tt_card_food", v).apply()

    /** 正在上的课加 2dp 主色描边 */
    var ttCardNowRing: Boolean
        get() = sp.getBoolean("tt_card_now_ring", true)
        set(v) = sp.edit().putBoolean("tt_card_now_ring", v).apply()

    // ── 课表槽位化（m41，2026-11-21）──────────────────────
    //
    // 预设只是「一次改很多条轴」的快捷方式；覆盖位 -1 = 跟随预设。
    // 老数据的 tint / spine / erode 在首次启动时由 [sliceVersion] 搬迁成覆盖值，
    // 所以升级后画面仍然一致，不需要用户重选。

    /** 选中的预设下标（0 纸卡 / 1 账页 / 2 密排 / 3 日程 / 4 安静） */
    var ttPreset: Int
        get() = sp.getInt("tt_preset", 0)
        set(v) = sp.edit().putInt("tt_preset", v).apply()

    /** 槽位搬迁版本：< 1 表示老数据，需要从 tt_card_* 生成覆盖位 */
    var ttSlotsVersion: Int
        get() = sp.getInt("tt_slots_v", 0)
        set(v) = sp.edit().putInt("tt_slots_v", v).apply()

    /** 覆盖位：-1 跟随预设。顺序与 [cn.dkarbon.enroute.ui.timetable.TtOverrides] 一致 */
    var ttOvShape: Int
        get() = sp.getInt("tt_ov_shape", -1)
        set(v) = sp.edit().putInt("tt_ov_shape", v).apply()
    var ttOvDecor: Int
        get() = sp.getInt("tt_ov_decor", -1)
        set(v) = sp.edit().putInt("tt_ov_decor", v).apply()
    var ttOvMeta: Int
        get() = sp.getInt("tt_ov_meta", -1)
        set(v) = sp.edit().putInt("tt_ov_meta", v).apply()
    var ttOvBadge: Int
        get() = sp.getInt("tt_ov_badge", -1)
        set(v) = sp.edit().putInt("tt_ov_badge", v).apply()
    var ttOvErosion: Int
        get() = sp.getInt("tt_ov_erosion", -1)
        set(v) = sp.edit().putInt("tt_ov_erosion", v).apply()
    var ttOvMotion: Int
        get() = sp.getInt("tt_ov_motion", -1)
        set(v) = sp.edit().putInt("tt_ov_motion", v).apply()
    var ttOvTone: Int
        get() = sp.getInt("tt_ov_tone", -1)
        set(v) = sp.edit().putInt("tt_ov_tone", v).apply()
    var ttOvHeader: Int
        get() = sp.getInt("tt_ov_header", -1)
        set(v) = sp.edit().putInt("tt_ov_header", v).apply()
    var ttOvGrid: Int
        get() = sp.getInt("tt_ov_grid", -1)
        set(v) = sp.edit().putInt("tt_ov_grid", v).apply()
    var ttOvBackdrop: Int
        get() = sp.getInt("tt_ov_backdrop", -1)
        set(v) = sp.edit().putInt("tt_ov_backdrop", v).apply()
    var ttOvBgMotion: Int
        get() = sp.getInt("tt_ov_bg_motion", -1)
        set(v) = sp.edit().putInt("tt_ov_bg_motion", v).apply()
    var ttOvTess: Int
        get() = sp.getInt("tt_ov_tess", -1)
        set(v) = sp.edit().putInt("tt_ov_tess", v).apply()
    var ttOvTessMode: Int
        get() = sp.getInt("tt_ov_tess_mode", -1)
        set(v) = sp.edit().putInt("tt_ov_tess_mode", v).apply()

    /**
     * 取色来源（票 16 追加项②）：0 = 固定农业色板；1 = 跟随壁纸取色（仅 Android 12+ 生效）。
     *
     * 存的是**用户意图**，不是「本机能不能」—— 换到低版本机型时意图还在，
     * 但主题层会自己忽略（见 `DynamicColorRules.available`）。
     */
    var colorSource: Int
        get() = sp.getInt("color_source", 0)
        set(v) = sp.edit().putInt("color_source", v).apply()

    // ── 自定义配色（票 25）────────────────────────────────
    //
    // 四个槽分开存：**浅色与深色各自独立**（纸白上达标的主色压到黑土上就是一团糊）。
    // 值 = ARGB 整数，`0` = 该槽没设过（不是黑色）。
    // 与主题设置同在本文件里，所以「清除所有数据」（[clearAll]）会一起清掉。

    /** 浅色·主色 */
    var customPrimaryLight: Int
        get() = sp.getInt("custom_primary_light", 0)
        set(v) = sp.edit().putInt("custom_primary_light", v).apply()

    /** 浅色·强调色 */
    var customAccentLight: Int
        get() = sp.getInt("custom_accent_light", 0)
        set(v) = sp.edit().putInt("custom_accent_light", v).apply()

    /** 深色·主色 */
    var customPrimaryDark: Int
        get() = sp.getInt("custom_primary_dark", 0)
        set(v) = sp.edit().putInt("custom_primary_dark", v).apply()

    /** 深色·强调色 */
    var customAccentDark: Int
        get() = sp.getInt("custom_accent_dark", 0)
        set(v) = sp.edit().putInt("custom_accent_dark", v).apply()

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

    // ── 电子木鱼「功德」（纯玩具，只存本机，不联网不上传）────────────────
    //
    // 2026-11-14 改：从「全设备一个整数」改成**按学号分开记 + 今日/累计两栏**。
    // 键都带账户前缀隔离，互不污染：
    //   merit_total_<key> / merit_day_<key> / merit_today_<key>
    // <key> = 已登录时的学号；未登录固定 "anon"。

    /** 累计功德（该账户） */
    fun meritTotal(key: String): Int = sp.getInt("merit_total_$key", 0)

    fun setMeritTotal(key: String, v: Int) {
        sp.edit().putInt("merit_total_$key", v).apply()
    }

    /** 存档里记的「今天」是哪天（`yyyy-MM-dd`；空 = 没存过） */
    fun meritDay(key: String): String = sp.getString("merit_day_$key", "").orEmpty()

    /** 今日功德 */
    fun meritToday(key: String): Int = sp.getInt("merit_today_$key", 0)

    fun setMeritToday(key: String, day: String, todayCount: Int) {
        sp.edit().putString("merit_day_$key", day).putInt("merit_today_$key", todayCount).apply()
    }

    /** `anon` 的累计（首次登录迁移用）；迁完置 0，避免重复并入 */
    fun clearAnonMerit() {
        sp.edit().putInt("merit_total_anon", 0).putInt("merit_today_anon", 0).apply()
    }

    /**
     * 老 key `merit`（全设备一整数）的一次性迁移。
     *
     * 迁到 [key] 的累计上并删掉旧 key —— 老用户打开不该发现功德归零。
     * 用 `merit_migrated` 防重复（万一旧 key 又被谁写回来）。
     */
    fun migrateLegacyMerit(key: String) {
        if (sp.getBoolean("merit_migrated", false)) return
        val legacy = sp.getInt("merit", 0)
        if (legacy > 0) {
            sp.edit().putInt("merit_total_$key", MeritRules.migrateLegacy(meritTotal(key), legacy)).apply()
        }
        sp.edit().remove("merit").putBoolean("merit_migrated", true).apply()
    }

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

    /**
     * 调试日志是否保存到本机（票 20 / D2）。
     *
     * **默认开**：崩溃/被杀时的日志才是最有用的，事后没法补。release 版同样生效
     * （不用 `BuildConfig.DEBUG` 写死，主人在发布版里也可能要导出）。
     */
    var debugLogToFile: Boolean
        get() = sp.getBoolean("debug_log_file", true)
        set(v) = sp.edit().putBoolean("debug_log_file", v).apply()

    /** 地图「共建引导」是否已自动弹过一次（票 12 §8.3；「清除所有数据」会一起清掉） */
    var mapGuideSeen: Boolean
        get() = sp.getBoolean("map_guide_seen", false)
        set(v) = sp.edit().putBoolean("map_guide_seen", v).apply()

    /** 连续自动续期失败次数（达到上限就停止自动尝试） */
    var credFailCount: Int
        get() = sp.getInt("cred_fail_count", 0)
        set(v) = sp.edit().putInt("cred_fail_count", v).apply()

    // ── 静默使用统计（m40，见 docs/规划-使用数据采集.md）────────────
    //
    // 三个键各司其职，**都不含任何身份信息**：
    //   ① 开关（默认开 —— 规划里的立场是「采发生了什么，不采是谁发生的」）
    //   ② install_id：本机随机 16 字节 hex，**永不上传**，只用来算当日标识 d 的盐
    //   ③ 首次安装时间：只用来算「安装后第几天」的档位

    /** 匿名使用统计开关；默认**开**（关于页有一句义务告知） */
    var telemetryEnabled: Boolean
        get() = sp.getBoolean("telemetry_on", true)
        set(v) = sp.edit().putBoolean("telemetry_on", v).apply()

    /** 本机安装标识（32 位 hex；空串 = 还没生成）。**不上传**，只作当日标识的盐 */
    var telemetryInstallId: String
        get() = sp.getString("telemetry_install_id", "").orEmpty()
        set(v) = sp.edit().putString("telemetry_install_id", v).apply()

    /** 首次见到本机的时间（epoch ms；0 = 还没记过） */
    var telemetryFirstSeenAt: Long
        get() = sp.getLong("telemetry_first_seen", 0L)
        set(v) = sp.edit().putLong("telemetry_first_seen", v).apply()

    /** 清空本文件里的所有偏好（供 [DataWiper] 用；会话密文也在本文件里，一并清掉） */
    fun clearAll() {
        sp.edit().clear().commit()
    }
}
