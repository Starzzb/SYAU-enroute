package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.timetable.MathBackdropMotion
import cn.dkarbon.enroute.ui.timetable.TtCourseCell
import cn.dkarbon.enroute.ui.timetable.TtSlots
import cn.dkarbon.enroute.ui.timetable.backdropDrift
import cn.dkarbon.enroute.ui.timetable.drawTtBackdrop

/**
 * 设置页顶部的实时预览（m41 v4）。
 *
 * ## 为什么不是「缩小的示意格子」
 *
 * 主人 2026-11-23 原话：预览要和主课程页面一样大小，不许形变，可以等比例缩放。
 * 所以这里照抄真课表的几何（TimetableScreen 的 GridMetrics 口径）：
 *
 *   轴列 40 · 时段条 14 · 6 个大节 · 7 列 —— 列宽 = (393 - 40) / 7 ≈ 50
 *   行高 = 88 × 行距档（0.88 / 1.0 / 1.12），下限 64 dp
 *
 * 唯一的缩放是把 LocalDensity 按面板宽度等比压低（density × 面板宽/393），
 * 于是 dp、sp、线宽、圆角全都乘同一个系数 —— 宽高同一个系数，绝不分开拉，
 * 格子长宽比与真机一致，看到的形状就是真机上会出现的形状。
 * 用密度缩放而不是 graphLayer 的原因是：布局尺寸天然就是缩放后的尺寸，
 * 父容器高度自动等于内容高度，不需要谁去反算，就不可能留出一条空白。
 *
 * ## 假数据口径
 *
 * 课名用真课名（不是个人信息），教师一律「张三」，教室用「一101」这种格式 ——
 * 依据 AGENTS.md：原型与截图里的假数据不许出现真实姓名。
 */
@Composable
fun TtPreview(slots: TtSlots, modifier: Modifier = Modifier) {
    val headerH = when (slots.header) { 0 -> 20.dp; 2 -> 32.dp; else -> 26.dp }
    val rowH = (88.dp * when (slots.grid) { 0 -> 0.88f; 2 -> 1.12f; else -> 1f }).coerceAtLeast(64.dp)
    val bandH = 14.dp
    val weekH = 44.dp
    val gridH = headerH + bandH * 3 + rowH * 6

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val s = (maxWidth / PHONE_W).coerceAtMost(1f)
        // 内容按 393dp 自然排版（里面全是真 dp），外层报「缩放后的尺寸」，
        // 缩放交给内容自己的 graphicsLayer —— 这样父容器高度天然等于内容高度，
        // 既不会留空白，也不会把第 6 个大节裁掉。
        ScaledHeight(s) {
            Column(
                Modifier.width(PHONE_W).graphicsLayer {
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            ) {
                WeekLine(weekH)
                GridBody(slots, headerH, bandH, rowH, gridH)
            }
        }
    }
}

/**
 * 量一次内容的自然尺寸，向上报「自然尺寸 × 缩放比例」，内容照原样摆在左上角。
 *
 * 缩放必须由内容自己用 graphicsLayer 完成：若在这里用 placeWithLayer 的图层缩放，
 * 图层边界就是父容器的缩放后尺寸，超出部分会被裁掉（实测第 6 个大节整块消失）。
 */
@Composable
private fun ScaledHeight(scale: Float, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier) { measurables, _ ->
        val p = measurables.first().measure(Constraints())
        layout((p.width * scale).roundToInt(), (p.height * scale).roundToInt()) {
            p.place(0, 0)
        }
    }
}


/** 预览里的侵蚀进度：定值，就为了让侵蚀轴选哪一档看得出来 */
private const val PREVIEW_ERODE = 0.45f

/** 真机宽度：预览内部一律按这个宽度排版，再由外层等比缩到面板宽度 */
private val PHONE_W = 393.dp

@Composable
private fun WeekLine(h: Dp) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().height(h).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("第 12 周", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Box(Modifier.weight(1f))
        Text("11 月 23 – 29 日", fontSize = 10.sp, color = t.inkFaint)
    }
}

