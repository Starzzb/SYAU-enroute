package cn.dkarbon.enroute.data.map

/**
 * 校园地图 v1 的数据模型（票 12）。
 *
 * 契约全文：`docs/校园地图-数据契约与接入方案.md`；真数据由 `tools/map/pack.py` 生成，
 * 放在 `assets/map/`（版本 `2026.11.14-1`，90 个面）。
 *
 * v1 是**相对位置**（`crs = "local"`，像素坐标，**y 轴向上**），不做 GPS、不申请任何定位权限。
 */

/** 地图包 manifest（`map/manifest.json`） */
data class MapManifest(
    /** 契约版本；不认就整包作废并回退内置 */
    val schema: Int = 0,
    val version: String = "",
    val crs: String = "",
    /** **`up`** = 源数据左下原点、y 轴向上（翻转由 [MapTransform.screenY] 负责） */
    val yAxis: String = "up",
    /** `[minX, minY, maxX, maxY]`（local: 源图像素范围） */
    val bbox: List<Double> = emptyList(),
    /** 人类可读的来源说明（界面上要显示） */
    val origin: String = "",
    val generated: String = "",
    /** 底图（可选）：只有一个 `file`，缺了就退回纯矢量模式 */
    val base: MapBase? = null,
    /** 渲染样式（fill/stroke 的 alpha 由这里定，**不写死在界面里**） */
    val style: MapStyle = MapStyle(),
    /** 类别码 → 中文（19 个码；属性面板的「类别」下拉就靠它） */
    val kindLabels: Map<String, String> = emptyMap(),
    /**
     * **官方基线**自带的标注字段名（票 13「B 路」）。
     *
     * `pack.py` 打包时会把众包合并进来的自定义字段统计到这个列表里。
     * 它是**只读层**：这些值来自 `campus.geojson` 的 properties，不在用户的
     * `edits.json` 里 —— 所以「已标注 N 个面」**不统计**它们，
     * 用户点「清除数据」也删不掉官方数据。
     */
    val customFields: List<String> = emptyList(),
    /** **逻辑层**：可以多个指向同一个 geojson，用 `filter.kind` 过滤 */
    val layers: List<MapLayer> = emptyList(),
) {
    val isValidBox: Boolean
        get() = bbox.size == 4 && bbox[2] > bbox[0] && bbox[3] > bbox[1]

    /** 类别下拉的选项（按码排序，稳定） */
    fun kindOptions(): List<Pair<String, String>> = kindLabels.entries
        .sortedBy { it.key }
        .map { it.key to it.value }

    companion object {
        const val SUPPORTED_SCHEMA = 1
    }
}

/** `manifest.base`：`{"file":"base@1x.webp","width":1024,"height":648}` */
data class MapBase(
    val file: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * `manifest.style`：底图叠加模式下的统一配色与透明度。
 *
 * 真数据是 `fill #000000 / fillAlpha 0.03 / strokeAlpha 0.22`：
 * 极淡的热区，透出底图细节 —— 界面必须读它，不能自己写死 0.12。
 */
data class MapStyle(
    val mode: String = "overlay",
    val fill: String = "#000000",
    val fillAlpha: Float = 0.03f,
    val stroke: String = "#1A1611",
    val strokeAlpha: Float = 0.22f,
    val strokeWidth: Float = 1f,
    val selectedFillAlpha: Float = 0.3f,
    val selectedStrokeAlpha: Float = 1f,
    val selectedStrokeWidth: Float = 2f,
)

data class MapLayer(
    val name: String = "",
    val file: String = "",
    val geom: String = "polygon",
    val label: String = "",
    /** 可选：层自带配色（真数据的 campus 层没给，靠要素自身的 `color`） */
    val fill: String = "",
    val stroke: String = "",
    val filter: MapFilter = MapFilter(),
)

data class MapFilter(val kind: List<String> = emptyList()) {
    fun accepts(k: String): Boolean = kind.isEmpty() || kind.contains(k)
}

/** 一个点（源像素坐标，y 轴向上） */
data class MapPoint(val x: Double = 0.0, val y: Double = 0.0)

/**
 * 一个面要素（v1 全部是 Polygon；`rings[0]` 是外环，其余是洞）。
 *
 * 真数据的属性是 `id/name/kind/colorGroup/color/areaPx`：
 * `name` 只有 1 条（九州体育场）、`kind` 全空 —— 所以**属性编辑是本票的核心功能**，
 * `color`/`colorGroup` 是矢量化时按图例色块聚出来的，A 模式按它上色。
 */
data class MapFeature(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val code: String = "",
    val floors: Int = 0,
    val dept: String = "",
    val note: String = "",
    /** 矢量化聚类出的色组号（1..12） */
    val colorGroup: Int = 0,
    /** 矢量化时的色值（`#8DAB87`） */
    val color: String = "",
    /** 原始面积（源像素²，pack.py 已算好；没有就本地算） */
    val areaPx: Double = 0.0,
    /**
     * **官方基线的标注字段**（`campus.geojson` properties 里除派生量以外的一切）。
     *
     * 票 13「B 路」要的就是这一个字段：官方数据留在 asset 里当**只读层**，
     * 不进 `edits.json`，所以不会被用户「清除数据」删掉，也不会算成用户标注。
     * 解析时**原样保留**（不认识的键也留着）——「不丢字段」是这条链路的硬要求。
     */
    val props: Map<String, String> = emptyMap(),
    val rings: List<List<MapPoint>> = emptyList(),
) {
    /** 命中时取**面积最小**者用 */
    val area: Double by lazy {
        if (areaPx > 0.0) return@lazy areaPx
        val outer = rings.firstOrNull() ?: return@lazy 0.0
        val holes = rings.drop(1).sumOf { kotlin.math.abs(ringArea(it)) }
        (kotlin.math.abs(ringArea(outer)) - holes).coerceAtLeast(0.0)
    }

    val bounds: DoubleArray by lazy {
        val pts = rings.flatten()
        if (pts.isEmpty()) return@lazy doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        doubleArrayOf(
            pts.minOf { it.x }, pts.minOf { it.y },
            pts.maxOf { it.x }, pts.maxOf { it.y },
        )
    }

    val floorsLabel: String get() = if (floors > 0) "$floors 层" else ""
}

/** 鞋带公式（源坐标），返回有符号面积 */
internal fun ringArea(ring: List<MapPoint>): Double {
    if (ring.size < 3) return 0.0
    var s = 0.0
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        s += a.x * b.y - b.x * a.y
    }
    return s / 2.0
}
