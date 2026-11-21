package cn.dkarbon.enroute.ui.curriculum

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry
import cn.dkarbon.enroute.data.curriculum.SyllabusParse
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 课程大纲（m39 重做配布）。
 *
 * ## 筛布分三层，从粗到细
 *
 * 1. **搜索框** —— 名字 / 代码，随打随筛；右侧「搜」是页面唯一的实心主色动作；
 * 2. **类型 chip 带数量** —— 单选，选中实心、未选细边框；数量直接印在标签里，不用点进去数；
 * 3. **学院横滑** —— 一次只看一个学院，换学院不重置类型筛选。
 *
 * ## 按钮形态分三种（不靠文字解释）
 *
 * · 实心填充 = 已选中的单选；
 * · 细边框 = 可点的单选；
 * · **虚线边框 = 布尔开关**（有学分 / 有页码），与单选在形态上就是两套东西。
 *
 * ## 数据全部来自内置资产
 *
 * 2066 门、14 个学院包，没有网络；只有「资产没读到」一种异常，且照实说。
 * 列表行补上 `pdf页码范围` —— 这个字段一直在资产里，之前没上屏。
 */
@Composable
fun SyllabusSearchScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    var onlyWithCredit by remember { mutableStateOf(false) }
    var onlyWithPage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.ensureSyllabusIndex() }

    val index = ui.syllabusIndex
    // 三个筛选叠起来算：类型（单选）→ 两个布尔 → 学院
    val types = remember(index) { SyllabusParse.types(index) }
    val typeCounts = remember(index) { index.groupingBy { it.type.ifBlank { "未标注" } }.eachCount() }
    val colleges = remember(index) { index.map { it.college }.distinct().sorted() }

    Column(Modifier.fillMaxSize()) {
        // ── ① 搜索 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = ui.syllabusQuery,
                onValueChange = vm::setSyllabusQuery,
                singleLine = true,
                placeholder = {
                    Text(
                        "课程名或课程代码，如 不动产 / 901040302",
                        fontSize = 12.sp, color = t.inkFaint,
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = t.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.weight(1f),
                shape = RectangleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = t.card,
                    unfocusedContainerColor = t.card,
                    focusedIndicatorColor = t.primary,
                    unfocusedIndicatorColor = t.outline,
                    cursorColor = t.primary,
                ),
            )
            Spacer(Modifier.width(Space.xs))
            // 唯一的实心主色动作
            Box(
                Modifier.heightIn(min = 44.dp).clip(RectangleShape)
                    .background(t.primary).clickable { }
                    .padding(horizontal = Space.m),
                contentAlignment = Alignment.Center,
            ) { Text("搜", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.paper) }
        }

        // ── ② 类型（单选，带数量）+ 布尔（虚线）──
        LazyRow(
            Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
            contentPadding = PaddingValues(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item(key = "all") {
                TypeChip(
                    label = "全部 ${index.size}",
                    selected = ui.syllabusType.isBlank(),
                    onClick = { vm.setSyllabusType("") },
                )
            }
            items(types, key = { "t-$it" }) { ty ->
                TypeChip(
                    label = "$ty ${typeCounts[ty] ?: 0}",
                    selected = ui.syllabusType == ty,
                    onClick = { vm.setSyllabusType(ty) },
                )
            }
            item(key = "c1") {
                BoolChip("有学分", onlyWithCredit) { onlyWithCredit = !onlyWithCredit }
            }
            item(key = "c2") {
                BoolChip("有页码", onlyWithPage) { onlyWithPage = !onlyWithPage }
            }
        }

        // ── ③ 学院横滑 ──
        if (colleges.size > 1) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "c-all") {
                    // 「全部」= 清空多选；选中态看「一个都没选」
                    TypeChip(
                        label = "全部",
                        selected = ui.syllabusColleges.isEmpty(),
                        onClick = { vm.clearSyllabusColleges() },
                        small = true,
                    )
                }
                items(colleges, key = { "c-$it" }) { c ->
                    // 多选：每个学院各自独立开关，选几个都行
                    TypeChip(
                        label = c,
                        selected = c in ui.syllabusColleges,
                        onClick = { vm.toggleSyllabusCollege(c) },
                        small = true,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = ui.syllabusList
            Text(
                when {
                    ui.syllabusIndexLoading -> "正在读大纲索引…"
                    ui.syllabusIndexMsg.isNotBlank() -> ui.syllabusIndexMsg
                    else -> "共 ${index.size} 门 · 当前 ${shown.size} 门"
                },
                fontSize = 10.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
                modifier = Modifier.weight(1f),
            )
        }

        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

        // ── 列表 ──
        val rows = remember(ui.syllabusList, onlyWithCredit, onlyWithPage) {
            ui.syllabusList.filter { e ->
                (!onlyWithCredit || e.creditHoursLabel.isNotBlank()) &&
                    (!onlyWithPage || e.pageRangeLabel.isNotBlank())
            }
        }
        // 打成一片的逐键筛选：停手 600 ms 才记一条，否则一次搜索会发十几次
        LaunchedEffect(ui.syllabusQuery, rows.size) {
            if (ui.syllabusQuery.isBlank()) return@LaunchedEffect
            kotlinx.coroutines.delay(600)
            cn.dkarbon.enroute.data.telemetry.Telemetry.record(
                "syllabus.search",
                mapOf(
                    "qlen" to cn.dkarbon.enroute.data.telemetry.Telemetry.bucketQueryLength(ui.syllabusQuery.length),
                    "results" to cn.dkarbon.enroute.data.telemetry.Telemetry.bucketResults(rows.size),
                ),
            )
        }
        if (rows.isEmpty() && !ui.syllabusIndexLoading && ui.syllabusIndexMsg.isBlank()) {
            Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
                Text(
                    "没有匹配的课程。\n内置大纲收录 14 个学院 2021 版共 ${index.size} 门，" +
                        "通识课与其它年级的课不在其中。",
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    color = t.inkSoft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l, end = Space.l, top = Space.s, bottom = Space.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items(rows, key = { it.id }) { e ->
                    EntryRow(e) { vm.openSyllabusById(e.id) }
                }
                item(key = "src") {
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "大纲来源：14 个学院《本科课程大纲（2021 版）》共 14 册 11,146 页 · " +
                            "内置资产收录 ${index.size} 门 · 通识课（形势与政策、社会主义发展史等）不在此册内",
                        fontSize = 9.5.sp,
                        lineHeight = 14.sp,
                        color = t.inkFaint,
                    )
                }
            }
        }
    }
}

