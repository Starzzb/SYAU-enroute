package cn.dkarbon.enroute.data.map

import android.content.Context
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI

/**
 * 地图共建的**上传通道**（票 13 阶段 B）。
 *
 * ## 只上传「地块 UID + 字段 + 值」
 * 请求体由 [MapContribute.body] 组装 —— 那是**唯一**决定"传什么"的地方，单测逐键断言：
 * 不含 `sid`、不含 `model`、不含 `userKey`、不含任何位置/硬件标识。
 * 唯一稳定标识是一个**本地随机 UUID**（可重置），冒充服务端的 `did` 只为让限流与归并有效。
 *
 * ## 为什么用明文 http
 * 服务端 TLS 在手机链路上会被中间设备 RST（`docs/校园地图-补丁服务部署.md` §1），
 * 所以补丁服务是明文 `18082`（回落 `18081`）。上传内容只有「公共设施属性 + 匿名随机串」，
 * 被看到也不泄露任何个人信息；篡改的后果是「官方基线上某条属性不对」，且要过人工审核
 * （`rev=1` 才进 snapshot）。这个取舍写在文档里，不在代码里偷偷决定。
 *
 * ## 不阻塞界面
 * [uploadAsync] 是 fire-and-forget：失败只落到队列 + 退避时间，UI 只读状态。
 */
class MapUploader(ctx: Context) {

    private val app = ctx.applicationContext
    private val store = MapContributeStore(app)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    sealed interface Outcome {
        data class Ok(val resp: MapContribute.MapPatchResponse) : Outcome
        /** 失败：`retryable=false` 表示「别重试了」（比如服务端不认这批数据） */
        data class Failed(val message: String, val retryable: Boolean = true) : Outcome
        data object NothingToDo : Outcome
    }

    /** 当前该上传的东西：队列里的（用户主动上传时才会进来） */
    fun pending(): List<MapContribute.Patch> = store.pending()

    fun pendingCount(): Int = store.pendingCount()

    fun isEnabled(): Boolean = store.isEnabled()

    fun anonymousKey(): String = store.anonymousKey()

    fun resetAnonymousKey(): String = store.resetAnonymousKey()

    fun nextTryAt(): Long = store.nextTryAt()

    fun lastMessage(): String = store.lastMessage()

    /**
     * 把「用户当前的标注」整理成待传补丁：**入队但不上传**。
     * 真正发送要用户点「上传」（票 13：不许偷偷传）。
     */
    fun queueFromEdits(edits: MapEdits, mapVersion: String): MapContribute.Sanitized {
        val raw = MapContribute.patchesFrom(edits)
        val clean = MapContribute.sanitize(raw, mapVersion)
        store.enqueue(clean.ok)
        return clean
    }

    /** 上传队列。`force=true` 时忽略退避时间（用户手动点「立刻重试」） */
    suspend fun upload(force: Boolean = false): Outcome = withContext(Dispatchers.IO) {
        if (!store.isEnabled()) return@withContext Outcome.Failed("还没开启「参与共建」", retryable = false)
        val patches = store.pending()
        if (patches.isEmpty()) return@withContext Outcome.NothingToDo
        val now = System.currentTimeMillis()
        if (!force && !MapContribute.shouldTryNow(now, store.nextTryAt())) {
            val mins = ((store.nextTryAt() - now) / 60_000L).coerceAtLeast(1)
            return@withContext Outcome.Failed("上次失败了，约 $mins 分钟后自动重试（也可以手动点重试）")
        }

        val key = store.anonymousKey()
        val sent = ArrayList<MapContribute.Patch>()
        var lastOk: MapContribute.MapPatchResponse? = null
        for (batch in MapContribute.chunk(patches)) {
            val body = MapContribute.body(
                mapVersion = MAP_VERSION,
                anonymousKey = key,
                appVersion = BuildConfig.VERSION_NAME,
                patches = batch,
            )
            if (body.toByteArray().size > MapContribute.MAX_BODY_BYTES) {
                store.setLastMessage("一批数据超过 256KB，已跳过（服务端上限）")
                continue
            }
            when (val r = post(body)) {
                is Outcome.Ok -> {
                    sent += batch
                    lastOk = r.resp
                }
                is Outcome.Failed -> {
                    // 失败的这一批留在队列里，成功的部分先移除（不重复上传，也不会丢）
                    if (sent.isNotEmpty()) {
                        store.savePending(patches.filterNot { sent.contains(it) })
                    }
                    val next = store.noteFailure()
                    store.setLastMessage(r.message)
                    DebugLog.error("MAP", "补丁上传失败：${r.message}（下次 ${(next - System.currentTimeMillis()) / 60000} 分钟后）")
                    return@withContext r
                }
                Outcome.NothingToDo -> Unit
            }
        }
        store.savePending(patches.filterNot { sent.contains(it) })
        store.noteSuccess()
        val msg = lastOk?.summary() ?: "没有可上传的内容"
        store.setLastMessage(msg)
        Outcome.Ok(lastOk ?: MapContribute.MapPatchResponse(0, 0, emptyList(), 0))
    }

