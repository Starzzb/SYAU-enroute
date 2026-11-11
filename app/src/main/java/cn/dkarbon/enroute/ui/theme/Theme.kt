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
    val InkSoft = Soil.n600       // #5F574C 二级文字
    val InkFaint = Soil.n500      // #7E7468 三级文字（9–11sp 用这档）
    val Rule = Soil.n200          // #D7D0C4 分割线/网格线

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
    val InkFaintDark = Soil.n500  // #7E7468

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
            outline = Soil.n300,
            outlineStrong = Soil.n900,
            hardShadow = Soil.n900,
            info = Steel.n500,
            infoBg = Steel.n50,
        )

        val Dark = InkTokenSet(
            paper = Ink.PaperDark, card = Ink.CardDark, cardAlt = Ink.CardAltDark,
            ink = Ink.InkDark, inkSoft = Ink.InkSoftDark, inkFaint = Ink.InkFaintDark,
            rule = Color(0xFF3E362C), brick = Ink.BrickDark, slate = Ink.SlateDark,
            olive = Ink.OliveDark, amber = Ink.AmberDark, rust = Ink.RustDark,
            isDark = true,
            primary = Machine.n300,
            accent = Machine.n400,
            outline = Soil.n700,
            outlineStrong = Soil.n400,
            hardShadow = Color(0xFF000000),
            info = Steel.n200,
            infoBg = Steel.n700,
        )
    }
}

val LocalInkTokens = staticCompositionLocalOf { InkTokenSet.Light }

/** 简写：`InkTokens.current.card` */
object InkTokens {
    val current: InkTokenSet
        @Composable
        @ReadOnlyComposable
        get() = LocalInkTokens.current
}

/**
 * 主题入口。
 *
 * 注意：**关掉了 dynamicColor**。动态取色会把我们的农业色板整个换掉
 * （Android 12+ 从壁纸取色），那样「硬朗农业」就白设计了。
 */
@Composable
fun EnRouteTheme(
    themeMode: Int = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }
    val tokens = if (darkTheme) InkTokenSet.Dark else InkTokenSet.Light

    CompositionLocalProvider(LocalInkTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (darkTheme) HardDarkScheme else HardLightScheme,
            typography = EnRouteTypography,
            shapes = HardShapes,
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
