package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.cardFloat

/**
 * 课表页的**第二套语汇「账页 / LEDGER」渲染分支**（2026-11-14 重做）。
 *
 * ## 与 HARD 的结构差（不是换色）
 * | | HARD 硬朗农业 | 账页 |
 * |---|---|---|
 * | 课程格 | 平面方卡（0 圆角）+ **左侧 2.5dp 竖脊** | 浮起便签（**10dp 圆角 + 软阴影**）+ **顶部 3dp 色帽** |
 * | 分隔 | 6 条横贯横线 + 田垄底纹 | **零横线**：交错行带 + 填充分节色带 |
 * | 数字 | IBM Plex Mono | **系统衬线 + tabular**，右对齐 |
 *
 * ## 为什么把「左脊」改成「顶帽」
 * 课表格只有 ~45dp 宽，左侧 2.5dp 脊 + 4dp 内边距会吃掉 6.5dp 的文本宽度（中文两个半字）。
 * 顶帽把课程色放在**高度**方向，宽度全给课名 —— 这是窄格里更省的占位方式。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LedgerCourseCell(
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
    val shape = f.shapes.courseCard

    Box(
        Modifier
            .fillMaxSize()
            // 便签浮起：两层软阴影（HARD 无阴影）
            .cardFloat(f, if (t.isDark) Color(0xFF000000) else t.ink)
            .clip(shape)
            .background(if (isNow) t.primary.copy(alpha = 0.06f) else t.card)
            .border(
                width = if (isNow) 1.5.dp else f.ruleWidth,
                color = if (isNow) t.primary else t.outline.copy(alpha = 0.34f),
                shape = shape,
            )
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
            // 侵蚀：上过去的部分褪成纸色（同一门课，不编新信息）
            .then(
                if (progress > 0.02f) {
                    Modifier.drawWithContent {
                        drawContent()
                        val cut = size.height * progress.coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    t.paper.copy(alpha = 0.92f),
                                    t.paper.copy(alpha = 0.70f),
                                    t.paper.copy(alpha = 0.34f),
                                ),
                                startY = 0f,
                                endY = cut.coerceAtLeast(1f),
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, cut),
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶部色帽：3dp，宽度全给课名
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (isNow) 3.5.dp else 3.dp)
                    .background(if (isNow) t.primary else spine),
            )
            Column(Modifier.padding(start = 3.5.dp, top = 3.dp, end = 2.5.dp, bottom = 2.dp)) {
                Text(
                    course.name,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
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

@Composable
internal fun LedgerAxisRow(sections: String, startTime: String) {
    val t = InkTokens.current
    val f = Form.current
    Column(
        Modifier.fillMaxSize().padding(end = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            Modifier
                .width(30.dp)
                .height(f.pillSize)
                .clip(f.shapes.pill)
                .background(t.cardAlt),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                sections,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                fontFamily = f.numberFamily,
                color = t.inkSoft,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            startTime,
            fontSize = 8.5.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.End,
            letterSpacing = 0.3.sp,
            fontFamily = f.numberFamily,
            color = t.inkFaint,
        )
    }
}

/**
 * 分节抬头（上午/下午/晚上）：**填充色带**，不是线。
 *
 * 这是本主题对「时段分节」的答案 —— 旧编辑版在时段条垂直中央画了 1dp 横线，
 * PIL 实测落在真实行边界上方 24.5px（7.00dp），是「网格没对齐」观感的直接来源。
 * 填充条没有「线」的形状，**不可能**被读成一条偏了的行线。
 */
@Composable
internal fun LedgerBand(axisW: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, label: String) {
    val t = InkTokens.current
    val f = Form.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(t.cardAlt),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(axisW).fillMaxHeight().padding(end = 8.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                label,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                letterSpacing = 1.6.sp,   // 0.16em 抬头感
                fontFamily = f.displayFamily,
                color = t.inkSoft,
            )
        }
        // 抬头右侧留空：色带自己就是分隔，不再加任何线
    }
}

/**
 * 未排课 / 冲突 的一行：账页式便签条（12dp 圆角，无阴影 —— 它不是「钉在格子里」的）。
 * 触区：视觉 38dp + 上下 5dp padding = 48dp 命中区。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LedgerUnscheduledRow(c: Course, onClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    val fam = Tt.famOf(c.courseNo.ifBlank { c.name })
    val spine = Tt.spineOf(fam, t.isDark)
    val shape = f.shapes.card

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .clip(shape)
            .background(t.card)
            .border(f.ruleWidth, t.outline.copy(alpha = 0.30f), shape)
            .combinedClickable(
                onClickLabel = "查看 ${c.name} 详情",
                onClick = onClick,
                onLongClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(24.dp).clip(f.shapes.badge).background(spine))
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name,
                fontSize = 13.sp,
                lineHeight = 18.sp,
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

/** 「本周」徽：圆角胶囊 + 淡底（HARD 是实心块，编辑版是细框，这里是胶囊） */
@Composable
internal fun LedgerWeekStamp(isThisWeek: Boolean) {
    if (!isThisWeek) return
    val t = InkTokens.current
    val f = Form.current
    val ink = cn.dkarbon.enroute.ui.theme.primaryInk()
    val shape = f.shapes.pill
    Box(
        Modifier
            .clip(shape)
            .background(t.primary.copy(alpha = 0.12f))
            .border(1.dp, ink.copy(alpha = 0.55f), shape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            "本周",
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
            color = ink,
        )
    }
}
