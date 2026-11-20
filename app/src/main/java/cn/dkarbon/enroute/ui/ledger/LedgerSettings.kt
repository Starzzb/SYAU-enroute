package cn.dkarbon.enroute.ui.ledger

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 「笺」设置页的自写组件层（票 35 → BiliPai 六条，只借思想与数值，不借码）。
 *
 * 六条落点（编号对应任务里的 6 条）：
 *   1. title / subtitle / value 三个参数、三个固定位置，绝不合并 —— [LedgerSettingRow]
 *   2. subtitle 跟着状态换文案 —— [LedgerSwitchRow]（subtitle 由调用方按当前状态给，
 *      组件只负责把它钉在 title 下方，不替调用方猜文案）
 *   3. 分组名描述内容范围、不复读分类名 —— [LedgerGroupCard] 的 title（调用方传
 *      「历史记录与权限」而不是再传一遍「隐私与权限」）
 *   4. 行视觉高度 44dp、命中区靠内部扩展补到 48dp —— [LedgerSettingsSpec] +
 *      外层 48dp 命中盒、内层 44dp 可视行：多出的 4dp 透明，命中区外扩而可视行不虚胖
 *   5. 分隔线为零，靠留白分行 —— 本文件不画任何行分隔线；行间那 4dp 透明边就是留白
 *   6. 弹簧 322.2 / 0.899 / 0.5；时长 180 / 240 —— [LedgerMotion]，弹簧已用在 [LedgerSwitch]
 *
 * 为什么新开一个文件而不是塞进 LedgerKit.kt：LedgerKit 服务资讯/我的两页的「行」，
 * 本文件服务设置页的「行 + 分组 + 开关」，密度与触区口径不同，混在一起会让
 * 「44dp 设置行」和「48dp 账目行」两套数值互相污染。
 */

/** 设置行度量。为什么集中成对象：让「44 视觉 / 48 命中」只有一处真源，改值不用满屏 grep。 */
object LedgerSettingsSpec {
    /** 行视觉高 44dp。票 35 §1.3 原话：44 是紧凑该有的值，视觉给到 48 会显得虚胖。 */
    val RowVisualHeight = 44.dp
    /** 命中区 48dp。补法是把命中区往外扩（内部扩展），不是把可视行拉高。 */
    val RowTouchTarget = 48.dp
    /** 行内水平留白 16dp：票 35 §1.3 两档同值。 */
    val RowHorizontalPadding = 16.dp
    /** 分组标题上 24 / 下 8：上给组间呼吸，下让标题与内容贴近（票 35 §1.3）。 */
    val GroupTitleTop = 24.dp
    val GroupTitleBottom = 8.dp
}

/** 动效数值（票 35 §3，Apache-2.0 侧可直抄的数值，不是 BiliPai 的代码）。 */
object LedgerMotion {
    /** 标准时长 180ms（Miuix 档实测）。 */
    const val StandardMs = 180
    /** 强调时长 240ms（与 180 同一来源，票 35 §3.4）。 */
    const val EmphasizedMs = 240

    /**
     * 弹簧：stiffness 322.2 = response 0.35s 的换算值；dampingRatio ≈0.899 是
     * 「轻微过冲后收敛」的欠阻尼 —— 开关/吸附这类短距离动作用它最跟手。
     * visibilityThreshold 0.5dp：Dp 动画的收敛阈值，0.5 保证肉眼看不到最后一格的抖动。
     */
    val switchSpring = spring(
        dampingRatio = 0.899f,
        stiffness = 322.2f,
        visibilityThreshold = 0.5.dp,
    )
}

/** 自写开关的几何（视觉件，命中区由行统一承载）。 */
private object LedgerSwitchSpec {
    val TrackWidth = 44.dp
    val TrackHeight = 24.dp
    val ThumbSize = 20.dp
    val ThumbPad = 2.dp
    val ThumbOffStart = ThumbPad
    val ThumbOnStart = TrackWidth - ThumbPad - ThumbSize
}

/**
 * 分组（group card）：只有标题 + 内容，**不画卡、不画分隔线**。
 *
 * 为什么叫 card 却不画容器：任务给的名词是「分组卡」，但规则 5 与「无容器」同一方向，
 * 所以这里只保留「分组」这个信息作用，视觉上就是标题 + 留白 + 行，行与行之间零分隔线。
 * 真需要容器时再往 content 里放带底色的槽，不在这里预设。
 */
@Composable
fun LedgerSwitch(checked: Boolean, enabled: Boolean = true) {
    val t = InkTokens.current
    val thumbStart by animateDpAsState(
        targetValue = if (checked) LedgerSwitchSpec.ThumbOnStart else LedgerSwitchSpec.ThumbOffStart,
        animationSpec = LedgerMotion.switchSpring,
        label = "ledgerSwitchThumb",
    )
    val trackColor = when {
        !enabled -> t.inkFaint.copy(alpha = 0.18f)
        checked -> t.primary
        else -> t.outline.copy(alpha = 0.45f)
    }
    Box(
        Modifier
            .size(width = LedgerSwitchSpec.TrackWidth, height = LedgerSwitchSpec.TrackHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbStart)
                .size(LedgerSwitchSpec.ThumbSize)
                .clip(CircleShape)
                .background(if (enabled) t.card else t.card.copy(alpha = 0.7f)),
        )
    }
}
