package cn.dkarbon.enroute.data.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * **共享数据集**（票 22）：服务端聚合后的权威数据 + 三档来源优先级。
 *
 * ## 三档来源（主人接受"服务器为基准"，但本地刚填的不能被吃掉）
 * ```
 * ① 待上传  PENDING  —— 本机填了、还没生效到服务器（值 ≠ 服务器值）
 * ② 共建    SHARED   —— 服务器共享数据（含「你上传成功后的那条」）
 * ③ 内置    BUILTIN  —— 安装包里的 asset 基线
 * ```
 * 「以服务器为基准」成立：①只在你自己的机器上、标「待上传」，一旦上传成功，
 * 同一次拉取后来源就变成 ②（值相等即视为已生效，见 [rows]）。
 *
 * ## 为什么纯逻辑
 * 合并优先级算错会让「别人的数据覆盖我的」「我填的丢了」这类事故静默发生，
 * 而它们在界面上看起来都"很正常"。所以这一层不碰网络/磁盘，全部可单测。
 */
object MapDataset {

    /** 来源（界面必须显示，票 22 红线） */
    enum class Source {
        /** 本地刚填、还没生效到服务器 */
        PENDING,
        /** 服务器共享数据 */
        SHARED,
        /** 安装包内置的官方基线 */
        BUILTIN,
    }

    data class Deleted(val featureId: String, val field: String)

