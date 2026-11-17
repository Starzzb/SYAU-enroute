package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ripple
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.theme.paperGrain
import cn.dkarbon.enroute.ui.theme.primaryInk
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar
import cn.dkarbon.enroute.ui.icons.AppIcons

// ── 底纹手势驱动（滑动才转）────────────────────────────────
// drive 单位 = 「圈」（1.0 = 超立方体一个完整四维周期，与 MathBackdropMotion 的 phase 同单位）。
// 横向：翻一页（pager 位置变化 1.0）转 1/6 圈 —— 换周的「转换」被看见，又不会转花眼。
// 纵向：滚 1px 转 1/2400 圈，滚满一屏课表（约 400px）约 1/6 圈，量级与切周一致。
private const val TURNS_PER_PAGE = 1f / 6f
private const val TURNS_PER_VERTICAL_PX = 1f / 2400f

/**
 * 课程表：**恒定 6 行 × 7 列的普通网格**（2026-11-13 按主人要求回退到 CET6 同款）。
 *
 * ```
 *        一    二    三    四    五    六    日
 *        10    11    12    13    14    15    16
 *  上午 ─────────────────────────────────────
 *  1-2   ▢    ▢    █高数  ▢    ▢    ▢    ▢
 *  08:00
 *  3-4   ▢    █英语 ▢    ▢    ▢    ▢    ▢
 *  10:00
 *  下午 ─────────────────────────────────────
 *  5-6   …
 * ```
 *
 * ## 与 CET6 的对齐点
 * · 空白行不删：永远渲染全部 6 个大节 + 3 个时段条（上午/下午/晚上）
 * · 整页 Canvas 画格线，格子本身透明；今天列是一条淡洗色竖带
 * · 表头星期 + 日期，今天用主色短线标记（不用满填色块）
 * · 课程卡 = 圆角 4dp 侵蚀卡（[ErodedCard]），吃完留 [EatenResidue]
 * · 小幽灵按真实时间在网格里定位，上课啃、下课漂、没课躺
 *
 * ## 本 App 保留的差异（主人点名「背景还是那个」）
 * · **数学底纹** 是**固定背景层**：铺在整屏最底，改由手势驱动 —— 手指在滑
 *   （横滑切周 / 纵向滚课表）它才跟着「转换」，手停 600ms 就静止；
 *   横滑一页约转 1/6 圈，纵向滚满一屏也约 1/6 圈（票 33 分层 + 滑动才转）
 * · 点卡片 → 详情浮层；长按卡片 → 快捷菜单（CET6 没有，这里保留）
 * · 未排课列表 + 周次校准对话框保留
 * · 配色走 [InkTokens]（农械橘体系），不是 CET6 的砖红 —— 只换结构不换皮
 */
