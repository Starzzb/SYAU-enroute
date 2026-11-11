package cn.dkarbon.enroute.ui.feed

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 资讯页。
 *
 * 展示口径（主人定）：**标题 + ≤100 字摘要 + 来源**；正文**点进去才抓**，
 * 抓完永久存本地 → 第二次（或别人）打开直接命中缓存。
 *
 * ## 排版（2026-11-11 重做）
 * 参考**知乎的信息层级** + **微博的时间线头部**，但落到硬朗体系里：
 *
 *   知乎给的是「标题够大 / 摘要够灰 / 元信息压到最小字号」这三级落差；
 *   微博给的是「左侧一个方寸见方的身份块，右边一列内容」这条骨架。
 *
 * 于是卡片变成：
 * ```
 *   ┌──────────────────────────────────┐
 *   │ [教务]  教务通知 · 11-10      ●   │  ← 身份块 + 元信息（未读点）
 *   │         关于下学期选课的通知        │  ← 标题 15sp/700
 *   │         各学院：现将下学期选课…     │  ← 摘要 12sp 灰，3 行
 *   │ ───────────────────────────────  │  ← 卡内 1dp 通栏细线（知乎 footer 分界）
 *   │ 教务            已缓存正文   读全文 ›│  ← 底栏：真实状态，不编数字
 *   └──────────────────────────────────┘
 * ```
 *
 * **没有「3 万阅读 / 128 赞」这种数字** —— 我们没有这些数据，编出来就是骗人。
 * 底栏只放真实可算的东西：分类、正文是否已缓存、竞赛倒计时。
 *
 * 另外两条硬要求保持：
 *   1. 分类之间**左右滑动**（[HorizontalPager]），chips 与滑动双向同步
 *   2. 启动时不发网络请求，首帧之后才补抓（[FeedViewModel.refreshIfEmpty]）
 */
@Composable
fun FeedScreen(ui: FeedUiState, vm: FeedViewModel, modifier: Modifier = Modifier) {
    val detail by vm.detail.collectAsState()
    BackHandler(enabled = detail.item != null) { vm.closeDetail() }

    val d = detail.item
    if (d != null) {
        FeedDetailView(detail = detail, onBack = { vm.closeDetail() })
    } else {
        FeedListView(ui = ui, vm = vm, modifier = modifier)
    }
}

// ── 列表 ──────────────────────────────────────────────────

@Composable
private fun FeedListView(ui: FeedUiState, vm: FeedViewModel, modifier: Modifier) {
    val t = InkTokens.current
    val cats = FeedViewModel.CATEGORIES
    val pagerState = rememberPagerState(pageCount = { cats.size })
    val scope = rememberCoroutineScope()

    // 首帧后补抓一次：冷启动不被网络拖住
    LaunchedEffect(Unit) { vm.refreshIfEmpty() }
    // 滑动到哪个分类，就把筛选同步过去
    LaunchedEffect(pagerState.currentPage) { vm.setFilter(cats[pagerState.currentPage]) }

    // 每个频道的条数（chips 上显示，避免点进去空手）
    val counts = remember(ui.items) {
        ui.items.groupingBy { it.category }.eachCount()
    }

    Column(modifier.fillMaxSize()) {
        // ── 页头 ──
        Row(
            Modifier.fillMaxWidth().padding(start = Space.l, end = Space.s, top = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("资讯", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        "${ui.items.size} 条",
                        fontSize = 10.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                    )
                    if (ui.unread > 0) {
                        Text(" · ", fontSize = 10.sp, color = t.rule)
                        Text(
                            "未读 ${ui.unread}",
                            fontSize = 10.sp,
                            color = t.primary,
                            fontWeight = FontWeight.Medium,
                            fontFamily = NumericFont,
                        )
                    }
                }
            }
            TextButton(enabled = !ui.loading, onClick = { vm.refresh() }) {
                Text(
                    if (ui.loading) "刷新中…" else "刷新",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ui.loading) t.inkFaint else t.primary,
                )
            }
        }

        // ── 搜索 + 筛选 ──
        SearchRow(
            query = ui.query,
            onlyUnread = ui.onlyUnread,
            hasFilter = ui.hasFilter,
            onQuery = vm::setQuery,
            onToggleUnread = vm::toggleOnlyUnread,
            onClear = vm::clearFilter,
        )

        // ── 频道 chips：横向可滚（源会越加越多，不能均分挤成一团）──
        LazyRow(
            Modifier.fillMaxWidth().padding(top = Space.xs),
            contentPadding = PaddingValues(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            items(items = cats, key = { it }) { c ->
                val i = cats.indexOf(c)
                ChannelChip(
                    label = c,
                    count = if (c == "全部") ui.items.size else counts[c] ?: 0,
                    selected = pagerState.currentPage == i,
                    accent = if (c == "全部") t.primary else categoryColor(c, t),
                    onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                )
            }
        }

        if (ui.msg.isNotBlank()) {
            Text(
                ui.msg,
                fontSize = 9.5.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(start = Space.l, end = Space.l, top = Space.xs),
            )
        }

        Spacer(Modifier.height(Space.s))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { page ->
            val cat = cats[page]
            val list = ui.items.filter {
                (cat == "全部" || it.category == cat) &&
                    ui.matchQuery(it) &&
                    (!ui.onlyUnread || it.readAt == null)
            }
            if (list.isEmpty()) {
                EmptyChannel(
                    loading = ui.loading,
                    channel = cat,
                    searching = ui.hasFilter,
                )
            } else {
                FeedList(list = list, onOpen = { vm.open(it) })
            }
        }
    }
}

