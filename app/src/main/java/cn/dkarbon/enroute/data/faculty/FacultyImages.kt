package cn.dkarbon.enroute.data.faculty

import android.content.Context
import cn.dkarbon.enroute.data.feed.FeedHttp
import java.io.File
import java.security.MessageDigest

/**
 * 官网正文图片的本地缓存：`filesDir/faculty/img/<sha1>.<ext>`。
 *
 * 落盘的理由：学院站不在 CDN 上，每点开一位老师都去拉一遍既慢又给站里添负担；
 * 图片基本不变，所以**不跟 JSON 缓存一起过期**（JSON 30 天会重抓，图直接复用），
 * 只在「清除所有数据」时整目录删掉。
 *
 * 文件名取 URL 的 sha1：同一张图在不同老师、不同页面上只存一份。
 */
object FacultyImages {

    /** 单张上限：官网正文图实测 100–300 KB，超过 1 MB 的多半是原图直出，不落盘 */
    private const val MAX_BYTES = 1024 * 1024

    /** 一次最多下几张：正文首图 + 少量配图，够看就行 */
    private const val MAX_IMAGES = 4

    fun dir(ctx: Context): File = File(ctx.filesDir, "faculty/img").apply { mkdirs() }

    fun fileFor(ctx: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val ext = Regex("""\.(jpe?g|png|webp)""", RegexOption.IGNORE_CASE)
            .find(url)?.value?.lowercase() ?: ".jpg"
        return File(dir(ctx), hash + ext)
    }

    /** 本地已有的那张；没有返回 null（界面据此显示空位，不去猜） */
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
