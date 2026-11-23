package cn.dkarbon.enroute.ui.feed

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cn.dkarbon.enroute.data.feed.FeedImages
import java.io.File
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.data.feed.FeedSources
import cn.dkarbon.enroute.data.feed.SourceResult
import cn.dkarbon.enroute.ui.common.CampusNetworkHint
import cn.dkarbon.enroute.ui.common.NetFailure
import cn.dkarbon.enroute.ui.common.campusNetworkLine
import cn.dkarbon.enroute.ui.common.rememberListScrollState
import cn.dkarbon.enroute.ui.ledger.LedgerGroupBreak
import cn.dkarbon.enroute.ui.ledger.LedgerGutter
import cn.dkarbon.enroute.ui.ledger.LedgerGutterGap
import cn.dkarbon.enroute.ui.ledger.LedgerGroupTitle
import cn.dkarbon.enroute.ui.ledger.LedgerRow
import cn.dkarbon.enroute.ui.ledger.LedgerTnum
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Motion
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Soil
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.theme.feedChannelInk
import cn.dkarbon.enroute.ui.theme.primaryInk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import cn.dkarbon.enroute.ui.icons.AppIcons
import cn.dkarbon.enroute.ui.kit.AnchoredCardOverlay
import cn.dkarbon.enroute.ui.kit.DetailScaffold
import cn.dkarbon.enroute.ui.kit.InkTinyLoader
import cn.dkarbon.enroute.ui.kit.PaperTex
import cn.dkarbon.enroute.ui.kit.paperTexture
import cn.dkarbon.enroute.ui.kit.backdropBlur

/** 页眉标题行高度：44dp（触区仍按 48dp 宽给图标）。 */
private val FeedTitleRowHeight = 44.dp

/**
 * 页眉放回的增益：收起来要滑满一个页眉高度（跟着内容走，不跳），
 * 放回来只要三分之一 —— 主人 2026-11-23 报「下滑之后头部灵敏度很低、很久才归位」。
 */
private const val RevealGain = 3f

/**
 * 系统「减少动画」开关（动画时长倍率 = 0）。
 * 每个列表行都要读它，所以只在 [FeedScreen] 根部观察一次、用 CompositionLocal 下发 ——
 * 否则每行一个 ContentObserver。
 */
private val LocalReducedMotion = staticCompositionLocalOf { false }

/** 「想加别的网站？」落到 GitHub issue 新建页，标题与正文预填（不要真实姓名学号）。 */
/** 学校 WebVPN：校外读校内站点的唯一官方通路（mangoVPN 是另一条，学生自备） */
private const val WEBVPN_URL = "https://webvpn.syau.edu.cn/"
private const val NEW_SOURCE_URL =
    "https://github.com/Starzzb/SYAU-enroute/issues/new" +
        "?title=%5B%E6%96%B0%E5%A2%9E%E6%9D%A5%E6%BA%90%5D" +
        "&body=%E7%BD%91%E7%AB%99%E5%90%8D%EF%BC%9A%0A%E7%BD%91%E5%9D%80%EF%BC%9A%0A%E6%83%B3%E7%9C%8B%E4%BB%80%E4%B9%88%EF%BC%9A"

/**
 * 资讯页（2026-11-13 按设计评审规格重做，见 docs/评审-资讯页信息架构.md）。
 *
 * 三层漏斗，不让用户被信息流淹没：
 * ```
 * 待办层  截止提醒条（真实 deadline ≤3 天才出现，可关闭，跨天/新条目重现）
 * 分流层  频道 chips（数字=未读数）+ meta 行（全部已读入口）
 * 阅读层  分组 sticky 头 + 密度分级卡片 + 渲染分页 30+30（已读退后）
 * ```
 *
 * 红点三级同源（条目 6dp 点 / chip 未读数 / tab 徽标），消除路径：
 * 点开详情、长按卡片菜单、频道级「全部已读」（无确认+5s 撤销）、全局级（确认框+撤销）。
 *
 * 正文维持「点开才抓、抓完永久缓存」；缓存命中不闪加载态。
 * 派生行全部由 VM 计算（FeedRows），pager 页只读结果，beyondViewportPageCount=0。
 */
@Composable
fun FeedScreen(ui: FeedUiState, vm: FeedViewModel, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsState()
    BackHandler(enabled = detail.item != null) { vm.closeDetail() }

    // 票 26：详情与列表是 if/else 两个分支，切过去时列表整棵被销毁。
    // 用 SaveableStateHolder 按「路由」存下两个分支的 saveable 状态（pager 页码 + 列表位置），
    // 返回时原位恢复；配合 FeedPage 里的 rememberListScrollState 才完整。
    val routeStates = rememberSaveableStateHolder()
    // 收藏三拍的第三拍落在列表行上：这里持着「刚刚被收藏/取消收藏的那一条」，
    // 详情页与长按菜单都往这里写，回到列表时对应行闪一下再淡掉（m31 A3）。
    val flashKey = remember { mutableStateOf<String?>(null) }
    val d = detail.item
    // 「减少动画」只在这一层观察一次，往下用 CompositionLocal 传
    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        if (d != null) {
            routeStates.SaveableStateProvider("feed:detail") {
                FeedDetailView(
                    detail = detail,
                    vm = vm,
                    onBack = { vm.closeDetail() },
                    onStarred = { key -> vm.toggleStar(key); flashKey.value = key },
                )
            }
        } else {
            routeStates.SaveableStateProvider("feed:list") {
                FeedListView(ui = ui, vm = vm, flashKey = flashKey, modifier = modifier)
            }
        }
    }
}

