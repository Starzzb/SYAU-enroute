package cn.dkarbon.enroute.data

/**
 * 接口诊断要探的 14 个请求 —— 一条一个请求，一条对应一把锁。
 *
 * 为什么是这 14 条：只探「App 真的依赖」的接口。凡是 `data/` 层为了页面
 * 能跑起来必须请求到的地址，都在这里；教务里我们从不调用（选课、退课、
 * 报名、评教）的入口不探。
 *
 * 地址与实测口径见 `docs/URP-学生端接口清单.md`（2026-11-14 取样）。
 * [expectJson] 为 false 的三条（学籍、两份课表、成绩首页、方案页）是
 * **服务端渲染 HTML**，本来就不返回 JSON，不能拿 JSON 当通过标准。
 */
data class ProbeSpec(
    val id: String,
    val label: String,
    /** 展示用地址；含 `<token>` 的是运行时才拿得到的路径 */
    val path: String,
    val expectJson: Boolean,
)

object ProbeCatalog {

    /** 运行时才确定的路径片段占位符 */
    const val TOKEN = "<token>"

    /**
     * 顺序即探测顺序，不能随便调：带 [TOKEN] 的几条依赖前面某条先拿到 token。
     * 相邻两层：成绩首页 → 成绩数据 / 全部及格；培养方案页 → 方案明细。
     */
    val specs: List<ProbeSpec> = listOf(
        ProbeSpec("roll.info", "学籍信息", "/student/rollManagement/rollInfo/index", false),
        ProbeSpec("timetable.now", "本学期课表", "/student/courseSelect/thisSemesterCurriculum/index", false),
        ProbeSpec("timetable.history", "历年课表", "/student/courseSelect/calendarSemesterCurriculum/index", false),
        ProbeSpec("exam.index", "考试页", "/student/examinationManagement/examPlan/index", false),
        ProbeSpec("exam.detail", "考试明细", "/student/examinationManagement/examPlan/detail", true),
        ProbeSpec("score.index", "成绩首页", "/student/integratedQuery/scoreQuery/allTermScores/index", false),
        ProbeSpec(
            "score.data", "成绩数据",
            "/student/integratedQuery/scoreQuery/$TOKEN/allTermScores/data", true,
        ),
        ProbeSpec(
            "score.pass", "全部及格成绩",
            "/student/integratedQuery/scoreQuery/$TOKEN/allPassingScores/callback", true,
        ),
        ProbeSpec(
            "plan.index", "培养方案页",
            "/student/integratedQuery/instructionPlanQuery/detail/index", false,
        ),
        ProbeSpec(
            "plan.detail", "培养方案明细",
            "/student/integratedQuery/instructionPlanQuery/$TOKEN/detail/callback", true,
        ),
        ProbeSpec(
            "teacher.search", "教师课表检索",
            "/student/teachingResources/teacherCurriculum/search", true,
        ),
        ProbeSpec(
            "room.campus", "教室·校区",
            "/student/teachingResources/freeClassroom/queryCodeCampusList", true,
        ),
        ProbeSpec(
            "room.building", "教室·楼栋",
            "/student/teachingResources/freeClassroom/queryCodeTeaBuildingList?&xqh=001", true,
        ),
        ProbeSpec(
            "room.free", "空闲教室",
            "/student/teachingResources/freeClassroom/today/11?dayplus=0", false,
        ),
    )

    /** 这一条算不算通过。HTML 接口只要求「200 且有内容」 */
    fun ok(spec: ProbeSpec, r: ProbeResult): Boolean {
        if (r.status !in 200..299) return false
        return if (spec.expectJson) r.looksLikeJson else r.bytes > 0
    }

    /** 行内那行小字：「200 · 1.2 KB」/「— · 超时」 */
    fun metaLine(r: ProbeResult?): String {
        if (r == null) return "…"
        if (r.status <= 0) return "— · 失败"
        return "${r.status} · ${formatBytes(r.bytes)}"
    }

    fun formatBytes(n: Int): String = when {
        n <= 0 -> "0 B"
        n < 1024 -> "$n B"
        n < 1024 * 10 -> String.format(java.util.Locale.US, "%.1f KB", n / 1024.0)
        else -> "${n / 1024} KB"
    }
}
