package cn.dkarbon.enroute.data.map

/**
 * 逻辑层拆分（纯函数）：`manifest.layers[]` 是**逻辑层**，可以多个指向同一个 geojson，
 * 用 `filter.kind` 过滤（v1 就用单文件 `campus.geojson` 装全部要素）。
 *
 * 两个必须守住的细节：
 * 1. 没有命中任何逻辑层的要素**不能悄悄丢掉** —— 归到 [MapLayerGroup.unmatched]，
 *    界面用兜底色画出来（丢要素看起来像"图缺了一块"，比配色错更难查）；
 * 2. 同一个 kind 命中多个逻辑层时，以**先声明的**为准（避免一栋楼上两次色）。
 */
object MapLayers {

    /** 一个逻辑层 + 它包住的要素（按 GeoJSON 里的顺序，便于稳定渲染） */
    data class Group(
        val layer: MapLayer,
        val features: List<MapFeature> = emptyList(),
    )

    data class Result(
        val groups: List<Group> = emptyList(),
        /** 没被任何层接住的要素（兜底画，别丢） */
        val unmatched: List<MapFeature> = emptyList(),
    )

    fun split(manifest: MapManifest, features: List<MapFeature>): Result {
        // 层与层的优先级 = 声明顺序：先到先得，避免重复上色
        val claimed = HashSet<String>()
        val groups = manifest.layers.map { layer ->
            val hit = features.filter { f ->
                val key = f.id.ifBlank { f.name }
                key !in claimed && layer.filter.accepts(f.kind)
            }
            hit.forEach { claimed += it.id.ifBlank { it.name } }
            Group(layer, hit)
        }
        val unmatched = features.filter { (it.id.ifBlank { it.name }) !in claimed }
        return Result(groups, unmatched)
    }

    /** 图例：只列出真有要素的层（空层不必占位） */
    fun legend(manifest: MapManifest, features: List<MapFeature>): List<MapLayer> =
        split(manifest, features).groups.filter { it.features.isNotEmpty() }.map { it.layer }
}
