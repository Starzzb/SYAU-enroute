package cn.dkarbon.enroute.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 视觉语言：**黑土 + 农机漆 + 田垄网格**（2026-11-10 重做，三位设计专家评审）。
 *
 * 上一版是「暖纸 + 墨 + 衬线字 + 细线」，主人判定「不应该这样设计，要硬朗 + 农业配色」。
 * 这一版的做法不是换几块颜色，而是换**结构**：
 *
 * | 维度 | 旧（文艺） | 新（硬朗） |
 * |---|---|---|
 * | 圆角 | 3–10dp | **全部 0** |
 * | 阴影 | 柔和 elevation | **无**（要立体用 [hardShadow] 2dp 硬投影） |
 * | 分割 | 极细半透明线 | **1dp 实心** `#D7D0C4` |
 * | 强调色 | 砖红点状使用 | **农械橘 `#C74E17`**，只占小面积（色条/图标/主按钮） |
 * | 底色 | 暖米白 `#F7F3EA` | 纸白偏灰 `#F5F2EC` / 暗色 `#100E0B` |
 * | 字体 | 衬线标题 + 无衬线正文 | **系统黑体 + 等宽数字**，字重 400/500/700/800 |
 * | 装饰 | 纸纹、毛边 | **零纹理零渐变**，靠网格和边线说话 |
 *
 * 名字保留 `Ink` / `InkTokens`（1792 行课表代码都依赖它们），只换值 —— 这样换肤
 * 不用改业务代码，也是唯一能在一天内落地的路径。
 */
object Ink {
    // ── 亮色（大面积是纸白与黑土）──
    val Paper = Soil.n50          // #F5F2EC 页面底
    val Card = Color(0xFFFAF8F4)  // 卡片面（比底略亮，靠 1dp 边线区分，不靠阴影）
    val CardAlt = Soil.n100       // #EDE8DF 次级面（表头、选中态）
    val Ink = Soil.n900           // #1A1611 正文（纯黑太硬，用带土的红黑）
    val InkSoft = Soil.n700       // #443E36 二级文字（原 n600，9.45:1）
    val InkFaint = Soil.n600      // #5F574C 三级文字（9–11sp 用这档，6.36:1；原 n500 只有 4.10:1）
    // 分割线/网格线。原 #D7D0C4 对纸底仅 1.37:1，调暗到同色相的 #8F7E61（纸 3.53 / 卡 3.72 / 卡Alt 3.23，≥3:1）
    val Rule = Color(0xFF8F7E61)

    // ── 语义色 ──
    val Brick = RustRed.n500         // #9B2417 错误、不及格
    val Slate = Steel.n500        // #345A66 信息、教务
    val Olive = Crop.n600         // #3F5A2E 进行中、通过
    val Amber = Wheat.n600        // #8F6713 警告、待办
    val Rust = Machine.n500       // #C74E17 主色/当前时间线/强调条

    // ── 暗色 ──
    val PaperDark = Soil.n950     // #100E0B（不是纯黑，留一点土色）
    val CardDark = Soil.n900      // #1A1611
    val CardAltDark = Soil.n800   // #2B2621
    val InkDark = Soil.n100       // #EDE8DF
    val InkSoftDark = Soil.n400   // #A1978A
    // 原 Soil.n500 #7E7468 对卡Alt 只有 3.27:1，提亮到同色相 #988D82（纸 5.94 / 卡 5.54 / 卡Alt 4.61，≥4.5:1）
    val InkFaintDark = Color(0xFF988D82)

    val BrickDark = RustRed.n200     // #E7A093
    val SlateDark = Steel.n200    // #A0B6BB
    val OliveDark = Crop.n200     // #BCC79B
    val AmberDark = Wheat.n300    // #DDB84D
    val RustDark = Machine.n300   // #EA8F5E
}

/**
 * 暗夜感知的 token 取值。
 *
 * 用法：`val t = InkTokens.current` 然后 `t.card` / `t.paper`。
 * ⚠️ 不要在 @Composable 之外引用 `Ink.Card` 渲染，那会写死浅色。
 * 新代码请优先用 [primary] / [accent] / [outline] / [hardShadow] 这几个新字段。
 */
