package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapContribute
import cn.dkarbon.enroute.data.map.MapEdit
import cn.dkarbon.enroute.data.map.MapEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地图共建上传的**隐私与契约红线**（票 13 阶段 B）。纯 JVM。
 *
 * 这一层最值钱的断言是第 1 条：**请求体里不许出现任何身份键**。
 * 隐私红线不该靠人工 review —— 一个手滑把 `sid` 加回去，这里就该红。
 *
 * 另外几条按「以 server.js 为准」的规则锁住：
 * · `featureId` 只认 `^f\d{1,6}$`（带下标的 `f0_1` 会被服务端整条拒）
 * · `reasons` 是**数组**（文档写成 map 是错的）
 * · 值 ≤200 字、单批 ≤200 条
 */
class MapContributeTest {

    private fun edits() = MapEdits(
        mapVersion = "2026.11.14-1",
        fieldOrder = listOf("层数", "所属单位"),
        features = mapOf(
            "f19" to MapEdit(name = "九州体育场", kind = "sport",
                fields = mapOf("层数" to "3", "所属单位" to "体育部")),
            "f20" to MapEdit(fields = mapOf("层数" to "2")),
        ),
    )

    // ── 1. 隐私：请求体里到底有什么 ───────────────────────

    @Test
    fun `request body carries exactly the allowed keys and no identity`() {
        val body = MapContribute.body(
            mapVersion = "2026.11.14-1",
            anonymousKey = "anon-1234",
            appVersion = "0.2.0+m19",
            patches = MapContribute.patchesFrom(edits()),
        )
        // 允许的
        assertTrue(body.contains("\"mapVersion\":\"2026.11.14-1\""))
        assertTrue(body.contains("\"featureId\":\"f19\""))
        // `client` 里**只允许** did + appVer —— 用整段精确断言，加任何键都会红
        assertTrue(
            "client 里出现了 did/appVer 以外的东西：$body",
            body.contains(",\"client\":{\"did\":\"anon-1234\",\"appVer\":\"0.2.0+m19\"}"),
        )
        // **禁止的**（票 13 红线：不传姓名/学号/设备号/位置）
        assertFalse("不许出现学号字段 sid", body.contains("\"sid\""))
        assertFalse("不许出现机型（服务端明文落盘）", body.contains("\"model\""))
        assertFalse("服务端不认 userKey 这个名字", body.contains("userKey"))
        assertFalse("不许出现定位", body.contains("\"lat\"") || body.contains("\"lng\"") || body.contains("location"))
        assertFalse("不许出现中文姓名键", body.contains("姓名"))
        assertFalse("不许出现硬件标识", body.contains("androidId") || body.contains("imei") || body.contains("serial"))
        // 注：`"field":"name"` 是**字段名**（固定字段 name/kind 也要上传），不是隐私泄露
        assertTrue(body.contains("\"field\":\"name\""))
    }

    @Test
    fun `anonymous key is random and not a hardware id`() {
        val a = MapContribute.newAnonymousKey()
        val b = MapContribute.newAnonymousKey()
        assertTrue(a.startsWith("anon-"))
        assertTrue("两次必须不同", a != b)
        assertEquals("anon- 前缀 + UUID 长度", 5 + 36, a.length)
    }

    // ── 2. featureId / field 与服务端正则一致 ─────────────

    @Test
    fun `featureId rule matches server regex and rejects legacy underscore ids`() {
        assertNull(MapContribute.featureIdError("f19"))
        assertNull(MapContribute.featureIdError("f123456"))
        // 服务端 ^f\d{1,6}$ → 7 位数字、下划线都不行
        assertNotNull(MapContribute.featureIdError("f1234567"))
        val legacy = MapContribute.featureIdError("f0_1")
        assertNotNull(legacy)
        assertTrue("要告诉用户这是旧命名导致", legacy!!.contains("旧命名"))
        assertNotNull(MapContribute.featureIdError("f19_x"))
        assertNotNull(MapContribute.featureIdError(""))
    }

    @Test
    fun `field rule rejects empty, too long and reserved names`() {
        assertNull(MapContribute.fieldError("层数"))
        assertNotNull(MapContribute.fieldError(""))
        assertNotNull(MapContribute.fieldError("一二三四五六七八九十一二三四五六七八九十一"))  // 21 字
        assertNotNull("保留字段名不能当自定义字段上传", MapContribute.fieldError("name"))
        assertNotNull(MapContribute.fieldError("kind"))
    }

