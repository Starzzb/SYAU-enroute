package cn.dkarbon.enroute.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 课程表数据模型。
 *
 * 来源有两种：
 *   1. 导入 JSON（由电脑侧 `export-timetable.mjs` 从 URP 抓包生成）
 *   2. App 内直接登录 URP 抓取（见 [UrpClient]）
 * 两者最终都归一成这个结构，界面和逻辑只认它。
 */
@Serializable
data class Timetable(
    val schema: String = "karbon-timetable/1",
    @SerialName("exported_at") val exportedAt: String = "",
    val term: String = "",
    @SerialName("total_credit") val totalCredit: Double? = null,
    @SerialName("session_times") val sessionTimes: Map<String, String> = emptyMap(),
    val courses: List<Course> = emptyList(),
) {
    val scheduled: List<Course> get() = courses.filter { it.weeks.isNotEmpty() && it.sections.isNotEmpty() }
    val unscheduled: List<Course> get() = courses.filter { it.weeks.isEmpty() || it.sections.isEmpty() }
}

@Serializable
data class Course(
    @SerialName("courseNo") val courseNo: String = "",
    @SerialName("seqNo") val seqNo: String = "",
    val name: String = "",
    val teacher: String = "",
    val category: String = "",
    @SerialName("examType") val examType: String = "",
    /** 上课周次，如 [1,2,3,5,8...] */
    val weeks: List<Int> = emptyList(),
    /** 星期 1..7 */
    val weekday: Int = 0,
    /** 节次，如 [1,2] */
    val sections: List<Int> = emptyList(),
    val room: String = "",
    val credit: Double? = null,
    val raw: String = "",
) {
    val weekdayName: String
        get() = when (weekday) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "未排"
        }

    /** 地点里的 "主校 / 第二教学楼 / 606" 简化成 "主校第二教学楼606" */
    val roomShort: String
        get() = room.replace(" / ", "").replace("/", "").trim()

    val sectionsLabel: String
        get() = when {
            sections.isEmpty() -> "未排"
            sections.size == 1 -> "第${sections[0]}节"
            else -> "第${sections.first()}-${sections.last()}节"
        }

    val weeksLabel: String
        get() {
            if (weeks.isEmpty()) return "未排"
            return if (weeks.size == weeks.last() - weeks.first() + 1) {
                "第${weeks.first()}-${weeks.last()}周"
            } else {
                "第" + weeks.joinToString(",") + "周"
            }
        }
}
