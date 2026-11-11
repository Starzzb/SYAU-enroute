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
) {
    val isEmpty: Boolean get() = text.isBlank() && attachments.isEmpty() && links.isEmpty()
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

            // 日期：链接结束之后 300 字以内的第一个
            val after = html.substring(m.range.last + 1, minOf(html.length, m.range.last + 301))
            val d = RE_ANY_DATE.find(after) ?: continue
            val date = normalizeDate(d)

            // 站点级标题过滤（省教育厅那种「什么通知都有」的源，只留竞赛相关）
            if (source.titleInclude.isNotEmpty() &&
                source.titleInclude.none { title.contains(it) }
            ) continue

            val abs = resolve(baseUrl, href)
            val key = "${source.key}|${idFromUrl(abs)}"
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
        var text = htmlToText(body).replace(Regex("""<[^<>]*$"""), "").trim()

        return BodyPayload(
            text = if (text.length < 20) "" else text,
            links = links.take(40),
            attachments = attachments.take(20),
        )
    }

    private val GENERIC_BODY_MARKERS = listOf(
        "class=\"zoom\"", "id=\"zoom\"", "class=\"TRS_Editor\"", "class=\"detail_con\"",
        "class=\"article-content\"", "class=\"article\"", "id=\"content\"", "class=\"content\"",
        "class=\"main_rcont\"",
    )

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
     * 从正文里捞报名/提交截止日期。
     *
     * 只在**真的写明了**的时候返回；捞不到返回空串。界面上宁可不显示倒计时，
     * 也不能猜一个日期 —— 竞赛截止日猜错是真的会害人错过。
     */
    fun extractDeadline(text: String): String {
        val d = """(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})"""
        // ①「报名截止时间：2026年11月20日」
        Regex("""(?:截止|截至)[^。；\n]{0,16}?$d""").find(text)?.let { return normalizeDate(it) }
        // ②「请于2026年11月20日前提交」
        Regex("""$d\s*(?:前|之前)[^。；\n]{0,10}?(?:提交|报送|报名|上传|申报|完成)""")
            .find(text)?.let { return normalizeDate(it) }
        return ""
    }

    // ── 竞赛管理平台（cxcy）────────────────────────────────

    /**
     * 沈阳农业大学**创新创业管理平台**的竞赛列表。
     *
     * 为什么单独一套：它的文章地址是 `comp/info?id=1542`，既不是 VSB 的 `info/{栏目}/{id}.htm`，
     * 也不是「标题 + 日期」——它的**列表页根本没有日期**。
     * 但详情页有比日期值钱得多的东西：
     * ```
     * 比赛时间：2026-08-01 00:00 至 2027-10-31 23:59
     * 报名时间：2026-08-01 00:00 至 2027-10-31 23:59
     * 主办方：全国大学生生命科学竞赛委员会
     * ```
     * 所以这里只负责「把 id 和标题拿到」，日期交给 [parseCxcyDetail] 在抓详情时补。
     *
     * 列表页免登录（实测 2026-11-11），一页 7 条，翻页 `?page=N`。
     */
    fun parseListCxcy(
        html: String,
        baseUrl: String,
        source: FeedSource,
        now: Long = System.currentTimeMillis(),
    ): List<FeedItem> {
        val out = LinkedHashMap<String, FeedItem>()
        for (m in RE_CXCY_LINK.findAll(html)) {
            val id = m.groupValues[1]
            val title = cleanTitle(m.groupValues[2])
            if (id.isBlank() || title.length < 4) continue

            val key = "${source.key}|$id"
            if (out.containsKey(key)) continue

            val abs = resolve(baseUrl, "../comp/info?id=$id")
            out[key] = FeedItem(
                key = key,
                sourceKey = source.key,
                sourceName = source.name,
                category = source.category,
                title = title,
                url = abs,
                // 日期未知：抓详情时用「报名开始日」补上
                publishedAt = "",
                firstSeenAt = now,
            )
        }
        return out.values.toList()
    }

    private val RE_CXCY_LINK = Regex(
        """<a[^>]+href="[^"]*comp/info\?id=(\d+)"[^>]*title="([^"]+)"""",
        RegexOption.IGNORE_CASE,
    )

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
        val mark = html.indexOf("id=\"vsb_content\"")
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


        var text = htmlToText(body)
        // 切割点可能落在标签中间，去掉末尾那半个标签（形如 `<div`）
        text = text.replace(Regex("""<[^<>]*$"""), "").trim()

        return BodyPayload(
            text = if (text.length < 5) "" else text,
            links = links.values.take(40).toList(),
            attachments = attachments.values.take(20).toList(),
        )
    }

    /** 是不是附件（下载链接 / 常见文档后缀 / PDF） */
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

    fun cleanTitle(s: String): String = s
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
