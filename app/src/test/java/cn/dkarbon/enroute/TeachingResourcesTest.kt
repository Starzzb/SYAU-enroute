package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UrpHomeApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 教学资源三条课表（课程 / 班级 / 教室）的解析与查询参数。
 *
 * 夹具是 2026-11-28 真机登录态下抓的**原始响应**，原样放在 `src/test/resources/urp/`：
 * 换服务器改字段时这几条会先红，比等到真机白屏早得多。
 */
class TeachingResourcesTest {

    private fun fx(name: String): String =
        TeachingResourcesTest::class.java.getResource("/urp/$name")!!.readText(Charsets.UTF_8)

    // ── 课程检索 ─────────────────────────────────────────

    @Test
    fun `课程检索 全大写键也能解析出字段`() {
        val page = UrpHomeApi.parseCourses(fx("tr_course.json"))
        assertEquals(2189, page.totalCount)
        assertEquals(10, page.hits.size)
        assertFalse(page.sessionLost)
        val first = page.hits.first()
        assertEquals("新生资格审查(农学)", first.name)
        assertEquals("X304101", first.courseNo)
        assertEquals("01", first.seqNo)
        assertEquals("X304101-01", first.code)
        assertEquals("1", first.credit)
        assertEquals("学生处", first.deptName)
        assertEquals("网络分散考试", first.examType)
        assertEquals("必修课", first.category)
        assertEquals("2026-2027-1-1", first.term)
        assertEquals("2026-2027学年秋", first.termName)
    }

    @Test
    fun `课程检索 不是 JSON 时给空页 不抛`() {
        val page = UrpHomeApi.parseCourses("<html>loginForm</html>")
        assertEquals(0, page.totalCount)
        assertEquals(0, page.hits.size)
    }

    @Test
    fun `课程检索表单 课名进 kcm 课号位留空 页大小封顶 200`() {
        val form = UrpHomeApi.courseSearchForm("2026-2027-1-1", " 园艺 ", 1, 999).toMap()
        assertEquals("2026-2027-1-1", form["zxjxjhh"])
        assertEquals("园艺", form["kcm"])
        // kcm 与 kch 是「且」，两边都填结果恒为 0 条
        assertEquals("", form["kch"])
        assertEquals("200", form["pageSize"])
    }

    @Test
    fun `课程检索表单 课程号进 kch 课名位留空`() {
        val form = UrpHomeApi.courseSearchForm("2026-2027-1-1", "X304101", 1, 40).toMap()
        assertEquals("X304101", form["kch"])
        assertEquals("", form["kcm"])
    }

    @Test
    fun `课程号判别 汉字算课名 纯 ASCII 带数字算课号`() {
        assertTrue(UrpHomeApi.looksLikeCourseNo("X304101"))
        assertTrue(UrpHomeApi.looksLikeCourseNo(" 901020203 "))
        assertFalse(UrpHomeApi.looksLikeCourseNo("不动产"))
        assertFalse(UrpHomeApi.looksLikeCourseNo("观赏园艺学（Ⅱ）"))
        // 带数字但含汉字：当课名查，否则「大学英语1」永远查不到
        assertFalse(UrpHomeApi.looksLikeCourseNo("大学英语1"))
        assertFalse(UrpHomeApi.looksLikeCourseNo(""))
    }

    // ── 班级检索 ─────────────────────────────────────────

    @Test
    fun `班级检索 数组套对象也能解析出方案号`() {
        val page = UrpHomeApi.parseClasses(fx("tr_class.json"))
        assertEquals(8799, page.totalCount)
        assertEquals(10, page.hits.size)
        val first = page.hits.first()
        assertEquals("12动医1", first.className)
        assertEquals("12动医1", first.classNo)
        // 查这个班课表要用的就是这个号，不是当前学期号 —— 搞错会拿到 [[]]
        assertEquals("2016-2017-1-1", first.planNo)
        assertEquals("2012", first.grade)
        assertEquals("动物科学与医学学院", first.deptName)
        assertEquals("动物医学", first.subjectName)
    }

    @Test
    fun `班级检索 空数组给空页`() {
        val page = UrpHomeApi.parseClasses("[]")
        assertEquals(0, page.hits.size)
    }

    @Test
    fun `班级检索表单 关键字进 classNum param_value 是死字段留空`() {
        val form = UrpHomeApi.classSearchForm("12动医1", "", "", 1, 40).toMap()
        // 实测 param_value 完全不过滤（全空与填词都 8799），classNum 才是精确匹配
        assertEquals("", form["param_value"])
        assertEquals("12动医1", form["classNum"])
        assertEquals("", form["yearNum"])
    }