@Immutable
data class InkTokenSet(
    // 旧的 12 个字段（课表/小组件在用，值已换成农业色板）
    val paper: Color,
    val card: Color,
    val cardAlt: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val rule: Color,
    val brick: Color,
    val slate: Color,
    val olive: Color,
    val amber: Color,
    val rust: Color,
    val isDark: Boolean,
    // 新字段：硬朗体系专有
    /** 主色：农械橘。只用于 3–4dp 色条、图标、主按钮，不做大面积填充 */
    val primary: Color = Machine.n500,
    /** 装饰亮橙：当前时间线、进行中高亮（不承载文字） */
    val accent: Color = Machine.n400,
    /** 卡片描边（1dp） */
    val outline: Color = Soil.n300,
    /** 强调描边（2dp，用于「下一节课」主卡、对话框） */
    val outlineStrong: Color = Soil.n900,
    /** 硬投影颜色 */
    val hardShadow: Color = Soil.n900,
    /** 信息色（教务提示条） */
    val info: Color = Steel.n500,
    /** 信息条底色 */
    val infoBg: Color = Steel.n50,
) {
    companion object {
        val Light = InkTokenSet(
            paper = Ink.Paper, card = Ink.Card, cardAlt = Ink.CardAlt,
            ink = Ink.Ink, inkSoft = Ink.InkSoft, inkFaint = Ink.InkFaint,
            rule = Ink.Rule, brick = Ink.Brick, slate = Ink.Slate,
            olive = Ink.Olive, amber = Ink.Amber, rust = Ink.Rust,
            isDark = false,
            primary = Machine.n500,
            accent = Machine.n400,
            // 卡片描边（1dp）：原 n300 #BEB5A6 对卡Alt 仅 1.66:1，加深到 n500 #7E7468（纸 4.10 / 卡 4.32 / 卡Alt 3.75）
            outline = Soil.n500,
            outlineStrong = Soil.n900,
            hardShadow = Soil.n900,
            info = Steel.n500,
            infoBg = Steel.n50,
        )

        val Dark = InkTokenSet(
            paper = Ink.PaperDark, card = Ink.CardDark, cardAlt = Ink.CardAltDark,
            ink = Ink.InkDark, inkSoft = Ink.InkSoftDark, inkFaint = Ink.InkFaintDark,
            // 原 #3E362C 对纸底仅 1.62:1，提亮到同色相 #7F6F5A（纸 3.97 / 卡 3.71 / 卡Alt 3.08，≥3:1）
            rule = Color(0xFF7F6F5A), brick = Ink.BrickDark, slate = Ink.SlateDark,
            olive = Ink.OliveDark, amber = Ink.AmberDark, rust = Ink.RustDark,
            isDark = true,
            primary = Machine.n300,
            accent = Machine.n400,
            // 原 n700 #443E36 对卡Alt 仅 1.42:1，提亮到同色相 #827767（纸 4.39 / 卡 4.10 / 卡Alt 3.41）
            outline = Color(0xFF827767),
            outlineStrong = Soil.n400,
            hardShadow = Color(0xFF000000),
            info = Steel.n200,
            infoBg = Steel.n700,
        )
    }
}

val LocalInkTokens = staticCompositionLocalOf { InkTokenSet.Light }

/**
 * 「主色当文字」时该用哪个色。`null` = 走各主题的固定规则（品牌橙 / 松针绿）；
 * 非 null = 主色被用户换过（自定义配色 / 壁纸取色），由 [PaletteGate.inkVariant] 推出来。
 *
 * 为什么要有这个 Local：`primaryInk()` 写死了 `Machine.n600`，主色一换它就跟不上了 ——
 * 会出现「按钮是松针绿、旁边的小字还是品牌橙」的割裂。
 */
val LocalPrimaryInk = staticCompositionLocalOf<Color?> { null }

/** 简写：`InkTokens.current.card` */
object InkTokens {
    val current: InkTokenSet
        @Composable
        @ReadOnlyComposable
        get() = LocalInkTokens.current
}

/**
 * 取某「风格 + 明暗」下的基础色板（**不含**任何取色覆盖）。
 *
 * 为什么单独抽出来：自定义配色的对比度闸门要按**被编辑的那一套**算
 * （编辑深色槽时用墨底，而不是当前界面那套纸白），所以不能只靠 `InkTokens.current`。
 * [EnRouteTheme] 自己也走这一个函数 —— 一处定义，避免两处颜色选择逻辑漂移。
 */
fun baseTokenSet(styleSet: Int, isDark: Boolean): InkTokenSet = when {
    styleSet == StyleSet.EDITORIAL && isDark -> EditorialDarkTokens
    styleSet == StyleSet.EDITORIAL -> EditorialLightTokens
    styleSet == StyleSet.SCHOLAR && isDark -> ScholarDarkTokens
    styleSet == StyleSet.SCHOLAR -> ScholarLightTokens
    isDark -> InkTokenSet.Dark
    else -> InkTokenSet.Light
}

