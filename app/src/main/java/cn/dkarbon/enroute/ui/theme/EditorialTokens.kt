package cn.dkarbon.enroute.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 第三套视觉风格：**校报头版 / Editorial**（2026-11-14，票 17）。
 *
 * ## 为什么需要第三套
 * 前两套（[InkTokenSet] 硬朗农业 / `Scholar` 学院纸本）**只差配色** —— `Theme.kt` 换风格时只换
 * `InkTokenSet` 与 M3 `ColorScheme`，`HardShapes`（全 0 圆角）与 `EnRouteTypography` 是硬编码的，
 * 页面骨架也一行没变。所以「学院纸本」看起来就是同一个 App 换了张皮。
 *
 * 这一套换的是**结构语言**：去卡片容器、用版面线分区、标题上衬线、数字离开等宽。
 * 规格来源 `docs/评审-前端设计方案-v3.md` §5（方向 B）。
 *
 * ## 主人解禁（票 17）
 * 0 圆角 / 禁渐变 / 禁阴影 / 禁纹理 —— **本主题内全部失效**。
 * 但仍然不可破的三条：① 数据诚实（暂缺 / — / 不编数字）② 对比度 ≥4.5:1（大字 ≥3:1）
 * ③ 触区 ≥48dp。
 *
 * ## 与旧主题的关系（零回归的硬约束）
 * 本文件只做「新增」：
 * - 新的 `InkTokenSet` 实例（字段集与旧两套完全相同，业务代码零改动）
 * - 新的 `Typography`（只在 EDITORIAL 下用；旧主题继续用 `EnRouteTypography`）
 * - 新的 [FormSpec]（形态轴：圆角/阴影/描边/字族/数字字族/是否用卡片容器）。
 *   旧主题取值为「现状值」，且**旧页面不读它**，所以 `HARD` / `SCHOLAR` 渲染逐像素不变。
 *
 * ## 刻意不做
 * 不打包中文衬线字体（思源宋体 5MB+）。用 `FontFamily.Serif` 走系统回退（Noto Serif CJK）。
 * 若某些 ROM 没有 CJK 衬线，标题会回退成黑体 —— 此时「报头感」减弱，但**版式仍成立**：
 * 去卡片、版面线、字重对比、字距这些都不依赖字族（承压设计，见 v3 §5.5）。
 */
object Editorial {

    // ── 亮：新闻纸 + 墨 ──────────────────────────────────
    val Paper = Color(0xFFF7F4EC)       // 页面底（新闻纸，比学院纸本更暖一点）
    val Card = Color(0xFFFFFDF7)        // 面（少量使用：浮层、内嵌区块）
    val CardAlt = Color(0xFFEDEAE1)     // 次级面（表头、选中态）
    val Ink = Color(0xFF12100D)         // 正文墨 18.67:1
    val InkSoft = Color(0xFF3E3A34)     // 二级 11.10:1
    val InkFaint = Color(0xFF5C574F)    // 三级 7.04:1（10–11sp 小字也够 AA）
    val Rule = Color(0xFFC9C2B2)        // 版面分隔线（纯装饰，1.74:1）
    val Outline = Color(0xFF8A8375)     // 需要「可辨边界」时用 3.70:1（≥3:1）
    val Accent = Color(0xFF8C1D18)      // 印章红：唯一强调色 8.96:1
    val Info = Color(0xFF345A66)        // 教务 / 信息（钢青）7.37:1
    val Olive = Color(0xFF1F5140)       // 通过 / 完成 8.94:1
    val Amber = Color(0xFF6B4A0E)       // 临近 / 待办 7.91:1
    val Brick = Color(0xFF8C1D18)       // 错误 / 不及格：与 accent 同值（报纸只有一种红，
                                        // 区分靠形状：危险用反白块，强调用红字/红细线）

