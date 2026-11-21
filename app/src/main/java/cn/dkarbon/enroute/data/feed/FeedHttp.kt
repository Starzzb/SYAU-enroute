package cn.dkarbon.enroute.data.feed

import cn.dkarbon.enroute.data.DebugLog
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * 抓取用的 HTTP。
 *
 * 三条实测结论写死在这里：
 *   1. 响应体是 **UTF-8**（四个站实测都是，但保留 charset 探测兜底，别假设）
 *   2. 支持 gzip（`Vary: Accept-Encoding`），不带就白吃流量
 *   3. 支持 `ETag` / `Last-Modified` 条件请求（304 能省掉 90% 的轮询成本）——
 *      目前测试期没接，接上服务器后加上
 */
object FeedHttp {

    /** 诚实标识自己是谁、怎么联系 —— 出问题时能被找到，这是礼貌也是自保 */
    private const val UA = "SYAU-Enroute/1.0 (personal student test build; contact via app)"

    /** @param elapsedMs 本次请求耗时（票 20 / D1）；`-1` = 未计时 */
    data class Resp(val status: Int, val body: String, val finalUrl: String, val elapsedMs: Long = -1)

    fun get(url: String, timeoutMs: Int = 15_000): Resp {
        // 票 20 / D1：自己计时（nanoTime，不受系统时间调整影响）
        val startedAt = System.nanoTime()
        var current = url
        var hops = 0
        while (hops < 5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs * 2
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                conn.setRequestProperty("Accept-Encoding", "gzip")
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")

                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")

                if (code in 300..399 && loc != null) {
                    current = FeedParser.resolve(current, loc)
                    hops++
                    continue
                }

                if (code !in 200..299) {
                    DebugLog.http("FEED", "GET", current, code, 0, elapsedMs = msSince(startedAt), note = "非 2xx")
                    return Resp(code, "", current, msSince(startedAt))
                }

                val raw = conn.inputStream.use { it.readBytes() }
                val enc = conn.getHeaderField("Content-Encoding").orEmpty()
                val bytes = if (enc.contains("gzip", true)) {
                    runCatching { GZIPInputStream(raw.inputStream()).use { it.readBytes() } }.getOrDefault(raw)
                } else {
                    raw
                }
                val text = decode(bytes, conn.contentType.orEmpty())
                val elapsed = msSince(startedAt)
                DebugLog.http("FEED", "GET", current, code, bytes.size, elapsedMs = elapsed, preview = text.take(120))
                return Resp(code, text, current, elapsed)
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return Resp(-1, "", url)
    }

    /**
     * 取二进制（图片）。与 [get] 同一套 UA / 超时 / 跳转策略，只是不解码成文本。
     *
     * @return 2xx 的字节；非 2xx 或异常返回 null（图片是锦上添花，不值得让整页失败）
     */
    fun getBytes(url: String, timeoutMs: Int = 15_000): ByteArray? {
        val startedAt = System.nanoTime()
        var current = url
        var hops = 0
        while (hops < 5) {
            val conn = runCatching { URL(current).openConnection() as HttpURLConnection }.getOrNull() ?: return null
            try {
                conn.requestMethod = "GET"
                conn.instanceFollowRedirects = false
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs * 2
                conn.setRequestProperty("User-Agent", UA)
                conn.setRequestProperty("Accept", "image/*,*/*;q=0.8")
                conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
                val code = conn.responseCode
                val loc = conn.getHeaderField("Location")
                if (code in 300..399 && loc != null) {
                    current = FeedParser.resolve(current, loc)
                    hops++
                    continue
                }
                if (code !in 200..299) {
                    DebugLog.http("FEED", "IMG", current, code, 0, elapsedMs = msSince(startedAt), note = "非 2xx")
                    return null
                }
                val bytes = conn.inputStream.use { it.readBytes() }
                DebugLog.http("FEED", "IMG", current, code, bytes.size, elapsedMs = msSince(startedAt))
                return bytes
            } catch (e: Exception) {
                DebugLog.error("FEED", "取图片失败 $current · ${e.javaClass.simpleName}: ${e.message}")
                return null
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return null
    }

    /** 从 nanoTime 起算到现在的毫秒数 */
    private fun msSince(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000

    /** 编码探测：header 的 charset → meta charset → UTF-8 */
    private fun decode(bytes: ByteArray, contentType: String): String {
        var charset = Regex("""charset=([\w-]+)""", RegexOption.IGNORE_CASE)
            .find(contentType)?.groupValues?.get(1)

        if (charset == null) {
            val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
            charset = Regex("""charset\s*=\s*["']?([\w-]+)""", RegexOption.IGNORE_CASE)
                .find(head)?.groupValues?.get(1)
        }

        val cs = runCatching { charsetOf(charset ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        // 剥 UTF-8 BOM：2026-11-21 实测 7 个校内源里 6 个带 BOM（EF BB BF）。
    // 不剥的话第一行变成 "\uFEFF<html…"，任何行首正则 / startsWith 判定都会整条失效。
    return String(bytes, cs).removePrefix("\uFEFF")
    }

    private fun charsetOf(name: String): java.nio.charset.Charset = when (name.lowercase()) {
        "gb2312", "gbk", "gb18030" -> Charset.forNameOrUtf8("GB18030")
        else -> Charset.forNameOrUtf8(name)
    }

    private object Charset {
        fun forNameOrUtf8(name: String): java.nio.charset.Charset =
            runCatching { java.nio.charset.Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }
}
