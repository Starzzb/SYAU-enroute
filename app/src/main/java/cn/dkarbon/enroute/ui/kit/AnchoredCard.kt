package cn.dkarbon.enroute.ui.kit

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 毛玻璃浮层：**锚定卡片**（形态 C，主人 2026-11-20 定案）。
 *
 * 为什么不是底部抽屉：抽屉盖住整屏，用户看完不知道退回哪里；返回键一按容易退掉 Activity。
 * 锚定卡片从触发它的那个按钮（⋯）下方长出来，折角指回按钮 —— 「我从哪来、点外面就回去」
 * 靠形态说清，不写一个字。
 *
 * 玻璃三档（厚的背后更透，薄的字更清楚）：
 * - API 31+ 有 RenderEffect：调用方用 [backdropBlur] 把背后内容真模糊，卡片只盖半透明纸。
 * - API 29–30 没有：靠三层半透明纸叠出等效不透明度（[GlassLevel.opaquePaper]），遮罩同时加深，
 *   否则背后文字会和卡片正文抢对比度。
 */

/** 玻璃厚度。T2 是默认档（主人选定）。 */
enum class GlassLevel(
    /** 背后内容的真模糊半径（API 31+ 生效） */
    val blur: Dp,
    /** API 31+：卡片纸底不透明度（真模糊在下面，可以更透） */
    val glassPaperLight: Float,
    val glassPaperDark: Float,
    /** API 29–30：卡片纸底第一层不透明度（没有模糊，得自己堆出等效） */
    val opaquePaperLight: Float,
    val opaquePaperDark: Float,
) {
    T1(12.dp, 0.86f, 0.90f, 0.97f, 0.975f),
    T2(20.dp, 0.72f, 0.78f, 0.97f, 0.975f),
    T3(28.dp, 0.58f, 0.66f, 0.96f, 0.97f),
}

/**
 * 纸纹瓦片（48×48 灰度噪点，`TileMode.Repeated` 平铺）。
 *
 * 为什么需要它：玻璃/纸片效果的「质感」大半来自表面颗粒。纯色 + 半透明在真机上看起来
 * 就是一块塑料贴纸（主人 2026-11-20 的原话：「软件内效果和 html 差别很大」）。
 * 噪点用固定种子生成，跨帧、跨设备一致，不必每帧重算。
 */
private fun grainTile(): ImageBitmap {
    val n = 48
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = java.util.Random(20261120L)
    val px = IntArray(n * n)
    for (i in px.indices) {
        val a = rnd.nextInt(26)          // 0–25 的 alpha：能看出颗粒，又不脏
        val v = 0x10 + rnd.nextInt(24)   // 灰褐色，跟纸底同色系
        px[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    bmp.setPixels(px, 0, n, 0, 0, n, n)
    return bmp.asImageBitmap()
}

/** 这台机器能不能做真背景模糊（RenderEffect 从 API 31 起） */
val canBlurBackdrop: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 给「卡片背后的那块内容」加模糊。**必须加在背板上**，不能加在卡片上 ——
 * Compose 的 `Modifier.blur` 只模糊自己，模糊卡片等于把卡片内容糊掉。
 *
 * @param active 只在浮层打开时开，关掉即归零（常开等于每帧多刷一遍背板）
 */
fun Modifier.backdropBlur(active: Boolean, level: GlassLevel = GlassLevel.T2): Modifier =
    if (!active) this
    else if (canBlurBackdrop) this.blur(level.blur, edgeTreatment = BlurredEdgeTreatment.Unbounded)
    else this

/**
 * 锚定卡片浮层。铺满父容器的 `Box`，内含遮罩 + 卡片本体。
 *
 * @param anchor 触发按钮在**根坐标**里的矩形（`onGloballyPositioned { it.boundsInRoot() }`）；
 *   传 null 时退回右上角定位（没有锚点可指的场合）。
 * @param accent 顶部 2dp 强调条颜色，按页面取色（资讯砖红 / 地图苔绿 / 学业靛青 / 待办赭黄）。
 * @param onDismiss 关闭回调。**动画播完才调**，调用方只需要把状态置 false。
 * @param maxHeight 卡片内容最大高度（超出在卡内滚，不撑破屏幕）。
 */
@Composable
fun AnchoredCardOverlay(
    anchor: Rect?,
    accent: Color,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.T2,
    width: Dp = 304.dp,
    maxHeight: Dp = 560.dp,
    /** 实心档：要精确读数/要确认的浮层不走玻璃，纸底完全不透明（设计结论见 docs/design/m31-三点浮层-统一.html） */
    solid: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.dp
    val screenH = config.screenHeightDp.dp

    val margin = Space.s
    val arrowHalf = 7.dp

    // 位置：默认从按钮下方 8dp 长出；贴边则往回收，折角跟着走
    // anchor 是 px 矩形（boundsInRoot），这里先换成 Dp 再参与排版 —— 混着算是 Dp/Float 不兼容
    val anchorLeft = anchor?.let { with(density) { it.left.toDp() } }
    val anchorBottom = anchor?.let { with(density) { it.bottom.toDp() } }
    val anchorCenterX = anchor?.let { with(density) { it.center.x.toDp() } }

    val gap = Space.s
    val rawLeft = anchorLeft ?: (screenW - width - margin)
    val left = rawLeft.coerceIn(margin, (screenW - width - margin).coerceAtLeast(margin))
    val rawTop = anchorBottom?.plus(gap) ?: (margin + 56.dp)
    // 放不下就上移，别让卡片被切掉
    val estH = maxHeight.coerceAtMost(screenH * 0.7f)
    val top = rawTop.coerceIn(margin + arrowHalf, (screenH - estH - margin).coerceAtLeast(margin + arrowHalf))
    val arrowX = anchorCenterX?.let {
        (it - left).coerceIn(20.dp, (width - 20.dp).coerceAtLeast(20.dp))
    } ?: (width - 28.dp)

    var closing by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    // 开口动画：0.9 倍缩放 + 上移 8dp + 淡入，220ms；折角先亮、卡片再落位（一拍半的感觉）
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }
    val close: () -> Unit = { closing = true }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(140, easing = LinearOutSlowInEasing))
            onDismiss()
        }
    }
    // 返回键只关这一层。旧版浮层没接这个，按返回直接退掉 Activity —— 主人 2026-11-20 报的正是它。
    BackHandler(enabled = true) { close() }

    // 遮罩：浅纸底上 0.34 压不暗，卡片会「浮」不起来；有真模糊时反而要浅（模糊本身已经分层）
    val scrim = when {
        canBlurBackdrop -> 0.24f
        t.isDark -> 0.46f
        else -> 0.38f
    }
    val pivotFrac = (arrowX.value / width.value).coerceIn(0.12f, 0.88f)

    Box(modifier.fillMaxSize()) {
        // 遮罩：点空白处关（表单类浮层不该这样，但纯操作菜单应该）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim * progress.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭",
                    onClick = close,
                ),
        )

        Box(
            Modifier
                .offset {
                    IntOffset(
                        with(density) { left.roundToPx() },
                        with(density) { top.roundToPx() },
                    )
                }
                .width(width)
                .graphicsLayer {
                    val v = progress.value
                    alpha = v
                    val s = 0.9f + 0.1f * v
                    scaleX = s
                    scaleY = s
                    translationY = (1f - v) * -8.dp.toPx()
                    transformOrigin = TransformOrigin(pivotFrac, 0f)
                },
        ) {
            // 折角：旋转 45° 的小方块，指回触发按钮
            if (anchor != null) {
                Box(
                    Modifier
                        .offset { IntOffset(0, 0) }
                        .padding(start = arrowX - arrowHalf)
                        .size(arrowHalf * 2)
                        .graphicsLayer {
                            rotationZ = 45f
                            alpha = progress.value
                        }
                        .background(
                            if (solid) {
                                t.paper
                            } else if (canBlurBackdrop) {
                                t.paper.copy(alpha = if (t.isDark) level.glassPaperDark + 0.06f else level.glassPaperLight + 0.06f)
                            } else {
                                t.paper.copy(alpha = if (t.isDark) level.opaquePaperDark else level.opaquePaperLight)
                            },
                            RoundedCornerShape(2.dp),
                        ),
                )
            }

            GlassPanel(level = level, solid = solid) {
                Column(
                    Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(accent))
                    content()
                    Spacer(Modifier.height(Space.xs))
                }
            }
        }
    }
}

