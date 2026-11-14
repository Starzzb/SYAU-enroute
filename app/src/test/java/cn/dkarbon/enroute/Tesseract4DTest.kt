package cn.dkarbon.enroute

import cn.dkarbon.enroute.ui.common.Tesseract4D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 票 29：四维超立方体「转换」动效的数学部分。
 *
 * 为什么这些断言值钱：**"四维在转"这件事眼睛判不准**。一张静态截图看十秒
 * 也分不清是四维旋转还是平面图形在晃。所以把可判定的部分全钉在单测里：
 *
 * | 断言 | 它到底在防什么 |
 * |---|---|
 * | 旋转矩阵正交 | 防"顺手改了公式"，把刚体旋转写成会拉伸的假旋转（图形会被扯变形） |
 * | 六平面各自独立 | 防只转了两个平面（那样内外层根本不会交换，等于做了个 2D 自转） |
 * | 内外层确实交换 | 这是主人要的「转换」效果本身 —— 用成员更替量化，不靠"看着像" |
 * | 整周期不退化 | 防某些瞬间图形被压成一条线（六速度配错了就会这样） |
 * | 投影无 NaN/Inf | 防透视除法除到 0（W_DISTANCE 取小了就会） |
 * | 相位归一化 | 防跨周期续算时出现负相位/越界相位 |
 */
class Tesseract4DTest {

    /** 六个坐标平面：[XY, XZ, XW, YZ, YW, ZW] 各自涉及的坐标下标 */
    private val planePairs = intArrayOf(0, 1, 0, 2, 0, 3, 1, 2, 1, 3, 2, 3)

    private fun orthonormalAt(phase: Float) {
        val angles = FloatArray(Tesseract4D.PLANES)
        Tesseract4D.anglesInto(angles, phase)
        val out = FloatArray(4)
        val basis = arrayOf(
            floatArrayOf(1f, 0f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f, 0f),
            floatArrayOf(0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, 0f, 1f),
        )
        val img = Array(4) { FloatArray(4) }
        for (i in 0 until 4) {
            Tesseract4D.rotateInto(out, basis[i][0], basis[i][1], basis[i][2], basis[i][3], angles)
            img[i][0] = out[0]; img[i][1] = out[1]; img[i][2] = out[2]; img[i][3] = out[3]
        }
        for (i in 0 until 4) {
            val norm = hypot(hypot(img[i][0], img[i][1]), hypot(img[i][2], img[i][3]))
            assertEquals("相位 $phase：基向量 $i 的像长度必须仍是 1", 1f, norm, 1e-4f)
        }
        for (i in 0 until 4) {
            for (j in i + 1 until 4) {
                val dot = img[i][0] * img[j][0] + img[i][1] * img[j][1] +
                    img[i][2] * img[j][2] + img[i][3] * img[j][3]
                assertEquals("相位 $phase：第 $i、$j 个像必须正交", 0f, dot, 1e-4f)
            }
        }
    }

    // ── ① 旋转是刚体旋转（正交）──────────────────────────

    @Test
    fun `六平面组合旋转矩阵处处正交`() {
        for (k in 0..36) orthonormalAt(k / 36f)
    }

    @Test
    fun `零角度是恒等变换`() {
        val zero = FloatArray(Tesseract4D.PLANES)
        val out = FloatArray(4)
        Tesseract4D.rotateInto(out, 0.3f, -0.7f, 1.1f, -0.4f, zero)
        assertEquals(0.3f, out[0], 1e-6f)
        assertEquals(-0.7f, out[1], 1e-6f)
        assertEquals(1.1f, out[2], 1e-6f)
        assertEquals(-0.4f, out[3], 1e-6f)
    }

    // ── ② 六个平面各自独立（不是两个平面的自转）─────────────

    @Test
    fun `每个平面只动它自己的两个坐标`() {
        val out = FloatArray(4)
        for (p in 0 until Tesseract4D.PLANES) {
            val angles = FloatArray(Tesseract4D.PLANES)
            angles[p] = 0.7f
            val i = planePairs[p * 2]
            val j = planePairs[p * 2 + 1]
            val v = floatArrayOf(0f, 0f, 0f, 0f)
            v[i] = 1f
            v[j] = 1f
            Tesseract4D.rotateInto(out, v[0], v[1], v[2], v[3], angles)
            for (k in 0 until 4) {
                if (k == i || k == j) continue
                assertEquals("平面 $p 不该动第 $k 个坐标", 0f, out[k], 1e-6f)
            }
            assertTrue(
                "平面 $p 必须真的转过（两个坐标都还等于原值说明没转）",
                abs(out[i] - v[i]) > 1e-3f || abs(out[j] - v[j]) > 1e-3f,
            )
        }
    }

    @Test
    fun `六个平面速度两两不同且都在转`() {
        val turns = Tesseract4D.TURNS_PER_CYCLE
        assertEquals(6, turns.size)
        for (i in 0 until turns.size) {
            assertTrue("第 $i 个平面必须真的在转", turns[i] > 0.2f)
            for (j in i + 1 until turns.size) {
                assertTrue(
                    "第 $i、$j 个平面速度不能相同（${turns[i]} vs ${turns[j]}），否则是重复转",
                    abs(turns[i] - turns[j]) > 1e-3f,
                )
            }
        }
    }

    // ── ③ 「转换」本身：内外两层确实交换 ──────────────────

