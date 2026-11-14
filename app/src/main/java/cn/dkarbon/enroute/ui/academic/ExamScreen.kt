package cn.dkarbon.enroute.ui.academic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.AcademicApi
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.common.rememberListScrollState
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import java.time.LocalDateTime

/**
 * 考试安排 —— **时间轴**（2026-11-13 重做）。
 *
 * ## 为什么从「扁平卡片列表」改成时间轴
 * 旧版一屏四张一样的卡，看不出「哪场是下一场」「已经考完的混在中间」。考试这件事，
 * 用户真正要回答的是**三个问题**：下一场还有几天 / 哪天几点在哪 / 考完了吗。
 * 所以：左侧一根竖轴 + 日期节点回答「什么时候」，右侧卡片回答「哪场、在哪」，
 * 顶部一条「下一场」把倒计时提到第一眼；已结束折叠到底，不占首屏。
 *
 * ## 信息三层
 * 主：课程名 + 起止时刻 + 倒计时（一眼能读）
 * 次：校区 · 教学楼 · 教室 / 时长 / 座位（点开卡片是详情浮层）
 * 原始：完整 JSON + 接口全部 key（详情浮层的最后一段，长按直接进）
 *
 * ## 性能（按 jetpack-compose-performance 规则）
 * · `today` / `now` 在屏幕级取一次，随参数下传 —— 不在每张卡里读时钟；
 * · 派生（分桶/排序/倒计时）全在 [ExamRows] 纯函数里，`remember` 带全 key；
 * · `InkTokens` 只在屏幕级读一次，行拿到的是 `InkTokenSet` 值参数；
 * · 每个 Lazy item 都有 key + contentType（见 [ExamRow.rowKey] / [ExamRow.contentType]）。
 */
