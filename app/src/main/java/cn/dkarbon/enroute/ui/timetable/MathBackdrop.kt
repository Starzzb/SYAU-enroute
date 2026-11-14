package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

/**
 * 画出当前选择的底纹，**并且跟着课表一起动**。
 *
 * @param turns 这个画面「转过多少圈」的连续量。
 *   现在由「一天中的进度 + 第几周」拼出来：
 *   一天转一整圈（每小时 15°，一节课能看出它在转）、换一周再叠 40°。
 *   所以它不是一张死图 —— 你上完一节课回来再看，形状已经变了；
 *   翻到下一周，是另一个姿态。
 */
@Composable
fun MathBackdrop(modifier: Modifier, color: Color, alpha: Float, turns: Float) {
    when (CurrentBackdrop) {
        Backdrop.NONE -> Unit
        Backdrop.TESSERACT -> Canvas(modifier) {
            drawTesseract(
                color = color,
                centerX = size.width / 2f,
                centerY = size.height * 0.56f,
                radius = size.height * 0.40f,
                // 四维里两个平面一起转，投影才不会退化成一张平面图
                angleA = 0.42f + turns * 2f * PI.toFloat(),
                angleB = 0.78f + turns * 4f * PI.toFloat(),
                alpha = alpha,
                strokeWidth = kotlin.math.ceil(1f * density),
            )
        }
    }
}
