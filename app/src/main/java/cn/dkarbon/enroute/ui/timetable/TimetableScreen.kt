package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
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
import java.util.Calendar

/**
 * 课程表：**一根竖线串课**（2026-11-11）。
 *
 * ## 布局
 * ```
 *  08:00 ─■ 周三 11/11                 3 节
 *  09:40 ┃  ┌────────────────────────────┐
 *        ┃  │ 🍎  高等数学                │
 *        ┃  │     二教606 · 张老师        │
 *  10:00 ┃  └────────────────────────────┘
 *        ┃  ┌────────────────────────────┐
 *        ┃  │ 🥐  大学物理        👻      │
 *  11:40 ┃  └────────────────────────────┘
 *        ┗
 * ```
 * · **每天一条独立的竖线**，从组头起、到最后一节止，段尾一个小方块封口
 * · 课程卡横向铺满，课名 14sp 粗体，一眼能读（网格里只有 9.5sp）
 * · 课程 = 一份食物（Twemoji，CC-BY 4.0），被「吃掉」多少就褪多少
 * · 正在上的那节，幽灵趴在那儿啃
 *
 * ## 数据前提
 * 依赖 [cn.dkarbon.enroute.data.UrpClient.parseSchedule] 正确解析
 * `timeAndPlaceList`（星期/节次/周次位图）。那条链断了这里就全是「未排」。
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
    val monday = remember(week) { cal.mondayOfWeek(week) }

    // 排不上时间的课（weeks/day/sections 有空的）单独列出来，不能静默丢掉
    val unscheduled = remember(tt) {
        tt.courses.filter { it.weeks.isEmpty() || it.weekday !in 1..7 || it.sections.isEmpty() }
    }
    val inWeek = remember(tt, week) {
        tt.courses.filter {
            it.weeks.isNotEmpty() && it.weekday in 1..7 && it.sections.isNotEmpty() && week in it.weeks
        }
    }

    val days = remember(inWeek, todayWeekday, isThisWeek) {
        (1..7).mapNotNull { d ->
            val list = inWeek.filter { it.weekday == d }.sortedBy { it.sections.first() }
            val isEmptyToday = isThisWeek && d == todayWeekday && list.isEmpty()
            if (list.isEmpty() && !isEmptyToday) null else d to list
        }
    }

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

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
        ) {
            days.forEach { (day, courses) ->
                item(key = "d$day", contentType = "day") {
                    DayHeader(
                        day = day,
                        date = monday.plusDays((day - 1).toLong()),
                        isToday = isThisWeek && day == todayWeekday,
                        count = courses.size,
                    )
                }

                if (courses.isEmpty()) {
                    item(key = "e$day", contentType = "empty") { EmptyDayRow() }
                } else {
                    itemsIndexed(
                        courses,
                        key = { _, it -> "c${day}_${it.courseNo}_${it.seqNo}_${it.sections.first()}" },
                        contentType = { _, _ -> "course" },
                    ) { idx, c ->
                        val isTodayCourse = isThisWeek && day == todayWeekday
                        CourseRow(
                            c = c,
                            summer = summer,
                            progress = if (isTodayCourse) courseProgress(c, nowMin, summer) else 0f,
                            isNow = isTodayCourse && isNowSession(c, nowMin, summer),
                            isFirst = idx == 0,
                            isLast = idx == courses.lastIndex,
                        )
                    }
                }
            }

            if (unscheduled.isNotEmpty()) {
                item(key = "unsched", contentType = "day") { UnscheduledHeader(unscheduled.size) }
                items(unscheduled, key = { "u_${it.courseNo}_${it.seqNo}" }, contentType = { "unsch" }) { c ->
                    UnscheduledRow(c)
                }
            }

            item(key = "foot", contentType = "foot") {
                FootNote(weekTotal = inWeek.size, unscheduled = unscheduled.size, summer = summer)
            }
        }
    }
}

// ── 度量 ──────────────────────────────────────────────────

/**
 * 一行三段：
 * ```
 * │← AxisW 时刻列 →│← SpineW 竖线槽 →│←── 课程卡 weight(1f) ──→│
 * ```
 * 竖线固定落在槽的正中，时刻列宽怎么改都不会错位。
 */
