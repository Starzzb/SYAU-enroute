package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Calendar

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
 * · **数学底纹 [MathBackdrop]** 继续压在网格下面当纸纹，随时间/周次转动
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

    // 一格一门课：同一格有多门（冲突）时取第一门，其余进未排课列表提示
    val cells = remember(tt, week) {
        val m = mutableMapOf<Pair<Int, Int>, Course>()
        for (c in tt.courses) {
            if (c.weekday !in 1..7 || week !in c.weeks) continue
            for (b in BigSession.ofCourse(c.sections)) m.putIfAbsent(c.weekday to b.index, c)
        }
        m
    }

    // 排不上时间的课 + 同格冲突被挤掉的课：都不能静默丢
    val unscheduled = remember(tt, week, cells) {
        val noSlot = tt.courses.filter {
            it.weeks.isEmpty() || it.weekday !in 1..7 || it.sections.isEmpty()
        }
        val bumped = tt.courses.filter { c ->
            c.weekday in 1..7 && week in c.weeks && c.sections.isNotEmpty() &&
                BigSession.ofCourse(c.sections).any { b ->
                    val owner = cells[c.weekday to b.index]
                    owner != null && owner !== c && owner.courseNo != c.courseNo
                }
        }
        (noSlot + bumped).distinct()
    }

    var detail by remember { mutableStateOf<Course?>(null) }
    var quick by remember { mutableStateOf<Course?>(null) }

    val jumpToWeek: (Int) -> Unit = { w ->
        week = w.coerceIn(minWeek, maxOf(minWeek, maxWeek))
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            WeekHeader(
                week = week,
                minWeek = minWeek,
                maxWeek = maxWeek,
                isThisWeek = isThisWeek,
                term = tt.term,
                courseCount = tt.courses.map { it.courseNo }.distinct().size,
                onPrev = { if (week > minWeek) week-- },
                onNext = { if (week < maxWeek) week++ },
                onToday = { week = todayWeek },
                onRecalibrate = { calibrating = true },
            )

            GridArea(
                modifier = Modifier.weight(1f),
                week = week,
                isThisWeek = isThisWeek,
                todayWeekday = todayWeekday,
                cells = cells,
                nowMin = nowMin,
                summer = summer,
                monday = cal.mondayOfWeek(week),
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
                if (unscheduled.isNotEmpty()) {
                    UnscheduledHeader(unscheduled.size)
                    unscheduled.forEach { UnscheduledRow(it, onClick = { detail = it }) }
                }
                FootNote(
                    weekTotal = cells.size,
                    unscheduled = unscheduled.size,
                    summer = summer,
                    eaten = if (isThisWeek) eatenCount(cells, todayWeekday, nowMin, summer) else 0,
                    todayTotal = if (isThisWeek) cells.filterKeys { it.first == todayWeekday }.values.distinct().size else 0,
                    isThisWeek = isThisWeek,
                )
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
 * - eadOnly = true 时不显示**日期的号数**（历史学期我们不知道那一周的真实公历日期，
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
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isToday -> t.primary
                                day >= 6 -> t.amber
                                else -> t.ink
                            },
                        )
                        Spacer(Modifier.height(1.dp))
                        // 只读（历年课表）不显示日期号数：那一周的真实公历日期我们并不知道
                        if (!readOnly) {
                            Text(
                                monday.plusDays((day - 1).toLong()).dayOfMonth.toString(),
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp,
                                fontFamily = NumericFont,
                                color = if (isToday) t.primary else t.inkFaint,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // 今天：一根主色短线（不是满填色块）
                        Box(
                            Modifier
                                .width(if (isToday) 10.dp else 0.dp)
                                .height(1.5.dp)
                                .background(if (isToday) t.primary else Color.Transparent),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = Tt.gridRule(t.isDark), thickness = Stroke.hairline)

        // ── 网格主体：数学底纹 → 格线 → 卡片 → 小幽灵 ──
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
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll, enabled = metrics.totalH > availH),
            ) {
                // 数学底纹：四维超立方体投影，压在格线下面只当纸纹（主人点名保留）
                MathBackdrop(
                    modifier = Modifier.width(metrics.gridW).height(metrics.totalH),
                    color = if (t.isDark) t.ink else t.outlineStrong,
                    alpha = if (t.isDark) 0.075f else 0.042f,
                    turns = nowMin / 1440f + (week - 1) * 0.11f,
                )

                // 底层：今天竖带 + 竖格线 + 行分隔
                Canvas(Modifier.width(metrics.gridW).height(metrics.totalH)) {
                    val colW = metrics.colW.toPx()
                    val axisW = metrics.axisW.toPx()
                    val ruleColor = Tt.gridRule(t.isDark)
                    val washColor = Tt.todayWash(t.isDark)
                    val todayColor = Tt.todayRule(t.isDark)

                    if (isThisWeek) {
                        val x = axisW + colW * (todayWeekday - 1)
                        drawRect(washColor, Offset(x, 0f), Size(colW, size.height))
                        drawLine(todayColor, Offset(x, 0f), Offset(x, size.height), 0.75.dp.toPx())
                        drawLine(todayColor, Offset(x + colW, 0f), Offset(x + colW, size.height), 0.75.dp.toPx())
                    }

                    for (i in 0..7) {
                        val x = axisW + colW * i
                        drawLine(ruleColor, Offset(x, 0f), Offset(x, size.height), 0.75.dp.toPx())
                    }
                    // 每个大节底部一条横格线（时段条处不画，条本身就是分隔）
                    for (i in 0..5) {
                        val y = metrics.yOfRowBottom(i).toPx()
                        drawLine(ruleColor, Offset(axisW, y), Offset(size.width, y), 0.75.dp.toPx())
                    }
                }

                Column(Modifier.width(metrics.gridW).height(metrics.totalH)) {
                    var lastPart: DayPart? = null
                    allBigs.forEach { big ->
                        if (big.part != lastPart) {
                            lastPart = big.part
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
                        Row(Modifier.fillMaxWidth().height(rowH)) {
                            Box(Modifier.width(metrics.axisW).fillMaxHeight()) {
                                AxisRowLabel(
                                    sections = big.label.replace(" 节", ""),
                                    startTime = SchoolClock.labelOf(big, summer),
                                )
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
                                        .padding(vertical = 1.5.dp, horizontal = 2.dp),
                                ) {
                                    when {
                                        course == null -> Unit
                                        prog >= 0.999f -> EatenResidue()
                                        else -> CourseCard(
                                            course = course,
                                            progress = prog,
                                            isNow = isTodayCol && prog > 0f,
                                            onClick = { onCourseClick(course) },
                                            onLongClick = { onCourseLongClick(course) },
                                        )
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
    Column(
        Modifier.fillMaxSize().padding(top = 3.dp, end = 3.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            sections,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            fontWeight = FontWeight.Bold,
            fontFamily = NumericFont,
            color = t.inkSoft,
        )
        Text(
            startTime,
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            fontFamily = NumericFont,
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
                    .border(Stroke.emphasis, t.primary.copy(alpha = 0.55f), RoundedCornerShape(4.dp)),
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
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("未排课 / 冲突", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.inkSoft)
        Spacer(Modifier.width(6.dp))
        Text("$n 门", fontSize = 9.5.sp, fontFamily = NumericFont, color = t.inkFaint)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(Stroke.hairline).background(t.rule))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnscheduledRow(c: Course, onClick: () -> Unit) {
    val t = InkTokens.current
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
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 14.dp)) {
        if (weekTotal == 0) {
            Text("这一周没有课。", fontSize = 11.sp, color = t.inkSoft)
            Spacer(Modifier.height(3.dp))
        }
        if (isThisWeek && todayTotal > 0) {
            Text(
                "今日已消化 $eaten 节 · 还剩 ${(todayTotal - eaten).coerceAtLeast(0)} 节",
                fontSize = 10.5.sp,
                fontFamily = NumericFont,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(
            buildString {
                append("本周 $weekTotal 格")
                if (unscheduled > 0) append(" · 另有 $unscheduled 门未排/冲突")
                append(" · ").append(SchoolClock.hintOf(summer))
                append("\n点卡片看详情 · 长按卡片复制/跳周")
            },
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
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
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(32.dp), enabled = week > minWeek) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "上一周",
                Modifier.size(16.dp),
                tint = if (week > minWeek) t.inkSoft else t.outline,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).combinedClickable(onClick = onRecalibrate, onLongClick = onRecalibrate),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "第 $week 周",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = NumericFont,
                    color = t.ink,
                )
                if (isThisWeek) {
                    Spacer(Modifier.width(7.dp))
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
            Spacer(Modifier.height(2.dp))
            Text(
                (term.ifBlank { "本学期" }) + " · $courseCount 门",
                fontSize = 9.5.sp,
                color = t.inkFaint,
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp), enabled = week < maxWeek) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, "下一周",
                Modifier.size(16.dp),
                tint = if (week < maxWeek) t.inkSoft else t.outline,
            )
        }
        IconButton(onClick = onToday, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Today, "回到本周", Modifier.size(16.dp), tint = t.primary)
        }
    }
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
            Spacer(Modifier.height(6.dp))
            Text("两种方式选一个", fontSize = 12.5.sp, color = t.inkFaint)
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
                    Text("电脑生成 JSON 后推到目录：", fontSize = 11.5.sp, color = t.inkFaint)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "adb push karbon-timetable.json /data/local/tmp/enroute-data/",
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontFamily = NumericFont,
                        color = t.inkFaint,
                    )
                    Spacer(Modifier.height(14.dp))
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