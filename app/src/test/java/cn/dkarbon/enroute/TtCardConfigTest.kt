package cn.dkarbon.enroute

import cn.dkarbon.enroute.ui.theme.FormSpec
import cn.dkarbon.enroute.ui.timetable.TtCardConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表版式配置的映射锁（m39，2026-11-21 真机四次返工后补）。
 *
 * 真机踩过的四次：
 * 1. `@Volatile var` 存开关 → Compose 不订阅，切了不动；
 * 2. 加 `revision: Int` 手动触发 → 同样不订阅；
 * 3. 全局单例 `by mutableStateOf` → 配置有两份真相（AppState + 单例），抄写漏了就不同步；
 * 4. 版式回退到 `Form.current` → 老用户 `styleSet=EDITORIAL`，两个选项都渲染成账页。
 *
 * 所以这里锁三件事：默认值是纸卡、layout=1 是账页、以及「不看 styleSet」。
 */
class TtCardConfigTest {

    @Test
    fun `默认配置等于改造前行为`() {
        val d = TtCardConfig()
        assertEquals("默认必须是纸卡", 0, d.layout)
        assertEquals("默认必须是四色", 0, d.tint)
        assertTrue("默认开竖脊", d.spine)
        assertTrue("默认开侵蚀", d.erode)
        assertTrue("默认开幽灵", d.ghost)
        assertTrue("默认开食物图标", d.food)
        assertTrue("默认开当前课描边", d.nowRing)
    }

    @Test
    fun `只有 layout 等于 1 才是账页`() {
        assertFalse(TtCardConfig(layout = 0).ledger)
        assertTrue(TtCardConfig(layout = 1).ledger)
    }

    @Test
    fun `两种版式映射到两个不同的 FormSpec`() {
        // 这一条防的是「两个选项都渲染成账页」那个真机 bug
        val paper = FormSpec.Hard
        val ledger = FormSpec.Ledger
        assertFalse("纸卡不该是 editorial", paper.editorial)
        assertTrue("账页必须是 editorial", ledger.editorial)
    }
}