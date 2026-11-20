package cn.dkarbon.enroute.ui.academic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.AcademicApi
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.common.CampusNetworkHint
import cn.dkarbon.enroute.ui.common.NetFailure
import cn.dkarbon.enroute.ui.common.rememberListScrollState
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.em

/**
 * 考试安排 —— **时间轴**（2026-11-13 重做，2026-11-20 m31 轨道 D 改版）。
 *
 * ## 为什么从「扁平卡片列表」改成时间轴
 * 旧版一屏四张一样的卡，看不出「哪场是下一场」「已经考完的混在中间」。考试这件事，
 * 用户真正要回答的是**三个问题**：下一场还有几天 / 哪天几点在哪 / 考完了吗。
 * 所以：左侧一根竖轴 + 日期节点回答「什么时候」，右侧卡片回答「哪场、在哪」，
 * 顶部一条「下一场」把倒计时提到第一眼；已结束折叠到底，不占首屏。
 *
 * ## m31 轨道 D 加了什么（都靠「形态」说话，界面上一句说明都不写）
 * · **卡片边缘三态**：已考完 = 1dp 灰边 + 整体 55% 不透明；下一场 = 1dp 主色边 + 8dp 主色光晕；
 *   更远的 = 常规细边。三态只看「此刻」，不看分桶 —— 一场十点半考完的考试，
 *   十点半之后就自己变灰，不用等刷新。
 * · **下一场呼吸**：scale 1 → 1.012、光晕 alpha 0.35 → 0.60，3.2s 一个来回；
 *   顶部「下一场」卡与轴上的那一行同相位 —— 它们说的是同一场考试。
 * · **轴上三态节点** + **当前时刻三角** + **LED 跑马灯**：一个光点从现在滚到下一场，
 *   周期按剩余天数对数映射到 4–12 秒，到点闪一下再回起点（见 [ledPeriodMs]）。
 * · **历史点亮**：展开「已结束」时，轴上的历史节点从灰空心点淡入成实心主色。
 *
 * ## 只有一个时钟
 * `now` 是全页唯一的「现在」，60 秒跳一次。之前它是「刷新时才取一次」的快照，
 * 于是页面停在那里半小时也不会自己翻面；现在跨小时、跨零点都会自己更新。
 * 呼吸与 LED 各自是 Compose 的无限动画，不额外起线程，也不跟这个时钟打架。
 *
 * ## 性能（按 jetpack-compose-performance 规则）
 * · `now` 在屏幕级取一次，随参数下传 —— 不在每张卡里读时钟；
 * · 派生（分桶/排序/倒计时/行间距）全在 remember 里算，key 带全；
 * · `InkTokens` 只在屏幕级读一次，行拿到的是 `InkTokenSet` 值参数；
 * · 每个 Lazy item 都有 key + contentType（见 [ExamRow.rowKey] / [ExamRow.contentType]）。
 */
