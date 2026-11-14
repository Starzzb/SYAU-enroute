package cn.dkarbon.enroute.data

/**
 * 会话**明文**记录的编解码（纯函数，JVM 单测直接打）。
 *
 * 记录里只有三样东西：
 *   · cookie 键值对（`JSESSIONID` 等，URP 靠它认人）
 *   · 展示用姓名（冷启动恢复后「我的」页不至于先空白一下）
 *   · 没有第三样 —— **密码、学号从不进这里**
 *
 * 格式故意用「一行一条 `键<TAB>值`」而不是 JSON：
 *   · 不需要引入序列化器，几十字节的东西不值得；
 *   · 人工 debug 时把密文解开（只可能在调试密钥下）就能一眼看懂，
 *     而 JSON 里那些转义只会增加出错面。
 *
 * 解码是**保守**的：任何一行格式不对就丢掉那一行；一条 cookie 都没解出来返回 `null`
 * （宁可当「没存过」，也不要拿半份 cookie 去请求教务）。
 */
object SessionCodec {

    /**
     * 密文串的版本前缀（写在 SharedPreferences 里，位于 Base64 之前）。
     * 以后换算法/换格式时，靠它拒绝旧数据，而不是解出一堆乱码。
     */
    const val PREFIX = "v1:"

    private const val TAB = '\t'

    /** 姓名那一行的键名。cookie 名不会等于它（cookie 名是 HTTP token，这里是保留字） */
    private const val NAME_KEY = "#name"

    /** 一份会话记录 */
    data class Record(
        val cookies: Map<String, String>,
        val displayName: String = "",
    ) {
        val isEmpty: Boolean get() = cookies.isEmpty()
    }

    fun encode(cookies: Map<String, String>, displayName: String = ""): String = buildString {
        append(NAME_KEY).append(TAB).append(oneLine(displayName)).append('\n')
        cookies.forEach { (k, v) ->
            val key = oneLine(k)
            if (key.isBlank()) return@forEach
            append(key).append(TAB).append(oneLine(v)).append('\n')
        }
    }

    /** @return 解出的记录；一条 cookie 都没有 → `null` */
    fun decode(text: String): Record? {
        if (text.isBlank()) return null
        var name = ""
        val cookies = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val i = line.indexOf(TAB)
            if (i <= 0) return@forEach
            val key = line.substring(0, i).trim()
            val value = line.substring(i + 1).trim()
            if (key.isEmpty()) return@forEach
            if (key == NAME_KEY) name = value else cookies[key] = value
        }
        if (cookies.isEmpty()) return null
        return Record(cookies = cookies, displayName = name)
    }

    /** 字段里的换行/制表符会破坏「一行一条」，存之前压成空格 */
    private fun oneLine(s: String): String = s
        .replace('\t', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
}