@Composable
fun TimetableScreen(ui: AppState, vm: TimetableViewModel) {
    val ctx = LocalContext.current
    val tt = ui.timetable

    if (tt == null) {
        ImportPanel(ui, vm)
        return
    }

    val cal = remember { TermCalendar(ctx.applicationContext) }
    var calibrating by rememberSaveable { mutableStateOf(!cal.isCalibrated) }

    val minWeek = remember(tt) { tt.courses.flatMap { it.weeks }.minOrNull() ?: 1 }
    val maxWeek = remember(tt) { tt.courses.flatMap { it.weeks }.maxOrNull() ?: 20 }

    val todayWeek = cal.currentWeek() ?: minWeek
    var week by rememberSaveable { mutableIntStateOf(todayWeek) }
    week = week.coerceIn(minWeek, maxOf(minWeek, maxWeek))

    val todayWeekday = cal.todayWeekday()
    val isThisWeek = week == todayWeek

    if (calibrating) {
        CalibrateDialog(
            initial = todayWeek,
            onConfirm = { n ->
                cal.calibrate(n)
                week = n
                calibrating = false
                vm.toast("已校准：本周第 $n 周")
            },
            onDismiss = if (cal.isCalibrated) ({ { calibrating = false } }) else null,
        )
    }

    // 30s ticker：侵蚀进度随时间推进。
    // 调试：adb shell am start -n cn.dkarbon.enroute/.MainActivity --ei debugMin 620
    val debugMin = remember {
        val a = ctx as? android.app.Activity
        a?.intent?.getIntExtra("debugMin", -1) ?: -1
    }
    val nowMin by produceState(initialValue = if (debugMin >= 0) debugMin else nowMinutes()) {
        if (debugMin >= 0) {
            value = debugMin
        } else {
            while (true) {
                value = nowMinutes()
                delay(30_000)
            }
        }
    }

    val summer = remember { SchoolClock.isSummerTime() }

    // ── 自定义课程展开（票 19）──────────────────────────────
    // 规则展开必须知道「第 1 周周一是哪天」：SingleDay 按日期换算周次要用。
    // 未校准（没设开学日）时不展开单日课 —— 宁可不显示，也不猜一个周次出来。
    val week1Monday = remember(week, cal.isCalibrated) {
        if (cal.isCalibrated && week != null) runCatching { cal.mondayOfWeek(1) }.getOrNull() else null
    }
    val termCode = tt.term

    // ── 按周计算抽成纯函数（票 33）──────────────────────────────
    // Pager 的每一页都要算自己的周，而课表数据本来就全在内存 ——
    // "预加载"因此零成本，只是渲染容器换了。
    // 合并规则（票 19）收在 `CustomRules.mergeIntoGrid`：教务先占位、自定义绝不覆盖，
    // 被挤掉的那门进「同格冲突」列表 —— 两门都看得见，且这套规则有单测。
    fun customFor(w: Int): List<Course> =
        if (week1Monday == null) emptyList()
        else ui.customCourses.flatMap { c ->
            cn.dkarbon.enroute.data.custom.CustomRules.toCourses(c, w, week1Monday, termCode)
        }

    fun mergedFor(w: Int) = cn.dkarbon.enroute.data.custom.CustomRules.mergeIntoGrid(
        urp = tt.courses,
        custom = customFor(w),
        week = w,
        bigsOf = { sec -> BigSession.ofCourse(sec).map { it.index } },
    )

    // 排不上时间的课 + 同格冲突被挤掉的课：都不能静默丢
    fun unscheduledFor(w: Int): List<Course> {
        val cellsW = mergedFor(w).first
        val all = tt.courses + customFor(w)
        val noSlot = all.filter {
            it.weeks.isEmpty() || it.weekday !in 1..7 || it.sections.isEmpty()
        }
        val bumped = all.filter { c ->
            c.weekday in 1..7 && w in c.weeks && c.sections.isNotEmpty() &&
                BigSession.ofCourse(c.sections).any { b ->
                    val owner = cellsW[c.weekday to b.index]
                    owner != null && owner !== c && owner.courseNo != c.courseNo
                }
        }
        return (noSlot + bumped + mergedFor(w).second).distinct()
    }

    // 尾注/未排课改为随 pager 页计算（pageCells/pageUnscheduled），
    // 顶层不再保留单周派生值 —— week 仍由 pagerState 回写，供头部与浮层用。

    var detail by remember { mutableStateOf<Course?>(null) }
    var quick by remember { mutableStateOf<Course?>(null) }

    // ── 左右滑动切周（票 33：HorizontalPager）────────────────────
    // 主人真机判票 28 的自研手势"涩"：64dp 阈值 + 25% 阻尼 + 松手 tween，
    // 攒不出系统级手感。照片墙那种"跟手 1:1、前后内容全在"的效果，
    // 现成件就是 HorizontalPager + beyondViewportPageCount —— 相邻周在屏外
    // 就已组合好，滑动时内容跟手平移，零阻尼零等待。
    // 纵向滚动天然分流：Pager 只吃横向 slop，页内 verticalScroll 照常。
    // 声明放在外层：WeekHeader 的按钮、详情浮层的跳周、pager 本体都要用同一份状态。
    val pageCount = maxOf(1, maxWeek - minWeek + 1)
    val pagerState = rememberPagerState(
        initialPage = (week - minWeek).coerceIn(0, pageCount - 1),
    ) { pageCount }
    val pagerScope = rememberCoroutineScope()

    // 周次 ← pager：切周唯一事实源是 pagerState，week 变成派生值
    LaunchedEffect(pagerState, minWeek) {
        snapshotFlow { pagerState.currentPage }.collect { p ->
            week = minWeek + p
        }
    }
    // 按钮/跳周 → pager（animateScrollToPage 自带系统动画）
    val jumpToWeek: (Int) -> Unit = { w ->
        val target = (w.coerceIn(minWeek, maxOf(minWeek, maxWeek)) - minWeek)
            .coerceIn(0, pageCount - 1)
        if (target != pagerState.currentPage) {
            pagerScope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    // ── 底纹手势驱动（滑动才转）──────────────────────────────
    // 两个方向合并成一个 drive：横滑切周（pager 位置）× 1/6 圈/页 + 纵向滚动 px × 1/2400 圈/px。
    // MathBackdropMotion 只吃 drive 的变化量；手指停 600ms 它自己判静止、彻底停帧。
    val backdropDrive = remember { mutableFloatStateOf(0f) }

    // 横向：page 位置（currentPage + 偏移）的变化 → 圈。animateScrollToPage（按钮跳周）也走这里。
    val lastPagePos = remember {
        mutableFloatStateOf(pagerState.currentPage + pagerState.currentPageOffsetFraction)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .collect { pos ->
                val delta = pos - lastPagePos.floatValue
                if (kotlin.math.abs(delta) > 1e-4f) {
                    backdropDrive.floatValue += delta * TURNS_PER_PAGE
                }
                lastPagePos.floatValue = pos
            }
    }

    // 纵向：在底纹宿主层（外层 Box）旁听子级 verticalScroll 的 onPreScroll available.y。
    // 返回 Offset.Zero —— 只旁听、不消费，课表滚动行为一行不改。
    val backdropScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                backdropDrive.floatValue += kotlin.math.abs(available.y) * TURNS_PER_VERTICAL_PX
                return Offset.Zero
            }
        }
    }

    // 编辑版（票 17）：整页铺一层极低对比度纸纹，替代「卡片+描边」的空间感。
    // 老主题不铺 —— 这一段是新分支，HARD / SCHOLAR 渲染不变。
    val form = Form.current
    val tone = InkTokens.current

    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(backdropScrollConnection),
    ) {
        // 票 33（分层）：数学底纹是**固定背景层**，铺满整屏、画在最底，
        // 不参与 Pager 的左右滑动、不参与页内 verticalScroll —— 它改由手势驱动：
        // 手指在滑（横滑切周 / 纵向滚课表）它才跟着转，手停 600ms 就静止。内容层透明地叠在它上面。
        //
        // 可见性修复（2026-11-15 真机）：分层后底纹从「GridArea 网格之上」挪到最底层，
        // 上面压着交错行带（zebra）、今日列 wash、不透明课卡。原先 0.075/0.042 是
        // 「画在网格之上」的取值，被 0.45–0.55 的行带一盖等于消失。
        // 所以这里提亮到 0.17（暗）/ 0.11（亮）—— 比 MathBackdrop.kt 的
        // tesseractAlpha(editorial)（0.115/0.085）更高：那个口径是按「关于页」调的
        // （留白最多、没有行带盖它）；课表页有 zebra 行带 + 实心卡两层遮挡，
        // 底纹必须自己扛住遮挡，故本页显式用 0.17/0.11，不共用该建议值。
        MathBackdropMotion(
            modifier = Modifier.fillMaxSize(),
            color = if (tone.isDark) tone.ink else tone.outlineStrong,
            alpha = if (tone.isDark) 0.17f else 0.11f,
            drive = backdropDrive,
        )
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (form.editorial) {
                        Modifier.paperGrain(color = tone.ink, gap = 26.dp, alpha = 0.030f)
                    } else {
                        Modifier
                    },
                ),
        ) {
            WeekHeader(
                week = week,
                minWeek = minWeek,
                maxWeek = maxWeek,
                isThisWeek = isThisWeek,
                term = tt.term,
                courseCount = tt.courses.map { it.courseNo }.distinct().size,
                onPrev = { jumpToWeek(week - 1) },
                onNext = { jumpToWeek(week + 1) },
                onToday = { jumpToWeek(todayWeek) },
                onRecalibrate = { calibrating = true },
            )

            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val pageWeek = minWeek + page
                val pageCells = mergedFor(pageWeek).first
                val pageUnscheduled = unscheduledFor(pageWeek)
                Column(Modifier.fillMaxSize()) {
                    GridArea(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        week = pageWeek,
                        isThisWeek = pageWeek == todayWeek,
                        todayWeekday = todayWeekday,
                        cells = pageCells,
                        nowMin = nowMin,
                        summer = summer,
                        monday = cal.mondayOfWeek(pageWeek),
                        onCourseClick = { detail = it },
                        onCourseLongClick = { quick = it },
                    )
                    // 未排课 + 尾注：上限高度，自己能滚，不抢网格地盘
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 148.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (pageUnscheduled.isNotEmpty()) {
                            UnscheduledHeader(pageUnscheduled.size)
                            pageUnscheduled.forEach { UnscheduledRow(it, onClick = { detail = it }) }
                        }
                        FootNote(
                            weekTotal = pageCells.size,
                            unscheduled = pageUnscheduled.size,
                            summer = summer,
                            eaten = if (pageWeek == todayWeek) eatenCount(pageCells, todayWeekday, nowMin, summer) else 0,
                            todayTotal = if (pageWeek == todayWeek) pageCells.filterKeys { it.first == todayWeekday }.values.distinct().size else 0,
                            isThisWeek = pageWeek == todayWeek,
                        )
                    }
                }
            }
        }

        detail?.let { c ->
            CourseDetailOverlay(
                course = c,
                week = week,
                todayWeek = todayWeek,
                summer = summer,
                onWeekSelect = jumpToWeek,
                onDismiss = { detail = null },
                // 票 10：浮层里点「查看大纲」→ 打开共用的大纲浮层（渲染在 EnRouteRoot 根节点上）
                onOpenSyllabus = { co -> vm.openSyllabus(co.courseNo, co.name) },
            )
        }

        quick?.let { c ->
            CourseQuickActions(
                course = c,
                week = week,
                todayWeek = todayWeek,
                summer = summer,
                onDetail = { detail = c },
                onDismiss = { quick = null },
            )
        }
    }
}

