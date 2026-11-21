package cn.dkarbon.enroute.data.curriculum

/**
 * 培养方案的数据模型（票 09）。
 *
 * 数据来自内置资产 `assets/curriculum/`（由 `tools/curriculum/pack.py` 从教务处 PDF 生成），
 * **全部离线读取，不打网络**。
 *
 * ⚠️ 三条诚实规则（资产构建时写下的口径，代码里也必须守）：
 * 1. **学分合计不能用课程行求和**：方案把各选修模块的全部候选课都列出来，学生只修其中一部分。
 *    实测 82 份有「总学分」行的方案里，**82 份**的课程行求和都高于总学分（最多高 52 学分）。
 *    「最低毕业学分」只读 [CurriculumPlan.creditTable]（学分结构表）。
 * 2. 课程性质空串 = 原文为空，**不补猜**。
 * 3. 同一课程代码在不同专业/版本学分学时可能不同，按本专业取用。
 */

/** 索引里的一条：一个专业（含培养类型）对应一个资产文件 */
data class PlanIndexEntry(
    val id: String = "",
    val major: String = "",
    /** 培养类型：普通班 / 卓越计划实验班 / 创新人才培养实验班… */
    val type: String = "",
    val bytes: Int = 0,
    val courses: Int = 0,
    /** 在该年年包（`plans/<年>.jsonl.br`）里的行号，0 起；-1 = 旧格式资产 */
    val line: Int = -1,
)

/** `plan_index.json` 的解析结果：年份 → 该年所有专业条目 */
data class PlanIndex(val byYear: Map<String, List<PlanIndexEntry>> = emptyMap()) {

    /** 有数据的年份，升序（`2024`, `2025`） */
    val years: List<String> get() = byYear.keys.sorted()

    fun entries(year: String): List<PlanIndexEntry> = byYear[year].orEmpty()

    /** 某年出现过的专业名（去重、保持索引顺序） */
    fun majors(year: String): List<String> =
        entries(year).map { it.major }.filter { it.isNotBlank() }.distinct()

    val isEmpty: Boolean get() = byYear.isEmpty()
}

/** 课程设置与教学进程里的一行 */
data class CurriculumCourse(
    val code: String = "",
    val name: String = "",
    val englishName: String = "",
    /** 课程类别（公共课 / 学科基础课 / 专业课…） */
    val category: String = "",
    /** 课程性质（公共必修课 / 学科基础限修课…）。**原文为空就是空串** */
    val nature: String = "",
    val credit: String = "",
    val hours: String = "",
    val theoryHours: String = "",
    val labHours: String = "",
    /** 开课学期，原文是汉字：`一` … `八` */
    val term: String = "",
    /** 这一行的「学分要求」（模块级，原样显示，不做计算） */
    val creditRequirement: String = "",
) {
    val isEmpty: Boolean get() = code.isBlank() && name.isBlank()
}

/** 培养目标/毕业要求里的一条（原文带「编号」） */
data class PlanPoint(
    val no: String = "",
    val content: String = "",
) {
    val isEmpty: Boolean get() = content.isBlank()
}

/**
 * 一条毕业要求。原文是「标题 + 内容 + 指标点」三段，PDF 把标题截断在词中间，
 * 所以上屏时把标题和内容接起来（[text]），指标点单独列。
 */
data class PlanRequirement(
    val no: String = "",
    val title: String = "",
    val content: String = "",
    /** 指标点 1.1 / 1.2 …（毕业要求逐条支撑） */
    val indicators: List<PlanPoint> = emptyList(),
) {
    /** `综合素养/价值观：具有…` —— 去掉原文标题前那个孤零零的冒号 */
    val text: String
        get() = (title + content).trim().removePrefix("：").trim()

    val isEmpty: Boolean get() = text.isBlank()
}

/** 一个专业的完整培养方案 */
data class CurriculumPlan(
    val year: String = "",
    val major: String = "",
    val type: String = "",
    /** 章节页范围 `[223, 234]`（PDF 页号），用于底部标注「共 N 页」 */
    val pageRange: List<Int> = emptyList(),
    val intro: String = "",
    val goals: String = "",
    val requirements: String = "",
    val mainDiscipline: String = "",
    val coreCourses: String = "",
    val practice: String = "",
    val duration: String = "",
    val degree: String = "",
    val goalList: List<PlanPoint> = emptyList(),
    val requirementList: List<PlanRequirement> = emptyList(),
    val courses: List<CurriculumCourse> = emptyList(),
    /** 学分结构表：**原表的行列**（含表头，不做任何合并/重排） */
    val creditTable: List<List<String>> = emptyList(),
    /** 各学期学分分配表：同上 */
    val semesterTable: List<List<String>> = emptyList(),
    /** 2025 版多出来的说明段落（开课单位说明 / 课程替代情况说明），原样展示 */
    val notes: List<Pair<String, String>> = emptyList(),
    /**
     * 「培养目标 + 毕业要求（含指标点）」原文在原 PDF 里的页码 [起, 止]。
     *
     * 这两节动辄两三千字，结构化信息（培养目标1 / 毕业要求1.1）别的字段已经给全，
     * 为体积只在内置资产里留一句话指路；页码由此字段给出，**不猜** ——
     * 老资产没有这个字段就是空列表，界面按「原文节选已精简」显示，不编页号。
     */
    val goalPages: List<Int> = emptyList(),
) {
    /** 这个专业的 PDF 页数（章节页范围给的是闭区间） */
    val pageCount: Int
        get() = if (pageRange.size >= 2) (pageRange[1] - pageRange[0] + 1).coerceAtLeast(0) else 0

    val isEmpty: Boolean get() = major.isBlank() && courses.isEmpty() && creditTable.isEmpty()
}
