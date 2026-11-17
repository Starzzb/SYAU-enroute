package cn.dkarbon.enroute.data.curriculum

/**
 * 课程大纲的数据模型（票 10）。
 *
 * 资产：`assets/curriculum/syllabus/index.json.br`（2066 门索引）+ `syllabus/<学院>.jsonl.br`（学院包，
 * 每行一门；索引指向 pack 文件的第 line 行）。两者都是 brotli（q11/lgwin22），读取层负责解压。
 * 来源：**14 个学院 2021 版课程大纲**（14 册 PDF，合计 11,146 页）。
 *
 * ⚠️ 覆盖率是硬事实：收录 14 个学院的 **2066 门**，通识课（形势与政策、社会主义发展史等）
 * 与其它年级的课不在其中 ——「没有大纲」是正常现象，界面必须照实说，不许空卡片、不许转圈。
 */

/** 索引里的一条 */
data class SyllabusIndexEntry(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    /** 理论课 / 实验课 / 实践课 */
    val type: String = "",
    val credit: String = "",
    /** 索引的 `hours` 是 `[理论学时, 实验学时]` */
    val theoryHours: String = "",
    val labHours: String = "",
    val bytes: Int = 0,
    /** 所属学院包文件名（`assets/curriculum/syllabus/<学院>.jsonl.br`）；空 = 旧格式资产 */
    val pack: String = "",
    /** 在该包里的行号（0 起）。`.jsonl` 一行一门，按行定位不用解整包 */
    val line: Int = -1,
) {
    /** `3 学分 · 48 / 0 学时`；缺项就少一段 */
    val creditHoursLabel: String
        get() {
            val c = if (credit.isNotBlank()) "$credit 学分" else ""
            val h = if (theoryHours.isNotBlank() || labHours.isNotBlank()) {
                "理论 ${theoryHours.ifBlank { "—" }} / 实验 ${labHours.ifBlank { "—" }} 学时"
            } else {
                ""
            }
            return listOf(c, h).filter { it.isNotBlank() }.joinToString(" · ")
        }
}

/** 大纲正文里的一节（`一、课程基本信息` … `九、教学大纲修订说明`，顺序照原文） */
data class SyllabusSection(val title: String = "", val text: String = "")

/** `教学章节` 的一行：`第一章 土地估价概述 · 6.0 学时，支撑课程目标1` */
data class SyllabusChapter(val title: String = "", val unit: String = "", val note: String = "")

/** `教学内容学时一览表` 的一行（**原表列**，别合并） */
data class SyllabusHoursRow(
    val content: String = "",
    val online: String = "",
    val offline: String = "",
    val lab: String = "",
    val total: String = "",
)

/** 单门课程大纲 */
data class Syllabus(
    val code: String = "",
    val name: String = "",
    val englishName: String = "",
    val type: String = "",
    val credit: String = "",
    val theoryHours: String = "",
    val labHours: String = "",
    val category: String = "",
    val department: String = "",
    val writer: String = "",
    val madeAt: String = "",
    /** `基本信息` 的键值对（课程代码/选用教材/主要参考书…），保持原顺序 */
    val basicInfo: List<Pair<String, String>> = emptyList(),
    /** `章节`：按原文顺序的小节（标题 + 正文） */
    val sections: List<SyllabusSection> = emptyList(),
    /** `教学章节`：章节级条目 */
    val chapters: List<SyllabusChapter> = emptyList(),
    /** `教学内容学时一览表` */
    val hoursTable: List<SyllabusHoursRow> = emptyList(),
    /** `课程目标列表` */
    val goals: List<PlanPoint> = emptyList(),
) {
    val isEmpty: Boolean get() = name.isBlank() && sections.isEmpty() && basicInfo.isEmpty()

    /**
     * 「教学内容学时一览表」的学时合计（**只在原表每一行都有数时才算**）。
     *
     * 任何一行「学时合计」为空（实测有「课程思政内容」这种只写内容的行）就返回 null，
     * 由界面不显示合计 —— 拿缺失行当 0 加出来的数字是假数据。
     */
    val hoursTotal: String?
        get() {
            if (hoursTable.isEmpty()) return null
            val nums = hoursTable.map { it.total.trim() }
            if (nums.any { it.isBlank() }) return null
            val sum = nums.mapNotNull { it.toDoubleOrNull() }.sum()
            return if (nums.any { it.toDoubleOrNull() == null }) null else PlanFormat.trim(sum)
        }
}

/** 数字显示小工具（`6.0` → `6`）；与 HomeModel 的 trimNum 同口径，但那边属于学业主页，不跨层引用 */
object PlanFormat {
    fun trim(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return ""
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r) && Math.abs(r) < 1e15) r.toLong().toString()
        else r.toString().trimEnd('0').trimEnd('.')
    }
}

/**
 * 课程大纲匹配结果。
 *
 * 三种结局必须分开（票 10 的要求）：唯一命中 / 多条候选（不猜）/ 没有。
 */
data class SyllabusMatch(
    val entry: SyllabusIndexEntry? = null,
    /** 命中多条时把候选列出来，让用户选，**不替用户猜** */
    val candidates: List<SyllabusIndexEntry> = emptyList(),
    /** 这次是靠什么命中的：`code` / `name` / `fuzzy` / `none`（排障与测试用） */
    val by: String = "none",
) {
    val found: Boolean get() = entry != null
}
