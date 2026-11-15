package cn.dkarbon.enroute.ui.user

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.ledger.LedgerGroupBreak
import cn.dkarbon.enroute.ui.ledger.LedgerGroupTitle
import cn.dkarbon.enroute.ui.ledger.LedgerTnum
import cn.dkarbon.enroute.ui.ledger.ledgerPress
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 「学业」组的一格（2×2 四方格里的 1/4）。
 *
 * 为什么四方格比平铺更适合数值：四个数并排成两行两列，一眼就能横向比较
 * 「绩点 vs 学分」「排名 vs 培养方案」；平铺成四行要上下扫四行才凑得齐，
 * 数值之间的比较关系被行间距拆散了。格子只画 1dp 边框不填底色，仍是纸面。
 *
 * @param value 上方大字（衬线 + tnum 等宽数字）。[missing] 时忽略本字段、显示「暂缺」。
 */
data class UserStatCell(
    val label: String,
    val value: String = "",
    val missing: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

/**
 * 「学业」分组：组间短横线 + 组标题 + [UserStatGrid]。
 * 复用 [LedgerGroupTitle] / [LedgerGroupBreak]，和「身份/学籍」同一套组节奏，
 * 只是组内容从账目行换成四方格。
 */
@Composable
fun UserStatGroup(title: String, cells: List<UserStatCell>) {
    Spacer(Modifier.height(14.dp))
    LedgerGroupBreak()
    Spacer(Modifier.height(14.dp))
    LedgerGroupTitle(label = title, count = cells.size, collapsible = false, collapsed = false)
    Spacer(Modifier.height(4.dp))
    UserStatGrid(cells)
}

/**
 * 2 × 2 四方格：两行两列、相邻格之间 10dp 间隙。
 *
 * 四格高度相等靠两层保证：
 *  1. 外层 Column `height(IntrinsicSize.Min)` —— 高度取内容最小固有高度，不随文字长短漂移；
 *  2. 两行各 `weight(1f)` 平分列高、格内 `fillMaxHeight()` 撑满整行。
 * 每格数值/指标名都 maxLines=1 不换行，固有高度一致，所以四格天然等高。
 */
@Composable
fun UserStatGrid(cells: List<UserStatCell>) {
    Column(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        cells.chunked(2).forEachIndexed { rowIndex, rowCells ->
            if (rowIndex > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                rowCells.forEachIndexed { colIndex, cell ->
                    if (colIndex > 0) Spacer(Modifier.width(10.dp))
                    UserStatTile(cell, Modifier.weight(1f).fillMaxHeight())
                }
                // 兜底：末行只有一格时，右侧补等宽占位，格子宽度仍与上面一行对齐
                if (rowCells.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 单个四方格。整格可点 ≥48dp（内容约 64dp），按压态 = 2% 墨（与账目行同源），
 * 1dp 边框 + 圆角 10dp、不填底色。clip 保证按压底色也贴着圆角，不溢出方角。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserStatTile(cell: UserStatCell, modifier: Modifier) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .border(1.dp, t.outline, shape)
            .clip(shape)
            .then(
                if (cell.onClick != null) {
                    Modifier.ledgerPress(ink = t.ink, onClickLabel = cell.label, onClick = cell.onClick!!)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (cell.missing) "暂缺" else cell.value,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (cell.missing) t.inkFaint else t.ink,
            style = LedgerTnum,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            cell.label,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = t.inkSoft,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
