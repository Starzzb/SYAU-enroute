package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UrpHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * 锁住「发给校内的请求头」这一层。
 *
 * ## 为什么断言看 [UrpHeaders.apply] 的返回值，而不是 `conn.getRequestProperty`
 *
 * JVM 的 `HttpURLConnection` 有一份「受限头黑名单」（`Origin`、`Connection`、以及所有 `Sec-` 开头的），
 * 命中就**静默丢弃**，读回来是 null；Android 的实现不是同一套。
 * 如果在单测里靠读回来断言，测到的是 JDK 的脾气，不是我们发出的东西，
 * 还会诱导后人为了"让测试过"而删掉这些头。
 *
 * 所以 [UrpHeaders.apply] 把「这次设了什么」作为账目返回，单测核账；
 * 少数不被黑名单拦的头（`Accept` / `Accept-Language` / `Content-Type` / `X-Requested-With`）
 * 再顺手用 `getRequestProperty` 交叉验证一遍，确保不是只记在账上而没真的设。
 */
class UrpHeadersTest {

    private fun conn(path: String, host: String = "10.2.0.47"): HttpURLConnection =
        URL("http://$host$path").openConnection() as HttpURLConnection

    /** 跑一次 apply，拿到「账目」与连接本身 */
    private fun sent(
        path: String,
        method: String = "GET",
        nav: Boolean = false,
        host: String = "10.2.0.47",
        referer: String? = null,
        omitReferer: Boolean = false,
    ): Pair<Map<String, String>, HttpURLConnection> {
        val c = conn(path, host)
        val led = UrpHeaders.apply(
            c, path = path, method = method, nav = nav,
            referer = referer, omitReferer = omitReferer,
        )
        return led to c
    }

    @Test
    fun `XHR GET 带齐浏览器那套头`() {
        val (h, c) = sent("/student/rollManagement/rollInfo/index")
        assertEquals(UrpHeaders.JSON_XHR, h["Accept"])
        assertEquals("zh-CN,zh;q=0.9,en;q=0.8", h["Accept-Language"])
        assertEquals("same-origin", h["Sec-Fetch-Site"])
        assertEquals("cors", h["Sec-Fetch-Mode"])
        assertEquals("empty", h["Sec-Fetch-Dest"])
        assertEquals("XMLHttpRequest", h["X-Requested-With"])
        assertEquals(
            UrpHeaders.URP + "/student/rollManagement/index",
            h["Referer"],
        )
        assertNull("GET 不该带 Origin", h["Origin"])
        assertNull("XHR 不该带 Sec-Fetch-User", h["Sec-Fetch-User"])
        assertNull("XHR 不该带 Upgrade-Insecure-Requests", h["Upgrade-Insecure-Requests"])
        assertTrue(h["User-Agent"]!!.contains("Mobile Safari"))
        // 交叉验证：这几个头 JDK 不拦，必须真的进了连接
        assertEquals(UrpHeaders.JSON_XHR, c.getRequestProperty("Accept"))
        assertEquals("XMLHttpRequest", c.getRequestProperty("X-Requested-With"))
    }

    @Test
    fun `顶层导航换成 navigate 那套头且不发 X-Requested-With`() {
        val (h, _) = sent("/index", nav = true)
        assertEquals(UrpHeaders.HTML_NAV, h["Accept"])
        assertEquals("navigate", h["Sec-Fetch-Mode"])
        assertEquals("document", h["Sec-Fetch-Dest"])
        assertEquals("?1", h["Sec-Fetch-User"])
        assertEquals("same-site", h["Sec-Fetch-Site"])
        assertEquals("1", h["Upgrade-Insecure-Requests"])
        assertNull(h["X-Requested-With"])
        assertNull("导航不该带 Origin", h["Origin"])
    }

    @Test
    fun `POST 带 Origin 与表单 Content-Type，GET 一个都不带`() {
        val path = "/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback"
        val (post, pc) = sent(path, method = "POST")
        assertEquals("http://10.2.0.47", post["Origin"])
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", post["Content-Type"])
        assertEquals(
            "application/x-www-form-urlencoded; charset=UTF-8",
            pc.getRequestProperty("Content-Type"),
        )

        val (get, _) = sent(path)
        assertNull(get["Origin"])
        assertNull(get["Content-Type"])
    }

    @Test
    fun `提到 CAS 的 POST，Origin 跟着换成登录域名`() {
        val (h, _) = sent("/tpass/rsa", method = "POST", host = "pass.syau.edu.cn")
        assertEquals("https://pass.syau.edu.cn", h["Origin"])
    }

    @Test
    fun `任何情况都不许出现 Sec-CH 客户端提示头`() {
        val all = listOf(
            sent("/a").first,
            sent("/index", nav = true).first,
            sent("/b", method = "POST").first,
        ).flatMap { it.keys }
        assertFalse(
            "出现了 Sec-CH-*：" + all.filter { it.startsWith("Sec-CH-", true) },
            all.any { it.startsWith("Sec-CH-", true) },
        )
    }

    @Test
    fun `账目里没有 Connection —— 连接复用交给连接池`() {
        val (h, _) = sent("/a")
        assertNull(h["Connection"])
    }

    @Test
    fun `Referer 按真实来源页反推 —— 八条映射逐个走一遍`() {
        val table = UrpHeaders.refererTable
        assertTrue("映射表不该缩水", table.size >= 8)
        table.forEach { (prefix, expected) ->
            assertEquals(
                "前缀 $prefix",
                UrpHeaders.URP + expected,
                UrpHeaders.refererFor("$prefix" + "some/callback"),
            )
        }
    }

    @Test
    fun `没实测过的路径退回门户首页，不臆造`() {
        assertEquals(UrpHeaders.URP + "/", UrpHeaders.refererFor("/student/whatever/newApi"))
        assertEquals(UrpHeaders.URP + "/", UrpHeaders.refererFor("/"))
    }

    @Test
    fun `查询串不影响 Referer 反推`() {
        val withQuery = "/student/teachingResources/freeClassroom/today/11?dayplus=0"
        assertEquals(
            UrpHeaders.URP + "/student/teachingResources/freeClassroom/index",
            UrpHeaders.refererFor(withQuery),
        )
    }

    @Test
    fun `显式 Referer 优先于反推`() {
        val (h, _) = sent(
            "/student/rollManagement/rollInfo/index",
            referer = "http://10.2.0.47/login?x=1",
        )
        assertEquals("http://10.2.0.47/login?x=1", h["Referer"])
    }

    @Test
    fun `omitReferer 时一条 Referer 都不发`() {
        val (h, c) = sent("/tpass/login", nav = true, host = "pass.syau.edu.cn", omitReferer = true)
        assertNull(h["Referer"])
        assertNull(c.getRequestProperty("Referer"))
    }

    @Test
    fun `校外的 host 一调就炸 —— 防止把校内头发给外网`() {
        val c = conn("/api", host = "example.com")
        val r = runCatching { UrpHeaders.apply(c, path = "/api", method = "GET") }
        assertTrue("应当抛异常，实际 ${r.exceptionOrNull()?.javaClass?.simpleName}", r.isFailure)
        assertNotNull(r.exceptionOrNull())
    }

    @Test
    fun `UA 里不带可识别的应用名`() {
        val ua = UrpHeaders.UA
        assertFalse(ua.contains("Enroute", true))
        assertFalse(ua.contains("SYAU", true))
        assertTrue(ua.startsWith("Mozilla/5.0 (Linux; Android "))
        assertTrue(ua.contains("Chrome/120.0.0.0"))
    }
}
