package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.FreeBuilding
import cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue
import cn.dkarbon.enroute.data.UrpHomeApi.ProfileField
import cn.dkarbon.enroute.data.UrpHomeApi.RoomOption
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit
import cn.dkarbon.enroute.data.UrpHomeApi.TermScore
import cn.dkarbon.enroute.ui.home.HomeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 学业主页的派生活辑（纯函数）。
 *
 * 重点是**不确定的情形**：日期缺、学分缺、绩点缺、楼层号不是数字。
 * 这些才是线上真会碰到的，也是最容易被写成"显示 0"的地方。
 */
class HomeModelTest {

    private val today = LocalDate.of(2026, 11, 13)

    private fun exam(date: String, clock: String = "10:00", name: String = "某课") =
        ExamItem(name = name, date = date, startClock = clock, time = "$date $clock")

    // ── 考试倒计时 ────────────────────────────────────────

    @Test
    fun `倒计时文案覆盖今天明天N天后已结束`() {
        assertEquals("今天", HomeModel.countdownLabel("2026-11-13", today))
        assertEquals("明天", HomeModel.countdownLabel("2026-11-14", today))
        assertEquals("2 天后", HomeModel.countdownLabel("2026-11-15", today))
        assertEquals("44 天后", HomeModel.countdownLabel("2026-12-27", today))
        assertEquals("已结束", HomeModel.countdownLabel("2026-11-12", today))
    }

    @Test
    fun `日期非法时不编倒计时`() {
        assertNull(HomeModel.countdownLabel("", today))
        assertNull(HomeModel.countdownLabel("11-15", today))
        assertNull(HomeModel.countdownLabel("2026-13-40", today))
        assertNull(HomeModel.daysUntil("不是日期", today))
    }

    @Test
    fun `下一场考试跳过已结束和没有日期的`() {
        val list = listOf(
            exam("2026-11-01"),          // 已结束
            exam(""),                    // 没日期，无法判断先后 → 跳过
            exam("2026-12-15"),
            exam("2026-11-15"),
        )
        val next = HomeModel.nextExam(list, today)
        assertEquals("2026-11-15", next?.date)
    }

    @Test
    fun `同一天两场按开考时刻取早的`() {
        val list = listOf(
            exam("2026-11-15", "14:00"),
            exam("2026-11-15", "08:00"),
        )
        assertEquals("08:00", HomeModel.nextExam(list, today)?.startClock)
    }

    @Test
    fun `全部考完时没有下一场也不算错`() {
        val list = listOf(exam("2026-11-01"), exam("2026-11-02"))
        assertNull(HomeModel.nextExam(list, today))
        assertEquals(2, HomeModel.finishedExamCount(list, today))
    }

    @Test
    fun `今天的考试仍然算下一场`() {
        val list = listOf(exam("2026-11-13", "10:00"))
        assertEquals("2026-11-13", HomeModel.nextExam(list, today)?.date)
        assertEquals(0, HomeModel.finishedExamCount(list, today))
    }

    // ── 学分进度 ──────────────────────────────────────────

    @Test
    fun `学分文案与进度`() {
        val p = CreditProgress("方案", "1", requiredXf = 165.0, earnedXf = 106.0, passedCount = 55)
        assertEquals("106 / 165 学分", HomeModel.creditLabel(p))
        assertEquals(64, p.percent)
        assertEquals(59.0, HomeModel.creditRemaining(p)!!, 0.001)
        assertEquals("通过 55 门", HomeModel.courseCountLabel(p))
    }

    @Test
    fun `需修学分缺失时不造分母`() {
        val p = CreditProgress(earnedXf = 12.5)
        assertEquals("已得 12.5 学分", HomeModel.creditLabel(p))
        assertNull(p.percent)
        assertNull(HomeModel.creditRemaining(p))
    }

