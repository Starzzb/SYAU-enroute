package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.SessionCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话明文记录的编解码测试（票 04）。
 *
 * 记录只有两样东西：cookie 键值对 + 展示用姓名。
 * 这里盯三件事：往返不丢内容、坏数据不被当正常数据、**密码根本不在这套结构里**
 * （最后一条用「未知键会被当 cookie 原样带走」这类边界来反向确认结构足够窄）。
 */
class SessionCodecTest {

    @Test
    fun `cookie 与姓名往返`() {
        val text = SessionCodec.encode(
            mapOf("JSESSIONID" to "abc123", "route" to "r2"),
            "张三",
        )
        val rec = SessionCodec.decode(text)!!
        assertEquals("abc123", rec.cookies["JSESSIONID"])
        assertEquals("r2", rec.cookies["route"])
        assertEquals(2, rec.cookies.size)
        assertEquals("张三", rec.displayName)
        assertFalse(rec.isEmpty)
    }

    @Test
    fun `没有姓名也能往返 姓名是可选展示项`() {
        val rec = SessionCodec.decode(SessionCodec.encode(mapOf("JSESSIONID" to "x")))!!
        assertEquals(1, rec.cookies.size)
        assertEquals("", rec.displayName)
    }

    @Test
    fun `cookie 值里的等号和分号原样保留`() {
        // 真实 cookie 值可能带 = / ; / %，不能被拆坏
        val v = "a=b;c=%20d"
        val rec = SessionCodec.decode(SessionCodec.encode(mapOf("JSESSIONID" to v)))!!
        assertEquals(v, rec.cookies["JSESSIONID"])
    }

    @Test
    fun `一条 cookie 都没有就是 null 不给半份存档`() {
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("   "))
        // 只有姓名行，没有 cookie —— 用它登录是登不进去的，当没有
        assertNull(SessionCodec.decode("#name\t张三\n"))
    }

    @Test
    fun `坏行被跳过 不影响其他行`() {
        val text = "#name\t李四\nJSESSIONID\tok\n这一行没有分隔符\n\t空键\n"
        val rec = SessionCodec.decode(text)!!
        assertEquals(mapOf("JSESSIONID" to "ok"), rec.cookies)
        assertEquals("李四", rec.displayName)
    }

    @Test
    fun `换行制表符会被压成空格 不破坏一行一条`() {
        val text = SessionCodec.encode(mapOf("JSESSIONID" to "a\nb\tc"), "张三\n李四")
        // 编码后仍然是「每条记录一行」
        assertEquals(2, text.trimEnd('\n').lines().size)
        val rec = SessionCodec.decode(text)!!
        assertEquals("a b c", rec.cookies["JSESSIONID"])
        assertEquals("张三 李四", rec.displayName)
    }

    @Test
    fun `空键的 cookie 被丢弃`() {
        val rec = SessionCodec.decode(SessionCodec.encode(mapOf("" to "v", "JSESSIONID" to "ok")))!!
        assertEquals(1, rec.cookies.size)
        assertEquals("ok", rec.cookies["JSESSIONID"])
    }

    @Test
    fun `记录里不含密码字段 结构只有 cookie 与姓名`() {
        val text = SessionCodec.encode(mapOf("JSESSIONID" to "abc"), "张三")
        // 编码结果的行数与「1 姓名 + N cookie」严格相等：没有第三个字段的位置
        assertEquals(2, text.trimEnd('\n').lines().size)
        listOf("password", "pass", "pl", "密码").forEach {
            assertFalse(text.contains(it, ignoreCase = true))
        }
        assertTrue(text.startsWith("#name"))
    }
}