private val AxisW = 48.dp
private val SpineW = 22.dp
private val SpineX = SpineW / 2
private val NodeSize = 7.dp
private val FoodSize = 26.dp

/**
 * 一天的竖线段：**只属于这一天**。
 *
 * 为什么不整页画一条：`LazyColumn` 里行会被回收，整页级 Canvas 拿不到真实滚动位置。
 * 每行画自己那一段，行紧挨着就成了一条连续的线，还能随滚动自然裁剪。
 */
@Composable
private fun DaySpine(
    modifier: Modifier = Modifier,
    isFirst: Boolean,
    isLast: Boolean,
    lineColor: Color,
) {
    Box(modifier.fillMaxHeight()) {
        Box(
            Modifier
                .offset(x = SpineX - 0.5.dp, y = if (isFirst) 10.dp else 0.dp)
                .width(1.dp)
                .fillMaxHeight()
                .padding(bottom = if (isLast) 7.dp else 0.dp)
                .background(lineColor),
        )
        // 段尾封口：告诉眼睛「这一天到这儿结束」
        if (isLast) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = SpineX - 3.dp, y = (-5).dp)
                    .size(6.dp)
                    .background(lineColor),
            )
        }
    }
}

// ── 一天的组头 ────────────────────────────────────────────

@Composable
private fun DayHeader(day: Int, date: java.time.LocalDate, isToday: Boolean, count: Int) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().height(36.dp)) {
        Box(Modifier.width(AxisW).fillMaxHeight())
        Box(Modifier.width(SpineW).fillMaxHeight()) {
            Box(
                Modifier
                    .offset(x = SpineX - NodeSize / 2, y = 10.dp)
                    .size(NodeSize)
                    .background(if (isToday) t.primary else t.inkFaint, RectangleShape),
            )
        }
        Row(
            Modifier.weight(1f).fillMaxHeight().padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")[day],
                fontSize = 13.5.sp,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (isToday) t.primary else t.ink,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${date.monthValue}/${date.dayOfMonth}",
                fontSize = 10.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            if (count > 0) {
                Text("$count 节", fontSize = 9.5.sp, fontFamily = NumericFont, color = t.inkSoft)
            }
        }
    }
}

// ── 课程行 ────────────────────────────────────────────────