// ── 列表 ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedListView(
    ui: FeedUiState,
    vm: FeedViewModel,
    flashKey: MutableState<String?>,
    modifier: Modifier,
) {
    val t = InkTokens.current
    val cats = FeedViewModel.CATEGORIES
    val pagerState = rememberPagerState(pageCount = { cats.size })
    val scope = rememberCoroutineScope()

    // 浏览器出口：⋯ 菜单「来源」组每行、以及 item 的原文链接
    val ctx = LocalContext.current
    val openUrl: (String) -> Unit = { u ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }

    // 首帧后补抓一次：冷启动不被网络拖住
    LaunchedEffect(Unit) { vm.refreshIfEmpty() }
    LaunchedEffect(pagerState.currentPage) { vm.setFilter(cats[pagerState.currentPage]) }

    // 成功横幅 4s / 撤销条 5s 自动收起（只在存在时跑计时，不常驻循环）
    LaunchedEffect(ui.banner?.at) {
        if (ui.banner?.tone == FeedBanner.Tone.OK) {
            delay(4000)
            vm.expireBanner()
        }
    }
    LaunchedEffect(ui.undo?.at) {
        if (ui.undo != null) {
            delay(5000)
            vm.expireUndo()
        }
    }

    // 浮层状态：长按卡片菜单 / 页头⋯菜单 / 全局已读确认 / 失败明细
    var quickItem by remember { mutableStateOf<FeedItem?>(null) }
    // 长按位置的锚点：快捷操作卡要从那条卡片下方长出来（和页头 ⋯ 同一套语言）
    var quickAnchor by remember { mutableStateOf<Rect?>(null) }
    var headerMenu by remember { mutableStateOf(false) }
    // ⋯ 按钮在根坐标里的矩形：锚定卡片的折角据此指回按钮（形态 C，主人 2026-11-20 定案）
    var moreAnchor by remember { mutableStateOf<Rect?>(null) }
    var confirmAllRead by remember { mutableStateOf(false) }
    var failDetail by remember { mutableStateOf(false) }

    val currentCat = cats[pagerState.currentPage]
    // 账页 v3 页眉状态：未读 + 总条数 + 最近更新时间。数字的衬线等宽由 LedgerHeader 内部统一处理。
    val headerStatus = remember(ui.unread, ui.items.size, ui.lastSyncAt) {
        buildString {
            if (ui.unread > 0) append("${ui.unread} 未读 · ")
            append("${ui.items.size} 条")
            if (ui.lastSyncAt > 0) {
                val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
                append(" · ").append(f.format(java.util.Date(ui.lastSyncAt))).append(" 更新")
            }
        }
    }
    // 搜索行按需展开（m31 A2）：「查」按钮退役，放大镜就是唯一入口。
    // 放大镜是**开关**：点一次开、再点一次关（主人 2026-11-20 指出旧版点了关不掉）。
    // 有筛选在身时不再强制常驻 —— 否则用户关不掉；改为按钮本身亮起来表示「筛选还开着」。
    val searchFocus = remember { FocusRequester() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val wantSearch = searchOpen
    LaunchedEffect(wantSearch) {
        if (wantSearch) runCatching { searchFocus.requestFocus() }
    }

    // 页眉收纳（m31 A1）：只观察「当前页」的滚动状态。
    // 每个分类页在 pager 里是独立 LazyColumn，由 FeedPage 把它的 LazyListState 上报到这个槽位；
    // 切页时旧页 dispose 清空、新页重报 —— 比较相等才清，避免旧页 onDispose 把新页刚报的抹掉。
    val listStateSlot = remember { mutableStateOf<LazyListState?>(null) }

    // 收纳进度 p ∈ [0,1]（0 展开、1 收起），只喂页眉自己的 graphicsLayer：
    // translationY = -h·p、alpha = 1-p。列表的 contentPadding.top 是量出来的定值 ——
    // 页眉怎么收，正文都一像素不动（旧版 AnimatedVisibility 竖直塌缩会把列表整体顶上去）。
    val collapse = remember { mutableFloatStateOf(0f) }
    val headerPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val headerPad = with(density) { headerPx.intValue.toDp() }

    // 列表顶部预留位跟着收纳进度一起退（主人 2026-11-20 报的「内容卡一半、上沿全白」就是这里）：
    // 旧版预留位是定值 —— 页眉被推上去之后，那条高度留在正文上方成了空白，滚到底也填不满。
    // 现在 p=0 让出整块页眉、p→1 让到只剩 Space.xs，正文跟着上移，永远不留白条。
    // 量化到 8 档：一次手势最多 8 次重组，肉眼当连续；位移本身仍走各自的 graphicsLayer。
    val collapseStep by remember {
        derivedStateOf { (collapse.floatValue * 8f).roundToInt() / 8f }
    }
    val listTopPad = headerPad * (1f - collapseStep) + Space.xs

    // 手势心跳：滚动每来一帧就 +1，吸附动画只认这个计数。
    // 旧版 snapshotFlow 观察的是 collapse 本身，而吸附动画又写回 collapse ——
    // 写一次就打断一次 collectLatest，于是每 140ms 只挪一帧，页眉看起来是「爬」回去的
    // （主人 2026-11-23 报的原症状：下滑之后灵敏度极低、很久才归位、甚至整块不回来）。
    val touchTick = remember { mutableIntStateOf(0) }

    // 判据：向上滚按滚动量收起（1:1 跟手）；向下滚只要列表已经在最顶就立刻放回，
    // 并且把「滚到顶之后没被列表吃掉」的余量（onPostScroll）也算进来 ——
    // 旧版要上一次手势先把列表送回 0/0、这一次手势才动页眉，第一下滑没反应、
    // 第二下滑又被 atTop 的精确判等卡住，页眉就彻底出不来了。
    val collapseConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val h = headerPx.intValue.toFloat()
                if (h <= 0f) return Offset.Zero
                touchTick.intValue++
                if (available.y < 0f) {
                    // 收起：页眉跟着手指走，正文同时滚 —— 两条都是 1:1
                    val p = collapse.floatValue
                    if (p < 1f) collapse.floatValue = (p - available.y / h).coerceIn(0f, 1f)
                    return Offset.Zero
                }
                if (available.y > 0f && atTop()) {
                    // 已经在最顶还要往下滑：直接放回页眉，不等列表把手势吃完
                    return Offset(0f, reveal(available.y, h))
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 列表滚不动了才剩下的下滑量（到顶后继续滑 / 甩）：一样拿来放回页眉。
                // 一次下滑手势就能「先滚回顶部、再顺势把页眉叫回来」，不用第二次手势。
                if (available.y <= 0f) return Offset.Zero
                val h = headerPx.intValue.toFloat()
                if (h <= 0f) return Offset.Zero
                touchTick.intValue++
                return Offset(0f, reveal(available.y, h))
            }

            private fun atTop(): Boolean = listStateSlot.value.let {
                it == null || (it.firstVisibleItemIndex == 0 && it.firstVisibleItemScrollOffset <= 1)
            }

            /** 用 [dy] 像素的下滑量放回页眉，返回真正吃掉的量（剩下的还给列表） */
            private fun reveal(dy: Float, h: Float): Float {
                val p = collapse.floatValue
                if (p <= 0f) return 0f
                val perPx = h / RevealGain
                val np = (p - dy / perPx).coerceAtLeast(0f)
                collapse.floatValue = np
                return ((p - np) * perPx).coerceIn(0f, dy)
            }
        }
    }

    // 松手吸附：p 只停在 0 或 1，不再「卡在一半」。
    // collectLatest 只被「新的手势帧」打断 —— 动画写回 collapse 不再取消自己。
    LaunchedEffect(Unit) {
        snapshotFlow { touchTick.intValue }.collectLatest {
            delay(120)
            val start = collapse.floatValue
            if (start <= 0f || start >= 1f) return@collectLatest
            val target = if (start >= 0.5f) 1f else 0f
            animate(start, target, animationSpec = tween(Motion.base)) { value, _ ->
                collapse.floatValue = value
            }
        }
    }

    // 详情页/长按菜单收藏后回列表：让那一行闪一下（三拍的第三拍）。没上屏就 600ms 后作废。
    LaunchedEffect(flashKey.value) {
        val k = flashKey.value ?: return@LaunchedEffect
        delay(600)
        if (flashKey.value == k) flashKey.value = null
    }

    // 两个布尔由派生状态给出：滚动过程中只在跨过阈值时变一次，
    // 不像直接读 collapse.floatValue 那样每帧都让本组件重组（位移本身走 graphicsLayer 图层）。
    val headerOn by remember { derivedStateOf { collapse.floatValue < 0.999f } }
    val edgeOn by remember { derivedStateOf { collapse.floatValue > 0.02f } }

    // 页眉浮在列表之上（m31 A1）：Box 叠层 —— 列表自己占满，contentPadding.top 预留页眉高度；
    // 页眉只在自己图层上位移 / 淡出，任何一帧都不改布局尺寸，正文纹丝不动。
    Box(modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(collapseConnection)
                // 浮层开着时把背板真模糊掉（API 31+；更老机型由卡片自带三层纸兜底）
                .backdropBlur(headerMenu),
            beyondViewportPageCount = 0,
            key = { cats[it] },
        ) { page ->
            val cat = cats[page]
            val rows = ui.rowsByChannel[cat].orEmpty()
            if (rows.isEmpty()) {
                // 空态/骨架不是 LazyColumn，吃不到 contentPadding —— 单独让出页眉那一条
                Box(Modifier.fillMaxSize().padding(top = listTopPad)) {
                    if (ui.loading && ui.items.isEmpty()) {
                        SkeletonList()
                    } else {
                        EmptyChannel(ui = ui, channel = cat, vm = vm)
                    }
                }
            } else {
                FeedPage(
                    rows = rows,
                    category = cat,
                    today = ui.today,
                    onOpen = vm::open,
                    onLongClick = { item, rect -> quickItem = item; quickAnchor = rect },
                    onToggleGroup = vm::toggleGroup,
                    onLoadMore = { vm.loadMore(cat) },
                    // 筛选签名：换筛选/搜索/密度就回顶部（新内容），只换频道不回顶部（同级回退）
                    resetKey = "${ui.query}|${ui.onlyUnread}|${ui.starredOnly}|${ui.compactByChannel[cat]}",
                    listStateSlot = listStateSlot,
                    topPadding = listTopPad,
                    flashKey = flashKey.value,
                    onFlashDone = { flashKey.value = null },
                )
            }
        }

        // 页眉浮层：叠在列表之上，自带纸底（内容从它下面滚过）。p ≥ 0.94 时整块不参与组合，
        // 免得一层看不见的点击热区压在列表上；headerPx 记的高度不会丢，列表预留位置照旧。
        if (headerOn) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { headerPx.intValue = it.height }
                    .graphicsLayer {
                        translationY = -headerPx.intValue * collapse.floatValue
                        alpha = 1f - collapse.floatValue
                    }
                    .background(t.paper),
            ) {
                // 标题行：一行「资讯 + 状态 + 放大镜 + ⋯」。除动作名外不写任何说明性文字。
                Row(
                    Modifier.fillMaxWidth().height(FeedTitleRowHeight).padding(start = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("资讯", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
                    Box(
                        Modifier.weight(1f).padding(start = Space.s),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            headerStatus,
                            fontSize = 11.5.sp,
                            color = t.inkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(FeedTitleRowHeight)
                            // 开关：开着时再点一次就收起来（旧版只会打开，关不掉）
                            .clickable(onClickLabel = "搜索") { searchOpen = !searchOpen },
                        contentAlignment = Alignment.Center,
                    ) {
                        // 筛选在身 / 搜索开着 → 图标亮起，不靠文字告诉用户
                        val lensOn = wantSearch || ui.hasFilter
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (lensOn) primaryInk() else t.inkSoft,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(FeedTitleRowHeight)
                            .onGloballyPositioned { moreAnchor = it.boundsInRoot() }
                            .clickable(onClickLabel = "更多操作") { headerMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⋯", fontSize = 14.sp, color = t.inkSoft, letterSpacing = 0.4.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.inkFaint))

                if (wantSearch) {
                    SearchRow(
                        query = ui.query,
                        onlyUnread = ui.onlyUnread,
                        starredOnly = ui.starredOnly,
                        hasFilter = ui.hasFilter,
                        focusRequester = searchFocus,
                        onQuery = vm::setQuery,
                        onToggleUnread = vm::toggleOnlyUnread,
                        onToggleStarred = vm::toggleStarredOnly,
                        onClear = { vm.clearFilter(); searchOpen = false },
                    )
                }

                // 待办层：竞赛截止行（只在「全部」频道 + 有今天/明天截止条目时出现，账页行样式）
                if (currentCat == "全部") {
                    DeadlineRow(
                        urgent = ui.urgentCompetitions,
                        today = ui.today,
                        onOpen = { scope.launch { pagerState.animateScrollToPage(cats.indexOf("竞赛")) } },
                    )
                }

                // 刷新失败行：口径统一走 CampusNetworkHint（网络 / 服务端 / 解析，各说各的）
                ui.banner?.takeIf { it.failed.isNotEmpty() || it.skipped.isNotEmpty() }?.let { b ->
                    RefreshFailLine(
                        banner = b,
                        onDetail = { failDetail = true },
                        onWebVpn = { openUrl(WEBVPN_URL) },
                    )
                }

                // 分流层：频道筛选文字行（账页式，36dp，选中项下 2dp 短横线）
                ChannelFilterRow(
                    cats = cats,
                    currentPage = pagerState.currentPage,
                    onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                )
            }
        }

        // 收起后顶部留 1dp 暗线：上面还有一条被收起来了，位置一眼可见
        if (edgeOn) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(Stroke.hairline)
                    .background(t.inkFaint)
                    // alpha 在图层阶段读，滚动中不触发重组
                    .graphicsLayer { alpha = 0.55f * collapse.floatValue },
            )
        }

        // 撤销条：列表底部、导航之上（5s 自动消失）
        ui.undo?.let { u ->
            UndoBar(
                label = u.label,
                onUndo = vm::undoRead,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // 长按卡片菜单：按 key 取**最新**条目渲染（评审 B14），
        // 菜单打开期间刷新/别处改状态不会用旧快照
        quickItem?.let { snap ->
            val item = ui.items.firstOrNull { it.key == snap.key } ?: snap
            AnchoredCardOverlay(
                anchor = quickAnchor,
                accent = t.ink,
                onDismiss = { quickItem = null; quickAnchor = null },
            ) {
                QuickActionsCard(
                    item = item,
                    onSetRead = { vm.setRead(item.key, it) },
                    onToggleStar = { vm.toggleStar(item.key); flashKey.value = item.key },
                    onDismiss = { quickItem = null; quickAnchor = null },
                )
            }
        }

        // 页头⋯菜单：锚定卡片（从按钮下方长出、折角指回去、点空白或返回键关）
        if (headerMenu) {
            AnchoredCardOverlay(
                anchor = moreAnchor,
                accent = t.primary,
                onDismiss = { headerMenu = false },
            ) {
                HeaderMenu(
                    ui = ui,
                    category = currentCat,
                    onRefresh = { vm.refresh(); headerMenu = false },
                    onChannelRead = { vm.markChannelRead(currentCat); headerMenu = false },
                    onAllRead = { confirmAllRead = true; headerMenu = false },
                    onToggleDensity = {
                        vm.setCompact(currentCat, !ui.compactOf(currentCat)); headerMenu = false
                    },
                    onFailDetail = { failDetail = true; headerMenu = false },
                    onToggleUnread = { vm.toggleOnlyUnread(); headerMenu = false },
                    onToggleStarred = { vm.toggleStarredOnly(); headerMenu = false },
                    onOpenSource = { openUrl(it); headerMenu = false },
                )
            }
        }

        if (failDetail) {
            AnchoredCardOverlay(
                anchor = moreAnchor,
                accent = t.brick,
                onDismiss = { failDetail = false },
                maxHeight = 420.dp,
                solid = true,
            ) {
                FailDetailCard(failed = ui.banner?.failed.orEmpty())
            }
        }

        if (confirmAllRead) {
            AlertDialog(
                onDismissRequest = { confirmAllRead = false },
                title = { Text("全部标为已读？", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "${ui.unread} 条未读的红点会消失，收藏和正文缓存不受影响。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = t.inkSoft,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmAllRead = false
                        vm.markChannelRead(null)
                    }) { Text("全部已读", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmAllRead = false }) {
                        Text("取消", fontSize = 14.sp, color = t.inkSoft)
                    }
                },
                containerColor = t.card,
            )
        }
    }
}

// ── 搜索行 / 文字动作 ─────────────────────────────────────

/** 小号文字动作按钮：纯文字、48dp 触区，不画任何盒子与胶囊（账页 v3 的「容器框退场」）。 */
@Composable
private fun TextAction(
    label: String,
    color: Color,
    onClickLabel: String? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(48.dp)
            .clickable(onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun SearchRow(
    query: String,
    onlyUnread: Boolean,
    starredOnly: Boolean,
    hasFilter: Boolean,
    focusRequester: FocusRequester,
    onQuery: (String) -> Unit,
    onToggleUnread: () -> Unit,
    onToggleStarred: () -> Unit,
    onClear: () -> Unit,
) {
    val t = InkTokens.current
    val accent = primaryInk()
    val focus = LocalFocusManager.current
    // 聚焦状态由 BasicTextField 的 InteractionSource 给出 —— 不额外加 state，避免与真实焦点脱节
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    // 下划线展开：0 → 1，从中间向两侧长出去（180ms）。
    // 用 Animatable 而不是 animateFloatAsState：需要能"打断重来"（快速点进点出时不跳）
    val lineGrow = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        lineGrow.animateTo(
            targetValue = if (focused) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        )
    }
    // 放大镜：聚焦时轻微放大 + 转一点；同样用 Animatable 便于打断
    val lensZoom = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        lensZoom.animateTo(
            targetValue = if (focused) 1f else 0f,
            animationSpec = tween(durationMillis = 150),
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = Space.l, end = Space.l, top = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 放大镜：内置矢量图标，聚焦时放大 1.08 + 转 8°
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = if (focused || query.isNotEmpty()) t.inkSoft else t.inkFaint,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        val z = 1f + 0.08f * lensZoom.value
                        scaleX = z
                        scaleY = z
                        rotationZ = 8f * lensZoom.value
                    },
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                // 光标与占位文字必须**同字号同行高**，否则 caret 会偏上/偏下几个像素
                textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = t.ink),
                cursorBrush = SolidColor(accent),
                interactionSource = interaction,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "搜标题 / 关键词",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = t.inkFaint,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(40.dp).clickable(onClickLabel = "清空搜索词") { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = t.inkFaint,
                        modifier = Modifier.size(15.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(4.dp))
            }
            // 未读 / 收藏：**常驻矢量图标按钮**（不再动态消失），选中填实 + 主色
            FilterIconButton(
                selected = onlyUnread,
                contentDesc = if (onlyUnread) "显示全部" else "只看未读",
                tint = accent,
                onClick = onToggleUnread,
            ) {
                Icon(
                    imageVector = if (onlyUnread) AppIcons.MarkEmailUnreadFilled else AppIcons.MarkEmailUnread,
                    contentDescription = null,
                    tint = if (onlyUnread) accent else t.inkSoft,
                    modifier = Modifier.size(19.dp),
                )
            }
            FilterIconButton(
                selected = starredOnly,
                contentDesc = if (starredOnly) "显示全部" else "只看收藏",
                tint = t.amber,
                onClick = onToggleStarred,
            ) {
                Icon(
                    imageVector = if (starredOnly) Icons.Filled.Star else AppIcons.StarBorder,
                    contentDescription = null,
                    tint = if (starredOnly) t.amber else t.inkSoft,
                    modifier = Modifier.size(19.dp),
                )
            }
            // 不额外放"清空筛选"图标：× 已能清词，未读/收藏再点一次即取消筛选。
            // 多一个漏斗只会让人犹豫该点哪个。
        }

        // 下划线：未激活是 1dp 的 rule；激活时主色从中间向两侧展开盖上去
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.l)
                .height(1.dp),
        ) {
            Box(Modifier.fillMaxSize().background(t.rule))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .fillMaxWidth(lineGrow.value)
                    .background(accent),
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

/**
 * 方形图标按钮（48dp 触区、无背景）。
 *
 * 三个筛选按钮共用：图标本身就是状态（填实=选中），不另加文字说明。
 */
@Composable
private fun FilterIconButton(
    selected: Boolean,
    contentDesc: String,
    tint: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(onClickLabel = contentDesc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── 截止行 / 刷新失败行 ─────────────────────────────────

/** 竞赛截止账页行：页边槽「截止」+ 标题 13sp/500 + 「今天/明天截止」brick。整行 ≤40dp。 */
@Composable
private fun DeadlineRow(urgent: List<FeedItem>, today: java.time.LocalDate, onOpen: () -> Unit) {
    val t = InkTokens.current
    // 只在「今天/明天截止」才占一行（病根③：0..3 天的宽口径常驻条改成真正紧急才出）
    val hot = urgent.filter {
        FeedRows.daysLeft(it.deadline, today)?.let { d -> d == 0L || d == 1L } == true
    }
    if (hot.isEmpty()) return
    // urgentCompetitions 已按 deadline 升序，first 即最近截止；多条只显示这一条 + 「等 N 条」
    val first = hot.first()
    val rest = hot.size - 1
    val days = FeedRows.daysLeft(first.deadline, today) ?: 0L
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClickLabel = "查看竞赛频道", onClick = onOpen)
            .padding(horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 页边槽放「截止」二字，与 LedgerRow 日期槽同一竖线（10.5sp 等宽、inkFaint）
        Box(Modifier.width(LedgerGutter + LedgerGutterGap).padding(end = LedgerGutterGap), contentAlignment = Alignment.CenterEnd) {
            Text("截止", fontSize = 10.5.sp, color = t.inkFaint, style = LedgerTnum)
        }
        Text(
            first.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = t.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (days == 0L) "今天截止" else "明天截止",
            fontSize = 11.5.sp,
            color = t.brick,
        )
        if (rest > 0) {
            Text(" · 等 $rest 条", fontSize = 11.5.sp, color = t.inkFaint)
        }
    }
}

/** 刷新失败行：只有部分/全部源失败才出现。口径统一走 [CampusNetworkHint]（图 + 一句），右端「详情」。 */
@Composable
private fun RefreshFailLine(
    banner: FeedBanner,
    onDetail: () -> Unit,
    onWebVpn: () -> Unit,
) {
    val t = InkTokens.current
    val failed = banner.failed
    val skipped = banner.skipped
    if (failed.isEmpty() && skipped.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        if (failed.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp)
                    .padding(start = Space.l, end = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 三句口径：超时/连不上 → Unreachable；HTTP 4xx/5xx → ServerError；解析不出 → Parse
                CampusNetworkHint(
                    failure = netFailureOf(failed.first().error),
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                Text(
                    "详情",
                    fontSize = 11.5.sp,
                    color = t.inkSoft,
                    modifier = Modifier.clickable(onClickLabel = "失败详情", onClick = onDetail).padding(start = Space.m),
                )
            }
        }
        // 校内源「没路」与「出错」是两件事：这里只说事实 + 给一条能走的路
        if (skipped.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp)
                    .padding(start = Space.l, end = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CampusNetworkHint(
                    failure = NetFailure.Unreachable,
                    modifier = Modifier.weight(1f),
                    compact = true,
                )
                Text(
                    "WebVPN",
                    fontSize = 11.5.sp,
                    color = t.inkSoft,
                    modifier = Modifier
                        .clickable(onClickLabel = "打开学校 WebVPN", onClick = onWebVpn)
                        .padding(start = Space.m),
                )
            }
        }
    }
}

// ── 频道筛选文字行 ───────────────────────────────────────

/** 频道筛选：账页式一行 11.5sp 文字。选中项文字 + 2dp 短横线吃该频道语义强调色，未选中 inkSoft。 */
@Composable
private fun ChannelFilterRow(
    cats: List<String>,
    currentPage: Int,
    onSelect: (Int) -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            // 与正文左沿对齐：保留页边槽 LedgerGutter 缩进 + 日期间隙 LedgerGutterGap
            .padding(start = Space.l + LedgerGutter + LedgerGutterGap, end = Space.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        cats.forEachIndexed { i, c ->
            val selected = currentPage == i
            // 选中项：文字与短横线都吃频道强调色（色系语义见 ui/theme/FeedChannelColors.kt）
            val accent = if (selected) feedChannelInk(c) else t.inkSoft
            // 36dp 整行触区；Column 恒定高度（文字 + 2dp 间隙 + 2dp 线），切页时文字不跳
            Box(
                Modifier.height(36.dp).clickable(onClickLabel = "切到$c 频道") { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        c,
                        fontSize = 11.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = accent,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .background(if (selected) accent else Color.Transparent),
                    )
                }
            }
        }
    }
}

