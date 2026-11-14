package cn.dkarbon.enroute.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * URP 会话的**加密存档**（票 04）。
 *
 * ## 存什么、不存什么
 *
 * | 项 | 处理 |
 * |---|---|
 * | cookie（JSESSIONID 等） | AES/GCM 加密后存进现有 `enroute` SharedPreferences |
 * | 展示用姓名 | 同上（加密；仅供冷启动后「我的」页立刻显示名字） |
 * | **密码** | **绝不存**（只在 [UrpClient.login] 那一次请求里存在） |
 * | 学号 | 不单独存（学籍接口抓到是内存态，不落盘） |
 *
 * ## 为什么不用 `androidx.security:security-crypto`
 *
 * `app/build.gradle.kts` 里没有这个依赖，本仓库一贯避免加依赖（票里也是这个顺序）。
 * 它内部做的也就是「Android Keystore 里的 AES key + EncryptedSharedPreferences」，
 * 而这里要加密的只有**一个字符串**，自己包一层只要 100 行，还能单测加解密本身
 * （见 [SessionCrypto]）。少一个依赖 = 少一份升级负担。
 *
 * ## 密钥怎么来
 *
 * `AndroidKeyStore` 里生成一把 AES-256，`PURPOSE_ENCRYPT|DECRYPT` + `BLOCK_MODE_GCM`，
 * **`setUserAuthenticationRequired(false)`**：会话恢复必须能在无人值守的冷启动里完成，
 * 要求指纹/密码会把「打开就能用」这条路堵死（这本来就是票 04 的目的）。
 *
 * ## 失败一律「当成没存过」
 *
 * 换机恢复备份、清应用数据、系统重置都可能让密文和 key 分离（Keystore key 不随备份迁移），
 * 这时 [SessionCrypto.open] 返回 null。我们**不会崩、不会猜**，直接当未登录，
 * 用户重登一次即可 —— 不会出现「一直卡在登录页又说已登录」这种鬼状态。
 */
class SessionStore(ctx: Context) {

    private val app = ctx.applicationContext
    private val prefs = Prefs(app)

    /**
     * [restore] 的结果。
     *
     * 三个字段而不是一个枚举，是因为「有会话」和「记着上次过期了」是两件事：
     * 过期后 cookie 已经没用了（会清掉），但**这个事实要留着**，
     * 否则下次冷启动又退回「未登录」，学生又会以为是自己没登。
     */
    data class Restore(
        val session: UrpClient.Session? = null,
        val displayName: String = "",
        val savedAt: Long = 0L,
        val expired: Boolean = false,
    )

    /** 把会话加密存盘。@return false = 存失败（界面要照实说「本次仍可用，下次要重登」） */
    fun save(session: UrpClient.Session): Boolean {
        // **一条 cookie 都没有就不算存过**：以前这里只看「明文非空」，而
        // encode 永远会写一行 `#name`，于是空会话也能「存成功」，
        // 冷启动 decode 又因为「没有 cookie」把它丢掉 —— 白存一趟还查不出原因。
        val cookies = session.cookiesSnapshot()
        if (cookies.isEmpty()) {
            Log.w("SessionStore", "save: 会话里没有任何 cookie，拒绝存盘（本次仍可用，下次要重登）")
            DebugLog.log("SESSION", "会话存盘跳过：内存里没有 cookie（登录没拿到 JSESSIONID）")
            return false
        }
        val key = openKey(create = true) ?: run {
            Log.w("SessionStore", "save: 取不到会话密钥，存盘失败")
            DebugLog.log("SESSION", "会话存盘失败：取不到设备密钥")
            return false
        }
        return try {
            val plain = SessionCodec.encode(cookies, session.displayName)
            if (plain.isBlank()) {
                false
            } else {
                val sealed = SessionCrypto.seal(key, plain.toByteArray(Charsets.UTF_8))
                prefs.sessionSealed = SessionCodec.PREFIX + sealed
                prefs.sessionSavedAt = System.currentTimeMillis()
                prefs.sessionExpired = false
                Log.i("SessionStore", "save: ok，cookie ${cookies.size} 项")
                true
            }
        } catch (e: Exception) {
            // 只记异常类型：密文/密钥细节不进日志
            Log.w("SessionStore", "会话加密存盘失败：${e.javaClass.simpleName}")
            false
        }
    }

