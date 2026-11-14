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

    /** 静默续期凭据（票 14）：本机 Keystore 加密，默认关闭 */
    private val credStore = cn.dkarbon.enroute.data.CredentialStore(app)

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
                val remind = prefs.remindersEnabled
                Loaded(store.load(), week, theme, style, remind, colorSource)
            }
            _ui.value = _ui.value.copy(
                timetable = saved.tt,
                timetableWeek = saved.week,
                themeMode = saved.theme,
                styleSet = saved.style,
                colorSource = saved.colorSource,
                remindersEnabled = saved.remind,
                timetableMsg = if (saved.tt == null) "" else "已载入 ${saved.tt.courses.size} 门课",
            )
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
        refreshHome()
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
                    entry == null -> "没按学籍专业定位到方案，请在下面手动选专业"
                    plan == null -> "读「$year 级 ${entry.major}」的方案失败（资产缺失或损坏）"
                    else -> ""
                },
            )
        }
    }

    /** 手动切换版本年份（2024 / 2025） */
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

    private fun loadPlan(year: String, entryId: String, major: String) {
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
        }
    }

    /**
     * 点某门课（培养方案页、课表详情、搜索页三处共用）。
     *
     * 匹配优先级：课程代码精确 → 归一课程名精确 → 名称包含；命中多条**不猜**，把候选摆出来。
     * 匹配不上照实说「这本院级大纲册子里没有这门课」——**不空卡片、不转圈**（票 10 的硬要求）。
     */
    fun openSyllabus(code: String, name: String) {
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
            _ui.value = _ui.value.copy(
                syllabusOpenId = entry?.id.orEmpty(),
                syllabusDetail = detail,
                syllabusDetailLoading = false,
                syllabusCandidates = match.candidates,
                syllabusDetailMsg = when {
                    entry == null && match.candidates.isNotEmpty() ->
                        "有 ${match.candidates.size} 门课名字对得上，不替你猜 —— 点一门看它的大纲"
                    entry == null ->
                        "这本院级大纲册子里没有「${name.ifBlank { code }}」—— 册子只收土地与环境学院的 " +
                            "$count 门（2021 版），通识课与外院课通常不在其中"
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
                syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(list, _ui.value.syllabusQuery, _ui.value.syllabusType),
                syllabusIndexLoading = false,
                syllabusIndexMsg = if (list.isEmpty()) "没读到大纲索引（安装包可能不完整）" else "",
            )
        }
    }

    fun setSyllabusQuery(q: String) {
        _ui.value = _ui.value.copy(
            syllabusQuery = q,
            syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(_ui.value.syllabusIndex, q, _ui.value.syllabusType),
        )
    }

    fun setSyllabusType(type: String) {
        val t = if (type == _ui.value.syllabusType) "" else type   // 再点一次取消筛选
        _ui.value = _ui.value.copy(
            syllabusType = t,
            syllabusList = cn.dkarbon.enroute.data.curriculum.SyllabusParse.filter(_ui.value.syllabusIndex, _ui.value.syllabusQuery, t),
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
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(
                historyMsg = "还没登录。历年课表要打教务（10.2.0.47，只在校内网可用）——" +
                    "回「课表」页登录一次即可",
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
            // 默认当前学期：优先服务器标「当前」的那项，否则用日期推出来的学期，再次是第一个
            val guess = cn.dkarbon.enroute.ui.home.HomeModel.currentTermCode(
                java.time.LocalDate.now(), _ui.value.timetable?.term.orEmpty(),
            )
            val pick = terms.firstOrNull { it.current }?.code
                ?: terms.firstOrNull { it.code == guess }?.code
                ?: terms.first().code
            cn.dkarbon.enroute.data.DebugLog.log(
                "HISTORY", "学期候选 ${terms.map { it.code }}，默认 $pick",
            )
            _ui.value = _ui.value.copy(historyTerms = terms, historyLoading = false)
            loadHistoryTerm(pick, force = false)
        }
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
                    historyMsg = "还没登录，读不到这一学期的课表 —— 回「课表」页登录一次",
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
            // 共建引导（票 12 §8.3）：第一次进地图页自动弹一次；之后只能从「怎么补？」打开
            val guideSeen = withContext(Dispatchers.IO) { runCatching { prefs.mapGuideSeen }.getOrDefault(false) }
            _ui.value = _ui.value.copy(
                mapPackage = pkg,
                mapLoading = false,
                mapEdits = edits,
                mapOrphans = orphans,
                mapSelectedId = "",
                mapGuideVisible = !guideSeen,
                mapMsg = when {
                    !pkg.ok -> "地图包读不出来：${cn.dkarbon.enroute.data.map.MapPackageRules.describe(pkg.issue)}"
                    else -> pkg.fallbackNote
                },
            )
        }
    }

    /** 「怎么补？」或在设置里再次查看共建引导 */
    fun openMapGuide() {
        _ui.value = _ui.value.copy(mapGuideVisible = true, mapGuideHint = "")
    }

    /**
     * 关掉引导卡（两个按钮都走这里）。
     *
     * 关一次就记下「弹过了」—— 下次进页面不再自动弹，但「怎么补？」随时能开。
     * @param start true = 点了「开始标注」，给一句「去点地图上任意一块」
     */
    fun closeMapGuide(start: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { prefs.mapGuideSeen = true } }
        }
        _ui.value = _ui.value.copy(
            mapGuideVisible = false,
            mapGuideHint = if (start) cn.dkarbon.enroute.ui.map.MapNudge.START_HINT else "",
        )
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
                fetchSchedule(s)
                fetchAcademic(s)
                // 学业主页那三件套（身份/学分进度/成绩统计）也顺手抓一遍，
                // 否则用户第一次点进「我的」要再等一次网络
                refreshHome()
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

    private suspend fun fetchAcademic(s: UrpClient.Session) {
        _ui.value = _ui.value.copy(examsLoading = true, scoresLoading = true, examsMsg = "抓取中…", scoresMsg = "抓取中…")
        val exams = runCatching { withContext(Dispatchers.IO) { AcademicApi.fetchExams(s) } }
        val scores = runCatching { withContext(Dispatchers.IO) { AcademicApi.fetchScores(s) } }
        cn.dkarbon.enroute.data.DebugLog.log(
            "DATA",
            "考试 ${exams.getOrDefault(emptyList()).size} 场 / 成绩 ${scores.getOrDefault(emptyList()).size} 条" +
                (exams.exceptionOrNull()?.let { " · 考试异常 ${it.message}" } ?: "") +
                (scores.exceptionOrNull()?.let { " · 成绩异常 ${it.message}" } ?: ""),
        )
        _ui.value = _ui.value.copy(
            examsLoading = false,
            scoresLoading = false,
            exams = exams.getOrDefault(emptyList()),
            scores = scores.getOrDefault(emptyList()),
            examsMsg = if (exams.getOrDefault(emptyList()).isEmpty())
                "没抓到考试（接口可能不同，去设置页跑一次「接口诊断」）"
            else "✅ ${exams.getOrDefault(emptyList()).size} 场考试",
            scoresMsg = if (scores.getOrDefault(emptyList()).isEmpty())
                "没抓到成绩（接口可能不同，去设置页跑一次「接口诊断」）"
            else "✅ ${scores.getOrDefault(emptyList()).size} 条成绩",
        )
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

    // ── 学业主页（身份 / 学分进度 / 成绩统计）──────────────

    /**
     * 没有可用会话时的提示。
     *
     * **按三态分开说**：过期了就说「过期、去续期」，别说成「还没登录」——
     * 学生明明登过，被告知「没登录」只会以为自己点错了。
     */
    private fun noSessionHint(): String =
        SessionText.noSessionHint(_ui.value.sessionState).ifBlank {
            "拿不到会话。回「课表」页重新登录一次（只在校内网可用）"
        }

    /**
     * 主页三件套。
     *
     * 三个请求**各自独立失败**：身份抓不到不该连累学分进度，反之亦然。
     * 最后按成功数给一句话，不全成功也不假装全成功。
     */
    fun refreshHome() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(homeMsg = noSessionHint())
            return
        }
        _ui.value = _ui.value.copy(homeLoading = true, homeMsg = "抓取中…")
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                Triple(
                    runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchProfile(s) },
                    runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchCreditProgress(s) },
                    runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchScoreStats(s) },
                )
            }
            val (pf, cr, sc) = r
            val okCount = listOf(pf.isSuccess, cr.isSuccess, sc.isSuccess).count { it }
            val firstErr = listOfNotNull(pf, cr, sc).firstNotNullOfOrNull { it.exceptionOrNull() }
            cn.dkarbon.enroute.data.DebugLog.log(
                "HOME",
                "主页抓取：身份=${pf.isSuccess} 学分=${cr.isSuccess} 成绩=${sc.isSuccess}" +
                    (firstErr?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
            )
            _ui.value = _ui.value.copy(
                homeLoading = false,
                profile = pf.getOrNull() ?: _ui.value.profile,
                credit = cr.getOrNull() ?: _ui.value.credit,
                scoreStats = sc.getOrNull() ?: _ui.value.scoreStats,
                homeMsg = when {
                    okCount == 3 -> ""
                    okCount == 0 -> friendlyError(firstErr ?: IllegalStateException("未知错误"))
                    else -> "$okCount/3 项抓取成功，其余下次刷新再试"
                },
            )
        }
    }

    // ── 空闲教室 ──────────────────────────────────────────

    /**
     * 刷新空闲教室。
     *
     * 级联顺序：校区 → 教学楼 → 节次 → 教室。任何一级拿不到列表就停在那级，
     * 并在 [AppState.roomsMsg] 里说清停在哪 —— 不显示"暂无数据"这种废话。
     */
    fun refreshRooms() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(roomsMsg = noSessionHint())
            return
        }
        _ui.value = _ui.value.copy(roomsLoading = true, roomsMsg = "查询中…")
        viewModelScope.launch {
            val (msg, campuses, buildings, rooms, campusNo, buildingNo, sessionNo) =
                withContext(Dispatchers.IO) { loadRoomsChain(s) }
            _ui.value = _ui.value.copy(
                roomsLoading = false,
                roomsMsg = msg,
                campuses = campuses.ifEmpty { _ui.value.campuses },
                buildings = buildings.ifEmpty { _ui.value.buildings },
                freeRooms = rooms ?: _ui.value.freeRooms,
                roomsCampusNo = campusNo,
                roomsBuildingNo = buildingNo,
                roomsSession = sessionNo,
            )
        }
    }

    /** 级联抓取的纯 IO 部分（7 元组让 state 一次写入，避免中间态闪烁） */
    private suspend fun loadRoomsChain(
        s: UrpClient.Session,
    ): RoomsChain {
        val campuses = runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchCampuses(s) }
            .getOrElse { return RoomsChain(friendlyError(it), emptyList(), emptyList(), null, "", "", 0) }
        val campusNo = _ui.value.roomsCampusNo.takeIf { it.isNotBlank() && campuses.any { c -> c.number == it } }
            ?: campuses.firstOrNull { it.name == "主校" }?.number
            ?: campuses.firstOrNull()?.number.orEmpty()
        if (campusNo.isBlank()) return RoomsChain("没拿到校区列表", campuses, emptyList(), null, "", "", 0)
        val campusName = campuses.firstOrNull { it.number == campusNo }?.name.orEmpty()

        val buildings = runCatching { cn.dkarbon.enroute.data.UrpHomeApi.fetchBuildings(s, campusNo) }
            .getOrElse { return RoomsChain(friendlyError(it), campuses, emptyList(), null, campusNo, "", 0) }
        // 「全部教学楼」的楼号是 n，接口会返回全校混排，不选它当默认
        val picked = buildings.filter { it.number != "n" && it.number != "全部" }
        val buildingNo = _ui.value.roomsBuildingNo
            .takeIf { it.isNotBlank() && picked.any { b -> b.number == it } }
            ?: picked.firstOrNull { it.number == "2" }?.number
            ?: picked.firstOrNull()?.number.orEmpty()
        if (buildingNo.isBlank()) return RoomsChain("没拿到教学楼列表", campuses, buildings, null, campusNo, "", 0)

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
                campusNo, buildingNo, sessionNo,
            )
        }
        cn.dkarbon.enroute.data.DebugLog.log(
            "HOME",
            "空闲教室 $campusName/$buildingName($buildingNo) 第 $sessionNo 节 +${_ui.value.roomsDayPlus} 天：" +
                "${rooms.roomCount} 间 · ${rooms.issue}",
        )
        return RoomsChain(
            cn.dkarbon.enroute.ui.home.HomeModel.freeRoomsHeadline(rooms, buildingName),
            campuses, buildings, rooms, campusNo, buildingNo, sessionNo,
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
                    look.sessionLost -> "没拿到数据：教务登录态可能已失效，或当前不在校园网 —— " +
                        "回「课表」页重新登录、连上校园网再试"
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
                    res == null || res.sessionLost ->
                        "没拿到课表：教务登录态可能已失效，或当前不在校园网 —— " +
                            "回「课表」页重新登录、连上校园网后再刷新"
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
        _ui.value = _ui.value.copy(teacherQuery = q)
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

    // ── 诊断：把候选接口全打一遍 ──────────────────────────
    fun runProbe() {
        val s = session
        if (s == null) {
            _ui.value = _ui.value.copy(probeMsg = "先登录一次再诊断", probing = false)
            return
        }
        _ui.value = _ui.value.copy(probing = true, probeMsg = "探测中…", probe = emptyList())
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { AcademicApi.probe(s) } }
            _ui.value = _ui.value.copy(
                probing = false,
                probe = r.getOrDefault(emptyList()),
                probeMsg = r.exceptionOrNull()?.let { friendlyError(it) }
                    ?: "探测完成，看下面哪条返回了 JSON",
            )
        }
    }

    // ── 数据来源 ②：导入 ──────────────────────────────────

    fun importTimetableText(text: String) {
        _ui.value = _ui.value.copy(timetableMsg = "解析中…")
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) { parseAndSave(text) }
            _ui.value = _ui.value.copy(timetableMsg = msg)
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
                    "没找到课表文件。用 adb push 到 /data/local/tmp/enroute-data/，或点「选择 JSON 文件」"
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
        _ui.value = _ui.value.copy(
            timetable = null,
            sessionState = SessionState.None,
            sessionCheckMsg = "",
            exams = emptyList(),
            scores = emptyList(),
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
            "连不上教务系统（10.2.0.47）。这个地址只在校内网可达 —— 请连校园网或 VPN 再试，或改用 JSON 导入。"
        } else {
            "失败：$raw"
        }
    }
}