    @Test
    fun `什么都没有时明说暂缺`() {
        assertEquals("暂缺", HomeModel.creditLabel(CreditProgress()))
        assertEquals("", HomeModel.courseCountLabel(CreditProgress()))
    }

    @Test
    fun `挂科门数会单独点出来`() {
        val p = CreditProgress(requiredXf = 165.0, earnedXf = 100.0, passedCount = 50, failedCount = 2)
        assertEquals("通过 50 门 · 挂 2 门", HomeModel.courseCountLabel(p))
    }

    @Test
    fun `已得超过需修时不出现负剩余`() {
        val p = CreditProgress(requiredXf = 100.0, earnedXf = 110.0)
        assertEquals(0.0, HomeModel.creditRemaining(p)!!, 0.001)
        assertEquals(100, p.percent)
    }

    // ── 成绩统计 ──────────────────────────────────────────

    @Test
    fun `绩点缺失显示暂缺而不是零`() {
        val s = ScoreStats(weightedGpa = null, creditSum = 24.05, passed = 13, total = 13)
        assertEquals("暂缺", HomeModel.gpaLabel(s))
        assertTrue(HomeModel.gpaHint(s).contains("没有可用的绩点字段"))
    }

    @Test
    fun `绩点保留两位并交代覆盖面`() {
        val s = ScoreStats(
            weightedGpa = 3.4147, gpaCreditSum = 20.0, creditSum = 24.05,
            passed = 13, total = 13, failed = 0,
        )
        assertEquals("3.41", HomeModel.gpaLabel(s))
        val hint = HomeModel.gpaHint(s)
        assertTrue(hint, hint.contains("覆盖 20 / 24.05 学分"))
        assertEquals("通过 13 门", HomeModel.scoreCountLabel(s))
    }

    @Test
    fun `有未通过时单独点出来`() {
        val s = ScoreStats(weightedGpa = 2.0, passed = 12, total = 13, failed = 1)
        assertEquals("通过 12 门 · 未通过 1 门", HomeModel.scoreCountLabel(s))
    }

    @Test
    fun `学期短名压成两位年份并去掉括号说明`() {
        assertEquals("24-25 秋", HomeModel.shortTerm("2024-2025学年秋(两学期)"))
        assertEquals("25-26 春", HomeModel.shortTerm("2025-2026学年春（两学期）"))
        assertEquals("某学期", HomeModel.shortTerm("某学期"))
    }

    @Test
    fun `学期段能原样映射`() {
        val s = ScoreStats(terms = listOf(TermScore("2024-2025学年秋", 24.05, 13, 13, 0)))
        assertEquals("24-25 秋", HomeModel.shortTerm(s.terms.first().title))
    }

    // ── 空闲教室 ──────────────────────────────────────────

    @Test
    fun `楼层按数字升序且非数字楼层排最后`() {
        val b = FreeBuilding(
            "第二教学楼",
            listOf(
                RoomOption("501", 100, "5"),
                RoomOption("201", 162, "2"),
                RoomOption("101", 162, "1"),
                RoomOption("附1", 30, ""),
            ),
        )
        val groups = HomeModel.roomGroups(b)
        assertEquals(listOf("1F", "2F", "5F", "其他"), groups.map { it.first })
        assertEquals("101", groups[0].second.first().room)
    }

    @Test
    fun `同层教室按房号长度再按字典序排`() {
        val b = FreeBuilding("楼", listOf(
            RoomOption("1002", 10, "10"), RoomOption("702", 10, "10"), RoomOption("1001", 10, "10"),
        ))
        val same = HomeModel.roomGroups(b).first { it.first == "10F" }.second.map { it.room }
        assertEquals(listOf("702", "1001", "1002"), same)
    }

    @Test
    fun `座位数缺失时不显示零座`() {
        assertEquals("72 座", HomeModel.seatsLabel(RoomOption("719", 72, "7")))
        assertEquals("", HomeModel.seatsLabel(RoomOption("719", 0, "7")))
    }

