package cn.dkarbon.enroute.data.feed

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 资讯的本地存储。
 *
 * 布局（就是「以后搬服务器」时最省事的形态 —— 每条正文一个文件，
 * 增删改都是 O(1)，不用重写整包）：
 * ```
 * filesDir/feed/
 *   ├── items.json          列表元数据（标题/摘要/日期/已读/是否已有正文）
 *   └── bodies/
 *       ├── jwc_1250_10666.txt     ← 正文，抓一次永久留着
 *       └── …
 * ```
 *
 * 为什么正文单独存文本文件而不是塞进一个大 JSON：
 *   · 一次刷新只重写 items.json（几十 KB），不动正文
 *   · 正文是「点进去才抓、抓完永久存」的东西，和下一个人共享 —— 天然适合当缓存
 *   · 以后搬到服务器，这一层换成 HTTP 就是同一个契约（key → text）
 */
class FeedStore(ctx: Context) {

    private val dir = File(ctx.applicationContext.filesDir, "feed")
    private val itemsFile = File(dir, "items.json")
    private val bodiesDir = File(dir, "bodies")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    init {
        runCatching { bodiesDir.mkdirs() }
    }

    // ── 列表 ──────────────────────────────────────────────

    suspend fun loadItems(): List<FeedItem> = mutex.withLock { readItems() }

    suspend fun saveItems(items: List<FeedItem>) = mutex.withLock {
        runCatching {
            dir.mkdirs()
            val tmp = File(dir, "items.json.tmp")
            tmp.writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FeedItem.serializer()), items))
            tmp.renameTo(itemsFile)
        }
        Unit
    }

    private fun readItems(): List<FeedItem> = runCatching {
        if (!itemsFile.exists()) return emptyList()
        json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(FeedItem.serializer()),
            itemsFile.readText(),
        )
    }.getOrDefault(emptyList())

    // ── 正文（永久缓存）──────────────────────────────────

    /**
     * 取已存的正文包（文本 + 链接 + 附件）；没有返回 null。
     * 命中就是「下一个人直接能看」。
     */
    fun body(key: String): BodyPayload? = runCatching {
        val f = bodyFile(key)
        if (!f.exists() || f.length() == 0L) return null
        val p = json.decodeFromString(BodyPayload.serializer(), f.readText())
        // 解析能力升级后，旧缓存当作未命中 —— 否则新字段永远是空的，
        // 详情页会一直显示旧结果（真机踩过：PDF 卡片出不来）。
        if (p.schema < BodyPayload.SCHEMA) return null
        p
    }.getOrNull() ?: legacyBody(key)

    /**
     * 早期只存纯文本的 `.txt` 缓存：**一律视为未命中**，顺手删掉。
     *
     * 那种缓存只有正文，没有 images / pdfUrl / attachmentsBlocked，
     * 读出来会让详情页显示半套内容 —— 不如重抓一次。
     */
    private fun legacyBody(key: String): BodyPayload? {
        runCatching {
            val f = File(bodiesDir, key.replace(Regex("""[^\w\-]"""), "_") + ".txt")
            if (f.exists()) f.delete()
        }
        return null
    }

    suspend fun saveBody(key: String, payload: BodyPayload) = mutex.withLock {
        runCatching {
            bodiesDir.mkdirs()
            // 落盘前盖版本号：调用方（解析器）不需要关心这个字段，
            // 但缓存闸门认它 —— 漏盖就会让这条缓存下一版立刻失效。
            val stamped = payload.copy(schema = BodyPayload.SCHEMA)
            bodyFile(key).writeText(json.encodeToString(BodyPayload.serializer(), stamped))
        }
        Unit
    }

    fun hasBody(key: String): Boolean = bodyBodyExists(key)

    private fun bodyBodyExists(key: String): Boolean =
        runCatching { bodyFile(key).let { it.exists() && it.length() > 0 } }.getOrDefault(false)

    /** 正文文件数（调试/统计用） */
    fun bodyCount(): Int = runCatching { bodiesDir.listFiles()?.size ?: 0 }.getOrDefault(0)

    private fun bodyFile(key: String): File =
        File(bodiesDir, key.replace(Regex("""[^\w\-]"""), "_") + ".json")

    // ── 清理 ──────────────────────────────────────────────

    suspend fun clear() = mutex.withLock {
        runCatching {
            itemsFile.delete()
            bodiesDir.deleteRecursively()
            bodiesDir.mkdirs()
        }
        Unit
    }
}
