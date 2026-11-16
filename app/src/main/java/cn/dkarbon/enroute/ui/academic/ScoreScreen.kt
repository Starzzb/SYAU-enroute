package cn.dkarbon.enroute.ui.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.common.rememberListScrollState
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 成绩查询（2026-11-13 重做）。
 *
 * ## 结构
 * 工具条（真实门数 / 学期数 / 不及格数）→ 学期分组（可折叠）→ 成绩行（主 / 次两层）→ 详情浮层（+ 原始）
 *
 * ## 为什么按学期分组
 * 旧版是一长条「课名 + 分数」，55 条从大一排到大四，想看「这学期怎么样」得自己数。
 * 按学期倒序分组后，最新学期永远在最上面，每个组头自带**真实的**门数 / 学分 / 均分。
 *
 * ## 不编数据（这是本条最硬的要求）
 * · **不显示绩点**：URP 这套接口没有绩点算法，任何「GPA 3.7」都是编的。
 * · **均分只在整学期都是百分制时才算**（[ScoreRows.termStats]）；混入等级制就不显示，
 *   并写明「含等级制课程，不计算均分」——空着比填个错数诚实。
 * · **加权平均要求学分齐全**，缺一门就是 null。
 * · 不及格只认「分数 < 60」或「等级是不及格」，考查课/合格制一律按未知处理。
 */
@Composable
fun ScoreScreen(ui: AppState, vm: TimetableViewModel, modifier: Modifier = Modifier) {
    val t = InkTokens.current

    var onlyFailed by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    var detail by remember { mutableStateOf<ScoreItem?>(null) }
    var showDiag by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    // 票 26：「只看不及格」是**换内容**（筛掉了别的课），所以它进 resetKey —— 切换必须回顶部；
    // 而学期折叠只是显隐，不算换内容，不参与 key。
    val listState = rememberListScrollState(if (onlyFailed) "failed" else "all")

    val shown = remember(ui.scores, onlyFailed) {
        if (onlyFailed) ui.scores.filter { it.failed } else ui.scores
    }
    val rows = remember(ui.scores, onlyFailed, collapsed) {
        ScoreRows.build(ui.scores, onlyFailed, collapsed)
    }
    val label = remember(ui.scores, shown, onlyFailed, collapsed) {
        buildString {
            append(ScoreRows.toolbarLabel(ui.scores, shown, onlyFailed))
            if (collapsed.isNotEmpty()) append(" · 已折叠 ${collapsed.size} 学期")
        }
    }

    Column(modifier.fillMaxSize()) {
        AcademicToolbar(
            t = t,
            label = label,
            loading = ui.scoresLoading,
            onRefresh = { vm.refreshScores() },
            onMore = { showMore = true },
        )

        // 页顶一行：这数据是几时取的（缓存命中直读时不打网络，必须标出年龄）
        val fetchStamp = remember(ui.scoresFetchedAt) {
            cn.dkarbon.enroute.data.CachePolicy.fetchStamp(System.currentTimeMillis(), ui.scoresFetchedAt)
        }
        if (fetchStamp.isNotBlank()) {
            Text(
                fetchStamp,
                fontSize = 11.5.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        if (ui.scores.isEmpty()) {
            AcademicProblem(
                t = t,
                title = "成绩没读出来",
                reason = ui.scoresMsg,
                loggedIn = ui.loggedIn,
                onRetry = { vm.refreshScores() },
                onDiagnose = { showDiag = true },
            )
        } else {
            if (ui.scoresMsg.isNotBlank() && !ui.scoresMsg.startsWith("✅")) {
                Text(
                    ui.scoresMsg,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = t.brick,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (shown.isEmpty()) {
                // 筛选后空了：不是「没有成绩」，是筛掉了
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("没有不及格的课", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "「只看不及格」筛掉了全部 ${ui.scores.size} 条记录。",
                        fontSize = 11.sp,
                        color = t.inkFaint,
                    )
                    Spacer(Modifier.height(12.dp))
                    SheetAction(t, "显示全部", primary = true) { onlyFailed = false }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = 2.dp, bottom = 28.dp),
                ) {
                    items(items = rows, key = { it.rowKey }, contentType = { it.contentType }) { row ->
                        when (row) {
                            is ScoreRow.Header -> TermHeader(
                                t = t,
                                header = row,
                                onToggle = {
                                    collapsed = if (row.term in collapsed) {
                                        collapsed - row.term
                                    } else {
                                        collapsed + row.term
                                    }
                                },
                            )
                            is ScoreRow.Item -> ScoreLine(
                                t = t,
                                score = row.score,
                                onClick = { detail = row.score },
                            )
                        }
                    }
                    item(key = "score-note", contentType = "score-note") {
                        Text(
                            "不显示绩点与排名：接口没有给出这套算法。\n" +
                                "均分只在「整学期都是百分制」时才算，加权还要求学分齐全 —— 缺任何一格就空着。",
                            fontSize = 9.5.sp,
                            lineHeight = 15.sp,
                            color = t.inkFaint,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 14.dp),
                        )
                    }
                }
            }
        }
    }

    detail?.let { s ->
        ScoreDetailSheet(t = t, score = s, onDismiss = { detail = null })
    }

    if (showDiag) {
        DiagnoseSheet(
            t = t,
            ui = ui,
            vm = vm,
            paths = listOf(AcademicApi.scorePathPreview()),
            onDismiss = { showDiag = false },
        )
    }

    if (showMore) {
        AcademicSheet(
            t = t,
            title = "更多",
            subtitle = "成绩查询 · 数据来自校内教务（${AcademicApi.URP_HOST}）",
            onDismiss = { showMore = false },
        ) {
            SheetMenuRow(t, "重新抓取", "再打一次成绩接口（token 每次都不一样，会重新取）") {
                showMore = false
                vm.refreshScores()
            }
            Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
            SheetMenuRow(
                t = t,
                title = if (onlyFailed) "显示全部课程" else "只看不及格",
                detail = if (onlyFailed) "现在筛着（${shown.size} 门）" else "只留有据可查的不及格",
            ) {
                showMore = false
                onlyFailed = !onlyFailed
            }
            Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
            SheetMenuRow(
                t = t,
                title = if (collapsed.isEmpty()) "折叠全部学期" else "展开全部学期",
                detail = "只留组头，一屏看完所有学期的概览",
            ) {
                showMore = false
                collapsed = if (collapsed.isEmpty()) {
                    ui.scores.map { it.term.ifBlank { "学期未标注" } }.toSet()
                } else {
                    emptySet()
                }
            }
            Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
            SheetMenuRow(t, "接口诊断", "看状态码、字节数和返回原文前 200 字") {
                showMore = false
                showDiag = true
            }
        }
    }
}

/**
 * 学期组头：一行主信息（学期名 + 门数 + 学分），一行统计（均分 / 加权 / 不及格）。
 *
 * 统计行**只显示算得出来的**：均分为 null 时改成一句「含等级制课程，不计算均分」，
 * 而不是显示 0 或者干脆不解释（不解释会被当成 bug）。
 */
@Composable
private fun TermHeader(
    t: InkTokenSet,
    header: ScoreRow.Header,
    onToggle: () -> Unit,
) {
    val st = header.stats
    val credits = ScoreRows.fmt(st.credits)
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.cardAlt)
            .clickable(onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                header.term,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append("${st.count} 门")
                    if (credits != null) append(" · $credits 学分")
                },
                fontSize = 10.5.sp,
                fontFamily = NumericFont,
                color = t.inkSoft,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (header.collapsed) "▸" else "▾", fontSize = 11.sp, color = t.inkFaint)
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val avg = ScoreRows.fmt(st.average)
            val weighted = ScoreRows.fmt(st.weighted)
            Text(
                buildString {
                    if (avg != null) {
                        append("算术均分 $avg")
                        if (weighted != null) append(" · 学分加权 $weighted")
                    } else {
                        append("含等级制课程，不计算均分")
                    }
                },
                fontSize = 9.5.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
                modifier = Modifier.weight(1f),
            )
            if (st.failed > 0) {
                Text(
                    "${st.failed} 门不及格",
                    fontSize = 9.5.sp,
                    fontFamily = NumericFont,
                    color = t.brick,
                )
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.outline))
}

