package cn.dkarbon.enroute.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 学业主页要用的 URP 接口（2026-11-13 用真机登录态逐条实测，见
 * `docs/URP-学生端接口清单.md`）。
 *
 * 为什么不塞进 [AcademicApi]：那边的考试/成绩是**上半年的旧接口**（`KCM/KCCJ` 大写键），
 * 这里的四支是**另一套**（`courseName/gradePointScore` 驼峰键），两套 schema 混在一个文件里
 * 会让人误以为键名可以互换。分开写，各自带自己的解析与单测。
 *
 * ## 四条纪律
 * 1. **只读**：这里只发 GET / 列表 POST，绝不碰选课、退课、报名、评教提交。
 * 2. **token 现取现用**：`instructionPlanQuery` 与 `scoreQuery` 路径里带每次都变的 token，
 *    必须「取页面 → 抠 token → 立刻查 data」，不缓存跨请求。
 * 3. **算不出的不编**：绩点用学校给的 `gradePointScore` 加权，缺一项就不给总绩点（见 [ScoreStats]）。
 * 4. **空态要能解释**：每条失败都带可执行提示（连校园网 / 去设置页诊断）。
 */
object UrpHomeApi {

    private const val URP = "http://" + AcademicApi.URP_HOST


    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── 路径 ──────────────────────────────────────────────
    private const val PROFILE = "/student/rollManagement/rollInfo/index"
    internal const val PLAN_INDEX = "/student/integratedQuery/instructionPlanQuery/detail/index"
    internal const val PASS_INDEX = "/student/integratedQuery/scoreQuery/allPassingScores/index"
    private const val CAMPUS_LIST = "/student/teachingResources/freeClassroom/queryCodeCampusList"
    private const val BUILDING_LIST = "/student/teachingResources/freeClassroom/queryCodeTeaBuildingList"
    private const val FREE_ROOMS = "/student/teachingResources/freeClassroom/today"
    private const val TEACHER_SEARCH = "/student/teachingResources/teacherCurriculum/search"
    private const val CLASSROOM_SEARCH = "/student/teachingResources/classroomCurriculum/search"

    /** 教师课表第一步：只是预热回执（100 字节），不发它第二步拿不到数据 */
    private const val TEACHER_SCHEDULE_LO =
        "/student/teachingResources/teacherCurriculum/searchCurriculumInfoLo"

    /** 教师课表第二步：真正的数据 */
    private const val TEACHER_SCHEDULE_CB =
        "/student/teachingResources/teacherCurriculum/searchCurriculumInfo/callback"

    /** 教室检索每页要多少条（分页参数形态与教师课表同族，实测 `pageSize` 生效） */
    internal const val CLASSROOM_PAGE_SIZE = 200

    /** 培养方案页里那次性的随机 token */
    internal val PLAN_TOKEN_RE = Regex("""instructionPlanQuery/([A-Za-z0-9]{8,16})/detail""")

    /**
     * 培养方案页里写死的「方案计划号」。
     *
     * callback **必须带 `fajhh`**（方案计划号），不带就是
     * `400 Required String parameter 'fajhh' is not present` —— 见 2026-11-16 真机日志。
     * 这个号在页面里以 `instructionPlanQuery("202403112440")` 的形式写死，不在 URL 里。
     */
    internal val PLAN_FAJHH_RE = Regex("""instructionPlanQuery\("(\d+)"\)""")

    /** 及格成绩页里那次性的随机 token */
    internal val PASS_TOKEN_RE = Regex("""scoreQuery/([A-Za-z0-9]{8,16})/allPassingScores""")

    // ── 模型 ──────────────────────────────────────────────

    /** 学籍页的一个「标签 → 值」对。整页都收下来，取用哪个由 UI 决定 */
    @Serializable
    data class ProfileField(val label: String = "", val value: String = "")

    @Serializable
    data class StudentProfile(val fields: List<ProfileField> = emptyList()) {
        fun of(vararg labels: String): String = labels.firstNotNullOfOrNull { l ->
            fields.firstOrNull { it.label == l }?.value?.takeIf { it.isNotBlank() }
        }.orEmpty()

        val studentNo: String get() = of("学号")
        val name: String get() = of("姓名")
        val college: String get() = of("院系", "学院", "系所")
        val major: String get() = of("专业")
        val majorDirection: String get() = of("专业方向")
        val className: String get() = of("班级", "行政班")
        val grade: String get() = of("年级")
        val level: String get() = of("培养层次")
        val enrolledAt: String get() = of("入学日期")
        val studyType: String get() = of("修读类型")
        val isEmpty: Boolean get() = fields.isEmpty()
    }

    /** 培养方案进度：学校端算好的学分与门数，不自己数 */
    @Serializable
    data class CreditProgress(
        val planName: String = "",
        val planNo: String = "",
        /** 需修学分 `xqzxf` */
        val requiredXf: Double = 0.0,
        /** 已得学分 `ydhxf` */
        val earnedXf: Double = 0.0,
        val passedCount: Int = 0,
        val failedCount: Int = 0,
    ) {
        /** 进度百分比；需修为 0 时返回 null（不显示 0% 这种假进度） */
        val percent: Int?
            get() = if (requiredXf <= 0.0) null
            else ((earnedXf / requiredXf) * 100).toInt().coerceIn(0, 100)

        val isEmpty: Boolean get() = planName.isBlank() && requiredXf <= 0.0
    }

    /** 单个学期的成绩汇总（学校给的 `tgms/zms`，不是我们数的） */
    @Serializable
    data class TermScore(
        val title: String = "",
        val credit: Double = 0.0,
        val passed: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
    )

    /**
     * 成绩统计。
     *
     * [weightedGpa] 为 null 有两种含义，界面都只显示「绩点暂缺」：
     * ① 没有任何一条同时有绩点和学分；
     * ② **有任意一门课缺绩点或缺学分** —— 那时算出来的均值只覆盖了一部分课程，
     *    是个「看着像真的」的假数字。宁可留空，也不给一个少算了几门课的平均分。
     */
    @Serializable
    data class ScoreStats(
        val weightedGpa: Double? = null,
        /** 参与加权计算的学分和（用来解释这个绩点覆盖面有多大） */
        val gpaCreditSum: Double = 0.0,
        val creditSum: Double = 0.0,
        val passed: Int = 0,
        val total: Int = 0,
        val failed: Int = 0,
        val terms: List<TermScore> = emptyList(),
    ) {
        val isEmpty: Boolean get() = total == 0 && terms.isEmpty() && creditSum <= 0.0
    }

    @Serializable
    data class CampusOption(val number: String = "", val name: String = "")

    @Serializable
    data class BuildingOption(val number: String = "", val name: String = "")

    @Serializable
    data class RoomOption(val room: String = "", val seats: Int = 0, val floor: String = "")

    @Serializable
    data class FreeBuilding(
        val name: String = "",
        val rooms: List<RoomOption> = emptyList(),
        /** 楼号（`acmcBuilding`），用来核对「服务器返回的到底是不是我选的那栋楼」 */
        val number: String = "",
    )

    /**
     * 一次空闲教室查询的结局。
     *
     * 为什么要显式区分：接口是**会话粘滞**的，`GET /today/<节次>` 只答「本会话上次 POST
     * 选中的那栋楼」。所以「列表是空的」至少有三种完全不同的原因，混成一句
     * 「这一节没有空闲教室」就是误导用户（见票 02）。
     */
    @Serializable
    enum class FreeRoomsIssue {
        /** 正常拿到这栋楼的空闲教室 */
        OK,

        /** 服务器答复正常，但这栋楼这一节确实没空教室 */
        NOTHING_FREE,

