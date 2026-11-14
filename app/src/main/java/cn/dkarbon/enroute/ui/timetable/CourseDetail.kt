package cn.dkarbon.enroute.ui.timetable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.delay

/**
 * 课程详情浮层（2026-11-11）。
 *
 * ## 为什么是「从底部顶上来的一块纸」而不是对话框
 * 周历里卡片只有 40dp 宽，点开就是想知道三件事：**在哪、什么时候、这周上不上**。
 * 所以这块纸按「答案的顺序」排：抬头（是什么课）→ 时间/地点 → 周次矩阵 → 动作。
 * 圆角 0、1.5dp 实心描边、顶部一条该课的满彩色带 —— 和硬朗体系同一套语言。
 *
 * ## 周次矩阵：既是读数器，也是遥控器
 * 教务只给一串 `[1,2,3,5,8…]`，直接写「第1-3,5,8-15周」要用户在脑子里画格子。
 * 这里把 21 周摊成一排小方块：有课填满色、当前正在看的那一周套橙框。
 * **点任意一格 = 周历直接跳到那一周**（浮层不关，后面的周历跟着换），
 * 于是「这门课第 12 周上不上」不用退出再翻页。
 */
@Composable
fun CourseDetailOverlay(
    course: Course,
    week: Int,
    todayWeek: Int,
    summer: Boolean,
    onWeekSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** 课程大纲入口（票 10）。默认不做事，课表侧调用时传进来即可 */
    onOpenSyllabus: (Course) -> Unit = {},
) {
    val t = InkTokens.current
    val key = course.courseNo.ifBlank { course.name }
    val fam = Tt.famOf(key)
    val wash = Tt.washOf(fam, t.isDark)
    val spine = Tt.spineOf(fam, t.isDark)
    val food = Food.of(key)

    // 进出场：进来是 200ms 顶上来，关掉时先滑下去再摘掉（不留突兀的硬切）
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(200)
            onDismiss()
        }
    }
    val slide by animateDpAsState(
        targetValue = if (entered && !closing) 0.dp else 460.dp,
        animationSpec = tween(200),
        label = "detailSheet",
    )
    val close = { closing = true }

    val (startMin, endMin) = CourseFacts.rangeOf(course, summer)
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        // 遮罩：点空白处关掉
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (closing) 0f else 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭详情",
                    onClick = close,
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = slide)
                .fillMaxWidth()
                .background(t.paper)
                .border(1.5.dp, t.outline),
        ) {
            // 顶部满彩色带：这门课的身份证
            Box(Modifier.fillMaxWidth().height(6.dp).background(spine))

            // ── 抬头 ──
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .background(wash)
                        .border(Stroke.hairline, spine.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FoodIcon(food = food, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        course.name,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = t.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            append(CourseFacts.weekdayName(course.weekday))
                            if (course.category.isNotBlank()) append(" · ").append(course.category)
                            if (course.credit != null) append(" · ").append(CourseFacts.trimZero(course.credit))
                                .append(" 学分")
                        },
                        fontSize = 10.5.sp,
                        color = t.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 点进来最想知道的一件事，就放在最显眼的位置
                val here = week in course.weeks
                Box(
                    Modifier
                        .height(24.dp)
                        .border(if (here) 2.dp else 1.dp, if (here) t.primary else t.outline)
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            week == todayWeek && here -> "本周 上课"
                            week == todayWeek -> "本周 不上"
                            here -> "第 $week 周 上课"
                            else -> "第 $week 周 不上"
                        },
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (here) t.primary else t.inkSoft,
                        maxLines = 1,
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

            // ── 事实四行：时间 / 地点 / 教师 / 周次 ──
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                FactRow("时间", "${course.sectionsLabel} · ${CourseFacts.fmtMin(startMin)}–${CourseFacts.fmtMin(endMin)}")
                FactRow("地点", course.room.ifBlank { "未填" })
                FactRow("教师", course.teacher.ifBlank { "未填" })
                FactRow("周次", CourseFacts.weeks(course.weeks))
                if (course.courseNo.isNotBlank()) {
                    FactRow("课程号", course.courseNo + " · " + course.seqNo, mono = true)
                }
            }

            Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

            WeekMatrix(
                weeks = course.weeks,
                viewed = week,
                todayWeek = todayWeek,
                fill = spine,
                wash = wash,
                onPick = onWeekSelect,
            )

            // ── 动作 ──
            // 课程大纲入口（票 10）：只加这一个入口，课表本身不动。
            // ⚠️ 这里**不判断「有没有大纲」**：判断要读 22KB 大纲索引，主线程不能读；
            // 所以入口常在，点开由大纲浮层如实说「有 / 这本册子里没有」。宁可可发现，也不撒谎。
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("课程大纲", fontSize = 11.sp, color = t.inkSoft, modifier = Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RectangleShape)
                        .border(Stroke.hairline, t.outline)
                        .background(t.card)
                        .clickable(onClickLabel = "查看这门课的大纲") { onOpenSyllabus(course) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text("查看大纲 →", fontSize = 11.5.sp, color = t.primary, fontWeight = FontWeight.Medium)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val cb = LocalClipboardManager.current
                var copied by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = {
                        cb.setText(AnnotatedString(CourseFacts.copyText(course, startMin, endMin)))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                ) {
                    Text(if (copied) "已复制" else "复制课程信息", fontSize = 12.5.sp)
                }
                Button(
                    onClick = close,
                    modifier = Modifier.weight(1f),
                    shape = RectangleShape,
                ) {
                    Text("知道了", fontSize = 12.5.sp)
                }
            }

            Spacer(Modifier.height(navPad + 6.dp))
        }
    }
}