@Composable
private fun GridBody(slots: TtSlots, headerH: Dp, bandH: Dp, rowH: Dp, gridH: Dp) {
    val t = InkTokens.current
    val drift = backdropDrift(slots.bgMotion)
    val axisW = 40.dp
    Box(
        Modifier
            .fillMaxWidth()
            .height(gridH)
            .drawBehind {
                val aw = axisW.toPx()
                val rh = rowH.toPx()
                val hh = headerH.toPx()
                val colW = (size.width - aw) / 7f
                drawTtBackdrop(
                    style = slots.backdrop,
                    drift = drift,
                    axisW = aw,
                    colW = colW,
                    rowH = rh,
                    bandH = bandH.toPx(),
                    rule = t.rule,
                    band = if (t.isDark) t.cardAlt.copy(alpha = 0.34f) else t.cardAlt.copy(alpha = 0.55f),
                )
                // 横线：时段条与大节行交替处各一条，跨到左轴。
                // 顺序必须与 Column 里的行序一致：表头 / 上午条 / r1 r2 / 下午条 / r3 r4 / 晚上条 / r5 r6。
                var y = hh
                val bottom = size.height
                drawLine(t.rule, Offset(aw, y), Offset(size.width, y), 0.75.dp.toPx())
                val steps = floatArrayOf(
                    bandH.toPx(), rh, rh,
                    bandH.toPx(), rh, rh,
                    bandH.toPx(), rh, rh,
                )
                for (s in steps) {
                    y += s
                    if (y <= bottom + 0.5f) {
                        drawLine(t.rule, Offset(aw, y), Offset(size.width, y), 0.75.dp.toPx())
                    }
                }
                for (c in 0..7) {
                    val x = aw + colW * c
                    drawLine(
                        t.rule.copy(alpha = 0.6f),
                        Offset(x, hh),
                        Offset(x, bottom),
                        0.75.dp.toPx(),
                    )
                }
            },
    ) {
        // 超立方体（tess 轴）：预览里也要真的转，否则「淡线 / 亮线 / 怎么转」在设置页看不见效果。
        // 主课表那层吃滑动手势当 drive；设置页没有手势，跟随档就退成自转，
        // 定格档给一个恒定 drive（MathBackdropMotion 600ms 后自己停帧）。
        if (slots.tess != 0) {
            val frozen = remember { mutableFloatStateOf(0f) }
            MathBackdropMotion(
                modifier = Modifier.fillMaxSize(),
                color = if (t.isDark) t.ink else t.outlineStrong,
                alpha = if (slots.tess == 2) {
                    if (t.isDark) 0.26f else 0.18f
                } else {
                    if (t.isDark) 0.17f else 0.11f
                },
                drive = if (slots.tessMode == 3) frozen else null,
                driveScale = if (slots.tessMode == 2) -2f else 1f,
                orthographic = slots.tessMode == 4,
            )
        }
        Column(Modifier.fillMaxSize()) {
            DayHeader(headerH, axisW)
            Band("上午", bandH, axisW)
            LessonRow(0, rowH, axisW, slots)
            LessonRow(1, rowH, axisW, slots)
            Band("下午", bandH, axisW)
            LessonRow(2, rowH, axisW, slots)
            LessonRow(3, rowH, axisW, slots)
            Band("晚上", bandH, axisW)
            LessonRow(4, rowH, axisW, slots)
            LessonRow(5, rowH, axisW, slots)
        }
    }
}

@Composable
private fun DayHeader(h: Dp, axisW: Dp) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().height(h)) {
        Box(Modifier.width(axisW))
        for (i in 0 until 7) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(DAYS[i], fontSize = 11.sp, color = t.inkSoft)
            }
        }
    }
}

@Composable
private fun Band(label: String, h: Dp, axisW: Dp) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(axisW))
        Text(label, fontSize = 9.sp, color = t.inkFaint, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun LessonRow(row: Int, rowH: Dp, axisW: Dp, slots: TtSlots) {
    val t = InkTokens.current
    Row(Modifier.fillMaxWidth().height(rowH)) {
        Box(Modifier.width(axisW).fillMaxHeight(), contentAlignment = Alignment.TopStart) {
            Text(
                TIMES[row],
                fontSize = 9.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(start = 3.dp, top = 2.dp),
            )
        }
        for (d in 0 until 7) {
            val demo = DEMOS.firstOrNull { it.day == d && it.row == row }
            Box(Modifier.weight(1f).fillMaxHeight().padding(1.dp)) {
                if (demo != null) {
                    TtCourseCell(
                        course = demo.course,
                        // 预览不跑「小幽灵」，但侵蚀要看得见：给一个恒定的 0.45 进度，
                        // 上褪 / 斜线 / 整块淡三档才有差别（progress = 0 时三档长得一样）
                        progress = PREVIEW_ERODE,
                        slots = slots,
                        tint = slots.tone,
                        onClickLabel = "",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private val DAYS = arrayOf("一", "二", "三", "四", "五", "六", "日")
private val TIMES = arrayOf("08:00", "10:10", "13:30", "15:40", "19:00", "20:40")

private class Demo(val day: Int, val row: Int, val course: Course)

private val DEMOS = listOf(
    Demo(0, 0, Course(courseNo = "demo-0-0", name = "马克思主义基本原理", room = "一101", teacher = "张三")),
    Demo(1, 0, Course(courseNo = "demo-1-0", name = "不动产估价", room = "二606", teacher = "张三")),
    Demo(4, 1, Course(courseNo = "demo-4-1", name = "地球表层系统概论", room = "二610", teacher = "张三")),
    Demo(2, 2, Course(courseNo = "demo-2-2", name = "土地资源调查与评价", room = "三205", teacher = "张三")),
    Demo(3, 2, Course(courseNo = "demo-3-2", name = "国土空间规划", room = "二408", teacher = "张三")),
    Demo(1, 3, Course(courseNo = "demo-1-3", name = "概率论与数理统计", room = "一302", teacher = "张三")),
    Demo(5, 4, Course(courseNo = "demo-5-4", name = "土地政策学", room = "三118", teacher = "张三")),
)