        /** 会话失效 / 不在校园网 / 服务器答的不是 JSON —— 要去重新登录或连校园网 */
        NO_SESSION,

        /** 服务器答的是**另一栋楼**的教室 —— 「先 POST 选楼」这一步没生效 */
        PICK_LOST,
    }

    @Serializable
    data class FreeRooms(
        val campusName: String = "",
        val buildings: List<FreeBuilding> = emptyList(),
        /** 这次查询的结局，决定空态文案（见 [FreeRoomsIssue]） */
        val issue: FreeRoomsIssue = FreeRoomsIssue.OK,
    ) {
        val roomCount: Int get() = buildings.sumOf { it.rooms.size }
    }

    /**
     * 一间教室的档案（`classroomCurriculum/search` 的 `records[]`）。
     *
     * 字段名分两类，必须分清：
     * - **实测过的**（2026-11-13 真响应样本）：`campusName`、`classNumberOfSeats`、
     *   `classroomName`、`classroomTypeDirections`、`departmentName`、`executiveEducationPlanName`；
     * - **推断的**：`teachingBuildingName` / `classroomNumber` —— 页面表头有「教学楼名 / 教室号」两列，
     *   但当时留存的样本被截断在这两个字段之前。所以解析时给了别名兜底，等到有一次完整样本再收紧。
     */
    @Serializable
    data class ClassroomInfo(
        val campusName: String = "",
        val buildingName: String = "",
        /** 教室号（`719` 这种，和空闲教室接口的 `classroom` 同口径） */
        val roomNo: String = "",
        /** 教室名（`5楼2机房` 这种） */
        val roomName: String = "",
        val seats: Int = 0,
        /** `classroomTypeDirections`：机房 / 多媒体教室 … */
        val type: String = "",
        val dept: String = "",
        val term: String = "",
    ) {
        val isEmpty: Boolean get() = roomNo.isBlank() && roomName.isBlank()
    }

    /** 教室检索的一页；[totalCount] 是服务器说的总数，用来说明「我到底翻了多少」 */
    @Serializable
    data class ClassroomPage(
        val totalCount: Int = 0,
        val pageNum: Int = 1,
        val records: List<ClassroomInfo> = emptyList(),
    )

    /**
     * 找一间教室的结局。
     *
     * 三个字段各有用途：[info] 是命中的档案；[sessionLost] 说明「不是没有这间教室，是没拿到数据」；
     * [scanned]/[totalCount] 让 UI 能如实交代覆盖面（「翻了 30/295 条」）。
     */
    @Serializable
    data class ClassroomLookup(
        val info: ClassroomInfo? = null,
        val totalCount: Int = 0,
        val scanned: Int = 0,
        val sessionLost: Boolean = false,
    )

    /**
     * 教师课表里的一个上课时段（某门课、某一天、某几节、某些周）。
     *
     * 字段全部来自实测样本 `docs/_probe/urp/teacher_schedule_callback.json`，
     * 字段表见 `docs/URP-学生端接口清单.md` §6.2。
     */
    @Serializable
    data class TeacherSlot(
        val courseName: String = "",
        val courseNo: String = "",
        val seqNo: String = "",
        /** 星期 1..7（1=周一）；服务器给的 `skxq` 有两份（id 内数字、外层字符串），解析时都认 */
        val weekday: Int = 0,
        /** 起始节次 */
        val startSection: Int = 0,
        /** 连上几节 */
        val span: Int = 1,
        /** 周次（由 `skzc` 24 位位图解出，不解析文字周次） */
        val weeks: List<Int> = emptyList(),
        /** 服务器原文周次描述，如 `1-3,5,8-15周上`；只作对照显示 */
        val weekText: String = "",
        val campus: String = "",
        val building: String = "",
        val room: String = "",
        val credit: String = "",
        val examType: String = "",
        val className: String = "",
        val students: Int = 0,
        val college: String = "",
        val term: String = "",
    ) {
        /** 占哪几个小节（如起始 3、连 2 → 3,4） */
        val sections: List<Int>
            get() = if (startSection <= 0) emptyList()
            else (startSection until startSection + span.coerceAtLeast(1)).toList()

        /** `第二教学楼 / 606` */
        val place: String
            get() = listOf(building, room).filter { it.isNotBlank() }.joinToString(" / ")

        val isEmpty: Boolean get() = courseName.isBlank() && weekday <= 0
    }

    /**
     * 一位教师某学期的课表。
     *
     * [sessionLost] 与「没排课」必须分开：前者是没拿到数据（登录态/网络），后者是学校真的没排。
     * 混成一句「暂无课表」会让学生以为这老师不上课。
     */
    @Serializable
    data class TeacherScheduleResult(
        val schedule: TeacherSchedule = TeacherSchedule(),
        val sessionLost: Boolean = false,
    )

    @Serializable
    data class TeacherSchedule(
        val teacherName: String = "",
        val teacherNo: String = "",
        val term: String = "",
        val slots: List<TeacherSlot> = emptyList(),
    ) {
        val isEmpty: Boolean get() = slots.isEmpty()

        /** 去重后的课程门数（同一门课在多个时段出现，只算一门） */
        val courseCount: Int
            get() = slots.filter { it.courseName.isNotBlank() }
                .map { it.courseNo + "|" + it.seqNo + "|" + it.courseName }
                .distinct().size
    }

    @Serializable
    data class TeacherHit(
        val name: String = "",
        val number: String = "",
        val dept: String = "",
        val title: String = "",
        val sex: String = "",
        /**
         * 这条记录属于哪个学期（`2026-2027-1-1`）。服务器没给就是空串。
         *
         * 有了它，界面才能钉住「这份结果是哪个学期的」，列表 key 也才有第二个维度。
         */
        val term: String = "",
    )

    // ── 抓取 ──────────────────────────────────────────────

    suspend fun fetchProfile(s: UrpClient.Session): StudentProfile =
        parseProfile(get(PROFILE, s))

    /**
     * 培养方案进度：先取页面抠 token 与方案计划号，再 **POST** callback。
     *
     * 两个坑都在真机日志里验过（2026-11-16）：
     * 1. callback 是 POST，GET 回 `400 Required String parameter 'fajhh' is not present`；
     * 2. `fajhh` 不在 URL 上，只以 `instructionPlanQuery("202403112440")` 写在页面脚本里。
     */
    suspend fun fetchCreditProgress(s: UrpClient.Session): CreditProgress {
        val html = get(PLAN_INDEX, s)
        val tk = PLAN_TOKEN_RE.find(html)?.groupValues?.get(1)
            ?: return CreditProgress()
        val fajhh = PLAN_FAJHH_RE.find(html)?.groupValues?.get(1)
            ?: return CreditProgress()
        return parseCreditProgress(
            postForm(
                "/student/integratedQuery/instructionPlanQuery/$tk/detail/callback",
                listOf("fajhh" to fajhh, "showMode" to "enrolled"),
                s,
            ),
        )
    }

    /** 成绩统计：同上两步 */
    suspend fun fetchScoreStats(s: UrpClient.Session): ScoreStats {
        val html = get(PASS_INDEX, s)
        val tk = PASS_TOKEN_RE.find(html)?.groupValues?.get(1)
            ?: return ScoreStats()
        return parseScoreStats(get("/student/integratedQuery/scoreQuery/$tk/allPassingScores/callback", s))
    }

    suspend fun fetchCampuses(s: UrpClient.Session): List<CampusOption> =
        parseCampuses(get(CAMPUS_LIST, s))

    suspend fun fetchBuildings(s: UrpClient.Session, campusNo: String): List<BuildingOption> =
        parseBuildings(get("$BUILDING_LIST?&xqh=$campusNo", s))

