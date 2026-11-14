package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UrpHomeApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学业主页四支接口的解析（2026-11-13 真机抓的 payload 当夹具）。
 *
 * 这些断言的意义：接口是逆向来的，学校随时可能改字段。夹具锁住「今天能跑通」，
 * 哪天解析出来的东西变空，测试先红，而不是等用户在主页看到一片「暂缺」。
 */
class HomeParseTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("home/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    // ── 学籍 ──────────────────────────────────────────────

    @Test
    fun `学籍页能解析出身份字段`() {
        val p = UrpHomeApi.parseProfile(res("urp_rollinfo.html"))
        assertTrue("一个字段都没解析到", p.fields.size >= 10)
        assertEquals("2024000000", p.studentNo)
        assertEquals("某某同学", p.name)
        // 培养层次在这套部署里是「本科」
        assertEquals("本科", p.level)
        // 入学日期是 8 位纯数字
        assertEquals("20240901", p.enrolledAt)
    }

    @Test
    fun `学籍同名字段只保留第一次出现的`() {
        val p = UrpHomeApi.parseProfile(res("urp_rollinfo.html"))
        val labels = p.fields.map { it.label }
        assertEquals("标签有重复：$labels", labels.size, labels.distinct().size)
    }

    @Test
    fun `没有 profile 结构的页面解析成空且不抛异常`() {
        val p = UrpHomeApi.parseProfile("<html><body>登录页</body></html>")
        assertTrue(p.isEmpty)
        assertEquals("", p.studentNo)
    }

    // ── 培养方案进度 ──────────────────────────────────────

    @Test
    fun `培养方案进度拿到需修与已得学分`() {
        val c = UrpHomeApi.parseCreditProgress(res("urp_plan_detail.json"))
        assertEquals("2024级土地资源管理培养方案", c.planName)
        assertEquals(165.0, c.requiredXf, 0.001)
        assertEquals(106.0, c.earnedXf, 0.001)
        assertEquals(55, c.passedCount)
        assertEquals(0, c.failedCount)
        assertEquals(64, c.percent)
    }

    @Test
    fun `培养方案缺字段时不显示百分比`() {
        val c = UrpHomeApi.parseCreditProgress("""{"famc":"某方案"}""")
        assertEquals("某方案", c.planName)
        assertNull("需修为 0 时不该给出百分比", c.percent)
    }

    // ── 成绩统计 ──────────────────────────────────────────

    @Test
    fun `成绩统计按学期汇总并算出加权绩点`() {
        val s = UrpHomeApi.parseScoreStats(res("urp_passing_scores.json"))
        assertTrue("没有学期段", s.terms.isNotEmpty())
        assertTrue("总学分应大于 0", s.creditSum > 0.0)
        assertTrue("通过门数应大于 0", s.passed > 0)
        val gpa = s.weightedGpa
        assertNotNull("应能算出加权绩点（学校给了 gradePointScore）", gpa)
        // 实测这名学生绩点在 2.x~4.x 之间；越界说明加权算错了
        assertTrue("绩点越界：$gpa", gpa!! > 1.0 && gpa < 5.0)
        // 参与加权的学分不能超过总学分
        assertTrue("加权覆盖面超过总学分", s.gpaCreditSum <= s.creditSum + 0.001)
    }

    @Test
    fun `没有绩点字段时不给总绩点`() {
        val raw = """
            {"lnList":[{"zxf":3.0,"tgms":1,"zms":1,"wtgms":0,"cjlx":"2024-2025学年秋",
              "cjList":[{"courseName":"某课","credit":3,"cj":88}]}]}
        """.trimIndent()
        val s = UrpHomeApi.parseScoreStats(raw)
        assertNull("没绩点却给出了总绩点", s.weightedGpa)
        assertEquals(3.0, s.creditSum, 0.001)
        assertEquals(1, s.passed)
    }

    @Test
    fun `空 payload 解析成空统计`() {
        assertTrue(UrpHomeApi.parseScoreStats("{}").isEmpty)
        assertTrue(UrpHomeApi.parseScoreStats("not json").isEmpty)
    }

    // ── 空闲教室 ──────────────────────────────────────────

    @Test
    fun `校区与楼栋列表`() {
        val campuses = UrpHomeApi.parseCampuses(res("urp_free_campus.json"))
        assertEquals(5, campuses.size)
        assertTrue("应有主校", campuses.any { it.name == "主校" && it.number == "001" })
        // 路南的校区号是单位数 2 —— 按位数猜会踩坑，这里钉死
        assertTrue("路南校区号不是 2", campuses.any { it.name == "路南" && it.number == "2" })

        val buildings = UrpHomeApi.parseBuildings(res("urp_free_buildings.json"))
        assertTrue(buildings.size > 10)
        assertTrue("应有第二教学楼", buildings.any { it.name == "第二教学楼" && it.number == "2" })
        assertTrue("非数字楼号也要保留", buildings.any { it.number == "ZZZYZX" })
    }

    @Test
    fun `空闲教室能解析出房号座位楼层`() {
        val ro = UrpHomeApi.parseFreeRooms(res("urp_free_rooms.json"))
        assertEquals("主校", ro.campusName)
        assertTrue("一栋楼都没有", ro.buildings.isNotEmpty())
        val b = ro.buildings.first()
        assertEquals("第二教学楼", b.name)
        assertTrue(b.rooms.isNotEmpty())
        val r = b.rooms.first { it.room == "719" }
        assertEquals(72, r.seats)
        assertEquals("7", r.floor)
        assertTrue(ro.roomCount >= b.rooms.size)
    }

    // ── 票 02：会话粘滞两步流 ──────────────────────────────

    @Test
    fun `楼号从 acmcBuilding 来`() {
        val ro = UrpHomeApi.parseFreeRooms(res("urp_free_rooms.json"))
        assertEquals("2", ro.buildings.first().number)
        // 第 3 节那份只有 7 间（实测 len=496）
        val s3 = UrpHomeApi.parseFreeRooms(res("urp_free_rooms_s3.json"))
        assertEquals(7, s3.roomCount)
        assertEquals("主校", s3.campusName)
    }

    @Test
    fun `按楼号过滤掉别的楼`() {
        val fixture = res("urp_free_rooms.json")   // 里面全是第二教学楼（楼号 2）
        assertEquals(0, UrpHomeApi.parseFreeRooms(fixture, "1").roomCount)
        assertTrue(UrpHomeApi.parseFreeRooms(fixture, "2").roomCount > 0)
        // 不传楼号 = 不过滤
        assertTrue(UrpHomeApi.parseFreeRooms(fixture).roomCount > 0)
    }

    @Test
    fun `选楼表单就是 position 加 xqm`() {
        assertEquals(
            listOf("position" to "001_2", "xqm" to "主校"),
            UrpHomeApi.freeRoomsPickForm("001", "2", "主校"),
        )
        // 校区号是单位数（路南 = 2）时不能自作聪明补零
        assertEquals("position" to "2_ZZZYZX", UrpHomeApi.freeRoomsPickForm("2", "ZZZYZX", "路南")[0])
    }

    @Test
    fun `第二步路径把节次与天数夹在合法区间`() {
        assertEquals(
            "/student/teachingResources/freeClassroom/today/11?dayplus=0",
            UrpHomeApi.freeRoomsPath(11, 0),
        )
        assertEquals(
            "/student/teachingResources/freeClassroom/today/1?dayplus=2",
            UrpHomeApi.freeRoomsPath(0, 9),
        )
        assertEquals(
            "/student/teachingResources/freeClassroom/today/12?dayplus=1",
            UrpHomeApi.freeRoomsPath(99, 1),
        )
    }

    @Test
    fun `正常情形判定为 OK 且能看见教室`() {
        val ro = UrpHomeApi.judgeFreeRooms("", res("urp_free_rooms.json"), "2")
        assertEquals(UrpHomeApi.FreeRoomsIssue.OK, ro.issue)
        assertTrue(ro.roomCount > 0)
    }

    @Test
    fun `真的没有空教室判定为 NOTHING_FREE`() {
        val empty = """{"curxqm":"主校","spareroomObjList":[]}"""
        val ro = UrpHomeApi.judgeFreeRooms("", empty, "6")
        assertEquals(UrpHomeApi.FreeRoomsIssue.NOTHING_FREE, ro.issue)
        assertEquals(0, ro.roomCount)
        // 楼条目在、但 claroom 空，也是「真的没有」
        val noRooms = """{"curxqm":"主校","spareroomObjList":[{"acmcBuilding":"6",
            "acmcBuildingName":"水利馆","claroom":[]}]}"""
        assertEquals(
            UrpHomeApi.FreeRoomsIssue.NOTHING_FREE,
            UrpHomeApi.judgeFreeRooms("", noRooms, "6").issue,
        )
    }

    @Test
    fun `返回的是别的楼判定为选楼没生效`() {
        // 会话里还粘着第二教学楼，我们要的是第一教学楼 —— 这正是「没发 POST」时的样子
        val ro = UrpHomeApi.judgeFreeRooms("", res("urp_free_rooms.json"), "1")
        assertEquals(UrpHomeApi.FreeRoomsIssue.PICK_LOST, ro.issue)
        assertEquals("不匹配的楼不该被当成本次结果渲染", 0, ro.roomCount)
    }

    @Test
    fun `登录页或空响应判定为会话失效`() {
        val login = """<html><body><form id="loginForm" name="lt" value="LT-1">
            <a href="https://pass.syau.edu.cn/tpass/login">登录</a></form></body></html>"""
        assertEquals(
            "选楼那步被踢回登录页",
            UrpHomeApi.FreeRoomsIssue.NO_SESSION,
            UrpHomeApi.judgeFreeRooms(login, res("urp_free_rooms.json"), "2").issue,
        )
        assertEquals(
            "取节次那步被踢回登录页",
            UrpHomeApi.FreeRoomsIssue.NO_SESSION,
            UrpHomeApi.judgeFreeRooms("", login, "2").issue,
        )
        assertEquals(
            "空响应（连接断了 / 非 200）",
            UrpHomeApi.FreeRoomsIssue.NO_SESSION,
            UrpHomeApi.judgeFreeRooms("", "", "2").issue,
        )
        assertEquals(
            "回了 HTML 而不是 JSON",
            UrpHomeApi.FreeRoomsIssue.NO_SESSION,
            UrpHomeApi.judgeFreeRooms("", "<html>502 Bad Gateway</html>", "2").issue,
        )
    }

    @Test
    fun `纯 JSON 数据页不会被误判成登录页`() {
        assertFalse(UrpHomeApi.looksLikeLoginPage(res("urp_free_rooms.json")))
        assertFalse(UrpHomeApi.looksLikeLoginPage(""))
        assertTrue(UrpHomeApi.looksLikeLoginPage("""<form id="loginForm">"""))
    }

    // ── 教师 ──────────────────────────────────────────────

    @Test
    fun `教师搜索解析出姓名院系职称`() {
        val ts = UrpHomeApi.parseTeachers(res("urp_teacher_search.json"))
        assertEquals(11, ts.size)
        val dong = ts.first { it.name == "董秀茹" }
        assertEquals("1995500023", dong.number)
        assertEquals("土环", dong.dept)
        assertEquals("教授", dong.title)
    }

    @Test
    fun `教师搜索的空响应不抛异常`() {
        assertEquals(0, UrpHomeApi.parseTeachers("[]").size)
        assertEquals(0, UrpHomeApi.parseTeachers("{}").size)
    }

    // ── 票 01：重复工号去重 + 学期收敛 ─────────────────────

    /** 两条记录：同一教师同一学期各返回一次（服务器没按学期过滤时的真实形态） */
    private fun rec(name: String, num: String, term: String = "2026-2027-1-1") =
        """{"departmentName":"土环","executiveEducationPlanName":"2026-2027学年秋",
           "id":{"executiveEducationPlanNumber":"$term","teacherNumber":"$num"},
           "professionalTitle":"教授","sex":"女","teacherName":"$name","zxjxjhh":"$term"}"""

    private fun payload(vararg records: String) =
        """[{"pageContext":{"totalCount":${records.size}},"pageNum":1,"pageSize":30,"records":[
            ${records.joinToString(",")}]}]"""

    @Test
    fun `重复工号只保留第一条`() {
        val raw = payload(
            rec("董秀茹", "1995500023", "2026-2027-1-1"),
            rec("董秀茹", "1995500023", "2025-2026-1-1"),
            rec("董克宝", "1995500020"),
        )
        val ts = UrpHomeApi.parseTeachers(raw)
        assertEquals("没去重：${ts.map { it.name + '/' + it.number }}", 2, ts.size)
        assertEquals("1995500023", ts[0].number)
        assertEquals("留的不是第一条学期", "2026-2027-1-1", ts[0].term)
        // 列表 key 依赖的唯一性：这是崩溃的直接原因，必须钉死
        assertEquals(ts.size, ts.map { it.number }.distinct().size)
    }

    @Test
    fun `工号缺失的记录不合并`() {
        val raw = payload(rec("甲", ""), rec("乙", ""))
        assertEquals("工号空的人不能当成同一个人", 2, UrpHomeApi.parseTeachers(raw).size)
    }

    @Test
    fun `按学期过滤时只留当前学期的记录`() {
        val raw = payload(
            rec("董秀茹", "1995500023", "2026-2027-1-1"),
            rec("董秀茹", "1995500023", "2025-2026-1-1"),
            rec("董克宝", "1995500020", "2025-2026-2-1"),
        )
        val only = UrpHomeApi.parseTeachers(raw, "2026-2027-1-1")
        assertEquals(only.map { it.term }.toString(), 1, only.size)
        assertEquals("董秀茹", only[0].name)
        // 不过滤时三条都在（去重后两条，因为董秀茹重复）
        assertEquals(2, UrpHomeApi.parseTeachers(raw).size)
    }

    @Test
    fun `学期第四段不同也算同一学期`() {
        val raw = payload(rec("董秀茹", "1995500023", "2026-2027-1-2"))
        assertEquals(1, UrpHomeApi.parseTeachers(raw, "2026-2027-1-1").size)
    }

    @Test
    fun `记录缺学期号时不被过滤掉`() {
        val raw = """[{"records":[{"teacherName":"董秀茹","id":{"teacherNumber":"1995500023"}}]}]"""
        assertEquals("学期字段缺失应保留，不能凭缺失就丢数据", 1,
            UrpHomeApi.parseTeachers(raw, "2026-2027-1-1").size)
    }

    @Test
    fun `学期号前缀只取前三段`() {
        assertEquals("2026-2027-1", UrpHomeApi.termPrefix("2026-2027-1-1"))
        assertEquals("2026-2027-1", UrpHomeApi.termPrefix("2026-2027-1"))
        assertEquals("", UrpHomeApi.termPrefix(""))
        assertEquals("", UrpHomeApi.termPrefix("乱填"))
    }

    @Test
    fun `真机夹具按当前学期收敛后不重复`() {
        val ts = UrpHomeApi.parseTeachers(res("urp_teacher_search.json"), "2026-2027-1-1")
        assertTrue("夹具里应有董秀茹", ts.any { it.name == "董秀茹" })
        assertEquals("工号必须唯一（否则列表 key 崩溃）", ts.size, ts.map { it.number }.distinct().size)
        assertEquals("先按入口的 11 条", 11, ts.size)
    }

    // ── 票 03：教室档案检索（参数未实测，锁的是解析骨架）─────

    private fun classroomFixture() = UrpHomeApi.parseClassroomSearch(res("urp_classroom_search.json"))

    @Test
    fun `教室检索能解析出档案字段`() {
        // ⚠️ 夹具 urp_classroom_search.json 标了「未实测」：外层形态与 totalCount 来自真实响应，
        // records 的字段按页面表头补全（见 docs §6.1）。所以这里锁的是**解析口径**，不是学校契约。
        val p = classroomFixture()
        assertEquals(295, p.totalCount)
        assertEquals(1, p.pageNum)
        assertEquals(3, p.records.size)

        val r719 = p.records.first { it.roomNo == "719" }
        assertEquals("主校", r719.campusName)
        assertEquals("第二教学楼", r719.buildingName)
        assertEquals(72, r719.seats)
        assertEquals("多媒体教室", r719.type)
        assertEquals("2026-2027-1-1", r719.term)

        // 机房那行是页面真实表头数据：教室号 2机房 / 教室名 5楼2机房 / 106 座 / 信电 / 机房
        val lab = p.records.first { it.roomNo == "2机房" }
        assertEquals("5楼2机房", lab.roomName)
        assertEquals(106, lab.seats)
        assertEquals("机房", lab.type)
        assertEquals("信电", lab.dept)
    }

    @Test
    fun `教室检索遇到非 JSON 返回空页而不是抛异常`() {
        assertTrue(UrpHomeApi.parseClassroomSearch("not json").records.isEmpty())
        assertTrue(UrpHomeApi.parseClassroomSearch("<html>登录</html>").records.isEmpty())
        assertTrue(UrpHomeApi.parseClassroomSearch("{}").records.isEmpty())
        assertTrue(UrpHomeApi.parseClassroomSearch("[]").records.isEmpty())
        // 字段缺一半也能出记录，不会被整条丢掉（靠 roomName 辨认）
        val partial = """[{"pageData":{"pageContext":{"totalCount":1},
            "records":[{"classroomName":"5楼2机房"}]}}]"""
        val p = UrpHomeApi.parseClassroomSearch(partial)
        assertEquals(1, p.records.size)
        assertEquals("5楼2机房", p.records.first().roomName)
        assertEquals(0, p.records.first().seats)
    }

    @Test
    fun `教室检索表单只带学期与分页`() {
        // 过滤字段名没实测 → 一个都不传，过滤全在客户端（口径单一）
        assertEquals(
            listOf(
                "executiveEducationPlanNumber" to "2026-2027-1-1",
                "pageNum" to "1",
                "pageSize" to "200",
            ),
            UrpHomeApi.classroomSearchForm("2026-2027-1-1", 1, 200),
        )
        // 越界参数夹住：pageNum 至少 1，pageSize 上界 500
        assertEquals("1", UrpHomeApi.classroomSearchForm("t", 0, 10)[1].second)
        assertEquals("500", UrpHomeApi.classroomSearchForm("t", 0, 9999)[2].second)
    }

    @Test
    fun `挑教室按教室号优先`() {
        val records = classroomFixture().records
        assertEquals("719", UrpHomeApi.pickClassroom(records, "第二教学楼", "719")?.roomNo)
        assertEquals("2机房", UrpHomeApi.pickClassroom(records, "第一教学楼", "2机房")?.roomNo)
    }

    @Test
    fun `不跨楼认领同一房号`() {
        val records = classroomFixture().records
        // 719 只在第二教学楼；问第一教学楼的 719 时必须说查不到，不能拿二教的顶替
        assertNull(UrpHomeApi.pickClassroom(records, "第一教学楼", "719"))
    }

    @Test
    fun `房号是别的房号的尾巴时不算命中`() {
        val records = listOf(
            UrpHomeApi.ClassroomInfo(buildingName = "第二教学楼", roomName = "1719", seats = 30),
        )
        assertNull(UrpHomeApi.pickClassroom(records, "第二教学楼", "719"))
        // 换成「2机房」这种非纯数字房号时，结尾匹配才允许生效
        val lab = listOf(
            UrpHomeApi.ClassroomInfo(buildingName = "第一教学楼", roomName = "5楼2机房", seats = 106),
        )
        assertEquals("5楼2机房", UrpHomeApi.pickClassroom(lab, "第一教学楼", "2机房")?.roomName)
    }

    @Test
    fun `同一档命中多条时宁可不给`() {
        val dup = listOf(
            UrpHomeApi.ClassroomInfo(buildingName = "第二教学楼", roomNo = "719", seats = 72),
            UrpHomeApi.ClassroomInfo(buildingName = "第二教学楼", roomNo = "719", seats = 1),
        )
        // 分不出哪条才是这间 → 当作查不到，免得把 1 座的座位数按到 72 座上
        assertNull(UrpHomeApi.pickClassroom(dup, "第二教学楼", "719"))
        // 楼名一致但记录没楼名字段时也算对得上（服务器不给不罚用户）
        val noBuilding = listOf(UrpHomeApi.ClassroomInfo(roomNo = "719", seats = 72))
        assertEquals("719", UrpHomeApi.pickClassroom(noBuilding, "第二教学楼", "719")?.roomNo)
    }
}
