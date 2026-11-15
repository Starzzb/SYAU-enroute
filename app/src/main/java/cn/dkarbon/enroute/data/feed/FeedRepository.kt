package cn.dkarbon.enroute.data.feed

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * ## 并发契约（2026-11-13 代码评审 A1 修复）
 *
 * 刷新期间列表**可点可收藏**（规格 §6.4），所以：
 * · 抓取阶段只产出解析结果，**不碰** `_items`；
 * · 「读最新快照 → 合并 → 赋值 → 写盘」整体在 [stateMutex] 里做，
 *   [updateItem]/[markReadBatch]/[markUnreadBatch] 共用同一把锁 ——
 *   刷新结束不会把期间发生的已读/收藏/正文标记冲掉。
 */
class FeedRepository(private val ctx: Context) {

    private val store = FeedStore(ctx)

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items.asStateFlow()

    /** 保护「读快照→改→赋值→写盘」整段；网络请求一律在锁外 */
    private val stateMutex = Mutex()

    suspend fun loadLocal() = withContext(Dispatchers.IO) {
        val loaded = store.loadItems()
        stateMutex.withLock { _items.value = loaded }
    }

    // ── 刷新列表 ──────────────────────────────────────────

    /**
     * 抓所有启用的源，合并入库。
     *
     * 铁律：**单源失败不影响其他源**。一个栏目改版不能把整次刷新变成失败。
     */
    suspend fun refresh(): SyncReport = withContext(Dispatchers.IO) {
        val results = mutableListOf<SourceResult>()
        // 每源的解析产物（锁外收集）；fresh 判定用刷新开始时的 key 集即可 ——
        // 期间用户新增的本地状态不影响「哪些条目是新的」
        val knownKeys = _items.value.map { it.key }.toHashSet()
        val fetched = mutableListOf<Pair<FeedSource, List<FeedItem>>>()
        // 票 20 / D1：整次刷新计时（给「抓取优化」留基线）
        val refreshStart = System.nanoTime()

        for (src in FeedSources.ALL.filter { it.enabled }) {
            val srcStart = System.nanoTime()
            val r = runCatching {
                // 主页 + 已实测翻页语义的额外页（串行、页间 400ms，单页失败不拖垮整源）
                val pages = mutableListOf<FeedHttp.Resp>()
                val first = FeedHttp.get(src.listUrl)
                require(first.status in 200..299) { "HTTP ${first.status}" }
                pages += first
                for (u in src.extraPages) {
                    delay(400)
                    val resp = runCatching { FeedHttp.get(u) }.getOrNull()
                    if (resp != null && resp.status in 200..299) pages += resp
                }

                val list = LinkedHashMap<String, FeedItem>()
                for (resp in pages) {
                    val parsed = when (src.kind) {
                        "generic" -> FeedParser.parseListGeneric(resp.body, resp.finalUrl, src)
                        "cxcy" -> FeedParser.parseListCxcy(resp.body, resp.finalUrl, src)
                        "tw" -> FeedParser.parseListTw(resp.body, resp.finalUrl, src)
                        "cahe" -> FeedParser.parseListCahe(resp.body, resp.finalUrl, src)
                        "jingsai" -> FeedParser.parseListJingsai(resp.body, resp.finalUrl, src)
                        else -> FeedParser.parseList(resp.body, resp.finalUrl, src)
                    }
                    parsed.forEach { list.putIfAbsent(it.key, it) }
                }
                val items = list.values.toList()
                require(items.isNotEmpty()) { "解析出 0 条（页面结构可能变了）" }

                // 解析质量哨兵（评审 B5）：cxcy 源有条目但报名窗口全空 →
                // 多半是「报名时间」格式漂移，报警但不编数据
                if (src.kind == "cxcy" && items.none { it.deadline.isNotBlank() || it.publishedAt.isNotBlank() }) {
                    DebugLog.error("FEED", "${src.name}：解析出 ${items.size} 条但日期全空，页面格式可能变了")
                }

                // 竞赛平台：列表已带报名窗口/主办方/状态；详情只补「比赛窗口」并预热正文缓存。
                // 只补**新的**（老的上一轮已经补过），每轮封顶 6 个、间隔 400ms。
                val withDates = if (src.kind == "cxcy") {
                    val freshOnes = items.filter { it.key !in knownKeys }
                    val fixed = enrichCxcy(freshOnes).associateBy { it.key }
                    items.map { fixed[it.key] ?: it }
                } else {
                    items
                }

                fetched += src to withDates
                val srcMs = (System.nanoTime() - srcStart) / 1_000_000
                DebugLog.log("FEED", "${src.name}：${srcMs}ms · ${pages.size} 页 · ${items.size} 条")
                SourceResult(src.key, items.size, 0)
            }.getOrElse { e ->
                val m = e.message ?: e.javaClass.simpleName
                val srcMs = (System.nanoTime() - srcStart) / 1_000_000
                DebugLog.error("FEED", "${src.name} 失败（${srcMs}ms）：$m")
                SourceResult(src.key, 0, 0, m)
            }
            results += r
        }

        // ── 合并入库：基于**当时最新**快照，整段持锁（A1 修复核心）──
        var addedTotal = 0
        val merged = stateMutex.withLock {
            val existing = _items.value.associateBy { it.key }.toMutableMap()
            val counted = results.map { sr ->
                val pair = fetched.firstOrNull { it.first.key == sr.sourceKey } ?: return@map sr
                var fresh = 0
                for (item in pair.second) {
                    val old = existing[item.key]
                    if (old == null) {
                        existing[item.key] = item
                        fresh++
                    } else {
                        // 保留本地状态（已读/收藏/正文/补全过的标题）；
                        // 平台自报状态会随时间变（比赛中→已结束），以最新抓取为准
                        existing[item.key] = old.copy(
                            title = if (old.bodyFetched) old.title else item.title,
                            summary = item.summary.ifBlank { old.summary },
                            publishedAt = item.publishedAt.ifBlank { old.publishedAt },
                            url = item.url.ifBlank { old.url },
                            urlAlt = item.urlAlt.ifBlank { old.urlAlt },
                            deadline = item.deadline.ifBlank { old.deadline },
                            regStart = item.regStart.ifBlank { old.regStart },
                            contestStart = item.contestStart.ifBlank { old.contestStart },
                            contestEnd = item.contestEnd.ifBlank { old.contestEnd },
                            organizer = item.organizer.ifBlank { old.organizer },
                            platformState = item.platformState.ifBlank { old.platformState },
                        )
                    }
                }
                addedTotal += fresh
                sr.copy(added = fresh)
            }
            results.clear()
            results.addAll(counted)

            val sorted = existing.values
                .sortedWith(
                    // 无日期条目（我爱竞赛网）按首见时间参与主排序，不永远沉底（评审 C7）
                    compareByDescending<FeedItem> { it.publishedAt.ifBlank { "0000" } }
                        .thenByDescending { it.firstSeenAt },
                )
                .take(MAX_ITEMS)
            _items.value = sorted
            store.saveItems(sorted)
            sorted
        }
        val totalMs = (System.nanoTime() - refreshStart) / 1_000_000
        val okCount = results.count { it.error == null }
        DebugLog.log(
            "FEED",
            "刷新总计 ${totalMs}ms · ${results.size} 源（成功 $okCount / 失败 ${results.size - okCount}）· 新增 $addedTotal 条，本地共 ${merged.size} 条",
        )
        SyncReport(results, System.currentTimeMillis(), (System.nanoTime() - refreshStart) / 1_000_000)
    }

