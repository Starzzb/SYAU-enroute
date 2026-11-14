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

    // ── 双击锚定（票 23 ①）──────────────────────────────

    @Test
    fun `双击放大后点击点下的内容不动`() {
        // 画布 1000×600；三种位置：中心 / 左上角 / 右下角
        val cw = 1000f
        val ch = 600f
        val cases = listOf(
            500f to 300f,   // 中心（这一档本来就对）
            120f to 80f,    // 左上角附近
            880f to 520f,   // 右下角附近
        )
        for ((tapX, tapY) in cases) {
            for (startZoom in listOf(1f, 2f, 4f)) {
                val zp = MapViewRules.doubleTapZoom(
                    zoom = startZoom, panX = 0f, panY = 0f,
                    tapX = tapX, tapY = tapY, canvasW = cw, canvasH = ch,
                )
                val k = zp.zoom / startZoom
                // 点击点下的那个内容点：它的「基础屏幕坐标」满足 c + 1*(b-c) + pan == tap（pan 初始为 0）
                val bX = tapX
                val bY = tapY
                // 放大后的屏幕坐标必须仍在点击点（误差 < 0.5px，远严于票面 40dp）
                val afterX = cw / 2f + k * (bX - cw / 2f) + zp.panX
                val afterY = ch / 2f + k * (bY - ch / 2f) + zp.panY
                assertEquals("tap($tapX,$tapY) @${startZoom}× 的 x 锚点", tapX, afterX, 0.5f)
                assertEquals("tap($tapX,$tapY) @${startZoom}× 的 y 锚点", tapY, afterY, 0.5f)
            }
        }
    }

    @Test
    fun `双击不会超过最大缩放`() {
        var zoom = 1f
        var panX = 0f
        var panY = 0f
        repeat(8) {
            val zp = MapViewRules.doubleTapZoom(zoom, panX, panY, 300f, 200f, 1000f, 600f)
            zoom = zp.zoom; panX = zp.panX; panY = zp.panY
        }
        assertEquals(MapViewRules.MAX_ZOOM, zoom, 1e-4f)
        // 到顶之后再双击是 no-op（k=1），不能继续漂
        val zp = MapViewRules.doubleTapZoom(zoom, panX, panY, 300f, 200f, 1000f, 600f)
        assertEquals(zoom, zp.zoom, 1e-4f)
        assertEquals(panX, zp.panX, 1e-4f)
    }

    // ── 惯性甩动（票 23 ②）──────────────────────────────

    @Test
    fun `摩擦衰减按帧率连续换算`() {
        // 一帧（16.67ms）正好剩 85%
        assertEquals(0.85f, MapViewRules.flingDecay(MapViewRules.FRAME_MS), 1e-3f)
        // 两帧 = 0.85²（掉帧时滑行距离不该突然变短）
        assertEquals(0.7225f, MapViewRules.flingDecay(MapViewRules.FRAME_MS * 2f), 1e-3f)
        // dt<=0 不衰减
        assertEquals(1f, MapViewRules.flingDecay(0f), 1e-6f)
        // 单调递减
        assertTrue(MapViewRules.flingDecay(20f) < MapViewRules.flingDecay(10f))
    }

    @Test
    fun `速度太小不滑行`() {
        val minPx = 100f
        assertFalse(MapViewRules.shouldFling(99f, minPx))     // 轻推 → 直接停
        assertFalse(MapViewRules.shouldFling(0f, minPx))
        assertTrue(MapViewRules.shouldFling(100f, minPx))     // 边界值要滑
        assertTrue(MapViewRules.shouldFling(2500f, minPx))
        assertFalse(MapViewRules.shouldFling(Float.NaN, minPx))
    }

    @Test
    fun `滑行位移与速度成正比`() {
        val limit = 5000f
        // 1000px/s 走一帧 ≈ 16.67px；2000px/s 走两倍
        val slow = MapViewRules.flingStep(0f, 1000f, MapViewRules.FRAME_MS, limit)
        val fast = MapViewRules.flingStep(0f, 2000f, MapViewRules.FRAME_MS, limit)
        assertEquals(16.6f, slow, 0.2f)
        assertEquals(slow * 2f, fast, 0.4f)
    }

    @Test
    fun `滑到边界施阻尼而不是硬停`() {
        // 所有中间量都写成命名变量 —— 上一版这里写了一个来源不明的 `600f`，
        // 它其实隐含了「起点是 0」这个前提，和 `start = 90f` 冲突，
        // 于是断言与实现静默错位（看起来在测、其实测错了，比没测更糟）。
        val limit = 100f
        val start = 90f                 // 已经贴近边界
        val velocity = 10_000f          // px/s
        val dtMs = 60f
        val rawStep = velocity * dtMs / 1000f          // 这一帧想走多远（= 600）
        val rawLanding = start + rawStep               // 未加阻尼的落点（= 690）
        val over = rawLanding - limit                  // 越界多少（= 590）

        val panned = MapViewRules.flingStep(start, velocity, dtMs, limit)

        // ① 确实越过了边界（有阻尼位移，不是硬停在 limit）
        assertTrue("应当越过边界（留一点阻尼位移）：$panned", panned > limit)
        // ② 落点 = 边界 + 越界部分 × 35%
        assertEquals(limit + over * MapViewRules.OVERDRAG_DECAY, panned, 1.5f)
        // ③ 方向性：越界越多，阻尼吃掉的比例越多（阻尼后的越界量必定小于原始越界量）
        assertTrue(
            "阻尼后的越界量 ${panned - limit} 应小于原始越界量 $over",
            panned - limit < over,
        )
        // ④ 同一速度但没越界 → 一点不衰减
        val insideStart = 0f
        val tinyDt = 1f
        val insideRaw = insideStart + velocity * tinyDt / 1000f   // = 10
        assertEquals(insideRaw, MapViewRules.flingStep(insideStart, velocity, tinyDt, limit), 0.2f)
        assertEquals(10f, insideRaw, 0.2f)                        // 顺手钉住这一档的直觉值
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
