package cn.dkarbon.enroute

import androidx.compose.ui.graphics.Color
import cn.dkarbon.enroute.ui.theme.ColorSource
import cn.dkarbon.enroute.ui.theme.ContrastRules
import cn.dkarbon.enroute.ui.theme.CustomPalette
import cn.dkarbon.enroute.ui.theme.HslTune
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.PaletteGate
import cn.dkarbon.enroute.ui.theme.PalettePresets
import cn.dkarbon.enroute.ui.theme.StyleSet
import cn.dkarbon.enroute.ui.theme.baseTokenSet
import cn.dkarbon.enroute.ui.theme.toArgbInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 票 25：主题自定义配色（预设 + 微调 + 对比度闸门）。
 *
 * 这一组锁的是**规则**，不是像素：
 * 1. 三档取色来源（固定 / 壁纸 / 自定义）的取值与回落；
 * 2. **预设必须开箱达标** —— 两种主题 × 浅/深 × 主/强调，一个都不许漏；
 * 3. 不达标就不许保存，且要能说清是**哪一对**不达标；
 * 4. 浅色与深色各自独立（改一套不动另一套）；
 * 5. 明度/饱和度微调不动色相（否则"协调的色板"会被用户调成彩虹）。
 */
class CustomPaletteTest {

    private val hardLight = baseTokenSet(StyleSet.HARD, isDark = false)
    private val hardDark = baseTokenSet(StyleSet.HARD, isDark = true)
    private val ledgerLight = baseTokenSet(StyleSet.EDITORIAL, isDark = false)
    private val ledgerDark = baseTokenSet(StyleSet.EDITORIAL, isDark = true)

    // ── ① 三档取色来源 ─────────────────────────────────────

    @Test
    fun `取色来源有三档且自定义不乱回落`() {
        assertEquals(ColorSource.CUSTOM, ColorSource.normalize(ColorSource.CUSTOM))
        assertEquals(ColorSource.CUSTOM, ColorSource.normalize(2))
        assertTrue(ColorSource.isValid(ColorSource.CUSTOM))
        // 垃圾值仍然回落到固定
        assertEquals(ColorSource.FIXED, ColorSource.normalize(99))
        assertFalse(ColorSource.isValid(99))
        assertEquals("自定义配色", ColorSource.name(ColorSource.CUSTOM))
        assertEquals("自定义", ColorSource.shortName(ColorSource.CUSTOM))
    }

    // ── ② 预设开箱达标 ─────────────────────────────────────

    @Test
    fun `浅色预设全部达标_硬朗农业`() = assertPresets(cases = listOf(hardLight))

    @Test
    fun `浅色预设全部达标_账页`() = assertPresets(cases = listOf(ledgerLight))

    @Test
    fun `深色预设全部达标_硬朗农业`() = assertPresets(cases = listOf(hardDark))

    @Test
    fun `深色预设全部达标_账页`() = assertPresets(cases = listOf(ledgerDark))

    private fun assertPresets(cases: List<InkTokenSet>) {
        val isDark = cases.first().isDark
        val params = PalettePresets.params(isDark)
        assertTrue("主色预设至少 6 个", params.primary.size >= 6)
        assertTrue("强调色预设至少 6 个", params.accent.size >= 6)
        for (base in cases) {
            params.primary.forEach { p ->
                params.accent.forEach { a ->
                    val bad = PaletteGate.failures(base, p.color, a.color)
                    assertTrue(
                        "预设「${p.name} + ${a.name}」在 ${if (isDark) "深色" else "浅色"}" +
                            "/${if (base === hardLight || base === hardDark) "硬朗" else "账页"} 下不达标：" +
                            bad.joinToString { "${it.text}（需 ${ContrastRules.fmt(it.min)}）" },
                        bad.isEmpty(),
                    )
                }
            }
        }
    }

    // ── ③ 低对比拦在保存之前 ───────────────────────────────

    @Test
    fun `低对比主色被闸门拦住并说清是哪一对`() {
        // 浅黄当主色：纸白上几乎看不见（远低于 4.5:1）
        val paleYellow = Color(0xFFE8C547)
        val bad = PaletteGate.failures(hardLight, paleYellow, Color(0xFF8C1D18))
        assertTrue("浅黄当主色必须被拦", bad.isNotEmpty())
        val labels = bad.map { it.label }
        assertTrue("要说清是主色/纸底不达标：$labels", labels.contains("主色/纸底"))
        assertTrue(
            "实测值要小于门槛",
            bad.first { it.label == "主色/纸底" }.ratio < ContrastRules.BODY_MIN,
        )
        // 换成预设里够深的那个色就该放行
        val ok = PaletteGate.failures(hardLight, PalettePresets.defaultPrimary(false), PalettePresets.defaultAccent(false))
        assertTrue("默认预设必须放行", ok.isEmpty())
    }