    @Test
    fun `mapVersion must look like a version`() {
        assertTrue(MapContribute.isValidMapVersion("2026.11.14-1"))
        assertTrue(MapContribute.isValidMapVersion("2026.1.2"))
        assertFalse(MapContribute.isValidMapVersion("v1"))
        assertFalse(MapContribute.isValidMapVersion(""))
    }

    // ── 3. 数据整理 ─────────────────────────────────────

    @Test
    fun `patches come from user edits only, one per cell, sorted`() {
        val p = MapContribute.patchesFrom(edits(), ts = "2026-11-14T18:00:00+08:00")
        // f19: name + kind + 层数 + 所属单位 = 4；f20: 层数 = 1
        assertEquals(5, p.size)
        assertEquals("f19|kind", p.first { it.featureId == "f19" && it.field == "kind" }.key)
        // 排序稳定（fid → field），保证同样的输入产生同样的请求体
        assertEquals(p.sortedWith(compareBy({ it.featureId }, { it.field })), p)
    }

    @Test
    fun `value longer than 200 chars is truncated, over-long fields dropped`() {
        val long = "字".repeat(260)
        val bad = MapEdits(
            fieldOrder = listOf("备注", "一二三四五六七八九十一二三四五六七八九十一"),
            features = mapOf("f19" to MapEdit(fields = mapOf(
                "备注" to long,
                "一二三四五六七八九十一二三四五六七八九十一" to "x",
            ))),
        )
        val clean = MapContribute.sanitize(MapContribute.patchesFrom(bad), "2026.11.14-1")
        assertEquals(1, clean.ok.size)
        assertEquals(200, clean.ok.first().value.length)
        assertEquals(1, clean.dropped.size)
        assertTrue(clean.dropped.first().second.contains("字段名"))
    }

    @Test
    fun `bad map version drops everything and says why`() {
        val clean = MapContribute.sanitize(MapContribute.patchesFrom(edits()), "v1")
        assertTrue(clean.ok.isEmpty())
        assertTrue(clean.dropped.isNotEmpty())
        assertTrue(clean.dropped.first().second.contains("地图版本"))
    }

    @Test
    fun `batching never exceeds 200 patches`() {
        val many = (1..450).map { MapContribute.Patch("f$it", "层数", "1", "set", "t") }
        val chunks = MapContribute.chunk(many)
        assertEquals(3, chunks.size)
        assertEquals(listOf(200, 200, 50), chunks.map { it.size })
        assertTrue(MapContribute.chunk(emptyList()).isEmpty())
    }

    @Test
    fun `backoff grows then caps`() {
        assertEquals(0L, MapContribute.backoffMillis(0))
        assertEquals(60_000L, MapContribute.backoffMillis(1))
        assertEquals(3 * 60 * 60_000L, MapContribute.backoffMillis(99))   // 封顶
        assertTrue(MapContribute.shouldTryNow(1000, 999))
        assertFalse(MapContribute.shouldTryNow(1000, 1001))
    }

    // ── 4. 响应解析（按 server.js 的真实形状）───────────────

    @Test
    fun `response parses accepted rejected and array reasons`() {
        val js = """{"accepted":3,"rejected":1,"reasons":[{"featureId":"f19","field":"层数","reasons":["value_too_long"]}],"keys":2}"""
        val r = MapContribute.parseResponse(js)!!
        assertEquals(3, r.accepted)
        assertEquals(1, r.rejected)
        assertEquals(2, r.keys)
        assertEquals("层数", r.reasons.first().field)
        assertEquals("value_too_long", r.reasons.first().reasons.first())
        assertTrue(r.summary().contains("已上传 3 条"))
    }

    @Test
    fun `garbage response is null, not a silent success`() {
        assertNull(MapContribute.parseResponse("not json"))
        assertNull(MapContribute.parseResponse("{}"))                       // 缺 reasons
        assertNull("缺 reasons 数组必须判失败，否则会把失败当成功", MapContribute.parseResponse("""{"accepted":1}"""))
    }

    @Test
    fun `preview line is human readable`() {
        val s = MapContribute.preview(5, 2, listOf("层数", "所属单位"))
        assertTrue(s.contains("5 条"))
        assertTrue(s.contains("2 个地块"))
        assertTrue(s.contains("层数"))
    }
}
