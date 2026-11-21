package cn.dkarbon.enroute.data.feed

import kotlinx.serialization.Serializable
import java.net.URI

/** 正文里的一个链接 / 一个附件 */
@Serializable
data class FeedLink(
    /** 显示文字（附件就是文件名） */
    val text: String,
    /** 绝对地址 */
    val url: String,
    /** 是否附件（下载链接 / 文档后缀） */
    val isAttachment: Boolean = false,
)

/** 抓下来的正文包：纯文本 + 链接 + 附件 */
@Serializable
data class BodyPayload(
    val text: String = "",
    val links: List<FeedLink> = emptyList(),
    val attachments: List<FeedLink> = emptyList(),
    /**
     * 正文里的插图（绝对地址）。
     *
     * 只收 VSB 站群的 `/__local/…`（或其它站的正经正文图）—— 2026-11-21 实测 56 个详情页：
     * 校内正文图 100% 走 `/__local/`，不带这个前缀的 `<img>` 全是模板 logo / 角标
     * （`images/logo.png`、`snjybq.png`、`ewm.jpg`），收进来就是垃圾。
     */
    val images: List<FeedLink> = emptyList(),
    /**
     * 正文**本身就是一份 PDF** 时的直链。
     *
     * 学生处 xsc 实测：正文只有 115 字「请安装 PDF 阅读器」，真正内容在
     * `iframe[src=/__local/…pdf?e=.pdf]` 里；lnjyt / cahe / fwwb 则是正文旁挂大 PDF。
     * 空串 = 没有。给了直链就说明「文字读不到但有文件可看」，界面显示 PDF 卡片，
     * 而不是报「解析失败」。
     */
    val pdfUrl: String = "",
    /**
     * 附件需要人工过图形验证码（校内站 `/system/_content/download.jsp`）。
     *
     * 实测：App 内直接下会拿到 3805 字节的验证码页，存成 .pdf 打不开、用户以为坏了。
     * 置 true 时界面只给「浏览器打开」并把理由写清楚，不提供「下载」。
     */
    val attachmentsBlocked: Boolean = false,
    /**
     * 缓存 schema 版本。**升级解析能力时必须加 1**，否则老缓存会一直挡着新字段。
     *
     * 2026-11-21 真机踩过：加了 images / pdfUrl 之后，已经缓存过的正文读出来
     * 还是旧结构（这两个字段恒为空），详情页照旧显示「请安装 PDF 阅读器」那段废话，
     * 看起来像新的 PDF 卡片没做。所以缓存要带版本，旧版本的当未命中处理。
     *
     * | 版本 | 变更 |
     * |---|---|
     * | 0 | 早期只有 text / links / attachments（未写版本号） |
     * | 2 | 增加 images / pdfUrl / attachmentsBlocked |
     */
    // ⚠️ 默认值必须是 0，不能写成 SCHEMA：
    // 旧缓存里根本没有这个字段，默认值就是「缺字段时填什么」——
    // 填 SCHEMA 等于让所有旧缓存都自称最新，闸门失效（单测已经抓到过一次）。
    val schema: Int = 0,
) {
    val isEmpty: Boolean
        get() = text.isBlank() && attachments.isEmpty() && links.isEmpty() &&
            images.isEmpty() && pdfUrl.isBlank()

    companion object {
        /** 当前解析能力的版本号；改解析逻辑就 +1（见 [schema] 注释） */
        const val SCHEMA = 2
    }
}

/**
 * 博达 VSB 站群解析器 —— **一套规则吃所有子站**。
 *
 * 不锚 CSS class（30 个站 30 套模板），只锚**站群内核**：
 *   · 文章链接：`info/{栏目ID}/{文章ID}.htm` 或 `content.jsp?…wbtreeid=&wbnewsid=`
 *   · 标题：`<a title="…">`（Family A）或 `<a><em>…</em></a>`（Family B，CMS 会截断）
 *   · 日期：块内第一个 `yyyy-MM-dd`
 *   · 正文：`id="vsb_content"`
 *
 * ## 实测的两族差异（2026-11-10）
 * ```
 * www / news  ：<li class="item"><a title="全标题"><h6>标题</h6><span class="date">2026-11-10</span></a>
 * jwc / xsc   ：<li id="line_u7_0"><a href="…"><span>2026-11-10</span><em>[科室]标题…</em></a>
 * ```
 * Family B 的标题**被 CMS 截断**（末尾 `...`），所以列表标题只能当摘要用，
 * 完整标题要等抓正文时才拿到 —— 这也是「点进去才抓正文」的另一个理由。
 */
object FeedParser {

    /** 文章链接：两种形态都认 */
    private val RE_ARTICLE_HREF = Regex(
        """(?:info/(\d{3,5})/(\d+)\.htm)|(?:wbtreeid=(\d+)[^"'&]*&(?:amp;)?wbnewsid=(\d+))""",
    )

