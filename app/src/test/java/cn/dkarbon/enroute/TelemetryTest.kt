package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.telemetry.Telemetry
import cn.dkarbon.enroute.data.telemetry.TelemetryId
import cn.dkarbon.enroute.data.telemetry.TelemetryQueue
import cn.dkarbon.enroute.data.telemetry.TelemetrySchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 使用数据采集的白名单、队列、标识与分档 —— 全部是纯函数，不碰网络、不碰 Android 运行时。
 *
 * 这个文件的核心不是「跑通」，而是**锁死边界**：
 * 一旦有人往 props 里塞了自由文本（课程名、学号、搜索词原文），
 * 这里必须红。所以下面有几条断言写得很"死"，别改宽。
 */
class TelemetryTest {

    // ── 白名单 ──────────────────────────────────

    @Test
    fun 未知事件一律拒绝() {
        assertNull(TelemetrySchema.sanitize("user.login", mapOf("ok" to "true")))
        assertNull(TelemetrySchema.sanitize("", emptyMap()))
        assertTrue(!TelemetrySchema.accepts("syllabus.opened"))
        assertTrue(TelemetrySchema.accepts("syllabus.open"))
    }

    @Test
    fun 未知字段直接丢弃而不是报错() {
        val out = TelemetrySchema.sanitize(
            "syllabus.open",
            mapOf("from" to "timetable", "hit" to "true", "course_name" to "高等数学"),
        )
        assertNotNull(out)
        assertEquals(setOf("from", "hit"), out!!.keys)
    }

    @Test
    fun 白名单内的字段塞自由文本会被换成_invalid() {
        val out = TelemetrySchema.sanitize("syllabus.open", mapOf("from" to "高等数学"))!!
        assertEquals(TelemetrySchema.INVALID, out["from"])
    }

    @Test
    fun 空白值不写进事件() {
        val out = TelemetrySchema.sanitize("syllabus.open", mapOf("from" to "  ", "hit" to "false"))!!
        assertEquals(setOf("hit"), out.keys)
    }

    @Test
    fun 版本号只认形态不认枚举() {
        assertTrue(TelemetrySchema.isVersionLike("0.9.1+m40"))
        assertTrue(TelemetrySchema.isVersionLike("1.0.0"))
        assertTrue(!TelemetrySchema.isVersionLike("0.9.1; rm -rf"))
        assertTrue(!TelemetrySchema.isVersionLike("v0.9.1"))
        assertEquals(
            TelemetrySchema.INVALID,
            TelemetrySchema.sanitize("app.crash", mapOf("ver" to "很长的中文串"))!!["ver"],
        )
    }

    /**
     * 端上白名单与 `server/telemetry/schema.json` 必须逐条一致。
     *
     * 这是唯一一条依赖仓库文件的测试：两个副本一旦漂移，服务端就会开始丢事件，
     * 而且丢得**毫无声响**（服务端只累加一个 dropped 计数）。所以宁可在这里红。
     */
    @Test
    fun 端上白名单与服务端_schema_一致() {
        val file = findSchema()
        assertEquals("找不到 server/telemetry/schema.json", true, file.exists())
        // 单测里不能用 android 的 org.json（Stub! 会直接抛），走项目本来就有的 kotlinx-serialization
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        assertEquals(TelemetrySchema.VERSION, root.getValue("version").jsonPrimitive.int)
        val events = root.getValue("events").jsonObject
        assertEquals(TelemetrySchema.EVENTS.size, events.size)
        for ((name, fields) in TelemetrySchema.EVENTS) {
            val srv = events[name]?.jsonObject
            assertTrue("服务端缺事件 " + name, srv != null)
            assertEquals("事件 " + name + " 的字段数不一致", fields.size, srv!!.size)
            for ((field, domain) in fields) {
                assertTrue("服务端缺字段 " + name + "." + field, srv.containsKey(field))
                val arr = srv.getValue(field).jsonArray
                val remote = arr.map { it.jsonPrimitive.content }.toSet()
                val local = if (domain.size == 1 && domain.first() == TelemetrySchema.VERSION_SENTINEL) {
                    setOf("@ver")
                } else {
                    domain
                }
                assertEquals("取值域不一致 " + name + "." + field, local, remote)
            }
        }
    }

    private fun findSchema(): File {
        // 单测的工作目录是 app/，从它往上找仓库根；找不到就当失败（下面会断言）
        var dir: File? = File(".").absoluteFile
        repeat(4) {
            val f = File(dir, "server/telemetry/schema.json")
            if (f.exists()) return f
            dir = dir?.parentFile
        }
        return File("server/telemetry/schema.json")
    }