// ── 列表页（派生行只读）──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedPage(
    rows: List<FeedRow>,
    category: String,
    today: java.time.LocalDate,
    onOpen: (FeedItem) -> Unit,
    onLongClick: (FeedItem, Rect?) -> Unit,
    onToggleGroup: (String) -> Unit,
    onLoadMore: () -> Unit,
    resetKey: String,
    listStateSlot: MutableState<LazyListState?>,
    topPadding: Dp,
    flashKey: String?,
    onFlashDone: () -> Unit,
) {
    // 票 26：saveable + 频道页 key，进详情返回原位；换筛选由 resetKey 触发回顶部
    val listState = rememberListScrollState(resetKey)

    // 把自己的 LazyListState 上报给页头（页眉按需隐藏读它）。
    // dispose 时只有槽里还是自己才清空：beyondViewportPageCount=0 只挂当前页，
    // 切页瞬间旧页 onDispose 若直接置空，会把新页刚上报的 state 抹掉。
    DisposableEffect(listState, listStateSlot) {
        listStateSlot.value = listState
        onDispose {
            if (listStateSlot.value === listState) listStateSlot.value = null
        }
    }

    // 分页触发（评审 B13）：对**索引值**响应而非布尔边沿，
    // 「还有更多」纳入判定 —— 到底不空调，短列表全可见首帧后即触发
    val footer = rows.lastOrNull() as? FeedRow.Footer
    val hasMore = footer != null && footer.shown < footer.total
    val lastVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    LaunchedEffect(lastVisible, rows.size, hasMore) {
        if (hasMore && FeedRows.shouldLoadMore(lastVisible, rows.size, footer!!.shown, footer.total)) {
            onLoadMore()
        }
    }

    // 账页 v3 的间距不走全局 spacedBy：条目间距 18dp 由 LedgerRow 内部的 9+1px+9 承担，
    // 组间 28dp + 短横线由「group-gap」插页承担 —— 这样最后一条之后才不拖一条多余分隔线。
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        // top 由页眉浮层量出来的高度决定（m31 A1）：页眉收起时这个值不变 —— 内容不跟着动
        contentPadding = PaddingValues(start = Space.l, end = Space.l, top = topPadding, bottom = Space.xxl),
    ) {
        rows.forEachIndexed { index, row ->
            when (row) {
                is FeedRow.Header -> {
                    // 组间边界：上一组末尾之后放 28dp 留白 + 56dp 短横线（缩进到正文左沿）
                    if (index > 0) {
                        item(key = "gap:${row.rowKey}", contentType = "group-gap") {
                            Column(Modifier.fillMaxWidth()) {
                                Spacer(Modifier.height(14.dp))
                                LedgerGroupBreak()
                                Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                    stickyHeader(key = row.rowKey, contentType = "header") {
                        LedgerGroupTitle(
                            label = row.label,
                            count = row.count,
                            collapsible = row.collapsible,
                            collapsed = row.collapsed,
                            onToggle = if (row.collapsible) ({ onToggleGroup(row.label) }) else null,
                        )
                    }
                }
                is FeedRow.Card -> item(key = row.rowKey, contentType = "entry") {
                    // 记下这一行在根坐标里的位置，长按菜单用它当锚点
                    var rowRect by remember { mutableStateOf<Rect?>(null) }
                    // 组内最后一条之后不加分隔线（修正 B）：下一条是组头/分页脚/列表尾都算
                    val lastInGroup = index + 1 >= rows.size ||
                        rows[index + 1] is FeedRow.Header ||
                        rows[index + 1] is FeedRow.Footer
                    // 收藏三拍的第三拍：该行底色闪一下（4% 主色，120ms 淡回纸色）
                    FlashRow(
                        flash = flashKey != null && flashKey == row.item.key,
                        onDone = onFlashDone,
                        modifier = Modifier.onGloballyPositioned { rowRect = it.boundsInRoot() },
                    ) {
                        LedgerRow(
                            item = row.item,
                            today = today,
                            compact = row.compact,
                            lastInGroup = lastInGroup,
                            onClick = { onOpen(row.item) },
                            onLongClick = { onLongClick(row.item, rowRect) },
                        )
                    }
                }
                is FeedRow.Footer -> item(key = row.rowKey, contentType = "footer") {
                    FeedPageFooter(shown = row.shown, total = row.total)
                }
            }
        }
    }
}

@Composable
private fun FeedPageFooter(shown: Int, total: Int) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            if (shown < total) "已显示 $shown/$total" else "已到底 · 共 $total 条",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = t.inkFaint,
        )
    }
}

