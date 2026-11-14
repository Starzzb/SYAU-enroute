package cn.dkarbon.enroute.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.data.feed.SourceResult
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.cardFloat
import cn.dkarbon.enroute.ui.theme.primaryInk
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Motion
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Soil
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    val d = detail.item
    if (d != null) {
        FeedDetailView(detail = detail, vm = vm, onBack = { vm.closeDetail() })
    } else {
        FeedListView(ui = ui, vm = vm, modifier = modifier)
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
    val haptic = LocalHapticFeedback.current

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

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FeedHeader(
                ui = ui,
                onRefresh = vm::refresh,
                onMore = { headerMenu = true },
            )

            SearchRow(
                query = ui.query,
                onlyUnread = ui.onlyUnread,
                starredOnly = ui.starredOnly,
                hasFilter = ui.hasFilter,
                onQuery = vm::setQuery,
                onToggleUnread = vm::toggleOnlyUnread,
                onToggleStarred = vm::toggleStarredOnly,
                onClear = vm::clearFilter,
            )

            // 待办层：截止提醒条（只在「全部」频道；可见性由 VM 算好，含关闭语义）
            if (currentCat == "全部" && ui.deadlineBarVisible) {
                DeadlineBar(
                    urgent = ui.urgentCompetitions,
                    today = ui.today,
                    onOpen = { scope.launch { pagerState.animateScrollToPage(cats.indexOf("竞赛")) } },
                    onDismiss = vm::dismissDeadlineBar,
                )
            }

            // 刷新结果横幅（成功 4s / 部分失败常驻 / 全失败可重试）
            ui.banner?.let { b ->
                RefreshBanner(
                    banner = b,
                    onRetry = vm::refresh,
                    onDetail = { failDetail = true },
                    onDismiss = vm::dismissBanner,
                )
            }

            // 分流层：chips（数字=未读数；长按=频道全部已读）
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = Space.minTouch).padding(top = Space.xs),
                contentPadding = PaddingValues(horizontal = Space.l),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(items = cats, key = { _, c -> c }) { i, c ->
                    ChannelChip(
                        label = c,
                        unread = ui.unreadOf(c),
                        selected = pagerState.currentPage == i,
                        accent = if (c == "全部") t.primary else categoryColor(c, t),
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (c == "全部") confirmAllRead = true else vm.markChannelRead(c)
                        },
                    )
                }
            }

            ChannelMetaRow(
                ui = ui,
                category = currentCat,
                onMarkRead = {
                    if (currentCat == "全部") confirmAllRead = true else vm.markChannelRead(currentCat)
                },
                onClearFilter = vm::clearFilter,
            )

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
                        "${ui.unread} 条未读的红点会消失，收藏和正文缓存不受影响。之后可用长按「标为未读」逐条恢复。",
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

// ── 页头 ──────────────────────────────────────────────────

@Composable
private fun FeedHeader(ui: FeedUiState, onRefresh: () -> Unit, onMore: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("资讯", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ui.unread > 0) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(t.primary))
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        "${ui.unread} 未读",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                        fontFamily = NumericFont,
                    )
                    Text(" · ", fontSize = 11.sp, color = t.rule)
                }
                Text("${ui.items.size} 条", fontSize = 11.sp, color = t.inkFaint, fontFamily = NumericFont)
                if (ui.lastSyncAt > 0) {
                    Text(" · ", fontSize = 11.sp, color = t.rule)
                    Text(
                        remember(ui.lastSyncAt) {
                            // Locale.ROOT：阿拉伯语等 locale 下也输出 ASCII 数字，与 tnum 对齐
                            val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)
                            f.format(java.util.Date(ui.lastSyncAt)) + " 更新"
                        },
                        fontSize = 11.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                    )
                }
            }
        }
        TextButton(enabled = !ui.loading, onClick = onRefresh) {
            Text(
                if (ui.loading) "刷新中…" else "刷新",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (ui.loading) t.inkFaint else t.primary,
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClickLabel = "更多操作", onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            // 视觉 32dp 字块，触区 48dp（规格 §2 触区红线）
            Box(
                Modifier.size(32.dp).border(Stroke.hairline, t.outline),
                contentAlignment = Alignment.Center,
            ) {
                Text("⋯", fontSize = 14.sp, color = t.inkSoft)
            }
        }
    }
}

