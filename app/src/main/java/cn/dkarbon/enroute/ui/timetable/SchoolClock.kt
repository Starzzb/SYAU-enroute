package cn.dkarbon.enroute.ui.timetable

import java.time.LocalDate

/**
 * 作息时间表：支持**夏季/冬季**两套。
 *
 * 沈阳农业大学实际作息（主人 2026-10-26 提供）：
 *   · 五一之后（5/1 起）→ 夏季作息，下午 **14:00** 上课
 *   · 十一之后（10/1 起）→ 冬季作息，下午 **13:30** 上课
 *
 * 只有下午的第 5-6、7-8 节受影响，上午和晚上不变。
 * 边界处理：5/1 当天算夏季，10/1 当天算冬季。
 */
enum class TermSeason(val label: String) {
    Spring("春季"), Summer("夏季"), Autumn("秋季"), Winter("冬季"),
}

object SchoolClock {

    /** 按日期判断当前该用哪套作息 */
    fun seasonOf(date: LocalDate = LocalDate.now()): TermSeason = when (date.monthValue) {
        5, 6, 7, 8, 9 -> TermSeason.Summer   // 5/1 ~ 9/30
        10, 11, 12, 1, 2, 3, 4 -> TermSeason.Winter // 10/1 ~ 4/30
        else -> TermSeason.Winter
    }

    /** 是否夏季作息（下午 14:00 上课） */
    fun isSummerTime(date: LocalDate = LocalDate.now()): Boolean =
        seasonOf(date) == TermSeason.Summer

    /**
     * 取某个大节的起止分钟数。
     * 夏季作息时，下午两节各推迟 30 分钟。
     */
    fun rangeOf(big: BigSession, summer: Boolean): Pair<Int, Int> {
        val shift = if (summer) 30 else 0
        return when (big) {
            BigSession.S1 -> 8 * 60 to 9 * 60 + 40
            BigSession.S2 -> 10 * 60 to 11 * 60 + 40
            BigSession.S3 -> (13 * 60 + 30 + shift) to (15 * 60 + 10 + shift)
            BigSession.S4 -> (15 * 60 + 30 + shift) to (17 * 60 + 10 + shift)
            BigSession.S5 -> 18 * 60 + 30 to 20 * 60 + 10
            BigSession.S6 -> 20 * 60 + 20 to 22 * 60
        }
    }

    /** 界面显示用的时段标签（含作息差异） */
    fun labelOf(big: BigSession, summer: Boolean): String {
        val (s, e) = rangeOf(big, summer)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    /** 一句话说明当前作息，给界面提示 */
    fun hintOf(summer: Boolean): String =
        if (summer) "夏季作息 · 下午 14:00 上课" else "冬季作息 · 下午 13:30 上课"
}
