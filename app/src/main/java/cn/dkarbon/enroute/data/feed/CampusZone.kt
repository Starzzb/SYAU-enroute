package cn.dkarbon.enroute.data.feed

import cn.dkarbon.enroute.data.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我现在在不在校内网」——一次探测，十分钟内复用。
 *
 * 为什么需要：`*.syau.edu.cn` 全校站群只解析出**教育网 IPv6**（实测 2026-11-20：
 * `jwc.syau.edu.cn → 2001:da8:900c:1a2:210:47:162:3`）。人在校外（联通/移动宽带、
 * 或者手机 5G）也有 IPv6 地址，但包到不了教育网边界 —— 结果是 9 个校内源全部
 * connect timeout，界面上看起来像「整个资讯都坏了」。
 *
 * 这不是 bug 也不是能优化的延迟：**没有路**。所以把「没路」和「出错」分开：
 * 探测到校外就跳过校内源（[cn.dkarbon.enroute.data.feed.SourceResult.skipped]），
 * 界面说「需校园网 / WebVPN」，公网源照常刷。
 *
 * 探测本身只碰一个静态页面，2.5 秒超时，失败即判校外（宁可少抓，也别让用户等一串超时）。
 */
object CampusZone {

    enum class Zone {
        /** 还没探过 */
        Unknown,

        /** 校内网 / WebVPN / mangoVPN 里：校内源直连可读 */
        Campus,

        /** 校外：校内源没有通路，跳过 */
        OffCampus,
    }

    /** 探测用的固定靶子：教务通知列表（校内最稳的一个静态页，200 即算通） */
    private const val PROBE_URL = "https://jwc.syau.edu.cn/index/jwtz.htm"

    private const val TTL_MS = 10 * 60 * 1000L

    @Volatile
    private var zone: Zone = Zone.Unknown

    @Volatile
    private var checkedAt: Long = 0L

    /** 上次探测结论（不触发网络） */
    fun peek(): Zone = zone

    /**
     * 探测当前网络身份。
     * @param force 忽略十分钟缓存，重新探（用户手动刷新整表时用）
     */
    suspend fun detect(force: Boolean = false): Zone = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && zone != Zone.Unknown && now - checkedAt < TTL_MS) return@withContext zone
        val z = runCatching {
            val resp = FeedHttp.get(PROBE_URL, timeoutMs = 2_500)
            if (resp.status in 200..299 && resp.body.isNotEmpty()) Zone.Campus else Zone.OffCampus
        }.getOrElse { Zone.OffCampus }
        zone = z
        checkedAt = now
        DebugLog.log("FEED", "网络身份探测：${if (z == Zone.Campus) "校内/VPN" else "校外"}（${PROBE_URL}）")
        z
    }

    /** 校内源能不能走：未知也当能（第一次刷新由 [detect] 决定，别提前砍源） */
    fun campusReachable(): Boolean = zone != Zone.OffCampus

    /** 测试与「手动切网络后立刻重试」用 */
    fun reset() {
        zone = Zone.Unknown
        checkedAt = 0L
    }
}