// ── 网格度量（渲染和小幽灵共用，避免坐标漂移）──────────────

/**
 * 网格唯一度量对象。行高由可用高度反推，但**不小于 64dp**——
 * 再矮卡片就放不下两行课名了；矮屏时整个网格区域纵向滚动兜底。
 *
 * ## ⚠️ 行坐标必须含「本行之前那条时段条」
 * 版面里时段条（上午/下午/晚上）是**渲染在该行前面**的 Column 子项，
 * 所以第 i 行的真实顶部 = rowH*i + bandH×(含本行所属时段在内的条数)。
 * 2026-11-13 真机验收发现的 bug：早期写法只数「之前」的时段条
 * （`bandsBefore` 不含本行），导致 Canvas 画出来的横格线比真实行底高出一个
 * bandH（14dp），卡片和格线错开 —— 现在由 [yOfRow] 统一给正确坐标。
 */
private data class GridMetrics(
    val gridW: Dp,
    val rowH: Dp,
    val axisW: Dp = 40.dp,
    val bandH: Dp = 14.dp,
) {
    val colW: Dp get() = (gridW - axisW) / 7

    fun xOfCol(d: Int): Dp = axisW + colW * (d - 1)

    /** 第 i 行（0..5）顶部 y：含本行所属时段条的占位 */
    fun yOfRow(i: Int): Dp {
        val (rows, bands) = gridRowTop(i)
        return rowH * rows + bandH * bands
    }

    /** 第 i 行**之后**那条横格线的 y（= 行底） */
    fun yOfRowBottom(i: Int): Dp = yOfRow(i) + rowH

    /** 含第 i 行所属时段在内，一共出现过几条时段条（i=0 → 1，即「上午」） */
    fun bandsThrough(i: Int): Int = gridRowTop(i).second

    val totalH: Dp get() = rowH * 6 + bandH * 3
}

// ── 网格本体 ──────────────────────────────────────────────

/**
 * 周网格本体。**几何逻辑（[GridMetrics] / [gridRowTop] / yOfRow）不在本参数里做任何开关**，
 * 只加一个 [readOnly] 给「历年课表」用：
 * - 
eadOnly = true 时不显示**日期的号数**（历史学期我们不知道那一周的真实公历日期，
 *   硬显示一个号数就是编数据）；同时调用方传 isThisWeek = false，
 *   于是今天竖带 / 当前时刻推进条 / 侵蚀进度 / 小幽灵全部自然不出现。
 */