    // ── 暗：夜间印刷 ─────────────────────────────────────
    val PaperDark = Color(0xFF14120F)
    val CardDark = Color(0xFF1C1A15)
    val CardAltDark = Color(0xFF262219)
    val InkDark = Color(0xFFF4F1E8)     // 15.39:1
    val InkSoftDark = Color(0xFFC9C3B6) // 9.91:1
    val InkFaintDark = Color(0xFFA9A296)// 6.87:1
    val RuleDark = Color(0xFF38332A)    // 1.39:1（装饰）
    val OutlineDark = Color(0xFF7A7261) // 3.65:1（≥3:1；#6B6355 只有 2.93:1，不合格已弃用）
    val AccentDark = Color(0xFFE6907E)  // 7.17:1
    val InfoDark = Color(0xFFA7BFC9)    // 9.06:1
    val OliveDark = Color(0xFF8FBF9C)   // 8.36:1
    val AmberDark = Color(0xFFD6B26A)   // 8.64:1
}

/**
 * 衬线族：**系统 Serif**（Android 通用族名 `serif`，中文一般落到 Noto Serif CJK）。
 * 不打包字体，所以有回退风险 —— 见本文件头「刻意不做」。
 */
val SerifFont: FontFamily = FontFamily.Serif

// ── 两套 token 实例（字段与 [InkTokenSet] 完全对齐，业务代码零改动）──

val EditorialLightTokens = InkTokenSet(
    paper = Editorial.Paper,
    card = Editorial.Card,
    cardAlt = Editorial.CardAlt,
    ink = Editorial.Ink,
    inkSoft = Editorial.InkSoft,
    inkFaint = Editorial.InkFaint,
    rule = Editorial.Rule,
    brick = Editorial.Brick,
    slate = Editorial.Info,
    olive = Editorial.Olive,
    amber = Editorial.Amber,
    rust = Editorial.Accent,
    isDark = false,
    primary = Editorial.Accent,
    accent = Editorial.Accent,
    outline = Editorial.Outline,
    outlineStrong = Editorial.Ink,
    hardShadow = Editorial.Ink,
    info = Editorial.Info,
    infoBg = Editorial.CardAlt,
)

val EditorialDarkTokens = InkTokenSet(
    paper = Editorial.PaperDark,
    card = Editorial.CardDark,
    cardAlt = Editorial.CardAltDark,
    ink = Editorial.InkDark,
    inkSoft = Editorial.InkSoftDark,
    inkFaint = Editorial.InkFaintDark,
    rule = Editorial.RuleDark,
    brick = Editorial.AccentDark,
    slate = Editorial.InfoDark,
    olive = Editorial.OliveDark,
    amber = Editorial.AmberDark,
    rust = Editorial.AccentDark,
    isDark = true,
    primary = Editorial.AccentDark,
    accent = Editorial.AccentDark,
    outline = Editorial.OutlineDark,
    outlineStrong = Editorial.InkFaintDark,
    hardShadow = Color(0xFF000000),
    info = Editorial.InfoDark,
    infoBg = Editorial.CardDark,
)

// ── 排版：衬线标题 + 无衬线正文 + 衬线 tabular 数字 ─────────────

/**
 * 编辑排版风格的字号阶梯。
 *
 * 与 [EnRouteTypography] 的三个不同：
 * 1. 标题走 [SerifFont]（硬朗农业是系统黑体）
 * 2. 字号跨度更大（26/22/17/14/12），靠字号而非色块分层
 * 3. 正文行高 1.65（报纸正文比 UI 文案松），字距在标题上收紧 −0.2sp（大字号需要负字距才不散）
 */
private fun editorialStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
    family: FontFamily = FontFamily.Default,
    /** 数字：本主题改「衬线 tabular」，不再等宽 —— 与硬朗农业的 IBM Plex Mono 形成对比 */
    tabularNumbers: Boolean = false,
): TextStyle = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    fontFamily = family,
    fontFeatureSettings = if (tabularNumbers) "tnum" else "tnum, lnum",
)