    /** 后台跑一次（设置页/地图页进入时用），失败不打扰 */
    fun uploadAsync(force: Boolean = false) {
        if (!store.isEnabled()) return
        if (!force && !MapContribute.shouldTryNow(System.currentTimeMillis(), store.nextTryAt())) return
        scope.launch { runCatching { upload(force) } }
    }

    // ── HTTP ─────────────────────────────────────────────

    private fun post(body: String): Outcome {
        var lastErr = "连不上补丁服务器（可能没网）"
        for (base in ENDPOINTS) {
            val conn = runCatching { URL(base).openConnection() as HttpURLConnection }.getOrNull()
                ?: continue
            try {
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("User-Agent", UA)
                if (API_KEY.isNotBlank()) conn.setRequestProperty("X-Api-Key", API_KEY)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    .orEmpty()

                return when {
                    code in 200..299 -> {
                        val parsed = MapContribute.parseResponse(text)
                        if (parsed == null) {
                            Outcome.Failed("服务器回了个看不懂的响应（HTTP $code），已记下，稍后重试")
                        } else {
                            Outcome.Ok(parsed)
                        }
                    }
                    code == 403 -> Outcome.Failed(
                        "服务器拒绝了请求（403）：需要 X-Api-Key。" +
                            "如果这是正式包，说明构建时没配 CMAP_API_KEY —— 找维护者补。",
                        retryable = false,
                    )
                    code == 413 -> Outcome.Failed("一批数据太大（413），已缩批重试", retryable = true)
                    code == 429 -> Outcome.Failed("上传太频繁，被限流了（429），一会儿自动重试")
                    code in 500..599 -> Outcome.Failed("补丁服务器出错了（HTTP $code），稍后自动重试")
                    else -> Outcome.Failed("补丁服务器返回 HTTP $code")
                }
            } catch (e: Exception) {
                lastErr = describe(e)
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return Outcome.Failed(lastErr)
    }

    companion object {
        /**
         * 补丁服务地址：18082 主用、18081 回落（与地图包一致，见部署文档 §1）。
         * 明文 http 的原因见类注释。
         */
        val ENDPOINTS = listOf(
            "http://47.95.12.198:18082/map/api/patches",
            "http://47.95.12.198:18081/map/api/patches",
        )

        /**
         * 当前地图包版本（与服务端 `mapVersion` 正则一致）。
         * 由地图包 manifest 决定；这里是**兜底值**，调用方应传真实版本
         * （`MapUploader.queueFromEdits(edits, mapVersion)` 已经这么做，上传时用同一版本）。
         */
        var MAP_VERSION: String = "2026.11.14-1"

        /**
         * 服务端 `X-Api-Key`（`CMAP_API_KEY`）。
         *
         * ⚠️ 这个键只是**防刷**，不是秘密凭据（写在 APK 里必然可被提取）。
         * 正式包应在 `build.gradle.kts` 里 `buildConfigField` 注入，别提交到仓库；
         * 这里留空 = 不带该头，服务端若配了键会返回 403 —— 界面上会明说原因，不会静默失败。
         */
        var API_KEY: String = ""

        private const val UA = "SYAU-Enroute (personal student test build; map contribute)"

        fun describe(e: Throwable?): String {
            val msg = e?.message.orEmpty()
            return when {
                e == null -> "连不上补丁服务器（可能没网）"
                e is java.net.UnknownHostException -> "连不上补丁服务器：域名解析失败（检查网络）"
                e is java.net.SocketTimeoutException -> "补丁服务器响应超时"
                e is java.net.ConnectException -> "补丁服务器拒绝连接（端口可能变了）"
                msg.contains("cleartext", true) -> "系统拦截了明文 http（网络策略没放行）"
                msg.isNotBlank() -> "网络异常：$msg"
                else -> "网络异常：${e.javaClass.simpleName}"
            }
        }

        fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    }
}
