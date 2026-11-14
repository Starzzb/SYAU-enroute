package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.LatestResult
import cn.dkarbon.enroute.data.UpdateParse
import cn.dkarbon.enroute.data.UpdatePolicy
import cn.dkarbon.enroute.data.UpdateRelation
import cn.dkarbon.enroute.data.UpdateSchedule
import cn.dkarbon.enroute.data.UpdateVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用内更新的**红线测试**（票 15）。全部是纯 JVM，不需要网络/设备。
 *
 * 覆盖票 15 验收里能自动化的部分：
 *   · 版本比较三态（含「服务器版本更低」不许提示更新）
 *   · 下载地址白名单（防劫持）—— 这是全票最要紧的一条
 *   · latest.json 解析：缺字段/坏 JSON/sha256 不合法 → 一律失败，不猜
 *   · 自动检查「每天最多一次」的时间窗
 */
class UpdatePolicyTest {

    private fun manifest(
        code: Long = 210,
        name: String = "0.2.1+m20",
        url: String = "https://github.com/Starzzb/SYAU-enroute/releases/download/v0.2.1/enroute-0.2.1.apk",
        sha: String = "a".repeat(64),
        size: String = "4750000",
        extra: String = "",
    ) = """
        {"versionCode":$code,"versionName":"$name","url":"$url","sha256":"$sha","size":$size$extra}
    """.trimIndent()

    // ── 版本关系 ─────────────────────────────────────────

    @Test
    fun `version relation is three-state`() {
        assertEquals(UpdateRelation.NEWER, UpdateVersions.relation(210, 200))
        assertEquals(UpdateRelation.SAME, UpdateVersions.relation(200, 200))
        assertEquals(UpdateRelation.OLDER, UpdateVersions.relation(190, 200))
    }

    @Test
    fun `server older than local must not prompt an update`() {
        val older = UpdateParse.latest(manifest(code = 190, name = "0.1.9"))
        assertTrue(older is LatestResult.Ok)
        val info = (older as LatestResult.Ok).info
        assertFalse("服务器版本更低时绝不能提示更新", UpdateVersions.hasUpdate(info, 200))
    }

    @Test
    fun `newer prompts and minSupported marks it mandatory`() {
        val r = UpdateParse.latest(manifest(code = 210, extra = ""","minSupported":205"""))
        val info = (r as LatestResult.Ok).info
        assertTrue(UpdateVersions.hasUpdate(info, 200))
        assertTrue(UpdateVersions.isMandatory(info, 200))
        // 已经到 205 了就不再强制
        assertFalse(UpdateVersions.isMandatory(info, 205))
    }

    // ── 下载地址白名单（防劫持）────────────────────────────

