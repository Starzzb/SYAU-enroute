package cn.dkarbon.enroute.ui.map

import cn.dkarbon.enroute.data.map.MapFeature
import kotlin.math.abs
import kotlin.math.sign

/**
 * 地图视图的**纯规则**（票 18）：淡出曲线、可拖边界、回弹衰减、锚点缩放。
 *
 * 抽成纯函数的原因：这四条都是「调一个数字就变一个手感」的东西，
 * 埋在 Compose 里只能靠反复上真机试；抽出来就能用单测把边界钉死
 * （例如「3.0× 时位图不透明度必须正好 0」「拖过头只允许衰减 35%」）。
 */
internal object MapViewRules {

    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 8f

    /** 位图开始淡出的缩放（此后线性降到 0） */
    const val FADE_START = 2.2f

    /** 位图完全消失的缩放（此后**根本不画**，不留半透明层） */
    const val FADE_END = 3.0f

    /** 拖过边界后的衰减系数（rubber band） */
    const val OVERDRAG_DECAY = 0.35f

    /** 松手回弹时长（ms）；票面要求 180–220 */
    const val SNAP_BACK_MS = 200

    /**
     * B 模式底图的不透明度。
     *
     * **线性**：`2.2×` 之前恒 1.0，`2.2×→3.0×` 从 1.0 线性降到 0.0，`3.0×` 之后恒 0。
     *
     * 为什么要线性而不是阶跃：以前是 `if (zoom > 2.5f) 0.35f`，一过 2.5× 直接砸到 0.35 ——
     * 半透明位图压在纸白底上就是主人说的那层「灰色模糊遮罩」，而且是突变，很跳。
     */
    fun bitmapAlpha(zoom: Float): Float = when {
        zoom <= FADE_START -> 1f
        zoom >= FADE_END -> 0f
        else -> ((FADE_END - zoom) / (FADE_END - FADE_START)).coerceIn(0f, 1f)
    }

    /**
     * 位图淡到一半以下时，矢量层必须**自己上色**（A 模式配色）。
     *
     * 否则位图一淡，矢量只剩 `style.fillAlpha`（真数据是 3%）的填充 —— 地图会变成灰白线框。
     */
    fun vectorTakesOver(bitmapAlpha: Float): Boolean = bitmapAlpha < 0.5f

    /**
     * 单轴可拖范围（正数，单位 px）。
     *
     * 两段构成：
     * 1. `(缩放后内容长度 − 画布长度) / 2` —— 能把内容边缘拖到画布边缘（旧公式 `cw*(zoom-1)/2`
     *    只在「内容正好铺满画布」时才对，而 `fit()` 是留边居中的）；
     * 2. `+ margin` —— **视野余量（pan padding / overscroll margin）**：
     *    允许把内容边缘**拖过**画布边缘这么远。没有它，四角地块永远只能贴屏幕边，
     *    而角落恰恰是最难看清、最难点的位置（主人 2026-11-14 的建议，标准地图做法）。
     */
    fun panLimit(contentLength: Float, canvasLength: Float, margin: Float = 0f): Float =
        ((contentLength - canvasLength) / 2f).coerceAtLeast(0f) + margin.coerceAtLeast(0f)

    // ── 点击判定（票 18 复测：双击不许顺带选中地块）──────────────

    /** 位移超过这个值（dp）就算拖动，不触发选中 */
    const val TAP_SLOP_DP = 8f

    /** 双击等待窗口（ms）：抬手后这么久之内没有第二次按下，才算单击 */
    const val DOUBLE_TAP_MS = 300L

    /** 双击两下之间允许的最大距离（dp）：超出就是不相关的两次点击 */
    const val DOUBLE_TAP_SLOP_DP = 40f

    /**
     * 这次手势算不算「点击」（而不是拖动/缩放）。
     *
     * 单指 + 总位移小于阈值才算 —— 拖动 450px 之后**绝不能**触发选中。
     */
    fun isTap(maxPointers: Int, movedScreen: Float, slopScreen: Float): Boolean =
        maxPointers <= 1 && movedScreen <= slopScreen

