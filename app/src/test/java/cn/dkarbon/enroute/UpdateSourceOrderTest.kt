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
    fun `主力源是 jsDelivr 而不是 GitHub raw`() {
        // 这条是本次改版的核心：国内 GitHub raw 超时，放第一位等于每次都白等 6 秒
        assertTrue(
            "首位应为 jsDelivr，实际是 ${urls.first()}",
            urls.first().contains("cdn.jsdelivr.net"),
        )
        assertTrue("清单里应仍保留 fastly 备份", urls.any { it.contains("fastly.jsdelivr.net") })
        assertTrue("清单里应仍保留 GitHub raw", urls.any { it.contains("raw.githubusercontent.com") })
    }

    @Test
    fun `GitHub raw 不能排在 jsDelivr 前面`() {
        val jsIdx = urls.indexOfFirst { it.contains("cdn.jsdelivr.net") }
        val rawIdx = urls.indexOfFirst { it.contains("raw.githubusercontent.com") }
        assertTrue("必须有 jsDelivr 源", jsIdx >= 0)
        assertTrue("必须有 GitHub raw 源", rawIdx >= 0)
        assertTrue("jsDelivr 应排在 raw 之前（jsIdx=$jsIdx, rawIdx=$rawIdx）", jsIdx < rawIdx)
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