@Composable
internal fun GridArea(
    modifier: Modifier,
    week: Int,
    isThisWeek: Boolean,
    todayWeekday: Int,
    cells: Map<Pair<Int, Int>, Course>,
    nowMin: Int,
    summer: Boolean,
    monday: LocalDate,
    onCourseClick: (Course) -> Unit,
    onCourseLongClick: (Course) -> Unit,
    readOnly: Boolean = false,
) {
    val t = InkTokens.current
    // 票 17：编辑版分支。ed = false 时下面每处 `if (ed) ... else <原值>` 都取原值，
    // 所以 HARD / SCHOLAR 的绘制指令与改造前完全一致。
    val f = Form.current
    val ed = f.editorial
    val accentText = primaryInk()
    val allBigs = BigSession.entries

    // 网格是「固定列宽 + 固定字宽」的表格，字号放到 1.3 会把 8 字课名挤成省略号。
    // 网格内部把 fontScale 夹到 1.15；详情浮层/设置页仍跟随系统字号。
    // Density 实例 remember 住，避免每次重组新建导致下游全部失效（评审 C9）。
    val d = androidx.compose.ui.platform.LocalDensity.current
    val clampedDensity = remember(d.density, d.fontScale) {
        androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(1.15f))
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides clampedDensity,
    ) {
    Column(modifier.fillMaxWidth()) {
        // ── 表头：星期 + 日期 ─────────────────────────
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val metrics = GridMetrics(maxWidth, rowH = 84.dp)
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(metrics.axisW))
                (1..7).forEach { day ->
                    val isToday = day == todayWeekday && isThisWeek
                    Column(
                        Modifier.weight(1f).padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            listOf("", "一", "二", "三", "四", "五", "六", "日")[day],
                            fontSize = if (ed) 13.sp else 12.sp,
                            lineHeight = if (ed) 17.sp else 15.sp,
                            letterSpacing = if (ed) 0.5.sp else 0.sp,
                            fontFamily = if (ed) f.displayFamily else null,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                // 「今天」的日名是小字：用 primaryInk（5.63:1），不用品牌橙原色（4.15:1）
                                isToday -> accentText
                                day >= 6 -> t.amber
                                else -> t.ink
                            },
                        )
                        Spacer(Modifier.height(1.dp))
                        // 只读（历年课表）不显示日期号数：那一周的真实公历日期我们并不知道
                        if (!readOnly) {
                            Text(
                                monday.plusDays((day - 1).toLong()).dayOfMonth.toString(),
                                fontSize = if (ed) 10.sp else 9.5.sp,
                                lineHeight = 12.sp,
                                fontFamily = if (ed) f.numberFamily else NumericFont,
                                color = if (isToday) accentText else t.inkFaint,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // 今天：一根主色短线（不是满填色块）。
                        // 编辑版拉长到 18dp 且压到 1dp —— 更像版面上的「标记线」。
                        Box(
                            Modifier
                                .width(
                                    when {
                                        !isToday -> 0.dp
                                        ed -> 18.dp
                                        else -> 10.dp
                                    },
                                )
                                .height(if (ed) 1.dp else 1.5.dp)
                                .background(if (isToday) t.primary else Color.Transparent),
                        )
                    }
                }
            }
        }
        // 表头下的分隔：账页用**一条** 1.5dp 墨线（旧编辑版是双线；双线在真机上会被读成
        // 「两行格线」，与网格横线的间隔不一致，是错位观感的次要来源之一，已弃用）
        if (ed) {
            Box(Modifier.fillMaxWidth().height(f.ruleHeavy).background(t.ink.copy(alpha = 0.88f)))
        } else {
            HorizontalDivider(color = Tt.gridRule(t.isDark), thickness = Stroke.hairline)
        }

        // ── 网格主体：格线 → 卡片 → 小幽灵 ──
        // 数学底纹已抽到 TimetableScreen 的固定背景层（票 33）：网格这里只画格线与内容。
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            val availH = maxHeight
            // 时段条 14dp + 显式行高：9sp 的「上午/下午」需要约 13dp，
            // 压到 12dp 文字会被裁成一条（CET6 2026-10-27 实测，这里沿用结论）。
            val bandH = 14.dp
            val rowH = ((availH - bandH * 3) / 6).coerceAtLeast(64.dp)
            val metrics = GridMetrics(maxWidth, rowH = rowH, bandH = bandH)

            // 矮屏兜底：网格比可视区高时纵向滚动，而不是把卡片压扁
            val vScroll = rememberScrollState()
    // 票 28：切周后回到顶部 —— 这是**新内容**，不是回退。
    // （与票 26 的"滚动记忆"语义一致：只有同一周内才谈得上记住位置。）
    androidx.compose.runtime.LaunchedEffect(week) { vScroll.scrollTo(0) }
            // 账页竖点线的 PathEffect：只在这里 remember 一次，
            // 不在 Canvas 的 draw 循环里每列 new 一个（draw 阶段零分配是硬要求）
            val dottedRule = remember(ed) {
                if (ed) cn.dkarbon.enroute.ui.theme.ledgerDottedColumns(Color.Black) else null
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll, enabled = metrics.totalH > availH),
            ) {
                // 底层：今天竖带 + 竖格线 + 行分隔
                Canvas(Modifier.width(metrics.gridW).height(metrics.totalH)) {
                    val colW = metrics.colW.toPx()
                    val axisW = metrics.axisW.toPx()
                    // 编辑版：横线用实心版面线（跨到左轴，像报纸表格），竖线更淡更细
                    val ruleColor = if (ed) t.rule else Tt.gridRule(t.isDark)
                    val vRuleColor = if (ed) t.rule.copy(alpha = 0.40f) else ruleColor
                    val hStroke = (if (ed) 1.dp else 0.75.dp).toPx()
                    val vStroke = 0.75.dp.toPx()
                    val washColor = if (ed) t.primary.copy(alpha = 0.045f) else Tt.todayWash(t.isDark)
                    val todayColor = if (ed) t.primary else Tt.todayRule(t.isDark)

                    if (isThisWeek) {
                        val x = axisW + colW * (todayWeekday - 1)
                        drawRect(washColor, Offset(x, 0f), Size(colW, size.height))
                        if (ed) {
                            // 账页：今天只压一条左边界轨（不框两条竖线，避免像「选中卡片」）
                            drawLine(todayColor, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                        } else {
                            drawLine(todayColor, Offset(x, 0f), Offset(x, size.height), 0.75.dp.toPx())
                            drawLine(todayColor, Offset(x + colW, 0f), Offset(x + colW, size.height), 0.75.dp.toPx())
                        }
                    }

                    if (ed) {
                        // ── 账页：**零横线** ──────────────────────────────
                        // ① 交错行带：隔行一层 cardAlt。它承担「横向网格线」的职责，
                        //    但因为是面而不是线，永远不会被读成「偏了几 dp 的格线」。
                        //    可见性修复（2026-11-15）：行带的职责是「提示行存在」，不是
                        //    「铺一层底色」。底纹分层到最底层后，0.55/0.45 会把 0.17/0.11
                        //    的底纹整条盖死；降到 0.22（暗）/0.18（亮）仍能看出条纹，
                        //    同时底纹透得出来。卡片实心度不动（卡片是内容，必须实）。
                        val zebra = t.cardAlt.copy(alpha = if (t.isDark) 0.22f else 0.18f)
                        for (i in 0..5) {
                            if (i % 2 == 1) {
                                drawRect(
                                    color = zebra,
                                    topLeft = Offset(axisW, metrics.yOfRow(i).toPx()),
                                    size = Size(size.width - axisW, metrics.rowH.toPx()),
                                )
                            }
                        }
                        // ② 竖格线：0.6dp 点线（比 HARD 的 0.75dp 实线弱一档，课卡不被切碎）
                        //    PathEffect 在外层 remember 一次，不在 draw 循环里每列新建
                        for (i in 0..7) {
                            val x = axisW + colW * i
                            drawLine(
                                color = t.rule.copy(alpha = 0.80f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 0.6.dp.toPx(),
                                pathEffect = dottedRule,
                            )
                        }
                        // ③ 这里**故意不画任何横线**。
                        //    旧编辑版画了 6 条行线 + 3 条「时段条垂直居中」的 1dp 线，
                        //    后者实测比真实行边界高 24.5px（7.00dp）—— 就是「网格没对齐」的来源。
                    } else {
                        for (i in 0..7) {
                            val x = axisW + colW * i
                            drawLine(vRuleColor, Offset(x, 0f), Offset(x, size.height), vStroke)
                        }
                        // 每个大节底部一条横格线（时段条处不画，条本身就是分隔）
                        for (i in 0..5) {
                            val y = metrics.yOfRowBottom(i).toPx()
                            drawLine(ruleColor, Offset(axisW, y), Offset(size.width, y), hStroke)
                        }
                    }
                }

                Column(Modifier.width(metrics.gridW).height(metrics.totalH)) {
                    var lastPart: DayPart? = null
                    allBigs.forEach { big ->
                        if (big.part != lastPart) {
                            lastPart = big.part
                            if (ed) {
                                // 账页：分节 = **填充色带**（14dp），不是线。
                                // 旧编辑版在这里画了一条垂直居中的 1dp 横线 —— 实测偏 7.00dp，
                                // 是「网格没对齐」的直接来源，已彻底删除。
                                LedgerBand(metrics.axisW, bandH, big.part.label)
                            } else {
                                // 时段标签只占轴宽，不侵入课程列
                                Row(Modifier.fillMaxWidth().height(bandH)) {
                                    Box(
                                        Modifier.width(metrics.axisW).fillMaxHeight().padding(end = 3.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Text(
                                            big.part.label,
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp,
                                            letterSpacing = 0.8.sp,
                                            color = t.inkFaint,
                                        )
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().height(rowH)) {
                            Box(Modifier.width(metrics.axisW).fillMaxHeight()) {
                                if (ed) {
                                    LedgerAxisRow(
                                        sections = big.label.replace(" 节", ""),
                                        startTime = SchoolClock.labelOf(big, summer),
                                    )
                                } else {
                                    AxisRowLabel(
                                        sections = big.label.replace(" 节", ""),
                                        startTime = SchoolClock.labelOf(big, summer),
                                    )
                                }
                            }
                            (1..7).forEach { day ->
                                val course = cells[day to big.index]
                                val isTodayCol = day == todayWeekday && isThisWeek
                                val prog = if (isTodayCol && course != null) {
                                    eatProgress(big, nowMin, summer)
                                } else 0f

                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(
                                            vertical = if (ed) f.cellInset else 1.5.dp,
                                            horizontal = 2.dp,
                                        ),
                                ) {
                                    when {
                                        course == null -> Unit
                                        // 票 17 重做：账页走「浮起便签」渲染（顶帽 + 圆角 + 软阴影）
                                        // 2026-11-17 主人指令：下课也别把卡片换成残影 —— 课名要一直在，
                                        // 而且一直点得开。侵蚀只做「褪色」，不做「删除」。
                                        ed -> LedgerCourseCell(
                                            course = course,
                                            progress = prog,
                                            isNow = isTodayCol && prog > 0f,
                                            onClick = { onCourseClick(course) },
                                            onLongClick = { onCourseLongClick(course) },
                                        )
                                        else -> CourseCard(
                                            course = course,
                                            progress = prog,
                                            isNow = isTodayCol && prog > 0f,
                                            onClick = { onCourseClick(course) },
                                            onLongClick = { onCourseLongClick(course) },
                                        )
                                    }
                                    // 自定义课标记（票 19 硬规矩 1）：右上角一枚「自」。
                                    // 画在单元格这一层（不是画在某套卡片里）→ 硬朗/账页两套都自动带上，
                                    // 也不必去改任何一套卡片的实现。尺寸压到 7sp，不抢课名。
                                    if (course != null && course.isCustom) {
                                        SelfMarkBadge(Modifier.align(Alignment.TopEnd))
                                    }
                                }
                            }
                        }
                    }
                }

                // 小幽灵覆盖层：按真实时间定位（不是匀速巡逻）
                if (isThisWeek) {
                    val eating = cells[todayWeekday to currentBig(nowMin, summer)?.index]
                    val eatKey = eating?.let { it.courseNo.ifBlank { it.name } }
                    TravellingGhost(
                        form = eatKey?.let { GhostForm.of(it) } ?: GhostForm.Round,
                        color = eatKey?.let { Tt.spineOf(Tt.famOf(it), t.isDark) } ?: t.primary,
                        size = 22.dp,
                        mood = ghostMoodAt(nowMin, cells, todayWeekday, summer),
                        modifier = Modifier.offset(
                            x = metrics.xOfCol(todayWeekday) + metrics.colW / 2 - 11.dp,
                            y = walkerY(metrics, nowMin, summer) - 11.dp,
                        ),
                    )
                }

                // ── 当前时刻推进条（2026-11-13 主人点名要的）──────────
                // CET6 里 Tt.nowTick 只定义了颜色、从未落笔，等于没有「现在」的读数。
                // 这里画一条**压在卡片之上**的横线 + 左端方块：正在吃的那门课，
                // 线就停在它被啃到的位置，和侵蚀进度是同一个量（walkerY）。
                if (isThisWeek) {
                    val nowY = walkerY(metrics, nowMin, summer)
                    Canvas(Modifier.width(metrics.gridW).height(metrics.totalH)) {
                        val left = metrics.xOfCol(todayWeekday).toPx()
                        val right = left + metrics.colW.toPx()
                        val y = nowY.toPx().coerceIn(0f, size.height)
                        val bar = 1.5.dp.toPx()
                        // 主线：只压今天那一列，扫一眼就知道「现在进行到哪」
                        drawLine(t.accent, Offset(left, y), Offset(right, y), bar)
                        // 左端方块：给线一个起点，不靠箭头
                        drawRect(
                            t.accent,
                            topLeft = Offset(left, y - 3.dp.toPx()),
                            size = Size(3.dp.toPx(), 6.dp.toPx()),
                        )
                        // 右端小圆点：终端标记
                        drawCircle(t.accent, bar * 1.6f, Offset(right, y))
                    }
                }
            }
        }
    }
    }
}

/** 左轴一行：`1-2` + `08:00` */
@Composable
private fun AxisRowLabel(sections: String, startTime: String) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    Column(
        Modifier.fillMaxSize().padding(top = 3.dp, end = 3.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            sections,
            fontSize = if (ed) 10.5.sp else 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (ed) 0.3.sp else 0.sp,
            fontFamily = if (ed) f.numberFamily else NumericFont,
            color = t.inkSoft,
        )
        Text(
            startTime,
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            fontFamily = if (ed) f.numberFamily else NumericFont,
            color = t.inkFaint,
        )
    }
}

