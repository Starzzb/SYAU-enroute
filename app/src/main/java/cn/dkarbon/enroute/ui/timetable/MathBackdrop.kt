package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.dkarbon.enroute.ui.common.Tesseract4D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 课表背景的「数学底纹」。
 *
 * ## 为什么是四维超立方体（tesseract）的二维投影
 * 课表本身是二维表：**一天一列（x）、时间一条轴（y）**。再往里塞一张普通的网格、
 * 或者一张装饰插画，只会跟表格线打架。
 * 超立方体刚好相反 —— 它是「16 个顶点 + 32 条棱」的**纯线框**，没有面、没有渐变，
 * 印在纸上就是几根细线，符合硬朗体系的「钢牌 / 印刷」质感，而且**天然带着「多一层维度」的意味**：
 * 课表是「这周 × 这七天 × 这几个时段」的切片，背景正好是那个被切开的四维体。
 *
 * ## 投影链路（和数学课本一致）
 * ```
 * 4D 顶点 (±1,±1,±1,±1) 16 个
 *   → 绕 XY 平面转 a1、绕 ZW 平面转 a2（四维里"同时转两个平面"才是刚体旋转）
 *   → 4D→3D：w 做透视除法  k = 1 / (D - w)
 *   → 3D→2D：绕 X、Y 各转一点，再按 z 做透视除法
 * ```
 * 全部是乘加，静态绘制（不跑动画），32 条线一次画完，代价可以忽略。
 *
 * ## 为什么只画在这里、不铺满整屏
 * 它只是「纸纹」，不是主角：透明度压到线宽的 6–10%，只在网格空白处看得见，
 * 一旦和课程卡抢注意力就说明画重了。
 */
enum class Backdrop {
    /** 不画（省电 / 想让表格更干净时） */
    NONE,

    /** 四维超立方体投影 */
    TESSERACT,
}

/** 当前使用的底纹。想换别的数学结构，只要在这里加一个枚举值 + 一个 draw 函数。 */
val CurrentBackdrop: Backdrop = Backdrop.TESSERACT

/** 超立方体能画出来的最小骨架：16 顶点、32 棱 */
private const val VERTEX_COUNT = 16
private val TESSERACT_EDGES: List<Pair<Int, Int>> = buildList {
    for (a in 0 until VERTEX_COUNT) {
        for (bit in 0 until 4) {
            val b = a xor (1 shl bit)
            if (a < b) add(a to b)
        }
    }
}

/**
 * 在给定矩形里画一个超立方体投影。
 *
 * @param alpha 线的不透明度（建议 0.06–0.12，再高就抢课名了）
 * @param depthFade true = 近处的棱更实、远处的更虚（多一层"体积感"）
 */
fun DrawScope.drawTesseract(
    color: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    angleA: Float = 0.42f,
    angleB: Float = 0.78f,
    alpha: Float = 0.09f,
    strokeWidth: Float = 1f,
    depthFade: Boolean = true,
) {
    val ca = cos(angleA)
    val sa = sin(angleA)
    val cb = cos(angleB)
    val sb = sin(angleB)

    // 16 个顶点：四个 ±1 的组合
    val xy = FloatArray(VERTEX_COUNT * 2)

    for (i in 0 until VERTEX_COUNT) {
        var x = if (i and 1 != 0) 1f else -1f
        var y = if (i and 2 != 0) 1f else -1f
        var z = if (i and 4 != 0) 1f else -1f
        var w = if (i and 8 != 0) 1f else -1f

        // ① 四维旋转：XY 平面 + ZW 平面
        var nx = x * ca - y * sa
        var ny = x * sa + y * ca
        x = nx
        y = ny
        nx = z * cb - w * sb
        var nw = z * sb + w * cb
        z = nx
        w = nw

        // ② 4D → 3D：w 做透视除法（D 取 2.6，太小会撕裂，太大就看不到"四维感"）
        val k4 = 1f / (2.6f - w)
        var x3 = x * k4
        var y3 = y * k4
        var z3 = z * k4

        // ③ 3D → 2D：绕 X、Y 各转一点，再按 z 透视
        val cy = cos(0.52f)
        val sy = sin(0.52f)
        var nx3 = x3 * cy + z3 * sy
        var nz3 = -x3 * sy + z3 * cy
        x3 = nx3
        z3 = nz3
        val cx3 = cos(0.36f)
        val sx3 = sin(0.36f)
        val ny3 = y3 * cx3 - z3 * sx3
        z3 = y3 * sx3 + z3 * cx3
        y3 = ny3

        val k2 = 1.9f / (4.2f - z3)
        xy[i * 2] = centerX + x3 * k2 * radius
        xy[i * 2 + 1] = centerY + y3 * k2 * radius
    }

    // 32 条棱：按"平均 w/z 深度"调透明度，近实远虚
    val base = color.copy(alpha = alpha)
    for ((a, b) in TESSERACT_EDGES) {
        val ax = xy[a * 2]
        val ay = xy[a * 2 + 1]
        val bx = xy[b * 2]
        val by = xy[b * 2 + 1]
        val c = if (depthFade) {
            // 用屏幕上的长度当深度的代理：越靠近中心越小 → 越远越淡
            val dx = bx - ax
            val dy = by - ay
            val d = kotlin.math.sqrt(dx * dx + dy * dy) / (radius * 0.9f)
            base.copy(alpha = (alpha * (0.45f + 0.55f * d.coerceIn(0f, 1f))))
        } else {
            base
        }
        drawLine(c, Offset(ax, ay), Offset(bx, by), strokeWidth)
    }
}

