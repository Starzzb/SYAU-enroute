package cn.dkarbon.enroute.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.data.feed.SourceResult
import cn.dkarbon.enroute.ui.common.rememberListScrollState
import cn.dkarbon.enroute.ui.ledger.LedgerGroupBreak
import cn.dkarbon.enroute.ui.ledger.LedgerGutter
import cn.dkarbon.enroute.ui.ledger.LedgerGutterGap
import cn.dkarbon.enroute.ui.ledger.LedgerGroupTitle
import cn.dkarbon.enroute.ui.ledger.LedgerHeader
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
import kotlinx.coroutines.launch

/** 页眉整条高度：44dp 行 + 1dp 页眉线，收起动画按它塌缩。 */
private val FeedHeaderHeight = 45.dp

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
    val d = detail.item
    if (d != null) {
        routeStates.SaveableStateProvider("feed:detail") {
            FeedDetailView(detail = detail, vm = vm, onBack = { vm.closeDetail() })
        }
    } else {
        routeStates.SaveableStateProvider("feed:list") {
            FeedListView(ui = ui, vm = vm, modifier = modifier)
        }
    }
}

// ── 列表 ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedListView(ui: FeedUiState, vm: FeedViewModel, modifier: Modifier) {
    val t = InkTokens.current
    val cats = FeedViewModel.CATEGORIES
    val pagerState = rememberPagerState(pageCount = { cats.size })
    val scope = rememberCoroutineScope()

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
    var headerMenu by remember { mutableStateOf(false) }
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
    // 「查」只把焦点送进搜索框；搜索行仍常驻页眉下方，筛选能力一点没少
    val searchFocus = remember { FocusRequester() }

    // 页眉按需隐藏（主人点名）：只观察「当前页」的滚动状态。
    // 每个分类页在 pager 里是独立 LazyColumn，由 FeedPage 把它的 LazyListState 上报到这个槽位；
    // 切页时旧页 dispose 清空、新页重报 —— 比较相等才清，避免旧页 onDispose 把新页刚报的抹掉。
    val listStateSlot = remember { mutableStateOf<LazyListState?>(null) }
    var headerHidden by remember { mutableStateOf(false) }

    // 判定：位置单调增大 = 下滚 → 收起；减小 = 上滚 → 立即展开；到顶 → 展开。
    // 不读 isScrollInProgress（非 snapshot 态，读它触发不了流），改看「位置变化」：
    // 每次变化都重启 1.2s 计时，停滚 1.2s 没动就回显，入口不会丢。
    LaunchedEffect(listStateSlot.value) {
        val ls = listStateSlot.value
        if (ls == null) {
            headerHidden = false
            return@LaunchedEffect
        }
        var lastPos = -1L
        var stopJob: Job? = null
        snapshotFlow { ls.firstVisibleItemIndex to ls.firstVisibleItemScrollOffset }
            .collect { (idx, off) ->
                val pos = idx * 1_000_000L + off
                val atTop = idx == 0 && off == 0
                if (atTop) {
                    stopJob?.cancel(); stopJob = null
                    if (headerHidden) headerHidden = false
                } else {
                    // 每次位置变化都重启回显计时：滚动中永远轮不到它，停 1.2s 才触发
                    stopJob?.cancel()
                    stopJob = launch {
                        delay(1200)
                        headerHidden = false
                        stopJob = null
                    }
                    if (lastPos >= 0) {
                        headerHidden = pos > lastPos
                    }
                }
                lastPos = pos
            }
    }

    // 180ms 竖直收放：页眉 + 搜索行 + 截止/失败行 + 频道筛选整体塌缩，下面内容上移。
    // 页眉在 Scaffold 内容区内，状态栏 inset 由父级保留 —— 收起不会顶到状态栏。
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 全收：页眉（标题/状态/⋯/查）、搜索行、频道筛选一起收一起回。
            // AnimatedVisibility 按真实高度塌缩，收起后不留空白；内容区 weight(1f) 自动补高。
            AnimatedVisibility(
                visible = !headerHidden,
                enter = expandVertically(animationSpec = tween(180)),
                exit = shrinkVertically(animationSpec = tween(180)),
            ) {
                Column {
                    LedgerHeader(
                        title = "资讯",
                        status = headerStatus,
                        onSearch = { searchFocus.requestFocus() },
                        onMore = { headerMenu = true },
                        modifier = Modifier.requiredHeight(FeedHeaderHeight),
                    )

                    SearchRow(
                        query = ui.query,
                        onlyUnread = ui.onlyUnread,
                        starredOnly = ui.starredOnly,
                        hasFilter = ui.hasFilter,
                        focusRequester = searchFocus,
                        onQuery = vm::setQuery,
                        onToggleUnread = vm::toggleOnlyUnread,
                        onToggleStarred = vm::toggleStarredOnly,
                        onClear = vm::clearFilter,
                    )

                    // 待办层：竞赛截止行（只在「全部」频道 + 有今天/明天截止条目时出现，账页行样式）
                    if (currentCat == "全部") {
                        DeadlineRow(
                            urgent = ui.urgentCompetitions,
                            today = ui.today,
                            onOpen = { scope.launch { pagerState.animateScrollToPage(cats.indexOf("竞赛")) } },
                        )
                    }

                    // 刷新失败行：只有源失败才出现的一行小字；成功横幅不显示（页眉「HH:mm 更新」已交代）
                    ui.banner?.takeIf { it.failed.isNotEmpty() }?.let { b ->
                        RefreshFailLine(banner = b, onDetail = { failDetail = true })
                    }

                    // 分流层：频道筛选文字行（账页式，36dp，选中项下 2dp 短横线）。
                    // 与页眉同收同回；停手 1.2s / 到顶 / 回滚即展开，筛选入口不会丢。
                    ChannelFilterRow(
                        cats = cats,
                        currentPage = pagerState.currentPage,
                        onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 0,
                key = { cats[it] },
            ) { page ->
                val cat = cats[page]
                val rows = ui.rowsByChannel[cat].orEmpty()
                if (rows.isEmpty()) {
                    if (ui.loading && ui.items.isEmpty()) {
                        SkeletonList()
                    } else {
                        EmptyChannel(ui = ui, channel = cat, vm = vm)
                    }
                } else {
                    FeedPage(
                        rows = rows,
                        category = cat,
                        today = ui.today,
                        onOpen = vm::open,
                        onLongClick = { quickItem = it },
                        onToggleGroup = vm::toggleGroup,
                        onLoadMore = { vm.loadMore(cat) },
                        // 筛选签名：换筛选/搜索/密度就回顶部（新内容），只换频道不回顶部（同级回退）
                        resetKey = "${ui.query}|${ui.onlyUnread}|${ui.starredOnly}|${ui.compactByChannel[cat]}",
                        listStateSlot = listStateSlot,
                    )
                }
            }
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
            FeedQuickActions(
                item = item,
                onSetRead = { vm.setRead(item.key, it) },
                onToggleStar = { vm.toggleStar(item.key) },
                onDismiss = { quickItem = null },
            )
        }

        // 页头⋯菜单
        if (headerMenu) {
            HeaderMenu(
                ui = ui,
                category = currentCat,
                onRefresh = vm::refresh,
                onChannelRead = { vm.markChannelRead(currentCat) },
                onAllRead = { confirmAllRead = true },
                onToggleDensity = {
                    vm.setCompact(currentCat, !ui.compactOf(currentCat))
                },
                onFailDetail = { failDetail = true },
                onDismiss = { headerMenu = false },
            )
        }

        if (failDetail) {
            FailDetailSheet(
                failed = ui.banner?.failed.orEmpty(),
                onDismiss = { failDetail = false },
            )
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
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = Space.l, end = Space.l, top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕", fontSize = 15.sp, color = if (query.isBlank()) t.inkFaint else t.inkSoft)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.5.sp, color = t.ink),
            cursorBrush = SolidColor(t.inkSoft),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("搜标题 / 关键词", fontSize = 12.5.sp, color = t.inkFaint)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(48.dp).clickable(onClickLabel = "清空搜索词") { onQuery("") },
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 12.sp, color = t.inkFaint) }
        }
        // 筛选开关改成纯文字：选中靠字重 + 主色（未读）/ 琥珀（藏），不画方块底
        Box(
            Modifier
                .height(48.dp)
                .clickable(onClickLabel = "只看未读", onClick = onToggleUnread)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "未读",
                fontSize = 12.sp,
                fontWeight = if (onlyUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (onlyUnread) primaryInk() else t.inkFaint,
            )
        }
        Box(
            Modifier
                .height(48.dp)
                .clickable(onClickLabel = "只看收藏", onClick = onToggleStarred)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "藏",
                fontSize = 12.sp,
                fontWeight = if (starredOnly) FontWeight.SemiBold else FontWeight.Normal,
                color = if (starredOnly) t.amber else t.inkFaint,
            )
        }
        if (hasFilter) {
            TextAction("清", t.inkSoft, onClickLabel = "清空筛选", onClick = onClear)
        }
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

