package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.curriculum.CurriculumMeta
import cn.dkarbon.enroute.data.curriculum.CurriculumParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 培养方案的解析与选择逻辑（票 09）。
 *
 * 夹具是**真实资产**（`assets/curriculum/` 原样拷贝，只是把中文文件名换成 ASCII 以免 CI 平台差异）：
 * - `plan_index.json`：101 个专业条目的索引
 * - `plan_2024_tudi.json` = `plan_2024_土地资源管理.json`
 * - `plan_2025_tudi.json` = `plan_2025_土地资源管理.json`
 * - `plan_2024_dzxx.json` = `plan_2024_电子信息工程.json`（**学分结构表里没有「总学分」行**，实测样本）
 */
class CurriculumParseTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("curriculum/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun index() = CurriculumParse.index(res("plan_index.json"))
    private fun plan2024() = CurriculumParse.plan(res("plan_2024_tudi.json"))

    // ── 索引 ──────────────────────────────────────────────

    @Test
    fun `索引解析出两个年份与全部专业`() {
        val idx = index()
        assertEquals(listOf("2024", "2025"), idx.years)
        assertEquals(55, idx.entries("2024").size)
        assertEquals(46, idx.entries("2025").size)
        val tudi = idx.entries("2024").first { it.id == "土地资源管理" }
        assertEquals("土地资源管理", tudi.major)
        assertEquals("普通班", tudi.type)
        assertEquals(93, tudi.courses)
        assertTrue(tudi.bytes > 10_000)
    }

    @Test
    fun `坏 JSON 不会抛异常`() {
        assertTrue(CurriculumParse.index("not json").isEmpty)
        assertTrue(CurriculumParse.index("{}").isEmpty)
        assertTrue(CurriculumParse.index("").isEmpty)
    }

    // ── 按年级 / 专业选文件 ────────────────────────────────

    @Test
    fun `按年级挑版本`() {
        val years = listOf("2024", "2025")
        assertEquals("2024", CurriculumParse.pickYear("2024级", years))
        assertEquals("2025", CurriculumParse.pickYear("2025级", years))
        // 年级缺/是没收录的年份（如 2023、2026）→ 回落到最新一年，并在界面标明年份
        assertEquals("2025", CurriculumParse.pickYear("2023级", years))
        assertEquals("2025", CurriculumParse.pickYear("", years))
        assertEquals("", CurriculumParse.pickYear("2024级", emptyList()))
    }

    @Test
    fun `按专业定位到本人那份`() {
        val idx = index()
        val e = CurriculumParse.pickPlan(idx, "2024", "土地资源管理")
        assertNotNull(e)
        assertEquals("土地资源管理", e!!.id)
        // 学籍写「土地资源管理专业」也能定位（去掉「专业」再比）
        assertEquals("土地资源管理", CurriculumParse.pickPlan(idx, "2024", "土地资源管理专业")?.id)
        // 定位不到 → null（界面让人手动选，不猜）
        assertNull(CurriculumParse.pickPlan(idx, "2024", "根本不存在的专业"))
        assertNull(CurriculumParse.pickPlan(idx, "2024", ""))
        assertNull(CurriculumParse.pickPlan(idx, "1999", "土地资源管理"))
    }

    @Test
    fun `资产文件名与打包约定一致`() {
        assertEquals("plan_2024_土地资源管理.json", CurriculumParse.fileName("2024", "土地资源管理"))
        assertEquals("plan_2025_农学_3148ff.json", CurriculumParse.fileName("2025", "农学_3148ff"))
    }

    // ── 单专业方案 ────────────────────────────────────────

    @Test
    fun `PDF 断行落在词中间时接回去`() {
        // 真实形态：原文「地理信息科学、不动产\n估价」
        assertEquals("不动产估价", CurriculumParse.tidy("不动产\n估价"))
        // 句子之间的换行要保留（毕业要求一条一行）
        assertEquals("甲。\n乙", CurriculumParse.tidy("甲。\n乙"))
        assertEquals("甲；\n乙", CurriculumParse.tidy("甲； \n 乙"))
        assertEquals("单行", CurriculumParse.tidy("单行"))
        assertEquals("", CurriculumParse.tidy("   "))
        assertEquals("a\nb", CurriculumParse.tidy("a\n\n\nb"))
    }

    @Test
    fun `方案解析出正文各节与课程行`() {
        val p = plan2024()
        assertEquals("2024", p.year)
        assertEquals("土地资源管理", p.major)
        assertEquals("普通班", p.type)
        assertTrue("专业介绍没了", p.intro.length > 50)
        assertTrue("培养目标没了", p.goals.isNotBlank())
        assertTrue("毕业要求没了", p.requirements.isNotBlank())
        assertEquals("土地资源管理", p.mainDiscipline)
        assertTrue("专业核心课程没了", p.coreCourses.contains("不动产估价"))
        assertTrue(p.duration.contains("4 年"))
        assertEquals("管理学学士", p.degree)
        assertEquals(93, p.courses.size)
        assertEquals(listOf(223, 234), p.pageRange)
        assertEquals(12, p.pageCount)
        // 培养目标 / 毕业要求是「编号 + 内容（+ 指标点）」的结构化条目
        assertEquals(5, p.goalList.size)
        assertTrue(p.goalList.first().content.contains("管理学"))
        assertEquals(9, p.requirementList.size)
        val req1 = p.requirementList.first()
        assertEquals("1", req1.no)
        assertTrue("标题和内容要接起来：${req1.text}", req1.text.startsWith("综合素养"))
        assertTrue("指标点没解析出来", req1.indicators.size >= 3)
        assertEquals("1.1", req1.indicators.first().no)

        val first = p.courses.first()
        assertEquals("902011101", first.code)
        assertEquals("大学外语Ⅰ", first.name)
        assertEquals("3", first.credit)
        assertEquals("48", first.hours)
        assertEquals("一", first.term)
        assertEquals("公共必修课", first.nature)
    }

    @Test
    fun `课程性质原文为空时保持空串不补猜`() {
        // 人造一条：性质缺失，绝不能拿「课程类别」冒充
        val raw = """{"专业名称":"某专业","课程设置与教学进程":[
            {"课程代码":"X1","课程名称":"某课","课程类别":"专业课","学分":"2","总学时":"32","开课学期":"一"}
        ],"学分结构表":[]}"""
        val p = CurriculumParse.plan(raw)
        assertEquals(1, p.courses.size)
        assertEquals("", p.courses.first().nature)
        assertEquals("专业课", p.courses.first().category)
    }

    @Test
    fun `坏方案不会抛异常`() {
        assertTrue(CurriculumParse.plan("not json").isEmpty)
        assertTrue(CurriculumParse.plan("{}").isEmpty)
        assertTrue(CurriculumParse.plan("").isEmpty)
    }

    // ── 学分结构表（核心诚实规则）─────────────────────────

    @Test
    fun `最低毕业学分只读学分结构表`() {
        val p = plan2024()
        assertEquals("165", CurriculumParse.minCredits(p))
        // 表是原样行列：总学分那行长这样
        val row = p.creditTable.first { it.firstOrNull() == "总学分" }
        assertEquals("165", row[3])
        assertEquals(14, p.creditTable.size)
    }

    @Test
    fun `绝不拿课程行求和当应修学分（反向断言）`() {
        val p = plan2024()
        // 课程行学分求和：把全部候选课都算上
        val sum = p.courses.mapNotNull { it.credit.toDoubleOrNull() }.sum()
        assertEquals(184.5, sum, 0.001)
        // 总学分来自表格是 165 —— 两者必须不同；若哪天有人把求和塞进 minCredits，这条会红
        assertEquals("165", CurriculumParse.minCredits(p))
        assertTrue(
            "课程行求和($sum)应高于原表总学分(165)，这正是不能求和的原因",
            sum > CurriculumParse.minCredits(p).toDouble(),
        )
    }

    @Test
    fun `学分结构表缺行的方案说暂缺而不是推算`() {
        // 实测：电子信息工程 2024 的学分结构表只抽到 6 行，没有「总学分」行
        val p = CurriculumParse.plan(res("plan_2024_dzxx.json"))
        assertEquals("电子信息工程", p.major)
        assertFalse("这份样本本来就没有总学分行", p.creditTable.isEmpty())
        assertEquals("找不到总学分行时必须返回空串", "", CurriculumParse.minCredits(p))
        assertFalse(CurriculumParse.creditTableMissing(p))
    }

    @Test
    fun `整张学分结构表缺失也能说清楚`() {
        val raw = """{"专业名称":"X","课程设置与教学进程":[{"课程代码":"A","课程名称":"B","学分":"3"}],
            "学分结构表":[]}"""
        val p = CurriculumParse.plan(raw)
        assertTrue(CurriculumParse.creditTableMissing(p))
        assertEquals("", CurriculumParse.minCredits(p))
    }

    // ── 课程按学期分组 ────────────────────────────────────

    @Test
    fun `课程按开课学期分组且按一至八排序`() {
        val groups = CurriculumParse.coursesByTerm(plan2024())
        assertEquals(
            listOf("一", "二", "三", "四", "五", "六", "七", "八"),
            groups.map { it.first },
        )
        assertEquals(13, groups.first { it.first == "一" }.second.size)
        assertEquals(4, groups.first { it.first == "八" }.second.size)
        // 分组不丢课程
        assertEquals(plan2024().courses.size, groups.sumOf { it.second.size })
        assertEquals("第 一 学期", CurriculumParse.termLabel("一"))
        assertEquals(CurriculumParse.TERM_UNKNOWN, CurriculumParse.termLabel("十"))
    }

    @Test
    fun `没有开课学期的行归到未标注学期并排在最后`() {
        val raw = """{"专业名称":"X","课程设置与教学进程":[
            {"课程代码":"A","课程名称":"甲","学分":"1","开课学期":""},
            {"课程代码":"B","课程名称":"乙","学分":"1","开课学期":"二"}],"学分结构表":[]}"""
        val groups = CurriculumParse.coursesByTerm(CurriculumParse.plan(raw))
        assertEquals(listOf("二", CurriculumParse.TERM_UNKNOWN), groups.map { it.first })
    }

    // ── 来源标注 ──────────────────────────────────────────

    @Test
    fun `底部来源写明版本与页数`() {
        val line = CurriculumMeta.sourceLine("2024", plan2024())
        assertTrue(line, line.contains("2024 级本科人才培养方案"))
        assertTrue(line, line.contains("共 695 页"))
        assertTrue(line, line.contains("第 223–234 页"))
        assertTrue(line, line.contains("93 门课程"))
        assertTrue(line, line.contains(CurriculumMeta.GENERATED))
        assertEquals(693, CurriculumMeta.totalPages("2025"))
        // 年份未知时不能瞎编页数
        assertFalse(CurriculumMeta.sourceLine("1999").contains("共"))
    }
}
