package cn.dkarbon.enroute.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.dkarbon.enroute.ui.feed.FeedScreen
import cn.dkarbon.enroute.ui.feed.FeedViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Motion
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.timetable.TimetableScreen
import cn.dkarbon.enroute.ui.user.UserScreen

/**
 * 主壳：三栏 —— 课表 / 资讯 / 我的。
 *
 * 两处 2026-11-10 主人明确要求的改动：
 *   1. **课表只有一页**（不再分「今天 / 一周」）—— 直接沿用 CET6 那套周视图，
 *      它的信息密度和「侵蚀」视觉已经调好了，拆成两页反而割裂。
 *   2. **底部导航条要有滑动动效** —— 选中指示条是一根会**平移过去**的橙色实心条，
 *      不是每栏各自亮一下。这也是硬朗体系里唯一允许的动效（180ms、无回弹）。
 */
@Composable
fun EnRouteRoot(ui: AppState, vm: TimetableViewModel) {
    val t = InkTokens.current
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val feedVm: FeedViewModel = viewModel()
    val feedUi by feedVm.ui.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = t.paper,
        bottomBar = {
            Column(Modifier.background(t.card)) {
                // 用 LocalConfiguration 的屏宽算指示条位置，避免 BoxWithConstraints 的子组合开销
                val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                val tabWidth = screenW / 3
                Box(Modifier.fillMaxWidth().height(Stroke.accentBar)) {
                    val x by animateDpAsState(
                        targetValue = tabWidth * tab,
                        animationSpec = tween(Motion.base),
                        label = "tabIndicator",
                    )
                    // 轨道底色（未选中区域）
                    Box(Modifier.fillMaxWidth().fillMaxHeight().background(t.rule))
                    // 滑动指示条
                    Box(
                        Modifier
                            .offset(x = x)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .background(t.primary),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(56.dp),
                ) {
                    HardTab("课表", Icons.Default.DateRange, tab == 0, 0) { tab = 0 }
                    HardTab(
                        label = "资讯",
                        icon = Icons.AutoMirrored.Filled.Article,
                        selected = tab == 1,
                        index = 1,
                        badge = feedUi.unread,
                    ) { tab = 1 }
                    HardTab("我的", Icons.Default.Person, tab == 2, 2) { tab = 2 }
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .consumeWindowInsets(inner),
        ) {
            when (tab) {
                0 -> TimetableScreen(ui = ui, vm = vm)
                1 -> FeedScreen(ui = feedUi, vm = feedVm)
                else -> UserScreen(ui = ui, vm = vm)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HardTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    index: Int,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (badge > 0) {
            BadgedBox(badge = { Badge { Text(if (badge > 99) "99+" else "$badge") } }) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            }
        } else {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) t.primary else t.inkFaint,
        )
    }
}
