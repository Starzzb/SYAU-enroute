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
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.curriculum.Syllabus
import cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.kit.DetailFieldRow
import cn.dkarbon.enroute.ui.kit.DetailScaffold
import cn.dkarbon.enroute.ui.kit.DetailSection
import cn.dkarbon.enroute.ui.kit.InkTinyLoader
import cn.dkarbon.enroute.ui.kit.PaperTex
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
    // 整页详情：没打开就不占位（原来靠浮层的 animated 显隐兜着）
    if (ui.syllabusOpenId == null) return

    val s = ui.syllabusDetail
    val entry = ui.syllabusIndex.firstOrNull { it.id == ui.syllabusOpenId }
    val headName = s?.name ?: entry?.name ?: "课程大纲"
    val meta = listOfNotNull(
        (s?.code ?: entry?.code)?.takeIf { it.isNotBlank() },
        (s?.credit ?: entry?.credit)?.takeIf { it.isNotBlank() }?.let { "$it 学分" },
        (s?.type ?: entry?.type)?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    DetailScaffold(
        backLabel = "课程大纲",
        tex = PaperTex.WEAVE,
        onBack = { vm.closeSyllabus() },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("课程大纲", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = t.inkSoft)
            Spacer(Modifier.weight(1f))
            Text("2021 版", fontSize = 9.5.sp, fontFamily = NumericFont, color = t.inkFaint)
        }
        Spacer(Modifier.height(6.dp))
        Text(headName, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
        if (meta.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                meta,
                fontSize = 9.5.sp,
                letterSpacing = 0.04.em,
                fontFamily = NumericFont,
                color = t.inkFaint,
            )
        }
        Spacer(Modifier.height(13.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.ink))

        when {
            ui.syllabusDetailLoading -> Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkTinyLoader()
                Spacer(Modifier.width(8.dp))
                Text("正在读大纲…", fontSize = 11.5.sp, color = t.inkSoft)
            }

            s == null -> {
                if (ui.syllabusDetailMsg.isNotBlank()) {
                    Text(
                        ui.syllabusDetailMsg,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = t.inkSoft,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                if (ui.syllabusCandidates.isNotEmpty()) {
                    DetailSection("这本册子里的同名课程") {
                        ui.syllabusCandidates.forEach { c ->
                            CandidateRow(c) { vm.openSyllabusById(c.id) }
                        }
                    }
                }
            }

            else -> SyllabusBody(s, ui.syllabusDetailMsg)
        }
    }
}

/** 候选行：框里不再套小卡，只用一条行线与右箭「›」表达可选 */
@Composable
private fun CandidateRow(entry: SyllabusIndexEntry, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                listOf(entry.code, entry.type, entry.creditHoursLabel)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 9.5.sp,
                fontFamily = NumericFont,
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
        Text(
            msg,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = t.inkSoft,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    // 顶部事实：8 条挤成两列会读错行，改成一行一条（框里有的是纵向空间）
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
        DetailSection("课程信息") {
            facts.forEach { (k, v) ->
                DetailFieldRow(k, v, valueMono = k.contains("学时") || k.contains("学分") || k.contains("时间"))
            }
        }
    }

    if (s.goals.isNotEmpty()) {
        DetailSection("课程目标") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                s.goals.forEach { g ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            if (g.no.isNotBlank()) "${g.no}." else "·",
                            fontSize = 10.sp,
                            color = t.inkFaint,
                            fontFamily = NumericFont,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            g.content,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp,
                            color = t.inkSoft,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    if (s.sections.isNotEmpty()) {
        DetailSection("内容章节") {
            s.sections.forEachIndexed { i, sec ->
                val open = i in expanded
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = if (open) expanded - i else expanded + i }
                        .padding(vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            sec.title,
                            fontSize = 12.sp,
                            color = t.ink,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(if (open) "▾" else "▸", fontSize = 11.sp, color = t.inkFaint)
                    }
                    if (open) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            prettySectionText(sec.text).ifBlank { "原文这一节为空" },
                            fontSize = 12.sp,
                            lineHeight = 20.sp,
                            color = if (sec.text.isBlank()) t.inkFaint else t.inkSoft,
                        )
                    } else if (sec.text.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            sec.text.take(48) + "…",
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            color = t.inkFaint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (s.chapters.isNotEmpty()) {
        DetailSection("教学章节") {
            MiniTable(
                title = "",
                headers = listOf("单位", "条目", "说明"),
                rows = s.chapters.map { listOf(it.unit, it.title, it.note) },
            )
        }
    }
    if (s.hoursTable.isNotEmpty()) {
        DetailSection("教学内容学时一览表") {
            MiniTable(
                title = "",
                headers = listOf("教学内容", "线上", "线下", "实验", "合计"),
                rows = s.hoursTable.map { listOf(it.content, it.online, it.offline, it.lab, it.total) },
                footer = s.hoursTotal?.let { "合计 $it 学时（原表有空白行时不显示合计）" } ?: "暂缺",
            )
        }
    }

    if (s.basicInfo.isNotEmpty()) {
        DetailSection("课程基本信息") {
            Row(
                Modifier.fillMaxWidth().clickable { openBasic = !openBasic }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "教材 / 参考书",
                    fontSize = 12.sp,
                    color = t.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(if (openBasic) "▾" else "▸", fontSize = 11.sp, color = t.inkFaint)
            }
            if (openBasic) {
                Spacer(Modifier.height(6.dp))
                s.basicInfo.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Text(k, fontSize = 10.sp, color = t.inkFaint, modifier = Modifier.width(78.dp))
                        Text(
                            v,
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            color = t.inkSoft,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text(
        "大纲来源：14 个学院《本科课程教学大纲（2021 版）》· 内置资产",
        fontSize = 9.sp,
        color = t.inkFaint,
    )
    Spacer(Modifier.height(6.dp))
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
    if (title.isNotBlank()) {
        Text(title, fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
    }
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

// 大纲原文里「一、课程基本信息」这类小节是从 PDF 直接抽的 key-value 长串：
// 「课程代码901130103开课教研室电工电子教研室课程名称…」，中间一个分隔都没有。
// 数据一个字不动，只在**显示**层按键名断行 —— 这是排版，不是改数据。
private val SECTION_KEYS = Regex(
    "(课程\\s*代码|开课\\s*教研室|课程\\s*名称|课程\\s*英文名称|英文\\s*名称|理论\\s*学时|" +
        "实验\\s*学时|总\\s*学时|学\\s*时|学\\s*分|课程\\s*类别|课程\\s*性质|选用\\s*教材|" +
        "实验\\s*教材|主要\\s*参考书|参考\\s*书|教材|执笔人|制定\\s*时间|审核人|讨论\\s*参加人|" +
        "参加人|开课\\s*单位|面向\\s*专业|适用\\s*专业)",
)

private fun prettySectionText(raw: String): String {
    if (raw.length < 80) return raw
    if (raw.count { it == '\n' } > 1) return raw
    // 一个键都没命中就别动，免得把普通叙述切碎（前半句、图表说明等）
    if (SECTION_KEYS.findAll(raw).count() < 3) return raw
    // 注意：trim() 会把行首的全角空格一起吃掉，首行就少一级缩进 —— 只去开头那个换行
    return SECTION_KEYS.replace(raw) { "\n\u3000" + it.value + " " }.removePrefix("\n")
}
