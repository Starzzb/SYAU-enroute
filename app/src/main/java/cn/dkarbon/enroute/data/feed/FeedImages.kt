package cn.dkarbon.enroute.data.feed

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 资讯正文插图的本地缓存：`filesDir/feed/img/<sha1>.<ext>`。
 *
 * 为什么要落盘而不是每次现拉（2026-11-21 实测依据）：
 *  · 沈农新闻一张原图 2976×1984 / **2.4 MB**，正文版 1024×682 / 73 KB，差 33 倍；
 *  · 挑战杯一条正文 13 张图合计 **2.16 MB**；
 *  · 校团委、教务通知的图都在校内 `/__local/`，出校门根本拉不到 —— 缓存是唯一能重复看的办法。
 *
 * 与 [FacultyImages] 同一套做法：文件名取 URL 的 sha1，同一张图在不同文章里只存一份；
 * 只在「清除所有数据」时整目录删掉。
 *
 * ⚠️ 超限策略与教师图**不同**：教师图超 1 MB 直接放弃，正文图不能 ——
 * 长截图（服务外包大赛 873×3780 / 706 KB、竞赛成果 2845×1280 ×4 张）本身就是内容，
 * 放弃了正文就只剩一行标题。这里放宽到 3 MB，超了才放弃并在界面说明。
 */
object FeedImages {

    /** 单张上限：实测正文图最大 2.4 MB（沈农新闻原图），超 3 MB 的多半不是正文图 */
    private const val MAX_BYTES = 3 * 1024 * 1024

    /** 一条正文最多下几张：实测最多 13 张（挑战杯），取前 8 张够看 */
    private const val MAX_IMAGES = 8

    fun dir(ctx: Context): File = File(ctx.filesDir, "feed/img").apply { mkdirs() }

    fun fileFor(ctx: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val ext = Regex("""\.(jpe?g|png|webp|gif)""", RegexOption.IGNORE_CASE)
            .find(url)?.value?.lowercase() ?: ".jpg"
        return File(dir(ctx), hash + ext)
    }

    /** 本地已有的那张；没有返回 null（界面显示空位，不去猜） */
    fun cached(ctx: Context, url: String): File? =
        fileFor(ctx, url).takeIf { it.isFile && it.length() > 0 }

    /**
     * 把 [urls] 里还没落盘的几张拉下来。阻塞调用，请在 IO 线程里用。
     *
     * 单张失败只是少一张图，不影响文字与其它图。
     */
    fun ensure(ctx: Context, urls: List<String>): List<File> = urls.take(MAX_IMAGES).mapNotNull { url ->
        cached(ctx, url)?.let { return@mapNotNull it }
        val bytes = FeedHttp.getBytes(url) ?: return@mapNotNull null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@mapNotNull null
        runCatching {
            val target = fileFor(ctx, url)
            val tmp = File(target.absolutePath + ".tmp")
            tmp.writeBytes(bytes)
            if (tmp.renameTo(target)) target else null
        }.getOrNull()
    }

    fun clear(ctx: Context) {
        runCatching { dir(ctx).deleteRecursively() }
    }
}
