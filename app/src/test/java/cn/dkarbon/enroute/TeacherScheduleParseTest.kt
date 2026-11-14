package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UrpHomeApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教师课表 parsers（票 08）。
 *
 * 夹具 `home/urp_teacher_schedule.json` 是 2026-11-14 **实测的真实响应**
 * （原样拷自 `docs/_probe/urp/teacher_schedule_callback.json`），
 * 所以这里锁的是真契约：字段名、位图周次、星期/节次的取法。
 */
class TeacherScheduleParseTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("home/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun schedule() = UrpHomeApi.parseTeacherSchedule(res("urp_teacher_schedule.json"))

    @Test
    fun `真实夹具解析出三个时段两门课`() {
        val s = schedule()
        assertEquals(3, s.slots.size)
        assertEquals(2, s.courseCount)
        assertEquals("董秀茹", s.teacherName)
        assertEquals("1995500023", s.teacherNo)
        assertEquals("2026-2027-1-1", s.term)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `时段字段逐个对得上`() {
        val slot = schedule().slots.first()
        assertEquals("不动产估价", slot.courseName)
        assertEquals("901040302", slot.courseNo)
        assertEquals("01", slot.seqNo)
        assertEquals("周二", cn.dkarbon.enroute.ui.home.HomeModel.weekdayLabel(slot.weekday))
        assertEquals(3, slot.startSection)
        assertEquals(2, slot.span)
        // 小节展开：起始 3 连 2 → [3,4]
        assertEquals(listOf(3, 4), slot.sections)
        assertEquals("第二教学楼", slot.building)
        assertEquals("606", slot.room)
        assertEquals("主校", slot.campus)
        assertEquals("3", slot.credit)
        assertEquals("常规考试（闭卷）", slot.examType)
        assertEquals("24土管1,24土管2", slot.className)
        assertEquals(62, slot.students)
        assertEquals("土环", slot.college)
    }

    @Test
    fun `周次来自 24 位位图而不是文字`() {
        val slot = schedule().slots.first()
        // skzc = 111010011111111000000000 → 1,2,3,5,8..15（共 12 个 1）
        assertEquals(12, slot.weeks.size)
        assertEquals(listOf(1, 2, 3, 5), slot.weeks.take(4))
        assertEquals(15, slot.weeks.last())
        assertTrue("第 4 周不该有课", 4 !in slot.weeks)
        // 文字周次只作对照，原文照存
        assertEquals("1-3,5,8-15周上", slot.weekText)
    }

    @Test
    fun `星期的两份来源都能认`() {
        // id.skxq 是数字、外层 skxq 是字符串；第二个时段应当解成周三/周四各一条
        val s = schedule()
        assertEquals(2, s.slots[0].weekday)
        assertEquals(2, s.slots[1].weekday)
        assertEquals(4, s.slots[2].weekday)
    }

    @Test
    fun `空响应与坏响应都不抛异常`() {
        assertTrue(UrpHomeApi.parseTeacherSchedule("[]").isEmpty)
        assertTrue(UrpHomeApi.parseTeacherSchedule("[[]]").isEmpty)
        assertTrue(UrpHomeApi.parseTeacherSchedule("{}").isEmpty)
        assertTrue(UrpHomeApi.parseTeacherSchedule("not json").isEmpty)
        assertTrue(UrpHomeApi.parseTeacherSchedule("").isEmpty)
    }

    @Test
    fun `第一步表单字段与顺序按实测 body`() {
        assertEquals(
            listOf(
                "teacherNum" to "1995500023",
                "teacherName" to "董秀茹",
                "planCode" to "2026-2027-1-1",
                "planName" to "2026-2027学年秋",
            ),
            UrpHomeApi.teacherScheduleLoForm("1995500023", "董秀茹", "2026-2027-1-1", "2026-2027学年秋"),
        )
    }

    @Test
    fun `第二步路径带两个查询参数`() {
        val p = UrpHomeApi.teacherSchedulePath("2026-2027-1-1", "1995500023")
        assertTrue(p, p.startsWith("/student/teachingResources/teacherCurriculum/searchCurriculumInfo/callback?"))
        assertTrue(p, p.contains("planCode=2026-2027-1-1"))
        assertTrue(p, p.contains("teacherNum=1995500023"))
    }
}