@Composable
fun ExamScreen(
    ui: AppState,
    vm: TimetableViewModel,
    modifier: Modifier = Modifier,
    // 详情是整页新页（m36）：由根节点渲染，列表这层不持有它 ——
    // 否则详情只是「盖在页内」，上层 SubHeader 与底栏还露在外面。
    onOpenDetail: (ExamItem, Boolean) -> Unit = { _, _ -> },
) {
    val t = InkTokens.current

    // ── 整页唯一的「现在」：60 秒一跳 ──────────────────────────
    // 考试是按时辰判「已考完 / 下一场」的，跨小时要准；但也没必要每行各起一个定时器。
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalDateTime.now()
        }
    }
    val today = now.toLocalDate()

    // 已考完的不单独隔离：全都在同一条轴上，往上滑就是过去（2026-11-20，m35）
    var collapsed by remember { mutableStateOf(emptySet<ExamBucket>()) }
    var showDiag by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    // 票 26：折叠分组不算「换内容」（只是同一批数据的显隐），所以 resetKey 恒定 ——
    // 从详情浮层返回、或折叠/展开后都停在你刚才那一行。
    val listState = rememberListScrollState()
    // m35：一条时间流之后，首屏要落在「下一场」，不能从头翻过去的考试。
    // 只做一次（记在 rememberSaveable 里），用户之后自己滚到哪就停在哪。
    var jumpedToNext by rememberSaveable { mutableStateOf(false) }

    val rows = remember(ui.exams, today, now, collapsed) {
        ExamRows.build(ui.exams, today, now, collapsed)
    }
    val stats = remember(ui.exams, today, now) { ExamRows.stats(ui.exams, today, now) }
    val label = remember(stats, collapsed) {
        buildString {
            append(ExamRows.toolbarLabel(stats))
            if (collapsed.isNotEmpty()) append(" · 已折叠 ${collapsed.size} 组")
        }
    }

    // ── 呼吸：全页一个相位，顶部卡和轴上那一行共用 ─────────────
    val motionOn = remember { animationsEnabled() }
    val breath = rememberInfiniteTransition(label = "exam-breath")
    val pulseRaw by breath.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "exam-breath-x",
    )
    // 系统关掉动画时停在静止帧：scale 1.0、光晕最淡。不是消失，是不再自己动。
    val pulse = if (motionOn) pulseRaw else 0f

    // 行间距：两场隔得越远，轴上留白越大。Lazy 的行是独立的，只能在这里预算
    val gaps = remember(rows, today, now) {
        val out = HashMap<String, Dp>(rows.size)
        var prev: ExamItem? = null
        rows.forEach { row ->
            if (row is ExamRow.Card) {
                out[row.rowKey] = rowGap(prev, row.exam, edgeState(row, today, now))
                prev = row.exam
            }
        }
        out
    }

    // LED 周期只跟「离下一场还有几天」有关（含小数：今天下午考就比明天考快）
    val next = stats.next
    val ledPeriod = remember(next, now) {
        val start = next?.let { ExamRows.startOf(it) }
        if (start == null) {
            6_000
        } else {
            ledPeriodMs(Duration.between(now, start).toMinutes() / 1440.0)
        }
    }
    val prevStartOfNext = remember(next, ui.exams) {
        next?.let { target -> previousStart(ui.exams, target) }
    }
    val nowLabel = remember(now) { now.format(HOUR_MIN) }

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
                // 「教务系统说本学期没有待考考试」是真结论，不是失败 —— 两种空态分开走
                if (ui.examsMsg.contains(NO_EXAM_MARK)) {
                    NothingToTake(t = t, msg = ui.examsMsg)
                } else {
                    ExamLoadFailed(
                        t = t,
                        msg = ui.examsMsg,
                        onRetry = { vm.refreshExams() },
                        onDiagnose = { showDiag = true },
                    )
                }
            } else {
                // 失败但旧数据还在：只留一行内网提示，列表照旧可读（不清空、不白屏）
                if (ui.examsMsg.isNotBlank() && !ui.examsMsg.startsWith("✅")) {
                    CampusNetworkHint(
                        failure = failureOf(ui.examsMsg),
                        compact = true,
                        modifier = Modifier.padding(horizontal = Axis.ContentStart),
                    )
                }
                // 首帧之后把「下一场」那一行顶到可视区顶部（next-up 卡占 index 0）
                LaunchedEffect(rows, next) {
                    if (!jumpedToNext && rows.isNotEmpty()) {
                        val idx = rows.indexOfFirst { it is ExamRow.Card && it.isNext }
                        if (idx >= 0) {
                            // 停在「下一场」那一行：它顶到列表首行，上面的旧考试留在滚动区里
                            runCatching { listState.scrollToItem(idx) }
                        }
                        jumpedToNext = true
                    }
                }
                // m35：下一场这条固定在列表之上 —— 它是这页的「现在」，不是列表里的一员。
                // 放进 LazyColumn 的话它会跟着滚走，用户翻两下就再也找不到下一场。
                if (next != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = Axis.ContentStart, end = 16.dp, bottom = 2.dp),
                    ) {
                        NextUpCard(
                            t = t,
                            exam = next,
                            daysAhead = stats.nextDays,
                            pulse = pulse,
                            onClick = { onOpenDetail(next, false) },
                        )
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 0.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
                ) {
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
                            is ExamRow.Card -> {
                                // 三态只在这里算一次：只有「下一场」那一行才吃呼吸相位，
                                // 其余行传 0，别让整屏每帧都因为 pulse 变化而重组。
                                val state = edgeState(row, today, now)
                                TimelineRow(
                                    t = t,
                                    row = row,
                                    state = state,
                                    gap = gaps[row.rowKey] ?: 10.dp,
                                    pulse = if (state == EdgeState.NEXT) pulse else 0f,
                                    motionOn = motionOn,
                                    ledPeriod = ledPeriod,
                                    marker = if (row.isNext) {
                                        markerFraction(prevStartOfNext, ExamRows.startOf(row.exam), now)
                                    } else {
                                        0f
                                    },
                                    nowLabel = nowLabel,
                                    onClick = { onOpenDetail(row.exam, false) },
                                    // 长按 = 「看原文」：直接落在原始 JSON 那一节
                                    onLongClick = { onOpenDetail(row.exam, true) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDiag) {
            DiagnoseSheet(
                t = t,
                ui = ui,
                vm = vm,
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

private const val NO_EXAM_MARK = "教务系统说"
private val HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm")

/** 三态判定：只看「现在」。已考完的永远优先于 isNext —— 一场正在拖堂的考试也不该还发光。 */
private fun edgeState(row: ExamRow.Card, today: LocalDate, now: LocalDateTime): EdgeState = when {
    ExamRows.isFinished(row.exam, today, now) -> EdgeState.PAST
    row.isNext -> EdgeState.NEXT
    else -> EdgeState.FUTURE
}

/** 排在 target 之前、且离它最近的那一场的开考时刻（用来把「现在」摆到两场之间的比例位置上）。 */
private fun previousStart(exams: List<ExamItem>, target: ExamItem): LocalDateTime? {
    val targetStart = ExamRows.startOf(target) ?: return null
    return exams
        .mapNotNull { e -> ExamRows.startOf(e)?.takeIf { it.isBefore(targetStart) } }
        .maxOrNull()
}

/**
 * 仓库层不告诉我们是「没网」还是「站点改版」，这里按文案兜底判一次性质。
 * 判不出来的按 [NetFailure.Unreachable] 处理：内网不通是这个页面最常见的失败。
 */
private fun failureOf(msg: String): NetFailure = when {
    msg.contains("解析") || msg.contains("格式") || msg.contains("改版") -> NetFailure.Parse
    msg.contains("HTTP") || msg.contains("状态码") || msg.contains("服务端") -> NetFailure.ServerError
    else -> NetFailure.Unreachable
}

// ── 顶部「下一场」────────────────────────────────────────

/**
 * 下一场倒计时卡。
 *
 * 主诉求「能一眼看出下一场还有几天」就靠这一条：它在列表最上面，
 * 不进分组、不可折叠，所以无论已结束折叠与否它都在第一屏。
 * 它和轴上那一行说的是同一场考试，所以共用同一个呼吸相位（[pulse]）。
 */
@Composable
private fun NextUpCard(
    t: InkTokenSet,
    exam: ExamItem,
    daysAhead: Long?,
    pulse: Float,
    onClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    var selfRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val countdown = ExamRows.countdownLabel(daysAhead) ?: "日期待定"
    val line = listOf(
        "${ExamRows.dayLabel(exam)} ${ExamRows.weekdayLabel(exam)}".trim(),
        ExamRows.timeRangeLabel(exam),
        ExamRows.placeLabel(exam),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    val scale = 1f + 0.012f * pulse
    val waveAlpha = if (t.isDark) 0.085f else 0.16f

    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { selfRect = it.boundsInWindow() }
            .drawBehind { edgeGlow(t.primary, 0.35f + 0.25f * pulse) }
            .background(t.card)
            .drawBehind {
                // 左溢出竖直波浪：海报纸的母题，只在卡片左侧被裁一刀
                // 波幅要压得住卡高：卡片 190dp 上下时，±11dp 的三个波腹才看得出「浪」，
                // 原来 ±7dp 两段在真机上就是一条直灰带。
                val amp = 11.dp.toPx()
                val belly = 24.dp.toPx()
                val h = size.height
                val path = Path()
                path.moveTo(0f, 0f)
                path.lineTo(belly, 0f)
                path.cubicTo(belly + amp, h * 0.10f, belly - amp, h * 0.26f, belly, h * 0.38f)
                path.cubicTo(belly + amp, h * 0.50f, belly - amp, h * 0.66f, belly, h * 0.78f)
                path.cubicTo(belly + amp * 0.6f, h * 0.86f, belly - amp * 0.3f, h * 0.94f, belly, h)
                path.lineTo(0f, h)
                path.close()
                drawPath(path, t.ink.copy(alpha = waveAlpha))
            }
            .border(Stroke.hairline, t.primary)
            .clickable { onClick(selfRect) }
            .padding(start = 30.dp, end = 13.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .background(t.primary.copy(alpha = if (t.isDark) 0.16f else 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    tint = t.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("下一场", fontSize = 9.sp, letterSpacing = 0.18.em, color = t.inkFaint)
                Spacer(Modifier.height(3.dp))
                Text(
                    exam.name.ifBlank { "（未命名考试）" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(line, fontSize = 11.sp, lineHeight = 16.sp, color = t.inkSoft)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    countdown,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumericFont,
                    color = t.primary,
                )
            }
        }
    }
}

// ── 分组头 ────────────────────────────────────────────────

/**
 * 分组头。轴在这一行也照常穿过（细线），不被标题打断 ——
 * 分组是「批注」，时间轴是「骨架」，批注不该把骨架剪断。
 */
@Composable
private fun BucketHeader(
    t: InkTokenSet,
    header: ExamRow.Header,
    onToggle: () -> Unit,
) {
    val dim = header.bucket == ExamBucket.PAST || header.bucket == ExamBucket.LATER
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (header.collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .drawBehind { axisLine(t.outline) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 27.dp)
                .padding(start = Axis.ContentStart, end = 4.dp),
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
            Spacer(Modifier.weight(1f))
            if (header.collapsible) {
                Text(
                    if (header.collapsed) "▸" else "▾",
                    fontSize = 11.sp,
                    color = t.inkFaint,
                )
            }
        }
    }
}

// ── 时间轴一行 ────────────────────────────────────────────

/**
 * 一行 = 左侧竖直轴 + 轴上节点 + 右侧日期与考试卡。
 *
 * 轴、节点、当前时刻三角、LED 全在 `drawBehind` 里一次画完（见 `ExamTimeline.kt`）：
 * 位置都是「按比例算的」，用 Compose 的盒子拼不出来，也没必要为它建一套图标。
 *
 * @param gap 行顶留白：两场隔得越久越大，轴的「疏密」就是时间流逝的刻度
 * @param state 三态（已考 / 下一场 / 更远）
 * @param marker 当前时刻在这一段轴上的比例（只在下一场那一行用）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineRow(
    t: InkTokenSet,
    row: ExamRow.Card,
    state: EdgeState,
    gap: Dp,
    pulse: Float,
    motionOn: Boolean,
    ledPeriod: Int,
    marker: Float,
    nowLabel: String,
    onClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onLongClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val e = row.exam
    val nodeY = Axis.nodeY(gap)
    val markerY = nodeY * marker

    // 历史节点点亮：展开「已结束」时这些行是新组合出来的，从灰点到实心主色 420ms 渐入
    val lit = if (state == EdgeState.PAST && motionOn) {
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMillis = 420)) }
        a.value
    } else {
        1f
    }

    // LED：只在下一场那一行跑。一个光点从「现在」滚到节点，到点闪一下，再回起点
    val led = if (state == EdgeState.NEXT && motionOn) {
        val tr = rememberInfiniteTransition(label = "exam-led")
        tr.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = ledPeriod, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "exam-led-x",
        ).value
    } else {
        0f
    }

    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                axisLine(t.outline)
                timelineNode(t, state, Axis.X.toPx(), nodeY.toPx(), pulse, lit)
                if (state == EdgeState.NEXT) {
                    val startY = markerY.toPx()
                    val endY = nodeY.toPx()
                    nowMarker(t, Axis.X, startY)
                    ledDot(t, Axis.X, startY + (endY - startY) * led, led)
                }
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = Axis.ContentStart)) {
            Spacer(Modifier.height(gap))
            Box(Modifier.height(Axis.DateLine), contentAlignment = Alignment.CenterStart) {
                Text(
                    "${ExamRows.dayLabel(e)} ${ExamRows.weekdayLabel(e)}".trim(),
                    fontSize = 10.5.sp,
                    fontFamily = NumericFont,
                    color = if (state == EdgeState.NEXT) t.primary else t.inkFaint,
                )
            }
            Spacer(Modifier.height(6.dp))
            ExamCard(
                t = t,
                exam = e,
                daysAhead = row.daysAhead,
                state = state,
                pulse = pulse,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            Spacer(Modifier.height(10.dp))
        }

        // 三角旁边标一下「现在几点」。越靠近下一场越淡：快到点时它会和日期行撞上，
        // 那时候「还有几小时」这件事已经由卡片上的倒计时接管了。
        if (state == EdgeState.NEXT) {
            val fade = ((0.66f - marker) / 0.11f).coerceIn(0f, 1f)
            if (fade > 0.01f) {
                Text(
                    "现在 $nowLabel",
                    fontSize = 9.5.sp,
                    fontFamily = NumericFont,
                    color = t.primary.copy(alpha = fade),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = Axis.X + 10.dp, y = markerY - 7.dp),
                )
            }
        }
    }
}

// ── 考试卡 ────────────────────────────────────────────────

/**
 * 一张考试卡。三态只差边缘与不透明度，正文排版完全一致 ——
 * 「已考完」是同一张卡退到背景里，不是另一套组件。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExamCard(
    t: InkTokenSet,
    exam: ExamItem,
    daysAhead: Long?,
    state: EdgeState,
    pulse: Float,
    onClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
    onLongClick: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    var selfRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val countdown = ExamRows.countdownLabel(daysAhead)
    val timeRange = ExamRows.timeRangeLabel(exam)
    val duration = ExamRows.durationLabel(exam)
    val place = ExamRows.placeLabel(exam)
    val glow = if (state == EdgeState.NEXT) 0.35f + 0.25f * pulse else 0f
    val scale = if (state == EdgeState.NEXT) 1f + 0.012f * pulse else 1f
    val stripe = (if (t.isDark) Color.White else t.inkFaint)
        .copy(alpha = if (t.isDark) 0.075f else 0.11f)
    val sheen = if (t.isDark) Color.White.copy(alpha = 0.030f) else Color.White.copy(alpha = 0.42f)
    val edgeLight = if (t.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.90f)
    val ghost = exam.name.trim().take(1)

    Box(
        Modifier
            .fillMaxWidth()
            // 55% 不透明包住整张卡（含边框与文字）：alpha 放在链首，背景和描边一起淡
            .then(if (state == EdgeState.PAST) Modifier.alpha(0.55f) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onGloballyPositioned { selfRect = it.boundsInWindow() }
            // 光晕画在最外：卡面之外的那一圈才看得见（卡内会被 background 盖掉）
            .drawBehind { edgeGlow(t.primary, glow) }
            .background(t.card)
            .drawBehind {
                // ① 竖向 4dp 条纹：票根纸的底纹
                val step = 4.dp.toPx()
                var x = step * 0.5f
                while (x < size.width) {
                    drawLine(stripe, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    x += step
                }
                // ② 115° 斜向光泽：左下暗 → 中段亮 → 右上收暗
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, sheen, Color.Transparent),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                    ),
                )
                // ③ 内缩 1dp 亮边：双线描边的里那一层
                drawRect(
                    color = edgeLight,
                    topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                    size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            }
            .border(Stroke.hairline, if (state == EdgeState.NEXT) t.primary else t.outline)
            .combinedClickable(
                onClick = { onClick(selfRect) },
                onLongClick = { onLongClick(selfRect) },
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
    ) {
        // 右下角的大字水印：只取名字第一个字，压到 4.5% 不打扰阅读
        if (ghost.isNotEmpty() && ghost != "（") {
            Text(
                ghost,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink.copy(alpha = 0.045f),
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EXAM", fontSize = 8.5.sp, letterSpacing = 0.18.em, color = t.inkFaint)
                Spacer(Modifier.weight(1f))
                if (countdown != null) {
                    Text(
                        countdown,
                        fontSize = 11.sp,
                        fontFamily = NumericFont,
                        color = if (state == EdgeState.NEXT) t.primary else t.inkSoft,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                // 两枚小圆环：版式配重，与左上标签一左一右夹住这一行
                Box(Modifier.size(11.dp).border(1.dp, t.inkFaint.copy(alpha = 0.45f), CircleShape))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.size(11.dp).border(1.dp, t.inkFaint.copy(alpha = 0.45f), CircleShape))
            }
            Spacer(Modifier.height(5.dp))
            Text(
                exam.name.ifBlank { "（未命名考试）" },
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (timeRange.isNotBlank() || duration.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(timeRange, duration).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.5.sp,
                    fontFamily = NumericFont,
                    color = t.inkSoft,
                )
            }
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
                Text("这条返回里没有可用的课程名与地点", fontSize = 10.sp, color = t.brick)
            }
        }
    }
}

// ── 两种空态 ──────────────────────────────────────────────

/**
 * 失败的统一说法。原来每个页面各写一句（「没连上学院官网」「刷新失败」…），
 * 用户看不出「该怎么解决」；现在只走 [CampusNetworkHint] 那一套口径。
 */
@Composable
private fun ExamLoadFailed(
    t: InkTokenSet,
    msg: String,
    onRetry: () -> Unit,
    onDiagnose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text("考试安排没读出来", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Spacer(Modifier.height(8.dp))
        CampusNetworkHint(failure = failureOf(msg))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetAction(t = t, label = "重新抓取", primary = true, onClick = onRetry)
            SheetAction(t = t, label = "接口诊断", onClick = onDiagnose)
        }
    }
}

/** 教务系统明确说本学期没有待考考试 —— 这是结论，不是失败，不要再摆一套「重试」。 */
@Composable
private fun NothingToTake(t: InkTokenSet, msg: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text(
            msg.ifBlank { "教务系统说本学期没有待考考试" },
            fontSize = 12.5.sp,
            lineHeight = 20.sp,
            color = t.inkSoft,
        )
    }
}
