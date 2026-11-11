package cn.dkarbon.enroute.ui

import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.ProbeResult
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.data.Timetable

/**
 * 界面状态（单一来源）。
 *
 * 名字特意保持 `AppState` 而不是 `UiState`：这个 App 只有课表一件事，
 * 考试/成绩是它的延伸，全部往这里加字段，不要搞多个 state 流互相打架。
 */
data class AppState(
    // ── 课表 ──
    val timetable: Timetable? = null,
    val timetableLoading: Boolean = false,
    /** 导入/抓取的结果提示（显示在导入面板里，不弹窗） */
    val timetableMsg: String = "",
    /** 当前查看第几周；-1 = 用学期起止日估算 */
    val timetableWeek: Int = -1,

    // ── 会话（仅内存，进程结束即失效）──
    /** 本次运行是否已登录 URP；考试/成绩要用 */
    val loggedIn: Boolean = false,
    /** 登录用户姓名（从门户页「欢迎您，XXX」解析，可能为空） */
    val userName: String = "",

    // ── 考试 ──
    val exams: List<ExamItem> = emptyList(),
    val examsLoading: Boolean = false,
    val examsMsg: String = "",

    // ── 成绩 ──
    val scores: List<ScoreItem> = emptyList(),
    val scoresLoading: Boolean = false,
    val scoresMsg: String = "",

    // ── 诊断（仅 debug 面板用）──
    val probe: List<ProbeResult> = emptyList(),
    val probing: Boolean = false,
    val probeMsg: String = "",

    // ── 设置 ──
    /** 主题模式：0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    /** 上课提醒总开关（默认开） */
    val remindersEnabled: Boolean = true,

    /** 「清除所有数据」的结果说明（只在设置页显示一次） */
    val wipeMsg: String = "",

    /** 一次性提示（toast），消费后清空 */
    val toast: String? = null,
)