// 标准卡 / 紧凑卡 / 组头 / 分页脚 / 收藏标已随账页 v3 移除 —— 条目改为 LedgerRow「行」，
// 组头改 LedgerGroupTitle，分页脚改 FeedPageFooter。渲染入口见上方 FeedPage。

// ── 骨架 / 空态 ───────────────────────────────────────────

/** 静态骨架（红线：无 shimmer 无渐变）：纯色条模拟「行」，不画卡框 */
@Composable
private fun SkeletonList() {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxSize().padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkTinyLoader()
            Spacer(Modifier.width(8.dp))
            Text("正在抓取…", fontSize = 12.sp, color = t.inkFaint)
        }
        repeat(3) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(0.72f).height(16.dp).background(t.rule))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(12.dp).background(t.rule.copy(alpha = 0.7f)))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.56f).height(12.dp).background(t.rule.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun EmptyChannel(ui: FeedUiState, channel: String, vm: FeedViewModel) {
    val t = InkTokens.current
    // 空态分支表（规格 §9.2）：按真实状态选字块/文案/动作
    val total = ui.totalOf(channel)
    val unread = ui.unreadOf(channel)
    val branch = when {
        ui.query.isNotBlank() -> EmptyBranch("无", "没有符合条件的资讯", "", "清空筛选")
        // 收藏空态也用书签图标（与详情顶栏、列表三拍同一个符号）
        ui.starredOnly && ui.items.none { it.starred } ->
            EmptyBranch("", "还没有收藏", "", "看全部", AppIcons.BookmarkOutline)
        ui.onlyUnread && unread == 0 && total > 0 -> EmptyBranch("完", "都读完了", "「$channel」共 $total 条已读", "看全部")
        // 首次无缓存且上次刷新全失败：给可操作的重试（规格 §9.2），失败原因走统一口径
        total == 0 && ui.banner?.tone == FeedBanner.Tone.FAIL ->
            EmptyBranch(
                "空",
                "还没有内容",
                campusNetworkLine(netFailureOf(ui.banner?.failed?.firstOrNull()?.error)),
                "刷新",
            )
        total == 0 -> EmptyBranch("空", if (channel == "全部") "还没有内容" else "「$channel」暂时没有内容", "", null)
        else -> EmptyBranch("空", "没有符合条件的资讯", "", "清空筛选")
    }
    Box(Modifier.fillMaxSize().padding(Space.l), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (branch.icon != null) {
                Icon(
                    branch.icon,
                    contentDescription = null,
                    tint = t.inkFaint,
                    modifier = Modifier.size(19.dp),
                )
            } else {
                Text(branch.glyph, fontSize = 13.sp, color = t.inkFaint)
            }
            Spacer(Modifier.height(Space.m))
            Text(branch.title, fontSize = 13.sp, color = t.inkSoft, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(Space.xs))
            if (branch.sub.isNotEmpty()) {
                Text(branch.sub, fontSize = 11.sp, color = t.inkFaint)
            }
            if (branch.action != null) {
                Spacer(Modifier.height(Space.m))
                TextAction(
                    branch.action,
                    primaryInk(),
                    onClickLabel = branch.action,
                ) {
                    when (branch.action) {
                        "清空筛选" -> vm.clearFilter()
                        "刷新" -> vm.refresh()
                        "看全部" -> {
                            if (ui.onlyUnread) vm.toggleOnlyUnread()
                            if (ui.starredOnly) vm.toggleStarredOnly()
                        }
                    }
                }
            }
        }
    }
}

