package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapDataset
import cn.dkarbon.enroute.data.map.MapEdit
import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 共享数据集（票 22）的纯逻辑测试。
 *
 * 这一层错一次的后果是**静默的**：别人的数据把你填的盖掉、或者永远拉不到更新，
 * 界面上看不出异常。所以合并、rev 比较、三档优先级全部在这里钉死。
 *
 * 服务端那边的同一套口径在 `tools/map/server_test.js`（18 项）里验，
 * 两边必须对得上 —— 一边改了另一边不跟，这里会红。
 */
class MapDatasetTest {

    private fun fullJson(rev: Long = 7) = """
        {"schema":1,"mapVersion":"2026.11.14-1","rev":$rev,"since":0,"incremental":false,
         "updatedAt":"2026-11-14T18:40:00Z",
         "fields":["层数","所属单位"],
         "features":{"f19":{"层数":"3","所属单位":"体育部"},"f20":{"层数":"2"}},
         "deleted":[]}
    """.trimIndent()

    private fun incJson(rev: Long, since: Long) = """
        {"schema":1,"mapVersion":"2026.11.14-1","rev":$rev,"since":$since,"incremental":true,
         "updatedAt":"2026-11-14T18:50:00Z",
         "fields":["层数"],"features":{"f19":{"层数":"4"}},"deleted":[]}
    """.trimIndent()

    private fun feature(id: String = "f19", props: Map<String, String> = emptyMap()) =
        MapFeature(id = id, name = "九州体育场", props = props)

    // ── 解析 ─────────────────────────────────────────────

    @Test
    fun `parse reads rev features fields`() {
        val ds = MapDataset.parse(fullJson())!!
        assertEquals(7L, ds.rev)
        assertEquals("2026.11.14-1", ds.mapVersion)
        assertEquals(listOf("层数", "所属单位"), ds.fields)
        assertEquals("3", ds.valueOf("f19", "层数"))
        assertEquals("体育部", ds.valueOf("f19", "所属单位"))
        assertEquals("2", ds.valueOf("f20", "层数"))
        assertNull(ds.valueOf("f19", "不存在"))
    }

    @Test
    fun `parse refuses a payload without rev - no rev means we cannot reason about freshness`() {
        assertNull(MapDataset.parse("""{"mapVersion":"x","features":{"f19":{"层数":"3"}}}"""))
        assertNull(MapDataset.parse("not json"))
    }

    @Test
    fun `deleted accepts objects and pipe strings`() {
        val a = MapDataset.parse("""{"rev":3,"features":{},"deleted":[{"featureId":"f19","field":"层数"}]}""")!!
        assertEquals("f19", a.deleted.first().featureId)
        val b = MapDataset.parse("""{"rev":3,"features":{},"deleted":["f20|所属单位"]}""")!!
        assertEquals("所属单位", b.deleted.first().field)
    }

    // ── 增量合并（since 语义）──────────────────────────────

    @Test
    fun `incremental merge replaces touched cells and keeps everything else`() {
        val base = MapDataset.parse(fullJson(rev = 7))!!
        val merged = MapDataset.merge(base, MapDataset.parse(incJson(rev = 9, since = 7))!!)
        assertEquals("4", merged.valueOf("f19", "层数"))       // 被碰过 → 新值
        assertEquals("体育部", merged.valueOf("f19", "所属单位"))  // 没被碰 → 保留
        assertEquals("2", merged.valueOf("f20", "层数"))        // 别的面 → 保留
        assertEquals(9L, merged.rev)
    }

    @Test
    fun `merge never lets rev go backwards`() {
        val base = MapDataset.parse(fullJson(rev = 9))!!
        val older = MapDataset.parse(incJson(rev = 5, since = 1))!!
        assertEquals("乱序响应不能让本地版本倒退", 9L, MapDataset.merge(base, older).rev)
    }

    @Test
    fun `delete removes the field and drops the feature when it becomes empty`() {
        val base = MapDataset.parse(fullJson())!!
        val del = MapDataset.parse(
            """{"rev":8,"features":{},"deleted":[{"featureId":"f20","field":"层数"}]}""",
        )!!
        val merged = MapDataset.merge(base, del)
        assertNull(merged.valueOf("f20", "层数"))
        assertFalse("面空了就该整条移除", merged.hasFeature("f20"))
        assertEquals("别的面不受影响", "3", merged.valueOf("f19", "层数"))
    }

