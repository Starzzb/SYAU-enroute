package cn.dkarbon.enroute.ui.theme

import cn.dkarbon.enroute.ui.theme.NumericFont

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 硬朗农业设计系统（2026-11-10 三位设计专家评审后定稿）。
 *
 * ## 一句话立场
 * 把「纸、毛笔、细线」换成「**黑土、麦秆、农机漆、田垄网格**」。
 * 硬朗 = **0 圆角 + 1~2dp 实心边线 + 无渐变 + 无阴影 + 等宽数字 + 4dp 栅格**。
 *
 * ## 三条红线（任何一条破了就是回退）
 * 1. **圆角一律 0**（唯一例外：头像圆、6dp 状态点）
 * 2. **不出现渐变、不出现 elevation 阴影**（要阴影只用 [hardShadow] 的 2dp 硬投影）
 * 3. **数字全部 tabular + 等宽**（课表本质是数据表，不是文案）
 *
 * ## 配色来源（农业意象，不是「农庄插画」）
 * 黑土 Soil / 农械橘 Machine / 麦秆黄 Wheat / 阜绿 Crop / 锈红 Rust / 钢青 Steel。
 * 橙色只占小面积（色条、图标、主按钮），大面积是黑土与纸白 —— 这是「工业农业」不是「草绿大圆角」。
 */
object Soil {
    val n0 = Color(0xFFFFFFFF)
    val n50 = Color(0xFFF5F2EC)
    val n100 = Color(0xFFEDE8DF)
    val n200 = Color(0xFFD7D0C4)
    val n300 = Color(0xFFBEB5A6)
    val n400 = Color(0xFFA1978A)
    val n500 = Color(0xFF7E7468)
    val n600 = Color(0xFF5F574C)
    val n700 = Color(0xFF443E36)
    val n800 = Color(0xFF2B2621)
    val n900 = Color(0xFF1A1611)
    val n950 = Color(0xFF100E0B)
}

/** 农械橘：拖拉机涂装那种橙，是主色 */
object Machine {
    val n50 = Color(0xFFFCEFE7)
    val n100 = Color(0xFFF9DCCA)
    val n200 = Color(0xFFF3B994)
    val n300 = Color(0xFFEA8F5E)
    val n400 = Color(0xFFDE6A2E)
    val n500 = Color(0xFFC74E17)
    val n600 = Color(0xFFA83D11)
    val n700 = Color(0xFF852F0D)
    val n800 = Color(0xFF61230A)
    val n900 = Color(0xFF421807)
}

/** 麦秆黄：晒干的麦秸，不是柠檬黄 */
object Wheat {
    val n50 = Color(0xFFFAF4DF)
    val n100 = Color(0xFFF4E7B9)
    val n200 = Color(0xFFE9D183)
    val n300 = Color(0xFFDDB84D)
    val n400 = Color(0xFFD9A62B)
    val n500 = Color(0xFFB4841B)
    val n600 = Color(0xFF8F6713)
    val n700 = Color(0xFF6B4C10)
    val n800 = Color(0xFF4B350D)
    val n900 = Color(0xFF32230A)
}

/** 阜绿：橄榄绿（庄稼成熟前的深绿），不是草绿 */
object Crop {
    val n50 = Color(0xFFEFF2E6)
    val n100 = Color(0xFFDCE2C6)
    val n200 = Color(0xFFBCC79B)
    val n300 = Color(0xFF93A46B)
    val n400 = Color(0xFF6D8148)
    val n500 = Color(0xFF4E6535)
    val n600 = Color(0xFF3F5A2E)
    val n700 = Color(0xFF324824)
    val n800 = Color(0xFF25361B)
    val n900 = Color(0xFF1A2613)
}

/** 锈红：不及格、错误 */
object RustRed {
    val n50 = Color(0xFFFBEAE6)
    val n100 = Color(0xFFF4CDC4)
    val n200 = Color(0xFFE7A093)
    val n300 = Color(0xFFD46B58)
    val n400 = Color(0xFFC0452F)
    val n500 = Color(0xFF9B2417)
    val n600 = Color(0xFF7E1C12)
    val n700 = Color(0xFF60150E)
}

/** 钢青：教务系统 / 信息提示 */
object Steel {
    val n50 = Color(0xFFE9EFF0)
    val n100 = Color(0xFFCDDADD)
    val n200 = Color(0xFFA0B6BB)
    val n300 = Color(0xFF6E8D94)
    val n400 = Color(0xFF46666E)
    val n500 = Color(0xFF345A66)
    val n600 = Color(0xFF2A4850)
    val n700 = Color(0xFF1F353B)
}

