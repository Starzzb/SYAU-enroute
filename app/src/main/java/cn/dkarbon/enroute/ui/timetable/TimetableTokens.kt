package cn.dkarbon.enroute.ui.timetable

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * 课表页的局部 token（沿用全局暖纸+墨，不引入 Material 默认色）。
 *
 * 2026-10-27 暗夜修复（主人笔记第 9 条）：这些值以前是写死的浅色常量，
 * 系统切深色时不会变 —— 暗夜下课表还是浅纸底+深灰格线，看着像贴了张白纸。
 * 现在改为「按深浅取两套」，由 [rule] / [wash] 等在 @Composable 里读取。
 */
object Tt {

    /** 浅色：14% 墨格线 / 深色：14% 亮墨，否则暗底上完全看不见 */
    fun gridRule(dark: Boolean): Color =
        if (dark) Color(0x2EE8E2D6) else Color(0x24262221)

    /** 8% 墨：时段条分隔 */
    fun gridRuleSoft(dark: Boolean): Color =
        if (dark) Color(0x1AE8E2D6) else Color(0x14262221)

    /** 4.5% 砖红：今天列底 */
    fun todayWash(dark: Boolean): Color =
        if (dark) Color(0x14D9796A) else Color(0x0BA63D2F)

    /** 22% 砖红：今天列两侧竖线 */
    fun todayRule(dark: Boolean): Color =
        if (dark) Color(0x4DD9796A) else Color(0x38A63D2F)

    /** 55% 砖红：现在刻度 */
    fun nowTick(dark: Boolean): Color =
        if (dark) Color(0xB3D9796A) else Color(0x8CA63D2F)

    /**
     * 四色课程洗色：低彩度暖纸区，满彩只用在 2.5dp 竖脊。
     *
     * wash 用于浅色底，washDark 用于深色底 —— 之前 washDark 定义了却从没被用过，
     * 等于暗夜下把浅色 wash 直接铺在黑底上，卡片亮得刺眼。
     */
    data class Fam(val wash: Color, val spine: Color, val washDark: Color)

    val Fams = listOf(
        Fam(Color(0xFFF0E1D8), Color(0xFFA63D2F), Color(0xFF3A2620)),
        Fam(Color(0xFFDEE4E7), Color(0xFF3B5566), Color(0xFF232C31)),
        Fam(Color(0xFFE5E8DA), Color(0xFF5C6B3C), Color(0xFF2A2E20)),
        Fam(Color(0xFFEDE5D3), Color(0xFF9A6A16), Color(0xFF322A1B)),
    )

    /** 按课程号取色（改名不换色） */
    fun famOf(key: String): Fam = Fams[((key.hashCode() and 0x7fffffff) % Fams.size)]

    /** 该用哪一档洗色 */
    fun washOf(fam: Fam, dark: Boolean): Color = if (dark) fam.washDark else fam.wash

    /** 该用哪一档竖脊 */
    fun spineOf(fam: Fam, dark: Boolean): Color = if (dark) fam.wash else fam.spine
}

/**
 * 网格唯一度量对象：渲染和小人共用，避免坐标漂移。
 */
data class GridMetrics(
    val gridW: Dp,
    val rowH: Dp,
    val axisW: Dp = 36.dp,
    val bandH: Dp = 20.dp,
) {
    val colW: Dp get() = (gridW - axisW) / 7

    /** 第 d 列（1..7）的左边界 */
    fun xOfCol(d: Int): Dp = axisW + colW * (d - 1)

    /** 第 d 列中心 */
    fun centerXOfCol(d: Int): Dp = xOfCol(d) + colW / 2

    /** 第 i 行（0..5）的顶部 y，含时段条 */
    fun yOfRow(i: Int): Dp = rowH * i + bandH * bandsBefore(i)

    /** 第 i 行之前有几个时段条 */
    fun bandsBefore(i: Int): Int =
        BigSession.entries.take(i).map { it.part }.distinct().size
}

/**
 * 咬痕：用纸色小圆盖住裁切硬边，形成不规则缺口。
 * 相位由 floor(p*25) 决定，避免逐帧抖动。
 */
fun DrawScope.biteMarks(p: Float, paperColor: Color) {
    if (p <= 0.02f) return
    val phase = floor(p * 25f).toInt()
    val r = 2.8.dp.toPx()
    repeat(4) { i ->
        val x = size.width * (0.22f + i * 0.226f)
        val jitter = if ((phase + i) % 2 == 0) 1.2.dp.toPx() else -1.0.dp.toPx()
        drawCircle(paperColor, r, Offset(x, jitter))
    }
}

/** 吃过的痕迹：吃完后留在格子里的一道短划，而不是完全空白 */
@Composable
fun EatenTrace(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 3.dp, top = 4.dp)) {
        Box(
            Modifier
                .width(6.dp)
                .height(2.dp)
                .background(Color(0x59A63D2F), RoundedCornerShape(1.dp)),
        )
    }
}

/** 时段条：上午 / 下午 / 晚上 */
@Composable
fun BandRow(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(start = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(7.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 9.5.sp,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
    }
}

/** 左轴一行的标签：3-4 + 起止时刻 */
@Composable
fun AxisRowLabel(sections: String, startTime: String) {
    Column(
        Modifier.width(36.dp).fillMaxHeight().padding(top = 3.dp, end = 2.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            sections,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            startTime,
            fontSize = 8.5.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
    }
}

/** 网格空白格：完全透明，只靠格线说话 */
@Composable
fun EmptyCell(modifier: Modifier = Modifier) {
    Box(modifier)
}

/** 时段起始时刻（用于轴与小人定位） */
fun BigSession.startMinute(summer: Boolean = false): Int = SchoolClock.rangeOf(this, summer).first

fun BigSession.startMinuteStatic(): Int = when (this) {
    BigSession.S1 -> 8 * 60
    BigSession.S2 -> 10 * 60
    BigSession.S3 -> 13 * 60 + 30
    BigSession.S4 -> 15 * 60 + 30
    BigSession.S5 -> 18 * 60 + 30
    BigSession.S6 -> 20 * 60 + 20
}

fun BigSession.endMinute(summer: Boolean = false): Int = SchoolClock.rangeOf(this, summer).second

fun BigSession.endMinuteStatic(): Int = when (this) {
    BigSession.S1 -> 9 * 60 + 40
    BigSession.S2 -> 11 * 60 + 40
    BigSession.S3 -> 15 * 60 + 10
    BigSession.S4 -> 17 * 60 + 10
    BigSession.S5 -> 20 * 60 + 10
    BigSession.S6 -> 22 * 60
}

/**
 * 吞噬进度：0 = 完整，1 = 完全吃掉。
 * 夏季/冬季作息不同，所以起止时间要按 [summer] 取值。
 */
fun eatProgress(big: BigSession, nowMinutes: Int, summer: Boolean = false): Float {
    val (s, e) = SchoolClock.rangeOf(big, summer)
    return when {
        nowMinutes <= s -> 0f
        nowMinutes >= e -> 1f
        else -> (nowMinutes - s).toFloat() / (e - s)
    }
}
