package cn.dkarbon.enroute.ui.timetable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.PathEffect
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 课表格子（m41）：七条轴叠出来的渲染器。
 *
 * 与 [ErodedCard] 的分工：
 *   · 侵蚀那一条轴直接复用 [ErodedCard]（它的咬痕是 2026-11-17 主人点名调过的，
 *     不重写）；本文件只决定「要不要侵蚀、侵蚀成什么样」。
 *   · 其余六轴（形状 / 装饰 / 字段 / 角标 / 底色 / 动效）在这里叠。
 *
 * 硬约束（主人 m38 原话，别实现时忘了）：
 *   1. **侵蚀只褪色，不删卡**；2. **课名永远在最上层**；3. 数据缺就「暂缺 / —」。
 */
@Composable
fun TtCourseCell(
    course: Course,
    progress: Float,
    slots: TtSlots,
    tint: Int,
    onClickLabel: String,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val key = course.courseNo.ifBlank { course.name }
    val fam = Tt.famOf(key)

    val wash = when (tint) {
        1 -> t.cardAlt
        2 -> Color.Transparent
        else -> Tt.washOf(fam, t.isDark)
    }
    val spineColor = Tt.spineOf(fam, t.isDark)

    // 动效：呼吸只改「覆盖层」的透明度，不动卡片底色 —— 关掉它必须是像素级还原。
    val breathe = if (slots.motion == 0) 1f else {
        val tr = rememberInfiniteTransition(label = "cell")
        tr.animateFloat(
            initialValue = if (slots.motion == 2) 0.55f else 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(2600, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "breathe",
        ).value
    }

    val shape = shapeOf(slots.shape)
    val outlineOnly = slots.shape == 2 || slots.tone == 2
    val erodeProgress = when (slots.erosion) {
        0 -> 0f
        3 -> progress * 0.6f
        else -> progress
    }

    // 色块（shape = 3）是实底：字必须换成纸白，否则深底深字读不出来。
    // 这是原型 .cell.sh3 的口径（background: accent; color: #FFFDF7）。
    val block = slots.shape == 3
    val paperInk = Color(0xFFFFFDF7)
    val textMain = if (block) paperInk else t.ink
    val textSoft = if (block) paperInk.copy(alpha = 0.86f) else t.inkSoft
    val textFaint = if (block) paperInk.copy(alpha = 0.72f) else t.inkFaint

    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            if (slots.decor == 1) {
                Box(Modifier.width(2.5.dp).fillMaxHeight().background(spineColor))
            }
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(start = if (slots.decor == 1) 4.dp else 4.5.dp, top = 3.dp, end = 2.dp, bottom = 2.5.dp),
                verticalArrangement = if (slots.meta == 2) Arrangement.SpaceBetween else Arrangement.Top,
            ) {
                when (slots.meta) {
                    // 三行：课名 / 地点 / 教师
                    0 -> Column {
                        Name(course.name, textMain)
                        if (course.roomShort.isNotBlank()) {
                            Spacer(Modifier.height(1.dp))
                            Line(CourseFacts.shortPlace(course.roomShort), 8.sp, 9.5.sp, textSoft)
                        }
                        if (course.teacher.isNotBlank()) Line(course.teacher, 7.5.sp, 9.sp, textFaint)
                    }
                    // 同行：课名 + 「地点 · 教师」挤一行
                    1 -> Column {
                        Name(course.name, textMain)
                        val sub = listOf(
                            CourseFacts.shortPlace(course.roomShort).takeIf { it.isNotBlank() } ?: "",
                            course.teacher.takeIf { it.isNotBlank() } ?: "",
                        ).filter { it.isNotEmpty() }.joinToString(" · ")
                        if (sub.isNotEmpty()) Line(sub, 7.5.sp, 9.sp, textSoft, maxLines = 1)
                    }
                    // 底栏：课名贴顶，地点教师贴底
                    2 -> {
                        Name(course.name, textMain)
                        Column {
                            if (course.roomShort.isNotBlank()) {
                                Line(CourseFacts.shortPlace(course.roomShort), 7.5.sp, 9.sp, textSoft, maxLines = 1)
                            }
                            if (course.teacher.isNotBlank()) Line(course.teacher, 7.sp, 8.5.sp, textFaint, maxLines = 1)
                        }
                    }
                    // 只课名
                    else -> Name(course.name, textMain, maxLines = 2)
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        // 底色 + 形状 + 底纹装饰（淡底/斜纹画在文字之下）
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                // 色块是实底（原型 .cell.sh3）：底色取本门课的主色，字已换成纸白；
                // 实底再叠描边会显得脏，所以这一档不描边。
                .background(
                    when {
                        block -> spineColor
                        slots.tone == 2 -> Color.Transparent
                        else -> wash
                    },
                )
                .then(
                    when {
                        outlineOnly -> Modifier.border(1.dp, spineColor, shape)
                        block -> Modifier
                        else -> Modifier.border(
                            0.75.dp,
                            spineColor.copy(alpha = if (t.isDark) 0.55f else 0.30f),
                            shape,
                        )
                    },
                )
                // 便签（shape = 1）：左侧一条虚线缝，和方卡的实线边一眼能分开
                .then(if (slots.shape == 1) Modifier.dashLeft(spineColor) else Modifier)
                .decorLayer(slots.decor, spineColor, t.isDark)
                .then(if (slots.erosion == 2) Modifier.diagVeil(progress, spineColor) else Modifier),
        )

        // 侵蚀（上褪 / 整块淡）：复用 ErodedCard 抽出来的咬痕画法
        if (slots.erosion == 1 || slots.erosion == 3) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .then(erosionBrush(erodeProgress, t.isDark)),
            ) { body() }
        } else {
            Box(Modifier.fillMaxSize()) { body() }
        }

        // 角标
        when (slots.badge) {
            1 -> Box(Modifier.matchParentSize().foldCorner(spineColor))
            2 -> Box(Modifier.matchParentSize().stripeBand(spineColor))
        }

        // 呼吸：一层极淡的覆盖，参与动效但不动字
        if (slots.motion != 0) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(spineColor.copy(alpha = 0.06f * breathe)),
            )
        }
    }
}