    @Test
    fun `空闲教室标题区分有与没有`() {
        val ro = cn.dkarbon.enroute.data.UrpHomeApi.FreeRooms(
            "主校", listOf(FreeBuilding("第二教学楼", listOf(RoomOption("719", 72, "7")))),
        )
        assertEquals("第二教学楼 · 1 间", HomeModel.freeRoomsHeadline(ro, "第二教学楼"))
        assertTrue(
            HomeModel.freeRoomsHeadline(cn.dkarbon.enroute.data.UrpHomeApi.FreeRooms(), "第二教学楼")
                .startsWith("第二教学楼这一节没有空闲教室"),
        )
    }

    @Test
    fun `空闲教室空态三分法`() {
        // ① 真的没空教室：告诉用户换节次/换天，而不是让他以为坏了
        val nothing = HomeModel.freeRoomsEmptyHint(FreeRoomsIssue.NOTHING_FREE, "第二教学楼")
        assertTrue(nothing, nothing.contains("这一节没有空闲教室"))
        assertTrue(nothing, nothing.contains("明天"))
        // ② 会话/网络：绝不能说自己「没有空教室」，要给可执行的下一步
        val noSession = HomeModel.freeRoomsEmptyHint(FreeRoomsIssue.NO_SESSION, "第二教学楼")
        assertTrue(noSession, noSession.contains("不是「没有空教室」"))
        assertTrue(noSession, noSession.contains("校园网"))
        assertTrue(noSession, noSession.contains("重新登录"))
        // ③ 选楼没生效：说明是故障，并指向刷新/诊断
        val lost = HomeModel.freeRoomsEmptyHint(FreeRoomsIssue.PICK_LOST, "第二教学楼")
        assertTrue(lost, lost.contains("不是你选的那栋楼"))
        assertTrue(lost, lost.contains("刷新"))
        // 楼名缺失时不留半截句子
        assertTrue(HomeModel.freeRoomsEmptyHint(FreeRoomsIssue.NOTHING_FREE).startsWith("这栋楼"))
    }

    // ── 身份 ──────────────────────────────────────────────

    @Test
    fun `副标题缺段落时不留空分隔符`() {
        val full = StudentProfile(listOf(
            ProfileField("班级", "24土管1"), ProfileField("专业", "土地资源管理"), ProfileField("年级", "2024级"),
        ))
        assertEquals("24土管1 · 土地资源管理 · 2024级", HomeModel.profileSubtitle(full))

        val partial = StudentProfile(listOf(ProfileField("专业", "土地资源管理")))
        assertEquals("土地资源管理", HomeModel.profileSubtitle(partial))
        assertEquals("", HomeModel.profileSubtitle(StudentProfile()))
    }

    @Test
    fun `身份徽标只挑有值的`() {
        val p = StudentProfile(listOf(
            ProfileField("学号", "2024000000"), ProfileField("院系", "土地与环境学院"),
            ProfileField("培养层次", "本科"), ProfileField("入学日期", "20240901"),
        ))
        val badges = HomeModel.profileBadges(p)
        assertEquals(listOf("学号", "院系", "层次", "入学"), badges.map { it.first })
        assertEquals("2024-09", badges.last().second)
    }

    @Test
    fun `头像取姓名首字`() {
        assertEquals("张", HomeModel.initialOf("张三"))
        assertEquals("", HomeModel.initialOf("  "))
    }

    @Test
    fun `教师行与空态文案`() {
        assertEquals("土环 · 教授", HomeModel.teacherLine(TeacherHit("董秀茹", "1", "土环", "教授")))
        assertEquals("教授", HomeModel.teacherLine(TeacherHit("某", "1", "", "教授")))
        assertTrue(HomeModel.teacherEmptyHint("").contains("输入教师姓名"))
        assertTrue(HomeModel.teacherEmptyHint("董").contains("没找到「董」"))
    }

