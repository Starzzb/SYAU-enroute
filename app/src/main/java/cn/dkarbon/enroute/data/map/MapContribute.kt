package cn.dkarbon.enroute.data.map

import java.net.URI
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 地图共建：**上传补丁的纯逻辑**（票 13 阶段 B）。
 *
 * 这一层不碰网络、不碰磁盘、不碰 Android，所以「传什么、不传什么」能被单测钉死 ——
 * 隐私红线不该靠人工 review 保证。
 *
 * ## 与服务器实现对齐（**以 `tools/map/server.js` 为准，不是文档**）
 * | 规则 | 代码实际 | 本层怎么做 |
 * |---|---|---|
 * | 端点 | `POST /map/api/patches`，需 `X-Api-Key`（配了才校验） | [MapUploader] |
 * | `mapVersion` | `^\d{4}\.\d{1,2}\.\d{1,2}(-\d+)?$` | [isValidMapVersion] 先本地挡住 |
 * | `featureId` | **`^f\d{1,6}$`（不接受 `f0_1` 这种带下标的）** | [featureIdError] |
 * | `field` | 非空、≤20 字 | [fieldError] |
 * | `value` | ≤200 字 | 截断并记账 |
 * | 单次 | ≤200 条、body ≤256KB | [MAX_PATCHES] / [MAX_BODY_BYTES] |
 * | 响应 | `{accepted, rejected, reasons:[{featureId, field, reasons:[…]}], keys}` | [MapPatchResponse] |
 * | 拉增量 | **没有 `GET /map/api/patches`**（只有 admin 的 snapshot） | 本层不做下行 |
 *
 * ## 隐私：客户端**发什么**
 * 服务器愿意收 `client.sid`（学号）与 `client.did`，并只落 HMAC/AES 派生值。
 * 但我们**不发送任何身份**，只发一个本地随机 UUID 冒充 `did`：
 * - `sid`：**永不发送**（App 里有登录态，但补丁不需要它，少传一项就少一分风险）
 * - `model`：**永不发送**（服务器是**明文落盘** `String(client.model).slice(0,40)`）
 * - `did`：本地随机 UUID，**不是**硬件标识（不是 ANDROID_ID/IMEI/序列号），设置页可重置
 * - 不发定位、不发时间以外的任何环境信息
 *
 * 这样服务端算出来的仍然是稳定的 `ch`（`did` 是稳定的），限流与归并有效；
 * 磁盘上只有 `ch`（HMAC）、`did_h`（HMAC 前 16 位）、`sid_enc=null`、`model=""`。
 */
object MapContribute {

    /** 单次上限（与服务端一致；超了自己分批，别让服务端悄悄截断） */
    const val MAX_PATCHES = 200
    const val MAX_BODY_BYTES = 256 * 1024
    const val MAX_VALUE = 200
    const val MAX_FIELD = 20

    /** 与服务端同一条正则：`f` + 1..6 位数字 */
    private val FID_RE = Regex("""^f\d{1,6}$""")
    private val MV_RE = Regex("""^\d{4}\.\d{1,2}\.\d{1,2}(-\d+)?$""")

    fun isValidMapVersion(v: String): Boolean = MV_RE.matches(v.trim())

    /** 不合法的 featureId 返回人话原因；合法返回 null */
    fun featureIdError(fid: String): String? {
        val f = fid.trim()
        if (f.isEmpty()) return "缺地块 id"
        if (!FID_RE.matches(f)) {
            return if (f.contains("_")) {
                "地块 id「$f」是旧命名（带下标的兜底 id），服务端不接受；请用 UID 重新出图"
            } else {
                "地块 id「$f」格式不对（服务端只认 f1…f999999）"
            }
        }
        return null
    }

    fun fieldError(field: String): String? {
        val f = field.trim()
        if (f.isEmpty()) return "字段名为空"
        if (f.length > MAX_FIELD) return "字段名超过 $MAX_FIELD 字"
        if (f in MapEdits.RESERVED) return "「$f」是保留字段名，不能作为自定义字段上传"
        return null
    }