fun tesseractAlpha(editorial: Boolean, isDark: Boolean): Float = when {
    editorial -> if (isDark) 0.115f else 0.085f
    isDark -> 0.14f
    else -> 0.12f
}

/** 手势驱动：最后一点位移之后多久没动静就算「静止」（纳秒） */
private const val DRIVE_IDLE_NANOS = 600_000_000L

/** 手势驱动：变化量小于这个值的 drive 视为无位移（浮点噪声，不算输入） */
private const val DRIVE_EPSILON = 1e-5f

/**
 * 每帧复用的缓冲。**进组合时建一次，之后一直复用** —— 60fps 下每帧 new 16 顶点 + 32 条线
 * 的数组，一秒钟就是几百个短命对象，GC 抖动的代价远大于画 32 条线。
 */
internal class TesseractBuffers {
    val angles = FloatArray(Tesseract4D.PLANES)
    val xy = FloatArray(Tesseract4D.VERTEX_COUNT * 2)
    val depth = FloatArray(Tesseract4D.VERTEX_COUNT)
    val scratch = FloatArray(4)
}

/**
 * 画一帧四维超立方体。
 *
 * @param phase 0..1 的周期相位（0 与 1 是同一个姿态，所以循环无接缝）
 * @param alpha 整体不透明度
 * @param radiusFactor 外接半径相对「短边」的倍数。0.55 时图形大约占短边的 6 成 ——
 *   再大就成了主角，再小四维结构看不清（这是「看得见」与「不抢戏」之间的实测取值）
 */
internal fun DrawScope.drawTesseract4D(
    color: Color,
    phase: Float,
    alpha: Float,
    strokeWidth: Float,
    buffers: TesseractBuffers,
    radiusFactor: Float = 0.55f,
    /** 正交投影（Schlegel 图） */
    ortho: Boolean = false,
) {
    val cx = size.width / 2f
    val cy = size.height * 0.52f
    val radius = kotlin.math.min(size.width, size.height) * radiusFactor

    Tesseract4D.anglesInto(buffers.angles, phase)
    Tesseract4D.projectInto(
        buffers.xy, buffers.depth, buffers.scratch, buffers.angles, cx, cy, radius, ortho,
    )

    val base = color.copy(alpha = alpha)
    val edges = Tesseract4D.EDGES
    var i = 0
    while (i < edges.size) {
        val a = edges[i]
        val b = edges[i + 1]
        // 近实远虚：透视因子越大越靠前，线就越实（一层"体积感"，不加面也能读出差深）
        // 下限给到 0.55 而不是 0.42：远处那半圈线太淡时，整张图会"缺一半"，看不出是个闭合体
        val near = (Tesseract4D.depthNormalize(buffers.depth[a]) +
            Tesseract4D.depthNormalize(buffers.depth[b])) * 0.5f
        drawLine(
            color = base.copy(alpha = alpha * (0.55f + 0.45f * near)),
            start = Offset(buffers.xy[a * 2], buffers.xy[a * 2 + 1]),
            end = Offset(buffers.xy[b * 2], buffers.xy[b * 2 + 1]),
            strokeWidth = strokeWidth,
        )
        i += 2
    }
}

