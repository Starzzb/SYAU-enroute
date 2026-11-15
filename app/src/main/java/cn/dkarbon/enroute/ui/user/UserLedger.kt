package cn.dkarbon.enroute.ui.user

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.ledger.LedgerGroupBreak
import cn.dkarbon.enroute.ui.ledger.LedgerGroupTitle
import cn.dkarbon.enroute.ui.ledger.LedgerGutter
import cn.dkarbon.enroute.ui.ledger.LedgerTnum
import cn.dkarbon.enroute.ui.ledger.ledgerPress
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 「我的」页的账目行（账页 v3 批 1）。
 *
 * 磁贴阵 → 行：行号页边槽（22dp 右对齐）+ 科目 + 点状引导线 + 值/单位（右对齐成一列）。
 * 数值列走 [LedgerTnum]（衬线 + tnum 等宽数字），空值显示「暂缺」，绝不填 0。
 * 入口行（教务/校园/本机）右端是「真实状态徽标 + ›」，没有可展示状态就只有 › ——
 * 「暂缺」是给缺失的数值用的，不给一条本来就只是入口的行。
 */
class UserLedgerEntry(
    val subject: String,
    val value: String = "",
    val unit: String = "",
    val missing: Boolean = false,
    val sub: String? = null,
    val badge: String = "",
    val chevron: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * 一条账目行。整行可点 ≥48dp，按压态 = 行底色 +2% 墨（无水波纹）。
 *
 * 行号槽 22dp + 12dp 间距 = 34dp，与 [LedgerGutter]（组间短横线的正文左沿）对齐，
 * 也和资讯页条目的正文左沿同宽 —— 两页的「行」起始位置一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserLedgerRow(
    index: Int,
    subject: String,
    value: String = "",
    unit: String = "",
    missing: Boolean = false,
    sub: String? = null,
    badge: String = "",
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (onClick != null) {
                    Modifier.ledgerPress(ink = t.ink, onClickLabel = subject, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 页边槽：行号 01/02/… 右对齐，衬线等宽，inkFaint
        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                index.toString().padStart(2, '0'),
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = t.inkFaint,
                style = LedgerTnum,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    subject,
                    fontSize = 16.sp,
                    lineHeight = 28.sp,
                    color = t.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill=true：科目占固定一半，引导线与数值列才不随科目长短左右漂移
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // 点状引导线：细且淡，把目光从科目引到右端的数值列（11dp 一个点、直径 1dp）
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.CenterStart) {
                    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                        val r = 0.5.dp.toPx()
                        val step = 11.dp.toPx()
                        var x = r
                        val y = size.height / 2f
                        while (x < size.width - r) {
                            drawCircle(color = t.inkFaint.copy(alpha = 0.35f), radius = r, center = Offset(x, y))
                            x += step
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (chevron) {
                    // 入口行：真实状态徽标（如「3 门」）在左，「›」在最右
                    if (badge.isNotBlank()) {
                        Text(badge, fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint, style = LedgerTnum)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("›", fontSize = 13.sp, lineHeight = 20.sp, color = t.inkFaint)
                } else {
                    // 数值列固定宽右对齐；单位单独一段、固定宽也右对齐。
                    // 两列常驻（无单位时空着），保证所有行的数值右边缘钉在同一 x。
                    Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                        if (missing) {
                            Text(
                                "暂缺",
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = t.inkFaint,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                value,
                                fontSize = 16.sp,
                                lineHeight = 28.sp,
                                color = t.ink,
                                fontWeight = FontWeight.Medium,
                                style = LedgerTnum,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) {
                        if (!missing && unit.isNotBlank()) {
                            Text(
                                unit,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = t.inkFaint,
                                style = LedgerTnum,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            if (sub != null) {
                Text(
                    sub,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = t.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 一个分组：组间 28dp 留白 + 短横线（第一组之前不加）→ 组标题 → 行，组内行间加
 * 1px 极淡分隔线（缩进到正文左沿，组内最后一行之后不加）。
 *
 * @param startIndex 该组第一行的账本行号（全局连续，跨组累计）
 */
@Composable
fun UserLedgerGroup(
    title: String,
    entries: List<UserLedgerEntry>,
    firstGroup: Boolean,
    startIndex: Int,
) {
    val t = InkTokens.current
    if (!firstGroup) {
        Spacer(Modifier.height(14.dp))
        LedgerGroupBreak()
        Spacer(Modifier.height(14.dp))
    }
    LedgerGroupTitle(label = title, count = entries.size, collapsible = false, collapsed = false)
    Spacer(Modifier.height(4.dp))
    entries.forEachIndexed { i, e ->
        UserLedgerRow(
            index = startIndex + i,
            subject = e.subject,
            value = e.value,
            unit = e.unit,
            missing = e.missing,
            sub = e.sub,
            badge = e.badge,
            chevron = e.chevron,
            onClick = e.onClick,
        )
        if (i < entries.size - 1) {
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(LedgerGutter))
                Box(Modifier.weight(1f).height(1.dp).background(t.inkFaint.copy(alpha = 0.5f)))
            }
            Spacer(Modifier.height(9.dp))
        }
    }
}

/**
 * 功能入口分组 box（2026-11-15 主人定：教务按钮放一个 box，其余的也是，确定归属）。
 *
 * 视觉：1dp 边框（[InkTokenSet.outline]，与「笺」主题的卡片描边同源）+ 圆角 10dp，
 * 不填底色 —— 盒内仍是纸面，只是用框把「可点的入口集合」圈成一团。
 * 组标题在 box 内顶部；每条入口一行（行高 44dp、命中区 48dp），行间 1dp 极淡分隔线。
 *
 * ## 与「资讯流无容器」不冲突
 * 资讯是**阅读流**：一行行正文顺着读，加盒子是打断阅读 —— 不放盒子。
 * 这里是**功能入口的集合**：可点的东西混在账目里看不出归属，放盒子更清楚。
 * 下一个代理：不要给资讯页加盒子。本页「身份/学籍」已合并进可展开身份卡
 * （见 UserIdentityCard，卡内含 2×2 四方格 UserStatGrid），不要在这里再重复给学业加盒子。
 *
 * @param first 是否第一组：非第一组时先画组间短横线（与 [UserLedgerGroup] 同一套节奏）
 */
@Composable
fun UserGroupBox(
    title: String,
    entries: List<UserLedgerEntry>,
    first: Boolean,
) {
    val t = InkTokens.current
    if (!first) {
        Spacer(Modifier.height(14.dp))
        LedgerGroupBreak()
        Spacer(Modifier.height(14.dp))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, t.outline, RoundedCornerShape(10.dp))
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
    ) {
        // 组标题在 box 内顶部：11.5sp 组名 + 右端条数（衬线等宽，与 LedgerGroupTitle 同语汇）
        Row(Modifier.fillMaxWidth().heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = t.inkSoft)
            Spacer(Modifier.weight(1f))
            Text(entries.size.toString(), fontSize = 11.5.sp, color = t.inkFaint, style = LedgerTnum)
        }
        entries.forEachIndexed { i, e ->
            UserGroupBoxRow(e)
            if (i < entries.size - 1) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(t.inkFaint.copy(alpha = 0.5f)))
            }
        }
    }
}

/**
 * box 内的一条入口。两种形态：
 * · 有 onClick（入口）→ 左侧科目 + 右端真实状态徽标 + 「›」；
 * · 无 onClick（状态行，如周次校准）→ 左侧科目 + 右对齐值/单位（无点线，点线留给学业账目行）。
 * 行高 44dp；命中区经 [minimumInteractiveComponentSize] 撑到 48dp。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserGroupBoxRow(entry: UserLedgerEntry) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .minimumInteractiveComponentSize()
            .then(
                if (entry.onClick != null) {
                    Modifier.ledgerPress(ink = t.ink, onClickLabel = entry.subject, onClick = entry.onClick!!)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.subject,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.onClick != null) {
            if (entry.badge.isNotBlank()) {
                Text(entry.badge, fontSize = 11.5.sp, lineHeight = 16.sp, color = t.inkFaint, style = LedgerTnum)
                Spacer(Modifier.width(6.dp))
            }
            Text("›", fontSize = 13.sp, lineHeight = 20.sp, color = t.inkFaint)
        } else if (entry.missing) {
            Text(
                "暂缺",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = t.inkFaint,
                textAlign = TextAlign.End,
            )
        } else {
            if (entry.value.isNotBlank()) {
                Text(entry.value, fontSize = 14.sp, lineHeight = 20.sp, color = t.ink, style = LedgerTnum)
            }
            if (entry.unit.isNotBlank()) {
                Spacer(Modifier.width(4.dp))
                Text(entry.unit, fontSize = 11.sp, lineHeight = 16.sp, color = t.inkFaint, style = LedgerTnum)
            }
        }
    }
}
