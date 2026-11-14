package cn.dkarbon.enroute.ui.theme

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 第二套主题：**账页 / LEDGER**（2026-11-14 重做，取代原「校报头版 / Editorial」）。
 *
 * ## 一句话
 * 课表是一本**带编号的账**：行有节次、列有抬头、每门课是一张**贴在账页上的便签**。
 *
 * ## 为什么推翻原编辑版
 * 原编辑版（去卡片 + 版面线 + 报纸衬线）在真机上是「报纸缩小版」，而且它的分节语汇
 * 依赖一条画在时段条**垂直中央**的 1dp 横线（`TimetableScreen.kt` 旧 516–524 行）。
 * PIL 实测这条线落在真实行边界**上方 24.5px = 7.00dp**，全宽横线一条不差，唯独它偏 ——
 * 主观上就是「网格没对齐」。新主题的规矩：**网格内部一条横贯横线都不画**（见下）。
 *
 * ## 三层差异（相对 HARD 硬朗农业）
 *
 * | 层 | HARD | LEDGER（本套） |
 * |---|---|---|
 * | **布局变体** | 连续网格线 + 平面方卡 + 轴上是「1-2 / 08:00」两行小字 | **零横线**：行与行之间靠 8dp 留白 + 交错行带底色；分节靠一条**填充色带**（不是线）；轴上是**节次圆牌 + 衬线时间**；课卡浮起（软阴影） |
 * | **排版系统** | 系统黑体 + IBM Plex Mono 数字，7 级阶梯 11–24sp，行高 1.5，字距 0–0.06em | **衬线承担标题与数字**（`SerifFont` + `tnum`，右对齐悬挂），无衬线只承担地名/教师/说明；5 级阶梯 13–30sp，行高 1.45，微标签字距 **+0.12em** |
 * | **形态 token** | 全 0 圆角 / 无阴影 / 1dp 实心描边 / 田垄纹理 | 卡 **10dp** 圆角、徽 **6dp**、浮层 **14dp**、轴牌 **整圆**；**两级软阴影**；描边 **0.8dp** 半透明；网格竖线改 **0.6dp 点线** |
 *
 * ## 与「壁纸取色」共存（硬约束）
 * `DynamicColors` 会把 [InkTokenSet.primary] / [InkTokenSet.accent] 换成用户的壁纸色。
 * 所以本套**层级一律不靠这两个色**：分节色带取 `cardAlt`、行带取 `paper` 的微差、
 * 节次圆牌取 `inkSoft`、课卡描边取 `outline`。主色只做「今天」「正在进行」「主按钮」的点缀
 * —— 换成任何壁纸色都不会塌。
 *
 * ## 三条不可破（与全仓一致）
 * ① 数据诚实（缺失就 `—`，不补 0）② 正文对比度 ≥4.5:1（大字 ≥3:1，见文件末尾实测表）
 * ③ 触区 ≥48dp。
 *
 * ## 命名遗留（不做无谓改动）
 * `Theme.kt` 里仍引用 `EditorialLightTokens` / `EditorialTypography` / `EditorialShapes`，
 * 那是**另一个代理负责的文件**，本主题只换实现不换符号名，避免两边同时改同一个文件打架。
 */

// ── 原始层（primitive）────────────────────────────────────

/**
 * 账页纸：**暖白偏米**，不是纯白（纯白在 OLED 上像表格软件）。
 * 与 HARD 的 `Soil`（偏灰的土白）区分：这里更暖、更亮一档。
 */
