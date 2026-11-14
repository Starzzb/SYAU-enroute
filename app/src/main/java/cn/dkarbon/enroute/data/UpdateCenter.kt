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

        for (url in MANIFEST_URLS) {
            val res = runCatching { httpGetText(url, timeoutMs = 10_000) }
            val text = res.getOrNull()
            if (text == null) {
                val e = res.exceptionOrNull()
                val msg = e?.message.orEmpty()
                // 仓库改名 / 换默认分支这类错误**不会自己好**，但用户永远看不到
                // （会静默回落到明文 http，正好绕过前缀钉死）。所以在日志里留一条 ERROR：
                // raw 地址 404 基本等于「仓库名或分支写错了」，见 MANIFEST_URLS 的注释。
                if (url.contains("raw.githubusercontent.com") && msg.contains("404")) {
                    DebugLog.error(
                        "UPDATE",
                        "latest.json 在 GitHub raw 上 404：$url —— 仓库名或默认分支可能写错了" +
                            "（默认分支是 master，不是 main）",
                    )
                }
                lastErr = describeNetError(e)
                continue
            }
            return@withContext when (val parsed = UpdateParse.latest(text)) {
                is LatestResult.Fail -> CheckResult.Failed(parsed.reason)
                is LatestResult.Ok -> {
                    val info = parsed.info
                    when (UpdateVersions.relation(info.versionCode, localCode)) {
                        UpdateRelation.NEWER -> {
                            prefs.edit()
                                .putLong(KEY_AVAILABLE_CODE, info.versionCode)
                                .putString(KEY_AVAILABLE_NAME, info.versionName)
                                .apply()
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
        CheckResult.Failed(lastErr)
    }

    /** 上次检查记下的「有新版」（设置页红点用它，不必联网）；已装上就自动失效 */
    fun pendingUpdateName(): String? {
        val code = prefs.getLong(KEY_AVAILABLE_CODE, 0L)
        if (code <= BuildConfig.VERSION_CODE.toLong()) return null
        return prefs.getString(KEY_AVAILABLE_NAME, null)
    }

    private fun clearAvailable() {
        prefs.edit().remove(KEY_AVAILABLE_CODE).remove(KEY_AVAILABLE_NAME).apply()
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
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = fileOf(info)
        val part = File(dir, target.name + ".part")
        part.delete()

        var conn: HttpURLConnection? = null
        try {
            var current = info.url
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
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_AUTO, 0L).takeIf { it > 0L }
        if (!UpdateSchedule.shouldAutoCheck(now, last)) return
        prefs.edit().putLong(KEY_LAST_AUTO, now).apply()
        scope.launch { runCatching { check(silent = true) } }
    }

    companion object {
        /**
         * `latest.json` 的三个候选地址，按顺序试（第一个拿到就用）。
         *
         * **为什么首选 GitHub raw**：明文 http 上的 `latest.json` 可以被中间人连同 `sha256`
         * 一起改掉 —— 那样「校验通过」就失去意义了。走 https 的 raw 文件没有这个洞；
         * 自己的机器只作回落，且 APK 入口地址还**钉死了本仓库前缀**
         * （[UpdatePolicy.PINNED_RELEASE_PREFIX]），最坏情况只是「下载失败」。
         */
        val MANIFEST_URLS = listOf(
            // ① 首选：GitHub raw（https，手机链路上 TLS 不会被 RST）
            "https://raw.githubusercontent.com/Starzzb/SYAU-enroute/master/releases/latest.json",
            // ② 回落：自己的机器（<2KB，明文，只在 raw 拉不到时用）
            "http://47.95.12.198:18082/app/latest.json",
            "http://47.95.12.198:18081/app/latest.json",
        )

        private const val UA = "SYAU-Enroute (personal student test build; update check)"
        private const val KEY_LAST_AUTO = "lastAutoCheckAt"
        private const val KEY_AVAILABLE_CODE = "availableCode"
        private const val KEY_AVAILABLE_NAME = "availableName"

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

        private fun hostOf(url: String): String =
            runCatching { URI(url).host.orEmpty() }.getOrDefault("")
    }
}
