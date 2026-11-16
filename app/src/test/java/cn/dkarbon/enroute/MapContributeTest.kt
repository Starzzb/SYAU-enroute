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
 * 2026-11-16 起：学号随补丁发送（只从内存态学籍取、不落盘），所以请求体里
 * **必须**出现 `sid`；不再有 shouldSend / preview 这类多重闸门。
 * 隐私红线剩下的部分：不发定位、不发姓名本身、不发 `userKey` 这种服务端不认的键。
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

    private fun client() = MapContribute.ClientInfo(
        did = "anon-1234",
        appVer = "0.3.0+m20",
        manufacturer = "vivo",
        model = "V2301A",
        device = "PD2301",
        sdkInt = 36,
        release = "16",
        ssaid = "ssaid-abcdef012345",
        installId = "install-xyz",
        sid = "2024156048",
    )

    @Test
    fun `request body carries exactly the declared client fields`() {
        val body = MapContribute.body(
            mapVersion = "2026.11.14-1",
            client = client(),
            patches = MapContribute.patchesFrom(edits()),
        )
        // 允许的
        assertTrue(body.contains("\"mapVersion\":\"2026.11.14-1\""))
        assertTrue(body.contains("\"featureId\":\"f19\""))
        // 票 22：机型/系统/设备标识**现在要发**（主人要求收集上传者信息）
        assertTrue(body.contains("\"did\":\"anon-1234\""))
        assertTrue(body.contains("\"manufacturer\":\"vivo\""))
        assertTrue(body.contains("\"model\":\"V2301A\""))
        assertTrue(body.contains("\"device\":\"PD2301\""))
        assertTrue(body.contains("\"sdkInt\":36"))
        assertTrue(body.contains("\"release\":\"16\""))
        assertTrue(body.contains("\"ssaid\":\"ssaid-abcdef012345\""))
        assertTrue(body.contains("\"installId\":\"install-xyz\""))
        assertTrue(body.contains("\"appVer\":\"0.3.0+m20\""))
        assertTrue(body.contains("\"hasName\":false"))
        // 2026-11-16 起：学号随补丁发送（服务端只落 sid_enc/HMAC，明文不落盘）
        assertTrue("学号必须随补丁发送", body.contains("\"sid\":\"2024156048\""))
        // **禁止的**（红线没变的部分）
        assertFalse("服务端不认 userKey 这个名字", body.contains("userKey"))
        assertFalse("不许出现定位", body.contains("\"lat\"") || body.contains("\"lng\"") || body.contains("location"))
        assertFalse("不许出现中文姓名键", body.contains("姓名"))
        // 注：`"field":"name"` 是**字段名**（固定字段 name/kind 也要上传），不是隐私泄露
        assertTrue(body.contains("\"field\":\"name\""))
    }

    @Test
    fun `sid is omitted when blank - not logged in`() {
        val noSid = client().copy(sid = "")
        val body = MapContribute.body("2026.11.14-1", noSid, emptyList())
        assertFalse("没登录就不出现 sid 键", body.contains("\"sid\""))
    }

    @Test
    fun `client block has no key beyond the declared ones`() {
        val body = MapContribute.body("2026.11.14-1", client(), emptyList())
        val start = body.indexOf("\"client\":{") + "\"client\":".length   // 从 `{` 开始，别把 `"client"` 自己算进去
        val end = body.indexOf('}', start)
        val block = body.substring(start, end + 1)
        val keys = Regex("\"(\\w+)\":").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(
            "client 里只能有这些键；多一个都算偷偷多传",
            setOf(
                "did", "sid", "hasName", "manufacturer", "model", "device", "sdkInt",
                "release", "ssaid", "installId", "appVer",
            ),
            keys,
        )
    }

    @Test
    fun `ssaid unavailable is an empty string, never a placeholder`() {
        val noSsaid = client().copy(ssaid = "")
        val body = MapContribute.body("2026.11.14-1", noSsaid, emptyList())
        assertTrue("拿不到就该是空串", body.contains("\"ssaid\":\"\""))
        assertFalse("绝不能编占位串", body.contains("unknown"))
        assertFalse(body.contains("\"ssaid\":\"0"))
    }

    @Test
    fun `sdkInt 0 is omitted instead of sending a fake value`() {
        val noSdk = client().copy(sdkInt = 0)
        val body = MapContribute.body("2026.11.14-1", noSdk, emptyList())
        assertFalse("取不到就不发这个键，别写 0", body.contains("\"sdkInt\""))
    }

    @Test
    fun `anonymous key is random and not a hardware id`() {
        val a = MapContribute.newAnonymousKey()
        val b = MapContribute.newAnonymousKey()
        assertTrue(a.startsWith("anon-"))
        assertTrue("两次必须不同", a != b)
        assertEquals("anon- 前缀 + UUID 长度", 5 + 36, a.length)
        // 「应用级设备标识」是另一件事：Android ID / installId 由系统与本地各自生成，
        // 这里只确认本地随机串不带任何硬件前缀
        assertFalse(a.contains("imei"))
        assertFalse(a.contains("serial"))
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
    fun `field rule rejects empty, too long and derived names - but allows name and kind`() {
        assertNull(MapContribute.fieldError("层数"))
        assertNotNull(MapContribute.fieldError(""))
        assertNotNull(MapContribute.fieldError("一二三四五六七八九十一二三四五六七八九十一"))  // 21 字
        // name / kind 是**一等可上传字段**（众包最缺的就是名称与类别），绝不能拒
        assertNull("名称必须能上传", MapContribute.fieldError("name"))
        assertNull("类别必须能上传", MapContribute.fieldError("kind"))
        // 派生量仍然拒（服务端没有几何，传了没意义）
        listOf("id", "colorGroup", "color", "areaPx").forEach {
            val why = MapContribute.fieldError(it)
            assertNotNull("派生量 $it 必须被拒", why)
            assertTrue("要说人话：$why", why!!.contains("派生量"))
        }
    }

    @Test
    fun `name and kind actually survive sanitize as two set patches`() {
        // 真机踩过的坑：生成侧要传 name/kind，校验侧又把它们拒掉 →「没有可上传的内容」
        val e = MapEdits(
            fieldOrder = emptyList(),
            features = mapOf("f19" to MapEdit(name = "九州体育场", kind = "sport")),
        )
        val raw = MapContribute.patchesFrom(e)
        val clean = MapContribute.sanitize(raw, "2026.11.14-1")

        assertEquals("名称 + 类别 = 2 条", 2, clean.ok.size)
        assertTrue("不能被当成不合格跳过", clean.dropped.isEmpty())
        val byField = clean.ok.associateBy { it.field }
        assertEquals("九州体育场", byField["name"]!!.value)
        assertEquals("sport", byField["kind"]!!.value)
        assertEquals("set", byField["name"]!!.op)
        assertEquals("set", byField["kind"]!!.op)
    }

    @Test
    fun `derived keys are dropped with a human reason`() {
        val raw = listOf(
            MapContribute.Patch("f19", "areaPx", "12704.5", "set", "t"),
            MapContribute.Patch("f19", "层数", "3", "set", "t"),
        )
        val clean = MapContribute.sanitize(raw, "2026.11.14-1")
        assertEquals(1, clean.ok.size)
        assertEquals("层数", clean.ok.first().field)
        assertEquals(1, clean.dropped.size)
        assertTrue(clean.dropped.first().second.contains("派生量"))
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
}
