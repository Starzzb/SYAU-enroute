package cn.dkarbon.enroute.data.feed

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 资讯仓库：刷列表 + 按需抓正文 + 永久缓存。
 *
 * ## 核心策略（2026-11-10 主人定）
 *
 * **列表**：一次刷新把各源列表页抓下来，合并入库（几百条量级）。
 * **正文**：**点进去才抓**，抓完写进 `feed/bodies/` **永久保存** ——
 *         下一个人（或你自己第二次）点开直接命中缓存，0 请求。
 *
 * 这条「按需 + 永久缓存」的设计正好是从「端上」平滑迁到「服务器」的形态：
 * 以后把 [FeedStore.body]/[FeedStore.saveBody] 换成 HTTP 调用就是一套共享缓存服务，
 * 上层 UI 一行不用改。
 */
class FeedRepository(private val ctx: Context) {

    private val store = FeedStore(ctx)

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items.asStateFlow()


    suspend fun loadLocal() = withContext(Dispatchers.IO) {
        _items.value = store.loadItems()
    }

    // ── 刷新列表 ──────────────────────────────────────────

    /**
     * 抓所有启用的源，合并入库。
     *
     * 铁律：**单源失败不影响其他源**。一个栏目改版不能把整次刷新变成失败。
     */
    suspend fun refresh(): SyncReport = withContext(Dispatchers.IO) {
        val existing = _items.value.associateBy { it.key }.toMutableMap()
        val results = mutableListOf<SourceResult>()
        var added = 0

        for (src in FeedSources.ALL.filter { it.enabled }) {
            val r = runCatching {
                val resp = FeedHttp.get(src.listUrl)
                require(resp.status in 200..299) { "HTTP ${resp.status}" }
                val list = when (src.kind) {
                    "generic" -> FeedParser.parseListGeneric(resp.body, resp.finalUrl, src)
                    "cxcy" -> FeedParser.parseListCxcy(resp.body, resp.finalUrl, src)
                    else -> FeedParser.parseList(resp.body, resp.finalUrl, src)
                }
                require(list.isNotEmpty()) { "解析出 0 条（页面结构可能变了）" }

                // 竞赛平台：列表页没有日期，新条目去详情页补「报名窗口」。
                // 只补**新的**（老的上一轮已经补过），每轮封顶 6 个、间隔 400ms。
                val withDates = if (src.kind == "cxcy") {
                    val freshOnes = list.filter { existing[it.key] == null }
                    val fixed = enrichCxcy(freshOnes).associateBy { it.key }
                    list.map { fixed[it.key] ?: it }
                } else {
                    list
                }

                var fresh = 0
                for (item in withDates) {
                    val old = existing[item.key]
                    if (old == null) {
                        existing[item.key] = item
                        fresh++
                    } else {
                        // 保留本地状态（已读/收藏/正文/补全过的标题）
                        existing[item.key] = old.copy(
                            title = if (old.bodyFetched) old.title else item.title,
                            summary = item.summary.ifBlank { old.summary },
                            publishedAt = item.publishedAt.ifBlank { old.publishedAt },
                            url = item.url.ifBlank { old.url },
                            urlAlt = item.urlAlt.ifBlank { old.urlAlt },
                            deadline = item.deadline.ifBlank { old.deadline },
                        )
                    }
                }
                added += fresh
                DebugLog.log("FEED", "${src.name}：抓到 ${list.size} 条，新增 $fresh")
                SourceResult(src.key, list.size, fresh)
            }.getOrElse { e ->
                val m = e.message ?: e.javaClass.simpleName
                DebugLog.error("FEED", "${src.name} 失败：$m")
                SourceResult(src.key, 0, 0, m)
            }
            results += r
        }

        val merged = existing.values
            .sortedWith(compareByDescending<FeedItem> { it.publishedAt }.thenByDescending { it.firstSeenAt })
            .take(MAX_ITEMS)

        _items.value = merged
        store.saveItems(merged)
        SyncReport(results, System.currentTimeMillis())
    }

    // ── 竞赛平台补日期 ────────────────────────────────────