/**
 * 底纹（backdrop 轴）：0 无 / 1 横线 / 2 田垄 / 3 复相 / 4 纯色。
 *
 * 底纹画在格线与卡片之下，永远不许吃掉字：最高一档「田垄」也只是隔行铺一层
 * cardAlt，不是实色块。bgMotion 打开时整层沿纵向缓移 —— 只移动底纹，不动格线，
 * 否则会看成「网格没对齐」。
 */
fun DrawScope.drawTtBackdrop(
    style: Int,
    drift: Float,
    axisW: Float,
    colW: Float,
    rowH: Float,
    bandH: Float,
    rule: Color,
    band: Color,
) {
    if (style == 0) return
    val shift = drift * rowH
    when (style) {
        1 -> {
            var y = shift
            while (y < size.height) {
                drawLine(rule, Offset(axisW, y), Offset(size.width, y), 1.dp.toPx())
                y += rowH
            }
        }
        2 -> {
            // 田垄：隔行铺一层，行高 = 大节行高；交错处也填，视觉上像整片垄沟
            var i = 0
            var y = -rowH + (shift % (rowH * 2))
            while (y < size.height) {
                if (i % 2 == 0) drawRect(band, Offset(axisW, y), Size(size.width - axisW, rowH))
                y += rowH
                i++
            }
        }
        3 -> {
            // 复相：3% 的细横线，比 1 档更密更淡，像纸的帘纹
            var y = shift
            while (y < size.height) {
                drawLine(rule.copy(alpha = 0.05f), Offset(axisW, y), Offset(size.width, y), 0.6.dp.toPx())
                y += rowH / 3f
            }
        }
        4 -> drawRect(band, Offset(axisW, 0f), Size(size.width - axisW, size.height))
    }
}

@Composable
fun backdropDrift(enabled: Boolean): Float {
    if (!enabled) return 0f
    val tr = rememberInfiniteTransition(label = "backdrop")
    return tr.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(48000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    ).value
}

