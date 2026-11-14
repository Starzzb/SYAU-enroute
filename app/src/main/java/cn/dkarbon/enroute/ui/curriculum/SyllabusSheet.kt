package cn.dkarbon.enroute.ui.curriculum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import cn.dkarbon.enroute.data.curriculum.Syllabus
import cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.academic.AcademicSheet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 课程大纲浮层（票 10）—— **三处入口共用这一个**：课表课程详情、培养方案课程行、大纲搜索页。
 *
 * 挂在 `EnRouteRoot` 的根 Box 上（一处渲染，全局可用），由 `AppState.syllabusOpenId` 控制显隐，
 * 所以任何入口只要调 `vm.openSyllabus(code, name)` 就行，不必各页各写一套。
 *
 * 纪律：
 * - 没有大纲 → 明说「这本院级大纲册子里没有」，**不空卡片、不转圈**；
 * - 命中多条 → 列候选让用户选，不猜；
 * - 章节可折叠，默认展开「课程基本信息 / 课程简介」，其余收起（大綱动辄几千字）。
 */
@Composable
fun SyllabusSheet(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    val s = ui.syllabusDetail
    val entry = ui.syllabusIndex.firstOrNull { it.id == ui.syllabusOpenId }

    AcademicSheet(
        t = t,
        title = s?.name ?: entry?.name ?: "课程大纲",
        subtitle = listOfNotNull(
            (s?.code ?: entry?.code)?.takeIf { it.isNotBlank() },
            (s?.credit ?: entry?.credit)?.takeIf { it.isNotBlank() }?.let { "$it 学分" },
            (s?.type ?: entry?.type)?.takeIf { it.isNotBlank() },
        ).joinToString(" · "),
        onDismiss = { vm.closeSyllabus() },
    ) {
        when {
            ui.syllabusDetailLoading -> Text(
                "正在读大纲…",
                fontSize = 11.5.sp,
                color = t.inkSoft,
            )

            s == null -> {
                if (ui.syllabusDetailMsg.isNotBlank()) {
                    Box(
                        Modifier.fillMaxWidth().background(t.cardAlt)
                            .border(Stroke.hairline, t.outline)
                            .padding(horizontal = Space.s, vertical = 9.dp),
                    ) {
                        Text(ui.syllabusDetailMsg, fontSize = 11.sp, lineHeight = 17.sp, color = t.inkSoft)
                    }
                }
                if (ui.syllabusCandidates.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    ui.syllabusCandidates.forEach { c ->
                        CandidateRow(c) { vm.openSyllabusById(c.id) }
                    }
                }
            }

            else -> SyllabusBody(s, ui.syllabusDetailMsg)
        }
    }
}

@Composable
private fun CandidateRow(entry: SyllabusIndexEntry, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Medium)
            Text(
                listOf(entry.code, entry.type, entry.creditHoursLabel)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 9.5.sp,
                color = t.inkFaint,
            )
        }
        Text("›", fontSize = 15.sp, color = t.inkFaint)
    }
}