private data class EmptyBranch(
    val glyph: String,
    val title: String,
    val sub: String,
    val action: String?,
    val icon: ImageVector? = null,
)

// ── 撤销条 ────────────────────────────────────────────────

@Composable
private fun UndoBar(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val t = InkTokens.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.l, vertical = Space.s)
            .height(Space.minTouch)
            .background(if (t.isDark) t.cardAlt else Soil.n900),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(Space.m))
        Text(
            label,
            fontSize = 12.sp,
            color = if (t.isDark) t.ink else t.paper,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "撤销",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (t.isDark) t.primary else cn.dkarbon.enroute.ui.theme.Machine.n200,
            modifier = Modifier.clickable(onClick = onUndo).padding(Space.m),
        )
    }
}

// ── 浮层：长按卡片 / 页头菜单 / 失败明细 ──────────────────

/** 底部浮层通用骨架：scrim + 1.5dp 边 + 顶部色条 + 180ms 滑入（CourseQuick 范式） */
/** 菜单动作行：序号 + 动作名 + 一句副文案（≥48dp 触区） */
@Composable
private fun MenuRow(
    index: String,
    title: String,
    sub: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    // 没有 sub 时把纵向留白加大一点，避免菜单显得局促 —— 不靠"补一句说明"来填空
    val vPad = if (sub == null) Space.l else Space.m
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l, vertical = vPad),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(index, fontSize = 9.sp, fontFamily = NumericFont, color = t.inkFaint)
            Spacer(Modifier.width(Space.m))
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) t.ink else t.inkFaint,
            )
        }
        // sub 只留"事实"（如目标域名），不留操作说明
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                sub,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                color = t.inkFaint,
                maxLines = 1,
                modifier = Modifier.padding(start = 27.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
}

