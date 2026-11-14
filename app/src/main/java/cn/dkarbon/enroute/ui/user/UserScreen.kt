package cn.dkarbon.enroute.ui.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.Reminders
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.home.HomeModel
import cn.dkarbon.enroute.ui.academic.ExamScreen
import cn.dkarbon.enroute.ui.academic.ScoreScreen
import cn.dkarbon.enroute.ui.debug.DebugScreen
import cn.dkarbon.enroute.ui.settings.SettingsScreen
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.widget.WoodenFish
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import cn.dkarbon.enroute.ui.timetable.TermCalendar
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/** 用户页下的子页面（用一个小栈做导航，系统返回键直接可用） */
private enum class UserPage(val title: String) {
    Root("我的"),
    Exam("考试安排"),
    Score("成绩查询"),
    Curriculum("培养方案"),
    Syllabus("课程大纲"),
    History("历年课表"),
    CampusMap("校园地图"),
    Rooms("空闲教室"),
    Teacher("查教师"),
    TeacherDetail("教师详情"),
    Settings("设置"),
    Debug("调试日志"),
    Login("登录"),
}

/**
 * 用户页 —— 底部导航只剩「课表 / 我的」两栏（2026-11-10 主人定）。
 *
 * 结构：
 * ```
 * 我的
 *  ├ 右上齿轮 → 设置
 *  │            └ 调试日志
 *  ├ 考试
 *  └ 成绩
 * ```
 * 为什么把考试/成绩塞进「我的」而不是底部：它们是**低频**页面（一学期看几次），
 * 而课表是每天十几次。底部导航给高频留位置，低频收进个人中心。
 */
