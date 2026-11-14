package cn.dkarbon.enroute.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * 本机凭据存档（票 14 —— 静默续期）。
 *
 * ## 这里为什么可以存密码
 *
 * 票 04 当时写的是「密码绝不持久化」。主人 2026-11-14 明确要求
 * 「登录一次之后，退出/隔天再开都不用再输密码」，**有意推翻**了那条保守选择，
 * 改成：**默认关闭 + 用户显式开启 + Keystore AES-256-GCM 加密 + 只发本校 CAS**，
 * 并且随时可关、一关就删密文。契约见 `.scratch/教务扩展/issues/14-silent-relogin.md`。
 *
 * ## 红线（代码与单测一起守）
 *
 * 1. **只发 `pass.syau.edu.cn`**：目标地址是 [UrpClient.CAS_LOGIN_URL] 常量，
 *    这里不做任何 URL 拼接；
 * 2. **只在本机**：密文进 SharedPreferences（键 `cred_sealed`），密钥进 AndroidKeyStore，
 *    与 [SessionStore] 的会话密文**用不同的 AAD**，两份密文不能互相顶替；
 * 3. **绝不进日志**：[Credentials.toString] 打码；这里只记「有/无凭据」「解不开」；
 * 4. **失败保护**：连续 3 次自动续期失败就停手（改过密码后无限重试会触发风控）；
 * 5. **关掉就是删掉**：[clear] 立即抹掉密文并删 Keystore 别名，不是标记「已关闭」。
 */
class CredentialStore(ctx: Context) {

    private val app = ctx.applicationContext
    private val prefs = Prefs(app)

    /**
     * 一份凭据。
     *
     * ⚠️ [toString] 必须打码 —— 这是「日志里 grep 不到学号密码」这条红线的最后一道闸：
     * 哪天有人 `Log.d(TAG, "$creds")` 或把整个对象拼进异常消息，也不会漏。
     */
    class Credentials(val user: String, val pass: String) {
        override fun toString(): String = "Credentials(user=***, pass=***, len=${user.length}/${pass.length})"
    }

    /** 用户是否开启「保持登录（自动续期）」 */
    fun isEnabled(): Boolean = prefs.credEnabled

    /** 首次登录后是否已经问过一次「要不要保持登录」 */
    fun hasPrompted(): Boolean = prefs.credPrompted

    fun markPrompted() {
        prefs.credPrompted = true
    }

    /** 连续失败次数（>= [MAX_FAILURES] 就停止自动续期） */
    fun failures(): Int = prefs.credFailCount

    fun recordFailure(): Int {
        val n = prefs.credFailCount + 1
        prefs.credFailCount = n
        return n
    }

    fun resetFailures() {
        if (prefs.credFailCount != 0) prefs.credFailCount = 0
    }

    /** 已达上限：不再自动尝试（等用户手动登录） */
    fun autoReloginBlocked(): Boolean = failures() >= MAX_FAILURES

    /**
     * 加密保存凭据，并顺便把开关打开。
     *
     * @return true = 已存好；false = 存不进去（Keystore 不可用），界面要说实话
     */
    fun save(user: String, pass: String): Boolean {
        if (user.isBlank() || pass.isBlank()) return false
        val key = openKey(create = true) ?: return false
        return try {
            val plain = user.trim() + "\t" + pass
            val sealed = SessionCrypto.seal(
                key,
                plain.toByteArray(Charsets.UTF_8),
                SessionCrypto.AAD_CREDENTIAL,
            )
            prefs.credSealed = SessionCodec.PREFIX + sealed
            prefs.credSavedAt = System.currentTimeMillis()
            prefs.credEnabled = true
            prefs.credFailCount = 0
            DebugLog.log("CRED", "凭据已加密存本机（长度已隐去）；自动续期可用")
            true
        } catch (e: Exception) {
            // 只记异常类型：绝不让用户名/密码进日志或异常栈
            Log.w("CredentialStore", "凭据加密存盘失败：${e.javaClass.simpleName}")
            DebugLog.error("CRED", "凭据加密存盘失败：${e.javaClass.simpleName}")
            false
        }
    }