    // ── 竞赛平台补比赛窗口 ────────────────────────────────

    /**
     * 竞赛管理平台：**列表页已带报名窗口**（2026-11-13 实测升级），
     * 这里只对**每轮新增**的竞赛条目补抓一次详情，拿「比赛窗口」并预热正文缓存。
     * 上限 6 个、间隔 400ms —— 学校平台是生产环境，不是给我压测的。
     * **尝试即计数**（评审 B6）：失败请求也占限额，平台大面积 500 时不会逐条硬打。
     * 成果公示（articledetail）没有报名字段，跳过补抓，正文留给点开时抓。
     */
    private suspend fun enrichCxcy(items: List<FeedItem>): List<FeedItem> {
        if (items.isEmpty()) return items
        var n = 0
        val out = mutableListOf<FeedItem>()
        for (it0 in items) {
            if (n >= CXCY_ENRICH_LIMIT || it0.url.contains("articledetail")) {
                out += it0
                continue
            }
            n++
            val resp = runCatching { FeedHttp.get(it0.url) }.getOrNull()
            delay(400)
            if (resp == null || resp.status !in 200..299) {
                out += it0
                continue
            }
            val info = runCatching { FeedParser.parseCxcyDetail(resp.body) }
                .getOrDefault(FeedParser.CompInfo())
            val payload = runCatching { FeedParser.parseBodyGeneric(resp.body, resp.finalUrl) }
                .getOrDefault(BodyPayload())
            if (!payload.isEmpty) store.saveBody(it0.key, payload)
            out += it0.copy(
                // 不拿报名开始日冒充发布日（评审 B4）：publishedAt 留空，
                // 排序由 firstSeenAt 兜底；报名开始日在 regStart 里单独展示
                deadline = it0.deadline.ifBlank { info.deadline },
                regStart = it0.regStart.ifBlank { info.startAt },
                contestStart = it0.contestStart.ifBlank { info.contestStart },
                contestEnd = it0.contestEnd.ifBlank { info.contestEnd },
                organizer = it0.organizer.ifBlank { info.organizer },
                summary = it0.summary.ifBlank { FeedSummary.clean(payload.text) },
                bodyFetched = !payload.isEmpty,
            )
        }
        DebugLog.log("FEED", "竞赛平台：补了 $n 条的比赛窗口")
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

        // ② 没命中，抓。先试静态页，失败再试备用地址
        // tw 也是博达 VSB 站群（vsb_content 正文）；其余非 vsb 族走通用正文提取
        val kind = FeedSources.byKey(item.sourceKey)?.kind
        val generic = kind != null && kind != "vsb" && kind != "tw"
        val candidates = listOf(item.url, item.urlAlt).filter { it.isNotBlank() }.distinct()
        for (u in candidates) {
            val resp = runCatching { FeedHttp.get(u) }.getOrNull() ?: continue
            if (resp.status !in 200..299) continue

            val payload = when {
                // 我爱竞赛网：精确切 article_content，别让侧栏推荐混进正文
                kind == "jingsai" -> FeedParser.parseBodyJingsai(resp.body, resp.finalUrl)
                generic -> FeedParser.parseBodyGeneric(resp.body, resp.finalUrl)
                else -> FeedParser.parseBody(resp.body, resp.finalUrl)
            }
            if (payload.isEmpty) continue

            store.saveBody(item.key, payload)

            // 顺带把被 CMS 截断的标题补全；竞赛条目顺手捞一下报名截止日
            val fullTitle = FeedParser.parseArticleTitle(resp.body)
            val deadline = if (item.category == "竞赛") FeedParser.extractDeadline(payload.text) else ""
            // 竞赛平台详情：补「比赛窗口」（列表只有报名窗口）
            val comp = if (kind == "cxcy") {
                runCatching { FeedParser.parseCxcyDetail(resp.body) }.getOrDefault(FeedParser.CompInfo())
            } else null
            // 我爱竞赛网：列表没日期，进详情才拿得到发布时间与摘要
            val jm = if (kind == "jingsai") FeedParser.parseJingsaiMeta(resp.body) else null
            updateItem(item.key) { old ->
                val bodySummary = FeedSummary.clean(payload.text)
                old.copy(
                    title = if (old.titleTruncated && fullTitle.length > old.title.length) fullTitle else old.title,
                    summary = if (old.summary.length < 12) {
                        FeedSummary.clean(jm?.first.orEmpty()).ifBlank { bodySummary }
                    } else old.summary,
                    publishedAt = old.publishedAt.ifBlank { jm?.second.orEmpty() },
                    bodyFetched = true,
                    deadline = old.deadline.ifBlank { deadline },
                    contestStart = old.contestStart.ifBlank { comp?.contestStart.orEmpty() },
                    contestEnd = old.contestEnd.ifBlank { comp?.contestEnd.orEmpty() },
                    organizer = old.organizer.ifBlank { comp?.organizer.orEmpty() },
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

    suspend fun markRead(key: String) = updateItem(key) {
        // 已读重读不刷新时间戳（评审 B8）：已读是状态，不是「最近读过」
        if (it.readAt == null) it.copy(readAt = System.currentTimeMillis()) else it
    }

    /**
     * 批量置已读（频道级 / 全局级共用）。
     * **一次状态发布 + 一次写盘** —— 逐条循环调 [updateItem] 会 N 次重写 items.json。
     * @param category null = 全部频道
     * @return 本次真正被标记的 key 集合（撤销用；空集合 = 没有未读）
     */
    suspend fun markReadBatch(category: String?): Set<String> = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val now = System.currentTimeMillis()
            val hit = mutableSetOf<String>()
            val updated = _items.value.map {
                if (it.readAt == null && (category == null || it.category == category)) {
                    hit += it.key
                    it.copy(readAt = now)
                } else it
            }
            if (hit.isNotEmpty()) {
                _items.value = updated
                store.saveItems(updated)
            }
            hit
        }
    }

    /** 批量恢复未读（撤销 / 长按「标为未读」）。同样一次写盘。 */
    suspend fun markUnreadBatch(keys: Set<String>) = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext
        stateMutex.withLock {
            val updated = _items.value.map { if (it.key in keys) it.copy(readAt = null) else it }
            _items.value = updated
            store.saveItems(updated)
        }
    }

    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        markReadBatch(null)
        Unit
    }

    suspend fun toggleStar(key: String) = updateItem(key) { it.copy(starred = !it.starred) }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            store.clear()
            _items.value = emptyList()
        }
    }

    // ── 内部 ──────────────────────────────────────────────

    private suspend fun updateItem(key: String, block: (FeedItem) -> FeedItem) = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            val updated = _items.value.map { if (it.key == key) block(it) else it }
            _items.value = updated
            store.saveItems(updated)
        }
    }

    companion object {
        /** 本地最多留多少条（几百 KB，够了；更老的靠正文缓存仍在） */
        private const val MAX_ITEMS = 800

        /** 每轮刷新最多给几个竞赛条目补抓详情（控频：学校平台是生产环境） */
        private const val CXCY_ENRICH_LIMIT = 6
    }
}
