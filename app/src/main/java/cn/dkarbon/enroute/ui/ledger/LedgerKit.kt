package cn.dkarbon.enroute.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.ui.feed.DeadlineTone
import cn.dkarbon.enroute.ui.feed.FeedRows
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.SerifFont
import cn.dkarbon.enroute.ui.theme.Space
import java.time.LocalDate

/**
 * 账页 v3 的展示件（只服务这套主题，不做通用化）。
 *
 * 设计来源：`docs/设计-账页v3-方案.md` §1.1 骨架图 + 批 1 修正：
 *   · 资讯流页边槽放**日期**（11-11）不放行号 —— 行号留给「我的」和设置目录；
 *   · 相邻条目之间加 1px 极淡分隔线（inkFaint 再降 alpha 到 0.5），缩进到正文左沿，
 *     组内最后一条之后不加。
 *
 * 这里的行是「排版语言」：层级只用位置 / 字号 / 字重 / 留白 / 颜色三档（ink / inkSoft / inkFaint），
 * 不画 Card、不画描边盒子、不画圆角阴影。
 */

/** 页边槽宽：放「11-11」这类 5 字符日期（10.5sp 衬线等宽），右对齐。行号「01」只需 22dp，日期要更宽。 */
internal val LedgerGutter = 34.dp

/** 账页数字：衬线 + tnum（等宽数字对齐）。正文仍用系统黑体，只有数字走衬线。 */
val LedgerTnum = TextStyle(fontFamily = SerifFont, fontFeatureSettings = "tnum")

/** 页眉日期「11-11」与空日期的占位：解析不到就显示「—」，绝不编日期。 */
internal fun ledgerDate(d: String): String =
    if (d.length >= 10) d.substring(5) else d.ifBlank { "—" }

/**
 * 整行按压态：行底色 +2% 墨（方案 §3 触区口径），无水波纹无胶囊。
 * 无卡之后热区靠整行承载，按压反馈要轻 —— 2% 是「知道点到了」的最低可感值。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.ledgerPress(
    ink: Color,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return this
        .background(if (pressed) ink.copy(alpha = 0.02f) else Color.Transparent)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = onClickLabel,
                    onLongClickLabel = onLongClickLabel,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            } else {
                Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = onClickLabel,
                    onClick = onClick,
                )
            },
        )
}

/**
 * 常驻页眉：章节名 15sp/700 + 状态 11.5sp + 右侧「查」块 + 1dp 页眉线。
 * [onMore] 非空时在「查」左侧补一个「⋯」入口（密度切换/全部已读等旧功能的家）。
 */
@Composable
fun LedgerHeader(
    title: String,
    status: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    val t = InkTokens.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = Space.l, end = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.weight(1f))
            Text(
                status,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = t.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 状态是页眉的定位注，给它一个上限宽，别把右侧入口挤掉
                modifier = Modifier.widthIn(max = 164.dp),
            )
            if (onMore != null) {
                Box(
                    Modifier.size(48.dp).clickable(onClickLabel = "更多操作", onClick = onMore),
                    contentAlignment = Alignment.Center,
                ) { Text("⋯", fontSize = 14.sp, color = t.inkSoft) }
            }
            Box(
                Modifier.size(48.dp).clickable(onClickLabel = "搜索", onClick = onSearch),
                contentAlignment = Alignment.Center,
            ) { Text("查", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.inkSoft) }
        }
        // 页眉线用 inkFaint：夜里不发亮（方案 §3 深色口径）
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.inkFaint))
    }
}

/**
 * 栏目名 11.5sp/500 + 短横线（导引线）+ 右端组条数。
 * 竞赛组可折叠：点击整行切换，收起时右侧箭头变「▸」。
 */
@Composable
fun LedgerGroupTitle(
    label: String,
    count: Int,
    collapsible: Boolean,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
) {
    val t = InkTokens.current
    Row(
        modifier
            .fillMaxWidth()
            // 吸顶时要把下面滚动上来的条目盖住，这里必须垫一层纸底（不是视觉容器框）
            .background(t.paper)
            .heightIn(min = 28.dp)
            .then(
                if (onToggle != null) {
                    Modifier.clickable(onClickLabel = "展开或折叠 $label", onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = t.inkSoft)
        Spacer(Modifier.width(8.dp))
        // 短横线：栏目名与条数之间的导引线，细且淡 —— 再淡一点就看不见，再重一点就成了表格线
        Box(Modifier.weight(1f).height(1.dp).background(t.inkFaint.copy(alpha = 0.35f)))
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), fontSize = 11.5.sp, color = t.inkFaint, style = LedgerTnum)
        if (collapsible) {
            Spacer(Modifier.width(6.dp))
            Text(if (collapsed) "▸" else "▾", fontSize = 11.5.sp, color = t.inkFaint)
        }
    }
}