@Composable
private fun CourseRow(
    c: Course,
    summer: Boolean,
    progress: Float,
    isNow: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val t = InkTokens.current
    val (start, end) = courseRange(c, summer)
    val fam = Tt.famOf(c.courseNo.ifBlank { c.name })
    val wash = Tt.washOf(fam, t.isDark)
    val spine = Tt.spineOf(fam, t.isDark)
    val food = Food.of(c.courseNo.ifBlank { c.name })
    val ghost = GhostForm.of(c.courseNo.ifBlank { c.name })
    val eaten = progress >= 0.999f

    Row(Modifier.fillMaxWidth()) {
        Column(
            Modifier.width(AxisW).padding(top = 9.dp, end = 2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "%02d:%02d".format(start / 60, start % 60),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = NumericFont,
                color = if (isNow) t.primary else t.inkSoft,
            )
            Text(
                "%02d:%02d".format(end / 60, end % 60),
                fontSize = 9.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
            )
        }

        DaySpine(
            modifier = Modifier.width(SpineW),
            isFirst = isFirst,
            isLast = isLast,
            lineColor = t.outline,
        )

        Box(Modifier.weight(1f).padding(vertical = 4.dp).padding(end = 14.dp)) {
            if (eaten) {
                EatenResidue(Modifier.fillMaxWidth().height(34.dp))
            } else {
                ErodedCard(
                    wash = wash,
                    spine = spine,
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FoodIcon(
                            food = food,
                            modifier = Modifier.size(FoodSize),
                            eaten = progress,
                        )
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.name,
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = t.ink,
                            )
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (c.roomShort.isNotBlank()) {
                                    Text(
                                        roomTag(c.roomShort),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = t.inkSoft,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                }
                                if (c.roomShort.isNotBlank() && c.teacher.isNotBlank()) {
                                    Text(" · ", fontSize = 10.sp, color = t.rule)
                                }
                                if (c.teacher.isNotBlank()) {
                                    Text(
                                        c.teacher,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = t.inkFaint,
                                    )
                                }
                            }
                        }
                        if (isNow) {
                            Spacer(Modifier.width(4.dp))
                            TravellingGhost(
                                form = ghost,
                                color = spine,
                                size = 22.dp,
                                mood = GhostMood.Feed,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDayRow() {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().height(30.dp)) {
        Box(Modifier.width(AxisW).fillMaxHeight())
        DaySpine(
            modifier = Modifier.width(SpineW),
            isFirst = true,
            isLast = true,
            lineColor = t.outline,
        )
        Box(
            Modifier.weight(1f).fillMaxHeight().padding(end = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("没课", fontSize = 10.5.sp, color = t.inkFaint)
        }
    }
}

// ── 未排课 ────────────────────────────────────────────────

@Composable
private fun UnscheduledHeader(n: Int) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(start = AxisW + SpineW, top = 18.dp, bottom = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("未排课", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.inkSoft)
        Spacer(Modifier.width(6.dp))
        Text("$n 门", fontSize = 9.5.sp, fontFamily = NumericFont, color = t.inkFaint)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(Stroke.hairline).background(t.rule))
    }
}

@Composable
private fun UnscheduledRow(c: Course) {
    val t = InkTokens.current
    val fam = Tt.famOf(c.courseNo.ifBlank { c.name })
    val spine = Tt.spineOf(fam, t.isDark)
    val food = Food.of(c.courseNo.ifBlank { c.name })

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = AxisW + SpineW, end = 14.dp, top = 2.dp, bottom = 2.dp)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
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
private fun FootNote(weekTotal: Int, unscheduled: Int, summer: Boolean) {
    val t = InkTokens.current
    Column(Modifier.fillMaxWidth().padding(start = AxisW + SpineW, top = 14.dp, end = 14.dp)) {
        if (weekTotal == 0) {
            Text("这一周没有课。", fontSize = 11.sp, color = t.inkSoft)
            Spacer(Modifier.height(3.dp))
        }
        Text(
            buildString {
                append("本周 $weekTotal 节")
                if (unscheduled > 0) append(" · 另有 $unscheduled 门未排课")
                append(" · ").append(SchoolClock.hintOf(summer))
            },
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = t.inkFaint,
        )
    }
}

// ── 计算 ──────────────────────────────────────────────────

private fun courseRange(c: Course, summer: Boolean): Pair<Int, Int> {
    val bigs = BigSession.ofCourse(c.sections)
    if (bigs.isEmpty()) return 0 to 0
    return SchoolClock.rangeOf(bigs.first(), summer).first to
        SchoolClock.rangeOf(bigs.last(), summer).second
}

private fun courseProgress(c: Course, nowMin: Int, summer: Boolean): Float {
    val bigs = BigSession.ofCourse(c.sections)
    if (bigs.isEmpty()) return 0f
    return bigs.map { eatProgress(it, nowMin, summer) }.average().toFloat()
}

private fun isNowSession(c: Course, nowMin: Int, summer: Boolean): Boolean {
    val bigs = BigSession.ofCourse(c.sections)
    if (bigs.isEmpty()) return false
    val (s, _) = SchoolClock.rangeOf(bigs.first(), summer)
    val (_, e) = SchoolClock.rangeOf(bigs.last(), summer)
    return nowMin in s..e
}

private fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

private fun roomTag(room: String): String = room
    .replace("第二教学楼", "二教")
    .replace("第一教学楼", "一教")
    .replace("实验楼", "")
    .replace("实验室", "实")
    .replace("土壤楼", "土壤")
    .replace("主校", "")
    .replace("实践上课地点", "实践")
    .trim()

// ── 周次头 ────────────────────────────────────────────────

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
            modifier = Modifier.weight(1f).clickable(onClick = onRecalibrate),
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
                            color = Color.White,
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

@Composable
private fun ImportTab(label: String, on: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .height(34.dp)
            .background(if (on) t.primary else Color.Transparent)
            .border(Stroke.hairline, if (on) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) Color.White else t.inkSoft,
        )
    }
}
