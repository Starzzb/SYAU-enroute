package cn.dkarbon.enroute.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.dkarbon.enroute.data.Prefs
import cn.dkarbon.enroute.data.feed.BodyPayload
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.data.feed.FeedRepository
import cn.dkarbon.enroute.data.feed.FeedSources
import cn.dkarbon.enroute.data.feed.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 刷新结果横幅（规格 §9.3）：成功 4s 自动收起；失败常驻可重试 */
data class FeedBanner(
    val tone: Tone,
    val text: String,
    val at: Long,
    val failed: List<SourceResult> = emptyList(),
) {
    enum class Tone { OK, PARTIAL, FAIL }
}

/** 撤销条：最近一批已读的 key 快照（新批覆盖旧批） */
data class UndoBatch(val keys: Set<String>, val label: String, val at: Long)

/** 资讯列表状态 */
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = false,
    /** 分类筛选，`全部` 表示不筛 */
    val filter: String = "全部",
    /** 关键词搜索（标题 + 摘要 + 来源），空串表示不搜 */
    val query: String = "",
    /** 只看未读 */
    val onlyUnread: Boolean = false,
    /** 只看收藏（与未读 AND） */
    val starredOnly: Boolean = false,
    /** 上次成功刷新时间戳；0 = 从未 */
    val lastSyncAt: Long = 0,
    /** 刷新结果横幅；null = 不显示 */
    val banner: FeedBanner? = null,
    /** 最近一批已读快照（撤销条）；null = 不显示 */
    val undo: UndoBatch? = null,
    /** 频道 → 紧凑密度 */
    val compactByChannel: Map<String, Boolean> = emptyMap(),
    /** 频道 → 渲染窗口（可见条目上限） */
    val windowByChannel: Map<String, Int> = emptyMap(),
    /** 已折叠的竞赛组名 */
    val collapsedGroups: Set<String> = setOf(CompGroups.CLOSED),
    /** 派生行缓存：频道 → 行列表（VM 计算，UI 只读） */
    val rowsByChannel: Map<String, List<FeedRow>> = emptyMap(),
    /** 频道 → 过滤后条数（meta 行/分页 guard 用，避免组合期重算） */
    val filteredCountByChannel: Map<String, Int> = emptyMap(),
    /** 今天日期基准：屏幕级取一次，刷新/跨天重算（禁止逐卡 LocalDate.now()） */
    val today: LocalDate = LocalDate.now(),
    /** 紧急竞赛（0≤剩余≤3 天，与已读无关）：VM 算好，UI 只读 */
    val urgentCompetitions: List<FeedItem> = emptyList(),
    /** 截止提醒条可见性：VM 算好（含「当日+key集合」关闭语义），UI 不读 Prefs */
    val deadlineBarVisible: Boolean = false,
    /** 最近一次刷新每个源的结果（⋯ 菜单「来源」组显示抓取状态）；空 = 本次还没刷过 */
    val sourceResults: List<SourceResult> = emptyList(),
) {
    /** 关键词匹配（保留旧 API，单测在用；与 FeedRows.matchQuery 同规则） */
    fun matchQuery(item: FeedItem): Boolean = FeedRows.matchQuery(item, query)

    val visible: List<FeedItem>
        get() = items.filter {
            (filter == "全部" || it.category == filter) &&
                matchQuery(it) &&
                (!onlyUnread || it.readAt == null) &&
                (!starredOnly || it.starred)
        }

    val unread: Int get() = items.count { it.readAt == null }

    fun unreadOf(category: String): Int =
        if (category == "全部") unread else items.count { it.category == category && it.readAt == null }

    fun totalOf(category: String): Int =
        if (category == "全部") items.size else items.count { it.category == category }

    val hasFilter: Boolean get() = query.isNotBlank() || onlyUnread || starredOnly

    fun compactOf(category: String): Boolean = compactByChannel[category] ?: false

    fun windowOf(category: String): Int = windowByChannel[category] ?: FeedRows.PAGE

    /** 过滤后条数（VM 派生缓存；未算出时回退未过滤总数） */
    fun filteredCountOf(category: String): Int =
        filteredCountByChannel[category] ?: totalOf(category)
}

