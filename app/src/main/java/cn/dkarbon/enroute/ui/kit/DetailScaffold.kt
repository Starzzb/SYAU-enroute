package cn.dkarbon.enroute.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 详情页的统一骨架：整页纸纹当「底」，正文装进一个「框」。
 *
 * 三层（见 docs/设计-m36-详情压底.md）：
 * 1. 底 —— 整页 [PaperTex] 纸纹，挂在滚动容器**外层**，纹随视口固定、不随内容滚；
 * 2. 面 —— [PaperFrame]：纸底 + 1dp 描边 + 压印，圈住眉行 / 标题 / 正文 / 落款；
 * 3. 字 —— 调用方排。
 *
 * 框里不重复铺纹。这条是从 m35 改过来的：m35 把正文直接摊在纹上，读起来发灰、发散；
 * 现在纹只做衬底，正文落在一块干净的纸上，框外左右各 14dp 让纹透出来。
 *
 * 进出动画：从被点条目矩形放大到整页（缩放 .94→1、位移 12dp→0、透明 0→1，220ms）。
 * 没有遮罩 —— 整页不是浮层，不存在"浮在别的东西上面"，也就不该压暗底下。
 */
@Composable
fun DetailScaffold(
    backLabel: String,
    tex: PaperTex,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 框底到屏幕底的距离，给页脚渐隐与手势条留位置 */
    bottomGap: Dp = 52.dp,
    /** 进来就停在正文末尾：长按出来的「看原始数据」用的就是这条 */
    initialScrollToEnd: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    BackHandler(enabled = true) { onBack() }

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220, easing = CubicBezierEasing(0.22f, 0.9f, 0.24f, 1f)),
        label = "detail-enter",
    )

    Box(
        modifier
            .fillMaxSize()
            // 先垫纸色再铺纹：paperTexture 只画纹线、不画底，缺这层会透出下层页面
            .background(t.paper)
            // 纹铺满整页；卷动在里层，所以纹不动
            .paperTexture(tex, t, radius = 0.dp)
            .graphicsLayer {
                val p = enter.value
                alpha = p
                scaleX = 0.94f + 0.06f * p
                scaleY = 0.94f + 0.06f * p
                translationY = (1f - p) * 12.dp.toPx()
            },
    ) {
        val scroll = rememberScrollState()
        // 长按进来是「要看原文」，不是「从头读一遍」：等首帧量完高度再一次跳到底
        LaunchedEffect(scroll.maxValue) {
            if (initialScrollToEnd && scroll.maxValue > 0) scroll.scrollTo(scroll.maxValue)
        }

        Column(Modifier.fillMaxSize()) {
            DetailBackRow(label = backLabel, onBack = onBack, actions = actions)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            ) {
                PaperFrame {
                    content()
                }
                Spacer(Modifier.height(bottomGap))
            }
        }

        // 页脚渐隐：内容滚到底时淡进纸色，不让字贴着屏幕边缘被切
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(62.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, t.paper))),
        )
    }
}

/** 返回行：44dp，左边 ‹ + 来由，右边交给调用方。三处详情共用同一个度量。 */
@Composable
fun DetailBackRow(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = InkTokens.current
    Row(
        modifier.fillMaxWidth().height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = t.inkSoft,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(label, fontSize = 10.5.sp, letterSpacing = 0.05.em, color = t.inkFaint)
        Spacer(Modifier.weight(1f))
        actions()
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * 正文框：纸底 + 1dp 描边 + 压印。
 *
 * 用 [inkPress] 而不是 `background + border` 两笔：inkPress 一次 [androidx.compose.ui.draw.drawBehind]
 * 就把底色渐变、上暗带、下亮带、内沿立体线画完，只需要再补一条可见的墨线边界。
 */
@Composable
fun PaperFrame(
    modifier: Modifier = Modifier,
    shapeRadius: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(shapeRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .inkPress(
                radius = shapeRadius,
                paperTop = t.card,
                paperBottom = t.card,
                dark = t.isDark,
                depth = 14.dp,
                feather = 10.dp,
            )
            .border(1.dp, t.outline, shape)
            .padding(contentPadding),
    ) {
        content()
    }
}

/** 分节：9sp 字距 .20em 的小标题 + 一条淡线。分节不各做一张小卡，都在同一个框里。 */
@Composable
fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    Column(modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text(
            title,
            fontSize = 9.sp,
            letterSpacing = 0.20.em,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.rule.copy(alpha = 0.45f)))
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 字段行：左标 68dp / 右值等宽。左标缺省时整行让给右值。 */
@Composable
fun DetailFieldRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueMono: Boolean = true,
) {
    val t = InkTokens.current
    Row(modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 11.5.sp,
            letterSpacing = 0.08.em,
            color = t.inkFaint,
            modifier = Modifier.width(68.dp),
        )
        Text(
            value,
            fontSize = 12.5.sp,
            color = t.ink,
            fontFamily = if (valueMono) cn.dkarbon.enroute.ui.theme.NumericFont else null,
            modifier = Modifier.weight(1f),
        )
    }
}
