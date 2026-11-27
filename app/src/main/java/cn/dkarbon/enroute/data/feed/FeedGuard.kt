package cn.dkarbon.enroute.data.feed

import java.net.URI

/**
 * 抓取前的统一闸门（2026-11-27 新增）。
 *
 * 三件事以前散落各处、等于没做：
 * ① **只抓该抓的**：协议只许 http/https，主机只许 `*.syau.edu.cn` 与 [EXTERNAL_HOSTS] 登记的校外源；
 *    校内站点带查询参数的地址按 robots 口径默认禁止（见 [robotsReason]）。
 * ② **只读这么多**：单个响应设上限，边读边数，超了就断 —— 以前 `readBytes()` 一路读到尾，
 *    一个被放大的响应（或误抓到压缩包）就能把手机内存吃掉。
 * ③ **别打太急**：同一主机两次请求之间留 [MIN_INTERVAL_MS]，跳转链路上每一跳都算。
 *
 * robots 口径取自 2026-11-14 的四站实测（见 AGENTS.md）：允许站点根与静态 .htm / .html，
 * 带查询参数的一律禁止，主站 /info/ 目录禁止（按最长匹配优先）。
 * （原文里的规则写法含有斜杠加星号，在 KDoc 里会被当成嵌套注释的开头，故改写成中文。）
 */
internal object FeedGuard {

    /** 单个 HTML 响应上限。校内新闻页实测最大约 300 KB，留足余量 */
    const val MAX_TEXT_BYTES = 3 * 1024 * 1024

    /** 单张图片上限（列表缩略图实测 30–200 KB） */
    const val MAX_IMAGE_BYTES = 6 * 1024 * 1024

    /** 同主机最小间隔：一分钟不超过 30 次，够用且不打扰 */
    const val MIN_INTERVAL_MS = 2_000L

    private val lastHit = HashMap<String, Long>()

    /**
     * 放行返回 null，拦下返回原因（原因会进调试日志，别写成给人看的说明书）。
     */
    fun check(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "地址解析不了"
        val scheme = uri.scheme?.lowercase() ?: return "没有协议"
        if (scheme != "http" && scheme != "https") return "协议不许：$scheme"
        val host = uri.host?.lowercase() ?: return "没有主机"
        return when {
            isSchoolHost(host) -> robotsReason(uri)
            isListedExternal(host) -> null
            else -> "站外主机：$host"
        }
    }

    private fun isSchoolHost(host: String): Boolean =
        host == "syau.edu.cn" || host.endsWith(".syau.edu.cn")

    /**
     * 校外源白名单。这些是 App 里**本来就在用**的公开信息源（每条在 FeedModels 都能对上），
     * 不是拦不住才放行：只放这一批，别的一律拒。
     *
     * 2026-11-27 的教训：闸门第一版只写了 `endsWith("syau.edu.cn")`，一刀切把
     * 教师资格考试、教育考试院、人事考试网、服务外包大赛等 9 个源全判成「站外主机」，
     * 界面上直接成了「失败」，功能被悄悄砍掉一片。
     *
     * 校外站点的 robots 我们按自己的口径读不到（各家写法不同），所以这里**不做**
     * 校内那套 `?` 拦截 —— 但仍受协议、读取上限、同主机限速三道约束。
     */
    private fun isListedExternal(host: String): Boolean =
        host in EXTERNAL_HOSTS || EXTERNAL_SUFFIXES.any { host == it || host.endsWith("." + it) }

    /** 逐个主机名登记（FeedModels 的 listUrl 原样） */
    private val EXTERNAL_HOSTS = setOf(
        "jobsyau.jysd.com",      // 就业信息网（外部 SaaS）
        "www.tiaozhanbei.net",   // 挑战杯
        "www.cahe.edu.cn",       // 中国高等教育学会
        "www.52jingsai.com",     // 我爱竞赛网
        "cdn1.52jingsai.com",    // 我爱竞赛网的图片 CDN
        "www.cpta.com.cn",       // 人事考试网
        "www.fwwb.org.cn",       // 服务外包大赛
        "jyt.ln.gov.cn",         // 辽宁省教育厅
    )

    /** 同一家单位的多子域，按后缀放行（四个 neea 站：ncre / cet / ntce / www） */
    private val EXTERNAL_SUFFIXES = setOf("neea.edu.cn")

    /**
     * robots 口径。带 `?` 的地址默认拒绝 —— 但校内有两类列表页**只有**查询参数形式
     * （创新创业学院 `comp/front/comp/list?page=N`、团委 `channel.jsp?urltype=…`），
     * 它们在本 App 里是资讯源，故登记在 [QUERY_EXEMPT_HOSTS]。
     * 这是**已知例外**，不是没看见规则：要不要继续抓，等学校给授权口径再定。
     */
    private fun robotsReason(uri: URI): String? {
        val host = uri.host.lowercase()
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery
        if (query != null && host !in QUERY_EXEMPT_HOSTS) return "带查询参数（robots Disallow: /*?*）"
        // 主站 /info/*.htm 明确禁止（学院子站没有这条）
        if ((host == "www.syau.edu.cn" || host == "syau.edu.cn") && path.startsWith("/info/")) {
            return "主站 /info/ 目录（robots 禁止）"
        }
        return null
    }

    /** 已知需要查询参数的校内源主机（每条都在 FeedModels 里有对应资讯源） */
    private val QUERY_EXEMPT_HOSTS = setOf("cxcy.syau.edu.cn", "tw.syau.edu.cn")

    /** 同主机限速；返回需要等待的毫秒数（0 = 不用等） */
    @Synchronized
    fun waitFor(host: String): Long {
        val now = System.currentTimeMillis()
        val last = lastHit[host] ?: 0L
        val wait = (last + MIN_INTERVAL_MS - now).coerceAtLeast(0L)
        lastHit[host] = now + wait
        return wait
    }

    /**
     * 边读边数上限。超限抛 [TooLarge]，调用方当成「这次没拿到」，不许把半个响应当正文。
     */
    fun readCapped(stream: java.io.InputStream, cap: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(cap, 64 * 1024))
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            if (total > cap) throw TooLarge(total, cap)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    class TooLarge(val bytes: Int, val cap: Int) : Exception("响应超过上限：$bytes > $cap")
}
