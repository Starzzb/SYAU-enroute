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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.ExamItem
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
import cn.dkarbon.enroute.ui.theme.Space
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
    About("关于"),
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
    // A6：用 rememberSaveable 保住子页栈 —— 切到资讯再回来，仍停在原页面（返回键行为不变）
    val stack = androidx.compose.runtime.saveable.rememberSaveable(
        saver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<UserPage>, String>(
            save = { list -> list.map { it.name } },
            restore = { names ->
                mutableStateListOf(*names.map { UserPage.valueOf(it) }.toTypedArray())
            },
        ),
    ) { mutableStateListOf(UserPage.Root) }
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
                        onOpenAbout = { stack.add(UserPage.About) },
                    )
                }
                UserPage.About -> cn.dkarbon.enroute.ui.about.AboutScreen()
                UserPage.Debug -> DebugScreen(onSelftest = { vm.runCryptoSelfTest() })
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
    var localExpanded by rememberSaveable { mutableStateOf(false) }

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
        // 设置只剩这一处齿轮入口（门禁 B3：同一目的地不许两个入口）
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

        // ── 教务组（数据来源 = URP，前提是登录态）──
        // 组头右侧就是本组的**状态前提**（门禁 B2），常驻不位移（A7）：
        // 登录动作本身只在身份卡上（门禁 B3），这里不再多一条登录行。
        GroupHead(
            title = "教务",
            premise = MyPageModel.sessionPremiseLabel(ui.sessionState) + if (ui.homeLoading) " · 读取中" else "",
            premiseColor = when (ui.sessionState) {
                SessionState.Expired -> t.brick
                SessionState.None -> t.inkFaint
                SessionState.Active -> t.olive
            },
        )

        cn.dkarbon.enroute.ui.home.IdentityCard(
            profile = ui.profile,
            fallbackName = ui.userName,
            sessionState = ui.sessionState,
            sessionCheckMsg = ui.sessionCheckMsg,
            loading = ui.homeLoading,
            onLogin = onOpenLogin,
        )

        Spacer(Modifier.height(Space.cardGap))

        // ① 下一场考试（时间最敏感，span2，倒计时 26sp 等宽）
        val nextExam = HomeModel.nextExam(ui.exams, today)
        WideTile(
            title = "下一场考试",
            hint = if (ui.exams.isNotEmpty()) "共 ${ui.exams.size} 场" else "",
            onClick = onOpenExam,
        ) {
            ExamTileBody(
                next = nextExam,
                today = today,
                loading = ui.examsLoading,
                examCount = ui.exams.size,
                finished = HomeModel.finishedExamCount(ui.exams, today),
            )
        }

        Spacer(Modifier.height(Space.cardGap))

        // ② 学业两个 1×1：培养方案进度（A1：点它去**培养方案页**，不是成绩页）｜加权绩点
        TileRow {
            HalfTile(
                title = "培养方案",
                value = MyPageModel.planPercentLabel(ui.credit),
                sub = MyPageModel.planRemainLabel(ui.credit),
                unknown = ui.credit.percent == null,
                onClick = onOpenCurriculum,
            )
            HalfTile(
                title = "加权绩点",
                value = MyPageModel.gpaTileLabel(ui.scoreStats),
                sub = MyPageModel.gpaSubLabel(ui.scoreStats),
                unknown = ui.scoreStats.weightedGpa == null,
                onClick = onOpenScore,
            )
        }

        Spacer(Modifier.height(Space.s))

        // ③ 空闲教室（span2）：一周 ≥3 次要用的东西，从 1.57 屏提到首屏
        WideTile(
            title = "空闲教室",
            hint = if (ui.freeRooms.roomCount > 0) "第 ${ui.roomsSession} 节" else "",
            onClick = onOpenRooms,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    MyPageModel.freeRoomsTile(ui.roomsSession, ui.freeRooms.roomCount),
                    fontSize = if (ui.freeRooms.roomCount > 0) 20.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ui.freeRooms.roomCount > 0) t.ink else t.inkFaint,
                    fontFamily = NumericFont,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (ui.freeRooms.roomCount > 0) "点开看楼栋明细" else "点进去查一次",
                    fontSize = 9.5.sp,
                    color = t.inkFaint,
                )
            }
        }

        Spacer(Modifier.height(Space.s))

        // ④ 工具格 2×2（都是**教务**来源：查教师/大纲/历年/成绩，全要 URP 登录态）
        TileRow {
            ToolTile(
                label = "查教师",
                sub = MyPageModel.teacherBadge(),
                onClick = onOpenTeacher,
            )
            ToolTile(
                label = "课程大纲",
                sub = MyPageModel.syllabusBadge(ui.syllabusIndex.size),
                onClick = onOpenSyllabus,
            )
        }
        Spacer(Modifier.height(Space.s))
        TileRow {
            ToolTile(
                label = "历年课表",
                sub = MyPageModel.historyBadge(
                    ui.historyPlanCode.takeIf { ui.historyTimetable != null }
                        ?.let { HomeModel.termLabel(it) }.orEmpty(),
                ),
                onClick = onOpenHistory,
            )
            ToolTile(
                label = "成绩查询",
                sub = MyPageModel.scoreBadge(ui.scores.size, ui.scoresLoading),
                onClick = onOpenScore,
            )
        }

        if (ui.homeMsg.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            cn.dkarbon.enroute.ui.home.HomeFootNote(ui.homeMsg)
        }

        // ── 校园组（数据来源 = 离线示意图；不申请定位）──
        GroupHead(title = "校园", premise = "离线可用 · 不申请定位")
        TileRow {
            ToolTile(
                label = "校园地图",
                sub = MyPageModel.mapBadge(),
                onClick = onOpenMap,
            )
        }

        // ── 本机组（数据来源 = 本机存储）──
        // 组头 premise 与折叠头摘要都写「课表门数/周次」——这正是本组的状态前提（B2）
        GroupHead(title = "本机", premise = MyPageModel.weekLabel(week))
        FoldSection(
            summary = MyPageModel.localSummary(ui.timetable?.courses?.size, week),
            expanded = localExpanded,
            onToggle = { localExpanded = !localExpanded },
        ) {
            FoldRow("课表", MyPageModel.courseCountLabel(ui.timetable?.courses?.size))
            FoldRow("周次", MyPageModel.weekLabel(week))
            FoldRow(
                "上课提醒",
                if (ui.remindersEnabled) "开 · 课前 ${Reminders.LEADS_MIN.joinToString(" / ")} 分钟" else "关",
            )
            FoldRow("登录态", SessionText.stateLabel(ui.sessionState))
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "沈农在途 ${BuildConfig.VERSION_NAME}",
            fontSize = 10.sp,
            fontFamily = NumericFont,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(18.dp))
        WoodenFish(studentNo = ui.profile.studentNo)
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * 考试磁贴的正文：倒计时 26sp 等宽数字 + 课程名/时间/地点。
 *
 * 三种空态分开写（门禁 C1：未知不是 0）—— 没数据 / 全考完 / 读取中。
 */
