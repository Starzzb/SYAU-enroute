package cn.dkarbon.enroute.data.map

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * 拉取共享数据集（票 22 骨架）。
 *
 * ## 只发一次请求的三种「没事」情况
 * | 情况 | 服务端 | 客户端 |
 * |---|---|---|
 * | 距上次检查 < 5 分钟 | — | **根本不发请求** |
 * | `rev` 没变 | `304 Not Modified`（ETag/If-None-Match） | 什么都不做、不落盘、无闪烁 |
 * | 返回的 `rev` ≤ 本地 | `200` 但增量空 | 同样不动 |
 *
 * ## 绝不阻塞界面
 * 调用方只在进入地图页时 fire-and-forget；失败只记 `lastError` + 退避，
 * 界面上最多一行小字，地图渲染不受影响（断网时照常用缓存）。
 */
class MapDatasetClient(ctx: Context) {

    private val app = ctx.applicationContext
    private val store = MapDatasetStore(app)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 面板渲染用：当前生效的数据集（缓存） */
    fun current(): MapDataset.Dataset? = store.load()

    fun lastCheckAt(): Long = store.lastCheckAt()

    fun lastError(): String = store.lastError()

    /**
     * 进地图页时调用：`> 5 分钟`或 `force` 才发请求。**fire-and-forget**。
     * @param onChanged 有新数据时回调（在 IO 线程；UI 层用来触发重组）
     */
    fun syncAsync(
        mapVersion: String,
        force: Boolean = false,
        onChanged: (MapDataset.Dataset) -> Unit = {},
    ) {
        if (mapVersion.isBlank()) return                       // 地图包还没加载完
        if (!force && !MapDataset.shouldPull(System.currentTimeMillis(), store.lastCheckAt())) return
        scope.launch {
            runCatching { sync(mapVersion, force) }
                .onSuccess { ds -> if (ds != null) onChanged(ds) }
        }
    }

    /**
     * 同步一次。
     * @return 有变化时返回合并后的数据集；304 / 无变化 / 失败返回 null
     */
    suspend fun sync(mapVersion: String, force: Boolean = false): MapDataset.Dataset? =
        withContext(Dispatchers.IO) {
            val base = store.load()
            if (!force && !MapDataset.shouldPull(System.currentTimeMillis(), store.lastCheckAt())) {
                return@withContext null
            }
            val since = base?.rev ?: 0L
            // 退避只用于**失败之后**，成功路径不受影响
            val backoffUntil = store.lastCheckAt() + MapContribute.backoffMillis(store.failures())
            if (!force && store.failures() > 0 && System.currentTimeMillis() < backoffUntil) {
                return@withContext null
            }

            store.noteCheck()          // 先记时间：失败也不会变成死循环重试
            var lastErr = "连不上补丁服务器（可能没网）"

            for (baseUrl in ENDPOINTS) {
                val url = buildUrl(baseUrl, mapVersion, since)
                val conn = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull()
                    ?: continue
                try {
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 15_000
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("User-Agent", UA)
                    // 省流量：rev 没变就该是 304，连 body 都不用读
                    if (base != null) conn.setRequestProperty("If-None-Match", "\"${mapVersion}-${base.rev}\"")

                    when (val code = conn.responseCode) {
                        304 -> {
                            store.noteSuccess()
                            store.clearError()
                            DebugLog.log("MAP", "dataset 304（rev=${base?.rev ?: 0} 未变）")
                            return@withContext null
                        }
                        in 200..299 -> {
                            val text = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                            val incoming = MapDataset.parse(text)
                            if (incoming == null) {
                                lastErr = "服务器返回的数据看不懂（缺 rev 或格式变了）"
                                continue
                            }
                            // rev 不比本地新 → 什么都不做（避免无意义写盘与重组）
                            if (base != null && incoming.rev <= base.rev) {
                                store.noteSuccess()
                                store.clearError()
                                return@withContext null
                            }
                            val merged = MapDataset.merge(base, incoming)
                            if (!store.save(merged)) {
                                store.noteFailure()
                                store.noteError("本地缓存写不进去（存储空间？）")
                                return@withContext null
                            }
                            store.noteSuccess()
                            store.clearError()
                            DebugLog.log(
                                "MAP",
                                "dataset 更新 rev=${merged.rev}（增量=${since > 0}，面数=${merged.features.size}）",
                            )
                            return@withContext merged
                        }
                        in 500..599 -> lastErr = "补丁服务器出错了（HTTP $code）"
                        else -> lastErr = "补丁服务器返回 HTTP $code"
                    }
                } catch (e: Exception) {
                    lastErr = MapUploader.describe(e)
                } finally {
                    runCatching { conn.disconnect() }
                }
            }
            store.noteFailure()
            store.noteError(lastErr)
            DebugLog.error("MAP", "dataset 拉取失败：$lastErr")
            null
        }

    private fun buildUrl(baseUrl: String, mapVersion: String, since: Long): String {
        val sb = StringBuilder(baseUrl)
        sb.append("?mapVersion=").append(java.net.URLEncoder.encode(mapVersion, "UTF-8"))
        if (since > 0) sb.append("&since=").append(since)
        return sb.toString()
    }

    companion object {
        /** 与上传同一套地址（18082 主用、18081 回落） */
        val ENDPOINTS = listOf(
            "http://47.95.12.198:18082/map/api/dataset",
            "http://47.95.12.198:18081/map/api/dataset",
        )

        /**
         * ⚠️ **这一行不能删。**
         *
         * nginx 在 18082 上有 UA 过滤器，`curl/` `wget/` `python-` `java/` 等一律 403。
         * `HttpURLConnection` 的**默认 UA 是 `Java/1.x`**，正好命中黑名单 ——
         * 删了这行，拉取会以「403」失败（看起来像权限问题，其实是 UA）。
         */
        private const val UA = "SYAU-Enroute (personal student test build; map dataset)"

        fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    }
}