@Composable
private fun ColumnScope.QuickActionsCard(
    item: FeedItem,
    onSetRead: (Boolean) -> Unit,
    onToggleStar: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val cb = LocalClipboardManager.current
    val unread = item.readAt == null
    var copied by remember { mutableStateOf(false) }

    Row(Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.s)) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(Color.Transparent)
                    .border(Stroke.hairline, t.outline),
                contentAlignment = Alignment.Center,
            ) { Text(item.category.take(2), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = t.inkSoft) }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.sourceName} · ${shortDate(item.publishedAt)} · ${if (unread) "未读" else "已读"}",
                    fontSize = 9.5.sp,
                    color = t.inkFaint,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        MenuRow(
            "01",
            if (unread) "标为已读" else "标为未读",
        ) { onSetRead(unread); onDismiss() }
        MenuRow(
            "02",
            if (item.starred) "取消收藏" else "收藏",
        ) { onToggleStar(); onDismiss() }
        MenuRow(
            "03",
            if (copied) "已复制" else "复制标题",
        ) {
            val text = buildString {
                append(item.title)
                append("｜").append(item.sourceName)
                if (item.category == "竞赛" && item.deadline.isNotBlank()) append("｜报名截止 ").append(item.deadline)
                append("｜").append(item.url.ifBlank { item.urlAlt })
            }
            cb.setText(AnnotatedString(text))
            copied = true
        }
        // 唯一带 sub 的一项：sub 是**目标域名**，属事实信息，不是操作说明
        MenuRow("04", "浏览器打开", hostOf(item.url.ifBlank { item.urlAlt })) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url.ifBlank { item.urlAlt }))) }
            onDismiss()
        }
}

@Composable
private fun HeaderMenu(
    ui: FeedUiState,
    category: String,
    onRefresh: () -> Unit,
    onChannelRead: () -> Unit,
    onAllRead: () -> Unit,
    onToggleDensity: () -> Unit,
    onFailDetail: () -> Unit,
    onToggleUnread: () -> Unit,
    onToggleStarred: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val t = InkTokens.current
    val chUnread = ui.unreadOf(category)
    val failed = ui.banner?.failed.orEmpty()
    val starred = ui.items.count { it.starred }

    Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.s)) {
        Text("资讯操作", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Spacer(Modifier.height(2.dp))
        Text("$category · $chUnread 未读", fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
    }

    // 筛子：收藏 / 未读（主人 2026-11-20：「操作也可以筛选收藏和未读」）
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        FilterCell("收藏", starred, ui.starredOnly, t.primary, onClick = onToggleStarred, modifier = Modifier.weight(1f))
        FilterCell("未读", ui.unread, ui.onlyUnread, t.primary, onClick = onToggleUnread, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(Space.xs))

    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
    // 动作组：刷新 / 已读 / 密度 / 失败详情（m31 A2：页眉只剩「放大镜 + ⋯」，能力全在这里）
    MenuRow("01", "刷新", "重新抓取全部频道，旧数据可见") { onRefresh() }
    MenuRow("02", "本频道全部已读", "将「$category」的 $chUnread 条未读标为已读", enabled = chUnread > 0) {
        onChannelRead()
    }
    MenuRow("03", "全部频道全部已读", "共 ${ui.unread} 条未读，收藏不受影响", enabled = ui.unread > 0) {
        onAllRead()
    }
    MenuRow(
        "04",
        "密度：${if (ui.compactOf(category)) "紧凑" else "标准"}",
        "只影响「$category」",
    ) { onToggleDensity() }
    if (failed.isNotEmpty()) {
        MenuRow("05", "刷新失败详情", "${failed.size} 个源失败") { onFailDetail() }
    }

    // ── 来源组：预设站点 + 最近一次抓取状态，末项是新增来源的出口 ──
    // 卡内只留一层滚动（外层 AnchoredCardOverlay 的 verticalScroll）—— 嵌套两层竖滚会互相抢手势
    Spacer(Modifier.height(Space.m))
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
    Text(
        "来源",
        fontSize = 9.5.sp,
        color = t.inkFaint,
        modifier = Modifier.padding(start = Space.l, top = Space.m, bottom = Space.xs),
    )
    FeedSources.ALL.forEach { s ->
        MenuRow("", s.name, sourceStatusLabel(s.key, ui)) {
            onOpenSource(s.listUrl)
        }
    }
    MenuRow("", "想加别的网站？") { onOpenSource(NEW_SOURCE_URL) }
}

/** 卡片顶部的筛选格：名字 + 数量，选中时左缘 3dp 强调条（不写「已选中」） */
@Composable
private fun FilterCell(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(3.dp)
    Row(
        modifier
            .height(Space.minTouch)
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(Stroke.hairline, if (selected) accent.copy(alpha = 0.55f) else t.outline.copy(alpha = 0.5f), shape)
            .clickable(onClickLabel = label, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .background(if (selected) accent else Color.Transparent),
        )
        Spacer(Modifier.width(Space.m))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal, color = t.ink)
        Spacer(Modifier.weight(1f))
        Text("$count", fontSize = 11.sp, fontFamily = NumericFont, color = if (selected) accent else t.inkFaint)
        Spacer(Modifier.width(Space.m))
    }
}

@Composable
private fun ColumnScope.FailDetailCard(failed: List<SourceResult>) {
    val t = InkTokens.current
    Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.s)) {
        Text("刷新失败详情", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Spacer(Modifier.height(2.dp))
        Text("${failed.size} 个源失败 · 其余源正常", fontSize = 9.5.sp, color = t.inkFaint)
    }
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
    Column {
        if (failed.isEmpty()) {
            Text(
                "这一次没有源失败。",
                fontSize = 11.5.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(horizontal = Space.l, vertical = Space.m),
            )
        }
        failed.forEach { f ->
                val name = cn.dkarbon.enroute.data.feed.FeedSources.byKey(f.sourceKey)?.name ?: f.sourceKey
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.ink)
                    Text(
                        campusNetworkLine(netFailureOf(f.error)),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = t.brick,
                    )
                }
                Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        }
    }
}

// ── 详情 ──────────────────────────────────────────────────

/**
 * 详情页：身份行 → 大标题 → 截止信息（竞赛）→ 通栏线 → 正文 → 附件 → 链接 → 原文。
 * 缓存命中首帧即内容；未命中出静态骨架；失败出 [重试][浏览器打开]。
 * 顶栏「藏」开关接上收藏。
 */
