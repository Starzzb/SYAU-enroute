package cn.dkarbon.enroute.ui.custom

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.custom.CustomCourse
import cn.dkarbon.enroute.data.custom.CustomRule
import cn.dkarbon.enroute.data.custom.CustomRules
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import java.time.LocalDate

/**
 * 自定义课程（票 19）：列表 + 添加/编辑。
 *
 * ## 「清晰易懂」怎么落的（票面要求，一条条对上）
 * 1. **先选规则类型**：第一步只问「按周次」还是「固定某一天」，两条路之后才进表单 ——
 *    不把两套字段塞一屏（[RuleKindStep]）；
 * 2. **必填只有课程名**：其余（地点/教师/备注）都可空；规则有默认值（第 1–16 周 · 周一 · 第 1-2 节）；
 * 3. **一句人话预览**随填随变（`CustomRules.preview`）：`第 3–16 周 · 每周二 · 第 5-6 节`；
 * 4. **周次快捷档**：1–16 / 1–8 / 9–16 一键填，仍可手改；
 * 5. **冲突即时提示**：选中的格子里已有课 → 「该时段已有《XX》，两门都会显示」；
 * 6. **＋ 再加一个时间**：同一门课可以挂多条规则（前八周周二 + 后八周周四）。
 *
 * 编辑/删除：列表行上直接给「编辑 / 删除」（不新增隐藏手势）。
 */
@Composable
fun CustomCourseScreen(ui: AppState, vm: TimetableViewModel) {
    if (ui.customEditorOpen) {
        CustomCourseEditor(ui = ui, vm = vm)
    } else {
        CustomCourseList(ui = ui, vm = vm)
    }
}

// ── 列表 ────────────────────────────────────────────────────

@Composable
private fun CustomCourseList(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.l)) {
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "自己加的课 · ${ui.customCourses.size} 门",
                fontSize = 13.sp,
                color = t.ink,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            SmallButton("＋ 新增") { vm.startAddCustom() }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "只存在本机，不上传、不改教务。网格里带「自」角标的就是这些课。",
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = t.inkFaint,
        )
        if (ui.customMsg.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(ui.customMsg, fontSize = 10.sp, color = t.primary)
        }
        Spacer(Modifier.height(Space.m))

        if (ui.customCourses.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().border(Stroke.hairline, t.outline).padding(Space.l),
            ) {
                Text(
                    "还没有自定义课。\n"+ "可以按周次排（第 3–16 周），也可以只排某一天。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = t.inkFaint,
                )
            }
        } else {
            ui.customCourses.forEach { c ->
                Column(
                    Modifier.fillMaxWidth().padding(bottom = Space.s)
                        .border(Stroke.hairline, t.outline).padding(Space.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, fontSize = 13.sp, color = t.ink, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(Space.xs))
                        // 与网格里的角标同一个字，便于对应
                        Text(
                            "自",
                            fontSize = 8.sp,
                            color = t.inkFaint,
                            modifier = Modifier.border(Stroke.hairline, t.outline)
                                .padding(horizontal = 2.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton2("编辑") { vm.editCustom(c.id) }
                        TextButton2("删除", danger = true) { confirmDelete = c.id }
                    }
                    val meta = listOf(c.teacher, c.room).filter { it.isNotBlank() }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, fontSize = 10.sp, color = t.inkSoft)
                    }
                    c.rules.forEach { r ->
                        Text("· " + CustomRules.preview(r), fontSize = 10.sp, color = t.inkSoft, fontFamily = NumericFont)
                    }
                    if (c.note.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        // 备注**完整显示**，不截断（票面硬规矩 4）
                        Text("备注：" + c.note, fontSize = 10.sp, lineHeight = 15.sp, color = t.inkFaint)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
    }

    confirmDelete?.let { id ->
        val name = ui.customCourses.firstOrNull { it.id == id }?.name.orEmpty()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除《$name》？", fontSize = 14.sp) },
            text = { Text("删除后网格里就不再出现；教务课不受影响。", fontSize = 12.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.deleteCustom(id)
                    confirmDelete = null
                }) { Text("删除", fontSize = 12.sp) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) {
                    Text("取消", fontSize = 12.sp)
                }
            },
        )
    }
}

