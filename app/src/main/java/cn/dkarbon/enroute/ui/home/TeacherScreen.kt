package cn.dkarbon.enroute.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 查教师。
 *
 * ⚠️ 这里给的是**教师条目**（姓名 / 院系 / 职称 / 工号）。
 * **点某一行会新开「教师详情页」**（课表 + 学院官网公开资料，见 [TeacherDetailScreen]），
 * 所以这一行是可点的，右侧画一个 `›` 明示可进。
 */
@Composable
fun TeacherScreen(
    ui: AppState,
    vm: TimetableViewModel,
    onOpenTeacher: (TeacherHit) -> Unit = {},
) {
    val t = InkTokens.current

    Column(Modifier.fillMaxSize()) {
        // ── 搜索框 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = ui.teacherQuery,
                onValueChange = vm::setTeacherQuery,
                singleLine = true,
                placeholder = { Text("教师姓名，如 董", fontSize = 13.sp, color = t.inkFaint) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = t.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchTeachers() }),
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
            Spacer(Modifier.width(Space.s))
            TextButton(onClick = { vm.searchTeachers() }, enabled = !ui.teacherLoading) {
                Text(if (ui.teacherLoading) "搜索中" else "搜索", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 结果/错误提示。空列表时这段由下面的空态承担，免得同一句话出现两遍
        if (ui.teacherMsg.isNotBlank() && ui.teachers.isNotEmpty()) {
            Text(
                ui.teacherMsg,
                fontSize = 11.sp,
                color = t.inkSoft,
                modifier = Modifier.padding(horizontal = Space.l),
            )
            Spacer(Modifier.height(Space.s))
        }

        if (ui.teachers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 还没搜过就什么都不说：「没找到」是检索结论，不能提前替用户下
                    val emptyLine = when {
                        ui.teacherLoading -> "搜索中…"
                        ui.teacherMsg.isNotBlank() -> ui.teacherMsg
                        else -> ""
                    }
                    if (emptyLine.isNotBlank()) {
                        Text(
                            emptyLine,
                            fontSize = 12.sp,
                            color = t.inkFaint,
                            lineHeight = 19.sp,
                        )
                        Spacer(Modifier.height(Space.m))
                    }
                    Text(
                        "只在校内网可用 · 数据来自教务系统教师课表",
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Space.l, end = Space.l, bottom = Space.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                // key 用「工号 + 学期 + 序号」三件套：
                // 只用工号曾经直接崩过（票 01：同一教师在两个学期各返回一条 →
                // `Key "1995500023" was already used`）。解析层已经去重，这里再加序号兜底：
                // 万一将来又冒出重复，最坏是这一行重绘，而不是整个进程闪退。
                itemsIndexed(
                    ui.teachers,
                    key = { i, hit -> "${hit.number}|${hit.term}|$i" },
                ) { _, hit ->
                    TeacherRow(hit, onClick = { onOpenTeacher(hit) })
                }
            }
        }
    }
}

@Composable
private fun TeacherRow(t: TeacherHit, onClick: () -> Unit) {
    val ink = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(ink.card)
            .border(Stroke.hairline, ink.outline)
            .clickable(onClickLabel = "查看 ${t.name} 的课表与官网资料", onClick = onClick)
            .padding(horizontal = Space.m, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                t.name.ifBlank { "（未署名）" },
                fontSize = 14.sp,
                color = ink.ink,
                fontWeight = FontWeight.Medium,
            )
            val line = HomeModel.teacherLine(t)
            if (line.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(line, fontSize = 10.5.sp, color = ink.inkFaint)
            }
        }
        if (t.number.isNotBlank()) {
            Text(
                t.number,
                fontSize = 10.sp,
                color = ink.inkFaint,
                fontFamily = NumericFont,
            )
            Spacer(Modifier.width(Space.xs))
        }
        // 可点的显式提示：没有这个箭头，用户根本不会想到能点进去（主人就是这么反馈的）
        Text("›", fontSize = 16.sp, color = ink.inkFaint)
    }
}