/**
 * 搜索行：方角输入框 + 「未读」筛选开关。
 *
 * 刻意不做「点放大镜才展开」——资讯这种场景就是要随手能搜，
 * 多一次点击等于劝退。输入即筛，只过滤本地已抓到的条目，不发网络请求。
 */
@Composable
private fun SearchRow(
    query: String,
    onlyUnread: Boolean,
    hasFilter: Boolean,
    onQuery: (String) -> Unit,
    onToggleUnread: () -> Unit,
    onClear: () -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.s),
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
            // 放大镜用一个方角「⌕」符号位，不用 emoji
            Box(
                Modifier.width(32.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text("⌕", fontSize = 15.sp, color = if (query.isBlank()) t.inkFaint else t.primary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.5.sp,
                    color = t.ink,
                ),
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
                    Modifier.size(32.dp).clickable { onQuery("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = 12.sp, color = t.inkFaint)
                }
            }
        }

        Spacer(Modifier.width(Space.xs))

        // 未读筛选
        Box(
            Modifier
                .height(34.dp)
                .background(if (onlyUnread) t.primary else Color.Transparent)
                .border(Stroke.hairline, if (onlyUnread) t.primary else t.outline)
                .clickable(onClick = onToggleUnread)
                .padding(horizontal = Space.m),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "未读",
                fontSize = 12.sp,
                fontWeight = if (onlyUnread) FontWeight.Bold else FontWeight.Normal,
                color = if (onlyUnread) Color.White else t.inkSoft,
            )
        }

        if (hasFilter) {
            Spacer(Modifier.width(Space.xs))
            Box(
                Modifier.size(34.dp).border(Stroke.hairline, t.outline).clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Text("清", fontSize = 11.sp, color = t.inkSoft)
            }
        }
    }
}

/** 一条列表行：要么是日期分组头，要么是一条资讯 */
private sealed interface FeedRow {
    data class DateHeader(val label: String) : FeedRow
    data class Item(val item: FeedItem) : FeedRow
}

@Composable
private fun FeedList(list: List<FeedItem>, onOpen: (FeedItem) -> Unit) {
    // 竞赛频道换一套排序和分组：别的频道按「发布日期」倒序，竞赛按「报名截止」正序 ——
    // 快截止的排最前面才有意义，不然最新发布的往往是最不急的。
    val ordered = remember(list) {
        if (list.firstOrNull()?.category != "竞赛") {
            list
        } else {
            list.sortedWith(
                compareBy<FeedItem> { openRank(it) }
                    .thenBy { it.deadline.ifBlank { "9999-12-31" } },
            )
        }
    }

    // 日期分桶：今天 / 昨天 / 本周 / 更早（竞赛是：即将截止 / 报名中 / 已截止 / 时间待定）
    val rows = remember(ordered) {
        buildList {
            var last = ""
            ordered.forEach { item ->
                val b = bucketOf(item)
                if (b != last) {
                    add(FeedRow.DateHeader(b))
                    last = b
                }
                add(FeedRow.Item(item))
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Space.l,
            end = Space.l,
            top = Space.xs,
            bottom = Space.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        items(
            items = rows,
            key = {
                when (it) {
                    is FeedRow.DateHeader -> "h:${it.label}"
                    is FeedRow.Item -> it.item.key
                }
            },
            contentType = { if (it is FeedRow.DateHeader) "header" else "card" },
        ) { row ->
            when (row) {
                is FeedRow.DateHeader -> DateHeaderRow(row.label)
                is FeedRow.Item -> FeedCard(row.item) { onOpen(row.item) }
            }
        }
    }
}

@Composable
private fun DateHeaderRow(label: String) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = t.inkFaint,
        )
        Spacer(Modifier.width(Space.s))
        Box(Modifier.weight(1f).height(Stroke.hairline).background(t.rule))
    }
}

