package cn.dkarbon.enroute.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.dkarbon.enroute.data.AcademicApi
import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.Prefs
import cn.dkarbon.enroute.data.Reminders
import cn.dkarbon.enroute.data.SessionProbe
import cn.dkarbon.enroute.data.SessionRules
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.SessionStore
import cn.dkarbon.enroute.data.Timetable
import cn.dkarbon.enroute.data.TimetableStore
import cn.dkarbon.enroute.data.UrpClient
import cn.dkarbon.enroute.ui.timetable.TermCalendar
import cn.dkarbon.enroute.ui.timetable.TtAxis
import cn.dkarbon.enroute.ui.timetable.TtDecorSpine
import cn.dkarbon.enroute.ui.timetable.TtOverrides
import cn.dkarbon.enroute.ui.timetable.TtPresetCount
import cn.dkarbon.enroute.ui.timetable.TtPresets
import cn.dkarbon.enroute.ui.timetable.TtSlots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全部业务逻辑（课表 / 考试 / 成绩 / 提醒 / 登录态）。
 *
 * 四条硬规矩：
 *   1. **密码只在内存**：登录那一次用完即弃，不写 Prefs、不写日志。
 *      会话 cookie 走加密存档（[SessionStore]，票 04），冷启动恢复后先验活再报「已登录」。
 *   2. **课表数据只落一个文件**（[TimetableStore]），登录抓取和 JSON 导入走同一条保存路径。
 *   3. **任何一次课表变化都重排提醒**（[syncReminders]），否则改了课表旧提醒还在响。
 *   4. **数据诚实**：会话过期就说过期（[SessionState.Expired]），不假装已登录，也不静默清空。
 */