/**
 * 会「转换」的四维背景。默认挂在「关于」页（页面留白最多，且没有数据要抢）。
 *
 * ## 省电三条（代码里能看出来）
 * 1. **只在可见时跑**：[LaunchedEffect] 的 key 是 [visible]，页面离开组合或 Activity 退到后台
 *    （`Lifecycle` 不到 STARTED）时协程直接停；回来重新起算，不做"补帧"。
 * 2. **每帧零分配**：顶点/棱/角度全在 [TesseractBuffers] 里复用；`Color`/`Offset` 都是
 *    Compose 的 value class，`copy`/构造不进堆。
 * 3. **不逐帧重组**：相位用 `mutableFloatStateOf` 存，但只在 [Canvas] 的 draw lambda 里读 ——
 *    Compose 会把这次读取算作"绘制阶段依赖"，只重绘、不重组。
 *
 * 另外：系统设置里把「动画时长」调到 0（开发者选项的"移除动画"/无障碍"减少动态效果"）时，
 * 整个动画不启动，只留一个静态姿态 —— 眩晕敏感的用户不该被背景逼着看动效。
 *
 * @param periodSeconds 一个完整周期。34 秒是有意的：快到能看出"在变"，慢到不会让人盯着看
 * @param drive 手势驱动量。为 null 时维持 34 秒自转（关于页）；非 null 时**忽略时间自转**，
 *   只按 drive 的变化量推进 phase（drive 的值单位是「圈」，1.0 = 一个完整四维周期）。
 *   静止判定：最后一次 drive 变化 600ms 内无新位移 → 彻底停帧（连帧循环都不空转），
 *   等下一次 drive 变化再用 [snapshotFlow] 唤醒。
 */
@Composable
fun MathBackdropMotion(
    modifier: Modifier = Modifier,
    color: Color,
    alpha: Float,
    periodSeconds: Float = 34f,
    initialPhase: Float = 0.31f,
    drive: State<Float>? = null,
    /** 跟手档的传动比：负数 = 反向，绝对值大于 1 = 转得更快 */
    driveScale: Float = 1f,
    /** 正交投影（Schlegel 图） */
    orthographic: Boolean = false,
) {
    val ctx = LocalContext.current
    // 只读一次：这个值在会话中不会变，不值得为它挂监听
    val animScaleOff = remember(ctx) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }

    val phase = remember { mutableFloatStateOf(Tesseract4D.normalizePhase(initialPhase)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> visible = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> visible = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(visible, animScaleOff, drive, driveScale) {
        if (!visible || animScaleOff) return@LaunchedEffect
        if (drive == null) {
            // 原行为：无条件按 periodSeconds 自转（关于页那处调用走这里）
            var startNanos = 0L
            while (true) {
                // 注意写成 `withFrameNanos { it }`：这个 lambda **不捕获任何变量**，
                // Kotlin 会把它编成单例，每帧不 new（写成捕获块的形式在 60fps 下每秒 60 个短命闭包）
                val now = withFrameNanos { it }
                if (startNanos == 0L) startNanos = now
                val elapsed = (now - startNanos) / 1_000_000_000f
                phase.floatValue = Tesseract4D.normalizePhase(elapsed / periodSeconds + initialPhase)
            }
        } else {
            // 手势驱动：drive 的值就是相位贡献（单位=圈），只吃它的变化量。
            // 手指停 600ms 后彻底停机 —— 用 snapshotFlow 挂起等下一次变化，不空转帧循环。
            var lastDrive = drive.value
            var lastInputAt = 0L
            while (true) {
                val now = withFrameNanos { it }
                val d = drive.value
                val delta = d - lastDrive
                lastDrive = d
                if (kotlin.math.abs(delta) > DRIVE_EPSILON) {
                    phase.floatValue = Tesseract4D.normalizePhase(phase.floatValue + delta * driveScale)
                    lastInputAt = now
                }
                if (now - lastInputAt > DRIVE_IDLE_NANOS) {
                    snapshotFlow { drive.value }.first { it != lastDrive }
                }
            }
        }
    }

    val buffers = remember { TesseractBuffers() }
    Canvas(modifier) {
        drawTesseract4D(
            color = color,
            phase = phase.floatValue,
            alpha = alpha,
            // 1.4 物理像素 —— 不乘 density：它要的是"纸上一根细线"，不是 1dp 的 UI 元素。
            // （2026-11-15 课表页可见性修复：从 1 提到 1.4，底纹被行带/卡片压住后
            //   线太细会更难被看见；1.4 仍在"背景不是主角"的档位内，不抢课名。）
            strokeWidth = 1.4f,
            buffers = buffers,
            ortho = orthographic,
        )
    }
}
