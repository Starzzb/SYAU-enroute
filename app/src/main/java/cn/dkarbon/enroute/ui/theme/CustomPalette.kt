package cn.dkarbon.enroute.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 主题自定义配色（票 25）。
 *
 * ## 主人定下的形状：**预设 + 微调**，不是自由取色
 * 理由（写在这里免得后人"顺手"加个 HSV 色环）：这套配色的语义是绑死在颜色上的 ——
 * 砖红=错误/不及格、橄榄=通过、琥珀=待办。自由取色十分钟就能做出「正文看不清」的主题，
 * 而用户不会意识到是自己选的色导致的。所以：
 *
 * 1. **主色 / 强调色各一行预设色块**，全部取自既有色阶（`Machine` / `Crop` / `Steel` /
 *    `RustRed` / `Wheat` / `Soil` / `Ledger`），开箱即达标（有单测逐个验）；
 * 2. 选中后允许**明度 / 饱和度**两条滑条微调（不做色环、不做色相滑条）；
 * 3. 任何要保存的组合都要过 [PaletteGate]：正文 < 4.5:1 直接不给存，并说清是哪一对不达标。
 *
 * ## 只换两个色
 * 与壁纸取色（[DynamicColorRules]）同一条纪律：**只覆盖 `primary` / `accent`**。
 * 纸底、墨色正文、砖红/橄榄/琥珀一律不动 —— 否则「数据诚实」的表达（不及格=砖红）会失真。
 *
 * ## 浅色 / 深色各自独立
 * 纸白上达标的主色压到黑土上就是一团糊，所以两套值分开存（[CustomPalette] 四个槽），
 * 切主题不互相覆盖。
 */

// ── ① 对比度计算（纯 Kotlin，不碰 Android API，JVM 单测直接跑）──────────

/**
 * WCAG 2.1 对比度。
 *
 * 为什么自己算而不用 `Color.luminance()`：后者是 Android 图形栈的实现（依赖 `android.graphics`），
 * 在 JVM 单测里跑不了；而且我们要的是**能复现的算术**，不是平台行为。
 * 公式与 `PrimaryInkContrastTest` 里那份完全一致（同一条规则两处用，别改歪）。
 */
object ContrastRules {

    /** 正文（< 18pt，或 < 14pt 粗体）门槛 */
    const val BODY_MIN = 4.5

    /** 大字（≥ 18pt 或 ≥ 14pt 粗体）与「承载信息的图形」门槛 */
    const val LARGE_MIN = 3.0

    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** 相对亮度（0 = 黑，1 = 白） */
    fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** 两色的对比度（1..21） */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = max(la, lb)
        val lo = min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** `5.63` 这种两位小数字符串（界面与单测提示共用一种格式，别两处各写一遍） */
    fun fmt(v: Double): String = String.format("%.2f", v)
}

/**
 * 一条对比关系。`label` 是给人看的中文短名（出现在"哪一对不达标"的提示里）。
 */
@Immutable
data class ContrastCheck(
    val label: String,
    val fg: Color,
    val bg: Color,
    val min: Double,
) {
    val ratio: Double get() = ContrastRules.ratio(fg, bg)
    val pass: Boolean get() = ratio >= min

    /** `主色/纸底 5.63:1` */
    val text: String get() = "$label ${ContrastRules.fmt(ratio)}:1"
}

// ── ② HSL 微调（只动明度与饱和度，不动色相）──────────────────────────

/**
 * 明度 / 饱和度微调。
 *
 * 刻意**不提供色相滑条**：换色相等于换一个预设，那是预设行的事；
 * 微调只解决「这个绿再深一点就好了」这类需求。色相保持不动也让"同一套里几个颜色协调"这件事
 * 由色板本身负责，用户不会把配色搞成彩虹。
 */
object HslTune {

    /** 明度偏移上限（±0.24 ≈ ±24% 亮度）；再大会直接撞到全黑/全白 */
    const val LIGHT_RANGE = 0.24f

    /** 饱和度缩放上限（-60% ~ +60%） */
    const val SAT_RANGE = 0.60f

