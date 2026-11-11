package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UrpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URP 课表解析的回归测试（2026-11-11）。
 *
 * 样本 `urp_schedule.json` 是**真实抓包**（已把学号换成 0000000000），
 * 不是手写片段 —— 这次的 bug 恰恰是「只测了字段名、没测能不能排进星期与节次」。
 *
 * 血的教训：上一版 `parseSchedule` 只读 courseName / teacher，
 * 完全没碰 `timeAndPlaceList`，于是 7 门课全部「未排」、界面显示 0 节。
 * 所以这里的断言必须落到 **weekday / sections / weeks** 三个字段上。
 */
class UrpScheduleParseTest {

    private fun raw(): String =
        javaClass.getResourceAsStream("/feed/urp_schedule.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val tt by lazy { UrpClient.parseSchedule(raw()) }

    @Test
    fun `能把课程解析出来`() {
        assertTrue("解析出 0 门课", tt.courses.isNotEmpty())
    }

    @Test
    fun `有课时的课程必须带星期和节次`() {
        // 真实数据里 4 门课有排课时间、3 门是空 timeAndPlaceList
        val scheduled = tt.courses.filter { it.weeks.isNotEmpty() }
        assertTrue("排上课的课程太少（${scheduled.size}），解析器可能又没读 timeAndPlaceList", scheduled.size >= 4)

        scheduled.forEach { c ->
            assertTrue("「${c.name}」星期为空", c.weekday in 1..7)
            assertTrue("「${c.name}」节次为空", c.sections.isNotEmpty())
            assertTrue("「${c.name}」教室为空", c.room.isNotBlank())
        }
    }

    @Test
    fun `连堂两节要拆成两个小节号`() {
        // 社会主义发展史：classSessions=9, continuingSession=2 → [9,10]
        val c = tt.courses.first { it.name.contains("社会主义发展史") }
        assertEquals(listOf(9, 10), c.sections)
        assertEquals(6, c.weekday)
    }

    @Test
    fun `周次位图解码正确`() {
        // 形势与政策Ⅴ：classWeek=111010000000000000000000 → 第 1,2,3,5 周
        val c = tt.courses.first { it.name.contains("形势与政策") }
        assertEquals(listOf(1, 2, 3, 5), c.weeks)
        assertEquals(1, c.weekday)
        assertEquals(listOf(7, 8), c.sections)
    }

    @Test
    fun `一门课多个时间段要拆成多条`() {
        // 不动产估价在真实数据里周二、周四各上一次
        val bdc = tt.courses.filter { it.name.contains("不动产估价") }
        assertTrue("不动产估价应该拆出多条（实际 ${bdc.size}）", bdc.size >= 2)
        assertEquals("上课的星期应该各不相同", bdc.map { it.weekday }.distinct().size, bdc.size)
    }

    @Test
    fun `真实数据里七门课全部排上了`() {
        // 实测：这位同学的 7 门课 timeAndPlaceList 都有内容（共 14 个时间段）
        assertEquals("应该拆成 7 门以上（多时间段会拆多条）", true, tt.courses.size >= 7)
        val named = tt.courses.map { it.name }.distinct()
        assertEquals("应有 7 门不同的课", 7, named.size)
        assertTrue("有课没排上时间：" + tt.courses.filter { it.weeks.isEmpty() }.joinToString { it.name },
            tt.courses.none { it.weeks.isEmpty() })
    }

    @Test
    fun `timeAndPlaceList 为空的课程要保留成未排条目`() {
        // 兜底分支用构造数据测：学校确实存在「选了课但没排时间」的情况
        val json = """
            {"xkxx":[{"999_01":{
              "courseName":"占位课程",
              "attendClassTeacher":"某某* ",
              "courseCategoryName":"选修课",
              "id":{"coureNumber":"999","coureSequenceNumber":"01",
                    "executiveEducationPlanNumber":"2026-2027-1-1"},
              "timeAndPlaceList":[]}}]}
        """.trimIndent()
        val t = UrpClient.parseSchedule(json)
        assertEquals(1, t.courses.size)
        assertEquals("占位课程", t.courses[0].name)
        assertTrue("应该是未排（weeks 为空）", t.courses[0].weeks.isEmpty())
        assertEquals("某某", t.courses[0].teacher)
    }

    @Test
    fun `学期能从培养方案号里提出来`() {
        assertEquals("2026-2027-1", tt.term)
    }

    @Test
    fun `空位图不会抛异常`() {
        assertTrue(UrpClient.decodeWeekBitmap("").isEmpty())
        assertEquals(listOf(1), UrpClient.decodeWeekBitmap("1"))
        assertEquals(listOf(2, 4), UrpClient.decodeWeekBitmap("0101"))
    }
}