    // ── 时间戳 ───────────────────────────────────────────

    private val TS_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** 服务端按字符串比较 ts，所以统一用**带时区**的 ISO8601（本机 +08:00） */
    fun nowTs(): String = OffsetDateTime.now().format(TS_FMT)

    // ── 一条补丁 ─────────────────────────────────────────

    /** 一条待上传补丁（字段名与服务器记录对齐） */
    data class Patch(
        val featureId: String,
        val field: String,
        val value: String,
        val op: String = "set",
        val ts: String,
    ) {
        /** 去重键：同一地块同一字段只保留最新一条（不参与 equals，故放 body 里） */
        val key: String = featureId + "|" + field
    }

    /** 从「用户的标注」生成补丁列表（只含有值/清值的格子，不含官方基线） */
    fun patchesFrom(
        edits: MapEdits,
        ts: String = nowTs(),
    ): List<Patch> {
        val out = ArrayList<Patch>()
        edits.features.forEach { (fid, e) ->
            if (e.name.isNotBlank()) out += Patch(fid, "name", e.name.trim(), "set", ts)
            if (e.kind.isNotBlank()) out += Patch(fid, "kind", e.kind.trim(), "set", ts)
            edits.fieldOrder.forEach { f ->
                val v = e.fields[f].orEmpty().trim()
                if (v.isNotBlank()) out += Patch(fid, f, v, "set", ts)
            }
        }
        // 同一格多条（不该发生，但别赌）：按 ts 取最后一条
        return out.groupBy { it.key }.map { (_, list) -> list.last() }
            .sortedWith(compareBy({ it.featureId }, { it.field }))
    }

    /**
     * 上传前**清理**：不合法的直接剔除并记账（不静默、也不让服务端替我们拒）。
     * 返回 (可上传, 预览提示行, 被剔除原因)
     */
    data class Sanitized(
        val ok: List<Patch>,
        val dropped: List<Pair<Patch, String>>,
    )

    fun sanitize(patches: List<Patch>, mapVersion: String): Sanitized {
        val mvErr = if (isValidMapVersion(mapVersion)) null else
            "地图版本「$mapVersion」格式不对（要 2026.11.14-1 这种），服务端会整批拒绝"
        val ok = ArrayList<Patch>()
        val dropped = ArrayList<Pair<Patch, String>>()
        patches.forEach { p ->
            val err = when {
                mvErr != null -> mvErr
                else -> featureIdError(p.featureId) ?: fieldError(p.field)
            }
            if (err != null) {
                dropped += p to err
                return@forEach
            }
            val v = if (p.value.length > MAX_VALUE) p.value.take(MAX_VALUE) else p.value
            ok += p.copy(value = v)
        }
        return Sanitized(ok, dropped)
    }

    /** 按 200 条/批切分（服务端超过就丢，我们主动切） */
    fun chunk(patches: List<Patch>): List<List<Patch>> =
        if (patches.isEmpty()) emptyList() else patches.chunked(MAX_PATCHES)

    // ── 请求体（**这是唯一决定"传什么"的地方**）────────────

