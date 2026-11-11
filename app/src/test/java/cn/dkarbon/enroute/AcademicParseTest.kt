package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.AcademicApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真机抓到的 payload** 锁住考试/成绩的解析。
 *
 * 这两段 JSON 是 2026-11-10 用 Playwright 在校园网、登录态下从
 * `http://10.2.0.47` 实测抓的（见 [AcademicApi] 的 KDoc）。把它们钉在测试里，
 * 以后学校换版本时这里会先红，而不是用户打开 App 看到空白。
 */
class AcademicParseTest {

    // 考试：真机响应（截取 2 条，字段原样）
    private val examPayload = """
    [{"title":"形势与政策Ⅴ\n10:00-11:40\n主校\n线上考场\n线上虚拟考场1\n","id":"0","start":"2026-11-15 10:00","end":"2026-11-15 11:40","color":"#3a87ad"},
     {"title":"不动产估价\n08:00-09:40\n主校\n第二教学楼\n201\n","id":"1","start":"2026-12-15 08:00","end":"2026-12-15 09:40","color":"#3a87ad"}]
    """.trimIndent()

    // 成绩：真机响应（list.records，字段原样）
    private val scorePayload = """
    {"list":{"pageSize":300,"pageNum":1,"pageContext":{"totalCount":55},"records":[
      {"ZXJXJHH":"2024-2025-1-1","ZXJXJHM":"2024-2025学年秋","KCH":"901010216","KXH":"02","KSSJ":"20250101",
       "LRZTDM":"05","KCSXDM":"002","KSLXDM":"","CJLRFSDM":"001","KCCJ":"73","DJCJ":"-75","WTGYYDM":"",
       "KCM":"生态学基础","YWKCM":"Fundamentals of Ecology","XF":2,"XS":32,"KCSXMC":"限修课",
       "KCLXMC":"","DJM":"中","WTGYYSM":"","RN":1},
      {"ZXJXJHH":"2025-2026-2-1","ZXJXJHM":"2025-2026学年春","KCH":"901040337","KXH":"01","KSSJ":"20260701",
       "LRZTDM":"05","KCSXDM":"002","KSLXDM":"","CJLRFSDM":"001","KCCJ":"90","DJCJ":"-95","WTGYYDM":"",
       "KCM":"应用遥感技术","YWKCM":"Application of Remote Sensing Technology","XF":2,"XS":32,"KCSXMC":"限修课",
       "KCLXMC":"","DJM":"优","WTGYYSM":"","RN":2}]}}
    """.trimIndent()

    // ── 考试 ─────────────────────────────────────────────

    @Test
    fun `考试 title 的五段被拆成课程名和时间地点`() {
        val list = AcademicApi.parseExamsJson(examPayload)
        assertEquals(2, list.size)

        val first = list[0]
        assertEquals("形势与政策Ⅴ", first.name)
        assertEquals("2026-11-15 10:00-11:40", first.time)
        assertEquals("主校 线上考场 线上虚拟考场1", first.place)
    }

    @Test
    fun `考试按开始时间排序`() {
        val list = AcademicApi.parseExamsJson(examPayload)
        assertTrue(list[0].startAt < list[1].startAt)
        assertEquals("不动产估价", list[1].name)
    }

    @Test
    fun `考试 payload 是空数组时不崩`() {
        assertEquals(0, AcademicApi.parseExamsJson("[]").size)
        assertEquals(0, AcademicApi.parseExamsJson("not json").size)
    }

    // ── 成绩 ─────────────────────────────────────────────

    @Test
    fun `成绩字段按 URP 原始键解析`() {
        val list = AcademicApi.parseScoresJson(scorePayload)
        assertEquals(2, list.size)

        val first = list[0]
        assertEquals("生态学基础", first.name)
        assertEquals("73", first.score)
        assertEquals("2", first.credit)
        assertEquals("中", first.grade)
        assertEquals("2024-2025学年秋", first.term)
    }

    @Test
    fun `成绩能区分及格与否的等级`() {
        val list = AcademicApi.parseScoresJson(scorePayload)
        assertEquals("中", list[0].grade)
        assertEquals("优", list[1].grade)
        assertEquals("90", list[1].score)
    }

    @Test
    fun `成绩 payload 结构变了就返回空 而不是抛异常`() {
        assertEquals(0, AcademicApi.parseScoresJson("""{"list":{"records":[]}}""").size)
        assertEquals(0, AcademicApi.parseScoresJson("""{"foo":1}""").size)
        assertEquals(0, AcademicApi.parseScoresJson("<html>登录</html>").size)
    }

    @Test
    fun `没有课程名的记录会被丢掉`() {
        val weird = """{"list":{"records":[{"KCCJ":"80"},{"KCM":"有名字","KCCJ":"90"}]}}"""
        val list = AcademicApi.parseScoresJson(weird)
        assertEquals(1, list.size)
        assertEquals("有名字", list[0].name)
    }
}
