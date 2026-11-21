package cn.dkarbon.enroute

import cn.dkarbon.enroute.ui.timetable.TtOverrides
import cn.dkarbon.enroute.ui.timetable.TtPresets
import cn.dkarbon.enroute.ui.timetable.TtSlots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 槽位解析（m41）的契约测试。
 *
 * 这里锁的是**序号**：设置页的下标、SharedPreferences 里存的值、
 * 渲染器 when 分支的常量，三处必须一致。改序号先改这里。
 */
class TtPresetTest {

    @Test
    fun `五套预设齐全且互不相同`() {
        assertEquals(5, TtPresets.ALL.size)
        val ids = TtPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // 任意两套至少差三条轴，否则「换预设」看起来没变化
        for (i in 0 until 5) for (j in i + 1 until 5) {
            val a = TtPresets.of(i).slots
            val b = TtPresets.of(j).slots
            val diff = listOf(
                a.shape != b.shape, a.decor != b.decor, a.meta != b.meta,
                a.badge != b.badge, a.erosion != b.erosion, a.motion != b.motion,
                a.tone != b.tone, a.header != b.header, a.grid != b.grid,
                a.backdrop != b.backdrop, a.bgMotion != b.bgMotion,
            ).count { it }
            assertTrue("$i vs $j 只差 $diff 条轴", diff >= 3)
        }
    }

    @Test
    fun `第一套预设等于改造前的样子`() {
        val s = TtPresets.of(0).slots
        assertEquals(0, s.shape)   // 方卡
        assertEquals(1, s.decor)   // 左脊
        assertEquals(0, s.meta)    // 三行
        assertEquals(1, s.erosion) // 上褪
        assertEquals(0, s.tone)    // 四色
        assertEquals(0, s.motion)  // 不动
    }

    @Test
    fun `覆盖位全为负时沿用预设`() {
        for (i in 0 until 5) {
            assertEquals(TtPresets.of(i).slots, TtPresets.resolve(i, TtOverrides()))
        }
    }

    @Test
    fun `单条覆盖只改那一条`() {
        val base = TtPresets.resolve(0, TtOverrides())
        val only = TtPresets.resolve(0, TtOverrides(tone = 2))
        assertEquals(2, only.tone)
        assertEquals(base.shape, only.shape)
        assertEquals(base.decor, only.decor)
        assertEquals(base.meta, only.meta)
    }

    @Test
    fun `越界的覆盖值被钳制而不是崩溃`() {
        val s = TtPresets.resolve(0, TtOverrides(shape = 99, tone = -5, backdrop = 42))
        assertTrue(s.shape in 0..4)
        assertTrue(s.tone in 0..2)
        assertTrue(s.backdrop in 0..4)
    }

    @Test
    fun `互斥由 sanitize 消掉`() {
        // 斜纹带会吃掉第二行，强制只课名
        assertEquals(3, TtPresets.sanitize(TtSlots(badge = 2, meta = 0)).meta)
        // 折角与角点抢同一角落
        assertEquals(0, TtPresets.sanitize(TtSlots(decor = 3, badge = 1)).badge)
        // 细线框没有面，淡底/斜纹没有意义
        assertEquals(0, TtPresets.sanitize(TtSlots(shape = 2, decor = 5)).decor)
        // 无色 + 色块 = 退化成描边
        assertNotEquals(3, TtPresets.sanitize(TtSlots(shape = 3, tone = 2)).shape)
    }

    @Test
    fun `背景动效与底纹是两条独立轴`() {
        // 关动效但保留底纹
        val a = TtPresets.resolve(0, TtOverrides(backdrop = 2, bgMotion = 0))
        assertEquals(2, a.backdrop)
        assertEquals(false, a.bgMotion)
        // 换纯色但开着动效
        val b = TtPresets.resolve(0, TtOverrides(backdrop = 4, bgMotion = 1))
        assertEquals(4, b.backdrop)
        assertEquals(true, b.bgMotion)
    }
}
