package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedGuard
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 抓取闸门的口径（2026-11-27）。
 *
 * 这份测试的意义：闸门第一版只放行 `*.syau.edu.cn`，一刀切把 9 个校外资讯源判成「站外主机」，
 * 界面上显示成功、实际一片「失败」。政策必须被锁住，改的时候先看这里。
 */
class FeedGuardTest {

    @Test
    fun `校内静态页放行`() {
        assertNull(FeedGuard.check("https://jwc.syau.edu.cn/info/1249/10703.htm"))
        assertNull(FeedGuard.check("https://news.syau.edu.cn/list.html"))
        assertNull(FeedGuard.check("https://www.syau.edu.cn/"))
    }

    @Test
    fun `校内带查询参数默认禁止 两个已知列表页除外`() {
        assertNotNull(FeedGuard.check("https://news.syau.edu.cn/list?page=2"))
        // 创新创业学院、团委的列表页只有查询参数形式，登记为例外
        assertNull(FeedGuard.check("https://cxcy.syau.edu.cn/comp/front/comp/list?page=1"))
        assertNull(FeedGuard.check("https://tw.syau.edu.cn/channel.jsp?urltype=x"))
    }

    @Test
    fun `主站 info 目录禁止 学院子站不禁止`() {
        assertNotNull(FeedGuard.check("https://www.syau.edu.cn/info/1002/1234.htm"))
        assertNull(FeedGuard.check("https://jwc.syau.edu.cn/info/1249/10703.htm"))
    }

    @Test
    fun `登记过的校外源放行`() {
        // 这九条都是 App 里真实在用的公开信息源（见 FeedModels 的 listUrl）
        listOf(
            "https://jobsyau.jysd.com/",
            "https://www.tiaozhanbei.net/tzb",
            "https://www.cahe.edu.cn/site/term/373.html",
            "https://www.52jingsai.com/bisai/xkjn/bangdan/",
            "https://cdn1.52jingsai.com/a.jpg",
            "http://www.cpta.com.cn/notice.html",
            "https://www.fwwb.org.cn/",
            "https://jyt.ln.gov.cn/xwzx/",
            "https://ncre.neea.edu.cn/html1/category/1507/872-1.htm",
            "https://cet.neea.edu.cn/html1/category/16093/1124-1.htm",
            "https://ntce.neea.edu.cn/html1/category/1507/1148-1.htm",
            "https://www.neea.edu.cn/html1/category/1508/151-1.htm",
        ).forEach { assertNull("应放行：$it", FeedGuard.check(it)) }
    }

    @Test
    fun `没登记的站外主机一律拒`() {
        assertNotNull(FeedGuard.check("https://evil.example.com/x.htm"))
        // 只是字符串后缀像，并不是子域 —— 别被拼出来骗过去
        assertNotNull(FeedGuard.check("https://notsyau.edu.cn/x.htm"))
        assertNotNull(FeedGuard.check("https://syau.edu.cn.evil.com/x.htm"))
    }

    @Test
    fun `非 http 协议与坏地址拒`() {
        assertNotNull(FeedGuard.check("file:///etc/passwd"))
        assertNotNull(FeedGuard.check("javascript:alert(1)"))
        assertNotNull(FeedGuard.check("不是地址"))
    }

    @Test
    fun `读取上限生效 超了就抛`() {
        val big = ByteArray(2048) { 65 }
        val ok = FeedGuard.readCapped(big.inputStream(), 4096)
        assertTrue(ok.size == 2048)
        val thrown = runCatching { FeedGuard.readCapped(big.inputStream(), 1024) }.exceptionOrNull()
        assertTrue("超限应抛 TooLarge，实际：$thrown", thrown is FeedGuard.TooLarge)
    }

    @Test
    fun `同主机限速按主机分开记`() {
        // 第一次不用等；紧接着第二次要等；换主机不用等
        FeedGuard.waitFor("limit-a.test")
        val second = FeedGuard.waitFor("limit-a.test")
        assertTrue("同一主机连续两次应要求等待，实际 $second", second > 0)
        assertTrue(FeedGuard.waitFor("limit-b.test") == 0L)
    }
}