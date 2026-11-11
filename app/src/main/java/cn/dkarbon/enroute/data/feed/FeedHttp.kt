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

    data class Resp(val status: Int, val body: String, val finalUrl: String)

    fun get(url: String, timeoutMs: Int = 15_000): Resp {
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
                    DebugLog.http("FEED", "GET", current, code, 0, note = "非 2xx")
                    return Resp(code, "", current)
                }

                val raw = conn.inputStream.use { it.readBytes() }
                val enc = conn.getHeaderField("Content-Encoding").orEmpty()
                val bytes = if (enc.contains("gzip", true)) {
                    runCatching { GZIPInputStream(raw.inputStream()).use { it.readBytes() } }.getOrDefault(raw)
                } else {
                    raw
                }
                val text = decode(bytes, conn.contentType.orEmpty())
                DebugLog.http("FEED", "GET", current, code, text.length, preview = text.take(120))
                return Resp(code, text, current)
            } finally {
                runCatching { conn.disconnect() }
            }
        }
        return Resp(-1, "", url)
    }

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
        return String(bytes, cs)
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
