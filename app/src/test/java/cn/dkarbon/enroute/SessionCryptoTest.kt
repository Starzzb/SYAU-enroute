package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.SessionCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 会话加密往返测试（票 04）。
 *
 * 这里用 JVM 自己生成的 AES key 跑真加解密 —— 因为 [SessionCrypto] 只依赖
 * javax.crypto / java.util.Base64，不碰 Android Keystore。
 * 也就是说：**加密逻辑本身是真的被测过**，真机上只剩「key 从 Keystore 取」这一层。
 *
 * 重点不只是「能解开」，还有「解不开时必须返回 null」：
 * 密文被改一个字节、或者换了 key，绝不能解出半截明文被当成 cookie 用。
 */
class SessionCryptoTest {

    private fun key(bits: Int = 256): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(bits) }.generateKey()

    @Test
    fun `加解密往返还原原文`() {
        val k = key()
        val plain = "JSESSIONID=abc123; route=deadbeef".toByteArray(Charsets.UTF_8)
        val sealed = SessionCrypto.seal(k, plain)
        assertTrue(sealed.isNotBlank())
        assertEquals(String(plain, Charsets.UTF_8), String(SessionCrypto.open(k, sealed)!!, Charsets.UTF_8))
    }

    @Test
    fun `中文姓名也能往返 不乱码`() {
        val k = key()
        val plain = "#name\t张三\nJSESSIONID=xyz".toByteArray(Charsets.UTF_8)
        val sealed = SessionCrypto.seal(k, plain)
        assertEquals("#name\t张三\nJSESSIONID=xyz", String(SessionCrypto.open(k, sealed)!!, Charsets.UTF_8))
    }

    @Test
    fun `每次加密的密文都不同 GCM 的 IV 必须每次都新`() {
        val k = key()
        val plain = "JSESSIONID=abc123".toByteArray(Charsets.UTF_8)
        val a = SessionCrypto.seal(k, plain)
        val b = SessionCrypto.seal(k, plain)
        // 同一明文同一 key 两次密文相同 = IV 复用了，GCM 直接失去安全性
        assertNotEquals(a, b)
        assertEquals(String(plain, Charsets.UTF_8), String(SessionCrypto.open(k, a)!!, Charsets.UTF_8))
        assertEquals(String(plain, Charsets.UTF_8), String(SessionCrypto.open(k, b)!!, Charsets.UTF_8))
    }

    @Test
    fun `密文被改一个字节就解不开 绝不返回半截明文`() {
        val k = key()
        val sealed = SessionCrypto.seal(k, "JSESSIONID=abc123".toByteArray(Charsets.UTF_8))
        // 改动倒数第三个字符（落在 GCM 认证覆盖的范围内）
        val idx = sealed.length - 3
        val tampered = sealed.substring(0, idx) +
            (if (sealed[idx] == 'A') 'B' else 'A') +
            sealed.substring(idx + 1)
        assertNull(SessionCrypto.open(k, tampered))
    }

    @Test
    fun `换了 key 就解不开`() {
        val sealed = SessionCrypto.seal(key(), "JSESSIONID=abc123".toByteArray(Charsets.UTF_8))
        assertNull(SessionCrypto.open(key(), sealed))
    }

    @Test
    fun `垃圾输入不抛异常 只返回 null`() {
        val k = key()
        assertNull(SessionCrypto.open(k, ""))
        assertNull(SessionCrypto.open(k, "这不是 base64"))
        assertNull(SessionCrypto.open(k, "AAAA")) // 比 IV 还短
    }

    @Test
    fun `密文里不会出现明文片段`() {
        val k = key()
        val secret = "JSESSIONID=SUPERSECRETVALUE"
        val sealed = SessionCrypto.seal(k, secret.toByteArray(Charsets.UTF_8))
        assertFalse(sealed.contains("SUPERSECRETVALUE"))
        assertFalse(sealed.contains("JSESSIONID"))
    }
}