    /**
     * 量化「内外交换」：任一时刻，投影后半径最小的 8 个顶点就是内层立方体、最大的 8 个是外层。
     * 如果六平面真的转了四个维度，**同一个顶点会时而属于内层、时而属于外层** —— 这就是交换。
     *
     * 这一条是整个票的核心断言：只要它红，说明做出来的不是"转换"。
     */
    @Test
    fun `内外两层在整个周期里确实交换`() {
        val samples = 180
        val everInner = BooleanArray(Tesseract4D.VERTEX_COUNT)
        val everOuter = BooleanArray(Tesseract4D.VERTEX_COUNT)
        val buffer = FrameBuffer()

        for (k in 0 until samples) {
            buffer.update(k / samples.toFloat())
            val order = (0 until Tesseract4D.VERTEX_COUNT).sortedBy { buffer.radius(it) }
            order.take(8).forEach { everInner[it] = true }
            order.takeLast(8).forEach { everOuter[it] = true }
        }

        val swapped = (0 until Tesseract4D.VERTEX_COUNT).count { everInner[it] && everOuter[it] }
        assertTrue(
            "只有 $swapped/16 个顶点在内外之间换过位；至少 12 个才叫「内外交换」（现在多半只转了两个平面）",
            swapped >= 12,
        )
    }

    // ── ④ 整周期不退化 ───────────────────────────────────

    @Test
    fun `整周期投影都不退化也不爆炸`() {
        val samples = 240
        val buffer = FrameBuffer()
        var minSize = Float.MAX_VALUE
        var maxSize = 0f
        for (k in 0 until samples) {
            buffer.update(k / samples.toFloat())
            var m = 0f
            for (i in 0 until Tesseract4D.VERTEX_COUNT) m = maxOf(m, buffer.radius(i))
            minSize = minOf(minSize, m)
            maxSize = maxOf(maxSize, m)
        }
        // radius=1 时实测外接半径落在 0.43–0.57；放宽到 0.30–0.90 只拦"压成一条线/炸成一片"
        assertTrue("最小外接半径 $minSize 太小，说明某一帧退化了", minSize > 0.30f)
        assertTrue("最大外接半径 $maxSize 太大，说明透视分母除到 0 附近了", maxSize < 0.90f)
    }

    @Test
    fun `投影不产生 NaN 或无穷`() {
        val buffer = FrameBuffer()
        for (k in 0..400) {
            buffer.update(k / 400f)
            for (i in 0 until Tesseract4D.VERTEX_COUNT) {
                val x = buffer.xy[i * 2]
                val y = buffer.xy[i * 2 + 1]
                assertTrue("相位 ${k / 400f} 顶点 $i 的 x 不是有限值", x.isFinite())
                assertTrue("相位 ${k / 400f} 顶点 $i 的 y 不是有限值", y.isFinite())
                assertTrue("深度不是有限值", buffer.depth[i].isFinite())
                assertTrue("顶点应在画布附近", abs(x) < 50f && abs(y) < 50f)
            }
        }
    }

    // ── ⑤ 相位归一化 ─────────────────────────────────────

    @Test
    fun `相位归一化到零到一`() {
        assertEquals(0f, Tesseract4D.normalizePhase(0f), 1e-6f)
        assertEquals(0.25f, Tesseract4D.normalizePhase(1.25f), 1e-6f)
        assertEquals(0f, Tesseract4D.normalizePhase(1f), 1e-6f)
        assertEquals(0.9f, Tesseract4D.normalizePhase(-0.1f), 1e-5f)
        assertEquals(0f, Tesseract4D.normalizePhase(Float.NaN), 1e-6f)
        assertEquals(0f, Tesseract4D.normalizePhase(Float.POSITIVE_INFINITY), 1e-6f)
        // 相位 0 与相位 1 必须画出同一个姿态（循环无接缝）
        val a = FloatArray(Tesseract4D.PLANES)
        val b = FloatArray(Tesseract4D.PLANES)
        Tesseract4D.anglesInto(a, 0f)
        Tesseract4D.anglesInto(b, 1f)
        for (i in 0 until Tesseract4D.PLANES) {
            assertEquals("第 $i 个平面在相位 0 与 1 处角度应相同", a[i], b[i], 1e-4f)
        }
    }

    @Test
    fun `深度归一化落在零到一且单调`() {
        assertEquals(0f, Tesseract4D.depthNormalize(0.1f), 1e-6f)
        assertEquals(1f, Tesseract4D.depthNormalize(9f), 1e-6f)
        var prev = -1f
        var k2 = 0.30f
        while (k2 <= 0.66f) {
            val v = Tesseract4D.depthNormalize(k2)
            assertTrue("depthNormalize 应单调不减", v >= prev - 1e-6f)
            assertTrue("必须落在 0..1", v in 0f..1f)
            prev = v
            k2 += 0.02f
        }
    }

    /** 复用缓冲的取帧器（与生产代码同一套 api，但自己持有数组，不碰 UI） */
    private class FrameBuffer {
        val angles = FloatArray(Tesseract4D.PLANES)
        val xy = FloatArray(Tesseract4D.VERTEX_COUNT * 2)
        val depth = FloatArray(Tesseract4D.VERTEX_COUNT)
        val scratch = FloatArray(4)

        fun update(phase: Float) {
            Tesseract4D.anglesInto(angles, phase)
            // 中心 (0,0)、半径 1：单测只关心比例关系，不关心像素
            Tesseract4D.projectInto(xy, depth, scratch, angles, 0f, 0f, 1f)
        }

        fun radius(i: Int): Float = hypot(xy[i * 2], xy[i * 2 + 1])
    }
}
