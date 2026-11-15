package cn.dkarbon.enroute.ui.curriculum

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.curriculum.CurriculumCourse
import cn.dkarbon.enroute.data.curriculum.CurriculumMeta
import cn.dkarbon.enroute.data.curriculum.CurriculumParse
import cn.dkarbon.enroute.data.curriculum.CurriculumPlan
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 培养方案页（票 09）—— 数据来自**内置资产**，全程离线。
 *
 * 三段式：身份头（年级 + 专业，可切换）→ 四个分区（介绍 / 目标与要求 / 学分结构 / 课程设置）→ 来源脚注。
 *
 * ⚠️ 信用纪律（`META.json` 原文）：**学分只认「学分结构表」**。
 * 课程行求和会虚高（实测 82 份有总学分行方案的求和全部高于总学分，最多高 52 学分），
 * 所以这一页**不显示任何课程学分合计**，学分结构表缺行时显示「暂缺」而不是推算值。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurriculumScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var pickingMajor by remember { mutableStateOf(false) }

    // 首次进入读一次（按需读索引 + 本人专业那一个文件，不预加载全部）
    LaunchedEffect(Unit) {
        if (ui.planIndex == null && !ui.planLoading) vm.openCurriculum()
    }

    Column(Modifier.fillMaxSize()) {
        // ── 头顶：年级 · 专业 · 培养类型 ──
        Row(
            Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val head = listOf(
                    if (ui.planYear.isNotBlank()) "${ui.planYear} 级" else "",
                    ui.planMajor,
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    head.ifBlank { "培养方案" },
                    fontSize = 15.sp,
                    color = t.ink,
                    fontWeight = FontWeight.Bold,
                )
                val sub = listOfNotNull(
                    ui.plan?.type?.takeIf { it.isNotBlank() },
                    ui.plan?.courses?.size?.takeIf { it > 0 }?.let { "$it 门课程" },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(sub, fontSize = 10.5.sp, color = t.inkSoft)
                }
            }
            Box(
                Modifier
                    .clip(RectangleShape)
                    .border(Stroke.hairline, t.outline)
                    .clickable { pickingMajor = !pickingMajor }
                    .padding(horizontal = Space.m, vertical = 7.dp),
            ) {
                Text(if (pickingMajor) "收起" else "换专业", fontSize = 11.sp, color = t.primary)
            }
        }

        // ── 版本年份 ──
        val years = ui.planIndex?.years.orEmpty()
        if (years.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(years, key = { it }) { y ->
                    Chip(
                        label = "$y 版",
                        selected = y == ui.planYear,
                        onClick = { vm.selectPlanYear(y) },
                    )
                }
            }
        }

        // ── 培养类型（同专业多条时才出现：普通班 / 实验班）──
        if (ui.planEntries.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(ui.planEntries, key = { it.id }) { e ->
                    Chip(
                        label = e.type.ifBlank { e.id },
                        selected = e.id == ui.planEntryId,
                        onClick = { vm.selectPlanMajor(e.id) },
                    )
                }
            }
        }

        // ── 专业选择面板（定位不到时自动展开由 planMsg 提示）──
        if (pickingMajor) {
            val majors = ui.planIndex?.majors(ui.planYear).orEmpty()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(horizontal = Space.l)) {
                items(majors, key = { it }) { m ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val entry = ui.planIndex?.entries(ui.planYear)
                                    ?.firstOrNull { it.major == m }
                                if (entry != null) {
                                    vm.selectPlanMajor(entry.id)
                                    pickingMajor = false
                                }
                            }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            m,
                            fontSize = 12.sp,
                            color = if (m == ui.planMajor) t.primary else t.inkSoft,
                            fontWeight = if (m == ui.planMajor) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (m == ui.planMajor) Text("当前", fontSize = 9.5.sp, color = t.inkFaint)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        }

        // ── 分区 tab ──
        LazyRow(
            Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
            contentPadding = PaddingValues(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(TABS.indices.toList(), key = { it }) { i ->
                Chip(label = TABS[i], selected = i == tab, onClick = { tab = i })
            }
        }

        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

        // ── 内容 ──
        val plan = ui.plan
        when {
            ui.planLoading -> CenterHint("正在读培养方案…")
            plan == null -> CenterHint(
                ui.planMsg.ifBlank { "没读到培养方案。安装包可能不完整，或这一年的资产缺失" },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l, end = Space.l, top = Space.m, bottom = Space.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (ui.planMsg.isNotBlank()) {
                    item(key = "msg") { Note(ui.planMsg) }
                }
                when (tab) {
                    0 -> introItems(plan)
                    1 -> goalItems(plan)
                    2 -> creditItems(plan)
                    else -> courseItems(plan, ui, vm)
                }
                item(key = "src") {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        CurriculumMeta.sourceLine(ui.planYear, plan),
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        color = t.inkFaint,
                    )
                }
            }
        }
    }
}

private val TABS = listOf("专业介绍", "目标与要求", "学分结构", "课程设置")