class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(AppState())
    val ui: StateFlow<AppState> = _ui.asStateFlow()

    private val prefs = Prefs(app)
    private val store = TimetableStore(app)
    private val termCalendar = TermCalendar(app)
    private val sessionStore = SessionStore(app)
    /** 自定义课程存档（票 19）：与教务数据分文件，重拉课表不影响它 */
    private val customStore = cn.dkarbon.enroute.data.custom.CustomCourseStore(app)

    /** 调试日志落盘（票 20 / D2）：挂上后 DebugLog 每条都会异步追加到本机文件 */
    private val debugFile by lazy {
        cn.dkarbon.enroute.data.DebugLogFile(java.io.File(getApplication<Application>().filesDir, "debug"))
    }

    /** 静默续期凭据（票 14）：本机 Keystore 加密，默认关闭 */
    private val credStore = cn.dkarbon.enroute.data.CredentialStore(app)

    /**
     * 教务数据三档缓存（票 24）：`filesDir/academic/cache.json`。
     *
     * 判据在 [cn.dkarbon.enroute.data.CachePolicy]（纯函数、单测锁死）；这里只管「抓/不抓、存/不存」。
     * [cache] 是内存镜像，只在 IO 线程写盘（见 [commitCache]）。
     */
    private val academicCacheStore = cn.dkarbon.enroute.data.AcademicCacheStore(app)
    private var cache = cn.dkarbon.enroute.data.AcademicCache()

    /**
     * 登录成功但「保持登录」还没问过时，把这次输入的凭据**暂存在内存**里。
     *
     * 只在「说明卡还开着」这段时间存在：用户点「启用」就把它们加密存盘，点「不了」就丢掉。
     * 不缓存到别的任何地方（不落盘、不进日志）。
     */
    private var pendingCred: cn.dkarbon.enroute.data.CredentialStore.Credentials? = null

    /**
     * URP 会话：内存里的工作副本。
     * cookie 的**加密存档**在 Prefs 里（[SessionStore]），冷启动会自动恢复 + 验活。
     */
    private var session: UrpClient.Session? = null

    /** 由 Activity 注册的「选 JSON 文件」入口（SAF 要在 Activity 侧注册 launcher） */
    var onPickFile: (() -> Unit)? = null

    /** 由 Activity 注册的「申请通知权限」入口（API 33+ 需要） */
    var onRequestNotifPermission: (() -> Unit)? = null

    /**
     * ⚠️ 启动路径纪律（2026-11-10 性能专家审查后定的）：
     * init 里**只允许内存赋值**。所有磁盘读取（课表 JSON、SharedPreferences）、
     * WorkManager 排程、通知渠道创建全部挪到 IO 线程 —— 这些原来都压在主线程首帧前，
     * 是中端机上几百毫秒的浪费。
     */
    init {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                // 一次批量读 prefs，不逐个 getXxx（每次都是一次 XML 查找）
                val week = prefs.timetableWeek
                val theme = prefs.themeMode
                // 风格读盘即归一：学院纸本已下线，老用户存量值平滑回落到硬朗农业（票 16）
                val style = cn.dkarbon.enroute.ui.theme.StyleSet.normalize(prefs.styleSet)
                val colorSource = cn.dkarbon.enroute.ui.theme.ColorSource.normalize(prefs.colorSource)
                // 自定义配色四个槽（票 25）：一次读齐，缺的槽留 0
                val palette = cn.dkarbon.enroute.ui.theme.CustomPalette(
                    primaryLight = prefs.customPrimaryLight,
                    accentLight = prefs.customAccentLight,
                    primaryDark = prefs.customPrimaryDark,
                    accentDark = prefs.customAccentDark,
                )
                val custom = customStore.load()
                val remind = prefs.remindersEnabled
                // 课表卡片偏好（m39）：一次读齐，和上面那批一起进 Loaded
                val cardLayout = prefs.ttCardLayout
                val cardTint = prefs.ttCardTint
                val cardSpine = prefs.ttCardSpine
                val cardErode = prefs.ttCardErode
                val cardGhost = prefs.ttCardGhost
                val cardFood = prefs.ttCardFood
                val cardNowRing = prefs.ttCardNowRing
                Loaded(
                    store.load(), week, theme, style, remind, colorSource, custom, palette,
                    cardLayout = cardLayout,
                    cardTint = cardTint,
                    cardSpine = cardSpine,
                    cardErode = cardErode,
                    cardGhost = cardGhost,
                    cardFood = cardFood,
                    cardNowRing = cardNowRing,
                )
            }
            _ui.value = _ui.value.copy(
                timetable = saved.tt,
                timetableWeek = saved.week,
                themeMode = saved.theme,
                styleSet = saved.style,
                ttCardLayout = saved.cardLayout,
                ttCardTint = saved.cardTint,
                ttCardSpine = saved.cardSpine,
                ttCardErode = saved.cardErode,
                ttCardGhost = saved.cardGhost,
                ttCardFood = saved.cardFood,
                ttCardNowRing = saved.cardNowRing,
                colorSource = saved.colorSource,
                customPalette = saved.palette,
                customCourses = saved.custom,
                remindersEnabled = saved.remind,
                timetableMsg = if (saved.tt == null) "" else "已载入 ${saved.tt.courses.size} 门课",
            )
            // 槽位化（m41）：老 tint/spine/erode 搬进覆盖位，再读齐预设与覆盖。
            migrateSlotsIfNeeded()
            _ui.value = _ui.value.copy(
                ttPreset = prefs.ttPreset,
                ttOvShape = prefs.ttOvShape,
                ttOvDecor = prefs.ttOvDecor,
                ttOvMeta = prefs.ttOvMeta,
                ttOvBadge = prefs.ttOvBadge,
                ttOvErosion = prefs.ttOvErosion,
                ttOvMotion = prefs.ttOvMotion,
                ttOvTone = prefs.ttOvTone,
                ttOvHeader = prefs.ttOvHeader,
                ttOvGrid = prefs.ttOvGrid,
                ttOvBackdrop = prefs.ttOvBackdrop,
                ttOvBgMotion = prefs.ttOvBgMotion,
                ttOvTess = prefs.ttOvTess,
                ttOvTessMode = prefs.ttOvTessMode,
            )
            // 读盘后立刻把卡片配置推给绘制层（否则冷启动第一帧还是默认值）
            // ①a 票 24：先把教务数据的三档缓存灌进界面 —— **这一步不发任何网络请求**，
            //     断网冷启动也能立刻看到身份 / 成绩 / 考试（失败绝不覆盖旧值的另一半在这里）。
            loadAcademicCacheIntoState()
            // ①b 调试日志落盘（票 20 / D2）：启动时轮转一次（清过期/超限），再挂上通道。
            //    全程 runCatching —— 日志 I/O 出错绝不许影响启动。
            runCatching {
                if (prefs.debugLogToFile) {
                    withContext(Dispatchers.IO) { debugFile.rotate() }
                    cn.dkarbon.enroute.data.DebugLog.attachFile(debugFile)
                    cn.dkarbon.enroute.data.DebugLog.log(
                        "LOG",
                        "落盘已开启（保留 ${cn.dkarbon.enroute.data.DebugLogFile.MAX_DAYS} 天 / 上限 " +
                            "${cn.dkarbon.enroute.data.DebugLogFile.MAX_BYTES / 1024 / 1024}MB，已占用 " +
                            "${debugFile.usageBytes() / 1024}KB）",
                    )
                }
            }

            // ② 恢复登录态（票 04）。放在课表之后：课表是本地文件，先让它出来，
            //    会话恢复要解密（Keystore 首次还要建 key），不能挡着首屏。
            restoreSession()
            withContext(Dispatchers.IO) {
                Reminders.ensureChannel(app)
                // 每日重排任务有 24h 门控，冷启动通常直接跳过（不再每次 enqueue）
                if (prefs.shouldRescheduleDaily()) {
                    Reminders.ensureDailyReschedule(app)
                    prefs.markDailyRescheduled()
                }
                saved.tt?.let { syncReminders(it) }
            }
        }
    }

    /** init 里一次批量读盘的载体（避免在主线程分多次读 SharedPreferences） */
    private data class Loaded(
        val tt: Timetable?,
        val week: Int,
        val theme: Int,
        val style: Int,
        val remind: Boolean,
        val colorSource: Int = 0,
        val custom: List<cn.dkarbon.enroute.data.custom.CustomCourse> = emptyList(),
        val palette: cn.dkarbon.enroute.ui.theme.CustomPalette =
            cn.dkarbon.enroute.ui.theme.CustomPalette.NONE,
        // 课表卡片偏好（m39）
        val cardLayout: Int = 0,
        val cardTint: Int = 0,
        val cardSpine: Boolean = true,
        val cardErode: Boolean = true,
        val cardGhost: Boolean = true,
        val cardFood: Boolean = true,
        val cardNowRing: Boolean = true,
    )

    // ── 登录态：冷启动恢复 + 验活（票 04）──────────────────

    /**
     * 从加密存档恢复会话，并**先验活再对外宣称已登录**。
     *
     * 三条路径：
     *   · 解出 cookie → 标 [SessionState.Active] + 「正在校验」→ 真去问一次只读接口；
     *   · 只有「上次过期」的记号 → 标 [SessionState.Expired]（不退回「未登录」，那样等于骗人）；
     *   · 什么都没有 → 保持 [SessionState.None]。
     *
     * 解密的耗时不确定（首次要建 Keystore key），因此整体在 IO 线程。
     */
    private suspend fun restoreSession() {
        val restored = withContext(Dispatchers.IO) {
            runCatching { sessionStore.restore() }.getOrNull()
        }
        if (restored == null) {
            // 存档层自己出错了（极少见）：照实当未登录，不猜
            DebugLog.log("SESSION", "会话存档读取异常，按未登录处理")
            return
        }

        val s = restored.session
        if (s != null) {
            session = s
            _ui.value = _ui.value.copy(
                sessionState = SessionRules.stateOf(hasSession = true, probe = null),
                userName = restored.displayName,
                sessionCheckMsg = "已恢复上次的登录，正在校验…",
            )
            // cookie 数量可以记，cookie 内容绝不进日志
            DebugLog.log("SESSION", "冷启动恢复会话：cookie ${s.cookiesSnapshot().size} 项（内容不记）")
            verifyRestoredSession(s)
            return
        }

        // L2：cookie 没了/过期了，但本机存了凭据且用户开了「保持登录」→ 静默续期一次
        val creds = withContext(Dispatchers.IO) {
            runCatching { if (credStore.isEnabled()) credStore.read() else null }.getOrNull()
        }
        if (creds != null && !credStore.autoReloginBlocked()) {
            DebugLog.log("SESSION", "没有可用会话 → 用本机凭据静默续期（L2）")
            if (silentRelogin(creds, reason = if (restored.expired) "expired" else "no-session")) return
        }

        if (restored.expired) {
            _ui.value = _ui.value.copy(
                sessionState = SessionState.Expired,
                sessionCheckMsg = SessionText.EXPIRED_NOTE,
            )
            DebugLog.log("SESSION", "上次的会话已过期（cookie 已清），等用户续期")
        } else {
            DebugLog.log("SESSION", "本机没有会话存档 → 未登录")
        }
    }

    /**
     * **静默续期**（票 14 的 L2）：用本机加密保存的学号密码自动重登一次。
     *
     * 纪律：
     * - 凭据只发给 [cn.dkarbon.enroute.data.UrpClient.CAS_LOGIN_URL]（常量，不拼接）；
     * - 成功 → 会话存盘 + 失败计数清零 + **不弹任何提示**（无感）；
     * - 失败 → 计数 +1；累计 3 次就停手，界面说「可能改过密码，请手动登录一次」；
     * - 日志只记结果，**绝不含学号密码**。
     *
     * @return true = 已经接手并落定了登录态（调用方不要再写「未登录/已过期」）
     */
    private suspend fun silentRelogin(
        creds: cn.dkarbon.enroute.data.CredentialStore.Credentials,
        reason: String,
    ): Boolean {
        _ui.value = _ui.value.copy(
            reloginRunning = true,
            sessionState = SessionState.Active,
            sessionCheckMsg = SessionText.RELATING_NOTE,
            reloginMsg = "",
        )
        DebugLog.log("SESSION", "开始静默续期（原因=$reason）")
        val result = runCatching {
            withContext(Dispatchers.IO) { UrpClient.login(creds.user, creds.pass) }
        }
        val s = result.getOrNull()
        if (s == null) {
            val n = withContext(Dispatchers.IO) { credStore.recordFailure() }
            val blocked = n >= cn.dkarbon.enroute.data.CredentialStore.MAX_FAILURES
            DebugLog.log(
                "SESSION",
                "静默续期失败（第 $n 次${if (blocked) "，已达上限，停止自动尝试" else ""}）：" +
                    (result.exceptionOrNull()?.let { it.javaClass.simpleName } ?: "unknown"),
            )
            _ui.value = _ui.value.copy(
                reloginRunning = false,
                sessionState = SessionState.Expired,
                sessionCheckMsg = if (blocked) SessionText.RELOGIN_BLOCKED_NOTE else SessionText.EXPIRED_NOTE,
                reloginMsg = if (blocked) SessionText.RELOGIN_BLOCKED_NOTE else "",
            )
            return true
        }
        session = s
        withContext(Dispatchers.IO) {
            runCatching { sessionStore.save(s) }
            runCatching { credStore.resetFailures() }
        }
        _ui.value = _ui.value.copy(
            sessionState = SessionState.Active,
            sessionCheckMsg = "",
            reloginRunning = false,
            reloginMsg = "",
            userName = s.displayName.ifBlank { _ui.value.userName },
        )
        DebugLog.log("SESSION", "静默续期成功 → 已恢复登录（用户无感）")
        // 续上之后照常拉数据（与手动登录后同一套动作，只是不弹说明卡）
        fetchSchedule(s)
        // 刚登录成功：真抓一次（凭据可能换了人，缓存先作废，见 login() 的注释）
        refreshHome(force = true)
        return true
    }

    // ── 保持登录（票 14）───────────────────────────────────

    /** 说明卡点「启用」：把本次登录的凭据加密存盘，并打开开关 */
    fun enableKeepLogin() {
        val creds = pendingCred
        if (creds == null) {
            _ui.value = _ui.value.copy(
                credPromptVisible = false,
                credStatus = "没拿到这次的登录凭据，请退出重登一次再开启",
            )
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { credStore.save(creds.user, creds.pass) }
            pendingCred = null
            withContext(Dispatchers.IO) { credStore.markPrompted() }
            _ui.value = _ui.value.copy(
                credPromptVisible = false,
                credEnabled = ok,
                credStatus = if (ok) credStore.statusLine() else "存不进去（设备密钥不可用），本次仍可用但下次要重登",
            )
            DebugLog.log("CRED", if (ok) "用户开启「保持登录」→ 凭据已加密存本机" else "用户开启「保持登录」但存盘失败")
        }
    }

    /** 说明卡点「不了」：丢掉内存里的凭据，并记下「问过了」 */
    fun dismissKeepLoginPrompt() {
        pendingCred = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { credStore.markPrompted() }
        }
        _ui.value = _ui.value.copy(credPromptVisible = false, credStatus = credStore.statusLine())
    }

    /** 设置页关掉「保持登录」：**立刻删密文与密钥**（不是只标记关闭） */
    fun disableKeepLogin() {
        pendingCred = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) { credStore.clear() }
            _ui.value = _ui.value.copy(
                credEnabled = false,
                credStatus = credStore.statusLine(),
                reloginMsg = "本机保存的密码已删除，以后会话过期要手动登录一次",
            )
            DebugLog.log("CRED", "用户关闭「保持登录」→ 密文与密钥已删")
        }
    }

    /** 设置页「清除本机保存的密码」（与关闭等价，但保留开关状态不变 = 一起关） */
    fun clearSavedPassword() = disableKeepLogin()

    /**
     * 密钥自检（调试页一键）：真 Keystore 加解密往返。
     *
     * 2026-11-14 的教训：会话存档在真机上从来没写成功，而 JVM 单测全绿 ——
     * 因为 AndroidKeyStore 的密钥不允许调用方提供 IV，普通 JCE 密钥允许。
     * 有了这个自检，「存档到底能不能用」在真机上一按就知道，不用猜。
     */
    fun runCryptoSelfTest() {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val session = runCatching { SessionStore(getApplication()).selfTest() }
                    .getOrElse { "✘ 会话自检异常：${it.javaClass.simpleName}" }
                val cred = runCatching { credStore.selfTest() }
                    .getOrElse { "✘ 凭据自检异常：${it.javaClass.simpleName}" }
                session + " ｜ " + cred
            }
            DebugLog.log("SELFTEST", r)
            toast(r)
        }
    }

    /** 刷新界面上的凭据状态（进设置页时调） */
    fun refreshCredStatus() {
        _ui.value = _ui.value.copy(
            credEnabled = credStore.isEnabled(),
            credStatus = credStore.statusLine(),
        )
    }

    /**
     * 验活并落定三态。
     *
     * **连不上 ≠ 过期**：手机不在校园网时教务根本问不到，这时保留会话、
     * 并在 [AppState.sessionCheckMsg] 里照实说「这次没法确认」——
     * 既不说它有效，也不冤枉它过期（否则学生每次出校都被要求重登）。
     */
    private suspend fun verifyRestoredSession(s: UrpClient.Session) {
        val probe = runCatching { UrpClient.verifySession(s) }.getOrElse { SessionProbe.Unreachable }
        when (probe) {
            SessionProbe.Ok -> {
                DebugLog.log("SESSION", "验活通过 → 会话有效")
                withContext(Dispatchers.IO) { runCatching { sessionStore.markVerified() } }
                _ui.value = _ui.value.copy(
                    sessionState = SessionRules.stateOf(hasSession = true, probe = probe),
                    sessionCheckMsg = "",
                )
                // 票 24：会话有效才谈得上补数据；**三档都新鲜就一个请求都不发**
                maybeSilentRefresh(s)
            }

            SessionProbe.Expired -> {
                DebugLog.log("SESSION", "验活失败 → 标记会话已过期（课表/成绩缓存一条不动）")
                session = null
                withContext(Dispatchers.IO) { runCatching { sessionStore.markExpired() } }
                _ui.value = _ui.value.copy(
                    sessionState = SessionRules.stateOf(hasSession = false, probe = probe),
                    sessionCheckMsg = SessionText.EXPIRED_NOTE,
                )
                // L2：用户开了「保持登录」就用本机凭据续上（成功则用户完全无感）
                val creds = withContext(Dispatchers.IO) {
                    runCatching { if (credStore.isEnabled()) credStore.read() else null }.getOrNull()
                }
                if (creds != null && !credStore.autoReloginBlocked()) {
                    silentRelogin(creds, reason = "probe-expired")
                }
            }

            SessionProbe.Unreachable -> {
                DebugLog.log("SESSION", "验活未完成 → 会话保留，界面说明「还无法确认」")
                _ui.value = _ui.value.copy(
                    sessionState = SessionRules.stateOf(hasSession = true, probe = probe),
                    sessionCheckMsg = SessionText.UNVERIFIED_NOTE,
                )
            }
        }
    }

    // ── 缓存三档（票 24）：身份=永久 / 成绩·考试·校区列表=每日 / 其余=每次 ──
    //
    // 纪律（与 CachePolicy 的判据配套，改这里前先看那段注释）：
    //   ① 判据全在 CachePolicy（纯函数、单测锁死），本类不自己写 `if (age > ...)`；
    //   ② 抓成功才写盘；抓失败**一个字段都不覆盖**，界面照常显示旧值 + 「更新于 …」；
    //   ③ 「刷新」是分块的：身份/学分绩点/考试/成绩各有各的入口，不做「一按全量重抓」。

    /** 当前时刻（判据都在 CachePolicy 里，这里只提供时钟） */
    private fun nowMs(): Long = System.currentTimeMillis()

    /** 读缓存 → 灌进界面。返回 true = 本机确实有缓存 */
    private suspend fun loadAcademicCacheIntoState(): Boolean {
        val loaded = withContext(Dispatchers.IO) { runCatching { academicCacheStore.load() }.getOrNull() }
        cache = loaded ?: cn.dkarbon.enroute.data.AcademicCache()
        val hasAny = loaded != null && !loaded.isEmpty
        _ui.value = _ui.value.copy(
            // 每一块都只在缓存里真有时才覆盖界面（拿空对象盖掉刚恢复的状态是 bug）
            profile = cache.profile.takeIf { !it.isEmpty } ?: _ui.value.profile,
            credit = cache.credit.takeIf { !it.isEmpty } ?: _ui.value.credit,
            scoreStats = cache.scoreStats.takeIf { !it.isEmpty } ?: _ui.value.scoreStats,
            scores = cache.scores.ifEmpty { _ui.value.scores },
            exams = cache.exams.ifEmpty { _ui.value.exams },
            campuses = cache.campuses.ifEmpty { _ui.value.campuses },
            buildings = cache.buildings.ifEmpty { _ui.value.buildings },
            examsFetchedAt = cache.examsAt,
            scoresFetchedAt = cache.scoresAt,
            roomsListsFetchedAt = cache.roomsListsAt,
            cacheUpdatedLabel = cacheLine(),
            homeDailyFetchedAt = cn.dkarbon.enroute.data.CachePolicy.dailyBlockAt(cache.creditAt, cache.scoreStatsAt),
            examsMsg = if (hasAny) examsCacheMsg() else _ui.value.examsMsg,
        )
        DebugLog.log(
            "CACHE",
            if (hasAny) {
                "读到缓存：身份=${cache.profileAt > 0} 绩点=${cache.scoreStatsAt > 0} " +
                    "考试=${cache.exams.size} 场 成绩=${cache.scores.size} 条（不发网络）"
            } else {
                "本机还没有教务缓存"
            },
        )
        return hasAny
    }

    /**
     * 「更新于：身份·学分·绩点 27 分钟前 · 考试·成绩 11 小时前」；没有缓存 → 空串（不显示假时间）。
     *
     * 同年份的项目并成一组：一行只有一句「什么时候取的」，才不会被折行拆成
     * 「考试 / 11 小时前」两段。全都一样老就只写年龄，不再点名。
     */
    private fun cacheLine(now: Long = nowMs()): String {
        val groups = LinkedHashMap<String, MutableList<String>>()
        fun tag(name: String, at: Long) {
            val age = cn.dkarbon.enroute.data.CachePolicy.ageLabel(now, at)
            if (age.isNotBlank()) groups.getOrPut(age) { mutableListOf() }.add(name)
        }
        tag("身份", cache.profileAt)
        tag("学分", cache.creditAt)
        tag("绩点", cache.scoreStatsAt)
        tag("考试", cache.examsAt)
        tag("成绩", cache.scoresAt)
        if (groups.isEmpty()) return ""
        if (groups.size == 1) return "更新于 " + groups.keys.first()
        return "更新于：" + groups.entries.joinToString(" · ") { (age, names) ->
            names.joinToString("·") + " " + age
        }
    }

    /** 考试页/考试卡的「数据是缓存」提示：有考试才说，没有就留空（空态自己有话） */
    private fun examsCacheMsg(): String {
        if (cache.exams.isEmpty()) return ""
        val age = cn.dkarbon.enroute.data.CachePolicy.ageLabel(nowMs(), cache.examsAt)
        return if (age.isBlank()) "" else "本地缓存 · 考试更新于 $age"
    }

    /** 合并 + 落盘 + 刷新「更新于」小字（唯一写缓存的地方） */
    private suspend fun commitCache(
        update: (cn.dkarbon.enroute.data.AcademicCache) -> cn.dkarbon.enroute.data.AcademicCache,
    ) {
        cache = update(cache)
        val ok = withContext(Dispatchers.IO) { runCatching { academicCacheStore.save(cache) }.getOrDefault(false) }
        if (!ok) DebugLog.log("CACHE", "缓存写盘失败（界面仍可用，只是下次要重抓）")
        _ui.value = _ui.value.copy(
            cacheUpdatedLabel = cacheLine(),
            examsFetchedAt = cache.examsAt,
            scoresFetchedAt = cache.scoresAt,
            roomsListsFetchedAt = cache.roomsListsAt,
            homeDailyFetchedAt = cn.dkarbon.enroute.data.CachePolicy.dailyBlockAt(cache.creditAt, cache.scoreStatsAt),
        )
    }

    /**
     * 冷启动**静默补齐**（票 24 的核心：三档全新鲜时一个请求都不发）。
     *
     * 只在「会话有效」之后调；每块各按自己的判据决定抓不抓：
     *   · 身份：缓存空才抓（永久档）；
     *   · 成绩/绩点：距上次成功 ≥ 24h 才抓（每日档）；
     *   · 考试：≥ 24h **或** 缓存里那场「下一场考试」已经过（双条件，见 CachePolicy）。
     * 任一失败都不影响其他块，也不覆盖已有缓存。
     */
    private suspend fun maybeSilentRefresh(s: UrpClient.Session) {
        val now = nowMs()
        // 总判据走 CachePolicy.anyBlockStale：false = 三档全新鲜 = **一个请求都不发**
        val anyStale = cn.dkarbon.enroute.data.CachePolicy.anyBlockStale(
            nowMs = now,
            profileIsEmpty = cache.profile.isEmpty,
            creditAt = cache.creditAt,
            scoreStatsAt = cache.scoreStatsAt,
            examsAt = cache.examsAt,
            nextExamAtMs = cache.nextExamAtMs,
            scoresAt = cache.scoresAt,
        )
        if (!anyStale) {
            DebugLog.log("CACHE", "冷启动：三档都新鲜 → 不发身份/成绩/考试请求，直接读缓存")
            return
        }
        val needIdentity = cn.dkarbon.enroute.data.CachePolicy.shouldFetchPermanent(cache.profile.isEmpty)
        val needProgress = cn.dkarbon.enroute.data.CachePolicy.shouldFetchDaily(
            now,
            minOf(cache.creditAt, cache.scoreStatsAt).takeIf { it > 0L } ?: 0L,
        )
        val needExams = cn.dkarbon.enroute.data.CachePolicy.shouldFetchExams(now, cache.examsAt, cache.nextExamAtMs)
        val needScores = cn.dkarbon.enroute.data.CachePolicy.shouldFetchDaily(now, cache.scoresAt)
        DebugLog.log(
            "CACHE",
            "冷启动静默补齐：身份=$needIdentity 成绩/绩点=$needProgress 考试=$needExams 成绩明细=$needScores",
        )
        _ui.value = _ui.value.copy(homeLoading = true)
        // 身份与成绩/绩点是两块，各自独立失败
        if (needIdentity) fetchIdentity(s)
        if (needProgress) fetchProgress(s)
        _ui.value = _ui.value.copy(homeLoading = false, homeMsg = if (needIdentity || needProgress) "" else _ui.value.homeMsg)
        if (needExams) fetchExamsBlock(s, silent = true)
        if (needScores) fetchScoresBlock(s, silent = true)
    }

    /**
     * 学籍身份（**永久档**）。抓失败或解析出空 → 保留旧值。
     * @return true = 这次真拿到了
     */
    private suspend fun fetchIdentity(s: UrpClient.Session): Boolean {
        val r = withContext(Dispatchers.IO) { runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchProfile(s) } }
        val p = r.getOrNull()
        if (p == null || p.isEmpty) {
            DebugLog.log(
                "CACHE",
                "身份没抓到 → 保留旧值（${r.exceptionOrNull()?.javaClass?.simpleName ?: "空页面"}）",
            )
            return false
        }
        _ui.value = _ui.value.copy(profile = p)
        commitCache { it.merge(nowMs(), profile = p) }
        DebugLog.log("CACHE", "身份已更新（永久档：之后不再自动重抓）")
        return true
    }

    /** 学分进度 + 成绩统计（**每日档**）。两块各自成功各自写，失败的那块保留旧值 */
    private suspend fun fetchProgress(s: UrpClient.Session): Boolean {
        val pair = withContext(Dispatchers.IO) {
            runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchCreditProgress(s) } to
                runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchScoreStats(s) }
        }
        val credit = pair.first.getOrNull()?.takeIf { !it.isEmpty }
        val stats = pair.second.getOrNull()?.takeIf { !it.isEmpty }
        if (credit == null && stats == null) {
            DebugLog.log("CACHE", "学分/绩点都没抓到 → 保留旧值")
            return false
        }
        _ui.value = _ui.value.copy(
            credit = credit ?: _ui.value.credit,
            scoreStats = stats ?: _ui.value.scoreStats,
        )
        commitCache { it.merge(nowMs(), credit = credit, scoreStats = stats) }
        DebugLog.log("CACHE", "学分=${credit != null} 绩点=${stats != null} 已更新（每日档）")
        return true
    }

    /**
     * 考试（**每日档 + 过期双条件**）。
     *
     * 服务端答「没有考试」是**有效结果**，要照实写进缓存（否则每次冷启动都会重抓）；
     * 但「没拿到」（`fetchExamsOrNull` 返回 null）**一个字都不覆盖**。
     */
    private suspend fun fetchExamsBlock(s: UrpClient.Session, silent: Boolean = false): Boolean {
        if (!silent) _ui.value = _ui.value.copy(examsLoading = true, examsMsg = "抓取中…")
        val r = withContext(Dispatchers.IO) { runCatching { AcademicApi.fetchExamsOrNull(s) } }
        // 内层 fetchExamsOrNull 已经用「null = 没拿到」表达了失败，这里只取一层 Result。
        // （写成 `?.getOrNull()` 会被解析成 List.getOrNull(index)，那是另一个函数）
        val list: List<cn.dkarbon.enroute.data.ExamItem>? = r.getOrNull()
        if (list == null) {
            DebugLog.log(
                "CACHE",
                "考试没拿到 → 保留 ${cache.exams.size} 场旧数据" +
                    (r.exceptionOrNull()?.let { "（${it.javaClass.simpleName}）" } ?: ""),
            )
            _ui.value = _ui.value.copy(
                examsLoading = false,
                examsMsg = if (cache.exams.isEmpty()) {
                    noSessionHint().ifBlank { "没抓到考试（接口可能不同，去设置页跑一次「接口诊断」）" }
                } else {
                    examsCacheMsg() + " · 这次没刷新成功，显示的仍是缓存"
                },
            )
            return false
        }
        _ui.value = _ui.value.copy(
            examsLoading = false,
            exams = list,
            examsMsg = if (list.isEmpty()) "教务系统说本学期没有待考考试" else "✅ ${list.size} 场考试",
        )
        commitCache { it.merge(nowMs(), exams = list) }
        DebugLog.log(
            "CACHE",
            "考试已更新：${list.size} 场，下一场=${cn.dkarbon.enroute.data.CachePolicy.nextExamAtMs(list, nowMs())}",
        )
        return true
    }

    /** 成绩明细（**每日档**）。没拿到 → 保留旧值 */
    private suspend fun fetchScoresBlock(s: UrpClient.Session, silent: Boolean = false): Boolean {
        if (!silent) _ui.value = _ui.value.copy(scoresLoading = true, scoresMsg = "抓取中…")
        val r = withContext(Dispatchers.IO) { runCatching { AcademicApi.fetchScoresOrNull(s) } }
        val list: List<cn.dkarbon.enroute.data.ScoreItem>? = r.getOrNull()
        if (list == null) {
            DebugLog.log("CACHE", "成绩没拿到 → 保留 ${cache.scores.size} 条旧数据")
            _ui.value = _ui.value.copy(
                scoresLoading = false,
                scoresMsg = if (cache.scores.isEmpty()) {
                    noSessionHint().ifBlank { "没抓到成绩（接口可能不同，去设置页跑一次「接口诊断」）" }
                } else {
                    "本地缓存 · 成绩 ${cn.dkarbon.enroute.data.CachePolicy.ageLabel(nowMs(), cache.scoresAt)}" +
                        " · 这次没刷新成功"
                },
            )
            return false
        }
        _ui.value = _ui.value.copy(
            scoresLoading = false,
            scores = list,
            scoresMsg = if (list.isEmpty()) "教务系统说还没有成绩记录" else "✅ ${list.size} 条成绩",
        )
        commitCache { it.merge(nowMs(), scores = list) }
        return true
    }

    // ── 提示 ──────────────────────────────────────────────

    fun toast(msg: String) {
        _ui.value = _ui.value.copy(toast = msg)
    }

    fun consumeToast() {
        if (_ui.value.toast != null) _ui.value = _ui.value.copy(toast = null)
    }

    // ── 培养方案（票 09：内置资产，离线）──────────────────

    /** 资产读取器（懒建；按需读，不预加载） */
    private val curriculum by lazy { cn.dkarbon.enroute.data.curriculum.CurriculumAssets(getApplication()) }

    /**
     * 进入「培养方案」：读索引 → 按学籍年级选版本 → 按专业定位本人那份。
     *
     * 定位不到专业**不报错**，把该年份的专业列表摆出来让用户手动选（票 09 的交互要求）。
     * 全部读取在 IO 线程，期间 [AppState.planLoading] = true。
     */
    fun openCurriculum() {
        _ui.value = _ui.value.copy(
            planLoading = true,
            planMsg = "正在读培养方案…",
        )
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val idx = curriculum.index()
                val year = idx?.let {
                    cn.dkarbon.enroute.data.curriculum.CurriculumParse.pickYear(
                        _ui.value.profile.grade, it.years,
                    )
                }.orEmpty()
                val entry = idx?.let {
                    cn.dkarbon.enroute.data.curriculum.CurriculumParse.pickPlan(
                        it, year, _ui.value.profile.major,
                    )
                }
                val plan = if (entry != null) curriculum.plan(year, entry.id) else null
                Triple(idx, year to entry, plan)
            }
            val (idx, yearEntry, plan) = loaded
            val (year, entry) = yearEntry
            cn.dkarbon.enroute.data.DebugLog.log(
                "CURRICULUM",
                "培养方案：索引=${idx?.years ?: "无"} 年级=${_ui.value.profile.grade.ifBlank { "未填" }} " +
                    "版本=$year 定位=${entry?.id ?: "未定位"} 课程=${plan?.courses?.size ?: 0} " +
                    "学分结构表=${plan?.creditTable?.size ?: 0} 行",
            )
            _ui.value = _ui.value.copy(
                planIndex = idx,
                planYear = year,
                planMajor = entry?.major ?: "",
                planEntries = if (idx == null || entry == null) emptyList() else {
                    idx.entries(year).filter { it.major == entry.major }
                },
                planEntryId = entry?.id.orEmpty(),
                plan = plan,
                planLoading = false,
                planMsg = when {
                    idx == null -> "没读到内置培养方案资产（安装包可能不完整）"
                    idx.years.isEmpty() -> "内置培养方案里没有任何年份的数据"
                    entry == null -> "未匹配到方案"
                    plan == null -> "读「$year 级 ${entry.major}」的方案失败（资产缺失或损坏）"
                    else -> ""
                },
            )
        }
    }

    /** 手动切换版本年份（2023 / 2024 / 2025） */
    fun selectPlanYear(year: String) {
        val idx = _ui.value.planIndex ?: return
        if (year == _ui.value.planYear) return
        // 换年份后优先仍选同一专业，不行就退到该年第一个专业
        val keepMajor = _ui.value.planMajor
        val entry = cn.dkarbon.enroute.data.curriculum.CurriculumParse.pickPlan(idx, year, keepMajor)
            ?: idx.entries(year).firstOrNull()
        loadPlan(year, entry?.id.orEmpty(), entry?.major.orEmpty())
    }

    /** 手动切换专业（按索引条目 id） */
    fun selectPlanMajor(entryId: String) {
        val entry = _ui.value.planEntries.firstOrNull { it.id == entryId }
            ?: _ui.value.planIndex?.entries(_ui.value.planYear)?.firstOrNull { it.id == entryId }
            ?: return
        loadPlan(_ui.value.planYear, entry.id, entry.major)
    }

    private fun loadPlan(year: String, entryId: String, major: String, from: String = "curriculum") {
        if (year.isBlank() || entryId.isBlank()) return
        _ui.value = _ui.value.copy(planLoading = true, planMsg = "正在读培养方案…")
        viewModelScope.launch {
            val plan = withContext(Dispatchers.IO) { curriculum.plan(year, entryId) }
            _ui.value = _ui.value.copy(
                planYear = year,
                planMajor = major,
                planEntryId = entryId,
                planEntries = _ui.value.planIndex?.entries(year)?.filter { it.major == major }.orEmpty(),
                plan = plan,
                planLoading = false,
                planMsg = if (plan == null) "读「$year 级 $major」的方案失败（资产缺失或损坏）" else "",
            )
            cn.dkarbon.enroute.data.telemetry.Telemetry.record(
                "plan.open",
                mapOf(
                    "year" to year.takeIf { it in setOf("2023", "2024", "2025") }.orEmpty(),
                    "from" to from,
                ),
            )
        }
    }

    /**
     * 点某门课（培养方案页、课表详情、搜索页三处共用）。
     *
     * 匹配优先级：课程代码精确 → 归一课程名精确 → 名称包含；命中多条**不猜**，把候选摆出来。
     * 匹配不上照实说「这本院级大纲册子里没有这门课」——**不空卡片、不转圈**（票 10 的硬要求）。
     */
    fun openSyllabus(code: String, name: String, from: String = "timetable") {
        _ui.value = _ui.value.copy(
            syllabusOpenId = "…",     // 占位：浮层立刻出现并显示「正在查」
            syllabusDetail = null,
            syllabusDetailLoading = true,
            syllabusDetailMsg = "",
            syllabusCandidates = emptyList(),
        )
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                val match = curriculum.matchSyllabus(code, name)
                val entry = match.entry
                val detail = entry?.let { curriculum.syllabus(it.id) }
                Triple(match, entry, detail)
            }
            val (match, entry, detail) = r
            val count = withContext(Dispatchers.IO) { curriculum.syllabusCount() }
            cn.dkarbon.enroute.data.DebugLog.log(
                "CURRICULUM",
                "查大纲 代码=${cn.dkarbon.enroute.data.curriculum.SyllabusParse.codeOf(code)} 命中方式=${match.by} " +
                    "候选=${match.candidates.size} 读到大纲=${detail != null}（册内 $count 门）",
            )
            // 「查得到吗」是这个功能唯一想知道的指标：命中/未命中各记一条，只带布尔与枚举来源
            cn.dkarbon.enroute.data.telemetry.Telemetry.record(
                "syllabus.open",
                mapOf("from" to from, "hit" to (detail != null).toString()),
            )
            if (detail == null) {
                cn.dkarbon.enroute.data.telemetry.Telemetry.record("syllabus.miss", mapOf("has_code" to code.isNotBlank().toString()))
            }
            _ui.value = _ui.value.copy(
                syllabusOpenId = entry?.id.orEmpty(),
                syllabusDetail = detail,
                syllabusDetailLoading = false,
                syllabusCandidates = match.candidates,
                syllabusDetailMsg = when {
                    entry == null && match.candidates.isNotEmpty() ->
                        "有 ${match.candidates.size} 门课名字对得上，不替你猜"
                    entry == null ->
                        "内置大纲里没有「${name.ifBlank { code }}」—— 收录 14 个学院 2021 版共 " +
                            "$count 门，通识课与其它年级的课通常不在其中"
                    detail == null -> "索引里有这门课，但大纲文件没读到（资产可能不完整）"
                    else -> ""
                },
            )
        }
    }

    /** 从候选里挑一门（命中多条时用户自己选） */
    fun openSyllabusById(id: String) {
        val entry = _ui.value.syllabusCandidates.firstOrNull { it.id == id }
            ?: _ui.value.syllabusIndex.firstOrNull { it.id == id }
            ?: return
        _ui.value = _ui.value.copy(
            syllabusOpenId = entry.id,
            syllabusDetail = null,
            syllabusDetailLoading = true,
            syllabusDetailMsg = "",
        )
        viewModelScope.launch {
            val detail = withContext(Dispatchers.IO) { curriculum.syllabus(entry.id) }
            cn.dkarbon.enroute.data.telemetry.Telemetry.record(
                "syllabus.open",
                mapOf("from" to "search", "hit" to (detail != null).toString()),
            )
            _ui.value = _ui.value.copy(
                syllabusDetail = detail,
                syllabusDetailLoading = false,
                syllabusCandidates = emptyList(),
                syllabusDetailMsg = if (detail == null) "大纲文件没读到（资产可能不完整）" else "",
            )
        }
    }

    fun closeSyllabus() {
        if (_ui.value.syllabusOpenId.isNotEmpty()) {
            _ui.value = _ui.value.copy(
                syllabusOpenId = "",
                syllabusDetail = null,
                syllabusDetailLoading = false,
                syllabusDetailMsg = "",
                syllabusCandidates = emptyList(),
            )
        }
    }

    // ── 大纲搜索页（票 10 第二处入口）────────────────────

    /** 进搜索页先确保索引在内存里（166 条，22KB） */
    fun ensureSyllabusIndex() {
        if (_ui.value.syllabusIndex.isNotEmpty() || _ui.value.syllabusIndexLoading) return
        _ui.value = _ui.value.copy(syllabusIndexLoading = true, syllabusIndexMsg = "正在读大纲索引…")
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { curriculum.syllabusIndex() }
            _ui.value = _ui.value.copy(
                syllabusIndex = list,
                syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(
                    list, _ui.value.syllabusQuery, _ui.value.syllabusType, _ui.value.syllabusColleges,
                ),
                syllabusIndexLoading = false,
                syllabusIndexMsg = if (list.isEmpty()) "没读到大纲索引（安装包可能不完整）" else "",
            )
        }
    }

    fun setSyllabusQuery(q: String) {
        _ui.value = _ui.value.copy(
            syllabusQuery = q,
            syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(
                _ui.value.syllabusIndex, q, _ui.value.syllabusType, _ui.value.syllabusColleges,
            ),
        )
    }

    /** 大纲学院多选：点一下切换该学院，空集 = 全部 */
    fun toggleSyllabusCollege(college: String) {
        val cur = _ui.value.syllabusColleges
        val next = if (college in cur) cur - college else cur + college
        _ui.value = _ui.value.copy(
            syllabusColleges = next,
            syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(
                _ui.value.syllabusIndex, _ui.value.syllabusQuery, _ui.value.syllabusType, next,
            ),
        )
    }

    /** 清空学院筛选（UI 上的「全部」） */
    fun clearSyllabusColleges() {
        _ui.value = _ui.value.copy(
            syllabusColleges = emptySet(),
            syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(
                _ui.value.syllabusIndex, _ui.value.syllabusQuery, _ui.value.syllabusType, emptySet(),
            ),
        )
    }

    fun setSyllabusType(type: String) {
        val t = if (type == _ui.value.syllabusType) "" else type   // 再点一次取消筛选
        _ui.value = _ui.value.copy(
            syllabusType = t,
            syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(
                _ui.value.syllabusIndex, _ui.value.syllabusQuery, t, _ui.value.syllabusColleges,
            ),
        )
    }

    // ── 历年课表（票 11）─────────────────────────────────

    private val historyStore by lazy { cn.dkarbon.enroute.data.HistoryTimetableStore(getApplication()) }

    /**
     * 进「历年课表」：先拿学期候选（不写死，服务器给最近 5 个）→ 默认当前学期 → 读该学期课表。
     *
     * 缓存优先：命中就**不打网络**，界面标「本地缓存 · 抓于 MM-DD」（历史学期不会再变，永久有效）。
     */
    fun openHistoryTimetable() {
        if (_ui.value.historyTerms.isNotEmpty() && _ui.value.historyPlanCode.isNotBlank()) {
            loadHistoryTerm(_ui.value.historyPlanCode, force = false)
            return
        }
        // 学期候选是「永久档」：本机缓存里有就直接用，不打网络（历史学期不会再变，取一次就够）
        if (cache.historyTerms.isNotEmpty()) {
            cn.dkarbon.enroute.data.DebugLog.log("HISTORY", "学期候选命中缓存（${cache.historyTerms.size} 个），不发网络")
            _ui.value = _ui.value.copy(historyTerms = cache.historyTerms)
            loadHistoryTerm(pickHistoryTerm(cache.historyTerms), force = false)
            return
        }
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(
                historyMsg = "未登录，读不到学期列表",
            )
            return
        }
        _ui.value = _ui.value.copy(historyLoading = true, historyMsg = "正在读学期列表…")
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val html = UrpClient.fetchSemesterCalendarPage(s)
                    cn.dkarbon.enroute.data.HistorySemesters.options(html)
                }
            }
            val terms = r.getOrDefault(emptyList())
            if (terms.isEmpty()) {
                _ui.value = _ui.value.copy(
                    historyLoading = false,
                    historyMsg = if (r.isFailure) friendlyError(r.exceptionOrNull()!!)
                    else "没拿到学期列表（页面上没有 planCode 下拉，可能页面改版了）",
                )
                return@launch
            }
            cn.dkarbon.enroute.data.DebugLog.log(
                "HISTORY", "学期候选 ${terms.map { it.code }}，默认 ${pickHistoryTerm(terms)}",
            )
            commitCache { it.merge(nowMs(), historyTerms = terms) }
            _ui.value = _ui.value.copy(historyTerms = terms, historyLoading = false)
            loadHistoryTerm(pickHistoryTerm(terms), force = false)
        }
    }

    /** 默认学期：服务器标「当前」→ 按日期推出来的学期 → 第一个（不写死，见 HistorySemesters） */
    private fun pickHistoryTerm(terms: List<cn.dkarbon.enroute.data.HistorySemesters.Option>): String {
        val guess = cn.dkarbon.enroute.ui.home.HomeModel.currentTermCode(
            java.time.LocalDate.now(), _ui.value.timetable?.term.orEmpty(),
        )
        return terms.firstOrNull { it.current }?.code
            ?: terms.firstOrNull { it.code == guess }?.code
            ?: terms.firstOrNull()?.code.orEmpty()
    }

    /** 切学期：缓存优先，没缓存才打网络 */
    fun selectHistoryTerm(planCode: String) {
        if (planCode.isBlank() || planCode == _ui.value.historyPlanCode && _ui.value.historyTimetable != null) return
        loadHistoryTerm(planCode, force = false)
    }

    /** 手动刷新：绕过缓存重新拉，成功后覆盖缓存 */
    fun refreshHistoryTerm() {
        val code = _ui.value.historyPlanCode
        if (code.isBlank()) {
            openHistoryTimetable()
            return
        }
        loadHistoryTerm(code, force = true)
    }

    private fun loadHistoryTerm(planCode: String, force: Boolean) {
        val code = planCode.trim()
        if (code.isBlank()) return
        _ui.value = _ui.value.copy(
            historyPlanCode = code,
            historyLoading = true,
            historyMsg = "正在读 $code 的课表…",
        )
        viewModelScope.launch {
            // ① 缓存
            val cached = if (force) null else withContext(Dispatchers.IO) { historyStore.read(code) }
            if (cached != null) {
                _ui.value = _ui.value.copy(
                    historyLoading = false,
                    historyTimetable = cached.timetable,
                    historyFromCache = true,
                    historyFetchedAt = cached.fetchedAt,
                    historyMsg = if (cached.timetable.courses.isEmpty()) "$code 这一学期没有课" else "",
                )
                return@launch
            }
            // ② 网络
            val s = session
            if (s == null) {
                _ui.value = _ui.value.copy(
                    historyLoading = false,
                    historyFromCache = false,
                    historyMsg = "未登录，读不到这一学期的课表",
                )
                return@launch
            }
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = UrpClient.fetchScheduleForTerm(s, code)
                    // schema 与「本学期课表」同构 → 直接复用同一个解析器（票 11 的硬要求）
                    UrpClient.parseSchedule(raw)
                }
            }
            val tt = r.getOrNull()
            val now = java.time.OffsetDateTime.now().toString()
            if (tt != null) {
                withContext(Dispatchers.IO) {
                    historyStore.write(cn.dkarbon.enroute.data.HistoryEntry(code, now, tt))
                }
            }
            cn.dkarbon.enroute.data.DebugLog.log(
                "HISTORY",
                "按学期拉课表 $code：课程=${tt?.courses?.size ?: -1} 学期=${tt?.term.orEmpty()}" +
                    (r.exceptionOrNull()?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
            )
            _ui.value = _ui.value.copy(
                historyLoading = false,
                historyTimetable = tt,
                historyFromCache = false,
                historyFetchedAt = now,
                historyMsg = when {
                    r.isFailure -> friendlyError(r.exceptionOrNull()!!)
                    tt == null -> "没拿到这一学期的课表"
                    tt.courses.isEmpty() -> "$code 这一学期没有排课（或这学期你没课）"
                    else -> ""
                },
            )
        }
    }

    // ── 校园地图（票 12）─────────────────────────────────

    private val mapLoader by lazy { cn.dkarbon.enroute.data.map.MapPackageLoader(getApplication()) }
    private val mapEditsStore by lazy { cn.dkarbon.enroute.data.map.MapEditsStore(getApplication()) }

    /**
     * 进「校园地图」：读地图包（外部 → 回退内置）+ 读标注 → 算孤立标注。
     *
     * 换图后 edits 按 `id` 合并；在新版里找不到的 id **不丢**，进 [AppState.mapOrphans] 顶部提示。
     */
    fun openCampusMap() {
        _ui.value = _ui.value.copy(mapLoading = true, mapMsg = "正在读地图包…")
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pkg = mapLoader.load()
                val edits = mapEditsStore.read() ?: cn.dkarbon.enroute.data.map.MapEdits()
                Triple(pkg, edits, mapEditsStore.path())
            }
            val (pkg, saved, path) = loaded
            // 版本变了：只更新「基于哪版」，标注全部保留
            val edits = cn.dkarbon.enroute.data.map.MapEditsOps.withVersion(saved, pkg.manifest.version)
            if (edits != saved) withContext(Dispatchers.IO) { mapEditsStore.write(edits) }
            val orphans = cn.dkarbon.enroute.data.map.MapEditsOps.orphans(pkg.features, edits)
            cn.dkarbon.enroute.data.DebugLog.log(
                "MAP",
                "地图包 ${pkg.manifest.version}（${pkg.source}）${pkg.features.size} 面 · " +
                    "标注 ${edits.features.size} 条 · 孤立 ${orphans.size} · $path",
            )
            // 共建引导已移除（票 12 §8.3 的首次自动弹不再需要）：点任意地块即见编辑面板
            _ui.value = _ui.value.copy(
                mapPackage = pkg,
                mapLoading = false,
                mapEdits = edits,
                mapOrphans = orphans,
                mapSelectedId = "",
                mapMsg = when {
                    !pkg.ok -> "地图包读不出来：${cn.dkarbon.enroute.data.map.MapPackageRules.describe(pkg.issue)}"
                    else -> pkg.fallbackNote
                },
            )
        }
    }

    fun selectMapFeature(id: String) {
        _ui.value = _ui.value.copy(mapSelectedId = id)
    }

    fun clearMapSelection() {
        if (_ui.value.mapSelectedId.isNotEmpty()) _ui.value = _ui.value.copy(mapSelectedId = "")
    }

    /** 名称/类别/自定义字段：改一次存一次（IO 线程，原子写） */
    private fun updateEdits(f: (cn.dkarbon.enroute.data.map.MapEdits) -> cn.dkarbon.enroute.data.map.MapEdits) {
        val next = f(_ui.value.mapEdits)
        if (next === _ui.value.mapEdits) return
        _ui.value = _ui.value.copy(mapEdits = next)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { mapEditsStore.write(next) }
        }
    }

    /**
     * 导入标注（m32 迁移用）：把导出过的 GeoJSON 读回来，合并进本机 `edits.json`。
     *
     * 换签名之后旧版必须卸载重装，本机标注会被系统清掉 —— 这条路径是用户把标注带过来的唯一方式。
     */
    fun importMapAnnotations(text: String) {
        val pkg = _ui.value.mapPackage
        if (pkg == null) {
            _ui.value = _ui.value.copy(mapExportMsg = "地图还没读出来，等它加载完再导入")
            return
        }
        val res = cn.dkarbon.enroute.data.map.MapAnnotationImport.parse(
            text = text,
            official = pkg.features,
            existing = _ui.value.mapEdits,
            mapVersion = pkg.manifest.version,
        )
        if (res.error.isNotBlank()) {
            _ui.value = _ui.value.copy(mapExportMsg = "导入失败：${res.error}")
            return
        }
        updateEdits { res.edits }
        val extra = buildString {
            if (res.fields > 0) append("，新增 ${res.fields} 个字段")
            if (res.skipped > 0) append("，${res.skipped} 个 id 在这版地图里找不到（已跳过）")
        }
        _ui.value = _ui.value.copy(mapExportMsg = "已导入 ${res.features} 个面的标注$extra")
        cn.dkarbon.enroute.data.DebugLog.log(
            "MAP", "导入标注：${res.features} 面 / 新字段 ${res.fields} / 跳过 ${res.skipped}",
        )
    }

    fun setMapFeatureName(id: String, name: String) = updateEdits {
        cn.dkarbon.enroute.data.map.MapEditsOps.setName(it, id, name)
    }

    fun setMapFeatureKind(id: String, kind: String) = updateEdits {
        cn.dkarbon.enroute.data.map.MapEditsOps.setKind(it, id, kind)
    }

    fun setMapFeatureField(id: String, field: String, value: String) = updateEdits {
        cn.dkarbon.enroute.data.map.MapEditsOps.setField(it, id, field, value)
    }

    /** 「＋ 添加字段」：**字段名全局共享**（加了之后所有要素都有这一列，值为空） */
    fun addMapField(name: String) = updateEdits {
        cn.dkarbon.enroute.data.map.MapEditsOps.addField(it, name)
    }

    /** 长按删除字段：连带删所有要素该列的值 */
    fun removeMapField(name: String) = updateEdits {
        cn.dkarbon.enroute.data.map.MapEditsOps.removeField(it, name)
    }

    /**
     * 导出标注：合并 asset + edits → `filesDir/map/export/` 下的 GeoJSON 与 CSV（UTF-8 BOM），
     * 并把内容留在 state 里，由界面用 `ACTION_CREATE_DOCUMENT` 让用户另存到手机。
     */
    fun exportMapAnnotations() {
        val pkg = _ui.value.mapPackage ?: return
        val edits = _ui.value.mapEdits
        _ui.value = _ui.value.copy(mapExportMsg = "正在导出…")
        viewModelScope.launch {
            val stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
            val res = withContext(Dispatchers.IO) {
                mapEditsStore.export(pkg.features, edits, pkg.manifest.version, stamp)
            }
            cn.dkarbon.enroute.data.DebugLog.log(
                "MAP", "导出标注：${res.geojsonName} / ${res.csvName}（${res.featureCount} 面）${res.error}",
            )
            _ui.value = _ui.value.copy(
                mapExportMsg = res.error.ifBlank {
                    "已导出 ${res.featureCount} 个面 → 文件已存到 ${res.geojsonPath.substringBeforeLast('/')}；" +
                        "下面可另存到手机"
                },
                mapExportGeoJson = res.geojson,
                mapExportCsv = res.csv,
                mapExportGeoName = res.geojsonName,
                mapExportCsvName = res.csvName,
            )
        }
    }

    // ── 调试日志（票 20）────────────────────────────────────

    /** 设置页「保存日志到本机」开关：关掉立刻摘掉通道（连文件都不再创建） */
    fun setDebugLogToFile(on: Boolean) {
        prefs.debugLogToFile = on
        cn.dkarbon.enroute.data.DebugLog.attachFile(if (on) debugFile else null)
        cn.dkarbon.enroute.data.DebugLog.log("LOG", if (on) "已开启日志落盘" else "已关闭日志落盘")
        _ui.value = _ui.value.copy(debugFileEnabled = on, debugFileUsage = debugUsageLine())
    }

    /** 「已占用 412KB · 保留 7 天」这类状态行 */
    fun debugUsageLine(): String {
        if (!prefs.debugLogToFile) return "未开启（日志只留在内存，退出即丢）"
        val kb = runCatching { debugFile.usageBytes() / 1024 }.getOrDefault(0L)
        return "已占用 ${kb}KB · 保留 ${cn.dkarbon.enroute.data.DebugLogFile.MAX_DAYS} 天 · 上限 " +
            "${cn.dkarbon.enroute.data.DebugLogFile.MAX_BYTES / 1024 / 1024}MB"
    }

    fun refreshDebugFileState() {
        _ui.value = _ui.value.copy(
            debugFileEnabled = prefs.debugLogToFile,
            debugFileUsage = debugUsageLine(),
        )
    }

    /** 清空历史日志文件（**不动内存缓冲**） */
    fun clearDebugHistory() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { debugFile.clear() }.getOrDefault(false) }
            _ui.value = _ui.value.copy(
                debugFileUsage = debugUsageLine(),
                toast = if (ok) "已清空历史日志文件" else "清空失败",
            )
        }
    }

    /** 把 7 天日志拼成一份文本（给「全部导出」用；走 SAF，不需要存储权限） */
    fun exportAllDebug(): String = runCatching { debugFile.read(null) }.getOrDefault("")

    /** 历史日志文件列表（新→旧），调试页「历史日志」用 */
    fun debugHistoryDays(): List<String> =
        runCatching { debugFile.logFiles().map { it.name.removeSuffix(".log") } }.getOrDefault(emptyList())

    fun readDebugDay(day: String): String = runCatching { debugFile.read(day) }.getOrDefault("")

    /**
     * 「全部导出」的文件名：带**日期范围**（`在途日志_2026-11-08_2026-11-14.txt`）。
     *
     * 落盘的价值就在跨天：用户昨天遇到问题、今天来反馈，得能拿到昨天的日志。
     * 没有落盘文件时退化成「今天」单日，不编范围。
     */
    fun exportAllDebugFileName(): String {
        return cn.dkarbon.enroute.data.DebugLogFile.exportFileName(
            days = debugHistoryDays(),
            today = java.time.LocalDate.now().toString(),
        )
    }

    /**
     * 「全部导出」的正文。
     *
     * ⚠️ **导出前再脱敏一遍**，不假设落盘时脱过就安全 —— 导出是日志**第二次离开设备**的机会
     * （SAF 存到 SD 卡、被别的 App 读到、被同步盘上传都在这一步之后）。
     * 脱敏函数是幂等的（`JSESSIONID=***` 再抹一次还是 `***`），重复调用无副作用。
     */
    fun exportAllDebugText(): String {
        val body = exportAllDebug()
        val head = buildString {
            appendLine("=== 沈农在途 调试日志（全部历史）===")
            appendLine("导出时间: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date()))
            appendLine("说明: 只存本机、不上传；导出前已再次脱敏（Cookie / CAS 票据 / 密码字段 / token）")
            appendLine("---")
        }
        return head + body.lineSequence()
            .joinToString("\n") { cn.dkarbon.enroute.data.DebugLogFile.redact(it) }
    }

    // ── 自定义课程（票 19）──────────────────────────────────

    /** 打开自定义课程列表 */
    fun openCustomCourses() {
        _ui.value = _ui.value.copy(customEditorOpen = false, customEditing = null, customMsg = "")
    }

    /** 新建：true = 按周次，false = 固定某一天（编辑页第一步就选这个） */
    fun startAddCustom() {
        _ui.value = _ui.value.copy(customEditing = null, customEditorOpen = true, customMsg = "")
    }

    fun editCustom(id: String) {
        val c = _ui.value.customCourses.firstOrNull { it.id == id } ?: return
        _ui.value = _ui.value.copy(customEditing = c, customEditorOpen = true, customMsg = "")
    }

    fun closeCustomEditor() {
        _ui.value = _ui.value.copy(customEditorOpen = false, customEditing = null)
    }

    /**
     * 保存（新建或更新）。**先写盘再改内存**：写不进去就如实报错，不让界面显示一个没存下的课。
     */
    fun saveCustom(course: cn.dkarbon.enroute.data.custom.CustomCourse) {
        if (course.name.isBlank()) {
            _ui.value = _ui.value.copy(customMsg = "课程名必填")
            return
        }
        if (course.rules.isEmpty()) {
            _ui.value = _ui.value.copy(customMsg = "至少要有一个上课时间")
            return
        }
        val id = course.id.ifBlank { "c" + System.currentTimeMillis() }
        val next = _ui.value.customCourses.filterNot { it.id == id } + course.copy(id = id)
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { customStore.save(next.sortedBy { it.name }) }
            DebugLog.log("CUSTOM", "保存自定义课《${course.name}》→ $ok（共 ${next.size} 门）")
            _ui.value = _ui.value.copy(
                customCourses = if (ok) next.sortedBy { it.name } else _ui.value.customCourses,
                customEditorOpen = false,
                customEditing = null,
                customMsg = if (ok) "已保存《${course.name}》" else "保存失败（写盘出错）",
            )
        }
    }

    fun deleteCustom(id: String) {
        val target = _ui.value.customCourses.firstOrNull { it.id == id } ?: return
        val next = _ui.value.customCourses.filterNot { it.id == id }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { customStore.save(next) }
            DebugLog.log("CUSTOM", "删除自定义课《${target.name}》→ $ok")
            _ui.value = _ui.value.copy(
                customCourses = if (ok) next else _ui.value.customCourses,
                customEditorOpen = false,
                customEditing = null,
                customMsg = if (ok) "已删除《${target.name}》" else "删除失败（写盘出错）",
            )
        }
    }

    // ── 设置 ──────────────────────────────────────────────

    fun setTimetableWeek(w: Int) {
        prefs.timetableWeek = w
        _ui.value = _ui.value.copy(timetableWeek = w)
    }

    fun setThemeMode(mode: Int) {
        val m = mode.coerceIn(0, 2)
        prefs.themeMode = m
        _ui.value = _ui.value.copy(themeMode = m)
    }

    // ── 课表卡片（m39）──────────────────────────────────

    /** 版式：0 = 侵蚀纸卡 / 1 = 账页格（老 `Form.current.editorial` 那套） */
    fun setTtCardLayout(v: Int) {
        val s = v.coerceIn(0, 1)
        prefs.ttCardLayout = s
        _ui.value = _ui.value.copy(ttCardLayout = s)
    }

    /** 底色来源：0 = 四色系 / 1 = 单墨 / 2 = 无色 */
    fun setTtCardTint(v: Int) {
        val s = v.coerceIn(0, 2)
        prefs.ttCardTint = s
        _ui.value = _ui.value.copy(ttCardTint = s)
    }

    fun setTtCardSpine(on: Boolean) {
        prefs.ttCardSpine = on
        _ui.value = _ui.value.copy(ttCardSpine = on)
    }

    fun setTtCardErode(on: Boolean) {
        prefs.ttCardErode = on
        _ui.value = _ui.value.copy(ttCardErode = on)
    }

    fun setTtCardGhost(on: Boolean) {
        prefs.ttCardGhost = on
        _ui.value = _ui.value.copy(ttCardGhost = on)
    }

    fun setTtCardFood(on: Boolean) {
        prefs.ttCardFood = on
        _ui.value = _ui.value.copy(ttCardFood = on)
    }

    fun setTtCardNowRing(on: Boolean) {
        prefs.ttCardNowRing = on
        _ui.value = _ui.value.copy(ttCardNowRing = on)
    }

    // ── 槽位化（m41）──────────────────────────────────────

    /**
     * 老数据的 tint / spine / erode → 覆盖位，只跑一次。
     *
     * 不搬的话，老用户升级后预设是「纸卡」，覆盖全是 -1，于是自定义过的
     * 单墨底色 / 关掉的竖脊全被预设盖回去 —— 那是视觉回退，不是升级。
     */
    private fun migrateSlotsIfNeeded() {
        // v1（m41）：m39 的 tint / spine / erode 搬进覆盖位，不让升级冲掉用户挑的观感。
        if (prefs.ttSlotsVersion < 1) {
            prefs.ttOvTone = prefs.ttCardTint.coerceIn(0, 2)
            prefs.ttOvDecor = if (prefs.ttCardSpine) TtDecorSpine else 0
            prefs.ttOvErosion = if (prefs.ttCardErode) 1 else 0
            prefs.ttSlotsVersion = 1
        }
        // v2（2026-11-23）：覆盖位从「-1 跟随」改成「显式值」，并把 m39 遗留的
        // 「账页版式」（tt_card_layout = 1）折成「账页」预设 —— 那个开关已经从设置页删掉，
        // 不折的话老用户会永久卡在账页渲染分支上（设置页怎么调格子都不变）。
        if (prefs.ttSlotsVersion >= 2) return
        val idx = if (prefs.ttCardLayout == 1) {
            TtPresets.indexOf("ledger")
        } else {
            prefs.ttPreset.coerceIn(0, TtPresetCount - 1)
        }
        val slots = TtPresets.resolve(
            idx,
            TtOverrides(
                shape = prefs.ttOvShape,
                decor = prefs.ttOvDecor,
                meta = prefs.ttOvMeta,
                badge = prefs.ttOvBadge,
                erosion = prefs.ttOvErosion,
                motion = prefs.ttOvMotion,
                tone = prefs.ttOvTone,
                header = prefs.ttOvHeader,
                grid = prefs.ttOvGrid,
                backdrop = prefs.ttOvBackdrop,
                bgMotion = prefs.ttOvBgMotion,
                tess = prefs.ttOvTess,
                tessMode = prefs.ttOvTessMode,
            ),
        )
        prefs.ttPreset = idx
        applySlots(slots, idx)
        prefs.ttSlotsVersion = 2
    }

    /**
     * 换预设 = **一次写入那一套的 13 个具体值**。
     *
     * 2026-11-23 主人定：预设那几枚不是「跟随某个基准」，而是「选择对应字段的效果组合」。
     * 所以点下去就把每条轴写成确定值（不再留 -1），界面任何一行都读得出当前值，
     * 也就不会再出现「换了没反应 / 看不出改了什么」。
     *
     * 版式标记（账页）跟着预设一起改 —— 否则「账页」只换格子不换页头，两边对不上。
     */
    fun setTtPreset(v: Int) {
        val idx = v.coerceIn(0, TtPresetCount - 1)
        val preset = TtPresets.of(idx)
        prefs.ttPreset = idx
        prefs.ttCardLayout = preset.layout
        applySlots(preset.slots, idx)
    }

    /** 把一套槽位原样写成显式覆盖位：磁盘与内存同一次改完，避免「半套生效」 */
    private fun applySlots(slots: TtSlots, preset: Int) {
        prefs.ttOvShape = slots.shape
        prefs.ttOvDecor = slots.decor
        prefs.ttOvMeta = slots.meta
        prefs.ttOvBadge = slots.badge
        prefs.ttOvErosion = slots.erosion
        prefs.ttOvMotion = slots.motion
        prefs.ttOvTone = slots.tone
        prefs.ttOvHeader = slots.header
        prefs.ttOvGrid = slots.grid
        prefs.ttOvBackdrop = slots.backdrop
        prefs.ttOvBgMotion = if (slots.bgMotion) 1 else 0
        prefs.ttOvTess = slots.tess
        prefs.ttOvTessMode = slots.tessMode
        _ui.value = _ui.value.copy(
            ttPreset = preset,
            ttCardLayout = prefs.ttCardLayout,
            ttOvShape = slots.shape,
            ttOvDecor = slots.decor,
            ttOvMeta = slots.meta,
            ttOvBadge = slots.badge,
            ttOvErosion = slots.erosion,
            ttOvMotion = slots.motion,
            ttOvTone = slots.tone,
            ttOvHeader = slots.header,
            ttOvGrid = slots.grid,
            ttOvBackdrop = slots.backdrop,
            ttOvBgMotion = if (slots.bgMotion) 1 else 0,
            ttOvTess = slots.tess,
            ttOvTessMode = slots.tessMode,
        )
    }

    /**
     * 改一条覆盖位。[axis] 用 [TtAxis] 里的序号，[value] = -1 表示「跟随预设」。
     * 逐条写而不是循环反射：这里是热路径上的手写代码，宁可多十行。
     */
    fun setTtOverride(axis: Int, value: Int) {
        val v = value
        when (axis) {
            TtAxis.SHAPE -> { prefs.ttOvShape = v; _ui.value = _ui.value.copy(ttOvShape = v) }
            TtAxis.DECOR -> { prefs.ttOvDecor = v; _ui.value = _ui.value.copy(ttOvDecor = v) }
            TtAxis.META -> { prefs.ttOvMeta = v; _ui.value = _ui.value.copy(ttOvMeta = v) }
            TtAxis.BADGE -> { prefs.ttOvBadge = v; _ui.value = _ui.value.copy(ttOvBadge = v) }
            TtAxis.EROSION -> { prefs.ttOvErosion = v; _ui.value = _ui.value.copy(ttOvErosion = v) }
            TtAxis.MOTION -> { prefs.ttOvMotion = v; _ui.value = _ui.value.copy(ttOvMotion = v) }
            TtAxis.TONE -> { prefs.ttOvTone = v; _ui.value = _ui.value.copy(ttOvTone = v) }
            TtAxis.HEADER -> { prefs.ttOvHeader = v; _ui.value = _ui.value.copy(ttOvHeader = v) }
            TtAxis.GRID -> { prefs.ttOvGrid = v; _ui.value = _ui.value.copy(ttOvGrid = v) }
            TtAxis.BACKDROP -> { prefs.ttOvBackdrop = v; _ui.value = _ui.value.copy(ttOvBackdrop = v) }
            TtAxis.BG_MOTION -> { prefs.ttOvBgMotion = v; _ui.value = _ui.value.copy(ttOvBgMotion = v) }
            TtAxis.TESS -> {
                prefs.ttOvTess = v
                _ui.value = _ui.value.copy(ttOvTess = v)
                // 不动「转动逻辑」的取值：关掉再打开要能回到用户原来选的档（整合稿 §3）
            }
            TtAxis.TESS_MODE -> { prefs.ttOvTessMode = v; _ui.value = _ui.value.copy(ttOvTessMode = v) }
        }
    }


    /** 切视觉风格（硬朗农业 / 学院纸本）；与明暗正交，不改 themeMode */
    fun setStyleSet(style: Int) {
        // 学院纸本（1）已下线：传进来也归一到硬朗农业，避免界面拿一套没人认识的语汇渲染
        val s = cn.dkarbon.enroute.ui.theme.StyleSet.normalize(style)
        prefs.styleSet = s
        _ui.value = _ui.value.copy(styleSet = s)
    }

    /**
     * 切取色来源（票 16 追加项②）。
     *
     * 低版本机型上不显示这个开关，但**存值仍然照存**：换机/升级后意图还在。
     * 主题层会自己判断能不能用（`DynamicColorRules.available`），不能用就退回固定色板。
     */
    fun setColorSource(source: Int) {
        val s = cn.dkarbon.enroute.ui.theme.ColorSource.normalize(source)
        prefs.colorSource = s
        _ui.value = _ui.value.copy(colorSource = s)
    }

    /**
     * 保存自定义配色（票 25）。
     *
     * 只写**当前编辑的那一套**（浅色或深色）的两个槽，另一套原样保留 —— 这是
     * 「切主题不丢」的实现：`CustomPalette.withMode` 只动一侧。
     *
     * 对比度闸门在 UI 层（[cn.dkarbon.enroute.ui.theme.PaletteGate]）已经拦过；
     * 这里不再二次校验，避免两处规则漂移 —— 但要保证**写盘与内存同时更新**，
     * 否则重启后颜色会「跳回旧的」。
     */
    fun setCustomPalette(palette: cn.dkarbon.enroute.ui.theme.CustomPalette) {
        prefs.customPrimaryLight = palette.primaryLight
        prefs.customAccentLight = palette.accentLight
        prefs.customPrimaryDark = palette.primaryDark
        prefs.customAccentDark = palette.accentDark
        _ui.value = _ui.value.copy(customPalette = palette)
    }

    /**
     * 上课提醒开关。
     *
     * 关掉要**立刻清掉已排任务**，否则用户关了还在响 —— 这是最招人烦的 bug 类型。
     * 打开时如果还没通知权限，顺手把 Activity 的权限申请拉起来。
     */
    fun setRemindersEnabled(on: Boolean) {
        prefs.remindersEnabled = on
        _ui.value = _ui.value.copy(remindersEnabled = on)
        val app = getApplication<Application>()
        if (on) {
            if (!Reminders.canNotify(app)) onRequestNotifPermission?.invoke()
            _ui.value.timetable?.let { syncReminders(it) }
        } else {
            Reminders.cancelAll(app)
        }
    }

    fun requestNotifPermission() {
        onRequestNotifPermission?.invoke()
    }

    // ── 数据来源 ①：校内登录 URP（课表 + 考试 + 成绩 一次搞定）──

    /**
     * 登录并抓全部数据。
     *
     * ⚠️ URP 主机是校内地址（10.2.0.47），校外必然失败 —— 失败提示要说人话。
     */
    fun login(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            _ui.value = _ui.value.copy(timetableMsg = "账号和密码都要填")
            return
        }
        cn.dkarbon.enroute.data.DebugLog.log("LOGIN", "开始登录（账号 ${user.length} 字符，已隐去）")
        _ui.value = _ui.value.copy(timetableLoading = true, timetableMsg = "正在登录…")
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { UrpClient.login(user, pass) }
            }
            result.onSuccess { s ->
                session = s
                _ui.value = _ui.value.copy(
                    sessionState = SessionState.Active,
                    sessionCheckMsg = "",
                    userName = s.displayName,
                )
                // 红线：日志里不出现姓名本身，只记「有没有识别到」
                DebugLog.log("LOGIN", "登录成功（姓名${if (s.displayName.isBlank()) "未识别" else "已识别"}）")
                // 会话加密存盘：这次成功之后，冷启动就不该再让人重登（票 04 的主诉求）
                val saved = withContext(Dispatchers.IO) { sessionStore.save(s) }
                DebugLog.log(
                    "LOGIN",
                    if (saved) "会话已加密存盘（下次冷启动免重登）" else "会话存盘失败，本次仍可用但下次要重登",
                )
                // 票 14：会话 cookie 会过期，长期免登录还要靠本机加密的凭据（L2）
                val credEnabled = withContext(Dispatchers.IO) { credStore.isEnabled() }
                val asked = withContext(Dispatchers.IO) { credStore.hasPrompted() }
                when {
                    credEnabled -> {
                        val ok = withContext(Dispatchers.IO) { credStore.save(user, pass) }
                        withContext(Dispatchers.IO) { if (ok) credStore.resetFailures() }
                        DebugLog.log("CRED", if (ok) "已开启「保持登录」→ 本次凭据已更新" else "凭据更新失败（会话仍可用）")
                        _ui.value = _ui.value.copy(credEnabled = ok, credStatus = credStore.statusLine())
                    }

                    !asked -> {
                        // 只在内存里留到用户作答为止；不点「启用」就丢弃
                        pendingCred = cn.dkarbon.enroute.data.CredentialStore.Credentials(user.trim(), pass)
                        _ui.value = _ui.value.copy(credPromptVisible = true)
                        DebugLog.log("CRED", "首次登录成功 → 询问是否保持登录（默认关闭）")
                    }
                }
                // 票 24：缓存跟着**账号**走。刚完成一次登录，无法在抓数据前知道是谁的账号，
                // 所以先作废旧缓存（合规红线：绝不把上一个人的身份/成绩留给下一个人看），
                // 紧接着的 fetchSchedule/fetchAcademic/refreshHome 会把它重新填满。
                cache = cn.dkarbon.enroute.data.AcademicCache()
                withContext(Dispatchers.IO) { runCatching { academicCacheStore.clear() } }
                _ui.value = _ui.value.copy(
                    cacheUpdatedLabel = "",
                    profile = cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile(),
                    credit = cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress(),
                    scoreStats = cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats(),
                    homeDailyFetchedAt = 0L,
                )
                fetchSchedule(s)
                fetchAcademic(s)
                // 学业主页那三件套（身份/学分进度/成绩统计）也顺手抓一遍，
                // 否则用户第一次点进「我的」要再等一次网络
                refreshHome(force = true)
            }.onFailure { e ->
                cn.dkarbon.enroute.data.DebugLog.error("LOGIN", "登录失败：${friendlyError(e)} | raw=${e.javaClass.simpleName}: ${e.message}")
                _ui.value = _ui.value.copy(
                    timetableLoading = false,
                    timetableMsg = friendlyError(e),
                )
            }
        }
    }

    private suspend fun fetchSchedule(s: UrpClient.Session) {
        runCatching<Timetable> {
            withContext(Dispatchers.IO) {
                UrpClient.parseSchedule(UrpClient.fetchSchedule(s))
            }
        }.onSuccess { tt ->
            val ok = store.save(tt)
            cn.dkarbon.enroute.data.DebugLog.log("DATA", "课表解析出 ${tt.courses.size} 门（保存=$ok）")
            _ui.value = _ui.value.copy(
                timetableLoading = false,
                timetable = tt,
                timetableMsg = if (ok) "✅ 抓到 ${tt.courses.size} 门课" else "已抓取但保存失败",
            )
            syncReminders(tt)
            refreshWidgets()
        }.onFailure { e ->
            _ui.value = _ui.value.copy(timetableLoading = false, timetableMsg = friendlyError(e))
        }
    }

    /**
     * 抓考试 + 成绩明细（**保留下来的旧入口**，供「这两块一起刷」的地方用）。
     * 失败各自保留旧值，见 [fetchExamsBlock] / [fetchScoresBlock]。
     */
    private suspend fun fetchAcademic(s: UrpClient.Session) {
        fetchExamsBlock(s)
        fetchScoresBlock(s)
    }

    /** 用缓存的会话重新抓考试/成绩（切到那两个 tab 时用） */
    fun refreshAcademic() {
        val s = session
        if (s == null) {
            val hint = noSessionHint()
            _ui.value = _ui.value.copy(examsMsg = hint, scoresMsg = hint)
            return
        }
        viewModelScope.launch { fetchAcademic(s) }
    }

    /** 票 24：**只刷考试**（考试卡/考试页的刷新入口用它，不连带成绩或身份） */
    fun refreshExams() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(examsMsg = noSessionHint())
            return
        }
        viewModelScope.launch { fetchExamsBlock(s) }
    }

    /** 票 24：**只刷成绩**（成绩页的刷新入口用它） */
    fun refreshScores() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(scoresMsg = noSessionHint())
            return
        }
        viewModelScope.launch { fetchScoresBlock(s) }
    }

    // ── 学业主页（身份 / 学分进度 / 成绩统计）──────────────

    /**
     * 没有可用会话时的提示。
     *
     * **按三态分开说**：过期了就说「过期」，别说成「未登录」——
     * 学生明明登过，被告知「没登录」只会以为自己点错了。只说状态这个事实，不写页面路线。
     */
    private fun noSessionHint(): String =
        SessionText.noSessionHint(_ui.value.sessionState).ifBlank {
            "拿不到会话（只在校内网可用）"
        }

    /**
     * 学业主页三块：身份（永久档）+ 学分进度 + 成绩统计（每日档）。
     *
     * @param force 手动刷新（用户按了「刷新」）→ 一律真抓；
     *   `false` 是**自动路径**（冷启动/切页），按 [cn.dkarbon.enroute.data.CachePolicy] 判据决定抓哪些，
     *   全新鲜时一个请求都不发。这就是「不要把刷新做成全量重抓」的落地方式。
     *
     * 三块各自独立失败；失败时**保留旧值**，只在提示里说清「显示的是多久前的数据」。
     */
    fun refreshHome(force: Boolean = false) {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(homeMsg = noSessionHint())
            return
        }
        val now = nowMs()
        val needIdentity = force || cn.dkarbon.enroute.data.CachePolicy.shouldFetchPermanent(cache.profile.isEmpty)
        val needProgress = force || cn.dkarbon.enroute.data.CachePolicy.shouldFetchDaily(
            now,
            minOf(cache.creditAt, cache.scoreStatsAt).takeIf { it > 0L } ?: 0L,
        )
        if (!needIdentity && !needProgress) {
            // 缓存新鲜：告诉用户「这块是缓存，多会儿的」，不假装刚抓过
            _ui.value = _ui.value.copy(homeMsg = "", cacheUpdatedLabel = cacheLine(now))
            DebugLog.log("CACHE", "主页三块都新鲜 → 不发请求")
            return
        }
        _ui.value = _ui.value.copy(homeLoading = true, homeMsg = "抓取中…")
        viewModelScope.launch {
            val okIdentity = if (needIdentity) fetchIdentity(s) else true
            val okProgress = if (needProgress) fetchProgress(s) else true
            val stale = cacheLine()
            _ui.value = _ui.value.copy(
                homeLoading = false,
                cacheUpdatedLabel = stale,
                homeMsg = when {
                    (needIdentity && !okIdentity) || (needProgress && !okProgress) ->
                        "这次没刷新成功。" +
                            (if (cache.isEmpty) "本机也还没有缓存。" else "$stale —— 显示的是缓存")
                    else -> ""
                },
            )
        }
    }

    /** 票 24：**只刷身份**（学籍那一块；身份是永久档，平时不需要动它） */
    fun refreshIdentity() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(homeMsg = noSessionHint())
            return
        }
        _ui.value = _ui.value.copy(homeLoading = true)
        viewModelScope.launch {
            val ok = fetchIdentity(s)
            _ui.value = _ui.value.copy(
                homeLoading = false,
                homeMsg = if (ok) "" else "身份没刷新成功，显示的还是缓存",
                cacheUpdatedLabel = cacheLine(),
            )
        }
    }

    /** 票 24：**只刷学分进度 + 绩点**（不碰身份、不碰考试） */
    fun refreshProgress() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(homeMsg = noSessionHint())
            return
        }
        _ui.value = _ui.value.copy(homeLoading = true)
        viewModelScope.launch {
            val ok = fetchProgress(s)
            _ui.value = _ui.value.copy(
                homeLoading = false,
                homeMsg = if (ok) "" else "学分/绩点没刷新成功，显示的还是缓存",
                cacheUpdatedLabel = cacheLine(),
            )
        }
    }

    // ── 空闲教室 ──────────────────────────────────────────

    /**
     * 刷新空闲教室。
     *
     * 级联顺序：校区 → 教学楼 → 节次 → 教室。任何一级拿不到列表就停在那级，
     * 并在 [AppState.roomsMsg] 里说清停在哪 —— 不显示"暂无数据"这种废话。
     *
     * 校区/教学楼列表是**每日档**（列表几乎不变，走 [cn.dkarbon.enroute.data.CachePolicy]）；
     * 教室明细是**实时数据**，每次刷新都真抓 —— 分档纪律见 AcademicCacheStore 顶部注释。
     */
    fun refreshRooms() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(roomsMsg = noSessionHint())
            return
        }
        _ui.value = _ui.value.copy(roomsLoading = true, roomsMsg = "查询中…")
        viewModelScope.launch {
            val chain = withContext(Dispatchers.IO) { loadRoomsChain(s) }
            // 列表真抓成功才写缓存：失败一律不覆盖旧列表旧时间戳
            if (chain.listsFetched) {
                commitCache {
                    it.merge(
                        nowMs(),
                        campuses = chain.campuses,
                        buildings = chain.buildings,
                        buildingsCampusNo = chain.campusNo,
                    )
                }
            }
            _ui.value = _ui.value.copy(
                roomsLoading = false,
                roomsMsg = chain.msg,
                campuses = chain.campuses.ifEmpty { _ui.value.campuses },
                buildings = chain.buildings.ifEmpty { _ui.value.buildings },
                freeRooms = chain.rooms ?: _ui.value.freeRooms,
                roomsCampusNo = chain.campusNo,
                roomsBuildingNo = chain.buildingNo,
                roomsSession = chain.sessionNo,
            )
        }
    }

    /** 级联抓取的纯 IO 部分（列表走每日缓存，明细每次真抓） */
    private suspend fun loadRoomsChain(
        s: UrpClient.Session,
    ): RoomsChain {
        val now = nowMs()
        // ① 校区列表（每日档）：新鲜就直读缓存，不发请求
        val campusesFresh = !cn.dkarbon.enroute.data.CachePolicy.shouldFetchDaily(now, cache.roomsListsAt) &&
            cache.campuses.isNotEmpty()
        val campuses: List<cn.dkarbon.enroute.data.UrpHomeApi.CampusOption>
        if (campusesFresh) {
            campuses = cache.campuses
        } else {
            val r = runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchCampuses(s) }
            val fetched = r.getOrNull()
            if (fetched.isNullOrEmpty()) {
                return RoomsChain(
                    r.exceptionOrNull()?.let { friendlyError(it) } ?: "没拿到校区列表",
                    emptyList(), emptyList(), null, "", "", 0, false,
                )
            }
            campuses = fetched
        }
        val campusNo = _ui.value.roomsCampusNo.takeIf { it.isNotBlank() && campuses.any { c -> c.number == it } }
            ?: campuses.firstOrNull { it.name == "主校" }?.number
            ?: campuses.firstOrNull()?.number.orEmpty()
        if (campusNo.isBlank()) return RoomsChain("没拿到校区列表", campuses, emptyList(), null, "", "", 0, false)
        val campusName = campuses.firstOrNull { it.number == campusNo }?.name.orEmpty()

        // ② 教学楼列表（每日档）：新鲜**且与当前校区对得上**才用缓存，否则真抓
        val buildingsFresh = !cn.dkarbon.enroute.data.CachePolicy.shouldFetchDaily(now, cache.roomsListsAt) &&
            cache.buildings.isNotEmpty() && cache.buildingsCampusNo == campusNo
        val buildings: List<cn.dkarbon.enroute.data.UrpHomeApi.BuildingOption>
        if (buildingsFresh) {
            buildings = cache.buildings
        } else {
            val r = runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchBuildings(s, campusNo) }
            val fetched = r.getOrNull()
            if (fetched == null) {
                return RoomsChain(
                    r.exceptionOrNull()?.let { friendlyError(it) } ?: "没拿到教学楼列表",
                    campuses, emptyList(), null, campusNo, "", 0, false,
                )
            }
            buildings = fetched
        }
        val listsFetched = !campusesFresh || !buildingsFresh

        // 「全部教学楼」的楼号是 n，接口会返回全校混排，不选它当默认
        val picked = buildings.filter { it.number != "n" && it.number != "全部" }
        val buildingNo = _ui.value.roomsBuildingNo
            .takeIf { it.isNotBlank() && picked.any { b -> b.number == it } }
            ?: picked.firstOrNull { it.number == "2" }?.number
            ?: picked.firstOrNull()?.number.orEmpty()
        if (buildingNo.isBlank()) {
            return RoomsChain("没拿到教学楼列表", campuses, buildings, null, campusNo, "", 0, listsFetched)
        }

        val sessionNo = _ui.value.roomsSession.takeIf { it in 1..12 }
            ?: cn.dkarbon.enroute.ui.home.HomeModel.currentSectionNo(
                nowMinutes(),
                summer = cn.dkarbon.enroute.ui.timetable.SchoolClock.isSummerTime(),
            )
        val buildingName = buildings.firstOrNull { it.number == buildingNo }?.name.orEmpty()
        // 两步流：先 POST 选楼，再 GET 节次（会话粘滞，见 UrpHomeApi.fetchFreeRooms 的注释）
        val rooms = runCatching {
            cn.dkarbon.enroute.data.UrpHomeApi.fetchFreeRooms(
                s, campusNo, campusName, buildingNo, sessionNo, _ui.value.roomsDayPlus,
            )
        }.getOrElse {
            // 网络/连接失败不是「没有空教室」：用 NO_SESSION 结局让空态说清楚该去连校园网
            return RoomsChain(
                friendlyError(it), campuses, buildings,
                cn.dkarbon.enroute.data.UrpHomeApi.FreeRooms(
                    issue = cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue.NO_SESSION,
                ),
                campusNo, buildingNo, sessionNo, listsFetched,
            )
        }
        cn.dkarbon.enroute.data.DebugLog.log(
            "HOME",
            "空闲教室 $campusName/$buildingName($buildingNo) 第 $sessionNo 节 +${_ui.value.roomsDayPlus} 天：" +
                "${rooms.roomCount} 间 · ${rooms.issue}" +
                if (listsFetched) "（列表已重抓并缓存）" else "（列表走缓存）",
        )
        return RoomsChain(
            cn.dkarbon.enroute.ui.home.HomeModel.freeRoomsHeadline(rooms, buildingName),
            campuses, buildings, rooms, campusNo, buildingNo, sessionNo, listsFetched,
        )
    }

    /** 空闲教室级联抓取的返回值（避免在 launch 里写 8 次 state） */
    private data class RoomsChain(
        val msg: String,
        val campuses: List<cn.dkarbon.enroute.data.UrpHomeApi.CampusOption>,
        val buildings: List<cn.dkarbon.enroute.data.UrpHomeApi.BuildingOption>,
        val rooms: cn.dkarbon.enroute.data.UrpHomeApi.FreeRooms?,
        val campusNo: String,
        val buildingNo: String,
        val sessionNo: Int,
        /** 这次真抓了校区/教学楼列表（成功了才允许写缓存） */
        val listsFetched: Boolean,
    )

    fun selectRoomsCampus(no: String) {
        if (no == _ui.value.roomsCampusNo) return
        _ui.value = _ui.value.copy(roomsCampusNo = no, roomsBuildingNo = "")
        refreshRooms()
    }

    fun selectRoomsBuilding(no: String) {
        if (no == _ui.value.roomsBuildingNo) return
        _ui.value = _ui.value.copy(roomsBuildingNo = no)
        refreshRooms()
    }

    fun selectRoomsSession(no: Int) {
        if (no == _ui.value.roomsSession) return
        _ui.value = _ui.value.copy(roomsSession = no)
        refreshRooms()
    }

    fun selectRoomsDay(dayPlus: Int) {
        if (dayPlus == _ui.value.roomsDayPlus) return
        _ui.value = _ui.value.copy(roomsDayPlus = dayPlus.coerceIn(0, 2))
        refreshRooms()
    }

    // ── 教室详情（票 03：从空闲教室下钻）───────────────

    /**
     * 点开一间空闲教室。
     *
     * ⚠️ 两件事必须说清（都进了浮层文案）：
     * 1. `classroomCurriculum/search` 返回的是**教室档案**（座位/类型/院系），
     *    「这间教室被哪些课占了」是行内「课表信息查看」的**另一条路由**，尚未探明 → 占用显示「暂缺」；
     * 2. 这支接口的**过滤参数未实测**，所以不在服务端筛，而是翻页拿回后在客户端精确匹配，
     *    匹配不上就说「没查到」，并交代翻了 30/295 条，绝不摆一张空课表。
     *
     * 会话与票 02 的空闲教室共用同一个 [session]，不另起 cookie 处理。
     */
    fun openRoomDetail(room: cn.dkarbon.enroute.data.UrpHomeApi.RoomOption) {
        val roomNo = room.room.trim()
        if (roomNo.isBlank()) return
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(
                roomDetailRoom = roomNo,
                roomDetailLoading = false,
                roomDetailInfo = null,
                roomDetailMsg = noSessionHint(),
            )
            return
        }
        val termCode = cn.dkarbon.enroute.ui.home.HomeModel.currentTermCode(
            java.time.LocalDate.now(),
            _ui.value.timetable?.term.orEmpty(),
        )
        val buildingName = _ui.value.buildings
            .firstOrNull { it.number == _ui.value.roomsBuildingNo }?.name.orEmpty()
        _ui.value = _ui.value.copy(
            roomDetailRoom = roomNo,
            roomDetailLoading = true,
            roomDetailInfo = null,
            roomDetailMsg = "查询中…",
            roomDetailTotal = 0,
            roomDetailScanned = 0,
        )
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    cn.dkarbon.enroute.data.UrpHomeApi.findClassroom(s, termCode, buildingName, roomNo)
                }
            }
            val look = r.getOrNull()
            cn.dkarbon.enroute.data.DebugLog.log(
                "HOME",
                "教室档案 $buildingName/$roomNo：命中=${look?.info != null} " +
                    "翻过=${look?.scanned ?: -1}/${look?.totalCount ?: -1} 会话失效=${look?.sessionLost == true}" +
                    (r.exceptionOrNull()?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
            )
            _ui.value = _ui.value.copy(
                roomDetailLoading = false,
                roomDetailInfo = look?.info,
                roomDetailTotal = look?.totalCount ?: 0,
                roomDetailScanned = look?.scanned ?: 0,
                roomDetailMsg = when {
                    r.isFailure -> friendlyError(r.exceptionOrNull()!!)
                    look == null -> "没查到这间教室的记录"
                    look.sessionLost -> "未登录或不在校园网"
                    look.info == null ->
                        cn.dkarbon.enroute.ui.home.HomeModel.classroomMissHint(look.totalCount, look.scanned)
                    else -> ""
                },
            )
        }
    }

    fun closeRoomDetail() {
        if (_ui.value.roomDetailRoom.isNotEmpty()) {
            _ui.value = _ui.value.copy(roomDetailRoom = "")
        }
    }

    // ── 教师详情（票 08：新开页，课表 + 官网公开资料）──────

    /**
     * 打开某位老师的详情页：同时抓**课表**（教务，需会话）和**官网公开资料**（站群，不需要会话）。
     *
     * 两条线各自失败互不牵连：官网连不上不该让课表空白，反之亦然。
     */
    fun openTeacherDetail(hit: cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit) {
        val termCode = cn.dkarbon.enroute.ui.home.HomeModel.currentTermCode(
            java.time.LocalDate.now(),
            _ui.value.timetable?.term.orEmpty(),
        )
        _ui.value = _ui.value.copy(
            teacherDetailNo = hit.number,
            teacherDetailName = hit.name,
            teacherDetailDept = hit.dept,
            teacherDetailTitle = hit.title,
            teacherDetailSex = hit.sex,
            teacherDetailTerm = termCode,
            teacherSchedule = cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule(),
            teacherScheduleMsg = "",
            teacherScheduleLoading = false,
            facultyInfo = null,
            facultyMsg = "",
            facultyStale = false,
            facultyCandidates = emptyList(),
        )
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(teacherScheduleMsg = noSessionHint())
        } else {
            loadTeacherSchedule(s, hit, termCode)
        }
        loadFaculty(hit, force = false)
    }

    fun closeTeacherDetail() {
        if (_ui.value.teacherDetailNo.isNotEmpty()) {
            _ui.value = _ui.value.copy(teacherDetailNo = "")
        }
    }

    /** 详情页右上角「刷新」：课表重新抓、官网强制绕过 TTL */
    fun refreshTeacherDetail() {
        val no = _ui.value.teacherDetailNo
        if (no.isBlank()) return
        val hit = cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit(
            name = _ui.value.teacherDetailName,
            number = no,
            dept = _ui.value.teacherDetailDept,
            title = _ui.value.teacherDetailTitle,
            sex = _ui.value.teacherDetailSex,
        )
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(teacherScheduleMsg = noSessionHint())
        } else {
            loadTeacherSchedule(s, hit, _ui.value.teacherDetailTerm)
        }
        loadFaculty(hit, force = true)
    }

    /** 教师课表两步流（① 预热回执 → ② callback） */
    private fun loadTeacherSchedule(
        s: UrpClient.Session,
        hit: cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit,
        termCode: String,
    ) {
        _ui.value = _ui.value.copy(teacherScheduleLoading = true, teacherScheduleMsg = "查询课表中…")
        viewModelScope.launch {
            val planName = cn.dkarbon.enroute.ui.home.HomeModel.termPlanName(termCode)
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    cn.dkarbon.enroute.data.UrpHomeApi.fetchTeacherSchedule(
                        s, hit.number, hit.name, termCode, planName,
                    )
                }
            }
            val res = r.getOrNull()
            cn.dkarbon.enroute.data.DebugLog.log(
                "HOME",
                // 只记工号与条数，不记姓名（日志纪律）
                "教师课表 工号=${hit.number} 学期=$termCode 时段=${res?.schedule?.slots?.size ?: -1}" +
                    " 会话失效=${res?.sessionLost == true}" +
                    (r.exceptionOrNull()?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
            )
            _ui.value = _ui.value.copy(
                teacherScheduleLoading = false,
                teacherSchedule = res?.schedule ?: cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule(),
                teacherScheduleMsg = when {
                    r.isFailure -> friendlyError(r.exceptionOrNull()!!)
                    res == null || res.sessionLost -> "未登录或不在校园网"
                    res.schedule.isEmpty ->
                        "这位老师本学期（" +
                            cn.dkarbon.enroute.ui.home.HomeModel.termLabel(termCode) + "）没有排课"
                    else -> ""
                },
            )
        }
    }

    /** 官网公开资料（只抓这一位；缓存命中且未过期就不发请求） */
    private fun loadFaculty(hit: cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit, force: Boolean) {
        val app = getApplication<Application>()
        _ui.value = _ui.value.copy(facultyLoading = true, facultyMsg = "查官网资料中…")
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    cn.dkarbon.enroute.data.faculty.FacultyRepository.load(
                        app, hit.number, hit.name, hit.dept, forceRefresh = force,
                    )
                }
            }
            val res = r.getOrNull()
            _ui.value = _ui.value.copy(
                facultyLoading = false,
                facultyInfo = res?.info,
                facultyStale = res?.stale == true,
                facultyCandidates = res?.candidates.orEmpty(),
                facultyMsg = when {
                    r.isFailure -> friendlyError(r.exceptionOrNull()!!)
                    res == null -> "官网资料没抓到"
                    else -> cn.dkarbon.enroute.ui.home.HomeModel.facultyMsg(res)
                },
            )
        }
    }

    // ── 查教师 ────────────────────────────────────────────

    fun setTeacherQuery(q: String) {
        // 改了姓名就撤掉上一次的结论（「没找到」），免得对着新名字还挂着旧结果
        _ui.value = _ui.value.copy(teacherQuery = q, teacherMsg = "")
    }

    /**
     * 按姓名搜教师。
     *
     * ⚠️ 只返回**教师条目**（姓名/院系/职称/工号），不是课表网格 ——
     * 「查看某位老师的课表」在 URP 里是行内弹窗的另一条路由，尚未探明（见交付说明遗留）。
     *
     * ⚠️ **必须带学期**：不传学期服务器会返回全部学期（实测 totalCount=1008），
     * 同一工号重复出现，列表 key 冲突直接把 App 崩掉（票 01）。
     * 学期号优先用服务器课表给的 `Timetable.term`，过期/缺失时按今天推。
     */
    fun searchTeachers() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(teacherMsg = noSessionHint())
            return
        }
        val q = _ui.value.teacherQuery.trim()
        if (q.isBlank()) {
            _ui.value = _ui.value.copy(teachers = emptyList(), teacherMsg = "输入教师姓名再搜")
            return
        }
        val termCode = cn.dkarbon.enroute.ui.home.HomeModel.currentTermCode(
            java.time.LocalDate.now(),
            _ui.value.timetable?.term.orEmpty(),
        )
        _ui.value = _ui.value.copy(teacherLoading = true, teacherMsg = "搜索中…")
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { cn.dkarbon.enroute.data.UrpHomeApi.searchTeachers(s, q, termCode) }
            }
            val hits = r.getOrNull()
            val err = r.exceptionOrNull()
            cn.dkarbon.enroute.data.DebugLog.log(
                "HOME",
                "查教师「$q」学期=$termCode 结果=${hits?.size ?: -1}" +
                    (err?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
            )
            _ui.value = _ui.value.copy(
                teacherLoading = false,
                teachers = hits ?: _ui.value.teachers,
                teacherMsg = when {
                    err != null -> friendlyError(err)
                    hits.isNullOrEmpty() -> cn.dkarbon.enroute.ui.home.HomeModel.teacherEmptyHint(q)
                    else -> cn.dkarbon.enroute.ui.home.HomeModel.teacherResultLine(hits.size, termCode)
                },
            )
        }
    }


    /**
     * 载入**示例数据**（仅 debug 源码集有真数据，release 里 `available = false`，入口不显示）。
     *
     * 用途只有一个：这两页依赖「校园网 + 登录态」，QA/版式自查看不到真数据时拿它灌满。
     * 提示文案里写死「示例数据（仅调试包）」，页面上会以 brick 色常驻一行 ——
     * 免得把示例当成真成绩。
     */
    fun loadAcademicSample() {
        if (!cn.dkarbon.enroute.data.AcademicSample.available) return
        val exams = cn.dkarbon.enroute.data.AcademicSample.exams()
        val scores = cn.dkarbon.enroute.data.AcademicSample.scores()
        _ui.value = _ui.value.copy(
            exams = exams,
            scores = scores,
            examsMsg = "示例数据（仅调试包）· ${exams.size} 场，不是从教务抓的",
            scoresMsg = "示例数据（仅调试包）· ${scores.size} 条，不是从教务抓的",
        )
        cn.dkarbon.enroute.data.DebugLog.log("DATA", "载入示例数据：考试 ${exams.size} 场 / 成绩 ${scores.size} 条")
    }

    // ── 诊断：把候选接口逐条打一遍 ────────────────────────
    fun runProbe() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(probing = false, probe = emptyList(), probeMsg = "先登录一次")
            return
        }
        _ui.value = _ui.value.copy(probing = true, probeMsg = "", probe = emptyList())
        viewModelScope.launch {
            // 探一条推一条：界面靠 probe.size 决定哪一行正在被钥匙戳。
            val err = runCatching {
                withContext(Dispatchers.IO) {
                    AcademicApi.probeEach(s) { _, r ->
                        _ui.value = _ui.value.copy(probe = _ui.value.probe + r)
                    }
                }
            }.exceptionOrNull()
            _ui.value = _ui.value.copy(
                probing = false,
                probeMsg = err?.let { friendlyError(it) } ?: "",
            )
        }
    }

    // ── 数据来源 ②：导入 ──────────────────────────────────

    fun importTimetableText(text: String) {
        _ui.value = _ui.value.copy(timetableMsg = "解析中…")
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) { parseAndSave(text) }
            _ui.value = _ui.value.copy(timetableMsg = msg)
            // 导入成功率是这个功能唯一的健康指标。只记成败与行数档，课表原文一个字都不传。
            val rows = _ui.value.timetable?.courses?.size ?: 0
            cn.dkarbon.enroute.data.telemetry.Telemetry.record(
                "import.json.try",
                mapOf(
                    "ok" to (rows > 0).toString(),
                    "rows" to when {
                        rows <= 0 -> "0"
                        rows <= 50 -> "1-50"
                        rows <= 500 -> "51-500"
                        else -> "500+"
                    },
                    "err" to if (rows > 0) "" else "other",
                ),
            )
        }
    }

    fun pickTimetableFile() {
        onPickFile?.invoke()
    }

    fun loadTimetableFromDevice() {
        _ui.value = _ui.value.copy(timetableMsg = "查找中…")
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                val f = store.findPushedFile()
                if (f == null) {
                    "没找到课表文件。用 adb push 到 /data/local/tmp/enroute-data/，或通过「选择 JSON 文件」导入"
                } else {
                    runCatching { parseAndSave(f.readText()) }
                        .getOrElse { "读取 ${f.name} 失败：${it.message}" }
                }
            }
            _ui.value = _ui.value.copy(timetableMsg = msg)
        }
    }

    /**
     * 清除本机**所有**数据（课表 / 偏好 / 周次校准 / 提醒任务 / 调试日志 / 加密会话存档），
     * 用于「从头再登录一次」。清完状态回到全新安装的样子（[SessionState.None]）。
     */
    fun wipeAllData() {
        val app = getApplication<Application>()
        val done = cn.dkarbon.enroute.data.DataWiper.wipeAll(app)
        session = null
        termCalendar.clear()
        // 内存镜像也必须重置：盘清了、界面清了，只要这里还留着旧对象，
        // 任何一次后续写缓存都会把旧身份/成绩又写回盘里（"清了个寂寞"）。
        cache = cn.dkarbon.enroute.data.AcademicCache()
        _ui.value = AppState(
            timetableMsg = "已清除全部数据，可以去登录了",
            wipeMsg = done.joinToString("、"),
            themeMode = prefs.themeMode,
            styleSet = prefs.styleSet,
            remindersEnabled = prefs.remindersEnabled,
        )
        refreshWidgets()
    }

    fun clearTimetable() {
        store.clear()
        Reminders.cancelAll(getApplication())
        session = null
        // 课表清了但会话留着 → 下次冷启动会「静默复活」登录态，跟用户刚清的动作矛盾。
        // 所以这里连持久化会话一起清（不清课表以外的缓存，成绩/考试列表也一起清了，见下）。
        runCatching { sessionStore.clear() }
            .onFailure { DebugLog.log("SESSION", "清持久化会话失败：${it.javaClass.simpleName}") }
        // 票 24：界面上把考试/成绩清空了，**缓存里也必须一起忘掉** ——
        // 否则下次冷启动它们又冒出来，用户会觉得「清了个寂寞」。
        // 一个清除动作只能有一个结果：界面一套、缓存一套是最坏的组合。
        val before = cache
        cache = cache.copy(
            exams = emptyList(),
            examsAt = 0L,
            nextExamAtMs = 0L,
            scores = emptyList(),
            scoresAt = 0L,
        )
        val changed = cache != before
        if (changed) {
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { academicCacheStore.save(cache) }.getOrDefault(false)
                }
                if (!ok) DebugLog.log("CACHE", "清缓存里的考试/成绩失败（下次冷启动可能会再读出来）")
            }
        }
        _ui.value = _ui.value.copy(
            timetable = null,
            sessionState = SessionState.None,
            sessionCheckMsg = "",
            exams = emptyList(),
            scores = emptyList(),
            cacheUpdatedLabel = if (changed) cacheLine() else _ui.value.cacheUpdatedLabel,
            timetableMsg = "已清空课表",
        )
    }

    private fun parseAndSave(text: String): String = runCatching {
        val tt = store.parse(text)
        if (tt.courses.isEmpty()) {
            "解析成功但 0 门课，检查 JSON 是不是空的"
        } else {
            store.save(tt)
            _ui.value = _ui.value.copy(timetable = tt)
            syncReminders(tt)
            refreshWidgets()
            cn.dkarbon.enroute.data.DebugLog.log("DATA", "导入成功：${tt.courses.size} 门课")
            "✅ 导入 ${tt.courses.size} 门课"
        }
    }.getOrElse { "解析失败：${it.message}" }

    // ── 提醒 ──────────────────────────────────────────────

    /** 课表一变就刷新桌面小组件（否则桌面上还是旧课） */
    private fun refreshWidgets() {
        viewModelScope.launch { cn.dkarbon.enroute.widget.refresh(getApplication()) }
    }

    private fun syncReminders(tt: Timetable) {
        val app = getApplication<Application>()
        if (!prefs.remindersEnabled) return
        Reminders.ensureChannel(app)
        val week = termCalendar.currentWeek()
        Reminders.reschedule(app, tt, week, enabled = true)
    }

    // ── 工具 ──────────────────────────────────────────────

    /** 当前时刻的分钟数（空闲教室推"第几小节"用） */
    private fun nowMinutes(): Int {
        val n = java.time.LocalTime.now()
        return n.hour * 60 + n.minute
    }

    private fun friendlyError(e: Throwable): String {
        val raw = e.message ?: e.javaClass.simpleName
        return if (
            raw.contains("10.2.0.47") || raw.contains("timeout", true) ||
            raw.contains("Connect", true) || raw.contains("refused", true) ||
            raw.contains("Failed to connect", true)
        ) {
            "连不上教务系统（10.2.0.47）—— 需要校园网内网或 mangoVPN 才能加载。也可以改用 JSON 导入。"
        } else {
            "失败：$raw"
        }
    }
}