val EditorialTypography = Typography(
    // 报头 / 主标题
    headlineLarge = editorialStyle(28, FontWeight.Bold, 34, -0.4, SerifFont),
    headlineMedium = editorialStyle(24, FontWeight.Bold, 30, -0.4, SerifFont),
    headlineSmall = editorialStyle(20, FontWeight.Bold, 26, -0.3, SerifFont),
    // 条目标题
    titleLarge = editorialStyle(18, FontWeight.Bold, 25, -0.2, SerifFont),
    titleMedium = editorialStyle(16, FontWeight.Bold, 23, -0.2, SerifFont),
    titleSmall = editorialStyle(14, FontWeight.Bold, 21, 0.0, SerifFont),
    // 正文（无衬线，行高 1.65）
    bodyLarge = editorialStyle(16, FontWeight.Normal, 26),
    bodyMedium = editorialStyle(14, FontWeight.Normal, 23, tabularNumbers = true),
    bodySmall = editorialStyle(12, FontWeight.Normal, 20),
    // 标签（小型大写感：加字距）
    labelLarge = editorialStyle(14, FontWeight.Bold, 20, 0.4),
    labelMedium = editorialStyle(12, FontWeight.Medium, 17, 1.0),
    labelSmall = editorialStyle(11, FontWeight.Medium, 16, 1.2, tabularNumbers = true),
)

/** 报头专用：`第 12 周` 那种大衬线数字 */
val EditorialDisplay = editorialStyle(26, FontWeight.Bold, 32, -0.6, SerifFont, tabularNumbers = true)

/** 表格/轴标签专用：衬线 tabular 数字（替代 [NumericFont]） */
val EditorialNumeric = editorialStyle(12, FontWeight.Normal, 16, 0.0, SerifFont, tabularNumbers = true)

/**
 * 编辑版的圆角：**小圆角**（2–3dp）。
 *
 * 报纸没有圆角，但 M3 的 Dialog / TextButton / Switch 在 0 圆角下会显得像贴纸；
 * 2dp 是「几乎看不出，但不像贴纸」的折中。旧两套仍用 [HardShapes]（全 0）。
 */
val EditorialShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(3.dp),
)

// ── 形态轴：[FormSpec] ───────────────────────────────────
//
// 颜色能靠换 token 解决，**圆角/阴影/描边/字族/布局开关换 token 做不到**（这两个值现在
// 硬编码在 Theme.kt 里）。所以单开一个与颜色并列的「形态轴」，由 LocalForm 提供。
//
// ⚠️ 铁律：`HARD` 与 `SCHOLAR` 取 [FormSpec.Hard]，取值 = 现状值；且**旧页面根本不读它**。
// 只有 EDITORIAL 分支（`if (Form.current.editorial)`）会读，所以老主题逐像素不变。

