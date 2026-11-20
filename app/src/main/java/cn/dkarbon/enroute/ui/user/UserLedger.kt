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
import cn.dkarbon.enroute.ui.kit.PaperTex
import cn.dkarbon.enroute.ui.kit.paperTexture
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
@Composable
fun UserGroupBox(
    title: String,
    entries: List<UserLedgerEntry>,
    first: Boolean,
    tex: PaperTex = PaperTex.NONE,
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
            .paperTexture(tex = tex, t = t, radius = 10.dp)
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