@Composable
fun ExamScreen(ui: AppState, vm: TimetableViewModel, modifier: Modifier = Modifier) {
    val t = InkTokens.current

    // 刷新一次就重取一次「现在」：考试是按时辰判「已考完」的，跨小时要准
    val stamp = remember(ui.exams, ui.examsLoading) { LocalDateTime.now() }
    val today = stamp.toLocalDate()

    var collapsed by remember { mutableStateOf(emptySet<ExamBucket>()) }
    var detail by remember { mutableStateOf<ExamItem?>(null) }
    var showRawFirst by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    // 票 26：折叠分组不算「换内容」（只是同一批数据的显隐），所以 resetKey 恒定 ——
    // 从详情浮层返回、或折叠/展开后都停在你刚才那一行。
    val listState = rememberListScrollState()

    val rows = remember(ui.exams, today, stamp, collapsed) {
        ExamRows.build(ui.exams, today, stamp, collapsed)
    }
    val stats = remember(ui.exams, today, stamp) { ExamRows.stats(ui.exams, today, stamp) }
    val label = remember(stats, collapsed) {
        buildString {
            append(ExamRows.toolbarLabel(stats))
            if (collapsed.isNotEmpty()) append(" · 已折叠 ${collapsed.size} 组")
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AcademicToolbar(
                t = t,
                label = label,
                loading = ui.examsLoading,
                onRefresh = { vm.refreshExams() },
                onMore = { showMore = true },
            )

            if (ui.exams.isEmpty()) {
                AcademicProblem(
                    t = t,
                    title = "考试安排没读出来",
                    reason = ui.examsMsg,
                    loggedIn = ui.loggedIn,
                    onRetry = { vm.refreshExams() },
                    onDiagnose = { showDiag = true },
                )
            } else {
                // 失败但旧数据还在：给一行说明，列表照旧可读（不清空、不白屏）
                if (ui.examsMsg.isNotBlank() && !ui.examsMsg.startsWith("✅")) {
                    Text(
                        ui.examsMsg,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = t.brick,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                AcademicHint(t, "点开看三层详情 · 长按直接看原始 JSON")
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
                ) {
                    val next = stats.next
                    if (next != null) {
                        item(key = "next-up", contentType = "exam-next") {
                            NextUpCard(
                                t = t,
                                exam = next,
                                daysAhead = stats.nextDays,
                                onClick = {
                                    detail = next
                                    showRawFirst = false
                                },
                            )
                        }
                    }
                    items(items = rows, key = { it.rowKey }, contentType = { it.contentType }) { row ->
                        when (row) {
                            is ExamRow.Header -> BucketHeader(
                                t = t,
                                header = row,
                                onToggle = {
                                    collapsed = if (row.bucket in collapsed) {
                                        collapsed - row.bucket
                                    } else {
                                        collapsed + row.bucket
                                    }
                                },
                            )
                            is ExamRow.Card -> TimelineRow(
                                t = t,
                                row = row,
                                onClick = {
                                    detail = row.exam
                                    showRawFirst = false
                                },
                                onLongClick = {
                                    detail = row.exam
                                    showRawFirst = true
                                },
                            )
                        }
                    }
                }
            }
        }

        detail?.let { e ->
            ExamDetailSheet(
                t = t,
                exam = e,
                showRawFirst = showRawFirst,
                onDismiss = { detail = null },
            )
        }

        if (showDiag) {
            DiagnoseSheet(
                t = t,
                ui = ui,
                vm = vm,
                paths = listOf(AcademicApi.examPathPreview()),
                onDismiss = { showDiag = false },
            )
        }

        if (showMore) {
            AcademicSheet(
                t = t,
                title = "更多",
                subtitle = "考试安排 · 数据来自校内教务（${AcademicApi.URP_HOST}）",
                onDismiss = { showMore = false },
            ) {
                SheetMenuRow(t, "重新抓取", "再打一次考试接口（需要登录态）") {
                    showMore = false
                    vm.refreshExams()
                }
                Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
                SheetMenuRow(t, "接口诊断", "看状态码、字节数和返回原文前 200 字") {
                    showMore = false
                    showDiag = true
                }
                Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
                SheetMenuRow(
                    t = t,
                    title = if (ExamBucket.PAST in collapsed) "展开已结束" else "折叠已结束",
                    detail = "考完的场次收起来后，首屏只剩要考的",
                ) {
                    showMore = false
                    collapsed = if (ExamBucket.PAST in collapsed) {
                        collapsed - ExamBucket.PAST
                    } else {
                        collapsed + ExamBucket.PAST
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
                SheetMenuRow(
                    t = t,
                    title = if (ExamBucket.LATER in collapsed) "展开更远" else "折叠更远（${stats.later} 场）",
                    detail = "下下周之后的场次先收起来",
                ) {
                    showMore = false
                    collapsed = if (ExamBucket.LATER in collapsed) {
                        collapsed - ExamBucket.LATER
                    } else {
                        collapsed + ExamBucket.LATER
                    }
                }
            }
        }
    }
}

// ── 顶部「下一场」────────────────────────────────────────

/**
 * 下一场倒计时卡。
 *
 * 主诉求「能一眼看出下一场还有几天」就靠这一条：它在列表最上面，
 * 不进分组、不可折叠，所以无论已结束折叠与否它都在第一屏。
 */
@Composable
private fun NextUpCard(
    t: InkTokenSet,
    exam: ExamItem,
    daysAhead: Long?,
    onClick: () -> Unit,
) {
    val countdown = ExamRows.countdownLabel(daysAhead) ?: "日期待定"
    val line = listOf(
        "${ExamRows.dayLabel(exam)} ${ExamRows.weekdayLabel(exam)}".trim(),
        ExamRows.timeRangeLabel(exam),
        ExamRows.placeLabel(exam),
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .background(t.card)
            .drawBehind {
                // 3dp 主色条：硬朗体系里「当前」的记号（Stroke.accentBar）。
                // ⚠️ 必须画在 background 之后，否则会被卡面盖掉
                drawRect(t.primary, size = Size(3.dp.toPx(), size.height))
            }
            .border(Stroke.hairline, t.outline)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("下一场", fontSize = 10.sp, letterSpacing = 0.6.sp, color = t.inkFaint)
                Spacer(Modifier.weight(1f))
                Text(
                    countdown,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumericFont,
                    color = t.primary,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                exam.name.ifBlank { "（未命名考试）" },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(line, fontSize = 11.sp, lineHeight = 16.sp, color = t.inkSoft)
        }
    }
}

// ── 分组头 ────────────────────────────────────────────────

@Composable
private fun BucketHeader(
    t: InkTokenSet,
    header: ExamRow.Header,
    onToggle: () -> Unit,
) {
    val dim = header.bucket == ExamBucket.PAST || header.bucket == ExamBucket.LATER
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (header.collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .heightIn(min = if (header.collapsible) 48.dp else 28.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            header.bucket.label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = if (dim) t.inkSoft else t.ink,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${header.count} 场",
            fontSize = 10.sp,
            fontFamily = NumericFont,
            color = t.inkFaint,
        )
        if (header.collapsible && header.collapsed) {
            Spacer(Modifier.width(8.dp))
            Text("已折叠", fontSize = 9.5.sp, color = t.inkFaint)
        }
        Spacer(Modifier.weight(1f))
        if (header.collapsible) {
            Text(
                if (header.collapsed) "▸" else "▾",
                fontSize = 11.sp,
                color = t.inkFaint,
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 12.dp).height(0.75.dp).background(t.rule))
}

// ── 时间轴一行 ────────────────────────────────────────────

/**
 * 一行 = 左侧日期节点 + 右侧考试卡。
 *
 * 竖轴画在 `drawBehind` 里（一次绘制，不用 Box 套 Box）：
 * 轴在 x = 44dp，节点对齐日期文字的第一行；下一场用实心主色方块，其余是空心方框 ——
 * 全部方角，与红线一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    t: InkTokenSet,
    row: ExamRow.Card,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val e = row.exam
    val highlight = row.isNext
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val axisX = 44.dp.toPx()
                // 轴线用 outline 而不是 rule：暗色下 rule(#3E362C) 在纸底上几乎看不见，
                // 而轴线是「时间轴」这个隐喻的骨架，不能省。
                drawLine(t.outline, Offset(axisX, 0f), Offset(axisX, size.height), 1.dp.toPx())
                val cy = 20.dp.toPx()
                val half = 3.5.dp.toPx()
                val topLeft = Offset(axisX - half, cy - half)
                val nodeSize = Size(half * 2, half * 2)
                if (highlight) {
                    drawRect(t.primary, topLeft, nodeSize)
                } else {
                    // 先用纸色挖空轴线，再描边框 —— 否则线会从方块中间穿过去
                    drawRect(t.paper, topLeft, nodeSize)
                    drawRect(
                        t.outline,
                        topLeft,
                        nodeSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                    )
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Column(
                Modifier.width(40.dp).padding(top = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    ExamRows.dayLabel(e),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumericFont,
                    color = if (highlight) t.primary else t.ink,
                )
                Text(
                    ExamRows.weekdayLabel(e),
                    fontSize = 9.sp,
                    color = t.inkFaint,
                )
            }
            Spacer(Modifier.width(12.dp))
            ExamCard(
                t = t,
                exam = e,
                daysAhead = row.daysAhead,
                highlight = highlight,
                onClick = onClick,
                onLongClick = onLongClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExamCard(
    t: InkTokenSet,
    exam: ExamItem,
    daysAhead: Long?,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val countdown = ExamRows.countdownLabel(daysAhead)
    val timeRange = ExamRows.timeRangeLabel(exam)
    val duration = ExamRows.durationLabel(exam)
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.card)
            .border(if (highlight) Stroke.emphasis else Stroke.hairline, if (highlight) t.primary else t.outline)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                exam.name.ifBlank { "（未命名考试）" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (countdown != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    countdown,
                    fontSize = 11.sp,
                    fontFamily = NumericFont,
                    color = if (highlight) t.primary else t.inkSoft,
                )
            }
        }
        if (timeRange.isNotBlank() || duration.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(timeRange, duration).filter { it.isNotBlank() }.joinToString(" · "),
                fontSize = 11.5.sp,
                fontFamily = NumericFont,
                color = t.inkSoft,
            )
        }
        val place = ExamRows.placeLabel(exam)
        if (place.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(place, fontSize = 11.sp, lineHeight = 16.sp, color = t.inkSoft)
        }
        if (exam.seat.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "座位 ${exam.seat}",
                fontSize = 10.5.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
            )
        }
        if (exam.name.isBlank() && place.isBlank() && timeRange.isBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("这条返回里没有可用的课程名与地点，点开看原始 JSON", fontSize = 10.sp, color = t.brick)
        }
    }
}