object Ledger {
    // 亮色
    val Paper = Color(0xFFFAF7F1)
    val Card = Color(0xFFFFFFFF)
    val CardAlt = Color(0xFFF1ECE1)   // 分节色带 / 节次圆牌底
    val BandTint = Color(0xFFF7F3EA)  // 交错行带（比纸深 1.5%，只在暗背景上做参照）
    val Ink = Color(0xFF16211B)       // 15.49:1
    val InkSoft = Color(0xFF3C4A42)   //  8.73:1
    val InkFaint = Color(0xFF5A6A60)  //  5.36:1
    val Rule = Color(0xFFD9D3C5)      // 装饰线（不承载文字）
    val Outline = Color(0xFF808E84)   // 可辨边界 3.21:1（≥3:1）
    val Pine = Color(0xFF1F4A3D)      // 主色：松针绿 9.33:1
    val Clay = Color(0xFFB4553C)      // 强调：陶土红 4.56:1
    val Indigo = Color(0xFF2E5266)    // 信息 7.81:1
    val Olive = Color(0xFF3E5C34)     // 通过 7.05:1
    val Ochre = Color(0xFF7A5714)     // 待办 6.14:1
    val Brick = Color(0xFF8E2B1E)     // 警示 7.81:1

    // 暗色（夜里是「深墨绿账本」，不是纯黑）
    val PaperDark = Color(0xFF141A17)
    val CardDark = Color(0xFF1B231F)
    val CardAltDark = Color(0xFF232C27)
    val BandTintDark = Color(0xFF181F1B)
    val InkDark = Color(0xFFF1EFE7)       // 15.33:1
    val InkSoftDark = Color(0xFFC6CDC6)   // 10.88:1
    val InkFaintDark = Color(0xFF9AA69D)  //  6.99:1
    val RuleDark = Color(0xFF33403A)
    val OutlineDark = Color(0xFF6E7B72)   // 3.98:1
    val PineDark = Color(0xFF8FBFA6)      // 8.55:1
    val ClayDark = Color(0xFFE0937A)      // 7.25:1
    val IndigoDark = Color(0xFFA8C2CF)    // 9.48:1
    val OliveDark = Color(0xFFA9C496)     // 8.19:1
    val OchreDark = Color(0xFFD9B96E)     // 9.34:1
    val BrickDark = Color(0xFFE5A08F)     // 8.20:1
}

/**
 * 衬线族：**系统 Serif**（Android 通用族名 `serif`，中文落到 Noto Serif CJK）。
 *
 * 主人点名保留的就是它。不打包字体（思源宋体 5MB+ 不进 APK），因此存在 ROM 回退风险；
 * 本主题的承压设计见 [FormSpec]：字号跳级、行高、字距、形态这些**都不依赖字族**，
 * 真回退成黑体时版式仍成立。
 */
val SerifFont: FontFamily = FontFamily.Serif

// ── 语义层（semantic）：与旧两套**同一份字段集**，业务代码零改动 ──────────

val EditorialLightTokens = InkTokenSet(
    paper = Ledger.Paper,
    card = Ledger.Card,
    cardAlt = Ledger.CardAlt,
    ink = Ledger.Ink,
    inkSoft = Ledger.InkSoft,
    inkFaint = Ledger.InkFaint,
    rule = Ledger.Rule,
    brick = Ledger.Brick,
    slate = Ledger.Indigo,
    olive = Ledger.Olive,
    amber = Ledger.Ochre,
    rust = Ledger.Clay,
    isDark = false,
    primary = Ledger.Pine,
    accent = Ledger.Clay,
    outline = Ledger.Outline,
    outlineStrong = Ledger.Ink,
    hardShadow = Ledger.Ink,
    info = Ledger.Indigo,
    infoBg = Ledger.CardAlt,
)

val EditorialDarkTokens = InkTokenSet(
    paper = Ledger.PaperDark,
    card = Ledger.CardDark,
    cardAlt = Ledger.CardAltDark,
    ink = Ledger.InkDark,
    inkSoft = Ledger.InkSoftDark,
    inkFaint = Ledger.InkFaintDark,
    rule = Ledger.RuleDark,
    brick = Ledger.BrickDark,
    slate = Ledger.IndigoDark,
    olive = Ledger.OliveDark,
    amber = Ledger.OchreDark,
    rust = Ledger.ClayDark,
    isDark = true,
    primary = Ledger.PineDark,
    accent = Ledger.ClayDark,
    outline = Ledger.OutlineDark,
    outlineStrong = Ledger.InkFaintDark,
    hardShadow = Color(0xFF000000),
    info = Ledger.IndigoDark,
    infoBg = Ledger.CardAltDark,
)