    /** `#RRGGBB` → (h 0..360, s 0..1, l 0..1) */
    fun toHsl(c: Color): Triple<Float, Float, Float> {
        val r = c.red
        val g = c.green
        val b = c.blue
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (abs(mx - mn) < 1e-6f) return Triple(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return Triple(h, s, l)
    }

    /** (h 0..360, s 0..1, l 0..1) → Color（不透明） */
    fun of(h: Float, s: Float, l: Float): Color {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        if (ss <= 1e-6f) return Color(ll, ll, ll, 1f)
        val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
        val p = 2f * ll - q
        fun hue(t0: Float): Float {
            var t = t0
            if (t < 0f) t += 1f
            if (t > 1f) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 1f / 2f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        return Color(
            hue(hh / 360f + 1f / 3f),
            hue(hh / 360f),
            hue(hh / 360f - 1f / 3f),
            1f,
        )
    }

    /**
     * 微调一个色。
     *
     * @param lightness -[LIGHT_RANGE]..[LIGHT_RANGE]，加到 l 上
     * @param saturation -[SAT_RANGE]..[SAT_RANGE]，按比例缩放 s
     */
    fun tune(base: Color, lightness: Float, saturation: Float): Color {
        val (h, s, l) = toHsl(base)
        val nl = (l + lightness.coerceIn(-LIGHT_RANGE, LIGHT_RANGE)).coerceIn(0.04f, 0.97f)
        val ns = (s * (1f + saturation.coerceIn(-SAT_RANGE, SAT_RANGE))).coerceIn(0f, 1f)
        return of(h, ns, nl)
    }
}

// ── ③ 预设色板（全部来自既有色阶，开箱即达标）────────────────────────

/** 一个预设：名字 + 颜色。名字进无障碍读屏（色块不能只靠颜色表达）。 */
@Immutable
data class PalettePreset(val name: String, val color: Color)

/** 一组预设（浅色或深色下的一行主色 + 一行强调色） */
@Immutable
data class PaletteParams(
    val primary: List<PalettePreset>,
    val accent: List<PalettePreset>,
)

/**
 * 预设色板。
 *
 * 浅色行都取自 `Machine/Crop/Steel/RustRed/Wheat/Soil/Ledger` 里**够深**的那几档
 * （保证能当正文色）；深色行取够亮的那几档。
 *
 * ⚠️ 注意浅色行里**没有品牌橙 `#C74E17`**：它对纸白只有 4.15:1，当小字正文不达标
 * （这正是 `primaryInk` 存在的原因，见 `PrimaryInkContrastTest` 的反向断言）。
 * 品牌橙仍然在「强调色」行里 —— 它作装饰色条是够的（4.37:1 ≥ 3:1）。
 */
object PalettePresets {

    /** 浅色 · 主色（8 档，实测 4.56:1 起） */
    val LIGHT_PRIMARY = listOf(
        PalettePreset("农机橘（深）", Machine.n600),   // #A83D11 5.63:1
        PalettePreset("农机橘（更暗）", Machine.n700), // #852F0D 7.80:1
        PalettePreset("锈红", RustRed.n500),           // #9B2417 7.07:1
        PalettePreset("阜绿", Crop.n600),              // #3F5A2E 6.93:1
        PalettePreset("钢青", Steel.n500),             // #345A66 6.71:1
        PalettePreset("松针绿", Ledger.Pine),          // #1F4A3D 8.93:1
        PalettePreset("麦秆黄（深）", Wheat.n600),     // #8F6713 4.56:1
        PalettePreset("黑土", Soil.n700),              // #443E36 9.45:1
    )

    /** 浅色 · 强调色（8 档，实测 3.00:1 起；强调色只做色条/高亮线，门槛是 3:1） */
    val LIGHT_ACCENT = listOf(
        PalettePreset("农械橘", Machine.n400),      // #DE6A2E 3.03:1
        PalettePreset("品牌橙", Machine.n500),      // #C74E17 4.15:1
        PalettePreset("麦秆黄", Wheat.n500),        // #B4841B 3.00:1
        PalettePreset("阜绿（亮）", Crop.n400),     // #6D8148 3.85:1
        PalettePreset("钢青（亮）", Steel.n300),    // #6E8D94 3.19:1
        PalettePreset("锈红（亮）", RustRed.n300),  // #D46B58 3.11:1
        PalettePreset("陶土红", Ledger.Clay),       // #B4553C 4.37:1
        PalettePreset("阜绿（深）", Crop.n500),     // #4E6535 5.5:1
    )

    /** 深色 · 主色（8 档，实测 6.57:1 起） */
    val DARK_PRIMARY = listOf(
        PalettePreset("农机橘", Machine.n300),      // #EA8F5E
        PalettePreset("农机橘（浅）", Machine.n200),// #F3B994
        PalettePreset("阜绿", Crop.n200),           // #BCC79B
        PalettePreset("钢青", Steel.n200),          // #A0B6BB
        PalettePreset("松针绿", Ledger.PineDark),   // #8FBFA6
        PalettePreset("麦秆黄", Wheat.n300),        // #DDB84D
        PalettePreset("锈红", RustRed.n200),        // #E7A093
        PalettePreset("土白", Soil.n200),           // #D7D0C4
    )

    /** 深色 · 强调色（8 档，实测 3.47:1 起） */
    val DARK_ACCENT = listOf(
        PalettePreset("农机橘", Machine.n400),      // #DE6A2E
        PalettePreset("品牌橙", Machine.n500),      // #C74E17
        PalettePreset("麦秆黄", Wheat.n300),        // #DDB84D
        PalettePreset("阜绿", Crop.n300),           // #93A46B
        PalettePreset("钢青", Steel.n300),          // #6E8D94
        PalettePreset("锈红", RustRed.n300),        // #D46B58
        PalettePreset("陶土红", Ledger.ClayDark),   // #E0937A
        PalettePreset("土白", Soil.n300),           // #BEB5A6
    )

    fun params(isDark: Boolean): PaletteParams =
        if (isDark) PaletteParams(DARK_PRIMARY, DARK_ACCENT)
        else PaletteParams(LIGHT_PRIMARY, LIGHT_ACCENT)

    /** 微调后仍然"开箱可用"的默认主色（浅色 / 深色） */
    fun defaultPrimary(isDark: Boolean): Color = params(isDark).primary.first().color

    fun defaultAccent(isDark: Boolean): Color = params(isDark).accent.first().color
}

// ── ④ 自定义配色（四个槽：浅/深 × 主/强调）──────────────────────────

/**
 * 用户自选的两个色，**浅色与深色各存一套**。
 *
 * 用 ARGB `Int` 而不是 `Color`：存进 SharedPreferences 只需要一个整数，
 * 读出来再 `Color(int)`；`0` = 这个槽还没设过（不是「黑色」）。
 */
@Immutable
data class CustomPalette(
    val primaryLight: Int = 0,
    val accentLight: Int = 0,
    val primaryDark: Int = 0,
    val accentDark: Int = 0,
) {
    fun primary(isDark: Boolean): Color? = pack(isDark, primaryLight, primaryDark)

    fun accent(isDark: Boolean): Color? = pack(isDark, accentLight, accentDark)

    private fun pack(isDark: Boolean, light: Int, dark: Int): Color? {
        val v = if (isDark) dark else light
        return if (v == 0) null else Color(v)
    }

    /** 该模式下四个槽是否都填了（缺一个就不该让用户选「自定义」，否则界面半套色） */
    fun complete(isDark: Boolean): Boolean = primary(isDark) != null && accent(isDark) != null

    /** 写一套（只动该模式的两个槽，另一套原样保留 —— 切主题不互相覆盖） */
    fun withMode(isDark: Boolean, primary: Color, accent: Color): CustomPalette =
        if (isDark) copy(primaryDark = primary.toArgbInt(), accentDark = accent.toArgbInt())
        else copy(primaryLight = primary.toArgbInt(), accentLight = accent.toArgbInt())

    /** 清掉某一套（「恢复默认」用；另一套保留） */
    fun cleared(isDark: Boolean): CustomPalette =
        if (isDark) copy(primaryDark = 0, accentDark = 0) else copy(primaryLight = 0, accentLight = 0)

    val hasAny: Boolean get() = this != NONE

    companion object {
        val NONE = CustomPalette()
    }
}

/** Color → ARGB Int（只取 8 位；alpha 强制不透明，避免存成半透明色后墙面发灰） */
fun Color.toArgbInt(): Int {
    val a = 0xFF
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

// ── ⑤ 保存前的对比度闸门 ─────────────────────────────────────────────

/**
 * 把候选主色/强调色放进当前配色里，逐条算对比度。
 *
 * 五条关系（与 `docs/评审-前端设计方案-v3.md` §7 同口径）：
 *
 * | # | 关系 | 门槛 | 为什么 |
 * |---|---|---|---|
 * | 1 | 主色 / 纸底（当文字） | 4.5 | 主色会被当正文色用（链接、数字、选中文字） |
 * | 2 | 主色 / 卡片 | 4.5 | 卡片是最常见的背景 |
 * | 3 | 按钮文字 / 主色 | 4.5 | 实心主按钮上的字 |
 * | 4 | 强调色 / 纸底 | 3.0 | 强调色只做色条/高亮线，按「图形」算 |
 * | 5 | 强调色 / 卡片 | 3.0 | 同上 |
 *
 * 注意 1/2 用的是**主色本身**，不是派生出来的 `primaryInk` —— 自定义色的意思就是
 * 「你自己选的这个色要能当正文」，所以门槛比品牌色更严（品牌橙 4.15:1 就是因此进不了主色行）。
 */
object PaletteGate {

    fun checks(base: InkTokenSet, primary: Color, accent: Color): List<ContrastCheck> {
        // 实心主按钮上的字色：亮色主题是白，暗色主题是墨底（= M3 的 onPrimary，取 paper 近似）
        val onPrimary = if (base.isDark) base.paper else Color.White
        return listOf(
            ContrastCheck("主色/纸底", primary, base.paper, ContrastRules.BODY_MIN),
            ContrastCheck("主色/卡片", primary, base.card, ContrastRules.BODY_MIN),
            ContrastCheck("按钮文字/主色", onPrimary, primary, ContrastRules.BODY_MIN),
            ContrastCheck("强调色/纸底", accent, base.paper, ContrastRules.LARGE_MIN),
            ContrastCheck("强调色/卡片", accent, base.card, ContrastRules.LARGE_MIN),
        )
    }

    /** 不达标的项（空 = 可以保存） */
    fun failures(base: InkTokenSet, primary: Color, accent: Color): List<ContrastCheck> =
        checks(base, primary, accent).filter { !it.pass }

    /**
     * 「主色的**文字**」该用哪个色。
     *
     * 硬朗农业里品牌橙当小字只有 4.15:1，所以有 `primaryInk`（= 更暗一档的 `Machine.n600`）。
     * 一旦主色换成用户自选 / 壁纸色，那条写死的规则就失效了 —— 这里改成**按需推**：
     * 保持色相与饱和度，朝「远离纸底」的方向每次走 3% 明度，直到两处背景都 ≥4.5:1。
     *
     * - 自定义配色：闸门已经保证原色达标，循环通常一次都不跑（返回原色）
     * - 壁纸取色：没有闸门可拦，这里做**尽力而为**的补偿（到不了就返回最接近的那档）
     */
    fun inkVariant(base: InkTokenSet, color: Color): Color {
        if (ContrastRules.ratio(color, base.paper) >= ContrastRules.BODY_MIN &&
            ContrastRules.ratio(color, base.card) >= ContrastRules.BODY_MIN
        ) {
            return color
        }
        val (h, s, l0) = HslTune.toHsl(color)
        val dir = if (base.isDark) 1f else -1f
        var l = l0
        var out = color
        repeat(24) {
            l = (l + dir * 0.03f).coerceIn(0f, 1f)
            out = HslTune.of(h, s, l)
            if (ContrastRules.ratio(out, base.paper) >= ContrastRules.BODY_MIN &&
                ContrastRules.ratio(out, base.card) >= ContrastRules.BODY_MIN
            ) {
                return out
            }
        }
        return out
    }
}
