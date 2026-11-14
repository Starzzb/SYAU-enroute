package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 课表页的**编辑排版（校报头版）渲染分支**（票 17）。
 *
 * ## 为什么单独一个文件
 * `TimetableScreen.kt` 里的 `CourseCard` / `UnscheduledRow` 是「硬朗农业 + 学院纸本」的
 * 卡片语言（洗色底 + 1dp 描边 + 4dp 圆角 + IBM Plex Mono）。编辑版不是换颜色，而是
 * **把卡片整个拿掉**：纸面上直接排字，用 2dp 竖脊 + 网格线 + 衬线标题分区。
 * 两套渲染差异太大，混在一个函数里加 `if` 会毁掉老主题的可读性（也更容易改坏）。
 *
 * ## 老主题零回归
 * 本文件**只有** [Form.current.editorial] 为真时才会被调用；调用点在
 * `CourseCard` / `UnscheduledRow` 的分支里，老主题走原路径，一行未改。
 *
 * ## 编辑版的四条规则（对应 v3 §5.2）
 * 1. **没有容器**：不铺底色、不描边、不圆角、不加阴影
 * 2. **竖脊当边界**：左侧 2dp 满彩竖条（课程色，`Tt.famOf` 同一套，改名不换色）
 * 3. **衬线承担标题**：课名走 `Form.current.displayFamily`（系统 Serif），地点/教师留无衬线小字
 * 4. **侵蚀不撕纸**：吃掉的部分**褪成纸色**（墨迹褪去），不画咬痕 —— 咬痕是卡片语言，报纸没有
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorialCourseCell(
    course: Course,
    progress: Float,
    isNow: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val t = InkTokens.current
    val f = Form.current
    val key = course.courseNo.ifBlank { course.name }
    val fam = Tt.famOf(key)
    val spine = Tt.spineOf(fam, t.isDark)
    val haptic = LocalHapticFeedback.current

    Box(
        Modifier
            .fillMaxSize()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "查看 ${course.name} 详情",
                onLongClickLabel = "${course.name} 快捷操作",
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick,
            )
            // 侵蚀：把「已上过去」的上半截褪成纸色，边界留一条 1dp 横线。
            // 只在今天那一列、且这节课有进度时才画（progress = 0 时整段跳过，零成本）。
            .then(
                if (progress > 0.02f) {
                    Modifier.drawWithContent {
                        drawContent()
                        val cut = size.height * progress.coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    t.paper.copy(alpha = 0.94f),
                                    t.paper.copy(alpha = 0.86f),
                                    t.paper.copy(alpha = 0.52f),
                                ),
                                startY = 0f,
                                endY = cut.coerceAtLeast(1f),
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, cut),
                        )
                        drawLine(
                            color = spine.copy(alpha = 0.55f),
                            start = Offset(0f, cut),
                            end = Offset(size.width, cut),
                            strokeWidth = 1f,
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxSize()) {
            // 竖脊：正在上的课加粗到 3dp 并用主色，远看就是「这一节正在进行」
            Box(
                Modifier
                    .width(if (isNow) 3.dp else 2.dp)
                    .fillMaxHeight()
                    .background(if (isNow) t.primary else spine),
            )
            Column(
                Modifier.padding(start = 3.dp, top = 2.dp, end = 1.dp, bottom = 1.dp),
            ) {
                Text(
                    course.name,
                    fontSize = 10.sp,
                    lineHeight = 12.5.sp,
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = f.titleTracking,
                    fontFamily = f.displayFamily,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isNow) t.primary else t.ink,
                )
                if (course.roomShort.isNotBlank()) {
                    Text(
                        CourseFacts.shortPlace(course.roomShort),
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = t.inkSoft,
                    )
                }
                if (course.teacher.isNotBlank()) {
                    Text(
                        course.teacher,
                        fontSize = 7.5.sp,
                        lineHeight = 9.5.sp,
                        letterSpacing = 0.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = t.inkFaint,
                    )
                }
            }
        }
    }
}

/**
 * 上完的课留下的痕迹：一条褪色横线 + 极淡的课名残影。
 *
 * 与老主题的 [EatenResidue] 的区别：那里是「砖红短划 + 灰烬」的撕纸语言，
 * 这里是报纸的「墨迹已经印淡了」—— 不编造任何新信息（还是同一门课）。
 */
@Composable
internal fun EditorialResidue(course: Course?) {
    val t = InkTokens.current
    val name = course?.name.orEmpty()
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 2.dp, end = 1.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(t.rule))
            if (name.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    name,
                    fontSize = 8.5.sp,
                    lineHeight = 10.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = Form.current.displayFamily,
                    color = t.inkFaint.copy(alpha = 0.45f),
                )
            }
        }
    }
}

/**
 * 未排课 / 冲突 的一行：**没有卡片**，只有一条下沿版面线 + 2dp 竖脊 + 衬线课名。
 * 触区整行 ≥ 44dp（视觉 38dp，靠 padding 撑到 48dp 命中区）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EditorialUnscheduledRow(c: Course, onClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    val fam = Tt.famOf(c.courseNo.ifBlank { c.name })
    val spine = Tt.spineOf(fam, t.isDark)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .combinedClickable(
                onClickLabel = "查看 ${c.name} 详情",
                onClick = onClick,
                onLongClick = onClick,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(26.dp).background(spine))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = f.titleTracking,
                fontFamily = f.displayFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = t.ink,
            )
            Text(
                buildString {
                    append(c.weeksLabel)
                    if (c.teacher.isNotBlank()) append(" · ").append(c.teacher)
                },
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                color = t.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