    /** `<a …>…</a>` 整块 */
    private val RE_A_BLOCK = Regex(
        """<a\b([^>]*?)>(.*?)</a>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val RE_HREF = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val RE_TITLE_ATTR = Regex("""title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val RE_EM = Regex("""<em\b[^>]*>(.*?)</em>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val RE_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val RE_DEPT = Regex("""^\s*\[([^\]]{1,20})]\s*""")

    /** 正文容器：`id="vsb_content"` 或 `id="vsb_content_4"`（校内竞赛 cxcy 是后者） */
    private val RE_VSB_CONTENT = Regex("""id\s*=\s*["']vsb_content""", RegexOption.IGNORE_CASE)
    /** `<img …>` */
    private val RE_IMG = Regex("""<img\b([^>]*)>""", RegexOption.IGNORE_CASE)
    /** `<iframe/embed/object …>`（正文是 PDF 时用它定位） */
    private val RE_EMBED = Regex("""<(?:iframe|embed|object)\b([^>]*)>""", RegexOption.IGNORE_CASE)
    /** `src="…"` / `data="…"` */
    private val RE_SRC = Regex("""(?:src|data)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** 附件后缀 */
    private val RE_ATT_EXT = Regex(
        """\.(pdf|docx?|xlsx?|pptx?|zip|rar|7z|txt)(\?|$)""",
        RegexOption.IGNORE_CASE,
    )

    // ── 列表页 ────────────────────────────────────────────

    /**
     * 解析列表页，返回条目（已去重、已按日期倒序）。
     *
     * @param baseUrl 列表页地址，用来把 `../info/…` 这种相对链接转成绝对地址
     */
    fun parseList(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()

        for (m in RE_A_BLOCK.findAll(html)) {
            val attrs = m.groupValues[1]
            val inner = m.groupValues[2]

            val href = RE_HREF.find(attrs)?.groupValues?.get(1) ?: continue
            val hit = RE_ARTICLE_HREF.find(href) ?: continue

            val colId = hit.groupValues[1].ifBlank { hit.groupValues[3] }
            val artId = hit.groupValues[2].ifBlank { hit.groupValues[4] }
            if (colId.isBlank() || artId.isBlank()) continue

            // 标题：优先 title 属性（Family A 有全标题），否则 <em>（可能内嵌 <span>），否则整块文本
            val attrTitle = RE_TITLE_ATTR.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            val emText = RE_EM.find(inner)?.groupValues?.get(1)?.let { stripTags(it) }.orEmpty()
            val rawText = stripTags(inner)

            val rawTitle = attrTitle.ifBlank { emText.ifBlank { rawText } }.trim()
            var title = cleanTitle(rawTitle)
            if (title.isBlank() || title.length < 4) continue
            if (title in setOf("网站首页", "通知公告", "首页", "更多", "下一条", "上一条")) continue

            // ⚠️ 截断判断必须用清洗**之前**的原文 —— cleanTitle 会把末尾的 ... 去掉
            val truncated = rawTitle.endsWith("...") || rawTitle.endsWith("…") ||
                rawTitle.endsWith("。。") || rawTitle.endsWith("..")

            // 日期：块内第一个 yyyy-mm-dd
            var dateText = RE_DATE.findAll(inner + stripTags(attrs))
                .firstOrNull()
                ?.let { "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}" }
                .orEmpty()

            // 站群里还有第三种排法：日期**在 </a> 后面**（`<li><a …>标题</a> 2025-12-17 </li>`）。
            // job 站的「创新创业竞赛」栏目就是这样，不兜住的话整批条目都没有日期，
            // 会被排到列表最后、界面上也不显示日期 —— 等于白抓。
            if (dateText.isBlank()) {
                val after = html.substring(
                    m.range.last + 1,
                    minOf(html.length, m.range.last + 81),
                )
                dateText = RE_DATE.find(after)
                    ?.let { "${it.groupValues[1]}-${it.groupValues[2].padStart(2, '0')}-${it.groupValues[3].padStart(2, '0')}" }
                    .orEmpty()
            }

            // 科室标签：[专业建设与质量监控科] —— 拆到摘要里，标题保持干净
            val dept = RE_DEPT.find(title)?.groupValues?.get(1)?.trim().orEmpty()
            if (dept.isNotBlank()) title = title.replaceFirst(RE_DEPT, "").trim()

            val key = "${source.key}|$colId|$artId"
            if (out.containsKey(key)) continue

            val abs = resolve(baseUrl, href)
            val alt = "https://${hostOf(baseUrl)}/content.jsp?urltype=news.NewsContentUrl" +
                "&wbtreeid=$colId&wbnewsid=$artId"

            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = title,
                summary = if (dept.isNotBlank()) "［$dept］" else "",
                url = abs,
                urlAlt = alt,
                publishedAt = dateText,
                firstSeenAt = now,
                titleTruncated = truncated,
            )
        }

        return out.values.sortedWith(
            compareByDescending<FeedItem> { it.publishedAt }.thenByDescending { it.firstSeenAt },
        )
    }

    // ── 通用列表（非 VSB 站）──────────────────────────────

    /**
     * 通用列表解析 —— 给**不是博达 VSB 站群**的竞赛站点用。
     *
     * 为什么需要第二套：VSB 解析器锚的是 `info/{栏目}/{文章}.htm` 这种地址形态，
     * 而挑战杯是 `/article/15841/`、辽宁省教育厅是
     * `/jyt/gk/zxtz/2026102110290659632/index.shtml`，一个都匹配不上。
     *
     * 这套的锚点换成「**一条长得像标题的链接 + 它附近的一个日期**」：
     *   1. 遍历所有 `<a>`，要求文字 8~160 字、含 ≥6 个汉字、不是 `javascript:`
     *   2. 在链接结束后的 300 字内找第一个日期（`2026-10-21` / `2026.10.21` / `2026年10月21日`）
     *   3. 找不到日期就丢掉 —— 导航栏、页脚、友情链接全都没有日期，这一条就能筛干净
     *
     * 代价：只认「列表项自带日期」的页面。纯 JS 渲染、或日期缺失的站点用不了，
     * 那种情况在 [FeedSources] 里就不会启用。
     */
    fun parseListGeneric(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()
        val cjk = Regex("""[\u4e00-\u9fa5]""")

        for (m in RE_A_BLOCK.findAll(html)) {
            val attrs = m.groupValues[1]
            val href = RE_HREF.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isBlank()) continue
            if (href.startsWith("javascript:", true) || href.startsWith("#")) continue

            val attrTitle = RE_TITLE_ATTR.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            var title = cleanTitle(attrTitle.ifBlank { stripTags(m.groupValues[2]) })
            if (title.length < 8 || title.length > 160) continue
            if (cjk.findAll(title).count() < 6) continue
            if (title in GENERIC_SKIP) continue
            if (source.urlInclude.isNotEmpty() && !href.contains(source.urlInclude)) continue

            // 日期：默认在链接结束之后 300 字以内找第一个
            val after = html.substring(m.range.last + 1, minOf(html.length, m.range.last + 301))
            var d: MatchResult? = null
            if (source.dateLookback) {
                // 「日期在前」的站点（如服务外包大赛：<span class="time">日期</span><a>标题</a>）：
                // **必须先看前**。只看后会把**下一条**的日期当成本条的
                // （2026-11-15 实测：关掉回看仍能解析出条目，但日期整体错位到下一条）。
                // 回看 200 字取最后一个：窗口里可能带上一条的日期，离本条最近的那个才可信。
                val before = html.substring(maxOf(0, m.range.first - 200), m.range.first)
                d = RE_ANY_DATE.findAll(before).lastOrNull()
            }
            if (d == null) d = RE_ANY_DATE.find(after)
            d ?: continue
            val date = normalizeDate(d)

            // 站点级标题过滤（省教育厅那种「什么通知都有」的源，只留竞赛相关）
            if (source.titleInclude.isNotEmpty() &&
                source.titleInclude.none { title.contains(it) }
            ) continue

            val abs = resolve(baseUrl, href)
            var key = "${source.key}|${idFromUrl(abs)}"
            // 主键去重：idFromUrl 取「路径里最长的一串数字」，但有的站点路径是按
            // 年月分目录的（中国教育考试网 `/html1/report/2610/14-1.htm` —— 同月所有文章
            // 最长数字都是 2610），会让整个月的条目挤成同一个键、只留第一条。
            // 这里做**局部兜底**：同键不同址时补一段地址短哈希。
            // 只在真撞车时补，没撞车的源主键一个字节都不变。
            val prev = out[key]
            if (prev != null && prev.url != abs) {
                key = "$key|${abs.hashCode().toUInt().toString(16).take(6)}"
            }
            if (out.containsKey(key)) continue

            val truncated = title.endsWith("...") || title.endsWith("…")
            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = title,
                url = abs,
                publishedAt = date,
                firstSeenAt = now,
                titleTruncated = truncated,
            )
        }

        return out.values.sortedWith(compareByDescending<FeedItem> { it.publishedAt })
    }

    /** `2026-10-21` / `2026.10.21` / `2026/10/21` / `2026年10月21日` 统一成 `yyyy-MM-dd` */
    private fun normalizeDate(m: MatchResult): String {
        val y = m.groupValues[1]
        val mo = m.groupValues[2].padStart(2, '0')
        val d = m.groupValues[3].padStart(2, '0')
        return "$y-$mo-$d"
    }

    /**
     * 从绝对地址里取「稳定且唯一」的 id 作为条目主键的一部分。
     *
     * ⚠️ 不能用「URL 最后一段」—— 实测踩过两次：
     *   · 辽宁省教育厅：`/jyt/gk/zxtz/2026102110290659632/index.shtml` → 每篇都是 `index.shtml`
     *   · 就业信息网：`/news/view/aid/752839/tag/xwkd`            → 每篇都是 `tag/xwkd`
     * 结果是整批条目共用一个主键，去重时**全部塌缩成一条**。
     *
     * 现在改成取「路径里最长的一段纯数字」（教育厅是时间戳、挑战杯是文章号、就业网是 aid），
     * 一个数字都没有才退回用整条路径。
     */
    private fun idFromUrl(abs: String): String {
        val path = abs.substringAfter("://", abs)
            .substringAfter('/', "")
            .substringBefore('#')
            .substringBefore('?')
        val nums = Regex("""\d{3,}""").findAll(path).map { it.value }.toList()
        return nums.maxByOrNull { it.length } ?: path.replace('/', '_').ifBlank { abs.hashCode().toString() }
    }

    private val RE_ANY_DATE = Regex(
        """(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""",
    )

    private val RE_P = Regex("""(?is)<p\b[^>]*>.*?</p>""")

    /** 导航类文字，通用列表里要排掉 */
    private val GENERIC_SKIP = setOf(
        "更多", "查看更多", "首页", "登录", "注册", "联系我们", "关于我们", "上一页", "下一页",
    )

    /**
     * 通用正文提取。
     *
     * 顺序：VSB 容器 → 常见正文容器（zoom / article / detail_con / TRS_Editor / content）
     *      → `<p>` 段落串联（正文页的 `<p>` 密度远高于导航，这一条兜住了大部分站）。
     * 实在拿不到就返回空，上层会提示「点浏览器打开看原文」——**不硬凑一堆导航文字**。
     */
    fun parseBodyGeneric(html: String, baseUrl: String = ""): BodyPayload {
        parseBody(html, baseUrl).let { if (!it.isEmpty) return it }

        var window = ""
        for (marker in GENERIC_BODY_MARKERS) {
            val i = html.indexOf(marker)
            if (i < 0) continue
            val tagEnd = html.indexOf('>', i)
            if (tagEnd < 0) continue
            window = html.substring(tagEnd + 1, minOf(html.length, tagEnd + 1 + 120_000))
            break
        }
        if (window.isBlank()) window = html

        // 正文之后的东西切掉
        for (mk in listOf("class=\"footer", "id=\"footer", "class=\"share", "id=\"share", "京ICP备")) {
            val i = window.indexOf(mk)
            if (i > 200) {
                window = window.substring(0, i)
                break
            }
        }

        val pRun = RE_P.findAll(window).joinToString("") { it.value }
        val body = if (cjkCount(pRun) >= 80) pRun else window

        val (links, attachments) = collectLinks(body, baseUrl)

        // 插图与 PDF：非 VSB 源（高教学会 / 我爱竞赛网 / 服务外包 / 人事考试网）同样要认
        val images = LinkedHashMap<String, FeedLink>()
        for (m in RE_IMG.findAll(body)) {
            val src = RE_SRC.find(m.groupValues[1])?.groupValues?.get(1)?.trim().orEmpty()
            if (src.isBlank() || !looksLikeBodyImage(src)) continue
            val real = when {
                src.startsWith("http") -> src
                baseUrl.isBlank() -> src
                else -> resolve(baseUrl, src)
            }
            images.putIfAbsent(real, FeedLink("", real))
        }
        var pdfUrl = ""
        for (m in RE_EMBED.findAll(body)) {
            val src = RE_SRC.find(m.groupValues[1])?.groupValues?.get(1)?.trim().orEmpty()
            if (!src.contains(".pdf", true)) continue
            pdfUrl = when {
                src.startsWith("http") -> src
                baseUrl.isBlank() -> src
                else -> resolve(baseUrl, src)
            }
            break
        }

        var text = htmlToText(body).replace(Regex("""<[^<>]*$"""), "").trim()

        return BodyPayload(
            text = if (text.length < 20) "" else text,
            links = links.take(40),
            attachments = attachments.take(20),
            images = images.values.take(8).toList(),
            pdfUrl = pdfUrl,
            attachmentsBlocked = attachments.any {
                it.url.contains("download.jsp", true) || it.url.contains("articledownload", true)
            },
        )
    }

    private val GENERIC_BODY_MARKERS = listOf(
        // Discuz 系（我爱竞赛网等）：正文在 <td id="article_content">，必须先命中它，
        // 否则会落到后面的 class="content"（那是侧栏推荐块，抓出来全是别的比赛链接）
        "id=\"article_content\"",
        // ── 2026-11-21 三路实测补进来的容器（写死一种会让整源空白） ──
        // 四个 neea 站同一 CMS 但分两个模板：前三个 Condiv、教育考试院公告是 conts
        "class=\"Condiv\"", "class=\"conts\"",
        // 中国人事考试网：正文 div#p_content
        "id=\"p_content\"",
        // 高教学会：正文 div.branch_text_box
        "class=\"branch_text_box\"",
        // 服务外包大赛：正文 div.show-content（长截图 + PDF）
        "class=\"show-content\"",
        // 竞赛成果：正文 div.content_detail（纯图片）
        "class=\"content_detail\"",
        // 省教育厅：正文 div.zoom（已在上面的 zoom 覆盖，这里保留注释说明）
        "class=\"zoom\"", "id=\"zoom\"", "class=\"TRS_Editor\"", "class=\"detail_con\"",
        "class=\"article-content\"", "class=\"article\"", "id=\"content\"", "class=\"content\"",
        "class=\"main_rcont\"",
    )

    // ── 我爱竞赛网（Discuz）正文 / 摘要 / 时间 ─────────────

    /**
     * 我爱竞赛网文章页（2026-11-13 实测结构）：
     * ```html
     * <div class="xg1"> 2026-10-15 14:12 | 评论: 0 | 43054 </div>
     * <h1 class="ph">全标题</h1>
     * <div class="s zhaiyao"><div><strong>摘要</strong>: …</div></div>
     * <table class="vwtb"><tr><td id="article_content"> 正文… </td></tr></table>
     * <div id="comment" class="bm">
     * ```
     * 通用提取器会撞到后面的 `class="content"`（侧栏推荐），所以单独精确切一刀。
     */
    fun parseBodyJingsai(html: String, baseUrl: String = ""): BodyPayload {
        val start = html.indexOf("id=\"article_content\"")
        if (start < 0) return parseBodyGeneric(html, baseUrl)
        val tagEnd = html.indexOf('>', start).let { if (it < 0) return BodyPayload() else it }
        var body = html.substring(tagEnd + 1, minOf(html.length, tagEnd + 1 + 120_000))
        // 正文结束：评论区 / 内容表格结束，二者取先到的
        val cut = listOf("id=\"comment\"", "</td></tr></table>", "class=\"pi\"")
            .map { body.indexOf(it) }
            .filter { it > 200 }
            .minOrNull()
        if (cut != null) body = body.substring(0, cut)

        val (links, attachments) = collectLinks(body, baseUrl)
        val text = htmlToText(body).trim()
        return BodyPayload(
            text = if (text.length < 20) "" else text,
            links = links.take(40),
            attachments = attachments.take(20),
        )
    }

    /** 我爱竞赛网的摘要块 + 发布时间（有就返回，没有给空串 —— 不猜） */
    fun parseJingsaiMeta(html: String): Pair<String, String> {
        val summary = Regex("""<div class="s zhaiyao">.*?<strong>摘要</strong>[：:]\s*(.*?)</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)
            ?.let { htmlToText(it, keepNewlines = false) }?.take(120).orEmpty()
        val date = Regex("""<div class="xg1">.*?(\d{4})-(\d{2})-(\d{2})\s+\d{1,2}:\d{2}""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }.orEmpty()
        return summary to date
    }

    private fun collectLinks(body: String, baseUrl: String): Pair<List<FeedLink>, List<FeedLink>> {
        val links = LinkedHashMap<String, FeedLink>()
        val attachments = LinkedHashMap<String, FeedLink>()
        for (m in RE_A_BLOCK.findAll(body)) {
            val attrs = m.groupValues[1]
            val href = RE_HREF.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isBlank() || href.startsWith("javascript:", true) || href.startsWith("#")) continue
            val label = RE_TITLE_ATTR.find(attrs)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                ?: htmlToText(m.groupValues[2], keepNewlines = false)
            val abs = if (href.startsWith("http") || baseUrl.isBlank()) href else resolve(baseUrl, href)
            if (isAttachment(href)) {
                val name = label.ifBlank { href.substringAfterLast('/').substringBefore('?') }
                attachments.putIfAbsent(href, FeedLink(name.take(120), abs, isAttachment = true))
            } else if (label.length >= 2 && href.length > 8) {
                links.putIfAbsent(href, FeedLink(label.take(80), abs))
            }
        }
        return links.values.toList() to attachments.values.toList()
    }

    private fun cjkCount(s: String): Int = Regex("""[\u4e00-\u9fa5]""").findAll(s).count()

    /**
     * 从正文里捞**报名/申报**截止日期。
     *
     * 只在**真的写明了**的时候返回；捞不到返回空串。界面上宁可不显示倒计时，
     * 也不能猜一个日期 —— 竞赛截止日猜错是真的会害人错过。
     *
     * 语义锚（评审 B3）：只认「报名/申报/报送/提交材料」语境下的截止；
     * 「作品提交截止」「比赛截止」不是报名截止，不抽 —— 错标比不标更害人。
     */
    fun extractDeadline(text: String): String {
        // 日期串：2026年11月20日 / 2026-11-20 / 2026.11.20（结尾「日」可选）
        val d = """(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})日?"""
        // ①「报名截止时间：2026年11月20日」—— 截止前 16 字内要有报名语境词
        Regex("""(?:截止|截至)[^。；\n]{0,16}?$d""").findAll(text).forEach { m ->
            val before = text.substring(maxOf(0, m.range.first - 16), m.range.first)
            if (Regex("""报名|申报|报送|提交材料""").containsMatchIn(before)) {
                return normalizeDate(m)
            }
        }
        // ②「请于2026年11月20日前完成报名 / 提交材料」
        Regex("""$d\s*(?:前|之前)[^。；\n]{0,10}?(?:报名|申报|报送|提交材料|完成)""")
            .find(text)?.let { return normalizeDate(it) }
        return ""
    }

    // ── 竞赛管理平台（cxcy）────────────────────────────────

    /**
     * 沈阳农业大学**创新创业管理平台**的三种列表（2026-11-13 实测重写）：
     *
     * ```
     * /comp/front/comp/list          科技竞赛（每页 7 条，?page=N 正序，共 993）
     * /dcplan/front/dcplan/list      大创项目（6 条，一年一条立项通知）
     * /comp/front/comp/articlelist?cid=12   成果公示（获奖作品展/名单）
     * ```
     *
     * **关键升级：列表页本身就带结构化字段**，不用再逐条进详情补日期：
     * ```html
     * <span class="state underway">比赛中</span>
     * <a href="../comp/info?id=1542" title="全标题">…</a>
     * <div class="txt">摘要…</div>
     * <div class="msg">…报名时间：2026.08.01至2027.10.31</div>
     * <div class="msg">…主办方：全国大学生生命科学竞赛委员会</div>
     * ```
     * 成果公示的日期在左侧 `<div class="day">08</div><div class="year">2025.12</div>`。
     * 比赛窗口（contestStart/End）列表没有，仍由 [parseCxcyDetail] 在抓详情时补。
     */
    fun parseListCxcy(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()
        for (m in RE_CXCY_LI.findAll(html)) {
            val block = m.groupValues[1]

            val link = RE_CXCY_LINK.find(block) ?: continue
            val href = link.groupValues[1]
            val id = link.groupValues[2]
            val title = cleanTitle(link.groupValues[3])
            if (id.isBlank() || title.length < 4) continue

            val key = "${source.key}|$id"
            if (out.containsKey(key)) continue

            val state = RE_CXCY_STATE.find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val reg = RE_CXCY_REG.find(block)
            val regStart = reg?.let { dotDate(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }.orEmpty()
            val regEnd = reg?.let { dotDate(it.groupValues[4], it.groupValues[5], it.groupValues[6]) }.orEmpty()
            val organizer = RE_CXCY_ORG.find(block)?.groupValues?.get(1)
                ?.let { stripTags(it).trim() }?.take(60).orEmpty()
            val summary = RE_CXCY_TXT.find(block)?.groupValues?.get(1)
                ?.let { FeedSummary.clean(htmlToText(it, keepNewlines = false)) }.orEmpty()

            // 成果公示：日期在 day + year 两个块里
            val artDate = RE_CXCY_DAY.find(block)?.let {
                dotDate(it.groupValues[2], it.groupValues[3], it.groupValues[1])
            }.orEmpty()

            val abs = resolve(baseUrl, href)
            // 备用地址：comp 的根路由 /comp/info?id= 实测也通（2026-11-13）；
            // articledetail 只有 front 路由（根路由 404），dcplan 只有 front 路由 → 无备用
            val alt = if (href.contains("articledetail") || href.contains("dcplan")) ""
            else "http://cxcy.syau.edu.cn/comp/info?id=$id"

            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = title,
                summary = summary,
                url = abs,
                urlAlt = if (alt != abs) alt else "",
                // 成果公示有真实发布日；竞赛/大创**不拿报名开始日冒充发布日**
                //（评审 B4）：publishedAt 留空，排序由 firstSeenAt 兜底，
                // 报名开始日在 regStart 字段里单独展示
                publishedAt = artDate,
                firstSeenAt = now,
                deadline = regEnd,
                regStart = regStart,
                organizer = organizer,
                platformState = state,
            )
        }
        return out.values.toList()
    }

    /** `<li class="clearfix">…</li>` 整块（平台三种列表共用这一种行结构） */
    private val RE_CXCY_LI = Regex(
        """<li\s+class="clearfix">(.*?)</li>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /** 行内详情链接：comp/info、dcplan/info、articledetail 三种形态，title 属性带全标题 */
    private val RE_CXCY_LINK = Regex(
        """<a[^>]+href="([^"]*(?:comp/info|dcplan/info|articledetail)\?id=(\d+))"[^>]*title="([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )

    private val RE_CXCY_STATE = Regex("""class="state[^"]*"\s*>([^<]+)<""", RegexOption.IGNORE_CASE)

    /** `报名时间：2026.08.01至2027.10.31`（点分隔；兼容 - / 年月日、可选时刻、至两侧空白） */
    private val RE_CXCY_REG = Regex(
        """报名时间[：:]\s*(\d{4})[.\-/年](\d{1,2})[.\-/月](\d{1,2})日?(?:\s+\d{1,2}:\d{2})?\s*至\s*(\d{4})[.\-/年](\d{1,2})[.\-/月](\d{1,2})""",
    )

    private val RE_CXCY_ORG = Regex("""主办方[：:]\s*([^<]{2,80})""")

    private val RE_CXCY_TXT = Regex(
        """<div class="txt">(.*?)</div>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /** 成果公示左侧日期块：`<div class="day">08</div> <div class="year">2025.12</div>` */
    private val RE_CXCY_DAY = Regex(
        """<div class="day">(\d{1,2})</div>\s*<div class="year">(\d{4})[.\-/](\d{1,2})</div>""",
        RegexOption.IGNORE_CASE,
    )

    private fun dotDate(y: String, mo: String, d: String): String =
        "$y-${mo.padStart(2, '0')}-${d.padStart(2, '0')}"

    /** 竞赛详情页里那几个结构化字段 */
    data class CompInfo(
        val startAt: String = "",
        val deadline: String = "",
        val organizer: String = "",
        val contestStart: String = "",
        val contestEnd: String = "",
    )

    /**
     * 从竞赛详情页抽「报名窗口 / 比赛窗口 / 主办方」。
     *
     * 只认页面上明确写出来的 `报名时间：… 至 …` 这种字段；
     * 抽不到就全空 —— **不猜**。正文里的「2027 年 1 月 15 日，网络报名截止」这种叙述句
     * 交给 [extractDeadline] 兜底，两者都失败就只是不显示倒计时。
     */
    fun parseCxcyDetail(html: String): CompInfo {
        val text = htmlToText(html)
        fun window(label: String): Pair<String, String>? {
            // 两个日期之间隔着「00:00 至」这种带数字的片段，所以不能简单用 [^\d]
            val m = Regex("""$label[：:]\s*(\d{4}-\d{2}-\d{2}).{0,24}?(\d{4}-\d{2}-\d{2})""").find(text)
                ?: return null
            return m.groupValues[1] to m.groupValues[2]
        }
        val reg = window("报名时间")
        val con = window("比赛时间")
        val org = Regex("""主办方[：:]\s*(\S{2,40})""").find(text)?.groupValues?.get(1)?.trim().orEmpty()
        return CompInfo(
            startAt = reg?.first.orEmpty(),
            deadline = reg?.second.orEmpty(),
            organizer = org,
            contestStart = con?.first.orEmpty(),
            contestEnd = con?.second.orEmpty(),
        )
    }

    // ── 校团委（tw）：日期拆两段 ─────────────────────────────

    /**
     * 校团委列表：`<div class="date"><b>5</b><span>2026-11</span></div>` +
     * `<div class="txt"><a href="info/1053/8419.htm">标题</a><p>正文首段摘要</p></div>`。
     *
     * 日期被拆成「日」+「年-月」两段，必须拼起来；只取 span 会只有月份。
     * 列表 `<p>` 是正文首段摘要（每项都有），直接当 summary，不必进详情。
     */
    fun parseListTw(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()
        for (m in RE_TW_LI.findAll(html)) {
            val block = m.groupValues[1]
            val day = RE_TW_DAY.find(block) ?: continue
            val link = RE_A_BLOCK.find(block) ?: continue
            val href = RE_HREF.find(link.groupValues[1])?.groupValues?.get(1) ?: continue
            val hit = RE_ARTICLE_HREF.find(href) ?: continue
            val colId = hit.groupValues[1].ifBlank { hit.groupValues[3] }
            val artId = hit.groupValues[2].ifBlank { hit.groupValues[4] }
            if (colId.isBlank() || artId.isBlank()) continue

            val rawTitle = RE_TITLE_ATTR.find(link.groupValues[1])?.groupValues?.get(1)
                ?: stripTags(link.groupValues[2])
            val title = cleanTitle(rawTitle)
            if (title.length < 6) continue

            val date = "${day.groupValues[2]}-${day.groupValues[3].padStart(2, '0')}-${day.groupValues[1].padStart(2, '0')}"
            val summary = RE_TW_P.find(block)?.groupValues?.get(1)
                ?.let { FeedSummary.clean(htmlToText(it, keepNewlines = false)) }.orEmpty()

            val key = "${source.key}|$colId|$artId"
            if (out.containsKey(key)) continue
            val abs = resolve(baseUrl, href)
            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = title,
                summary = summary,
                url = abs,
                urlAlt = "https://${hostOf(baseUrl)}/content.jsp?urltype=news.NewsContentUrl&wbtreeid=$colId&wbnewsid=$artId",
                publishedAt = date,
                firstSeenAt = now,
                titleTruncated = rawTitle.trim().let { it.endsWith("...") || it.endsWith("…") },
            )
        }
        return out.values.sortedWith(compareByDescending<FeedItem> { it.publishedAt })
    }

    private val RE_TW_LI = Regex("""<li>(.*?)</li>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val RE_TW_DAY = Regex("""<div class="date">\s*<b>(\d{1,2})</b>\s*<span>(\d{4})-(\d{1,2})</span>""", RegexOption.IGNORE_CASE)
    private val RE_TW_P = Regex("""<p>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    // ── 中国高等教育学会（cahe）：日期拆两段 + h6 标题 ────────

    /**
     * 高教学会「业务公告」：锚点内部是
     * `<div class="time_section"><span class="date">31</span><span class="year">2026/10</span></div>`
     * + `<div class="item_cont"><h6>标题</h6><p>摘要</p></div>`。
     *
     * 直接用 a.text() 会把标题+日期+摘要糊成一串，所以逐块取：
     * h6 标题、p 摘要、date+year 拼日期。标题白名单（source.titleInclude）筛竞赛相关。
     */
    fun parseListCahe(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()
        for (m in RE_CAHE_A.findAll(html)) {
            val href = m.groupValues[1]
            val inner = m.groupValues[2]
            val id = Regex("""/site/content/(\d+)\.html""").find(href)?.groupValues?.get(1) ?: continue

            val title = RE_CAHE_H6.find(inner)?.groupValues?.get(1)
                ?.let { cleanTitle(stripTags(it)) }.orEmpty()
            if (title.length < 6) continue
            if (source.titleInclude.isNotEmpty() && source.titleInclude.none { title.contains(it) }) continue

            val day = RE_CAHE_DATE.find(inner) ?: continue
            val date = "${day.groupValues[2]}-${day.groupValues[3].padStart(2, '0')}-${day.groupValues[1].padStart(2, '0')}"
            val summary = RE_CAHE_P.find(inner)?.groupValues?.get(1)
                ?.let { FeedSummary.clean(htmlToText(it, keepNewlines = false)) }.orEmpty()

            val key = "${source.key}|$id"
            if (out.containsKey(key)) continue
            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = title,
                summary = summary,
                url = resolve(baseUrl, href),
                publishedAt = date,
                firstSeenAt = now,
            )
        }
        return out.values.sortedWith(compareByDescending<FeedItem> { it.publishedAt })
    }

    private val RE_CAHE_A = Regex(
        """<a\s+href="(/site/content/\d+\.html)"[^>]*>(.*?)</a>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )
    private val RE_CAHE_DATE = Regex(
        """<span class="date">(\d{1,2})</span>\s*<span class="year">(\d{4})/(\d{1,2})</span>""",
        RegexOption.IGNORE_CASE,
    )
    private val RE_CAHE_H6 = Regex("""<h6>(.*?)</h6>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val RE_CAHE_P = Regex("""<p>(.*?)</p>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    // ── 我爱竞赛网（jingsai）：无日期 + img@title + 站点根链接 ──

    /**
     * 我爱竞赛网「榜单竞赛」列表（GB18030，FeedHttp 已按 charset 解码）。
     *
     * 三个实测坑（2026-11-11 调研 + 2026-11-13 复核）：
     * 1. 列表**完全没有日期** → publishedAt 留空，排序靠 firstSeenAt；
     * 2. 可见标题被截断，全标题在 `<img title="…">` 或 `<a title="…">` 里；
     * 3. `article-NNNNN-1.html` 的 base 是**站点根**，不是当前分类目录。
     *
     * 截止日：标题里常写【11月13日截止报名】——只有这种**写明了**的才抽，
     * 年份用当前年兜底（12 月写 1 月的截止按下一年算）。抽不到就不显示。
     */
    fun parseListJingsai(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
        today: java.time.LocalDate = java.time.LocalDate.now(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()
        val root = "https://www.52jingsai.com/"
        // 只扫「榜单竞赛」主列表块（<ul><li><a href="article-…）：
        // 顶部横幅和侧栏热榜会重复同一批文章且标题截断更狠。
        // 主列表的 li 以图片链接开头，侧栏热榜的 li 以 <em> 开头 —— 用这个区分。
        val listStart = RE_JINGSAI_UL.find(html)?.range?.first ?: 0
        val listEnd = html.indexOf("</ul>", listStart).let { if (it < 0) html.length else it }
        val window = html.substring(listStart, listEnd)
        for (m in RE_JINGSAI_LI.findAll(window)) {
            val block = m.groupValues[1]
            val id = RE_JINGSAI_ID.find(block)?.groupValues?.get(1) ?: continue
            // 全标题优先 img@title，其次 a@title，最后可见文本
            val title = RE_JINGSAI_IMG_TITLE.find(block)?.groupValues?.get(1)
                ?: RE_TITLE_ATTR.find(block)?.groupValues?.get(1)
                ?: RE_JINGSAI_SPAN.find(block)?.groupValues?.get(1)?.let { stripTags(it) }
                ?: continue
            val clean = cleanTitle(title)
            if (clean.length < 8) continue

            val key = "${source.key}|$id"
            if (out.containsKey(key)) continue

            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = clean,
                url = root + "article-$id-1.html",
                publishedAt = "",
                firstSeenAt = now,
                deadline = deadlineFromTitle(clean, today),
            )
        }
        return out.values.toList()
    }

    private val RE_JINGSAI_UL = Regex("""<ul><li><a href="article-""", RegexOption.IGNORE_CASE)
    private val RE_JINGSAI_LI = Regex("""<li>(.*?)</li>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val RE_JINGSAI_ID = Regex("""article-(\d{3,6})-1\.html""")
    private val RE_JINGSAI_IMG_TITLE = Regex("""<img[^>]+title="([^"]{8,})"""", RegexOption.IGNORE_CASE)
    private val RE_JINGSAI_SPAN = Regex("""<span><a[^>]*>(.*?)</a></span>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /**
     * 从标题里抽「X月X日截止报名」这类**写明了的**截止日。
     *
     * 年份规则（评审 B1/B2 收紧，宁可不显示）：
     * 1. 标题里写明了 `(20xx)年` → 直接采信；
     * 2. 没写年：候选日在「今天」之后 → 用今年；
     * 3. 候选日已过但**只过了 ≤30 天**且现在是 12 月、截止在 1 月 → 按下一年
     *    （12 月发「1月10日截止」的真实场景）；
     * 4. 其它已过期的（年中陈旧条目）→ 返回空串，**不造未来日期**。
     */
    internal fun deadlineFromTitle(title: String, today: java.time.LocalDate): String {
        val m = Regex("""(\d{1,2})月(\d{1,2})日[^】\]]{0,8}(?:截止|截至)|(?:截止|截至)[^【\[]{0,8}(\d{1,2})月(\d{1,2})日""")
            .find(title) ?: return ""
        val mo = (m.groupValues[1].ifBlank { m.groupValues[3] }).toIntOrNull() ?: return ""
        val d = (m.groupValues[2].ifBlank { m.groupValues[4] }).toIntOrNull() ?: return ""
        if (mo !in 1..12 || d !in 1..31) return ""

        // 标题写明了年份 → 优先采信
        val explicitYear = Regex("""(20\d{2})年""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        if (explicitYear != null) {
            return runCatching { java.time.LocalDate.of(explicitYear, mo, d).toString() }.getOrDefault("")
        }

        val candidate = runCatching { java.time.LocalDate.of(today.year, mo, d) }.getOrNull() ?: return ""
        if (!candidate.isBefore(today)) return candidate.toString()
        // 只允许「12 月看到 1 月截止」这一种跨年；其余过期一律不显示
        if (today.monthValue == 12 && mo == 1) {
            return runCatching { java.time.LocalDate.of(today.year + 1, mo, d).toString() }.getOrDefault("")
        }
        return ""
    }

    // ── 正文 ──────────────────────────────────────────────

    /** 只要纯文本（老调用方/单测用） */
    fun parseArticle(html: String): String = parseBody(html).text

    /**
     * 从详情页抽「正文 + 超链接 + 附件」。
     *
     * 为什么附件要单独抽：很多通知的**实质内容就在附件里**（申报表、名单、实施细则 xlsx/doc）。
     * 只抽正文的话，「奖学金公示名单」这种信息的价值等于 0。
     *
     * 附件识别（实测）：`/system/_content/download.jsp?…wbfileid=…`，
     * 以及正文直接是 PDF 的 `/__local/…/xxx.pdf`。
     */
    fun parseBody(html: String, baseUrl: String = ""): BodyPayload {
        // 容器 id 实测有两种：`vsb_content`（6 源）与 `vsb_content_4`（校内竞赛 cxcy）。
        // 写死 `id="vsb_content"` 会让 cxcy 整页空白 —— 用正则认前缀。
        val mark = RE_VSB_CONTENT.find(html)?.range?.first ?: -1
        if (mark < 0) return BodyPayload()

        // 从开标签的 `>` 之后开始 —— 否则正文里会残留 `id="vsb_content">` 这段垃圾
        val tagEnd = html.indexOf('>', mark)
        val start = if (tagEnd in 0 until html.length - 1) tagEnd + 1 else mark

        val window = html.substring(start, minOf(html.length, start + 120_000))

        // 切掉正文之后的东西（页脚/分享/相关链接）
        val cutMarkers = listOf(
            "id=\"div_vote_id\"", "class=\"footer", "id=\"footer", "<!-- 底部",
            "class=\"main_bottom", "class=\"share", "id=\"share",
        )
        var body = window
        for (mk in cutMarkers) {
            val i = body.indexOf(mk)
            if (i >= 0) {
                body = body.substring(0, i)
                break
            }
        }

        // ── 链接与附件：趁标签还在，从原始片段里抽 ──
        val links = LinkedHashMap<String, FeedLink>()
        val attachments = LinkedHashMap<String, FeedLink>()


        for (m in RE_A_BLOCK.findAll(body)) {
            val attrs = m.groupValues[1]
            val href = RE_HREF.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (href.isBlank()) continue
            if (href.startsWith("javascript:", true) || href.startsWith("#")) continue

            val label = RE_TITLE_ATTR.find(attrs)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
                ?: htmlToText(m.groupValues[2], keepNewlines = false)

            // 附件链接实测都是相对路径（/system/_content/download.jsp…），必须按文章地址转绝对
            val abs = when {
                href.startsWith("http") -> href
                baseUrl.isBlank() -> href
                else -> resolve(baseUrl, href)
            }

            if (isAttachment(href)) {
                val name = label.ifBlank { href.substringAfterLast('/').substringBefore('?') }
                attachments.putIfAbsent(href, FeedLink(name.take(120), abs, isAttachment = true))
            } else if (label.length >= 2 && href.length > 8) {
                links.putIfAbsent(href, FeedLink(label.take(80), abs))
            }
        }


        // ── 插图：只留正文图，丢掉模板 logo ──
        // 实测（2026-11-21，56 个 VSB 详情页）：正文图 100% 走 `/__local/…`，
        // 其余 `<img>` 全是 logo/角标/二维码（images/logo.png、snjybq.png、ewm.jpg）。
        val images = LinkedHashMap<String, FeedLink>()
        for (m in RE_IMG.findAll(body)) {
            val attrs = m.groupValues[1]
            val src = RE_SRC.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
            if (src.isBlank()) continue
            if (!looksLikeBodyImage(src)) continue
            val real = when {
                src.startsWith("http") -> src
                baseUrl.isBlank() -> src
                else -> resolve(baseUrl, src)
            }
            images.putIfAbsent(real, FeedLink("", real))
        }

        // ── 正文本身是 PDF：iframe/embed/object 指向 .pdf（学生处 xsc 实测） ──
        var pdfUrl = ""
        for (m in RE_EMBED.findAll(body)) {
            val src = RE_SRC.find(m.groupValues[1])?.groupValues?.get(1)?.trim().orEmpty()
            if (!src.contains(".pdf", true)) continue
            pdfUrl = when {
                src.startsWith("http") -> src
                baseUrl.isBlank() -> src
                else -> resolve(baseUrl, src)
            }
            break
        }

        var text = htmlToText(body)
        // 切割点可能落在标签中间，去掉末尾那半个标签（形如 `<div`）
        text = text.replace(Regex("""<[^<>]*$"""), "").trim()

        // download.jsp / articledownload = 附件挂图形验证码（实测返回 3805 字节验证码页）。
        // 界面据此只给「浏览器打开」，不给「下载」—— 否则存下来是张 HTML。
        val attBlocked = attachments.keys.any {
            it.contains("download.jsp", true) || it.contains("articledownload", true)
        }
        return BodyPayload(
            text = if (text.length < 5) "" else text,
            links = links.values.take(40).toList(),
            attachments = attachments.values.take(20).toList(),
            images = images.values.take(8).toList(),
            pdfUrl = pdfUrl,
            attachmentsBlocked = attBlocked,
        )
    }

    /**
     * 这张图是不是「正文图」（而不是模板元素）。
     *
     * 判据来自 2026-11-21 对 20 个源的实测：
     *  · 校内 VSB：正文图全在 `/__local/`，其余是 logo / 角标 / 二维码；
     *  · 高教学会：图在 `cmsfiles.zhongkefu.com.cn`；
     *  · 我爱竞赛网：图在 `cdn1.52jingsai.com`；
     *  · 服务外包大赛：长截图在 `/attached/image/`。
     */
    private fun looksLikeBodyImage(src: String): Boolean =
        src.contains("/__local/") ||
            src.contains("cmsfiles.", true) ||
            src.contains("cdn1.52jingsai.com", true) ||
            src.contains("/attached/image/", true)

    /** 是不是附件（下载链接 / 常见文档后缀 / PDF） */


    /**
     * 就业信息网（jobsyau.jysd.com，VSB 之外的 SaaS）正文解包。
     *
     * 2026-11-21 实测完整链路：
     *     base64 解码 -> zlib inflate -> substr(91) -> base64 解码 -> substr(15)
     * 样本 aid=752839 解出 1926 字节 / 10 个 <p> / 436 汉字 / 1 张图。
     *
     * 为什么不用 WebView：那条链要先跑站点自己的 base64.min.js，还会被外链重定向带跑
     * （aid=752480 就跳到辽宁日报客户端页）。纯计算的事放在数据层，可单测。
     *
     * @return 解出来的 HTML 片段；解不开返回空串（上层会退回普通正文提取）
     */
    fun decodeJyBody(html: String): String {
        val m = RE_JY_PAYLOAD.find(html) ?: return ""
        val raw = m.groupValues[1]
        return runCatching {
            // ⚠️ 必须按 ISO_8859_1（单字节一一映射）转，不能用默认 UTF-8：
            // 这一步解出来的是 deflate 二进制，用 UTF-8 解码会把非法字节替换成 U+FFFD，
            // 再转回字节就永远 inflate 失败。
            val step1 = String(java.util.Base64.getDecoder().decode(raw.trim()), Charsets.ISO_8859_1)
            // 中间层是 "A"*91 + base64 的纯 ASCII，用 UTF-8 解没问题；显式写出来是为了别让人以为靠默认值
            val step2 = String(inflate(step1.toByteArray(Charsets.ISO_8859_1)), Charsets.UTF_8)
            val cut = if (step2.length > 91) step2.substring(91) else step2
            // 最内层是正文 HTML（可能含中文），必须 UTF-8
            val step3 = String(java.util.Base64.getDecoder().decode(cut.trim()), Charsets.UTF_8)
            if (step3.length > 15) step3.substring(15) else step3
        }.getOrDefault("")
    }

    private fun inflate(b: ByteArray): ByteArray =
        java.util.zip.InflaterInputStream(b.inputStream()).use { it.readBytes() }

    /** 就业信息网正文载荷：`var content = "…base64…"` 这一类赋值 */
    private val RE_JY_PAYLOAD = Regex(
        """(?:content|article)\s*[=:]\s*[\x22\x27]([A-Za-z0-9+/=\s]{60,})[\x22\x27]""",
        RegexOption.IGNORE_CASE,
    )

    private fun isAttachment(href: String): Boolean =
        href.contains("download.jsp", true) ||
            href.contains("wbfileid=", true) ||
            RE_ATT_EXT.containsMatchIn(href) ||
            (href.contains("__local", true) && href.contains(".pdf", true))

    /** 从正文里补全被截断的标题：详情页 `<h1>`/`<h2>`/`<title>` */
    fun parseArticleTitle(html: String): String {
        for (re in listOf(
            Regex("""<h1\b[^>]*>(.*?)</h1>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("""<h2\b[^>]*>(.*?)</h2>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
            Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
        )) {
            val t = cleanTitle(stripTags(re.find(html)?.groupValues?.get(1).orEmpty()))
            if (t.length in 6..120) return t
        }
        return ""
    }

    // ── 工具 ──────────────────────────────────────────────

    /**
     * 标题清洗。
     *
     * 2026-11-15 加「解实体 + 去残留标签」：政府 CMS 的 `title` 属性里会写
     * `&#60;br>` 这种**编码过的换行标签**（如中国教育考试网的四六级公告），
     * 只在 `title` 属性里出现、锚文本是空的。不处理的话标题里会留一串 `&#60;br>`。
     *
     * 顺序是「先解实体、再剥标签」：解完之后 `&#60;br>` 才变成真正的 `<br>`，
     * 才能被第二道剥掉 —— 反过来先剥就剥不到。
     */
    fun cleanTitle(s: String): String = decodeEntities(s)
        .replace(Regex("""(?s)<[^>]+>"""), " ")
        .replace(Regex("""\s+"""), " ")
        .replace("&nbsp;", " ")
        .trim()
        .trimEnd('.', '…', '。')

    /** 去标签 + 解实体 */
    fun stripTags(s: String): String = htmlToText(s, keepNewlines = false)

    /** HTML → 纯文本（<p>/<br>/<div> 转换行） */
    fun htmlToText(html: String, keepNewlines: Boolean = true): String {
        var s = html
        s = s.replace(Regex("""(?is)<(script|style|noscript)\b.*?</\1>"""), " ")
        if (keepNewlines) {
            s = s.replace(Regex("""(?i)<br\s*/?>"""), "\n")
            s = s.replace(Regex("""(?i)</(p|div|li|h1|h2|h3|h4|tr)>"""), "\n")
            s = s.replace(Regex("""(?i)</td>"""), " ")
        }
        s = s.replace(Regex("""(?s)<[^>]+>"""), " ")
        s = decodeEntities(s)
        s = s.replace("\r", "")
        s = if (keepNewlines) {
            s.replace(Regex("""[ \t]+"""), " ")
                .replace(Regex("""\n[ \t]+"""), "\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        } else {
            s.replace(Regex("""\s+"""), " ").trim()
        }
        return s
    }

    private fun decodeEntities(s: String): String {
        if (!s.contains('&')) return s
        var out = s
        val map = mapOf(
            "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
            "&quot;" to "\"", "&#39;" to "'", "&ldquo;" to "“", "&rdquo;" to "”",
            "&mdash;" to "—", "&hellip;" to "…", "&middot;" to "·",
        )
        for ((k, v) in map) out = out.replace(k, v)
        out = Regex("""&#(\d{2,5});""").replace(out) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null) String(Character.toChars(code)) else m.value
        }
        return out
    }

    /** 相对链接 → 绝对链接 */
    fun resolve(baseUrl: String, href: String): String = runCatching {
        if (href.startsWith("http")) href
        else URI(baseUrl).resolve(href.replace(" ", "%20")).toString()
    }.getOrDefault(href)

    fun hostOf(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
}
