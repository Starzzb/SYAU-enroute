package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.data.ScheduleMath
import cn.dkarbon.enroute.data.Timetable
import cn.dkarbon.enroute.ui.timetable.BigSession
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 课表 → 时间轴换算的回归测试。
 *
 * 为什么先测这一块：今日卡、上课提醒、桌面小组件三处都依赖它，
 * 一处算错三处一起错（而且提醒错了是「课上完了才响」，用户会骂人）。
 */
class ScheduleMathTest {

    private fun course(
        name: String,
        weekday: Int,
        sections: List<Int>,
        weeks: List<Int> = (1..16).toList(),
        room: String = "主校 / 606",
    ) = Course(
        name = name,
        teacher = "张老师",
        weeks = weeks,
        weekday = weekday,
        sections = sections,
        room = room,
    )

    private fun tt(vararg c: Course) = Timetable(courses = c.toList())

    // 2026-11-10 是周二。冬天（11 月）→ 冬季作息，下午 13:30 上课
    private val tuesday = LocalDate.of(2026, 11, 10)

    @Test
    fun `周二只出周二的课`() {
        val t = tt(
            course("高等数学", weekday = 2, sections = listOf(1, 2)),
            course("大学英语", weekday = 3, sections = listOf(1, 2)),
        )
        val slots = ScheduleMath.slotsOf(t, tuesday, week = 10, summer = false)
        assertEquals(1, slots.size)
        assertEquals("高等数学", slots[0].course.name)
    }

    @Test
    fun `不在本周的课不出现`() {
        val t = tt(course("单周课", weekday = 2, sections = listOf(3, 4), weeks = listOf(1, 3, 5, 7)))
        assertTrue(ScheduleMath.slotsOf(t, tuesday, week = 10, summer = false).isEmpty())
        assertEquals(1, ScheduleMath.slotsOf(t, tuesday, week = 7, summer = false).size)
    }

    @Test
    fun `未校准时不做周次过滤 宁可多显示`() {
        val t = tt(course("单周课", weekday = 2, sections = listOf(3, 4), weeks = listOf(1, 3, 5)))
        assertEquals(1, ScheduleMath.slotsOf(t, tuesday, week = null, summer = false).size)
    }

    @Test
    fun `一节课占两个小节 只出一个大节`() {
        val t = tt(course("高等数学", weekday = 2, sections = listOf(1, 2)))
        val slots = ScheduleMath.slotsOf(t, tuesday, week = 10, summer = false)
        assertEquals(1, slots.size)
        assertEquals(BigSession.S1, slots[0].big)
    }

    @Test
    fun `跨午休的课会拆成两个大节`() {
        // 5-8 节 = S3 + S4
        val t = tt(course("实验课", weekday = 2, sections = listOf(5, 6, 7, 8)))
        val slots = ScheduleMath.slotsOf(t, tuesday, week = 10, summer = false)
        assertEquals(2, slots.size)
        assertEquals(BigSession.S3, slots[0].big)
        assertEquals(BigSession.S4, slots[1].big)
    }

    @Test
    fun `冬季下午 13点30上课 夏季顺延半小时`() {
        val winter = SchoolClock.rangeOf(BigSession.S3, summer = false)
        val summer = SchoolClock.rangeOf(BigSession.S3, summer = true)
        assertEquals(13 * 60 + 30, winter.first)
        assertEquals(14 * 60, summer.first)
        // 上午不受作息影响
        assertEquals(
            SchoolClock.rangeOf(BigSession.S1, summer = false),
            SchoolClock.rangeOf(BigSession.S1, summer = true),
        )
    }

    @Test
    fun `下一节课是结束时间还没过的那节`() {
        val t = tt(
            course("第一节", weekday = 2, sections = listOf(1, 2)),   // 08:00-09:40
            course("第二节", weekday = 2, sections = listOf(3, 4)),   // 10:00-11:40
        )
        // 09:50 —— 第一节已下课，第二节还没开始
        val next = ScheduleMath.nextSlot(t, LocalDateTime.of(2026, 11, 10, 9, 50), 10, false)
        assertEquals("第二节", next?.course?.name)

        // 09:30 —— 第一节还在上（用 endMin 判定，所以「下一节」仍是它）
        val during = ScheduleMath.nextSlot(t, LocalDateTime.of(2026, 11, 10, 9, 30), 10, false)
        assertEquals("第一节", during?.course?.name)
    }

    @Test
    fun `正在上的课单独能取到`() {
        val t = tt(course("第一节", weekday = 2, sections = listOf(1, 2)))
        val cur = ScheduleMath.currentSlot(t, LocalDateTime.of(2026, 11, 10, 8, 30), 10, false)
        assertEquals("第一节", cur?.course?.name)

        val none = ScheduleMath.currentSlot(t, LocalDateTime.of(2026, 11, 10, 12, 0), 10, false)
        assertNull(none)
    }

    @Test
    fun `全部上完了就没有下一节`() {
        val t = tt(course("第一节", weekday = 2, sections = listOf(1, 2)))
        assertNull(ScheduleMath.nextSlot(t, LocalDateTime.of(2026, 11, 10, 22, 30), 10, false))
    }

    @Test
    fun `人话时长`() {
        assertEquals("现在", ScheduleMath.humanize(0))
        assertEquals("25 分钟后", ScheduleMath.humanize(25))
        assertEquals("1 小时后", ScheduleMath.humanize(60))
        assertEquals("1 小时 30 分后", ScheduleMath.humanize(90))
        assertEquals("2 天后", ScheduleMath.humanize(60 * 48))
    }

    @Test
    fun `时间标签补零`() {
        val t = tt(course("早课", weekday = 2, sections = listOf(1, 2)))
        val slot = ScheduleMath.slotsOf(t, tuesday, week = 10, summer = false).first()
        assertEquals("08:00", slot.startLabel)
        assertEquals("09:40", slot.endLabel)
        assertEquals("08:00–09:40", slot.rangeLabel)
    }
}
