package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.data.custom.CustomCourse
import cn.dkarbon.enroute.data.custom.CustomRule
import cn.dkarbon.enroute.data.custom.CustomRules
import cn.dkarbon.enroute.data.custom.CustomCourseFile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 自定义课程（票 19）：规则展开、冲突合并、学期过滤、人话预览、持久化编解码。
 *
 * 全部纯函数 + 注入日期，不碰真实时钟与文件。
 */
class CustomCourseTest {

    // 2026-09-07 是周一（第 1 周周一）
    private val week1Monday = LocalDate.of(2026, 9, 7)

    private fun weekly(
        from: Int = 3,
        to: Int = 16,
        parity: CustomRule.Parity = CustomRule.Parity.ALL,
        weekday: Int = 2,
        big: Int = 3,
    ) = CustomRule.Weekly(from, to, parity, weekday, big)

    private fun course(
        id: String = "c1",
        name: String = "小组会议",
        rules: List<CustomRule> = listOf(weekly()),
        note: String = "",
        termCode: String = "",
    ) = CustomCourse(id = id, name = name, rules = rules, note = note, termCode = termCode)

    // ── 规则展开 ─────────────────────────────────────────

    @Test
    fun `按周次区间只在区间内出现`() {
        val c = course()
        // 第 1、2 周不该出现（验收：第 1–2 周不出现）
        assertEquals(0, CustomRules.slotsOf(c, 2, week1Monday, "").size)
        // 第 3、16 周要有
        assertEquals(1, CustomRules.slotsOf(c, 3, week1Monday, "").size)
        assertEquals(1, CustomRules.slotsOf(c, 16, week1Monday, "").size)
        // 第 17 周超出
        assertEquals(0, CustomRules.slotsOf(c, 17, week1Monday, "").size)
        // 落位：周二、第 3 大节
        val slot = CustomRules.slotsOf(c, 5, week1Monday, "").first()
        assertEquals(2, slot.weekday)
        assertEquals(3, slot.bigIndex)
    }

    @Test
    fun `单周双周规则正确`() {
        val odd = course(rules = listOf(weekly(parity = CustomRule.Parity.ODD)))
        val even = course(rules = listOf(weekly(parity = CustomRule.Parity.EVEN)))
        // 单周：第 3、5 周有；第 4、6 周没有
        assertTrue(CustomRules.slotsOf(odd, 3, week1Monday, "").isNotEmpty())
        assertTrue(CustomRules.slotsOf(odd, 5, week1Monday, "").isNotEmpty())
        assertTrue(CustomRules.slotsOf(odd, 4, week1Monday, "").isEmpty())
        assertTrue(CustomRules.slotsOf(odd, 6, week1Monday, "").isEmpty())
        // 双周：第 4、6 周有；第 3、5 周没有
        assertTrue(CustomRules.slotsOf(even, 4, week1Monday, "").isNotEmpty())
        assertTrue(CustomRules.slotsOf(even, 6, week1Monday, "").isNotEmpty())
        assertTrue(CustomRules.slotsOf(even, 3, week1Monday, "").isEmpty())
        assertTrue(CustomRules.slotsOf(even, 5, week1Monday, "").isEmpty())
    }

    @Test
    fun `固定某一天只落在那一周且星期由日期决定`() {
        // 2026-11-20 是周五 → 第 11 周（9/7 起算）
        val d = LocalDate.of(2026, 11, 20)
        assertEquals(5, d.dayOfWeek.value)
        val week = CustomRules.weekOfDate(d, week1Monday)
        assertEquals(11, week)
        val c = course(rules = listOf(CustomRule.SingleDay(date = d.toString(), bigIndex = 5)))
        // 目标周有，且星期是 5（不是用户填的什么）
        val slot = CustomRules.slotsOf(c, week, week1Monday, "").single()
        assertEquals(5, slot.weekday)
        assertEquals(5, slot.bigIndex)
        // 前后一周都没有
        assertTrue(CustomRules.slotsOf(c, week - 1, week1Monday, "").isEmpty())
        assertTrue(CustomRules.slotsOf(c, week + 1, week1Monday, "").isEmpty())
    }