    // ── 学期号（票 01）────────────────────────────────────

    @Test
    fun `按日历推当前学期号`() {
        // 8 月及以后 = 新学年第一学期；1 月仍属上一学年第一学期
        assertEquals("2026-2027-1-1", HomeModel.currentTermCode(LocalDate.of(2026, 11, 13)))
        assertEquals("2026-2027-1-1", HomeModel.currentTermCode(LocalDate.of(2026, 9, 1)))
        assertEquals("2026-2027-1-1", HomeModel.currentTermCode(LocalDate.of(2027, 1, 10)))
        // 2~7 月 = 第二学期
        assertEquals("2026-2027-2-1", HomeModel.currentTermCode(LocalDate.of(2027, 3, 5)))
        assertEquals("2026-2027-2-1", HomeModel.currentTermCode(LocalDate.of(2027, 7, 20)))
    }

    @Test
    fun `服务器课表给的学期优先`() {
        assertEquals(
            "2026-2027-1-1",
            HomeModel.currentTermCode(today, serverTerm = "2026-2027-1"),
        )
        // 服务器自带四段 → 原样用，不去改那个未实测的第四段
        assertEquals(
            "2026-2027-1-3",
            HomeModel.currentTermCode(today, serverTerm = "2026-2027-1-3"),
        )
    }

    @Test
    fun `缓存里过期的学期号不用`() {
        assertEquals(
            "2026-2027-1-1",
            HomeModel.currentTermCode(today, serverTerm = "2025-2026-1"),
        )
        assertEquals("2026-2027-1-1", HomeModel.currentTermCode(today, serverTerm = "乱填"))
        assertEquals("2026-2027-1-1", HomeModel.currentTermCode(today, serverTerm = ""))
    }

    @Test
    fun `学期号转成人看的季节`() {
        assertEquals("2026-2027 秋", HomeModel.termLabel("2026-2027-1-1"))
        assertEquals("2026-2027 春", HomeModel.termLabel("2026-2027-2-1"))
        // 认不出就原样返回，不编季节
        assertEquals("某学期", HomeModel.termLabel("某学期"))
        assertEquals("", HomeModel.termLabel(""))
    }

    @Test
    fun `搜索结果行交代学期且零条时留白`() {
        assertEquals("找到 11 位 · 2026-2027 秋", HomeModel.teacherResultLine(11, "2026-2027-1-1"))
        assertEquals("", HomeModel.teacherResultLine(0, "2026-2027-1-1"))
    }

    // ── 教室详情（票 03）──────────────────────────────────

    @Test
    fun `教室浮层副标题缺项就少一段`() {
        assertEquals("第二教学楼 · 72 座 · 7F", HomeModel.roomDetailSubtitle("第二教学楼", 72, "7"))
        assertEquals("第二教学楼", HomeModel.roomDetailSubtitle("第二教学楼", 0, ""))
        assertEquals("", HomeModel.roomDetailSubtitle("", 0, " "))
    }

    @Test
    fun `占用那一段必须明说暂缺且不冒充空表`() {
        val hint = HomeModel.occupancyHint()
        // 关键词：暂缺 + 说明为什么 + 「以现场为准」
        assertTrue(hint, hint.contains("暂缺"))
        assertTrue(hint, hint.contains("实测"))
        assertTrue(hint, hint.contains("现场"))
        // 绝不能出现「没有课/空闲」这种会让人白跑一趟的说法
        assertFalse(hint, hint.contains("没有课"))
        assertFalse(hint, hint.contains("全天空闲"))
    }