/**
 * 组间短横线：56dp 长、缩进到正文左沿（页边槽不画线）。
 * 与条目间的细分隔线不同：这条是「上一章结束」的标记，和 28dp 留白一起用。
 */
@Composable
fun LedgerGroupBreak(modifier: Modifier = Modifier) {
    val t = InkTokens.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(LedgerGutter))
        Box(Modifier.width(56.dp).height(1.dp).background(t.inkFaint.copy(alpha = 0.5f)))
    }
}

/**
 * 资讯条目行：页边槽日期 + 标题 17sp/600 + 摘要 13sp/22sp（最多 2 行）+ 元信息 11.5sp/16sp。
 *
 * 为什么摘要颜色用 inkSoft 而不是 inkFaint：摘要 13sp 是「第二正文」，inkFaint 在纸底只有 5.36:1，
 * 长句会累；inkSoft（8.73:1）读起来才不眯眼。元信息才是 inkFaint 的定位注。
 *
 * @param compact 密度切换（原「紧凑卡」）：true 时只显示标题 + 元信息，摘要退场。
 * @param lastInGroup 组内最后一条之后不画分隔线（修正 B）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedgerRow(
    item: FeedItem,
    today: LocalDate,
    compact: Boolean,
    lastInGroup: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val haptic = LocalHapticFeedback.current
    val unread = item.readAt == null
    val title = if (item.titleTruncated) item.title.trimEnd('.', '…') + "…" else item.title
    val summary = item.summary.replace("id=\"vsb_content\">", "").trim()
    val dl = if (item.category == "竞赛") FeedRows.deadlineLabel(item, today) else null

    Row(
        modifier
            .fillMaxWidth()
            .ledgerPress(
                ink = t.ink,
                onClickLabel = "打开 ${item.title}",
                onLongClickLabel = "${item.title} 更多操作",
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // 页边槽：日期右对齐、衬线等宽（修正 A：资讯流不放行号）
        Box(
            Modifier.width(LedgerGutter).padding(top = 5.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Text(
                ledgerDate(item.publishedAt),
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = t.inkFaint,
                style = LedgerTnum,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                // 已读退后：不靠红点，靠标题颜色从 ink 降到 inkSoft（层级只走颜色三档）
                color = if (unread) t.ink else t.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact && summary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    summary.take(100),
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    color = t.inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.category, fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint)
                if (dl != null) {
                    Text(" · ", fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint)
                    Text(
                        dl.text,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = when (dl.tone) {
                            DeadlineTone.HOT -> t.brick
                            DeadlineTone.WARM -> t.amber
                            DeadlineTone.DEAD -> t.inkFaint
                        },
                        style = LedgerTnum,
                    )
                }
                if (item.platformState.isNotBlank()) {
                    Text(" · ", fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint)
                    Text(item.platformState, fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint)
                }
                Spacer(Modifier.weight(1f))
                if (item.starred) {
                    Text("藏", fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = t.amber)
                }
            }
            if (!lastInGroup) {
                Spacer(Modifier.height(9.dp))
                // 修正 B：相邻条目间的边界 —— 1px、inkFaint 且 alpha 再降到 0.5，
                // 缩进到正文左沿（本 Column 就是正文栏，天然不延伸到页边槽）
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.inkFaint.copy(alpha = 0.5f)))
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

/** 页脚入口（纯文字，无图标无胶囊）。 */
data class LedgerNavEntry(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * 页脚：1dp 线 + 纯文字三入口（48dp 触区）+ 右侧 folio。
 * 资讯页本批不挂它 —— 底部导航在 EnRouteRoot（ui/ 包，不在本批允许改动范围），
 * 这里先把件备好，等壳换骨架时直接用。
 */
@Composable
fun LedgerFooter(
    entries: List<LedgerNavEntry>,
    folio: String?,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    Column(modifier.fillMaxWidth().background(t.paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.inkFaint))
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
            entries.forEach { e ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(onClickLabel = e.label, onClick = e.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        e.label,
                        fontSize = 12.sp,
                        fontWeight = if (e.selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (e.selected) t.ink else t.inkSoft,
                    )
                }
            }
        }
        if (folio != null) {
            Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = 6.dp)) {
                Spacer(Modifier.weight(1f))
                Text(folio, fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint, style = LedgerTnum)
            }
        }
    }
}
