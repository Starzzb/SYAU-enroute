package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.ui.academic.ExamBucket
import cn.dkarbon.enroute.ui.academic.ExamRow
import cn.dkarbon.enroute.ui.academic.ExamRows
import cn.dkarbon.enroute.ui.academic.ScoreRow
import cn.dkarbon.enroute.ui.academic.ScoreRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 考试时间轴 / 成绩统计的纯函数测试（2026-11-13）。
 *
 * 全部用**固定日期**打边界，不读系统时钟：上下周分界、跨天、已考完、字段缺失
 * 这几处一旦用 `LocalDate.now()` 就变成「今天跑绿、周五跑红」的测试。
 */
class AcademicRowsTest {

    // 2026-11-13 是周五；本周 = 11-09(周一) ~ 11-15(周日)
    private val today = LocalDate.of(2026, 11, 13)
    private val now = LocalDateTime.of(2026, 11, 13, 9, 0)

    private fun exam(
        name: String,
        date: String,
        start: String = "08:00",
        end: String = "09:40",
        campus: String = "主校",
        building: String = "第二教学楼",
        room: String = "201",
    ) = ExamItem(
        name = name,
        time = "$date $start-$end",
        place = "$campus $building $room",
        startAt = "$date $start",
        date = date,
        startClock = start,
        endClock = end,
        durationMin = 100,
        campus = campus,
        building = building,
        room = room,
    )

    // ── 时间轴分桶 ────────────────────────────────────────

    @Test
    fun `今天 本周 更远 已结束 四桶按日历周切`() {
        assertEquals(ExamBucket.TODAY, ExamRows.bucket(exam("今天考", "2026-11-13"), today))
        // 周日仍属本周（周一为首日），这是与「未来 7 天」写法的关键差别
        assertEquals(ExamBucket.THIS_WEEK, ExamRows.bucket(exam("周日考", "2026-11-15"), today))
        // 下周一就不是本周了
        assertEquals(ExamBucket.LATER, ExamRows.bucket(exam("下周一考", "2026-11-16"), today))
        assertEquals(ExamBucket.PAST, ExamRows.bucket(exam("昨天考", "2026-11-12"), today))
    }

    @Test
    fun `日期解析不出来的考试归到更远 而不是假装已结束`() {
        val broken = ExamItem(name = "脏数据", startAt = "", date = "", startClock = "", endClock = "")
        assertEquals(ExamBucket.LATER, ExamRows.bucket(broken, today))
        assertNull(ExamRows.dateOf(broken))
        assertNull(ExamRows.daysAhead(broken, today))
    }

    @Test
    fun `距今天几天按自然日算`() {
        assertEquals(0L, ExamRows.daysAhead(exam("a", "2026-11-13"), today))
        assertEquals(2L, ExamRows.daysAhead(exam("b", "2026-11-15"), today))
        assertEquals(32L, ExamRows.daysAhead(exam("c", "2026-12-15"), today))
        assertEquals(-1L, ExamRows.daysAhead(exam("d", "2026-11-12"), today))
    }

    // ── 倒计时文案 ────────────────────────────────────────

    @Test
    fun `倒计时文案 今天 明天 N天后 过去不显示`() {
        assertEquals("今天", ExamRows.countdownLabel(0))
        assertEquals("明天", ExamRows.countdownLabel(1))
        assertEquals("5 天后", ExamRows.countdownLabel(5))
        assertNull(ExamRows.countdownLabel(-1))
        assertNull(ExamRows.countdownLabel(null))
    }

    @Test
    fun `未来最近一场跳过今天已考完的 并给出倒计时`() {
        val finished = exam("上午已考完", "2026-11-13", "08:00", "08:40")   // now=09:00 已结束
        val laterToday = exam("下午场", "2026-11-13", "14:00", "15:40")
        val nextWeek = exam("下周", "2026-11-18", "08:00")
        val list = listOf(finished, laterToday, nextWeek)

        val next = ExamRows.nextUpcoming(list, today, now)
        assertNotNull(next)
        assertEquals("下午场", next!!.name)

        val stats = ExamRows.stats(list, today, now)
        assertEquals(0L, stats.nextDays)
        assertEquals("3 场 · 下一场 11-13 · 今天", ExamRows.toolbarLabel(stats))
    }

    @Test
    fun `全部考完时工具条说已全部考完 不给假倒计时`() {
        val list = listOf(exam("老考试", "2026-11-01"), exam("更老的", "2026-10-01"))
        val stats = ExamRows.stats(list, today, now)
        assertNull(stats.next)
        assertEquals("2 场 · 已全部考完", ExamRows.toolbarLabel(stats))
    }

    // ── 行管线 ────────────────────────────────────────────

