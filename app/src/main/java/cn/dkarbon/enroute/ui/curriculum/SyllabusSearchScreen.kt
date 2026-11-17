package cn.dkarbon.enroute.ui.curriculum

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
 * 课程大纲搜索页（票 10 的第二处入口）—— 按名称/代码搜、按类型筛。
 *
 * 数据是**内置资产**（土环学院 2021 版，166 门），所以没有网络、没有加载失败态；
 * 只有「资产没读到」一种异常，且照实说。
 *
 * 点一行 → 打开共用的 [SyllabusSheet] 浮层（不新开页，看完能立刻回列表点下一门）。
 */
@Composable
fun SyllabusSearchScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current

    LaunchedEffect(Unit) { vm.ensureSyllabusIndex() }

    Column(Modifier.fillMaxSize()) {
        // ── 搜索框 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = ui.syllabusQuery,
                onValueChange = vm::setSyllabusQuery,
                singleLine = true,
                placeholder = { Text("课程名或课程代码，如 不动产 / 901040302", fontSize = 12.sp, color = t.inkFaint) },
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
        }

        // ── 类型筛选 ──
        val types = SyllabusParse.types(ui.syllabusIndex)
        if (types.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(types, key = { it }) { ty ->
                    FilterChip(
                        label = ty,
                        selected = ui.syllabusType == ty,
                        onClick = { vm.setSyllabusType(ty) },
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    ui.syllabusIndexLoading -> "正在读大纲索引…"
                    ui.syllabusIndexMsg.isNotBlank() -> ui.syllabusIndexMsg
                    else -> "共 ${ui.syllabusIndex.size} 门 · 当前 ${ui.syllabusList.size} 门"
                },
                fontSize = 10.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
                modifier = Modifier.weight(1f),
            )
        }

        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

        // ── 列表 ──
        if (ui.syllabusList.isEmpty() && !ui.syllabusIndexLoading && ui.syllabusIndexMsg.isBlank()) {
            Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
                Text(
                    "没有匹配的课程。\n内置大纲收录 14 个学院 2021 版共 ${ui.syllabusIndex.size} 门，" +
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
                items(ui.syllabusList, key = { it.id }) { e ->
                    EntryRow(e) { vm.openSyllabusById(e.id) }
                }
                item(key = "src") {
                    Spacer(Modifier.height(Space.s))
                    SyllabusNote(
                        "大纲来源：14 个学院《本科课程大纲（2021 版）》共 14 册 11,146 页，" +
                            "内置资产收录 ${ui.syllabusIndex.size} 门 · 通识课（形势与政策、社会主义发展史等）不在此册内",
                    )
                }
            }
        }
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
            Text(
                listOf(e.code, e.creditHoursLabel, e.type)
                    .filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 9.5.sp,
                color = t.inkFaint,
            )
        }
        Text("›", fontSize = 15.sp, color = t.inkFaint)
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