/** 刷新失败行：只有部分/全部源失败才出现，一行小字无底色无圆角无图标，右端纯文字「详情」。 */
@Composable
private fun RefreshFailLine(banner: FeedBanner, onDetail: () -> Unit) {
    val t = InkTokens.current
    val n = banner.failed.size
    if (n == 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(start = Space.l, end = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$n 个源本次没取到",
            fontSize = 11.5.sp,
            color = t.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "详情",
            fontSize = 11.5.sp,
            color = t.inkSoft,
            modifier = Modifier.clickable(onClickLabel = "失败详情", onClick = onDetail).padding(start = Space.m),
        )
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
    onLongClick: (FeedItem) -> Unit,
    onToggleGroup: (String) -> Unit,
    onLoadMore: () -> Unit,
    resetKey: String,
    listStateSlot: MutableState<LazyListState?>,
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
        contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.xs, bottom = Space.xxl),
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
                    // 组内最后一条之后不加分隔线（修正 B）：下一条是组头/分页脚/列表尾都算
                    val lastInGroup = index + 1 >= rows.size ||
                        rows[index + 1] is FeedRow.Header ||
                        rows[index + 1] is FeedRow.Footer
                    LedgerRow(
                        item = row.item,
                        today = today,
                        compact = row.compact,
                        lastInGroup = lastInGroup,
                        onClick = { onOpen(row.item) },
                        onLongClick = { onLongClick(row.item) },
                    )
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
        Text("正在抓取…", fontSize = 12.sp, color = t.inkFaint, modifier = Modifier.align(Alignment.CenterHorizontally))
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
        ui.query.isNotBlank() -> EmptyBranch("无", "没有符合条件的资讯", "换个词，或清空筛选", "清空筛选")
        ui.starredOnly && ui.items.none { it.starred } -> EmptyBranch("藏", "还没有收藏", "", "看全部")
        ui.onlyUnread && unread == 0 && total > 0 -> EmptyBranch("完", "都读完了", "「$channel」共 $total 条已读", "看全部")
        // 首次无缓存且上次刷新全失败：给可操作的重试（规格 §9.2）
        total == 0 && ui.banner?.tone == FeedBanner.Tone.FAIL -> EmptyBranch("空", "还没有内容", "检查网络后重试", "刷新")
        total == 0 -> EmptyBranch("空", if (channel == "全部") "还没有内容" else "「$channel」暂时没有内容", "", null)
        else -> EmptyBranch("空", "没有符合条件的资讯", "换个筛选条件", "清空筛选")
    }
    Box(Modifier.fillMaxSize().padding(Space.l), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(branch.glyph, fontSize = 13.sp, color = t.inkFaint)
            Spacer(Modifier.height(Space.m))
            Text(branch.title, fontSize = 13.sp, color = t.inkSoft, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(Space.xs))
            Text(branch.sub, fontSize = 11.sp, color = t.inkFaint)
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

private data class EmptyBranch(val glyph: String, val title: String, val sub: String, val action: String?)

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
@Composable
private fun SheetScaffold(
    accent: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val t = InkTokens.current
    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(Motion.base.toLong())
            onDismiss()
        }
    }
    val slide by animateDpAsState(
        targetValue = if (entered && !closing) 0.dp else 320.dp,
        animationSpec = tween(Motion.base),
        label = "feedSheet",
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    val close = { closing = true }
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (closing) 0f else 0.30f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭",
                    onClick = close,
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                // 动画值读取推迟到 Layout 阶段（评审 B11），滑入期间不重组子树
                .offset { androidx.compose.ui.unit.IntOffset(0, with(density) { slide.roundToPx() }) }
                .fillMaxWidth()
                .background(t.paper)
                .border(Stroke.strong, t.outline)
                .padding(bottom = navPad + Space.xs),
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).background(accent))
            content()
            Box(
                Modifier.fillMaxWidth().height(Space.minTouch).clickable(onClick = close),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", fontSize = 12.5.sp, color = t.inkFaint)
            }
        }
    }
}