@Composable
private fun SyllabusBody(s: Syllabus, msg: String) {
    val t = InkTokens.current
    var expanded by remember(s.code) { mutableStateOf(setOf<Int>(0, 1)) }
    var openBasic by remember(s.code) { mutableStateOf(false) }

    if (msg.isNotBlank()) {
        Text(msg, fontSize = 11.sp, lineHeight = 17.sp, color = t.inkSoft)
        Spacer(Modifier.height(Space.s))
    }

    // 顶部事实行
    val facts = listOf(
        "课程类型" to s.type,
        "学分" to s.credit,
        "理论学时" to s.theoryHours,
        "实验学时" to s.labHours,
        "课程类别" to s.category,
        "开课教研室" to s.department,
        "执笔人" to s.writer,
        "制定时间" to s.madeAt,
    ).filter { it.second.isNotBlank() }
    if (facts.isNotEmpty()) {
        Column(
            Modifier.fillMaxWidth().background(t.card)
                .border(Stroke.hairline, t.outline)
                .padding(horizontal = Space.s, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            facts.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { (k, v) ->
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                            Text(k, fontSize = 9.5.sp, color = t.inkFaint, modifier = Modifier.width(52.dp))
                            Text(
                                v,
                                fontSize = 10.5.sp,
                                color = t.inkSoft,
                                fontFamily = if (k.contains("学")) NumericFont else null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(Space.s))
    }

    // 课程目标
    if (s.goals.isNotEmpty()) {
        Text("课程目标", fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        s.goals.forEach { g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
                Text(
                    if (g.no.isNotBlank()) "${g.no}." else "·",
                    fontSize = 10.sp,
                    color = t.inkFaint,
                    fontFamily = NumericFont,
                    modifier = Modifier.width(18.dp),
                )
                Text(g.content, fontSize = 10.5.sp, lineHeight = 16.sp, color = t.inkSoft, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(Space.s))
    }

    // 章节（一、二、三… 按原文顺序，可折叠）
    s.sections.forEachIndexed { i, sec ->
        val open = i in expanded
        Column(
            Modifier.fillMaxWidth().padding(bottom = 3.dp).background(t.card)
                .border(Stroke.hairline, t.outline)
                .clickable {
                    expanded = if (open) expanded - i else expanded + i
                }
                .padding(horizontal = Space.s, vertical = Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sec.title,
                    fontSize = 11.5.sp,
                    color = t.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(if (open) "▾" else "▸", fontSize = 11.sp, color = t.inkFaint)
            }
            if (open) {
                Spacer(Modifier.height(4.dp))
                Text(
                    sec.text.ifBlank { "原文这一节为空" },
                    fontSize = 10.5.sp,
                    lineHeight = 17.sp,
                    color = if (sec.text.isBlank()) t.inkFaint else t.inkSoft,
                )
            } else if (sec.text.isNotBlank()) {
                Text(
                    sec.text.take(48) + "…",
                    fontSize = 9.5.sp,
                    lineHeight = 14.sp,
                    color = t.inkFaint,
                    maxLines = 1,
                )
            }
        }
    }

    // 教学章节 / 学时一览表：原表渲染
    if (s.chapters.isNotEmpty()) {
        Spacer(Modifier.height(Space.s))
        MiniTable(
            title = "教学章节",
            headers = listOf("单位", "条目", "说明"),
            rows = s.chapters.map { listOf(it.unit, it.title, it.note) },
        )
    }
    if (s.hoursTable.isNotEmpty()) {
        Spacer(Modifier.height(Space.s))
        val rows = s.hoursTable.map {
            listOf(it.content, it.online, it.offline, it.lab, it.total)
        }
        MiniTable(
            title = "教学内容学时一览表",
            headers = listOf("教学内容", "线上", "线下", "实验", "合计"),
            rows = rows,
            footer = s.hoursTotal?.let { "合计 $it 学时（原表有空白行时不显示合计）" }
                ?: "原表有空白学时行，故不显示合计（不拿空白当 0 相加）",
        )
    }

    if (s.basicInfo.isNotEmpty()) {
        Spacer(Modifier.height(Space.s))
        Column(
            Modifier.fillMaxWidth().background(t.card).border(Stroke.hairline, t.outline)
                .clickable { openBasic = !openBasic }
                .padding(horizontal = Space.s, vertical = Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("课程基本信息（教材/参考书）", fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (openBasic) "▾" else "▸", fontSize = 11.sp, color = t.inkFaint)
            }
            if (openBasic) {
                Spacer(Modifier.height(4.dp))
                s.basicInfo.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                        Text(k, fontSize = 9.5.sp, color = t.inkFaint, modifier = Modifier.width(74.dp))
                        Text(v, fontSize = 10.sp, lineHeight = 15.sp, color = t.inkSoft, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(Space.s))
    Text(
        "大纲来源：土地与环境学院本科课程大纲（2021 版）· 内置资产",
        fontSize = 9.sp,
        color = t.inkFaint,
    )
}

/** 小表格：按原表列渲染，横向可滚 */
@Composable
private fun MiniTable(
    title: String,
    headers: List<String>,
    rows: List<List<String>>,
    footer: String = "",
) {
    val t = InkTokens.current
    Text(title, fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(3.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column {
            Row {
                headers.forEachIndexed { j, h ->
                    Box(
                        Modifier.width(if (j == 0) 132.dp else 62.dp).heightIn(min = 26.dp)
                            .background(t.cardAlt).border(Stroke.hairline, t.outline)
                            .padding(horizontal = 3.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(h, fontSize = 9.sp, color = t.ink, fontWeight = FontWeight.Bold)
                    }
                }
            }
            rows.forEach { row ->
                Row {
                    headers.indices.forEach { j ->
                        val cell = row.getOrNull(j).orEmpty()
                        Box(
                            Modifier.width(if (j == 0) 132.dp else 62.dp).heightIn(min = 26.dp)
                                .background(t.card).border(Stroke.hairline, t.outline)
                                .padding(horizontal = 3.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell,
                                fontSize = 8.5.sp,
                                lineHeight = 12.sp,
                                color = t.inkSoft,
                                fontFamily = if (j > 0) NumericFont else null,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
    if (footer.isNotBlank()) {
        Spacer(Modifier.height(3.dp))
        Text(footer, fontSize = 9.sp, color = t.inkFaint)
    }
}

/** 一个「查看大纲」按钮（课表详情 & 其它入口复用） */
@Composable
fun SyllabusEntryButton(label: String, onClick: () -> Unit) {
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

/** 一行的说明文字（搜索页脚注用） */
@Composable
fun SyllabusNote(text: String) {
    val t = InkTokens.current
    Text(
        text,
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        color = t.inkFaint,
        modifier = Modifier.fillMaxWidth()
            .background(t.cardAlt)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.s, vertical = 8.dp),
    )
}
