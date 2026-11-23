package cn.dkarbon.enroute

import cn.dkarbon.enroute.ui.timetable.TtAxis
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
                a.tess != b.tess, a.tessMode != b.tessMode,
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
    fun `超立方体关掉时不改写转动逻辑`() {
        // 锁只表达在设置页那一行（置灰 + 不显示选中值），值的保存不受影响：
        // 关掉再打开要能回到原来选的档，所以 sanitize 不许把它压成定格。
        val off = TtPresets.resolve(0, TtOverrides(tess = 0, tessMode = 2))
        assertEquals(0, off.tess)
        assertEquals(2, off.tessMode)

        // 显示时可以自由选档
        val on = TtPresets.resolve(0, TtOverrides(tess = 2, tessMode = 4))
        assertEquals(2, on.tess)
        assertEquals(4, on.tessMode)
    }

    @Test
    fun `转动逻辑是超立方体的子轴`() {
        assertEquals(-1, TtAxis.parentOf(TtAxis.SHAPE))
        assertEquals(TtAxis.TESS, TtAxis.parentOf(TtAxis.TESS_MODE))
        // 13 条轴的取名、取值表长度必须一致，否则设置页会下标越界
        assertEquals(13, TtAxis.sizes.size)
        assertEquals(13, TtAxis.names.size)
        assertEquals(13, TtAxis.options.size)
        for (i in 0 until 13) {
            assertEquals(TtAxis.options[i].size, TtAxis.sizes[i])
        }
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

    @Test
    fun `预设可以展开成显式覆盖值`() {
        // 2026-11-23：点预设 = 把 13 条轴写成具体值，界面里不再有「跟随」这一枚。
        // 展开后再解析必须回到同一套槽位（不然点了预设观感会飘）。
        for (i in 0 until 5) {
            val slots = TtPresets.of(i).slots
            val e = TtPresets.explicit(slots)
            assertEquals(slots, TtPresets.resolve(i, e))
            // 展开的结果不许留 -1：设置页每一行都得读得出一个确定值
            assertTrue(e.shape >= 0 && e.decor >= 0 && e.meta >= 0 && e.badge >= 0)
            assertTrue(e.erosion >= 0 && e.motion >= 0 && e.tone >= 0)
            assertTrue(e.header >= 0 && e.grid >= 0 && e.backdrop >= 0)
            assertTrue(e.bgMotion >= 0 && e.tess >= 0 && e.tessMode >= 0)
        }
    }

    @Test
    fun `账页是唯一带版式标记的预设`() {
        // 版式标记只影响页头质感与未排课行；格子一律由 slots 决定，
        // 所以除账页外都必须是 0，否则又会出现「格子走另一条渲染分支」。
        assertEquals(1, TtPresets.layoutOf(TtPresets.indexOf("ledger")))
        for (i in 0 until 5) {
            if (TtPresets.of(i).id != "ledger") assertEquals(0, TtPresets.layoutOf(i))
        }
    }
}