// ── 搜索行 ────────────────────────────────────────────────

@Composable
private fun SearchRow(
    query: String,
    onlyUnread: Boolean,
    starredOnly: Boolean,
    hasFilter: Boolean,
    onQuery: (String) -> Unit,
    onToggleUnread: () -> Unit,
    onToggleStarred: () -> Unit,
    onClear: () -> Unit,
) {
    val t = InkTokens.current
    val onAccentText = if (t.isDark) Soil.n950 else Color.White
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.minTouch).padding(start = Space.l, end = Space.l, top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(34.dp)
                .background(t.card)
                .border(Stroke.hairline, if (query.isBlank()) t.outline else t.primary),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("⌕", fontSize = 15.sp, color = if (query.isBlank()) t.inkFaint else t.primary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = t.ink),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(t.primary),
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜标题 / 关键词", fontSize = 12.5.sp, color = t.inkFaint)
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(32.dp).clickable(onClickLabel = "清空搜索词") { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 12.sp, color = t.inkFaint) }
            }
        }

        Spacer(Modifier.width(Space.xs))
        FilterToggle("未读", onlyUnread, t.primary, onAccentText, onToggleUnread)
        Spacer(Modifier.width(Space.xs))
        FilterToggle("藏", starredOnly, t.amber, onAccentText, onToggleStarred, square = true)
        if (hasFilter) {
            Spacer(Modifier.width(Space.xs))
            Box(
                Modifier
                    .height(48.dp)
                    .width(34.dp)
                    .clickable(onClickLabel = "清空筛选", onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(34.dp).border(Stroke.hairline, t.outline),
                    contentAlignment = Alignment.Center,
                ) { Text("清", fontSize = 11.sp, color = t.inkSoft) }
            }
        }
    }
}

@Composable
private fun FilterToggle(
    label: String,
    on: Boolean,
    accent: Color,
    onAccentText: Color,
    onClick: () -> Unit,
    square: Boolean = false,
) {
    val t = InkTokens.current
    Box(
        Modifier
            .height(34.dp)
            .then(if (square) Modifier.width(34.dp) else Modifier)
            .background(if (on) accent else Color.Transparent)
            .border(Stroke.hairline, if (on) accent else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = if (square) 0.dp else Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) onAccentText else t.inkSoft,
        )
    }
}

// ── 截止提醒条 / 刷新横幅 ─────────────────────────────────

@Composable
private fun DeadlineBar(urgent: List<FeedItem>, today: java.time.LocalDate, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val t = InkTokens.current
    val first = urgent.firstOrNull() ?: return
    val days = FeedRows.daysLeft(first.deadline, today) ?: 0L
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.l, end = Space.l, top = Space.s)
            .height(40.dp)
            .background(t.card)
            .border(Stroke.hairline, t.brick)
            .clickable(onClickLabel = "查看竞赛频道", onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Stroke.accentBar).fillMaxHeight().background(t.brick))
        Spacer(Modifier.width(Space.s))
        Text(
            if (urgent.size == 1) {
                first.title.take(20) + (if (first.title.length > 20) "…" else "")
            } else {
                "${urgent.size} 项竞赛 3 天内截止"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = t.ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (days == 0L) "今天截止" else "最急剩 $days 天",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = NumericFont,
            color = t.brick,
        )
        Box(
            Modifier.size(48.dp).clickable(onClickLabel = "今日不再提醒", onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) { Text("✕", fontSize = 12.sp, color = t.inkFaint) }
    }
}

