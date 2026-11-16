package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.ProbeCatalog
import cn.dkarbon.enroute.data.ProbeResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住接口诊断的 14 条清单，以及「什么算通过」的口径。
 *
 * 这几件事都曾经出过错或者很容易出错：
 * - 顺序：带 token 的两条必须排在被依赖者后面，否则永远拿不到 token
 * - 口径：学籍/两份课表/成绩首页/方案页本来返回 HTML，不能拿 JSON 当通过标准
 * - 诚实：请求没发出去（-1）不能凑一个假的 200 出来
 */
class ProbeCatalogTest {

    @Test
    fun `清单是 14 条`() {
        assertEquals(14, ProbeCatalog.specs.size)
    }

    @Test
    fun `id 不重复`() {
        val ids = ProbeCatalog.specs.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `带 token 的两条排在被依赖者之后`() {
        val index = ProbeCatalog.specs.withIndex().associate { it.value.id to it.index }
        // 成绩数据要成绩首页先给出 token
        assertTrue(index.getValue("score.data") > index.getValue("score.index"))
        // 方案明细要方案页先给出 token
        assertTrue(index.getValue("plan.detail") > index.getValue("plan.index"))
    }

    @Test
    fun `token 只出现在依赖 token 的那几条路径里`() {
        val withToken = ProbeCatalog.specs.filter { it.path.contains(ProbeCatalog.TOKEN) }.map { it.id }
        assertEquals(listOf("score.data", "score.pass", "plan.detail"), withToken)
    }

    @Test
    fun `HTML 接口不拿 JSON 当通过标准`() {
        val roll = ProbeCatalog.specs.first { it.id == "roll.info" }
        assertFalse(roll.expectJson)
        // 返回 HTML、状态 200、有内容 —— 算通
        assertTrue(ProbeCatalog.ok(roll, ProbeResult("/x", 200, 8123, false, "<html>")))
        // 同样是 HTML 但一个字都没有 —— 不算通
        assertFalse(ProbeCatalog.ok(roll, ProbeResult("/x", 200, 0, false, "")))
        // 状态不对 —— 不算通
        assertFalse(ProbeCatalog.ok(roll, ProbeResult("/x", 500, 40, false, "err")))
    }

    @Test
    fun `JSON 接口要求真的是 JSON`() {
        val score = ProbeCatalog.specs.first { it.id == "score.data" }
        assertTrue(ProbeCatalog.ok(score, ProbeResult("/x", 200, 900, true, "{\"a\":1}")))
        // 200 但返回的是登录页 HTML —— 常见于登录态过期，必须算不通
        assertFalse(ProbeCatalog.ok(score, ProbeResult("/x", 200, 900, false, "<html>登录")))
    }

    @Test
    fun `没发出去的请求算失败且状态码如实是负`() {
        val any = ProbeCatalog.specs.first()
        assertFalse(ProbeCatalog.ok(any, ProbeResult("/x", -1, 0, false, "请求失败")))
    }

    @Test
    fun `还没出结果的行显示省略号`() {
        assertEquals("…", ProbeCatalog.metaLine(null))
    }

    @Test
    fun `失败的行不显示状态码`() {
        assertEquals("— · 失败", ProbeCatalog.metaLine(ProbeResult("/x", -1, 0, false, "")))
    }

    @Test
    fun `成功的行显示状态码和字节数`() {
        assertEquals("200 · 1.2 KB", ProbeCatalog.metaLine(ProbeResult("/x", 200, 1229, true, "")))
        assertEquals("200 · 512 B", ProbeCatalog.metaLine(ProbeResult("/x", 200, 512, true, "")))
        assertEquals("200 · 31 KB", ProbeCatalog.metaLine(ProbeResult("/x", 200, 31 * 1024, true, "")))
    }

    @Test
    fun `每条路径都已落成绝对路径`() {
        ProbeCatalog.specs.forEach { spec ->
            assertTrue("${spec.id} 路径必须以 / 开头", spec.path.startsWith("/student/"))
        }
    }
}
