package cn.dkarbon.enroute.data.faculty

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.feed.FeedHttp
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 学院官网公开资料的抓取编排。
 *
 * ## 只在用户点开某位教师时抓这一位
 * 不做全站遍历、不做批量——票 05 §6 的合规边界。
 *
 * ## 流程
 * 1. 缓存命中且未过期 → 直接返回（[FacultyResult.fromCache]），不发任何请求；
 * 2. 否则：按教务院系找官网站点 → 在师资栏目页按**姓名**找详情链接 → 抓详情页 → 抽正文与字段；
 * 3. 抓到就落盘；**抓不到但手里有旧缓存** → 返回旧缓存并标 [FacultyResult.stale]（不清空、不假装最新）。
 *
 * 失败分类见 [FacultyFail]，界面据此说三句不同的话，不允许混成一句「暂无信息」。
 */
object FacultyRepository {

    private val CN = Regex("""[\u4e00-\u9fa5]""")

    /** 站群页面可能是 GBK，`FeedHttp` 会按 header/meta 探测编码 —— 这里只挑超时 */
    private const val TIMEOUT_MS = 12_000

    /**
     * 一次查询最多打几个栏目页（含翻页与兄弟栏目）。
     *
     * 只为**一位老师**、**用户点开时**才跑，所以给一个硬上限：即便某个院把师资栏目
     * 拆成十几页，也不会变成爬站。超出上限就按已有结果说实话（找不到 / 网络不行）。
     */
    private const val MAX_PAGES = 10

    /**
     * 读一位老师的官网公开信息（阻塞调用，必须在 IO 线程里跑）。
     *
     * @param dept 教务侧的 `departmentName`（`土环` / `农学` …），用来选站
     */
    fun load(
        ctx: Context,
        teacherNo: String,
        name: String,
        dept: String,
        forceRefresh: Boolean = false,
        now: OffsetDateTime = OffsetDateTime.now(),
    ): FacultyResult {
        val source = FacultySources.of(dept)
        val store = FacultyStore(ctx)
        // 先按工号找缓存；没有工号时用「姓名@域名」哈希，省得同一位老师两条缓存
        val host = source?.host.orEmpty()
        val key = FacultyCache.key(teacherNo, name, host)
        val cached = if (key.isBlank()) null else store.read(key)

        if (cached != null && !forceRefresh && FacultyCache.isFresh(cached.fetchedAt, now)) {
            return FacultyResult(info = cached, fromCache = true)
        }
        if (source == null) {
            return if (cached != null) {
                FacultyResult(info = cached, fail = FacultyFail.NoSource, stale = true)
            } else {
                FacultyResult(fail = FacultyFail.NoSource)
            }
        }
        if (name.isBlank()) {
            return FacultyResult(fail = FacultyFail.NotFound)
        }

        val fetchedAt = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        var sawNetworkError = false
        val candidates = mutableListOf<String>()
        var sawColumn = false

        // 待探页面队列：配置里的栏目打头，页内自己给出的「翻页」与「兄弟栏目」追加在后。
        // 这样配置只写一个入口也不会漏掉同组栏目（正高/副高/教师系列是同一个下拉），
        // 也不必为每个院把每页都写进表里 —— 链接是页面给的，不是我们猜的。
        val queue = ArrayDeque<String>()
        source.columns.forEach { queue.addLast(source.columnUrl(it)) }
        val visited = mutableSetOf<String>()
        var budget = MAX_PAGES

        while (queue.isNotEmpty() && budget-- > 0) {
            val listUrl = queue.removeFirst()
            if (!visited.add(listUrl)) continue
            val listPage = runCatching { FeedHttp.get(listUrl, TIMEOUT_MS) }.getOrNull()
            if (listPage == null) { sawNetworkError = true; continue }
            if (listPage.status !in 200..299) { sawNetworkError = sawNetworkError || listPage.status < 0; continue }
            sawColumn = true
            val links = FacultyParser.findDetailLinks(listPage.body, name, listPage.finalUrl)
            if (links.isEmpty()) {
                // 这一页没找到：把它自己的翻页和同目录兄弟栏目排进队列继续找
                queue += FacultyParser.pagination(listPage.body, listPage.finalUrl)
                queue += FacultyParser.siblingColumns(listPage.body, listPage.finalUrl)
                continue
            }
            candidates += links
            if (candidates.size > 1) return FacultyResult(fail = FacultyFail.Ambiguous, candidates = candidates)

            val detail = runCatching { FeedHttp.get(links.first(), TIMEOUT_MS) }.getOrNull()
            if (detail == null || detail.status !in 200..299) {
                sawNetworkError = true
                queue += FacultyParser.pagination(listPage.body, listPage.finalUrl)
                continue
            }
            val info = FacultyParser.parseDetail(
                html = detail.body,
                name = name,
                key = key,
                dept = dept,
                collegeName = source.college,
                sourceUrl = detail.finalUrl,
                sourceHost = source.host,
                fetchedAt = fetchedAt,
            )
            if (info == null) {
                DebugLog.error("FACULTY", "详情页抽不出正文：$listUrl（工号=$key）")
                // 详情页抽不出东西：同组的其它栏目里也许有另一位同名的页面，接着找
                queue += FacultyParser.siblingColumns(listPage.body, listPage.finalUrl)
                continue
            }
            val saved = store.write(info)
            store.trim()
            // 图片按需下到本地（失败不影响文字；下次点开再补）
            val pics = runCatching { FacultyImages.ensure(ctx, info.images) }.getOrElse { emptyList() }
            DebugLog.log(
                "FACULTY",
                "官网抓取成功：${source.college} / 容器=${info.template} / 字段=" +
                    info.fields.rows().map { it.first } + " / 图=${pics.size} / 落盘=$saved",
            )
            return FacultyResult(info = info)
        }

        val fail = when {
            sawNetworkError && !sawColumn -> FacultyFail.Network
            candidates.isNotEmpty() -> FacultyFail.NotParsed
            sawNetworkError -> FacultyFail.Network
            else -> FacultyFail.NotFound
        }
        DebugLog.log("FACULTY", "官网抓取失败：$fail（${source.college} / 工号=$key）")
        return if (cached != null) {
            FacultyResult(info = cached, fail = fail, stale = true, candidates = candidates)
        } else {
            FacultyResult(fail = fail, candidates = candidates)
        }
    }
}