    /**
     * 组装请求体。字段名严格照 `server.js`：
     * `{schema, mapVersion, client:{did, appVer}, patches:[{featureId, field, value, op, ts}]}`
     *
     * **刻意不出现的键**：`sid`（学号）、`userKey`（服务器不认这个名）、`model`（明文落盘）、
     * 以及任何位置/硬件标识。单测里逐条断言它们不存在。
     */
    fun body(
        mapVersion: String,
        anonymousKey: String,
        appVersion: String,
        patches: List<Patch>,
    ): String {
        val sb = StringBuilder()
        sb.append("{\"schema\":1")
        sb.append(",\"mapVersion\":").append(q(mapVersion))
        sb.append(",\"client\":{\"did\":").append(q(anonymousKey))
        if (appVersion.isNotBlank()) sb.append(",\"appVer\":").append(q(appVersion.take(20)))
        sb.append("}")
        sb.append(",\"patches\":[")
        patches.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append("{\"featureId\":").append(q(p.featureId))
            sb.append(",\"field\":").append(q(p.field))
            sb.append(",\"value\":").append(q(p.value))
            sb.append(",\"op\":").append(q(p.op))
            sb.append(",\"ts\":").append(q(p.ts))
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    /** 上传前的**预览**（给用户看「要传什么」）：一行一条，人话 */
    fun preview(patchCount: Int, featureCount: Int, fields: List<String>): String =
        buildString {
            append("将上传 $patchCount 条属性（涉及 $featureCount 个地块）")
            if (fields.isNotEmpty()) append("：").append(fields.joinToString("、"))
        }

    /** 新的匿名设备键（随机 UUID，可重置；**不是**硬件号） */
    fun newAnonymousKey(): String = "anon-" + UUID.randomUUID().toString()

    // ── 响应 ─────────────────────────────────────────────

    /**
     * `{accepted, rejected, reasons:[{featureId, field, reasons:[…]}], keys}`
     *
     * 注意 `reasons` 是**数组**（文档里写成 map 是错的，以 server.js 为准）。
     */
    data class MapPatchResponse(
        val accepted: Int,
        val rejected: Int,
        val reasons: List<Rejected>,
        val keys: Int,
    ) {
        data class Rejected(val featureId: String, val field: String, val reasons: List<String>)

        val isRateLimited: Boolean get() = accepted == 0 && rejected == 0 && keys == 0

        /** 人话总结（UI 直接显示） */
        fun summary(): String = when {
            accepted > 0 && rejected == 0 -> "已上传 $accepted 条，服务器已收下"
            accepted > 0 -> "已上传 $accepted 条，另有 $rejected 条被拒：${reasons.firstOrNull()?.let { "${it.field}（${it.reasons.joinToString("、")}）" } ?: "格式问题"}"
            else -> "服务器没有接收任何一条：${reasons.firstOrNull()?.let { "${it.field}（${it.reasons.joinToString("、")}）" } ?: "格式不合规"}"
        }
    }

    private val json = kotlinx.serialization.json.Json { isLenient = true }

    fun parseResponse(text: String): MapPatchResponse? {
        val o = runCatching {
            json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return null

        fun prim(key: String): String =
            (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()

        // 注意：服务端的 `reasons` 是**数组**（文档写成 map 是错的，以 server.js 为准）
        val reasonsArr = o["reasons"] as? kotlinx.serialization.json.JsonArray ?: return null
        val reasons = reasonsArr
            .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .map { r ->
                fun rs(key: String): String =
                    (r[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                MapPatchResponse.Rejected(
                    featureId = rs("featureId"),
                    field = rs("field"),
                    reasons = (r["reasons"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        .orEmpty(),
                )
            }
        return MapPatchResponse(
            accepted = prim("accepted").toIntOrNull() ?: 0,
            rejected = prim("rejected").toIntOrNull() ?: 0,
            reasons = reasons,
            keys = prim("keys").toIntOrNull() ?: 0,
        )
    }

    // ── 退避 ─────────────────────────────────────────────

    /**
     * 失败退避：1 分钟 → 5 → 15 → 60 → 180（封顶）。**不入死循环**，
     * 也不做「每分钟重试」那种烧电行为。
     *
     * @param failures 连续失败次数（0 = 下一次立刻可试）
     */
    fun backoffMillis(failures: Int): Long {
        val table = longArrayOf(0L, 60_000L, 5 * 60_000L, 15 * 60_000L, 60 * 60_000L, 3 * 60 * 60_000L)
        return table[failures.coerceIn(0, table.size - 1)]
    }

    fun shouldTryNow(nowMs: Long, nextTryAtMs: Long): Boolean = nowMs >= nextTryAtMs

    // ── 杂项 ─────────────────────────────────────────────

    /** 有没有东西可传（没有就别打扰用户） */
    fun hasAnything(patches: List<Patch>): Boolean = patches.isNotEmpty()

    /** 主机名（错误提示里用，不打印完整 URL —— 里面没有秘密，但少打点少泄露） */
    fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")

    private fun q(s: String): String {
        val sb = StringBuilder("\"")
        s.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
