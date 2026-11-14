package cn.dkarbon.enroute

import cn.dkarbon.enroute.ui.timetable.WeekSwipeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表左右滑动切周的手势判定（票 28）。
 *
 * 都是纯函数，参数单位是 px（调用方把 dp 换算好再传），所以这里用 px 直白的数字。
 * 重点保的是**纵向优先**：上下滚动看 9–12 节不能被误判成切周。
 */
class WeekSwipeRulesTest {

    private val minSwipePx = 192f      // 64dp @3x
    private val flingPx = 2400f        // 800dp/s @3x

    // ── 正常横滑 ─────────────────────────────────────────

    @Test
    fun `横向够阈值就切周且方向正确`() {
        // 向左滑（dx<0）→ 下一周
        assertEquals(1, WeekSwipeRules.decide(-250f, 8f, 0f, minSwipePx, flingPx))
        // 向右滑（dx>0）→ 上一周
        assertEquals(-1, WeekSwipeRules.decide(250f, 8f, 0f, minSwipePx, flingPx))
        // 刚好到阈值也算
        assertEquals(1, WeekSwipeRules.decide(-192f, 0f, 0f, minSwipePx, flingPx))
    }

    @Test
    fun `位移不够且不甩就不切`() {
        assertEquals(0, WeekSwipeRules.decide(-100f, 5f, 0f, minSwipePx, flingPx))
        assertEquals(0, WeekSwipeRules.decide(-191f, 0f, 300f, minSwipePx, flingPx))
    }

    // ── 纵向优先（最关键）────────────────────────────────

    @Test
    fun `纵向占优一律不切周`() {
        // 往下滚：纵向更大 → 不切（哪怕横向位移也不小）
        assertEquals(0, WeekSwipeRules.decide(-300f, 400f, -3000f, minSwipePx, flingPx))
        // 斜着滑，横向只是略大（不到 1.5 倍）→ 也不切
        assertEquals(0, WeekSwipeRules.decide(-250f, 200f, -3000f, minSwipePx, flingPx))
        // 横向明显占优 → 才切
        assertEquals(1, WeekSwipeRules.decide(-400f, 100f, 0f, minSwipePx, flingPx))
    }

    @Test
    fun `纵向滚动时连接管都不该发生`() {
        // 手指竖着走：无论走多远都不接管（shouldClaim=false → 不 consume → 纵向滚动照常）
        assertFalse(WeekSwipeRules.shouldClaim(30f, 300f, minSwipePx))
        assertFalse(WeekSwipeRules.shouldClaim(120f, 120f, minSwipePx))
        // 横向够大且占优才接管
        assertTrue(WeekSwipeRules.shouldClaim(200f, 20f, minSwipePx))
        assertTrue(WeekSwipeRules.shouldClaim(-250f, 0f, minSwipePx))
        // 横向不够大（未到阈值）也不接管
        assertFalse(WeekSwipeRules.shouldClaim(150f, 0f, minSwipePx))
    }

    // ── 速度兜底 ─────────────────────────────────────────

    @Test
    fun `快速轻扫位移不够也切`() {
        // 只滑了 40px，但速度 3000px/s 向左 → 下一周
        assertEquals(1, WeekSwipeRules.decide(-40f, 5f, -3000f, minSwipePx, flingPx))
        assertEquals(-1, WeekSwipeRules.decide(40f, 5f, 3000f, minSwipePx, flingPx))
    }

    @Test
    fun `速度兜底也要求横向占优且方向不矛盾`() {
        // 竖着甩得再快也不算
        assertEquals(0, WeekSwipeRules.decide(-40f, 300f, -3000f, minSwipePx, flingPx))
        // 位移与速度方向相反（手先过去又拉回）→ 不猜，归位
        assertEquals(0, WeekSwipeRules.decide(-40f, 0f, 3000f, minSwipePx, flingPx))
        // 速度不够快 → 不切
        assertEquals(0, WeekSwipeRules.decide(-40f, 0f, 1000f, minSwipePx, flingPx))
    }

