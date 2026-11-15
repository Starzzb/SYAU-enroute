package cn.dkarbon.enroute.ui.home

import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.FreeBuilding
import cn.dkarbon.enroute.data.UrpHomeApi.RoomOption
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import cn.dkarbon.enroute.data.UrpHomeApi.TeacherHit
import cn.dkarbon.enroute.ui.timetable.BigSession
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 学业主页的**纯函数**派生层。
 *
 * 为什么不写在 Composable 里：这一层的每个结论都是「给人看的数字」——
 * 剩几天考试、绩点是多少、学分差多少。放在纯函数里才能用 JVM 单测钉死，
 * 而不是靠上真机看一眼「大概对」。
 *
 * 三条文案纪律（沿用整个项目）：
 * 1. 算不出 → 返回 null，由 UI 说「暂缺」，**绝不填 0 或占位符**；
 * 2. 已过去 → 明确说「已结束」，不假装还在倒计时；
 * 3. 单位与来源写在文案里（「按学校绩点」「学校端统计」），而不是让人猜。
 */
object HomeModel {

    /** 解析 `2026-11-15`；失败返回 null（不猜日期） */
    fun dateOf(iso: String): LocalDate? =
        runCatching { LocalDate.parse(iso.trim()) }.getOrElse {
            if (it is DateTimeParseException) null else null
        }