@Immutable
data class FormSpec(
    /** 是否启用编辑排版语汇（去卡片化、版面线、衬线标题） */
    val editorial: Boolean = false,
    /** 通用卡片圆角（编辑版 = 0：没有卡片） */
    val radiusCard: Dp = 0.dp,
    /** 课表课程卡圆角（现状 4dp 是主人点名的 CET6 局部例外，不能改） */
    val radiusCourseCard: Dp = 4.dp,
    /** 浮层 / 对话框圆角 */
    val radiusSheet: Dp = 0.dp,
    /** 普通分节线粗细 */
    val ruleWidth: Dp = 1.dp,
    /** 刊头双线 / 重线粗细 */
    val ruleHeavy: Dp = 2.dp,
    /** 纸张浮动阴影：半径（0 = 不用阴影） */
    val shadowRadius: Dp = 0.dp,
    /** 纸张浮动阴影：透明度 */
    val shadowAlpha: Float = 0f,
    /** 是否用卡片容器。false → 卡片退化为「分节 + 规则线」 */
    val usesCardContainer: Boolean = true,
    /** 展示字族（标题） */
    val displayFamily: FontFamily = FontFamily.Default,
    /** 数字字族（本主题 = 衬线 tabular，替掉 IBM Plex Mono） */
    val numberFamily: FontFamily = NumericFont,
    /** 标题字距（负值 = 收紧） */
    val titleTracking: TextUnit = 0.sp,
    /** 正文行高倍数（现状 1.5） */
    val bodyLeading: Float = 1.5f,
    /** 是否画纸纹（编辑版：极低对比度的横纹 + 噪点） */
    val paperTexture: Boolean = false,
    /** 触区下限 —— 三套主题都不许低于这个值（票 17 的硬约束之一） */
    val minTouch: Dp = 48.dp,
) {
    companion object {
        /** 硬朗农业 / 学院纸本共用（现状值，零回归） */
        val Hard = FormSpec()

        /** 校报头版：放开圆角/阴影/纹理，标题上衬线，数字去等宽 */
        val Editorial = FormSpec(
            editorial = true,
            radiusCard = 0.dp,
            radiusCourseCard = 0.dp,
            radiusSheet = 2.dp,
            ruleWidth = 1.dp,
            ruleHeavy = 2.dp,
            // 「纸张浮动」：只给浮层/刊头用，不铺到每一个块上
            shadowRadius = 6.dp,
            shadowAlpha = 0.10f,
            usesCardContainer = false,
            displayFamily = SerifFont,
            numberFamily = SerifFont,
            titleTracking = (-0.2).sp,
            bodyLeading = 1.65f,
            paperTexture = true,
        )

        /** 按 [StyleSet] 取值 */
        fun of(styleSet: Int): FormSpec = when (styleSet) {
            StyleSet.EDITORIAL -> Editorial
            else -> Hard   // HARD / SCHOLAR 都是形态现状值
        }
    }
}

val LocalForm = staticCompositionLocalOf { FormSpec.Hard }

/** 简写：`Form.current.editorial` */
object Form {
    val current: FormSpec
        @Composable
        @ReadOnlyComposable
        get() = LocalForm.current
}

// ── 编辑排版专用 Modifier ────────────────────────────────

/**
 * 纸张浮动阴影：低强度、只用于「纸片浮在桌面上」的语义（浮层 / 刊头卡）。
 * **不要给每一个列表块都加** —— 那会退回「卡片 App」的观感，本主题就没意义了。
 */
fun Modifier.paperFloat(spec: FormSpec, color: Color = Color(0xFF12100D)): Modifier =
    if (spec.shadowRadius <= 0.dp) this else this.drawBehind {
        val r = spec.shadowRadius.toPx()
        // 三层极淡矩形模拟柔和投影（不用 RenderEffect/blur：中端机零成本）
        repeat(3) { i ->
            val inset = r * i * 0.33f
            drawRect(
                color = color.copy(alpha = spec.shadowAlpha * (1f - i * 0.28f) / 3f),
                topLeft = Offset(0f, inset * 0.5f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height),
            )
        }
    }

/**
 * 版面线（报纸分区线）：比 `HorizontalDivider` 更「硬」——实心、不透明、不随主题灰化。
 * 编辑版用它替代卡片边界。
 */
fun Modifier.editorialRule(spec: FormSpec, color: Color, heavy: Boolean = false): Modifier =
    this.background(color).then(
        if (heavy) Modifier else Modifier,
    )

/**
 * 纸纹：极低对比度的横纹（报纸的纤维感）+ 稀疏墨点。
 * 只在静态容器上挂（整页一层），**不要挂在滚动列表的每一项**上。
 */
