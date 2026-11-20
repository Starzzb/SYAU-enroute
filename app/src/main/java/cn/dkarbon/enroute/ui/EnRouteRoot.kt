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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.UpdateCenter
import cn.dkarbon.enroute.data.UpdateDialogPolicy
import cn.dkarbon.enroute.data.UpdateInfo
import cn.dkarbon.enroute.data.UpdateVersions
import cn.dkarbon.enroute.ui.feed.FeedScreen
import cn.dkarbon.enroute.ui.feed.FeedViewModel
import cn.dkarbon.enroute.ui.settings.UpdateDialog
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Motion
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.timetable.TimetableScreen
import cn.dkarbon.enroute.ui.user.UserScreen
import kotlinx.coroutines.delay
import cn.dkarbon.enroute.ui.icons.AppIcons

private const val FIRST_FRAME_SETTLE_MS = 500L

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
    // 资讯详情同样是整页新页（m36）：底栏让位，阅读面到底
    val feedDetail by feedVm.detail.collectAsStateWithLifecycle()

    // 更新提醒（文档 §3.3）：检查在 Application，展示在这里。只在启动默认的课表 tab、
    // 且没有其它弹层时弹 —— 已知限制：若用户恰好在资讯/地图浮层上启动，可能叠层（文档 §3.3）。
    val ctx = LocalContext.current
    val updateCenter = remember { UpdateCenter(ctx.applicationContext) }
    val available by UpdateCenter.available.collectAsStateWithLifecycle()
    // 阻塞式下载（m37）：状态在 UpdateCenter 的 companion 里，UI 只是订阅者 ——
    // 浮层被回收、切 tab、转屏都不影响下载本身。
    val downloadState by UpdateCenter.downloadState.collectAsStateWithLifecycle()
    val installPendingApk = cn.dkarbon.enroute.ui.update.rememberInstallWithPermission(updateCenter)
    var dialogInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var openSettingsForUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(available, tab, ui.syllabusOpenId, ui.credPromptVisible) {
        val info = available ?: return@LaunchedEffect
        if (!UpdateDialogPolicy.shouldShow(info, updateCenter.dismissedCode())) return@LaunchedEffect
        if (tab != 0 || ui.syllabusOpenId.isNotBlank() || ui.credPromptVisible) return@LaunchedEffect
        withFrameNanos { }   // 首帧之后才考虑弹（与 MainActivity 启动纪律一致）
        delay(FIRST_FRAME_SETTLE_MS)
        if (tab != 0 || ui.syllabusOpenId.isNotBlank() || ui.credPromptVisible) return@LaunchedEffect
        dialogInfo = info
    }

    // 票 26：按 tab 存 saveable 状态（滚动位置等），同级切换可原地恢复
    val routeStates = rememberSaveableStateHolder()

    // 考试详情同样是整页新页（m36）：挂根节点、与 tab 内容互斥。
    // 详情开着时列表不参与组合 —— 上层 SubHeader 与底栏一起让位，
    // 界面上只剩详情头部那一个返回箭头（2026-11-20 修两层箭头）。
    var examDetail by remember {
        mutableStateOf<Pair<cn.dkarbon.enroute.data.ExamItem, Boolean>?>(null)
    }

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
        // 墨洗底栏（m34，2026-11-20）：贴底、上部 16dp 圆角、三栏等距、朱点指示当前项。
        bottomBar = {
            // 大纲详情是整页新页（m36）：底栏让位，不再和详情同屏
            if (ui.syllabusOpenId.isBlank() && examDetail == null &&
                feedDetail.item == null
            ) {
                cn.dkarbon.enroute.ui.kit.InkNavBar(
                    selected = tab,
                    unread = if (tab == 1) 0 else feedUi.unread,
                    onSelect = { tab = it },
                )
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
            // 课程大纲详情（票 10 / m36）：课表详情、培养方案课程行、大纲搜索页三处入口共用，
            // 统一挂在根节点渲染一次。打开时**替换** tab 内容 —— 整页详情是新页，
            // 下层列表不参与组合，底下的返回按钮也就不存在可点的可能（2026-11-20 修）。
            val shownExam = examDetail
            if (ui.syllabusOpenId.isNotBlank()) {
                cn.dkarbon.enroute.ui.curriculum.SyllabusSheet(ui = ui, vm = vm)
            } else if (shownExam != null) {
                cn.dkarbon.enroute.ui.academic.ExamDetailPage(
                    t = t,
                    exam = shownExam.first,
                    showRawFirst = shownExam.second,
                    onBack = { examDetail = null },
                )
            } else {
                routeStates.SaveableStateProvider("tab:$tab") {
                    when (tab) {
                        0 -> TimetableScreen(ui = ui, vm = vm)
                        1 -> FeedScreen(ui = feedUi, vm = feedVm)
                        else -> UserScreen(
                            ui = ui,
                            vm = vm,
                            openSettingsForUpdate = openSettingsForUpdate,
                            onOpenSettingsForUpdateConsumed = { openSettingsForUpdate = false },
                            onOpenExamDetail = { exam, raw -> examDetail = exam to raw },
                        )
                    }
                }
            }

            // 「保持登录」一次性说明卡（票 14）：首次登录成功后弹一次，挂在根节点免得漏掉
            if (ui.credPromptVisible) {
                cn.dkarbon.enroute.ui.user.KeepLoginPrompt(vm = vm)
            }

            // 更新提醒弹窗（方案 B 里程表）：挂在根节点，盖在一切之上
            dialogInfo?.let { info ->
                UpdateDialog(
                    info = info,
                    fromCode = BuildConfig.VERSION_CODE.toLong(),
                    mandatory = UpdateVersions.isMandatory(info, BuildConfig.VERSION_CODE.toLong()),
                    onUpdateNow = {
                        // m37：点「更新」就地进入阻塞下载 —— 不再跳设置页（那是一次多余的换页），
                        // 下载状态在 UpdateCenter，浮层挂在根节点，切页也不会丢。
                        dialogInfo = null
                        updateCenter.startDownload(info)
                    },
                    onGotIt = { dialogInfo = null },
                    onDismissVersion = {
                        updateCenter.dismissVersion(info.versionCode)
                        dialogInfo = null
                    },
                )
            }

            // 阻塞式下载浮层（m37）：四态同屏互斥 —— 下载中无出口，失败给「重试 / 稍后」
            if (downloadState !is UpdateCenter.DownloadState.Idle) {
                cn.dkarbon.enroute.ui.update.BlockingDownloadSheet(
                    state = downloadState,
                    onInstall = { apk -> installPendingApk(apk) },
                    onRetry = {
                        (downloadState as? UpdateCenter.DownloadState.Failed)?.let {
                            updateCenter.startDownload(it.info)
                        }
                    },
                    onLater = { updateCenter.resetDownload() },
                )
            }
        }
    }
}

