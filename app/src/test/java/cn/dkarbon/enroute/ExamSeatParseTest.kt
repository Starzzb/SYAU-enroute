package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.AcademicApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试座位号 / 考试名称的解析回归（2026-11-28）。
 *
 * 样本 `urp_examplan_index.html` 是**真机登录后抓下来的考试页 HTML**（截取时间轴区块，
 * 座位号已替换成 101~104，其余结构、缩进、`</br>` 全按原样）。
 * `urp_exams.json` 是同一时刻 `/examPlan/detail` 的真实响应。
 *
 * ## 这一轮查到的事实
 * detail JSON 里每场考试只有 `title`/`id`/`start`/`end`/`color` 五个键，
 * **座位号与考试名称都不在其中**；它们只出现在 index 页服务端渲染的时间轴里。
 * 所以 App 之前一直显示「暂缺」，不是渲染错了，是根本没取到。
 */
class ExamSeatParseTest {

    private fun html(): String =
        javaClass.getResourceAsStream("/feed/urp_examplan_index.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private fun json(): String =
        javaClass.getResourceAsStream("/feed/urp_exams.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @Test
    fun `四场考试的座位号都能从时间轴抠出来`() {
        val seats = AcademicApi.parseExamSeatsHtml(html())
        assertEquals(4, seats.size)
        val byName = seats.associateBy { it.name }
        assertEquals("101", byName.getValue("形势与政策Ⅴ").seat)
        assertEquals("102", byName.getValue("不动产估价").seat)
        assertEquals("103", byName.getValue("国土空间规划").seat)
        assertEquals("104", byName.getValue("地球表层系统概论").seat)
    }

    @Test
    fun `考试名称当类别取出来`() {
        val seats = AcademicApi.parseExamSeatsHtml(html())
        assertTrue(seats.all { it.type == "2026-2027秋期末考试" })
    }

    @Test
    fun `课程名不带课程号前缀也不带已结束后缀`() {
        val names = AcademicApi.parseExamSeatsHtml(html()).map { it.name }
        assertTrue(names.toString(), names.none { it.contains("已结束") })
        assertTrue(names.toString(), names.none { it.contains("902021205") })
        assertTrue(names.contains("形势与政策Ⅴ"))
    }

    @Test
    fun `日期与开考时刻取对了`() {
        val e = AcademicApi.parseExamSeatsHtml(html()).first { it.name == "不动产估价" }
        assertEquals("2026-12-15", e.date)
        assertEquals("08:00", e.startClock)
    }

    @Test
    fun `合并后座位落到对应课程且教室没被顶掉`() {
        val merged = AcademicApi.mergeExamSeats(
            AcademicApi.parseExamsJson(json()),
            AcademicApi.parseExamSeatsHtml(html()),
        )
        assertEquals(4, merged.size)
        val e = merged.first { it.name == "不动产估价" }
        assertEquals("102", e.seat)
        assertEquals("2026-2027秋期末考试", e.type)
        assertEquals("201", e.room)
        assertEquals("2026-12-15", e.date)
    }

    @Test
    fun `按开考时间排序不受合并影响`() {
        val merged = AcademicApi.mergeExamSeats(
            AcademicApi.parseExamsJson(json()),
            AcademicApi.parseExamSeatsHtml(html()),
        )
        assertEquals(merged.map { it.startAt }.sorted(), merged.map { it.startAt })
    }

    @Test
    fun `时间轴里没有的考试保持空座位 不拿邻座顶上`() {
        val one = AcademicApi.parseExamsJson(json()).filter { it.name == "不动产估价" }
        val merged = AcademicApi.mergeExamSeats(one, emptyList())
        assertEquals("", merged.single().seat)
        assertEquals("", merged.single().type)
    }

    @Test
    fun `空 HTML 与不相干的页面都不炸`() {
        assertTrue(AcademicApi.parseExamSeatsHtml("").isEmpty())
        assertTrue(AcademicApi.parseExamSeatsHtml("<html><body>本学期没有考试</body></html>").isEmpty())
    }
}