    @Test
    fun `行是一条时间流 按开考时间正序 不产组头`() {
        val list = listOf(
            exam("下月", "2026-12-15"),
            exam("昨天", "2026-11-12"),
            exam("上周", "2026-11-05"),
            exam("今天", "2026-11-13", "14:00", "15:40"),
            exam("周日", "2026-11-15"),
        )
        val rows = ExamRows.build(list, today, now, collapsed = emptySet())

        // m35：桶头取消，轴从过去一路走到未来
        assertTrue(rows.filterIsInstance<ExamRow.Header>().isEmpty())
        val names = rows.filterIsInstance<ExamRow.Card>().map { it.exam.name }
        assertEquals(listOf("上周", "昨天", "今天", "周日", "下月"), names)
    }

    @Test
    fun `已结束的不再折叠 也不被关进单独的段 仍留在轴上`() {
        val list = listOf(exam("今天", "2026-11-13", "14:00", "15:40"), exam("上周", "2026-11-05"))
        // 就算外面还按老习惯传了个 collapsed 集合，也不该再吃掉任何一场
        val rows = ExamRows.build(list, today, now, collapsed = setOf(ExamBucket.PAST))

        assertTrue(rows.filterIsInstance<ExamRow.Header>().isEmpty())
        val cards = rows.filterIsInstance<ExamRow.Card>().map { it.exam.name }
        assertEquals(listOf("上周", "今天"), cards)
    }

    @Test
    fun `下一场那张卡带 isNext 标记 其余不带`() {
        val list = listOf(exam("今天下午", "2026-11-13", "14:00", "15:40"), exam("下月", "2026-12-15"))
        val cards = ExamRows.build(list, today, now, emptySet()).filterIsInstance<ExamRow.Card>()
        assertEquals(1, cards.count { it.isNext })
        assertEquals("今天下午", cards.first { it.isNext }.exam.name)
    }

    @Test
    fun `行 key 唯一 且 contentType 区分下一场`() {
        val list = listOf(exam("今天下午", "2026-11-13", "14:00", "15:40"), exam("下月", "2026-12-15"))
        val rows = ExamRows.build(list, today, now, emptySet())
        assertEquals(rows.size, rows.map { it.rowKey }.distinct().size)
        assertEquals("exam-card-next", rows.first { it is ExamRow.Card && it.isNext }.contentType)
        assertTrue(rows.filterIsInstance<ExamRow.Header>().all { it.contentType == "exam-header" })
    }

    @Test
    fun `空列表不产出行`() {
        assertTrue(ExamRows.build(emptyList(), today, now, emptySet()).isEmpty())
        assertEquals("0 场", ExamRows.toolbarLabel(ExamRows.stats(emptyList(), today, now)))
    }

    @Test
    fun `考场与时长文案缺字段就留空`() {
        val full = exam("有地点", "2026-11-13")
        assertEquals("主校 · 第二教学楼 · 201", ExamRows.placeLabel(full))
        assertEquals("100 分钟", ExamRows.durationLabel(full))
        assertEquals("08:00-09:40", ExamRows.timeRangeLabel(full))
        assertEquals("11-13", ExamRows.dayLabel(full))
        assertEquals("周五", ExamRows.weekdayLabel(full))

        val bare = ExamItem(name = "没地点", date = "2026-11-13", startClock = "08:00", endClock = "")
        assertEquals("", ExamRows.placeLabel(bare))
        assertEquals("", ExamRows.durationLabel(bare))
        assertEquals("08:00", ExamRows.timeRangeLabel(bare))
    }

    // ── 成绩：学期分组 ────────────────────────────────────

    private fun score(
        name: String,
        term: String,
        s: String,
        xf: String,
        grade: String = "",
    ) = ScoreItem(
        name = name,
        term = term,
        score = s,
        credit = xf,
        grade = grade,
    )

    @Test
    fun `学期倒序 春季在秋季之上`() {
        val terms = listOf("2024-2025学年秋", "2025-2026学年春", "2024-2025学年春")
        assertEquals(
            listOf("2025-2026学年春", "2024-2025学年春", "2024-2025学年秋"),
            ScoreRows.sortTerms(terms),
        )
    }

    @Test
    fun `解析不出学年的学期排最后 不丢`() {
        val sorted = ScoreRows.sortTerms(listOf("2024-2025学年秋", "不分学期"))
        assertEquals("2024-2025学年秋", sorted.first())
        assertEquals(listOf("不分学期"), sorted.drop(1))
    }