    @Test
    fun `allowed hosts are github release hosts over https`() {
        assertTrue(UpdatePolicy.isAllowedApkUrl(
            "https://github.com/Starzzb/SYAU-enroute/releases/download/v0.2.1/enroute-0.2.1.apk"))
        // 302 之后的落点：只校验主机（前缀钉死不适用于跳转目标）
        assertTrue(UpdatePolicy.isAllowedRedirect(
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/456?X-Amz=x"))
        assertTrue(UpdatePolicy.isAllowedRedirect(
            "https://release-assets.githubusercontent.com/x/y/z.apk"))
        // 但**入口**地址不能是裸的对象存储（必须走本仓库 Releases）
        assertFalse(UpdatePolicy.isAllowedApkUrl(
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/456"))
    }

    @Test
    fun `another repository is rejected - a tampered latest_json cannot redirect us elsewhere`() {
        // 中间人把 latest.json 的 url+sha256 一起换成「他自己的 GitHub 仓库」：
        // 主机合法、sha256 也自洽 —— 只有「仓库前缀钉死」能挡住这种
        assertFalse(UpdatePolicy.isAllowedApkUrl(
            "https://github.com/attacker/enroute/releases/download/v9/enroute.apk"))
    }

    @Test
    fun `foreign hosts and plain http are rejected`() {
        // 自己的 ECS？不行 —— APK 绝不放那台机器（出口 148KB/s）
        assertFalse(UpdatePolicy.isAllowedApkUrl("http://47.95.12.198:18081/app/enroute.apk"))
        // 明文一律不行（中间人可换包）
        assertFalse(UpdatePolicy.isAllowedApkUrl("http://github.com/Starzzb/SYAU-enroute/releases/download/v1/a.apk"))
        // 伪装子域
        assertFalse(UpdatePolicy.isAllowedApkUrl("https://github.com.evil.example/a.apk"))
        assertFalse(UpdatePolicy.isAllowedApkUrl("https://evil.example/github.com/a.apk"))
        // 空/怪字符串
        assertFalse(UpdatePolicy.isAllowedApkUrl(""))
        assertFalse(UpdatePolicy.isAllowedApkUrl("not a url"))
    }

    @Test
    fun `manifest pointing at a foreign host is rejected, not silently accepted`() {
        val r = UpdateParse.latest(manifest(url = "https://evil.example/enroute.apk"))
        assertTrue(r is LatestResult.Fail)
        assertTrue((r as LatestResult.Fail).reason.contains("允许"))
    }

    // ── 解析的严格性 ─────────────────────────────────────

    @Test
    fun `malformed manifests fail with a human reason`() {
        assertTrue(UpdateParse.latest("not json") is LatestResult.Fail)
        assertTrue(UpdateParse.latest("{}") is LatestResult.Fail)                       // 缺 versionCode
        assertTrue(UpdateParse.latest("""{"versionCode":210}""") is LatestResult.Fail)  // 缺 versionName/url
        // sha256 不是 64 位 → 不能校验 → 拒绝（绝不「凑合装」）
        val badSha = UpdateParse.latest(manifest(sha = "abc"))
        assertTrue(badSha is LatestResult.Fail)
        assertTrue((badSha as LatestResult.Fail).reason.contains("sha256"))
    }

    @Test
    fun `good manifest parses every field`() {
        val r = UpdateParse.latest(manifest(code = 210, name = "0.2.1+m20", extra = ""","notes":"修了几个坑","minSupported":205"""))
        val i = (r as LatestResult.Ok).info
        assertEquals(210L, i.versionCode)
        assertEquals("0.2.1+m20", i.versionName)
        assertEquals("修了几个坑", i.notes)
        assertEquals(205L, i.minSupported)
        assertEquals(4_750_000L, i.size)
        assertEquals("4.5 MB", i.sizeText)
        assertEquals(64, i.sha256.length)
    }

    @Test
    fun `size 0 is not invented as a number`() {
        val r = UpdateParse.latest(manifest(size = "0"))
        val i = (r as LatestResult.Ok).info
        assertEquals("", i.sizeText)   // 数据诚实：没给就空着，不写 0.0 MB
    }

    // ── 自动检查的时间窗 ───────────────────────────────────

    @Test
    fun `auto check runs at most once per day`() {
        val now = 1_700_000_000_000L
        assertTrue("从没查过 → 查", UpdateSchedule.shouldAutoCheck(now, null))
        assertTrue("刚好 24h → 查", UpdateSchedule.shouldAutoCheck(now, now - UpdateSchedule.DAY_MS))
        assertFalse("23h → 不查", UpdateSchedule.shouldAutoCheck(now, now - UpdateSchedule.DAY_MS + 1000))
        assertFalse("1 分钟前查过 → 不查", UpdateSchedule.shouldAutoCheck(now, now - 60_000))
        // 时钟被往回调（用户改时间/跨时区）：仍查一次，否则检查永久停摆
        assertTrue(UpdateSchedule.shouldAutoCheck(now, now + 10 * UpdateSchedule.DAY_MS))
    }
}
