package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.HistorySemesters
import cn.dkarbon.enroute.data.UrpClient
import cn.dkarbon.enroute.ui.timetable.HistoryRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历年课表（票 11）：学期选项解析、请求体、缓存判定、「历史不侵蚀」的派生逻辑。
 *
 * 夹具：
 * - `home/history_schedule_2024_2025_1.json` —— **真实响应**（2024-2025 秋，42KB，
 *   原样拷自 `docs/_probe/urp/calendar_schedule_after_switch.json`），用**现有** `parseSchedule` 解析；
 * - `home/history_semester_select.html` —— 学期下拉。**值是实测的真实 5 个学期**
 *   （`docs/_probe/urp/calendar_probe.txt` 提取到的 opts），HTML 标记按 URP 的
 *   `select[name=planCode]` 常规形态重建（当时只存了提取结果，没存整页）。
 */
class HistoryTimetableTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("home/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun schedule() = UrpClient.parseSchedule(res("history_schedule_2024_2025_1.json"))

    // ── 学期选项（不写死）─────────────────────────────────

    @Test
    fun `学期选项从下拉里解析且认得当前学期`() {
        val opts = HistorySemesters.options(res("history_semester_select.html"))
        assertEquals(5, opts.size)
        assertEquals(
            listOf(
                "2026-2027-1-1", "2025-2026-2-1", "2025-2026-1-1", "2024-2025-2-1", "2024-2025-1-1",
            ),
            opts.map { it.code },
        )
        // 服务器标了「(当前)」，且是 selected
        assertEquals(1, opts.count { it.current })
        assertEquals("2026-2027-1-1", opts.first { it.current }.code)
        // 别的 select 里的 option 不能混进来
        assertTrue(opts.none { it.code == "x" })
    }

    @Test
    fun `没有 planCode 下拉时返回空而不是瞎猜`() {
        assertTrue(HistorySemesters.options("").isEmpty())
        assertTrue(HistorySemesters.options("<html><body>登录页</body></html>").isEmpty())
        assertTrue(HistorySemesters.options("""<select name="other"><option value="1">甲</option></select>""").isEmpty())
    }

    // ── 请求体（开头那个 &）──────────────────────────────

    @Test
    fun `切学期表单必须以与号开头`() {
        assertEquals("&planCode=2024-2025-1-1", HistorySemesters.scheduleBody("2024-2025-1-1"))
        assertTrue(HistorySemesters.scheduleBody("2024-2025-1-1").startsWith("&"))
        // 前后空白不能带进表单
        assertEquals("&planCode=2024-2025-1-1", HistorySemesters.scheduleBody(" 2024-2025-1-1 "))
    }

    // ── 解析复用现有解析器（不另写）──────────────────────

    @Test
    fun `真实样本用现有解析器就能解析`() {
        val tt = schedule()
        assertTrue("这门课都没解析出来，说明 schema 变了", tt.courses.isNotEmpty())
        assertEquals("2024-2025-1", tt.term)
        // 样本里第一门课，逐字段核对解析口径（与「本学期课表」同一个解析器）
        val c = tt.courses.first()
        assertTrue(c.name.isNotBlank())
        assertTrue(c.sections.isNotEmpty())
        assertTrue(c.weeks.isNotEmpty())
        assertTrue(c.weekday in 1..7)
    }

    // ── 周范围与网格映射 ─────────────────────────────────

    @Test
    fun `周范围从课程周次推出来`() {
        val courses = schedule().courses
        val range = HistoryRules.weekRange(courses)
        assertEquals(courses.flatMap { it.weeks }.min(), range.first)
        assertEquals(courses.flatMap { it.weeks }.max(), range.last)
        assertTrue(range.first >= 1)
        // 一门课都没有时给 1..16 兜底（给 0..0 网格会塌成一行）
        assertEquals(1..16, HistoryRules.weekRange(emptyList()))
    }

    @Test
    fun `某周有哪几门课能算出来且不越界`() {
        val tt = schedule()
        val range = HistoryRules.weekRange(tt.courses)
        val cells = HistoryRules.cellsOf(tt.courses, range.first)
        assertTrue("第 ${range.first} 周应该排了课", cells.isNotEmpty())
        // 键必须是 (星期 1..7, 大节 1..6)
        assertTrue(cells.keys.all { it.first in 1..7 && it.second in 1..6 })
        // 不存在的周 → 空（界面据此显示空网格，而不是报错）
        assertTrue(HistoryRules.cellsOf(tt.courses, range.last + 50).isEmpty())
    }

    // ── 历史学期「不侵蚀」────────────────────────────────

    @Test
    fun `历史学期永远不是本周所以不侵蚀`() {
        assertEquals(false, HistoryRules.isThisWeek())
        // 侵蚀进度只在本周才可能非 0
        assertEquals(0f, HistoryRules.progress(HistoryRules.isThisWeek(), 0.8f), 0.0001f)
        assertEquals(0.8f, HistoryRules.progress(true, 0.8f), 0.0001f)
    }

    // ── 缓存：命名 / 命中 / 时间标记 ──────────────────────

    @Test
    fun `缓存按学期一个文件`() {
        assertEquals("2024-2025-1-1.json", HistorySemesters.cacheFileName("2024-2025-1-1"))
        assertEquals("2026-2027-2-1.json", HistorySemesters.cacheFileName(" 2026-2027-2-1 "))
        // 异常字符不会跑出目录
        assertEquals("2024_2025_x.json", HistorySemesters.cacheFileName("2024/2025 x"))
    }

    @Test
    fun `历史学期缓存永久有效但要有内容`() {
        assertTrue(HistorySemesters.isCacheHit("2024-2025-1-1", "{}"))
        assertFalse("没缓存就不算命中", HistorySemesters.isCacheHit("2024-2025-1-1", null))
        assertFalse(HistorySemesters.isCacheHit("2024-2025-1-1", ""))
        assertFalse(HistorySemesters.isCacheHit("", "{}"))
    }

    @Test
    fun `缓存时间标记只写月日且不编日期`() {
        assertEquals("本地缓存 · 抓于 11-14", HistorySemesters.stamp("2026-11-14T10:30:00+08:00"))
        assertEquals("本地缓存 · 抓取时间未知", HistorySemesters.stamp("昨天"))
        assertEquals("本地缓存 · 抓取时间未知", HistorySemesters.stamp(""))
    }

    @Test
    fun `样本对应的学期号能拼成人话`() {
        assertEquals("2024-2025 秋", cn.dkarbon.enroute.ui.home.HomeModel.termLabel("2024-2025-1-1"))
        assertEquals("2025-2026 春", cn.dkarbon.enroute.ui.home.HomeModel.termLabel("2025-2026-2-1"))
        assertNotNull(schedule().courses.firstOrNull())
    }
}
