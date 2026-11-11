package cn.dkarbon.enroute.data.feed

import kotlinx.serialization.Serializable

/**
 * 一个资讯源。
 *
 * 栏目 ID 与路径全部实测确认过（2026-11-10），见 `docs/SYAU-信息发布平台清单.md`。
 * ⚠️ `jw.syau.edu.cn` 是**纪委**不是教务（jw=纪委），教务是 `jwc`。
 */
@Serializable
data class FeedSource(
    /** 稳定短键，拼条目主键用 */
    val key: String,
    val name: String,
    /** 列表页地址 */
    val listUrl: String,
    /** 分类：教务 / 通知 / 学工 / 新闻 / 竞赛 */
    val category: String,
    val enabled: Boolean = true,
    /**
     * 解析族：
     *   · `vsb` —— 博达 VSB 站群（校内各子站），认 `info/{栏目}/{文章}.htm`
     *   · `generic` —— 其他站，认「像标题的链接 + 附近日期」
     */
    val kind: String = "vsb",
    /**
     * 标题白名单关键词。非空时，只有标题命中其中任意一个才收录。
     *
     * 用在「什么通知都发」的综合性源上（比如辽宁省教育厅的最新通知，
     * 里面混着教师节、信息化评选这类跟学生竞赛无关的东西）。
     */
    val titleInclude: List<String> = emptyList(),
    /**
     * 链接地址必须包含的子串（非空时生效）。
     *
     * 通用解析器只认「标题 + 日期」，站点的**导航块偶尔也带日期**（活动列表页尤其如此），
     * 这时靠路径前缀把文章和导航分开：挑战杯的文章全是 `/article/{id}/`。
     */
    val urlInclude: String = "",
)

/**
 * 一条资讯。
 *
 * 主键 `key = "源key|栏目ID|文章ID"` —— **不是 URL**。
 * 因为同一篇有两种形态：`/info/1249/10662.htm`（静态页可能没生成 → 404）
 * 和 `content.jsp?…wbtreeid=1249&wbnewsid=10662`（一定可用）。用 URL 当键会重复入库。
 */
@Serializable
data class FeedItem(
    val key: String,
    val sourceKey: String,
    val sourceName: String,
    val category: String,
    val title: String,
    /** ≤100 字摘要：列表页有就用列表的，抓完正文后补成正文前 100 字 */
    val summary: String = "",
    /** 绝对 URL，用于「查看原文」和抓正文 */
    val url: String = "",
    /** 备用动态地址（/info/ 404 时用） */
    val urlAlt: String = "",
    val publishedAt: String = "",
    val firstSeenAt: Long = 0,
    val readAt: Long? = null,
    val starred: Boolean = false,
    /** 标题被 CMS 截断（带 …），抓正文后可补全 */
    val titleTruncated: Boolean = false,
    /** 正文是否已永久存到本地 */
    val bodyFetched: Boolean = false,
    /**
     * 竞赛报名/提交截止日（`yyyy-MM-dd`）。
     *
     * 只有能从正文里**真的解析出日期**才填，解析不到就是空串 —— 界面上宁可不显示倒计时，
     * 也不能编一个日期出来（误了截止时间是真的会误事）。
     */
    val deadline: String = "",
)

/** 一次同步的结果 */
@Serializable
data class SyncReport(
    val perSource: List<SourceResult> = emptyList(),
    val at: Long = 0,
) {
    val totalNew: Int get() = perSource.sumOf { it.added }
    val failed: Int get() = perSource.count { it.error != null }
    fun summary(): String = buildString {
        append("新增 $totalNew 条")
        if (failed > 0) append(" · $failed 个源失败")
    }
}

@Serializable
data class SourceResult(
    val sourceKey: String,
    val fetched: Int = 0,
    val added: Int = 0,
    val error: String? = null,
)

/**
 * 内置源表。
 *
 * 说明：目前是**个人测试构建**，四个源全开（主人 2026-11-10 明确「测试期不担心负载」）。
 * 真要给别的同学用之前，`jwc` / `xsc` 需要先拿到学校授权 —— 它们的 robots.txt
 * 是 `Allow: /$` + `Disallow: /`，事实上禁止全站抓取。
 */
object FeedSources {

