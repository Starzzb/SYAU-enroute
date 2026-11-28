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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import cn.dkarbon.enroute.data.UrpHomeApi
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 课程课表（教学资源 → 课程课表）。
 *
 * 为何要做成「先搜再点开」而不是把 2189 门一次列出来：教务这页本身就是检索页
 * （关键字塞进 `kcm` 走 LIKE），一学期两千多门既不该也不必要全量拉回手机。
 * 搜到后点一条 → [CatalogScheduleScreen] 看那张课表的周网格与明细。
 */
@Composable
fun CourseCatalogScreen(
    ui: AppState,
    vm: TimetableViewModel,
    onOpen: () -> Unit = {},
) {
    val t = InkTokens.current
    Column(Modifier.fillMaxSize()) {
        CatalogSearchField(
            query = ui.courseQuery,
            onQuery = vm::setCourseQuery,
            loading = ui.courseLoading,
            placeholder = "课程名或课程号，如 不动产",
            onSearch = vm::searchCourses,
        )
        if (ui.courseMsg.isNotBlank() && ui.courseHits.isNotEmpty()) {
            Text(
                ui.courseMsg,
                fontSize = 11.sp,
                color = t.inkSoft,
                modifier = Modifier.padding(horizontal = Space.l),
            )
            Spacer(Modifier.height(Space.s))
        }
        if (ui.courseHits.isEmpty()) {
            CatalogEmpty(
                loading = ui.courseLoading,
                msg = ui.courseMsg,
                footer = "数据来自教务课程检索 · 只在校内网可用",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                itemsIndexed(
                    ui.courseHits,
                    key = { i, hit -> "${hit.code}|${hit.term}|$i" },
                ) { _, hit ->
                    CatalogRow(
                        title = hit.name,
                        lines = listOf(
                            listOf(
                                hit.code,
                                hit.teacherName,
                                if (hit.credit.isNotBlank()) hit.credit + " 学分" else "",
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            listOf(hit.deptName, hit.category, hit.examType)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                        ),
                        onClick = {
                            vm.openCourseSchedule(hit)
                            onOpen()
                        },
                    )
                }
            }
        }
    }
}

/** 班级课表（教学资源 → 班级课表）：全校 8799 个班，同样先检索 */
@Composable
fun ClassCatalogScreen(
    ui: AppState,
    vm: TimetableViewModel,
    onOpen: () -> Unit = {},
) {
    val t = InkTokens.current
    Column(Modifier.fillMaxSize()) {
        CatalogSearchField(
            query = ui.classQuery,
            onQuery = vm::setClassQuery,
            loading = ui.classLoading,
            placeholder = "班级全名或年级，如 12动医1",
            onSearch = vm::searchClasses,
        )
        if (ui.classMsg.isNotBlank() && ui.classHits.isNotEmpty()) {
            Text(
                ui.classMsg,
                fontSize = 11.sp,
                color = t.inkSoft,
                modifier = Modifier.padding(horizontal = Space.l),
            )
            Spacer(Modifier.height(Space.s))
        }
        if (ui.classHits.isEmpty()) {
            CatalogEmpty(
                loading = ui.classLoading,
                msg = ui.classMsg,
                footer = "数据来自教务班级检索 · 只在校内网可用",
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Space.l, end = Space.l, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                itemsIndexed(
                    ui.classHits,
                    key = { i, hit -> "${hit.classNo}|${hit.planNo}|$i" },
                ) { _, hit ->
                    CatalogRow(
                        title = hit.className,
                        lines = listOf(
                            listOf(
                                if (hit.grade.isNotBlank()) hit.grade + " 级" else "",
                                hit.deptName,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            listOf(hit.subjectName, hit.planName)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                        ),
                        onClick = {
                            vm.openClassSchedule(hit)
                            onOpen()
                        },
                    )
                }
            }
        }
    }
}

/**
 * 课表详情：课程课表与班级课表共用一屏（教务返回的是同一种 `[[{slot}…]]`）。
 *
 * 渲染直接复用教师详情页那套（[WeekGrid] 周网格 + [SlotRow] 明细）——
 * 同一份数据在三个入口长一个样，比各写一套更不容易走形。
 */
@Composable
fun CatalogScheduleScreen(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    val slots = ui.catalogSchedule.slots
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.l),
    ) {
        if (ui.catalogSubtitle.isNotBlank()) {
            Text(
                ui.catalogSubtitle,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BlockTitle(
                if (slots.isEmpty()) "课表" else "周课表",
                if (slots.isEmpty()) "" else "${slots.size} 段",
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.refreshCatalogSchedule() }, enabled = !ui.catalogLoading) {
                Text(if (ui.catalogLoading) "查询中" else "重查", fontSize = 12.sp)
            }
        }
        when {
            ui.catalogLoading && slots.isEmpty() -> HintText("正在查这份课表…")
            ui.catalogMsg.isNotBlank() && slots.isEmpty() -> HintText(ui.catalogMsg)
            slots.isEmpty() -> HintText("教务没有返回排课")
            else -> {
                WeekGrid(ui.catalogSchedule)
                if (ui.catalogMsg.isNotBlank()) {
                    Spacer(Modifier.height(Space.s))
                    HintText(ui.catalogMsg)
                }
                Spacer(Modifier.height(Space.m))
                BlockTitle("明细", "${slots.size} 段")
                Spacer(Modifier.height(Space.xs))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    slots.forEach { SlotRow(it) }
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

/** 检索框：与「查教师」同款（矩形、无圆角、有底线） */
@Composable
private fun CatalogSearchField(
    query: String,
    onQuery: (String) -> Unit,
    loading: Boolean,
    placeholder: String,
    onSearch: () -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(placeholder, fontSize = 13.sp, color = t.inkFaint) },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = t.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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
        TextButton(onClick = onSearch, enabled = !loading) {
            Text(if (loading) "搜索中" else "搜索", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** 检索结果一行：标题 + 两行小字（第二行空就不占位） */
@Composable
private fun CatalogRow(title: String, lines: List<String>, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.ifBlank { "（名称未提供）" },
                fontSize = 13.sp,
                color = t.ink,
                fontWeight = FontWeight.Medium,
            )
            lines.filter { it.isNotBlank() }.forEach { line ->
                Text(line, fontSize = 10.5.sp, color = t.inkFaint, fontFamily = NumericFont)
            }
        }
        Text("›", fontSize = 16.sp, color = t.inkFaint)
    }
}

/** 空态：没搜过就什么都不说（「没找到」是结论，不能提前替用户下） */
@Composable
private fun CatalogEmpty(loading: Boolean, msg: String, footer: String) {
    val t = InkTokens.current
    Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val line = when {
                loading -> "搜索中…"
                msg.isNotBlank() -> msg
                else -> ""
            }
            if (line.isNotBlank()) {
                Text(line, fontSize = 12.sp, color = t.inkFaint, lineHeight = 19.sp)
                Spacer(Modifier.height(Space.m))
            }
            Text(footer, fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
    }
}
