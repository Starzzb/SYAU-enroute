package cn.dkarbon.enroute

import androidx.compose.ui.graphics.Color
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.timetable.Tt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 深色模式下「卡片看不清楚」的回归锁（2026-11-21，主人点名）。
 *
 * ## 背景
 *
 * 暗档原来的取值让卡片几乎消失：card #1F1C17 与 paper #191713 只差 3 级亮度、
 * outline #8C8474 对卡片约 2.6:1、四款 washDark 亮度差又跳得远 ——
 * 结果就是「看不清卡片边界、四块洗色像补丁」。
 *
 * ## 锁什么
 *
 * 1. 卡片与页面底可分：card 对 paper ≥ 1.12:1；
 * 2. 描边可用：outline 对 card ≥ 3:1；
 * 3. 四款 washDark 亮度接近：最大/最小相对亮度比 ≤ 1.45（同亮度、不同色相）；
 * 4. spineDark 比 washDark 亮但不如正文刺眼；
 * 5. 正文仍最亮：ink 对每款 washDark ≥ 7:1（课名始终读得清）。
 *
 * 浅色档沿用既有的 ContrastAuditTest。
 */
class DarkCardVisibilityTest {

    private fun argb(c: Color): Long = (c.value shr 32).toLong() and 0xFFFFFFFFL

    private fun channel(v: Long, shift: Int): Double {
        val c = ((v shr shift) and 0xFF).toDouble() / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun luminance(c: Color): Double {
        val v = argb(c)
        return 0.2126 * channel(v, 16) + 0.7152 * channel(v, 8) + 0.0722 * channel(v, 0)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val dark = InkTokenSet.Dark

    @Test
    fun `卡片与页面底在深色下可分辨`() {
        val r = ratio(dark.card, dark.paper)
        assertTrue("card 对 paper 只有 " + r + ":1，卡片会看不见", r >= 1.12)
        val ra = ratio(dark.cardAlt, dark.card)
        assertTrue("cardAlt 对 card 只有 " + ra + ":1，次级面分不出来", ra >= 1.10)
    }

    @Test
    fun `描边在深色下够清楚`() {
        for ((name, bg) in listOf("paper" to dark.paper, "card" to dark.card, "cardAlt" to dark.cardAlt)) {
            val r = ratio(dark.outline, bg)
            assertTrue("outline 对 " + name + " 只有 " + r + ":1", r >= 3.0)
        }
    }

    @Test
    fun `四款洗色亮度接近只靠色相区分`() {
        val lums = Tt.Fams.map { luminance(it.washDark) }
        val spread = lums.max() / lums.min()
        assertTrue("washDark 亮度跨 " + spread + " 倍，会看成不同亮度的补丁", spread <= 1.45)
    }

    @Test
    fun `竖脊比洗色亮但不如正文刺眼`() {
        for ((i, fam) in Tt.Fams.withIndex()) {
            val onWash = ratio(fam.spineDark, fam.washDark)
            assertTrue("第 " + (i + 1) + " 款 spineDark 对 washDark 只有 " + onWash + ":1", onWash >= 1.8)
            // 但竖脊不能比正文还亮 —— 白条会抢课名（直接比相对亮度，不比 ratio：
            // ratio 对「两个亮色」不敏感，会给出 2.7 这种看着很大、实际两者都暗的数字）
            assertTrue(
                "第 " + (i + 1) + " 款 spineDark 比正文还亮，会抢课名",
                luminance(fam.spineDark) < luminance(dark.ink),
            )
        }
    }

    @Test
    fun `课名在每款洗色上都读得清`() {
        for ((i, fam) in Tt.Fams.withIndex()) {
            val r = ratio(dark.ink, fam.washDark)
            assertTrue("第 " + (i + 1) + " 款洗色上课名只有 " + r + ":1", r >= 7.0)
        }
    }
}