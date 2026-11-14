package cn.dkarbon.enroute.ui.user

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 「我的」页方案二的磁贴件（票 16 §B）。
 *
 * ## 只借布局轴，不借材质轴
 * 两列 span 只是为了「五个核心数字一屏可见」；**材质一律留在本仓库现有语汇**：
 * 0 圆角（[RectangleShape]）+ 1dp 描边（[Stroke.hairline]）+ **无 elevation / 无渐变 / 无阴影**。
 * 也**不做四色分区** —— 六格四色会把纸墨风变成看板。
 *
 * ## 层级怎么来
 * 只靠三样：**磁贴尺寸**（span2 / 1×1 / 76dp 工具格）、**数字字号**（26 / 22 / 20）、
 * **描边轻重**（主磁贴 hairline，工具格同宽但内边距更紧）。**不新增任何 token**。
 */

/** 磁贴内边距：比大卡（16dp）紧一档，让两列并排时不显臃肿 */
private val TilePadH = 12.dp
private val TilePadV = 10.dp

/** 一个磁贴的底子：面 + 1dp 边线 + 方角，可点 */
@Composable
private fun TileSurface(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    Column(
        modifier
            .clip(RectangleShape)
            .background(tint ?: t.card)
            .border(Stroke.hairline, t.outline)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = TilePadH, vertical = TilePadV),
        content = content,
    )
}

/** 磁贴标题行：小标题（左）+ 可选说明（右，等宽数字） */
@Composable
private fun TileHead(title: String, hint: String = "", hintColor: Color? = null) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 10.sp, color = t.inkFaint, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (hint.isNotBlank()) {
            Text(hint, fontSize = 9.5.sp, color = hintColor ?: t.inkFaint, fontFamily = NumericFont)
        }
    }
}

/** 跨两列的大磁贴（考试倒计时 / 空闲教室） */
@Composable
fun WideTile(
    title: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    TileSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        TileHead(title, hint)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/**
 * 两列网格容器：子项请用 [HalfTile] 或 [ToolTile]（它们会自己 `weight(1f)`）。
 *
 * 为什么不用 LazyVerticalGrid：整页已经在 `verticalScroll` 里，嵌套懒加载容器会报
 * 「无限高度约束」；而这里条目数固定（≤6），直接 Row 排布更省也更好控。
 */
@Composable
fun TileRow(
    modifier: Modifier = Modifier,
    gap: Dp = Space.s,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/**
 * 1×1 学业磁贴：**大数字 + 短副行**（`64%` / `还差 42.5 学分`）。
 *
 * 数字统一 [NumericFont] 且底部对齐 —— 两列并排时数字要成列。
 */
@Composable
fun RowScope.HalfTile(
    title: String,
    value: String,
    sub: String,
    unknown: Boolean,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    TileSurface(onClick = onClick, modifier = Modifier.weight(1f)) {
        TileHead(title)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = if (unknown) 20.sp else 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold,
            color = if (unknown) t.inkFaint else t.ink,
            fontFamily = NumericFont,
        )
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 9.5.sp, lineHeight = 13.sp, color = t.inkFaint)
    }
}

/**
 * 工具格（76dp）：只有一行文字 + 可选副字，**不放图标**（门禁 A4：不许图标阵）。
 *
 * 76dp 是票面给的数：四格两行正好落在首屏内，且比磁贴矮一档，层级自然下沉。
 */
@Composable
fun RowScope.ToolTile(
    label: String,
    sub: String,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    TileSurface(onClick = onClick, modifier = Modifier.weight(1f).heightIn(min = 76.dp)) {
        Spacer(Modifier.weight(1f))
        Text(label, fontSize = 12.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(sub, fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
        Spacer(Modifier.weight(1f))
    }
}

/**
 * 分组标题：`教务` / `校园` / `本机` + 右侧状态前提（门禁 B1/B2）。
 *
 * **锚定数据来源**：教务 = 需要 URP 登录态；校园 = 需要位置/离线图；本机 = 只管本地存储。
 * 换成「常用功能 / 学习工具 / 其他」，下面每一行的归属就讲不清了 —— 这正是评审的黑名单第 5 条。
 */
@Composable
fun GroupHead(title: String, premise: String, premiseColor: Color? = null) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(top = Space.m, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 10.5.sp, color = t.inkFaint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(Space.s))
        Box(Modifier.weight(1f).height(Stroke.hairline).background(t.rule))
        if (premise.isNotBlank()) {
            Spacer(Modifier.width(Space.s))
            Text(
                premise,
                fontSize = 9.5.sp,
                color = premiseColor ?: t.inkFaint,
                fontFamily = NumericFont,
            )
        }
    }
}

/**
 * 折叠区（本机组）：头 48–56dp、`animateDpAsState(180ms)` 过渡、**不新增长按**。
 *
 * 头里就是该组的状态前提（课表门数 + 周次），收起时也能读到 —— 这是「诊断折叠」而不是
 * 「把功能藏起来」。
 */
@Composable
fun FoldSection(
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    val headHeight by animateDpAsState(
        targetValue = if (expanded) 56.dp else 50.dp,
        animationSpec = tween(180),
        label = "foldHead",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = headHeight)
                .clip(RectangleShape)
                .background(t.card)
                .border(Stroke.hairline, t.outline)
                .clickable(onClick = onToggle)
                .padding(horizontal = TilePadH, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("本机状态", fontSize = 12.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(summary, fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起" else "展开",
                tint = t.inkFaint,
                modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(t.card)
                    .border(Stroke.hairline, t.outline)
                    .padding(horizontal = TilePadH, vertical = 2.dp),
                content = content,
            )
        }
    }
}

/** 折叠区里的一行（本机状态明细）：label 左、value 右，等宽数字 */
@Composable
fun FoldRow(label: String, value: String) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.minTouch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.5.sp, color = t.inkSoft)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.sp, color = t.ink, fontFamily = NumericFont)
    }
}