    /**
     * 竞赛管理平台的列表页**没有日期**，但详情页有 `报名时间：A 至 B`。
     *
     * 所以对**每轮新增**的竞赛条目补抓一次详情（老条目上一轮补过，不重复抓）。
     * 上限 6 个、间隔 400ms —— 学校平台是生产环境，不是给我压测的。
     * 顺手把正文存进缓存：用户点进去直接秒开，不用再请求一次。
     */
    private suspend fun enrichCxcy(items: List<FeedItem>): List<FeedItem> {
        if (items.isEmpty()) return items
        var n = 0
        val out = mutableListOf<FeedItem>()
        for (it0 in items) {
            if (n >= CXCY_ENRICH_LIMIT) {
                out += it0
                continue
            }
            val resp = runCatching { FeedHttp.get(it0.url) }.getOrNull()
            if (resp == null || resp.status !in 200..299) {
                out += it0
                continue
            }
            n++
            delay(400)
            val info = runCatching { FeedParser.parseCxcyDetail(resp.body) }
                .getOrDefault(FeedParser.CompInfo())
            val payload = runCatching { FeedParser.parseBodyGeneric(resp.body, resp.finalUrl) }
                .getOrDefault(BodyPayload())
            if (!payload.isEmpty) store.saveBody(it0.key, payload)
            out += it0.copy(
                // 报名开始日就是这条竞赛「出现」的时间，拿它当排序键
                publishedAt = info.startAt.ifBlank { info.contestStart },
                deadline = info.deadline,
                summary = payload.text.take(100),
                bodyFetched = !payload.isEmpty,
            )
        }
        DebugLog.log("FEED", "竞赛平台：补了 $n 条的报名时间")
        return out
    }

    // ── 正文（按需 + 永久缓存）───────────────────────────

    /**
     * 取正文：**先查本地缓存，没有再抓**。
     *
     * @return 正文纯文本；抓不到返回空串（正文可能是 PDF，需要用户点「查看原文」）
     */
    suspend fun ensureBody(item: FeedItem): BodyPayload = withContext(Dispatchers.IO) {
        // ① 命中缓存 —— 这就是「下一个人直接能看」
        store.body(item.key)?.let { cached ->
            if (!item.bodyFetched) updateItem(item.key) { it.copy(bodyFetched = true) }
            return@withContext cached
        }

        // ② 没命中，抓。先试静态页，失败再试 content.jsp
        val generic = FeedSources.byKey(item.sourceKey)?.kind == "generic"
        val candidates = listOf(item.url, item.urlAlt).filter { it.isNotBlank() }.distinct()
        for (u in candidates) {
            val resp = runCatching { FeedHttp.get(u) }.getOrNull() ?: continue
            if (resp.status !in 200..299) continue

            val payload = if (generic) {
                FeedParser.parseBodyGeneric(resp.body, resp.finalUrl)
            } else {
                FeedParser.parseBody(resp.body, resp.finalUrl)
            }
            if (payload.isEmpty) continue

            store.saveBody(item.key, payload)

            // 顺带把被 CMS 截断的标题补全；竞赛条目顺手捞一下报名截止日
            val fullTitle = FeedParser.parseArticleTitle(resp.body)
            val deadline = if (item.category == "竞赛") FeedParser.extractDeadline(payload.text) else ""
            updateItem(item.key) { old ->
                old.copy(
                    title = if (old.titleTruncated && fullTitle.length > old.title.length) fullTitle else old.title,
                    summary = if (old.summary.length < 12) payload.text.take(100) else old.summary,
                    bodyFetched = true,
                    deadline = old.deadline.ifBlank { deadline },
                )
            }
            DebugLog.log("FEED", "抓正文成功：${item.title.take(20)}… (${payload.text.length} 字 / 附件 ${payload.attachments.size} / 链接 ${payload.links.size})")
            return@withContext payload
        }

        DebugLog.error("FEED", "抓正文失败：${item.title.take(20)}…（正文可能是 PDF 或页面改版）")
        BodyPayload()
    }

    fun hasBody(key: String): Boolean = store.hasBody(key)

    fun bodyCount(): Int = store.bodyCount()

    // ── 已读 / 收藏 ───────────────────────────────────────

    suspend fun markRead(key: String) = updateItem(key) { it.copy(readAt = System.currentTimeMillis()) }

    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updated = _items.value.map { if (it.readAt == null) it.copy(readAt = now) else it }
        _items.value = updated
        store.saveItems(updated)
    }

    suspend fun toggleStar(key: String) = updateItem(key) { it.copy(starred = !it.starred) }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        store.clear()
        _items.value = emptyList()
    }

    // ── 内部 ──────────────────────────────────────────────

    private suspend fun updateItem(key: String, block: (FeedItem) -> FeedItem) = withContext(Dispatchers.IO) {
        val updated = _items.value.map { if (it.key == key) block(it) else it }
        _items.value = updated
        store.saveItems(updated)
    }

    companion object {
        /** 本地最多留多少条（几百 KB，够了；更老的靠正文缓存仍在） */
        private const val MAX_ITEMS = 800

        /** 每轮刷新最多给几个竞赛条目补抓详情（控频：学校平台是生产环境） */
        private const val CXCY_ENRICH_LIMIT = 6
    }
}
