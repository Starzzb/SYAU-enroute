package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.curriculum.SyllabusParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程大纲的解析与匹配（票 10）。
 *
 * 夹具是**真实资产**（`assets/curriculum/syllabus_index.json` 166 门 + 两份单门大纲原样拷贝）。
 * 重点锁三件事：结构解析、名称归一（罗马数字坑）、匹配优先级与「不猜」。
 */
class SyllabusParseTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("curriculum/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun index() = SyllabusParse.index(res("syllabus_index.json"))
    private fun wudong() = SyllabusParse.syllabus(res("syllabus_901040302.json"))

    // ── 索引 ──────────────────────────────────────────────

    @Test
    fun `索引解析出 166 门与三种类型`() {
        val idx = index()
        assertEquals(166, idx.size)
        val e = idx.first { it.code == "901040302" }
        assertEquals("不动产估价", e.name)
        assertEquals("理论课", e.type)
        assertEquals("3", e.credit)
        assertEquals("48", e.theoryHours)
        assertEquals("0", e.labHours)
        assertEquals("3 学分 · 理论 48 / 实验 0 学时", e.creditHoursLabel)
        // 三种类型都在（筛选 chip 靠它）
        assertEquals(setOf("理论课", "实验课", "实践课"), SyllabusParse.types(idx).toSet())
        assertEquals(3, SyllabusParse.types(idx).size)
    }

    @Test
    fun `坏索引不抛异常`() {
        assertTrue(SyllabusParse.index("not json").isEmpty())
        assertTrue(SyllabusParse.index("{}").isEmpty())
    }

    // ── 单门大纲 ──────────────────────────────────────────

    @Test
    fun `大纲结构逐段解析`() {
        val s = wudong()
        assertEquals("901040302", s.code)
        assertEquals("不动产估价", s.name)
        assertEquals("理论课", s.type)
        assertEquals("3", s.credit)
        assertEquals("专业课", s.category)
        assertEquals("土地资源管理教研室", s.department)
        assertEquals("董秀茹", s.writer)

        // 章节按原文顺序，标题原样（实测「三、教学目的」而不是「教学目标」，不能写死）
        assertEquals(9, s.sections.size)
        assertEquals("一、课程基本信息", s.sections.first().title)
        assertTrue(s.sections.first().text.contains("901040302"))
        assertEquals("九、教学大纲修订说明", s.sections.last().title)

        // 教学章节 / 学时一览表 / 课程目标
        assertEquals(7, s.chapters.size)
        assertEquals("第一章 土地估价概述", s.chapters.first().title)
        assertEquals("6.0 学时，支撑课程目标1", s.chapters.first().note)
        assertEquals(8, s.hoursTable.size)
        assertEquals("第一章 土地估价概述", s.hoursTable.first().content)
        assertEquals("6", s.hoursTable.first().total)
        assertEquals(3, s.goals.size)
        assertEquals("1", s.goals.first().no)
        assertTrue(s.goals.first().content.contains("不动产估价基础"))

        // 基本信息（教材/参考书）
        assertTrue(s.basicInfo.any { it.first == "选用教材" })
        assertNotNull(s.basicInfo.firstOrNull { it.first == "主要参考书" })
    }

    @Test
    fun `学时合计只在原表没有空行时才给`() {
        // 实测这份表最后一行是「课程思政内容」，学时三列全空 → 不能当 0 相加
        assertNull("有空白学时行时必须不给合计", wudong().hoursTotal)

        val complete = SyllabusParse.syllabus(
            """{"课程名称":"某课","教学内容学时一览表":[
                {"教学内容":"甲","学时合计":"6"},{"教学内容":"乙","学时合计":"8"}]}""",
        )
        assertEquals("14", complete.hoursTotal)
    }

    @Test
    fun `空章节保留标题而不是删掉`() {
        // 实测「八、本课程的先修课程」正文只有几个字；保留小节 + 空正文，
        // 界面才会说「原文这一节为空」，而不是假装这一节不存在
        val s = wudong()
        val empty = s.sections.firstOrNull { it.title.contains("先修课程") }
        assertNotNull("空正文的小节也要保留", empty)
        assertTrue(empty!!.text.length < 20)
        assertTrue(s.sections.all { it.title.isNotBlank() })
    }

    @Test
    fun `实践课大纲也能解析`() {
        val s = SyllabusParse.syllabus(res("syllabus_911040303.json"))
        assertEquals("不动产估价教学实习", s.name)
        assertEquals("实践课", s.type)
        assertEquals("1", s.credit)
        assertTrue("实践课也该有章节", s.sections.isNotEmpty())
    }

    // ── 课程代码 / 名称归一（真实坑）───────────────────────

    @Test
    fun `课表课程号带课序号要拆掉`() {
        assertEquals("901040302", SyllabusParse.codeOf("901040302_01"))
        assertEquals("901040302", SyllabusParse.codeOf(" 901040302 "))
        assertEquals("901040302", SyllabusParse.codeOf("901040302"))
        assertEquals("901040302A", SyllabusParse.codeOf("901040302A"))
        assertEquals("", SyllabusParse.codeOf(""))
        assertEquals("", SyllabusParse.codeOf("  "))
    }

    @Test
    fun `罗马数字写法差异能归一`() {
        // 真实场景：课表写「形势与政策Ⅴ」（U+2164），册子里可能写 ASCII V
        assertEquals(
            SyllabusParse.normalizeCourseName("形势与政策V"),
            SyllabusParse.normalizeCourseName("形势与政策Ⅴ"),
        )
        assertEquals(
            SyllabusParse.normalizeCourseName("创业基础II"),
            SyllabusParse.normalizeCourseName("创业基础Ⅱ"),
        )
        // 小写罗马数字也认
        assertEquals(
            SyllabusParse.normalizeCourseName("高等数学IV"),
            SyllabusParse.normalizeCourseName("高等数学ⅳ"),
        )
        // 全角字母数字、空格、中点、括号都不影响比对
        assertEquals(
            SyllabusParse.normalizeCourseName("GIS空间数据分析与应用"),
            SyllabusParse.normalizeCourseName("ＧＩＳ 空间数据分析与应用"),
        )
        assertEquals(
            SyllabusParse.normalizeCourseName("不动产估价"),
            SyllabusParse.normalizeCourseName("不动产·估价"),
        )
        assertEquals("", SyllabusParse.normalizeCourseName("   "))
        // 归一不改原意：不同课不能撞在一起
        assertFalse(
            SyllabusParse.normalizeCourseName("大学外语Ⅰ") ==
                SyllabusParse.normalizeCourseName("大学外语Ⅱ"),
        )
    }

    // ── 匹配优先级 ────────────────────────────────────────

    @Test
    fun `课程代码精确匹配优先`() {
        val m = SyllabusParse.match(index(), "901040302_01", "随便一个不相干的名字")
        assertTrue(m.found)
        assertEquals("901040302", m.entry!!.code)
        assertEquals("code", m.by)
    }

    @Test
    fun `代码不中再比名称且罗马数字能命中`() {
        // 索引里「不动产估价」是理论课；用名字匹配（代码给空）
        val m = SyllabusParse.match(index(), "", "不动产估价")
        assertTrue(m.found)
        assertEquals("901040302", m.entry!!.code)
        assertEquals("name", m.by)

        // 名字里带不同写法的罗马数字：索引里若有 Ⅴ/V 差异也要对上
        val idx = listOf(
            cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry(
                id = "X1", code = "X1", name = "形势与政策Ⅴ", type = "理论课",
            ),
        )
        val m2 = SyllabusParse.match(idx, "", "形势与政策V")
        assertTrue("Ⅴ 与 V 必须能互相命中", m2.found)
        assertEquals("X1", m2.entry!!.id)
    }

    @Test
    fun `名称包含作为最后手段`() {
        val idx = listOf(
            cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry(
                id = "X2", code = "X2", name = "土地资源调查与评价", type = "理论课",
            ),
        )
        val m = SyllabusParse.match(idx, "", "土地资源调查")
        assertTrue(m.found)
        assertEquals("fuzzy", m.by)
    }

    @Test
    fun `通识课不在册子里要如实返回没有`() {
        val m = SyllabusParse.match(index(), "", "社会主义发展史")
        assertFalse("这本册子里本来就没有通识课", m.found)
        assertNull(m.entry)
        assertTrue(m.candidates.isEmpty())
        assertEquals("none", m.by)

        val m2 = SyllabusParse.match(index(), "902010101", "形势与政策Ⅴ")
        assertFalse(m2.found)
    }

    @Test
    fun `命中多条只给候选不猜`() {
        val idx = listOf(
            cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry(
                id = "A", code = "A1", name = "不动产估价", type = "理论课",
            ),
            cn.dkarbon.enroute.data.curriculum.SyllabusIndexEntry(
                id = "B", code = "B1", name = "不动产估价", type = "实验课",
            ),
        )
        val m = SyllabusParse.match(idx, "", "不动产估价")
        assertFalse("多条时不许自己选一门", m.found)
        assertEquals(2, m.candidates.size)
        assertEquals("name", m.by)
    }

    // ── 搜索过滤 ──────────────────────────────────────────

    @Test
    fun `搜索按名称或代码包含过滤并按类型筛`() {
        val idx = index()
        val byName = SyllabusParse.filter(idx, "不动产", "")
        assertTrue(byName.size >= 2)
        assertTrue(byName.all { it.name.contains("不动产") })

        val byCode = SyllabusParse.filter(idx, "901040302", "")
        assertEquals(1, byCode.size)
        assertEquals("不动产估价", byCode.first().name)

        val lab = SyllabusParse.filter(idx, "不动产", "实践课")
        assertTrue(lab.isNotEmpty())
        assertTrue(lab.all { it.type == "实践课" })

        // 空查询 + 空类型 = 全量
        assertEquals(idx.size, SyllabusParse.filter(idx, "", "").size)
        // 查不到就是空（界面据此说「册子里没有」）
        assertTrue(SyllabusParse.filter(idx, "社会主义发展史", "").isEmpty())
    }
}