/** 单选 chip：选中实心主色，未选细边框 */
@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit, small: Boolean = false) {
    val t = InkTokens.current
    Box(
        Modifier.heightIn(min = if (small) 30.dp else 32.dp).clip(RectangleShape)
            .background(if (selected) t.primary else t.card)
            .border(if (selected) 1.5.dp else Stroke.hairline, if (selected) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s + 2.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (small) 10.sp else 10.5.sp,
            color = if (selected) t.paper else t.inkSoft,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 布尔 chip：虚线边框 —— 形态上与「单选」分家，不靠文字解释 */
@Composable
private fun BoolChip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    val primaryColor = t.primary
    val outlineColor = t.outline
    Box(
        Modifier.heightIn(min = 32.dp).clip(RectangleShape)
            .background(if (on) t.cardAlt else t.card)
            // 虚线边框：用 Canvas 自己画（BorderStroke 的 PathEffect 重载在 1.x 里没有）
            .drawBehind {
                val c = if (on) primaryColor else outlineColor
                val stroke = 1.dp.toPx()
                val dash = 4.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(c, Offset(x, 0f), Offset(minOf(x + dash, size.width), 0f), stroke)
                    drawLine(c, Offset(x, size.height), Offset(minOf(x + dash, size.width), size.height), stroke)
                    x += dash * 2
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(c, Offset(0f, y), Offset(0f, minOf(y + dash, size.height)), stroke)
                    drawLine(c, Offset(size.width, y), Offset(size.width, minOf(y + dash, size.height)), stroke)
                    y += dash * 2
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = Space.s + 2.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            color = if (on) t.ink else t.inkSoft,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun EntryRow(e: SyllabusIndexEntry, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .clickable(onClickLabel = "打开 ${e.name} 的大纲", onClick = onClick)
            .padding(horizontal = Space.m, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, fontSize = 13.sp, color = t.ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.type.isNotBlank()) {
                    Box(
                        Modifier.background(t.cardAlt).padding(horizontal = 4.dp, vertical = 1.dp),
                    ) { Text(e.type, fontSize = 9.sp, color = t.inkSoft) }
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    listOf(e.code, e.creditHoursLabel)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 9.5.sp,
                    color = t.inkFaint,
                    fontFamily = NumericFont,
                )
                Spacer(Modifier.weight(1f))
                // PDF 页码：一直躺在资产里，之前没上屏；没有就留空，不猜
                if (e.pageRangeLabel.isNotBlank()) {
                    Text(
                        e.pageRangeLabel,
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.xs))
        Text("›", fontSize = 15.sp, color = t.inkFaint)
    }
}