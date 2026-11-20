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
     * 只能在校内网（或校园 WebVPN / mangoVPN）里访问的源。
     * 校外时这些源不是「失败」，而是**没路** —— 刷新时直接跳过，界面说「需校园网」，
     * 别让 9 个源的 connect timeout 把整条刷新拖成一片红（2026-11-20 实测：校外 IPv6 也进不去）。
     */
    val campusOnly: Boolean = false,
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
    /**
     * 翻页时要抓的额外页（完整 URL，按顺序串行抓，页间 400ms）。
     *
     * 只给**翻页语义已实测确认**的源用：创新创业平台 `?page=N` 正序、每页 7 条。
     * 博达站群的倒序分页（`cxcyjs/6.htm`→`/1.htm`）语义不统一，不在此列。
     */
    val extraPages: List<String> = emptyList(),
    /**
     * 日期在链接**前面**时打开（2026-11-15 新增）。
     *
     * 通用解析器默认只在链接结束后 300 字内找日期。但有一类站点的列表是
     * `<li><h6><span class="time">2026-08-27</span><a href="…">标题</a></h6></li>`
     * —— 日期排在标题之前，正向找不到就该往前看。
     *
     * 默认 `false`：只有实测确认「日期在前」的源才开，否则会有"拿到上一条日期"的风险。
     * 打开后仍**先正向找**，正向找不到才回看链接前 200 字并取其中**最后一个**日期。
     * 实测：服务外包大赛（fwwb）首页是这个结构。
     */
    val dateLookback: Boolean = false,
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
    /** 报名开始日（竞赛平台列表/详情的结构化字段；其它源为空） */
    val regStart: String = "",
    /** 比赛开始/结束日（竞赛平台详情字段；列表拿不到就空） */
    val contestStart: String = "",
    val contestEnd: String = "",
    /** 主办方（竞赛平台列表自带；其它源为空） */
    val organizer: String = "",
    /** 平台自报状态原文：`比赛中` / `已结束`（只转述，不自己推算） */
    val platformState: String = "",
)

