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