// ── 课程卡 ────────────────────────────────────────────────

/**
 * 课程卡：圆角 4dp 侵蚀卡（CET6 同款结构）。
 * 左侧 2.5dp 满彩竖脊是唯一高彩度面积；课名 / 教室 / 教师三行按行高排。
 * 点 = 详情浮层，长按 = 快捷菜单（本 App 保留的交互）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseCard(
    course: Course,
    progress: Float,
    isNow: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = InkTokens.current
    val f = Form.current
    val key = course.courseNo.ifBlank { course.name }
    val fam = Tt.famOf(key)
    val wash = Tt.washOf(fam, t.isDark)
    val spine = Tt.spineOf(fam, t.isDark)
    val haptic = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        ErodedCard(
            wash = wash,
            spine = spine,
            progress = progress,
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "查看 ${course.name} 详情",
                    onLongClickLabel = "${course.name} 快捷操作",
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = onClick,
                ),
        ) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(2.5.dp).fillMaxHeight().background(spine))
                Column(Modifier.padding(start = 4.dp, top = 2.5.dp, end = 2.dp, bottom = 2.dp)) {
                    Text(
                        course.name,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = t.ink,
                    )
                    Spacer(Modifier.height(1.dp))
                    if (course.roomShort.isNotBlank()) {
                        Text(
                            CourseFacts.shortPlace(course.roomShort),
                            fontSize = 8.sp,
                            lineHeight = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = t.inkSoft,
                        )
                    }
                    if (course.teacher.isNotBlank()) {
                        Text(
                            course.teacher,
                            fontSize = 7.5.sp,
                            lineHeight = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = t.inkFaint,
                        )
                    }
                }
            }
        }

        // 正在上：2dp 主色描边（「当前」档）
        if (isNow) {
            Box(
                Modifier
                    .matchParentSize()
                    .border(Stroke.emphasis, t.primary.copy(alpha = 0.55f), f.shapes.courseCard),
            )
        }
    }
}

// ── 小幽灵定位 ────────────────────────────────────────────

/** 现在落在哪个大节（不在任何大节里返回 null） */
private fun currentBig(nowMin: Int, summer: Boolean): BigSession? =
    BigSession.entries.firstOrNull {
        val (s, e) = SchoolClock.rangeOf(it, summer)
        nowMin in s..e
    }

