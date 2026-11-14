package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.AcademicApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试 / 成绩的**结构化字段**解析测试（2026-11-13 补齐）。
 *
 * 上一版把 exam title 的后三行拼成一个 `place` 字符串，成绩只吃了 5 个键。
 * 这里锁住的是新契约：模板五行逐行落位、成绩字段按 URP 原始键全量保留，
 * 页面上的「主信息 / 次信息 / 原始」三层才有分层依据。
 */
class AcademicFieldsTest {

    private fun examSample(): String =
        javaClass.getResourceAsStream("/feed/urp_exams.json")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private val scorePayload = """
    {"list":{"pageContext":{"totalCount":2},"records":[
      {"ZXJXJHH":"2024-2025-1-1","ZXJXJHM":"2024-2025学年秋","KCH":"901010216","KXH":"02","KSSJ":"20250101",
       "LRZTDM":"05","KCSXDM":"002","KSLXDM":"","CJLRFSDM":"001","KCCJ":"73","DJCJ":"-75","WTGYYDM":"",
       "KCM":"生态学基础","YWKCM":"Fundamentals of Ecology","XF":2,"XS":32,"KCSXMC":"限修课",
       "KCLXMC":"","DJM":"中","WTGYYSM":"","RN":1},
      {"ZXJXJHH":"2025-2026-2-1","ZXJXJHM":"2025-2026学年春","KCH":"901040337","KXH":"01","KSSJ":"20260701",
       "LRZTDM":"05","KCSXDM":"002","KSLXDM":"","CJLRFSDM":"001","KCCJ":"90","DJCJ":"-95","WTGYYDM":"",
       "KCM":"应用遥感技术","YWKCM":"Application of Remote Sensing Technology","XF":2,"XS":32,"KCSXMC":"限修课",
       "KCLXMC":"","DJM":"优","WTGYYSM":"","RN":2}]}}
    """.trimIndent()

    // ── 考试：title 五行落位 ───────────────────────────────

    @Test
    fun `考试地点被拆成 校区 教学楼 教室`() {
        val e = AcademicApi.parseExamsJson(examSample()).first { it.name == "不动产估价" }
        assertEquals("主校", e.campus)
        assertEquals("第二教学楼", e.building)
        assertEquals("201", e.room)
        assertTrue(e.extraLines.isEmpty())
    }

    @Test
    fun `线上考场同样拆得开 不被当成教学楼缺失`() {
        val e = AcademicApi.parseExamsJson(examSample()).first { it.name == "形势与政策Ⅴ" }
        assertEquals("主校", e.campus)
        assertEquals("线上考场", e.building)
        assertEquals("线上虚拟考场1", e.room)
    }

    @Test
    fun `日期 起止时刻 时长从 start end 真算`() {
        val e = AcademicApi.parseExamsJson(examSample()).first { it.name == "不动产估价" }
        assertEquals("2026-12-15", e.date)
        assertEquals("08:00", e.startClock)
        assertEquals("09:40", e.endClock)
        assertEquals(100, e.durationMin)
        assertEquals("2026-12-15 08:00", e.startAt)
    }

    @Test
    fun `接口其他字段不丢 id color 与原始 key 全量`() {
        val e = AcademicApi.parseExamsJson(examSample()).first { it.name == "不动产估价" }
        assertEquals("1", e.apiId)
        assertEquals("#3a87ad", e.colorHex)
        assertEquals(setOf("title", "id", "start", "end", "color"), e.fields.keys)
        // raw 是完整 json，不再是截断 400 字 —— 「看原始」要能看全
        assertTrue(e.raw.contains("\"color\":\"#3a87ad\""))
    }

    @Test
    fun `时间算不出来时时长是 0 而不是猜一个 100`() {
        val weird = """[{"title":"某课\n上午\n主校\n第二教学楼\n201\n","id":"9","start":"","end":""}]"""
        val e = AcademicApi.parseExamsJson(weird).first()
        assertEquals(0, e.durationMin)
        assertEquals("", e.date)
        assertEquals("", e.startClock)
        // 时间没解析出来时，退回 title 里的原始时间段文案
        assertTrue(e.time.contains("上午"))
    }

    // ── 成绩：全量字段 ────────────────────────────────────

    @Test
    fun `成绩补齐英文名 课程属性 课程号 课序号 学时 学期代码 考试日期`() {
        val s = AcademicApi.parseScoresJson(scorePayload).first()
        assertEquals("生态学基础", s.name)
        assertEquals("Fundamentals of Ecology", s.englishName)
        assertEquals("限修课", s.category)
        assertEquals("901010216", s.code)
        assertEquals("02", s.classNo)
        assertEquals("32", s.hours)
        assertEquals("2024-2025-1-1", s.termCode)
        assertEquals("20250101", s.examDate)
        assertEquals("", s.courseType)
    }

    @Test
    fun `成绩原始 key 全量保留 空值也留键名`() {
        val s = AcademicApi.parseScoresJson(scorePayload).first()
        // 实测这一条返回 21 个键，一个都不能丢
        assertEquals(21, s.fields.size)
        assertEquals("", s.fields["KCLXMC"])        // 存在但为空
        assertEquals("-75", s.fields["DJCJ"])
        assertEquals("中", s.fields["DJM"])
        assertTrue(s.raw.startsWith("{"))
    }

    @Test
    fun `数值派生 学分与分数字段缺失时为 null`() {
        val list = AcademicApi.parseScoresJson(scorePayload)
        assertEquals(2.0, list[0].creditValue!!, 0.001)
        assertEquals(73.0, list[0].scoreValue!!, 0.001)

        val text = """{"list":{"records":[{"KCM":"体育","KCCJ":"优秀","DJM":"优","XF":"","ZXJXJHM":"2024-2025学年秋"}]}}"""
        val s = AcademicApi.parseScoresJson(text).first()
        assertNull("等级制课程没有百分制分数", s.scoreValue)
        assertNull("学分缺失不能当 0", s.creditValue)
        assertTrue("等级制不算挂科", !s.failed)
    }

    @Test
    fun `不及格只认分数小于 60 或等级写明不及格`() {
        val text = """{"list":{"records":[
          {"KCM":"挂科","KCCJ":"48","DJM":"","XF":2,"ZXJXJHM":"2024-2025学年秋"},
          {"KCM":"等级挂","KCCJ":"","DJM":"不及格","XF":2,"ZXJXJHM":"2024-2025学年秋"},
          {"KCM":"合格制","KCCJ":"合格","DJM":"","XF":1,"ZXJXJHM":"2024-2025学年秋"}]}}"""
        val list = AcademicApi.parseScoresJson(text)
        assertTrue(list[0].failed)
        assertTrue(list[1].failed)
        assertTrue("合格/合格制这种没有分数证据的不算挂科", !list[2].failed)
    }

    @Test
    fun `诊断文案只拼地址不发请求`() {
        assertTrue(AcademicApi.examPathPreview().contains("/examPlan/detail?start="))
        assertTrue(AcademicApi.scorePathPreview().contains("/allTermScores/data"))
    }
}