    /**
     * 距离某天还有几天（今天 = 0，明天 = 1，已过去 = 负数）。
     * 日期解析不出来 → null。
     */
    fun daysUntil(iso: String, today: LocalDate): Long? =
        dateOf(iso)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }

    /**
     * 倒计时文案。
     *
     * @return 「今天」「明天」「N 天后」「已结束」；日期无效 → null
     */
    fun countdownLabel(iso: String, today: LocalDate): String? {
        val d = daysUntil(iso, today) ?: return null
        return when {
            d < 0 -> "已结束"
            d == 0L -> "今天"
            d == 1L -> "明天"
            else -> "$d 天后"
        }
    }

    /** 下一场还没开始的考试（`date` 空的一律跳过，因为无法判断先后） */
    fun nextExam(exams: List<ExamItem>, today: LocalDate): ExamItem? =
        exams.filter { it.date.isNotBlank() }
            .filter { (daysUntil(it.date, today) ?: -1) >= 0 }
            .minByOrNull { it.date + " " + it.startClock }

    /** 本学期/全部考试里已考完的场次（用于「已考 2 场」这类小结） */
    fun finishedExamCount(exams: List<ExamItem>, today: LocalDate): Int =
        exams.count { (daysUntil(it.date, today) ?: 1) < 0 }

    // ── 身份 ──────────────────────────────────────────────

    /** 头像位用的一个字：取姓名首字；姓名空则空串 */
    fun initialOf(name: String): String = name.trim().take(1)

    /**
     * 主标题下面那行副标题：`24土管1 · 土地资源管理 · 2024级`，缺哪段就少哪段。
     * 一段都没有时返回空串（UI 不显示空标题行）。
     */
    fun profileSubtitle(p: StudentProfile): String {
        val parts = listOf(p.className, p.major, p.grade).filter { it.isNotBlank() }
        return parts.joinToString(" · ")
    }

    /** 身份卡上的标签块：只挑有值的，最多 4 个，避免一屏全是字 */
    fun profileBadges(p: StudentProfile): List<Pair<String, String>> = listOf(
        "学号" to p.studentNo,
        "院系" to p.college,
        "层次" to p.level,
        "入学" to compactDate(p.enrolledAt),
    ).filter { it.second.isNotBlank() }.take(4)

    /** `20240901` / `2024-09-01` → `2024-09` */
    fun compactDate(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 6) return raw.trim()
        return digits.substring(0, 4) + "-" + digits.substring(4, 6)
    }

    // ── 学分进度 ──────────────────────────────────────────

    /** `106 / 165 学分`；需修为 0 时只报已得，不编分母 */
    fun creditLabel(p: CreditProgress): String = when {
        p.requiredXf > 0.0 -> "${trimNum(p.earnedXf)} / ${trimNum(p.requiredXf)} 学分"
        p.earnedXf > 0.0 -> "已得 ${trimNum(p.earnedXf)} 学分"
        else -> "暂缺"
    }

    /** 还差多少学分；需修为 0 → null */
    fun creditRemaining(p: CreditProgress): Double? =
        if (p.requiredXf <= 0.0) null else (p.requiredXf - p.earnedXf).coerceAtLeast(0.0)

    /** 通过门数文案：`通过 55 门 / 挂 0 门`（挂科 0 门时只说通过） */
    fun courseCountLabel(p: CreditProgress): String = when {
        p.passedCount <= 0 && p.failedCount <= 0 -> ""
        p.failedCount > 0 -> "通过 ${p.passedCount} 门 · 挂 ${p.failedCount} 门"
        else -> "通过 ${p.passedCount} 门"
    }

    // ── 成绩 ──────────────────────────────────────────────

    /** 绩点文案。null → 「绩点暂缺」（**不显示 0.00**） */
    fun gpaLabel(s: ScoreStats): String =
        s.weightedGpa?.let { String.format("%.2f", it) } ?: "暂缺"

    /**
     * 绩点的解释行：参与加权的学分范围。
     * 学校给的绩点不是每门都有（考查课常常没有），所以要交代覆盖面，避免误导。
     */
    fun gpaHint(s: ScoreStats): String {
        if (s.weightedGpa == null) return "没有可用的绩点字段"
        val cover = if (s.creditSum > 0.0 && s.gpaCreditSum > 0.0) {
            "覆盖 ${trimNum(s.gpaCreditSum)} / ${trimNum(s.creditSum)} 学分"
        } else {
            "按学校绩点加权"
        }
        return "按学校绩点加权 · $cover"
    }

    /** `通过 13 门 · 未通过 0 门` */
    fun scoreCountLabel(s: ScoreStats): String = when {
        s.total <= 0 && s.passed <= 0 -> ""
        s.failed > 0 -> "通过 ${s.passed} 门 · 未通过 ${s.failed} 门"
        else -> "通过 ${s.passed} 门"
    }

    /**
     * 学期短名：`2024-2025学年秋(两学期)` → `24-25 秋`。
     * 只做「去掉括号说明 + 学年压成两位」，不改语义。
     */
    fun shortTerm(title: String): String {
        val t = title.substringBefore('(').substringBefore('（').trim()
        val m = Regex("""(\d{4})-(\d{4})学年(.*)""").find(t)
        return if (m != null) {
            val a = m.groupValues[1].takeLast(2)
            val b = m.groupValues[2].takeLast(2)
            val tail = m.groupValues[3].trim()
            "$a-$b" + if (tail.isNotBlank()) " $tail" else ""
        } else {
            t
        }
    }

    // ── 空闲教室 ──────────────────────────────────────────

    /**
     * 按楼层分组，楼层从高到低（自习时人一般愿意去低层，但"从高到低"能让高层先被看到——
     * 这里选择**楼层升序**，因为低层离楼梯口近、人多；升序更好选）。
     *
     * 楼层号解析不出来的一律归到「其他」组，不丢教室。
     */
    fun roomGroups(building: FreeBuilding): List<Pair<String, List<RoomOption>>> {
        val named = building.rooms.groupBy { r ->
            r.floor.trim().takeIf { it.isNotBlank() } ?: "其他"
        }
        return named.entries
            .sortedWith(compareBy({ it.key.toIntOrNull() ?: Int.MAX_VALUE }, { it.key }))
            .map { (floor, rooms) ->
                val label = if (floor == "其他") floor else "${floor}F"
                label to rooms.sortedWith(compareBy({ it.room.length }, { it.room }))
            }
    }

    /** 座位数够不够坐：>0 才能显示，别显示 "0 座" */
    fun seatsLabel(r: RoomOption): String = if (r.seats > 0) "${r.seats} 座" else ""

    /** 空闲教室小结：`第二教学楼 · 47 间` */
    fun freeRoomsHeadline(ro: cn.dkarbon.enroute.data.UrpHomeApi.FreeRooms, buildingName: String): String {
        if (ro.roomCount <= 0) return freeRoomsEmptyHint(ro.issue, buildingName)
        val name = buildingName.ifBlank { ro.buildings.firstOrNull()?.name.orEmpty() }
        return listOf(name, "${ro.roomCount} 间").filter { it.isNotBlank() }.joinToString(" · ")
    }

    /**
     * 空闲教室空态文案 —— **三种原因必须分开说**（票 02）：
     * 真的没空教室 / 会话失效或不在校园网 / 选楼这一步没生效。
     *
     * 混成一句「这一节没有空闲教室」会让人以为教室真的空着或满着，从而做出错误决定；
     * 这一层只负责把「为什么空」讲清楚，并给出**下一步能做什么**。
     */
    fun freeRoomsEmptyHint(
        issue: cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue,
        buildingName: String = "",
    ): String {
        val who = buildingName.trim().ifBlank { "这栋楼" }
        return when (issue) {
            cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue.OK,
            cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue.NOTHING_FREE ->
                "${who}这一节没有空闲教室。换一节课次，或者选「明天 / 后天」看看"
            cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue.NO_SESSION ->
                "没拿到数据（不是「没有空教室」）。教务登录态可能已失效，或当前不在校园网 —— " +
                    "回「课表」页重新登录、连上校园网再刷新"
            cn.dkarbon.enroute.data.UrpHomeApi.FreeRoomsIssue.PICK_LOST ->
                "服务器返回的不是你选的那栋楼（「先选楼再查」这步没生效）。刷新后再试一次；" +
                    "若一直这样，说明教务接口行为改了，去设置页跑一次接口诊断"
        }
    }

    // ── 学期号 ────────────────────────────────────────────

    /** URP 的学期号：`2026-2027-1` 或 `2026-2027-1-1`（= `executiveEducationPlanNumber`） */
    private val TERM_RE = Regex("""^(\d{4})-(\d{4})-([12])(-\d+)?$""")

    /**
     * 当前学期号，形如 `2026-2027-1-1`。查教师课表必须带它，否则服务器会把**全部学期**
     * 的老师混着返回（同一工号重复 → 列表 key 冲突崩溃，见票 01）。
     *
     * 两道来源，**先信学校的**：
     * 1. [serverTerm] = `Timetable.term`（`2026-2027-1`），由 `UrpClient.parseSchedule`
     *    从课表响应里的 `executiveEducationPlanNumber` 截前三段得到 —— 那是学校的原值。
     *    但只有落在**当前学年**时才用：本地缓存里可能躺着上学期导入的课表，那个学期号早过期了。
     * 2. 推不出来就按日历推：8 月~次年 1 月 = 第 1 学期（秋），2~7 月 = 第 2 学期（春）。
     *
     * 第四段（实测只有秋学期样本 `2026-2027-1-1` 的末位 `1`）：serverTerm 自带四段就**原样**用，
     * 只有三段时补 `-1`。春学期的第四段待校园网实测（已写进票 03 的遗留项）。
     */
    fun currentTermCode(today: LocalDate, serverTerm: String = ""): String {
        val m = today.monthValue
        // 学年起始年：8 月及以后算新学年，1~7 月仍属上一学年
        val acadStart = if (m >= 8) today.year else today.year - 1
        val semester = if (m in 2..7) 2 else 1
        val fallback = "$acadStart-${acadStart + 1}-$semester-1"

        val hit = TERM_RE.find(serverTerm.trim()) ?: return fallback
        val a = hit.groupValues[1]
        val b = hit.groupValues[2]
        // 服务器给的学期不是当前学年 → 视为过期缓存，不用
        if (a != acadStart.toString() || b != (acadStart + 1).toString()) return fallback
        val tail = hit.groupValues[4]
        return "$a-$b-${hit.groupValues[3]}" + if (tail.isNotBlank()) tail else "-1"
    }

    /** `2026-2027-1-1` → 「2026-2027 秋」；认不出就原样返回（不编学期名） */
    fun termLabel(termCode: String): String {
        val t = termCode.trim()
        val hit = TERM_RE.find(t) ?: return t
        // 学校口径：第 1 学期 = 秋，第 2 学期 = 春（成绩 payload 里 termName/termCode 就是这个对应）
        val season = if (hit.groupValues[3] == "1") "秋" else "春"
        return "${hit.groupValues[1]}-${hit.groupValues[2]} $season"
    }

    /** 搜索结果那一行：`找到 11 位 · 2026-2027 秋`；0 条时返回空串（空态另有文案） */
    fun teacherResultLine(count: Int, termCode: String): String {
        if (count <= 0) return ""
        val label = termLabel(termCode)
        return if (label.isBlank()) "找到 $count 位" else "找到 $count 位 · $label"
    }

    /** 服务器口径的学期名：`2026-2027-1-1` → `2026-2027学年秋`（`planName` 回执用） */
    fun termPlanName(termCode: String): String {
        val hit = TERM_RE.find(termCode.trim()) ?: return ""
        return "${hit.groupValues[1]}-${hit.groupValues[2]}学年" + if (hit.groupValues[3] == "1") "秋" else "春"
    }

    // ── 教师详情（票 08）──────────────────────────────────

    /** 星期几 → 中文；越界返回空串（不猜） */
    fun weekdayLabel(d: Int): String =
        if (d in 1..7) listOf("一", "二", "三", "四", "五", "六", "日")[d - 1].let { "周$it" } else ""

    /** `第 3-4 节`；起始节次缺失 → 空串 */
    fun sectionsLabel(slot: cn.dkarbon.enroute.data.UrpHomeApi.TeacherSlot): String {
        val secs = slot.sections
        if (secs.isEmpty()) return ""
        return if (secs.size == 1) "第 ${secs.first()} 节" else "第 ${secs.first()}-${secs.last()} 节"
    }

    /** 课表块标题：`3 个时段 · 2 门课`；空课表返回空串（空态另有文案） */
    fun scheduleHeadline(schedule: cn.dkarbon.enroute.data.UrpHomeApi.TeacherSchedule): String {
        if (schedule.isEmpty) return ""
        return "${schedule.slots.size} 个时段 · ${schedule.courseCount} 门课"
    }

    /**
     * 明细列表里的一行：`周三 第 3-4 节 · 1-3,5,8-15周上`。
     * 周次优先用服务器原文 `zcsm`（学生就是这么看的），没有才回落到位图解出的周次。
     */
    fun slotWhenLabel(slot: cn.dkarbon.enroute.data.UrpHomeApi.TeacherSlot): String {
        val day = weekdayLabel(slot.weekday)
        val secs = sectionsLabel(slot)
        val weeks = slot.weekText.ifBlank {
            if (slot.weeks.isEmpty()) "" else compressWeeks(slot.weeks) + "周"
        }
        return listOf(day, secs, weeks).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** `[1,2,3,5,8,9,10]` → `1-3,5,8-10周`（连续段压成区间） */
    fun compressWeeks(weeks: List<Int>): String {
        val w = weeks.filter { it > 0 }.distinct().sorted()
        if (w.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = w.first()
        var prev = start
        for (i in 1 until w.size) {
            val cur = w[i]
            if (cur == prev + 1) {
                prev = cur
                continue
            }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = cur
            prev = cur
        }
        parts += if (start == prev) "$start" else "$start-$prev"
        return parts.joinToString(",")
    }

    /** 官网块的一行来源说明：`信息来自 土地与环境学院官网 · 抓取于 2026-11-14` */
    fun facultySourceLine(info: cn.dkarbon.enroute.data.faculty.FacultyInfo): String {
        val day = info.fetchedAt.trim().take(10)
        val from = info.collegeName.ifBlank { info.sourceHost }.ifBlank { "学院官网" }
        return "信息来自 $from · " + if (day.isNotBlank()) "抓取于 $day" else "抓取时间未知"
    }

    /**
     * 官网块的失败/状态文案（票 08 要求「三种失败必须可辨」的第二、三种都在这）。
     *
     * 注意「没解析到」与「没收录」是两件事：前者是页面只有图，后者是栏目里没这个人。
     */
    fun facultyMsg(res: cn.dkarbon.enroute.data.faculty.FacultyResult): String {
        if (res.info != null && !res.stale) return ""
        val cachedNote = res.info?.let { "（更新失败，显示 " + it.fetchedAt.trim().take(10) + " 的缓存）" }.orEmpty()
        return when (res.fail) {
            cn.dkarbon.enroute.data.faculty.FacultyFail.None -> cachedNote
            cn.dkarbon.enroute.data.faculty.FacultyFail.NoSource ->
                "这位老师所属院系在学院官网没有师资栏目，查不到公开资料"
            cn.dkarbon.enroute.data.faculty.FacultyFail.NotFound ->
                "学院官网的师资栏目里没找到这位老师（可能只在博导/其他专栏里）"
            cn.dkarbon.enroute.data.faculty.FacultyFail.NotParsed ->
                "找到页面了，但这一页正文只在图片里，解析不到可读信息"
            cn.dkarbon.enroute.data.faculty.FacultyFail.Ambiguous ->
                "官网上同名老师有 ${res.candidates.size} 位，不替你猜"
            cn.dkarbon.enroute.data.faculty.FacultyFail.Network ->
                "没连上学院官网（校园网/网络不通都可能这样），联网后刷新"
        } + cachedNote
    }

    /** 教室浮层副标题：`第二教学楼 · 72 座 · 7F`，缺哪段少哪段；全缺就是空串 */
    fun roomDetailSubtitle(buildingName: String, seats: Int, floor: String): String {
        val f = floor.trim()
        return listOf(
            buildingName.trim(),
            if (seats > 0) "$seats 座" else "",
            if (f.isNotBlank()) "${f}F" else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /**
     * 浮层里「占用（今天/明天/后天被哪些课占了）」那一段的说明。
     *
     * 票 03 的结论：URP 把「教室档案」和「教室占用课表」拆成两条路由，前者通了、后者**还没探明**
     * （2026-11-14 探针被 CAS 登录态挡住，见 `docs/URP-学生端接口清单.md` §6.1）。
     * 所以在实测补齐之前，这一段只能如实说「暂缺」——
     * **绝不能拿一张空课表冒充「这间教室今天没人用」**，那是假数据，会让人白跑一趟。
     */
    fun occupancyHint(): String =
        "暂缺：教务把「教室档案」和「教室占用课表」分成两条接口，后者的请求参数还没实测（2026-11-14）。" +
            "这里先不摆空表，等实测通了再补 —— 在此之前请以现场为准。"

    /**
     * 「没查到这间教室」时交代**检索覆盖面**：翻了 scanned 条、库里共 totalCount 间。
     * 不说覆盖范围的「查不到」等于让人无从判断是教室不存在还是我们没翻到。
     */
    fun classroomMissHint(totalCount: Int, scanned: Int): String {
        val head = "没查到这间教室的记录"
        if (totalCount <= 0 && scanned <= 0) return "$head（这次一条数据都没拿回来）"
        val cover = if (totalCount > 0) "翻过 $scanned / 共 $totalCount 间教室" else "翻过 $scanned 条记录"
        return "$head（$cover）。可能是这栋楼的教室还没录进教室库，或这学期没给它排课"
    }

    // ── 教师 ──────────────────────────────────────────────

    /** `董秀茹 · 土环 · 教授`，缺项就少一段 */
    fun teacherLine(t: TeacherHit): String =
        listOf(t.dept, t.title).filter { it.isNotBlank() }.joinToString(" · ")

    /** 教师搜索的空态文案：区分「没搜」和「搜了没有」 */
    fun teacherEmptyHint(keyword: String): String = when {
        keyword.isBlank() -> "输入教师姓名搜索（支持部分匹配，如「董」）"
        else -> "没找到「$keyword」。换个字试试，或确认这学期 TA 有排课"
    }

    // ── 节次 ──────────────────────────────────────────────

    /**
     * 按当前时刻推出「现在是第几小节」（1..12）。
     *
     * 空闲教室接口按**小节**（1~12）查，而课表里的大节是 1~6 —— 一个大节等于两个小节。
     * 规则：
     * - 正在上某个大节 → 前一半算第一个小节，后一半算第二个；
     * - 在两个大节之间 → 取**下一个大节的第一个小节**（正要开始的才是用户关心的）；
     * - 全部上完 → 12。
     */
    fun currentSectionNo(nowMinutes: Int, summer: Boolean): Int {
        BigSession.entries.forEach { b ->
            val (s, e) = SchoolClock.rangeOf(b, summer)
            if (nowMinutes in s..e) {
                val mid = s + (e - s) / 2
                return if (nowMinutes <= mid) b.sessions.first else b.sessions.last
            }
            if (nowMinutes < s) return b.sessions.first
        }
        return BigSession.entries.last().sessions.last
    }

    // ── 数字格式 ──────────────────────────────────────────

    /** 12.25 → "12.25"；12.0 → "12"；避免"12.0 学分"这种半吊子 */
    fun trimNum(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "0"
        val rounded = Math.round(v * 100.0) / 100.0
        return if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}