/** 小幽灵 y：上课 = 行内按真实进度；课间 = 下一行顶部；放学 = 网格底 */
private fun walkerY(m: GridMetrics, nowMin: Int, summer: Boolean): Dp {
    val cur = currentBig(nowMin, summer)
    if (cur != null) {
        val (s, e) = SchoolClock.rangeOf(cur, summer)
        val span = (e - s).toFloat()
        val ratio = if (span <= 0f) 0f else (nowMin - s) / span
        return m.yOfRow(cur.index - 1) + m.rowH * ratio
    }
    val next = BigSession.entries.firstOrNull {
        nowMin < SchoolClock.rangeOf(it, summer).first
    }
    if (next != null) return m.yOfRow(next.index - 1)
    return m.totalH
}

/** 小幽灵情绪：有课在啃 / 空堂躺着 / 快上课了溜达 / 夜里犯困 */
private fun ghostMoodAt(
    nowMin: Int,
    cells: Map<Pair<Int, Int>, Course>,
    today: Int,
    summer: Boolean,
): GhostMood {
    val cur = currentBig(nowMin, summer)
    if (cur != null) {
        return if (cells[today to cur.index] != null) GhostMood.Feed else GhostMood.Doze
    }
    val next = BigSession.entries.firstOrNull { nowMin < SchoolClock.rangeOf(it, summer).first }
    if (next != null) {
        val gap = SchoolClock.rangeOf(next, summer).first - nowMin
        return if (gap > 30) GhostMood.Doze else GhostMood.Wander
    }
    return GhostMood.Doze
}

