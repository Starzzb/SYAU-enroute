package cn.dkarbon.enroute.ui.kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 压印（paper press）：一张宣纸被压出浅凹槽的质感。
 *
 * 参考件（uiverse `javierBarroso/yellow-panda-85`）在浏览器里用一条 CSS 写成的：
 * ```
 * box-shadow: inset 0 18px 16px rgba(44,44,44,.055),
 *             inset 0 -18px 16px rgba(255,255,255,.85),
 *             1px 1px 2px rgba(255,255,255,.9),
 *            -1px -1px 2px rgba(44,44,44,.08);
 * ```
 * 拆开看是四件事：**上缘一道向内的暗带**（凹槽吃光）、**下缘一道向内的亮带**（唇口受光）、
 * **左上亮边 / 右下暗边的一像素立体外沿**、以及**由上到下极浅的底色渐变**。
 *
 * Compose 没有 inset box-shadow（`shadow()` 只能画在外侧），所以这里逐层画，
 * 全部落在一次 [drawBehind] 里，顺序完全可控，也不用模糊（比 blur 便宜得多）：
 *
 * 1. 底色 —— 垂直渐变，[paperTop] → [paperBottom]，就是 CSS 的 `linear-gradient(180deg, …)`；
 * 2. 上带 —— `verticalGradient(shade → 透明)`，长度 [depth] + [feather]（对齐 CSS 的 18 + 16）；
 * 3. 下带 —— `verticalGradient(透明 → sheen)`，同样长度；
 * 4. 外沿 —— `drawRoundRect` 用「左上亮 → 右下暗」的对角渐变描一圈 1dp，内缩半像素避免被裁掉一半。
 *
 * 整块被 [clip] 到圆角内，所以两条内带自然沿圆角收口，不会露出方角。
 *
 * @param radius 圆角半径；内带与外沿都按它收口，必须与外部形状一致。
 * @param paperTop 上缘纸色（亮档通常是 card，暗档是 card）。
 * @param paperBottom 下缘纸色（通常 cardAlt，暗档会更亮一点表示受光）。
 * @param dark 暗档开关，只影响四条阴影的颜色与强度 —— 亮档的白不能带到暗档，
 *   否则深纸会被一圈强白光洗成灰白（原型第一版就踩过这个坑）。
 */
fun Modifier.inkPress(
    radius: Dp,
    paperTop: Color,
    paperBottom: Color,
    dark: Boolean,
    depth: Dp = 18.dp,
    feather: Dp = 16.dp,
    edgeWidth: Dp = 1.dp,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val shade = if (dark) Color.Black.copy(alpha = 0.50f) else Color(0xFF2C2C2C).copy(alpha = 0.055f)
    val sheen = if (dark) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.85f)
    val edgeLight = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.90f)
    val edgeDark = if (dark) Color.Black.copy(alpha = 0.60f) else Color(0xFF2C2C2C).copy(alpha = 0.10f)
    val band = depth + feather

    return this
        .clip(shape)
        .drawBehind {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@drawBehind

            // 1. 底色：由上到下的极浅渐变
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(paperTop, paperBottom),
                    startY = 0f,
                    endY = h,
                    tileMode = TileMode.Clamp,
                ),
            )

            // 带子占全高的比例；卡片很矮时（折角帧、行内小卡）收到 50% 就够，别把两条带压成一块灰
            val t = (band.toPx() / h).coerceIn(0.02f, 0.5f)

            // 2. 上缘暗带
            drawRect(
                brush = Brush.verticalGradient(
                    0f to shade,
                    t to Color.Transparent,
                    startY = 0f,
                    endY = h,
                    tileMode = TileMode.Clamp,
                ),
            )

            // 3. 下缘亮带
            drawRect(
                brush = Brush.verticalGradient(
                    (1f - t) to Color.Transparent,
                    1f to sheen,
                    startY = 0f,
                    endY = h,
                    tileMode = TileMode.Clamp,
                ),
            )

            // 4. 立体外沿：左上一道亮、右下一道暗，描在内侧半像素处
            val inset = 0.5.dp.toPx()
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(edgeLight, edgeDark),
                    start = Offset.Zero,
                    end = Offset(w, h),
                ),
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2f, h - inset * 2f),
                cornerRadius = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
                style = Stroke(width = edgeWidth.toPx()),
            )
        }
}
