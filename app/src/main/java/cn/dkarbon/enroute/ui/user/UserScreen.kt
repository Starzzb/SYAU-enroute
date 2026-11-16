package cn.dkarbon.enroute.ui.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.academic.ExamScreen
import cn.dkarbon.enroute.ui.academic.ScoreScreen
import cn.dkarbon.enroute.ui.debug.DebugScreen
import cn.dkarbon.enroute.ui.home.HomeFootNote
import cn.dkarbon.enroute.ui.home.HomeModel
import cn.dkarbon.enroute.ui.ledger.LedgerHeader
import cn.dkarbon.enroute.ui.settings.SettingsScreen
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.timetable.TermCalendar
import cn.dkarbon.enroute.ui.widget.WoodenFish

/** 用户页下的子页面（用一个小栈做导航，系统返回键直接可用） */
private enum class UserPage(val title: String) {
    Root("我的"),
    Exam("考试安排"),
    Score("成绩查询"),
    Curriculum("培养方案"),
    Syllabus("课程大纲"),
    History("历年课表"),
    CampusMap("校园地图"),
    CustomCourses("自定义课程"),
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
fun UserScreen(
    ui: AppState,
    vm: TimetableViewModel,
    openSettingsForUpdate: Boolean = false,
    onOpenSettingsForUpdateConsumed: () -> Unit = {},
) {
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

    // 弹窗「立即更新」→ 切到设置页（文档 §3.4 方案 A）。只负责压栈；
    // 自动下载由 UpdateSection 消费 UpdateCenter.downloadRequest。
    LaunchedEffect(openSettingsForUpdate) {
        if (openSettingsForUpdate) {
            if (stack.last() != UserPage.Settings) stack.add(UserPage.Settings)
            onOpenSettingsForUpdateConsumed()
        }
    }

    // 票 26：子页栈本身已经 saveable（A6），但**页内滚动位置**要靠 SaveableStateHolder 才留得住 ——
    // 设置 → 关于 → 返回，设置页停在原处；考试 → 设置 → 返回，时间轴停在原处。
    val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        if (current != UserPage.Root) {
            SubHeader(current.title, onBack = { stack.removeAt(stack.lastIndex) })
        }
        Box(Modifier.weight(1f)) {
            pageStates.SaveableStateProvider("user:${current.name}") {
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
                    onOpenCustom = { vm.openCustomCourses(); stack.add(UserPage.CustomCourses) },
                    onOpenRooms = { stack.add(UserPage.Rooms) },
                    onOpenTeacher = { stack.add(UserPage.Teacher) },
                    onOpenAbout = { stack.add(UserPage.About) },
                    onOpenLogin = { stack.add(UserPage.Login) },
                )
                UserPage.Exam -> ExamScreen(ui = ui, vm = vm)
                UserPage.Score -> ScoreScreen(ui = ui, vm = vm)
                UserPage.Curriculum -> cn.dkarbon.enroute.ui.curriculum.CurriculumScreen(ui = ui, vm = vm)
                UserPage.Syllabus -> cn.dkarbon.enroute.ui.curriculum.SyllabusSearchScreen(ui = ui, vm = vm)
                UserPage.History -> cn.dkarbon.enroute.ui.timetable.HistoryTimetableScreen(ui = ui, vm = vm)
                UserPage.CampusMap -> cn.dkarbon.enroute.ui.map.CampusMapScreen(ui = ui, vm = vm)
                UserPage.CustomCourses -> cn.dkarbon.enroute.ui.custom.CustomCourseScreen(ui = ui, vm = vm)
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
                UserPage.Debug -> DebugScreen(
                    onSelftest = { vm.runCryptoSelfTest() },
                    ui = ui,
                    onClearHistory = { vm.clearDebugHistory() },
                    exportAllName = { vm.exportAllDebugFileName() },
                    exportAllText = { vm.exportAllDebugText() },
                )
                UserPage.Login -> LoginScreen(
                    vm = vm,
                    sessionState = ui.sessionState,
                    onDone = { stack.removeAt(stack.lastIndex) },
                )
                }
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
    onOpenCustom: () -> Unit,
    onOpenRooms: () -> Unit,
    onOpenTeacher: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val cal = remember { TermCalendar(ctx.applicationContext) }
    val week = remember(ui.timetable) { cal.currentWeek() }
    var headerMenu by remember { mutableStateOf(false) }
    // 身份卡展开态（票 28）：rememberSaveable 记住 —— 切到资讯再回来，展开/收起不丢
    var identityExpanded by rememberSaveable { mutableStateOf(false) }

    // 进「我的」就顺手刷新主页三件套（有**有效**会话且上次抓空了才刷，避免每次切页都打网络）。
    // 键是 sessionState 而不是布尔 loggedIn：过期→有效也会重跑一次（续期后立刻有数据）。
    LaunchedEffect(ui.sessionState) {
        if (ui.sessionState == SessionState.Active && ui.credit.isEmpty && !ui.homeLoading) vm.refreshHome()
    }

    // 页眉状态：登录态 + 读取中（原「教务」组头 premise 上移到常驻页眉）
    val headerStatus = MyPageModel.sessionPremiseLabel(ui.sessionState) +
        if (ui.homeLoading) " · 读取中" else ""

    // 账目行的展示派生（纯函数，不建状态）。空值 → 「暂缺」，绝不填 0。
    val name = ui.profile.name.ifBlank { ui.userName }
        .ifBlank { SessionText.fallbackName(ui.sessionState) }
    val identitySub = SessionText.identitySubtitle(
        state = ui.sessionState,
        profileSubtitle = HomeModel.profileSubtitle(ui.profile),
        loading = ui.homeLoading,
        checkMsg = ui.sessionCheckMsg,
    )
    val gpa = ui.scoreStats.weightedGpa?.let { String.format(java.util.Locale.US, "%.2f", it) }.orEmpty()
    val creditHasData = ui.credit.earnedXf > 0.0 || ui.credit.requiredXf > 0.0
    val freeRoomsValue = MyPageModel.freeRoomsTile(ui.roomsSession, ui.freeRooms.roomCount)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LedgerHeader(
                title = "我的",
                status = headerStatus,
                // 搜索是批 3：本页还没有搜索框，页眉不挂「查」（不留死按钮）
                onSearch = null,
                // 原顶条的「刷新 / 设置」收进 ⋯ 菜单，入口不丢
                onMore = { headerMenu = true },
            )
            // 页顶一行：绩点/学分/排名（每日档）是几时取的。命中缓存直读时不打网络，必须标出年龄。
            val dailyStamp = remember(ui.homeDailyFetchedAt) {
                cn.dkarbon.enroute.data.CachePolicy.fetchStamp(System.currentTimeMillis(), ui.homeDailyFetchedAt)
            }
            if (dailyStamp.isNotBlank()) {
                Text(
                    dailyStamp,
                    fontSize = 11.5.sp,
                    color = t.inkFaint,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = 2.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.l),
            ) {
                Spacer(Modifier.height(4.dp))

                // ── 可展开身份卡（2026-11-15 主人定：身份/学籍 + 学业合并成一张卡）──
                // 为什么合并：首屏第一块只留一张「学生证」——身份是常驻第一层，
                // 成绩（绩点/学分/排名/培养方案）是按需看的第二层，收进卡内展开；
                // 未登录时整卡就是登录门禁（点此登录教务，不展开），B3 入口不丢。
                UserIdentityCard(
                    state = ui.sessionState,
                    name = name,
                    studentNo = ui.profile.studentNo.ifBlank {
                        MyPageModel.sessionPremiseLabel(ui.sessionState)
                    },
                    subtitle = identitySub,
                    cells = listOf(
                        UserStatCell(
                            label = "加权绩点",
                            value = gpa,
                            missing = ui.scoreStats.weightedGpa == null,
                            onClick = onOpenScore,
                        ),
                        UserStatCell(
                            label = "已修学分",
                            value = if (creditHasData) HomeModel.trimNum(ui.credit.earnedXf) else "",
                            missing = !creditHasData,
                            onClick = onOpenCurriculum,
                        ),
                        // 排名：URP 接口没给这套算法（见 ScoreScreen 说明），如实显示暂缺
                        UserStatCell(
                            label = "排名",
                            value = "",
                            missing = true,
                            onClick = onOpenScore,
                        ),
                        // 培养方案是纯跳转入口，格内放「查看」作值；点击与原来那行一致
                        UserStatCell(
                            label = "培养方案",
                            value = "查看",
                            missing = false,
                            onClick = onOpenCurriculum,
                        ),
                    ),
                    expanded = identityExpanded,
                    onToggle = { identityExpanded = !identityExpanded },
                    onLogin = onOpenLogin,
                )

                // ─────────────────────────────────────────────────────────────
                // 功能入口从这里进分组 box（主人 2026-11-15：「教务的按钮放入一个 box 内，其余的也是，确定归属」）。
                // 注意：这与「资讯流无容器」不冲突 —— 资讯是阅读流（不放盒子），
                // 功能入口是可点的集合（放盒子更清楚）。下一个代理不要再给资讯页加盒子。
                // 首屏第一块的身份/学籍也已收进可展开身份卡（见 UserIdentityCard），与下面三个 box 同族。
                // ─────────────────────────────────────────────────────────────
                UserGroupBox(
                    title = "教务",
                    first = false,
                    entries = listOf(
                        UserLedgerEntry(
                            subject = "考试安排",
                            badge = MyPageModel.examBadge(ui.exams.size, ui.examsLoading),
                            chevron = true,
                            onClick = onOpenExam,
                        ),
                        UserLedgerEntry(
                            subject = "成绩查询",
                            badge = MyPageModel.scoreBadge(ui.scores.size, ui.scoresLoading),
                            chevron = true,
                            onClick = onOpenScore,
                        ),
                        UserLedgerEntry(subject = "查教师", chevron = true, onClick = onOpenTeacher),
                        UserLedgerEntry(
                            subject = "课程大纲",
                            badge = MyPageModel.syllabusBadge(ui.syllabusIndex.size),
                            chevron = true,
                            onClick = onOpenSyllabus,
                        ),
                        UserLedgerEntry(
                            subject = "历年课表",
                            badge = MyPageModel.historyBadge(
                                ui.historyPlanCode.takeIf { ui.historyTimetable != null }
                                    ?.let { HomeModel.termLabel(it) }.orEmpty(),
                            ),
                            chevron = true,
                            onClick = onOpenHistory,
                        ),
                        UserLedgerEntry(
                            subject = "空闲教室",
                            badge = if (freeRoomsValue != MyPageModel.UNKNOWN) freeRoomsValue else "",
                            chevron = true,
                            onClick = onOpenRooms,
                        ),
                    ),
                )

                // 缓存时间/主页提示（数据诚实：显示的是旧数据就必须标出年龄）
                if (ui.cacheUpdatedLabel.isNotBlank() || ui.homeMsg.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    if (ui.cacheUpdatedLabel.isNotBlank()) HomeFootNote(ui.cacheUpdatedLabel)
                    if (ui.homeMsg.isNotBlank()) HomeFootNote(ui.homeMsg)
                }

                // ── 校园（1 行）：离线示意图，不申请定位 ──
                UserGroupBox(
                    title = "校园",
                    first = false,
                    entries = listOf(
                        UserLedgerEntry(subject = "校园地图", chevron = true, onClick = onOpenMap),
                    ),
                )

                // ── 本机（4 行）：自定义课程 / 周次校准 / 设置 / 关于 —— 设置与关于从隐藏入口浮到明处 ──
                UserGroupBox(
                    title = "本机",
                    first = false,
                    entries = listOf(
                        UserLedgerEntry(
                            subject = "自定义课程",
                            badge = if (ui.customCourses.isEmpty()) "" else "${ui.customCourses.size} 门",
                            chevron = true,
                            onClick = onOpenCustom,
                        ),
                        UserLedgerEntry(
                            subject = "周次校准",
                            value = MyPageModel.weekLabel(week),
                        ),
                        UserLedgerEntry(subject = "设置", chevron = true, onClick = onOpenSettings),
                        UserLedgerEntry(subject = "关于", chevron = true, onClick = onOpenAbout),
                    ),
                )

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
        if (headerMenu) {
            UserHeaderMenu(
                refreshEnabled = ui.sessionState == SessionState.Active && !ui.homeLoading,
                onRefresh = { vm.refreshHome(force = true) },
                onSettings = onOpenSettings,
                onDismiss = { headerMenu = false },
            )
        }
    }
}

/**
 * 页头 ⋯ 菜单（账页 v3：原顶条「刷新 / 设置」收进来，入口不丢）。
 * 静态底部菜单，不用动画也不引 Material Sheet —— 纸面 + 遮罩 + 三条横排动作即可。
 */
@Composable
private fun UserHeaderMenu(
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = InkTokens.current
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭",
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(t.paper)
                .border(Stroke.strong, t.outline),
        ) {
            UserMenuRow(
                index = "01",
                title = "刷新",
                sub = "只刷新身份 + 学分/绩点这一组（票 24）",
                enabled = refreshEnabled,
                onClick = { onRefresh(); onDismiss() },
            )
            UserMenuRow(
                index = "02",
                title = "设置",
                sub = "主题、字号、教务与数据",
                onClick = { onSettings(); onDismiss() },
            )
            Box(
                Modifier.fillMaxWidth().height(Space.minTouch).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", fontSize = 12.5.sp, color = t.inkFaint)
            }
        }
    }
}

/** 页头菜单里的一条动作：序号 + 动作名 + 一句副文案（≥48dp 触区） */
@Composable
private fun UserMenuRow(
    index: String,
    title: String,
    sub: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index, fontSize = 9.sp, color = t.inkFaint)
            Spacer(Modifier.width(Space.m))
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) t.ink else t.inkFaint,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            sub,
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            color = t.inkFaint,
            maxLines = 1,
            modifier = Modifier.padding(start = 27.dp),
        )
    }
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
}