    /** 读盘并解密。任何一步失败都返回「没存过」，绝不返回半份 cookie */
    fun restore(): Restore {
        val expiredFlag = prefs.sessionExpired
        val blob = prefs.sessionSealed
        if (blob.isBlank()) {
            Log.i("SessionStore", "restore: 本机没有会话密文（expired=$expiredFlag）")
            return Restore(expired = expiredFlag)
        }
        if (!blob.startsWith(SessionCodec.PREFIX)) {
            Log.w("SessionStore", "restore: 版本前缀不认识（期望 ${SessionCodec.PREFIX}），丢弃")
            prefs.sessionSealed = ""
            return Restore(expired = expiredFlag)
        }
        val key = openKey(create = false)
        if (key == null) {
            // key 没了（换机/清数据/系统重置）：密文成了纯粹的垃圾，清掉
            Log.w("SessionStore", "restore: 会话密钥不存在，丢弃密文")
            prefs.sessionSealed = ""
            return Restore(expired = expiredFlag)
        }
        val plain = SessionCrypto.open(key, blob.removePrefix(SessionCodec.PREFIX))
        if (plain == null) {
            Log.w("SessionStore", "restore: 密文解不开（key 变了或密文损坏），丢弃")
            prefs.sessionSealed = ""
            return Restore(expired = expiredFlag)
        }
        val record = SessionCodec.decode(String(plain, Charsets.UTF_8))
        if (record == null) {
            Log.w("SessionStore", "restore: 解出来的记录里没有 cookie，丢弃")
            prefs.sessionSealed = ""
            return Restore(expired = expiredFlag)
        }
        val session = UrpClient.Session.fromStored(record.cookies, record.displayName)
        Log.i("SessionStore", "restore: ok，cookie ${record.cookies.size} 项")
        return Restore(
            session = session,
            displayName = record.displayName,
            savedAt = prefs.sessionSavedAt,
            expired = false,
        )
    }

    /** 验活通过：清掉可能残留的「过期」标记（不改密文） */
    fun markVerified() {
        if (prefs.sessionExpired) prefs.sessionExpired = false
    }

    /**
     * 验活失败（服务器不认这份会话）。
     *
     * **清掉死 cookie、留下「过期」这个事实**：课程表等缓存数据一条不动，
     * 界面显示「已登录但会话已过期 · 点此续期」。
     */
    fun markExpired() {
        prefs.sessionSealed = ""
        prefs.sessionExpired = true
    }

    /** 用户自己清登录态（如「只清课表」）：密文和过期标记都清，回到未登录 */
    fun clear() {
        prefs.sessionSealed = ""
        prefs.sessionSavedAt = 0L
        prefs.sessionExpired = false
    }

    /**
     * 「清除所有数据」用：密文 + 过期标记 + **Keystore 密钥本身**一起销毁。
     *
     * 删 key 是关键一步（crypto-shredding）：万一密文还有残留在备份/快照里，
     * 没有 key 就是不可解的随机字节，而不是「等哪天 key 又回来了就能读」。
     */
    fun forget() {
        clear()
        runCatching {
            val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        }.onFailure { Log.w("SessionStore", "删会话密钥失败：${it.javaClass.simpleName}") }
    }

    // ── Keystore ──────────────────────────────────────────

    /** @param create true = 没有就新建（存盘路径）；false = 没有就算了（读盘路径） */
    private fun openKey(create: Boolean): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val existing = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        when {
            existing != null -> existing
            create -> generateKey()
            else -> null
        }
    }.getOrElse {
        Log.w("SessionStore", "取会话密钥失败：${it.javaClass.simpleName}")
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
            // GCM 下必须允许随机 IV（默认就是 true，写出来是为了别被人顺手关掉）
            .setRandomizedEncryptionRequired(true)
            // 不要求用户认证：冷启动无人值守恢复会话，见类注释
            .setUserAuthenticationRequired(false)
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }.onFailure {
        Log.w("SessionStore", "生成会话密钥失败：${it.javaClass.simpleName}")
    }.getOrNull()

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "enroute.urp.session.v1"
    }
}
