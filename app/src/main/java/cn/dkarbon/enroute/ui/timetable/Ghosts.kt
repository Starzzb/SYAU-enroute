package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * 课表里的「小幽灵」—— **六个形象**，全部程序化绘制。
 *
 * ## 为什么不再用位图
 * 上一版借了 Twemoji 的 `monster_ghost.xml`，只有一个形象、颜色写死，
 * 每门课看起来一模一样。改成 Canvas 画之后：
 *   · 形象由课程号决定 → **每门课有自己的那只**
 *   · 颜色直接吃课程色族（[Tt.Fams] 的竖脊色）→ 和卡片配色天然对齐
 *   · 任意尺寸都清晰，不用准备多套密度的图
 *
 * ## 六个形象
 * `Round` 圆头 · `Square` 方头 · `Pointed` 尖头 · `Tall` 长条 · `Blob` 团子 · `Horned` 双角
 *
 * 形象只是「这门课的辨识标记」，不承载任何信息 —— 所以是静态的，不跑动画。
 * 唯一会动的是 [TravellingGhost]（时间轴上那个「现在」）。
 */
enum class GhostForm(val label: String) {
    Round("圆头"),
    Square("方头"),
    Pointed("尖头"),
    Tall("长条"),
    Blob("团子"),
    Horned("双角");

    companion object {
        /** 稳定映射：同一门课永远是同一只幽灵（改名不换形象） */
        fun of(key: String): GhostForm {
            val h = key.hashCode() and 0x7fffffff
            return entries[h % entries.size]
        }
    }
}

/** 静态幽灵标记：给每门课一个固定的形象 */
@Composable
fun GhostMark(
    form: GhostForm,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val eye = faceInk()
    Canvas(modifier) {
        drawGhost(form, color, alpha, eye)
    }
}

/**
 * 会动的幽灵：时间轴上的「现在」。
 *
 * 和上一版一样只跑 2fps —— 实测每帧重绘成本约 6% CPU，
 * 一只 26dp 的小东西不值得跟屏幕刷新率跑。漂移本来就是慢动作。
 *
 * @param mood 决定动作：上课时上下颠（像在啃东西），下课漂移，夜里慢悠悠
 */
@Composable
fun TravellingGhost(
    form: GhostForm,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    mood: GhostMood = GhostMood.Wander,
) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (now - last >= 500_000_000L || last == 0L) {
                    last = now
                    phase.floatValue = ((now - start) % 8_000_000_000L) / 8_000_000_000f
                }
            }
        }
    }

    // @Composable 不能在 Canvas 的 draw lambda 里读；进 Canvas 之前就取好
    val eye = faceInk()

    Canvas(modifier) {
        val p = phase.floatValue
        // 三种情绪三种节奏：上课（颠得快）、漂移（中速）、夜里（几乎不动）
        val bobAmp: Float
        val bobFreq: Float
        val swayAmp: Float
        when (mood) {
            GhostMood.Wander -> { bobAmp = 3f; bobFreq = 4f; swayAmp = 3f }
            GhostMood.Feed -> { bobAmp = 4.5f; bobFreq = 9f; swayAmp = 1.6f }
            GhostMood.Doze -> { bobAmp = 1.6f; bobFreq = 1.6f; swayAmp = 1f }
        }
        val bob = sin(p * 6.28318f * bobFreq) * bobAmp
        val sway = sin(p * 6.28318f * 3.3f) * swayAmp
        val breathe = 1f + sin(p * 6.28318f * (if (mood == GhostMood.Doze) 1.1f else 3.8f)) * 0.05f
        val swayPx = sway.dp.toPx()
        val bobPx = bob.dp.toPx()

        withTransform({
            translate(swayPx, bobPx)
            scale(breathe, breathe, pivot = center)
        }) {
            drawGhost(form, color, 1f, eye)
        }
    }
}