    // ── 队列 ────────────────────────────────────

    @Test
    fun 环形队列超限时丢最旧的() {
        val lines = (1..50).joinToString("\n") { "{\"n\":" + it + "}" } + "\n"
        val cap = 120
        val kept = TelemetryQueue.trimToCap(lines, cap)
        assertTrue(kept.toByteArray(Charsets.UTF_8).size <= cap)
        assertTrue("最后一行必须还在", kept.contains("\"n\":50"))
        assertTrue("最早的行应该被丢掉", !kept.contains("\"n\":1\n"))
    }

    @Test
    fun 没超上限时原样返回不做重写() {
        val text = "{\"a\":1}\n{\"a\":2}\n"
        assertEquals(text, TelemetryQueue.trimToCap(text, 10_000))
    }

    @Test
    fun 单行超上限时保留它() {
        val long = "{\"x\":\"" + "a".repeat(500) + "\"}\n"
        val kept = TelemetryQueue.trimToCap(long, 100)
        assertTrue("单行超限也要留下痕迹", kept.contains("aaa"))
    }

    // ── 标识 ────────────────────────────────────

    @Test
    fun 安装标识稳定且64位() {
        val id = "0123456789abcdef0123456789abcdef"
        val a = TelemetryId.install(id)
        val b = TelemetryId.install(id)
        assertEquals("同一台设备必须算得出同一个标识", a, b)
        assertEquals(16, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
        assertTrue("两台设备不能撞", a != TelemetryId.install("fedcba9876543210fedcba9876543210"))
        assertTrue("标识不能是原像", !a.contains(id))
    }

    @Test
    fun 没有安装标识就不写任何身份字段() {
        assertEquals("", TelemetryId.install(""))
        assertEquals("", TelemetryId.daily("", LocalDate.of(2026, 11, 21)))
    }

    @Test
    fun 当日标识同日稳定跨日变化且只有8位() {
        val id = "0123456789abcdef0123456789abcdef"
        val a = TelemetryId.daily(id, LocalDate.of(2026, 11, 21))
        val b = TelemetryId.daily(id, LocalDate.of(2026, 11, 21))
        val c = TelemetryId.daily(id, LocalDate.of(2026, 11, 22))
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(8, a.length)
        assertTrue(a.all { it in "0123456789abcdef" })
    }

    @Test
    fun 会话标识两位字节四位十六进制() {
        val s = TelemetryId.newSession()
        assertEquals(4, s.length)
        assertTrue(s.all { it in "0123456789abcdef" })
    }

    // ── 分档 ────────────────────────────────────

    @Test
    fun 分档结果必须落在白名单允许的取值里() {
        for (n in listOf(0, 1, 2, 5, 6, 20, 21, 999)) {
            assertTrue("" + n, Telemetry.bucketCount(n) in TelemetrySchema.FACE_BUCKETS)
            assertTrue("" + n, Telemetry.bucketResults(n) in setOf("0", "1-5", "6-20", "20+"))
        }
        for (ms in listOf(0L, 299L, 300L, 799L, 800L, 1500L, 60000L)) {
            assertTrue(Telemetry.bucketMs(ms) in setOf("<300", "300-800", "800-1500", "1500+"))
            assertTrue(Telemetry.bucketAssetMs(ms) in setOf("0-50", "50-200", "200-600", "600+"))
            assertTrue(Telemetry.bucketSinceStart(ms) in setOf("0-5", "5-30", "30-120", "120+"))
        }
        for (d in listOf(0L, 1L, 7L, 8L, 30L, 31L, 90L, 91L, 180L, 181L, 400L)) {
            assertTrue(
                Telemetry.bucketDaysSinceInstall(d) in
                    setOf("0", "1-7", "8-30", "31-90", "91-180", "180+"),
            )
        }
        // 档位必须覆盖服务端 180 天保留期，否则 90 天以上的用户全挤进一个桶
        assertEquals("91-180", Telemetry.bucketDaysSinceInstall(91))
        assertEquals("180+", Telemetry.bucketDaysSinceInstall(181))
    }

    @Test
    fun 搜索词只记长度档() {
        assertEquals("0", Telemetry.bucketQueryLength(0))
        assertEquals("1-2", Telemetry.bucketQueryLength(2))
        assertEquals("3+", Telemetry.bucketQueryLength(40))
    }
}