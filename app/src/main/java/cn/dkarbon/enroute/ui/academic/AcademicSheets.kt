package cn.dkarbon.enroute.ui.academic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.AcademicApi
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.Motion
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 考试 / 成绩两页共用的**工具条 / 浮层 / 失败态**（2026-11-13）。
 *
 * 为什么抽出来：这两页的结构是同一个模子 ——
 *   ① 页内不再写「考试」「成绩」（SubHeader 已经写了），改成一条只放**真实计数 + 操作**的工具条；
 *   ② 失败态必须是「能动手」的（重试 / 诊断 / 看原始），不能是一句「暂无数据」；
 *   ③ 每条数据都能看到**原始 JSON**（接口是逆向出来的，用户要能自查）。
 * 三件事都只写一次，两页共用，避免第二页走样。
 *
 * 红线照旧：0 圆角、无渐变、无 elevation（浮层用 1.5dp 实边 + hardShadow）、
 * 触区 ≥ 48dp、数字一律 [NumericFont]。
 */

// ── 工具条 ────────────────────────────────────────────────

/**
 * 页内工具条：左边是**真实计数/筛选状态**，右边是刷新与更多。
 *
 * 刻意不放标题字样：调用方的 SubHeader 已经显示「考试」「成绩」，
 * 页内再写一遍等于把标题喊两遍（这是主人点名要去掉的那一行）。
 */
@Composable
fun AcademicToolbar(
    t: InkTokenSet,
    label: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = 11.5.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            if (loading) {
                Text("抓取中…", fontSize = 11.sp, color = t.inkFaint, modifier = Modifier.padding(end = 12.dp))
            } else {
                // 主色当文字必须 ≥14sp/700（硬朗农业的主色对纸面只有 4.37:1）
                Box(
                    Modifier
                        .clip(RectangleShape)
                        .clickable(onClick = onRefresh)
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("刷新", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary)
                }
            }
            Box(
                Modifier
                    .clip(RectangleShape)
                    .clickable(onClick = onMore)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .border(Stroke.hairline, t.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋯", fontSize = 13.sp, color = t.inkSoft)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(0.75.dp).background(t.rule))
    }
}

/** 浮层菜单的一行：动作 + 一句它会做什么（48dp 触区，行间 1dp 线由调用方加） */
@Composable
fun SheetMenuRow(
    t: InkTokenSet,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = t.ink, fontWeight = FontWeight.Medium)
            if (detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(detail, fontSize = 10.sp, lineHeight = 15.sp, color = t.inkFaint)
            }
        }
        Text("›", fontSize = 14.sp, color = t.inkFaint)
    }
}

// ── 底部浮层 ──────────────────────────────────────────────

/**
 * 底部浮层（与课表长按菜单同一套语言：黑纱 + 实边 + 直角）。
 *
 * 用 180ms 位移而不是缩放：硬朗体系里没有「弹跳」，位移最省事也最快。
 * 位移值用 `Modifier.offset { }` 懒读，避免每帧重组（性能规则 §1）。
 */
@Composable
fun AcademicSheet(
    t: InkTokenSet,
    title: String,
    subtitle: String = "",
    onDismiss: () -> Unit,
    /**
     * 触发按钮在根坐标里的矩形。给了就把卡片摆到它下面、折角指回去；
     * 没给（例如从列表行点进来）退回右上角，不硬造一个假锚点。
     */
    anchor: androidx.compose.ui.geometry.Rect? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 全 App 三点/浮层统一成「锚定卡片」一套语言（主人 2026-11-20）：
    // 从触发处下方长出、点空白或返回键关。要精确读数的档位走实心，不上玻璃。
    cn.dkarbon.enroute.ui.kit.AnchoredCardOverlay(
        anchor = anchor,
        accent = t.accent,
        onDismiss = onDismiss,
        maxHeight = 560.dp,
        solid = true,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.ink)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 10.5.sp, lineHeight = 15.sp, color = t.inkFaint)
            }
        }
        Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
        content()
    }
}