/**
 * 主题入口。
 *
 * 注意：**不做整包 dynamicColor**（Material 的 dynamicColorScheme 会把纸墨体系整个换掉）。
 * 壁纸取色只作为**第三选项**存在，且只接 primary / accent 两个色（见 [DynamicColorRules]）。
 *
 * @param styleSet 风格语汇（[StyleSet.HARD] 硬朗农业 / [StyleSet.SCHOLAR] 学院纸本 /
 *   [StyleSet.EDITORIAL] 校报头版）。与明暗正交 —— 深色纸本是合法组合，所以刻意不塞进 [ThemeMode]。
 *
 * ## 三套主题的差异层次（票 17）
 * | 层 | HARD / SCHOLAR | EDITORIAL |
 * |---|---|---|
 * | 颜色 | `InkTokenSet` / `Scholar*` | `Editorial*` |
 * | 形态（圆角/阴影/描边/字族/是否用卡片容器） | [FormSpec.Hard]（现状值） | [FormSpec.Editorial] |
 * | 排版 | [EnRouteTypography] + [NumericFont] | [EditorialTypography] + 衬线数字 |
 *
 * ⚠️ `HARD` / `SCHOLAR` 的渲染必须逐像素不变：两者仍然拿 `EnRouteTypography` + `HardShapes`，
 * 且 `FormSpec.Hard` 的取值就是现状值。只有 EDITORIAL 走新分支。
 */
@Composable
fun EnRouteTheme(
    themeMode: Int = ThemeMode.System,
    styleSet: Int = StyleSet.HARD,
    /** 取色来源（[ColorSource.FIXED] / [ColorSource.WALLPAPER] / [ColorSource.CUSTOM]） */
    colorSource: Int = ColorSource.FIXED,
    /**
     * 自定义配色的四个槽（票 25）。仅 [colorSource] == [ColorSource.CUSTOM] 时生效；
     * 该模式下的两个槽没填全就**原样返回**主题自带色板（绝不半套色）。
     */
    customPalette: CustomPalette = CustomPalette.NONE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }
    val editorial = styleSet == StyleSet.EDITORIAL
    val scholar = styleSet == StyleSet.SCHOLAR
    val baseTokens = baseTokenSet(styleSet, darkTheme)
    /*
     * 壁纸取色（Material You，票 16 追加项②）：**只覆盖 primary / accent**。
     *
     * 纸白底、墨色正文、砖红/橄榄/琥珀这些语义色必须保持固定 ——
     * 砖红被壁纸染成蓝色，「不及格/已过期」的表达就失真了（数据诚实红线）。
     * 低版本（< API 31）或读不到系统色时**原样返回** base token，不抛不崩。
     */
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tokens = when {
        /*
         * 自定义配色（票 25）：用户从预设色板里挑的 primary / accent。
         * 保存时已经过 [PaletteGate] 的 4.5:1 闸门，这里只负责覆盖；
         * 该模式缺槽（老数据 / 只设了浅色就切到深色）→ 原样用主题色板，不留半套色。
         */
        colorSource == ColorSource.CUSTOM -> {
            val p = customPalette.primary(darkTheme)
            val a = customPalette.accent(darkTheme)
            if (p != null && a != null) DynamicColorRules.override(baseTokens, p, a) else baseTokens
        }
        colorSource == ColorSource.WALLPAPER &&
            DynamicColorRules.available(android.os.Build.VERSION.SDK_INT) -> {
            val (primaryShade, accentShade) = DynamicColorRules.shades(darkTheme)
            val primary = DynamicColorRules.systemColor(ctx, primaryShade)
            val accent = DynamicColorRules.systemColor(ctx, accentShade)
            if (primary != null && accent != null) {
                DynamicColorRules.override(baseTokens, primary, accent)
            } else {
                baseTokens
            }
        }
        else -> baseTokens
    }
    /*
     * 主色被换过之后，「主色当文字」那一档也要跟着换（否则按钮是新色、旁边小字还是品牌橙）。
     * 自定义配色已被 4.5:1 闸门保证过，直接用；壁纸色没有闸门，用 inkVariant 尽力补偿。
     */
    val primaryTextInk: Color? = when {
        colorSource == ColorSource.CUSTOM && tokens !== baseTokens -> tokens.primary
        colorSource == ColorSource.WALLPAPER && tokens !== baseTokens ->
            PaletteGate.inkVariant(baseTokens, tokens.primary)
        else -> null
    }
    val scheme = when {
        editorial && darkTheme -> EditorialDarkScheme
        editorial -> EditorialLightScheme
        scholar && darkTheme -> ScholarDarkScheme
        scholar -> ScholarLightScheme
        darkTheme -> HardDarkScheme
        else -> HardLightScheme
    }
    // 旧两套继续用 EnRouteTypography + HardShapes（零回归）；编辑版换字族与字号阶梯
    val typography = if (editorial) EditorialTypography else EnRouteTypography
    val shapes = if (editorial) EditorialShapes else HardShapes

    CompositionLocalProvider(
        LocalInkTokens provides tokens,
        LocalPrimaryInk provides primaryTextInk,
        LocalForm provides FormSpec.of(styleSet),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

/**
 * 主题模式常量。
 * 显式开关的意义：国产 ROM 上「跟随系统」经常不生效（iQOO 实测系统深色但 App 仍浅色）。
 */
object ThemeMode {
    const val System = 0
    const val Light = 1
    const val Dark = 2
}
