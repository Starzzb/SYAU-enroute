package cn.dkarbon.enroute.ui.academic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.ProbeCatalog
import cn.dkarbon.enroute.data.ProbeResult
import cn.dkarbon.enroute.data.ProbeSpec
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke as InkStroke
import kotlin.math.PI
import kotlin.math.sin

/** 一行的高度；窗口固定放 [VIEW] 行，多出来的整叠往上滚 */
private val ROW_H = 66.dp
private const val VIEW = 3

/** 一次诊断里某一行此刻的样子 */
private enum class Phase { Acting, Ok, Reject }

/**
 * 接口诊断面板 —— 一条接口对应一把锁。
 *
 * 按下按钮，容器从按钮下方撑开；每试一条往下长一行，涨到三行后整叠往上滚一行。
 * 钥匙从左边凑上去戳锁：通了锁梁弹开、勾描出来；不通锁身晃两下、钥匙被弹回去。
 *
 * 这里**不放任何解释性文字**：正在做什么由钥匙的动作说明，结果由锁的开合说明。
 * 行内只有三样事实 —— 接口名、状态码与字节数、真实路径。
 *
 * @param onRun 点按钮触发一次完整探测（[ProbeCatalog.specs] 全部 14 条）
 */
@Composable
fun ProbePanel(
    t: InkTokenSet,
    ui: AppState,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val total = ProbeCatalog.specs.size
    val done = ui.probe.size.coerceAtMost(total)
    val running = ui.probing
    // 已出结果的行 + 正在被戳的那一行
    val rows = (done + if (running) 1 else 0).coerceAtMost(total)
    // 长条左边那格：通了几个、断了一个。不写「已完成 3/14」这种说明书。
    val okCount = (0 until done).count { i ->
        val r = ui.probe.getOrNull(i)
        r != null && ProbeCatalog.ok(ProbeCatalog.specs[i], r)
    }

    // 开跑就自己撑开，不用再点一次
    LaunchedEffect(running) { if (running) expanded = true }

    val windowH by animateDpAsState(
        targetValue = if (expanded) ROW_H * rows.coerceAtMost(VIEW) else 0.dp,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "winH",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "chev",
    )
    val scroll = rememberScrollState()
    // 新行进来就跟到底；手动往上翻时 rows 不变，不会被强行拉回。
    LaunchedEffect(rows, scroll.maxValue) {
        if (rows > 0 && expanded) scroll.animateScrollTo(scroll.maxValue)
    }

    Column(modifier.fillMaxWidth()) {

        // ── 长条：左半边放本次的事实，右部那个格子才是按钮 ──
        // 整块不响应点击 —— 只有一个明确的按钮，别让它变成「点哪都行」。
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .border(InkStroke.strong, t.ink),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (done == 0) "$total 项" else "$okCount 通",
                fontSize = 11.5.sp,
                fontFamily = NumericFont,
                color = t.inkSoft,
                modifier = Modifier.padding(start = 14.dp),
            )
            if (done > 0 && done - okCount > 0) {
                Text(
                    text = " · ${done - okCount} 断",
                    fontSize = 11.5.sp,
                    fontFamily = NumericFont,
                    color = t.rust,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .width(InkStroke.hairline)
                    .height(22.dp)
                    .drawBehind { drawRect(t.ink.copy(alpha = 0.22f)) },
            )
            Row(
                Modifier
                    .height(44.dp)
                    .clickable(enabled = !running) {
                        expanded = true
                        onRun()
                    }
                    .padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        running -> "检测中"
                        done > 0 -> "重新检测"
                        else -> "开始检测"
                    },
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (running) t.inkFaint else t.ink,
                )
                Spacer(Modifier.width(8.dp))
                Chevron(color = if (running) t.inkFaint else t.ink, degrees = chevron)
            }
        }

        // ── 窗口：固定三行高，多出来的整叠往上滚；也可以自己往上拖看历史 ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(windowH)
                .clipToBounds(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
            ) {
                for (i in 0 until rows) {
                    ProbeRow(
                        t = t,
                        spec = ProbeCatalog.specs[i],
                        result = ui.probe.getOrNull(i),
                    )
                }
            }

            // 窗口就三行，被切掉的行会露出半截锁与半行字。用纸色把上下边缘淡掉，
            // 只用渐隐说明「这里还能翻」，不写任何提示文字。
            val fade by animateDpAsState(
                targetValue = if (expanded) 20.dp else 0.dp,
                animationSpec = tween(200, easing = FastOutSlowInEasing),
                label = "fade",
            )
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        drawContent()
                        val h = fade.toPx()
                        if (h <= 0f) return@drawWithContent
                        val atTop = scroll.value > 2
                        val atEnd = scroll.value < scroll.maxValue - 2
                        if (atTop) {
                            drawRect(
                                Brush.verticalGradient(listOf(t.paper, t.paper.copy(alpha = 0f)), 0f, h),
                                size = Size(size.width, h),
                            )
                        }
                        if (atEnd) {
                            drawRect(
                                Brush.verticalGradient(listOf(t.paper.copy(alpha = 0f), t.paper), size.height - h, size.height),
                                size = Size(size.width, h),
                                topLeft = Offset(0f, size.height - h),
                            )
                        }
                    },
            )
        }

        // 未登录 / 中途断了 —— 这种情况下没有行可看，才需要一句话
        if (ui.probeMsg.isNotBlank() && !running) {
            Text(
                text = ui.probeMsg,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ── 一行 ──────────────────────────────────────────────────

@Composable
private fun ProbeRow(t: InkTokenSet, spec: ProbeSpec, result: ProbeResult?) {
    val phase = when {
        result == null -> Phase.Acting
        ProbeCatalog.ok(spec, result) -> Phase.Ok
        else -> Phase.Reject
    }
    val metaColor = when (phase) {
        Phase.Ok -> t.olive
        Phase.Reject -> t.rust
        Phase.Acting -> t.inkFaint
    }
    val lineColor = if (phase == Phase.Reject) t.rust.copy(alpha = 0.34f) else t.rule.copy(alpha = 0.5f)
    val dashed = phase == Phase.Reject

    Column(Modifier.fillMaxWidth().height(ROW_H)) {
        Row(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = spec.label,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (phase == Phase.Reject) t.rust else t.ink,
                )
                Text(
                    text = ProbeCatalog.metaLine(result),
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    fontFamily = NumericFont,
                    color = metaColor,
                )
                Text(
                    text = shortPath(spec.path),
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    fontFamily = NumericFont,
                    color = t.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(Modifier.width(92.dp).height(44.dp)) {
                LockKey(t = t, phase = phase)
            }
        }

        // 分隔线：断掉的那条画成虚线，把「这条没通」写进形状里
        Box(
            Modifier
                .fillMaxWidth()
                .height(InkStroke.hairline)
                .drawBehind {
                    if (dashed) {
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, size.height / 2f),
                            end = Offset(size.width, size.height / 2f),
                            strokeWidth = size.height,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                        )
                    } else {
                        drawRect(lineColor)
                    }
                },
        )
    }
}