    @Test
    fun `成绩按学期分组 组头计真实门数`() {
        val list = listOf(
            score("生态学基础", "2024-2025学年秋", "73", "2", "中"),
            score("土壤学", "2024-2025学年秋", "88", "3", "良"),
            score("遥感", "2025-2026学年春", "90", "2", "优"),
        )
        val rows = ScoreRows.build(list, onlyFailed = false, collapsed = emptySet())
        val headers = rows.filterIsInstance<ScoreRow.Header>()
        assertEquals(listOf("2025-2026学年春", "2024-2025学年秋"), headers.map { it.term })
        assertEquals(1, headers[0].stats.count)
        assertEquals(2, headers[1].stats.count)
        assertEquals(3, rows.filterIsInstance<ScoreRow.Item>().size)
    }

    @Test
    fun `学期折叠后同一学期只剩组头`() {
        val list = listOf(
            score("生态学基础", "2024-2025学年秋", "73", "2"),
            score("遥感", "2025-2026学年春", "90", "2"),
        )
        val rows = ScoreRows.build(list, onlyFailed = false, collapsed = setOf("2024-2025学年秋"))
        assertEquals(1, rows.filterIsInstance<ScoreRow.Item>().size)
        assertTrue(rows.filterIsInstance<ScoreRow.Header>().first { it.term == "2024-2025学年秋" }.collapsed)
    }

    @Test
    fun `只看不及格 只留有据可查的不及格`() {
        val list = listOf(
            score("挂科", "2024-2025学年秋", "52", "2", ""),
            score("等级不及格", "2024-2025学年秋", "", "2", "不及格"),
            score("及格", "2024-2025学年秋", "62", "2", ""),
            score("等级制不知道", "2024-2025学年秋", "中等", "2", ""),
        )
        val rows = ScoreRows.build(list, onlyFailed = true, collapsed = emptySet())
        val names = rows.filterIsInstance<ScoreRow.Item>().map { it.score.name }
        assertEquals(listOf("挂科", "等级不及格"), names)
    }

    // ── 成绩：统计（缺失不编）──────────────────────────────

    @Test
    fun `字段齐全时给出学分合计 算术均分 学分加权`() {
        val list = listOf(
            score("生态学基础", "2024-2025学年秋", "73", "2"),
            score("土壤学", "2024-2025学年秋", "88", "3"),
        )
        val st = ScoreRows.termStats(list)
        assertEquals(2, st.count)
        assertEquals(5.0, st.credits!!, 0.001)
        assertEquals(80.5, st.average!!, 0.001)
        // (73*2 + 88*3) / 5 = 82.0
        assertEquals(82.0, st.weighted!!, 0.001)
    }

    @Test
    fun `有等级制课程时不算均分 也不给加权`() {
        val list = listOf(
            score("生态学基础", "2024-2025学年秋", "73", "2"),
            score("体育", "2024-2025学年秋", "优秀", "1", "优"),
        )
        val st = ScoreRows.termStats(list)
        assertNull("混入等级制就不该给平均分", st.average)
        assertNull("平均分没有，加权更不能有", st.weighted)
        assertEquals("学分仍然算得出来", 3.0, st.credits!!, 0.001)
    }

    @Test
    fun `缺学分时不算学分合计与加权 但均分照给`() {
        val list = listOf(
            score("生态学基础", "2024-2025学年秋", "73", "2"),
            score("神秘课程", "2024-2025学年秋", "81", ""),
        )
        val st = ScoreRows.termStats(list)
        assertNull(st.credits)
        assertNull(st.weighted)
        assertEquals(77.0, st.average!!, 0.001)
    }

    @Test
    fun `学分是 0 或负数视为缺失`() {
        val list = listOf(score("零学分", "2024-2025学年秋", "90", "0"))
        val st = ScoreRows.termStats(list)
        assertNull(st.credits)
        assertNull(st.weighted)
        assertEquals(90.0, st.average!!, 0.001)
    }

    @Test
    fun `空学期统计不崩 全返回空`() {
        val st = ScoreRows.termStats(emptyList())
        assertEquals(0, st.count)
        assertNull(st.credits)
        assertNull(st.average)
        assertNull(st.weighted)
        assertEquals(0, st.failed)
    }

    @Test
    fun `工具条文案带真实计数与筛选状态`() {
        val list = listOf(
            score("挂科", "2024-2025学年秋", "52", "2"),
            score("及格", "2025-2026学年春", "77", "2"),
        )
        assertEquals("2 门 · 2 学期 · 1 门不及格", ScoreRows.toolbarLabel(list, list, false))
        val failed = list.filter { it.failed }
        assertEquals("只看不及格 · 1 门", ScoreRows.toolbarLabel(list, failed, true))
        assertEquals("0 门", ScoreRows.toolbarLabel(emptyList(), emptyList(), false))
    }

    @Test
    fun `均分格式化 null 不产出字符串`() {
        assertNull(ScoreRows.fmt(null))
        assertEquals("82.0", ScoreRows.fmt(82.0))
        assertEquals("79.3", ScoreRows.fmt(79.26))
    }
}
