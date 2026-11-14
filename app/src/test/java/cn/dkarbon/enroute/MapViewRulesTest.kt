package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapFeature
import cn.dkarbon.enroute.data.map.MapPoint
import cn.dkarbon.enroute.data.map.MapProjection
import cn.dkarbon.enroute.ui.map.MapViewRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 票 18：地图手势与缩放体验的纯规则。
 *
 * 这四条在 Compose 里只能靠反复上真机试手感；抽成纯函数后，
 * 「3.0× 必须正好 0」「拖过头只留 35%」「双击锚点下的内容不许跑」都能在这里钉死。
 */
class MapViewRulesTest {

    // ── B：底图淡出曲线 ──────────────────────────────────

    @Test
    fun `底图在2点2倍到3倍之间线性淡出`() {
        // 2.2× 之前完全不透明
        assertEquals(1f, MapViewRules.bitmapAlpha(1f), 1e-6f)
        assertEquals(1f, MapViewRules.bitmapAlpha(2.0f), 1e-6f)
        assertEquals(1f, MapViewRules.bitmapAlpha(2.2f), 1e-6f)
        // 区间内线性：中点 2.6× = 0.5
        assertEquals(0.5f, MapViewRules.bitmapAlpha(2.6f), 1e-6f)
        // 端点必须是 0（票面要求：=0 时跳过绘制，不许留半透明层）
        assertEquals(0f, MapViewRules.bitmapAlpha(3.0f), 1e-6f)
        assertEquals(0f, MapViewRules.bitmapAlpha(4.0f), 1e-6f)
        assertEquals(0f, MapViewRules.bitmapAlpha(8.0f), 1e-6f)
    }

    @Test
    fun `淡出是单调的没有台阶`() {
        // 以前是 if (zoom > 2.5) 0.35 → 2.5 前后从 1.0 直接掉到 0.35，这里不许出现这种断崖
        var prev = 1f
        var z = 1.0f
        while (z <= 5.0f) {
            val a = MapViewRules.bitmapAlpha(z)
            assertTrue("z=$z 处 alpha 反而变大了", a <= prev + 1e-6f)
            assertTrue("z=$z alpha 越界", a in 0f..1f)
            prev = a
            z += 0.05f
        }
        // 任意相邻 0.05 步长的落差都远小于旧版那 0.65 的断崖
        assertTrue(MapViewRules.bitmapAlpha(2.45f) - MapViewRules.bitmapAlpha(2.55f) < 0.2f)
    }

    @Test
    fun `位图淡到一半以下矢量接管配色`() {
        assertFalse("位图还很清楚时用热区模式", MapViewRules.vectorTakesOver(1.0f))
        assertFalse(MapViewRules.vectorTakesOver(0.5f))
        assertTrue("淡过半就自己上色，否则只剩 3% 填充的线框", MapViewRules.vectorTakesOver(0.49f))
        assertTrue(MapViewRules.vectorTakesOver(0f))
        // 与淡出曲线对齐：2.6× 之后接管
        assertTrue(MapViewRules.vectorTakesOver(MapViewRules.bitmapAlpha(2.7f)))
        assertFalse(MapViewRules.vectorTakesOver(MapViewRules.bitmapAlpha(2.4f)))
    }

    // ── A：可拖边界与回弹 ────────────────────────────────

    @Test
    fun `可拖范围按内容尺寸减画布尺寸算`() {
        // 内容比画布宽 → 有得拖
        assertEquals(300f, MapViewRules.panLimit(contentLength = 1600f, canvasLength = 1000f), 1e-3f)
        // 内容比画布窄（fit 留边）→ 不能拖
        assertEquals(0f, MapViewRules.panLimit(contentLength = 800f, canvasLength = 1000f), 1e-3f)
        // 视野余量：content 比 canvas 窄时，limit == margin（不再被 coerce 成 0）
        assertEquals(6f, MapViewRules.panLimit(contentLength = 800f, canvasLength = 1000f, margin = 6f), 1e-3f)
    }

