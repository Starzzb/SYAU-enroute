package cn.dkarbon.enroute.data.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 地图包解析（纯函数）：manifest 与 GeoJSON。
 *
 * 只用现有 `kotlinx.serialization`，**不引任何地图库**（票 12 的硬规矩）。
 * 解析失败一律返回 null，由 [MapPackageLoader] 决定「回退内置并提示」。
 */
object MapParse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 不进 [MapFeature.props] 的键：派生量 + 已有专门槽位的固定字段。
     * 其余一律当「官方基线标注」原样留下（票 13 B 路）。
     */
    private val DERIVED_OR_FIXED_KEYS = setOf(
        "id", "name", "kind", "code", "floors", "dept", "note",
        "colorGroup", "color", "areaPx",
    )

    fun manifest(text: String): MapManifest? {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val layers = (o["layers"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.map { l ->
                MapLayer(
                    name = str(l, "name"),
                    file = str(l, "file"),
                    geom = str(l, "geom").ifBlank { "polygon" },
                    label = str(l, "label"),
                    fill = str(l, "fill"),
                    stroke = str(l, "stroke"),
                    filter = MapFilter(
                        kind = ((l["filter"] as? JsonObject)?.get("kind") as? JsonArray)
                            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
                            ?.filter { it.isNotBlank() }
                            .orEmpty(),
                    ),
                )
            }
            .orEmpty()
        val baseObj = o["base"] as? JsonObject
        val styleObj = o["style"] as? JsonObject
        val selObj = styleObj?.get("selected") as? JsonObject
        val kindLabels = (o["kindLabels"] as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }
            ?.toMap()
            .orEmpty()
        // 官方基线字段名（pack.py 统计）；老包没有这个键 → 空表，行为与从前一致
        val customFields = (o["customFields"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return MapManifest(
            schema = str(o, "schema").toIntOrNull() ?: 0,
            version = str(o, "version"),
            crs = str(o, "crs"),
            yAxis = str(o, "yAxis").ifBlank { "up" },
            bbox = (o["bbox"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
                .orEmpty(),
            origin = str(o, "origin"),
            generated = str(o, "generated"),
            base = baseObj?.let {
                MapBase(
                    file = str(it, "file"),
                    width = str(it, "width").toIntOrNull() ?: 0,
                    height = str(it, "height").toIntOrNull() ?: 0,
                )
            },
            style = MapStyle(
                mode = str(styleObj, "mode").ifBlank { "overlay" },
                fill = str(styleObj, "fill").ifBlank { "#000000" },
                fillAlpha = str(styleObj, "fillAlpha").toFloatOrNull() ?: 0.03f,
                stroke = str(styleObj, "stroke").ifBlank { "#1A1611" },
                strokeAlpha = str(styleObj, "strokeAlpha").toFloatOrNull() ?: 0.22f,
                strokeWidth = str(styleObj, "strokeWidth").toFloatOrNull() ?: 1f,
                selectedFillAlpha = str(selObj, "fillAlpha").toFloatOrNull() ?: 0.3f,
                selectedStrokeAlpha = str(selObj, "strokeAlpha").toFloatOrNull() ?: 1f,
                selectedStrokeWidth = str(selObj, "strokeWidth").toFloatOrNull() ?: 2f,
            ),
            kindLabels = kindLabels,
            customFields = customFields,
            layers = layers,
        )
    }

    /** GeoJSON 解析结果 */
    data class GeoJsonDoc(
        val features: List<MapFeature> = emptyList(),
        /** 非 Polygon 的要素数（v1 契约只收面；>0 视为包不合格） */
        val nonPolygon: Int = 0,
        /**
         * 源数据里 **id 重复**的次数。
         *
         * 实测 2026.11.14 那版：90 个面里 0 出现了 43 次（只有 48 个唯一 id）。
         * 契约要求 id 唯一稳定，否则「标注」会 43 个面共用一条、逻辑层还会互相顶掉。
         * 这里**临时**给重复的加序号（0#2、0#3…）保证能用，同时把次数报给界面提示。
         */
        val duplicateIds: Int = 0,
    )

    /**
     * 解析 `FeatureCollection`。返回 null = 根本不是 JSON / 顶层结构不对。
     *
     * 只认 `Polygon`；`MultiPolygon`/`Point`/`LineString` 计入 [GeoJsonDoc.nonPolygon]，
     * 由校验层判不合格（契约要求单文件全 Polygon）。
     */
    fun geoJson(text: String): GeoJsonDoc? {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return null
        val arr = root["features"] as? JsonArray ?: return null
        var nonPolygon = 0
        var duplicateIds = 0
        val seen = HashMap<String, Int>()
        val features = arr.filterIsInstance<JsonObject>().mapIndexedNotNull { index, f ->
            val props = f["properties"] as? JsonObject
            val geom = f["geometry"] as? JsonObject
            val type = str(geom, "type")
            if (type != "Polygon") {
                nonPolygon++
                return@mapIndexedNotNull null
            }
            val rings = (geom?.get("coordinates") as? JsonArray)
                ?.filterIsInstance<JsonArray>()
                ?.map { ring ->
                    ring.filterIsInstance<JsonArray>().mapNotNull { pt ->
                        val c = pt.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
                        if (c.size >= 2) MapPoint(c[0], c[1]) else null
                    }
                }
                ?.filter { it.size >= 3 }
                .orEmpty()
            if (rings.isEmpty()) {
                nonPolygon++
                return@mapIndexedNotNull null
            }
            val rawId = str(props, "id").ifBlank { str(f, "id") }
            val baseId = rawId.ifBlank { "auto_$index" }
            val n = (seen[baseId] ?: 0) + 1
            seen[baseId] = n
            if (n > 1) duplicateIds++
            MapFeature(
                id = if (n == 1) baseId else baseId + "#" + n,
                name = str(props, "name"),
                kind = str(props, "kind"),
                code = str(props, "code"),
                floors = str(props, "floors").toDoubleOrNull()?.toInt() ?: 0,
                dept = str(props, "dept"),
                note = str(props, "note"),
                colorGroup = str(props, "colorGroup").toDoubleOrNull()?.toInt() ?: 0,
                color = str(props, "color"),
                areaPx = str(props, "areaPx").toDoubleOrNull() ?: 0.0,
                props = officialProps(props),
                rings = rings,
            )
        }
        return GeoJsonDoc(features = features, nonPolygon = nonPolygon, duplicateIds = duplicateIds)
    }

    /**
     * **官方基线字段**：properties 里除派生量以外的一切，原样保留（票 13 B 路）。
     *
     * 为什么单列一个函数：这是「不丢字段」的唯一入口，
     * 以后要加过滤条件（长度上限、保留字冲突）只改这里，别在解析主流程里散开。
     * 固定字段（`name`/`kind`）不进来 —— 它们有自己的槽位，用户标注对它们的覆盖
     * 走 [cn.dkarbon.enroute.data.map.MapEditsOps] 的优先级，不靠这张表。
     */
    private fun officialProps(props: JsonObject?): Map<String, String> {
        if (props == null) return emptyMap()
        val out = LinkedHashMap<String, String>()
        props.forEach { (k, v) ->
            if (k in DERIVED_OR_FIXED_KEYS) return@forEach
            if (v is JsonNull || v is JsonObject || v is JsonArray) return@forEach
            val s = (v as? JsonPrimitive)?.content?.trim().orEmpty()
            if (s.isNotBlank()) out[k] = s
        }
        return out
    }

    private fun str(o: JsonObject?, key: String): String {
        if (o == null) return ""
        val v = o[key] ?: return ""
        if (v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content?.trim().orEmpty()
    }
}