@Composable
private fun ChannelChip(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier
            .height(30.dp)
            .background(if (selected) accent else Color.Transparent)
            .border(Stroke.hairline, if (selected) accent else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else t.inkSoft,
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            count.toString(),
            fontSize = 9.5.sp,
            fontFamily = NumericFont,
            color = if (selected) Color.White.copy(alpha = 0.75f) else t.inkFaint,
        )
    }
}

/**
 * 信息流卡片。
 *
 * 三级字号落差是这张卡的全部重点：
 *   标题 15sp/700 → 摘要 12sp/400 灰 → 元信息 9.5~10sp 更灰。
 * 之前的版本标题只有 13.5sp、摘要是 10.5sp，读完像一张表格而不是一条消息。
 */
@Composable
private fun FeedCard(item: FeedItem, onClick: () -> Unit) {
    val t = InkTokens.current
    val unread = item.readAt == null
    val cat = categoryColor(item.category, t)

    Column(
        Modifier
            .fillMaxWidth()
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(Space.m)) {
            // 身份块：微博的头像位，改成方角徽记（两字缩写 + 分类色）
            Box(
                Modifier
                    .size(38.dp)
                    .background(cat.copy(alpha = if (t.isDark) 0.22f else 0.10f))
                    .border(Stroke.hairline, cat.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.category.take(2),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = cat,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                // 来源 · 日期（微博那一行，但日期用等宽字保证竖向对齐）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.sourceName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
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
                        fontFamily = NumericFont,
                    )
                    if (unread) {
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(5.dp).clip(CircleShape).background(t.primary))
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (item.titleTruncated) item.title.trimEnd('.', '…') + "…" else item.title,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = t.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                val s = sanitizeSummary(item.summary)
                if (s.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        s.take(100),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = t.inkFaint,
                        maxLines = 3,
                    )
                }
            }
        }

        // 卡内通栏细线：知乎卡片 footer 的那道分界，也是硬朗体系里「表格感」的来源
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.outline.copy(alpha = 0.7f)))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.m, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.category,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
                color = cat,
            )
            deadlineText(item)?.let { (txt, hot) ->
                Spacer(Modifier.width(Space.s))
                Text("·", fontSize = 9.sp, color = t.rule)
                Spacer(Modifier.width(Space.s))
                Text(
                    txt,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (hot) t.brick else t.inkFaint,
                    fontFamily = NumericFont,
                )
            }
            Spacer(Modifier.weight(1f))
            // 底栏只留「真的会变」的信息。原来这里还有「已存正文」和「读全文 ›」，
            // 前者是缓存实现细节、后者整张卡都能点 —— 全是噪音，删了。
            Text("›", fontSize = 13.sp, color = t.inkFaint)
        }
    }
}

@Composable
private fun EmptyChannel(loading: Boolean, channel: String, searching: Boolean = false) {
    val t = InkTokens.current
    Box(Modifier.fillMaxSize().padding(Space.l), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(38.dp)
                    .border(Stroke.hairline, t.outline),
                contentAlignment = Alignment.Center,
            ) {
                Text("空", fontSize = 13.sp, color = t.inkFaint)
            }
            Spacer(Modifier.height(Space.m))
            Text(
                when {
                    loading -> "正在抓取…"
                    searching -> "没有符合条件的"
                    channel == "全部" -> "还没有内容"
                    else -> "「$channel」暂时没有内容"
                },
                fontSize = 13.sp,
                color = t.inkSoft,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                if (searching) "换个词，或点「清」" else "点右上角刷新",
                fontSize = 11.sp,
                color = t.inkFaint,
            )
        }
    }
}

// ── 详情 ──────────────────────────────────────────────────

/**
 * 详情页排版（知乎文章页的信息顺序）：
 * 身份行 → 大标题 → 通栏线 → 正文（按段分开，段间距 14dp）
 * → 附件 → 文中链接 → 原文入口。
 */
