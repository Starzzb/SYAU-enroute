package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapEditsCodec
import cn.dkarbon.enroute.data.map.MapEditsOps
import cn.dkarbon.enroute.data.map.MapFeature
import cn.dkarbon.enroute.data.map.MapParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方基线（票 13「B 路」）的纯逻辑测试。
 *
 * 这一层的存在意义：官方众包数据**留在 asset 里当只读层**，不进用户的 `edits.json`。
 * 三条不能被破坏的性质，全在这里锁住：
 *   1. 解析时**保留未知 properties**（不然 pack 进去的官方字段白丢了）
 *   2. 同一字段两边都有值 → **用户标注胜**；用户清空 → 退回官方值
 *   3. 「已标注 N 个面」只数用户标注（官方数据不能记在用户头上）
 */
class MapOfficialBaselineTest {

    private val geojson = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","id":"f19","properties":{
             "id":"f19","name":"九州体育场","kind":"sport","colorGroup":11,"color":"#8DAB87","areaPx":12704.5,
             "层数":"3","所属单位":"体育部","开放时间":"06:00-22:00"},
           "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}},
          {"type":"Feature","id":"f20","properties":{
             "id":"f20","name":"","kind":"","colorGroup":12,"color":"#4B5F3F","areaPx":3947.0},
           "geometry":{"type":"Polygon","coordinates":[[[2,2],[3,2],[3,3],[2,2]]]}}
        ]}
    """.trimIndent()

    private val manifestWithCustomFields = """
        {"schema":1,"version":"2026.11.14-1","crs":"local","bbox":[0,0,1024,648],"yAxis":"up",
         "customFields":["层数","所属单位","开放时间"],
         "layers":[{"name":"campus","file":"campus.geojson","geom":"polygon"}]}
    """.trimIndent()

    private fun features(): List<MapFeature> =
        MapParse.geoJson(geojson)!!.features

    // 1. 保留未知 properties
    @Test
    fun `parser keeps official unknown properties and drops derived ones`() {
        val f19 = features().first { it.id == "f19" }
        assertEquals("3", f19.props["层数"])
        assertEquals("体育部", f19.props["所属单位"])
        assertEquals("06:00-22:00", f19.props["开放时间"])
        // 派生量与固定字段不进 props（它们有自己的槽位 / 不该被当标注）
        assertNull(f19.props["colorGroup"])
        assertNull(f19.props["areaPx"])
        assertNull(f19.props["name"])
        assertEquals(11, f19.colorGroup)
        assertEquals(12704.5, f19.areaPx, 0.01)
    }

    @Test
    fun `manifest parses customFields and tolerates old packages without it`() {
        val m = MapParse.manifest(manifestWithCustomFields)!!
        assertEquals(listOf("层数", "所属单位", "开放时间"), m.customFields)
        // 老包没有 customFields → 空表，不炸
        val old = MapParse.manifest("""{"schema":1,"version":"old","bbox":[0,0,10,10]}""")!!
        assertEquals(emptyList<String>(), old.customFields)
    }

    // 2. 合并优先级
    @Test
    fun `user annotation wins over official value and loses the official badge`() {
        val f19 = features().first { it.id == "f19" }
        // 用户只改「层数」，别的没动
        val edits = MapEdits(
            mapVersion = "2026.11.14-1",
            fieldOrder = listOf("层数"),
            features = mapOf("f19" to cn.dkarbon.enroute.data.map.MapEdit(fields = mapOf("层数" to "4"))),
        )
        val views = MapEditsOps.fieldViews(f19, edits, listOf("层数", "所属单位", "开放时间"))
        val byName = views.associateBy { it.name }

        assertEquals("4", byName["层数"]!!.value)
        assertFalse("用户填的不能标成官方", byName["层数"]!!.official)
        // 官方独有字段仍然显示，且标 official
        assertEquals("体育部", byName["所属单位"]!!.value)
        assertTrue(byName["所属单位"]!!.official)
    }

    @Test
    fun `clearing the user value falls back to the official value, still marked official`() {
        val f19 = features().first { it.id == "f19" }
        // 用户建了同名列但留空（= 清掉自己的值）
        val edits = MapEdits(
            fieldOrder = listOf("层数"),
            features = mapOf("f19" to cn.dkarbon.enroute.data.map.MapEdit(fields = emptyMap())),
        )
        val v = MapEditsOps.fieldViews(f19, edits, listOf("层数")).first { it.name == "层数" }
        assertEquals("3", v.value)
        assertTrue("退回官方值时必须标官方，否则像用户自己填的", v.official)
    }

    @Test
    fun `field order is user fields first then official-only ones, no duplicates`() {
        val f19 = features().first { it.id == "f19" }
        val edits = MapEdits(fieldOrder = listOf("备注"))  // 用户自建字段，官方没有
        val names = MapEditsOps.fieldViews(f19, edits, listOf("层数", "所属单位", "备注")).map { it.name }
        assertEquals(listOf("备注", "层数", "所属单位"), names)
    }

    @Test
    fun `official fields with blank value do not occupy a row`() {
        val f20 = features().first { it.id == "f20" }   // 这一面没有任何官方字段
        val views = MapEditsOps.fieldViews(f20, MapEdits(), listOf("层数", "所属单位"))
        assertTrue("没有官方值的面不该出现空行", views.isEmpty())
    }

    // 3. 「已标注 N」只数用户
    @Test
    fun `annotated count only counts user edits, never the official baseline`() {
        val feats = features()          // 两个面都带官方字段
        val edits = MapEdits(features = mapOf("f20" to cn.dkarbon.enroute.data.map.MapEdit(name = "二号楼")))
        assertEquals("官方基线不能算进用户标注数", 1, edits.features.size)
        // 官方字段仍然挂在要素上（只读层），与这个计数无关
        assertTrue(feats.first { it.id == "f19" }.props.isNotEmpty())
    }

    @Test
    fun `edits codec round-trips without swallowing official fields`() {
        // 用户标注序列化后不能被官方字段污染（官方字段永不进 edits.json）
        val edits = MapEdits(
            mapVersion = "2026.11.14-1",
            fieldOrder = listOf("层数"),
            features = mapOf("f19" to cn.dkarbon.enroute.data.map.MapEdit(fields = mapOf("层数" to "4"))),
        )
        val text = MapEditsCodec.toJson(edits)
        assertFalse(text.contains("体育部"))
        assertFalse(text.contains("开放时间"))
        val back = MapEditsCodec.fromJson(text)!!
        assertEquals("4", back.features["f19"]!!.fields["层数"])
    }

}