    /**
     * 某节次的空闲教室 —— **两步流**（2026-11-13 实测，证据 `docs/_probe/urp/free_sticky.js`）。
     *
     * ## 这个接口是会话粘滞的
     * `GET .../freeClassroom/today/<节次>?dayplus=N` **只返回本会话上一次 POST 选中的那栋楼**：
     *
     * | 做法 | 结果 |
     * |---|---|
     * | 只 `GET /today/11` | 空 |
     * | 先 `POST position=001_1&xqm=主校` 再 GET `/today/11` | 第一教学楼 74 间 |
     * | 先 `POST position=001_2` 再 GET `/today/11` | 第二教学楼 67 间 |
     * | 先 `POST position=001_6` 再 GET `/today/11` | 空（水利馆该节次确实没空教室） |
     * | `GET /today/11?dayplus=0&position=001_2`（参数当查询串） | 空（**参数被忽略**） |
     *
     * 所以旧版「只 GET」永远拿不到数据 —— 这就是票 02 说的「无论怎么选都是这一节没有空闲教室」。
     *
     * ① 和 ② 必须**紧挨着、同一个 [UrpClient.Session]**：中间插别的请求不会破坏它，
     * 但换了会话（重新登录）就会回到「没选楼」的状态。
     *
     * @param campusNo 校区号（`001` 主校 / `2` 路南 …）
     * @param campusName 校区名（`xqm` 字段要的是**名字**不是号，实测 `主校`）
     * @param buildingNo 楼号
     * @param sessionNo 节次 1..12
     * @param dayPlus 0 今天 / 1 明天 / 2 后天
     */
    suspend fun fetchFreeRooms(
        s: UrpClient.Session,
        campusNo: String,
        campusName: String,
        buildingNo: String,
        sessionNo: Int,
        dayPlus: Int = 0,
    ): FreeRooms {
        // 两步整体独占（见 [UrpSticky]）：快速切楼时 A 的第二步不许插到 B 的选楼后面
        return UrpSticky.withFreeRoom {
            // ① 选楼：POST 表单，服务器把「当前楼」记在会话里，响应是 153KB 的外壳 HTML（内容无用）
            val pickBody = postForm(FREE_ROOMS, freeRoomsPickForm(campusNo, buildingNo, campusName), s)
            // ② 立刻取这一节的空闲教室（同一会话，中间不插请求）
            val roomsBody = get(freeRoomsPath(sessionNo, dayPlus), s)
            judgeFreeRooms(pickBody, roomsBody, buildingNo)
        }
    }

    /** 「选楼」表单：`position=<校区号>_<楼号>`、`xqm=<校区名>`（纯函数，单测锁住字段名） */
    internal fun freeRoomsPickForm(
        campusNo: String,
        buildingNo: String,
        campusName: String,
    ): List<Pair<String, String>> = listOf(
        "position" to "${campusNo.trim()}_${buildingNo.trim()}",
        "xqm" to campusName.trim(),
    )

    /** 第二步的路径：节次夹到 1..12、dayplus 夹到 0..2（越界的值服务器会直接给空） */
    internal fun freeRoomsPath(sessionNo: Int, dayPlus: Int): String =
        "$FREE_ROOMS/${sessionNo.coerceIn(1, 12)}?dayplus=${dayPlus.coerceIn(0, 2)}"

    /**
     * 按教师姓名搜教师课表条目。
     *
     * ## 为什么学期号是必填的（2026-11-13 实测，见 `docs/_probe/urp/teacher_probe.txt`）
     * 页面上的查询表单就是 `executiveEducationPlanNumber`（学年学期下拉）+ `departmentNum` +
     * `teacherName` + 分页。**学期传空串 = 服务器返回全部学期的课表**（`totalCount=1008`），
     * 同一工号会在多个学期各出现一次 —— 票 01 那次
     * `Key "1995500023" was already used` 崩溃就是这么来的。带上学期（`2026-2027-1-1`）后
     * 实测 `teacherName=董` 只返回 11 条，全部是 `zxjxjhh=2026-2027-1-1`。
     *
     * ## 兜底
     * 学期号的第四段实测只有秋学期样本（`-1`）。若哪天带学期搜到 0 条，就退回「不带学期搜 +
     * 按每条记录自带的 `zxjxjhh` 过滤当前学期」：多一次请求，但结果仍然只含当前学期，且仍去重。
     */
    suspend fun searchTeachers(
        s: UrpClient.Session,
        keyword: String,
        termCode: String = "",
        pageSize: Int = 60,
    ): List<TeacherHit> {
        fun body(term: String): String = postForm(
            TEACHER_SEARCH,
            listOf(
                "executiveEducationPlanNumber" to term,
                "departmentNum" to "",
                "teacherName" to keyword.trim(),
                "pageNum" to "1",
                "pageSize" to pageSize.toString(),
            ),
            s,
        )

        val hits = parseTeachers(body(termCode), termCode)
        if (hits.isNotEmpty() || termCode.isBlank()) return hits
        return parseTeachers(body(""), termCode)
    }

    /**
     * 找一间教室的**档案**（票 03）。
     *
     * ## ⚠️ 参数形态未实测（2026-11-14）
     * `POST /classroomCurriculum/search` 的**过滤字段名**没验过：探针（`docs/_probe/urp/probe_classroom.js`）
     * 被 CAS 登录态挡在门外，见 `docs/URP-学生端接口清单.md` §6.1。所以这里**一个过滤字段都不猜**，
     * 只传「学期 + 分页」这两个形态在教师课表那边实测过同族的参数，拿回整页后在客户端按
     * 「楼名 + 教室号/教室名」精确匹配。
     *
     * 这样即使服务器忽略我们传的东西，结果也不会因此变**错** —— 最坏是「页没翻够、没找到」，
     * 而那种情况会连 `scanned/totalCount` 一起如实报给用户，不会伪装成「这间教室不存在」。
     *
     * ## 它返回的是档案，不是占用
     * 这支接口给的是教室档案（座位/类型/院系）。「这间教室被哪些课占着」在 URP 里是表格行内
     * 「课表信息查看」的**另一条路由**，尚未探明 —— 所以浮层里明写「占用暂缺」，绝不摆空课表。
     *
     * @param maxPages 最多翻几页（每页 [CLASSROOM_PAGE_SIZE] 条）；默认 3 页 ≈ 600 间，够覆盖全校 295 间
     */
    suspend fun findClassroom(
        s: UrpClient.Session,
        termCode: String,
        buildingName: String,
        roomNo: String,
        maxPages: Int = 3,
    ): ClassroomLookup {
        var total = 0
        var scanned = 0
        var page = 1
        while (page <= maxPages) {
            val body = postForm(
                CLASSROOM_SEARCH,
                classroomSearchForm(termCode, page, CLASSROOM_PAGE_SIZE),
                s,
            )
            if (looksLikeLoginPage(body)) {
                return ClassroomLookup(sessionLost = true, scanned = scanned)
            }
            val parsed = parseClassroomSearch(body)
            if (parsed.records.isEmpty()) {
                // 一条都没解析出来：是「被踢回登录页 / 不是 JSON」，还是「真没有教室」
                return ClassroomLookup(
                    totalCount = parsed.totalCount,
                    scanned = scanned,
                    sessionLost = !body.trimStart().startsWith("["),
                )
            }
            total = parsed.totalCount
            scanned += parsed.records.size
            pickClassroom(parsed.records, buildingName, roomNo)?.let {
                return ClassroomLookup(info = it, totalCount = total, scanned = scanned)
            }
            if (total > 0 && scanned >= total) break
            page++
        }
        return ClassroomLookup(totalCount = total, scanned = scanned)
    }

