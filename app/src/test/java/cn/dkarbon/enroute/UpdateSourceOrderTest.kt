package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UpdateCenter
import cn.dkarbon.enroute.data.UpdatePolicy
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
    fun `自建源排在第一位，且落在 app 前缀下`() {
        // 2026-11-27 起与 2026-11-16 的结论**相反**：那台机器上的 /app/ 已经有真文件
        // （由 server/telemetry/deploy.sh 从 releases/ 同步），从「白等超时的死源」变成
        // 「不缓存、不发旧版」的首选。别照着老注释把它挪到最后。
        val selfIdx = urls.indexOfFirst { it.contains(UpdatePolicy.SELF_HOST_HOST) }
        assertEquals("自建源应排在第一位", 0, selfIdx)
        assertEquals(
            "自建清单地址必须是那个常量（端口与前缀都钉死）",
            UpdatePolicy.SELF_HOST_MANIFEST_URL,
            urls[selfIdx],
        )
        assertTrue("自建清单必须在 /app/ 下", urls[selfIdx].endsWith("/app/latest.json"))
    }

    @Test
    fun `自建口只认那个主机端口和 app 前缀`() {
        assertTrue(UpdatePolicy.isAllowedSelfHost(UpdatePolicy.SELF_HOST_APK_URL))
        assertTrue(UpdatePolicy.isAllowedSelfHost(UpdatePolicy.SELF_HOST_MANIFEST_URL))
        // 端口、目录、协议、主机 —— 任一项不同就拒（明文口只开了这一个格子）
        assertTrue("18081 不走这条口", !UpdatePolicy.isAllowedSelfHost("http://47.95.12.198:18081/app/x.apk"))
        assertTrue("目录本身不是文件", !UpdatePolicy.isAllowedSelfHost("http://47.95.12.198:18082/app/"))
        assertTrue("别的路径应拒", !UpdatePolicy.isAllowedSelfHost("http://47.95.12.198:18082/map/api/dataset"))
        assertTrue("https 不走明文口", !UpdatePolicy.isAllowedSelfHost("https://47.95.12.198:18082/app/x.apk"))
        assertTrue("别的 IP 应拒", !UpdatePolicy.isAllowedSelfHost("http://47.95.12.199:18082/app/x.apk"))
    }

    @Test
    fun `下载候选最后一项是自建口，且它是稳定文件名`() {
        val original = "https://github.com/Starzzb/SYAU-enroute/releases/download/m22/SYAU-enroute-m22.apk"
        val candidates = UpdatePolicy.mirrorsOf(original) + UpdatePolicy.SELF_HOST_APK_URL
        assertEquals("最后一项应是自建口", UpdatePolicy.SELF_HOST_APK_URL, candidates.last())
        assertTrue(
            "自建口文件名不带版本号（每版覆盖同一个文件）",
            UpdatePolicy.SELF_HOST_APK_URL.endsWith("/app/enroute-latest.apk"),
        )
        // 前几项仍是「原始地址 + 镜像」，不改口
        assertTrue(candidates.drop(1).dropLast(1).all { it.endsWith(original) })
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