/** 菜单动作行：序号 + 动作名 + 一句副文案（≥48dp 触区） */
@Composable
private fun MenuRow(
    index: String,
    title: String,
    sub: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
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
        Spacer(Modifier.height(2.dp))
        Text(sub, fontSize = 9.5.sp, lineHeight = 13.sp, color = t.inkFaint, maxLines = 1, modifier = Modifier.padding(start = 27.dp))
    }
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
}

@Composable
private fun FeedQuickActions(
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

    SheetScaffold(accent = t.ink, onDismiss = onDismiss) {
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
            if (unread) "红点会消失，可用「未读」筛选找回" else "恢复红点，方便稍后再读",
        ) { onSetRead(unread); onDismiss() }
        MenuRow(
            "02",
            if (item.starred) "取消收藏" else "收藏",
            if (item.starred) "从「藏」筛选中移除" else "只看收藏",
        ) { onToggleStar(); onDismiss() }
        MenuRow(
            "03",
            if (copied) "已复制到剪贴板" else "复制标题",
            if (item.category == "竞赛" && item.deadline.isNotBlank()) "标题 + 截止日 + 原文链接" else "标题 + 来源 + 原文链接",
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
        MenuRow("04", "浏览器打开", hostOf(item.url.ifBlank { item.urlAlt })) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url.ifBlank { item.urlAlt }))) }
            onDismiss()
        }
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
    onDismiss: () -> Unit,
) {
    val t = InkTokens.current
    val chUnread = ui.unreadOf(category)
    val failed = ui.banner?.failed.orEmpty()
    SheetScaffold(accent = t.primary, onDismiss = onDismiss) {
        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.s)) {
            Text("资讯操作", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.height(2.dp))
            Text("$category · $chUnread 未读", fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        // 原页头「刷新」按钮在账页 v3 里收进菜单：页眉右侧只剩「⋯ / 查」，刷新能力不丢
        MenuRow("01", "刷新", "重新抓取全部频道，旧数据可见") { onRefresh(); onDismiss() }
        MenuRow("02", "本频道全部已读", "将「$category」的 $chUnread 条未读标为已读", enabled = chUnread > 0) {
            onChannelRead(); onDismiss()
        }
        MenuRow("03", "全部频道全部已读", "共 ${ui.unread} 条未读，收藏不受影响", enabled = ui.unread > 0) {
            onAllRead(); onDismiss()
        }
        MenuRow(
            "04",
            "密度：${if (ui.compactOf(category)) "紧凑" else "标准"}",
            "只影响「$category」",
        ) { onToggleDensity(); onDismiss() }
        if (failed.isNotEmpty()) {
            MenuRow("05", "刷新失败详情", "${failed.size} 个源失败") { onFailDetail(); onDismiss() }
        }
    }
}