// ── 锁与钥匙 ──────────────────────────────────────────────

/**
 * 路径只留末尾两段。
 *
 * 14 条接口里有 3 条的路径前半段完全一样（都挂 `freeClassroom/`），从头截断会让它们
 * 看起来一模一样；从尾部截断又会让「校区别」「楼栋别」分不出来。所以保留尾巴。
 */
private fun shortPath(path: String): String {
    val p = path.replace(ProbeCatalog.TOKEN, "…")
    val parts = p.split('/').filter { it.isNotEmpty() }
    return if (parts.size <= 2) p else "…/" + parts.takeLast(2).joinToString("/")
}

@Composable
private fun LockKey(t: InkTokenSet, phase: Phase) {
    val open by animateFloatAsState(
        targetValue = if (phase == Phase.Ok) 1f else 0f,
        animationSpec = tween(230, easing = FastOutSlowInEasing),
        label = "open",
    )
    val tick by animateFloatAsState(
        targetValue = if (phase == Phase.Ok) 1f else 0f,
        animationSpec = tween(200, delayMillis = 110, easing = LinearEasing),
        label = "tick",
    )
    val keyAlpha by animateFloatAsState(
        targetValue = if (phase == Phase.Acting) 1f else 0f,
        animationSpec = if (phase == Phase.Reject) tween(220, delayMillis = 190) else tween(160),
        label = "keyA",
    )
    val keyX by animateFloatAsState(
        targetValue = when (phase) {
            Phase.Acting -> 26f
            Phase.Ok -> 44f
            Phase.Reject -> -34f
        },
        animationSpec = tween(if (phase == Phase.Reject) 240 else 300, easing = FastOutSlowInEasing),
        label = "keyX",
    )

    // 不通时锁身晃两下：sin 三个来回，幅度随时间衰减
    val shake = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == Phase.Reject) {
            shake.snapTo(0f)
            shake.animateTo(1f, tween(300, easing = LinearEasing))
        } else {
            shake.snapTo(0f)
        }
    }

    val lockColor = t.ink
    val okColor = t.olive
    val badColor = t.rust
    val keyColor = t.primary

    Canvas(Modifier.fillMaxSize()) {
        val u = size.height / 44f          // 1dp 的像素数（动作区 44dp 高）
        val cy = size.height / 2f
        // 锁：36×40，右对齐
        val lockW = 36f * u
        val lockH = 40f * u
        val ox = size.width - lockW
        val oy = cy - lockH / 2f

        val p = shake.value
        val dx = if (p < 1f) sin(p * 3f * 2f * PI.toFloat()) * 2.5f * (1f - p) * u else 0f

        drawLock(
            ink = lockColor,
            ok = okColor,
            bad = badColor,
            open = open,
            tick = tick,
            reject = phase == Phase.Reject,
            ox = ox + dx,
            oy = oy,
            u = u,
        )

        if (keyAlpha > 0.01f) {
            drawKey(
                color = if (phase == Phase.Reject) badColor else keyColor,
                alpha = keyAlpha,
                cx = ox - 40f * u + keyX * u,
                cy = cy,
                u = u,
            )
        }
    }
}