/** 今日已吃完几门（底部统计用；summer 必须传，漏了会把夏季没上完的课误判成已消化） */
private fun eatenCount(
    cells: Map<Pair<Int, Int>, Course>,
    todayWeekday: Int,
    nowMin: Int,
    summer: Boolean,
): Int = cells.filterKeys { it.first == todayWeekday }.values.distinct().count { c ->
    BigSession.ofCourse(c.sections).all { eatProgress(it, nowMin, summer) >= 0.999f }
}

private fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

// ── 未排课 / 尾注 ─────────────────────────────────────────

@Composable
private fun UnscheduledHeader(n: Int) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "未排课 / 冲突",
            fontSize = if (ed) 13.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (ed) f.titleTracking else 0.sp,
            fontFamily = if (ed) f.displayFamily else null,
            color = t.inkSoft,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$n 门",
            fontSize = 9.5.sp,
            letterSpacing = if (ed) 0.8.sp else 0.sp,
            fontFamily = if (ed) f.numberFamily else NumericFont,
            color = t.inkFaint,
        )
        Spacer(Modifier.width(8.dp))
        // 编辑版：小标题右侧是**实心重线**（2dp 墨），替掉 1dp 淡线
        Box(
            Modifier.weight(1f).height(if (ed) 2.dp else Stroke.hairline)
                .background(if (ed) t.ink.copy(alpha = 0.72f) else t.rule),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnscheduledRow(c: Course, onClick: () -> Unit) {
    val t = InkTokens.current
    // 票 17 重做：账页的未排课行是「便签条」（圆角 + 描边，无阴影），不是无容器的版面线
    if (Form.current.editorial) {
        LedgerUnscheduledRow(c, onClick)
        return
    }
    val fam = Tt.famOf(c.courseNo.ifBlank { c.name })
    val spine = Tt.spineOf(fam, t.isDark)
    val food = Food.of(c.courseNo.ifBlank { c.name })

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .combinedClickable(onClickLabel = "查看 ${c.name} 详情", onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FoodIcon(food = food, modifier = Modifier.size(20.dp), eaten = 0f)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = t.ink,
            )
            Text(
                buildString {
                    append(c.weeksLabel)
                    if (c.teacher.isNotBlank()) append(" · ").append(c.teacher)
                },
                fontSize = 9.5.sp,
                color = t.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(4.dp).background(spine))
    }
}

@Composable
private fun FootNote(
    weekTotal: Int,
    unscheduled: Int,
    summer: Boolean,
    eaten: Int,
    todayTotal: Int,
    isThisWeek: Boolean,
) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 14.dp)) {
        if (weekTotal == 0) {
            Text(
                "这一周没有课。",
                fontSize = if (ed) 12.sp else 11.sp,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(3.dp))
        }
        if (isThisWeek && todayTotal > 0) {
            Text(
                "今日已消化 $eaten 节 · 还剩 ${(todayTotal - eaten).coerceAtLeast(0)} 节",
                fontSize = 10.5.sp,
                letterSpacing = if (ed) 0.4.sp else 0.sp,
                fontFamily = if (ed) f.numberFamily else NumericFont,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(
            buildString {
                append("本周 $weekTotal 格")
                if (unscheduled > 0) append(" · 另有 $unscheduled 门未排/冲突")
                append(" · ").append(SchoolClock.hintOf(summer))
            },
            fontSize = 9.5.sp,
            lineHeight = if (ed) 15.sp else 14.sp,
            letterSpacing = if (ed) 0.3.sp else 0.sp,
            color = t.inkFaint,
        )
    }
}

// ── 周次头 ────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekHeader(
    week: Int,
    minWeek: Int,
    maxWeek: Int,
    isThisWeek: Boolean,
    term: String,
    courseCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onRecalibrate: () -> Unit,
) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    Column(Modifier.fillMaxWidth()) {
        // ⚠️ 表头布局**逐 dp 冻结**：三枚图标按钮各占 32dp，标题列 weight(1f) 吃掉剩余。
        // 48dp 触区靠 [HeaderTapPad] 叠在 Row **之上**（不参与布局）实现，
        // 所以 HARD 与账页的表头元素一个像素都不会移动（2026-11-14 主人要求）。
        Box(Modifier.fillMaxWidth().height(if (ed) 69.dp else 72.dp)) {
        Row(
            Modifier
                .fillMaxSize()
                // 编辑版把标题行压到 69dp，余下的 3dp 给「报头双线」——
                // 总高仍是 72dp，网格可用高度不变（几何一行没动）。
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                tint = if (week > minWeek) t.inkSoft else t.outline,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).combinedClickable(onClick = onRecalibrate, onLongClick = onRecalibrate),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "第 $week 周",
                        // 账页：页级标题走到 26sp（HARD 22sp / 旧编辑版 24sp），靠**跨级**而不是靠颜色分层
                        fontSize = if (ed) 26.sp else 22.sp,
                        lineHeight = if (ed) 32.sp else 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = if (ed) f.titleTracking else 0.sp,
                        fontFamily = if (ed) f.displayFamily else NumericFont,
                        color = t.ink,
                    )
                    if (isThisWeek) {
                        Spacer(Modifier.width(7.dp))
                        if (ed) {
                            // 账页：「本周」是**胶囊**（淡底 + 细边 + 大字距）
                            LedgerWeekStamp(isThisWeek = true)
                        } else {
                            Box(Modifier.background(t.primary).padding(horizontal = 5.dp, vertical = 1.dp)) {
                                Text(
                                    "本周",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = if (t.isDark) androidx.compose.ui.graphics.Color(0xFF100E0B) else Color.White,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    (term.ifBlank { "本学期" }) + " · $courseCount 门",
                    fontSize = 9.5.sp,
                    letterSpacing = if (ed) 1.5.sp else 0.sp,
                    color = t.inkFaint,
                )
            }
            HeaderIcon(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                tint = if (week < maxWeek) t.inkSoft else t.outline,
            )
            HeaderIcon(icon = AppIcons.Today, tint = t.primary)
        }

            // ── 48dp 触区（透明，不参与布局）────────────────────
            // 位置常量由「Row 左右各 6dp 内边距 + 图标按钮 32dp」推出，与上面 Row 严格对齐：
            //   左箭头中心 = 6+16 = 22dp  → 相对 CenterStart 偏 -2dp（48/2=24）
            //   右箭头中心 = 右-6-32-16    → 相对 CenterEnd 偏 -30dp
            //   日历  中心 = 右-6-16       → 相对 CenterEnd 偏 +2dp
            HeaderTapPad(
                modifier = Modifier.align(Alignment.CenterStart).offset(x = (-2).dp),
                label = "上一周",
                enabled = week > minWeek,
                onClick = onPrev,
            )
            HeaderTapPad(
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-30).dp),
                label = "下一周",
                enabled = week < maxWeek,
                onClick = onNext,
            )
            HeaderTapPad(
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = 2.dp),
                label = "回到本周",
                enabled = true,
                onClick = onToday,
            )
        }
        // 报头双线：粗 1.5dp + 留白 1dp + 细 0.75dp
        if (ed) {
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(t.ink))
            Box(Modifier.fillMaxWidth().height(1.dp))
            Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.ink))
        }
    }
}