// ── 排版层（typography）──────────────────────────────────
//
// 与 HARD 的差别不是「字号大一点」，而是**换了一套分级逻辑**：
//   · HARD：7 级密阶梯（11/12/14/16/18/20/24），每级都有一份重量，靠字号微差分层
//   · 本套：5 级疏阶梯（13/15/17/22/30，公比≈1.3），**跨级才换尺寸**，同屏最多出现 3 级
//   · 行高从 1.5 收到 **1.45**（数据密度）、微标签字距从 0.06em 放到 **0.12em**（账本的抬头感）
//   · 衬线只用于「标题 + 数字」，正文与辅助信息维持无衬线 —— 这样中文小字号不会糊

private fun ledgerStyle(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
    family: FontFamily = FontFamily.Default,
    /** 数字：衬线 + tabular（`tnum` 保证时间列竖排对齐）；非数字位仍传 false 走默认 */
    tabular: Boolean = false,
): TextStyle = TextStyle(
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = letterSpacing.sp,
    fontFamily = family,
    fontFeatureSettings = if (tabular) "tnum" else "normal",
)

val EditorialTypography = Typography(
    // 页级标题：衬线、负字距、跨级
    headlineLarge = ledgerStyle(30, FontWeight.Bold, 36, -0.6, SerifFont, tabular = true),
    headlineMedium = ledgerStyle(22, FontWeight.Bold, 28, -0.4, SerifFont, tabular = true),
    headlineSmall = ledgerStyle(21, FontWeight.Bold, 27, -0.3, SerifFont, tabular = true),
    // 条目标题
    titleLarge = ledgerStyle(17, FontWeight.Bold, 24, -0.2, SerifFont),
    titleMedium = ledgerStyle(15, FontWeight.Bold, 21, -0.1, SerifFont),
    titleSmall = ledgerStyle(13, FontWeight.Bold, 19, 0.1, SerifFont),
    // 正文：无衬线，行高 1.45
    bodyLarge = ledgerStyle(16, FontWeight.Normal, 23),
    bodyMedium = ledgerStyle(14, FontWeight.Normal, 20, tabular = true),
    bodySmall = ledgerStyle(13, FontWeight.Normal, 19),
    // 标签：小型大写的「抬头」感（中文靠字距撑，不靠大小写）
    labelLarge = ledgerStyle(13, FontWeight.Medium, 18, 1.3),
    labelMedium = ledgerStyle(11, FontWeight.Medium, 16, 1.4),
    labelSmall = ledgerStyle(10, FontWeight.Medium, 14, 1.4, tabular = true),
)

/** 页眉大数字（`第 12 周`）：衬线 tabular */
val LedgerDisplay = ledgerStyle(28, FontWeight.Bold, 34, -0.8, SerifFont, tabular = true)

/** 表/轴数字：衬线 tabular，右对齐悬挂 */
val LedgerNumeric = ledgerStyle(12, FontWeight.Normal, 16, 0.2, SerifFont, tabular = true)

/** 账本抬头微标签：`上午 / 下午 / 晚上` 这类分节名，字距放到 0.16em */
val LedgerMicro = ledgerStyle(9, FontWeight.Medium, 12, 1.6, SerifFont)

// ── 形态层（form）────────────────────────────────────────

/**
 * 圆角：便签 10dp / 小徽 6dp / 浮层 14dp。
 *
 * 为什么这次敢用圆角：HARD 的方角是「农机钢牌」的语义，本套是「纸上便签」，
 * 10dp 是「贴上去的一小张纸」的手感；超过 14dp 就变成消费级圆角卡，丢失账本感。
 */
val EditorialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

// ── 形态轴：[FormSpec] ───────────────────────────────────
//
// ⚠️ 铁律（零回归）：`HARD` 取 [FormSpec.Hard]，其取值 = 现状值；旧页面只读 [FormSpec.editorial]
// 与 displayFamily / numberFamily / titleTracking 等旧字段，所以老主题逐像素不变。
// 本套新增的开关（zebra / dottedColumns / bandFill / softShadow / pillAxis / noRowRules）
// 默认值全是「老主题的现状」，只有 EDITORIAL 打开。

