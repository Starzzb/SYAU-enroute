package cn.dkarbon.enroute.data.map

/**
 * 点选命中测试（纯函数，单测直接打）。
 *
 * ## 为什么要三条规则叠加
 * 1. **射线法**判「点在面内」（含洞：在外环内且不在任何洞里）；
 * 2. 命中多个面时取**面积最小**者 —— 不然一栋楼点在绿地里，绿地会把它吞掉；
 * 3. 射线都没中时，用**距离兜底**（≤24dp 最近面）—— 校门那种小面在缩小时只有几个像素，
 *    手指根本点不准，没有兜底就永远点不中。
 */
object MapHit {

    /** 射线法：向右发射水平射线，数穿越次数（奇数 = 在内） */
    fun pointInRing(x: Double, y: Double, ring: List<MapPoint>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val pi = ring[i]
            val pj = ring[j]
            val intersect = ((pi.y > y) != (pj.y > y)) &&
                (x < (pj.x - pi.x) * (y - pi.y) / ((pj.y - pi.y) + 0.0) + pi.x)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    /** 点在面内：外环内，且**不在任何洞内** */
    fun contains(f: MapFeature, x: Double, y: Double): Boolean {
        val outer = f.rings.firstOrNull() ?: return false
        if (!pointInRing(x, y, outer)) return false
        return f.rings.drop(1).none { pointInRing(x, y, it) }
    }

    /**
     * 面命中：只考虑 [candidates]（调用方先按屏幕范围粗筛），返回**面积最小**的那个。
     */
    fun pick(candidates: List<MapFeature>, x: Double, y: Double): MapFeature? =
        candidates.asSequence()
            .filter { MapGeometry.bboxContains(it, x, y) }
            .filter { contains(it, x, y) }
            .minByOrNull { it.area }

    /**
     * 距离兜底：返回边界距离 ≤ [maxSourceDistance] 的最近面。
     * `maxSourceDistance` 由界面把 24dp 换算成**源坐标距离**再传进来。
     */
    fun nearest(candidates: List<MapFeature>, x: Double, y: Double, maxSourceDistance: Double): MapFeature? =
        candidates.asSequence()
            .map { it to MapGeometry.distanceToFeature(it, x, y) }
            .filter { it.second <= maxSourceDistance }
            .minByOrNull { it.second }
            ?.first

    /**
     * 点选的完整口径：**先射线命中（取最小面积），没中再用距离兜底**。
     *
     * @param maxScreenDistance 兜底半径（屏幕 dp→px 由调用方换算好）
     */
    fun pickOrNearest(
        candidates: List<MapFeature>,
        x: Double,
        y: Double,
        transform: MapTransform,
        maxScreenDistance: Double,
    ): MapFeature? {
        pick(candidates, x, y)?.let { return it }
        val maxSrc = transform.toSourceDistance(maxScreenDistance)
        return nearest(candidates, x, y, maxSrc)
    }
}
