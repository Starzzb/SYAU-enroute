package cn.dkarbon.enroute.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import java.time.LocalDate

/**
 * 学业主页的卡片。
 *
 * 设计取向：**一张卡只说一件事**，数字用等宽字体、右对齐；卡片之间靠 1dp 边线分隔，
 * 不用阴影不用圆角（沿用全 App 的硬朗体系）。
 *
 * 数据纪律写在每张卡的空值分支里：拿不到就说「暂缺 / 连校园网」，不显示 0。
 */

/** 卡片底子：面 + 1dp 边线 + 16dp 内边距，可点 */
@Composable
private fun HomeCard(
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.cardPad, vertical = Space.m),
        content = content,
    )
}

/** 小标题 + 右侧说明（卡片顶行统一这个结构） */
@Composable
private fun CardHead(title: String, hint: String = "", hintColor: androidx.compose.ui.graphics.Color? = null) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 11.sp, color = t.inkFaint, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (hint.isNotBlank()) {
            Text(
                hint,
                fontSize = 10.sp,
                color = hintColor ?: t.inkFaint,
                fontFamily = NumericFont,
            )
        }
    }
}

// ── ① 身份 ────────────────────────────────────────────────

/**
 * 身份卡：姓名 + 副标题 + 徽标。
 *
 * 三态（票 04）各有各的样子，**不能都画成「未登录」**：
 *   · [SessionState.None] 整块就是登录入口（用户此刻只能做这一件事）；
 *   · [SessionState.Active] 展示身份，不可点；
 *   · [SessionState.Expired] 保留身份展示，但**可点** → 一键去续期，副标题直说「会话已过期」。
 */
@Composable
fun IdentityCard(
    profile: StudentProfile,
    fallbackName: String,
    sessionState: SessionState,
    sessionCheckMsg: String,
    loading: Boolean,
    onLogin: () -> Unit,
) {
    val t = InkTokens.current
    val name = profile.name.ifBlank { fallbackName }
        .ifBlank { SessionText.fallbackName(sessionState) }
    val subtitle = HomeModel.profileSubtitle(profile)
    val badges = HomeModel.profileBadges(profile)
    val expired = sessionState == SessionState.Expired

    HomeCard(onClick = if (SessionText.identityClickable(sessionState)) onLogin else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(t.cardAlt),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    HomeModel.initialOf(name).ifBlank { "?" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                )
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    SessionText.identitySubtitle(
                        state = sessionState,
                        profileSubtitle = subtitle,
                        loading = loading,
                        checkMsg = sessionCheckMsg,
                    ),
                    fontSize = 11.sp,
                    // 过期用砖红：这是「需要注意但数据没丢」，不是错误红
                    color = if (expired) t.brick else t.inkFaint,
                )
            }
        }

        if (badges.isNotEmpty()) {
            Spacer(Modifier.height(Space.m))
            Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
            Spacer(Modifier.height(Space.s))
            // 徽标两行铺开：每行最多 2 个，等宽分配，数字列用等宽字体
            badges.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    row.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 9.5.sp, color = t.inkFaint)
                            Text(
                                value,
                                fontSize = 12.sp,
                                color = t.ink,
                                fontWeight = FontWeight.Medium,
                                fontFamily = NumericFont,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── ② 学分进度 ────────────────────────────────────────────

/**
 * 学分进度：横条 + `106 / 165`。
 *
 * 分母来自培养方案（学校端算的）；拿不到分母就**不画条**，只报已得学分 ——
 * 一条永远 0% 的进度条比没有更误导。
 */
@Composable
fun CreditProgressCard(
    credit: CreditProgress,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    val t = InkTokens.current
    val percent = credit.percent
    // 有没有真数据：需修或已得至少一个为正。都为 0 时不能显示「0 学分」——
    // 那会被读成「我一学分都没有」，而实际是「还没抓到」。
    val hasData = credit.earnedXf > 0.0 || credit.requiredXf > 0.0
    HomeCard(onClick = onOpen) {
        CardHead(
            title = credit.planName.ifBlank { "培养方案进度" },
            hint = percent?.let { "$it%" } ?: if (loading) "读取中" else "",
            hintColor = t.primary,
        )
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (hasData) HomeModel.trimNum(credit.earnedXf) else "—",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasData) t.ink else t.inkFaint,
                fontFamily = NumericFont,
            )
            val unit = when {
                credit.requiredXf > 0.0 -> " / ${HomeModel.trimNum(credit.requiredXf)} 学分"
                credit.earnedXf > 0.0 -> " 学分"
                else -> ""
            }
            if (unit.isNotBlank()) {
                Text(
                    unit,
                    fontSize = 12.sp,
                    color = t.inkFaint,
                    fontFamily = NumericFont,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            val count = HomeModel.courseCountLabel(credit)
            if (count.isNotBlank()) {
                Text(count, fontSize = 10.5.sp, color = t.inkSoft, modifier = Modifier.padding(bottom = 5.dp))
            }
        }

        if (percent != null) {
            Spacer(Modifier.height(Space.s))
            // 进度条：底槽 + 填充，全部直角，填充用主色
            Box(Modifier.fillMaxWidth().height(6.dp).background(t.cardAlt)) {
                Box(
                    Modifier
                        .fillMaxWidth(percent / 100f)
                        .height(6.dp)
                        .background(t.primary),
                )
            }
            Spacer(Modifier.height(Space.xs))
            val remain = HomeModel.creditRemaining(credit)
            if (remain != null) {
                Text(
                    "还差 ${HomeModel.trimNum(remain)} 学分 · 数据来自培养方案",
                    fontSize = 9.5.sp,
                    color = t.inkFaint,
                )
            }
        } else {
            Spacer(Modifier.height(Space.xs))
            Text(
                if (loading) "正在读取培养方案…" else "方案进度暂缺（需登录且在校内网）",
                fontSize = 9.5.sp,
                color = t.inkFaint,
            )
        }
    }
}

