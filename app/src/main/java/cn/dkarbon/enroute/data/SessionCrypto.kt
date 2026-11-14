package cn.dkarbon.enroute.data

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 会话密文的对称加解密封装（票 04）。
 *
 * ## 为什么是 AES/GCM 而不是自己糊一套
 *
 * - **GCM 带认证**：密文被改过一个字节，解密就会失败（返回 `null`），
 *   而不是解出一段「看起来像 cookie 的乱码」然后拿去请求教务。会话数据最怕这种静默损坏。
 * - **随机 IV 每次都要新的**：同一个 key 下重复 IV 会让 GCM 直接失去安全性，
 *   而 App 每次登录都会重写密文，所以 IV 必须现生成、跟着密文一起存
 *   （存的是 `Base64(IV ‖ 密文 ‖ tag)`）。
 * - **AAD 绑定用途**：把一段固定的用途串作为附加认证数据，
 *   密文被搬到别的用途/别的文件里就解不开。
 *
 * ## 为什么能单测
 *
 * 这里**只依赖 javax.crypto + java.util.Base64**（Android API 26 起 `java.util.Base64` 就有），
 * 不碰 `android.security.keystore`。密钥从哪来（Keystore / 测试里的 KeyGenerator）
 * 与本文件无关 —— 于是 JVM 单测能真跑一遍加解密往返，而不是靠真机瞄一眼。
 */
object SessionCrypto {

    const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** GCM 推荐 96 位 IV（长度只用于解密时校验） */
    private const val IV_BYTES = 12

    /** 认证标签 128 位（满强度） */
    private const val TAG_BITS = 128

    /**
     * 附加认证数据：把密文绑到「沈农在途的 URP 会话」这一个用途上。
     * 改这里等于换格式 —— 老密文会全部解不开，所以别改（要改就升版本号）。
     */
    private val AAD = "cn.dkarbon.enroute.session.v1".toByteArray(Charsets.UTF_8)

    /**
     * 凭据（票 14）用**另一个** AAD：会话密文和凭据密文不能互相顶替。
     * 把会话的密文塞进 `cred_sealed` 会立刻解不开，反之亦然 —— 这是有意的。
     */
    const val AAD_CREDENTIAL = "cn.dkarbon.enroute.credential.v1"



    /** 空串 = 会话用途（票 04 的老密文保持能开）；非空 = 该用途自己的 AAD */
    private fun aadBytes(aad: String): ByteArray =
        if (aad.isBlank()) AAD else aad.toByteArray(Charsets.UTF_8)

    /** 加密：返回 `Base64(IV ‖ 密文)`，可直接塞进 SharedPreferences 字符串 */
    @JvmOverloads
    fun seal(key: SecretKey, plain: ByteArray, aad: String = ""): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // ⚠️ **不要自己传 IV**：AndroidKeyStore 的密钥默认
        // `setRandomizedEncryptionRequired(true)`，此时由调用方提供 IV 会让
        // `Cipher.init` 直接抛 `InvalidAlgorithmParameterException: Caller-provided IV not permitted`。
        // 2026-11-14 实战教训：票 04 的会话存档因此**从来没写成功过**
        // （单测用普通 JCE 密钥，允许自带 IV，所以一直绿的 —— 真机上 save() 静默返回 false）。
        // 正确姿势：让 provider/Keystore 生成 IV，init 之后从 `cipher.iv` 取。
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aadBytes(aad))
        val body = cipher.doFinal(plain)
        val iv = cipher.iv   // 12 字节；与解密侧 GCMParameterSpec 对应
        return Base64.getEncoder().encodeToString(iv + body)
    }

    /**
     * 解密。
     *
     * @return 明文；**任何失败都返回 `null`**（密文被改、key 换了/丢了、格式不对）。
     *   调用方必须把 `null` 当成「这份存档不能用了」，回到未登录，
     *   绝不能把半截结果当会话用。
     */
    @JvmOverloads
    fun open(key: SecretKey, sealed: String, aad: String = ""): ByteArray? {
        val raw = runCatching { Base64.getDecoder().decode(sealed) }.getOrNull() ?: return null
        if (raw.size <= IV_BYTES) return null
        val iv = raw.copyOfRange(0, IV_BYTES)
        val body = raw.copyOfRange(IV_BYTES, raw.size)
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aadBytes(aad))
            cipher.doFinal(body)
        }.getOrNull()
    }
}