    /** 设备自检：真 Keystore 做一次加密→解密往返（不含任何真实凭据） */
    fun selfTest(): String = try {
        val key = openKey(create = true) ?: return "✘ 取不到凭据密钥"
        val probe = "cred-selftest"
        val sealed = SessionCrypto.seal(
            key, probe.toByteArray(Charsets.UTF_8), SessionCrypto.AAD_CREDENTIAL,
        )
        val back = SessionCrypto.open(
            key, sealed, SessionCrypto.AAD_CREDENTIAL,
        )?.let { String(it, Charsets.UTF_8) }
        if (back == probe) "✔ 凭据加密可用（AES-256-GCM 往返成功）" else "✘ 凭据密文解不开"
    } catch (e: Exception) {
        "✘ 凭据加密不可用：${e.javaClass.simpleName}"
    }

    /** 读回凭据；任何一步失败都返回 null（当作没存过），**不猜、不半份** */
    fun read(): Credentials? {
        if (!prefs.credEnabled) return null
        val blob = prefs.credSealed
        if (blob.isBlank()) return null
        if (!blob.startsWith(SessionCodec.PREFIX)) {
            Log.w("CredentialStore", "凭据存档版本不认识，丢弃")
            clear()
            return null
        }
        val key = openKey(create = false)
        if (key == null) {
            Log.w("CredentialStore", "凭据密钥不存在，丢弃密文")
            clear()
            return null
        }
        val plain = SessionCrypto.open(
            key,
            blob.removePrefix(SessionCodec.PREFIX),
            SessionCrypto.AAD_CREDENTIAL,
        )
        if (plain == null) {
            Log.w("CredentialStore", "凭据密文解不开（key 变了或密文损坏），丢弃")
            clear()
            return null
        }
        val text = String(plain, Charsets.UTF_8)
        val i = text.indexOf('\t')
        if (i <= 0 || i >= text.length - 1) {
            Log.w("CredentialStore", "凭据存档格式不对，丢弃")
            clear()
            return null
        }
        return Credentials(text.substring(0, i), text.substring(i + 1))
    }

    /**
     * 关闭「保持登录」：**立刻删密文**（不是只把开关改成 false），
     * 顺手清零失败计数。Keystore 别名也删（crypto-shredding）。
     */
    fun clear(): Boolean {
        prefs.credSealed = ""
        prefs.credSavedAt = 0L
        prefs.credEnabled = false
        prefs.credFailCount = 0
        deleteKey()
        DebugLog.log("CRED", "本机凭据已清除（密文与密钥都删了）")
        return true
    }

    /** 「清除所有数据」用：与 [clear] 相同，但日志口径不同（便于排查） */
    fun forget() {
        prefs.credSealed = ""
        prefs.credSavedAt = 0L
        prefs.credEnabled = false
        prefs.credFailCount = 0
        prefs.credPrompted = false
        deleteKey()
    }

    fun savedAt(): Long = prefs.credSavedAt

    /** 界面用的一行状态（**不含任何凭据内容**） */
    fun statusLine(): String = when {
        !isEnabled() -> "未开启（密码不保存，会话过期后要手动重登）"
        prefs.credSealed.isBlank() -> "已开启，但本机没有可用凭据（下次登录会重新保存）"
        autoReloginBlocked() -> "已开启，但连续 ${failures()} 次续期失败，已暂停自动续期"
        else -> "已开启（学号与密码用设备密钥加密存在本机，只发给学校认证页）"
    }

    // ── Keystore ──────────────────────────────────────────

    private fun openKey(create: Boolean): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        when {
            existing != null -> existing
            create -> generateKey()
            else -> null
        }
    }.getOrElse {
        Log.w("CredentialStore", "取凭据密钥失败：${it.javaClass.simpleName}")
        null
    }

    private fun generateKey(): SecretKey? = runCatching {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            // 必须 false：自动续期发生在冷启动，没有人守着屏幕，要指纹就做不成
            .setUserAuthenticationRequired(false)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }.onFailure {
        Log.w("CredentialStore", "生成凭据密钥失败：${it.javaClass.simpleName}")
    }.getOrNull()

    private fun deleteKey() {
        runCatching {
            val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        }.onFailure { Log.w("CredentialStore", "删凭据密钥失败：${it.javaClass.simpleName}") }
    }

    companion object {
        /** 连续失败上限（票 14 安全要求 §7） */
        const val MAX_FAILURES = 3

        private const val PROVIDER = "AndroidKeyStore"

        /** 与会话存档**不同**的别名：清会话不该顺手把凭据也带走 */
        private const val ALIAS = "enroute.urp.cred.v1"
    }
}
