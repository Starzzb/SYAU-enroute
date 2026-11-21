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

    /** 14% 墨格线（浅）/ 18% 亮墨（暗）：普通网格的竖线与行线 */
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

    /**
     * 四色课程洗色：低彩度暖纸区，满彩只用在 2.5dp 竖脊。
     *
     * wash 用于浅色底，washDark 用于深色底 —— 之前 washDark 定义了却从没被用过，
     * 等于暗夜下把浅色 wash 直接铺在黑底上，卡片亮得刺眼。
     */
    /**
     * 一档课程配色：亮档 wash/spine、暗档 washDark/spineDark。
     *
     * `spineDark` 是 m39 新加的：旧代码暗档直接拿亮档 wash 当 spine（近白），
     * 2.5dp 白条在黑底上发光，比课名还亮。
     */
    data class Fam(
        val wash: Color,
        val spine: Color,
        val washDark: Color,
        val spineDark: Color = spine,
    )

    /**
     * 深色档三色（m39 重做，2026-11-21）。
     *
     * 旧值 `#3A2620 / #232C31 / #2A2E20 / #322A1B` 的问题（真机可见）：
     * 四块洗色亮度跨 18–20% 但色相跳得远，在黑底上像**四块不同亮度的补丁**，
     * 块与块的分界比网格线还抢眼 —— 暗处人眼先读亮度、后读色相，这是生理上的。
     *
     * 新口径：**同亮度、不同色相**。四档亮度差压到 ≤4%，只在色相上区分；
     * spine 也从「亮档 wash 反色（近白）」改成「同色系提亮到 65–70%」，
     * 让课名重新成为卡片里最亮的东西 —— 旧值的白 spine 反而抢字。
     */
    val Fams = listOf(
        Fam(Color(0xFFF0E1D8), Color(0xFFA63D2F), Color(0xFF2A1D18), Color(0xFFC4776A)),
        Fam(Color(0xFFDEE4E7), Color(0xFF3B5566), Color(0xFF1B2226), Color(0xFF7E9AA5)),
        Fam(Color(0xFFE5E8DA), Color(0xFF5C6B3C), Color(0xFF20241A), Color(0xFF93A57E)),
        Fam(Color(0xFFEDE5D3), Color(0xFF9A6A16), Color(0xFF262019), Color(0xFFB4956A)),
    )

    /** 按课程号取色（改名不换色） */
    fun famOf(key: String): Fam = Fams[((key.hashCode() and 0x7fffffff) % Fams.size)]

    /** 该用哪一档洗色 */
    fun washOf(fam: Fam, dark: Boolean): Color = if (dark) fam.washDark else fam.wash

    /** 该用哪一档竖脊 */
    fun spineOf(fam: Fam, dark: Boolean): Color = if (dark) fam.spineDark else fam.spine
}

/** 时段起始时刻（用于轴与小人定位） */
fun BigSession.startMinute(summer: Boolean = false): Int = SchoolClock.rangeOf(this, summer).first

fun BigSession.endMinute(summer: Boolean = false): Int = SchoolClock.rangeOf(this, summer).second

/**
 * 第 i 大节的行顶偏移，用「行高 × 行数 + 时段条高 × 条数」表示：
 * ```
 * rowTop(i) = rowH * first + bandH * second
 * ```
 *
 * ⚠️ 时段条是渲染在**该行前面**的 Column 子项，所以第一条大节（上午）顶上也有条：
 * `i=0 → (0, 1)`，`i=2 → (2, 2)`，`i=4 → (4, 3)`。
 * 早期只数「之前」的时段条（不含本行），Canvas 格线就整体上移一个 bandH，
 * 卡片和格线错开 —— 2026-11-13 真机验收抓到，见 [GridMetrics] 注释。
 *
 * 放在这里而不是 UI 文件里：纯 Int 逻辑，JVM 单测能直接钉死（GridGeometryTest）。
 */
internal fun gridRowTop(i: Int): Pair<Int, Int> =
    i to BigSession.entries.take(i + 1).map { it.part }.distinct().size

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