/** 一行「标签 + 值」。标签固定 42dp 宽，值左端永远对齐 */
@Composable
private fun FactRow(label: String, value: String, mono: Boolean = false) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 3.5.dp)) {
        Text(label, fontSize = 10.5.sp, color = t.inkFaint, modifier = Modifier.width(42.dp))
        Text(
            value,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = t.ink,
            fontFamily = if (mono) NumericFont else null,
        )
    }
}

/**
 * 周次矩阵：7 列 × N 行（21 周正好 3 行，没有尾巴空格）。
 *
 * ## 为什么是「轻读数器」而不是 21 个实心色块
 * 21 个深色块会变成全屏最重的一块，比 19sp 的课名还重 —— 而它只是辅助信息。
 * 现在：有课 = 淡色底 + 细描边 + 深字，**只有"正在看的那一周"用满色实填**。
 * 于是"我在看第 8 周"反而更跳，那正是点进来的目的。
 *
 * 点一格 = 周历翻到那一周，浮层不关。
 */
@Composable
private fun WeekMatrix(
    weeks: List<Int>,
    viewed: Int,
    todayWeek: Int,
    fill: Color,
    wash: Color,
    onPick: (Int) -> Unit,
) {
    val t = InkTokens.current
    if (weeks.isEmpty()) return
    val total = maxOf(21, weeks.maxOrNull() ?: 21)
    val perRow = 7

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("上课周次", fontSize = 10.5.sp, color = t.inkFaint)
            Spacer(Modifier.weight(1f))
            Text("共 ${weeks.size} 周 · 点格子跳到那一周", fontSize = 9.sp, color = t.inkFaint)
        }
        Spacer(Modifier.height(6.dp))

        (1..total).chunked(perRow).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { w ->
                    val on = w in weeks
                    val isViewed = w == viewed
                    Box(
                        Modifier
                            .weight(1f)
                            .height(24.dp)
                            .background(
                                when {
                                    isViewed -> fill
                                    on -> wash
                                    else -> Color.Transparent
                                },
                            )
                            .border(
                                when {
                                    isViewed -> 2.dp
                                    on -> 0.75.dp
                                    else -> 1.dp
                                },
                                when {
                                    isViewed -> t.primary
                                    on -> fill.copy(alpha = 0.45f)
                                    else -> t.rule.copy(alpha = 0.6f)
                                },
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "跳到第 $w 周",
                                onClick = { onPick(w) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$w",
                            fontSize = 9.5.sp,
                            fontFamily = NumericFont,
                            fontWeight = if (isViewed) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isViewed -> Color.White
                                on -> t.ink
                                else -> t.inkFaint
                            },
                        )
                        // 今天所在的周：顶上一条 6×2dp 的小方条（0 圆角体系里不画圆点）
                        if (w == todayWeek) {
                            Box(
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 2.dp)
                                    .width(6.dp)
                                    .height(2.dp)
                                    .background(if (isViewed) Color.White else t.primary),
                            )
                        }
                    }
                }
                repeat(perRow - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

// ── 课程事实（详情浮层与长按菜单共用）────────────────────

/**
 * 从 [Course] 推出人话的一小组事实。
 *
 * 抽成 object 是因为「详情浮层」和「长按快捷菜单」要说同一套话 ——
 * 两处各写一遍必然分叉（复制出来的文字和屏上显示的不一致，是最招人烦的 bug）。
 */
internal object CourseFacts {

    /** 这门课在时间轴上的起止分钟（跨多个大节时取首尾） */
    fun rangeOf(c: Course, summer: Boolean): Pair<Int, Int> {
        val bigs = BigSession.ofCourse(c.sections)
        if (bigs.isEmpty()) return 0 to 0
        return SchoolClock.rangeOf(bigs.first(), summer).first to
            SchoolClock.rangeOf(bigs.last(), summer).second
    }

    fun fmtMin(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    fun weekdayName(d: Int): String = when (d) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "未排课"
    }

    fun trimZero(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    /**
     * 周次压缩：`[1,2,3,5,8,9,10,11]` → `第 1-3,5,8-11 周`。
     *
     * 教务给的是位图，直接 joinToString 会变成「第1,2,3,5,8,9,10,11周」——
     * 一行字读不出结构，所以要连续段合并。
     */
    fun weeks(weeks: List<Int>): String {
        if (weeks.isEmpty()) return "未排"
        val s = weeks.distinct().sorted()
        val parts = mutableListOf<String>()
        var start = s.first()
        var prev = s.first()
        for (w in s.drop(1)) {
            if (w == prev + 1) {
                prev = w
                continue
            }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = w
            prev = w
        }
        parts += if (start == prev) "$start" else "$start-$prev"
        return "第 " + parts.joinToString(",") + " 周"
    }

    /**
     * 教室名压缩：`第二教学楼 / 606` → `二606`。
     *
     * 42dp 的卡、一行副标题里都放不下楼名全称，所以统一在这里压：
     * 楼名换成缩写、超长时只留「楼名尾两字 + 房号」，最后把「教」也省掉。
     * 周历卡和长按菜单共用这一份，避免两处显示不一。
     */
    fun shortPlace(room: String): String {
        val r = room
            .replace("第二教学楼", "二教")
            .replace("第一教学楼", "一教")
            .replace("实验楼", "")
            .replace("实验室", "实")
            .replace("土壤楼", "土壤")
            .replace("主校", "")
            .replace("实践上课地点", "实践")
            .replace(" / ", "")
            .replace("/", "")
            .replace(" ", "")
            .trim()
        val digits = Regex("[0-9]{2,}").find(r)
        val short = if (r.length > 6 && digits != null) {
            r.substring(0, digits.range.first).takeLast(2).trim() + digits.value
        } else {
            r
        }
        return short.replace("教", "").ifBlank { short }
    }

    /** 一行摘要：`周二 第3-4节 · 二606`（长按菜单用，不放整段） */
    fun brief(c: Course): String = buildString {
        append(weekdayName(c.weekday))
        append(" ")
        append(c.sectionsLabel)
        if (c.room.isNotBlank()) append(" · ").append(shortPlace(c.room))
    }

    /** 复制到剪贴板的文本：一行能贴给同学的那种 */
    fun copyText(c: Course, startMin: Int, endMin: Int): String = buildString {
        append(c.name)
        append("｜")
        append(weekdayName(c.weekday))
        append(" ")
        append(c.sectionsLabel)
        append(" ")
        append(fmtMin(startMin))
        append("-")
        append(fmtMin(endMin))
        if (c.room.isNotBlank()) {
            append("｜")
            append(c.room)
        }
        if (c.teacher.isNotBlank()) {
            append("｜")
            append(c.teacher)
        }
        append("｜")
        append(weeks(c.weeks))
    }
}
