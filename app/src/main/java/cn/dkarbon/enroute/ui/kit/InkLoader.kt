package cn.dkarbon.enroute.ui.kit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.dkarbon.enroute.ui.theme.InkTokens
import kotlin.math.abs
import kotlin.math.cos

/**
 * 墨洗加载动画（2026-11-20 m34，取自 grumpy-dolphin-30 的五片翻页语汇）。
 *
 * 五片纸沿左边缘为轴依次 rotateY(0 → −180°)，翻到背面后淡出；
 * 片宽按 |cos(θ)| 收缩模拟透视，翻过 90° 后镜像到轴左侧，形成「书页依次掀过」。
 * 五片相位差 150ms，周期 2s，颜色 ink → inkSoft → slate → olive → amber。
 *
 * 三档：[InkLoader] 30dp（默认）、[InkTinyLoader] 16dp（行内）、[InkBigLoader] 44dp（整页）。
 * 全 Canvas 绘制，无子 Composable，无额外依赖。
 */
@Composable
private fun InkLoaderImpl(side: Dp, modifier: Modifier = Modifier) {
    val t = InkTokens.current
    val infinite = rememberInfiniteTransition(label = "inkLoaderLoop")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "inkLoaderProgress",
    )
    val colors = listOf(t.ink, t.inkSoft, t.slate, t.olive, t.amber)

    Canvas(modifier.size(side)) {
        val s = side.toPx()
        val axis = s * 0.5f
        for (i in 0 until 5) {
            // 相位：每片错开 150ms（占 2s 周期的 0.075）
            val phase = (((progress - i * 0.075f) % 1f) + 1f) % 1f
            val theta = if (phase < 0.5f) (phase / 0.5f) * 180f else 180f
            val projected = (abs(cos(Math.toRadians(theta.toDouble()))) * s)
                .toFloat().coerceAtLeast(0.6f)
            val alpha = when {
                phase < 0.5f -> 0.95f - 0.40f * (phase / 0.5f)
                phase < 0.9f -> 0.55f
                else -> 0.55f * (1f - (phase - 0.9f) / 0.1f)
            }.coerceIn(0f, 1f)
            // 翻过 90° 后镜像到轴左侧（对齐 CSS transform-origin:left 的翻面朝向）
            val left = if (phase < 0.5f) axis else axis - projected
            drawRect(
                color = colors[i],
                topLeft = Offset(left, 0f),
                size = Size(projected, s),
                alpha = alpha,
            )
        }
    }
}

/** 30dp 标准加载动画（列表 / 卡片内主指示器）。 */
@Composable
fun InkTinyLoader(modifier: Modifier = Modifier) = InkLoaderImpl(16.dp, modifier)
