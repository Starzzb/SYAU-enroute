package cn.dkarbon.enroute.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 历年课表的本地缓存：`filesDir/timetable/history/<planCode>.json`。
 *
 * 三条规矩（票 11）：
 * 1. **按学期一个文件**，存的是**解析后的 [Timetable]**（不是原始 payload）——
 *    打开历史学期时不必再跑一遍解析，也不受学校改字段影响（存的是我们自己的 schema）；
 * 2. **永久有效**：历史学期不会再变，所以不像教师官网资料那样设 TTL；
 *    但留手动刷新入口（学校可能补录/更正）；
 * 3. 「清除所有数据」连带清空这个目录（见 [DataWiper]）。
 *
 * 体积：单学期解析后几十 KB，5 个学期 < 500KB，不做淘汰。
 */
@Serializable
data class HistoryEntry(
    val planCode: String = "",
    /** 抓取时间，ISO-8601 带偏移 */
    val fetchedAt: String = "",
    val timetable: Timetable = Timetable(),
)

class HistoryTimetableStore(ctx: Context) {

    private val dir = File(File(ctx.applicationContext.filesDir, "timetable"), "history")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    fun read(planCode: String): HistoryEntry? {
        if (planCode.isBlank()) return null
        val f = File(dir, HistorySemesters.cacheFileName(planCode))
        if (!f.isFile) return null
        return runCatching { json.decodeFromString(HistoryEntry.serializer(), f.readText()) }.getOrNull()
            ?.takeIf { it.timetable.courses.isNotEmpty() || it.planCode.isNotBlank() }
    }

    /** 原子写：先 `.tmp` 再改名，避免掉电/被杀留下半个 JSON */
    fun write(entry: HistoryEntry): Boolean = runCatching {
        if (entry.planCode.isBlank()) return false
        dir.mkdirs()
        val target = File(dir, HistorySemesters.cacheFileName(entry.planCode))
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(json.encodeToString(HistoryEntry.serializer(), entry))
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }.getOrDefault(false)

    fun clear(): Boolean = runCatching {
        dir.listFiles()?.forEach { it.delete() }
        true
    }.getOrDefault(false)

    fun count(): Int = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0

    fun dirPath(): String = dir.absolutePath
}