/** 详情页状态：正文 + 文中链接 + 附件 */
data class FeedDetailState(
    val item: FeedItem? = null,
    val payload: BodyPayload = BodyPayload(),
    val loading: Boolean = false,
)

/**
 * 资讯的 ViewModel。
 *
 * 2026-11-13 评审规格落地：
 * · 派生行（过滤/排序/分组/折叠/窗口）全部在这里算，pager 页只读结果
 * · 批量已读一次写盘 + 撤销快照；收藏接上 UI
 * · 正文缓存命中不闪加载态
 * · init 不发网络；首帧后 refreshIfEmpty；回前台超 30 分钟静默刷新
 */
class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FeedRepository(app)
    private val prefs = Prefs(app)

    private val _ui = MutableStateFlow(FeedUiState(lastSyncAt = prefs.feedLastSyncAt))
    val ui: StateFlow<FeedUiState> = _ui.asStateFlow()

    private val _detail = MutableStateFlow(FeedDetailState())
    val detail: StateFlow<FeedDetailState> = _detail.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.loadLocal() }
            publish()
        }
    }

    /** 首帧之后再补抓（界面在 LaunchedEffect 里调一次） */
    fun refreshIfEmpty() {
        if (_ui.value.items.isEmpty() && !_ui.value.loading) refresh()
    }

    /** 回到前台：距上次成功刷新 > 30 分钟 → 静默刷新（旧数据可见，无阻塞提示） */
    fun refreshIfStale() {
        val last = _ui.value.lastSyncAt
        // last==0（从未成功但有旧缓存）视为无穷旧，也要刷（评审 B7）
        if ((last == 0L || System.currentTimeMillis() - last > 30 * 60 * 1000L) && !_ui.value.loading) {
            refresh()
        }
        // 跨天重算日期基准（倒计时/分桶都吃它）
        val now = LocalDate.now()
        if (_ui.value.today != now) {
            _ui.value = _ui.value.copy(today = now)
            recomputeAll()
        }
    }

    // ── 派生行 ────────────────────────────────────────────

    /** cancel-and-replace：快速输入/批量已读时只留最新一次重算（评审 A2） */
    private var recomputeJob: kotlinx.coroutines.Job? = null

    private fun publish() {
        val s = _ui.value
        val compact = CATEGORIES.associateWith { prefs.feedCompact(it) }
        // items 与 compact 一起进状态，recompute 读的是同一份快照（避免读到旧 items）
        _ui.value = s.copy(items = repo.items.value, compactByChannel = compact)
        recompute(_ui.value)
    }

    /** 全频道重算派生行（items/筛选/折叠/窗口变化时）。800 条量级 <5ms，Default 调度。 */
    private fun recompute(s: FeedUiState) {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch(Dispatchers.Default) {
            val rows = LinkedHashMap<String, List<FeedRow>>()
            val filteredCounts = LinkedHashMap<String, Int>()
            for (cat in CATEGORIES) {
                val (r, total) = FeedRows.build(
                    items = s.items,
                    category = cat,
                    query = s.query,
                    onlyUnread = s.onlyUnread,
                    starredOnly = s.starredOnly,
                    compact = s.compactOf(cat),
                    collapsed = if (cat == "竞赛") s.collapsedGroups else emptySet(),
                    window = s.windowOf(cat),
                    today = s.today,
                )
                rows[cat] = r
                filteredCounts[cat] = total
            }
            // 紧急竞赛与提醒条可见性也在这里算好（评审 B9：组合期不做 O(N) 派生、不读 Prefs）
            val urgent = s.items.filter {
                it.category == "竞赛" && it.deadline.isNotBlank() &&
                    (FeedRows.daysLeft(it.deadline, s.today)?.let { d -> d in 0..3 } == true)
            }.sortedBy { it.deadline }
            val day = s.today.toString()
            val urgentKeys = urgent.map { it.key }.toSet()
            val barVisible = urgent.isNotEmpty() &&
                !(prefs.deadlineDismissDay == day && prefs.deadlineDismissKeys == urgentKeys)
            _ui.value = _ui.value.copy(
                rowsByChannel = rows,
                filteredCountByChannel = filteredCounts,
                urgentCompetitions = urgent,
                deadlineBarVisible = barVisible,
            )
        }
    }

    private fun recomputeAll() = recompute(_ui.value)

    /** 筛选变化 → 所有频道窗口重置 30（规格 §6.1） */
    private fun resetWindows(s: FeedUiState): FeedUiState = s.copy(windowByChannel = emptyMap())

    fun setFilter(category: String) {
        if (_ui.value.filter == category) return
        _ui.value = _ui.value.copy(filter = category)
    }

    fun setQuery(q: String) {
        if (_ui.value.query == q) return
        _ui.value = resetWindows(_ui.value.copy(query = q))
        recomputeAll()
    }

    fun toggleOnlyUnread() {
        _ui.value = resetWindows(_ui.value.copy(onlyUnread = !_ui.value.onlyUnread))
        recomputeAll()
    }

    fun toggleStarredOnly() {
        _ui.value = resetWindows(_ui.value.copy(starredOnly = !_ui.value.starredOnly))
        recomputeAll()
    }

    fun clearFilter() {
        _ui.value = resetWindows(_ui.value.copy(query = "", onlyUnread = false, starredOnly = false))
        recomputeAll()
    }

    /** 列表滑到倒数第 5 条 → 窗口 +30（纯本地，无网络无 spinner）。
     *  total 用**过滤后**条数（评审 B12），筛选态下窗口不虚增。 */
    fun loadMore(category: String) {
        val s = _ui.value
        val total = s.filteredCountOf(category)
        val cur = s.windowOf(category)
        if (cur >= total) return
        _ui.value = s.copy(windowByChannel = s.windowByChannel + (category to (cur + FeedRows.PAGE)))
        recomputeAll()
    }

    fun toggleGroup(label: String) {
        val s = _ui.value
        val next = if (label in s.collapsedGroups) s.collapsedGroups - label else s.collapsedGroups + label
        _ui.value = s.copy(collapsedGroups = next)
        recomputeAll()
    }

    fun setCompact(category: String, compact: Boolean) {
        prefs.setFeedCompact(category, compact)
        _ui.value = _ui.value.copy(compactByChannel = _ui.value.compactByChannel + (category to compact))
        recomputeAll()
    }

    // ── 刷新 ──────────────────────────────────────────────

    fun refresh() {
        if (_ui.value.loading) return
        _ui.value = _ui.value.copy(loading = true)
        viewModelScope.launch {
            val report = runCatching { repo.refresh() }.getOrElse { e ->
                _ui.value = _ui.value.copy(
                    loading = false,
                    banner = FeedBanner(
                        FeedBanner.Tone.FAIL,
                        "刷新失败 · ${(e.message ?: "网络不可用").take(30)}",
                        System.currentTimeMillis(),
                    ),
                )
                return@launch
            }
            val failed = report.perSource.filter { it.error != null }
            val ok = report.failed < report.perSource.size
            if (ok) prefs.feedLastSyncAt = report.at
            val banner = when {
                report.perSource.isNotEmpty() && failed.size == report.perSource.size ->
                    FeedBanner(FeedBanner.Tone.FAIL, "刷新失败 · ${(failed.first().error ?: "网络不可用").take(30)}", report.at, failed)
                failed.isNotEmpty() ->
                    FeedBanner(FeedBanner.Tone.PARTIAL, "新增 ${report.totalNew} 条 · ${failed.size} 个源失败", report.at, failed)
                else ->
                    FeedBanner(FeedBanner.Tone.OK, if (report.totalNew > 0) "新增 ${report.totalNew} 条" else "已是最新", report.at)
            }
            // 只发布一次（评审 A2）：banner/loading/lastSyncAt/today 进状态后
            // 由 publish() 统一灌 items + 重算，不做双写
            _ui.value = _ui.value.copy(
                loading = false,
                lastSyncAt = if (ok) report.at else _ui.value.lastSyncAt,
                banner = banner,
                today = LocalDate.now(),
                sourceResults = report.perSource,
            )
            publish()
        }
    }

    /** 成功横幅 4s 自动收起（UI LaunchedEffect 调） */
    fun expireBanner() {
        val b = _ui.value.banner ?: return
        if (b.tone == FeedBanner.Tone.OK && System.currentTimeMillis() - b.at >= 4000) {
            _ui.value = _ui.value.copy(banner = null)
        }
    }

    fun dismissBanner() {
        _ui.value = _ui.value.copy(banner = null)
    }

    // ── 已读 / 撤销 / 收藏 ────────────────────────────────

    /** 频道级全部已读（无确认，出撤销条）。category=null 表示全局。 */
    fun markChannelRead(category: String?) {
        viewModelScope.launch {
            val keys = repo.markReadBatch(category)
            if (keys.isEmpty()) return@launch
            // ⚠️ 必须用 ${keys.size}：写成 $keys.size 会把整个 key 集合打进文案
            // （2026-11-13 真机验收抓到：撤销条显示成一长串 news|1010|24386…）
            val label = if (category == null) "全部 ${keys.size} 条" else "「$category」${keys.size} 条"
            _ui.value = _ui.value.copy(undo = UndoBatch(keys, "已把$label 标为已读", System.currentTimeMillis()))
            publish()
        }
    }

    /** 撤销最近一批已读（5s 窗口内，UI 计时） */
    fun undoRead() {
        val batch = _ui.value.undo ?: return
        _ui.value = _ui.value.copy(undo = null)
        viewModelScope.launch {
            repo.markUnreadBatch(batch.keys)
            publish()
        }
    }

    fun expireUndo() {
        val u = _ui.value.undo ?: return
        if (System.currentTimeMillis() - u.at >= 5000) {
            _ui.value = _ui.value.copy(undo = null)
        }
    }

    /** 单条已读/未读切换（长按菜单） */
    fun setRead(key: String, read: Boolean) {
        viewModelScope.launch {
            if (read) repo.markRead(key) else repo.markUnreadBatch(setOf(key))
            publish()
        }
    }

    fun toggleStar(key: String) {
        viewModelScope.launch {
            repo.toggleStar(key)
            publish()
        }
    }

    // ── 详情 ──────────────────────────────────────────────

    /**
     * 点进详情：**缓存命中直接出内容，不闪「正在读取…」**（修正规格 D8）。
     * 未命中才进加载态；抓完永久缓存。
     */
    fun open(item: FeedItem) {
        val cached = repo.hasBody(item.key)
        _detail.value = FeedDetailState(item = item, loading = !cached)
        viewModelScope.launch {
            val payload = repo.ensureBody(item)
            _detail.value = FeedDetailState(
                item = repo.items.value.firstOrNull { it.key == item.key } ?: item,
                payload = payload,
                loading = false,
            )
            // 已读重读不改状态（repo.markRead 内部已幂等）；publish 只走一次
            repo.markRead(item.key)
            publish()
        }
    }

    /** 详情页重试（正文抓取失败时） */
    fun retryDetail() {
        val item = _detail.value.item ?: return
        open(item)
    }

    fun closeDetail() {
        _detail.value = FeedDetailState()
    }

    fun markAllRead() {
        markChannelRead(null)
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
            publish()
        }
    }

    // ── 截止提醒条关闭语义（当日 + key 集合）────────────────

    fun dismissDeadlineBar() {
        prefs.deadlineDismissDay = _ui.value.today.toString()
        prefs.deadlineDismissKeys = _ui.value.urgentCompetitions.map { it.key }.toSet()
        _ui.value = _ui.value.copy(deadlineBarVisible = false)
    }

    companion object {
        val CATEGORIES: List<String> get() = FeedSources.CATEGORIES
    }
}