@Composable
fun UserScreen(ui: AppState, vm: TimetableViewModel) {
    val stack = remember { mutableStateListOf(UserPage.Root) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val current = stack.last()

    Column(Modifier.fillMaxSize()) {
        if (current != UserPage.Root) {
            SubHeader(current.title, onBack = { stack.removeAt(stack.lastIndex) })
        }
        Box(Modifier.weight(1f)) {
            when (current) {
                UserPage.Root -> UserRoot(
                    ui = ui,
                    vm = vm,
                    onOpenSettings = { stack.add(UserPage.Settings) },
                    onOpenExam = { stack.add(UserPage.Exam) },
                    onOpenScore = { stack.add(UserPage.Score) },
                    onOpenCurriculum = { stack.add(UserPage.Curriculum) },
                    onOpenSyllabus = { stack.add(UserPage.Syllabus) },
                    onOpenHistory = { stack.add(UserPage.History) },
                    onOpenMap = { stack.add(UserPage.CampusMap) },
                    onOpenRooms = { stack.add(UserPage.Rooms) },
                    onOpenTeacher = { stack.add(UserPage.Teacher) },
                    onOpenLogin = { stack.add(UserPage.Login) },
                )
                UserPage.Exam -> ExamScreen(ui = ui, vm = vm)
                UserPage.Score -> ScoreScreen(ui = ui, vm = vm)
                UserPage.Curriculum -> cn.dkarbon.enroute.ui.curriculum.CurriculumScreen(ui = ui, vm = vm)
                UserPage.Syllabus -> cn.dkarbon.enroute.ui.curriculum.SyllabusSearchScreen(ui = ui, vm = vm)
                UserPage.History -> cn.dkarbon.enroute.ui.timetable.HistoryTimetableScreen(ui = ui, vm = vm)
                UserPage.CampusMap -> cn.dkarbon.enroute.ui.map.CampusMapScreen(ui = ui, vm = vm)
                UserPage.Rooms -> cn.dkarbon.enroute.ui.home.RoomsScreen(ui = ui, vm = vm)
                UserPage.Teacher -> cn.dkarbon.enroute.ui.home.TeacherScreen(
                    ui = ui,
                    vm = vm,
                    // 点某位老师 → 压一页「教师详情」（票 08 要求：独立页面，不是浮层）
                    onOpenTeacher = { hit ->
                        vm.openTeacherDetail(hit)
                        stack.add(UserPage.TeacherDetail)
                    },
                )
                UserPage.TeacherDetail -> cn.dkarbon.enroute.ui.home.TeacherDetailScreen(ui = ui, vm = vm)
                UserPage.Settings -> {
                    // 进设置页先把本机凭据状态读准（票 14）
                    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshCredStatus() }
                    SettingsScreen(
                        ui = ui,
                        vm = vm,
                        onOpenDebug = { stack.add(UserPage.Debug) },
                    )
                }
                UserPage.Debug -> DebugScreen()
                UserPage.Login -> LoginScreen(
                    vm = vm,
                    sessionState = ui.sessionState,
                    onDone = { stack.removeAt(stack.lastIndex) },
                )
            }
        }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                modifier = Modifier.size(22.dp),
                tint = t.inkSoft,
            )
        }
        Text(title, fontSize = 15.sp, color = t.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UserRoot(
    ui: AppState,
    vm: TimetableViewModel,
    onOpenSettings: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenScore: () -> Unit,
    onOpenCurriculum: () -> Unit,
    onOpenSyllabus: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenTeacher: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val cal = remember { TermCalendar(ctx.applicationContext) }
    val week = remember(ui.timetable) { cal.currentWeek() }
    val summer = remember { SchoolClock.isSummerTime(LocalDate.now()) }
    val today = remember { LocalDate.now() }

    // 进「我的」就顺手刷新主页三件套（有**有效**会话且上次抓空了才刷，避免每次切页都打网络）。
    // 键是 sessionState 而不是布尔 loggedIn：过期→有效也会重跑一次（续期后立刻有数据）。
    LaunchedEffect(ui.sessionState) {
        if (ui.sessionState == SessionState.Active && ui.credit.isEmpty && !ui.homeLoading) vm.refreshHome()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ── 顶条：只留操作，不重复「我的」标题（底部 tab 已经写了）──
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (ui.sessionState == SessionState.Active) {
                TextButton(onClick = { vm.refreshHome() }, enabled = !ui.homeLoading) {
                    Text(if (ui.homeLoading) "读取中" else "刷新", fontSize = 12.sp)
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = t.inkSoft)
            }
        }

        // ── ① 身份 ──
        cn.dkarbon.enroute.ui.home.IdentityCard(
            profile = ui.profile,
            fallbackName = ui.userName,
            sessionState = ui.sessionState,
            sessionCheckMsg = ui.sessionCheckMsg,
            loading = ui.homeLoading,
            onLogin = onOpenLogin,
        )

        // 会话过期时在身份卡下面补一条**常驻**入口（卡片可点，但可点这件事要写出来）
        if (ui.sessionState == SessionState.Expired) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "会话已过期 · 重新登录一次即可继续，课表还在",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.brick,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenLogin) {
                    Text("续期登录", fontSize = 12.sp, color = t.brick)
                }
            }
        }

        cn.dkarbon.enroute.ui.home.CardGap()

        // ── ② 下一场考试（时间最敏感，放最上面）──
        cn.dkarbon.enroute.ui.home.NextExamCard(
            exams = ui.exams,
            today = today,
            loading = ui.examsLoading,
            onOpen = onOpenExam,
        )

        cn.dkarbon.enroute.ui.home.CardGap()

        // ── ③ 学分进度 ──
        cn.dkarbon.enroute.ui.home.CreditProgressCard(
            credit = ui.credit,
            loading = ui.homeLoading,
            onOpen = onOpenScore,
        )

        cn.dkarbon.enroute.ui.home.CardGap()

        // ── ④ 成绩概览 ──
        cn.dkarbon.enroute.ui.home.ScoreOverviewCard(
            stats = ui.scoreStats,
            loading = ui.homeLoading,
            onOpen = onOpenScore,
        )

        if (ui.homeMsg.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            cn.dkarbon.enroute.ui.home.HomeFootNote(ui.homeMsg)
        }

        // ── 数据概览 ──
        cn.dkarbon.enroute.ui.home.SectionLabel("本机数据")
        GroupCard {
            StatRow("课表", "${ui.timetable?.courses?.size ?: 0} 门课")
            Divider()
            StatRow(
                "周次",
                when {
                    week != null -> "第 $week 周 · ${SchoolClock.hintOf(summer)}"
                    else -> "未校准（去课表页校准一次）"
                },
            )
            Divider()
            StatRow("上课提醒", if (ui.remindersEnabled) "开 · 课前 ${Reminders.LEADS_MIN.joinToString(" / ")} 分钟" else "关")
            Divider()
            // 三态在「本机数据」里也要看得见：这是学生判断「要不要去重登」的唯一依据
            StatRow("登录态", SessionText.stateLabel(ui.sessionState))
        }

        Spacer(Modifier.height(14.dp))

        // ── 子页面入口 ──
        cn.dkarbon.enroute.ui.home.SectionLabel("教务")
        GroupCard {
            // 登录/续期入口一直在，只是三态说法不同：没登过叫「登录」，过期叫「续期」
            if (ui.sessionState != SessionState.Active) {
                NavRow(SessionText.actionLabel(ui.sessionState), "", onOpenLogin)
                Divider()
            }
            NavRow("考试安排", if (ui.exams.isEmpty()) "—" else "${ui.exams.size} 场", onOpenExam)
            Divider()
            NavRow("成绩查询", if (ui.scores.isEmpty()) "—" else "${ui.scores.size} 条", onOpenScore)
            Divider()
            NavRow("培养方案", ui.planMajor.ifBlank { "" }, onOpenCurriculum)
            Divider()
            NavRow("课程大纲", if (ui.syllabusIndex.isEmpty()) "" else "${ui.syllabusIndex.size} 门", onOpenSyllabus)
            Divider()
            NavRow("历年课表", ui.historyPlanCode.takeIf { ui.historyTimetable != null }?.let { HomeModel.termLabel(it) }.orEmpty(), onOpenHistory)
            Divider()
            NavRow("校园地图", "相对位置", onOpenMap)
            Divider()
            NavRow(
                "空闲教室",
                if (ui.freeRooms.roomCount > 0) "第 ${ui.roomsSession} 节 · ${ui.freeRooms.roomCount} 间" else "",
                onOpenRooms,
            )
            Divider()
            NavRow("查教师", if (ui.teachers.isEmpty()) "" else "${ui.teachers.size} 位", onOpenTeacher)
            Divider()
            NavRow("设置", "", onOpenSettings)
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "沈农在途 ${BuildConfig.VERSION_NAME}",
            fontSize = 10.sp,
            fontFamily = NumericFont,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(18.dp))
        WoodenFish()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun Divider() {
    val t = InkTokens.current
    Box(Modifier.fillMaxWidth().height(0.6.dp).background(t.rule))
}

@Composable
private fun StatRow(label: String, value: String) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, color = t.inkSoft)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NavRow(label: String, badge: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = t.ink)
        Spacer(Modifier.weight(1f))
        if (badge.isNotBlank()) {
            Text(badge, fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = t.inkFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}