    val ALL: List<FeedSource> = listOf(
        FeedSource(
            key = "jwc",
            name = "教务通知",
            listUrl = "https://jwc.syau.edu.cn/index/jwtz.htm",
            category = "教务",
        ),
        FeedSource(
            key = "www",
            name = "校级通知",
            listUrl = "https://www.syau.edu.cn/index/tzgg.htm",
            category = "通知",
        ),
        FeedSource(
            key = "xsc",
            name = "学生处",
            listUrl = "https://xsc.syau.edu.cn/tzgg.htm",
            category = "学工",
        ),
        FeedSource(
            key = "news",
            name = "沈农新闻",
            listUrl = "https://news.syau.edu.cn/index.htm",
            category = "新闻",
        ),

        // 就业信息网（外部 SaaS：江苏「就业宝」jysd，公网免登录）。
        // 首页是服务端渲染的，一页能出 80+ 条带日期条目，走 generic 解析；
        // 只用 `news/view/aid/` 的文章，把导航和职位页筛掉。
        FeedSource(
            key = "jy",
            name = "就业信息网",
            listUrl = "https://jobsyau.jysd.com/",
            category = "就业",
            kind = "generic",
            urlInclude = "/news/view/aid/",
        ),

        // ── 竞赛（2026-11-11 加）─────────────────────────────
        // 校内竞赛通知的实际出口是「学生创新创业与就业指导处」，不是教务处。
        // 实测 2026-11-11：15 条，全是大创/创新大赛/职业规划大赛/标本制作大赛这类
        // 真正要报名的东西，VSB 结构（info/1023/xxxx.htm），老解析器直接吃得下。
        FeedSource(
            key = "job",
            name = "创新创业处",
            listUrl = "https://job.syau.edu.cn/tzgg.htm",
            category = "竞赛",
        ),
        // 同一个站的「创新创业竞赛」栏目 —— 校内竞赛的成果侧（谁拿了什么奖）。
        // 实测 2026-11-11：一页 15 条，info/1040/*，VSB 结构。
        // 注意：这个站的分页是**倒序**的（`cxcyjs/6.htm` → `/1.htm` 是往回翻），
        // 所以只抓第一页就够拿到最新 15 条，不做翻页。
        FeedSource(
            key = "cxcy",
            name = "校内竞赛",
            listUrl = "https://job.syau.edu.cn/cxcy1/cxcyjs.htm",
            category = "竞赛",
        ),
        // 创新创业**管理平台**的竞赛报名列表（内网 10.2.0.110，免登录）。
        // 和其它源的区别：这里能拿到**报名窗口**（`报名时间：A 至 B`），
        // 所以它是唯一能算「还剩几天报名」的源。列表页没有日期，
        // 日期在详情页里 —— 由 FeedRepository 对新条目补抓一次（每轮最多 6 个，间隔 400ms）。
        FeedSource(
            key = "cxcycomp",
            name = "竞赛平台",
            listUrl = "http://cxcy.syau.edu.cn/comp/front/comp/list",
            category = "竞赛",
            kind = "cxcy",
        ),
        // 挑战杯官网「挑战杯动态」：/article/{id}/，不是 VSB，走 generic 解析。        // 站点偶尔在同一列表里混进活动页，用 urlInclude 只留 /article/ 下面的。
        FeedSource(
            key = "tzb",
            name = "挑战杯",
            listUrl = "https://www.tiaozhanbei.net/tzb",
            category = "竞赛",
            kind = "generic",
            urlInclude = "/article/",
        ),
        // 辽宁省教育厅「最新通知」：省级赛事通知的源头（省赛名单就是从这里下发的）。
        // 这个栏目什么都发（教师节、信息化评选…），所以用标题白名单只留竞赛相关。
        FeedSource(
            key = "lnjyt",
            name = "辽宁省教育厅",
            listUrl = "https://jyt.ln.gov.cn/jyt/gk/zxtz/index.shtml",
            category = "竞赛",
            kind = "generic",
            titleInclude = listOf("大赛", "竞赛", "挑战杯", "创新大赛", "职业规划大赛", "学科竞赛", "创新创业"),
        ),
    )

    fun byKey(key: String): FeedSource? = ALL.firstOrNull { it.key == key }

    /**
     * 频道顺序（2026-11-11 加「竞赛」）。
     *
     * 排前面的先被点开：竞赛 → 教务 → 通知 → 学工 → 新闻。
     * 这里的字符串同时是 [FeedItem.category] 的取值，改动要同步 [FeedSource.category]。
     */
    val CATEGORIES: List<String> = listOf("全部", "竞赛", "教务", "就业", "通知", "学工", "新闻")
}