    /**
     * 教室检索表单：**只放学期 + 分页**。
     *
     * 为什么不放 `classroomName` / `teachingBuildingName`：这两个名字是从响应字段和页面表头推的，
     * 没实测过是否被接受。传错字段最坏是被忽略（无害），但会让人误以为「过滤生效了」——
     * 干脆不传，过滤全放在客户端，口径单一。
     */
    internal fun classroomSearchForm(
        termCode: String,
        pageNum: Int,
        pageSize: Int,
    ): List<Pair<String, String>> = listOf(
        "executiveEducationPlanNumber" to termCode.trim(),
        "pageNum" to pageNum.coerceAtLeast(1).toString(),
        "pageSize" to pageSize.coerceIn(1, 500).toString(),
    )

    /**
     * 一位教师某学期的课表 —— **两步**（2026-11-14 实测，见 `docs/URP-学生端接口清单.md` §6.2）。
     *
     * ① `POST .../teacherCurriculum/searchCurriculumInfoLo`（表单四字段）只是回执，
     *   **必须发**：不发它，② 拿不到数据（和考试接口的预热请求同一个机理）。
     * ② `GET .../teacherCurriculum/searchCurriculumInfo/callback?planCode=&teacherNum=`
     *   才是数据，返回数组套数组 `[[{slot}…]]`。
     *
     * @param planCode 学期号（`2026-2027-1-1`），由 `HomeModel.currentTermCode` 给
     * @param planName 人话学期名（`2026-2027学年秋`），由 `HomeModel.termLabel` 之类给；
     *   这一步只回传给我们看，不参与定位
     */
    suspend fun fetchTeacherSchedule(
        s: UrpClient.Session,
        teacherNo: String,
        teacherName: String,
        planCode: String,
        planName: String,
    ): TeacherScheduleResult {
        postForm(
            TEACHER_SCHEDULE_LO,
            teacherScheduleLoForm(teacherNo, teacherName, planCode, planName),
            s,
        )
        val body = get(teacherSchedulePath(planCode, teacherNo), s)
        // 被踢回登录页 / 只回了 HTML：这是「没拿到」，不是「没排课」
        if (looksLikeLoginPage(body) || !body.trimStart().startsWith("[")) {
            return TeacherScheduleResult(sessionLost = true)
        }
        return TeacherScheduleResult(schedule = parseTeacherSchedule(body))
    }

    /** 第一步的表单（字段名/顺序按实测 body 原样） */
    internal fun teacherScheduleLoForm(
        teacherNo: String,
        teacherName: String,
        planCode: String,
        planName: String,
    ): List<Pair<String, String>> = listOf(
        "teacherNum" to teacherNo.trim(),
        "teacherName" to teacherName.trim(),
        "planCode" to planCode.trim(),
        "planName" to planName.trim(),
    )

    /** 第二步的路径（两个查询参数，值先 URL 编码） */
    internal fun teacherSchedulePath(planCode: String, teacherNo: String): String {
        val pc = java.net.URLEncoder.encode(planCode.trim(), "UTF-8")
        val tn = java.net.URLEncoder.encode(teacherNo.trim(), "UTF-8")
        return "$TEACHER_SCHEDULE_CB?planCode=$pc&teacherNum=$tn"
    }

    // ── 纯解析（单测直接打这些，用真机 payload 锁 schema）──────

