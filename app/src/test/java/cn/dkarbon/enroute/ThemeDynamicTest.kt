package cn.dkarbon.enroute

import androidx.compose.ui.graphics.Color
import cn.dkarbon.enroute.ui.theme.ColorSource
import cn.dkarbon.enroute.ui.theme.DynamicColorRules
import cn.dkarbon.enroute.ui.theme.InkTokenSet
import cn.dkarbon.enroute.ui.theme.StyleSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 票 16 追加项：学院纸本下线 + 壁纸取色。
 *
 * 两条都不需要真机就能锁的部分：
 * 1. **平滑回落**：老用户存着 `styleSet = SCHOLAR(1)`，读出来必须是 `HARD`，
 *    否则会拿一套已下线的语汇渲染（界面像坏了）；
 * 2. **克制取色**：壁纸色**只准**覆盖 primary / accent，
 *    纸白底、墨色正文、砖红/橄榄/琥珀这些语义色一毫米都不许动（数据诚实红线）。
 */
class ThemeDynamicTest {

    // ── ① 学院纸本下线 ────────────────────────────────────

    @Test
    fun `老数据里的学院纸本平滑回落到硬朗农业`() {
        assertEquals(StyleSet.HARD, StyleSet.normalize(StyleSet.SCHOLAR))
        // 现行两套原样保留
        assertEquals(StyleSet.HARD, StyleSet.normalize(StyleSet.HARD))
        assertEquals(StyleSet.EDITORIAL, StyleSet.normalize(StyleSet.EDITORIAL))
        // 垃圾值也回落（老/坏数据不能让界面变空白）
        assertEquals(StyleSet.HARD, StyleSet.normalize(99))
        assertEquals(StyleSet.HARD, StyleSet.normalize(-1))
    }

    @Test
    fun `设置页不再列出学院纸本`() {
        assertFalse("学院纸本不该出现在可选项里", StyleSet.SELECTABLE.contains(StyleSet.SCHOLAR))
        assertTrue(StyleSet.SELECTABLE.contains(StyleSet.HARD))
        // token 代码保留（类没删），只是 UI 不露出
        assertEquals("学院纸本", StyleSet.name(StyleSet.SCHOLAR))
    }

    // ── ② 壁纸取色 ───────────────────────────────────────

    @Test
    fun `低版本不提供壁纸取色`() {
        assertFalse(DynamicColorRules.available(30))
        assertFalse(DynamicColorRules.available(29))
        assertTrue(DynamicColorRules.available(31))
        assertTrue(DynamicColorRules.available(35))
    }

    @Test
    fun `取色只覆盖主色与强调色其余语义色一字不动`() {
        val base = InkTokenSet.Light
        val wallpaperPrimary = Color(0xFF3F6F8F)
        val wallpaperAccent = Color(0xFF4E7F9C)
        val out = DynamicColorRules.override(base, wallpaperPrimary, wallpaperAccent)

        // 换掉的只有这两个
        assertEquals(wallpaperPrimary, out.primary)
        assertEquals(wallpaperAccent, out.accent)
        // 语义色必须原样（数据诚实：砖红=错误/不及格，不能被壁纸染成蓝色）
        assertEquals(base.brick, out.brick)
        assertEquals(base.olive, out.olive)
        assertEquals(base.amber, out.amber)
        // 纸白底 / 墨色正文 / 描边 / 明暗标记都不动
        assertEquals(base.paper, out.paper)
        assertEquals(base.card, out.card)
        assertEquals(base.ink, out.ink)
        assertEquals(base.inkSoft, out.inkSoft)
        assertEquals(base.inkFaint, out.inkFaint)
        assertEquals(base.rule, out.rule)
        assertEquals(base.outline, out.outline)
        assertEquals(base.outlineStrong, out.outlineStrong)
        assertEquals(base.isDark, out.isDark)
    }

    @Test
    fun `暗色下挑更亮的档位`() {
        val light = DynamicColorRules.shades(isDark = false)
        val dark = DynamicColorRules.shades(isDark = true)
        // 亮色主题压纸白底要够深；暗色主题压黑土要够亮
        assertTrue("亮色档位应更靠深色端", light.first > dark.first)
        assertTrue(light.second > dark.second)
        assertEquals(600 to 400, light)
        assertEquals(200 to 300, dark)
    }

    @Test
    fun `系统取色资源都落在 API31 的取色表里`() {
        (100..1000 step 100).forEach { shade ->
            assertTrue("$shade 档应有资源", DynamicColorRules.systemRes(shade) != null)
        }
        // 表外档位不给（避免越界拿不到东西还不报错）
        assertEquals(null, DynamicColorRules.systemRes(50))
        assertEquals(null, DynamicColorRules.systemRes(1100))
    }

    @Test
    fun `取色来源归一化`() {
        assertEquals(ColorSource.FIXED, ColorSource.normalize(0))
        assertEquals(ColorSource.WALLPAPER, ColorSource.normalize(1))
        assertEquals(ColorSource.FIXED, ColorSource.normalize(7))
        // 票 25 加了第三档 CUSTOM：0/1/2 都合法，表外的才回落
        assertTrue(ColorSource.isValid(0) && ColorSource.isValid(1) && ColorSource.isValid(2))
        assertTrue(ColorSource.isValid(ColorSource.CUSTOM))
        assertFalse(ColorSource.isValid(3))
        assertEquals(ColorSource.CUSTOM, ColorSource.normalize(2))
        assertEquals("跟随壁纸取色", ColorSource.name(ColorSource.WALLPAPER))
        assertEquals("固定", ColorSource.name(ColorSource.FIXED))
        assertEquals("自定义配色", ColorSource.name(ColorSource.CUSTOM))
    }
}