/** 浮层里的一行「标签 : 值」；值可能是空串（那就显示「未提供」而不是留白装神秘） */
@Composable
fun SheetField(
    t: InkTokenSet,
    label: String,
    value: String,
    mono: Boolean = false,
    emphasize: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, fontSize = 11.sp, color = t.inkFaint, modifier = Modifier.width(76.dp))
        Text(
            value.ifBlank { "未提供" },
            fontSize = if (emphasize) 12.5.sp else 11.5.sp,
            lineHeight = 18.sp,
            fontFamily = if (mono) NumericFont else null,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = if (value.isBlank()) t.inkFaint else if (emphasize) t.ink else t.inkSoft,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 原始 JSON 区块。
 *
 * 为什么必须给：这两个接口是**逆向**出来的（见 `AcademicApi` 的 KDoc），
 * 学校一改版，页面上的结构化字段就可能不再对得上。留一个「原文 + 全部 key」
 * 的出口，用户才能把它贴给开发者，而不是只能说出「不显示」。
 */
@Composable
fun RawJsonBlock(t: InkTokenSet, raw: String, fields: Map<String, String>) {
    val cb = LocalClipboardManager.current
    var copied by remember(raw) { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("原始 JSON", fontSize = 10.5.sp, color = t.inkFaint)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .clip(RectangleShape)
                .border(Stroke.hairline, t.outline)
                .clickable {
                    cb.setText(AnnotatedString(raw))
                    copied = true
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(if (copied) "已复制" else "复制", fontSize = 11.sp, color = t.inkSoft)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(t.cardAlt)
            .border(Stroke.hairline, t.rule)
            .padding(8.dp),
    ) {
        SelectionContainer {
            Text(
                raw.ifBlank { "（这条没有原文）" },
                fontSize = 9.5.sp,
                lineHeight = 14.sp,
                fontFamily = NumericFont,
                color = t.inkSoft,
            )
        }
    }

    if (fields.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text(
            "接口返回的字段（${fields.size} 个，空值也保留）",
            fontSize = 10.5.sp,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(4.dp))
        fields.entries.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(k, fontSize = 9.5.sp, fontFamily = NumericFont, color = t.inkFaint, modifier = Modifier.width(90.dp))
                Text(
                    if (v.isBlank()) "—" else v,
                    fontSize = 10.sp,
                    fontFamily = NumericFont,
                    color = if (v.isBlank()) t.inkFaint else t.inkSoft,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── 考试详情（整页） ──────────────────────────────────────

/**
 * 考试详情页：整页纸纹当底、正文装进一个框（见 docs/设计-m36-详情压底.md）。
 *
 * 为什么从浮层改成整页：浮层是「点一下就关」的辅读，只装得下三五行；这里要放
 * 时间地点 + 原始 JSON + 全部接口字段，是**越读越长**的正文，塞进 304dp 宽的
 * 浮层只会缩成一团（主人 2026-11-20 指出的「畸形」）。
 *
 * @param showRawFirst 长按进来时直接停在原文区（用户意图是「看原文」，不是「看详情」）
 */
@Composable
fun ExamDetailPage(
    t: InkTokenSet,
    exam: ExamItem,
    showRawFirst: Boolean,
    onBack: () -> Unit,
) {
    val sub = buildString {
        append(ExamRows.dayLabel(exam))
        append(" ")
        append(ExamRows.weekdayLabel(exam))
        val range = ExamRows.timeRangeLabel(exam)
        if (range.isNotBlank()) append(" · ").append(range)
    }

    cn.dkarbon.enroute.ui.kit.DetailScaffold(
        backLabel = "考试安排",
        tex = cn.dkarbon.enroute.ui.kit.PaperTex.GRID,
        onBack = onBack,
        initialScrollToEnd = showRawFirst,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("考试", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = t.inkSoft)
            Spacer(Modifier.weight(1f))
            Text(
                ExamRows.countdownLabel(ExamRows.daysAhead(exam, java.time.LocalDate.now())) ?: "",
                fontSize = 9.5.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            exam.name.ifBlank { "（未命名考试）" },
            fontSize = 19.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            color = t.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            sub,
            fontSize = 9.5.sp,
            letterSpacing = 0.04.em,
            fontFamily = NumericFont,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(13.dp))
        // 通栏墨线：标题区与正文的分界，比普通分割线重一档
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.ink))

        cn.dkarbon.enroute.ui.kit.DetailSection("时间与地点") {
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("日期", exam.date.ifBlank { "暂缺" })
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("时间", ExamRows.timeRangeLabel(exam).ifBlank { "暂缺" })
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("考场", ExamRows.placeLabel(exam).ifBlank { "暂缺" })
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("座位", exam.seat.ifBlank { "暂缺" })
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("时长", ExamRows.durationLabel(exam).ifBlank { "暂缺" })
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("考试名称", exam.type.ifBlank { "暂缺" }, valueMono = false)
        }

        cn.dkarbon.enroute.ui.kit.DetailSection("校区与教室") {
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("校区", exam.campus.ifBlank { "暂缺" }, valueMono = false)
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("教学楼", exam.building.ifBlank { "暂缺" })
            cn.dkarbon.enroute.ui.kit.DetailFieldRow("教室", exam.room.ifBlank { "暂缺" })
            if (exam.extraLines.isNotEmpty()) {
                cn.dkarbon.enroute.ui.kit.DetailFieldRow("其他行", exam.extraLines.joinToString(" / "), valueMono = false)
            }
        }

        cn.dkarbon.enroute.ui.kit.DetailSection("原始") {
            RawJsonBlock(t, exam.raw, exam.fields)
        }
    }
}

// ── 失败 / 空态 ───────────────────────────────────────────

/** 空态里的一个动作（48dp 触区，实边方角按钮） */@Composable
fun SheetAction(
    t: InkTokenSet,
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RectangleShape)
            .background(if (primary && enabled) t.primary else t.card)
            .border(Stroke.hairline, if (primary && enabled) t.primary else t.outline)
            .clickable(enabled = enabled, onClick = onClick)
            .height(48.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> t.inkFaint
                primary && t.isDark -> Color(0xFF100E0B)
                primary -> Color.White
                else -> t.inkSoft
            },
        )
    }
}

