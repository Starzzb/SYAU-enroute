package cn.dkarbon.enroute.ui.timetable

import kotlin.math.abs
import kotlin.math.sign

/**
 * 课表左右滑动切周的手势判定（票 28）。
 *
 * 全部是**纯函数**，Composable 里只负责把位移/速度喂进来 —— 因为这套判定的坑全在数字上
 * （横向占优、阈值、速度兜底、边界不回退），塞在 `pointerInput` 里就只能真机试。
 *
 * ## 为什么「纵向优先」是第一位的要求
 *
 * 课表要上下滚才能看到 9–12 节。如果横向判定不严，用户往下滚一屏就会被判成切周 ——
 * 那不是「手感差一点」，是**整个课表没法用**。所以这里三重保险：
 * 1. 横向位移必须 ≥ [MIN_SWIPE_DP]，且**大于纵向位移的 [DOMINANCE] 倍**；
 * 2. 手指移动超过阈值之后才"接管"（[shouldClaim]），一按下不抢；
 * 3. 速度兜底也要求横向占优 —— 快速斜扫不算。
 */
object WeekSwipeRules {

    /** 横向位移阈值（dp）：小于它不认 */
    const val MIN_SWIPE_DP = 64f

    /** 横向必须是纵向的这么多倍，才算"横向意图" */
    const val DOMINANCE = 1.5f

    /** 速度兜底（dp/s）：快速轻扫即使位移不够也切 */
    const val FLING_VELOCITY_DP_S = 800f

    /** 拖动时越界（超过一屏宽）的阻尼系数 */
    const val DRAG_OVER_EDGE_DECAY = 0.25f

    /** 手指移动到这里才**接管**手势（在此之前纵向滚动说了算） */
    fun shouldClaim(dx: Float, dy: Float, minSwipePx: Float): Boolean {
        if (!dx.isFinite() || !dy.isFinite()) return false
        if (abs(dy) > abs(dx)) return false                  // 纵向更大 → 绝不抢
        return abs(dx) >= minSwipePx && abs(dx) > abs(dy) * DOMINANCE
    }

    /**
     * 松手时决定切不切、往哪切。
     *
     * @return `-1` = 上一周（向右滑），`+1` = 下一周（向左滑），`0` = 不切
     */
    fun decide(dx: Float, dy: Float, velocityX: Float, minSwipePx: Float, flingPx: Float): Int {
        if (!dx.isFinite() || !dy.isFinite()) return 0
        // 纵向占优 → 一律不切（纵向滚动优先）
        if (abs(dy) > abs(dx)) return 0
        val horizontalDominant = abs(dx) > abs(dy) * DOMINANCE

        // ① 位移够了：方向看位移
        if (horizontalDominant && abs(dx) >= minSwipePx) return if (dx > 0f) -1 else 1

        // ② 位移不够但甩得快：方向看速度（仍要求横向占优，且两者不矛盾）
        if (horizontalDominant &&
            velocityX.isFinite() &&
            abs(velocityX) >= flingPx &&
            velocityX * dx >= 0f
        ) {
            return if (velocityX > 0f) -1 else 1
        }

        return 0
    }

    /**
     * 边界处理：这一刀切不切得动。
     *
     * 到边界时**不静默无效** —— 调用方要按「回弹」处理（见 [SwipeOutcome]）。
     */
    fun canSwipeTo(week: Int, step: Int, minWeek: Int, maxWeek: Int): Boolean =
        step != 0 && (week + step) in minWeek..maxWeek

    /** 松手后的三种结局：切了 / 回弹（边界）/ 归位（没到阈值） */
    enum class SwipeOutcome {
        /** 切周成功 */
        SWITCHED,

        /** 到边界：内容回弹，周次不变（要让用户看到"到头了"） */
        BOUNCE_AT_EDGE,

        /** 没到阈值：轻轻一碰，归位 */
        SNAP_BACK,
    }

    fun outcomeFor(step: Int, week: Int, minWeek: Int, maxWeek: Int): SwipeOutcome = when {
        step == 0 -> SwipeOutcome.SNAP_BACK
        canSwipeTo(week, step, minWeek, maxWeek) -> SwipeOutcome.SWITCHED
        else -> SwipeOutcome.BOUNCE_AT_EDGE
    }

    /** 拖动中的位移（超出一屏宽时施阻尼，手感上"拽不动"而不是硬顶住） */
    fun dragOffset(dx: Float, widthPx: Float): Float {
        if (widthPx <= 0f) return 0f
        val over = abs(dx) - widthPx
        return if (over <= 0f) dx else sign(dx) * (widthPx + over * DRAG_OVER_EDGE_DECAY)
    }

    /** 拖动时的透明度：越拖越淡（新一周入场时反着来） */
    fun alphaFor(offsetPx: Float, widthPx: Float, maxFade: Float = 0.55f): Float {
        if (widthPx <= 0f) return 1f
        return 1f - (abs(offsetPx) / widthPx).coerceIn(0f, 1f) * maxFade
    }

    /** 过渡时长（ms）：票面要求 180–220，取中值附近 */
    const val TRANSITION_MS = 200
}