@Composable
private fun FeedDetailView(detail: FeedDetailState, onBack: () -> Unit) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val item = detail.item ?: return
    val cat = categoryColor(item.category, t)

    val openUrl: (String) -> Unit = { u ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部条
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
            TextButton(onClick = {
                openUrl(item.url.ifBlank { item.urlAlt })
            }) { Text("浏览器打开", fontSize = 11.5.sp, color = t.primary) }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            // 身份行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(cat.copy(alpha = if (t.isDark) 0.22f else 0.10f))
                        .border(Stroke.hairline, cat.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(item.category.take(2), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cat)
                }
                Spacer(Modifier.width(Space.s))
                Column {
                    Text(
                        item.sourceName,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        item.publishedAt,
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                        fontFamily = NumericFont,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))
            Text(
                item.title,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
            )
            Spacer(Modifier.height(Space.m))
            Box(Modifier.fillMaxWidth().height(Stroke.emphasis).background(t.ink))
            Spacer(Modifier.height(Space.l))

            val payload = detail.payload
            if (detail.loading) {
                Text("正在读取…", fontSize = 13.sp, color = t.inkFaint)
            } else {
                if (payload.text.isNotBlank()) {
                    // 按换行分段：一整块 Text 读起来是一堵墙
                    val paras = remember(payload.text) {
                        payload.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        paras.forEach { p ->
                            Text(
                                p,
                                fontSize = 14.sp,
                                lineHeight = 25.sp,
                                color = t.ink,
                            )
                        }
                    }
                } else {
                    Text(
                        "这篇取不到正文，可能是个 PDF。",
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = t.brick,
                    )
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
                            // 不用 emoji 当图标：一个方角「附」字块
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .border(Stroke.hairline, t.inkFaint),
                                contentAlignment = Alignment.Center,
                            ) {
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
                            Text("下载", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = t.primary)
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
                            Text(
                                l.text,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = t.slate,
                                maxLines = 2,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (payload.isEmpty) {
                    Spacer(Modifier.height(Space.m))
                    Text(
                        "没有可显示的内容。",
                        fontSize = 11.sp,
                        color = t.brick,
                    )
                }

                // 原文入口（知乎文章底部「查看原文」的位置）
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
                    Text("查看原文 →", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = t.primary)
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

/** 摘要清洗：早期版本可能把 `id="vsb_content">` 这段残渣存进摘要 */
private fun sanitizeSummary(s: String): String =
    s.replace("id=\"vsb_content\">", "").trim()

/**
 * 频道 → 颜色。语义跟着设计文档走：**钢青 = 教务系统**，主色（农械橘）留给最该被点开的
 * 「竞赛」；新闻用农机蓝灰，避免和主色撞。
 */
private fun categoryColor(category: String, t: InkTokenSet): Color = when (category) {
    "教务" -> t.slate
    "竞赛" -> t.primary
    "就业" -> if (t.isDark) Color(0xFFB99BA6) else Color(0xFF5A3A46)
    "通知" -> t.amber
    "学工" -> t.olive
    "新闻" -> if (t.isDark) Color(0xFF7E9CC0) else Color(0xFF37516B)
    else -> t.inkFaint
}

/** 列表里日期只留「MM-DD」，年份靠分组头兜底 */
private fun shortDate(d: String): String =
    if (d.length >= 10) d.substring(5) else d

/** 日期分桶：今天 / 昨天 / 本周 / 更早；竞赛频道换成报名状态分桶 */
private fun bucketOf(item: FeedItem): String {
    if (item.category == "竞赛") {
        val d = daysLeft(item.deadline) ?: return "时间待定"
        return when {
            d < 0 -> "已截止"
            d <= 7 -> "即将截止"
            else -> "报名中"
        }
    }
    val date = item.publishedAt
    if (date.isBlank()) return "更早"
    return try {
        val d = LocalDate.parse(date)
        val today = LocalDate.now()
        when {
            d == today -> "今天"
            d == today.minusDays(1) -> "昨天"
            d.isAfter(today.minusDays(7)) -> "本周"
            else -> "更早"
        }
    } catch (_: Exception) {
        "更早"
    }
}

/** 竞赛排序档位：0=还开着，1=已截止，2=时间没写 */
private fun openRank(item: FeedItem): Int {
    val d = daysLeft(item.deadline) ?: return 2
    return if (d < 0) 1 else 0
}

/** 距离截止还有几天；没写截止日就返回 null（不猜） */
private fun daysLeft(deadline: String): Long? = try {
    val due = LocalDate.parse(deadline)
    java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), due)
} catch (_: Exception) {
    null
}

/**
 * 竞赛倒计时。只有真的解析出截止日期才显示 —— 宁可没有，不编一个。
 * 返回 (文案, 是否紧急)。
 */
private fun deadlineText(item: FeedItem): Pair<String, Boolean>? {
    if (item.category != "竞赛" || item.deadline.isBlank()) return null
    return try {
        val due = LocalDate.parse(item.deadline)
        val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), due)
        when {
            days < 0 -> "已截止" to false
            days == 0L -> "今天截止" to true
            days <= 7 -> "剩 $days 天" to true
            else -> "截止 ${item.deadline.substring(5)}" to false
        }
    } catch (_: Exception) {
        null
    }
}

/** 从 URL 里取域名，给「查看原文」那行做提示 */
private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)
