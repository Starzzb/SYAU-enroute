package cn.dkarbon.enroute.data

/**
 * URP 一次 HTTP 交换的完整结果。
 *
 * 为什么不再只返回正文：以前非 2xx 一律回空串，于是「500 维护页」和「真的没有数据」
 * 在调用方看来一模一样 —— 空列表写进缓存，界面上就成了「这学期没有考试」。
 * 状态码、正文、最终地址一起带回来，判失败的活交给 [UrpFailure]，由调用方决定是
 * 「过期了要重登」还是「这次没拿到」，两者不许混成同一个 null。
 */
internal data class UrpResponse(
    val code: Int,
    val body: String,
    val path: String,
    val location: String? = null,
) {
    val ok: Boolean get() = code in 200..299

    val failure: UrpFailure get() = UrpFailure.of(code, body, location)

    /**
     * 2xx 且不是登录页 → 正文。
     *
     * @throws SessionExpiredException 明确的登录页 / 401 / 403 —— 只有这一种该清会话；
     * @throws UrpHttpException 其余非 2xx（4xx/5xx/跟不动的 3xx）—— 只是这一次没拿到。
     */
    fun requireOk(where: String): String = when (failure) {
        UrpFailure.None -> body
        UrpFailure.Expired -> throw SessionExpiredException(where + " → HTTP " + code + "（登录页/401/403）")
        else -> throw UrpHttpException(code, where, where + " → HTTP " + code + "（" + failure.name + "）")
    }
}

/** 非 2xx 但不是「会话过期」的失败 —— 界面该说「这次没拿到」，不许清会话 */
class UrpHttpException(
    val code: Int,
    val where: String,
    message: String,
) : Exception(message)

/**
 * 失败分类。**只有 [Expired] 能让上层清会话**，这条是 2026-11-27 定的：
 * 以前「5xx 维护页」也被 [UrpClient.judgeSession] 当成过期，学生被踢下线重登，
 * 而服务器其实只是 502 了一下。
 */
internal enum class UrpFailure {
    None,
    Expired,
    Server,
    Client,
    Redirect,
    Unknown;

    companion object {
        fun of(code: Int, body: String, location: String? = null): UrpFailure = when {
            code == 401 || code == 403 -> Expired
            code in 300..399 -> if (UrpSigns.isLoginRedirect(location)) Expired else Redirect
            code in 200..299 -> if (UrpSigns.isLoginPage(body)) Expired else None
            code in 500..599 -> Server
            code in 400..499 -> Client
            else -> Unknown
        }
    }
}

/**
 * 会话粘滞流程的互斥闸。
 *
 * 空闲教室是「先 POST 选中楼栋，再 GET 问那一栋」的两步流程，选楼状态存在**服务器会话**里。
 * 快速切楼或并发刷新时，A 的第二步可能插到 B 的第一步后面，答出来的是 B 选的那栋 ——
 * 数据不错，但答非所问。所以整段独占，两步之间不许别人选楼。
 *
 * 用 ReentrantLock 而不是协程 Mutex：包里两步本来就是阻塞式 HttpURLConnection，
 * 这段临界区里没有任何挂起点，用协程锁反而要在阻塞线程上再挂一次。
 */
internal object UrpSticky {
    private val freeRoom = java.util.concurrent.locks.ReentrantLock()

    fun <T> withFreeRoom(block: () -> T): T {
        freeRoom.lock()
        return try {
            block()
        } finally {
            freeRoom.unlock()
        }
    }
}

/** 认「这是不是登录页」的记号 —— 写法都来自实测抓包（`docs/_probe/urp/`） */
internal object UrpSigns {

    /** CAS 登录页 / URP 自己的登录页 */
    fun isLoginPage(body: String): Boolean {
        if (body.isBlank()) return false
        val head = if (body.length > 8192) body.substring(0, 8192) else body
        return head.contains("tpass/login") ||
            head.contains("loginForm") ||
            (head.contains("name=\"lt\"") && head.contains("name=\"execution\""))
    }

    /** 302 的目标是不是登录入口（CAS 踢回登录页 = 会话过期） */
    fun isLoginRedirect(location: String?): Boolean {
        if (location.isNullOrBlank()) return false
        val lower = location.lowercase()
        return lower.contains("pass.syau.edu.cn") || lower.contains("tpass/login")
    }
}