@Composable
private fun Name(text: String, color: Color, maxLines: Int = 3) {
    Text(
        text,
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = color,
    )
}

@Composable
private fun Line(
    text: String,
    size: androidx.compose.ui.unit.TextUnit,
    line: androidx.compose.ui.unit.TextUnit,
    color: Color,
    maxLines: Int = 1,
) {
    Text(
        text,
        fontSize = size,
        lineHeight = line,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = color,
    )
}

/** 形状轴：方卡 / 便签 / 细线 / 色块 / 标签 */
fun shapeOf(shape: Int): Shape = when (shape) {
    0 -> RoundedCornerShape(4.dp)
    1 -> RoundedCornerShape(2.dp)
    2 -> RectangleShape
    3 -> RoundedCornerShape(2.dp)
    // 标签：右上角切一个 10dp 的斜口（原型 border-radius: 0 10px 2px 0）
    else -> RoundedCornerShape(topStart = 0.dp, topEnd = 10.dp, bottomEnd = 2.dp, bottomStart = 0.dp)
}

/** 装饰轴里「面」的部分：顶帽 / 角点 / 底线 / 淡底 / 斜纹。左脊由正文 Row 前那 2.5dp 承担。 */
private fun Modifier.decorLayer(decor: Int, color: Color, dark: Boolean): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    when (decor) {
        2 -> drawRect(color, Offset(0f, 0f), Size(w, 3.dp.toPx()))
        3 -> {
            val arm = w * 0.22f
            drawLine(color, Offset(w - arm, 0f), Offset(w, 0f), 2.dp.toPx())
            drawLine(color, Offset(w, 0f), Offset(w, arm), 2.dp.toPx())
        }
        4 -> drawRect(color, Offset(0f, h - 1.5.dp.toPx()), Size(w, 1.5.dp.toPx()))
        5 -> drawRect(color.copy(alpha = if (dark) 0.16f else 0.10f), Offset.Zero, Size(w, h))
        6 -> {
            // 斜纹：三根 45° 细线，压在文字之下，透明度低到不影响可读
            val step = w / 3f
            for (i in 0 until 3) {
                val x = step * (i + 0.5f)
                drawLine(
                    color.copy(alpha = if (dark) 0.20f else 0.13f),
                    Offset(x, 0f),
                    Offset(x + h * 0.6f, h),
                    3.dp.toPx(),
                )
            }
        }
    }
}

/** 侵蚀轴 = 斜线：一条从左上切到右下的半透明带，跟随进度右移 */
/** 形状轴的「便签」：左边一条虚线缝 */
private fun Modifier.dashLeft(color: Color): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    val inset = 1.dp.toPx()
    drawLine(
        color.copy(alpha = 0.8f),
        Offset(inset, inset),
        Offset(inset, size.height - inset),
        stroke,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
    )
}

private fun Modifier.diagVeil(progress: Float, color: Color): Modifier = drawBehind {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f) return@drawBehind
    val w = size.width
    val h = size.height
    val bandW = w * 0.55f
    val x = -bandW + (w + bandW) * p
    drawPath(
        Path().apply {
            moveTo(x, 0f)
            lineTo(x + bandW, 0f)
            lineTo(x + bandW - h * 0.5f, h)
            lineTo(x - h * 0.5f, h)
            close()
        },
        color.copy(alpha = 0.18f),
    )
}

/** 角标 = 折角：右上角一块 11dp 三角 */
private fun Modifier.foldCorner(color: Color): Modifier = drawBehind {
    val a = 11.dp.toPx()
    drawPath(
        Path().apply {
            moveTo(size.width - a, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, a)
            close()
        },
        color.copy(alpha = 0.85f),
    )
}

/** 角标 = 斜纹带：右上角两条 45° 短斜线，只配「只课名」才不压字 */
private fun Modifier.stripeBand(color: Color): Modifier = drawBehind {
    val a = 9.dp.toPx()
    for (i in 0 until 2) {
        val off = a * (i + 1)
        drawLine(
            color.copy(alpha = 0.75f),
            Offset(size.width - off, 0f),
            Offset(size.width, off),
            1.5.dp.toPx(),
        )
    }
}
