package cn.dkarbon.enroute.data.telemetry

import android.content.Context
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.DebugLog
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * 事件上传：一批最多 100 条，gzip，失败按 1/4/16 分钟退避，三次扔整批。
 *
 * ## 为什么用明文 http 和自定义 UA
 *
 * 与 [cn.dkarbon.enroute.data.map.MapUploader] 同一条链路上的同一台机器
 * （`47.95.12.198`，18082 主用 / 18081 回落）：TLS 在手机链路上会被中间设备 RST，
 * 所以走明文；nginx 在 18082 上有 UA 过滤器，`Java/1.x` 默认 UA 会直接 403 ——
 * 那种失败看起来像网络错误，实际是被挡了，非常难查（地图那边已经踩过一次）。
 *
 * ## 三次失败就扔，不攒隐私库
 *
 * 这是设计承诺，不是省事：一份「永远发不出去、永远在长大」的本地事件库，
 * 会在用户看得见的地方变成一个隐私风险。三次不行就整批丢弃，只在调试日志留一行。
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
            Result.Ok -> {
                queue.dropFirst(pending.size)
                clearBackoff()
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

    private fun degradeOf(lines: List<String>): String = lines.map { line ->
        // 只留 v 与 e：正则取两个字段，取不到就退化成空对象（服务端会按 v/e 记一条「未知」）
        val v = Regex("""\"v\":(\d+)""").find(line)?.groupValues?.get(1) ?: TelemetrySchema.VERSION.toString()
        val e = Regex("""\"e\":\"([^\"]+)\""" + "\"").find(line)?.groupValues?.get(1) ?: "unknown"
        "{\"v\":" + v + ",\"e\":\"" + e + "\"}"
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
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.use { it.readBytes() }
                when {
                    code in 200..299 -> return Result.Ok
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
            backoffFile.writeText(next.toString() + "\n" + (System.currentTimeMillis() + waitMs))
            // 三次之后不再延长：下次 flush 直接整批丢弃（见 runOnce 的 Failed 分支说明）
            if (next >= MAX_ATTEMPTS) {
                queue.clear()
                clearBackoff()
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

    private enum class Result { Ok, Rejected, Failed }

    companion object {

        /** 一批最多 100 条 */
        const val MAX_BATCH = 100

        /** 攒够 20 条才发（进后台/手动除外） */
        const val MIN_BATCH = 20

        /** 三次失败后丢弃 */
        const val MAX_ATTEMPTS = 3

        /** 采集端点：18082 主用 / 18081 回落 */
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