@Composable
private fun RefreshBanner(
    banner: FeedBanner,
    onRetry: () -> Unit,
    onDetail: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = InkTokens.current
    val accent = when (banner.tone) {
        FeedBanner.Tone.OK -> t.olive
        FeedBanner.Tone.PARTIAL -> t.amber
        FeedBanner.Tone.FAIL -> t.brick
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.l, end = Space.l, top = Space.xs)
            .height(36.dp)
            .background(t.card)
            .border(Stroke.hairline, accent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(Stroke.accentBar).fillMaxHeight().background(accent))
        Spacer(Modifier.width(Space.s))
        Text(
            banner.text,
            fontSize = 12.sp,
            color = t.ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when (banner.tone) {
            FeedBanner.Tone.FAIL -> Text(
                "重试",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = t.brick,
                modifier = Modifier.clickable(onClick = onRetry).padding(Space.s),
            )
            FeedBanner.Tone.PARTIAL -> Text(
                "详情",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = t.amber,
                modifier = Modifier.clickable(onClick = onDetail).padding(Space.s),
            )
            FeedBanner.Tone.OK -> Unit
        }
        if (banner.tone != FeedBanner.Tone.OK) {
            Box(
                Modifier.size(48.dp).clickable(onClickLabel = "关闭提示", onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Text("✕", fontSize = 11.sp, color = t.inkFaint) }
        }
    }
}

// ── chips / meta 行 ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelChip(
    label: String,
    unread: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    val onAccentText = if (t.isDark) Soil.n950 else Color.White
    // 账页：频道 chip 是**胶囊**，选中态不再用分类色填充（分类色里含主色派生值，
    // 壁纸取色会把它换掉）—— 改成「淡底 + 墨色描边 + 墨色文字」，
    // 层级由字重/字距承担，颜色只在未读计数上出现。
    val pill = RoundedCornerShape(if (ed) 999.dp else 0.dp)
    Row(
        Modifier
            .height(if (ed) 32.dp else 30.dp)
            .then(if (ed) Modifier.clip(pill) else Modifier)
            .background(
                when {
                    ed && selected -> t.cardAlt
                    selected -> accent
                    else -> Color.Transparent
                },
            )
            .border(
                if (ed && selected) 1.dp else Stroke.hairline,
                when {
                    ed && selected -> t.ink.copy(alpha = 0.55f)
                    ed -> t.outline.copy(alpha = 0.55f)
                    selected -> accent
                    else -> t.outline
                },
                pill,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "$label 全部已读")
            .padding(horizontal = if (ed) 14.dp else Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = if (ed) 0.3.sp else 0.sp,
            fontFamily = if (ed) f.displayFamily else null,
            color = if (selected && !ed) onAccentText else if (selected) t.ink else t.inkSoft,
        )
        if (unread > 0) {
            Spacer(Modifier.width(Space.xs))
            Text(
                unread.toString(),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (ed) f.numberFamily else NumericFont,
                color = when {
                    ed -> primaryInk()
                    selected -> onAccentText.copy(alpha = 0.75f)
                    else -> t.primary
                },
            )
        }
    }
}