    @Test
    fun `班级检索表单 四位数字当年级 不塞 classNum`() {
        val form = UrpHomeApi.classSearchForm("2012", "", "", 1, 40).toMap()
        assertEquals("2012", form["yearNum"])
        assertEquals("", form["classNum"])
    }

    @Test
    fun `班级检索表单 显式年级优先 不被关键字盖掉`() {
        val form = UrpHomeApi.classSearchForm("12动医1", "2012", "102", 1, 40).toMap()
        assertEquals("2012", form["yearNum"])
        assertEquals("12动医1", form["classNum"])
        assertEquals("102", form["departmentNum"])
    }

    // ── 课表回调（三条共用解析）───────────────────────────

    @Test
    fun `课程课表 单门课也能解出周次与教室`() {
        val s = UrpHomeApi.parseTeacherSchedule(fx("tr_courseSlots.json"))
        assertEquals(1, s.slots.size)
        val slot = s.slots.first()
        assertEquals("观赏园艺学（Ⅱ）", slot.courseName)
        assertEquals(2, slot.weekday)
        assertEquals(5, slot.startSection)
        assertEquals(2, slot.span)
        assertEquals(listOf(5, 6), slot.sections)
        assertEquals("第二教学楼", slot.building)
        assertEquals("601", slot.room)
        assertEquals("2026-2027-1-1", slot.term)
        // skzc 24 位位图 111110011111111111000000 → 1-5 与 8-18 周
        assertEquals(16, slot.weeks.size)
        assertTrue(slot.weeks.containsAll(listOf(1, 5, 8, 18)))
        assertFalse(slot.weeks.contains(6))
    }

    @Test
    fun `班级课表 整周多时段都能解出 星期都在 1 到 7`() {
        val s = UrpHomeApi.parseTeacherSchedule(fx("tr_classSlots.json"))
        assertTrue(s.slots.size > 5)
        assertTrue(s.slots.all { it.weekday in 1..7 })
        assertTrue(s.slots.all { it.startSection > 0 })
        assertTrue(s.slots.all { it.courseName.isNotBlank() })
    }

    @Test
    fun `教室课表 也能解出课程与时段`() {
        val s = UrpHomeApi.parseTeacherSchedule(fx("tr_roomSlots.json"))
        assertTrue(s.slots.size > 5)
        assertTrue(s.slots.all { it.weekday in 1..7 })
        assertTrue(s.slots.any { it.building.isNotBlank() })
    }

    @Test
    fun `空课表与 双空数组 都不抛`() {
        assertEquals(0, UrpHomeApi.parseTeacherSchedule("[]").slots.size)
        assertEquals(0, UrpHomeApi.parseTeacherSchedule("[[]]").slots.size)
        assertEquals(0, UrpHomeApi.parseTeacherSchedule("<html>loginForm</html>").slots.size)
    }

    // ── 三条回调的路径 ───────────────────────────────────

    @Test
    fun `课程课表路径 planCode 就是学期号`() {
        val p = UrpHomeApi.courseSlotsPath("2026-2027-1-1", "901020203", "04")
        assertEquals(
            UrpHomeApi.COURSE_SLOTS_CB +
                "?planCode=2026-2027-1-1&courseCode=901020203&courseSequenceCode=04",
            p,
        )
    }

    @Test
    fun `班级课表路径 中文班名要编码`() {
        val p = UrpHomeApi.classSlotsPath("2016-2017-1-1", "12动医1")
        assertTrue(p.startsWith(UrpHomeApi.CLASS_SLOTS_CB + "?planCode=2016-2017-1-1&classCode="))
        assertTrue(p.contains("%E5%8A%A8"))
        assertFalse(p.contains("动"))
    }

    @Test
    fun `教室课表路径 房号必须带楼号前缀`() {
        val p = UrpHomeApi.classroomSlotsPath("2026-2027-1-1", "001", "2", "601")
        assertTrue(p.startsWith(UrpHomeApi.CLASSROOM_SLOTS_CB + "?planNumber=2026-2027-1-1"))
        assertTrue(p.contains("campusNumber=001"))
        assertTrue(p.contains("teachingBuildingNumber=2"))
        assertTrue(p.endsWith("classroomNumber=2-601"))
    }
}
