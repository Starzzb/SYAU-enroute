package cn.dkarbon.enroute.data.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 用户标注（票 12 §7 —— **本票的核心功能**）。
 *
 * 主人原话：「支持用户点击添加字段图层所属的属性和添加字段」。
 * 于是：
 * - 固定字段：`名称`（文本）、`类别`（从 `manifest.kindLabels` 里选）；
 * - 动态字段：用户自己加（`层数` / `所属单位` / …），**字段名全局共享** ——
 *   一个要素加了「层数」，所有要素都有这一列（别的要素值为空）；
 * - 落盘 `filesDir/map/edits.json`，**绝不改 assets**；
 * - 导出合并结果（GeoJSON + CSV），否则用户手填的字就被锁在手机里。
 *
 * 全部是纯函数 / 纯数据（IO 在 [MapEditsStore]），单测直接打。
 */
data class MapEdits(
    val schema: Int = SCHEMA,
    /** 这批标注是在哪版地图上做的（换图后用于「按 id 合并」的说明） */
    val mapVersion: String = "",
    /** 自定义字段名，**全局有序**（添加顺序） */
    val fieldOrder: List<String> = emptyList(),
    /** 要素 id → 该要素的标注（`name` / `kind` + 自定义字段平铺） */
    val features: Map<String, MapEdit> = emptyMap(),
) {
    val isEmpty: Boolean get() = features.isEmpty() && fieldOrder.isEmpty()

    /** 这条要素有没有任何标注 */
    fun editOf(id: String): MapEdit? = features[id]

    companion object {
        const val SCHEMA = 1
        /** 固定字段的保留名（自定义字段不能叫这些，否则会和固定字段打架） */
        val RESERVED = listOf("name", "kind", "id", "colorGroup", "color", "areaPx")
    }
}

/** 单个要素的标注：固定字段 + 自定义字段（`fields` 的键必在 [MapEdits.fieldOrder] 里） */
data class MapEdit(
    val name: String = "",
    val kind: String = "",
    val fields: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = name.isBlank() && kind.isBlank() && fields.values.all { it.isBlank() }
}

/**
 * 标注的读写（**契约里的 JSON 形态**）：
 *
 * ```jsonc
 * {"schema":1,"mapVersion":"2026.11.14-1",
 *  "fieldOrder":["层数","所属单位"],
 *  "features":{"f19":{"name":"九州体育场","kind":"sport","层数":"3","所属单位":"体育部"}}}
 * ```
 *
 * 自定义字段是**平铺**在要素对象里的（不套一层 `fields`），所以在 [MapEdits.RESERVED] 之外的键都算自定义字段。
 */
object MapEditsCodec {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

    fun toJson(edits: MapEdits): String {
        val root = LinkedHashMap<String, JsonElement>()
        root["schema"] = JsonPrimitive(edits.schema)
        root["mapVersion"] = JsonPrimitive(edits.mapVersion)
        root["fieldOrder"] = JsonArray(edits.fieldOrder.map { JsonPrimitive(it) })
        val feats = LinkedHashMap<String, JsonElement>()
        edits.features.forEach { (id, e) ->
            val obj = LinkedHashMap<String, JsonElement>()
            if (e.name.isNotBlank()) obj["name"] = JsonPrimitive(e.name)
            if (e.kind.isNotBlank()) obj["kind"] = JsonPrimitive(e.kind)
            // 只为**全局字段表里存在的**字段写值，避免写出孤儿列
            edits.fieldOrder.forEach { f ->
                e.fields[f]?.takeIf { it.isNotBlank() }?.let { obj[f] = JsonPrimitive(it) }
            }
            if (obj.isNotEmpty()) feats[id] = JsonObject(obj)
        }
        root["features"] = JsonObject(feats)
        return JsonObject(root).toString()
    }

