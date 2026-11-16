package cn.dkarbon.enroute.ui.settings

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.UpdateDialogPolicy
import cn.dkarbon.enroute.data.UpdateInfo
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.primaryInk
import kotlinx.coroutines.delay

/**
 * 更新提醒弹窗 · 方案 B「里程表」（定稿，文档 §2.3）。
 *
 * 不用 `AlertDialog`：主体可滚动 + 页脚固定，用 `Box` + 底部 sheet 自己拼。
 * 里程表是唯一动效焦点：逐位比较，只滚有变化的位，滚到位停住不循环。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    fromCode: Long,
    mandatory: Boolean,
    onUpdateNow: () -> Unit,
    onGotIt: () -> Unit,
    onDismissVersion: () -> Unit,
) {
    val t = InkTokens.current
    val f = Form.current
    val ctx = LocalContext.current
    // 「prefers-reduced-motion」等价开关：系统动画时长调成 0 时直接跳到位（文档 §5）
    val reducedMotion = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val notes = remember(info.notes) { UpdateDialogPolicy.parseNotes(info.notes) }
    val slice = remember(notes) { UpdateDialogPolicy.notesSlice(notes) }
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // 遮罩不设点击：三个出口必须显式选一个（频率语义靠这个不跑偏）
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(t.paper, f.shapes.sheet)
                .border(1.5.dp, t.outline, f.shapes.sheet),
        ) {
            // ── 里程表：数字向上滚，只滚变化的位 ──
            Odometer(from = fromCode, to = info.versionCode, reducedMotion = reducedMotion)

            // 标题就是「该翻表了」，版本号本身在表盘上（不写「发现新版本」，不重复版本行）
            Text(
                "该翻表了",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 18.dp),
            )

            // ── 更新内容：`·` 前缀列表；notes 为空整块不显示（连分隔线一起藏）──
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp)
                        .height(1.dp)
                        .background(t.rule),
                )
                val visible = if (expanded) notes else slice.items
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 12.dp),
                ) {
                    visible.forEach { note ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(
                                "·",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryInk(),
                                modifier = Modifier.padding(end = 9.dp),
                            )
                            Text(
                                note,
                                fontSize = 12.sp,
                                lineHeight = 19.sp,
                                color = t.inkSoft,
                            )
                        }
                    }
                    if (!expanded && slice.overflow > 0) {
                        Text(
                            slice.overflowText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryInk(),
                            modifier = Modifier
                                .padding(vertical = 7.dp)
                                .clickable { expanded = true },
                        )
                    } else if (expanded) {
                        Text(
                            "收起",
                            fontSize = 12.sp,
                            color = t.inkFaint,
                            modifier = Modifier
                                .padding(vertical = 7.dp)
                                .clickable { expanded = false },
                        )
                    }
                }
            }

            // ── 页脚固定：立即更新独占一行，两个次要按钮同行等分 ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .height(1.dp)
                    .background(t.rule),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 16.dp)) {
                PrimaryButton(text = "立即更新", onClick = onUpdateNow)
                Spacer(Modifier.height(9.dp))
                if (UpdateDialogPolicy.showsDismiss(mandatory)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        GhostButton("我知道了", Modifier.weight(1f), onClick = onGotIt)
                        GhostButton("不再提醒本次更新", Modifier.weight(1f), onClick = onDismissVersion)
                    }
                } else {
                    // 强制更新：整块移除「不再提醒」，只剩两个出口（可催，不能锁死）
                    GhostButton("我知道了", Modifier.fillMaxWidth(), onClick = onGotIt)
                }
                val meta = buildString {
                    if (info.sizeText.isNotBlank()) append(info.sizeText)
                    if (mandatory) {
                        if (isNotEmpty()) append(" · ")
                        append("当前版本已停止支持")
                    }
                }
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        meta,
                        fontSize = 9.5.sp,
                        fontFamily = NumericFont,
                        letterSpacing = 0.06.sp,
                        color = t.inkFaint,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

/** 逐位比较的里程表：只滚有变化的位（文档 §2.3 第 2 点） */
@Composable
private fun Odometer(from: Long, to: Long, reducedMotion: Boolean) {
    val t = InkTokens.current
    val a = digitsOf(from)
    val b = digitsOf(to)
    val len = maxOf(a.size, b.size)
    val aPadded = a.toMutableList().also { while (it.size < len) it.add(0, 0) }
    val bPadded = b.toMutableList().also { while (it.size < len) it.add(0, 0) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 26.dp, top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            for (i in 0 until len) {
                ReelDigit(
                    fromDigit = aPadded[i],
                    toDigit = bPadded[i],
                    changed = aPadded[i] != bPadded[i],
                    index = i,
                    reducedMotion = reducedMotion,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "BUILD $from",
                fontSize = 9.5.sp,
                fontFamily = NumericFont,
                letterSpacing = 0.11.sp,
                color = t.inkFaint,
            )
            Text(
                "BUILD $to",
                fontSize = 9.5.sp,
                fontFamily = NumericFont,
                letterSpacing = 0.11.sp,
                color = primaryInk(),
            )
        }
    }
}

/** 单个数字窗：变化位 = 旧值滚出 + 新值滚入；未变化位 = 静止灰字 */
@Composable
private fun ReelDigit(
    fromDigit: Int,
    toDigit: Int,
    changed: Boolean,
    index: Int,
    reducedMotion: Boolean,
) {
    val t = InkTokens.current
    val window = 62.dp
    val windowPx = with(LocalDensity.current) { window.toPx() }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(changed, reducedMotion) {
        if (!changed || reducedMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // 逐位错开，一位一位翻过去；450ms tween 无回弹（与导航条 180ms 同族）
        delay(index * 60L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 450,
                easing = CubicBezierEasing(0.25f, 0.9f, 0.3f, 1f),
            ),
        )
    }

    Box(
        Modifier
            .size(width = 46.dp, height = window)
            .clip(RectangleShape)
            .background(t.card)
            .border(1.dp, if (changed) t.primary else t.outline, RectangleShape)
            .clipToBounds(),
    ) {
        if (!changed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DigitText(fromDigit, color = t.inkSoft)
            }
        } else {
            // 两个数字**平级**，各自按进度位移：旧值向上滚出、新值从下方滚入。
            // 不用「Column 装两格 + 整体 translationY」的写法 —— 那个在真机上量出来是空白
            // （2026-11-16 实测：240→999 三位全是变化位，三格全空），因为嵌套层的高度链
            // 一旦被父级约束影响，位移就把两格都推到了可视区外。
            val p by progress.asState()
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (-windowPx * p).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) { DigitText(fromDigit, color = t.inkSoft) }
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (windowPx * (1f - p)).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) { DigitText(toDigit, color = t.primary) }
        }
    }
}

@Composable
private fun DigitText(digit: Int, color: Color) {
    Text(
        "$digit",
        fontSize = 32.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = NumericFont,
        color = color,
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(f.shapes.control)
            .background(t.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (t.isDark) Color(0xFF100E0B) else Color.White,
        )
    }
}

@Composable
private fun GhostButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(f.shapes.control)
            .background(Color.Transparent)
            .border(1.dp, t.outline, f.shapes.control)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = t.ink,
        )
    }
}

private fun digitsOf(n: Long): List<Int> = n.toString().map { it - '0' }
