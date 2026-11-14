package cn.dkarbon.enroute

import cn.dkarbon.enroute.ui.timetable.BigSession
import cn.dkarbon.enroute.ui.timetable.DayPart
import cn.dkarbon.enroute.ui.timetable.eatProgress
import cn.dkarbon.enroute.ui.timetable.gridRowTop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表网格几何 + 侵蚀进度（2026-11-13 真机验收后补的回归测试）。
 *
 * 由来：真机上「课程卡和横格线错开约 14dp」，根因是行顶坐标没把
 * **本行所属的那条时段条**算进去（第一条大节「上午」顶上也有条）。
 * 这个 bug 属于纯算术，JVM 单测就能钉死，不该等真机才发现。
 */
class GridGeometryTest {

    private val rowH = 100
    private val bandH = 14

    /** 把 (rows, bands) 换算成像素，模拟 GridMetrics.yOfRow */
    private fun rowTop(i: Int): Int {
        val (rows, bands) = gridRowTop(i)
        return rowH * rows + bandH * bands
    }

    @Test
    fun `每一行都含本行所属的时段条`() {
        // 上午：S1 顶上就是「上午」条
        assertEquals(0 to 1, gridRowTop(0))
        assertEquals(1 to 1, gridRowTop(1))
        // 下午：S3 顶上多了「下午」条
        assertEquals(2 to 2, gridRowTop(2))
        assertEquals(3 to 2, gridRowTop(3))
        // 晚上：S5 顶上多了「晚上」条
        assertEquals(4 to 3, gridRowTop(4))
        assertEquals(5 to 3, gridRowTop(5))
    }

    @Test
    fun `行顶坐标按 行高加时段条 累加且首行不为零`() {
        assertEquals(14, rowTop(0))                    // 上午条
        assertEquals(14 + 100, rowTop(1))
        assertEquals(14 + 100 * 2 + 14, rowTop(2))     // 上午条 + 两行 + 下午条
        assertEquals(14 + 100 * 3 + 14, rowTop(3))
        assertEquals(14 + 100 * 4 + 28, rowTop(4))     // 再加晚上条
        assertEquals(14 + 100 * 5 + 28, rowTop(5))
        // 关键断言：行顶严格递增，且相邻行差值 = rowH 或 rowH + bandH
        for (i in 1..5) {
            val d = rowTop(i) - rowTop(i - 1)
            assertTrue("第 $i 行间距异常：$d", d == rowH || d == rowH + bandH)
        }
        // 首行必须给「上午」条让位（这正是当初错开 14dp 的根源）
        assertTrue("首行没有给上午条让位", rowTop(0) > 0)
    }

    @Test
    fun `行底与下一行顶之间正好差一条时段条`() {
        val totalBands = 3
        val total = rowH * BigSession.entries.size + bandH * totalBands
        // 最后一个行底 == 网格总高
        assertEquals(total, rowTop(5) + rowH)
        // 下午第一行(S3) 的行顶 - 上一行行底 == bandH
        assertEquals(bandH, rowTop(2) - (rowTop(1) + rowH))
        assertEquals(bandH, rowTop(4) - (rowTop(3) + rowH))
        // 同一时段内的相邻行之间没有额外间距
        assertEquals(0, rowTop(1) - (rowTop(0) + rowH))
    }

    @Test
    fun `时段分组是 上午2 下午2 晚上2`() {
        val byPart = BigSession.entries.groupBy { it.part }
        assertEquals(2, byPart[DayPart.Morning]!!.size)
        assertEquals(2, byPart[DayPart.Afternoon]!!.size)
        assertEquals(2, byPart[DayPart.Evening]!!.size)
    }

    @Test
    fun `侵蚀进度按时段起止线性推进`() {
        // 冬季作息 S1 = 08:00–09:40
        assertEquals(0f, eatProgress(BigSession.S1, 8 * 60, summer = false))
        assertEquals(1f, eatProgress(BigSession.S1, 9 * 60 + 40, summer = false))
        assertEquals(0f, eatProgress(BigSession.S1, 7 * 60 + 30, summer = false))
        assertEquals(1f, eatProgress(BigSession.S1, 12 * 60, summer = false))
        val half = eatProgress(BigSession.S1, 8 * 60 + 50, summer = false)  // 正好一半
        assertTrue("进度应接近 0.5，实际 $half", half in 0.49f..0.51f)
    }

    @Test
    fun `夏季作息下午整体推迟三十分钟`() {
        // 冬季 13:30 开始，夏季 14:00 开始
        assertEquals(13 * 60 + 30, cn.dkarbon.enroute.ui.timetable.SchoolClock.rangeOf(BigSession.S3, false).first)
        assertEquals(14 * 60, cn.dkarbon.enroute.ui.timetable.SchoolClock.rangeOf(BigSession.S3, true).first)
        // 上午不受影响
        assertEquals(
            cn.dkarbon.enroute.ui.timetable.SchoolClock.rangeOf(BigSession.S1, false),
            cn.dkarbon.enroute.ui.timetable.SchoolClock.rangeOf(BigSession.S1, true),
        )
    }
}