/**
 * 表头图标：**只有画，没有点击**（点击与语义由盖在上面的 [HeaderTapPad] 负责）。
 * 占位 32dp，与改造前的 `IconButton(size = 32.dp)` 完全一致 —— 表头不会位移。
 */
@Composable
private fun HeaderIcon(
    icon: ImageVector,
    tint: Color,
) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(16.dp), tint = tint)
    }
}

/**
 * 48dp 触区补丁：透明、圆形涟漪、带无障碍标签。
 *
 * 为什么单独一层：需求是「触区 48dp，但表头元素一个都不许移动」。
 * 把 32dp 的按钮直接放大到 48dp 会让标题列少 48dp、图标各移 8dp；
 * 而 `requiredSize` 撑破父容器又会和标题列抢 8dp 的点击。
 * 唯一两全的做法是**让补丁叠在 Row 之上**（Box 里后声明 = 画在最上层 = 命中优先），
 * 布局尺寸仍由下面那层 32dp 的 Row 决定。
 */
@Composable
private fun HeaderTapPad(
    modifier: Modifier,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(48.dp)
            .clip(Form.current.shapes.circle)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label; role = Role.Button },
    )
}

// ── 校准 / 导入 ───────────────────────────────────────────

@Composable
private fun CalibrateDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: (() -> Unit)?,
) {
    var n by remember { mutableIntStateOf(initial) }

    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("校准学期周次", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "教务没给开学日期，告诉我一次现在是第几周，之后自动推算。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = { if (n > 1) n-- }, modifier = Modifier.size(40.dp)) {
                        Text("−", fontSize = 16.sp)
                    }
                    Text(
                        "第 $n 周",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                    OutlinedButton(onClick = { if (n < 30) n++ }, modifier = Modifier.size(40.dp)) {
                        Text("+", fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(n) }) { Text("确定") } },
        dismissButton = onDismiss?.let { cb -> { TextButton(onClick = cb) { Text("取消") } } },
    )
}

@Composable
private fun ImportPanel(ui: AppState, vm: TimetableViewModel) {
    var mode by rememberSaveable { mutableStateOf("login") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    val t = InkTokens.current

    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(26.dp))
            Text("还没有课程表", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportTab("登录抓取", mode == "login") { mode = "login" }
                ImportTab("导入 JSON", mode == "file") { mode = "file" }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (mode == "login") {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "需要连着校园网（关掉代理 / VPN）。密码只用一次，不保存。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = t.inkFaint,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("学号", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("密码", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                    )
                    Spacer(Modifier.height(14.dp))
                    if (ui.timetableLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(ui.timetableMsg.ifBlank { "登录中…" }, fontSize = 12.5.sp, color = t.inkSoft)
                        }
                    } else {
                        Button(
                            onClick = { vm.login(user.trim(), pass) },
                            enabled = user.isNotBlank() && pass.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                        ) { Text("登录并抓取", fontSize = 13.5.sp) }
                    }
                    if (ui.timetableMsg.isNotBlank() && !ui.timetableLoading) {
                        Spacer(Modifier.height(8.dp))
                        Text(ui.timetableMsg, fontSize = 12.sp, color = t.brick)
                    }
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { vm.loadTimetableFromDevice() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                    ) { Text("从 /data/local/tmp 查找", fontSize = 13.sp) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.pickTimetableFile() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                    ) { Text("用文件选择器导入", fontSize = 12.5.sp) }

                    if (ui.timetableMsg.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            ui.timetableMsg,
                            fontSize = 12.sp,
                            color = if (ui.timetableMsg.startsWith("✅")) t.olive else t.brick,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImportTab(label: String, on: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .height(34.dp)
            .background(if (on) t.primary else Color.Transparent)
            .border(Stroke.hairline, if (on) t.primary else t.outline)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) (if (t.isDark) androidx.compose.ui.graphics.Color(0xFF100E0B) else Color.White) else t.inkSoft,
        )
    }
}

/**
 * 自定义课的「自」角标（票 19）。
 *
 * 为什么做成角标而不是细杠/换色：细杠和配色都被**科目色系**占用了（`Tt.famOf`），
 * 再借一层视觉语义会打架；「自」一个字最直白 —— 用户要能一眼分清哪门是自己加的。
 * 尺寸 7sp + 半透明底，只在右上角占一小块，不遮课名。
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SelfMarkBadge(modifier: Modifier = Modifier) {
    val t = InkTokens.current
    Box(
        modifier
            .padding(top = 1.dp, end = 1.dp)
            .background(t.card.copy(alpha = 0.82f))
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Text("\u81ea", fontSize = 7.sp, lineHeight = 9.sp, color = t.inkSoft, fontWeight = FontWeight.Bold)
    }
}