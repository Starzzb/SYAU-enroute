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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.data.HistorySemesters
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.home.HomeModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import java.time.LocalDate

/**
 * 历年课表（票 11）—— 只读回看往年学期。
 *
 * 为什么放在「我的」而不是课表页的学期切换处：课表页是每天十几次的高频界面，
 * 塞进学期切换会把「看今天」变得复杂；历年课表是低频回顾，与考试/成绩同级更稳。
 *
 * 只读口径（**历史不是今天**）：
 * - 不画今天竖带、不画当前时刻推进条、不做侵蚀、不显示小幽灵 —— 这些都靠传 `isThisWeek = false` 自然关掉；
 * - 不显示日期号数（那一周的真实公历日期我们不知道，硬编就是假数据），见 [GridArea] 的 `readOnly`；
 * - 不允许校准周次（校准是「现在第几周」，对历史学期无意义）。
 */
@Composable
fun HistoryTimetableScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current

    LaunchedEffect(Unit) { vm.openHistoryTimetable() }

    val tt = ui.historyTimetable
    val range = remember(tt) { HistoryRules.weekRange(tt?.courses.orEmpty()) }
    var week by remember(ui.historyPlanCode) { mutableIntStateOf(range.first) }
    var detail by remember { mutableStateOf<Course?>(null) }
    val summer = remember { SchoolClock.isSummerTime() }

    Column(Modifier.fillMaxSize()) {
        // ── 学期选择（服务器给的候选，不写死）──
        if (ui.historyTerms.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(ui.historyTerms, key = { it.code }) { option ->
                    TermChip(
                        label = HomeModel.termLabel(option.code).ifBlank { option.code } +
                            if (option.current) " · 当前" else "",
                        selected = option.code == ui.historyPlanCode,
                        onClick = { vm.selectHistoryTerm(option.code) },
                    )
                }
            }
        }

        // ── 状态行：缓存标记 + 刷新 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    ui.historyLoading -> "读取中…"
                    ui.historyMsg.isNotBlank() -> ui.historyMsg
                    ui.historyFromCache -> HistorySemesters.stamp(ui.historyFetchedAt)
                    tt != null -> "来自教务 · ${HomeModel.termLabel(tt.term.ifBlank { ui.historyPlanCode })}"
                    else -> ""
                },
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                color = t.inkFaint,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.refreshHistoryTerm() }, enabled = !ui.historyLoading) {
                Text("刷新", fontSize = 11.5.sp)
            }
        }

        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

        if (tt == null) {
            Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        ui.historyLoading -> "正在读这一学期的课表…"
                        ui.historyMsg.isNotBlank() -> ui.historyMsg
                        else -> "选一个学期看看"
                    },
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = t.inkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            return@Column
        }

        // ── 周切换（历史学期没有「今天」，只能前后翻）──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton("‹") { if (week > range.first) week-- }
            Text(
                "第 $week 周",
                fontSize = 12.5.sp,
                color = t.ink,
                fontWeight = FontWeight.Medium,
                fontFamily = NumericFont,
                modifier = Modifier.padding(horizontal = Space.s),
            )
            StepButton("›") { if (week < range.last) week++ }
            Spacer(Modifier.weight(1f))
            Text(
                "${tt.courses.map { it.courseNo.ifBlank { it.name } }.distinct().size} 门 · 共 ${range.last} 周",
                fontSize = 9.5.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
            )
        }

        GridArea(
            modifier = Modifier.weight(1f),
            week = week,
            // 历史学期永远不是「本周」——这一条由 HistoryRules 单测钉住
            isThisWeek = HistoryRules.isThisWeek(),
            todayWeekday = 0,
            cells = HistoryRules.cellsOf(tt.courses, week),
            nowMin = 0,
            summer = summer,
            monday = LocalDate.now(),
            onCourseClick = { detail = it },
            onCourseLongClick = { detail = it },
            readOnly = true,
        )
    }

    detail?.let { c ->
        CourseDetailOverlay(
            course = c,
            week = week,
            // -1 = 没有任何一周是「今天」，历史课表详情里不会出现「本周」高亮
            todayWeek = -1,
            summer = summer,
            onWeekSelect = { w -> week = w.coerceIn(range.first, range.last) },
            onDismiss = { detail = null },
            onOpenSyllabus = { co -> vm.openSyllabus(co.courseNo, co.name) },
        )
    }
}

/**
 * 历史学期的只读口径 —— **纯函数，单测直接打**（票 11 要求「不侵蚀」有派生逻辑可测）。
 */
internal object HistoryRules {

    /** 历史学期永远不是「本周」：今天竖带 / 推进条 / 侵蚀 / 小幽灵全部因此不出现 */
    fun isThisWeek(): Boolean = false

    /** 侵蚀进度：非本周恒为 0（把「现在几点」对历史课表使用就是错的数据） */
    fun progress(isThisWeek: Boolean, base: Float): Float = if (isThisWeek) base else 0f

    /**
     * 周范围：从课程的周次推；一门课都没有时给 `1..16` 兜底
     * （给 `0..0` 会让网格塌成一行，反而像坏了）。
     */
    fun weekRange(courses: List<Course>): IntRange {
        val weeks = courses.flatMap { it.weeks }.filter { it > 0 }
        if (weeks.isEmpty()) return 1..16
        return weeks.min()..weeks.max()
    }

    /**
     * 一格一门课（与课表页同口径：同格冲突取先出现的那门）。
     *
     * 分出来是为了让「某学期第 N 周有哪几门课」能用单测直接验（真实样本做夹具）。
     */
    fun cellsOf(courses: List<Course>, week: Int): Map<Pair<Int, Int>, Course> {
        val m = mutableMapOf<Pair<Int, Int>, Course>()
        for (c in courses) {
            if (c.weekday !in 1..7 || week !in c.weeks) continue
            for (b in BigSession.ofCourse(c.sections)) m.putIfAbsent(c.weekday to b.index, c)
        }
        return m
    }
}

@Composable
private fun TermChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .heightIn(min = 32.dp)
            .clip(RectangleShape)
            .background(if (selected) t.cardAlt else t.card)
            .border(if (selected) 1.5.dp else Stroke.hairline, if (selected) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) t.ink else t.inkSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .clip(RectangleShape)
            .border(Stroke.hairline, t.outline)
            .background(t.card)
            .clickable(onClickLabel = if (label == "‹") "上一周" else "下一周", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, color = t.inkSoft)
    }
}