    fun fromJson(text: String): MapEdits? {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val schema = (o["schema"] as? JsonPrimitive)?.content?.toIntOrNull() ?: MapEdits.SCHEMA
        if (schema != MapEdits.SCHEMA) return null
        val fieldOrder = (o["fieldOrder"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()
        val features = LinkedHashMap<String, MapEdit>()
        (o["features"] as? JsonObject)?.forEach { (id, v) ->
            val fo = v as? JsonObject ?: return@forEach
            val fields = LinkedHashMap<String, String>()
            fo.forEach { (k, fv) ->
                if (k in MapEdits.RESERVED) return@forEach
                val s = (fv as? JsonPrimitive)?.content?.trim().orEmpty()
                if (s.isNotBlank()) fields[k] = s
            }
            features[id] = MapEdit(
                name = (fo["name"] as? JsonPrimitive)?.content?.trim().orEmpty(),
                kind = (fo["kind"] as? JsonPrimitive)?.content?.trim().orEmpty(),
                fields = fields,
            )
        }
        // 字段表兜底：老文件里出现了字段表没有的列，也补进来（不丢用户的数据）
        val extra = features.values.flatMap { it.fields.keys }.distinct().filter { it !in fieldOrder }
        return MapEdits(
            schema = schema,
            mapVersion = (o["mapVersion"] as? JsonPrimitive)?.content?.trim().orEmpty(),
            fieldOrder = fieldOrder + extra,
            features = features,
        )
    }
}

/** 标注的增删改与合并（纯函数） */
object MapEditsOps {

    /** 加一个自定义字段（全局）：名字去空白、去重、不能撞保留名 */
    fun addField(edits: MapEdits, name: String): MapEdits {
        val n = name.trim().take(20)
        if (n.isBlank()) return edits
        if (n in MapEdits.RESERVED) return edits
        if (n in edits.fieldOrder) return edits
        return edits.copy(fieldOrder = edits.fieldOrder + n)
    }

    /** 删一个自定义字段（连带删所有要素该列的值） */
    fun removeField(edits: MapEdits, name: String): MapEdits {
        if (name !in edits.fieldOrder) return edits
        return edits.copy(
            fieldOrder = edits.fieldOrder.filter { it != name },
            features = edits.features.mapValues { (_, e) -> e.copy(fields = e.fields - name) }
                .filterValues { !it.isEmpty },
        )
    }

    fun setName(edits: MapEdits, id: String, name: String): MapEdits =
        put(edits, id) { it.copy(name = name.trim()) }

    fun setKind(edits: MapEdits, id: String, kind: String): MapEdits =
        put(edits, id) { it.copy(kind = kind.trim()) }

    /** 设某要素某字段的值；空串 = 清空这一条（字段本身保留） */
    fun setField(edits: MapEdits, id: String, field: String, value: String): MapEdits {
        if (field !in edits.fieldOrder) return edits
        return put(edits, id) {
            val v = value.trim()
            it.copy(fields = if (v.isBlank()) it.fields - field else it.fields + (field to v))
        }
    }

    private fun put(edits: MapEdits, id: String, f: (MapEdit) -> MapEdit): MapEdits {
        if (id.isBlank()) return edits
        val cur = edits.features[id] ?: MapEdit()
        val next = f(cur)
        val features = if (next.isEmpty) edits.features - id else edits.features + (id to next)
        return edits.copy(features = features)
    }

    /** 换图（版本变了）：标注按 id 保留，只更新「这批标注基于哪版」 */
    fun withVersion(edits: MapEdits, version: String): MapEdits =
        if (edits.mapVersion == version) edits else edits.copy(mapVersion = version)

    /**
     * 合并后的「要素 + 标注」视图（界面与导出都用它）。
     *
     * 优先级：**edits 覆盖 asset** 的同名字段（`name` / `kind`）；自定义字段只来自 edits。
     */
    data class Row(
        val feature: MapFeature,
        val name: String,
        val kind: String,
        /** 自定义字段（按 [MapEdits.fieldOrder] 顺序取值，空值也保留位置 = ""） */
        val fields: List<Pair<String, String>>,
    )

    fun merged(features: List<MapFeature>, edits: MapEdits): List<Row> = features.map { f ->
        val e = edits.features[f.id]
        Row(
            feature = f,
            name = e?.name?.takeIf { it.isNotBlank() } ?: f.name,
            kind = e?.kind?.takeIf { it.isNotBlank() } ?: f.kind,
            fields = edits.fieldOrder.map { it to (e?.fields?.get(it).orEmpty()) },
        )
    }

    /** 合并后的一条（界面信息卡用）；控件要给未标注的要素也能编辑，所以缺省为空 */
    fun rowOf(feature: MapFeature, edits: MapEdits): Row {
        val e = edits.features[feature.id]
        return Row(
            feature = feature,
            name = e?.name ?: "",
            kind = e?.kind ?: feature.kind,
            fields = edits.fieldOrder.map { it to (e?.fields?.get(it).orEmpty()) },
        )
    }

    /**
     * **地图版本变了**：edits 里的 id 在新版 GeoJSON 里找不到 → 孤立标注。
     * 这些**不静默丢弃**，界面顶部提示 N 条，且它们继续留在 edits.json 里等用户处理。
     */
    fun orphans(features: List<MapFeature>, edits: MapEdits): List<String> {
        val ids = features.map { it.id }.toSet()
        return edits.features.keys.filter { it !in ids }.sorted()
    }
}

/**
 * 导出（票 12 §7「必须做」）：asset + edits 合并 → GeoJSON / CSV。
 *
 * CSV 带 **UTF-8 BOM**，否则 Excel 打开中文是乱码。
 */
object MapExport {

    const val BOM = "\uFEFF"

    /** 合并后的 GeoJSON（`campus_annotated.geojson`） */
    fun geoJson(features: List<MapFeature>, edits: MapEdits): String {
        val rows = MapEditsOps.merged(features, edits)
        val sb = StringBuilder()
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
        rows.forEachIndexed { i, row ->
            if (i > 0) sb.append(',')
            val f = row.feature
            val props = LinkedHashMap<String, String>()
            props["id"] = f.id
            props["name"] = row.name
            props["kind"] = row.kind
            if (f.colorGroup > 0) props["colorGroup"] = f.colorGroup.toString()
            if (f.color.isNotBlank()) props["color"] = f.color
            if (f.areaPx > 0.0) props["areaPx"] = num(f.areaPx)
            row.fields.filter { it.second.isNotBlank() }.forEach { (k, v) -> props[k] = v }
            sb.append("{\"type\":\"Feature\",\"id\":").append(quote(f.id))
            sb.append(",\"properties\":{")
            props.entries.forEachIndexed { j, (k, v) ->
                if (j > 0) sb.append(',')
                sb.append(quote(k)).append(':').append(quote(v))
            }
            sb.append("},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[")
            f.rings.forEachIndexed { ri, ring ->
                if (ri > 0) sb.append(',')
                sb.append('[')
                ring.forEachIndexed { pi, p ->
                    if (pi > 0) sb.append(',')
                    sb.append('[').append(num(p.x)).append(',').append(num(p.y)).append(']')
                }
                sb.append(']')
            }
            sb.append("]}}")
        }
        sb.append("]}")
        return sb.toString()
    }

    /** 合并后的 CSV（带 BOM）：`id,name,kind,<自定义字段…>` */
    fun csv(features: List<MapFeature>, edits: MapEdits): String {
        val rows = MapEditsOps.merged(features, edits)
        val sb = StringBuilder(BOM)
        sb.append(listOf("id", "name", "kind") .plus(edits.fieldOrder).joinToString(",") { esc(it) }).append("\r\n")
        rows.forEach { row ->
            val cells = listOf(row.feature.id, row.name, row.kind)
                .plus(row.fields.map { it.second })
            sb.append(cells.joinToString(",") { esc(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** 文件名：`campus_annotated_<版本>_<yyyyMMdd-HHmm>.<ext>` */
    fun fileName(version: String, stamp: String, ext: String): String {
        val v = version.trim().ifBlank { "unknown" }.replace(Regex("""[^0-9A-Za-z._\-]"""), "_")
        return "campus_annotated_${v}_$stamp.$ext"
    }

    private fun quote(s: String): String {
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

    /** 保留 3 位小数（坐标原始精度过高，会把导出文件撑大一倍） */
    private fun num(v: Double): String =
        BigDecimal(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** CSV 单元格转义：含逗号/引号/换行就加引号并把引号翻倍 */
    private fun esc(s: String): String {
        if (s.isBlank()) return ""
        val need = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (!need) s else "\"" + s.replace("\"", "\"\"") + "\""
    }
}
