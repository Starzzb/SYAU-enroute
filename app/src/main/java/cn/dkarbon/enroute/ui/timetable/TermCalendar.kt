package cn.dkarbon.enroute.ui.timetable

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 学期周次校准。
 *
 * 服务器没给学期起始日，所以「今天第几周」必须由用户校准一次并记住。
 * 校准方式：用户告知「当前是第 N 周」，反推出第 1 周的周一日期，
 * 之后每天都从这个锚点自动算出准确周次。
 *
 * 这比按「9/1 开学」硬编码靠谱 —— 实测 9/1 起算会把第 10 周算成第 8 周。
 */
class TermCalendar(ctx: Context) {

    private val sp = ctx.getSharedPreferences("term-calendar", Context.MODE_PRIVATE)

    /** 第 1 周周一的 epochDay（-1 表示未校准） */
    var week1MondayEpochDay: Long
        get() = sp.getLong("week1_monday", -1L)
        set(v) = sp.edit().putLong("week1_monday", v).apply()

    val isCalibrated: Boolean get() = week1MondayEpochDay >= 0

    /**
     * 用「今天是第 N 周」反推第 1 周周一。
     */
    fun calibrate(todayIsWeek: Int, today: LocalDate = LocalDate.now()) {
        val thisMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val week1 = thisMonday.minusWeeks((todayIsWeek - 1).toLong())
        week1MondayEpochDay = week1.toEpochDay()
    }

    /** 今天属于第几周；未校准返回 null */
    fun currentWeek(today: LocalDate = LocalDate.now()): Int? {
        if (!isCalibrated) return null
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val days = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(week1MondayEpochDay), monday)
        if (days < 0) return 1
        return (days / 7).toInt() + 1
    }

    /** 今天星期几（1=周一 .. 7=周日） */
    fun todayWeekday(today: LocalDate = LocalDate.now()): Int = today.dayOfWeek.value

    /** 第 N 周对应周的周一日期 */
    fun mondayOfWeek(n: Int): LocalDate =
        LocalDate.ofEpochDay(week1MondayEpochDay).plusWeeks((n - 1).toLong())

    /** 判断今天是不是第 n 周的今天 */
    fun isThisWeek(n: Int, today: LocalDate = LocalDate.now()): Boolean = currentWeek(today) == n

    fun clear() {
        sp.edit().remove("week1_monday").apply()
    }
}

/** 节次所属时段 */
enum class DayPart(val label: String, val sessions: IntRange) {
    Morning("上午", 1..4),
    Afternoon("下午", 5..8),
    Evening("晚上", 9..12);

    companion object {
        fun of(session: Int): DayPart = entries.firstOrNull { session in it.sessions } ?: Morning
    }
}

/**
 * 大节：两小节合并成一个上课单元。
 *
 * 现实里课程都是「一次性上两节」，如果按单节渲染，一门课会占两行、
 * 看起来像两门不同的课。所以网格按大节排：
 *   第 1 大节 = 1-2 节   第 2 大节 = 3-4 节   第 3 大节 = 5-6 节
 *   第 4 大节 = 7-8 节   第 5 大节 = 9-10 节  第 6 大节 = 11-12 节
 */
enum class BigSession(val index: Int, val label: String, val sessions: IntRange, val part: DayPart) {
    S1(1, "1-2 节", 1..2, DayPart.Morning),
    S2(2, "3-4 节", 3..4, DayPart.Morning),
    S3(3, "5-6 节", 5..6, DayPart.Afternoon),
    S4(4, "7-8 节", 7..8, DayPart.Afternoon),
    S5(5, "9-10 节", 9..10, DayPart.Evening),
    S6(6, "11-12 节", 11..12, DayPart.Evening);

    companion object {
        /** 某一小节属于哪个大节 */
        fun of(session: Int): BigSession? = entries.firstOrNull { session in it.sessions }

        /** 这门课占哪些大节（可能跨多个，如 7-8 节一节课占 S4） */
        fun ofCourse(sections: List<Int>): List<BigSession> =
            sections.mapNotNull { of(it) }.distinct().sortedBy { it.index }
    }
}