/**
 * 「读不到数据」的页面：说清**为什么**，并给能点的下一步。
 *
 * 这是本轮验收的一条硬要求：手机不在校园网时接口必然失败，
 * 此时页面不能白屏，也不能只写「暂无数据」——那等于告诉用户「你没考试」。
 */
@Composable
fun AcademicProblem(
    t: InkTokenSet,
    title: String,
    reason: String,
    loggedIn: Boolean,
    onRetry: () -> Unit,
    onDiagnose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            if (!loggedIn) {
                "考试/成绩接口在学校内网（${AcademicApi.URP_HOST}），登录态也只存在这一次运行里。\n" +
                    "现在读不到数据，通常是因为「还没登录」，或没连校园网 / mangoVPN。"
            } else {
                reason.ifBlank { "接口这次返回了空，可能是这学期还没排考，也可能学校改了版。" }
            },
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = t.inkSoft,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetAction(t, "重新抓取", primary = loggedIn, onClick = onRetry)
            SheetAction(t, "接口诊断", onClick = onDiagnose)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "「接口诊断」会真的去请求一次，并把状态码、字节数、返回原文前 200 字列出来。",
            fontSize = 10.sp,
            lineHeight = 16.sp,
            color = t.inkFaint,
        )
    }
}

/**
 * 接口诊断浮层（考试 / 成绩共用）。
 *
 * 结果直接读 `ui.probe`：设置页那份是同一份数据，但在失败页就地能跑，
 * 用户不用为了「看一眼为什么失败」再翻三层菜单。
 */
@Composable
fun DiagnoseSheet(
    t: InkTokenSet,
    ui: AppState,
    vm: TimetableViewModel,
    onDismiss: () -> Unit,
) {
    AcademicSheet(t = t, title = "接口诊断", onDismiss = onDismiss) {
        ProbePanel(t = t, ui = ui, onRun = { vm.runProbe() })
    }
}

/** 成绩详情浮层（同样三层：主 → 次 → 原始） */
@Composable
fun ScoreDetailSheet(
    t: InkTokenSet,
    score: ScoreItem,
    onDismiss: () -> Unit,
) {
    AcademicSheet(
        t = t,
        title = score.name.ifBlank { "（未命名课程）" },
        subtitle = score.term.ifBlank { "学期未标注" },
        onDismiss = onDismiss,
    ) {
        SheetField(
            t,
            "成绩",
            score.score.ifBlank { if (score.grade.isNotBlank()) score.grade else "" },
            mono = score.scoreValue != null,
            emphasize = true,
        )
        SheetField(t, "等级", score.grade, emphasize = true)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
        Spacer(Modifier.height(8.dp))
        SheetField(t, "英文名", score.englishName)
        SheetField(t, "课程属性", score.category)
        SheetField(t, "课程类型", score.courseType)
        SheetField(t, "学分", score.credit, mono = true)
        SheetField(t, "学时", score.hours, mono = true)
        SheetField(t, "课程号", score.code, mono = true)
        SheetField(t, "课序号", score.classNo, mono = true)
        SheetField(t, "考试日期", score.examDate, mono = true)
        SheetField(t, "学期代码", score.termCode, mono = true)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.rule))
        RawJsonBlock(t, score.raw, score.fields)
    }
}