    // ── 边界 ────────────────────────────────────────────

    @Test
    fun `第1周往回滑和最后一周往后滑都不切换`() {
        assertFalse(WeekSwipeRules.canSwipeTo(week = 1, step = -1, minWeek = 1, maxWeek = 20))
        assertFalse(WeekSwipeRules.canSwipeTo(week = 20, step = 1, minWeek = 1, maxWeek = 20))
        assertTrue(WeekSwipeRules.canSwipeTo(week = 2, step = -1, minWeek = 1, maxWeek = 20))
        assertTrue(WeekSwipeRules.canSwipeTo(week = 19, step = 1, minWeek = 1, maxWeek = 20))

        // 边界要**回弹**，不能静默无效
        assertEquals(
            WeekSwipeRules.SwipeOutcome.BOUNCE_AT_EDGE,
            WeekSwipeRules.outcomeFor(step = -1, week = 1, minWeek = 1, maxWeek = 20),
        )
        assertEquals(
            WeekSwipeRules.SwipeOutcome.BOUNCE_AT_EDGE,
            WeekSwipeRules.outcomeFor(step = 1, week = 20, minWeek = 1, maxWeek = 20),
        )
        // 没到阈值：归位（也不是什么都不做）
        assertEquals(
            WeekSwipeRules.SwipeOutcome.SNAP_BACK,
            WeekSwipeRules.outcomeFor(step = 0, week = 5, minWeek = 1, maxWeek = 20),
        )
        // 正常切换
        assertEquals(
            WeekSwipeRules.SwipeOutcome.SWITCHED,
            WeekSwipeRules.outcomeFor(step = 1, week = 5, minWeek = 1, maxWeek = 20),
        )
    }

    // ── 拖动表现 ─────────────────────────────────────────

    @Test
    fun `拖动超出一屏宽后施阻尼`() {
        val w = 1080f
        // 界内：跟手
        assertEquals(300f, WeekSwipeRules.dragOffset(300f, w), 0.01f)
        // 越界：只留 25% 的额外位移
        val dragged = WeekSwipeRules.dragOffset(1500f, w)
        assertTrue("应当超过一屏宽但被阻尼：$dragged", dragged > w && dragged < 1500f)
        assertEquals(w + (1500f - w) * WeekSwipeRules.DRAG_OVER_EDGE_DECAY, dragged, 0.5f)
        // 反向同理
        assertEquals(-dragged, WeekSwipeRules.dragOffset(-1500f, w), 0.5f)
    }

    @Test
    fun `拖动越远越淡且不超过上限`() {
        val w = 1080f
        assertEquals(1f, WeekSwipeRules.alphaFor(0f, w), 1e-4f)
        val half = WeekSwipeRules.alphaFor(w / 2f, w)
        assertTrue(half in 0.6f..0.9f)
        val full = WeekSwipeRules.alphaFor(w, w)
        assertEquals(1f - 0.55f, full, 1e-4f)
        // 拖过头也不会比下限更淡（避免整块看不见）
        assertEquals(full, WeekSwipeRules.alphaFor(w * 3f, w), 1e-4f)
    }

    @Test
    fun `坏数据不炸也不切`() {
        assertEquals(0, WeekSwipeRules.decide(Float.NaN, 0f, 0f, minSwipePx, flingPx))
        assertEquals(0, WeekSwipeRules.decide(-300f, Float.NaN, 0f, minSwipePx, flingPx))
        assertFalse(WeekSwipeRules.shouldClaim(Float.NaN, 0f, minSwipePx))
        assertEquals(0f, WeekSwipeRules.dragOffset(300f, 0f), 0.01f)
        assertEquals(1f, WeekSwipeRules.alphaFor(300f, 0f), 1e-4f)
    }

    @Test
    fun `过渡时长在票面要求的区间内`() {
        assertTrue(
            "过渡时长 ${WeekSwipeRules.TRANSITION_MS}ms 应在 180–220",
            WeekSwipeRules.TRANSITION_MS in 180..220,
        )
    }
}