    @Test
    fun `一门课两条规则都生效`() {
        // 前八周周二 第 3 大节 + 后八周周四 第 5 大节
        val c = course(
            rules = listOf(
                weekly(from = 1, to = 8, weekday = 2, big = 3),
                weekly(from = 9, to = 16, weekday = 4, big = 5),
            ),
        )
        assertEquals(2, CustomRules.slotsOf(c, 5, week1Monday, "").single().weekday)
        assertEquals(4, CustomRules.slotsOf(c, 12, week1Monday, "").single().weekday)
        // 第 5 周二只有一条规则命中
        assertEquals(1, CustomRules.slotsOf(c, 5, week1Monday, "").size)
    }

    @Test
    fun `大节映射到小节`() {
        assertEquals(listOf(5, 6), CustomRules.sectionsOf(3))
        assertEquals(listOf(1, 2), CustomRules.sectionsOf(1))
        assertEquals(listOf(11, 12), CustomRules.sectionsOf(6))
        assertTrue(CustomRules.sectionsOf(9).isEmpty())
    }

    @Test
    fun `区间填反了会自动纠正`() {
        val r = CustomRules.normalize(CustomRule.Weekly(fromWeek = 20, toWeek = 3, weekday = 9, bigIndex = 0))
        assertEquals(3, r.fromWeek)
        assertEquals(20, r.toWeek)
        assertEquals(7, r.weekday)
        assertEquals(1, r.bigIndex)
    }

    // ── 学期过滤 ─────────────────────────────────────────

    @Test
    fun `不属于当期学期的自定义课不显示`() {
        val c = course(termCode = "2026-2027-1")
        assertTrue(CustomRules.slotsOf(c, 5, week1Monday, "2026-2027-1").isNotEmpty())
        assertTrue("换了学期就不该出现", CustomRules.slotsOf(c, 5, week1Monday, "2025-2026-2").isEmpty())
        // 课程没记学期（空）= 不限制
        val any = course(termCode = "")
        assertTrue(CustomRules.slotsOf(any, 5, week1Monday, "2025-2026-2").isNotEmpty())
    }

    // ── 映射成 UI 的 Course（卡片复用）────────────────────

    @Test
    fun `展开成与教务课同类型的 Course 并带来源标记与备注`() {
        val c = course(note = "带笔记本，很长的备注也不能被截断：①②③ ABCDEFG 1234567890")
        val list = CustomRules.toCourses(c, 5, week1Monday, "")
        val ui = list.single()
        // 同一个类型 Course → 卡片直接复用，不需要第二套
        assertEquals("小组会议", ui.name)
        assertEquals(listOf(5), ui.weeks)
        assertEquals(2, ui.weekday)
        assertEquals(listOf(5, 6), ui.sections)
        // 可区分：origin = custom
        assertEquals(Course.ORIGIN_CUSTOM, ui.origin)
        assertTrue(ui.isCustom)
        // 备注完整带过去
        assertTrue(ui.note.contains("带笔记本"))
        assertTrue(ui.note.contains("1234567890"))
        // 教务课不能被误判成自定义
        assertFalse(Course(name = "普通课").isCustom)
    }

    // ── 冲突合并（教务优先、都可见）────────────────────────

    private fun urp(day: Int, big: Int, name: String) = Course(
        courseNo = "u$name", name = name, weekday = day, weeks = (1..16).toList(),
        sections = CustomRules.sectionsOf(big),
    )