    @Test
    fun `视野余量让四角地块能拖到视野中部`() {
        // 画布 1000 宽，zoom=2 → 内容 2000；margin = 35% 画布 = 350
        val canvas = 1000f
        val margin = MapViewRules.viewMargin(canvas)
        assertEquals(350f, margin, 1e-3f)
        // limit = (2000-1000)/2 + 350 = 850 —— 内容边缘可以越过画布边缘 350px
        assertEquals(850f, MapViewRules.panLimit(2000f, canvas, margin), 1e-3f)
        // 没有 margin 时只有 500（边缘只能贴到画布边，四角地块永远到不了视野中间）
        assertEquals(500f, MapViewRules.panLimit(2000f, canvas), 1e-3f)
        // 1× 且内容与画布等宽时，margin 仍给 350 → 轻微拖动可用了（以前是 0，被说「钉死」）
        assertEquals(350f, MapViewRules.panLimit(1000f, canvas, margin), 1e-3f)
    }

    @Test
    fun `回弹目标就是夹到边界且不该在拖动中途调用`() {
        // 数学部分：越界的 pan 回弹到边界，界内的原样不动
        assertEquals(850f, MapViewRules.snapBackTarget(1200f, 850f), 1e-3f)
        assertEquals(-850f, MapViewRules.snapBackTarget(-1200f, 850f), 1e-3f)
        assertEquals(300f, MapViewRules.snapBackTarget(300f, 850f), 1e-3f)
        // 「只在手指全抬起后调用」是调用点契约（`awaitEachGesture` 的 while 退出后才 snapBack），
        // 单测锁不到时序，靠真机清单第 3 条验。
    }

    @Test
    fun `旧公式与实际内容不一致时以新公式为准`() {
        // 竖屏：画布 1000×600，图幅 1024×648 按宽适配 → 内容宽 1000、高 633
        // 旧公式 cw*(zoom-1)/2 在 zoom=2 时给 500；按内容算应是 (1000*2-1000)/2 = 500（宽轴一致）
        val contentW = 1000f * 2f
        assertEquals(500f, MapViewRules.panLimit(contentW, 1000f), 1e-3f)
        // 但纵向内容只有 633：旧公式 ch*(zoom-1)/2 = 300，新公式 (633*2-600)/2 = 333 —— 差 33px
        assertEquals(333f, MapViewRules.panLimit(633f * 2f, 600f), 1e-3f)
        assertTrue("旧公式给 300，新公式 333，不能混用", abs(333f - 300f) > 30f)
    }

    @Test
    fun `拖过头只留三成半松手回弹到边界`() {
        val limit = 100f
        // 界内原样
        assertEquals(80f, MapViewRules.applyOverdrag(80f, limit), 1e-3f)
        assertEquals(-80f, MapViewRules.applyOverdrag(-80f, limit), 1e-3f)
        // 正好在界上
        assertEquals(100f, MapViewRules.applyOverdrag(100f, limit), 1e-3f)
        // 超出 100 → 只多给 35
        assertEquals(135f, MapViewRules.applyOverdrag(200f, limit), 1e-3f)
        assertEquals(-135f, MapViewRules.applyOverdrag(-200f, limit), 1e-3f)
        // 松手回弹
        assertEquals(100f, MapViewRules.clampToBounds(135f, limit), 1e-3f)
        assertEquals(0f, MapViewRules.clampToBounds(135f, 0f), 1e-3f)
    }

    // ── A：锚点缩放 ─────────────────────────────────────

    @Test
    fun `以点击点缩放时锚点下的内容不动`() {
        // 屏幕坐标下：内容点 c 在缩放前的位置 = pan + c*zoom（忽略中心偏移，这里只看相对量）
        val anchor = 240f     // 双击点相对画布中心的偏移
        val pan = -60f
        val k = 2f
        val pan2 = MapViewRules.panAfterZoomAt(anchor, pan, k)
        // 缩放前锚点对应的内容坐标
        val contentBefore = (anchor - pan) / 1f
        // 缩放后：内容点在屏幕上的位置 = pan2 + content * k
        val screenAfter = pan2 + contentBefore * k
        assertEquals(anchor, screenAfter, 1e-3f)
        // 也等价于票面公式：pan' = anchor - (anchor - pan) * k
        assertEquals(anchor - (anchor - pan) * k, pan2, 1e-6f)
    }

    @Test
    fun `缩放比为一等价于不动`() {
        assertEquals(37f, MapViewRules.panAfterZoomAt(120f, 37f, 1f), 1e-6f)
    }

    // ── 点击判定（票 18 复测：双击不许选中）─────────────