    @Test
    fun `深色下太暗的主色同样被拦`() {
        // 在墨底上放一个深褐：看不清
        val deepBrown = Color(0xFF2B1A12)
        val bad = PaletteGate.failures(hardDark, deepBrown, PalettePresets.defaultAccent(true))
        assertTrue(bad.isNotEmpty())
        assertTrue(bad.map { it.label }.contains("主色/纸底"))
    }

    @Test
    fun `强调色门槛是 3 比 1 不是 4 比 5`() {
        // 品牌橙对纸底 4.15:1：当正文不够、当强调够 —— 它必须出现在强调行、不在主色行
        val brand = Color(0xFFC74E17)
        val asPrimary = PaletteGate.failures(hardLight, brand, Color(0xFF8F6713))
        assertTrue("品牌橙当主色应被拦", asPrimary.any { it.label.startsWith("主色") })
        val asAccent = PaletteGate.failures(hardLight, PalettePresets.defaultPrimary(false), brand)
        assertTrue("品牌橙当强调色应放行：${asAccent.map { it.text }}", asAccent.isEmpty())
        assertFalse(
            "浅色主色行里不该有品牌橙",
            PalettePresets.LIGHT_PRIMARY.any { it.color.toArgbInt() == brand.toArgbInt() },
        )
    }

    // ── ④ 浅色 / 深色各自独立 ─────────────────────────────

    @Test
    fun `浅色与深色各存一套写在四个槽里`() {
        val lightPrimary = PalettePresets.defaultPrimary(false)
        val lightAccent = PalettePresets.defaultAccent(false)
        val darkPrimary = PalettePresets.defaultPrimary(true)
        val darkAccent = PalettePresets.defaultAccent(true)

        val onlyLight = CustomPalette.NONE.withMode(false, lightPrimary, lightAccent)
        assertEquals(lightPrimary.toArgbInt(), onlyLight.primaryLight)
        assertEquals(0, onlyLight.primaryDark)
        assertTrue(onlyLight.complete(false))
        assertFalse("深色槽还没设，不能算完整", onlyLight.complete(true))
        assertNull("深色没设时读出来是 null（主题层据此回退自带色板）", onlyLight.primary(true))

        // 再写深色：浅色两个槽必须原样保留
        val both = onlyLight.withMode(true, darkPrimary, darkAccent)
        assertEquals(lightPrimary.toArgbInt(), both.primaryLight)
        assertEquals(lightAccent.toArgbInt(), both.accentLight)
        assertEquals(darkPrimary.toArgbInt(), both.primaryDark)
        assertTrue(both.complete(true) && both.complete(false))
        assertEquals(lightPrimary, both.primary(false))
        assertEquals(darkPrimary, both.primary(true))
    }

    @Test
    fun `恢复默认只清当前那一套`() {
        val both = CustomPalette.NONE
            .withMode(false, PalettePresets.defaultPrimary(false), PalettePresets.defaultAccent(false))
            .withMode(true, PalettePresets.defaultPrimary(true), PalettePresets.defaultAccent(true))
        val clearedLight = both.cleared(false)
        assertEquals(0, clearedLight.primaryLight)
        assertNotNull("深色那套不该被动", clearedLight.primary(true))
        assertTrue(clearedLight.complete(true))
        assertFalse(clearedLight.complete(false))
        assertTrue("全空时 hasAny 为假", CustomPalette.NONE.cleared(true).cleared(false).hasAny.not())
    }

    // ── ⑤ 微调 ────────────────────────────────────────────

    @Test
    fun `明度微调变亮变暗但不动色相`() {
        val base = PalettePresets.defaultPrimary(false)
        val (h0, s0, l0) = HslTune.toHsl(base)
        val lighter = HslTune.tune(base, HslTune.LIGHT_RANGE, 0f)
        val darker = HslTune.tune(base, -HslTune.LIGHT_RANGE, 0f)

        assertTrue("明度应上升", HslTune.toHsl(lighter).third > l0)
        assertTrue("明度应下降", HslTune.toHsl(darker).third < l0)
        // 色相不许漂（±1° 容差：RGB→HSL 往返有浮点误差）
        assertTrue("色相不许被微调改掉", abs(HslTune.toHsl(lighter).first - h0) < 1f)
        assertTrue("饱和度也不许被明度滑条改掉", abs(HslTune.toHsl(lighter).second - s0) < 0.02f)
    }

