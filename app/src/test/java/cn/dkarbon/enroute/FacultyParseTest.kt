package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.faculty.FacultyCache
import cn.dkarbon.enroute.data.faculty.FacultyFail
import cn.dkarbon.enroute.data.faculty.FacultyInfo
import cn.dkarbon.enroute.data.faculty.FacultyParser
import cn.dkarbon.enroute.data.faculty.FacultyPrivacy
import cn.dkarbon.enroute.data.faculty.FacultyResult
import cn.dkarbon.enroute.data.faculty.FacultySources
import cn.dkarbon.enroute.ui.home.HomeModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * 学院官网公开资料的抽取、清洗与缓存规则（票 08）。
 *
 * 夹具全部是 2026-11-13 实测的真实页面（`docs/_probe/faculty/raw/` 拷过来），
 * 其中 `det_zbxy_1808` 特意带着手机号 + QQ + 私人邮箱 —— 用它锁「不许落地」。
 */
class FacultyParseTest {

    private fun html(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("faculty/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parse(file: String, name: String, key: String = "1995500023"): FacultyInfo? =
        FacultyParser.parseDetail(
            html = html(file),
            name = name,
            key = key,
            dept = "土环",
            collegeName = "土地与环境学院",
            sourceUrl = "https://thxy.syau.edu.cn/info/1611/4929.htm",
            sourceHost = "thxy.syau.edu.cn",
            fetchedAt = "2026-11-14T10:30:00+08:00",
        )

    // ── 正文抽取（多模板兜底）────────────────────────────

    @Test
    fun `五种模板都能抽出正文`() {
        // vsb_content（土环）、WspDetail+WspDetailContent（植保/生物）、嵌套（林学）
        listOf(
            "det_thxy_wangjingkuan.html",
            "det_zbxy_1808.html",
            "det_swxy_quanbo.html",
            "det_lin_luxiujun.html",
        ).forEach { f ->
            val body = FacultyParser.extractBody(html(f))
            assertNotNull("抽不出正文：$f", body)
            assertTrue("正文太短，判成失败：$f", body!!.usable)
        }
    }

    @Test
    fun `容器全没命中的页面走整页兜底`() {
        // det_jgxy_zhoumi 的实测结论是「容器都没命中」，整页文字仍可用
        val body = FacultyParser.extractBody(html("det_jgxy_zhoumi.html"))
        assertNotNull(body)
        assertEquals("whole_page", body!!.container)
        assertTrue(body.usable)
    }

    @Test
    fun `只有图片和姓名的页面判成没解析到`() {
        val poor = """<html><body><div id="vsb_content">
            <p><img src="/x.jpg"></p><p>张</p></div></body></html>"""
        val body = FacultyParser.extractBody(poor)
        assertTrue("正文只有 1 个汉字，不该算抓到", body == null || !body.usable)
        assertNull(
            "这种页面必须返回 null，界面才会说「没解析到」",
            FacultyParser.parseDetail(poor, "张三", "1", "土环", "土环学院", "https://x", "x", "2026-11-14T00:00:00+08:00"),
        )
    }

    @Test
    fun `乱码与空页面不会抛出异常`() {
        assertNull(FacultyParser.extractBody(""))
        assertNull(FacultyParser.extractBody("   "))
        assertTrue(FacultyParser.extractBody("<html><script>var a='中文中文中文';</script></html>")?.let { !it.usable } ?: true)
    }

    // ── 字段抽取 ─────────────────────────────────────────

    @Test
    fun `副教授不会被认成教授`() {
        assertEquals("副教授", FacultyParser.fieldsOf("汪老师，副教授，硕士生导师，主要从事土壤学研究。").title)
        assertEquals("教授", FacultyParser.fieldsOf("董老师，女，教授，博士生导师。").title)
    }

    @Test
    fun `研究方向能从多种措辞抽出来`() {
        assertTrue(
            FacultyParser.fieldsOf("研究方向：土壤肥力与植物营养").research.contains("土壤肥力"),
        )
        assertTrue(
            FacultyParser.fieldsOf("主要研究方向为森林培育与种质资源").research.contains("森林培育"),
        )
        assertTrue(
            FacultyParser.fieldsOf("主要从事农业资源利用方面的教学与科研").research.contains("农业资源利用"),
        )
    }

    @Test
    fun `真实页面上抽得出职称与研究方向`() {
        val info = parse("det_thxy_wangjingkuan.html", "汪景宽")
        assertNotNull(info)
        assertFalse("一个字段都没抽到", info!!.fields.isEmpty)
        assertTrue("职称应识别出来：${info.fields.title}", info.fields.title.isNotBlank())
        assertEquals("vsb_content", info.template)
        // 来源与时间必须带，否则用户没法判断这是哪年的信息
        assertEquals("2026-11-14T10:30:00+08:00", info.fetchedAt)
        assertTrue(HomeModel.facultySourceLine(info).contains("土地与环境学院"))
        assertTrue(HomeModel.facultySourceLine(info).contains("2026-11-14"))
    }

    @Test
    fun `办公电话只收学校总机格式`() {
        val f = FacultyParser.fieldsOf("办公电话：024-88487135；手机：13464032492")
        assertEquals("024-88487135", f.phone)
        assertFalse("手机号不该进字段", f.phone.contains("13464032492"))
    }

    // ── 合规：隐私清洗 ───────────────────────────────────

    @Test
    fun `手机号 QQ 私人邮箱不落地`() {
        val info = parse("det_zbxy_1808.html", "方红")
        assertNotNull(info)
        val blob = (info!!.rawText + info.fields.rows().joinToString { it.second } +
            info.sections.joinToString { it.text + it.title })
        // 实测这份页面里就有这三样，必须一个都不剩
        assertFalse("手机号落地了", blob.contains("13464032492"))
        assertFalse("QQ 号落地了", blob.contains("1147888178"))
        assertFalse("私人邮箱落地了", blob.contains("snzhibao@163.com"))
        assertFalse("还残留隐私：${info.fields.email}", FacultyPrivacy.containsPrivate(blob))
        // 校方邮箱要留（那是公务联系方式）
        assertEquals("fanghong@syau.edu.cn", info.fields.email)
    }

    @Test
    fun `非校方邮箱一律丢掉`() {
        val info = parse("det_swxy_quanbo.html", "曲波")
        assertNotNull(info)
        assertFalse("163 邮箱不该进缓存", info!!.rawText.contains("@163.com"))
        assertFalse(FacultyPrivacy.containsPrivate(info.fields.email))
        assertTrue(
            "清洗器不能把校方邮箱也洗掉",
            !FacultyPrivacy.containsPrivate("联系：name@syau.edu.cn"),
        )
    }

    // ── 栏目页找人 ───────────────────────────────────────

    @Test
    fun `按姓名在栏目页里找到绝对地址`() {
        val column = html("col_thxy_zgjzc_utf8.html")
        val base = "https://thxy.syau.edu.cn/szdw/jsxl/zgjzc.htm"
        val links = FacultyParser.findDetailLinks(column, "汪景宽", base)
        assertEquals(1, links.size)
        assertEquals("https://thxy.syau.edu.cn/info/1611/4929.htm", links.first())
        // 栏目页里没这个人 → 空（界面据此说「栏目里没找到」）
        assertTrue(FacultyParser.findDetailLinks(column, "不存在的老师", base).isEmpty())
    }

    @Test
    fun `院系映射一院一配且不认识的不猜`() {
        assertEquals("thxy.syau.edu.cn", FacultySources.of("土环")?.host)
        assertEquals("thxy.syau.edu.cn", FacultySources.of("土地与环境学院")?.host)
        assertEquals("nxy.syau.edu.cn", FacultySources.of("农学")?.host)
        assertEquals("marx.syau.edu.cn", FacultySources.of("马院")?.host)
        // 图书馆没有师资栏目 → null（不许拿别的学院顶替）
        assertNull(FacultySources.of("图书"))
        assertNull(FacultySources.of(""))
        assertTrue(FacultySources.all.size >= 16)
    }

    // ── 缓存键 / TTL / 淘汰 ───────────────────────────────

    @Test
    fun `缓存键优先用工号`() {
        assertEquals("1995500023", FacultyCache.key("1995500023", "董秀茹", "thxy.syau.edu.cn"))
        // 没有工号时退化成姓名@域名的短哈希：稳定、定长、不含隐私
        val a = FacultyCache.key("", "董秀茹", "thxy.syau.edu.cn")
        val b = FacultyCache.key("", "董秀茹", "thxy.syau.edu.cn")
        assertEquals(a, b)
        assertEquals(16, a.length)
        assertFalse("键里不该出现姓名", a.contains("董"))
    }

    @Test
    fun `TTL 三十天`() {
        val now = OffsetDateTime.parse("2026-11-14T10:00:00+08:00")
        assertTrue(FacultyCache.isFresh("2026-10-16T10:00:00+08:00", now))   // 29 天
        assertFalse(FacultyCache.isFresh("2026-10-14T10:00:00+08:00", now))  // 31 天
        assertFalse("解析不出时间就当作不新鲜", FacultyCache.isFresh("昨天", now))
        assertFalse("未来时间不认", FacultyCache.isFresh("2027-01-01T00:00:00+08:00", now))
    }

    @Test
    fun `超上限按最旧淘汰`() {
        val entries = listOf(
            FacultyCache.Entry("a", 100, "2026-01-01T00:00:00+08:00"),
            FacultyCache.Entry("b", 100, "2026-06-01T00:00:00+08:00"),
            FacultyCache.Entry("c", 100, "2026-11-01T00:00:00+08:00"),
        )
        assertEquals(listOf("a"), FacultyCache.evictionPlan(entries, maxEntries = 2))
        assertEquals(listOf("a", "b"), FacultyCache.evictionPlan(entries, maxEntries = 1))
        // 体积超限也按同一个顺序删
        assertEquals(listOf("a"), FacultyCache.evictionPlan(entries, maxEntries = 99, maxBytes = 250))
        assertTrue(FacultyCache.evictionPlan(entries, maxEntries = 99, maxBytes = 999).isEmpty())
    }

    // ── 界面文案：三种失败必须说成三句话 ─────────────────

    @Test
    fun `官网失败文案三种可分`() {
        val noSource = HomeModel.facultyMsg(FacultyResult(fail = FacultyFail.NoSource))
        val notFound = HomeModel.facultyMsg(FacultyResult(fail = FacultyFail.NotFound))
        val notParsed = HomeModel.facultyMsg(FacultyResult(fail = FacultyFail.NotParsed))
        val network = HomeModel.facultyMsg(FacultyResult(fail = FacultyFail.Network))
        assertTrue(noSource, noSource.contains("没有师资栏目"))
        assertTrue(notFound, notFound.contains("没找到"))
        assertTrue(notParsed, notParsed.contains("解析不到"))
        assertTrue(network, network.contains("没连上"))
        assertEquals(4, setOf(noSource, notFound, notParsed, network).size)
        // 有新鲜数据就不该有提示
        val fresh = FacultyResult(
            info = FacultyInfo(key = "1", fetchedAt = "2026-11-14T10:00:00+08:00"),
            fail = FacultyFail.None,
        )
        assertEquals("", HomeModel.facultyMsg(fresh))
        // 刷新失败但有旧缓存：要标日期，不能假装是最新的
        val stale = HomeModel.facultyMsg(
            FacultyResult(
                info = FacultyInfo(key = "1", fetchedAt = "2026-11-01T10:00:00+08:00"),
                fail = FacultyFail.Network,
                stale = true,
            ),
        )
        assertTrue(stale, stale.contains("2026-11-01"))
        assertTrue(stale, stale.contains("更新失败"))
    }
}
