package cn.dkarbon.enroute.data.map

import android.content.Context
import java.io.File

/**
 * 共享数据集的**本地缓存**（票 22）。
 *
 * 一个文件：`filesDir/map/server_data.json`（与 `edits.json` 分开，互不污染）
 * - 原子写：先写 `.tmp` 再 `rename` —— 杀进程不会留半个 JSON
 * - 缓存里有 `rev`，它就是「本地已经拉到哪个版本」的凭据
 * - 读失败（文件损坏/格式变了）一律当**没有缓存**，不抛异常：地图照常能看
 */
class MapDatasetStore(ctx: Context) {

    private val app = ctx.applicationContext
    private val file = File(app.filesDir, "map/server_data.json")
    private val prefs = app.getSharedPreferences("map_dataset", Context.MODE_PRIVATE)

    /** 上次真正发过请求的时间（5 分钟窗口用；失败也算，避免死循环重试） */
    fun lastCheckAt(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    fun noteCheck(nowMs: Long = System.currentTimeMillis()) =
        prefs.edit().putLong(KEY_LAST_CHECK, nowMs).apply()

    fun lastError(): String = prefs.getString(KEY_LAST_ERR, "").orEmpty()

    fun noteError(msg: String) = prefs.edit().putString(KEY_LAST_ERR, msg.take(200)).apply()

    fun clearError() = prefs.edit().remove(KEY_LAST_ERR).apply()

    /** 连续失败次数（用于退避；成功后清零） */
    fun failures(): Int = prefs.getInt(KEY_FAILS, 0)

    fun noteSuccess() = prefs.edit().putInt(KEY_FAILS, 0).apply()

    fun noteFailure(): Int {
        val n = failures() + 1
        prefs.edit().putInt(KEY_FAILS, n).apply()
        return n
    }

    /** 读缓存；没有或坏了都返回 null（不抛） */
    fun load(): MapDataset.Dataset? = runCatching {
        if (!file.exists() || file.length() == 0L) return null
        MapDataset.parse(file.readText())
    }.getOrNull()

    /** 原子写：成功返回 true（写失败不影响界面，下次同步再试） */
    fun save(ds: MapDataset.Dataset): Boolean = runCatching {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(MapDataset.encode(ds))
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }.getOrDefault(false)

    fun clear(): Boolean = runCatching { file.delete() }.getOrDefault(false)

    /** 只取版本号，不解整份数据（快速判「有没有变」） */
    fun cachedRev(): Long = load()?.rev ?: 0L

    /** 编码交给纯逻辑层（往返有单测）：见 [MapDataset.encode] */
    private companion object {
        const val KEY_LAST_CHECK = "lastCheckAt"
        const val KEY_LAST_ERR = "lastErr"
        const val KEY_FAILS = "fails"
    }
}
