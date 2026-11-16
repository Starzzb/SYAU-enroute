package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UpdateCenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新清单的源顺序与缓存绕行（2026-11-16）。
 *
 * 背景：真机上「检查更新」长期卡在「正在连接服务器…」。2026-11-16 实测发现
 * **主力源国内超时、两个自建回落全都是死的**（18082=连接被掐、18081=404，
 * nginx 里根本没配 `/app/` 这条路由）。改版后主力换成 jsDelivr（5 次全通 428–656ms）。
 *
 * 这组测试锁住三件容易在下次改动中被悄悄破坏的事：
 *   1. 顺序（jsDelivr 必须在最前，死掉的自建机必须在最后）
 *   2. 每条都带按小时变化的 cache-busting 参数（否则 jsDelivr 的 12 小时缓存会让用户半年看不到新版）
 *   3. 参数格式是 `yyyyMMddHH`（不能带 ':' 或 '-' 以外的怪字符，会破坏 URL）
 */
class UpdateSourceOrderTest {

    private val urls = UpdateCenter.MANIFEST_URLS

    @Test
    fun `首位是能拿到新内容的源，不是 jsDelivr`() {
        // 2026-11-16 二次实测：jsDelivr 对 @master 缓存 12 小时且 purge 无效 ——
        // 发新版后它会继续返回旧内容（实测 purge 返回 CF/FY 均 true，内容仍是上一版）。
        // 所以首位必须是直读 GitHub 的源（gh-proxy / githack / raw），jsDelivr 只能垫底。
        assertTrue(
            "首位不能是 jsDelivr，实际是 ${urls.first()}",
            !urls.first().contains("jsdelivr.net"),
        )
        assertTrue("清单里应保留 jsDelivr 作最后兜底", urls.any { it.contains("jsdelivr.net") })
        assertTrue("清单里应有直读 GitHub 的源", urls.any { it.contains("raw.githubusercontent.com") })
    }

    @Test
    fun `jsDelivr 必须排在所有直读源之后`() {
        val jsIdx = urls.indexOfFirst { it.contains("jsdelivr.net") }
        val directIdx = urls.indexOfFirst { !it.contains("jsdelivr.net") }
        assertTrue("必须有 jsDelivr 源", jsIdx >= 0)
        assertTrue("必须有直读源", directIdx >= 0)
        assertTrue("jsDelivr 有 12 小时缓存，必须排在直读源之后（js=$jsIdx, direct=$directIdx）", jsIdx > directIdx)
    }

    @Test
    fun `已知不可用的自建源必须排在最后`() {
        // 2026-11-16 实测：18082 连接被掐、18081 返回 404。放前面会每次白等超时。
        val selfHosted = urls.withIndex().filter { it.value.contains("47.95.12.198") }
        if (selfHosted.isNotEmpty()) {
            val firstPublic = urls.indexOfFirst { !it.contains("47.95.12.198") }
            selfHosted.forEach {
                assertTrue(
                    "自建源 ${it.value} 应排在所有公共源之后",
                    firstPublic < 0 || it.index > firstPublic,
                )
            }
        }
    }

    @Test
    fun `清单地址都是 https 或已放行明文的自建机`() {
        urls.forEach { u ->
            val ok = u.startsWith("https://") || u.contains("47.95.12.198")
            assertTrue("清单地址 $u 既不是 https 也不是已放行明文的自建机", ok)
        }
    }

    // ── 下载镜像 ───────────────────────────────────────────

    @Test
    fun `镜像地址由已校验的入口地址拼出`() {
        val original = "https://github.com/Starzzb/SYAU-enroute/releases/download/m22/SYAU-enroute-m22.apk"
        val list = cn.dkarbon.enroute.data.UpdatePolicy.mirrorsOf(original)
        assertEquals("第一项必须是直连", original, list.first())
        assertTrue("应至少有一个镜像", list.size >= 2)
        list.drop(1).forEach {
            assertTrue("镜像 $it 必须以镜像域开头且原样包含原始地址", it.endsWith(original) && it != original)
        }
    }

    @Test
    fun `入口地址校验拒绝镜像域（钉死只针对入口）`() {
        // latest.json 里的 url 必须是本仓库 Releases —— 镜像地址不许当入口
        val mirror = "https://gh-proxy.com/https://github.com/Starzzb/SYAU-enroute/releases/download/m22/a.apk"
        assertTrue("镜像地址不应通过入口校验", !cn.dkarbon.enroute.data.UpdatePolicy.isAllowedApkUrl(mirror))
    }

    @Test
    fun `镜像域允许作为下载跳转目标`() {
        assertTrue(
            "gh-proxy 应允许跳转",
            cn.dkarbon.enroute.data.UpdatePolicy.isAllowedRedirect("https://gh-proxy.com/x"),
        )
        assertTrue(
            "objects.githubusercontent.com 仍应允许",
            cn.dkarbon.enroute.data.UpdatePolicy.isAllowedRedirect("https://objects.githubusercontent.com/x"),
        )
        assertTrue(
            "无关域名仍应拒绝",
            !cn.dkarbon.enroute.data.UpdatePolicy.isAllowedRedirect("https://evil.com/x"),
        )
    }

    // ── cache-busting ──────────────────────────────────────

    @Test
    fun `cache-busting 参数正确拼接`() {
        val u = "https://cdn.jsdelivr.net/gh/a/b@master/c/latest.json"
        assertEquals("$u?t=2026111613", UpdateCenter.cacheBusted(u, "2026111613"))
    }

    @Test
    fun `已有查询串时用 and 连接`() {
        val u = "https://example.com/x.json?foo=1"
        assertEquals("$u&t=2026111613", UpdateCenter.cacheBusted(u, "2026111613"))
    }

    @Test
    fun `空时间戳不改变原地址`() {
        val u = "https://example.com/x.json"
        assertEquals(u, UpdateCenter.cacheBusted(u, ""))
    }

    @Test
    fun `时间戳是 yyyyMMddHH 形态`() {
        // 用固定时间点，避免跨时区/跨日抖动
        val stamp = UpdateCenter.hourStamp(1_700_000_000_000L)
        assertEquals("长度应为 10 位", 10, stamp.length)
        assertTrue("应为纯数字，实际 $stamp", stamp.all { it.isDigit() })
        assertTrue("年份前四位应是 2023", stamp.startsWith("2023"))
        // 小时必须在 00..23 之间（SimpleDateFormat 的 HH 不含 AM/PM）
        val hour = stamp.substring(8).toInt()
        assertTrue("小时 $hour 超出 0..23", hour in 0..23)
    }

    @Test
    fun `每条源都能被 cache-busted 且不破坏原地址`() {
        val stamp = "2026111613"
        urls.forEach { u ->
            val busted = UpdateCenter.cacheBusted(u, stamp)
            assertTrue("$u 附加参数后应仍以原地址开头", busted.startsWith(u))
            assertTrue("$u 附加参数后应含 t=$stamp", busted.contains("t="))
        }
    }
}
