package cn.dkarbon.enroute.data.custom

import cn.dkarbon.enroute.data.Course
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 自定义课程（票 19）：用户手动加进课表的课。
 *
 * 三条硬规矩写在类型上：
 * 1. **可区分**：展开出的 [Course.origin] 恒为 [Course.ORIGIN_CUSTOM]，网格里画「自」角标；
 * 2. **不覆盖教务**：合并用 `putIfAbsent`，教务先占位；被挤掉的自定义条目进「同格冲突」列表，**两门都可见**；
 * 3. **只存本机**：`filesDir/timetable/custom.json`，不上传、不进地图补丁。
 */
@Serializable
sealed interface CustomRule {

    /** 按周次：第 [fromWeek]–[toWeek] 周、[parity] 全/单/双、周 [weekday]、第 [bigIndex] 大节 */
    @Serializable
    @SerialName("weekly")
    data class Weekly(
        val fromWeek: Int = 1,
        val toWeek: Int = 16,
        val parity: Parity = Parity.ALL,
        val weekday: Int = 1,
        val bigIndex: Int = 1,
    ) : CustomRule

    /** 固定某一天：具体日期的课（一次性） */
    @Serializable
    @SerialName("single")
    data class SingleDay(
        val date: String = "",      // yyyy-MM-dd（存字符串，避免额外序列化器）
        val bigIndex: Int = 1,
    ) : CustomRule

    @Serializable
    enum class Parity {
        @SerialName("all") ALL,
        @SerialName("odd") ODD,
        @SerialName("even") EVEN,
    }
}

/** 一门自定义课程；[rules] 可以有多条（前八周周二 + 后八周周四）。 */
@Serializable
data class CustomCourse(
    val id: String = "",
    /** 必填 */
    val name: String = "",
    val room: String = "",
    val teacher: String = "",
    /** 备注（主人点名要；**完整保存，不截断**） */
    val note: String = "",
    /** 配色键；留空按名称哈希取色（与现有 `Tt.famOf` 同口径） */
    val colorKey: String = "",
    /** 属于哪个学期（与课表同一口径）；空 = 不限制 */
    val termCode: String = "",
    val rules: List<CustomRule> = emptyList(),
) {
    val isUsable: Boolean get() = name.isNotBlank() && rules.isNotEmpty()
}

/** 一条规则在某一周里的**落位**（展开结果） */
data class CustomSlot(
    val course: CustomCourse,
    val weekday: Int,
    val bigIndex: Int,
)

/**
 * 规则展开、人话预览、冲突检测 —— **纯函数**，日期由调用方注入（便于单测）。
 */
object CustomRules {

    /** 大节 → 小节（第 3 大节 = 第 5-6 小节） */
    fun sectionsOf(bigIndex: Int): List<Int> = when (bigIndex) {
        1 -> listOf(1, 2)
        2 -> listOf(3, 4)
        3 -> listOf(5, 6)
        4 -> listOf(7, 8)
        5 -> listOf(9, 10)
        6 -> listOf(11, 12)
        else -> emptyList()
    }

    /** 大节的中文标签（与课表头一致） */
    fun bigLabel(bigIndex: Int): String = when (bigIndex) {
        1 -> "1-2 节"
        2 -> "3-4 节"
        3 -> "5-6 节"
        4 -> "7-8 节"
        5 -> "9-10 节"
        6 -> "11-12 节"
        else -> "—"
    }