    /**
     * 一份数据集。全量与增量用同一个形状 —— 增量时 [features] 只含被碰过的格，
     * [deleted] 列出要移除的格。
     */
    data class Dataset(
        val schema: Int = 1,
        val mapVersion: String = "",
        /** 服务器数据版本；**唯一的「服务器变了没有」判据** */
        val rev: Long = 0,
        val updatedAt: String = "",
        val fields: List<String> = emptyList(),
        val features: Map<String, Map<String, String>> = emptyMap(),
        val deleted: List<Deleted> = emptyList(),
        /** 每格生效那条的 rev（排查用，界面不显示） */
        val fieldRev: Map<String, Map<String, Long>> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = features.isEmpty() && deleted.isEmpty()

        fun valueOf(featureId: String, field: String): String? =
            features[featureId]?.get(field)

        /** 该面在服务器上有没有任何字段 */
        fun hasFeature(featureId: String): Boolean = features[featureId]?.isNotEmpty() == true
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析 dataset 响应。**宽容但不瞎猜**：
     * - 缺 `rev` → 返回 null（没有 rev 就没法判断增量，宁可当作失败重试）
     * - `deleted` 兼容两种形状：`[{featureId, field}]` 或 `["f19|层数"]`
     */
    fun parse(text: String): Dataset? {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val rev = (o["rev"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null

        val features = LinkedHashMap<String, Map<String, String>>()
        (o["features"] as? JsonObject)?.forEach { (fid, v) ->
            val fo = v as? JsonObject ?: return@forEach
            val m = LinkedHashMap<String, String>()
            fo.forEach { (k, pv) ->
                if (pv is JsonPrimitive) m[k] = pv.content
            }
            if (m.isNotEmpty()) features[fid] = m
        }

        val fields = (o["fields"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        val deleted = (o["deleted"] as? JsonArray)?.mapNotNull { el ->
            when (el) {
                is JsonObject -> {
                    val fid = (el["featureId"] as? JsonPrimitive)?.content.orEmpty()
                    val f = (el["field"] as? JsonPrimitive)?.content.orEmpty()
                    if (fid.isBlank() || f.isBlank()) null else Deleted(fid, f)
                }
                is JsonPrimitive -> {
                    val s = el.content
                    val i = s.indexOf('|')
                    if (i <= 0 || i >= s.length - 1) null else Deleted(s.substring(0, i), s.substring(i + 1))
                }
                else -> null
            }
        }.orEmpty()

        val fieldRev = LinkedHashMap<String, Map<String, Long>>()
        (o["fieldRev"] as? JsonObject)?.forEach { (fid, v) ->
            val fo = v as? JsonObject ?: return@forEach
            val m = LinkedHashMap<String, Long>()
            fo.forEach { (k, rv) ->
                (rv as? JsonPrimitive)?.content?.toLongOrNull()?.let { m[k] = it }
            }
            if (m.isNotEmpty()) fieldRev[fid] = m
        }

        return Dataset(
            schema = (o["schema"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1,
            mapVersion = (o["mapVersion"] as? JsonPrimitive)?.content.orEmpty(),
            rev = rev,
            updatedAt = (o["updatedAt"] as? JsonPrimitive)?.content.orEmpty(),
            fields = fields,
            features = features,
            deleted = deleted,
            fieldRev = fieldRev,
        )
    }

    /**
     * 增量合并：把 [incoming]（可能只含被碰过的格）叠到 [base] 上。
     *
     * - `rev` 只增不减（乱序响应不会让本地版本倒退）
     * - 同名字段直接替换（服务器为基准）
     * - [Dataset.deleted] 里的格从累积结果里删掉
     * - 全量响应（`incoming.features` 覆盖了 base 的所有 fid 时）自然收敛，不需要特判
     */
    fun merge(base: Dataset?, incoming: Dataset): Dataset {
        if (base == null) {
            return incoming.copy(deleted = emptyList())   // 首次全量：删除列表已体现在 features 里
        }
        val acc = LinkedHashMap<String, MutableMap<String, String>>()
        base.features.forEach { (fid, m) -> acc[fid] = LinkedHashMap(m) }
        incoming.features.forEach { (fid, m) ->
            acc.getOrPut(fid) { LinkedHashMap() }.putAll(m)
        }
        incoming.deleted.forEach { d ->
            val fm = acc[d.featureId] ?: return@forEach
            fm.remove(d.field)
            if (fm.isEmpty()) acc.remove(d.featureId)
        }
        val fields = LinkedHashSet<String>()
        fields.addAll(base.fields)
        fields.addAll(incoming.fields)
        // 被删掉的字段名也去掉（除非别的面还在用）
        val stillUsed = acc.values.flatMap { it.keys }.toSet()
        return Dataset(
            schema = incoming.schema,
            mapVersion = incoming.mapVersion.ifBlank { base.mapVersion },
            rev = maxOf(base.rev, incoming.rev),
            updatedAt = incoming.updatedAt.ifBlank { base.updatedAt },
            fields = fields.filter { it in stillUsed }.sorted(),
            features = acc,
            deleted = emptyList(),
            fieldRev = base.fieldRev + incoming.fieldRev,
        )
    }

    /**
     * 拉取后「变了多少格」：新增 / 改值 / 被删的格都算一格。
     *
     * [base] 为 null（首次全量）时按 [after] 的格数计。给「已更新 N 条」提示用 ——
     * 这个数字必须可信，宁可少算一格，不能把「没变」说成「更新了」。
     */
    fun updatedCount(base: Dataset?, after: Dataset): Int {
        if (base == null) return after.features.values.sumOf { it.size }
        var n = 0
        after.features.forEach { (fid, m) ->
            m.forEach { (field, v) ->
                if (base.features[fid]?.get(field) != v) n++
            }
        }
        base.features.forEach { (fid, m) ->
            m.forEach { (field, _) ->
                if (after.features[fid]?.get(field) == null) n++
            }
        }
        return n
    }

    /** 需不需要真的发请求：没变就不发（票 22「只有服务器更新字段才拉取」） */
    fun shouldPull(nowMs: Long, lastCheckMs: Long, windowMs: Long = FIVE_MIN): Boolean {
        if (lastCheckMs <= 0L) return true
        if (nowMs < lastCheckMs) return true          // 时钟回拨：给一次机会
        return nowMs - lastCheckMs >= windowMs
    }

    const val FIVE_MIN = 5L * 60L * 1000L

    /**
     * 序列化成**和服务器同样的形状**（磁盘缓存用）。
     *
     * 放在这里而不是 Store 里：`parse(encode(x)) == x` 这条往返必须能被单测锁住 ——
     * 缓存写坏了的表现是「地图上别人的数据突然消失」，很难在线索里看出来。
     */
    fun encode(ds: Dataset): String {
        val sb = StringBuilder()
        sb.append("{\"schema\":").append(ds.schema)
        sb.append(",\"mapVersion\":").append(q(ds.mapVersion))
        sb.append(",\"rev\":").append(ds.rev)
        sb.append(",\"updatedAt\":").append(q(ds.updatedAt))
        sb.append(",\"fields\":[")
        ds.fields.forEachIndexed { i, f -> if (i > 0) sb.append(','); sb.append(q(f)) }
        sb.append("],\"features\":{")
        ds.features.entries.forEachIndexed { i, (fid, m) ->
            if (i > 0) sb.append(',')
            sb.append(q(fid)).append(":{")
            m.entries.forEachIndexed { j, (k, v) ->
                if (j > 0) sb.append(',')
                sb.append(q(k)).append(':').append(q(v))
            }
            sb.append('}')
        }
        sb.append("},\"deleted\":[]}")
        return sb.toString()
    }

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

    // ── 三档来源 ─────────────────────────────────────────

    /**
     * 属性面板的一行：显示值 + 来源。
     *
     * @param editable 只有「待上传」可以编辑（共建/内置是只读层，
     *   要改就「＋ 添加字段」用同名列覆盖 —— 这样归属永远清楚）
     */
    data class Row(
        val name: String,
        val value: String,
        val source: Source,
        val editable: Boolean,
    )

    /**
     * 生成属性面板的行（**优先级就在这里**，改这一段之前先看单测）。
     *
     * 判定顺序（逐字段）：
     * 1. 本地填了值：
     *    - 与服务器值相同 → 已生效 → [Source.SHARED]（上传成功后的状态）
     *    - 不同（服务器没有或值不同）→ 还没生效 → [Source.PENDING]，**可编辑**
     * 2. 服务器有值 → [Source.SHARED]
     * 3. 内置 asset 有值（[MapFeature.props]）→ [Source.BUILTIN]
     * 4. **用户自己加的字段**（在 [MapEdits.fieldOrder] 里）即使还空着也保留一行，
     *    否则「＋ 添加字段」之后没有输入框可填 —— 这是实打实的功能缺陷
     * 5. 其余没值的（只被声明/只在别的面有值）→ 不占行
     *
     * @param declared 官方/共建字段名清单（`manifest.customFields` + 数据集里的 `fields`）
     */
    fun rows(
        feature: MapFeature,
        edits: MapEdits,
        dataset: Dataset?,
        declared: List<String>,
    ): List<Row> {
        val mine = edits.features[feature.id]
        val order = LinkedHashSet<String>()
        edits.fieldOrder.forEach { order += it }                       // 用户格子永远在
        declared.forEach { order += it }
        dataset?.features?.get(feature.id)?.keys?.forEach { order += it }

        val out = ArrayList<Row>(order.size)
        order.forEach { field ->
            val localValue = mine?.fields?.get(field).orEmpty().trim()
            val serverValue = dataset?.valueOf(feature.id, field).orEmpty().trim()
            val builtinValue = feature.props[field].orEmpty().trim()
            val isUserSlot = field in edits.fieldOrder

            val row = when {
                localValue.isNotBlank() && localValue == serverValue ->
                    Row(field, localValue, Source.SHARED, editable = false)   // 已生效
                localValue.isNotBlank() ->
                    Row(field, localValue, Source.PENDING, editable = true)   // 等上传
                serverValue.isNotBlank() ->
                    Row(field, serverValue, Source.SHARED, editable = false)
                builtinValue.isNotBlank() ->
                    Row(field, builtinValue, Source.BUILTIN, editable = false)
                isUserSlot ->
                    Row(field, "", Source.PENDING, editable = true)           // 空的自有格子：留着让你填
                else -> null
            }
            row?.let { out += it }
        }
        return out
    }

    /** 来源 → 中文标记（界面直接显示，**不做省略**） */
    fun label(source: Source): String = when (source) {
        Source.PENDING -> "待上传"
        Source.SHARED -> "共建"
        Source.BUILTIN -> "内置"
    }

    /** 来源 → 一句话解释（长按/详情用） */
    fun explain(source: Source): String = when (source) {
        Source.PENDING -> "你填的，还没生效到服务器"
        Source.SHARED -> "服务器共享数据（含审核合并结果）"
        Source.BUILTIN -> "安装包内置的基线，服务器有同名字段时以服务器为准"
    }
}
