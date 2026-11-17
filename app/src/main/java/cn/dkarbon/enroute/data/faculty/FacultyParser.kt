package cn.dkarbon.enroute.data.faculty

/**
 * 官网正文抽取与字段识别 —— **全是纯函数**，用 `docs/_probe/faculty/raw/` 的真实页面做单测夹具。
 *
 * ## 为什么要有「多级容器兜底」
 * 全校院系站是同一套 VSB 站群，但模板有好几代：
 * `vsb_content` / `vsb_content_xxx` / `v_news_content` / `WspDetailContent` / `WspDetail`，
 * 而且**有的页面一个容器都没命中**（实测 `det_jgxy_zhoumi`），正文散在整页 `<p>` 里。
 * 只用「抓 vsb_content」的朴素做法会把这些页判成「没信息」。
 *
 * ## 为什么要有「文本量阈值」
 * 命中容器 ≠ 拿到正文：有的页容器里只有一张照片 + 姓名。低于 [MIN_ZH_CHARS] 个汉字就**认定失败**，
 * 让界面上说「官网没解析到」，而不是显示一张空卡片（票 05 §3 的结论）。
 */
object FacultyParser {

    /** 正文至少要这么多个汉字才算「抓到了」（实测最少的一份是 382 字） */
    const val MIN_ZH_CHARS = 120

    /** 正文纯文本截断上限（缓存体积上限见 [FacultyCache]） */
    const val RAW_TEXT_LIMIT = 8000

    /** 每个小节的正文截断上限 */
    const val SECTION_TEXT_LIMIT = 2000

    private val ZH = Regex("""[\u4e00-\u9fa5]""")

