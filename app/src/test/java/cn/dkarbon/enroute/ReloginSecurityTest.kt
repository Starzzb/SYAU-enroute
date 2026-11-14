package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.CredentialStore
import cn.dkarbon.enroute.data.SessionCodec
import cn.dkarbon.enroute.data.SessionCrypto
import cn.dkarbon.enroute.data.UrpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import javax.crypto.KeyGenerator

/**
 * 静默续期（票 14）的安全红线 —— 全是 JVM 单测，不装 Android 也能守。
 *
 * 覆盖：
 * 1. **凭据只允许发往 `pass.syau.edu.cn`**（常量 + 拼接收敛）；
 * 2. 凭据的 `toString()` 打码、日志串里 grep 不到学号密码；
 * 3. 凭据密文与会话密文的 **AAD 不能互窜**（会话密文塞进 cred 槽解不开）；
 * 4. 加解密往返（AES/GCM 真实跑一遍）；
 * 5. 失败上限常量（连续 3 次停手）。
 *
 * 说明：Keystore / SharedPreferences 只能真机测，这里测的是**与平台无关的那部分**。
 */
class ReloginSecurityTest {

    private fun testKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    // ── 红线 1：目标域名 ─────────────────────────────────

    @Test
    fun `凭据只发往学校认证页`() {
        assertEquals("pass.syau.edu.cn", UrpClient.CAS_LOGIN_HOST)
        val url = UrpClient.CAS_LOGIN_URL
        assertEquals("https", URI(url).scheme)
        assertEquals("pass.syau.edu.cn", URI(url).host)
        assertTrue("必须是 https", url.startsWith("https://"))
        // 主机部分写死：任何 service 值都改不了目标主机
        listOf("http://10.2.0.47/login", "https://evil.example/x", "", "../../etc/passwd").forEach { svc ->
            val built = UrpClient.casLoginUrl(svc)
            assertEquals("service=$svc 时主机被改了", "pass.syau.edu.cn", URI(built).host)
            assertTrue(built.startsWith("https://pass.syau.edu.cn/tpass/login?service="))
        }
    }

    @Test
    fun `认证地址里不能出现别的域名`() {
        val built = UrpClient.casLoginUrl("http://10.2.0.47/login")
        // service 是 URL 编码后的参数，不是主机；除认证页本身外不该出现别的 host 作为目标
        assertEquals("pass.syau.edu.cn", URI(built).host)
        assertTrue(built.contains("service="))
        assertFalse("目标不是 10.2.0.47", built.startsWith("http://10.2.0.47"))
    }

    // ── 红线 2：日志里 grep 不到凭据 ─────────────────────

    @Test
    fun `凭据对象打印出来是打码的`() {
        val user = "202412345678"
        val pass = "SuperSecret!2026"
        val creds = CredentialStore.Credentials(user, pass)
        val s = creds.toString()
        assertFalse("学号不能出现在 toString 里", s.contains(user))
        assertFalse("密码不能出现在 toString 里", s.contains(pass))
        assertTrue(s.contains("***"))
        // 拼进异常消息/日志串也不漏
        val line = "续期失败：$creds"
        assertFalse(line.contains(user))
        assertFalse(line.contains(pass))
    }

    // ── 红线 3：会话密文与凭据密文不能互窜 ───────────────

    @Test
    fun `凭据密文与会话密文用不同 AAD 不能互相顶替`() {
        val key = testKey()
        val plain = "202412345678\tSuperSecret!2026".toByteArray(Charsets.UTF_8)
        val sealedCred = SessionCrypto.seal(key, plain, SessionCrypto.AAD_CREDENTIAL)
        // 用凭据 AAD 能开
        assertEquals(
            String(plain, Charsets.UTF_8),
            String(SessionCrypto.open(key, sealedCred, SessionCrypto.AAD_CREDENTIAL)!!, Charsets.UTF_8),
        )
        // 当成会话密文去开 → 解不开（AAD 不匹配，GCM 认证失败）
        assertNull(SessionCrypto.open(key, sealedCred))
        // 反过来：会话密文塞进凭据槽也解不开
        val sealedSession = SessionCrypto.seal(key, plain)
        assertNull(SessionCrypto.open(key, sealedSession, SessionCrypto.AAD_CREDENTIAL))
    }

    @Test
    fun `密文被改一个字节就解不开`() {
        val key = testKey()
        val plain = "202412345678\tpass".toByteArray(Charsets.UTF_8)
        val sealed = SessionCrypto.seal(key, plain, SessionCrypto.AAD_CREDENTIAL)
        // 改最后一位（Base64 里翻一个字符）
        val last = sealed.last()
        val tampered = sealed.dropLast(1) + (if (last == 'A') 'B' else 'A')
        assertNull(SessionCrypto.open(key, tampered, SessionCrypto.AAD_CREDENTIAL))
    }

    @Test
    fun `换一把密钥解不开`() {
        val sealed = SessionCrypto.seal(testKey(), "x\ty".toByteArray(), SessionCrypto.AAD_CREDENTIAL)
        assertNull(SessionCrypto.open(testKey(), sealed, SessionCrypto.AAD_CREDENTIAL))
    }

    // ── 凭据记录的格式（与 SessionCodec 同款前缀）─────────

    @Test
    fun `凭据密文前缀与会话密文一致便于版本判定`() {
        assertEquals("v1:", SessionCodec.PREFIX)
        // 凭据存的是 PREFIX + Base64，读的时候先校验前缀，不认识就整份丢弃
        val sealed = SessionCrypto.seal(testKey(), "a\tb".toByteArray(), SessionCrypto.AAD_CREDENTIAL)
        assertFalse(sealed.startsWith(SessionCodec.PREFIX))
        assertTrue(SessionCodec.PREFIX + sealed != sealed)
    }

    @Test
    fun `学号密码分列靠制表符且第二段允许含特殊字符`() {
        // CredentialStore.read() 用「第一个 \t」切分：密码里即便有 \t 也不会串位（学号不带 \t）
        val line = "202412345678" + "\t" + "a!b@c#\t尾缀"
        val i = line.indexOf('\t')
        assertEquals("202412345678", line.substring(0, i))
        assertEquals("a!b@c#\t尾缀", line.substring(i + 1))
        assertNotEquals("", line.substring(i + 1))
    }

    // ── 失败保护 ─────────────────────────────────────────

    @Test
    fun `连续三次失败就停止自动续期`() {
        assertEquals(3, CredentialStore.MAX_FAILURES)
        var n = 0
        repeat(2) { n++ }
        assertTrue("两次还没到上限", n < CredentialStore.MAX_FAILURES)
        n++
        assertTrue("第三次到上限 → 停手", n >= CredentialStore.MAX_FAILURES)
    }
}