    @Test
    fun `拖动不算点击双击更不算`() {
        // 单击：单指 + 位移在阈值内
        assertTrue(MapViewRules.isTap(maxPointers = 1, movedScreen = 0f, slopScreen = 20f))
        assertTrue(MapViewRules.isTap(maxPointers = 1, movedScreen = 18f, slopScreen = 20f))
        // 拖动：450px（lead 实测的那次）绝不能触发选中
        assertFalse(MapViewRules.isTap(maxPointers = 1, movedScreen = 450f, slopScreen = 20f))
        // 双指（缩放）也不是点击
        assertFalse(MapViewRules.isTap(maxPointers = 2, movedScreen = 0f, slopScreen = 20f))
        // 阈值可按 dp 换算后传入；默认 8dp
        assertEquals(8f, MapViewRules.TAP_SLOP_DP, 1e-6f)
    }

    @Test
    fun `双击要同时满足时间窗口与距离`() {
        // 300ms 内、落点相近 → 是双击
        assertTrue(MapViewRules.isDoubleTap(nowMs = 1200, lastTapMs = 1000, dx = 5f, dy = 5f, slopScreen = 100f))
        // 超过 300ms → 不是（那时第一下已经当单击处理了）
        assertFalse(MapViewRules.isDoubleTap(nowMs = 1400, lastTapMs = 1000, dx = 0f, dy = 0f, slopScreen = 100f))
        // 落点太远 → 是两次独立点击
        assertFalse(MapViewRules.isDoubleTap(nowMs = 1100, lastTapMs = 1000, dx = 300f, dy = 0f, slopScreen = 100f))
        // 没有上一下
        assertFalse(MapViewRules.isDoubleTap(nowMs = 1100, lastTapMs = 0, dx = 0f, dy = 0f, slopScreen = 100f))
        // 边界：正好 300ms 仍算（<=）
        assertTrue(MapViewRules.isDoubleTap(nowMs = 1300, lastTapMs = 1000, dx = 0f, dy = 0f, slopScreen = 100f))
        assertEquals(300L, MapViewRules.DOUBLE_TAP_MS)
    }

    // ── A 模式配色兜底链 ────────────────────────────────

    @Test
    fun `矢量上色按要素色层配色色组依次兜底`() {
        val withColor = MapFeature(id = "a", color = "#8DAB87")
        assertEquals("#8DAB87", MapViewRules.vectorFillHex(withColor, "#123456"))

        val noColor = MapFeature(id = "b", color = "")
        assertEquals("层配色优先", "#123456", MapViewRules.vectorFillHex(noColor, "#123456"))

        val groupOnly = MapFeature(id = "c", color = "", colorGroup = 3)
        val hex = MapViewRules.vectorFillHex(groupOnly, "")
        assertTrue(hex.startsWith("#") && hex.length == 7)

        // 什么都没有 → 空串（交给主题兜底色，不硬编）
        assertEquals("", MapViewRules.vectorFillHex(MapFeature(id = "d"), ""))
    }

    // ── 与投影公式的一致性（不改 fit/withUser，只验证联动）──────

    @Test
    fun `缩放后内容尺寸与投影一致`() {
        // 1000×600 画布放 1024×648 的图：**高度是受限轴**（648 更高），所以内容宽 < 画布宽。
        // 这里直接用投影算出的 scale，不手写假设值 —— 否则就是在测自己的想象。
        val base = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 1000f, 600f, padding = 2f)
        val contentW = (1024.0 * base.scale).toFloat()
        val contentH = (648.0 * base.scale).toFloat()
        // 高度轴已经贴边（内容高≈596 + 4 留边 = 600）→ 几乎不能上下拖
        assertEquals(0f, MapViewRules.panLimit(contentH, 600f), 1f)
        // 宽度方向内容比画布窄 → 也不能拖（fit 是等比居中的）
        assertEquals(0f, MapViewRules.panLimit(contentW, 1000f), 1f)
        // zoom=3：宽度富余 (contentW*3 - 1000)/2
        assertEquals((contentW * 3f - 1000f) / 2f, MapViewRules.panLimit(contentW * 3f, 1000f), 1f)
        // 且随 zoom 单调增大
        assertTrue(
            MapViewRules.panLimit(contentW * 4f, 1000f) > MapViewRules.panLimit(contentW * 3f, 1000f),
        )
    }
}
