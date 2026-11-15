package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.SessionProbe
import cn.dkarbon.enroute.data.SessionRules
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.UrpClient
import cn.dkarbon.enroute.ui.SessionText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录态三态判定的回归测试（票 04）。
 *
 * 为什么值得测：这是整个功能的**语义核心**。
 * 一旦「连不上」被当成「过期」，离校的学生每次开 App 都会被告知要重登（假警报）；
 * 一旦「过期」被当成「未登录」，用户又会以为自己从没登录过 —— 这正是票 04 要消灭的歧义。
 */
class SessionStateTest {

    // ── 三态判定 ──────────────────────────────────────────

    @Test
    fun `没有会话且没验活 就是未登录`() {
        assertEquals(SessionState.None, SessionRules.stateOf(hasSession = false, probe = null))
    }

    @Test
    fun `有会话没验活 先算已登录 等着验活结论`() {
        assertEquals(SessionState.Active, SessionRules.stateOf(hasSession = true, probe = null))
    }

    @Test
    fun `验活通过 已登录`() {
        assertEquals(
            SessionState.Active,
            SessionRules.stateOf(hasSession = true, probe = SessionProbe.Ok),
        )
    }

    @Test
    fun `验活判定过期 即使手上还握着 cookie 也算过期`() {
        // 关键：服务器不认了就是过期，不能因为本地还有 cookie 就继续宣称有效
        assertEquals(
            SessionState.Expired,
            SessionRules.stateOf(hasSession = true, probe = SessionProbe.Expired),
        )
        assertEquals(
            SessionState.Expired,
            SessionRules.stateOf(hasSession = false, probe = SessionProbe.Expired),
        )
    }

    @Test
    fun `验活连不上时 不当成过期 保留会话`() {
        // 手机不在校园网 → 问不到教务，这时说「过期」是冤枉用户
        assertEquals(
            SessionState.Active,
            SessionRules.stateOf(hasSession = true, probe = SessionProbe.Unreachable),
        )
        assertNotEquals(
            SessionState.Expired,
            SessionRules.stateOf(hasSession = true, probe = SessionProbe.Unreachable),
        )
    }

    @Test
    fun `连不上但没有会话 仍是未登录 不撒谎说有会话`() {
        assertEquals(
            SessionState.None,
            SessionRules.stateOf(hasSession = false, probe = SessionProbe.Unreachable),
        )
    }

    @Test
    fun `只有有效会话才允许打教务接口`() {
        assertTrue(SessionRules.canCall(SessionState.Active))
        assertFalse(SessionRules.canCall(SessionState.None))
        assertFalse(SessionRules.canCall(SessionState.Expired))
    }

    @Test
    fun `过期这件事要记在本机 不能退回未登录`() {
        assertTrue(SessionRules.keepsExpiredMemory(SessionState.Expired))
        assertFalse(SessionRules.keepsExpiredMemory(SessionState.None))
        assertFalse(SessionRules.keepsExpiredMemory(SessionState.Active))
    }

    // ── 验活响应的判定（纯函数）────────────────────────────

    @Test
    fun `校区列表 JSON 数组 = 会话有效`() {
        assertEquals(
            SessionProbe.Ok,
            UrpClient.judgeSession("""[{"campusNumber":"001","campusName":"主校"}]"""),
        )
        // 带 BOM/前导空白的也认
        assertEquals(SessionProbe.Ok, UrpClient.judgeSession("  \n[]"))
    }

    @Test
    fun `CAS 登录页 = 会话过期`() {
        val loginPage = """
            <html><body>
            <form id="loginForm" action="/tpass/login">
              <input type="hidden" name="lt" value="LT-1234-abc"/>
            </form>
            </body></html>
        """.trimIndent()
        assertEquals(SessionProbe.Expired, UrpClient.judgeSession(loginPage))
    }

    @Test
    fun `空响应或别的 HTML = 会话过期 不给数据就当没登录`() {
        assertEquals(SessionProbe.Expired, UrpClient.judgeSession(""))
        assertEquals(SessionProbe.Expired, UrpClient.judgeSession("   \n  "))
        assertEquals(SessionProbe.Expired, UrpClient.judgeSession("<html><body>系统维护</body></html>"))
    }

    // ── 文案：三态必须说三句不同的话 ───────────────────────

    @Test
    fun `三态正名互不相同且各自写清`() {
        assertEquals("未登录", SessionText.stateLabel(SessionState.None))
        assertEquals("已登录（会话有效）", SessionText.stateLabel(SessionState.Active))
        assertEquals("已登录但会话已过期", SessionText.stateLabel(SessionState.Expired))
        assertEquals(
            3,
            listOf(SessionState.None, SessionState.Active, SessionState.Expired)
                .map { SessionText.stateLabel(it) }
                .toSet()
                .size,
        )
    }

    @Test
    fun `未登录的副标题是登录入口 过期的副标题是续期入口`() {
        val none = SessionText.identitySubtitle(SessionState.None, "", false, "")
        val expired = SessionText.identitySubtitle(SessionState.Expired, "", false, "")
        assertTrue(none.contains("登录"))
        assertTrue(expired.contains("过期"))
        assertTrue(expired.contains("续期"))
        // 过期的文案必须提到密码处理方式（票 04 验收项）
        assertTrue(expired.contains("不保存"))
        assertNotEquals(none, expired)
    }

    @Test
    fun `有效会话优先显示学籍摘要 没摘要时才用验活说明`() {
        assertEquals(
            "24土管1 · 土地资源管理 · 2024级",
            SessionText.identitySubtitle(SessionState.Active, "24土管1 · 土地资源管理 · 2024级", false, "忽略我"),
        )
        assertEquals(
            "正在读取学籍…",
            SessionText.identitySubtitle(SessionState.Active, "", true, ""),
        )
        assertEquals(
            "这次没连上教务（多半不在校园网）",
            SessionText.identitySubtitle(SessionState.Active, "", false, "这次没连上教务（多半不在校园网）"),
        )
        // 有有效会话时副标题只交代会话状态，不给登录入口提示
        assertEquals(
            "已登录 · 会话已加密保存，下次打开不用重登",
            SessionText.identitySubtitle(SessionState.Active, "", false, ""),
        )
    }

    @Test
    fun `接口面板提示按三态分开 过期指向续期而不是重新登录`() {
        val none = SessionText.noSessionHint(SessionState.None)
        val expired = SessionText.noSessionHint(SessionState.Expired)
        assertTrue(none.contains("还没登录"))
        assertTrue(expired.contains("过期"))
        assertTrue(expired.contains("续期"))
        assertNotEquals(none, expired)
    }

    @Test
    fun `过期文案说明数据还在 免得以为课表被清空`() {
        assertTrue(SessionText.EXPIRED_NOTE.contains("过期"))
        assertTrue(SessionText.EXPIRED_NOTE.contains("还在"))
        assertTrue(SessionText.UNVERIFIED_NOTE.contains("没法确认"))
        // 验活没做成 ≠ 过期：这句话里不许出现「过期」这种断言
        assertFalse(SessionText.UNVERIFIED_NOTE.contains("已过期"))
    }

    @Test
    fun `只有非有效会话的身份卡才可点`() {
        assertTrue(SessionText.identityClickable(SessionState.None))
        assertTrue(SessionText.identityClickable(SessionState.Expired))
        assertFalse(SessionText.identityClickable(SessionState.Active))
    }

    @Test
    fun `登录入口标题区分首次登录与续期`() {
        assertEquals("登录教务", SessionText.actionLabel(SessionState.None))
        assertEquals("续期登录（会话已过期）", SessionText.actionLabel(SessionState.Expired))
    }
}