@Composable
private fun FeedDetailView(
    detail: FeedDetailState,
    vm: FeedViewModel,
    onBack: () -> Unit,
    onStarred: (String) -> Unit,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val item = detail.item ?: return
    val today = remember { java.time.LocalDate.now() }
    val dl = remember(item.deadline, item.category, today) {
        if (item.category == "竞赛") FeedRows.deadlineLabel(item, today) else null
    }

    val openUrl: (String) -> Unit = { u ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }

    DetailScaffold(
        backLabel = "资讯",
        tex = PaperTex.CROSS,
        onBack = onBack,
        actions = {
            // 收藏开关：书签图标（实心=已收藏）+ 三拍反馈，不再用「藏」字
            BookmarkToggle(
                starred = item.starred,
                onToggle = { onStarred(item.key) },
                size = 17.dp,
                touch = 40.dp,
            )
            Spacer(Modifier.width(Space.xs))
            TextButton(onClick = { openUrl(item.url.ifBlank { item.urlAlt }) }) {
                // 主色文字 ≥14sp/700（规格 §2 对比裁定）
                Text("浏览器打开", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary)
            }
        },
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 账页 v3 去框：分类名排成来源上方的小字，不再用色块徽章
                Column {
                    Text(item.category, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = t.inkSoft)
                    Spacer(Modifier.height(1.dp))
                    Text(item.sourceName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = t.ink)
                }
                Spacer(Modifier.weight(1f))
                Text(item.publishedAt.ifBlank { "日期未标注" }, fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
            }

            Spacer(Modifier.height(Space.m))
            Text(item.title, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)

            // 竞赛结构化信息：报名窗口 / 比赛窗口 / 主办方 / 平台状态（有就显示，没有不编）
            if (item.category == "竞赛") {
                val facts = buildList {
                    if (item.regStart.isNotBlank()) add("报名开始：${item.regStart}")
                    if (item.deadline.isNotBlank()) {
                        val d = FeedRows.daysLeft(item.deadline, today)
                        add(
                            if (d != null && d < 0) "报名已于 ${item.deadline} 截止"
                            else "报名截止：${item.deadline}" + (d?.let { "（剩 $it 天）" } ?: ""),
                        )
                    }
                    if (item.regStart.isNotBlank() && item.contestStart.isNotBlank()) {
                        add("比赛时间：${item.contestStart} 至 ${item.contestEnd.ifBlank { "未标注" }}")
                    }
                    if (item.organizer.isNotBlank()) add("主办方：${item.organizer}")
                    if (item.platformState.isNotBlank()) add("平台状态：${item.platformState}")
                }
                if (facts.isNotEmpty()) {
                    Spacer(Modifier.height(Space.s))
                    // 账页 v3 去框：竞赛结构化事实改成纯文字列，不再衬色块底
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        facts.forEach { f ->
                            // 截止行才吃色阶；「报名开始」等普通事实行用二级文字
                            val isDeadlineLine = f.startsWith("报名截止") || f.startsWith("报名已于")
                            Text(
                                f,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontWeight = if (isDeadlineLine && dl?.tone == DeadlineTone.HOT) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = NumericFont,
                                color = if (isDeadlineLine) {
                                    when (dl?.tone) {
                                        DeadlineTone.HOT -> t.brick
                                        DeadlineTone.WARM -> t.amber
                                        else -> t.inkSoft
                                    }
                                } else t.inkSoft,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.m))
            Box(Modifier.fillMaxWidth().height(Stroke.emphasis).background(t.ink))
            Spacer(Modifier.height(Space.l))

            val payload = detail.payload
            if (detail.loading) {
                // 静态骨架：3 根条 + 真实状态文案（无 shimmer）
                Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    Box(Modifier.fillMaxWidth().height(12.dp).background(t.rule))
                    Box(Modifier.fillMaxWidth().height(12.dp).background(t.rule))
                    Box(Modifier.fillMaxWidth(0.64f).height(12.dp).background(t.rule))
                }
                Spacer(Modifier.height(Space.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InkTinyLoader()
                    Spacer(Modifier.width(8.dp))
                    Text("正在读取正文…", fontSize = 12.sp, color = t.inkFaint)
                }
            } else {
                if (payload.text.isNotBlank()) {
                    val paras = remember(payload.text) {
                        payload.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        paras.forEach { p ->
                            // 阅读参数：正文 16sp、行高 28sp（1.75，中文长读舒适带）
                            Text(p, fontSize = 16.sp, lineHeight = 28.sp, color = t.ink)
                        }
                    }
                } else {
                    // 正文取不到 = 解析这一层的问题，口径统一走 CampusNetworkHint
                    CampusNetworkHint(failure = NetFailure.Parse)
                }

                if (payload.isEmpty || payload.text.isBlank()) {
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Box(
                            Modifier.height(48.dp)
                                .clickable(onClick = vm::retryDetail).padding(horizontal = Space.m),
                            contentAlignment = Alignment.Center,
                        ) { Text("重试", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary) }
                        Box(
                            Modifier.height(48.dp)
                                .clickable { openUrl(item.url.ifBlank { item.urlAlt }) }.padding(horizontal = Space.m),
                            contentAlignment = Alignment.Center,
                        ) { Text("浏览器打开", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary) }
                    }
                }

                // ── 正文插图：实测正文图都在 /__local/ 等正文域，模板图已在解析层滤掉 ──
                if (payload.images.isNotEmpty()) {
                    Spacer(Modifier.height(Space.l))
                    FeedImageList(urls = payload.images.map { it.url })
                }

                // ── 正文本身是 PDF（学生处 xsc 实测：正文只有「请安装 PDF 阅读器」）──
                if (payload.pdfUrl.isNotBlank()) {
                    Spacer(Modifier.height(Space.l))
                    FeedPdfCard(url = payload.pdfUrl, onOpen = { openUrl(payload.pdfUrl) })
                }

                if (payload.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xxl))
                    SectionLabel("附件 ${payload.attachments.size}")
                    Spacer(Modifier.height(Space.s))
                    payload.attachments.forEach { a ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { openUrl(a.url) }
                                .padding(vertical = Space.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("附", fontSize = 11.5.sp, color = t.inkFaint)
                            Spacer(Modifier.width(Space.s))
                            Text(
                                a.text.ifBlank { "附件" },
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = t.slate,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(Space.s))
                            // 实测：校内站附件走 download.jsp + 图形验证码，App 内下拿到的是验证码页。
                            // 所以有验证码时只给「浏览器打开」，措辞如实说明要人工过码。
                            Text(
                                if (payload.attachmentsBlocked) "浏览器打开" else "下载",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary,
                            )
                        }
                    }
                }

                if (payload.links.isNotEmpty()) {
                    Spacer(Modifier.height(Space.xl))
                    SectionLabel("文中链接 ${payload.links.size}")
                    Spacer(Modifier.height(Space.s))
                    payload.links.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clickable { openUrl(l.url) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(Modifier.padding(top = 6.dp).size(4.dp).background(t.inkFaint))
                            Spacer(Modifier.width(Space.s))
                            Text(l.text, fontSize = 12.sp, lineHeight = 18.sp, color = t.slate, maxLines = 2, modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(Space.xxl))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { openUrl(item.url.ifBlank { item.urlAlt }) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        hostOf(item.url.ifBlank { item.urlAlt }),
                        fontSize = 10.5.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("查看原文 →", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary)
                }
            }
        Spacer(Modifier.height(Space.s))
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = InkTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = t.inkSoft)
        Spacer(Modifier.width(Space.s))
        Box(Modifier.weight(1f).height(Stroke.hairline).background(t.rule))
    }
}

// ── 小工具 ────────────────────────────────────────────────

