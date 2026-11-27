package cn.dkarbon.enroute.data

import java.net.URI

/**
 * 应用内更新的**纯逻辑层**（票 15）。
 *
 * 这里没有任何网络/文件/Android API —— 全部是「给定输入决定输出」的纯函数，
 * 所以能在 JVM 单测里把几条红线钉死：
 *   1. `latest.json` 解析不出来 → 说人话，不猜、不降级成「有更新」
 *   2. 下载地址**只认白名单主机**（防劫持，票 15 红线）
 *   3. 版本比较必须认「服务器版本比本机低」这种情况（不能提示更新）
 *   4. 自动检查每天最多一次（时间窗可测）
 *   5. sha256 不匹配一律判失败（「凑合装」是绝对禁止）
 */

/** `latest.json` 的内容（服务器侧契约见 `docs/版本控制与发版流程.md` §4） */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    /** APK 直链 —— 必须是 [UpdatePolicy.ALLOWED_APK_HOSTS] 里的主机，且 https */
    val url: String,
    /** 小写十六进制 sha256（64 字符） */
    val sha256: String,
    /** 字节数；0 表示服务器没给（不拿它做判断，只用于显示） */
    val size: Long = 0,
    val notes: String = "",
    /** 低于这个 versionCode 的老版本「强制提示」（0 = 不强制） */
    val minSupported: Long = 0,
) {
    /** 人类可读体积：`23.1 MB`；没给就返回空串（不编数字） */
    val sizeText: String
        get() = when {
            size <= 0L -> ""
            size >= 1024L * 1024L -> "%.1f MB".format(size / 1024.0 / 1024.0)
            else -> "%d KB".format(size / 1024L)
        }
}

/** 解析结果：成功带数据，失败带**人话原因**（失败原因要能直接显示给用户） */
sealed interface LatestResult {
    data class Ok(val info: UpdateInfo) : LatestResult
    data class Fail(val reason: String) : LatestResult
}

/** 版本关系（三态，不是布尔 —— 「服务器更低」和「一样」要分开表达） */
enum class UpdateRelation { NEWER, SAME, OLDER }

object UpdatePolicy {