/** 幽灵此刻在干什么 —— 决定动画节奏 */
enum class GhostMood {
    /** 下课的走廊里漂 */
    Wander,

    /** 正在上课（在吃这门课） */
    Feed,

    /** 深夜，困 */
    Doze,
}

// ── 绘制 ──────────────────────────────────────────────────

private fun DrawScope.drawGhost(form: GhostForm, color: Color, alpha: Float, eye: Color) {
    val w = size.width
    val h = size.height
    val body = Path()

    when (form) {
        GhostForm.Round -> {
            body.moveTo(w * 0.15f, h * 0.80f)
            body.cubicTo(w * 0.08f, h * 0.28f, w * 0.36f, h * 0.06f, w * 0.50f, h * 0.06f)
            body.cubicTo(w * 0.64f, h * 0.06f, w * 0.92f, h * 0.28f, w * 0.85f, h * 0.80f)
            waveBottom(body, w, h, 0.85f, 0.15f)
        }

        GhostForm.Square -> {
            body.moveTo(w * 0.18f, h * 0.80f)
            body.lineTo(w * 0.18f, h * 0.30f)
            body.quadraticTo(w * 0.18f, h * 0.10f, w * 0.38f, h * 0.10f)
            body.lineTo(w * 0.62f, h * 0.10f)
            body.quadraticTo(w * 0.82f, h * 0.10f, w * 0.82f, h * 0.30f)
            body.lineTo(w * 0.82f, h * 0.80f)
            waveBottom(body, w, h, 0.82f, 0.18f)
        }

        GhostForm.Pointed -> {
            body.moveTo(w * 0.50f, h * 0.04f)
            body.quadraticTo(w * 0.90f, h * 0.44f, w * 0.84f, h * 0.80f)
            waveBottom(body, w, h, 0.84f, 0.16f)
            body.quadraticTo(w * 0.10f, h * 0.44f, w * 0.50f, h * 0.04f)
        }

        GhostForm.Tall -> {
            body.moveTo(w * 0.28f, h * 0.86f)
            body.lineTo(w * 0.28f, h * 0.26f)
            body.cubicTo(w * 0.28f, h * 0.06f, w * 0.72f, h * 0.06f, w * 0.72f, h * 0.26f)
            body.lineTo(w * 0.72f, h * 0.86f)
            waveBottom(body, w, h, 0.72f, 0.28f, bumps = 2)
        }

        GhostForm.Blob -> {
            body.moveTo(w * 0.10f, h * 0.72f)
            body.cubicTo(w * 0.06f, h * 0.24f, w * 0.94f, h * 0.24f, w * 0.90f, h * 0.72f)
            waveBottom(body, w, h, 0.90f, 0.10f)
        }

        GhostForm.Horned -> {
            // 双角：从头顶两侧各挑一根
            body.moveTo(w * 0.16f, h * 0.80f)
            body.cubicTo(w * 0.10f, h * 0.34f, w * 0.30f, h * 0.12f, w * 0.50f, h * 0.12f)
            body.cubicTo(w * 0.70f, h * 0.12f, w * 0.90f, h * 0.34f, w * 0.84f, h * 0.80f)
            waveBottom(body, w, h, 0.84f, 0.16f)
        }
    }
    body.close()
    drawPath(body, color.copy(alpha = alpha))

    // 双角的两根角画在身体之外（不参与填充，免得盖住眼睛）
    if (form == GhostForm.Horned) {
        val horn = Path().apply {
            moveTo(w * 0.26f, h * 0.20f)
            lineTo(w * 0.18f, h * 0.00f)
            lineTo(w * 0.38f, h * 0.10f)
            close()
            moveTo(w * 0.74f, h * 0.20f)
            lineTo(w * 0.82f, h * 0.00f)
            lineTo(w * 0.62f, h * 0.10f)
            close()
        }
        drawPath(horn, color.copy(alpha = alpha))
    }

    drawGhostFace(form, color, alpha, eye)
}