/**
 * 正文插图：按宽度铺满、高度按原图比例算，多张竖排。
 *
 * 三条来自 2026-11-21 实测的处理：
 *  · **不裁切**（ContentScale.FillWidth 而不是 Crop）—— 通知里的图常是表格截图/名单，
 *    裁掉两边就等于丢信息；
 *  · **长截图不设高度上限**（服务外包大赛那条 873×3780，等比到屏宽约 1500dp 高），
 *    外层是 DetailScaffold 的滚动容器，让它自然撑开；
 *  · tzb 那种写死 style="width:600px;height:338px" 的内联尺寸已在解析层丢弃，
 *    这里只认原图宽高比。
 */
@Composable
private fun FeedImageList(urls: List<String>) {
    val ctx = LocalContext.current
    val t = InkTokens.current
    var files by remember(urls) { mutableStateOf<List<File>>(emptyList()) }

    LaunchedEffect(urls) {
        val done = withContext(Dispatchers.IO) {
            runCatching { FeedImages.ensure(ctx, urls) }.getOrElse { emptyList() }
        }
        files = done
    }

    if (files.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            files.forEach { f -> FeedImage(f) }
        }
    } else if (urls.isNotEmpty()) {
        // 图没下满时不解释太多：文字已经在上面，图是补充
        Text("正文有 " + urls.size + " 张图，暂未取下", fontSize = 11.sp, color = t.inkFaint)
    }
}

@Composable
private fun FeedImage(file: File) {
    var bitmap by remember(file.path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file.path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }
    val img = bitmap ?: return
    Image(
        bitmap = img,
        contentDescription = null,
        // FillWidth：长截图等比铺满，不裁、不留白边
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 正文就是一份 PDF 时（学生处 xsc 实测：正文 0 字，只有「请安装 PDF 阅读器」）。
 *
 * 不内嵌渲染：Android WebView 不带 PDF 插件，iframe src=.pdf 会白屏；
 * 引 PdfRenderer 又要自己拼每页位图，收益不抵成本。
 * 这里如实说「这是 PDF」，给一键交给系统查看器 —— 直链实测无需 Referer/Cookie。
 */
@Composable
private fun FeedPdfCard(url: String, onOpen: () -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.cardAlt)
            .border(Stroke.hairline, t.outline)
            .clickable(onClick = onOpen)
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("这份公告是 PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Text(
            "正文没有文字版，内容在文件里。点开用系统查看器阅读。",
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = t.inkSoft,
        )
        Text(hostOf(url), fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
    }
}

private fun sanitizeSummary(s: String): String =
    s.replace("id=\"vsb_content\">", "").trim()

private fun shortDate(d: String): String =
    if (d.length >= 10) d.substring(5) else d.ifBlank { "—" }

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)

// ── 失败口径 / 来源状态 / 收藏三拍 ─────────────────────────

/**
 * 仓库层只给一句 error 字符串（`HTTP 502` / `解析出 0 条（页面结构可能变了）` / 异常 message）。
 * 出口统一成三档：HTTP 4xx-5xx → [NetFailure.ServerError]；解析不出来 → [NetFailure.Parse]；
 * 其余（超时 / 连不上 / 证书）→ [NetFailure.Unreachable]。
 */
private fun netFailureOf(err: String?): NetFailure {
    val s = err.orEmpty()
    if (s.isBlank()) return NetFailure.Unreachable
    if (s.contains("HTTP", ignoreCase = true)) {
        val code = Regex("\\d{3}").find(s)?.value?.toIntOrNull()
        return if (code != null && code >= 400) NetFailure.ServerError else NetFailure.Unreachable
    }
    if (s.contains("解析") || s.contains("0 条")) return NetFailure.Parse
    return NetFailure.Unreachable
}

/** 「刚刚 / 12 分钟前 / 3 小时前 / 2 天前」——只描述事实。 */
private fun agoLabel(at: Long): String {
    val diff = System.currentTimeMillis() - at
    if (diff <= 0L) return "刚刚"
    val min = diff / 60_000L
    return when {
        min < 1L -> "刚刚"
        min < 60L -> "$min 分钟前"
        min < 1_440L -> "${min / 60L} 小时前"
        else -> "${min / 1_440L} 天前"
    }
}

/** ⋯ 菜单「来源」组每行的副标题：最近一次抓取的时间 + 结果。 */
private fun sourceStatusLabel(key: String, ui: FeedUiState): String {
    val at = ui.lastSyncAt
    val whenPart = if (at > 0L) agoLabel(at) else ""
    val res = ui.sourceResults.firstOrNull { it.sourceKey == key }
    val whatPart = when {
        res == null -> "未抓取"
        res.skipped != null -> "需校园网"
        res.error != null -> "失败"
        else -> "${res.fetched} 条"
    }
    return if (whenPart.isBlank()) whatPart else "$whenPart · $whatPart"
}

/** 系统「减少动画」判据：Animator / Transition 时长倍率为 0 视为开启（Compose 无原生等价物）。 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    val reduced = remember { mutableStateOf(animatorsOff(ctx)) }
    DisposableEffect(ctx) {
        val obs = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                reduced.value = animatorsOff(ctx)
            }
        }
        listOf(
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
        ).forEach { ctx.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, obs) }
        onDispose { ctx.contentResolver.unregisterContentObserver(obs) }
    }
    return reduced.value
}

private fun animatorsOff(ctx: android.content.Context): Boolean {
    fun scale(key: String) = Settings.Global.getFloat(ctx.contentResolver, key, 1f)
    return scale(Settings.Global.ANIMATOR_DURATION_SCALE) == 0f ||
        scale(Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f
}

/**
 * 收藏三拍之第三拍：该行底色 4% 主色闪 120ms，再用 180ms 淡回纸底。
 * 只有这次真正点了收藏的那一行触发一次；动画走完回调清 key。
 */
@Composable
private fun FlashRow(
    flash: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = InkTokens.current
    val reduced = LocalReducedMotion.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(flash, reduced) {
        if (!flash) return@LaunchedEffect
        if (reduced) {
            onDone()
            return@LaunchedEffect
        }
        a.snapTo(1f)
        delay(120)
        a.animateTo(0f, tween(180))
        onDone()
    }
    // 底色在 draw 阶段读动画值：闪的 300ms 里不重组行内容
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind { drawRect(t.primary.copy(alpha = 0.04f * a.value)) },
    ) {
        content()
    }
}

/**
 * 收藏开关：书签轮廓 / 实心两态，点击三拍 ——
 * ① 图标 1 → 1.28 → 1（spring，damping 0.45，回弹一次）
 * ② 一圈描边环 8dp → 26dp 淡出（180ms）
 * ③ 行底色闪一下（交给 [FlashRow]）
 * 系统开了「减少动画」时只换图标，不动效。
 */
@Composable
private fun BookmarkToggle(
    starred: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 19.dp,
    touch: Dp = 44.dp,
) {
    val t = InkTokens.current
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .size(touch)
            .clickable(onClickLabel = if (starred) "取消收藏" else "收藏") {
                onToggle()
                if (!reduced) {
                    scope.launch {
                        scale.snapTo(1f)
                        scale.animateTo(
                            1.28f,
                            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                        )
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                    scope.launch {
                        ring.snapTo(0f)
                        ring.animateTo(1f, tween(180))
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 第二拍：一圈描边 8dp → 26dp 淡出。半径与透明度都在 draw 阶段读 —— 全程不重组
        Box(
            Modifier
                .size(40.dp)
                .drawBehind {
                    if (!reduced && ring.value > 0f && ring.value < 1f) {
                        val radius1 = (8.dp + (26.dp - 8.dp) * ring.value).toPx() / 2f
                        drawCircle(
                            color = t.primary.copy(alpha = 0.55f * (1f - ring.value)),
                            radius = radius1,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                },
        )
        Icon(
            imageVector = if (starred) AppIcons.BookmarkFilled else AppIcons.BookmarkOutline,
            contentDescription = null,
            tint = if (starred) t.primary else t.inkFaint,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}