private fun DrawScope.drawLock(
    ink: Color,
    ok: Color,
    bad: Color,
    open: Float,
    tick: Float,
    reject: Boolean,
    ox: Float,
    oy: Float,
    u: Float,
) {
    val color = if (reject) bad else ink
    val stroke = Stroke(width = 2.2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // 锁体
    drawRoundRect(
        color = color,
        topLeft = Offset(ox + 6f * u, oy + 17f * u),
        size = Size(24f * u, 18f * u),
        cornerRadius = CornerRadius(1.5f * u, 1.5f * u),
        style = stroke,
    )

    // 锁孔：开锁时淡出
    if (open < 0.99f) {
        drawCircle(color.copy(alpha = color.alpha * (1f - open)), radius = 2f * u, center = Offset(ox + 18f * u, oy + 24.5f * u))
        drawLine(
            color = color.copy(alpha = color.alpha * (1f - open)),
            start = Offset(ox + 18f * u, oy + 26f * u),
            end = Offset(ox + 18f * u, oy + 30.5f * u),
            strokeWidth = 2f * u,
            cap = StrokeCap.Round,
        )
    }

    // 锁梁：以**右脚底**为铰点往外拧开（34°），右脚留在锁体里不动。
    // 不能整体平移 —— 那样两端一起离开，看起来像锁头掉了下来，而不是被打开了。
    val shackle = Path().apply {
        moveTo(ox + 10f * u, oy + 17f * u)
        lineTo(ox + 10f * u, oy + 11f * u)
        arcTo(
            rect = Rect(ox + 10f * u, oy + 3f * u, ox + 26f * u, oy + 19f * u),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(ox + 26f * u, oy + 17f * u)
    }
    withTransform({
        rotate(34f * open, pivot = Offset(ox + 26f * u, oy + 17f * u))
    }) {
        drawPath(shackle, color, style = stroke)
    }

    // 通了描一个勾
    if (tick > 0.01f) {
        val a = Offset(ox + 11.5f * u, oy + 26f * u)
        val b = Offset(ox + 15.8f * u, oy + 30.4f * u)
        val c = Offset(ox + 24f * u, oy + 21.2f * u)
        val l1 = (b - a).getDistance()
        val l2 = (c - b).getDistance()
        val d = tick * (l1 + l2)
        val sw = 2.4f * u
        if (d <= l1) {
            drawLine(ok, a, a + (b - a) * (d / l1), strokeWidth = sw, cap = StrokeCap.Round)
        } else {
            drawLine(ok, a, b, strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(ok, b, b + (c - b) * ((d - l1) / l2), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawKey(color: Color, alpha: Float, cx: Float, cy: Float, u: Float) {
    val c = color.copy(alpha = color.alpha * alpha)
    val sw = 2.2f * u
    // 环
    drawCircle(c, radius = 4.6f * u, center = Offset(cx, cy - 2f * u), style = Stroke(sw))
    // 杆：往右下斜，通向锁孔
    drawLine(
        c,
        start = Offset(cx + 4.2f * u, cy + 0.4f * u),
        end = Offset(cx + 15.6f * u, cy + 5.6f * u),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
    // 两枚齿
    drawLine(
        c,
        start = Offset(cx + 12.4f * u, cy + 4.2f * u),
        end = Offset(cx + 10.6f * u, cy + 7.6f * u),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
    drawLine(
        c,
        start = Offset(cx + 16.4f * u, cy + 6f * u),
        end = Offset(cx + 14.6f * u, cy + 9.4f * u),
        strokeWidth = sw,
        cap = StrokeCap.Round,
    )
}

// ── 按钮右侧的箭头 ────────────────────────────────────────

@Composable
private fun Chevron(color: Color, degrees: Float) {
    Canvas(Modifier.size(14.dp)) {
        withTransform({ rotate(degrees, pivot = center) }) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = h / 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val p = Path().apply {
                moveTo(w * 0.18f, h * 0.38f)
                lineTo(w * 0.5f, h * 0.68f)
                lineTo(w * 0.82f, h * 0.38f)
            }
            drawPath(p, color, style = stroke)
        }
    }
}
