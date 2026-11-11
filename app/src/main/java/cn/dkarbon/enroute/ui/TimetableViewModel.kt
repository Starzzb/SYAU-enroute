package cn.dkarbon.enroute.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.dkarbon.enroute.data.AcademicApi
import cn.dkarbon.enroute.data.Prefs
import cn.dkarbon.enroute.data.Reminders
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
 * 全部业务逻辑（课表 / 考试 / 成绩 / 提醒）。
 *
 * 三条硬规矩：
 *   1. **凭据只在内存**：`session` 是普通字段，进程一死就没了。不写 Prefs、不写日志。
 *   2. **课表数据只落一个文件**（[TimetableStore]），登录抓取和 JSON 导入走同一条保存路径。
 *   3. **任何一次课表变化都重排提醒**（[syncReminders]），否则改了课表旧提醒还在响。
 */
class TimetableViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(AppState())
    val ui: StateFlow<AppState> = _ui.asStateFlow()

    private val prefs = Prefs(app)
    private val store = TimetableStore(app)
    private val termCalendar = TermCalendar(app)

    /** URP 会话：仅本进程有效，用于课表/考试/成绩三个接口复用同一次登录 */
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
                val remind = prefs.remindersEnabled
                Triple(store.load(), week, theme) to remind
            }
            val (triple, remind) = saved
            val (tt, week, theme) = triple
            _ui.value = _ui.value.copy(
                timetable = tt,
                timetableWeek = week,
                themeMode = theme,
                remindersEnabled = remind,
                timetableMsg = if (tt == null) "" else "已载入 ${tt.courses.size} 门课",
            )
            withContext(Dispatchers.IO) {
                Reminders.ensureChannel(app)
                // 每日重排任务有 24h 门控，冷启动通常直接跳过（不再每次 enqueue）
                if (prefs.shouldRescheduleDaily()) {
                    Reminders.ensureDailyReschedule(app)
                    prefs.markDailyRescheduled()
                }
                if (tt != null) syncReminders(tt)
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
                _ui.value = _ui.value.copy(loggedIn = true, userName = s.displayName)
                cn.dkarbon.enroute.data.DebugLog.log("LOGIN", "登录成功，姓名=${s.displayName.ifBlank { "未识别" }}")
                fetchSchedule(s)
                fetchAcademic(s)
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
            _ui.value = _ui.value.copy(
                examsMsg = "还没登录。回「课表」页登录一次即可（只在校内网可用）",
                scoresMsg = "还没登录。回「课表」页登录一次即可（只在校内网可用）",
            )
            return
        }
        viewModelScope.launch { fetchAcademic(s) }
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
     * 清除本机**所有**数据（课表 / 偏好 / 周次校准 / 提醒任务 / 调试日志），
     * 用于「从头再登录一次」。清完状态回到全新安装的样子。
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
            remindersEnabled = prefs.remindersEnabled,
        )
        refreshWidgets()
    }

    fun clearTimetable() {
        store.clear()
        Reminders.cancelAll(getApplication())
        session = null
        _ui.value = _ui.value.copy(
            timetable = null,
            loggedIn = false,
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
