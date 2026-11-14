package cn.dkarbon.enroute.data.map

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 源像素坐标 ⇄ 屏幕坐标的**等比映射**（纯函数，单测直接打）。
 *
 * ## 两个坑，都必须处理
 * 1. **等比**：把 `bbox` 塞进画布时按 `min(w/bw, h/bh)` 取比例并居中留边 ——
 *    非等比拉伸会把操场画成方的、把楼栋压扁。
 * 2. **y 轴翻转**：源数据是「左下角原点、y 轴向上」，屏幕 y 向下 →
 *    `screenY = offsetY + (bbox.maxY - y) * scale`。漏了这一步整张图上下颠倒。
 *
 * @param scale 源像素 → 屏幕像素的比例
 * @param offsetX/offsetY 画布内留边后的左上角偏移（已含居中）
 */
data class MapTransform(
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double,
    val bbox: List<Double>,
) {
    private val minX: Double get() = bbox[0]
    private val maxY: Double get() = bbox[3]

    fun screenX(x: Double): Float = (offsetX + (x - minX) * scale).toFloat()

    /** y 轴翻转：源坐标 y 越大越靠上，屏幕 y 越小越靠上 */
    fun screenY(y: Double): Float = (offsetY + (maxY - y) * scale).toFloat()

    fun sourceX(sx: Float): Double = (sx - offsetX) / scale + minX

    fun sourceY(sy: Float): Double = maxY - (sy - offsetY) / scale

    /** 源坐标两点距离 → 屏幕像素 */
    fun toScreenDistance(d: Double): Double = d * scale

    /** 屏幕像素距离 → 源坐标距离 */
    fun toSourceDistance(px: Double): Double = if (scale <= 0.0) 0.0 else px / scale
}

object MapProjection {

    /**
     * 把 `bbox` **等比**塞进 `canvasW × canvasH`，居中留边 [padding]。
     *
     * 注意这里只算「适配（fit）」这一档；用户缩放/拖拽由界面在它之上再叠一层
     * 用户变换（[withUser]），命中测试反向解开即可 —— 这样 Path 可以按源坐标缓存，
     * 缩放时不必重建。
     */
    fun fit(
        bbox: List<Double>,
        canvasW: Float,
        canvasH: Float,
        padding: Float = 0f,
    ): MapTransform {
        val safe = if (bbox.size == 4 && bbox[2] > bbox[0] && bbox[3] > bbox[1]) bbox
        else listOf(0.0, 0.0, 1.0, 1.0)
        val bw = safe[2] - safe[0]
        val bh = safe[3] - safe[1]
        val availW = max(1.0, canvasW - padding * 2.0)
        val availH = max(1.0, canvasH - padding * 2.0)
        val scale = min(availW / bw, availH / bh)
        val drawW = bw * scale
        val drawH = bh * scale
        val offX = padding + (availW - drawW) / 2.0
        val offY = padding + (availH - drawH) / 2.0
        return MapTransform(scale = scale, offsetX = offX, offsetY = offY, bbox = safe)
    }

    /**
     * 在 fit 之上叠用户变换（缩放 + 平移），得到最终「源 → 屏幕」的映射。
     *
     * 缩放围绕画布中心：`scale' = scale * zoom`，偏移按中心缩放推导。
     */
    fun withUser(
        base: MapTransform,
        zoom: Double,
        panX: Float,
        panY: Float,
        canvasW: Float,
        canvasH: Float,
    ): MapTransform {
        val z = zoom.coerceIn(1.0, 8.0)
        val cx = canvasW / 2.0
        val cy = canvasH / 2.0
        val newScale = base.scale * z
        // 以画布中心为不动点：offset' = c - (c - offset) * z
        val offX = cx - (cx - base.offsetX) * z + panX
        val offY = cy - (cy - base.offsetY) * z + panY
        return MapTransform(newScale, offX, offY, base.bbox)
    }
}

/** 几何小工具：点到线段距离 / 点到面距离（命中兜底用） */
object MapGeometry {

    fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-12) return hypot(px - ax, py - ay)
        var t = ((px - ax) * dx + (py - ay) * dy) / len2
        t = t.coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    /** 点到要素**边界**的最短距离（洞的边也算） */
    fun distanceToFeature(f: MapFeature, x: Double, y: Double): Double {
        var best = Double.MAX_VALUE
        f.rings.forEach { ring ->
            if (ring.size < 2) return@forEach
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[(i + 1) % ring.size]
                val d = distanceToSegment(x, y, a.x, a.y, b.x, b.y)
                if (d < best) best = d
            }
        }
        return if (best == Double.MAX_VALUE) Double.MAX_VALUE else best
    }

    private fun hypot(x: Double, y: Double): Double = sqrt(x * x + y * y)

    /** 要素 bbox 是否包含点（粗筛，命中测试先过这一关） */
    fun bboxContains(f: MapFeature, x: Double, y: Double): Boolean {
        val b = f.bounds
        if (b.size < 4) return false
        return x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]
    }

    /** 屏幕内粗筛：要素 bbox 与屏幕矩形是否相交（只对屏幕内要素做命中测试） */
    fun visibleInScreen(f: MapFeature, t: MapTransform, w: Float, h: Float): Boolean {
        val b = f.bounds
        if (b.size < 4) return false
        val x1 = t.screenX(b[0])
        val x2 = t.screenX(b[2])
        val y1 = t.screenY(b[3])   // maxY → 屏幕上方
        val y2 = t.screenY(b[1])
        val left = min(x1, x2)
        val right = max(x1, x2)
        val top = min(y1, y2)
        val bottom = max(y1, y2)
        return right >= 0f && left <= w && bottom >= 0f && top <= h
    }

    fun distance(a: MapPoint, b: MapPoint): Double = hypot(a.x - b.x, a.y - b.y)
}
