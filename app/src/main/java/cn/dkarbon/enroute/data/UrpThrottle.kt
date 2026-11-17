package cn.dkarbon.enroute.data

/**
 * 同 host 的最小发送间隔。
 *
 * ## 为什么
 *
 * 真人在网页上点一次「考试 → 明细」，两次请求之间隔着几百毫秒到几秒的思考时间。
 * 我们一次刷新会把十几条请求挤在几十毫秒里发出去 —— 这个节奏本身就不是人。
 * 节流不改变请求**数量**、**顺序**、**内容**，只把相邻两条拉开。
 *
 * ## 代价（实测写进 CHANGELOG）
 *
 * 一次全量刷新约 14 条，最坏多花 ~2 秒；诊断页同理。用户看到的是「慢一点」，
 * 换来的是"节奏像人"。
 *
 * ## 实现
 *
 * 调用点全都在 `Dispatchers.IO` 上，所以用阻塞 `Thread.sleep` 而不做成 `suspend`，
 * 免得把五个调用点全改成挂起链。全局一把锁：只有一个目标 host，不需要按 host 分桶。
 */
internal object UrpThrottle {

    /** 相邻两条请求的最小间隔（毫秒） */
    const val MIN_GAP_MS = 150L

    private val lock = Any()
    private var lastAt = 0L

    /** 单测用：清掉上一次的时间戳 */
    fun reset() = synchronized(lock) { lastAt = 0L }

    /** 距上次发送不足 [MIN_GAP_MS] 就先睡够；不动任何业务逻辑 */
    fun await(host: String) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = MIN_GAP_MS - (now - lastAt)
            if (lastAt != 0L && wait > 0) {
                runCatching { Thread.sleep(wait) }
            }
            lastAt = System.currentTimeMillis()
        }
    }
}
