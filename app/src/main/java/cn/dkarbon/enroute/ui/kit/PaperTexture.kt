package cn.dkarbon.enroute.ui.kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 纸纹：页面底与卡片内的 1px 级底纹，共八款。
 *
 * 来源（2026-11-20 主人指定四款参考，2026-11-20 晚追加四款自创）：
 * · [WEAVE]   uiverse.io/kandalgaonkarshubham/lovely-donkey-52 —— 斜向双层编织 + 竖向细导引。
 * · [GRID]    uiverse.io/Gautammsharma/brown-badger-26 —— 24dp 方格 + 交点圆点。
 * · [CROSS]   uiverse.io/lucas_5777/fluffy-chipmunk-60 —— 30dp 方格，交点刻短十字。
 * · [RIPPLE]  同心弧，圆心落在页脚外，裁切处只余平缓弧线。
 * · [SCALLOP] 半圆叠瓦，密而不乱，适合密集卡片页。
 * · [DOTS]    方阵细点，像纸面纤维，最轻，正文页不抢字。
 * · [CRACK]   哥窑开片式斜折裂纹，唯一不规则款，固定点表，逐帧一致不闪。
 *
 * 硬约束（见 docs/设计-m36-详情压底.md）：
 * 1. 浓度总和不超过 18%，否则正文开始发灰；
 * 2. 正文框里不重复铺纹 —— 纹是「底」，框是「面」；
 * 3. 一张卡只用一款；
 * 4. 数据卡密集时 α 再乘 0.6；个人身份卡不铺纹理。
 *
 * 实现：纯 [drawBehind]，无位图、无离屏、无额外依赖。
 */
enum class PaperTex { NONE, WEAVE, GRID, CROSS, RIPPLE, SCALLOP, DOTS, CRACK, EDGE }

/** 纹理线的颜色：亮档用墨、暗档用纸，暗档整体再淡三成。 */
private fun texInk(t: InkTokenSet, alpha: Float): Color {
    val base = if (t.isDark) t.ink else t.inkFaint
    return base.copy(alpha = if (t.isDark) alpha * 0.72f else alpha)
}

/**
 * 在底上铺一层纸纹。用作卡片时调用位置要放在 `border` **之前**，让纹理随圆角一起被裁；
 * 用作整页底时传 `radius = 0.dp`，且要挂在滚动容器外层（纹随视口固定，不随内容滚）。
 *
 * @param radius 与宿主圆角一致，纹理不会溢出圆角
 */
fun Modifier.paperTexture(
    tex: PaperTex,
    t: InkTokenSet,
    radius: Dp = 10.dp,
): Modifier {
    if (tex == PaperTex.NONE) return this
    return this
        .clip(RoundedCornerShape(radius))
        .drawBehind {
            when (tex) {
                PaperTex.GRID -> drawGrid(t)
                PaperTex.CROSS -> drawCross(t)
                PaperTex.WEAVE -> drawWeave(t)
                PaperTex.RIPPLE -> drawRipple(t)
                PaperTex.SCALLOP -> drawScallop(t)
                PaperTex.DOTS -> drawDots(t)
                PaperTex.CRACK -> drawCrack(t)
                PaperTex.EDGE -> drawEdge(t)
                PaperTex.NONE -> Unit
            }
        }
}

/** 24dp 方格 + 交点 1.1dp 圆点。 */
private fun DrawScope.drawGrid(t: InkTokenSet) {
    val step = 24.dp.toPx()
    val sw = 1.dp.toPx()
    val line = texInk(t, 0.052f)
    var x = step
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = sw)
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = sw)
        y += step
    }
    val dot = texInk(t, 0.095f)
    val r = 1.1.dp.toPx()
    var gy = step
    while (gy < size.height) {
        var gx = step
        while (gx < size.width) {
            drawCircle(dot, radius = r, center = Offset(gx, gy))
            gx += step
        }
        gy += step
    }
}

/** 30dp 方格，交点上刻 7dp 的短十字（横 + 竖各一道）。 */
private fun DrawScope.drawCross(t: InkTokenSet) {
    val step = 30.dp.toPx()
    val arm = 3.5.dp.toPx()
    val sw = 1.dp.toPx()
    val line = texInk(t, 0.090f)
    var gy = step
    while (gy < size.height) {
        var gx = step
        while (gx < size.width) {
            drawLine(line, Offset(gx - arm, gy), Offset(gx + arm, gy), strokeWidth = sw)
            drawLine(line, Offset(gx, gy - arm), Offset(gx, gy + arm), strokeWidth = sw)
            gx += step
        }
        gy += step
    }
}

/** 27dp 步距的斜向编织：+30° / −30° 两道斜线 + 一条竖向细导引。 */
private fun DrawScope.drawWeave(t: InkTokenSet) {
    val step = 27.dp.toPx()
    val sw = 1.dp.toPx()
    val diag = texInk(t, 0.050f)
    val vert = texInk(t, 0.032f)
    val w = size.width
    val h = size.height
    val skew = h * 0.577f // tan30°

    var c = -h
    while (c < w + skew) {
        drawLine(diag, Offset(c, 0f), Offset(c + skew, h), strokeWidth = sw)
        c += step
    }
    c = -skew
    while (c < w + h) {
        drawLine(diag, Offset(c + skew, 0f), Offset(c, h), strokeWidth = sw)
        c += step
    }
    var x = step * 0.5f
    while (x < w) {
        drawLine(vert, Offset(x, 0f), Offset(x, h), strokeWidth = sw)
        x += step * 2f
    }
}


