package cn.dkarbon.enroute.ui.academic

import android.animation.ValueAnimator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.min

/**
 * 考试页时间轴的**绘制原语**（m31 轨道 D，2026-11-20）。
 *
 * 轴、节点、当前时刻三角、LED 光点全部走 `Canvas` / `drawBehind`，**不进图标体系** ——
 * 图标是一组可复用的符号，而这里的每一样都是「按时间比例算出来的一次性图形」：
 * 节点位置取决于行间距，三角取决于现在几点，LED 取决于离下一场还有几天。
 * 用 ImageVector 表达它们只能做成死的 24×24，反而失真。
 *
 * 几何口径（与卡片左边缘对齐）：
 * ```
 * │← 24dp →│            ← 轴线 x
 * │        │← 28dp →│   ← 轴到卡面
 * │        │        │← 卡面 x = 52dp
 * ```
 * 轴与卡面都从行顶算起；节点圆心落在日期行的竖直中线上，所以
 * 「行顶留白 gap」一改，节点跟着走 —— 两场之间隔得越远，轴上留白越大。
 */

internal object Axis {
    /** 轴线到列表左边界的距离 */
    val X: Dp = 24.dp

    /** 卡面（以及分组标题文字）的起点；与 [X] 相差 28dp，正好是「轴到卡片」的留白 */
    val ContentStart: Dp = 52.dp

    /** 日期行高度：节点圆心 = 这一行的竖直中线 */
    val DateLine: Dp = 16.dp

    /** 节点圆心距行顶的距离 */
    fun nodeY(gap: Dp): Dp = gap + DateLine * 0.5f
}

/** 卡片边缘的三态（按考试时刻与 `now` 比出来，不看分桶） */
internal enum class EdgeState {
    /** 已经考完：1dp 灰边 + 整体 55% 不透明 */
    PAST,

    /** 下一场：1dp 主色边 + 8dp 主色光晕，跟着整页的呼吸一起缩放 */
    NEXT,

    /** 更远的：常规样式 */
    FUTURE,
}

/**
 * 系统是否允许动画（设置里关掉动画 / 开发者选项把动画时长设成 0）。
 *
 * 关掉时呼吸停在静止帧（scale 1.0、光晕最淡），LED 停在起点 —— 不是消失，
 * 而是「不再自己动」，避免给前庭敏感的用户制造无法关掉的运动。
 */
internal fun animationsEnabled(): Boolean =
    runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)

/**
 * LED 一个来回的周期：剩余天数 → 4–12 秒（对数映射）。
 *
 * 用对数而不是线性，是因为「今天考」和「90 天后考」在心理上的差别远大于
 * 「30 天后」和「90 天后」：对数让近处的几档拉得开，远处的几档不至于滚成蜗牛。
 * 90 天封顶，再远也不放慢（否则光点几乎静止，看不出在走）。
 */
internal fun ledPeriodMs(daysLeft: Double): Int {
    val d = daysLeft.coerceIn(0.0, LED_CAP_DAYS)
    val k = (ln(1.0 + d) / ln(1.0 + LED_CAP_DAYS)).toFloat()
    return (LED_MIN_MS + (LED_MAX_MS - LED_MIN_MS) * k).toInt().coerceIn(LED_MIN_MS, LED_MAX_MS)
}

private const val LED_MIN_MS = 4_000
private const val LED_MAX_MS = 12_000
private const val LED_CAP_DAYS = 90.0

// ── 轴 ────────────────────────────────────────────────────

/** 一条竖轴线段（每行各画自己那一段，拼起来就是连续的轴）。 */
internal fun DrawScope.axisLine(color: Color, x: Dp = Axis.X) {
    val px = x.toPx()
    drawLine(color, Offset(px, 0f), Offset(px, size.height), 1.dp.toPx())
}

/**
 * 轴上的节点，三态：
 * - [EdgeState.PAST]：9dp 实心主色 + 2dp 淡晕（[lit] 0→1 渐入 = 展开「已结束」时点亮）
 * - [EdgeState.NEXT]：14dp 空心 + 1.5dp 主色环 + 外圈（外圈随呼吸收放）
 * - [EdgeState.FUTURE]：7dp 小空心灰点
 *
 * 一律先用纸色把轴线挖空再画，否则线会从节点中间穿过去。
 */