    /**
     * **唯一允许的 APK 来源主机**。
     *
     * 为什么写死在代码里而不是信 `latest.json`：`latest.json` 本身就是从网上取的，
     * 它要是被中间人改过、把 url 指到别的域名，App 就会去装一个陌生包。
     * 白名单 + 只许 https = 即使 latest.json 被篡改，最坏也只是「下载失败」。
     *
     * APK 不放自己的 ECS（出口 148 KB/s，4.75MB 要 30 秒还挤占补丁服务）——
     * 走 GitHub Releases 直链，所以白名单是这几家：
     *   · `github.com`                          ← Releases 的 `releases/download/...` 入口
     *   · `objects.githubusercontent.com`       ← 302 之后的真实对象地址（主要落点）
     *   · `release-assets.githubusercontent.com`← 新版 Releases 的资源域
     *   · `github-releases.githubusercontent.com`← 老版资源域，保留兼容
     */
    val ALLOWED_APK_HOSTS = listOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
    )

    /**
     * **下载镜像**（2026-11-16 加，实测驱动）。
     *
     * GitHub Releases 在国内经常下不动，实测这两个代理能通：
     * `gh-proxy.com`（HEAD 1372ms、实下 512KB 拿到 PK 头）、`ghproxy.net`（HEAD 5876ms）。
     * 用法是把原始地址**整条拼在代理域名后面**：
     * `https://gh-proxy.com/https://github.com/...apk`
     *
     * ⚠️ 镜像只做「**下载**」，不做「清单」的主源 —— 因为它是个第三方转发，
     * 清单用镜像等于把「谁决定发什么版本」交出去一半。清单走直连 GitHub 的源。
     *
     * ⚠️ 镜像**不参与** [isAllowedApkUrl] 的前缀钉死：那条只校验 `latest.json` 里的入口地址。
     * 镜像地址由 [mirrorsOf] 从**已通过校验的**入口地址拼出来，所以安全性由构造保证。
     */
    val MIRROR_PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
    )

    /** 镜像主机（只用于「下载过程中的跳转」白名单，不放行 `latest.json` 入口） */
    val MIRROR_HOSTS = listOf("gh-proxy.com", "ghproxy.net")

    /**
     * **自建分发口**（2026-11-27 开）。
     *
     * 这台机器上只放两样东西，都在 `/app/` 一个目录下（nginx 路由见
     * `server/telemetry/deploy.sh`，文件由发版脚本同步）：
     *   · `/app/latest.json`        —— 与仓库里 `releases/latest.json` **同一个文件**
     *   · `/app/enroute-latest.apk` —— 最新包的稳定文件名副本（每版覆盖）
     *
     * ## 为什么允许明文 http
     *
     * 这台机器没有域名证书；`https://wb.dkarbon.cn` 在手机链路上时通时不通
     * （DNS 被解析到 fake-ip 后握手被 RST），而 18082 是实测能连的那一个口。
     * 风险由三层兜住，缺一层都不行：
     *   1. **地址钉死**：只有 `http://[SELF_HOST_HOST]:[SELF_HOST_HTTP_PORT]/app/` 这一种形态通过
     *      ——协议、主机、端口、路径前缀全要匹配，改一个字就拒；
     *   2. **sha256**：包体必须与 `latest.json` 里的 `sha256` 一致，路上被改一个字节就判失败并删包；
     *   3. **签名**：真装的时候 Android 还会按签名再挡一次 —— 不是同一把钥匙签的包装不上。
     * 所以从明文口拿到的最坏结果仍然是「下载失败」，不是「装上错东西」。
     *
     * **清单（latest.json）仍以 https 源为主**：自建只是国内必通的旁路，
     * 不是「谁决定发什么版本」的唯一来源。
     */
    const val SELF_HOST_HOST = "47.95.12.198"

    /** 自建口的明文 HTTP 端口（nginx 里那个只放行少量前缀的站点） */
    const val SELF_HOST_HTTP_PORT = 18082

    const val SELF_HOST_ORIGIN = "http://" + SELF_HOST_HOST + ":" + SELF_HOST_HTTP_PORT

    const val SELF_HOST_MANIFEST_URL = SELF_HOST_ORIGIN + "/app/latest.json"

    /** 最新包的稳定地址（每版覆盖同一个文件名，地址不随版本变） */
    const val SELF_HOST_APK_URL = SELF_HOST_ORIGIN + "/app/enroute-latest.apk"

    private const val SELF_HOST_APP_PREFIX = "/app/"

    /** 自建口地址是否合法：**http** + 那个主机 + 那个端口 + `/app/` 下**确实有文件名** */
    fun isAllowedSelfHost(url: String): Boolean {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!u.scheme.equals("http", ignoreCase = true)) return false
        if (!u.host.orEmpty().equals(SELF_HOST_HOST, ignoreCase = true)) return false
        if ((if (u.port > 0) u.port else 80) != SELF_HOST_HTTP_PORT) return false
        val path = u.path.orEmpty()
        // 前缀之后必须还有东西 —— `/app/` 本身是目录，不是一个文件
        return path.startsWith(SELF_HOST_APP_PREFIX) && path.length > SELF_HOST_APP_PREFIX.length
    }

    /**
     * 下载候选地址：**直连优先**，失败再走镜像。
     *
     * @param original 已经过 [isAllowedApkUrl] 校验的入口地址
     */
    fun mirrorsOf(original: String): List<String> =
        listOf(original) + MIRROR_PREFIXES.map { it + original }

    /**
     * **APK 只许来自本仓库的 Releases**（`AboutScreen` 里的仓库地址就是这个）。
     *
     * 301 之后会跳到 `objects.githubusercontent.com/...`，那条是**下载过程中的跳转**，
     * 由 [UpdateCenter] 在每一跳单独校验主机白名单；这里的「前缀钉死」只约束
     * `latest.json` 里写的那条**入口地址**。
     *
     * ⚠️ 换仓库 / 换组织时**必须改这里**，否则 App 会拒绝更新（这是故意的：宁可拒绝，不可装错）。
     */
    const val PINNED_RELEASE_PREFIX = "https://github.com/Starzzb/SYAU-enroute/releases/download/"

    /** 下载地址是否合法：**https** + 主机在白名单里 + **路径前缀钉死在本仓库** */
    fun isAllowedApkUrl(url: String): Boolean {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!u.scheme.equals("https", ignoreCase = true)) return false
        val host = u.host?.lowercase() ?: return false
        if (host.isBlank()) return false
        // 精确等于白名单主机；子域**不放行**（`github.com.evil.com` 这种必须被拒）
        if (host !in ALLOWED_APK_HOSTS) return false
        // ★ 真正兜底的一条：必须是**本仓库的 Releases 下载前缀**。
        // 为什么光有主机白名单不够：`latest.json` 目前还有一条明文 http 的回落地址，
        // 中间人能在路上把 url 和 sha256 **一起**改掉 —— 那样主机是 github.com、sha256 也“自洽”，
        // 白名单挡不住。钉死仓库前缀之后，攻击者只能指到别人的仓库，而那个仓库他传不了包。
        if (!url.trim().startsWith(PINNED_RELEASE_PREFIX)) return false
        return u.path.orEmpty().isNotBlank()
    }

    /** sha256 字符串看起来合法吗（64 位十六进制，大小写不敏感） */
    fun looksLikeSha256(s: String): Boolean =
        s.length == 64 && s.all { it in "0123456789abcdefABCDEF" }

    /**
     * **下载过程中的跳转目标**（GitHub 302 → `objects.githubusercontent.com`）：
     * 只要求 https + 主机在白名单。前缀钉死只针对 `latest.json` 里那条入口地址，
     * 否则正常的 Releases 跳转会被自己拒掉。
     */
    fun isAllowedRedirect(url: String): Boolean {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!u.scheme.equals("https", ignoreCase = true)) return false
        val host = u.host?.lowercase() ?: return false
        // 跳转白名单 = GitHub 自己的域 + 下载镜像域（镜像是我们从已校验地址拼出来的）
        return host.isNotBlank() && (host in ALLOWED_APK_HOSTS || host in MIRROR_HOSTS)
    }
}

