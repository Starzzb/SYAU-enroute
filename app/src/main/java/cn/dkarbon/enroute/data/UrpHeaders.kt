package cn.dkarbon.enroute.data

import android.os.Build
import java.net.HttpURLConnection

/**
 * 发往校内服务（`10.2.0.47` 与 `pass.syau.edu.cn`）的请求头的**唯一真源**。
 *
 * ## 为什么要有这一个文件
 *
 * 改前五处 HTTP 写手（[UrpClient]、[UrpHomeApi] 两个、[AcademicApi] 两个）各自设头，
 * UA 有三份副本，`Referer` 几乎一律写死 `http://10.2.0.47/`，且都缺
 * `Accept-Language` / `Sec-Fetch-*` / `Origin` / `Connection`。
 * 结果就是：只要对方哪天开始比对请求头，我们连"这是一台真手机的浏览器"都解释不了。
 *
 * 这里只做一件事 —— 把一台真机 Chrome 会发的头，按同一份规则设上去。
 *
 * ## 明确不做的（写在这里，防止后人"好心"加回来）
 *
 * - **不发 `Sec-CH-UA*`**：值与 UA 稍有不符就是强指纹，宁可不发。单测断言不含 `Sec-CH-`。
 * - **不设 `Cookie`**：谁发给哪个域由调用方决定，这里一律不碰，避免把会话发给外部域。
 * - **不改 `Accept-Encoding`**：交给 `HttpURLConnection` 自己协商 gzip，手动设会破坏解压。
 * - **不伪造 `Host` / `Content-Length`**：由连接自己算。
 *
 * ## 调用约定
 *
 * 只允许对 [ALLOWED_HOSTS] 里的两个 host 调用，其他 host 直接抛异常 ——
 * 这不是防御性编程，是因为**误用一次就会把校内头发给外网**，得让它当场炸。
 */
internal object UrpHeaders {

    const val URP = "http://10.2.0.47"
    const val SSO = "https://pass.syau.edu.cn"

    /** XHR / AJAX 请求用的 Accept（教务的 jQuery 代码就是发这一串） */
    const val JSON_XHR = "application/json, text/javascript, */*; q=0.01"

    /** 顶层页面导航用的 Accept（Chrome 点链接时的默认值） */
    const val HTML_NAV =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    private val ALLOWED_HOSTS = setOf("10.2.0.47", "pass.syau.edu.cn")