/**
 * 课程色环（6 色轮转）。
 *
 * 规矩：色块内文字**永远用 ink**，不用彩色文字；彩色只出现在左侧 3dp 色条。
 * 这样 8 节课连排时不会变成调色盘，色盲/灰度截图也还能靠色条位置区分。
 */
val CoursePalette: List<Color> = listOf(
    Machine.n500,  // 农械橘
    Color(0xFF96700F), // 麦秆黄（压深，保证白底可读）
    Crop.n600,     // 阜绿
    Color(0xFF2C5A56), // 田水青
    Color(0xFF5A3A46), // 壤紫褐
    Color(0xFF37516B), // 农机蓝灰
)

/** 课程底色：主色 12%（亮）/ 22%（暗）叠在 surface 上 */
fun courseTint(base: Color, dark: Boolean): Color =
    base.copy(alpha = if (dark) 0.22f else 0.12f)

/** 线宽档位。1dp 必须是整 dp，否则某些 DPI 会发灰 */
object Stroke {
    val hairline = 1.dp
    val strong = 1.5.dp
    val emphasis = 2.dp
    val accentBar = 3.dp
    val accentBarThick = 4.dp
}

/** 间距：基准 4dp，节奏 8dp。只允许这几个值 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp
    val gutter = 16.dp
    val cardPad = 16.dp
    val cardGap = 12.dp
    val appBar = 56.dp
    val bottomBar = 80.dp
    val minTouch = 48.dp
    val timeColumn = 44.dp
}

/** 动效：全部 ≤240ms，无回弹 */
object Motion {
    const val fast = 120
    const val base = 180
    const val slow = 240
}

/** 全 0 圆角 —— 这是「硬朗」的第一道门 */
val HardShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

// ── 字体 ────────────────────────────────────────────────────

/**
 * 数字/时间的等宽字体族 —— **打包进 APK 的 IBM Plex Mono**（OFL 许可，约 230KB）。
 *
 * 为什么不像正文那样用系统字体：系统 monospace 在不同 ROM 上的 CJK 回退不可控，
 * 而课表的时间列、成绩、学分必须**竖向对齐**才算「硬朗」。等宽数字是这类界面的骨架。
 */