    fun weekdayLabel(weekday: Int): String = when (weekday) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "—"
    }

    /**
     * 某一天的日期 → 第几周（以「第 1 周周一」为基准）。
     *
     * @return 1..N；早于第 1 周返回 ≤0（表示不属于本学期）
     */
    fun weekOfDate(date: LocalDate, week1Monday: LocalDate): Int =
        (ChronoUnit.DAYS.between(week1Monday, date) / 7).toInt() + 1

    /** Weekly 规则在读入时归一到合法区间（避免用户填 20→3 这种） */
    fun normalize(rule: CustomRule.Weekly): CustomRule.Weekly {
        val a = rule.fromWeek.coerceIn(1, 30)
        val b = rule.toWeek.coerceIn(1, 30)
        return rule.copy(fromWeek = minOf(a, b), toWeek = maxOf(a, b), weekday = rule.weekday.coerceIn(1, 7), bigIndex = rule.bigIndex.coerceIn(1, 6))
    }

    /** 该规则在 [week] 这一周是否生效 */
    fun matchesWeek(rule: CustomRule.Weekly, week: Int): Boolean {
        val r = normalize(rule)
        if (week !in r.fromWeek..r.toWeek) return false
        return when (r.parity) {
            CustomRule.Parity.ALL -> true
            CustomRule.Parity.ODD -> week % 2 == 1
            CustomRule.Parity.EVEN -> week % 2 == 0
        }
    }

    /** 该规则是否落在 [week]（Weekly 看周次，SingleDay 按日期换算成周次） */
    fun matches(rule: CustomRule, week: Int, week1Monday: LocalDate): Boolean = when (rule) {
        is CustomRule.Weekly -> matchesWeek(rule, week)
        is CustomRule.SingleDay -> {
            val d = parseDate(rule.date) ?: return false
            weekOfDate(d, week1Monday) == week
        }
    }

    fun parseDate(s: String): LocalDate? =
        runCatching { LocalDate.parse(s.trim(), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

    /**
     * 把一门课在 [week] 的落位展开出来（一门课可能因为多条规则落多格）。
     *
     * @param termCode 当前学期；课程自己记了学期且不匹配就跳过（学期过滤）
     */
    fun slotsOf(course: CustomCourse, week: Int, week1Monday: LocalDate, termCode: String): List<CustomSlot> {
        if (course.name.isBlank()) return emptyList()
        if (course.termCode.isNotBlank() && termCode.isNotBlank() && course.termCode != termCode) return emptyList()
        return course.rules.asSequence()
            .filter { matches(it, week, week1Monday) }
            .mapNotNull { r ->
                when (r) {
                    is CustomRule.Weekly -> CustomSlot(course, r.weekday.coerceIn(1, 7), r.bigIndex.coerceIn(1, 6))
                    is CustomRule.SingleDay -> {
                        val d = parseDate(r.date) ?: return@mapNotNull null
                        // 单日课的星期由**日期本身**决定，不让用户填错
                        CustomSlot(course, d.dayOfWeek.value, r.bigIndex.coerceIn(1, 6))
                    }
                }
            }
            .distinctBy { it.weekday to it.bigIndex }
            .toList()
    }

    /** 展开成周网格用的 [Course]（**与教务课同一个类型**，卡片直接复用） */
    fun toCourses(course: CustomCourse, week: Int, week1Monday: LocalDate, termCode: String): List<Course> =
        slotsOf(course, week, week1Monday, termCode).map { slot ->
            Course(
                courseNo = CUSTOM_ID_PREFIX + course.id,
                name = course.name,
                teacher = course.teacher,
                category = "自定义",
                weeks = listOf(week),
                weekday = slot.weekday,
                sections = sectionsOf(slot.bigIndex),
                room = course.room,
                origin = Course.ORIGIN_CUSTOM,
                note = course.note,
                // 配色键：留空就用课程名（与教务课 `courseNo ?: name` 同思路）
                raw = course.colorKey.ifBlank { course.name },
            )
        }

    const val CUSTOM_ID_PREFIX = "custom:"

    // ── 人话预览（随填随变）──────────────────────────────

    /**
     * 「第 3–16 周 · 每周二 · 第 3-4 节」/「第 1–16 周 · 每周（单周）二 · 第 5-6 节」/「2026-11-20（周五）· 第 5-6 节」
     */
    fun preview(rule: CustomRule): String = when (rule) {
        is CustomRule.Weekly -> {
            val r = normalize(rule)
            val parity = when (r.parity) {
                CustomRule.Parity.ALL -> "每周"
                CustomRule.Parity.ODD -> "每周（单周）"
                CustomRule.Parity.EVEN -> "每周（双周）"
            }
            // 「每周二」读起来顺；单双周时写成「每周（单周）二」
            val day = weekdayLabel(r.weekday).removePrefix("周")
            "第 ${r.fromWeek}–${r.toWeek} 周 · $parity$day · 第 ${bigLabel(r.bigIndex)}"
        }

        is CustomRule.SingleDay -> {
            val d = parseDate(rule.date)
            if (d == null) {
                "尚未选择日期 · 第 ${bigLabel(rule.bigIndex)}"
            } else {
                val wd = weekdayLabel(d.dayOfWeek.value)
                "${d}（$wd）· 第 ${bigLabel(rule.bigIndex)}"
            }
        }
    }

    /**
     * 合并成周网格：**教务先占位，自定义绝不覆盖**（票 19 硬规矩 3）。
     *
     * @return `first` = 网格（一格一门）；`second` = 被挤掉的课程（**不能丢**，界面要单列出来）
     */
    fun mergeIntoGrid(
        urp: List<Course>,
        custom: List<Course>,
        week: Int,
        bigsOf: (List<Int>) -> List<Int>,
    ): Pair<Map<Pair<Int, Int>, Course>, List<Course>> {
        val cells = LinkedHashMap<Pair<Int, Int>, Course>()
        for (c in urp) {
            if (c.weekday !in 1..7 || week !in c.weeks) continue
            for (b in bigsOf(c.sections)) cells.putIfAbsent(c.weekday to b, c)
        }
        val bumped = mutableListOf<Course>()
        for (c in custom) {
            if (c.weekday !in 1..7) continue
            var placed = false
            for (b in bigsOf(c.sections)) {
                val key = c.weekday to b
                if (cells.containsKey(key)) continue
                cells[key] = c
                placed = true
            }
            if (!placed) bumped += c
        }
        // 同一格被两门自定义课抢时，后一门也算被挤掉
        val customInGrid = custom.filter { c ->
            c.weekday in 1..7 && bigsOf(c.sections).any { cells[c.weekday to it] === c }
        }.toSet()
        bumped += custom.filter { it !in customInGrid && it !in bumped }
        return cells to bumped
    }

    /** 冲突提示用：「该时段已有《XX》，两门都会显示」；没冲突返回空串 */
    fun conflictHint(existingNames: List<String>): String {
        val names = existingNames.filter { it.isNotBlank() }.distinct()
        if (names.isEmpty()) return ""
        val head = names.take(2).joinToString("》《") { it }
        return "该时段已有《$head》，两门都会显示"
    }
}