    @Test
    fun `饱和度微调只动饱和度`() {
        val base = PalettePresets.defaultPrimary(false)
        val (h0, s0, _) = HslTune.toHsl(base)
        val vivid = HslTune.tune(base, 0f, HslTune.SAT_RANGE)
        val muted = HslTune.tune(base, 0f, -HslTune.SAT_RANGE)
        assertTrue("饱和度应上升", HslTune.toHsl(vivid).second > s0)
        assertTrue("饱和度应下降", HslTune.toHsl(muted).second < s0)
        assertTrue("色相不许变", abs(HslTune.toHsl(vivid).first - h0) < 1f)
    }

    @Test
    fun `微调到极限也不会越界或变半透明`() {
        val base = PalettePresets.defaultPrimary(false)
        listOf(-1f, 0f, 1f).forEach { l ->
            listOf(-1f, 0f, 1f).forEach { s ->
                val c = HslTune.tune(base, l * HslTune.LIGHT_RANGE, s * HslTune.SAT_RANGE)
                assertEquals("微调结果必须不透明", 1f, c.alpha, 0.001f)
                assertTrue(c.red in 0f..1f && c.green in 0f..1f && c.blue in 0f..1f)
            }
        }
    }

    // ── ⑥ 主色当文字：换色后要跟着换 ───────────────────────

    @Test
    fun `自定义主色的文字档直接用它自己`() {
        // 自定义色已经过 4.5:1 闸门，inkVariant 不应再改它
        val p = PalettePresets.defaultPrimary(false)
        assertEquals(p, PaletteGate.inkVariant(hardLight, p))
    }

    @Test
    fun `壁纸色没闸门时 inkVariant 尽力补到 4 点 5`() {
        // 一个明显太亮的"壁纸色"：亮橙压在纸白上肯定不够
        val wallpaperish = Color(0xFFEA8F5E)
        assertTrue(ContrastRules.ratio(wallpaperish, hardLight.paper) < ContrastRules.BODY_MIN)
        val tuned = PaletteGate.inkVariant(hardLight, wallpaperish)
        assertTrue(
            "补偿后对纸底要 ≥4.5：${ContrastRules.fmt(ContrastRules.ratio(tuned, hardLight.paper))}",
            ContrastRules.ratio(tuned, hardLight.paper) >= ContrastRules.BODY_MIN,
        )
        assertTrue(
            "对卡片也要 ≥4.5",
            ContrastRules.ratio(tuned, hardLight.card) >= ContrastRules.BODY_MIN,
        )
        // 深色方向相反：要往亮的一端走
        val darkWallpaper = Color(0xFF7A3A1E)
        val darkTuned = PaletteGate.inkVariant(hardDark, darkWallpaper)
        assertTrue(
            "暗色下补偿后对墨底要 ≥4.5",
            ContrastRules.ratio(darkTuned, hardDark.paper) >= ContrastRules.BODY_MIN,
        )
    }

    // ── ⑦ 存储形态 ────────────────────────────────────────

    @Test
    fun `颜色存成不透明 ARGB 整数`() {
        val c = Color(0xFFA83D11)
        assertEquals(0xFFA83D11.toInt(), c.toArgbInt())
        // 半透明色也强制存成不透明（否则墙面会发灰）
        val half = Color(0x80A83D11)
        assertEquals(0xFFA83D11.toInt(), half.toArgbInt())
        // 往返一致
        assertEquals(c, Color(c.toArgbInt()))
        for (p in PalettePresets.LIGHT_PRIMARY) {
            assertEquals(p.name, p.color, Color(p.color.toArgbInt()))
        }
    }

    // ── ⑧ 对比度公式与既有主色档一致（防两处各写一份漂移）──────

    @Test
    fun `对比度公式与既有断言同口径`() {
        // 品牌橙 / 硬朗卡片的 4.37:1 是 PrimaryInkContrastTest 里已锁过的事实
        val brandOnCard = ContrastRules.ratio(Color(0xFFC74E17), Color(0xFFFAF8F4))
        assertEquals(4.37, brandOnCard, 0.02)
        // 同色比自己是 1:1
        assertEquals(1.0, ContrastRules.ratio(Color(0xFF123456), Color(0xFF123456)), 0.001)
    }
}
