package cn.dkarbon.enroute.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.dkarbon.enroute.data.feed.BodyPayload
import cn.dkarbon.enroute.data.feed.FeedItem
import cn.dkarbon.enroute.data.feed.FeedRepository
import cn.dkarbon.enroute.data.feed.FeedSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 资讯列表状态 */
data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val loading: Boolean = false,
    val msg: String = "",
    /** 分类筛选，`全部` 表示不筛 */
    val filter: String = "全部",
    /** 已永久缓存正文的条数 */
    val bodyCount: Int = 0,
    /** 关键词搜索（标题 + 摘要），空串表示不搜 */
    val query: String = "",
    /** 只看未读 */
    val onlyUnread: Boolean = false,
) {
    val visible: List<FeedItem>
        get() = items.filter {
            (filter == "全部" || it.category == filter) && matchQuery(it) && (!onlyUnread || it.readAt == null)
        }

    /** 关键词匹配：标题或摘要包含即命中，空格分隔多关键词（全部命中） */
    fun matchQuery(item: FeedItem): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val hay = item.title + " " + item.summary + " " + item.sourceName
        return q.split(' ', '　').filter { it.isNotBlank() }.all { hay.contains(it, ignoreCase = true) }
    }

    val unread: Int get() = items.count { it.readAt == null }

    /** 当前有没有在筛选（决定要不要显示「清空」） */
    val hasFilter: Boolean get() = query.isNotBlank() || onlyUnread
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
 * 为什么和 [cn.dkarbon.enroute.ui.TimetableViewModel] 分开：
 *   课表是「手动登录 + 学期级」的节奏，资讯是「列表刷新 + 点开抓正文」的节奏。
 *   塞进同一个 StateFlow 会让每次刷新资讯都触发课表页重组。
 */
class FeedViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FeedRepository(app)

    private val _ui = MutableStateFlow(FeedUiState())
    val ui: StateFlow<FeedUiState> = _ui.asStateFlow()

    private val _detail = MutableStateFlow(FeedDetailState())
    val detail: StateFlow<FeedDetailState> = _detail.asStateFlow()

    /**
     * ⚠️ init 里**不发起网络请求**。
     * 首次打开如果是空缓存，先渲染空态，再由界面在首帧后调 [refreshIfEmpty] 补抓 ——
     * 否则冷启动会被网络首包拖到几百毫秒甚至秒级。
     */
    init {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) { repo.loadLocal() }
            publish()
        }
    }

    /** 首帧之后再补抓（界面在 LaunchedEffect 里调一次） */
    fun refreshIfEmpty() {
        if (_ui.value.items.isEmpty() && !_ui.value.loading) refresh()
    }

    private fun publish() {
        _ui.value = _ui.value.copy(
            items = repo.items.value,
            bodyCount = repo.bodyCount(),
        )
    }

    fun setFilter(category: String) {
        _ui.value = _ui.value.copy(filter = category)
    }

    /** 关键词搜索。输入过程中不触发网络，只筛本地已抓到的条目 */
    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    fun toggleOnlyUnread() {
        _ui.value = _ui.value.copy(onlyUnread = !_ui.value.onlyUnread)
    }

    /** 一键清掉搜索词 + 未读筛选 */
    fun clearFilter() {
        _ui.value = _ui.value.copy(query = "", onlyUnread = false)
    }

    fun refresh() {
        if (_ui.value.loading) return
        _ui.value = _ui.value.copy(loading = true, msg = "刷新中…")
        viewModelScope.launch {
            val report = runCatching { repo.refresh() }.getOrElse { e ->
                _ui.value = _ui.value.copy(loading = false, msg = "刷新失败：${e.message}")
                return@launch
            }
            publish()
            _ui.value = _ui.value.copy(
                loading = false,
                msg = report.summary().let {
                    if (_ui.value.items.isEmpty()) "没抓到内容（检查网络）" else it
                },
            )
        }
    }

    /** 点进详情：命中缓存就秒开，否则当场抓 */
    fun open(item: FeedItem) {
        _detail.value = FeedDetailState(item = item, loading = true)
        viewModelScope.launch {
            val cached = repo.hasBody(item.key)
            val payload = repo.ensureBody(item)
            _detail.value = FeedDetailState(
                item = repo.items.value.firstOrNull { it.key == item.key } ?: item,
                payload = payload,
                loading = false,
            )
            if (!cached) publish()
            repo.markRead(item.key)
            publish()
        }
    }

    fun closeDetail() {
        _detail.value = FeedDetailState()
    }

    fun markAllRead() {
        viewModelScope.launch {
            repo.markAllRead()
            publish()
        }
    }

    fun toggleStar(key: String) {
        viewModelScope.launch {
            repo.toggleStar(key)
            publish()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
            publish()
        }
    }

    companion object {
        val CATEGORIES: List<String> get() = FeedSources.CATEGORIES
    }
}