internal fun DrawScope.timelineNode(
    t: InkTokenSet,
    state: EdgeState,
    cx: Float,
    cy: Float,
    pulse: Float = 0f,
    lit: Float = 1f,
) {
    val center = Offset(cx, cy)
    when (state) {
        EdgeState.PAST -> {
            val a = lit.coerceIn(0f, 1f)
            // 点亮过程：先挖空，再淡晕，最后实心 —— 三层一起渐入
            drawCircle(t.paper, 5.dp.toPx(), center)
            if (a < 1f) {
                drawCircle(t.outline, 3.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
            if (a > 0f) {
                drawCircle(t.primary.copy(alpha = 0.16f * a), 6.5.dp.toPx(), center)
                drawCircle(t.primary.copy(alpha = a), 4.5.dp.toPx(), center)
            }
        }

        EdgeState.NEXT -> {
            // 外圈：呼吸时 0.14 ↔ 0.26
            drawCircle(
                t.primary.copy(alpha = 0.14f + 0.12f * pulse),
                9.dp.toPx(),
                center,
                style = Stroke(3.dp.toPx()),
            )
            drawCircle(t.paper, 6.75.dp.toPx(), center)
            drawCircle(t.primary, 6.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }

        EdgeState.FUTURE -> {
            drawCircle(t.paper, 4.5.dp.toPx(), center)
            drawCircle(t.outline, 3.5.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        }
    }
}

/**
 * 「现在」标记：轴上一个朝右的三角 + 一小截横线。
 *
 * 位置由调用方按**当前时刻在一段轴上的比例**算好传进来 —— 它在两场之间走得越靠下，
 * 说明离下一场越近（见 `ExamScreen.kt` 的 `markerFraction`）。
 */
internal fun DrawScope.nowMarker(t: InkTokenSet, x: Dp, y: Float) {
    val ax = x.toPx()
    val reach = 3.5.dp.toPx()

    drawCircle(t.paper, 5.dp.toPx(), Offset(ax, y))

    val path = Path().apply {
        moveTo(ax - reach * 0.5f, y - reach)
        lineTo(ax + reach, y)
        lineTo(ax - reach * 0.5f, y + reach)
        close()
    }
    drawPath(path, t.primary)
    // 横向那一小截：把三角和前后的轴连成一个「指针」而不是孤立的三角形
    drawLine(
        t.primary,
        Offset(ax + reach, y),
        Offset(ax + reach + 4.dp.toPx(), y),
        1.dp.toPx(),
    )
}

/**
 * LED 光点：从「现在」滚到「下一场」节点的那一颗。
 *
 * @param progress 0 = 起点（三角处），1 = 到节点
 *
 * 到点（> 0.9）时头部直径与光晕一起放大做「闪一下」，然后周期重启回到起点。
 * 尾迹两粒只向上（来时的方向）画，让运动有方向感而不像一颗静止的圆点。
 */
internal fun DrawScope.ledDot(t: InkTokenSet, x: Dp, y: Float, progress: Float) {
    val ax = x.toPx()
    val flash = if (progress > 0.9f) ((progress - 0.9f) / 0.1f).coerceIn(0f, 1f) else 0f

    val head = (2.5f + 2.5f * flash).dp.toPx()
    val center = Offset(ax, y)

    // 光晕：两层，闪的时候一起加亮
    drawCircle(t.accent.copy(alpha = 0.16f + 0.20f * flash), head * 2.4f, center)
    drawCircle(t.accent.copy(alpha = 0.10f + 0.14f * flash), head * 1.6f, center)
    drawCircle(t.accent, head, center)

    if (progress > 0.04f) {
        val step = 4.dp.toPx()
        drawCircle(t.accent.copy(alpha = 0.42f), head * 0.62f, Offset(ax, y - step))
        drawCircle(t.accent.copy(alpha = 0.18f), head * 0.38f, Offset(ax, y - step * 2f))
    }
}

/**
 * 卡片外圈的柔光（下一场专用的 8dp 主色光晕）。
 *
 * 硬朗体系里不用 `Modifier.shadow`：那是 Material 的软投影，边缘会发糊、还带灰调；
 * 这里要的是**同一色相的扩散**，所以自己画四层由外而内递亮的矩形，
 * 卡面本身是不透明的，画在它下面的部分会被盖住，露出来的正好是 8dp 那一圈。
 *
 * @param alpha 0.35（呼吸最淡）↔ 0.60（最亮）
 */
internal fun DrawScope.edgeGlow(color: Color, alpha: Float) {
    if (alpha <= 0.001f) return
    val step = 8.dp.toPx() / 4f
    for (i in 4 downTo 1) {
        val grow = step * i
        val a = alpha * when (i) {
            1 -> 0.14f
            2 -> 0.08f
            3 -> 0.05f
            else -> 0.03f
        }
        drawRect(
            color = color.copy(alpha = a),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
        )
    }
}

/** 一段轴上的比例（0..1）；两场间隔为 0 或算不出来时给 0.5，不装作精确。 */
internal fun markerFraction(prev: LocalDateTime?, start: LocalDateTime?, now: LocalDateTime): Float {
    if (prev == null || start == null) return 0.5f
    val total = Duration.between(prev, start).toMinutes().toFloat()
    if (total <= 0f) return 0.5f
    return (Duration.between(prev, now).toMinutes().toFloat() / total).coerceIn(0f, 1f)
}

/** 两场之间的轴上留白：间隔越久留白越大，但压在 6–31dp 之间，不让一屏只剩一条轴。 */
internal fun rowGap(prev: ExamItem?, e: ExamItem, state: EdgeState): Dp {
    val days = if (prev == null) {
        0L
    } else {
        val a = ExamRows.startOf(prev)?.toLocalDate()
        val b = ExamRows.startOf(e)?.toLocalDate()
        if (a != null && b != null) ChronoUnit.DAYS.between(a, b).coerceAtLeast(0L) else 0L
    }
    return when (state) {
        // 过去的事收紧：已考完的那一段不需要留白
        EdgeState.PAST -> (6f + min(days, 3L).toFloat() * 2f).dp
        // 下一场这一行是「现在 → 下一场」的跑道：三角、LED 都在这里跑，
        // 所以留白要够长，否则光点几乎原地不动、看不出在走
        EdgeState.NEXT -> (36f + min(days, 3L).toFloat() * 10f).dp
        EdgeState.FUTURE -> (10f + min(days, 7L).toFloat() * 3f).dp
    }
}