object UpdateParse {

    private val json = kotlinx.serialization.json.Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * 解析 `latest.json`。**任何一项不合格都整体失败**，不做「能读多少算多少」——
     * 更新包半信半疑地装上去比不装危险得多。
     *
     * 用 kotlinx.serialization 而不是 `org.json`：后者在 JVM 单测里是空实现（会抛异常），
     * 这几条红线必须能在纯 JVM 上验，所以解析器也跟着走纯 Kotlin 的。
     */
    fun latest(text: String): LatestResult {
        val root = runCatching {
            json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        }.getOrNull()
            ?: return LatestResult.Fail("服务器返回的不是合法 JSON 对象（latest.json 可能没放对）")

        fun str(key: String): String =
            (root[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()

        fun num(key: String): Long =
            (root[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.toLong() ?: 0L

        val code = num("versionCode")
        if (code <= 0L) return LatestResult.Fail("latest.json 缺 versionCode（或不是正整数）")

        val name = str("versionName")
        if (name.isBlank()) return LatestResult.Fail("latest.json 缺 versionName")

        val url = str("url")
        if (url.isBlank()) return LatestResult.Fail("latest.json 缺下载地址 url")
        if (!UpdatePolicy.isAllowedApkUrl(url)) {
            return LatestResult.Fail(
                "下载地址不在允许的范围内（只允许 GitHub Releases），已拒绝：${hostOf(url).ifBlank { url.take(40) }}",
            )
        }

        val sha = str("sha256")
        if (!UpdatePolicy.looksLikeSha256(sha)) {
            return LatestResult.Fail("latest.json 的 sha256 不是 64 位十六进制，无法校验，拒绝更新")
        }

        return LatestResult.Ok(
            UpdateInfo(
                versionCode = code,
                versionName = name,
                url = url,
                sha256 = sha.lowercase(),
                size = num("size").coerceAtLeast(0L),
                notes = str("notes"),
                minSupported = num("minSupported").coerceAtLeast(0L),
            ),
        )
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("")
}

object UpdateVersions {

    /** 远端 vs 本机：`NEWER` / `SAME` / `OLDER` */
    fun relation(remoteCode: Long, localCode: Long): UpdateRelation = when {
        remoteCode > localCode -> UpdateRelation.NEWER
        remoteCode == localCode -> UpdateRelation.SAME
        else -> UpdateRelation.OLDER
    }

    /** 是否需要提示更新（只有 NEWER 才算） */
    fun hasUpdate(info: UpdateInfo, localCode: Long): Boolean =
        relation(info.versionCode, localCode) == UpdateRelation.NEWER

    /** 是否强制提示（老到服务器不支持了）。只影响提示强弱，**绝不阻断使用**（票 15 红线） */
    fun isMandatory(info: UpdateInfo, localCode: Long): Boolean =
        hasUpdate(info, localCode) && info.minSupported > 0L && localCode < info.minSupported
}

object UpdateSchedule {

    const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * 启动时是否该静默查一次：从没查过 → 查；距上次 ≥24h → 查；否则不查。
     *
     * 时钟被往回调（`now < last`，用户手动改时间或跨时区）也**查一次**，
     * 否则「时间被调回去」会让检查永久停摆。
     */
    fun shouldAutoCheck(nowMs: Long, lastMs: Long?, windowMs: Long = DAY_MS): Boolean {
        if (lastMs == null || lastMs <= 0L) return true
        if (nowMs < lastMs) return true
        return nowMs - lastMs >= windowMs
    }
}