fun Modifier.paperGrain(color: Color, gap: Dp = 22.dp, alpha: Float = 0.045f): Modifier =
    this.drawBehind {
        val g = gap.toPx()
        var y = g
        while (y < size.height) {
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += g
        }
        // 稀疏墨点（固定相位，不随机 —— 重组不能闪）
        val stepX = size.width / 9f
        val stepY = g * 2.3f
        var cy = g
        var row = 0
        while (cy < size.height) {
            var cx = stepX * (0.5f + (row % 3) * 0.33f)
            while (cx < size.width) {
                drawCircle(
                    color = color.copy(alpha = alpha * 0.9f),
                    radius = 0.8f,
                    center = Offset(cx, cy),
                )
                cx += stepX
            }
            cy += stepY
            row++
        }
    }

// ── M3 scheme（Switch / TextButton / Dialog 这些组件还在用 M3 主色）──

val EditorialLightScheme = lightColorScheme(
    primary = Editorial.Accent,
    onPrimary = Color(0xFFFFFDF7),      // 9.11:1
    primaryContainer = Editorial.CardAlt,
    onPrimaryContainer = Editorial.Accent,
    secondary = Editorial.Info,
    onSecondary = Color(0xFFFFFDF7),
    secondaryContainer = Color(0xFFE4E8EA),
    onSecondaryContainer = Editorial.Info,
    tertiary = Editorial.Amber,
    onTertiary = Color(0xFFFFFDF7),
    tertiaryContainer = Color(0xFFF2EBDA),
    onTertiaryContainer = Editorial.Amber,
    error = Editorial.Brick,
    onError = Color(0xFFFFFDF7),
    errorContainer = Color(0xFFF5E0DB),
    onErrorContainer = Editorial.Brick,
    background = Editorial.Paper,
    onBackground = Editorial.Ink,
    surface = Editorial.Paper,
    onSurface = Editorial.Ink,
    surfaceVariant = Editorial.CardAlt,
    onSurfaceVariant = Editorial.InkSoft,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Editorial.Card,
    surfaceContainer = Editorial.Paper,
    surfaceContainerHigh = Editorial.CardAlt,
    surfaceContainerHighest = Color(0xFFE4E0D5),
    outline = Editorial.Outline,
    outlineVariant = Editorial.Rule,
    inverseSurface = Editorial.Ink,
    inverseOnSurface = Editorial.Card,
    inversePrimary = Editorial.AccentDark,
)

val EditorialDarkScheme = darkColorScheme(
    primary = Editorial.AccentDark,
    // 暗色强调色是浅朱，白字只有 2.3:1 —— 必须压成深底文字
    onPrimary = Color(0xFF1B0E0A),
    primaryContainer = Editorial.CardDark,
    onPrimaryContainer = Editorial.AccentDark,
    secondary = Editorial.InfoDark,
    onSecondary = Color(0xFF0C1116),
    secondaryContainer = Color(0xFF20303A),
    onSecondaryContainer = Editorial.InfoDark,
    tertiary = Editorial.AmberDark,
    onTertiary = Color(0xFF0C1116),
    tertiaryContainer = Color(0xFF33290F),
    onTertiaryContainer = Editorial.AmberDark,
    error = Editorial.AccentDark,
    onError = Color(0xFF2A0F0A),
    errorContainer = Color(0xFF4A1D15),
    onErrorContainer = Editorial.AccentDark,
    background = Editorial.PaperDark,
    onBackground = Editorial.InkDark,
    surface = Editorial.PaperDark,
    onSurface = Editorial.InkDark,
    surfaceVariant = Editorial.CardAltDark,
    onSurfaceVariant = Editorial.InkSoftDark,
    surfaceContainerLowest = Color(0xFF0E0D0A),
    surfaceContainerLow = Editorial.CardDark,
    surfaceContainer = Editorial.PaperDark,
    surfaceContainerHigh = Editorial.CardAltDark,
    surfaceContainerHighest = Color(0xFF2E2920),
    outline = Editorial.OutlineDark,
    outlineVariant = Editorial.RuleDark,
    inverseSurface = Editorial.InkDark,
    inverseOnSurface = Editorial.PaperDark,
    inversePrimary = Editorial.Accent,
)
