package cn.dkarbon.enroute.data.faculty

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.OffsetDateTime

/**
 * 教师公开信息的缓存**规则**（全是纯函数，单测直接打）。
 *
 * 为什么与 [FacultyStore] 分开：TTL、键、淘汰策略是「该怎么判」，
 * 落盘是「往哪写」。混在一起就只能靠真机验证策略对不对。
 *
 * 方案出自票 05 §5：按工号存结构化 JSON、TTL 30 天、上限 500 条 / 5MB、超出淘汰最旧。
 */
object FacultyCache {

    const val TTL_DAYS = 30
    const val MAX_ENTRIES = 500
    const val MAX_BYTES = 5L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val SAFE = Regex("""[^A-Za-z0-9_\-]""")

    /**
     * 缓存键：**优先工号**（教务侧稳定唯一，搜教师→点详情能直接命中，不必先匹配姓名）。
     * 没有工号时退化成 `姓名@域名` 的短哈希 —— 这是票 05 定的兜底。
     */
    fun key(teacherNo: String, name: String, host: String): String {
        val no = teacherNo.trim()
        if (no.isNotBlank()) return SAFE.replace(no, "_")
        val seed = name.trim() + "@" + host.trim()
        if (seed == "@") return ""
        val digest = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(16)
    }

    /**
     * 缓存是否还算新鲜（TTL 30 天）。
     *
     * `fetchedAt` 解析不出来 → **当作不新鲜**（宁可多抓一次，也不要拿一条不知道年代的资料
     * 当最新信息展示）。
     */
    fun isFresh(fetchedAt: String, now: OffsetDateTime, ttlDays: Int = TTL_DAYS): Boolean {
        val at = runCatching { OffsetDateTime.parse(fetchedAt.trim()) }.getOrNull() ?: return false
        if (at.isAfter(now.plusDays(1))) return false   // 服务器/设备时钟错乱写进来的未来时间，不认
        return at.plusDays(ttlDays.toLong()).isAfter(now)
    }

    fun toJson(info: FacultyInfo): String = json.encodeToString(FacultyInfo.serializer(), info)

    fun fromJson(text: String): FacultyInfo? =
        runCatching { json.decodeFromString(FacultyInfo.serializer(), text) }.getOrNull()

    /** 一条缓存的体积元信息（淘汰策略的输入） */
    data class Entry(val key: String, val bytes: Long, val fetchedAt: String)

    /**
     * 该淘汰哪些（按 `fetchedAt` 最旧优先），保证「条数 ≤ 500 且总量 ≤ 5MB」。
     *
     * 纯函数：喂进去一批文件元信息，返回要删的键，写盘的那一层照着删。
     */
    fun evictionPlan(
        entries: List<Entry>,
        maxEntries: Int = MAX_ENTRIES,
        maxBytes: Long = MAX_BYTES,
    ): List<String> {
        if (entries.isEmpty()) return emptyList()
        val oldestFirst = entries.sortedWith(
            compareBy({ runCatching { OffsetDateTime.parse(it.fetchedAt.trim()) }.getOrNull() }, { it.key }),
        )
        val doomed = mutableListOf<String>()
        var count = entries.size
        var bytes = entries.sumOf { it.bytes }
        for (e in oldestFirst) {
            if (count <= maxEntries && bytes <= maxBytes) break
            doomed += e.key
            count--
            bytes -= e.bytes
        }
        return doomed
    }
}

/**
 * 教师公开信息的磁盘缓存：`filesDir/faculty/<键>.json`，一条一个文件、原子写。
 *
 * 与 `data/feed/` 的正文缓存**故意不合并**：资讯正文永久有效无所谓，
 * 教师资料要 TTL + 手动刷新，混在一起会让失效策略互相污染（票 05 §5）。
 */
class FacultyStore(ctx: Context) {

    private val dir: File = File(ctx.filesDir, "faculty")

    fun read(key: String): FacultyInfo? {
        if (key.isBlank()) return null
        val f = File(dir, "$key.json")
        if (!f.isFile) return null
        return runCatching { FacultyCache.fromJson(f.readText(Charsets.UTF_8)) }.getOrNull()
    }

    /** 原子写：先写 `.tmp` 再改名，避免掉电/进程被杀留下半个 JSON */
    fun write(info: FacultyInfo): Boolean {
        if (info.key.isBlank()) return false
        return runCatching {
            dir.mkdirs()
            val target = File(dir, "${info.key}.json")
            val tmp = File(dir, "${info.key}.json.tmp")
            tmp.writeText(FacultyCache.toJson(info), Charsets.UTF_8)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }.getOrDefault(false)
    }

    /** 超上限时按「最旧」淘汰（近似的 LRU） */
    fun trim() {
        runCatching {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()
            if (files.isEmpty()) return
            val entries = files.map { f ->
                val info = runCatching { FacultyCache.fromJson(f.readText(Charsets.UTF_8)) }.getOrNull()
                FacultyCache.Entry(f.name.removeSuffix(".json"), f.length(), info?.fetchedAt.orEmpty())
            }
            FacultyCache.evictionPlan(entries).forEach { key ->
                runCatching { File(dir, "$key.json").delete() }
            }
        }
    }

    /** 「清除所有数据」要连带清掉这里（票 05 §5） */
    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    fun count(): Int = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
}