/**
 * 玻璃面板本体：纸底 + 顶光 + 底影 + 1dp 描边。
 *
 * 三层纸叠法（API<31）参考 `docs/design/m31-三点浮层-统一.html` §玻璃；
 * 真机有 RenderEffect 时只盖一层半透明纸，透出的是**真的模糊图**。
 */
@Composable
fun GlassPanel(
    level: GlassLevel,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(14.dp)
    val baseAlpha = when {
        solid -> 1f
        canBlurBackdrop -> if (t.isDark) level.glassPaperDark else level.glassPaperLight
        else -> if (t.isDark) level.opaquePaperDark else level.opaquePaperLight
    }
    val grain = remember { grainTile() }
    // 平铺噪点：ShaderBrush + ImageShader（Brush.bitmap 在本版 Compose 里没有）
    val grainBrush = remember(grain) {
        ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
    }
    Box(
        modifier
            // 浅纸底上 18dp 8% 的软影等于没影：抬高度、加深，卡片才「离纸面一截」
            .shadow(
                elevation = if (solid) 8.dp else 26.dp,
                shape = shape,
                ambientColor = t.hardShadow.copy(alpha = 0.16f),
                spotColor = t.hardShadow.copy(alpha = 0.26f),
            )
            .clip(shape)
            .background(t.paper.copy(alpha = baseAlpha))
            // 表面颗粒：玻璃与纸片的区别就在这里（4% 左右，过了就成噪点电视）
            .background(grainBrush, alpha = if (solid) 0.025f else 0.06f),
    ) {
        // 没有真模糊（API < 31）：纸底已经接近不透明，只叠一层很淡的纵向纸影给一点厚度，
        // 不再靠「第二层半透明纸」凑不透明度 —— 真机实测那样凑完仍有 30% 的列表文字透出来
        if (solid) {
            // 实心档：不叠任何半透明层，避免「看起来像玻璃又看不清字」
        } else if (!canBlurBackdrop) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                t.ink.copy(alpha = 0.035f),
                            ),
                        ),
                    ),
            )
        } else {
            // 有真模糊：只加一点向上的高光，别把背后糊成一片
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                t.paper.copy(alpha = 0.14f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        // 顶光 / 底影各 1dp：玻璃需要一个「厚度」边，否则像块塑料贴纸
        Box(
            Modifier
                .matchParentSize()
                .border(Stroke.hairline, t.outline.copy(alpha = 0.62f), shape),
        )
        // 顶 1dp 高光 + 底 1dp 阴影：一块玻璃/纸片的上下边不一样亮，才有厚度
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to t.paper.copy(alpha = if (t.isDark) 0.10f else 0.62f),
                        0.06f to Color.Transparent,
                        0.94f to Color.Transparent,
                        1f to t.ink.copy(alpha = 0.10f),
                    ),
                ),
        )
        Column(content = content)
    }
}

