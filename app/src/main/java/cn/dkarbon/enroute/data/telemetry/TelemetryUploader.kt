package cn.dkarbon.enroute.data.telemetry

import android.content.Context
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.DebugLog
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * 事件上传：一批最多 100 条，gzip，失败按 1/4/16 分钟退避，三次丢掉最旧的一半。
 *
 * ## 为什么用明文 http 和自定义 UA
 *
 * 与 [cn.dkarbon.enroute.data.map.MapUploader] 是同一台机器、同一条链路的两个口
 * （18082 主用 / 18081 回落）：TLS 会被手机链路上的中间设备 RST，所以走明文；
 * nginx 按 UA 挡脚本客户端，`HttpURLConnection` 的默认 `Java/1.x` 会直接 403 ——
 * 看起来像网络错误，其实是被挡了。UA 规则的完整说明只写在 MapUploader 的 `UA` 常量上。
 *
 * ## 三次失败就扔掉一半，不攒隐私库
 *
 * 这是设计承诺，不是省事：一份「永远发不出去、永远在长大」的本地事件库，
 * 会在用户看得见的地方变成一个隐私风险。
 *
 * 但**丢的方式改过一次**（2026-11-27）：原来是三次连不上就 `queue.clear()` 整批蒸发，
 * 于是「离线两天 → 一联网就清空」，等于白采。现在是丢掉**最旧的一半**留一半：
 * 既不让库无限长，又给新事件留出位置；真正发不出去的旧事件也不会永远占着头。
 *
 * ## 2xx 不等于「都收到了」
 *
 * 服务端对 schema 不认的行**不报错**，仍然回 200，把条数写在 body 里：
 * `{"accepted":N,"dropped":M}`。所以出队只认 `accepted`，不再认状态码 ——
 * 否则本地删了、服务端也没留，两头都看不见（2026-11-27 真机实测踩到）。
 *
 * ## 服务端 400 的处理
 *
 * 400 意味着它不认这批 schema（多半是服务端升级后把旧版本关了）。
 * 这时整批丢弃并 [degraded] 置位，之后只发 `v` 与 `e` 两个字段，避免无休止地重试同一批。
 */