val NumericFont: FontFamily = FontFamily(
    androidx.compose.ui.text.font.Font(cn.dkarbon.enroute.R.font.ibm_plex_mono_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(cn.dkarbon.enroute.R.font.ibm_plex_mono_semibold, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(cn.dkarbon.enroute.R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

/**
 * 字号阶梯。规矩：
 *   · 字重只允许 400 / 500 / 700 / 800，**禁 300 及以下**（细字＝文艺，与硬朗冲突）
 *   · 数字带 `tnum, lnum`，保证课表时间、学分、成绩竖向对齐
 */
private fun hardStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
    family: FontFamily = FontFamily.Default,
): TextStyle = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    fontFamily = family,
    fontFeatureSettings = "tnum, lnum",
)

val EnRouteTypography = Typography(
    labelSmall = hardStyle(11, FontWeight.Medium, 15, 0.5),
    labelMedium = hardStyle(12, FontWeight.Medium, 16, 0.2),
    labelLarge = hardStyle(14, FontWeight.Bold, 20, 0.5),
    bodySmall = hardStyle(12, FontWeight.Normal, 18),
    bodyMedium = hardStyle(14, FontWeight.Normal, 21),
    bodyLarge = hardStyle(16, FontWeight.Normal, 24),
    titleSmall = hardStyle(14, FontWeight.Bold, 20, 0.2),
    titleMedium = hardStyle(16, FontWeight.Bold, 22),
    titleLarge = hardStyle(18, FontWeight.Bold, 24, -0.1),
    headlineSmall = hardStyle(20, FontWeight.Bold, 26, -0.2),
    headlineMedium = hardStyle(24, FontWeight.ExtraBold, 28, -0.4),
)

/** 9sp 微字（课表格内节次、角标），M3 没有对应 role */
val LabelMicro = hardStyle(9, FontWeight.Medium, 12, 0.6)

// ── M3 ColorScheme ─────────────────────────────────────────

val HardLightScheme = lightColorScheme(
    primary = Machine.n500,
    onPrimary = Color.White,
    primaryContainer = Machine.n100,
    onPrimaryContainer = Machine.n900,
    secondary = Wheat.n600,
    onSecondary = Color.White,
    secondaryContainer = Wheat.n100,
    onSecondaryContainer = Wheat.n800,
    tertiary = Crop.n600,
    onTertiary = Color.White,
    tertiaryContainer = Crop.n100,
    onTertiaryContainer = Crop.n800,
    error = RustRed.n500,
    onError = Color.White,
    errorContainer = RustRed.n100,
    onErrorContainer = RustRed.n600,
    background = Soil.n50,
    onBackground = Soil.n900,
    surface = Soil.n50,
    onSurface = Soil.n900,
    surfaceVariant = Soil.n100,
    onSurfaceVariant = Soil.n600,
    surfaceContainerLowest = Soil.n0,
    surfaceContainerLow = Color(0xFFFAF8F4),
    surfaceContainer = Soil.n50,
    surfaceContainerHigh = Soil.n100,
    surfaceContainerHighest = Color(0xFFE4DED2),
    outline = Soil.n300,
    outlineVariant = Soil.n200,
    inverseSurface = Soil.n800,
    inverseOnSurface = Soil.n100,
    inversePrimary = Machine.n200,
)

val HardDarkScheme = darkColorScheme(
    primary = Machine.n300,
    onPrimary = Soil.n950,
    primaryContainer = Machine.n900,
    onPrimaryContainer = Machine.n200,
    secondary = Wheat.n300,
    onSecondary = Wheat.n900,
    secondaryContainer = Wheat.n800,
    onSecondaryContainer = Wheat.n100,
    tertiary = Crop.n200,
    onTertiary = Crop.n900,
    tertiaryContainer = Crop.n700,
    onTertiaryContainer = Crop.n100,
    error = RustRed.n200,
    onError = RustRed.n700,
    errorContainer = RustRed.n600,
    onErrorContainer = RustRed.n100,
    background = Soil.n950,
    onBackground = Soil.n100,
    surface = Soil.n950,
    onSurface = Soil.n100,
    surfaceVariant = Soil.n800,
    onSurfaceVariant = Soil.n400,
    surfaceContainerLowest = Color(0xFF0B0907),
    surfaceContainerLow = Color(0xFF15110E),
    surfaceContainer = Soil.n900,
    surfaceContainerHigh = Soil.n800,
    surfaceContainerHighest = Soil.n700,
    outline = Soil.n700,
    outlineVariant = Color(0xFF3E362C),
    inverseSurface = Soil.n100,
    inverseOnSurface = Soil.n800,
    inversePrimary = Machine.n500,
)

// ── 三个硬朗 Modifier ───────────────────────────────────────

/**
 * 硬投影：不用 elevation，用偏移 2dp、模糊 0 的实心块。
 * 这是「印刷/钢牌」的质感，Material 的柔和阴影在硬朗体系里是噪音。
 */
fun Modifier.hardShadow(color: Color, dx: Dp = 2.dp, dy: Dp = 2.dp): Modifier =
    this.drawBehind {
        drawRect(color, topLeft = Offset(dx.toPx(), dy.toPx()), size = size)
    }

/** 田垄线：等距横线，空状态/占位用 */
fun Modifier.furrows(color: Color, gap: Dp = 12.dp): Modifier = this.drawBehind {
    val g = gap.toPx()
    var y = 0f
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += g
    }
}

/**
 * 侵蚀进度：从底部向上画 45° 斜纹，高度 = 已过去的比例。
 * 课表格子用它表示「这节课已经上掉多少」，纯函数、不依赖动画。
 */
fun Modifier.erosion(p: Float, lineColor: Color, fillColor: Color): Modifier = this.drawWithCache {
    val h = size.height * p.coerceIn(0f, 1f)
    val path = Path().apply {
        var x = -size.height
        while (x < size.width + size.height) {
            moveTo(x, size.height)
            lineTo(x + h, size.height - h)
            x += 6.dp.toPx()
        }
    }
    onDrawBehind {
        if (h <= 0f) return@onDrawBehind
        drawRect(fillColor, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
        drawPath(path, lineColor, style = Stroke(width = 1.dp.toPx()))
        drawLine(
            lineColor,
            Offset(0f, size.height - h),
            Offset(size.width, size.height - h),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** 虚线（「已取消 / 失效」态用），6-6 间隔 */
fun dashedEffect(): PathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
