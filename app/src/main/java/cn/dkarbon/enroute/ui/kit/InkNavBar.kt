package cn.dkarbon.enroute.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.icons.AppIcons
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont

/**
 * 墨洗底部导航（m34 改版，2026-11-20）。
 *
 * 口径（主人 m03539）：
 * - **贴底、上部圆角 16dp**：不再是中心浮起的圆角托；
 * - **按键等距**：每栏 weight(1f)，图标文字垂直居中；
 * - **当前项**：浅底 `cardAlt` + 图标文字转墨色 + 下方 3dp 朱点（形态表达，不靠文字）；
 * - 顶边一条 1dp `outline` 细线，随上圆角走（水墨「细如毛笔」）。
 *
 * 旧的滑动橙色指示条去掉 —— 朱点即是当前项指示，墨洗口径不容许一条橙色实条。
 */
@Composable
fun InkNavBar(
    selected: Int,
    unread: Int = 0,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val corner = 16.dp
    val items = listOf(
        NavItem("课表", Icons.Default.DateRange),
        NavItem("资讯", AppIcons.Article),
        NavItem("我的", Icons.Default.Person),
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .background(t.card)
            .drawBehind {
                val r = corner.toPx()
                drawPath(
                    path = Path().apply {
                        moveTo(0f, r)
                        quadraticBezierTo(0f, 0f, r, 0f)
                        lineTo(size.width - r, 0f)
                        quadraticBezierTo(size.width, 0f, size.width, r)
                    },
                    color = t.outline,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                InkNavTab(
                    label = item.label,
                    icon = item.icon,
                    selected = i == selected,
                    badge = if (i == 1 && selected != 1) unread else 0,
                    modifier = Modifier.weight(1f),
                ) { onSelect(i) }
            }
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)

@Composable
private fun InkNavTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    val tint = if (selected) t.ink else t.inkFaint
    Column(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) t.cardAlt else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (badge > 0) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = t.primary,
                        contentColor = if (t.isDark) Color(0xFF191713) else Color.White,
                    ) {
                        Text(
                            if (badge > 99) "99+" else "$badge",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NumericFont,
                        )
                    }
                },
            ) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = tint,
        )
        Spacer(Modifier.height(1.dp))
        // 当前项：3dp 朱点。未选中留同尺寸空位，避免切换时图标上下跳动。
        if (selected) {
            Box(Modifier.size(3.dp).clip(CircleShape).background(t.rust))
        } else {
            Spacer(Modifier.size(3.dp))
        }
    }
}