    @Test
    fun `查不到教室时交代检索覆盖面`() {
        val hit = HomeModel.classroomMissHint(295, 30)
        assertTrue(hit, hit.startsWith("没查到这间教室的记录"))
        assertTrue(hit, hit.contains("翻过 30 / 共 295 间教室"))
        // 总数缺失时只说翻了多少条，不编总数
        val noTotal = HomeModel.classroomMissHint(0, 30)
        assertTrue(noTotal, noTotal.contains("翻过 30 条记录"))
        assertTrue(noTotal, !noTotal.contains("共 0"))
        // 一条都没拿回来时也不能装作翻过
        val nothing = HomeModel.classroomMissHint(0, 0)
        assertTrue(nothing, nothing.contains("一条数据都没拿回来"))
    }

    // ── 教师详情（票 08）──────────────────────────────────

    private fun slot(
        weekday: Int = 2,
        start: Int = 3,
        span: Int = 2,
        weekText: String = "1-3,5,8-15周上",
        weeks: List<Int> = listOf(1, 2, 3, 5),
    ) = cn.dkarbon.enroute.data.UrpHomeApi.TeacherSlot(
        courseName = "不动产估价", courseNo = "901040302", seqNo = "01",
        weekday = weekday, startSection = start, span = span,
        weeks = weeks, weekText = weekText, campus = "主校", building = "第二教学楼", room = "606",
    )

    @Test
    fun `学期号能拼成服务器口径的 planName`() {
        assertEquals("2026-2027学年秋", HomeModel.termPlanName("2026-2027-1-1"))
        assertEquals("2026-2027学年春", HomeModel.termPlanName("2026-2027-2-1"))
        assertEquals("", HomeModel.termPlanName("乱填"))
    }

    @Test
    fun `星期与节次标签`() {
        assertEquals("周一", HomeModel.weekdayLabel(1))
        assertEquals("周日", HomeModel.weekdayLabel(7))
        assertEquals("", HomeModel.weekdayLabel(0))
        assertEquals("", HomeModel.weekdayLabel(9))
        assertEquals("第 3-4 节", HomeModel.sectionsLabel(slot()))
        assertEquals("第 3 节", HomeModel.sectionsLabel(slot(span = 1)))
        assertEquals("", HomeModel.sectionsLabel(slot(start = 0)))
    }

    @Test
    fun `时段行优先用服务器原文周次`() {
        val line = HomeModel.slotWhenLabel(slot())
        assertTrue(line, line.startsWith("周二"))
        assertTrue(line, line.contains("第 3-4 节"))
        assertTrue(line, line.contains("1-3,5,8-15周上"))
        // 没有原文周次时回落到位图压缩
        val noText = HomeModel.slotWhenLabel(slot(weekText = "", weeks = listOf(1, 2, 3, 5, 8, 9, 10)))
        assertTrue(noText, noText.contains("1-3,5,8-10周"))
    }

    @Test
    fun `周次连续段压成区间`() {
        assertEquals("1-3,5,8-10", HomeModel.compressWeeks(listOf(1, 2, 3, 5, 8, 9, 10)))
        assertEquals("7", HomeModel.compressWeeks(listOf(7)))
        assertEquals("", HomeModel.compressWeeks(emptyList()))
        assertEquals("2-3", HomeModel.compressWeeks(listOf(3, 2, 3)))
    }

    @Test
    fun `课表小结报时段数与门数`() {
        val s = cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule(
            slots = listOf(
                slot(weekday = 2),
                slot(weekday = 4),
                slot(weekday = 2, start = 7, weekText = "第13周", weeks = listOf(13))
                    .copy(courseName = "专业教育与社会需求（土管）", seqNo = "02"),
            ),
        )
        assertEquals("3 个时段 · 2 门课", HomeModel.scheduleHeadline(s))
        assertEquals("", HomeModel.scheduleHeadline(cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule()))
    }

    // ── 数字格式 ──────────────────────────────────────────

    @Test
    fun `数字去掉多余的零`() {
        assertEquals("12", HomeModel.trimNum(12.0))
        assertEquals("12.25", HomeModel.trimNum(12.25))
        assertEquals("63.99", HomeModel.trimNum(63.99))
        assertEquals("0", HomeModel.trimNum(0.0))
    }
}