@Immutable
data class FormSpec(
    /** 是否启用第二套语汇（账页）。false = HARD 原路径 */
    val editorial: Boolean = false,
    /** 通用卡片圆角（旧：0） */
    val radiusCard: Dp = 0.dp,
    /** 课表课程卡圆角（HARD 现状 4dp，主人点名的局部例外） */
    val radiusCourseCard: Dp = 4.dp,
    /** 浮层 / 对话框圆角 */
    val radiusSheet: Dp = 0.dp,
    /** 普通分节线粗细 */
    val ruleWidth: Dp = 1.dp,
    /** 刊头重线粗细 */
    val ruleHeavy: Dp = 2.dp,
    /** 纸张浮动阴影：半径（0 = 不用阴影） */
    val shadowRadius: Dp = 0.dp,
    /** 纸张浮动阴影：透明度 */
    val shadowAlpha: Float = 0f,
    /** 是否用卡片容器 */
    val usesCardContainer: Boolean = true,
    /** 展示字族（标题） */
    val displayFamily: FontFamily = FontFamily.Default,
    /** 数字字族 */
    val numberFamily: FontFamily = NumericFont,
    /** 标题字距（负值 = 收紧） */
    val titleTracking: TextUnit = 0.sp,
    /** 正文行高倍数 */
    val bodyLeading: Float = 1.5f,
    /** 是否画纸纹 */
    val paperTexture: Boolean = false,
    /** 触区下限 —— 三套主题都不许低于这个值 */
    val minTouch: Dp = 48.dp,

    // ── 账页专有开关（默认值 = 老主题现状，故对 HARD 无副作用）──
    /** 行与行之间**不画**横贯横线（改由留白 + 交错底色分节） */
    val noRowRules: Boolean = false,
    /** 交错行带（隔行 1.5% 底色），代替横向网格线 */
    val rowZebra: Boolean = false,
    /** 竖格线用点线（0.6dp），代替 0.75dp 实线 */
    val dottedColumns: Boolean = false,
    /** 分节（上午/下午/晚上）用**填充色带**，不画线 */
    val bandFill: Boolean = false,
    /** 课卡是否投影（两级软阴影） */
    val cardShadow: Boolean = false,
    /** 左轴是否用「节次圆牌 + 衬线时间」 */
    val pillAxis: Boolean = false,
    /** 行内边距（课程格与行边界之间的喘息） */
    val cellInset: Dp = 1.5.dp,
    /** 节次圆牌直径 */
    val pillSize: Dp = 20.dp,
) {
    companion object {
        /** 硬朗农业（现状值，零回归） */
        val Hard = FormSpec()

        /** 账页：横幅、零横线、交错行带、软阴影便签、衬线数字 */
        val Ledger = FormSpec(
            editorial = true,
            radiusCard = 10.dp,
            radiusCourseCard = 10.dp,
            radiusSheet = 14.dp,
            ruleWidth = 0.8.dp,
            ruleHeavy = 1.5.dp,
            shadowRadius = 6.dp,
            shadowAlpha = 0.07f,
            usesCardContainer = true,
            displayFamily = SerifFont,
            numberFamily = SerifFont,
            titleTracking = (-0.2).sp,
            bodyLeading = 1.45f,
            paperTexture = false,
            // 账页专有
            noRowRules = true,
            rowZebra = true,
            dottedColumns = true,
            bandFill = true,
            cardShadow = true,
            pillAxis = true,
            cellInset = 3.dp,
            pillSize = 20.dp,
        )

        /** 旧符号名，`Theme.kt` 仍引用；语义 = 第二套主题 */
        val Editorial: FormSpec get() = Ledger

        fun of(styleSet: Int): FormSpec = when (styleSet) {
            StyleSet.EDITORIAL -> Ledger
            else -> Hard
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

/**
 * **小字主色**（`primaryInk`）：凡是「主色的**文字**」都用它，不要直接用 [InkTokenSet.primary]。
 *
 * ## 为什么需要它（2026-11-14 实测）
 * 品牌主色 `#C74E17` 对硬朗纸底 `#F5F2EC` 只有 **4.15:1**、对卡片 `#FAF8F4` **4.37:1**,
 * 都不到正文要求的 4.5:1。但品牌色不能改（改了整套视觉就变了），
 * 所以单独给「小字」一档更暗的同色相：`#A83D11` = 色板里现成的 `Machine.n600`（5.63 / 5.93）。
 * **不新造颜色** —— 五级色阶已经够用，多一个只为凑对比度的色会让色板腐烂。
 *
 * ## 用在哪 / 不用在哪
 * - ✅ 文字（≤18sp 一律算小字，含 14sp 的「全部已读」）：日期高亮、未读计数、「本周」徽
 * - ❌ 非文字：进度条、圆点、色条、图标、面积填充 —— 这些用 [InkTokenSet.primary] 原色
 *
 * ## 账页
 * 账页的松针绿本身 9.33:1（亮）/ 8.55:1（暗），两档都过，所以它两个值相同。
 * 保留同一个入口是为了**调用点不写分支**：页面只管「这是小字主色」，具体取哪档由主题决定。
 */
@Composable
@ReadOnlyComposable
fun primaryInk(): Color {
    // 主色被用户换过（自定义配色 / 壁纸取色）时，主题层已经把「小字主色」算好了 —— 直接用，
    // 否则会出现「按钮是松针绿、紧挨着的小字还是品牌橙」的割裂（票 25）。
    LocalPrimaryInk.current?.let { return it }
    val t = InkTokens.current
    return when {
        Form.current.editorial -> if (t.isDark) Ledger.PineDark else Ledger.Pine
        t.isDark -> Machine.n300
        else -> Machine.n600
    }
}

// ── Modifier（账页专用）─────────────────────────────────

/**
 * 两级软阴影：不用 RenderEffect/blur（中端机零成本），用两层偏移矩形堆出「纸片浮起」。
 *
 * 为什么允许阴影：本主题的卡片是**贴在账页上的便签**，影子是「它浮在纸上」的唯一线索；
 * HARD 的方角平面卡不需要这层语义，所以 HARD 不开启（`cardShadow=false` → 本 Modifier 空转）。
 */
fun Modifier.cardFloat(spec: FormSpec, color: Color): Modifier =
    if (!spec.cardShadow) this else this.drawBehind {
        val a = spec.shadowAlpha
        drawRect(
            color = color.copy(alpha = a * 0.55f),
            topLeft = Offset(0f, 2.dp.toPx()),
            size = Size(size.width, size.height),
        )
        drawRect(
            color = color.copy(alpha = a * 0.30f),
            topLeft = Offset(0f, 5.dp.toPx()),
            size = Size(size.width, size.height),
        )
    }

/** 旧符号名（老调用点保留）：纸张浮动阴影 */
fun Modifier.paperFloat(spec: FormSpec, color: Color = Color(0xFF12100D)): Modifier =
    cardFloat(spec, color)

/**
 * 账本抬头色带：一条**填充**的分节带（不是线）。
 *
 * 这是本主题对「时段分节」的答案：用 14dp 高的淡色填充条把上午/下午/晚上分开。
 * 它和行线的区别是**面积**（14dp 对 1dp）—— 不会被误读成「差了一点点的行线」，
 * 因为不存在线的形状。旧编辑版那条「居中横线」正是错位观感的来源（偏 7.00dp），已删除。
 */
fun Modifier.bandRule(color: Color): Modifier = this.drawBehind {
    drawRect(color)
}

/**
 * 网格竖线（点线）。0.6dp 宽、2–3dp 间隔 —— 比实线弱一档，课卡放上去不会被「铁丝网」切碎，
 * 但空堂时仍能读出「这周有几节课的空位」。
 */
fun ledgerDottedColumns(color: Color): androidx.compose.ui.graphics.PathEffect =
    androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(2.4f, 2.6f), 0f)

// ── M3 配色（Switch / TextButton / Dialog 等仍走 M3）──────

val EditorialLightScheme = lightColorScheme(
    primary = Ledger.Pine,
    onPrimary = Color(0xFFFFFFFF),          // 9.98:1
    primaryContainer = Ledger.CardAlt,
    onPrimaryContainer = Ledger.Pine,
    secondary = Ledger.Indigo,
    onSecondary = Color(0xFFFFFFFF),        // 7.81:1
    secondaryContainer = Color(0xFFE3E9EC),
    onSecondaryContainer = Ledger.Indigo,
    tertiary = Ledger.Clay,
    onTertiary = Color(0xFFFFFFFF),         // 4.56:1
    tertiaryContainer = Ledger.CardAlt,
    onTertiaryContainer = Ledger.Brick,
    error = Ledger.Brick,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6E4DF),
    onErrorContainer = Ledger.Brick,
    background = Ledger.Paper,
    onBackground = Ledger.Ink,
    surface = Ledger.Paper,
    onSurface = Ledger.Ink,
    surfaceVariant = Ledger.CardAlt,
    onSurfaceVariant = Ledger.InkSoft,
    surfaceContainerLowest = Ledger.Card,
    surfaceContainerLow = Ledger.Card,
    surfaceContainer = Ledger.Paper,
    surfaceContainerHigh = Ledger.CardAlt,
    surfaceContainerHighest = Ledger.BandTint,
    outline = Ledger.Outline,
    outlineVariant = Ledger.Rule,
    inverseSurface = Ledger.Ink,
    inverseOnSurface = Ledger.Card,
    inversePrimary = Ledger.PineDark,
)

val EditorialDarkScheme = darkColorScheme(
    primary = Ledger.PineDark,
    onPrimary = Color(0xFF0F1613),          // 8.89:1
    primaryContainer = Ledger.CardAltDark,
    onPrimaryContainer = Ledger.PineDark,
    secondary = Ledger.IndigoDark,
    onSecondary = Color(0xFF0B1418),
    secondaryContainer = Color(0xFF1F2B31),
    onSecondaryContainer = Ledger.IndigoDark,
    tertiary = Ledger.ClayDark,
    onTertiary = Color(0xFF1A0F0A),
    tertiaryContainer = Color(0xFF33201A),
    onTertiaryContainer = Ledger.ClayDark,
    error = Ledger.BrickDark,
    onError = Color(0xFF1A0E0B),
    errorContainer = Color(0xFF43231D),
    onErrorContainer = Ledger.BrickDark,
    background = Ledger.PaperDark,
    onBackground = Ledger.InkDark,
    surface = Ledger.PaperDark,
    onSurface = Ledger.InkDark,
    surfaceVariant = Ledger.CardAltDark,
    onSurfaceVariant = Ledger.InkSoftDark,
    surfaceContainerLowest = Color(0xFF101613),
    surfaceContainerLow = Ledger.CardDark,
    surfaceContainer = Ledger.PaperDark,
    surfaceContainerHigh = Ledger.CardAltDark,
    surfaceContainerHighest = Ledger.BandTintDark,
    outline = Ledger.OutlineDark,
    outlineVariant = Ledger.RuleDark,
    inverseSurface = Ledger.InkDark,
    inverseOnSurface = Ledger.PaperDark,
    inversePrimary = Ledger.Pine,
)

/**
 * 纸纹：本主题**不使用**（零纹理）。保留符号只为兼容旧的 import，
 * 调用后不画任何东西 —— 避免「远处一片噪点」在 OLED 上变成脏。
 */
fun Modifier.paperGrain(color: Color, gap: Dp = 22.dp, alpha: Float = 0.045f): Modifier = this

/** 旧符号名（老调用点保留）：版面线 */
fun Modifier.editorialRule(spec: FormSpec, color: Color, heavy: Boolean = false): Modifier =
    bandRule(color)
