package cn.dkarbon.enroute

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cn.dkarbon.enroute.ui.theme.Ledger
import cn.dkarbon.enroute.ui.theme.Machine
import cn.dkarbon.enroute.ui.theme.Soil
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主色对比度锁（2026-11-14）。
 *
 * ## 为什么有这份测试
 * 品牌主色 `#C74E17` 对纸底只有 **4.15:1**（对卡片 4.37:1），低于正文 4.5:1。
 * 品牌色不许改，所以「主色**文字**」单独用更暗一档的 `primaryInk`（= `Machine.n600` `#A83D11`）。
 * 这份测试把两件事钉死：
 *
 * **正向**：`primaryInk` 在 4 组「底色 × 明暗」上都 ≥ 4.5:1（含账页的两组）。
 *  → 以后有人调 `paper` / `card` / `Surf`，这里立刻红。
 *
 * **反向**：`primary` 在硬朗亮色纸底上**必须** < 4.5:1。
 *  → 这条看着别扭，其实是护栏：哪天有人「顺手把主色调亮一点让对比度达标」，
 *    测试会提醒他那等于改品牌色；正确做法是给小字用 `primaryInk`，不是动品牌色。
 *
 * 只有 [InkTokenSet] 的纯色值被引用，不构造 `FormSpec` / `FontFamily`，
 * 所以能在纯 JVM 上跑（不需要 Robolectric）。
 */
class PrimaryInkContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    // HARD 卡片色（Theme.kt 里 InkTokenSet.Light.card 的实值）
    private val hardCard = Color(0xFFFAF8F4)

    @Test
    fun `primaryInk passes 4_5 on all four surfaces`() {
        // 硬朗 亮：Machine.n600 = #A83D11
        assertTrue(
            "hard light: primaryInk/paper 只有 ${ratio(Machine.n600, Soil.n50)}",
            ratio(Machine.n600, Soil.n50) >= 4.5,
        )
        assertTrue(
            "hard light: primaryInk/card 只有 ${ratio(Machine.n600, hardCard)}",
            ratio(Machine.n600, hardCard) >= 4.5,
        )
        // 硬朗 暗：Machine.n300
        assertTrue(
            "hard dark: primaryInk/paper 只有 ${ratio(Machine.n300, Soil.n950)}",
            ratio(Machine.n300, Soil.n950) >= 4.5,
        )
        assertTrue(
            "hard dark: primaryInk/card 只有 ${ratio(Machine.n300, Soil.n900)}",
            ratio(Machine.n300, Soil.n900) >= 4.5,
        )
        // 账页 亮 / 暗：松针绿两档
        assertTrue(
            "ledger light: primaryInk/paper 只有 ${ratio(Ledger.Pine, Ledger.Paper)}",
            ratio(Ledger.Pine, Ledger.Paper) >= 4.5,
        )
        assertTrue(
            "ledger light: primaryInk/card 只有 ${ratio(Ledger.Pine, Ledger.Card)}",
            ratio(Ledger.Pine, Ledger.Card) >= 4.5,
        )
        assertTrue(
            "ledger dark: primaryInk/paper 只有 ${ratio(Ledger.PineDark, Ledger.PaperDark)}",
            ratio(Ledger.PineDark, Ledger.PaperDark) >= 4.5,
        )
        assertTrue(
            "ledger dark: primaryInk/card 只有 ${ratio(Ledger.PineDark, Ledger.CardDark)}",
            ratio(Ledger.PineDark, Ledger.CardDark) >= 4.5,
        )
    }

    @Test
    fun `brand primary stays below 4_5 on light paper - that is why primaryInk exists`() {
        val r = ratio(Machine.n500, Soil.n50)
        assertTrue(
            "品牌主色 #C74E17 在纸底上变成 ${r}:1，如果它已经 ≥4.5，" +
                "说明品牌色被改亮了 —— 那不是本项目的改法（要么保留品牌色+用 primaryInk，要么先跟主人确认）",
            r < 4.5,
        )
    }

    @Test
    fun `body ink keeps its headroom`() {
        // 顺带锁住正文与二级/三级文字，防止有人为了「好看」把它们调灰
        assertTrue("ink/paper 低于 4.5", ratio(Soil.n900, Soil.n50) >= 4.5)
        assertTrue("inkSoft/paper 低于 4.5", ratio(Soil.n700, Soil.n50) >= 4.5)
        assertTrue("inkFaint/paper 低于 4.5", ratio(Soil.n600, Soil.n50) >= 4.5)
    }
}