// ── 编辑页 ──────────────────────────────────────────────────

/** 第一步：只问规则类型（两条路分开，不混一屏） */
@Composable
private fun RuleKindStep(onWeekly: () -> Unit, onSingle: () -> Unit) {
    val t = InkTokens.current
    Column(Modifier.fillMaxSize().padding(horizontal = Space.l)) {
        Spacer(Modifier.height(Space.s))
        Text("这门课怎么排？", fontSize = 15.sp, color = t.ink, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "先选一种；之后还能「再加一个时间」（比如前八周周二、后八周周四）。",
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(Space.l))
        KindCard(
            title = "按周次重复",
            sub = "第 N–M 周 · 每周几 · 第几大节。可只在单周或双周。",
            example = "例：第 3–16 周 · 每周二 · 第 3-4 节",
            onClick = onWeekly,
        )
        Spacer(Modifier.height(Space.s))
        KindCard(
            title = "只排某一天",
            sub = "指定一个日期，只在那一周出现。",
            example = "例：2026-11-20（周五）· 第 5-6 节",
            onClick = onSingle,
        )
    }
}

@Composable
private fun KindCard(title: String, sub: String, example: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxWidth().heightIn(min = Space.minTouch)
            .border(Stroke.hairline, t.outline).clickable(onClick = onClick)
            .padding(Space.m),
    ) {
        Text(title, fontSize = 13.sp, color = t.ink, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 10.5.sp, lineHeight = 15.sp, color = t.inkSoft)
        Spacer(Modifier.height(2.dp))
        Text(example, fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
    }
}

/**
 * 表单页。
 *
 * 状态全在本地（`rememberSaveable`），点「保存」才交给 VM 落盘 —— 中途退出不留半条脏数据。
 */
@Composable
private fun CustomCourseEditor(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    val editing = ui.customEditing

    // 第一步（选类型）**只在新建时**出现：null = 还没选；true = 按周次；false = 单日
    var weeklySeed by rememberSaveable(editing?.id) {
        mutableStateOf(if (editing == null) null else editing.rules.firstOrNull() is CustomRule.Weekly)
    }
    if (editing == null && weeklySeed == null) {
        RuleKindStep(onWeekly = { weeklySeed = true }, onSingle = { weeklySeed = false })
        return
    }

    // 表单状态
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var room by rememberSaveable(editing?.id) { mutableStateOf(editing?.room.orEmpty()) }
    var teacher by rememberSaveable(editing?.id) { mutableStateOf(editing?.teacher.orEmpty()) }
    var note by rememberSaveable(editing?.id) { mutableStateOf(editing?.note.orEmpty()) }
    var rules by remember(editing?.id) {
        mutableStateOf(
            editing?.rules?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    if (weeklySeed == true) CustomRule.Weekly()
                    else CustomRule.SingleDay(date = LocalDate.now().toString()),
                ),
        )
    }

    // ── 冲突提示：与本学期教务课 + 其他自定义课比对（即时）──
    val conflicts = remember(rules, ui.timetable, ui.customCourses, editing?.id) {
        conflictsOf(rules, ui.timetable?.courses.orEmpty(), ui.customCourses, editing?.id, ui.timetable?.term.orEmpty())
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.l)) {
        Spacer(Modifier.height(Space.s))
        Text(
            if (editing == null) "新增自定义课" else "编辑《${editing.name}》",
            fontSize = 15.sp, color = t.ink, fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Space.s))

        // ① 课程名（**唯一必填**）
        FieldLabel("课程名（必填）")
        Input(name, placeholder = "如 自习 / 小组会议 / 补课") { name = it }

        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Column(Modifier.weight(1f)) {
                FieldLabel("地点（可空）")
                Input(room, placeholder = "如 2-606") { room = it }
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("教师（可空）")
                Input(teacher, placeholder = "") { teacher = it }
            }
        }

        Spacer(Modifier.height(Space.s))
        FieldLabel("备注（可空）")
        Input(note, placeholder = "比如：带笔记本 / 交作业", singleLine = false) { note = it }

        // ② 规则列表（可多条）
        Spacer(Modifier.height(Space.l))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("上课时间", fontSize = 12.sp, color = t.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
        }
        rules.forEachIndexed { i, rule ->
            Spacer(Modifier.height(Space.s))
            RuleEditor(
                index = i,
                rule = rule,
                canRemove = rules.size > 1,
                onChange = { nr -> rules = rules.toMutableList().also { it[i] = nr } },
                onRemove = { rules = rules.toMutableList().also { it.removeAt(i) } },
                hint = conflicts.firstOrNull { it.first == i }?.second.orEmpty(),
            )
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            SmallButton("＋ 按周次") {
                rules = rules + CustomRule.Weekly()
            }
            SmallButton("＋ 固定某一天") {
                rules = rules + CustomRule.SingleDay(date = LocalDate.now().toString())
            }
        }

        if (ui.customMsg.isNotBlank()) {
            Spacer(Modifier.height(Space.s))
            Text(ui.customMsg, fontSize = 10.sp, color = t.brick)
        }

        Spacer(Modifier.height(Space.l))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(
                Modifier.weight(1f).border(Stroke.hairline, t.outline)
                    .clickable { vm.closeCustomEditor() }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("取消", fontSize = 12.sp, color = t.inkSoft) }
            Box(
                Modifier.weight(1f).background(if (name.isBlank()) t.cardAlt else t.primary)
                    .clickable(enabled = name.isNotBlank()) {
                        vm.saveCustom(
                            CustomCourse(
                                id = editing?.id.orEmpty(),
                                name = name.trim(),
                                room = room.trim(),
                                teacher = teacher.trim(),
                                note = note,
                                colorKey = editing?.colorKey.orEmpty(),
                                termCode = ui.timetable?.term.orEmpty().ifBlank { editing?.termCode.orEmpty() },
                                rules = rules,
                            ),
                        )
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "保存",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (name.isBlank()) t.inkFaint else t.paper,
                )
            }
        }
        Spacer(Modifier.height(Space.xxl))
    }
}