/** 一行成绩：左主信息（课名 / 英文名 / 属性·学分·课程号），右分数 */
@Composable
private fun ScoreLine(
    t: InkTokenSet,
    score: ScoreItem,
    onClick: () -> Unit,
) {
    val failed = score.failed
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.card)
            .drawBehind {
                // 台账式细线：成绩是表，靠行线读，不靠卡片阴影。
                // 线中心上移半线宽，否则会被画布底边裁掉一半、暗色下等于没有
                val w = 0.75.dp.toPx()
                drawLine(
                    t.rule,
                    Offset(0f, size.height - w / 2f),
                    Offset(size.width, size.height - w / 2f),
                    w,
                )
                if (failed) drawRect(t.brick, size = Size(3.dp.toPx(), size.height))
            }
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(
                start = if (failed) 15.dp else 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    score.name.ifBlank { "（未命名课程）" },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (score.englishName.isNotBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        score.englishName,
                        fontSize = 10.sp,
                        color = t.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (score.category.isNotBlank()) append(score.category)
                        if (score.credit.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("${score.credit} 学分")
                        }
                        if (score.hours.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("${score.hours} 学时")
                        }
                        if (score.code.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(score.code)
                        }
                    }.ifBlank { "—" },
                    fontSize = 9.5.sp,
                    fontFamily = NumericFont,
                    color = t.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // 没有百分制分数时显示等级，而不是显示一个横杠装作没出成绩
                    score.score.ifBlank { score.grade.ifBlank { "—" } },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NumericFont,
                    color = if (failed) t.brick else t.ink,
                )
                if (score.score.isNotBlank() && score.grade.isNotBlank()) {
                    Text(
                        score.grade,
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                    )
                }
            }
        }
    }
}
