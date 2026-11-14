package cn.dkarbon.enroute.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 备选视觉风格：**学院纸本 / E-Ink**（2026-11-13）。
 *
 * ## 为什么要有第二套
 * 「硬朗农业」是为课表设计的 —— 大面积网格、每天看十几次、需要强刺激区分科目。
 * 但考试安排和成绩是**长读**页面：一屏十几行小字、要一行行对比日期和分数。
 * 农械橘在这种场景是噪音（且 `#C74E17` 对纸白只有 4.37:1，当文字必须 ≥14sp/700 才能用）。
 *
 * 这一套把强调色**收敛到一种**（褪色墨水靛），其余全是纸白 + 石墨黑 + 细格线，
 * 读完一页眼睛不累；同时把强调色的对比度抬到 9.10:1 —— 强调色终于可以直接当正文文字用。
 *
 * ## 不改的三条红线（与 [HardTokens] 完全一致）
 * 1. 圆角 0；2. 无渐变、无 elevation（要浮层立体感仍用 hardShadow）；3. 数字全部 [NumericFont]。
 *
 * ## 刻意**不做**的事
 * 不换成衬线中文：`NumericFont`（IBM Plex Mono）只覆盖数字与拉丁，中文衬线要靠 ROM 的
 * Noto Serif CJK 回退，国产 ROM 上不保证有 —— 一旦回退到黑体，「纸本」感就假了。
 * 所以这套只改**色、线、面**，字族与字号阶梯原样沿用 `EnRouteTypography`。
 *
 * 对比度实测值见 `docs/评审-考试与成绩页-v2.md` §5（正文 ≥ 4.5:1、大字 ≥ 3:1，全部达标）。
 */
object Scholar {

    // ── 亮：纸白 + 石墨 ──────────────────────────────────
    val Paper = Color(0xFFF6F5F1)       // 页面底（低彩度纸白，比硬朗的 #F5F2EC 更冷一点）
    val Card = Color(0xFFFCFBF8)        // 卡片面
    val CardAlt = Color(0xFFEDEBE4)     // 次级面（组头、表头）
    val Ink = Color(0xFF17181A)         // 正文（石墨黑，不用纯黑）
    val InkSoft = Color(0xFF3B3F45)     // 二级文字 10.23:1
    val InkFaint = Color(0xFF555A61)    // 三级文字 6.72:1（小字也够 AA）
    val Rule = Color(0xFFD9D7CF)        // 格线 / 分割线
    val Outline = Color(0xFFB5B3A9)     // 卡片 1dp 描边
    val Indigo = Color(0xFF1F4A66)      // 唯一强调色：褪色墨水靛 → 纸面 9.10:1
    val Vermilion = Color(0xFFA32A1B)   // 不及格 / 错误（朱砂，只做语义）
    val Green = Color(0xFF2F5D46)       // 通过 / 已完成
    val Ochre = Color(0xFF7A5314)       // 警告 / 临近

    // ── 暗：墨夜（夜间与考期冲刺用）────────────────────────
    val PaperDark = Color(0xFF131519)
    val CardDark = Color(0xFF191C21)
    val CardAltDark = Color(0xFF23272D)
    val InkDark = Color(0xFFE9EAE6)     // 14.13:1
    val InkSoftDark = Color(0xFFC2C6C2) // 9.88:1
    val InkFaintDark = Color(0xFF9AA09C)// 6.41:1
    val RuleDark = Color(0xFF2C3137)
    val OutlineDark = Color(0xFF3F464D)
    val IndigoDark = Color(0xFF7FB4D2)  // 提起的靛，暗底 7.61:1
    val VermilionDark = Color(0xFFE39B90)
    val GreenDark = Color(0xFF93C0A6)
    val OchreDark = Color(0xFFD9B36A)

    /** 信息条底色（教务提示） */
    val InfoBg = Color(0xFFE7EBEE)
    val InfoBgDark = Color(0xFF1E2A33)
}

/**
 * 风格开关的取值。
 *
 * 为什么不做成 `ThemeMode` 的第四个值：`ThemeMode` 管的是**明暗**（跟随系统/浅/深），
 * 风格管的是**语汇**（农业硬朗/学院纸本）。两者正交，合成一个枚举会让「深色纸本」无法表达。
 */
object StyleSet {
    /** 硬朗农业（默认，现状） */
    const val HARD = 0
    /** 学院纸本 / E-Ink */
    const val SCHOLAR = 1
    /**
     * 校报头版 / Editorial（票 17，2026-11-14）。
     *
     * 前两套只差配色；这一套换的是**结构语言**（去卡片容器、版面线分区、衬线标题、
     * 数字离开等宽）。token 见 [EditorialTokens][cn.dkarbon.enroute.ui.theme.Editorial]，
     * 形态见 [FormSpec]，页面侧通过 `Form.current.editorial` 走另一条渲染分支。
     */
    const val EDITORIAL = 2

    fun isValid(v: Int): Boolean = v == HARD || v == SCHOLAR || v == EDITORIAL

    fun name(v: Int): String = when (v) {
        SCHOLAR -> "学院纸本"
        EDITORIAL -> "校报头版"
        else -> "硬朗农业"
    }
}

