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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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

    // 票 26：按 tab 存 saveable 状态（滚动位置等），同级切换可原地恢复
    val routeStates = rememberSaveableStateHolder()

    // 回到前台：资讯距上次成功刷新 >30 分钟 → 静默刷新；顺带跨天重算日期基准
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) feedVm.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                        // 正在看资讯时不挂徽标（未读数页头已承载）；切走恢复
                        badge = if (tab == 1) 0 else feedUi.unread,
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
            // 票 26：三个 tab 是同级路由 —— 切走再回来要停在原处（资讯滚到第 30 条 → 去课表 → 回资讯），
            // 所以每个 tab 的 saveable 状态按 tab 名存一份。只存 Bundle，杀进程不恢复。
            routeStates.SaveableStateProvider("tab:$tab") {
                when (tab) {
                    0 -> TimetableScreen(ui = ui, vm = vm)
                    1 -> FeedScreen(ui = feedUi, vm = feedVm)
                    else -> UserScreen(ui = ui, vm = vm)
                }
            }

            // 课程大纲浮层（票 10）：课表详情 / 培养方案课程行 / 大纲搜索页三处入口共用，
            // 统一挂在根节点上渲染一次 —— 各入口只调 vm.openSyllabus(...)，不必各页各写一套。
            if (ui.syllabusOpenId.isNotBlank()) {
                cn.dkarbon.enroute.ui.curriculum.SyllabusSheet(ui = ui, vm = vm)
            }

            // 「保持登录」一次性说明卡（票 14）：首次登录成功后弹一次，挂在根节点免得漏掉
            if (ui.credPromptVisible) {
                cn.dkarbon.enroute.ui.user.KeepLoginPrompt(vm = vm)
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
            // 未读不是错误：徽标用主色（农械橘），不用 M3 默认 error 红。
            // 暗色底是浅橙 → 文字用深土色保证对比。
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = t.primary,
                        contentColor = if (t.isDark) androidx.compose.ui.graphics.Color(0xFF100E0B) else androidx.compose.ui.graphics.Color.White,
                    ) {
                        Text(
                            if (badge > 99) "99+" else "$badge",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = cn.dkarbon.enroute.ui.theme.NumericFont,
                        )
                    }
                },
            ) {
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