class TelemetryUploader(
    private val ctx: Context,
    private val queue: TelemetryQueue,
) {

    private val backoffFile = java.io.File(ctx.filesDir, "telemetry/backoff.txt")

    /** 服务端拒过这一版 schema 之后，只发最小字段 */
    var degraded: Boolean = false
        private set

    /**
     * 跑一次：够条件就发一批。返回是否「这一趟没有留下该发的东西」。
     *
     * 不发的情况都返回 `true`（没有待发内容不算失败，别去重试）。
     */
    fun runOnce(reason: String): Boolean {
        // 同一时刻只允许一趟上传：[Telemetry.record] 里那句 net.fail 也可能引发新的 flush，
        // 两趟同时跑会各自 peek 到同一批再各自 dropFirst —— 队列会被删乱
        if (!Gate.busy.compareAndSet(false, true)) return true
        return try {
            runOnceInner(reason)
        } finally {
            Gate.busy.set(false)
        }
    }

    private fun runOnceInner(reason: String): Boolean {
        if (!Prefs_TELEMETRY_ENABLED(ctx)) return true
        val pending = queue.peek(MAX_BATCH)
        if (pending.isEmpty()) return true
        if (pending.size < MIN_BATCH && reason != "background" && reason != "manual") {
            // 太少就先攒着，减少唤醒与请求次数（进后台/手动例外：那时不发可能就没机会了）
            return true
        }
        if (System.currentTimeMillis() < readNextTryAt()) return true

        val body = if (degraded) degradeOf(pending) else pending.joinToString("\n")
        val result = post(gzip(body.toByteArray(Charsets.UTF_8)))
        return when (result) {
            is Result.Ok -> {
                // 只对**服务端真的收下**的那部分出队。accepted 缺省（-1）= 老服务端没写 body，
                // 这时退回「整批都收下了」的旧口径，别把好数据卡在本地
                val kept = if (result.accepted >= 0) result.accepted.coerceIn(0, pending.size) else pending.size
                val lost = (pending.size - kept).coerceAtLeast(0)
                queue.dropFirst(kept)
                if (lost == 0) {
                    clearBackoff()
                    return true
                }
                // 有行被丢：那是 schema 不认的（多半是服务端升级后关了某个字段/事件）。
                // 降级成只发 v/e，下一批就一定能过；本地不再拿这些行反复试
                degraded = true
                DebugLog.log("telemetry", "服务端丢了 $lost 条（schema 不认）→ 改用最小字段")
                Telemetry.record("net.fail", mapOf("where" to "telemetry", "kind" to "parse"))
                if (kept == 0) {
                    // 一条都没收下：整批格式坏了，或服务端 schema 变了。计一次退避往前走，
                    // 三次之后 bumpBackoff 会丢掉最旧的一半 —— 别在这儿原地打转
                    bumpBackoff()
                } else {
                    clearBackoff()
                }
                true
            }
            Result.Rejected -> {
                // schema 不认：整批丢弃 + 降级，别让同一批无限重试
                queue.dropFirst(pending.size)
                degraded = true
                clearBackoff()
                DebugLog.log("telemetry", "服务端不认这批 schema，已丢弃并降级为只发 v/e")
                true
            }
            Result.Failed -> {
                bumpBackoff()
                false
            }
        }
    }

    /**
     * 降级行：只留 `v` / `e` / `t` 三个字段。
     *
     * `t` 必须留（2026-11-27 修）：服务端 `clean_line` 拿不到合法 `t` 就整行判脏，
     * 而「降级」是为了让这批一定能过 —— 少了 `t` 反而 100% 被拒，降级等于白降。
     * `t` 从原行里抠，抠不到才用当前时刻（宁可时间近似，也不能整行丢掉）。
     */
    private fun degradeOf(lines: List<String>): String = lines.map { line ->
        val v = Regex("""\"v\":(\d+)""").find(line)?.groupValues?.get(1) ?: TelemetrySchema.VERSION.toString()
        val e = Regex("""\"e\":\"([^\"]+)\""" + "\"").find(line)?.groupValues?.get(1) ?: "unknown"
        val t = Regex("""\"t\":(\d+)""").find(line)?.groupValues?.get(1)
            ?: (System.currentTimeMillis() / 1000L).toString()
        "{\"v\":" + v + ",\"e\":\"" + e + "\",\"t\":" + t + "}"
    }.joinToString("\n")

    private fun post(gz: ByteArray): Result {
        for (base in ENDPOINTS) {
            val conn = runCatching { URL(base).openConnection() as HttpURLConnection }.getOrNull() ?: continue
            try {
                conn.requestMethod = "POST"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8_000
                conn.readTimeout = 15_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-ndjson")
                conn.setRequestProperty("Content-Encoding", "gzip")
                // ⚠️ 这两行都不能删：默认 UA 是 Java/1.x，正好在 nginx 黑名单里
                conn.setRequestProperty("User-Agent", UA)
                if (API_KEY.isNotBlank()) conn.setRequestProperty("X-Api-Key", API_KEY)
                conn.outputStream.use { it.write(gz) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes() }?.toString(Charsets.UTF_8).orEmpty()
                when {
                    // 服务端把「收下几条、丢了几条」写在 body 里，读它，别看状态码
                    code in 200..299 -> return Result.Ok(
                        accepted = countOf(text, "accepted"),
                        dropped = countOf(text, "dropped"),
                    )
                    // 400/422：schema 不认；413：这批太大，扔了重攒
                    code == 400 || code == 422 || code == 413 -> return Result.Rejected
                    // 401/403：键不对（构建时没配）—— 静默失败，不再重试
                    code == 401 || code == 403 -> {
                        DebugLog.log("telemetry", "服务端拒绝（HTTP " + code + "）：可能没配 TELEMETRY_KEY")
                        return Result.Rejected
                    }
                    // 5xx / 其他：记一行，继续试下一个端点
                }
            } catch (_: Exception) {
                // 网络错误：继续试下一个端点
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return Result.Failed
    }

    /** 从 `{"accepted":N,"dropped":M}` 里取一个数；取不到返回 -1（＝不知道，不是 0） */
    private fun countOf(json: String, key: String): Int =
        Regex("\"" + key + "\"\\s*:\\s*(\\d+)").find(json)
            ?.groupValues?.get(1)?.toIntOrNull() ?: -1

    // ── 退避（1 / 4 / 16 分钟，三次封顶）────────────────

    fun readNextTryAt(): Long = runCatching {
        if (backoffFile.exists()) backoffFile.readText().trim().toLongOrNull() ?: 0L else 0L
    }.getOrDefault(0L)

    private fun bumpBackoff() {
        runCatching {
            backoffFile.parentFile?.mkdirs()
            val attempts = if (backoffFile.exists()) {
                (backoffFile.readLines().firstOrNull()?.toIntOrNull() ?: 0)
            } else 0
            val next = attempts + 1
            val waitMs = when (next) {
                1 -> 60_000L
                2 -> 4 * 60_000L
                else -> 16 * 60_000L
            }
            cn.dkarbon.enroute.data.SafeWrite.text(backoffFile, next.toString() + "\n" + (System.currentTimeMillis() + waitMs))
            // 三次之后不再延长：丢掉最旧的一半，给新事件腾地方（整批清空会连离线那几天的
            // 数据一起蒸发，等于白采；只扔一半既止血又留证据）
            if (next >= MAX_ATTEMPTS) {
                val thrown = queue.dropOldestHalf()
                clearBackoff()
                DebugLog.log("telemetry", "连续 " + next + " 次没发出去，丢掉最旧的 " + thrown + " 条")
            }
        }
    }

    private fun clearBackoff() {
        runCatching { backoffFile.delete() }
    }

    /** gzip 一整批（NDJSON 文本，一条约 110 字节，压完约 25 字节） */
    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size / 4 + 64)
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private sealed interface Result {
        /** 服务端回了 200：`accepted`/`dropped` 取自 body，-1 = body 里没有这个数 */
        data class Ok(val accepted: Int, val dropped: Int) : Result
        /** schema 不认这批（400/422/413）或键不对（401/403）：整批丢弃 */
        data object Rejected : Result
        /** 网络/5xx：留着下次再发 */
        data object Failed : Result
    }

    /** 上传闸门：进程内同一时刻只跑一趟（见 [runOnce]） */
    private object Gate {
        val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    companion object {

        /** 一批最多 100 条 */
        const val MAX_BATCH = 100

        /** 攒够 20 条才发（进后台/手动除外） */
        const val MIN_BATCH = 20

        /** 三次失败后丢弃 */
        const val MAX_ATTEMPTS = 3

        /** 采集端点，顺序即优先级；为什么是明文、为什么必须带 UA 见类注释 */
        val ENDPOINTS = listOf(
            "http://47.95.12.198:18082/api/telemetry",
            "http://47.95.12.198:18081/api/telemetry",
        )

        private const val UA = "SYAU-Enroute (personal student test build; usage telemetry)"

        /** 与地图补丁同一把键；留空 = 不带该头 */
        private val API_KEY: String = BuildConfig.CMAP_API_KEY

        /** 白名单外的开关读取（避免把 Prefs 依赖引进来让本类不可单测） */
        private fun Prefs_TELEMETRY_ENABLED(ctx: Context): Boolean =
            runCatching { cn.dkarbon.enroute.data.Prefs(ctx).telemetryEnabled }.getOrDefault(false)
    }
}