    @Test
    fun `同格冲突时教务占格自定义进冲突列表两门都可见`() {
        val urpList = listOf(urp(2, 3, "教务课"))
        val custom = CustomRules.toCourses(course(), 5, week1Monday, "")
        val (cells, bumped) = CustomRules.mergeIntoGrid(
            urp = urpList, custom = custom, week = 5,
            bigsOf = { sec -> listOf(((sec.firstOrNull() ?: 1) + 1) / 2) },
        )
        // 格子里是教务课（自定义绝不覆盖）
        assertEquals("教务课", cells[2 to 3]?.name)
        // 被挤掉的自定义课在冲突列表里（不静默丢）
        assertEquals(1, bumped.size)
        assertEquals("小组会议", bumped.first().name)
    }

    @Test
    fun `没冲突时自定义课正常占格`() {
        val custom = CustomRules.toCourses(course(), 5, week1Monday, "")
        val (cells, bumped) = CustomRules.mergeIntoGrid(
            urp = emptyList(), custom = custom, week = 5,
            bigsOf = { sec -> listOf(((sec.firstOrNull() ?: 1) + 1) / 2) },
        )
        assertEquals("小组会议", cells[2 to 3]?.name)
        assertTrue(bumped.isEmpty())
    }

    // ── 人话预览 ─────────────────────────────────────────

    @Test
    fun `预览文案与票面一致`() {
        assertEquals(
            "第 3–16 周 · 每周二 · 第 5-6 节",
            CustomRules.preview(CustomRule.Weekly(3, 16, CustomRule.Parity.ALL, 2, 3)),
        )
        assertEquals(
            "第 1–16 周 · 每周（单周）二 · 第 3-4 节",
            CustomRules.preview(CustomRule.Weekly(1, 16, CustomRule.Parity.ODD, 2, 2)),
        )
        assertEquals(
            "2026-11-20（周五）· 第 5-6 节",
            CustomRules.preview(CustomRule.SingleDay("2026-11-20", 3)),
        )
        // 还没选日期
        assertTrue(CustomRules.preview(CustomRule.SingleDay("", 3)).startsWith("尚未选择日期"))
    }

    @Test
    fun `冲突提示文案`() {
        assertEquals("", CustomRules.conflictHint(emptyList()))
        assertEquals("该时段已有《高数》，两门都会显示", CustomRules.conflictHint(listOf("高数")))
        assertTrue(CustomRules.conflictHint(listOf("高数", "线代")).contains("《高数》《线代》"))
        // 重名只提示一次
        assertEquals("该时段已有《高数》，两门都会显示", CustomRules.conflictHint(listOf("高数", "高数")))
    }

    // ── 持久化编解码（sealed 多态）────────────────────────

    @Test
    fun `存盘再读回规则不丢且类型正确`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
        val courses = listOf(
            course(id = "a", name = "自习", rules = listOf(
                CustomRule.Weekly(3, 16, CustomRule.Parity.ODD, 2, 3),
                CustomRule.SingleDay("2026-11-20", 5),
            ), note = "备注也要存下来"),
        )
        val text = json.encodeToString(CustomCourseFile.serializer(), CustomCourseFile(courses = courses))
        val back = json.decodeFromString(CustomCourseFile.serializer(), text).courses
        assertEquals(1, back.size)
        assertEquals("备注也要存下来", back[0].note)
        assertEquals(2, back[0].rules.size)
        assertTrue(back[0].rules[0] is CustomRule.Weekly)
        assertTrue(back[0].rules[1] is CustomRule.SingleDay)
        assertEquals(CustomRule.Parity.ODD, (back[0].rules[0] as CustomRule.Weekly).parity)
        assertEquals("2026-11-20", (back[0].rules[1] as CustomRule.SingleDay).date)
    }

    @Test
    fun `坏档不崩只当空`() {
        val json = Json { ignoreUnknownKeys = true }
        assertTrue(runCatching { json.decodeFromString(CustomCourseFile.serializer(), "not json") }.isFailure)
        // 空列表也正常
        val empty = json.decodeFromString(CustomCourseFile.serializer(), """{"schema":1,"courses":[]}""")
        assertTrue(empty.courses.isEmpty())
    }
}