@Composable
private fun ChannelMetaRow(
    ui: FeedUiState,
    category: String,
    onMarkRead: () -> Unit,
    onClearFilter: () -> Unit,
) {
    val t = InkTokens.current
    val unread = ui.unreadOf(category)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(start = Space.l, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString {
                if (ui.hasFilter) {
                    append("筛选中 · 命中 ${ui.filteredCountOf(category)} 条")
                } else {
                    append("$category · ${ui.totalOf(category)} 条")
                    if (unread > 0) append(" · $unread 未读")
                    if (category == "竞赛") append(" · 按截止排序")
                }
            },
            fontSize = 10.sp,
            color = t.inkFaint,
            fontFamily = NumericFont,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (ui.hasFilter) {
            Text(
                "清空筛选",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = t.inkSoft,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = Space.minTouch)
                    .clickable(onClick = onClearFilter)
                    .padding(horizontal = Space.s, vertical = Space.xs),
            )
        }
        if (unread > 0) {
            Text(
                "全部已读",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                // 14sp 也算小字：品牌橙在纸底只有 4.15:1，改用小字主色（5.63:1）
                color = primaryInk(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .heightIn(min = Space.minTouch)
                    .clickable(onClickLabel = "本频道全部已读", onClick = onMarkRead)
                    .padding(horizontal = Space.s, vertical = Space.xs),
            )
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
) {
    val t = InkTokens.current
    val listState = rememberLazyListState()

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

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.xs, bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        // 分组头 sticky 吸顶（规格 §5.5）；卡片/分页脚按 contentType 回收。
        // 行窗口 ≤ 30+组头，逐行发射的开销可忽略，换来吸顶语义。
        rows.forEach { row ->
            when (row) {
                is FeedRow.Header -> stickyHeader(key = row.rowKey, contentType = row.contentType) {
                    GroupHeader(
                        row = row,
                        onToggle = if (row.collapsible) ({ onToggleGroup(row.label) }) else null,
                    )
                }
                is FeedRow.Card -> item(key = row.rowKey, contentType = row.contentType) {
                    if (row.compact) {
                        CompactCard(item = row.item, today = today, onClick = { onOpen(row.item) }, onLongClick = { onLongClick(row.item) })
                    } else {
                        StandardCard(item = row.item, today = today, onClick = { onOpen(row.item) }, onLongClick = { onLongClick(row.item) })
                    }
                }
                is FeedRow.Footer -> item(key = row.rowKey, contentType = row.contentType) {
                    PageFooter(shown = row.shown, total = row.total)
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(row: FeedRow.Header, onToggle: (() -> Unit)?) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    Row(
        Modifier
            .fillMaxWidth()
            .background(t.paper)
            .heightIn(min = 28.dp)
            .then(
                if (onToggle != null) Modifier.clickable(onClickLabel = "展开或折叠 ${row.label}", onClick = onToggle)
                else Modifier,
            )
            .padding(
                top = Space.xs,
                start = if (ed) 14.dp else 0.dp,
                end = if (ed) 14.dp else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            // 账页：分组名走衬线 + 大字距（账本的「抬头」），HARD 保持 1sp 无衬线
            letterSpacing = if (ed) 1.6.sp else 1.sp,
            fontFamily = if (ed) f.displayFamily else null,
            color = t.inkFaint,
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            row.count.toString(),
            fontSize = 10.sp,
            fontFamily = if (ed) f.numberFamily else NumericFont,
            color = t.inkFaint,
        )
        if (onToggle != null) {
            Spacer(Modifier.width(Space.xs))
            Text(if (row.collapsed) "▸" else "▾", fontSize = 10.sp, color = t.inkFaint)
        }
        Spacer(Modifier.width(Space.s))
        if (ed) {
            // 账页：导引线用**点线**（2/4 间隔）—— 与「能不用线就不用线」的语汇一致，
            // 实线在这里会把分组名读成「表格标题行」，那就退回 HARD 的语言了
            Box(
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .drawBehind {
                        drawLine(
                            color = t.rule.copy(alpha = 0.85f),
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 4f)),
                        )
                    },
            )
        } else {
            Box(Modifier.weight(1f).height(Stroke.hairline).background(t.rule))
        }
    }
}

@Composable
private fun PageFooter(shown: Int, total: Int) {
    val t = InkTokens.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        Box(Modifier.fillMaxWidth().height(Space.minTouch), contentAlignment = Alignment.Center) {
            Text(
                if (shown < total) "上滑加载 · 已显示 $shown/$total" else "已到底 · 共 $total 条",
                fontSize = 10.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
            )
        }
    }
}

// ── 卡片：标准 / 紧凑 ─────────────────────────────────────

/**
 * 标准卡（全部/竞赛/教务/通知/学工默认）：
 * 身份块 32dp + 元信息 + 标题 15sp + 摘要 2 行 + 底栏。
 * 已读降级：点消失、标题 700→500、ink→inkSoft（退后但不隐藏）。
 * 紧急竞赛（≤3 天）左侧 3dp brick 色条。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StandardCard(item: FeedItem, today: java.time.LocalDate, onClick: () -> Unit, onLongClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    val unread = item.readAt == null
    val cat = categoryColor(item.category, t)
    // 查表级计算，不值得 remember 的槽位开销（评审 B10）
    val dl = if (item.category == "竞赛") FeedRows.deadlineLabel(item, today) else null
    val urgent = dl?.tone == DeadlineTone.HOT
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(if (ed) f.radiusCard else 0.dp)

    Row(
        Modifier
            .fillMaxWidth()
            // 账页：卡片是「贴在账页上的一张便签」—— 两侧留白 + 圆角 + 软阴影。
            // HARD 走原路径（满宽、方角、1dp 实线），一像素都没动。
            .then(if (ed) Modifier.padding(horizontal = 14.dp) else Modifier)
            .then(if (ed) Modifier.cardFloat(f, if (t.isDark) Color.Black else t.ink) else Modifier)
            .then(if (ed) Modifier.clip(shape) else Modifier)
            .background(t.card)
            .border(
                if (ed) f.ruleWidth else Stroke.hairline,
                if (ed) t.outline.copy(alpha = 0.30f) else t.outline,
                shape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = "${item.title} 更多操作",
            ),
    ) {
        if (urgent) {
            Box(Modifier.width(Stroke.accentBar).fillMaxHeight().background(t.brick))
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.padding(Space.m)) {
                Box(
                    Modifier
                        .size(32.dp)
                        // 账页：分类徽是一枚「小印章」（6dp 圆角），不是方块
                        .then(if (ed) Modifier.clip(RoundedCornerShape(6.dp)) else Modifier)
                        .background(cat.copy(alpha = if (t.isDark) 0.22f else 0.10f))
                        .border(Stroke.hairline, cat.copy(alpha = 0.45f), RoundedCornerShape(if (ed) 6.dp else 0.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.category.take(2),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (ed) f.displayFamily else null,
                        color = cat,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.sourceName,
                            fontSize = 10.5.sp,
                            fontWeight = if (ed) FontWeight.Normal else FontWeight.Medium,
                            letterSpacing = if (ed) 0.4.sp else 0.sp,
                            color = t.inkSoft,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(Space.xs))
                        Text("·", fontSize = 10.sp, color = t.rule)
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            shortDate(item.publishedAt),
                            fontSize = 9.5.sp,
                            color = t.inkFaint,
                            fontFamily = if (ed) f.numberFamily else NumericFont,
                        )
                        Spacer(Modifier.weight(1f))
                        if (item.starred) {
                            StarMark()
                            Spacer(Modifier.width(Space.xs))
                        }
                        if (unread) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (ed) primaryInk() else t.primary))
                        }
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        if (item.titleTruncated) item.title.trimEnd('.', '…') + "…" else item.title,
                        // 账页：条目标题走衬线（与课表里的课名同一族），靠字族 + 字重做层级
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = if (unread) t.ink else t.inkSoft,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = if (ed) f.titleTracking else 0.sp,
                        fontFamily = if (ed) f.displayFamily else null,
                        maxLines = 2,
                    )
                    val s = sanitizeSummary(item.summary)
                    if (s.isNotBlank()) {
                        Spacer(Modifier.height(Space.xs))
                        Text(s.take(100), fontSize = 12.sp, lineHeight = 18.sp, color = t.inkFaint, maxLines = 2)
                    }
                }
            }
            // 账页：卡片内部**不画分隔线**（与网格同一条规矩：能不用线就不用线），
            // 靠 4dp 间距 + 底部那行小字的位置来分区。HARD 保留原来的 1dp 实线。
            if (ed) {
                Spacer(Modifier.height(Space.xs))
            } else {
                Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.outline.copy(alpha = 0.7f)))
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(horizontal = Space.m, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.category,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = if (ed) 1.2.sp else 0.sp,
                    color = cat,
                )
                dl?.let {
                    Spacer(Modifier.width(Space.s))
                    Text("·", fontSize = 9.sp, color = t.rule)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        it.text,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (ed) f.numberFamily else NumericFont,
                        color = when (it.tone) {
                            DeadlineTone.HOT -> t.brick
                            DeadlineTone.WARM -> t.amber
                            DeadlineTone.DEAD -> t.inkFaint
                        },
                    )
                }
                if (item.platformState.isNotBlank()) {
                    Spacer(Modifier.width(Space.s))
                    Text("·", fontSize = 9.sp, color = t.rule)
                    Spacer(Modifier.width(Space.s))
                    Text(item.platformState, fontSize = 9.5.sp, color = t.inkFaint)
                }
                Spacer(Modifier.weight(1f))
                Text("›", fontSize = 13.sp, color = t.inkFaint)
            }
        }
    }
}

/**
 * 紧凑卡（就业/新闻默认）：3dp 频道色条 + 标题 + 单行元信息。
 * 一屏 6–9 条快速扫过；整卡可点可长按。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactCard(item: FeedItem, today: java.time.LocalDate, onClick: () -> Unit, onLongClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    val ed = f.editorial
    val unread = item.readAt == null
    val cat = categoryColor(item.category, t)
    val dl = if (item.category == "竞赛") FeedRows.deadlineLabel(item, today) else null
    val barColor = if (dl?.tone == DeadlineTone.HOT) t.brick else cat
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(if (ed) f.radiusCard else 0.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (ed) Modifier.padding(horizontal = 14.dp) else Modifier)
            .then(if (ed) Modifier.cardFloat(f, if (t.isDark) Color.Black else t.ink) else Modifier)
            .then(if (ed) Modifier.clip(shape) else Modifier)
            .background(t.card)
            .border(
                if (ed) f.ruleWidth else Stroke.hairline,
                if (ed) t.outline.copy(alpha = 0.30f) else t.outline,
                shape,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onLongClickLabel = "${item.title} 更多操作",
            ),
    ) {
        // 频道色条：账页里改成 4dp（更宽的「书签边」），靠 clip 自动带圆角
        Box(Modifier.width(if (ed) 4.dp else Stroke.accentBar).fillMaxHeight().background(barColor))
        Column(Modifier.weight(1f).padding(start = Space.m, end = Space.m, top = Space.s, bottom = Space.s)) {
            Text(
                if (item.titleTruncated) item.title.trimEnd('.', '…') + "…" else item.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = if (ed) f.titleTracking else 0.sp,
                fontFamily = if (ed) f.displayFamily else null,
                color = if (unread) t.ink else t.inkSoft,
                maxLines = 2,
            )
            Spacer(Modifier.height(Space.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.sourceName + " · " + shortDate(item.publishedAt),
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    color = t.inkFaint,
                    fontFamily = if (ed) f.numberFamily else NumericFont,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                dl?.let {
                    Text(
                        " · " + it.text,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (ed) f.numberFamily else NumericFont,
                        color = when (it.tone) {
                            DeadlineTone.HOT -> t.brick
                            DeadlineTone.WARM -> t.amber
                            DeadlineTone.DEAD -> t.inkFaint
                        },
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (item.starred) {
                    StarMark()
                    Spacer(Modifier.width(Space.xs))
                }
                if (unread) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (ed) primaryInk() else t.primary))
                }
            }
        }
    }
}

/** 收藏标：方角「藏」字块（16dp 高，amber） */
@Composable
private fun StarMark() {
    val t = InkTokens.current
    Box(
        Modifier
            .height(16.dp)
            .border(Stroke.hairline, t.amber)
            .padding(horizontal = Space.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text("藏", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = t.amber)
    }
}

// ── 骨架 / 空态 ───────────────────────────────────────────

/** 静态骨架（红线：无 shimmer 无渐变）：3 张卡形 + 一句真实状态 */
@Composable
private fun SkeletonList() {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxSize().padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text("正在抓取…", fontSize = 12.sp, color = t.inkFaint, modifier = Modifier.align(Alignment.CenterHorizontally))
        repeat(3) {
            Column(
                Modifier.fillMaxWidth().background(t.card).border(Stroke.hairline, t.outline).padding(Space.m),
            ) {
                Row {
                    Box(Modifier.size(32.dp).background(t.cardAlt))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth().height(12.dp).background(t.rule))
                        Spacer(Modifier.height(Space.s))
                        Box(Modifier.fillMaxWidth(0.64f).height(12.dp).background(t.rule))
                    }
                }
                Spacer(Modifier.height(Space.m))
                Box(Modifier.fillMaxWidth().height(10.dp).background(t.rule))
                Spacer(Modifier.height(Space.xs))
                Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(t.rule))
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
        ui.starredOnly && ui.items.none { it.starred } -> EmptyBranch("藏", "还没有收藏", "长按卡片，点「收藏」", "看全部")
        ui.onlyUnread && unread == 0 && total > 0 -> EmptyBranch("完", "都读完了", "「$channel」共 $total 条已读", "看全部")
        // 首次无缓存且上次刷新全失败：给可操作的重试（规格 §9.2）
        total == 0 && ui.banner?.tone == FeedBanner.Tone.FAIL -> EmptyBranch("空", "还没有内容", "检查网络后重试", "刷新")
        total == 0 -> EmptyBranch("空", if (channel == "全部") "还没有内容" else "「$channel」暂时没有内容", "点右上角刷新", null)
        else -> EmptyBranch("空", "没有符合条件的资讯", "换个筛选条件", "清空筛选")
    }
    Box(Modifier.fillMaxSize().padding(Space.l), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(38.dp).border(Stroke.hairline, t.outline), contentAlignment = Alignment.Center) {
                Text(branch.glyph, fontSize = 13.sp, color = t.inkFaint)
            }
            Spacer(Modifier.height(Space.m))
            Text(branch.title, fontSize = 13.sp, color = t.inkSoft, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(Space.xs))
            Text(branch.sub, fontSize = 11.sp, color = t.inkFaint)
            if (branch.action != null) {
                Spacer(Modifier.height(Space.m))
                Box(
                    Modifier
                        .height(34.dp)
                        .border(Stroke.hairline, t.outline)
                        .clickable {
                            when (branch.action) {
                                "清空筛选" -> vm.clearFilter()
                                "刷新" -> vm.refresh()
                                "看全部" -> {
                                    if (ui.onlyUnread) vm.toggleOnlyUnread()
                                    if (ui.starredOnly) vm.toggleStarredOnly()
                                }
                            }
                        }
                        .padding(horizontal = Space.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(branch.action, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary)
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
    val cat = categoryColor(item.category, t)
    val unread = item.readAt == null
    var copied by remember { mutableStateOf(false) }

    SheetScaffold(accent = cat, onDismiss = onDismiss) {
        Row(Modifier.padding(start = Space.l, end = Space.l, top = Space.m, bottom = Space.s)) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(cat.copy(alpha = if (t.isDark) 0.22f else 0.10f))
                    .border(Stroke.hairline, cat.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Text(item.category.take(2), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cat) }
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
            if (item.starred) "从「藏」筛选中移除" else "在搜索行点「藏」只看收藏",
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
        MenuRow("01", "本频道全部已读", "将「$category」的 $chUnread 条未读标为已读", enabled = chUnread > 0) {
            onChannelRead(); onDismiss()
        }
        MenuRow("02", "全部频道全部已读", "共 ${ui.unread} 条未读，收藏不受影响", enabled = ui.unread > 0) {
            onAllRead(); onDismiss()
        }
        MenuRow(
            "03",
            "卡片密度：${if (ui.compactOf(category)) "紧凑" else "标准"}",
            "只影响「$category」，点一下切换",
        ) { onToggleDensity(); onDismiss() }
        if (failed.isNotEmpty()) {
            MenuRow("04", "刷新失败详情", "${failed.size} 个源失败") { onFailDetail(); onDismiss() }
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
    val cat = categoryColor(item.category, t)
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
            // 收藏开关（方角「藏」字块，选中 amber 实底）
            Box(
                Modifier
                    .size(32.dp)
                    .background(if (item.starred) t.amber else Color.Transparent)
                    .border(Stroke.hairline, t.amber)
                    .clickable(onClickLabel = if (item.starred) "取消收藏" else "收藏") { vm.toggleStar(item.key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "藏",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (item.starred) (if (t.isDark) Soil.n950 else Color.White) else t.amber,
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
                Box(
                    Modifier
                        .size(30.dp)
                        .background(cat.copy(alpha = if (t.isDark) 0.22f else 0.10f))
                        .border(Stroke.hairline, cat.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Text(item.category.take(2), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cat) }
                Spacer(Modifier.width(Space.s))
                Column {
                    Text(item.sourceName, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = t.ink)
                    Spacer(Modifier.height(1.dp))
                    Text(item.publishedAt.ifBlank { "日期未标注" }, fontSize = 9.5.sp, color = t.inkFaint, fontFamily = NumericFont)
                }
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
                    Column(
                        Modifier.fillMaxWidth().background(t.card).border(Stroke.hairline, t.outline).padding(Space.m),
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
                            Text(p, fontSize = 14.sp, lineHeight = 25.sp, color = t.ink)
                        }
                    }
                } else {
                    Text("这篇取不到正文，可能是个 PDF。", fontSize = 12.sp, lineHeight = 19.sp, color = t.brick)
                }

                if (payload.isEmpty || payload.text.isBlank()) {
                    Spacer(Modifier.height(Space.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Box(
                            Modifier.height(34.dp).border(Stroke.hairline, t.outline)
                                .clickable(onClick = vm::retryDetail).padding(horizontal = Space.l),
                            contentAlignment = Alignment.Center,
                        ) { Text("重试", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = t.primary) }
                        Box(
                            Modifier.height(34.dp).border(Stroke.hairline, t.outline)
                                .clickable { openUrl(item.url.ifBlank { item.urlAlt }) }.padding(horizontal = Space.l),
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
                                .background(t.card)
                                .border(Stroke.hairline, t.outline)
                                .clickable { openUrl(a.url) }
                                .padding(horizontal = Space.m, vertical = Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(20.dp).border(Stroke.hairline, t.inkFaint), contentAlignment = Alignment.Center) {
                                Text("附", fontSize = 9.sp, color = t.inkSoft)
                            }
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
                        .border(Stroke.hairline, t.outline)
                        .clickable { openUrl(item.url.ifBlank { item.urlAlt }) }
                        .padding(horizontal = Space.m, vertical = Space.m),
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

/** 频道 → 颜色（语义跟设计文档：钢青=教务，主色=竞赛） */
internal fun categoryColor(category: String, t: InkTokenSet): Color = when (category) {
    "教务" -> t.slate
    "竞赛" -> t.primary
    "就业" -> if (t.isDark) Color(0xFFB99BA6) else Color(0xFF5A3A46)
    "通知" -> t.amber
    "学工" -> t.olive
    "新闻" -> if (t.isDark) Color(0xFF7E9CC0) else Color(0xFF37516B)
    else -> t.inkFaint
}

private fun shortDate(d: String): String =
    if (d.length >= 10) d.substring(5) else d.ifBlank { "—" }

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)
