package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapAnnotationImport
import cn.dkarbon.enroute.data.map.MapEdit
import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapEditsOps
import cn.dkarbon.enroute.data.map.MapExport
import cn.dkarbon.enroute.data.map.MapFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * m32 迁移用的导入路径：**导出 → 导入必须能走回来**。
 *
 * 背景：2026-11-20 release 私钥被误删，换签名后旧版只能卸载重装，`edits.json` 会被系统清掉。
 * 用户能带走的只有导出过的那份 `campus_annotated_*.geojson` —— 这条往返一旦断了，
 * 等于换个签名就把所有人的标注作废，所以单测直接锁「往返等价」。
 */
class MapAnnotationImportTest {

    private fun official() = listOf(
        MapFeature(id = "f19", name = "三教", kind = "teach", props = mapOf("层数" to "3")),
        MapFeature(id = "f30", name = "九州体育场", kind = "sport"),
    )

    private fun annotated(): MapEdits {
        var e = MapEdits()
        e = MapEditsOps.addField(e, "层数")
        e = MapEditsOps.addField(e, "备注")
        e = MapEditsOps.setName(e, "f19", "第三教学楼")
        e = MapEditsOps.setKind(e, "f19", "hall")
        e = MapEditsOps.setField(e, "f19", "层数", "5")
        e = MapEditsOps.setField(e, "f19", "备注", "东门斜对面")
        e = MapEditsOps.setField(e, "f30", "备注", "晚上开灯")
        return e
    }

    @Test
    fun `导出再导入，标注等价`() {
        val edits = annotated()
        val geo = MapExport.geoJson(official(), edits)

        val r = MapAnnotationImport.parse(geo, official(), MapEdits(), "2026.11.17-1")
        assertEquals("", r.error)
        assertEquals(2, r.features)
        assertEquals("官方 f19 层数=3、标注 5：导入应写回覆盖值 5", "5", r.edits.features["f19"]?.fields?.get("层数"))
        assertEquals("第三教学楼", r.edits.features["f19"]?.name)
        assertEquals("hall", r.edits.features["f19"]?.kind)
        assertEquals("晚上开灯", r.edits.features["f30"]?.fields?.get("备注"))
        // 字段表并集：已有顺序在前，新学的追加
        assertEquals(listOf("层数", "备注"), r.edits.fieldOrder)
        assertEquals("2026.11.17-1", r.edits.mapVersion)
    }

    @Test
    fun `官方值与导出值相同的项不写进标注`() {
        // 只有几何、没有用户改动的导出：导入后应当仍是空标注，不能把官方值冻进 edits
        val geo = MapExport.geoJson(official(), MapEdits())
        val r = MapAnnotationImport.parse(geo, official(), MapEdits(), "v")
        assertEquals(0, r.features)
        assertTrue(r.edits.isEmpty)
    }

    @Test
    fun `找不到的 id 计入跳过，不塞给相邻要素`() {
        val geo = """{"type":"FeatureCollection","features":[
            {"type":"Feature","id":"f19","properties":{"id":"f19","name":"三教","备注":"保留"},
             "geometry":{"type":"Polygon","coordinates":[]}},
            {"type":"Feature","id":"f999","properties":{"备注":"这版的图里没有"},
             "geometry":{"type":"Polygon","coordinates":[]}}
        ]}"""
        val r = MapAnnotationImport.parse(geo, official(), MapEdits(), "v")
        assertEquals(1, r.skipped)
        assertEquals(1, r.features)
        assertEquals(null, r.edits.features["f999"])
        assertEquals("保留", r.edits.features["f19"]?.fields?.get("备注"))
    }

    @Test
    fun `与已有标注合并——同 id 以导入为准，其它保留`() {
        var existing = MapEditsOps.addField(MapEdits(), "备注")
        existing = MapEditsOps.setField(existing, "f30", "备注", "旧的")
        existing = MapEditsOps.setName(existing, "f30", "旧名字")

        val geo = MapExport.geoJson(official(), annotated())
        val r = MapAnnotationImport.parse(geo, official(), existing, "v")

        // f30 在导出里没有改 name（导出的是官方名），所以导入保留旧标注而不是抹掉
        assertEquals("旧名字", r.edits.features["f30"]?.name)
        assertEquals("晚上开灯", r.edits.features["f30"]?.fields?.get("备注"))
        assertEquals("第三教学楼", r.edits.features["f19"]?.name)
    }

    @Test
    fun `坏文件只报错不炸`() {
        assertEquals("不是 JSON 对象", MapAnnotationImport.parse("这不是 JSON", official()).error)
        assertEquals("没有 features 数组", MapAnnotationImport.parse("""{"type":"X"}""", official()).error)
        // 空集合是合法的（导入一份没有标注的导出）：不算错，也不该产生条目
        val empty = MapAnnotationImport.parse("""{"type":"FeatureCollection","features":[]}""", official())
        assertEquals("", empty.error)
        assertEquals(0, empty.features)
    }

    @Test
    fun `保留名字段不会被当成自定义列`() {
        val geo = """{"type":"FeatureCollection","features":[
            {"type":"Feature","id":"f19","properties":{"id":"f19","name":"三教","kind":"teach",
             "colorGroup":"7","color":"#8DAB87","areaPx":"12.5","备注":"x"}}
        ]}"""
        val r = MapAnnotationImport.parse(geo, official(), MapEdits(), "v")
        assertEquals(listOf("备注"), r.edits.fieldOrder)
        assertEquals(MapEdit(name = "", kind = "", fields = mapOf("备注" to "x")), r.edits.features["f19"])
    }
}
