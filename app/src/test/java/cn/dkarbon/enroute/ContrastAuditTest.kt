package cn.dkarbon.enroute

import androidx.compose.ui.graphics.Color
import cn.dkarbon.enroute.ui.theme.EditorialDarkTokens
import cn.dkarbon.enroute.ui.theme.EditorialLightTokens
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 两套主题（硬朗农业 HARD / 账页 LEDGER）× 明暗两态的可读性对比度锁（2026-11-15）。
 *
 * ## 硬指标（全部 ≥ 门槛才算绿）
 * - 正文（ink）、次要（inkSoft）、三级（inkFaint）对 paper / card / cardAlt 全部 **≥ 4.5:1**
 * - 分隔线（rule）、边框（outline）对 paper / card / cardAlt 全部 **≥ 3:1**
 * - 暗色正文不得是纯白 `#FFFFFF`（用略暖高亮）
 *
 * ## 输出
 * 跑完把全部配对写进 `app/build/reports/contrast-audit.md`（一张表），
 * 与 `docs/设计-对比度实测.md` 保持同口径，作为本次调 token 的证据。
 *
 * ## 为什么自己算亮度
 * 纯 JVM 单测不碰 `android.graphics`；`Color.value` 的高 32 位存着 0xAARRGGBB，
 * 用位移取出 sRGB 通道即可，WCAG 相对亮度公式与 `ContrastRules` 一致。
 */
class ContrastAuditTest {

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

    private fun hex(c: Color): String = "%08X".format(argb(c))

    private fun f(v: Double): String = String.format("%.2f", v)

    private data class Pair_(val label: String, val fg: Color, val bg: Color, val min: Double)

    private val sets = listOf(
        "HARD 亮" to InkTokenSet.Light,
        "HARD 暗" to InkTokenSet.Dark,
        "账页 亮" to EditorialLightTokens,
        "账页 暗" to EditorialDarkTokens,
    )

    private fun pairs(t: InkTokenSet): List<Pair_> {
        val text = listOf("ink" to t.ink, "inkSoft" to t.inkSoft, "inkFaint" to t.inkFaint)
        val bgs = listOf("paper" to t.paper, "card" to t.card, "cardAlt" to t.cardAlt)
        val out = ArrayList<Pair_>()
        for ((tn, fg) in text) for ((bn, bg) in bgs) {
            out += Pair_("$tn/$bn", fg, bg, 4.5)
        }
        val lines = listOf("rule" to t.rule, "outline" to t.outline)
        for ((ln, fg) in lines) for ((bn, bg) in bgs) {
            out += Pair_("$ln/$bn", fg, bg, 3.0)
        }
        return out
    }

    @Test
    fun `all text and divider pairs meet WCAG thresholds`() {
        val sb = StringBuilder()
        sb.appendLine("# 对比度实测（自动生成）")
        sb.appendLine()
        sb.appendLine("| 主题 | 配对 | 前景 | 背景 | 对比度 | 门槛 | 结果 |")
        sb.appendLine("|---|---|---|---|---|---|---|")
        var failed = 0
        for ((name, t) in sets) {
            for (p in pairs(t)) {
                val r = ratio(p.fg, p.bg)
                val ok = r >= p.min
                if (!ok) failed++
                sb.appendLine(
                    "| $name | ${p.label} | #${hex(p.fg)} | #${hex(p.bg)} | ${f(r)}:1 | ${p.min} | ${if (ok) "✅" else "❌"} |",
                )
            }
        }
        sb.appendLine()
        sb.appendLine("失败 $failed 条。")
        val dir = File("build/reports")
        dir.mkdirs()
        File(dir, "contrast-audit.md").writeText(sb.toString())

        assertTrue("有 $failed 条配对不达标，明细见 build/reports/contrast-audit.md", failed == 0)
    }

    @Test
    fun `dark body ink is warm highlight not pure white`() {
        assertTrue(
            "HARD 暗色正文不应是纯白 #FFFFFF",
            InkTokenSet.Dark.ink != Color(0xFFFFFFFF),
        )
        assertTrue(
            "账页暗色正文不应是纯白 #FFFFFF",
            EditorialDarkTokens.ink != Color(0xFFFFFFFF),
        )
    }

    @Test
    fun `sanity - reference pair math matches known values`() {
        // 锁公式：Soil.n900 #1A1611 对 Soil.n50 #F5F2EC ≈ 16.11:1
        val r = ratio(cn.dkarbon.enroute.ui.theme.Soil.n900, cn.dkarbon.enroute.ui.theme.Soil.n50)
        assertTrue("公式漂移：$r", kotlin.math.abs(r - 16.11) < 0.05)
    }
}