    /**
     * 是不是这一串的第二下（双击）。
     *
     * @param nowMs 本次抬手时刻
     * @param lastTapMs 上一次单击候选的时刻（0 = 没有）
     * @param dx/dy 两次落点的差
     */
    fun isDoubleTap(
        nowMs: Long,
        lastTapMs: Long,
        dx: Float,
        dy: Float,
        slopScreen: Float,
    ): Boolean {
        if (lastTapMs <= 0L) return false
        if (nowMs - lastTapMs > DOUBLE_TAP_MS) return false
        return kotlin.math.hypot(dx.toDouble(), dy.toDouble()) <= slopScreen
    }

    /** 视野余量：画布长度的 35%（能把边角地块拖到视野中部偏外，又不至于拖出一大片空） */
    const val VIEW_MARGIN_RATIO = 0.35f

    fun viewMargin(canvasLength: Float): Float = canvasLength * VIEW_MARGIN_RATIO

    /**
     * 越界衰减：超出 [limit] 的部分只保留 [decay]（默认 35%）。
     *
     * 手感上表现为「能拉出去一点，但明显拽不动」，松手再弹回边界。
     */
    fun applyOverdrag(value: Float, limit: Float, decay: Float = OVERDRAG_DECAY): Float {
        if (limit <= 0f) return value * decay
        val over = abs(value) - limit
        if (over <= 0f) return value
        return sign(value) * (limit + over * decay)
    }

    /**
     * 松手后应该回到的位置（夹到边界内）。
     *
     * ⚠️ 只该在**所有手指抬起之后**调用一次（见 [snapBackTarget] 的调用点约定）：
     * 以前用「停手 140ms」近似手势结束，手指拖着不动就会被误判成结束 → 画面被拉回，
     * 主人报的「能移动但会回弹」就是这个。
     */
    fun clampToBounds(value: Float, limit: Float): Float =
        if (limit <= 0f) 0f else value.coerceIn(-limit, limit)

    /** 回弹目标 = 夹到边界；单列出来是为了让「回弹只在手势结束时」这条契约有名字 */
    fun snapBackTarget(pan: Float, limit: Float): Float = clampToBounds(pan, limit)

    /**
     * 以屏幕上的某一点为锚点缩放后，新的平移量。
     *
     * `pan' = anchor - (anchor - pan) * k`
     *
     * @param anchor 锚点相对**画布中心**的偏移（双击/双指质心 − 画布中心）
     * @param pan 当前平移
     * @param k 缩放比（新 zoom / 旧 zoom）
     */
    fun panAfterZoomAt(anchor: Float, pan: Float, k: Float): Float = anchor - (anchor - pan) * k

    /**
     * 矢量层上色：`要素 color` → `层 fill` → 按 `colorGroup` 兜底。
     *
     * 真数据每条要素自带 `#RRGGBB`（矢量化时聚出来的色组），
     * 所以绝大多数情况走第一档；缺色时才用色组号映射到一组固定色。
     */
    fun vectorFillHex(feature: MapFeature, layerFill: String): String {
        if (feature.color.isNotBlank()) return feature.color
        if (layerFill.isNotBlank()) return layerFill
        return groupFallbackHex(feature.colorGroup)
    }

    /** `colorGroup`（1..12）→ 兜底色；未知返回空串（交给调用方的主题色） */
    fun groupFallbackHex(group: Int): String = when (group) {
        1 -> "#8DAB87"
        2 -> "#C9A66B"
        3 -> "#8FB8CC"
        4 -> "#B5A28C"
        5 -> "#9CB380"
        6 -> "#D6B98C"
        7 -> "#A9B7C6"
        8 -> "#C4A48A"
        9 -> "#9FB1A0"
        10 -> "#BFAF9A"
        11 -> "#8DAB87"
        12 -> "#A8A29A"
        else -> ""
    }
}