/** 一次同步的结果 */
@Serializable
data class SyncReport(
    val perSource: List<SourceResult> = emptyList(),
    val at: Long = 0,
    /** 本次刷新总耗时（ms，票 20 / D1）；0 = 未计时 */
    val elapsedMs: Long = 0,
) {
    val totalNew: Int get() = perSource.sumOf { it.added }
    val failed: Int get() = perSource.count { it.error != null }
    val skipped: Int get() = perSource.count { it.skipped != null }
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
    /** 非失败：本次没抓（如 [FeedSource.campusOnly] 在校外）。有值 = 跳过原因，不进「失败」口径 */
    val skipped: String? = null,
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
            campusOnly = true,
        ),
        FeedSource(
            key = "www",
            name = "校级通知",
            listUrl = "https://www.syau.edu.cn/index/tzgg.htm",
            category = "通知",
            campusOnly = true,
        ),
        FeedSource(
            key = "xsc",
            name = "学生处",
            listUrl = "https://xsc.syau.edu.cn/tzgg.htm",
            category = "学工",
            campusOnly = true,
        ),
        FeedSource(
            key = "news",
            name = "沈农新闻",
            listUrl = "https://news.syau.edu.cn/index.htm",
            category = "新闻",
            campusOnly = true,
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
            campusOnly = true,
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
        // 2026-11-13 实测升级：**列表页本身就带**「报名时间：2026.08.01至2027.10.31」、
        // 主办方、平台状态（比赛中/已结束）——不再需要逐条进详情补日期。
        // 翻页 ?page=N 正序、每页 7 条、共 993 条；只抓前 3 页（21 条）覆盖进行中的赛，
        // 更老的多为已结束，不放大请求量。
        FeedSource(
            key = "cxcycomp",
            name = "竞赛平台",
            listUrl = "http://cxcy.syau.edu.cn/comp/front/comp/list",
            category = "竞赛",
            campusOnly = true,
            kind = "cxcy",
            extraPages = listOf(
                "http://cxcy.syau.edu.cn/comp/front/comp/list?page=2",
                "http://cxcy.syau.edu.cn/comp/front/comp/list?page=3",
            ),
        ),
        // 同一平台的「大创项目」列表：大学生创新训练计划立项（每年 5-6 月报名）。
        // 结构与竞赛列表同族（dcplan/info?id=），报名时间同样在列表页。
        FeedSource(
            key = "cxcydc",
            name = "大创项目",
            listUrl = "http://cxcy.syau.edu.cn/dcplan/front/dcplan/list",
            category = "竞赛",
            campusOnly = true,
            kind = "cxcy",
        ),
        // 同一平台的「成果公示」：获奖名单/获奖作品展（日期在列表左侧 day+year 块）。
        // 注意真实路径是 /comp/front/comp/articlelist?cid=12，
        // 文档里旧地址 /comp/articlelist?cid=12 实测 404（2026-11-13）。
        FeedSource(
            key = "cxcyart",
            name = "竞赛成果",
            listUrl = "http://cxcy.syau.edu.cn/comp/front/comp/articlelist?cid=12",
            category = "竞赛",
            campusOnly = true,
            kind = "cxcy",
        ),
        // 挑战杯官网「挑战杯动态」：/article/{id}/，不是 VSB，走 generic 解析。
        // 站点偶尔在同一列表里混进活动页，用 urlInclude 只留 /article/ 下面的。
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

        // ── 2026-11-13 新增：打破信息差的三块拼图 ─────────────
        // 校团委「通知公告」：校级活动赛事（演讲/短视频/社团科创）。
        // 日期被拆成 <b>日</b>+<span>年-月</span> 两段，走专用 tw 解析族；
        // 列表 <p> 自带正文首段摘要，不用进详情就有摘要。
        FeedSource(
            key = "tw",
            name = "校团委",
            listUrl = "https://tw.syau.edu.cn/channel.jsp?urltype=tree.TreeTempUrl&wbtreeid=1053",
            category = "通知",
            campusOnly = true,
            kind = "tw",
        ),
        // 中国高等教育学会「业务公告」：全国权威口径（竞赛目录/榜单赛事的出处）。
        // 日期拆成 <span class=date>日</span>+<span class=year>年/月</span>，标题在 <h6>。
        // 什么都发（招聘/课题/声明），标题白名单只留竞赛相关。
        FeedSource(
            key = "cahe",
            name = "高教学会",
            listUrl = "https://www.cahe.edu.cn/site/term/373.html",
            category = "竞赛",
            kind = "cahe",
            titleInclude = listOf("大赛", "竞赛", "挑战杯", "创新大赛", "华灿", "教学创新"),
        ),
        // 我爱竞赛网「榜单竞赛」：教育部榜单赛事的商业聚合视角，补全国覆盖面。
        // ⚠️ GB18030 编码（FeedHttp 已按 charset 探测）；列表无日期 → 日期留空，
        // 截止日只在标题里写明（如【11月13日截止报名】）时由解析器抽出；
        // 文章真实地址在站点根（article-NNNNN-1.html），不在分类目录下。
        FeedSource(
            key = "jingsai",
            name = "我爱竞赛网",
            listUrl = "https://www.52jingsai.com/bisai/xkjn/bangdan/",
            category = "竞赛",
            kind = "jingsai",
        ),

        // ── 证书考证（2026-11-15 新增，实测见 docs/竞赛证书信息源-探测报告.md）──
        // 四家都是中国教育考试网（neea）体系：同一套政府 CMS，列表结构一致 ——
        //   <li><span id='ReportIDname'><a href='/html1/report/…' title="标题">
        //   </span><span id='ReportIDIssueTime'>2026-11-10</span></li>
        // 标题在 title 属性里、锚文本可能为空（和校内水利学院那种一样），通用解析器已按此取值。
        // robots: 四个子域全部 404（无限制），详情页不抓（列表已含标题+日期+链接）。
        FeedSource(
            key = "ncre",
            name = "计算机等级考试",
            listUrl = "https://ncre.neea.edu.cn/html1/category/1507/872-1.htm",
            category = "证书",
            kind = "generic",
            urlInclude = "/html1/report/",
        ),
        FeedSource(
            key = "cet",
            name = "四六级考试",
            listUrl = "https://cet.neea.edu.cn/html1/category/16093/1124-1.htm",
            category = "证书",
            kind = "generic",
            urlInclude = "/html1/report/",
        ),
        FeedSource(
            key = "ntce",
            name = "教师资格考试",
            listUrl = "https://ntce.neea.edu.cn/html1/category/1507/1148-1.htm",
            category = "证书",
            kind = "generic",
            urlInclude = "/html1/report/",
        ),
        FeedSource(
            key = "neea_gs",
            name = "教育考试院公告",
            listUrl = "https://www.neea.edu.cn/html1/category/1508/151-1.htm",
            category = "证书",
            kind = "generic",
            urlInclude = "/html1/report/",
        ),
        // 中国人事考试网：专业技术人员职业资格考试（初级会计/经济师这类在校生能报的）。
        // 列表 <a title="…">标题</a><i>[2026-02-28]</i>，日期在链接之后 —— 正向可找。
        FeedSource(
            key = "cpta",
            name = "人事考试网",
            listUrl = "http://www.cpta.com.cn/notice.html",
            category = "证书",
            kind = "generic",
            urlInclude = "/notice/",
        ),
        // 服务外包创新创业大赛：日期在**链接之前**（<span class="time">），需回看。
        // 这是唯一一家实测确认 `dateLookback` 的源；首页即列表（/news/index.html 是空壳）。
        FeedSource(
            key = "fwwb",
            name = "服务外包大赛",
            listUrl = "https://www.fwwb.org.cn/",
            category = "竞赛",
            kind = "generic",
            urlInclude = "/news/show/",
            dateLookback = true,
        ),
    )

    fun byKey(key: String): FeedSource? = ALL.firstOrNull { it.key == key }

    /**
     * 频道顺序（2026-11-11 加「竞赛」）。
     *
     * 排前面的先被点开：竞赛 → 教务 → 通知 → 学工 → 新闻。
     * 这里的字符串同时是 [FeedItem.category] 的取值，改动要同步 [FeedSource.category]。
     */
    val CATEGORIES: List<String> = listOf("全部", "竞赛", "证书", "教务", "就业", "通知", "学工", "新闻")
}