    /**
     * UA：机型版本取运行时值，Chrome 版本保持常量。
     *
     * 取运行时的理由：全校几千台不同 Android 版本，写死 `Android 13` 反而是"所有人同一台手机"。
     * 单测环境（JVM）拿不到 `Build.VERSION`，回退 `13`，保证测试可跑。
     */
    val UA: String by lazy {
        val release = runCatching { Build.VERSION.RELEASE }.getOrNull()?.takeIf { it.isNotBlank() }
        "Mozilla/5.0 (Linux; Android ${release ?: "13"}) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }

    /**
     * 按目标路径反推**真实来源页**。
     *
     * 浏览器点「考试页 → 明细」时 Referer 是考试页，不是首页。我们原来一律给首页，
     * 等于告诉对方"所有请求都从首页发出"，这本身就是一条特征。
     *
     * ⚠️ 只列**已实测过**的路径；没见过的路径退回 [URP]`/`，**不猜**。
     * 映射表由 `UrpHeadersTest` 锁住。
     */
    fun refererFor(path: String): String {
        val p = path.substringBefore('?')
        val target = REFERERS.entries.firstOrNull { p.startsWith(it.key) }?.value
        return URP + (target ?: "/")
    }

    private val REFERERS = linkedMapOf(
        "/student/rollManagement/rollInfo/" to "/student/rollManagement/index",
        "/student/courseSelect/thisSemesterCurriculum/" to "/student/courseSelect/index",
        "/student/courseSelect/calendarSemesterCurriculum/" to
            "/student/courseSelect/calendarSemesterCurriculum/index",
        "/student/examinationManagement/" to "/student/examinationManagement/index",
        "/student/integratedQuery/scoreQuery/" to "/student/integratedQuery/scoreQuery/index",
        "/student/integratedQuery/instructionPlanQuery/" to
            "/student/integratedQuery/instructionPlanQuery/index",
        "/student/teachingResources/freeClassroom/" to
            "/student/teachingResources/freeClassroom/index",
        "/student/curriculum/teacherCurriculum/" to
            "/student/curriculum/teacherCurriculum/index",
    )

    /** 单测与日志用的映射快照（键=路径前缀，值=相对 Referer） */
    val refererTable: Map<String, String> get() = REFERERS.toMap()

    /**
     * 一次把该设的头设上。
     *
     * @param path 目标路径（含前导 `/`，可带 query），用于反推 `Referer`
     * @param method HTTP 方法
     * @param accept Accept 值，导航页传 [HTML_NAV]
     * @param hasBody 是否带请求体（决定要不要 `Origin` 与 `Content-Type`）
     * @param nav true = 顶层页面导航（登录页、门户首页），false = XHR
     * @param referer 显式指定 Referer（登录页那种特殊来源），null 表示按 [path] 反推
     * @param omitReferer 完全不发 `Referer`（地址栏直接输入 / 被 302 送过来的顶层导航就是这样）
     * @param logTag 非空时把"本次请求头名集合"写进日志（只写名字，不写值）
     * @return 本次实际设上的头（名→值）。**给单测用**：JVM 的 `HttpURLConnection`
     *   有一套"受限头"机制会静默丢弃 `Origin` / `Sec-*` / `Connection`（Android 的实现不是这一套），
     *   所以断言这些头不能靠 `getRequestProperty` 读回来，得看这里返回的账。
     */
    fun apply(
        conn: HttpURLConnection,
        path: String,
        method: String = "GET",
        accept: String = JSON_XHR,
        hasBody: Boolean = method.equals("POST", ignoreCase = true),
        nav: Boolean = false,
        referer: String? = null,
        omitReferer: Boolean = false,
        logTag: String? = null,
    ): Map<String, String> {
        val url = conn.url
        val host = url.host
        require(host in ALLOWED_HOSTS) {
            "UrpHeaders 只用于校内服务，拒绝把校内请求头发给 $host"
        }

        val set = linkedMapOf<String, String>()
        fun put(name: String, value: String) {
            conn.setRequestProperty(name, value)
            set[name] = value
        }

        put("User-Agent", UA)
        put("Accept", if (nav) HTML_NAV else accept)
        put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        // `Connection` 有意不设：HTTP/1.1 的连接复用由连接池自己管，
        // 两边实现（JDK 的受限头表、Android 的 okhttp 层）都会把手工设的这条丢掉，
        // 设了反而给人"我们控制住了"的错觉。真机抓包看到的那条 keep-alive 是连接池发的。
        if (!omitReferer) put("Referer", referer ?: refererFor(path))

        // Sec-Fetch-*：真机 Chrome 每一条请求都带，缺了就是"非浏览器"
        put("Sec-Fetch-Site", if (nav) "same-site" else "same-origin")
        put("Sec-Fetch-Mode", if (nav) "navigate" else "cors")
        put("Sec-Fetch-Dest", if (nav) "document" else "empty")
        if (nav) put("Sec-Fetch-User", "?1")

        if (nav) {
            put("Upgrade-Insecure-Requests", "1")
        } else {
            // 教务的 jQuery 发 XHR 时带这个；带它比不带更像网页
            put("X-Requested-With", "XMLHttpRequest")
        }

        if (hasBody && !nav) {
            val origin = if (host == "pass.syau.edu.cn") SSO else URP
            put("Origin", origin)
            put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }

        if (logTag != null) {
            DebugLog.step(logTag, "请求头 ${set.size} 项：${set.keys.joinToString(",")}")
        }
        return set
    }
}