// ── ③ 成绩概览 ────────────────────────────────────────────

/**
 * 成绩概览：加权绩点（学校给的绩点）+ 通过门数。
 *
 * 绩点缺失时显示「暂缺」而不是 0.00 —— 0.00 会被当成真的挂科绩点。
 */
@Composable
fun ScoreOverviewCard(
    stats: ScoreStats,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    val t = InkTokens.current
    HomeCard(onClick = onOpen) {
        CardHead(
            title = "成绩概览",
            hint = if (stats.terms.isNotEmpty()) "${stats.terms.size} 个学期" else if (loading) "读取中" else "",
        )
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("加权绩点", fontSize = 9.5.sp, color = t.inkFaint)
                Text(
                    HomeModel.gpaLabel(stats),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stats.weightedGpa == null) t.inkFaint else t.ink,
                    fontFamily = NumericFont,
                )
            }
            Spacer(Modifier.width(Space.l))
            Column {
                Text("总学分", fontSize = 9.5.sp, color = t.inkFaint)
                Text(
                    if (stats.creditSum > 0.0) HomeModel.trimNum(stats.creditSum) else "—",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = t.inkSoft,
                    fontFamily = NumericFont,
                )
            }
            Spacer(Modifier.weight(1f))
            val count = HomeModel.scoreCountLabel(stats)
            if (count.isNotBlank()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(count, fontSize = 10.5.sp, color = if (stats.failed > 0) t.brick else t.olive)
                    if (stats.weightedGpa != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(HomeModel.gpaHint(stats), fontSize = 9.sp, color = t.inkFaint)
                    }
                }
            }
        }
        if (stats.isEmpty) {
            Spacer(Modifier.height(Space.xs))
            Text(
                if (loading) "正在读取成绩…" else "暂无成绩数据（需登录且在校内网）",
                fontSize = 9.5.sp,
                color = t.inkFaint,
            )
        }
    }
}

// ── ④ 下一场考试 ──────────────────────────────────────────

/**
 * 下一场考试：课名 + 倒计时 + 地点。
 *
 * 全部考完时整张卡换成「本学期 X 场考试已结束」——**不显示一张空的倒计时卡**。
 */
@Composable
fun NextExamCard(
    exams: List<ExamItem>,
    today: LocalDate,
    loading: Boolean,
    onOpen: () -> Unit,
) {
    val t = InkTokens.current
    val next = HomeModel.nextExam(exams, today)
    val finished = HomeModel.finishedExamCount(exams, today)

    HomeCard(onClick = onOpen) {
        CardHead(title = "下一场考试", hint = if (exams.isNotEmpty()) "共 ${exams.size} 场" else "")
        Spacer(Modifier.height(Space.s))
        if (next == null) {
            Text(
                when {
                    loading -> "正在读取考试安排…"
                    exams.isEmpty() -> "还没有考试安排（需登录且在校内网）"
                    else -> "已考完 $finished 场，本学期没有待考的了"
                },
                fontSize = 11.5.sp,
                color = t.inkFaint,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 倒计时块：数字大、单位小，像刻度
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val label = HomeModel.countdownLabel(next.date, today).orEmpty()
                    val big = label.removeSuffix(" 天后").removeSuffix("天")
                    Text(
                        big,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (label == "今天" || label == "明天") t.brick else t.ink,
                        fontFamily = NumericFont,
                    )
                    Text(
                        when {
                            label.endsWith("天后") -> "天后"
                            label == "今天" || label == "明天" -> ""
                            else -> label
                        },
                        fontSize = 9.sp,
                        color = t.inkFaint,
                    )
                }
                Spacer(Modifier.width(Space.m))
                Box(Modifier.width(Stroke.hairline).height(34.dp).background(t.rule))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        next.name.ifBlank { "（未命名课程）" },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = t.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        listOf(next.date, next.startClock.takeIf { it.isNotBlank() }?.let { "$it 开考" }.orEmpty())
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 10.5.sp,
                        color = t.inkSoft,
                        fontFamily = NumericFont,
                    )
                    val place = listOf(next.campus, next.building, next.room)
                        .filter { it.isNotBlank() }.joinToString(" ")
                    if (place.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(place, fontSize = 10.sp, color = t.inkFaint)
                    }
                }
            }
        }
    }
}

/** 卡片之间的固定间距，避免每个调用点各写一个 Spacer 高度 */
@Composable
fun CardGap() {
    Spacer(Modifier.height(Space.cardGap))
}

/** 一行等宽小字（用于卡片内的次要说明汇总） */
@Composable
fun HomeFootNote(text: String) {
    val t = InkTokens.current
    Text(
        text,
        fontSize = 9.5.sp,
        color = t.inkFaint,
        fontFamily = NumericFont,
        modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
    )
}

/** 分组小标题（页内分区用，不做卡片） */
@Composable
fun SectionLabel(text: String) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(text, fontSize = 11.sp, color = t.inkFaint, fontWeight = FontWeight.Medium)
    }
}