/** 同心弧：圆心落在容器下缘以外 18% 处，只让平缓的一段弧线穿过版面。 */
private fun DrawScope.drawRipple(t: InkTokenSet) {
    val gap = 22.dp.toPx()
    val sw = 0.6.dp.toPx()
    val ink = texInk(t, 0.055f)
    val cx = size.width * 0.5f
    val cy = size.height * 1.18f
    var r = gap
    val maxR = size.height * 1.25f
    while (r < maxR) {
        drawCircle(ink, radius = r, center = Offset(cx, cy), style = Stroke(width = sw))
        r += gap
    }
}

/** 半圆叠瓦：28dp × 17dp 一格，格顶中点画半径 13.35dp 的下半圆，奇数行错开半格。 */
private fun DrawScope.drawScallop(t: InkTokenSet) {
    val gw = 28.dp.toPx()
    val gh = 17.dp.toPx()
    val sw = 0.7.dp.toPx()
    val r = 13.35.dp.toPx()
    val ink = texInk(t, 0.055f)
    var row = 0
    var y = 0f
    while (y < size.height + gh) {
        val shift = if (row % 2 == 0) 0f else gw * 0.5f
        var x = -gw + shift
        while (x < size.width + gw) {
            drawArc(
                color = ink,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(x + gw * 0.5f - r, y - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = Stroke(width = sw),
            )
            x += gw
        }
        y += gh
        row++
    }
}

/** 方阵细点：12dp 步距，0.9dp 点，像纸面纤维。 */
private fun DrawScope.drawDots(t: InkTokenSet) {
    val step = 12.dp.toPx()
    val r = 0.9.dp.toPx()
    val ink = texInk(t, 0.070f)
    var gy = step * 0.5f
    while (gy < size.height) {
        var gx = step * 0.5f
        while (gx < size.width) {
            drawCircle(ink, radius = r, center = Offset(gx, gy))
            gx += step
        }
        gy += step
    }
}

/**
 * 冰裂（哥窑开片）：120dp 见方一组五条斜折裂纹，固定点表 —— 同机同尺寸逐帧一致，不会闪。
 * 归一坐标，x 与 y 都按 0..1 落在格内。
 */
/**
 * 边缘深度：没有纹样，只有四边向内压暗、上沿一线亮边。
 *
 * 像一张纸被压进凹槽里 —— 卡面上没有任何会抢字的笔划，
 * 所以它也能用在内容最密的盒子上（教务数据那块）。
 */
private fun DrawScope.drawEdge(t: InkTokenSet) {
    val shade = if (t.isDark) Color.Black else t.ink
    val aTop = if (t.isDark) 0.55f else 0.105f
    val aBottom = if (t.isDark) 0.42f else 0.075f
    val aSide = if (t.isDark) 0.34f else 0.055f
    val span = 18.dp.toPx().coerceAtMost(size.minDimension * 0.45f)

    // 上深下浅：光从上方来（与压印阴影同一套物理直觉）
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(shade.copy(alpha = aTop), Color.Transparent),
            startY = 0f,
            endY = span,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, span),
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, shade.copy(alpha = aBottom)),
            startY = size.height - span,
            endY = size.height,
        ),
        topLeft = Offset(0f, size.height - span),
        size = Size(size.width, span),
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(shade.copy(alpha = aSide), Color.Transparent),
            startX = 0f,
            endX = span,
        ),
        topLeft = Offset.Zero,
        size = Size(span, size.height),
    )
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, shade.copy(alpha = aSide)),
            startX = size.width - span,
            endX = size.width,
        ),
        topLeft = Offset(size.width - span, 0f),
        size = Size(span, size.height),
    )

    // 上沿一线亮边：纸边被压出的一道高光
    drawLine(
        color = (if (t.isDark) Color.White else Color.White).copy(alpha = if (t.isDark) 0.06f else 0.55f),
        start = Offset(1.dp.toPx(), 0.5f.dp.toPx()),
        end = Offset(size.width - 1.dp.toPx(), 0.5f.dp.toPx()),
        strokeWidth = 1.dp.toPx(),
    )
}

private fun DrawScope.drawCrack(t: InkTokenSet) {
    val step = 120.dp.toPx()
    val sw = 0.8.dp.toPx()
    val ink = texInk(t, 0.100f)
    val strokes = arrayOf(
        floatArrayOf(0f, 34f, 28f, 30f, 46f, 42f, 74f, 38f, 98f, 52f, 120f, 48f),
        floatArrayOf(18f, 0f, 22f, 26f, 34f, 52f, 30f, 82f, 42f, 120f),
        floatArrayOf(120f, 74f, 86f, 78f, 60f, 70f, 34f, 80f, 0f, 74f),
        floatArrayOf(60f, 0f, 58f, 22f, 70f, 40f, 66f, 60f),
        floatArrayOf(96f, 0f, 92f, 24f, 104f, 44f),
    )
    var oy = 0f
    while (oy < size.height) {
        var ox = -step
        while (ox < size.width) {
            for (pts in strokes) {
                val path = Path()
                path.moveTo(ox + pts[0], oy + pts[1])
                var i = 2
                while (i < pts.size) {
                    path.lineTo(ox + pts[i], oy + pts[i + 1])
                    i += 2
                }
                drawPath(path, ink, style = Stroke(width = sw))
            }
            ox += step
        }
        oy += step
    }
}
