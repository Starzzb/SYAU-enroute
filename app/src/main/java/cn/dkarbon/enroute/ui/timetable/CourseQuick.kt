package cn.dkarbon.enroute.ui.timetable

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.delay

/**
 * 长按卡片弹出的快捷菜单（2026-11-11）。
 *
 * ## 为什么长按要有菜单
 * 点卡片是「看」（详情浮层）；长按是「动手」——复制给同学、翻到本周、直接看周次。
 * 这两件事如果都塞进一个浮层，浮层就会变成一大坨按钮，反而慢。
 *
 * ## 形式
 * 三行实心条目（0 圆角、行间 1dp 线），每行「动作 + 一句它会做什么」。
 * 不做图标：40dp 的卡都看不清图标，菜单里也就别装点了 —— 字说清楚最快。
 */
@Composable
fun CourseQuickActions(
    course: Course,
    week: Int,
    todayWeek: Int,
    summer: Boolean,
    onDetail: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = InkTokens.current
    val key = course.courseNo.ifBlank { course.name }
    val fam = Tt.famOf(key)
    val spine = Tt.spineOf(fam, t.isDark)
    val wash = Tt.washOf(fam, t.isDark)
    val food = Food.of(key)
    val ghost = GhostForm.of(key)

    var entered by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(closing) {
        if (closing) {
            delay(180)
            onDismiss()
        }
    }
    val slide by animateDpAsState(
        targetValue = if (entered && !closing) 0.dp else 320.dp,
        animationSpec = tween(180),
        label = "quickSheet",
    )
    val close = { closing = true }

    val (startMin, endMin) = CourseFacts.rangeOf(course, summer)
    val navPad = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cb = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (closing) 0f else 0.30f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "关闭快捷菜单",
                    onClick = close,
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = slide)
                .fillMaxWidth()
                .background(t.paper)
                .border(1.5.dp, t.outline),
        ) {
            Box(Modifier.fillMaxWidth().height(5.dp).background(spine))

            // 这是哪门课（免得长按错了还不知道）
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(wash)
                        .border(Stroke.hairline, spine.copy(alpha = 0.28f)),
                    contentAlignment = Alignment.Center,
                ) {
                    FoodIcon(food = food, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        course.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(CourseFacts.weekdayName(course.weekday))
                            append(" ")
                            append(course.sectionsLabel)
                            if (course.room.isNotBlank()) append(" · ").append(course.room)
                        },
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GhostMark(form = ghost, color = spine, size = 22.dp, modifier = Modifier.size(22.dp))
            }

            Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))

            QuickRow(
                index = "01",
                title = if (copied) "已复制到剪贴板" else "复制课程信息",
                subtitle = CourseFacts.brief(course),
                onClick = {
                    cb.setText(AnnotatedString(CourseFacts.copyText(course, startMin, endMin)))
                    copied = true
                },
            )
            QuickRow(
                index = "02",
                title = "查看完整详情",
                subtitle = "周次矩阵 · 课程号 · 完整地点",
                onClick = {
                    onDetail()
                    close()
                },
            )

            // 取消
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "取消",
                        onClick = close,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", fontSize = 12.5.sp, color = t.inkFaint)
            }

            Spacer(Modifier.height(navPad + 4.dp))
        }
    }
}

/** 菜单里的一行：动作名 + 一句它到底做什么 */
@Composable
private fun QuickRow(
    index: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = title,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index,
            fontSize = 9.sp,
            fontFamily = cn.dkarbon.enroute.ui.theme.NumericFont,
            color = t.inkFaint,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = t.ink,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                fontSize = 9.5.sp,
                lineHeight = 13.sp,
                color = t.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
}
