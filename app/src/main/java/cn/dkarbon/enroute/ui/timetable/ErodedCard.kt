package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
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
 * 2026-11-17 主人修正：「侵蚀效果要减轻，保证进度条过去之后能看得到课程名称，还可以点击查看」。
 * 技术做法改了两处，顺序是**先洗后写字**：
 *   1) 旧做法用 `clipRect` 把上半截正文裁掉 —— 下课（progress≥1）时课名整块消失，卡片还被残影顶掉。
 *      现在**不裁正文**，正文永远整块画在最后，字压在洗色层之上，读得清。
 *   2) 掉色层只在正文背后把纸底洗淡（不透明度降权），咬痕半径与裂纹数量减半，
 *      吃过之后是「旧纸」而不是「空白」。
 * 点击/长按由调用方挂在 [modifier] 上，跟渲染无关，所以卡片一直可点。
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
                val p = progress.coerceIn(0f, 1f)
                val cut = size.height * p

                // 1) 先洗纸底：被吃掉的高度里，卡片色洗成旧纸色（只动背景，不动字）
                if (p > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(gone, faded.copy(alpha = 0.55f), faded.copy(alpha = 0.18f)),
                            startY = 0f,
                            endY = cut.coerceAtLeast(1f),
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, cut),
                    )

                    // 2) 咬痕：一排纸色半圆压在洗色层的下缘，形成不规则缺口（CET6 同款）。
                    //    2026-11-17 收小到 0.18–0.24 步宽，避免啃到课名。
                    if (p > 0.05f) {
                        val teeth = 7
                        val step = size.width / teeth
                        for (i in 0 until teeth) {
                            val wobble = sin((p * 40f + i * 1.7f)) * size.height * 0.02f
                            val r = step * (0.18f + (i % 3) * 0.03f)
                            drawCircle(
                                color = paper,
                                radius = r,
                                center = Offset(step * (i + 0.5f), cut + wobble),
                            )
                        }
                    }

                    // 3) 侵蚀区里的细小裂纹：只到 4 条、透明度减半，落在字的缝隙里
                    val cracks = (p * 6).toInt().coerceAtMost(4)
                    repeat(cracks) { i ->
                        val cx = size.width * (0.12f + i * 0.22f)
                        val cy = cut * (0.35f + (i % 3) * 0.18f)
                        drawLine(
                            color = (if (isDark) Color(0xFF6E675C) else Color(0xFF9C948A))
                                .copy(alpha = 0.18f),
                            start = Offset(cx, cy),
                            end = Offset(cx + size.width * 0.03f, cy + cut * 0.08f),
                            strokeWidth = 0.6.dp.toPx(),
                        )
                    }
                }

                // 4) 正文最后画：整块，永不裁切 —— 进度条过去之后课名照样在、照样点得开
                this@drawWithContent.drawContent()
            },
    ) {
        content()
    }
}
