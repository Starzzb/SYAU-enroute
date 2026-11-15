package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.feed.FeedParser
import cn.dkarbon.enroute.data.feed.FeedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「证书考证 + 全国竞赛」新源的回归测试（2026-11-15 新增）。
 *
 * 样本全部是**真实页面存下来的**（`src/test/resources/feed/`，2026-11-15 抓取），
 * 实测证据见 `docs/竞赛证书信息源-探测报告.md`。
 *
 *   · `ncre.html`    中国教育考试网 · 全国计算机等级考试公告
 *   · `cet.html`     中国教育考试网 · 四六级考试公告
 *   · `ntce.html`    中国教育考试网 · 中小学教师资格考试公告
 *   · `neea_gs.html` 中国教育考试网 · 公示公告
 *   · `cpta.html`    中国人事考试网 · 通知公告
 *   · `fwwb.html`    中国大学生服务外包创新创业大赛 · 首页（日期在链接**之前**）
 *
 * 这套源的三个坑，每个都有对应断言：
 *   1. 标题在 `title` 属性里、锚文本为空（政府 CMS 通病）→ 必须拿到标题而不是空串
 *   2. `title` 里带编码过的换行 `&#60;br>` → 必须清成空格，不能留在标题里
 *   3. fwwb 的日期在链接之前 → 靠 `dateLookback` 回看，关掉就取不到（反向验证）
 */
class CertFeedSourcesTest {

    private fun html(name: String): String =
        javaClass.getResourceAsStream("/feed/$name")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private fun source(key: String) = FeedSources.byKey(key)!!

    private val dateRe = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** 所有新源的通用断言：条目数、主键不塌缩、日期格式、链接前缀、标题干净 */
    private fun assertHealthy(htmlName: String, key: String, minCount: Int = 5) {
        val src = source(key)
        val items = FeedParser.parseListGeneric(html(htmlName), src.listUrl, src)

        assertTrue("$key 只解析出 ${items.size} 条，主键可能塌缩或选择器失效", items.size >= minCount)
        assertEquals("$key 主键有重复", items.size, items.map { it.key }.toSet().size)

        items.forEach { it0 ->
            assertTrue("$key 日期格式不对：${it0.publishedAt}", dateRe.matches(it0.publishedAt))
            assertTrue("$key 链接不在白名单路径内：${it0.url}", it0.url.contains(src.urlInclude))
            assertTrue("$key 标题过短：'${it0.title}'", it0.title.length >= 8)
            // 坑 1 + 坑 2：标题必须真拿到，且没有残留的实体/标签
            assertTrue("$key 标题残留 HTML 实体：'${it0.title}'", !it0.title.contains("&#"))
            assertTrue("$key 标题残留标签：'${it0.title}'", !it0.title.contains("<br"))
        }
        assertTrue("$key 条目没按日期倒序", items.zipWithNext().all { it.first.publishedAt >= it.second.publishedAt })
    }

    // ── 中国教育考试网体系（四家同构）──────────────────────

    @Test
    fun `计算机等级考试公告能解析`() = assertHealthy("ncre.html", "ncre", minCount = 10)

    @Test
    fun `四六级考试公告能解析且标题清掉编码换行`() = assertHealthy("cet.html", "cet", minCount = 10)

    @Test
    fun `教师资格考试公告能解析`() = assertHealthy("ntce.html", "ntce", minCount = 10)

    @Test
    fun `教育考试院公示公告能解析`() = assertHealthy("neea_gs.html", "neea_gs", minCount = 5)

    // ── 人事考试网 ────────────────────────────────────────

    @Test
    fun `人事考试网通知能解析`() = assertHealthy("cpta.html", "cpta", minCount = 5)

    // ── 服务外包大赛（日期在前的唯一一家）──────────────────

    @Test
    fun `服务外包大赛能解析且日期来自回看`() = assertHealthy("fwwb.html", "fwwb", minCount = 3)

    @Test
    fun `关掉回看会把下一条的日期偷过来`() {
        // 反向验证：fwwb 的日期在链接之前。关掉回看后**仍然能解析出条目**，
        // 但日期会整体错位到下一条 —— 这比"取不到"更危险，因为它看起来是正常的。
        // 所以断言必须钉具体日期，不能只数条数。
        val src = source("fwwb")
        val withLookback = FeedParser.parseListGeneric(html("fwwb.html"), src.listUrl, src)
        val noLookback = FeedParser.parseListGeneric(html("fwwb.html"), src.listUrl, src.copy(dateLookback = false))

        val target = "/news/show/663"
        val good = withLookback.first { it.url.endsWith(target) }
        assertEquals("服务外包 663 号文的正解日期应是 2026-08-27", "2026-08-27", good.publishedAt)

        val bad = noLookback.firstOrNull { it.url.endsWith(target) }
        assertTrue("关掉回看后这条本该还在（只是日期错），实际整个丢了", bad != null)
        assertTrue(
            "关掉回看后日期本应错位，实际却还是对的 —— 说明回看逻辑没生效或页面结构变了",
            bad!!.publishedAt != good.publishedAt,
        )
    }

    // ── 源配置本身的一致性 ────────────────────────────────

    @Test
    fun `新源配置都在频道列表里`() {
        val newKeys = listOf("ncre", "cet", "ntce", "neea_gs", "cpta", "fwwb")
        newKeys.forEach { k ->
            val s = FeedSources.byKey(k) ?: throw AssertionError("源 $k 不存在")
            assertTrue("源 $k 的 category='${s.category}' 不在 CATEGORIES 里", s.category in FeedSources.CATEGORIES)
            assertTrue("源 $k 未启用", s.enabled)
            assertEquals("源 $k 应该走 generic 解析族", "generic", s.kind)
            assertTrue("源 $k 缺 urlInclude，会把导航条目混进来", s.urlInclude.isNotBlank())
        }
    }

    @Test
    fun `证书频道在频道列表里`() {
        assertTrue("CATEGORIES 里没有『证书』频道", "证书" in FeedSources.CATEGORIES)
    }
}
