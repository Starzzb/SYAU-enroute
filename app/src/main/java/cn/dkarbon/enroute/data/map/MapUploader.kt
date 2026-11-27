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
 * ## 只上传「地块 UID + 字段 + 值 + 必要的贡献者信息」
 * 请求体由 [MapContribute.body] 组装 —— 那是**唯一**决定"传什么"的地方，单测逐键断言。
 * 贡献者信息：本地随机 UUID（`did`）+ 机型/系统/应用级标识（票 22）+ **学号**（`sid`）。
 * `sid` 只从内存态学籍取、**不落本地盘**；服务端收到后只落 `sid_enc`（AES-256-GCM），
 * 明文不落盘、不进日志。不发定位、不发姓名本身。
 *
 * ## 为什么用明文 http
 * 服务端 TLS 在手机链路上会被中间设备 RST（`docs/校园地图-补丁服务部署.md` §1），
 * 所以补丁服务是明文 `18082`（回落 `18081`）。上传内容只有「公共设施属性 + 贡献者信息」，
 * 服务端对 `sid` 只算派生值、不存明文；篡改的后果是「官方基线上某条属性不对」，
 * 且要过人工审核（`rev=1` 才进 snapshot）。这个取舍写在文档里，不在代码里偷偷决定。
 *
 * ## 一次同意，之后直接上云（2026-11-16）
 * [upload] 里只剩「开关开着 + 有内容」这一道内置检查；
 * 不再有每次导出前的二次确认/预览。开关开启时已经看过同意说明。
 * 「导出标注」用 [uploadFromEdits]：整理成补丁 → 入队 → 立刻上传。
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

    /** UI 侧开关（**关闭即刻生效**：之后一个字节都不会再发） */
    fun setEnabled(on: Boolean) = store.setEnabled(on)

    fun clearQueue() = store.clearQueue()

    fun anonymousKey(): String = store.anonymousKey()

    /** 安装级 UUID（「重置身份」会一并更换）；界面预览要用 */
    fun installId(): String = store.installId()

    /** 上传会随附的设备/学号信息（预览/确认弹窗要如实列出，不许藏） */
    fun clientInfo(sid: String = "", hasName: Boolean = false): MapContribute.ClientInfo =
        MapDeviceIdentity.collect(
            ctx = app,
            did = store.anonymousKey(),
            installId = store.installId(),
            appVersion = BuildConfig.VERSION_NAME,
            sid = sid,
            hasName = hasName,
        )

    fun resetAnonymousKey(): String = store.resetAnonymousKey()

    fun nextTryAt(): Long = store.nextTryAt()

    fun lastMessage(): String = store.lastMessage()

    /** 把「用户当前的标注」整理成待传补丁：**入队但不上传**。 */
    fun queueFromEdits(edits: MapEdits, mapVersion: String): MapContribute.Sanitized {
        val raw = MapContribute.patchesFrom(edits)
        val clean = MapContribute.sanitize(raw, mapVersion)
        store.enqueue(clean.ok)
        return clean
    }

    /**
     * 「导出标注」的上传通道（2026-11-16）：把当前标注整理成补丁并入队，然后**立刻**上传（force）。
     * 开关关着时一个字节都不发；本地导出照旧由调用方自己做。
     */
    suspend fun uploadFromEdits(
        edits: MapEdits,
        mapVersion: String,
        sid: String = "",
        hasName: Boolean = false,
    ): Outcome = withContext(Dispatchers.IO) {
        if (!store.isEnabled()) {
            return@withContext Outcome.Failed("还没开启「参与共建」", retryable = false)
        }
        val clean = queueFromEdits(edits, mapVersion)
        if (clean.ok.isEmpty()) {
            return@withContext if (clean.dropped.isNotEmpty())
                Outcome.Failed("没有可上传的内容：${clean.dropped.first().second}", retryable = false)
            else Outcome.NothingToDo
        }
        upload(mapVersion, force = true, sid = sid, hasName = hasName)
    }

    /** 上传队列。`force=true` 时忽略退避时间（用户手动点「立刻重试」）。`sid` 只随本次请求，不落盘 */
    suspend fun upload(
        mapVersion: String,
        force: Boolean = false,
        sid: String = "",
        hasName: Boolean = false,
    ): Outcome = withContext(Dispatchers.IO) {
        val patches = store.pending()
        // 一次同意后的内置检查：开关关着 → 一个字节都不发；没内容 → 无事可做。
        if (!store.isEnabled()) {
            return@withContext Outcome.Failed("还没开启「参与共建」", retryable = false)
        }
        if (patches.isEmpty()) return@withContext Outcome.NothingToDo
        val now = System.currentTimeMillis()
        if (!force && !MapContribute.shouldTryNow(now, store.nextTryAt())) {
            val mins = ((store.nextTryAt() - now) / 60_000L).coerceAtLeast(1)
            return@withContext Outcome.Failed("上次失败了，约 $mins 分钟后自动重试")
        }

        val key = store.anonymousKey()
        val clientInfo = MapDeviceIdentity.collect(
            ctx = app,
            did = key,
            installId = store.installId(),
            appVersion = BuildConfig.VERSION_NAME,
            sid = sid,
            hasName = hasName,
        )
        val sent = ArrayList<MapContribute.Patch>()
        var lastOk: MapContribute.MapPatchResponse? = null
        for (batch in MapContribute.chunk(patches)) {
            val body = MapContribute.body(
                mapVersion = mapVersion,
                client = clientInfo,
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
    fun uploadAsync(mapVersion: String, force: Boolean = false, sid: String = "", hasName: Boolean = false) {
        if (!store.isEnabled()) return
        if (!force && !MapContribute.shouldTryNow(System.currentTimeMillis(), store.nextTryAt())) return
        scope.launch { runCatching { upload(mapVersion, force, sid, hasName) } }
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
         * 服务端 `X-Api-Key`，由 `build.gradle.kts` 从 `local.properties` 注入
         * （`CMAP_API_KEY`）—— 公开仓库里不出现明文键，换键不用改代码。
         *
         * ⚠️ **它不是安全边界**：打进 APK 的字符串必然可被提取。真正拦人的是三件事：
         * ① 服务端限流 400 条/小时（靠稳定的 `did` → `ch` 才有效）
         * ② 众包数据要人工过一遍才合并进资产（**不实时下行**）
         * ③ 贡献者信息里学号只算派生值（sid_enc / ch），不落明文；不含姓名/定位/硬件序列号
         * 留空 = 不带该头；服务端若配了键会返回 403，界面会明说原因，不静默失败。
         */
        val API_KEY: String = cn.dkarbon.enroute.BuildConfig.CMAP_API_KEY

        /**
         * ⚠️ **这一行不能删。**
         *
         * nginx 在 18082 上有 UA 过滤器，`curl/` `wget/` `python-` `java/` 等一律 403。
         * `HttpURLConnection` 的**默认 UA 是 `Java/1.x`**，正好命中黑名单 ——
         * 删了这行，上传会以「403」而不是网络错误失败，非常难查（已被踩过一次）。
         *
         * **这条规则的三份 UA 各自独立**：上传（这里）、拉地图包（MapDatasetClient）、
         * 遥测（TelemetryUploader）。规则说明只写在这一处，另两处按需指向这里。
         */
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