    /** 容器候选，**按命中率从高到低**试（顺序即兜底顺序，别随意调）；名字进缓存便于排障 */
    private val CONTAINERS: List<Pair<String, Regex>> = listOf(
        "vsb_content" to Regex("""id\s*=\s*"vsb_content[^"]*"""", RegexOption.IGNORE_CASE),
        "v_news_content" to Regex("""class\s*=\s*"v_news_content[^"]*"""", RegexOption.IGNORE_CASE),
        "WspDetailContent" to Regex("""id\s*=\s*"WspDetailContent"""", RegexOption.IGNORE_CASE),
        "WspDetailContent(class)" to Regex("""class\s*=\s*"WspDetailContent[^"]*"""", RegexOption.IGNORE_CASE),
        "WspDetail" to Regex("""id\s*=\s*"WspDetail"""", RegexOption.IGNORE_CASE),
    )

    /** 到这个位置就认为正文结束（页脚/投票/二维码），避免把导航与版权塞进正文 */
    private val TAIL_MARKERS = listOf(
        """id="div_vote_id"""",
        """class="footer"""",
        """id="footer"""",
        """id="qr_box"""",
    )

    private const val REGION_LIMIT = 40_000

    /** 一次抽取的结果：正文 + 命中的容器名（`whole_page` = 整页兜底） */
    data class Body(val container: String, val text: String) {
        val zhChars: Int get() = ZH.findAll(text).count()
        val usable: Boolean get() = zhChars >= MIN_ZH_CHARS
    }

    /**
     * 抽正文：五个容器挨个试，取「汉字最多的那个」；都不够就退到整页。
     *
     * @return 命中且够长 → [Body]；整页都抽不出东西 → null（调用方说「没解析到」）
     */
    fun extractBody(html: String): Body? {
        if (html.isBlank()) return null
        var best: Body? = null
        CONTAINERS.forEach { (label, re) ->
            val m = re.find(html) ?: return@forEach
            val region = regionFrom(html, m.range.last + 1)
            val body = Body(label, toText(region))
            if (body.zhChars > (best?.zhChars ?: -1)) best = body
        }
        if (best != null && best!!.usable) return best
        // 兜底：整页文字（`det_jgxy_zhoumi` 这种容器全没命中的页面靠它）
        val whole = Body("whole_page", toText(html))
        return when {
            whole.usable -> whole
            best != null && (best?.zhChars ?: 0) > 0 -> best   // 有字但太少：交回去，由 usable 判定失败
            else -> null
        }
    }

    /** 从容器标记往后切一段，遇到页脚标记就停 */
    private fun regionFrom(html: String, start: Int): String {
        val end = TAIL_MARKERS
            .map { marker -> html.indexOf(marker, start).let { if (it < 0) Int.MAX_VALUE else it } }
            .minOrNull() ?: Int.MAX_VALUE
        val stop = minOf(end, html.length, start + REGION_LIMIT)
        return if (start >= stop) "" else html.substring(start, stop)
    }

    /**
     * HTML 片段 → 纯文本。
     *
     * 逐条都是踩过的坑：
     * - 先干掉 `<script>` / `<style>` / 注释，否则 JS 里的中文会混进正文；
     * - `</p>` `</div>` `</li>` `</tr>` `<br>` → 换行，`<td>` → ` | `（VSB 的表格字段是一格一值）；
     * - `&nbsp;` 与全角空格要清，否则字段抽取正则会漏；
     * - 长度 < 2 的行丢掉（多半是标点或单个空格）。
     */
    fun toText(fragment: String): String {
        if (fragment.isBlank()) return ""
        var s = fragment
        s = Regex("""(?is)<(script|style)[^>]*>.*?</\1>""").replace(s, " ")
        s = Regex("""(?s)<!--.*?-->""").replace(s, " ")
        s = Regex("""(?i)</(p|div|li|tr|h1|h2|h3|table)>""").replace(s, "\n")
        s = Regex("""(?i)<br\s*/?>""").replace(s, "\n")
        s = Regex("""(?i)</td>""").replace(s, " | ")
        s = Regex("""(?s)<[^>]+>""").replace(s, " ")
        s = unescape(s).replace('\u00a0', ' ')
        return s.split('\n')
            .map { it.replace(Regex("""[ \t]+"""), " ").trim().trim('|').trim() }
            .filter { it.length >= 2 }
            .joinToString("\n")
    }

    private fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&mdash;", "—")

    /**
     * 字段抽取（纯函数）。识别不到就给空串；**绝不拿教务字段冒充官网字段**。
     */
    fun fieldsOf(text: String, name: String = ""): FacultyFields {
        val flat = text.replace('\n', ' ')
        fun first(re: Regex): String = re.find(flat)?.let { it.groupValues.lastOrNull().orEmpty().trim() }.orEmpty()

        // 职称：`副教授` 必须排在 `教授` 前面，否则一律识别成教授
        val title = first(
            Regex("""(副教授|副研究员|高级实验师|教授|研究员|讲师|助教|实验师|工程师|馆员)"""),
        )
        val degree = first(Regex("""(博士研究生|博士|硕士研究生|硕士|研究生|本科|学士)"""))
        val supervisor = first(Regex("""(博士生导师|硕士生导师|博导|硕导)"""))
        val role = first(
            Regex("""(副院长|副主任|副所长|院长|系主任|所长|中心主任|教研室主任|党支部书记|学科带头人)"""),
        )
        val research = listOf(
            Regex("""(?:主要研究方向|研究方向|研究领域|研究兴趣)\s*[：:是为]?\s*([^\n。；]{2,150})"""),
            Regex("""主要从事\s*([^\n。；]{2,150})"""),
            Regex("""主要研究\s*([^\n。；]{2,150})"""),
        ).firstNotNullOfOrNull { re -> re.find(flat)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } }
            .orEmpty()
        val office = listOf(
            Regex("""(?:办公地点|办公室|办公地址)\s*[：:]?\s*([^\n。；，,]{2,40})"""),
        ).firstNotNullOfOrNull { re -> re.find(flat)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } }
            .orEmpty()
        // 只留学院的公务电话（024-8xxxxxxx），手机号一律不算
        val phone = Regex("""024[-\s]?(\d{7,8})""").find(flat)
            ?.let { "024-" + it.groupValues[1] }.orEmpty()
        // 只留校方邮箱；163/qq/sina 之类属私人联系方式，抽的时候就丢掉
        val email = Regex("""[A-Za-z0-9._%+\-]+@syau\.edu\.cn""", RegexOption.IGNORE_CASE)
            .find(flat)?.value.orEmpty()
        val sex = Regex("""[，,]\s*(男|女)\s*[，,]""").find(flat)?.groupValues?.get(1).orEmpty()
        val birth = Regex("""(\d{4})\s*年\s*(\d{1,2})\s*月\s*生""").find(flat)
            ?.let { "${it.groupValues[1]} 年 ${it.groupValues[2]} 月" }.orEmpty()

        return FacultyFields(
            title = title,
            degree = degree,
            supervisor = supervisor,
            research = FacultyPrivacy.scrub(research).take(200),
            role = role,
            office = FacultyPrivacy.scrub(office),
            phone = phone,
            email = email,
            sex = sex,
            birth = birth,
        )
    }

    /**
     * 长文本按小节切（`教育经历：` / `工作经历：` 这种小标题起头）。
     *
     * 只用在同一行就是「短标题+冒号」的形态；切不出小节也不影响主字段。
     */
    fun sectionsOf(text: String, maxSections: Int = 8): List<FacultySection> {
        val header = Regex("""^([\u4e00-\u9fa5A-Za-z（）()·\s]{2,14})[：:]\s*$""")
        val out = mutableListOf<FacultySection>()
        var title: String? = null
        val buf = StringBuilder()
        fun flush() {
            val body = buf.toString().trim()
            if (!title.isNullOrBlank() && body.length >= 20) {
                out += FacultySection(
                    title = FacultyPrivacy.scrub(title!!),
                    text = FacultyPrivacy.scrub(body).take(SECTION_TEXT_LIMIT),
                )
            }
            buf.setLength(0)
        }
        text.split('\n').forEach { raw ->
            val line = raw.trim()
            val m = header.matchEntire(line)
            if (m != null) {
                flush()
                title = m.groupValues[1].trim()
            } else if (title != null) {
                buf.append(line).append('\n')
            }
        }
        flush()
        return out.take(maxSections)
    }

    /**
     * 在师资栏目页里按姓名找详情页链接。
     *
     * 只认「链接文字 == 姓名」的短文本锚点（VSB 列表里姓名就是锚点文字），
     * 返回**绝对地址**列表：命中多个说明重名，调用方**不许猜**，直接把候选给用户。
     */
    fun findDetailLinks(listHtml: String, name: String, baseUrl: String): List<String> {
        val target = name.trim()
        if (target.isBlank()) return emptyList()
        val anchor = Regex("""(?is)<a\b[^>]*href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""")
        return anchor.findAll(listHtml).mapNotNull { m ->
            val label = toText(m.groupValues[2]).replace("\n", "").trim()
            if (label != target) return@mapNotNull null
            val href = m.groupValues[1].trim()
            if (!href.contains("/info/") && !href.contains("content.jsp")) return@mapNotNull null
            resolveUrl(baseUrl, href)
        }.distinct().toList()
    }

    /**
     * 兄弟栏目：师资栏目页的导航里就带着同目录的其它栏目
     * （`szdw/jsxl/zgjzc.htm` 这页上能点到 `fgjzc.htm`、`js.htm`）。
     *
     * 只用页面自己给出的链接，**不猜路径** —— 所以「一院一配」不必把每个职称栏都写全。
     * 只认同目录（同 `substringBeforeLast('/')`）的 `.htm`；导航里指向 `../../xygk/…`
     * 的那些天然被排除。
     */
    fun siblingColumns(listHtml: String, listUrl: String, limit: Int = 6): List<String> {
        val self = listUrl.substringBefore('#').substringBefore('?')
        val dir = self.substringBeforeLast('/', "")
        if (dir.isEmpty() || dir == self) return emptyList()
        val anchor = Regex("""(?is)<a\b[^>]*href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""")
        return anchor.findAll(listHtml).mapNotNull { m ->
            val href = m.groupValues[1].trim()
            if (href.contains('?') || href.startsWith("#")) return@mapNotNull null
            val url = resolveUrl(self, href).substringBefore('#').substringBefore('?')
            if (!url.endsWith(".htm") || url == self) return@mapNotNull null
            if (url.substringBeforeLast('/') != dir) return@mapNotNull null
            val file = url.substringAfterLast('/')
            // 同目录下的翻页（`fgjzc/1.htm`）不在这里返回，交给 pagination()
            if (file.equals("index.htm", true)) return@mapNotNull null
            url
        }.distinct().take(limit).toList()
    }

    /**
     * 栏目自身的翻页：`…/fgjzc.htm` 页底给的 `fgjzc/1.htm`、`fgjzc/2.htm`。
     *
     * 只认「以本栏目文件名当目录名」下的 `数字.htm`，按页码升序。
     * 土环的副高栏就这样分成两页 —— 不翻页会漏掉半栏人（蒋卓东在第 2 页）。
     */
    fun pagination(listHtml: String, listUrl: String, limit: Int = 8): List<String> {
        val self = listUrl.substringBefore('#').substringBefore('?')
        if (!self.endsWith(".htm")) return emptyList()
        val prefix = self.removeSuffix(".htm") + "/"
        val anchor = Regex("""(?is)<a\b[^>]*href\s*=\s*"([^"]+)"[^>]*>(.*?)</a>""")
        return anchor.findAll(listHtml).mapNotNull { m ->
            val url = resolveUrl(self, m.groupValues[1].trim()).substringBefore('#').substringBefore('?')
            if (!url.startsWith(prefix)) return@mapNotNull null
            val file = url.removePrefix(prefix)
            if (!Regex("""\d+\.htm""").matches(file)) return@mapNotNull null
            file.removeSuffix(".htm").toInt() to url
        }.distinctBy { it.second }
            .sortedBy { it.first }
            .map { it.second }
            .take(limit)
            .toList()
    }

    /** 相对地址 → 绝对地址（`../../info/1611/4929.htm` → `https://thxy.syau.edu.cn/info/1611/4929.htm`） */
    fun resolveUrl(baseUrl: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return runCatching { java.net.URI(baseUrl).resolve(href).toString() }.getOrDefault(href)
    }

    /**
     * 整页解析：抽正文 → 抽字段 → 切小节。
     *
     * @return `null` = 这页没有可用正文（只有图/只有姓名）→ 界面说「官网没解析到」
     */
    fun parseDetail(
        html: String,
        name: String,
        key: String,
        dept: String,
        collegeName: String,
        sourceUrl: String,
        sourceHost: String,
        fetchedAt: String,
    ): FacultyInfo? {
        val body = extractBody(html) ?: return null
        if (!body.usable) return null
        val fields = fieldsOf(body.text, name)
        val sections = sectionsOf(body.text)
        return FacultyInfo(
            key = key,
            name = name,
            dept = dept,
            collegeName = collegeName,
            sourceUrl = sourceUrl,
            sourceHost = sourceHost,
            fetchedAt = fetchedAt,
            template = body.container,
            fields = fields,
            sections = sections,
            rawText = FacultyPrivacy.scrub(body.text).take(RAW_TEXT_LIMIT),
        )
    }
}

/**
 * 隐私清洗（票 05 §6 的合规红线）。
 *
 * 抽取阶段就要做，**不是**界面层遮一下 —— 清不掉的东西会进缓存、进日志。
 * 三类一律丢：个人手机号、QQ 号、非校方邮箱（163/qq/sina…）。
 */
object FacultyPrivacy {

    private val MOBILE = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
    private val QQ = Regex("""(?i)QQ\s*[:：]?\s*\d{5,12}""")
    private val PRIVATE_MAIL = Regex(
        """[A-Za-z0-9._%+\-]+@(?!syau\.edu\.cn)[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""",
        RegexOption.IGNORE_CASE,
    )

    fun scrub(text: String): String {
        if (text.isBlank()) return text
        var s = MOBILE.replace(text, "")
        s = QQ.replace(s, "")
        s = PRIVATE_MAIL.replace(s, "")
        return s.replace(Regex("""[ \t]{2,}"""), " ").trim()
    }

    /** 单元测试与自检用：这段文本里还残留隐私吗 */
    fun containsPrivate(text: String): Boolean =
        MOBILE.containsMatchIn(text) || QQ.containsMatchIn(text) || PRIVATE_MAIL.containsMatchIn(text)
}