// ── 各区内容（LazyListScope 扩展，保持主函数短）────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.introItems(plan: CurriculumPlan) {
    item(key = "intro") {
        if (plan.intro.isNotBlank()) {
            SectionCard("专业介绍", plan.intro)
        } else {
            SectionCard("专业介绍", "原文这一节为空")
        }
    }
    item(key = "facts") {
        val facts = listOf(
            "主干学科" to plan.mainDiscipline,
            "专业核心课程" to plan.coreCourses,
            "主要实践性教学环节" to plan.practice,
            "修业年限" to plan.duration,
            "授予学位" to plan.degree,
        ).filter { it.second.isNotBlank() }
        Column(
            Modifier.fillMaxWidth().background(InkTokens.current.card)
                .border(Stroke.hairline, InkTokens.current.outline)
                .padding(horizontal = Space.s, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (facts.isEmpty()) {
                Text("这几项原文为空", fontSize = 11.sp, color = InkTokens.current.inkFaint)
            }
            facts.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        k,
                        fontSize = 10.5.sp,
                        color = InkTokens.current.inkFaint,
                        modifier = Modifier.width(84.dp),
                    )
                    Text(
                        v,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = InkTokens.current.inkSoft,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.goalItems(plan: CurriculumPlan) {
    item(key = "goals") {
        SectionCard("培养目标", plan.goals.ifBlank { "原文这一节为空" })
    }
    items(plan.goalList, key = { "goal-" + it.no + it.content.take(10) }) { g ->
        Bullet(if (g.no.isNotBlank()) "${g.no}. ${g.content}" else g.content)
    }
    item(key = "req") {
        SectionCard("毕业要求", plan.requirements.ifBlank { "原文这一节为空" })
    }
    items(plan.requirementList, key = { "req-" + it.no + it.text.take(10) }) { r ->
        RequirementBlock(r)
    }
    if (plan.notes.isNotEmpty()) {
        items(plan.notes, key = { it.first }) { (k, v) -> SectionCard(k, v) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.creditItems(plan: CurriculumPlan) {
    item(key = "min") {
        val min = CurriculumParse.minCredits(plan)
        val t = InkTokens.current
        Column(
            Modifier.fillMaxWidth().background(t.cardAlt)
                .border(1.dp, t.outline)
                .padding(horizontal = Space.m, vertical = Space.m),
        ) {
            if (min.isNotBlank()) {
                Text("最低毕业学分  $min", fontSize = 16.sp, color = t.ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "取自下方「学分结构表」的『总学分』行 —— 不是课程行求和",
                    fontSize = 10.sp,
                    color = t.inkFaint,
                )
            } else if (CurriculumParse.creditTableMissing(plan)) {
                Text("学分结构表暂缺", fontSize = 14.sp, color = t.ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "这份 PDF 里这一页的表格没能提取出来。按数据诚实规则，这里不显示推算值" +
                        "（课程设置里列的是全部候选课，求和会虚高）",
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = t.inkFaint,
                )
            } else {
                Text("学分结构表里没有『总学分』行", fontSize = 14.sp, color = t.ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "原表的这一行没被提取到。同样不显示推算值，避免把选修候选课的学分算进应修学分",
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = t.inkFaint,
                )
            }
        }
    }
    if (plan.creditTable.isNotEmpty()) {
        item(key = "ct") { TableBlock("学分结构表", plan.creditTable) }
    }
    if (plan.semesterTable.isNotEmpty()) {
        item(key = "st") { TableBlock("各学期学分分配表", plan.semesterTable) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.courseItems(
    plan: CurriculumPlan,
    ui: AppState,
    vm: TimetableViewModel,
) {
    CurriculumParse.coursesByTerm(plan).forEach { (term, list) ->
        item(key = "term-$term") {
            Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    CurriculumParse.termLabel(term),
                    fontSize = 12.sp,
                    color = InkTokens.current.ink,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${list.size} 门",
                    fontSize = 9.5.sp,
                    color = InkTokens.current.inkFaint,
                    fontFamily = NumericFont,
                )
            }
        }
        items(list, key = { term + "|" + it.code + "|" + it.name }) { c ->
            // 点某门课 → 打开课程大纲浮层（票 10）。有无大纲、命中多条都在浮层里如实说
            CourseRow(c) { vm.openSyllabus(c.code, c.name) }
        }
    }
    if (plan.courses.isEmpty()) {
        item(key = "empty") { Note("这份方案里没提取到课程行（原文该节可能是表格图片）") }
    }
}

// ── 组件 ────────────────────────────────────────────────────

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
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
            fontSize = 11.5.sp,
            color = if (selected) t.ink else t.inkSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = InkTokens.current.inkSoft,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxWidth().background(t.card).border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 11.sp, lineHeight = 18.sp, color = t.inkSoft)
    }
}

@Composable
private fun Bullet(text: String) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text("·", fontSize = 11.sp, color = t.inkFaint, modifier = Modifier.width(10.dp))
        Text(text, fontSize = 11.sp, lineHeight = 18.sp, color = t.inkSoft, modifier = Modifier.weight(1f))
    }
}

/** 一条毕业要求：编号 + 正文 + 指标点（1.1 / 1.2 …） */
@Composable
private fun RequirementBlock(r: cn.dkarbon.enroute.data.curriculum.PlanRequirement) {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxWidth().background(t.card).border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (r.no.isNotBlank()) "要求 ${r.no}" else "要求",
            fontSize = 11.sp,
            color = t.ink,
            fontWeight = FontWeight.Bold,
        )
        Text(r.text, fontSize = 11.sp, lineHeight = 18.sp, color = t.inkSoft)
        r.indicators.forEach { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    (p.no.ifBlank { "指标" }),
                    fontSize = 9.5.sp,
                    color = t.inkFaint,
                    fontFamily = NumericFont,
                    modifier = Modifier.width(38.dp),
                )
                Text(
                    p.content,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = t.inkFaint,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    val t = InkTokens.current
    Text(
        text,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
        color = t.inkSoft,
        modifier = Modifier.fillMaxWidth().background(t.cardAlt)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.s, vertical = 8.dp),
    )
}

/**
 * 原表渲染：保留行列，横向可滚（表头随表一起滚）。
 *
 * 表格类硬要求（票 09 复审）：
 * 1. 列宽固定（同一个 `j → 列宽` 映射同时用于表头与数据，不会写两套）
 * 2. 单元格可换行，长文最多 3 行再省略（表头放宽到 4 行，防止长表头被吃）
 * 3. 数字列右对齐、文字列左对齐
 * 4. 空值原样（空格子=原表为空，绝不填 0）
 */
@Composable
private fun TableBlock(title: String, rows: List<List<String>>) {
    val t = InkTokens.current
    val cols = rows.maxOfOrNull { it.size } ?: 0
    if (rows.isEmpty() || cols <= 0) return
    val headerRows = leadingHeaderRows(rows)
    val numeric = BooleanArray(cols) { j -> isNumericColumn(rows, headerRows, j) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                rows.forEachIndexed { i, row ->
                    Row {
                        for (j in 0 until cols) {
                            val cell = row.getOrElse(j) { "" }.trim()
                            val isHeader = i < headerRows
                            val right = numeric[j]
                            Box(
                                Modifier
                                    .width(columnWidth(j, right))
                                    .heightIn(min = 30.dp)
                                    .background(if (isHeader) t.cardAlt else t.card)
                                    .border(Stroke.hairline, t.outline)
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    cell,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    color = if (isHeader) t.ink else t.inkSoft,
                                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = if (right) TextAlign.End else TextAlign.Start,
                                    maxLines = if (isHeader) 4 else 3,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
        // 表头按原文照排（学分结构表两行、各学期学分分配表一行）；空格子=原表为空
        Text(
            "表头按原文照排；空格子=原表为空",
            fontSize = 9.sp,
            color = t.inkFaint,
        )
    }
}

/** 表头行数：以「教育环节 / 学期」开头，或首格为空的续行；碰到第一行数据就停 */
private fun leadingHeaderRows(rows: List<List<String>>): Int {
    var n = 0
    for (row in rows) {
        val first = row.firstOrNull()?.trim().orEmpty()
        if (first.isEmpty() || first == "教育环节" || first == "学期") n++ else break
    }
    return n.coerceAtLeast(1)
}

/** 这一列是不是数字列：表头以下所有非空单元格都是数字 / 百分比 / 破折号 */
private fun isNumericColumn(rows: List<List<String>>, headerRows: Int, j: Int): Boolean {
    var seen = false
    for (i in headerRows until rows.size) {
        val c = rows[i].getOrElse(j) { "" }.trim()
        if (c.isEmpty() || c == "—" || c == "——") continue
        seen = true
        if (!CELL_NUMBER.matches(c)) return false
    }
    return seen
}

/** 列宽：首列放标签、数字列窄、其余文字列中等 —— 全部固定宽，长文换行不撑列 */
private fun columnWidth(j: Int, numeric: Boolean): Dp = when {
    numeric -> 56.dp
    j == 0 -> 88.dp
    else -> 72.dp
}

private val CELL_NUMBER = Regex("""^\d+(\.\d+)?%?$""")

/** 课程行：`课程代码 · 课程名` / `学分 · 学时 · 性质`；点击查大纲（票 10 入口） */
@Composable
private fun CourseRow(c: CurriculumCourse, onClick: () -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .clickable(onClickLabel = "查看 ${c.name} 的课程大纲", onClick = onClick)
            .padding(horizontal = Space.s, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.name.ifBlank { "（课程名原文为空）" },
                fontSize = 12.sp,
                color = t.ink,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (c.code.isNotBlank()) {
                Text(c.code, fontSize = 9.sp, color = t.inkFaint, fontFamily = NumericFont)
            }
        }
        val meta = listOf(
            if (c.credit.isNotBlank()) "${c.credit} 学分" else "",
            if (c.hours.isNotBlank()) "${c.hours} 学时" else "",
            // 性质原文为空 → 照实说「未注明」，不拿课程类别冒充
            if (c.nature.isNotBlank()) c.nature else "性质未注明",
        ).filter { it.isNotBlank() }.joinToString(" · ")
        Text(meta, fontSize = 10.sp, color = t.inkFaint)
    }
}