    @Test
    fun `merge onto null base is the full pull`() {
        val merged = MapDataset.merge(null, MapDataset.parse(fullJson())!!)
        assertEquals(2, merged.features.size)
        assertTrue(merged.deleted.isEmpty())
    }

    // ── 5 分钟窗口 ────────────────────────────────────────

    @Test
    fun `shouldPull respects the five minute window and clock rollback`() {
        val now = 1_700_000_000_000L
        assertTrue("从没拉过 → 拉", MapDataset.shouldPull(now, 0L))
        assertTrue("刚好 5 分钟 → 拉", MapDataset.shouldPull(now, now - MapDataset.FIVE_MIN))
        assertFalse("4 分 59 秒 → 不拉", MapDataset.shouldPull(now, now - MapDataset.FIVE_MIN + 1000))
        assertTrue("时钟回拨也要拉一次", MapDataset.shouldPull(now, now + MapDataset.FIVE_MIN * 3))
    }

    // ── 三档来源（票 22 的核心）──────────────────────────

    @Test
    fun `local value not yet on server shows as pending and stays editable`() {
        val ds = MapDataset.parse(fullJson())!!
        val edits = MapEdits(
            fieldOrder = listOf("层数"),
            features = mapOf("f19" to MapEdit(fields = mapOf("层数" to "5"))),   // 服务器是 3
        )
        val row = MapDataset.rows(feature(), edits, ds, listOf("层数")).first { it.name == "层数" }
        assertEquals("5", row.value)
        assertEquals(MapDataset.Source.PENDING, row.source)
        assertTrue("待上传必须可编辑", row.editable)
        assertEquals("待上传", MapDataset.label(row.source))
    }

    @Test
    fun `value equal to the server value counts as shared (upload succeeded)`() {
        val ds = MapDataset.parse(fullJson())!!
        val edits = MapEdits(
            fieldOrder = listOf("层数"),
            features = mapOf("f19" to MapEdit(fields = mapOf("层数" to "3"))),   // 与服务器一致
        )
        val row = MapDataset.rows(feature(), edits, ds, listOf("层数")).first { it.name == "层数" }
        assertEquals(MapDataset.Source.SHARED, row.source)
        assertFalse("已生效的不该再让人编辑（避免误解成本地未提交）", row.editable)
        assertEquals("共建", MapDataset.label(row.source))
    }

    @Test
    fun `server value beats builtin, builtin is the last resort`() {
        val ds = MapDataset.parse(fullJson())!!
        val feat = feature(props = mapOf("层数" to "1", "开放时间" to "06:00-22:00"))
        // 服务器对 f19 有「层数」「所属单位」，没有「开放时间」→ 后者退回内置
        val rows = MapDataset.rows(feat, MapEdits(), ds, listOf("层数", "所属单位", "开放时间"))
        val byName = rows.associateBy { it.name }

        assertEquals("服务器值必须盖过内置值", "3", byName["层数"]!!.value)
        assertEquals(MapDataset.Source.SHARED, byName["层数"]!!.source)

        assertEquals("体育部", byName["所属单位"]!!.value)
        assertEquals(MapDataset.Source.SHARED, byName["所属单位"]!!.source)

        assertEquals("服务器没有的字段退回内置", "06:00-22:00", byName["开放时间"]!!.value)
        assertEquals(MapDataset.Source.BUILTIN, byName["开放时间"]!!.source)
        assertEquals("内置", MapDataset.label(MapDataset.Source.BUILTIN))
    }

    @Test
    fun `a declared field with no value anywhere does not occupy a row - but a user-added slot does`() {
        // 声明了「层数」但这面没值、服务器也没有 → 不占行（否则 90 个面全是空行）
        val declaredOnly = MapDataset.rows(feature(props = emptyMap()), MapEdits(), null, listOf("层数"))
        assertTrue(declaredOnly.isEmpty())

        // 用户自己加的「备注」即使还空着，也必须留一行 ——
        // 否则「＋ 添加字段」之后没有输入框可填（这是功能缺陷，不是样式问题）
        val withUserSlot = MapDataset.rows(
            feature(props = emptyMap()),
            MapEdits(fieldOrder = listOf("备注")),
            null,
            listOf("层数"),
        )
        assertEquals(1, withUserSlot.size)
        assertEquals("备注", withUserSlot.first().name)
        assertEquals("", withUserSlot.first().value)
        assertTrue("空的自有格子必须可编辑", withUserSlot.first().editable)
    }