/** 波浪下摆：从右往左走，bumps 个半圆 */
private fun waveBottom(
    p: Path,
    w: Float,
    h: Float,
    rightX: Float,
    leftX: Float,
    bumps: Int = 3,
) {
    val span = (rightX - leftX) / bumps
    var x = rightX
    repeat(bumps) {
        val next = x - span
        p.quadraticTo(
            w * (x - span / 2f), h * 1.02f,
            w * next, h * 0.80f,
        )
        x = next
    }
}

/** 眼睛：六个形象各给一套，这是「不同的显示效果」真正落地的地方 */
private fun DrawScope.drawGhostFace(form: GhostForm, color: Color, alpha: Float, eye: Color) {
    val w = size.width
    val h = size.height
    val lw = Stroke(width = (w * 0.055f).coerceAtLeast(0.8f))

    when (form) {
        GhostForm.Round -> {
            dot(w * 0.38f, h * 0.44f, w * 0.070f, eye, alpha)
            dot(w * 0.62f, h * 0.44f, w * 0.070f, eye, alpha)
        }

        GhostForm.Square -> {
            // 方眼：跟方头呼应
            val s = w * 0.10f
            drawRect(eye.copy(alpha = alpha), Offset(w * 0.33f, h * 0.38f), Size(s, s))
            drawRect(eye.copy(alpha = alpha), Offset(w * 0.57f, h * 0.38f), Size(s, s))
        }

        GhostForm.Pointed -> {
            // 一只大眼：尖头那股「独眼」劲儿
            drawCircle(eye.copy(alpha = alpha), w * 0.13f, Offset(w * 0.50f, h * 0.46f))
            drawCircle(color.copy(alpha = alpha), w * 0.055f, Offset(w * 0.53f, h * 0.43f))
        }

        GhostForm.Tall -> {
            // 竖条眼
            drawLine(eye.copy(alpha = alpha), Offset(w * 0.40f, h * 0.36f), Offset(w * 0.40f, h * 0.52f), lw.width)
            drawLine(eye.copy(alpha = alpha), Offset(w * 0.60f, h * 0.36f), Offset(w * 0.60f, h * 0.52f), lw.width)
        }

        GhostForm.Blob -> {
            dot(w * 0.36f, h * 0.50f, w * 0.055f, eye, alpha)
            dot(w * 0.64f, h * 0.50f, w * 0.055f, alpha = alpha, c = eye)
            // 两颊腮红：团子专属
            drawCircle(color.copy(alpha = alpha * 0.35f), w * 0.06f, Offset(w * 0.24f, h * 0.58f))
            drawCircle(color.copy(alpha = alpha * 0.35f), w * 0.06f, Offset(w * 0.76f, h * 0.58f))
        }

        GhostForm.Horned -> {
            dot(w * 0.38f, h * 0.46f, w * 0.062f, eye, alpha)
            dot(w * 0.62f, h * 0.46f, w * 0.062f, eye, alpha)
            // 凶眉：有角的当然要有点脾气
            drawLine(eye.copy(alpha = alpha * 0.8f), Offset(w * 0.30f, h * 0.34f), Offset(w * 0.44f, h * 0.30f), lw.width)
            drawLine(eye.copy(alpha = alpha * 0.8f), Offset(w * 0.70f, h * 0.34f), Offset(w * 0.56f, h * 0.30f), lw.width)
        }
    }
}

private fun DrawScope.dot(x: Float, y: Float, r: Float, c: Color, alpha: Float) {
    drawCircle(c.copy(alpha = alpha), r, Offset(x, y))
}

/** 眼睛颜色：深浅两套，保证在任何洗色上都看得见 */
@Composable
private fun faceInk(): Color {
    val dark = cn.dkarbon.enroute.ui.theme.InkTokens.current.isDark
    return if (dark) Color(0xFF12100C) else Color(0xFF241F19)
}
