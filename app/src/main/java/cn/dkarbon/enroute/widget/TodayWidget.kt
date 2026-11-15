package cn.dkarbon.enroute.widget

import android.content.Context
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.MainActivity
import cn.dkarbon.enroute.data.ScheduleMath
import cn.dkarbon.enroute.data.TimetableStore
import cn.dkarbon.enroute.ui.theme.Ink
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import cn.dkarbon.enroute.ui.timetable.TermCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 桌面小组件：今天 / 下一节课。
 *
 * 三条设计约束：
 *   1. **不联网、不登录** —— 只读本地 `timetable.json`。桌面上的东西必须秒开，
 *      失败了也不能空着转圈（所以没数据时显示「去 App 里导入课表」）。
 *   2. **点一下进 App** —— 整块可点，落到 Today 页。
 *   3. **数据变化后要刷新** —— 见 [refresh]。
 *
 * 注意：Glance 里不能用 Compose 的 Material 组件，只能用 Glance 自己的 `Text`/`Column`；
 * 颜色直接取 [Ink] 的浅色值（小组件不跟随 App 内主题，跟随系统由 host 决定）。
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val data = produceState<WidgetData?>(initialValue = null, context) {
                value = withContext(Dispatchers.IO) { load(context) }
            }
            WidgetBody(data.value, context)
        }
    }

    private data class WidgetData(
        val dateLabel: String,
        val headline: String,
        val detail: String,
        val rows: List<Triple<String, String, String>>,
        val hasTimetable: Boolean,
    )

    private fun load(context: Context): WidgetData {
        val now = LocalDateTime.now()
        val fmt = DateTimeFormatter.ofPattern("M月d日")
        val tt = TimetableStore(context).load()
            ?: return WidgetData(
                dateLabel = now.format(fmt),
                headline = "还没导入课表",
                detail = "去 App 里导入课表",
                rows = emptyList(),
                hasTimetable = false,
            )

        val week = TermCalendar(context).currentWeek()
        val summer = SchoolClock.isSummerTime(now.toLocalDate())
        val today = ScheduleMath.slotsOf(tt, now.toLocalDate(), week, summer)
        val next = ScheduleMath.nextSlot(tt, now, week, summer)
        val current = ScheduleMath.currentSlot(tt, now, week, summer)

        val headline = when {
            current != null -> "正在上 · ${current.course.name}"
            next != null -> "下一节 ${next.startLabel} · ${next.course.name}"
            else -> "今天没课了"
        }
        val detail = when {
            current != null -> current.course.roomShort.ifBlank { current.rangeLabel }
            next != null -> {
                val mins = ScheduleMath.minutesUntil(next, now)
                buildString {
                    append(ScheduleMath.humanize(mins))
                    if (next.course.roomShort.isNotBlank()) append(" · ").append(next.course.roomShort)
                }
            }
            else -> ""
        }

        return WidgetData(
            dateLabel = now.format(fmt),
            headline = headline,
            detail = detail,
            rows = today.take(4).map { Triple(it.startLabel, it.course.name, it.course.roomShort) },
            hasTimetable = true,
        )
    }

    @androidx.compose.runtime.Composable
    private fun WidgetBody(d: WidgetData?, context: Context) {
        val ink = ColorProvider(Ink.Ink)
        val soft = ColorProvider(Ink.InkSoft)
        val faint = ColorProvider(Ink.InkFaint)
        val brick = ColorProvider(Ink.Brick)

        Column(
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Ink.Paper))
                .padding(12.dp)
                .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java))),
        ) {
            Row(GlanceModifier.fillMaxWidth()) {
                Text(
                    "沈农在途",
                    style = TextStyle(color = soft, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    d?.dateLabel.orEmpty(),
                    style = TextStyle(color = faint, fontSize = 10.sp),
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            Text(
                d?.headline ?: "载入中…",
                style = TextStyle(color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            if (!d?.detail.isNullOrBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    d.detail,
                    style = TextStyle(color = brick, fontSize = 11.sp),
                )
            }
            if (d != null && d.rows.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                d.rows.forEach { (time, name, room) ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(
                            time,
                            style = TextStyle(color = faint, fontSize = 10.sp),
                            modifier = GlanceModifier.width(38.dp),
                        )
                        Text(
                            name,
                            style = TextStyle(color = soft, fontSize = 10.sp),
                            maxLines = 1,
                        )
                        if (room.isNotBlank()) {
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                room,
                                style = TextStyle(color = faint, fontSize = 9.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 课表/周次变化后刷新所有已放置的小组件 */
suspend fun refresh(context: Context) {
    runCatching { TodayWidget().updateAll(context) }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