    @Test
    fun `server-only field appears even when the feature has no builtin value`() {
        val ds = MapDataset.parse(fullJson())!!
        val rows = MapDataset.rows(feature(id = "f20"), MapEdits(), ds, emptyList())
        assertEquals(1, rows.size)
        assertEquals("层数", rows.first().name)
        assertEquals(MapDataset.Source.SHARED, rows.first().source)
    }

    // ── 缓存往返（写坏 = 别人的数据"突然消失"）──────────────

    @Test
    fun `encode then parse round-trips losslessly`() {
        val ds = MapDataset.parse(fullJson())!!
        val back = MapDataset.parse(MapDataset.encode(ds))!!
        assertEquals(ds.rev, back.rev)
        assertEquals(ds.mapVersion, back.mapVersion)
        assertEquals(ds.fields, back.fields)
        assertEquals(ds.features, back.features)
    }

    @Test
    fun `encode escapes quotes and newlines`() {
        val ds = MapDataset.parse("""{"rev":1,"features":{"f1":{"备注":"含\"引号\"\n与换行"}}}""")!!
        val back = MapDataset.parse(MapDataset.encode(ds))!!
        assertNotNull(back)
        assertEquals("含\"引号\"\n与换行", back.valueOf("f1", "备注"))
    }

    @Test
    fun `a full pull supersedes stale cache rev`() {
        val stale = MapDataset.parse(fullJson(rev = 12))!!
        val fresh = MapDataset.parse(fullJson(rev = 15))!!
        val merged = MapDataset.merge(stale, fresh)
        assertEquals(15L, merged.rev)
        assertEquals(2, merged.features.size)
    }

    // ── 空库首次拉取（线上刚部署、还没有任何补丁）──────────

    @Test
    fun `empty dataset from a fresh server parses as rev 0 with no features`() {
        val ds = MapDataset.parse("""{"schema":1,"mapVersion":"2026.11.14-1","rev":0,"fields":[],"features":{},"deleted":[]}""")
        assertNotNull("空库也是合法响应，不能当成解析失败", ds)
        assertEquals(0L, ds!!.rev)
        assertTrue(ds.isEmpty)
    }

    @Test
    fun `empty dataset behaves exactly like no dataset - everything falls back to builtin`() {
        val feat = feature(props = mapOf("层数" to "3", "所属单位" to "体育部"))
        val empty = MapDataset.parse("""{"rev":0,"fields":[],"features":{}}""")!!
        val withEmpty = MapDataset.rows(feat, MapEdits(), empty, listOf("层数", "所属单位"))
        val withNull = MapDataset.rows(feat, MapEdits(), null, listOf("层数", "所属单位"))

        assertEquals("空基线不能改变任何一行", withNull, withEmpty)
        assertEquals(2, withEmpty.size)
        withEmpty.forEach {
            assertEquals(MapDataset.Source.BUILTIN, it.source)   // 全部回落「内置」
            assertFalse(it.editable)
        }
        assertEquals("3", withEmpty.first { it.name == "层数" }.value)
    }

    @Test
    fun `merging an empty first pull keeps a usable cache and does not invent values`() {
        val incoming = MapDataset.parse("""{"rev":0,"fields":[],"features":{}}""")!!
        val merged = MapDataset.merge(null, incoming)
        assertEquals(0L, merged.rev)
        assertTrue(merged.features.isEmpty())
        // 用空缓存渲染：仍然全部是内置（不会被"空值"覆盖成空）
        val rows = MapDataset.rows(feature(props = mapOf("层数" to "3")), MapEdits(), merged, listOf("层数"))
        assertEquals("3", rows.single().value)
        assertEquals(MapDataset.Source.BUILTIN, rows.single().source)
    }
}