/** 单条规则的编辑控件 + 人话预览 + 冲突提示 */
@Composable
private fun RuleEditor(
    index: Int,
    rule: CustomRule,
    canRemove: Boolean,
    onChange: (CustomRule) -> Unit,
    onRemove: () -> Unit,
    hint: String,
) {
    val t = InkTokens.current
    Column(Modifier.fillMaxWidth().border(Stroke.hairline, t.outline).padding(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("时间 ${index + 1}", fontSize = 11.sp, color = t.inkSoft, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            if (canRemove) TextButton2("移除", danger = true, onClick = onRemove)
        }
        Spacer(Modifier.height(4.dp))

        when (rule) {
            is CustomRule.Weekly -> {
                // 快捷档
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    listOf(1 to 16, 1 to 8, 9 to 16).forEach { (a, b) ->
                        val on = rule.fromWeek == a && rule.toWeek == b
                        Chip(if (a == 1 && b == 16) "1–16" else "$a–$b", on) {
                            onChange(rule.copy(fromWeek = a, toWeek = b))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Stepper("第 ${rule.fromWeek} 周") { onChange(rule.copy(fromWeek = (rule.fromWeek - 1).coerceAtLeast(1))) }
                    Stepper("到 ${rule.toWeek} 周") { onChange(rule.copy(toWeek = (rule.toWeek + 1).coerceAtMost(30))) }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    CustomRule.Parity.entries.forEach { p ->
                        val label = when (p) {
                            CustomRule.Parity.ALL -> "每周"
                            CustomRule.Parity.ODD -> "单周"
                            CustomRule.Parity.EVEN -> "双周"
                        }
                        Chip(label, rule.parity == p) { onChange(rule.copy(parity = p)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    (1..7).forEach { d ->
                        Chip(CustomRules.weekdayLabel(d), rule.weekday == d) { onChange(rule.copy(weekday = d)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    (1..6).forEach { b ->
                        Chip(CustomRules.bigLabel(b), rule.bigIndex == b) { onChange(rule.copy(bigIndex = b)) }
                    }
                }
            }

            is CustomRule.SingleDay -> {
                val date = CustomRules.parseDate(rule.date)
                Text(
                    if (date == null) "日期：未选" else "日期：$date",
                    fontSize = 11.sp,
                    color = t.inkSoft,
                    fontFamily = NumericFont,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Stepper("昨天") {
                        val d = (date ?: LocalDate.now()).minusDays(1)
                        onChange(rule.copy(date = d.toString()))
                    }
                    Stepper("明天") {
                        val d = (date ?: LocalDate.now()).plusDays(1)
                        onChange(rule.copy(date = d.toString()))
                    }
                    Stepper("今天") { onChange(rule.copy(date = LocalDate.now().toString())) }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    (1..6).forEach { b ->
                        Chip(CustomRules.bigLabel(b), rule.bigIndex == b) { onChange(rule.copy(bigIndex = b)) }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        // ③ 人话预览（随填随变）
        Text(
            CustomRules.preview(rule),
            fontSize = 11.sp,
            color = t.ink,
            fontWeight = FontWeight.Medium,
            fontFamily = NumericFont,
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 9.5.sp, lineHeight = 14.sp, color = t.brick)
        }
    }
}

/** 冲突检测：返回 (规则下标 → 提示文案) */
internal fun conflictsOf(
    rules: List<CustomRule>,
    urpCourses: List<cn.dkarbon.enroute.data.Course>,
    customs: List<CustomCourse>,
    editingId: String?,
    termCode: String,
): List<Pair<Int, String>> = rules.mapIndexedNotNull { i, rule ->
    val names = if (rule is CustomRule.Weekly) {
        urpCourses.filter { c ->
            c.weekday == rule.weekday &&
                cn.dkarbon.enroute.ui.timetable.BigSession.ofCourse(c.sections).any { it.index == rule.bigIndex } &&
                (rule.fromWeek..rule.toWeek).any { w -> w in c.weeks && CustomRules.matchesWeek(rule, w) }
        }.map { it.name }
    } else {
        // 单日课：日期换算成周次后才能比；这里只能提示「同星期同大节」的教务课
        val d = (rule as CustomRule.SingleDay).let { CustomRules.parseDate(it.date) }
        if (d == null) emptyList() else urpCourses.filter { c ->
            c.weekday == d.dayOfWeek.value &&
                cn.dkarbon.enroute.ui.timetable.BigSession.ofCourse(c.sections).any { it.index == rule.bigIndex }
        }.map { it.name }
    }
    val otherCustom = customs.filter { it.id != editingId && it.rules.any { r -> sameSlot(r, rule) } }.map { it.name }
    val hint = CustomRules.conflictHint(names + otherCustom)
    if (hint.isBlank()) null else i to hint
}

private fun sameSlot(a: CustomRule, b: CustomRule): Boolean = when {
    a is CustomRule.Weekly && b is CustomRule.Weekly -> a.weekday == b.weekday && a.bigIndex == b.bigIndex
    a is CustomRule.SingleDay && b is CustomRule.SingleDay ->
        a.date == b.date && a.bigIndex == b.bigIndex
    else -> false
}

// ── 小控件 ──────────────────────────────────────────────────

@Composable
private fun FieldLabel(text: String) {
    val t = InkTokens.current
    Text(text, fontSize = 9.5.sp, color = t.inkFaint, modifier = Modifier.padding(bottom = 3.dp))
}

@Composable
private fun Input(
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    val t = InkTokens.current
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder, fontSize = 11.sp, color = t.inkFaint) },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = t.ink),
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = t.paper,
            unfocusedContainerColor = t.paper,
            focusedIndicatorColor = t.primary,
            unfocusedIndicatorColor = t.outline,
            cursorColor = t.primary,
        ),
    )
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier.heightIn(min = 30.dp).background(if (on) t.cardAlt else t.card)
            .border(if (on) 1.5.dp else Stroke.hairline, if (on) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.sp, color = if (on) t.ink else t.inkSoft, fontFamily = NumericFont)
    }
}

@Composable
private fun Stepper(label: String, onClick: () -> Unit) {
    Chip(label, on = false, onClick = onClick)
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier.clip(RectangleShape).border(Stroke.hairline, t.outline).background(t.card)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(label, fontSize = 10.5.sp, color = t.inkSoft) }
}

@Composable
private fun TextButton2(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
    ) { Text(label, fontSize = 11.sp, color = if (danger) t.brick else t.inkSoft) }
}