// ── 两套 token 实例（字段与 [InkTokenSet] 完全对齐，业务代码零改动）──

val ScholarLightTokens = InkTokenSet(
    paper = Scholar.Paper,
    card = Scholar.Card,
    cardAlt = Scholar.CardAlt,
    ink = Scholar.Ink,
    inkSoft = Scholar.InkSoft,
    inkFaint = Scholar.InkFaint,
    rule = Scholar.Rule,
    brick = Scholar.Vermilion,
    slate = Scholar.Indigo,
    olive = Scholar.Green,
    amber = Scholar.Ochre,
    rust = Scholar.Indigo,
    isDark = false,
    primary = Scholar.Indigo,
    accent = Scholar.Indigo,
    outline = Scholar.Outline,
    outlineStrong = Scholar.Ink,
    hardShadow = Scholar.Ink,
    info = Scholar.Indigo,
    infoBg = Scholar.InfoBg,
)

val ScholarDarkTokens = InkTokenSet(
    paper = Scholar.PaperDark,
    card = Scholar.CardDark,
    cardAlt = Scholar.CardAltDark,
    ink = Scholar.InkDark,
    inkSoft = Scholar.InkSoftDark,
    inkFaint = Scholar.InkFaintDark,
    rule = Scholar.RuleDark,
    brick = Scholar.VermilionDark,
    slate = Scholar.IndigoDark,
    olive = Scholar.GreenDark,
    amber = Scholar.OchreDark,
    rust = Scholar.IndigoDark,
    isDark = true,
    primary = Scholar.IndigoDark,
    accent = Scholar.IndigoDark,
    outline = Scholar.OutlineDark,
    outlineStrong = Scholar.InkFaintDark,
    hardShadow = Color(0xFF000000),
    info = Scholar.IndigoDark,
    infoBg = Scholar.InfoBgDark,
)

// ── M3 scheme（Switch / TextButton / Dialog 这些组件还在用 M3 的主色）──

val ScholarLightScheme = lightColorScheme(
    primary = Scholar.Indigo,
    onPrimary = Color.White,          // 9.42:1
    primaryContainer = Scholar.InfoBg,
    onPrimaryContainer = Scholar.Indigo,
    secondary = Scholar.Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EBE6),
    onSecondaryContainer = Scholar.Green,
    tertiary = Scholar.Ochre,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2EBDA),
    onTertiaryContainer = Scholar.Ochre,
    error = Scholar.Vermilion,
    onError = Color.White,
    errorContainer = Color(0xFFF5E0DB),
    onErrorContainer = Scholar.Vermilion,
    background = Scholar.Paper,
    onBackground = Scholar.Ink,
    surface = Scholar.Paper,
    onSurface = Scholar.Ink,
    surfaceVariant = Scholar.CardAlt,
    onSurfaceVariant = Scholar.InkSoft,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Scholar.Card,
    surfaceContainer = Scholar.Paper,
    surfaceContainerHigh = Scholar.CardAlt,
    surfaceContainerHighest = Color(0xFFE4E1D8),
    outline = Scholar.Outline,
    outlineVariant = Scholar.Rule,
    inverseSurface = Scholar.Ink,
    inverseOnSurface = Scholar.Card,
    inversePrimary = Scholar.IndigoDark,
)

val ScholarDarkScheme = darkColorScheme(
    primary = Scholar.IndigoDark,
    // 暗色主色是浅靛，白字只有 2.24:1 —— 必须压成深底文字
    onPrimary = Color(0xFF0C1116),
    primaryContainer = Scholar.InfoBgDark,
    onPrimaryContainer = Scholar.IndigoDark,
    secondary = Scholar.GreenDark,
    onSecondary = Color(0xFF0C1116),
    secondaryContainer = Color(0xFF20322A),
    onSecondaryContainer = Scholar.GreenDark,
    tertiary = Scholar.OchreDark,
    onTertiary = Color(0xFF0C1116),
    tertiaryContainer = Color(0xFF33290F),
    onTertiaryContainer = Scholar.OchreDark,
    error = Scholar.VermilionDark,
    onError = Color(0xFF2A0F0A),
    errorContainer = Color(0xFF4A1D15),
    onErrorContainer = Scholar.VermilionDark,
    background = Scholar.PaperDark,
    onBackground = Scholar.InkDark,
    surface = Scholar.PaperDark,
    onSurface = Scholar.InkDark,
    surfaceVariant = Scholar.CardAltDark,
    onSurfaceVariant = Scholar.InkSoftDark,
    surfaceContainerLowest = Color(0xFF0E1013),
    surfaceContainerLow = Scholar.CardDark,
    surfaceContainer = Scholar.PaperDark,
    surfaceContainerHigh = Scholar.CardAltDark,
    surfaceContainerHighest = Color(0xFF2B3037),
    outline = Scholar.OutlineDark,
    outlineVariant = Scholar.RuleDark,
    inverseSurface = Scholar.InkDark,
    inverseOnSurface = Scholar.PaperDark,
    inversePrimary = Scholar.Indigo,
)
