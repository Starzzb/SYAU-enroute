package cn.dkarbon.enroute.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherSlot
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.timetable.BigSession

/**
 * 教师详情页（票 08）—— **独立页面**，不是浮层。
 *
 * 两块内容，来源必须分清：
 * 1. **本学期课表**：教务 `searchCurriculumInfoLo → callback` 两步流，周次用 24 位位图；
 * 2. **官网公开资料**：学院 VSB 站群正文解析，**标注来源与抓取时间**，可打开原文。
 *
 * 三种「没有内容」必须长得不一样（主人明确要求）：
 * - 课表空但接口正常 → 「这位老师本学期没有排课」；
 * - 课表没拿到 → 登录态/校园网提示；
 * - 官网没解析到 → 「官网没解析到…」+ 原文入口。
 * 这三句分开写在 `HomeModel` / ViewModel 里，不在这层拼字符串。
 */
@Composable
fun TeacherDetailScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        // ── 身份头 ──
        Row(
            Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.teacherDetailName.ifBlank { "教师详情" },
                    fontSize = 17.sp,
                    color = t.ink,
                    fontWeight = FontWeight.Bold,
                )
                val sub = listOf(ui.teacherDetailDept, ui.teacherDetailTitle, ui.teacherDetailSex)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(sub, fontSize = 11.sp, color = t.inkSoft)
                }
                if (ui.teacherDetailNo.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "工号 ${ui.teacherDetailNo}",
                        fontSize = 10.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                    )
                }
            }
            TextButton(onClick = { vm.refreshTeacherDetail() }) {
                Text("刷新", fontSize = 12.sp)
            }
        }

        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l, vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            // ── 官网正文图片（有就贴在顶部，没有不占位）──
            TeacherPhotoRow(ui.facultyInfo?.images.orEmpty())

            // ── 课表块 ──
            BlockTitle(
                title = "TA 这学期的课表",
                right = HomeModel.scheduleHeadline(ui.teacherSchedule),
            )
            when {
                ui.teacherScheduleLoading -> HintText("正在查课表…")
                ui.teacherSchedule.isEmpty ->
                    HintText(ui.teacherScheduleMsg.ifBlank { "这位老师本学期没有排课" })
                else -> {
                    WeekGrid(ui.teacherSchedule)
                    Spacer(Modifier.height(Space.xs))
                    ui.teacherSchedule.slots
                        .sortedWith(compareBy({ it.weekday }, { it.startSection }))
                        .forEach { SlotRow(it) }
                }
            }

            Spacer(Modifier.height(Space.m))

            // ── 官网公开资料块 ──
            BlockTitle(
                title = "学院官网公开资料",
                right = if (ui.facultyLoading) "查询中" else "",
            )
            val info = ui.facultyInfo
            if (info != null) {
                info.fields.rows().forEach { (label, value) ->
                    LabelRow(label, value)
                }
                if (info.fields.rows().isEmpty()) {
                    if (info.sections.isNotEmpty()) {
                        Text("原文", fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
                    } else {
                        HintText("暂缺")
                    }
                }
                info.sections.take(3).forEach { sec ->
                    Spacer(Modifier.height(Space.xs))
                    Text(sec.title, fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
                    Text(
                        sec.text.take(320),
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = t.inkSoft,
                    )
                }
                if (ui.facultyMsg.isNotBlank()) {
                    Spacer(Modifier.height(Space.xs))
                    HintText(ui.facultyMsg)
                }
                Spacer(Modifier.height(Space.s))
                Text(
                    HomeModel.facultySourceLine(info),
                    fontSize = 10.sp,
                    color = t.inkFaint,
                    lineHeight = 15.sp,
                )
                Spacer(Modifier.height(Space.xs))
                OpenUrlButton("在浏览器打开官网原文") {
                    runCatching { uri.openUri(info.sourceUrl) }
                }
            } else {
                HintText(
                    when {
                        ui.facultyLoading -> "正在查官网资料…"
                        ui.facultyMsg.isNotBlank() -> ui.facultyMsg
                        else -> "学院官网没解析到这位老师的信息"
                    },
                )
            }
            if (ui.facultyCandidates.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
                ui.facultyCandidates.forEachIndexed { i, url ->
                    OpenUrlButton("候选原文 ${i + 1}") { runCatching { uri.openUri(url) } }
                }
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/** 块标题 + 右侧小结 */
@Composable
private fun BlockTitle(title: String, right: String = "") {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (right.isNotBlank()) {
            Text(right, fontSize = 10.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
    }
}

@Composable
private fun HintText(text: String) {
    val t = InkTokens.current
    Text(text, fontSize = 11.sp, lineHeight = 17.sp, color = t.inkSoft)
}

/** 浮层/详情里的一行「标签 → 值」；值空就不显示（不写「未提供」占地方） */
@Composable
private fun LabelRow(label: String, value: String) {
    val t = InkTokens.current
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 10.5.sp, color = t.inkFaint, modifier = Modifier.width(64.dp))
        Text(
            value,
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = t.inkSoft,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OpenUrlButton(label: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .clip(RectangleShape)
            .border(Stroke.hairline, t.outline)
            .background(t.card)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = 8.dp),
    ) {
        Text(label, fontSize = 11.5.sp, color = t.primary, fontWeight = FontWeight.Medium)
    }
}

/**
 * 周网格：6 个大节 × 7 天。
 *
 * 为什么还留网格（明细列表也给了）：网格回答「TA 哪天满、哪天闲」，
 * 明细回答「这门课几周上、在哪个教室、多少人」。两者都塞进网格会看不清，
 * 所以网格只放课程名前几个字，其余信息在下面的列表里给全。
 */
@Composable
private fun WeekGrid(schedule: TeacherSchedule) {
    val t = InkTokens.current
    val cells = remember(schedule) {
        val map = HashMap<Int, MutableList<TeacherSlot>>()
        schedule.slots.forEach { s ->
            val big = BigSession.of(s.startSection) ?: return@forEach
            map.getOrPut(s.weekday * 10 + big.index) { mutableListOf() }.add(s)
        }
        map
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
            Spacer(Modifier.width(34.dp))
            (1..7).forEach { d ->
                Text(
                    HomeModel.weekdayLabel(d).takeLast(1),
                    fontSize = 9.5.sp,
                    color = t.inkFaint,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        BigSession.entries.forEach { big ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp)) {
                Text(
                    big.label.replace(" 节", ""),
                    fontSize = 8.5.sp,
                    color = t.inkFaint,
                    fontFamily = NumericFont,
                    modifier = Modifier.width(34.dp),
                )
                (1..7).forEach { d ->
                    val list = cells[d * 10 + big.index].orEmpty()
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 30.dp)
                            .padding(horizontal = 1.dp)
                            .background(if (list.isEmpty()) t.card else t.cardAlt)
                            .border(Stroke.hairline, t.outline)
                            .padding(horizontal = 2.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (list.isNotEmpty()) {
                            val head = list.first()
                            Text(
                                buildString {
                                    append(head.courseName.take(5))
                                    if (list.size > 1) append("+${list.size - 1}")
                                },
                                fontSize = 8.5.sp,
                                lineHeight = 11.sp,
                                color = t.ink,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 明细一行：课程 + 时间 + 地点 + 班级人数 + 学分考核 */
@Composable
private fun SlotRow(slot: TeacherSlot) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.s, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                slot.courseName.ifBlank { "（课程名未提供）" },
                fontSize = 12.5.sp,
                color = t.ink,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (slot.credit.isNotBlank()) {
                Text("${slot.credit} 学分", fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
            }
        }
        Text(HomeModel.slotWhenLabel(slot), fontSize = 10.5.sp, color = t.inkSoft, fontFamily = NumericFont)
        val place = listOf(slot.campus, slot.place).filter { it.isNotBlank() }.joinToString(" · ")
        if (place.isNotBlank()) {
            Text(place, fontSize = 10.5.sp, color = t.inkFaint)
        }
        val tail = listOf(
            slot.className,
            if (slot.students > 0) "${slot.students} 人" else "",
            slot.examType,
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (tail.isNotBlank()) {
            Text(tail, fontSize = 9.5.sp, color = t.inkFaint, lineHeight = 14.sp)
        }
    }
}