@Composable
private fun FailDetailSheet(failed: List<SourceResult>, onDismiss: () -> Unit) {
    val t = InkTokens.current
    SheetScaffold(accent = t.brick, onDismiss = onDismiss) {
        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.s)) {
            Text("刷新失败详情", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.height(2.dp))
            Text("${failed.size} 个源失败 · 其余源正常", fontSize = 9.5.sp, color = t.inkFaint)
        }
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            failed.forEach { f ->
                val name = cn.dkarbon.enroute.data.feed.FeedSources.byKey(f.sourceKey)?.name ?: f.sourceKey
                Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = t.ink)
                    Text(f.error.orEmpty(), fontSize = 11.sp, lineHeight = 16.sp, color = t.brick)
                }
                Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
            }
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
private fun FeedDetailView(detail: FeedDetailState, vm: FeedViewModel, onBack: () -> Unit) {
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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.xxs, end = Space.l, top = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = t.inkSoft,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text("资讯", fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.weight(1f))
            // 收藏开关：账页 v3 去掉方角字块，纯文字 —— 选中靠字重 + amber，不画底不描边
            Box(
                Modifier
                    .height(48.dp)
                    .clickable(onClickLabel = if (item.starred) "取消收藏" else "收藏") { vm.toggleStar(item.key) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "藏",
                    fontSize = 12.sp,
                    fontWeight = if (item.starred) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (item.starred) t.amber else t.inkFaint,
                )
            }
            Spacer(Modifier.width(Space.s))
            TextButton(onClick = { openUrl(item.url.ifBlank { item.urlAlt }) }) {
                // 主色文字 ≥14sp/700（规格 §2 对比裁定）
                Text("浏览器打开", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
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
                Text("正在读取正文…", fontSize = 12.sp, color = t.inkFaint)
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
                    Text("这篇取不到正文，可能是个 PDF。", fontSize = 12.sp, lineHeight = 19.sp, color = t.brick)
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
                            Text("下载", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary)
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
            Spacer(Modifier.height(Space.huge))
        }
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

private fun sanitizeSummary(s: String): String =
    s.replace("id=\"vsb_content\">", "").trim()

private fun shortDate(d: String): String =
    if (d.length >= 10) d.substring(5) else d.ifBlank { "—" }

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)