@Composable
private fun ExamTileBody(
    next: ExamItem?,
    today: LocalDate,
    loading: Boolean,
    examCount: Int,
    finished: Int,
) {
    val t = InkTokens.current
    if (next == null) {
        Text(
            when {
                loading -> "正在读取考试安排…"
                examCount == 0 -> "还没有考试安排（需登录且在校内网）"
                else -> "已考完 $finished 场，本学期没有待考的了"
            },
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = t.inkFaint,
        )
        return
    }
    val label = HomeModel.countdownLabel(next.date, today).orEmpty()
    val big = label.removeSuffix(" 天后").removeSuffix("天")
    val unit = when {
        label.endsWith("天后") -> "天后"
        label == "今天" || label == "明天" -> ""
        else -> label
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                big,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (label == "今天" || label == "明天") t.brick else t.ink,
                fontFamily = NumericFont,
            )
            if (unit.isNotBlank()) {
                Text(unit, fontSize = 9.sp, color = t.inkFaint)
            }
        }
        Spacer(Modifier.width(Space.m))
        Box(Modifier.width(Stroke.hairline).height(32.dp).background(t.rule))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                next.name.ifBlank { "（未命名课程）" },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = t.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOf(next.date, next.startClock.takeIf { it.isNotBlank() }?.let { "$it 开考" }.orEmpty())
                    .filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 10.sp,
                color = t.inkSoft,
                fontFamily = NumericFont,
            )
            val place = listOf(next.campus, next.building, next.room)
                .filter { it.isNotBlank() }.joinToString(" ")
            if (place.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(place, fontSize = 9.5.sp, color = t.inkFaint)
            }
        }
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

/**
 * 组内分隔线。
 *
 * A5：以前是 0.6dp，低于 `HardTokens` 里写的 0.75–1dp 下限（那会让分隔线在部分屏上消失）。
 * 现在收敛到唯一的 hairline 常量，全 App 只有一种细线宽度。
 */
@Composable
private fun Divider() {
    val t = InkTokens.current
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
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

/**
 * 一行入口（`GroupCard` 里用）。
 *
 * A3：`heightIn(min = Space.minTouch)` 保证 ≥48dp 触控（仓库其它页都这么做，这里以前漏了）。
 * A4：箭头朝右 —— 右端就是「进入」的方向。
 * A8：`badge` 只放**真实状态**，其余一律空串（空比噪声诚实）。
 */
@Composable
private fun NavRow(label: String, badge: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.minTouch)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = t.ink)
        Spacer(Modifier.weight(1f))
        if (badge.isNotBlank()) {
            Text(badge, fontSize = 11.sp, color = t.inkFaint, fontFamily = NumericFont)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = t.inkFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}
