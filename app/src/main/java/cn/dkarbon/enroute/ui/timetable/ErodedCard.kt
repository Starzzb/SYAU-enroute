package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.sin

/**
 * 课程卡的「侵蚀」渲染。
 *
 * 主人设定的机制（2026-10-26）：
 *   · 小幽灵从**上往下**吃，吃掉多少，卡片就从顶部被侵蚀多少
 *   · 侵蚀区不是直接消失，而是**掉色**：从暖纸洗色 → 灰白 → 半透明
 *   · 边缘有**不规则咬痕**（不是一条直线）
 *   · 吃到一半时，上半截已褪色，下半截仍是原色 —— 视觉上能"读出进度"
 *
 * 技术做法：把「已吃高度」当作裁切边界，在裁切区之上叠加一层
 * 向下的灰色渐变蒙版，配合纸色小圆打出的咬痕。
 */
@Composable
fun ErodedCard(
    wash: Color,          // 卡片原色（暖纸洗色）
    spine: Color,         // 竖脊满彩色
    progress: Float,      // 0 = 完整，1 = 吃光
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 暗夜里纸底换成深色，掉色/侵蚀两档也要跟着压暗，
    // 否则黑色网格上会出现几块发白的「亮斑」，比不侵蚀还难看。
    val isDark = cn.dkarbon.enroute.ui.theme.InkTokens.current.isDark
    val paper = if (isDark) Color(0xFF17150F) else Color(0xFFF7F3EA)
    val faded = if (isDark) Color(0xFF3A362E) else Color(0xFFDDD8CF)   // 掉色后的灰
    val gone = if (isDark) Color(0xFF211E18) else Color(0xFFEFEBE2)    // 完全侵蚀后的底

    // 2026-11-13 回退 CET6 普通样式：卡片允许小圆角（主人点名「和 cet6 一样」）。
    // 票 31：改走形状 token —— HARD 下仍是 4dp（`ShapeSet.Hard.courseCard`），零变化；
    // 若将来这张侵蚀卡也进账页，会自动取账页的 10dp。
    val shape = cn.dkarbon.enroute.ui.theme.Form.current.shapes.courseCard
    Box(
        modifier
            .clip(shape)
            .background(wash)
            .border(0.75.dp, spine.copy(alpha = 0.30f), shape)
            .drawWithContent {
                val cut = size.height * progress.coerceIn(0f, 1f)

                // 1) 先画原卡片（只画未被吃掉的下半部分）
                clipRect(top = cut) { this@drawWithContent.drawContent() }

                // 2) 被吃掉的上半区：覆盖掉色层
                if (progress > 0f) {
                    // 顶部到底部的掉色渐变（越靠上越灰、越透明）
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(gone, faded.copy(alpha = 0.85f), faded.copy(alpha = 0.4f)),
                            startY = 0f,
                            endY = cut.coerceAtLeast(1f),
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, cut),
                    )

                    // 3) 咬痕：一排纸色半圆盖住裁切硬边，形成不规则缺口（CET6 同款）。
                    //    相位由 progress 驱动，避免逐帧跳动。
                    if (progress > 0.05f) {
                        val teeth = 7
                        val step = size.width / teeth
                        for (i in 0 until teeth) {
                            val wobble = sin((progress * 40f + i * 1.7f)) * size.height * 0.035f
                            val r = step * (0.34f + (i % 3) * 0.06f)
                            drawCircle(
                                color = paper,
                                radius = r,
                                center = Offset(step * (i + 0.5f), cut + wobble),
                            )
                        }
                    }

                    // 4) 侵蚀区里的细小裂纹（短竖线，随进度增多）
                    val cracks = (progress * 12).toInt().coerceAtMost(8)
                    repeat(cracks) { i ->
                        val cx = size.width * (0.12f + i * 0.11f)
                        val cy = cut * (0.35f + (i % 3) * 0.18f)
                        drawLine(
                            color = (if (isDark) Color(0xFF6E675C) else Color(0xFF9C948A))
                                .copy(alpha = 0.35f),
                            start = Offset(cx, cy),
                            end = Offset(cx + size.width * 0.03f, cy + cut * 0.10f),
                            strokeWidth = 0.6.dp.toPx(),
                        )
                    }
                }
            },
    ) {
        content()
    }
}

/**
 * 被完全吃掉后留下的痕迹：不是空白，是一道褪色的短划 + 淡淡的残影。
 */
@Composable
fun EatenResidue(modifier: Modifier = Modifier) {
    val dark = cn.dkarbon.enroute.ui.theme.InkTokens.current.isDark
    val spine = if (dark) Color(0xFFD9796A) else Color(0xFFA63D2F)
    val ash = if (dark) Color(0xFF4A453C) else Color(0xFFB9B2A6)

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // 一道砖红短划（表示"这里有过一节课"）
            drawLine(
                color = spine.copy(alpha = 0.28f),
                start = Offset(size.width * 0.18f, size.height * 0.30f),
                end = Offset(size.width * 0.52f, size.height * 0.30f),
                strokeWidth = 1.6.dp.toPx(),
            )
            // 几粒灰烬
            repeat(3) { i ->
                drawCircle(
                    color = ash.copy(alpha = 0.35f),
                    radius = 0.9.dp.toPx(),
                    center = Offset(
                        size.width * (0.24f + i * 0.10f),
                        size.height * (0.46f + i * 0.06f),
                    ),
                )
            }
        }
    }
}
