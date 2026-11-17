package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.faculty.FacultyParser
import cn.dkarbon.enroute.data.faculty.FacultySources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 师资栏目的「翻页 + 同组栏目」发现（2026-11-17）。
 *
 * 起因：蒋卓东（副教授）在详情页里有完整资料，App 却报「师资栏目里没找到这位老师」。两层原因：
 * 1. 土环配置里只有 **正高栏**（`zgjzc.htm`）与博导栏，副高在 `fgjzc.htm` —— 压根没去；
 * 2. 副高栏**自己还分两页**（`fgjzc/1.htm`、`fgjzc/2.htm`），他在第 2 页。
 *
 * 修法不写死更多路径：栏目页的导航里就带着同目录的兄弟栏目，页底就带着自己的翻页，
 * 顺着页面给的链接走即可。夹具是当日真实页面（`docs/_probe/faculty/`，手机号与邮箱已脱敏）。
 */
class FacultyColumnWalkTest {

    private fun html(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("faculty/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private val base = "https://thxy.syau.edu.cn"
    private val fgjzc = "$base/szdw/jsxl/fgjzc.htm"
    private val fgjzcP2 = "$base/szdw/jsxl/fgjzc/2.htm"
    private val zgjzc = "$base/szdw/jsxl/zgjzc.htm"

    @Test
    fun `副高栏自己分两页 翻页链接按页码升序`() {
        assertEquals(
            listOf("$base/szdw/jsxl/fgjzc/1.htm", fgjzcP2),
            FacultyParser.pagination(html("col_thxy_fgjzc.html"), fgjzc),
        )
    }

    @Test
    fun `正高栏页面上能发现同目录的副高与教师系列栏目`() {
        val sibs = FacultyParser.siblingColumns(html("col_thxy_zgjzc_utf8.html"), zgjzc)
        assertTrue("应发现副高栏：$sibs", sibs.contains(fgjzc))
        assertTrue("应发现教师系列总栏：$sibs", sibs.contains("$base/szdw/jsxl/js.htm"))
        assertFalse("不该把自己当兄弟：$sibs", sibs.contains(zgjzc))
        // 导航里那一堆别的目录（学院简介、党群工作…）一律不许混进来
        assertTrue("混进了别的目录：$sibs", sibs.all { it.startsWith("$base/szdw/jsxl/") && !it.contains("/szdw/jsxl/fgjzc/") })
    }

    @Test
    fun `翻页链接不会被当成兄弟栏目`() {
        val sibs = FacultyParser.siblingColumns(html("col_thxy_fgjzc.html"), fgjzc)
        assertTrue("翻页被当成栏目了：$sibs", sibs.none { Regex("""/fgjzc/\d+\.htm$""").containsMatchIn(it) })
    }

    @Test
    fun `蒋卓东不在副高第一页 在第二页`() {
        assertTrue(
            "第一页不该有他",
            FacultyParser.findDetailLinks(html("col_thxy_fgjzc.html"), "蒋卓东", fgjzc).isEmpty(),
        )
        assertEquals(
            listOf("$base/info/1612/4923.htm"),
            FacultyParser.findDetailLinks(html("col_thxy_fgjzc_p2.html"), "蒋卓东", fgjzcP2),
        )
    }

    @Test
    fun `土环配置现在覆盖副高栏`() {
        val s = FacultySources.of("土环")
        assertNotNull(s)
        assertTrue("土环没配副高栏：${s!!.columns}", s.columns.any { it.contains("fgjzc") })
        // 正高栏仍在最前：先查最可能命中的那一栏，别让每次查询都先跑几页
        assertTrue(s.columns.first().contains("zgjzc"))
    }

    @Test
    fun `详情页能抽出正文且夹具里没有真实手机号`() {
        val raw = html("det_thxy_jiangzhuodong.html")
        assertFalse("夹具里还有 11 位手机号", Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""").containsMatchIn(raw))
        val info = FacultyParser.parseDetail(
            html = raw,
            name = "蒋卓东",
            key = "2022500009",
            dept = "土环",
            collegeName = "土地与环境学院",
            sourceUrl = "$base/info/1612/4923.htm",
            sourceHost = "thxy.syau.edu.cn",
            fetchedAt = "2026-11-17T10:40:00+08:00",
        )
        assertNotNull("详情页抽不出正文", info)
        val text = info!!.rawText
        assertTrue("正文里应有研究方向：${text.take(80)}", text.contains("研究方向"))
    }
}
