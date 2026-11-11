package cn.dkarbon.enroute.data

import cn.dkarbon.enroute.ui.timetable.BigSession
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 课表 → 时间轴的换算。**纯函数，无 Android 依赖，可单测。**
 *
 * 为什么单独抽出来：三个地方都要算「今天有什么课、下一节是什么」——
 * 今日卡（[cn.dkarbon.enroute.ui.today]）、上课提醒（[Reminders]）、
 * 桌面小组件（[cn.dkarbon.enroute.widget]）。三处各写一遍必然三套口径，
 * 出问题时你会不知道该信哪个。
 *
 * 关于包依赖：`BigSession` / `SchoolClock` 落在 `ui.timetable` 包里（从 CET6 原样搬来），
 * 这里是 data 层反过来引用 ui 层。不是最漂亮，但把作息表挪包会牵动那 1792 行 UI 代码，
 * 不值得 —— 只此一处例外，别再扩散。
 */
object ScheduleMath {

    /** 某一节课在时间轴上的一个格子（一门课可能占多个大节，就产生多个 Slot） */
    data class Slot(
        val course: Course,
        val big: BigSession,
        val startMin: Int,
        val endMin: Int,
    ) {
        val startLabel: String get() = fmt(startMin)
        val endLabel: String get() = fmt(endMin)
        val rangeLabel: String get() = "$startLabel–$endLabel"

        private fun fmt(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
    }

    /**
     * 某一天的全部课，按开始时间排序。
     *
     * @param week 当前第几周；null = 未校准，此时**不做周次过滤**（宁可多显示也别显示空白）
     * @param summer 夏季作息（下午顺延 30 分钟），由 [SchoolClock.isSummerTime] 给出
     */
    fun slotsOf(
        tt: Timetable,
        date: LocalDate,
        week: Int?,
        summer: Boolean,
    ): List<Slot> {
        val weekday = date.dayOfWeek.value
        return tt.courses.asSequence()
            .filter { it.weekday == weekday && it.sections.isNotEmpty() }
            .filter { week == null || it.weeks.isEmpty() || week in it.weeks }
            .flatMap { c ->
                BigSession.ofCourse(c.sections).asSequence().map { big ->
                    val (s, e) = SchoolClock.rangeOf(big, summer)
                    Slot(course = c, big = big, startMin = s, endMin = e)
                }
            }
            .sortedBy { it.startMin }
            .toList()
    }

    /**
     * 下一节课 = 结束时间还没过的第一节。
     *
     * 注意用 `endMin > now` 而不是 `startMin > now`：正在上的那节课应该显示成
     * 「正在上」，不是「下一节」。这条是今日卡和提醒共用的口径。
     */
    fun nextSlot(
        tt: Timetable,
        now: LocalDateTime,
        week: Int?,
        summer: Boolean,
    ): Slot? {
        val nowMin = now.hour * 60 + now.minute
        return slotsOf(tt, now.toLocalDate(), week, summer).firstOrNull { it.endMin > nowMin }
    }

    /** 正在上的课（start ≤ now < end）；没有则 null */
    fun currentSlot(
        tt: Timetable,
        now: LocalDateTime,
        week: Int?,
        summer: Boolean,
    ): Slot? {
        val nowMin = now.hour * 60 + now.minute
        return slotsOf(tt, now.toLocalDate(), week, summer)
            .firstOrNull { nowMin >= it.startMin && nowMin < it.endMin }
    }

    /** 距离开课还有多少分钟（负数=已开始） */
    fun minutesUntil(slot: Slot, now: LocalDateTime): Int =
        slot.startMin - (now.hour * 60 + now.minute)

    /** 「3 小时后」「25 分钟后」这种人话 */
    fun humanize(minutes: Int): String = when {
        minutes <= 0 -> "现在"
        minutes < 60 -> "${minutes} 分钟后"
        minutes < 60 * 24 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "${h} 小时后" else "${h} 小时 ${m} 分后"
        }
        else -> "${minutes / (60 * 24)} 天后"
    }
}
