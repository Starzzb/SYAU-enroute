package cn.dkarbon.enroute.ui.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 墨洗登录输入行（2026-11-20 m34，取自 sharp-panda-27 的「左下墨线 + 标签上浮」）。
 *
 * 只画左边 + 底边两条 1.5dp 墨线，左下角 8dp 圆角连接 —— 细如毛笔，不用新拟物灰影。
 * 标签平时落在输入行内（灰字），聚焦或有内容时上浮到左上角、缩小、变成墨底纸字；
 * 墨块盖住左线顶端，形成「标签嵌在边框上」的观感。
 */
@Composable
fun InkLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
) {
    val t = InkTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val floated = focused || value.isNotEmpty()
    val labelY by animateDpAsState(
        targetValue = if (floated) 0.dp else 17.dp,
        animationSpec = tween(180),
        label = "loginLabelY",
    )
    val labelSize by animateFloatAsState(
        targetValue = if (floated) 10f else 14f,
        animationSpec = tween(180),
        label = "loginLabelSize",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        interactionSource = interaction,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        textStyle = TextStyle(fontSize = 15.sp, color = t.ink),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .drawBehind {
                val w = 1.5.dp.toPx()
                val r = 8.dp.toPx()
                val h = size.height
                // 左边 + 左下圆角 + 底边：一条连续墨线
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(0f, h - r)
                    quadraticBezierTo(0f, h, r, h)
                    lineTo(size.width, h)
                }
                drawPath(
                    path,
                    color = if (focused) t.ink else t.outline,
                    style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().height(58.dp)) {
                Box(Modifier.fillMaxWidth().padding(start = 14.dp, top = 26.dp, end = 10.dp)) {
                    inner()
                }
                Text(
                    label,
                    fontSize = labelSize.sp,
                    color = if (floated) t.paper else t.inkFaint,
                    modifier = Modifier
                        .padding(start = if (floated) 8.dp else 14.dp)
                        .offset(y = labelY)
                        .then(
                            if (floated) Modifier.background(t.ink, RoundedCornerShape(2.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                            else Modifier,
                        ),
                )
            }
        },
    )
}

/** 描边反白按钮：平时 1.5dp 墨框墨字，按下 / 悬停反白（墨底纸字）。 */
@Composable
fun InkOutlineButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val inverted = enabled && (hovered || pressed)
    val borderColor by animateColorAsState(
        targetValue = if (enabled) t.ink else t.outline,
        animationSpec = tween(140),
        label = "loginBtnBorder",
    )
    val bg by animateColorAsState(
        targetValue = if (inverted) t.ink else Color.Transparent,
        animationSpec = tween(120),
        label = "loginBtnBg",
    )
    val fg by animateColorAsState(
        targetValue = if (!enabled) t.inkFaint else if (inverted) t.paper else t.ink,
        animationSpec = tween(120),
        label = "loginBtnFg",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .then(
                Modifier.drawBehind {
                    val w = 1.5.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(width = w),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    )
                },
            )
            .clickable(interaction, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.5.sp, color = fg)
    }
}