    /** 学籍页是服务端渲染的 `profile-info-name` / `profile-info-value` 对，全量收下 */
    internal fun parseProfile(html: String): StudentProfile {
        val re = Regex(
            """class="profile-info-name">\s*(.*?)\s*</div>\s*<div class="profile-info-value[^"]*"[^>]*>\s*(.*?)\s*</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val fields = re.findAll(html).map { m ->
            ProfileField(
                label = clean(m.groupValues[1]),
                value = clean(m.groupValues[2]),
            )
        }.filter { it.label.isNotBlank() }.toList()
        // 同名标签只保留第一个（页面里「备注」这类会重复出现）
        val seen = LinkedHashMap<String, ProfileField>()
        fields.forEach { seen.putIfAbsent(it.label, it) }
        return StudentProfile(seen.values.toList())
    }

    internal fun parseCreditProgress(text: String): CreditProgress {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return CreditProgress()
        return CreditProgress(
            planName = str(o, "famc"),
            planNo = str(o, "planNum"),
            requiredXf = num(o, "xqzxf"),
            earnedXf = num(o, "ydhxf"),
            passedCount = num(o, "tgmc").toInt(),
            failedCount = num(o, "wtgmc").toInt(),
        )
    }

    /**
     * 及格成绩 callback。
     *
     * 结构是 `{lnList:[{zxf,tgms,zms,wtgms,cjlx,cjList:[...]}]}`，`cjList` 每条带
     * `gradePointScore`（学校算的绩点）与 `credit`。加权绩点 = Σ(绩点×学分)/Σ学分，
     * **只要有一条缺绩点或学分，就不给总数**（见 [ScoreStats.weightedGpa] 的注释）。
     */
    internal fun parseScoreStats(text: String): ScoreStats {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return ScoreStats()
        val list = root["lnList"] as? JsonArray ?: return ScoreStats()

        var creditSum = 0.0
        var gpaWeighted = 0.0
        var gpaCredit = 0.0
        var passed = 0
        var total = 0
        var failed = 0
        var anyGpa = false
        var allHaveGpa = true
        val terms = mutableListOf<TermScore>()

        list.filterIsInstance<JsonObject>().forEach { term ->
            val tCredit = num(term, "zxf")
            val tPassed = num(term, "tgms").toInt()
            val tTotal = num(term, "zms").toInt()
            val tFailed = num(term, "wtgms").toInt()
            creditSum += tCredit
            passed += tPassed
            total += tTotal
            failed += tFailed
            val title = str(term, "cjlx").ifBlank { str(term, "zxjxjhh") }
            if (title.isNotBlank() || tTotal > 0) {
                terms += TermScore(title, tCredit, tPassed, tTotal, tFailed)
            }

            (term["cjList"] as? JsonArray)?.filterIsInstance<JsonObject>()?.forEach { c ->
                val cr = num(c, "credit")
                val gp = num(c, "gradePointScore")
                // 空行（课程名/学分/绩点全无）不是「缺绩点」，跳过，别让它把整张表作废
                val blank = cr <= 0.0 && gp <= 0.0 && str(c, "courseName").isBlank()
                when {
                    blank -> Unit
                    cr > 0.0 && gp > 0.0 -> {
                        gpaWeighted += gp * cr
                        gpaCredit += cr
                        anyGpa = true
                    }
                    // 有学分没绩点（或反过来）：这门课算不进加权，算出来的均值就只覆盖了一部分课程。
                    // 那是个「看着像真的」的假数字，比留空更糟 —— 整表判缺，界面显示「绩点暂缺」。
                    else -> allHaveGpa = false
                }
            }
        }

        val gpa = if (allHaveGpa && anyGpa && gpaCredit > 0.0) gpaWeighted / gpaCredit else null
        return ScoreStats(
            weightedGpa = gpa,
            gpaCreditSum = gpaCredit,
            creditSum = creditSum,
            passed = passed,
            total = total,
            failed = failed,
            terms = terms,
        )
    }

    internal fun parseCampuses(text: String): List<CampusOption> =
        runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?.filterIsInstance<JsonObject>()
            ?.map { CampusOption(str(it, "campusNumber"), str(it, "campusName")) }
            ?.filter { it.name.isNotBlank() }
            .orEmpty()

    internal fun parseBuildings(text: String): List<BuildingOption> =
        runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?.filterIsInstance<JsonObject>()
            ?.map { o ->
                val id = o["id"] as? JsonObject
                BuildingOption(
                    number = str(id, "teachingBuildingNumber"),
                    name = str(o, "teachingBuildingName"),
                )
            }
            ?.filter { it.name.isNotBlank() }
            .orEmpty()

    /**
     * 空闲教室 payload。
     *
     * ```jsonc
     * {"curxqm":"主校","spareroomObjList":[
     *   {"acmcBuilding":"2","acmcBuildingName":"第二教学楼",
     *    "claroom":[{"classroom":"719","classNumberOfSeats":"72","szlc":"7"}]}]}
     * ```
     *
     * @param buildingNo 传了就只留这栋楼。**以前这个参数是死的**（函数收了但没用），
     *   所以「接口在全部教学楼时会把全校楼混在一起」这条注释其实一直没生效；
     *   现在按 `acmcBuilding` 真过滤，楼号缺失的条目保留（不猜）。
     */
    internal fun parseFreeRooms(text: String, buildingNo: String = ""): FreeRooms {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return FreeRooms()
        val arr = root["spareroomObjList"] as? JsonArray ?: return FreeRooms()
        val buildings = arr.filterIsInstance<JsonObject>().map { o ->
            val rooms = (o["claroom"] as? JsonArray)?.filterIsInstance<JsonObject>()?.map { r ->
                RoomOption(
                    room = str(r, "classroom"),
                    seats = num(r, "classNumberOfSeats").toInt(),
                    floor = str(r, "szlc"),
                )
            }.orEmpty()
            FreeBuilding(
                name = str(o, "acmcBuildingName"),
                rooms = rooms,
                number = str(o, "acmcBuilding"),
            )
        }
        val keep = if (buildingNo.isBlank()) buildings else buildings.filter {
            it.number.isBlank() || it.number == buildingNo.trim()
        }
        return FreeRooms(
            campusName = str(root, "curxqm"),
            buildings = keep.filter { it.rooms.isNotEmpty() },
        )
    }

    /**
     * 两步流的结局判定（纯函数：喂两份响应体，返回结局 + 解析结果）。
     *
     * 判据都是**响应里看得见的东西**，不猜：
     * - 任一响应是登录页 / 空 / 不是 JSON → [FreeRoomsIssue.NO_SESSION]（会话没了或不在校园网）；
     * - 返回的 `acmcBuilding` 跟请求的楼号不一致 → [FreeRoomsIssue.PICK_LOST]（选楼没生效）；
     * - JSON 正常、就是没有教室 → [FreeRoomsIssue.NOTHING_FREE]（真的空，不是故障）。
     */
    internal fun judgeFreeRooms(
        pickBody: String,
        roomsBody: String,
        buildingNo: String,
    ): FreeRooms {
        val parsed = parseFreeRooms(roomsBody, buildingNo)
        val all = parseFreeRooms(roomsBody) // 不按楼过滤，用于发现「答的是别的楼」
        val issue = when {
            looksLikeLoginPage(pickBody) || looksLikeLoginPage(roomsBody) -> FreeRoomsIssue.NO_SESSION
            roomsBody.isBlank() -> FreeRoomsIssue.NO_SESSION
            !roomsBody.trimStart().startsWith("{") -> FreeRoomsIssue.NO_SESSION
            buildingNo.isNotBlank() &&
                all.buildings.any { it.number.isNotBlank() && it.number != buildingNo.trim() } ->
                FreeRoomsIssue.PICK_LOST
            parsed.roomCount > 0 -> FreeRoomsIssue.OK
            else -> FreeRoomsIssue.NOTHING_FREE
        }
        return parsed.copy(issue = issue)
    }

    /**
     * 响应是不是「被踢回登录页」了。
     *
     * URP 的 CAS 登录页有几个稳定特征（`tpass/login`、`loginForm`、`execution`），
     * 正常的数据页不会同时出现这些；宁可保守也不要把正常页误判成登录页。
     */
    internal fun looksLikeLoginPage(text: String): Boolean {
        if (text.isBlank()) return false
        val loginForm = text.contains("loginForm") || text.contains("id=\"lt\"") ||
            text.contains("name=\"lt\"")
        val cas = text.contains("tpass/login") || text.contains("pass.syau.edu.cn")
        return loginForm || cas
    }

    /**
     * 教师课表搜索结果。
     *
     * 返回是 `[{pageContext:{totalCount},records:[…]}]`，每条 `id.teacherNumber` 是工号。
     *
     * @param termCode 传了就**只保留这个学期的记录**（比对每条记录自带的 `zxjxjhh`）。
     *   记录里学期号缺失的保留 —— 宁可多一条，也不因为字段缺失把数据丢掉。
     */
    internal fun parseTeachers(text: String, termCode: String = ""): List<TeacherHit> {
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return emptyList()
        val prefix = termPrefix(termCode)
        val records = arr.filterIsInstance<JsonObject>()
            .flatMap { (it["records"] as? JsonArray)?.toList().orEmpty() }
            .filterIsInstance<JsonObject>()
        val hits = records.map { o ->
            val id = o["id"] as? JsonObject
            TeacherHit(
                name = str(o, "teacherName"),
                number = str(id, "teacherNumber"),
                dept = str(o, "departmentName"),
                title = str(o, "professionalTitle"),
                sex = str(o, "sex"),
                term = str(o, "zxjxjhh").ifBlank { str(id, "executiveEducationPlanNumber") },
            )
        }.filter { it.name.isNotBlank() }
            .filter { prefix.isBlank() || it.term.isBlank() || it.term.startsWith(prefix) }
        return dedupeTeachers(hits)
    }

    /**
     * 按工号去重（保留第一条）。
     *
     * 这一步是票 01 的核心修复：服务器在**不限定学期**时会跨学期返回同一位老师的多条记录，
     * 而列表用「工号」当 key → 主线程抛 `Key "1995500023" was already used`，App 直接闪退。
     * 去重放在解析层（纯函数，可单测），UI 只负责渲染。
     *
     * 工号为空串的记录无法判定「是不是同一个人」，按原顺序**全部保留**，不合并、不猜。
     */
    internal fun dedupeTeachers(hits: List<TeacherHit>): List<TeacherHit> {
        val seen = HashSet<String>()
        return hits.filter { it.number.isBlank() || seen.add(it.number) }
    }

    /**
     * 学期号的「前三段」：`2026-2027-1-1` / `2026-2027-1` → `2026-2027-1`。
     *
     * 为什么不做成四段全等：第四段是什么（实测样本只有 `-1`）尚未在春学期验证，
     * 按前三段比对既认得出「是不是同一学期」，又不会被那个未验证的尾巴挡住。
     * 少于三段（空串、乱填）返回空串 = 不做学期过滤。
     */
    internal fun termPrefix(termCode: String): String {
        val parts = termCode.trim().split('-').filter { it.isNotBlank() }
        if (parts.size < 3) return ""
        return parts.take(3).joinToString("-")
    }

    /**
     * 解析教室检索的响应：
     * `[{"xqzs":"21","pageData":{"pageContext":{"totalCount":295},"pageNum":1,
     *    "pageSize":30,"records":[{…}]}}]`。
     *
     * 容错口径：
     * - `xqzs` 含义不明（样本里是 `"21"`，猜不出是校区分组还是院系编码）→ **不用它**；
     * - 顶层不是数组（被踢回登录页时是 HTML，或错误时是对象）→ 返回空页，不抛异常；
     * - `teachingBuildingName` / `classroomNumber` 是按页面表头 + 同族命名推的，所以带了别名兜底。
     */
    internal fun parseClassroomSearch(text: String): ClassroomPage {
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return ClassroomPage()
        arr.filterIsInstance<JsonObject>().forEach { wrapper ->
            val pd = wrapper["pageData"] as? JsonObject ?: return@forEach
            val ctx = pd["pageContext"] as? JsonObject
            val records = (pd["records"] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.map { o ->
                    ClassroomInfo(
                        campusName = str(o, "campusName"),
                        buildingName = str(o, "teachingBuildingName").ifBlank {
                            str(o, "teachingBuilding")
                        },
                        roomNo = str(o, "classroomNumber").ifBlank { str(o, "classroom") },
                        roomName = str(o, "classroomName"),
                        seats = num(o, "classNumberOfSeats").toInt(),
                        type = str(o, "classroomTypeDirections"),
                        dept = str(o, "departmentName"),
                        term = str(o, "executiveEducationPlanNumber").ifBlank {
                            str(o, "zxjxjhh")
                        },
                    )
                }
                .orEmpty()
                .filter { !it.isEmpty }
            val pageNo = num(pd, "pageNum").toInt()
            return ClassroomPage(
                totalCount = num(ctx, "totalCount").toInt(),
                pageNum = if (pageNo <= 0) 1 else pageNo,
                records = records,
            )
        }
        return ClassroomPage()
    }

    /**
     * 从一页记录里挑出「就是这一间」的那条（纯函数）。
     *
     * 匹配分三档，**先严后宽**，任何一档命中多条就返回 null（当作「查不到」）——
     * 宁可说查不到，也不要把隔壁教室的座位数安到这间头上。
     *
     * 1. 楼名对得上 + 教室号完全相等（最可靠）
     * 2. 楼名对得上 + 教室名完全相等
     * 3. 楼名对得上 + 教室名以房号结尾 —— 如房号 `2机房` ↔ 教室名 `5楼2机房`；
     *    要求前一位不是数字，免得 `719` 匹配到 `1719`
     *
     * 三档都要求「楼名对得上」（记录里楼名为空时视为对得上）。**故意不跨楼匹配**：
     * 两个楼都有 719 时，宁可查不到，也不能把别栋楼的 719 的座位数显示成这间的。
     */
    internal fun pickClassroom(
        records: List<ClassroomInfo>,
        buildingName: String,
        roomNo: String,
    ): ClassroomInfo? {
        val room = roomNo.trim()
        if (room.isBlank() || records.isEmpty()) return null
        val building = buildingName.trim()
        fun sameBuilding(r: ClassroomInfo): Boolean =
            building.isBlank() || r.buildingName.isBlank() || r.buildingName == building

        fun endsWithRoom(name: String): Boolean {
            if (!name.endsWith(room)) return false
            if (name.length == room.length) return true
            return !name[name.length - room.length - 1].isDigit()
        }

        val tiers = listOf(
            records.filter { sameBuilding(it) && it.roomNo == room },
            records.filter { sameBuilding(it) && it.roomName == room },
            records.filter { sameBuilding(it) && endsWithRoom(it.roomName) },
        )
        return tiers.firstOrNull { it.isNotEmpty() }?.singleOrNull()
    }

    /**
     * 解析教师课表：`[[{slot}, {slot}, …]]`（**数组套数组**，取 `[0]`）。
     *
     * 关键点：
     * - 周次只认 `id.skzc` 的 **24 位位图**（借 `UrpClient.decodeWeekBitmap`，不另写一份），
     *   `zcsm`（`1-3,5,8-15周上`）只当对照文案存着 —— 文字周次解析过就会错在边界上；
     * - `skxq` 有两个来源：`id` 里是数字，外层是字符串，**以数字优先**；
     * - 空数组 / `[[]]` / 非 JSON 一律返回空课表，不抛异常（没排课和解析失败由调用方区分）。
     */
    internal fun parseTeacherSchedule(text: String): TeacherSchedule {
        val root = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return TeacherSchedule()
        // 外层再套一层数组：[[...]]；也容忍直接给 [...] 的形态
        val list: List<JsonElement> = when {
            root.isNotEmpty() && root.firstOrNull() is JsonArray ->
                (root.first() as JsonArray).toList()
            else -> root.toList()
        }
        val slots = list.filterIsInstance<JsonObject>().map { o ->
            val id = o["id"] as? JsonObject
            val weekday = num(id, "skxq").toInt().let {
                if (it in 1..7) it else str(o, "skxq").toIntOrNull() ?: 0
            }
            TeacherSlot(
                courseName = str(o, "kcm"),
                courseNo = str(id, "kch"),
                seqNo = str(id, "kxh"),
                weekday = weekday,
                startSection = num(id, "skjc").toInt(),
                span = num(o, "cxjc").toInt().let { if (it <= 0) 1 else it },
                weeks = UrpClient.decodeWeekBitmap(str(id, "skzc")),
                weekText = str(o, "zcsm"),
                campus = str(o, "xqm"),
                building = str(o, "jxlm"),
                room = str(o, "jasm"),
                credit = str(o, "xf"),
                examType = str(o, "kslxmc"),
                className = str(o, "bjh"),
                students = num(o, "xss").toInt(),
                college = str(o, "kkxsm"),
                term = str(id, "zxjxjhh"),
            )
        }.filter { !it.isEmpty }
        return TeacherSchedule(
            teacherNo = str((list.firstOrNull { it is JsonObject } as? JsonObject)?.get("id") as? JsonObject, "jsh"),
            teacherName = str((list.firstOrNull { it is JsonObject } as? JsonObject), "jsm"),
            term = slots.firstOrNull { it.term.isNotBlank() }?.term.orEmpty(),
            slots = slots,
        )
    }

    // ── HTTP ──────────────────────────────────────────────

    private fun get(path: String, s: UrpClient.Session): String {
        DebugLog.request("HOME", "GET", path)
        val conn = URL(URP + path).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            applyCommon(conn, s, path, "GET")
            // 非 2xx 不往下走：500 的错误页曾被解析成「空列表」，界面就成了「没有数据」
            read(conn, path, s).requireOk("GET " + path)
        } catch (e: Exception) {
            DebugLog.error("HOME", "GET ${path.take(80)} 失败：${e.javaClass.simpleName} ${e.message}")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun postForm(path: String, form: List<Pair<String, String>>, s: UrpClient.Session): String {
        DebugLog.request("HOME", "POST", path)
        val body = form.joinToString("&") {
            "${java.net.URLEncoder.encode(it.first, "UTF-8")}=${java.net.URLEncoder.encode(it.second, "UTF-8")}"
        }
        val conn = URL(URP + path).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            applyCommon(conn, s, path, "POST")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            read(conn, path, s).requireOk("POST " + path)
        } catch (e: Exception) {
            DebugLog.error("HOME", "POST ${path.take(80)} 失败：${e.javaClass.simpleName} ${e.message}")
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 头一律交给 [UrpHeaders] 统一设（含 `Accept` / `Referer` / `Sec-Fetch-*` / `Origin`），
     * 这里只管超时、跳转与 `Cookie`。
     */
    private fun applyCommon(conn: HttpURLConnection, s: UrpClient.Session, path: String, method: String) {
        conn.connectTimeout = 12_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        UrpThrottle.await(conn.url.host)
        UrpHeaders.apply(conn, path = path, method = method, logTag = "HOME")
        val cookie = s.cookieHeader()
        if (cookie.isNotBlank()) conn.setRequestProperty("Cookie", cookie)
    }

    private fun read(conn: HttpURLConnection, path: String, s: UrpClient.Session): UrpResponse {
        val code = conn.responseCode
        // 收下 Set-Cookie：空闲教室是**会话粘滞**的，选楼状态存在服务器会话里。
        // 若服务器在这一步换了 JSESSIONID 而我们不更新 cookie，下一次 GET 就回到「没选楼」→ 恒空。
        runCatching { s.absorb(conn) }
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        DebugLog.http("HOME", if (conn.requestMethod == "POST") "POST" else "GET", path, code, body.length, preview = body)
        // 跳转是跟了的（applyCommon 里 instanceFollowRedirects = true），所以登录页会以 200 落地，
        // 由 UrpResponse.failure 里的 isLoginPage 认出来 —— 不用在这里单独看 Location。
        return UrpResponse(code, body, path)
    }

    // ── 教学资源：课程 / 班级 / 教室课表（2026-11-28 真机登录态实测）──────
    //
    // 四条回调（course / class / classroom / teacher）返回的是**同一个 schema**：
    // [[{slot}…]]，字段名完全一致（id.skzc 周位图、kcm、skxq、skjc、cxjc…），
    // 所以课表解析只有一份 [parseTeacherSchedule]，这里不再复制。
    // 差别只在查询参数名（实测见 docs/URP-学生端接口清单.md §6.7）：
    //   课程 planCode + courseCode + courseSequenceCode
    //   班级 planCode（= 该班方案号）+ classCode（= 班级名）
    //   教室 planNumber + campusNumber + teachingBuildingNumber + classroomNumber（楼号-房号）
    internal const val COURSE_SEARCH = "/student/teachingResources/courseCurriculum/search"
    internal const val COURSE_SLOTS_CB =
        "/student/teachingResources/courseCurriculum/searchCurriculum/callback"
    internal const val CLASS_SEARCH = "/student/teachingResources/classCurriculum/search"
    internal const val CLASS_SLOTS_CB =
        "/student/teachingResources/classCurriculum/searchCurriculumInfo/callback"
    internal const val CLASSROOM_SLOTS_CB =
        "/student/teachingResources/classroomCurriculum/searchCurriculum/callback"

    /**
     * 课程检索命中的一门课。
     *
     * 服务器键名是**全大写**（KCM / KCH / XF …），与本文件其它接口的驼峰键不同族 ——
     * 解析用大小写不敏感取值（[ciStr]），日后学校哪天改回驼峰也不用再接一次。
     */
    @Serializable
    data class CourseHit(
        val name: String = "",
        val courseNo: String = "",
        val seqNo: String = "",
        val deptName: String = "",
        val teacherName: String = "",
        val credit: String = "",
        val examType: String = "",
        val category: String = "",
        val term: String = "",
        val termName: String = "",
    ) {
        val isEmpty: Boolean get() = name.isBlank()

        /** 901020203-04：课程号-课序号，教务列表里就是这么成对出现的 */
        val code: String
            get() = listOf(courseNo, seqNo).filter { it.isNotBlank() }.joinToString("-")
    }

    @Serializable
    data class CoursePage(
        val totalCount: Int = 0,
        val hits: List<CourseHit> = emptyList(),
        val sessionLost: Boolean = false,
    )

    /** 班级检索命中的行政班（12动医1 这种） */
    @Serializable
    data class ClassHit(
        val className: String = "",
        val classNo: String = "",
        val deptName: String = "",
        val grade: String = "",
        val subjectName: String = "",
        /** 查这个班的课表要用的方案号（2016-2017-1-1 这种，**不是**当前学期号） */
        val planNo: String = "",
        val planName: String = "",
        val term: String = "",
    ) {
        val isEmpty: Boolean get() = className.isBlank()
    }

    @Serializable
    data class ClassPage(
        val totalCount: Int = 0,
        val hits: List<ClassHit> = emptyList(),
        val sessionLost: Boolean = false,
    )

    /**
     * 按课程名/课程号查课程课表条目。
     *
     * 学期必传（不带会跨学期重复，与教师课表同一个坑）。
     *
     * ## kcm 只认课名、kch 只认课号，两个都填是「且」
     * 2026-11-28 在真机页面里实测（同一个学期、同样表头）：
     * * `kcm=不动产` → 2 条（不动产估价 / 901040302）
     * * `kch=901020203` → 5 条（观赏园艺学（Ⅱ）…）
     * * `kcm=不动产&kch=不动产` → **0 条**，`kcm=X304101&kch=X304101` 同样 0 条
     * * 两个都空 → 2189 条（整学期课程表）
     *
     * 所以一个输入框要按关键字形态选字段：先按 [looksLikeCourseNo] 猜，猜错
     * （0 条）就换另一边再发一次。之前两个都塞 kcm，输课程号永远查不到。
     */
    suspend fun searchCourses(
        s: UrpClient.Session,
        termCode: String,
        keyword: String,
        pageNum: Int = 1,
        pageSize: Int = 40,
    ): CoursePage {
        val byCode = looksLikeCourseNo(keyword)
        val first = postForm(
            COURSE_SEARCH,
            courseSearchForm(termCode, keyword, pageNum, pageSize, byCode),
            s,
        )
        if (looksLikeLoginPage(first) || !first.trimStart().startsWith("{")) {
            return CoursePage(sessionLost = true)
        }
        val page = parseCourses(first)
        if (keyword.isBlank() || page.totalCount > 0 || page.hits.isNotEmpty()) return page
        // 猜错字段就换另一边再试一次：kcm 与 kch 是「且」，填错必然 0 条
        val second = postForm(
            COURSE_SEARCH,
            courseSearchForm(termCode, keyword, pageNum, pageSize, !byCode),
            s,
        )
        if (looksLikeLoginPage(second) || !second.trimStart().startsWith("{")) return page
        return parseCourses(second)
    }

    /**
     * 关键字像不像课程号：含汉字的一律当课名（课名里也可能有阿拉伯数字），
     * 纯 ASCII 且带数字的当课号（X304101 / 901020203）。
     */
    internal fun looksLikeCourseNo(raw: String): Boolean {
        val k = raw.trim()
        if (k.isEmpty()) return false
        if (k.any { it.code > 0x2E80 }) return false
        return k.any { it.isDigit() }
    }

    /**
     * 课程检索表单（字段名按实测 body 原样）。
     *
     * [byCode] 为真时关键字进 kch（课程号），否则进 kcm（课程名）——
     * 两边同时填会被服务器当成「且」，结果恒为 0 条。
     */
    internal fun courseSearchForm(
        termCode: String,
        keyword: String,
        pageNum: Int,
        pageSize: Int,
        byCode: Boolean = looksLikeCourseNo(keyword),
    ): List<Pair<String, String>> = listOf(
        "zxjxjhh" to termCode.trim(),
        "kkxsh" to "",
        "kcm" to if (byCode) "" else keyword.trim(),
        "kch" to if (byCode) keyword.trim() else "",
        "kxh" to "",
        "kclb" to "",
        "pageNum" to pageNum.coerceAtLeast(1).toString(),
        "pageSize" to pageSize.coerceIn(1, 200).toString(),
    )

    /**
     * 按班级名查班级课表条目。
     *
     * ## 哪个键才真的过滤（2026-11-28 真机页面内实测）
     * 表单长这样：`param_value` / `executiveEducationPlanNum` / `yearNum` / `departmentNum`
     * / `subjectNum` / `classNum`。实测结论：
     *
     * | 表单 | totalCount |
     * |---|---|
     * | 全空 | 8799 |
     * | `param_value=12` / `=园艺` / `=12动医1` | 8799（**完全无效**） |
     * | `classNum=12动医1` | 1（**精确匹配**） |
     * | `classNum=12` / `12动医` / `%12%` / `12动医_` | 0（不认部分匹配、不认通配符） |
     * | `yearNum=2012` | 3（该年级所有班） |
     *
     * 所以关键字进 `classNum`（要写全名），四位数（2012）当年级进 `yearNum`。
     * `param_value` 照旧带上但留空 —— 它在这台部署里是死字段。
     */
    suspend fun searchClasses(
        s: UrpClient.Session,
        keyword: String,
        yearNum: String = "",
        departmentNum: String = "",
        pageNum: Int = 1,
        pageSize: Int = 40,
    ): ClassPage {
        val body = postForm(
            CLASS_SEARCH,
            classSearchForm(keyword, yearNum, departmentNum, pageNum, pageSize),
            s,
        )
        if (looksLikeLoginPage(body) || !body.trimStart().startsWith("[")) {
            return ClassPage(sessionLost = true)
        }
        return parseClasses(body)
    }

    /** 班级检索表单（字段名按实测 body 原样） */
    internal fun classSearchForm(
        keyword: String,
        yearNum: String,
        departmentNum: String,
        pageNum: Int,
        pageSize: Int,
    ): List<Pair<String, String>> {
        val k = keyword.trim()
        // 四位数当年级（2012 → 该年级 3 个班）；其余当班级全名（精确匹配）
        val asGrade = yearNum.trim().isEmpty() && k.length == 4 && k.all { it.isDigit() }
        return listOf(
            "param_value" to "",
            "executiveEducationPlanNum" to "",
            "yearNum" to if (asGrade) k else yearNum.trim(),
            "departmentNum" to departmentNum.trim(),
            "subjectNum" to "",
            "classNum" to if (asGrade) "" else k,
            "pageNum" to pageNum.coerceAtLeast(1).toString(),
            "pageSize" to pageSize.coerceIn(1, 200).toString(),
        )
    }

    /** 课程课表：一门课（课程号 + 课序号）在某个学期的排课 */
    suspend fun fetchCourseSchedule(
        s: UrpClient.Session,
        planCode: String,
        courseNo: String,
        seqNo: String,
    ): TeacherScheduleResult = fetchSlots(s, courseSlotsPath(planCode, courseNo, seqNo))

    /** 班级课表：planCode 用该班记录自带的方案号（2016-2017-1-1），不是当前学期号 */
    suspend fun fetchClassSchedule(
        s: UrpClient.Session,
        planNo: String,
        className: String,
    ): TeacherScheduleResult = fetchSlots(s, classSlotsPath(planNo, className))

    /**
     * 教室课表：classroomNumber 必须是**楼号-房号**全称（2-601）。
     *
     * 只传 601 时服务器回 [[]]（空课表），看起来像「这间教室没课」—— 实测确认必须带前缀。
     */
    suspend fun fetchClassroomSchedule(
        s: UrpClient.Session,
        planNumber: String,
        campusNo: String,
        buildingNo: String,
        roomNo: String,
    ): TeacherScheduleResult = fetchSlots(
        s,
        classroomSlotsPath(planNumber, campusNo, buildingNo, roomNo),
    )

    /** 三条课表回调共用的取数口径：被踢回登录页 / 非 JSON = 「没拿到」，不是「没排课」 */
    private suspend fun fetchSlots(s: UrpClient.Session, path: String): TeacherScheduleResult {
        val body = get(path, s)
        if (looksLikeLoginPage(body) || !body.trimStart().startsWith("[")) {
            return TeacherScheduleResult(sessionLost = true)
        }
        return TeacherScheduleResult(schedule = parseTeacherSchedule(body))
    }

    internal fun courseSlotsPath(planCode: String, courseNo: String, seqNo: String): String =
        COURSE_SLOTS_CB + "?planCode=" + enc(planCode) +
            "&courseCode=" + enc(courseNo) + "&courseSequenceCode=" + enc(seqNo)

    internal fun classSlotsPath(planNo: String, className: String): String =
        CLASS_SLOTS_CB + "?planCode=" + enc(planNo) + "&classCode=" + enc(className)

    internal fun classroomSlotsPath(
        planNumber: String,
        campusNo: String,
        buildingNo: String,
        roomNo: String,
    ): String = CLASSROOM_SLOTS_CB + "?planNumber=" + enc(planNumber) +
        "&campusNumber=" + enc(campusNo) +
        "&teachingBuildingNumber=" + enc(buildingNo) +
        "&classroomNumber=" + enc(buildingNo + "-" + roomNo)

    /**
     * 解析课程检索：{pageSize,pageNum,pageContext:{totalCount},records:[{KCM,KCH,…}]}。
     *
     * 顶层是对象（与班级检索的数组套对象不同族）；拿不到 JSON 时返回空页而不是抛异常。
     */
    internal fun parseCourses(text: String): CoursePage {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return CoursePage()
        val total = (root["pageContext"] as? JsonObject)?.let {
            ciStr(it, "totalCount").toIntOrNull()
        } ?: 0
        val hits = (root["records"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .map { o ->
                CourseHit(
                    name = ciStr(o, "KCM"),
                    courseNo = ciStr(o, "KCH"),
                    seqNo = ciStr(o, "KXH"),
                    deptName = ciStr(o, "KKXSM"),
                    teacherName = ciStr(o, "JSM"),
                    credit = ciStr(o, "XF"),
                    examType = ciStr(o, "KSLXMC"),
                    category = ciStr(o, "KCLBMC"),
                    term = ciStr(o, "ZXJXJHH"),
                    termName = ciStr(o, "ZXJXJHM"),
                )
            }
            .filter { !it.isEmpty }
        return CoursePage(totalCount = total, hits = hits)
    }

    /**
     * 解析班级检索：[{pageContext:{totalCount},records:[{className, id:{classNum,
     * executiveEducationPlanNumber}, …}]}] —— **数组套对象**，与课程检索不同族。
     */
    internal fun parseClasses(text: String): ClassPage {
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return ClassPage()
        val wrappers = arr.filterIsInstance<JsonObject>()
        val total = wrappers.firstNotNullOfOrNull { w ->
            (w["pageContext"] as? JsonObject)?.let { ciStr(it, "totalCount").toIntOrNull() }
        } ?: 0
        val hits = wrappers
            .flatMap { (it["records"] as? JsonArray)?.toList().orEmpty() }
            .filterIsInstance<JsonObject>()
            .map { o ->
                val id = o["id"] as? JsonObject
                ClassHit(
                    className = ciStr(o, "className"),
                    classNo = ciStr(id, "classNum"),
                    deptName = ciStr(o, "departmentName"),
                    grade = ciStr(o, "admissionGrade"),
                    subjectName = ciStr(o, "subjectName"),
                    planNo = ciStr(id, "executiveEducationPlanNumber"),
                    planName = ciStr(o, "executiveEducationPlanName"),
                    term = ciStr(o, "zxjxjhh"),
                )
            }
            .filter { !it.isEmpty }
        return ClassPage(totalCount = total, hits = hits)
    }

    /** 大小写不敏感取值：课程检索的键是全大写（KCM），其余接口是驼峰 */
    private fun ciStr(o: JsonObject?, key: String): String {
        if (o == null) return ""
        val actual = o.keys.firstOrNull { it.equals(key, true) } ?: return ""
        val v = o[actual] ?: return ""
        if (v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content?.trim().orEmpty()
    }

    /** 查询参数的 URL 编码（课名/班级名是中文，不编码服务器会 400） */
    private fun enc(v: String): String =
        java.net.URLEncoder.encode(v.trim(), "UTF-8")
    // ── 工具 ──────────────────────────────────────────────

    private fun clean(s: String): String =
        s.replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    private fun str(o: JsonObject?, key: String): String {
        if (o == null) return ""
        val v = o[key] ?: return ""
        if (v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content?.trim().orEmpty()
    }

    private fun num(o: JsonObject?, key: String): Double {
        val t = str(o, key)
        if (t.isBlank() || t == "null") return 0.0
        return t.toDoubleOrNull() ?: 0.0
    }

}

