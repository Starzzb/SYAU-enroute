package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.AcademicApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试解析的回归测试（2026-11-11）。
 *
 * 样本 `urp_exams.json` 是**真实响应**：从浏览器里抓到的那 4 场考试。
 *
 * ## 这个 bug 的教训
 * 接口 `/examPlan/detail` 本身没问题，但**必须先 GET 一次 `/examPlan/index` 建会话**，
 * 否则一律返回 `{"status":"203","data":""}` —— 看起来像「本学期没有考试」。
 * 解析器没错，错在调用顺序。解析测试锁住的是后半段（schema），
 * 调用顺序那半段只能靠代码注释 + 真机验证兜。
 */
class ExamParseTest {

    private fun raw(): String =
        javaClass.getResourceAsStream("/feed/urp_exams.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val exams by lazy { AcademicApi.parseExamsJson(raw()) }

    @Test
    fun `四场考试都能解析出来`() {
        assertEquals(4, exams.size)
    }

    @Test
    fun `课程名从 title 第一行取`() {
        val names = exams.map { it.name }
        assertTrue(names.contains("形势与政策Ⅴ"))
        assertTrue(names.contains("不动产估价"))
        assertTrue(names.contains("国土空间规划"))
        assertTrue(names.contains("地球表层系统概论"))
    }

    @Test
    fun `时间与地点拆得对`() {
        val e = exams.first { it.name.contains("不动产估价") }
        assertTrue("时间没拼对：${e.time}", e.time.contains("2026-12-15"))
        assertTrue("时间段没拼对：${e.time}", e.time.contains("08:00-09:40"))
        assertTrue("地点没拼对：${e.place}", e.place.contains("第二教学楼"))
        assertTrue("地点缺教室：${e.place}", e.place.contains("201"))
    }

    @Test
    fun `按开考时间升序排列`() {
        val starts = exams.map { it.startAt }
        assertEquals(starts.sorted(), starts)
        assertTrue(exams.first().startAt.startsWith("2026-11-15"))
    }

    @Test
    fun `首页没有考试时返回空表而不是崩`() {
        assertTrue(AcademicApi.parseExamsJson("""{"status":"203","msg":"null","data":""}""").isEmpty())
        assertTrue(AcademicApi.parseExamsJson("").isEmpty())
        assertTrue(AcademicApi.parseExamsJson("[]").isEmpty())
    }
}
