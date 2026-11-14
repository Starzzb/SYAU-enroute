package cn.dkarbon.enroute.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color

/**
 * 壁纸取色（Material You / dynamic color）—— 票 16 追加项②。
 *
 * ## 为什么只接两个色
 *
 * 我们的配色不是 Material 体系，而是一套**语义色板**：纸白底、墨色正文、
 * 砖红=错误/不及格、橄榄=通过、琥珀=待办。壁纸取色如果整包替换，
 * 第一个坏掉的就是 [InkTokenSet.brick] —— 那会直接影响「数据诚实」相关的警示表达
 * （不及格/过期这类信息会失去固定含义）。所以只覆盖：
 * - [InkTokenSet.primary]（主色：色条、主按钮）
 * - [InkTokenSet.accent]（装饰亮色：当前时间线、进行中高亮）
 *
 * **纸白/墨色/砖红/橄榄/琥珀一律不动。**
 *
 * ## 为什么不用 material3 的 dynamicColorScheme
 *
 * 那会连带改掉 surface/onSurface 一整包语义，等于把我们的纸墨体系换成 Material。
 * 这里直接读 `android.R.color.system_accent1_*`（API 31+ 系统取色表）自己挑档位。
 */
object DynamicColorRules {

    /** 动态取色要求 Android 12（API 31）以上；低版本连选项都不显示 */
    const val MIN_SDK = 31

    fun available(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S

    /**
     * 挑档位（`system_accent1_<档>` 的下标）。
     *
     * 亮色用 600/400（够深，压在纸白上对比足）；暗色用 200/300（够亮，压在黑土上不糊）。
     * 这两个数是**刻意克制**的：不跟随壁纸亮度自适应，避免同一个按钮今天深明天浅。
     *
     * @return `first` = primary 档位，`second` = accent 档位
     */
    fun shades(isDark: Boolean): Pair<Int, Int> = if (isDark) 200 to 300 else 600 to 400

    /**
     * 把壁纸色盖到 token 上。
     *
     * 纯函数：只换 primary / accent，其余字段（含 isDark）原样透传 ——
     * 单测就是靠这一点锁「语义色不被壁纸色污染」。
     */
    fun override(base: InkTokenSet, primary: Color, accent: Color): InkTokenSet =
        base.copy(primary = primary, accent = accent)

    /** 系统取色资源；档位不在表里返回 null（调用方保留原配色） */
    fun systemRes(shade: Int): Int? = when (shade) {
        100 -> android.R.color.system_accent1_100
        200 -> android.R.color.system_accent1_200
        300 -> android.R.color.system_accent1_300
        400 -> android.R.color.system_accent1_400
        500 -> android.R.color.system_accent1_500
        600 -> android.R.color.system_accent1_600
        700 -> android.R.color.system_accent1_700
        800 -> android.R.color.system_accent1_800
        900 -> android.R.color.system_accent1_900
        1000 -> android.R.color.system_accent1_1000
        else -> null
    }

    /** 读一个档位的实际颜色（API 31+ 才调；低版本返回 null 由调用方兜底） */
    fun systemColor(ctx: Context, shade: Int): Color? {
        if (!available(Build.VERSION.SDK_INT)) return null
        val res = systemRes(shade) ?: return null
        return runCatching { Color(ctx.getColor(res)) }.getOrNull()
    }
}

/**
 * 取色来源：**固定 / 壁纸 / 自定义**（自定义是票 25 加的第三档）。
 *
 * - [FIXED]：用主题自带的农业色板（默认，老用户升级后看到的就是这个）
 * - [WALLPAPER]：Material You 壁纸取色，仅 Android 12+ 生效
 * - [CUSTOM]：用户从预设色板里挑 + 微调（见 [CustomPalette] / [PaletteGate]）
 *
 * 三档都只覆盖 `primary` / `accent`，语义色永远不动（数据诚实红线）。
 */
object ColorSource {
    const val FIXED = 0
    const val WALLPAPER = 1
    const val CUSTOM = 2

    fun isValid(v: Int): Boolean = v == FIXED || v == WALLPAPER || v == CUSTOM

    /** 不认识的存值一律回落固定（避免老/坏数据让界面变空白） */
    fun normalize(v: Int): Int = when (v) {
        WALLPAPER -> WALLPAPER
        CUSTOM -> CUSTOM
        else -> FIXED
    }

    fun name(v: Int): String = when (v) {
        WALLPAPER -> "跟随壁纸取色"
        CUSTOM -> "自定义配色"
        else -> "固定"
    }

    /** 设置页三档按钮的短名（按钮上只放名字，说明进弹窗 —— AGENTS.md 界面文案约定） */
    fun shortName(v: Int): String = when (v) {
        WALLPAPER -> "壁纸"
        CUSTOM -> "自定义"
        else -> "固定"
    }
}
