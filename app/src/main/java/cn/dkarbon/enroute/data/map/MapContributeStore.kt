package cn.dkarbon.enroute.data.map

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * 地图共建的**本地状态与待传队列**（票 13 阶段 B）。
 *
 * 三样东西：
 * - 开关（**默认关**，用户主动开才上传）
 * - 匿名设备键（随机 UUID，可重置；**不是**硬件标识）
 * - 待传队列 `filesDir/map/pending.json`（失败后退避重试用，进程杀了也不丢）
 *
 * 为什么队列单独一个文件、而不是塞进 `edits.json`：`edits.json` 是**用户数据**（要导出、
 * 要合并、要保证不丢），队列是**传输中间态**（上传成功就该消失）。混在一起会让
 * 「导出的标注」里混进「还没传上去的东西」这种说不清的状态。
 */
class MapContributeStore(ctx: Context) {

    private val ctx = ctx.applicationContext
    private val prefs = this.ctx.getSharedPreferences("map_contribute", Context.MODE_PRIVATE)
    private val queueFile = File(this.ctx.filesDir, "map/pending.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    // ── 开关 ─────────────────────────────────────────────

    /** **默认关**（票 13 红线：用户主动开才上传） */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, on).apply()

    /** 用户是否已经看过「要传什么」的说明（首次开启时二次确认过一次） */
    fun hasAcknowledged(): Boolean = prefs.getBoolean(KEY_ACK, false)

    fun setAcknowledged(v: Boolean) = prefs.edit().putBoolean(KEY_ACK, v).apply()

    // ── 匿名键 ───────────────────────────────────────────

    /** 稳定匿名键；第一次取时随机生成。**不含任何硬件标识** */
    fun anonymousKey(): String {
        val cur = prefs.getString(KEY_ANON, null)
        if (!cur.isNullOrBlank()) return cur
        val fresh = MapContribute.newAnonymousKey()
        prefs.edit().putString(KEY_ANON, fresh).apply()
        return fresh
    }

    /** 重置：与历史数据脱钩（票 13 验收项）。`installId` 一并更换 —— 弹窗里承诺的是「重置后即更换」 */
    fun resetAnonymousKey(): String {
        val fresh = MapContribute.newAnonymousKey()
        prefs.edit().putString(KEY_ANON, fresh).putString(KEY_INSTALL, newInstallId()).apply()
        return fresh
    }

    /**
     * 安装级 UUID（票 22）：卸载重装才会变；「重置身份」也会换。
     * 与 `did` 分开存：`did` 是**可随时重置**的匿名身份，`installId` 是**这次安装**的标记。
     */
    fun installId(): String {
        val cur = prefs.getString(KEY_INSTALL, null)
        if (!cur.isNullOrBlank()) return cur
        val fresh = newInstallId()
        prefs.edit().putString(KEY_INSTALL, fresh).apply()
        return fresh
    }

    private fun newInstallId(): String = "install-" + java.util.UUID.randomUUID().toString()

    // ── 退避 ─────────────────────────────────────────────

    fun consecutiveFailures(): Int = prefs.getInt(KEY_FAILS, 0)

    fun nextTryAt(): Long = prefs.getLong(KEY_NEXT_TRY, 0L)

    fun noteSuccess() = prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_NEXT_TRY, 0L)
        .putLong(KEY_LAST_OK, System.currentTimeMillis()).apply()

    /** 记一次失败并算出下次可试时间（退避表见 [MapContribute.backoffMillis]） */
    fun noteFailure(nowMs: Long = System.currentTimeMillis()): Long {
        val n = prefs.getInt(KEY_FAILS, 0) + 1
        val next = nowMs + MapContribute.backoffMillis(n)
        prefs.edit().putInt(KEY_FAILS, n).putLong(KEY_NEXT_TRY, next).apply()
        return next
    }

    fun lastOkAt(): Long = prefs.getLong(KEY_LAST_OK, 0L)

    fun lastMessage(): String = prefs.getString(KEY_LAST_MSG, "").orEmpty()

    fun setLastMessage(msg: String) = prefs.edit().putString(KEY_LAST_MSG, msg.take(300)).apply()

    // ── 待传队列 ─────────────────────────────────────────

    fun pending(): List<MapContribute.Patch> = runCatching {
        if (!queueFile.exists() || queueFile.length() == 0L) return emptyList()
        val arr = json.parseToJsonElement(queueFile.readText()) as? JsonArray ?: return emptyList()
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content.orEmpty()
            val fid = s("featureId")
            val field = s("field")
            if (fid.isBlank() || field.isBlank()) return@mapNotNull null
            MapContribute.Patch(
                featureId = fid,
                field = field,
                value = s("value"),
                op = s("op").ifBlank { "set" },
                ts = s("ts"),
            )
        }
    }.getOrDefault(emptyList())

    fun savePending(list: List<MapContribute.Patch>) = runCatching {
        queueFile.parentFile?.mkdirs()
        val arr = JsonArray(
            list.map { p ->
                JsonObject(
                    linkedMapOf(
                        "featureId" to JsonPrimitive(p.featureId),
                        "field" to JsonPrimitive(p.field),
                        "value" to JsonPrimitive(p.value),
                        "op" to JsonPrimitive(p.op),
                        "ts" to JsonPrimitive(p.ts),
                    ),
                )
            },
        )
        val tmp = File(queueFile.parentFile, "pending.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(queueFile)
    }.isSuccess

    /** 入队 = 覆盖同格旧值（同一地块同一字段只留最新一条） */
    fun enqueue(patches: List<MapContribute.Patch>) {
        if (patches.isEmpty()) return
        val merged = LinkedHashMap<String, MapContribute.Patch>()
        pending().forEach { merged[it.key] = it }
        patches.forEach { merged[it.key] = it }
        savePending(merged.values.sortedWith(compareBy({ it.featureId }, { it.field })))
    }

    fun clearQueue() = runCatching { queueFile.delete() }.isSuccess

    fun pendingCount(): Int = pending().size

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ACK = "acknowledged"
        const val KEY_ANON = "anonKey"
        const val KEY_INSTALL = "installId"
        const val KEY_FAILS = "fails"
        const val KEY_NEXT_TRY = "nextTryAt"
        const val KEY_LAST_OK = "lastOkAt"
        const val KEY_LAST_MSG = "lastMsg"
    }
}
