package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UrpHomeApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 培养方案 callback 的调用契约。
 *
 * 真机日志（2026-11-16 11:41）里这条接口一直是失败的：
 *
 * ```
 * → GET /student/integratedQuery/instructionPlanQuery/***/detail/callback
 * ← 400 227B  <title>400 Required String parameter 'fajhh' is not present</title>
 * ```
 *
 * 两个必须钉死的事实：
 * 1. callback 只认 **POST**，且表单里必须有 `fajhh`（方案计划号）；
 * 2. `fajhh` 不在 URL 上，只以 `instructionPlanQuery("202403112440")` 写死在页面脚本里。
 *
 * 页面原文见 `docs/_probe/urp/plan_page.html:131` 与 `:203`。
 */
class PlanCallbackContractTest {

    private val planIndexPage = """
        <script>
        $(function () {
            instructionPlanQuery("202403112440");
        });
        var currentShowMode = 'enrolled';
        function instructionPlanQuery(fajhh, showMode) {
            $.ajax({
                url: "/student/integratedQuery/instructionPlanQuery/dxq482056a/detail/callback",
                type: "post",
                data: "fajhh=" + fajhh + "&showMode=" + showMode,
                dataType: "json",
            });
        }
        </script>
    """.trimIndent()

    @Test
    fun `方案计划号能从页面脚本里抠出来`() {
        val m = UrpHomeApi.PLAN_FAJHH_RE.find(planIndexPage)
        assertNotNull("页面里的 instructionPlanQuery(\"...\") 必须能匹配到方案计划号", m)
        assertEquals("202403112440", m!!.groupValues[1])
    }

    @Test
    fun `同一个页面里 token 与方案计划号都取得到`() {
        assertEquals("dxq482056a", UrpHomeApi.PLAN_TOKEN_RE.find(planIndexPage)?.groupValues?.get(1))
        assertEquals("202403112440", UrpHomeApi.PLAN_FAJHH_RE.find(planIndexPage)?.groupValues?.get(1))
    }

    @Test
    fun `缺方案计划号时不猜值`() {
        val noFajhh = """url: "/student/integratedQuery/instructionPlanQuery/dxq482056a/detail/callback""""
        assertEquals(null, UrpHomeApi.PLAN_FAJHH_RE.find(noFajhh)?.groupValues?.get(1))
    }

    @Test
    fun `没有 token 的页面两个都取不到`() {
        val plain = "<html><body>请先登录</body></html>"
        assertEquals(null, UrpHomeApi.PLAN_TOKEN_RE.find(plain)?.groupValues?.get(1))
        assertEquals(null, UrpHomeApi.PLAN_FAJHH_RE.find(plain)?.groupValues?.get(1))
    }
}
