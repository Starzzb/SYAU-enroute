package cn.dkarbon.enroute.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import cn.dkarbon.enroute.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

/**
 * 应用内更新：**查 latest.json → 下载 → 校验 sha256 → 交系统安装器**（票 15）。
 *
 * ## 三条红线（写进代码，不只写文档）
 * 1. **不静默安装**：下完只到「已就绪」，装不装由用户点；权限不够要明说去哪开
 * 2. **APK 绝不从自己的 ECS 下**（出口 148 KB/s，4.75MB 要 30 秒还挤占地图补丁服务）
 *    → GitHub Releases 直链；主机白名单见 [UpdatePolicy.ALLOWED_APK_HOSTS]
 * 3. **校验不过就删**：sha256 不符 / 体积为 0 / 下了一半 → 删文件 + 说人话，绝不「凑合装」
 *
 * 失败字符串是**直接给用户看的**，所以是「连不上服务器（可能没网）」而不是异常类名。
 */
class UpdateCenter(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val dir = File(ctx.applicationContext.filesDir, "updates")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    sealed interface CheckResult {
        /** 已是最新（含「服务器上的版本比本机旧」这一种） */
        data class Latest(val localName: String, val note: String = "") : CheckResult
        data class Available(val info: UpdateInfo, val mandatory: Boolean) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    sealed interface DownloadResult {
        data class Ready(val apk: File) : DownloadResult
        data object Cancelled : DownloadResult
        data class Failed(val message: String) : DownloadResult
    }

    // ── 查询 ─────────────────────────────────────────────

    suspend fun check(silent: Boolean = false): CheckResult = withContext(Dispatchers.IO) {
        val localCode = BuildConfig.VERSION_CODE.toLong()
        val localName = BuildConfig.VERSION_NAME
        var lastErr = "连不上服务器（可能没网，或服务器在维护）"

        for (raw in MANIFEST_URLS) {
            // 每条都带按小时变化的 ?t=，绕开 jsDelivr 的 12 小时边缘缓存
            val url = cacheBusted(raw, hourStamp())
            val res = runCatching { httpGetText(url, timeoutMs = MANIFEST_TIMEOUT_MS) }
            val text = res.getOrNull()
            if (text == null) {
                val e = res.exceptionOrNull()
                val msg = e?.message.orEmpty()
                // 仓库改名 / 换默认分支这类错误**不会自己好**，但用户永远看不到。
                // 所以在日志里留一条 ERROR：raw 地址 404 基本等于「仓库名或分支写错了」。
                if (raw.contains("raw.githubusercontent.com") && msg.contains("404")) {
                    DebugLog.error(
                        "UPDATE",
                        "latest.json 在 GitHub raw 上 404：$raw —— 仓库名或默认分支可能写错了" +
                            "（默认分支是 master，不是 main）",
                    )
                }
                lastErr = describeNetError(e)
                continue
            }
            return@withContext when (val parsed = UpdateParse.latest(text)) {
                is LatestResult.Fail -> {
                    _available.value = null
                    CheckResult.Failed(parsed.reason)
                }
                is LatestResult.Ok -> {
                    val info = parsed.info
                    when (UpdateVersions.relation(info.versionCode, localCode)) {
                        UpdateRelation.NEWER -> {
                            prefs.edit()
                                .putLong(KEY_AVAILABLE_CODE, info.versionCode)
                                .putString(KEY_AVAILABLE_NAME, info.versionName)
                                .putString(KEY_AVAILABLE_INFO, serializeInfo(info))
                                .apply()
                            _available.value = info
                            CheckResult.Available(info, UpdateVersions.isMandatory(info, localCode))
                        }
                        UpdateRelation.SAME -> {
                            clearAvailable()
                            CheckResult.Latest(localName)
                        }
                        UpdateRelation.OLDER -> {
                            // 服务器版本比本机旧（回滚/灰度）：**不提示更新**，但如实说明
                            clearAvailable()
                            CheckResult.Latest(localName, note = "服务器上最新是 ${info.versionName}，比本机旧")
                        }
                    }
                }
            }
        }
        if (silent) DebugLog.log("UPDATE", "静默检查失败：$lastErr")
        _available.value = null
        CheckResult.Failed(lastErr)
    }

    /** 上次检查记下的「有新版」（设置页红点用它，不必联网）；已装上就自动失效 */
    fun pendingUpdateName(): String? {
        val code = prefs.getLong(KEY_AVAILABLE_CODE, 0L)
        if (code <= BuildConfig.VERSION_CODE.toLong()) return null
        return prefs.getString(KEY_AVAILABLE_NAME, null)
    }

    /** 「不再提醒本次更新」压制的版本号（只存一个 Long，见文档 §1.2） */
    fun dismissedCode(): Long = prefs.getLong(KEY_DISMISSED_CODE, 0L)

    /** 记下「这个版本不再提醒」；下一版（versionCode 变了）照常弹 */
    fun dismissVersion(code: Long) {
        prefs.edit().putLong(KEY_DISMISSED_CODE, code).apply()
    }

    private fun clearAvailable() {
        prefs.edit().remove(KEY_AVAILABLE_CODE).remove(KEY_AVAILABLE_NAME).remove(KEY_AVAILABLE_INFO).apply()
        _available.value = null
    }

    // ── 下载 ─────────────────────────────────────────────

    fun fileOf(info: UpdateInfo): File =
        File(dir, "enroute-${info.versionName.replace(Regex("""[^\w.\-+]"""), "_")}.apk")

    /** 已下好的包（存在且非空）；「上次下到一半」时不复用（没有断点续传） */
    fun downloadedApk(info: UpdateInfo): File? = fileOf(info).takeIf { it.exists() && it.length() > 0 }

    /**
     * 下载 + 校验。**边下边写 `.part`，校验通过才改名** ——
     * 中途被杀进程也绝不会在目标路径留下半个包。
     *
     * @param onProgress (已下载字节, 总字节；总字节未知时传 -1)
     *   ——回调在 **IO 线程**被调用。UI 层直接写 Compose state 是安全的（快照写入线程安全，
     *   重组在主线程读），但**别在回调里做重活**（每 64KB 一次，会很频繁）。
     */
    /**
     * 下载 APK：**直连优先，失败逐个走镜像**（2026-11-16）。
     *
     * 为什么需要：GitHub Releases 在国内经常下不动（连接被掐），但同一份资产经
     * `gh-proxy.com` 能下（实测实下 512KB 拿到 PK 头、HEAD 1372ms）。
     * 镜像地址由 [UpdatePolicy.mirrorsOf] 从**已通过前缀钉死的**入口地址拼出，
     * 所以安全性由构造保证 —— 镜像只能转发这一个包，不能指向别处。
     *
     * 注意 404/403 这类**确定性失败**也照样换镜像：GitHub 对国内 IP 有时给 403 限流。
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): DownloadResult {
        val candidates = UpdatePolicy.mirrorsOf(info.url)
        var last: DownloadResult.Failed? = null
        for ((i, url) in candidates.withIndex()) {
            val r = downloadOnce(url, info, onProgress)
            if (r is DownloadResult.Ready) return r
            if (r is DownloadResult.Cancelled) return r
            last = r as? DownloadResult.Failed
            DebugLog.log("UPDATE", "下载候选 ${i + 1}/${candidates.size} 失败（${hostOf(url)}）：${last?.message}")
        }
        return last ?: DownloadResult.Failed("没有可用的下载地址")
    }

    private suspend fun downloadOnce(
        startUrl: String,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileOf(info)
        val part = File(dir, target.name + ".part")
        part.delete()

        var conn: HttpURLConnection? = null
        try {
            var current = startUrl
            var hops = 0
            while (true) {
                if (hops++ > 5) return@withContext DownloadResult.Failed("下载地址跳转太多次，已放弃")
                // 每一跳都重新过白名单：GitHub Releases 会 302 到 objects.githubusercontent.com
                if (!UpdatePolicy.isAllowedRedirect(current)) {
                    return@withContext DownloadResult.Failed(
                        "下载地址跳到了不允许的域名，已中断（${hostOf(current)}）",
                    )
                }
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", UA)
                    setRequestProperty("Accept", "application/vnd.android.package-archive,*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc.isNullOrBlank()) {
                        return@withContext DownloadResult.Failed("服务器跳转没给目标地址（HTTP $code）")
                    }
                    current = runCatching { URI(current).resolve(loc).toString() }.getOrNull()
                        ?: return@withContext DownloadResult.Failed("服务器给的跳转地址看不懂，已中断")
                    continue
                }
                if (code !in 200..299) {
                    // 一次 2xx 都没拿到 → 直接失败（带人话）
                    return@withContext DownloadResult.Failed(
                        when (code) {
                            404 -> "下载地址失效了（404）：${info.versionName} 的包可能还没传到 Releases"
                            403 -> "服务器拒绝下载（403）：检查 GitHub Releases 是否公开"
                            else -> "服务器返回 HTTP $code，下载失败"
                        },
                    )
                }

                val total = conn.contentLengthLong
                var done = 0L
                FileOutputStream(part).use { out ->
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()   // 取消 → 抛 CancellationException
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress(done, total)
                        }
                    }
                }
                if (total > 0 && done != total) {
                    part.delete()
                    return@withContext DownloadResult.Failed(
                        "下载不完整（拿到 $done / $total 字节），已删除，请重试",
                    )
                }
                break
            }

            val actual = sha256Of(part)
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                part.delete()
                DebugLog.error("UPDATE", "sha256 不匹配：期望 ${info.sha256} 实际 $actual")
                return@withContext DownloadResult.Failed(
                    "安装包校验失败（sha256 不一致），已删除。可能没下完或被改过，请重试",
                )
            }
            if (target.exists() && !target.delete()) {
                part.delete()
                return@withContext DownloadResult.Failed("旧包删不掉（存储权限异常），请清理后重试")
            }
            if (!part.renameTo(target)) {
                part.delete()
                return@withContext DownloadResult.Failed("文件保存失败（存储可能满了），请清理空间后重试")
            }
            onProgress(target.length(), target.length())
            DownloadResult.Ready(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            DownloadResult.Failed(describeNetError(e))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // ── 安装 ─────────────────────────────────────────────

    /** 系统是否允许本 App 装未知来源应用（Android 8+ 一直是按 App 授权的） */
    fun canInstallPackages(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** 去系统设置开「安装未知应用」—— 权限被拒时给的可执行出路 */
    fun unknownSourcesIntent(apk: File): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, apkUri(apk))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * 交给系统安装器。**调用方必须先确认已下载且校验通过**。
     * `FLAG_GRANT_READ_URI_PERMISSION` 不能少：否则安装器读不到我们私有目录里的包。
     */
    fun installIntent(apk: File): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri(apk), "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun apkUri(apk: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)

    // ── 启动静默检查（每天最多一次）────────────────────────

    /**
     * 启动时调用：满足「距上次 ≥24h」才发一次请求，**无论成败都立刻记时间** ——
     * 不然没网时每次冷启动都重试，白烧流量和电。
     */
    fun maybeAutoCheckAsync() {
        // 先把「上次已发现的新版」发出来：今天若还没到 24h 不重新查，弹窗也要能弹
        //（文档 §1.2「我知道了 → 下次启动继续弹」依赖这一点）。
        restorePendingFromPrefs()
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_AUTO, 0L).takeIf { it > 0L }
        if (!UpdateSchedule.shouldAutoCheck(now, last)) return
        prefs.edit().putLong(KEY_LAST_AUTO, now).apply()
        scope.launch { runCatching { check(silent = true) } }
    }

    /** 从上次落盘的完整 [UpdateInfo] 恢复进程级 `available`（只认比本机新的） */
    private fun restorePendingFromPrefs() {
        if (_available.value != null) return
        val saved = prefs.getString(KEY_AVAILABLE_INFO, null) ?: return
        val info = (UpdateParse.latest(saved) as? LatestResult.Ok)?.info ?: return
        if (info.versionCode <= BuildConfig.VERSION_CODE.toLong()) return
        _available.value = info
    }

    companion object {
        /**
         * 进程级「有新版」信号（文档 §3.1）。检查在 Application 跑、展示在 UI 层，
         * 两者靠它连接。用 [StateFlow] 不用 SharedFlow：UI 可能晚于检查完成才订阅，
         * SharedFlow 会丢掉早到的事件。
         */
        private val _available = MutableStateFlow<UpdateInfo?>(null)
        val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

        /**
         * 「立即更新」的一次性下载请求：弹窗点「立即更新」→ 跳设置页，
         * [cn.dkarbon.enroute.ui.settings.UpdateSection] 读到后自动开始下载（文档 §1.4 / §3.4 方案 A）。
         * 消费后由 UpdateSection 置空，避免重进设置页时重复下载。
         */
        private val _downloadRequest = MutableStateFlow<UpdateInfo?>(null)
        val downloadRequest: StateFlow<UpdateInfo?> = _downloadRequest.asStateFlow()

        fun requestDownload(info: UpdateInfo) {
            _downloadRequest.value = info
        }

        fun consumeDownloadRequest() {
            _downloadRequest.value = null
        }

        /** [UpdateInfo] → latest.json 同构 JSON（落盘用，反序列化直接复用 [UpdateParse.latest]） */
        private fun serializeInfo(info: UpdateInfo): String =
            org.json.JSONObject()
                .put("versionCode", info.versionCode)
                .put("versionName", info.versionName)
                .put("url", info.url)
                .put("sha256", info.sha256)
                .put("size", info.size)
                .put("notes", info.notes)
                .put("minSupported", info.minSupported)
                .toString()

        /**
         * `latest.json` 的候选地址，按顺序试（第一个拿到就用）。
         *
         * ## 2026-11-16 全面改版（实测驱动）
         *
         * 真机上点「检查更新」长期卡在「正在连接服务器…」，实测三个源的真实情况：
         *
         * | 源 | 实测结果 |
         * |---|---|
         * | `raw.githubusercontent.com` | **超时**（国内不通） |
         * | `cdn.jsdelivr.net/gh/…@master/…` | **5 次全通，428–656ms** |
         * | `fastly.jsdelivr.net/…` | 同源换节点，作备份 |
         * | 自建 `47.95.12.198:18082/app/latest.json` | **000（连接被掐）** |
         * | 自建 `47.95.12.198:18081/app/latest.json` | **404** |
         *
         * 也就是说：改版前**主力国内不通、两个回落全是死的**，能连上才怪。
         * 自建机上只有 `/map/api/` 配了 nginx 路由，`/app/` 这条从来没配置过 ——
         * 下面保留它们只是**给将来真部署时留位**，并标注清楚现在不可用，
         * 免得下一个人又以为有可用回落而在真机上白等超时。
         *
         * ## 为什么 composer 清单可以放 CDN，APK 不行
         *
         * 清单只有 408 字节，被中间人改掉也没用：真正的防线是
         * [UpdatePolicy.PINNED_RELEASE_PREFIX]（APK 地址必须指向本仓库 releases）+ sha256。
         * 攻击者拿不到本仓库写权限，就提供不出**可安装**的包 —— **最坏结果只是「下载失败」**。
         * APK 本体（50MB+）不放自建机：个人机没 TLS，且 50MB 分发不是它该干的活。
         *
         * 顺序：jsDelivr 主（国内实测最快）→ fastly 备份 → GitHub raw（境外最快）→ 自建（现不可用）。
         * timeout 从 10s 收到 6s：四条最坏 24s 太久，用户等不了；实测通的那条约 500ms。
         */
        val MANIFEST_URLS = listOf(
            // ① gh-proxy：实测 832ms 且**内容是最新的**（见下方 jsDelivr 的坑）
            "https://gh-proxy.com/https://raw.githubusercontent.com/Starzzb/SYAU-enroute/master/releases/latest.json",
            // ② raw.githack：实测 1148ms，内容新
            "https://raw.githack.com/Starzzb/SYAU-enroute/master/releases/latest.json",
            // ③ GitHub raw 直连：实测 669ms，但国内时通时不通
            "https://raw.githubusercontent.com/Starzzb/SYAU-enroute/master/releases/latest.json",
            // ④⑤ jsDelivr：最快（428–656ms）但**分支缓存 12 小时且 purge 无效** —— 放后面，
            //      只在前面全挂时用；用了也可能拿到旧版本（表现为「已是最新」，不会误报更新）
            "https://cdn.jsdelivr.net/gh/Starzzb/SYAU-enroute@master/releases/latest.json",
            "https://fastly.jsdelivr.net/gh/Starzzb/SYAU-enroute@master/releases/latest.json",
        )

        /** 单条源的超时；通的那条约 500ms，6s 已足够且不让用户干等 */
        private const val MANIFEST_TIMEOUT_MS = 5_000


        private const val UA = "SYAU-Enroute (personal student test build; update check)"
        private const val KEY_LAST_AUTO = "lastAutoCheckAt"
        private const val KEY_AVAILABLE_CODE = "availableCode"
        private const val KEY_AVAILABLE_NAME = "availableName"
        private const val KEY_AVAILABLE_INFO = "availableInfo"
        private const val KEY_DISMISSED_CODE = "dismissedCode"

        /** 异常 → 人话（**直接显示给用户**） */
        fun describeNetError(e: Throwable?): String {
            val msg = e?.message.orEmpty()
            return when {
                e == null -> "连不上服务器（可能没网，或服务器在维护）"
                e is java.net.UnknownHostException -> "连不上服务器：域名解析失败（检查网络）"
                e is java.net.SocketTimeoutException -> "服务器响应超时，稍后再试"
                e is java.net.ConnectException -> "连接被拒绝：服务器可能没起，或端口变了"
                msg.contains("No space", true) -> "手机存储空间不够了"
                msg.isNotBlank() -> "网络异常：$msg"
                else -> "网络异常：${e.javaClass.simpleName}"
            }
        }

        /** 文件 sha256（小写十六进制）；流式读，不整包进内存 */
        fun sha256Of(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** 取 `latest.json`：只认 2xx + 纯文本，跳转最多 3 跳 */
        fun httpGetText(url: String, timeoutMs: Int = 10_000): String {
            var current = url
            var hops = 0
            while (hops++ < 3) {
                val conn = URL(current).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = timeoutMs
                    conn.readTimeout = timeoutMs * 2
                    conn.setRequestProperty("User-Agent", UA)
                    conn.setRequestProperty("Accept", "application/json,*/*;q=0.8")
                    // jsDelivr 对 @master 的缓存是 s-maxage=43200（12 小时），
                    // 不加这条会「发新版半天内查不到」。No-cache 让它回源，
                    // URL 上的 ?t= 再兜一层（见 cacheBusted）。
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store")
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: break
                        current = URI(current).resolve(loc).toString()
                        continue
                    }
                    if (code !in 200..299) throw java.io.IOException("HTTP $code")
                    return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                } finally {
                    runCatching { conn.disconnect() }
                }
            }
            throw java.io.IOException("跳转太多次")
        }

        /**
         * 给 URL 加一个按小时变化的查询参数，绕开 CDN 的边缘缓存。
         *
         * **为什么需要**：jsDelivr 的响应头是 `s-maxage=43200`（12 小时）+ `max-age=604800`（7 天）。
         * 我在 2026-11-16 实测：改完 `latest.json` 立刻请求，返回的还是 12 小时前的旧内容。
         * 有这条参数后每次都是新的 key，CDN 只能回源。
         *
         * ⚠️ 只对**清单**用，不要加到 APK 下载地址上 —— 那会让 sha256 校验的缓存优势归零，
         * 而且 GitHub Release 的资产地址本来就不走这层缓存。
         *
         * @param hourStamp `yyyyMMddHH` 形态的时间戳（调用方传入，便于单测）
         */
        fun cacheBusted(url: String, hourStamp: String): String {
            if (hourStamp.isBlank()) return url
            val sep = if (url.contains('?')) '&' else '?'
            return "$url${sep}t=$hourStamp"
        }

        /** 当前小时戳 `yyyyMMddHH`（本地时区，够用 —— 只用来区分缓存 key） */
        fun hourStamp(now: Long = System.currentTimeMillis()): String =
            java.text.SimpleDateFormat("yyyyMMddHH", java.util.Locale.US).format(java.util.Date(now))

        private fun hostOf(url: String): String =
            runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    }
}
