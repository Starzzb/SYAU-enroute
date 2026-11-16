package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UpdateDialogPolicy
import cn.dkarbon.enroute.data.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新提醒弹窗的纯逻辑测试（文档 §5 的 6 条）。
 *
 * 第 6 条按定稿版式做了适配：文档 §2.3 第 5 点已把「编号」改成「`·` 前缀」，
 * 所以 `parseNotes` 不再产出 `01/02` 序号 —— 这里锁「无序号、超过 9 条不补零出错」。
 */
class UpdateDialogPolicyTest {

    private fun info(code: Long, minSupported: Long = 0L) = UpdateInfo(
        versionCode = code,
        versionName = "0.2.0+m$code",
        url = "https://github.com/Starzzb/SYAU-enroute/releases/download/v1/a.apk",
        sha256 = "a".repeat(64),
        notes = "",
        minSupported = minSupported,
    )

    // 1. 频率判定：同版本 dismissed → false；不同版本 → true
    @Test
    fun `same version dismissed means no show, different version still shows`() {
        val v = info(200)
        assertFalse(UpdateDialogPolicy.shouldShow(v, 200L))
        assertTrue(UpdateDialogPolicy.shouldShow(v, 0L))
        assertTrue(UpdateDialogPolicy.shouldShow(v, 199L))
        assertTrue(UpdateDialogPolicy.shouldShow(info(201), 200L))
    }

    // 2. notes 切分：`；` 与换行切；`、` 不切分；空串 → 空列表
    @Test
    fun `notes split on semicolon and newline but not on dun comma`() {
        assertEquals(listOf("a", "b、c", "d"), UpdateDialogPolicy.parseNotes("a；b、c\nd"))
        assertEquals(emptyList<String>(), UpdateDialogPolicy.parseNotes(""))
        assertEquals(emptyList<String>(), UpdateDialogPolicy.parseNotes("  ； \n "))
    }

    // 3. notes 截断：超过 8 条 → 前 8 条 + 剩余计数；正好 8 条 → 无计数
    @Test
    fun `notes truncate to eight with overflow count only when exceeded`() {
        val nine = (1..9).map { "第${it}条" }
        val sliced = UpdateDialogPolicy.notesSlice(nine)
        assertEquals(8, sliced.items.size)
        assertEquals(1, sliced.overflow)
        assertEquals("等 1 项", sliced.overflowText)

        val eight = (1..8).map { "第${it}条" }
        val exact = UpdateDialogPolicy.notesSlice(eight)
        assertEquals(8, exact.items.size)
        assertEquals(0, exact.overflow)
        assertEquals("", exact.overflowText)
    }

    // 4. 强制更新不显示「不再提醒」：UI 决策函数在 mandatory 时返回 false
    @Test
    fun `mandatory update hides the never-remind button`() {
        assertFalse(UpdateDialogPolicy.showsDismiss(true))
        assertTrue(UpdateDialogPolicy.showsDismiss(false))
    }

    // 5. dismiss 只压该版本：dismiss 200 后，201 仍该弹
    @Test
    fun `dismissing one version does not suppress the next`() {
        // 状态只存一个 Long（文档 §1.2）：新 versionCode 与旧 dismissedCode 不等 → 弹
        assertTrue(UpdateDialogPolicy.shouldShow(info(201), 200L))
        assertFalse(UpdateDialogPolicy.shouldShow(info(200), 200L))
    }

    // 6. 编号格式（定稿适配）：`·` 前缀取代编号 —— 不产序号，超过 9 条条目完整
    @Test
    fun `parseNotes emits no numbering so ten items have no zero padding bug`() {
        val ten = (1..10).joinToString("；") { "第${it}项" }
        val items = UpdateDialogPolicy.parseNotes(ten)
        assertEquals(10, items.size)
        assertEquals("第1项", items.first())
        assertEquals("第10项", items.last())
        // 旧编号方案的补零两位在这里必须不存在（01/02 序号已随 §2.3 作废）
        assertFalse(items.first().startsWith("0"))
    }
}
