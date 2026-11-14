package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapEditsCodec
import cn.dkarbon.enroute.data.map.MapEditsOps
import cn.dkarbon.enroute.data.map.MapExport
import cn.dkarbon.enroute.data.map.MapFeature
import cn.dkarbon.enroute.data.map.MapHit
import cn.dkarbon.enroute.data.map.MapIssue
import cn.dkarbon.enroute.data.map.MapLayers
import cn.dkarbon.enroute.data.map.MapManifest
import cn.dkarbon.enroute.data.map.MapPackageRules
import cn.dkarbon.enroute.data.map.MapParse
import cn.dkarbon.enroute.data.map.MapPoint
import cn.dkarbon.enroute.data.map.MapProjection
import cn.dkarbon.enroute.data.map.MapTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校园地图 v1（票 12）：解析、等比映射、**y 轴翻转**、射线命中、逻辑层拆分、标注编辑与导出。
 *
 * 夹具是**真数据**（`assets/map/manifest.json` + `campus.geojson`，90 面，原样拷贝）：
 * 想验证「字段全空、只有一条有名字」这种现状时用它；
 * 射线命中/洞这类需要精确坐标的，用合成小多边形（确定性更好）。
 */
class MapTest {

    private fun res(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("map/$name")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun manifest() = MapParse.manifest(res("manifest.json"))!!
    private fun features() = MapParse.geoJson(res("campus.geojson"))!!.features

    private fun square(id: String, x0: Double, y0: Double, x1: Double, y1: Double) = MapFeature(
        id = id,
        name = id,
        kind = "other",
        rings = listOf(
            listOf(
                MapPoint(x0, y0), MapPoint(x1, y0), MapPoint(x1, y1), MapPoint(x0, y1), MapPoint(x0, y0),
            ),
        ),
    )

    // ── manifest / geojson 解析（真数据）──────────────────

    @Test
    fun `真数据 manifest 的字段都对得上`() {
        val m = manifest()
        assertEquals(1, m.schema)
        assertEquals("2026.11.14-1", m.version)
        assertEquals("local", m.crs)
        assertEquals("up", m.yAxis)   // y 轴向上：翻转的责任在 MapTransform
        assertTrue(m.isValidBox)
        assertEquals(listOf(0.0, 0.0, 1024.0, 648.0), m.bbox)
        // 样式驱动渲染：热区很淡（0.03），不是界面里写死的数
        assertEquals(0.03f, m.style.fillAlpha, 1e-6f)
        assertEquals(0.22f, m.style.strokeAlpha, 1e-6f)
        assertEquals(0.3f, m.style.selectedFillAlpha, 1e-6f)
        // 19 个类别码 → 属性面板的「类别」下拉
        assertEquals(19, m.kindLabels.size)
        assertEquals("教学楼", m.kindLabels["teach"])
        assertEquals("运动场", m.kindLabels["sport"])
        assertEquals(listOf("base@1x.webp", 1024, 648).toString(), listOf(m.base?.file, m.base?.width, m.base?.height).toString())
        assertNotNull(m.layers.firstOrNull { it.name == "campus" })
    }

    @Test
    fun `真数据 90 个面且字段空是现状`() {
        val fs = features()
        assertEquals(90, fs.size)
        // 主人只标了一条名字，类别全空 —— 这正是「属性编辑」要解决的
        assertEquals(1, fs.count { it.name.isNotBlank() })
        assertEquals(0, fs.count { it.kind.isNotBlank() })
        val f19 = fs.first { it.id == "f19" }
        assertEquals("九州体育场", f19.name)
        assertTrue("矢量化色值要带上", f19.color.startsWith("#"))
        assertTrue("pack.py 算好的面积要带上", f19.areaPx > 0.0)
        // ⚠️ 源数据 id 不唯一（实测 f0 出现 43 次）——解析层临时加序号兜住，
        //    这样标注不会 43 个面共用一条；解析时也要把重复次数报出来让界面提示
        assertEquals(fs.size, fs.map { it.id }.distinct().size)
        val doc = MapParse.geoJson(res("campus.geojson"))!!
        assertEquals(42, doc.duplicateIds)
        assertTrue(fs.any { it.id == "f0#2" })
    }

    @Test
    fun `多环洞的面能解析且面积扣掉洞`() {
        val raw = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"id":"h1"},
            "geometry":{"type":"Polygon","coordinates":[
              [[0,0],[10,0],[10,10],[0,10],[0,0]],
              [[4,4],[6,4],[6,6],[4,6],[4,4]]]}}]}"""
        val doc = MapParse.geoJson(raw)!!
        assertEquals(1, doc.features.size)
        assertEquals(0, doc.nonPolygon)
        val f = doc.features.first()
        assertEquals(2, f.rings.size)
        // 100 - 4 = 96
        assertEquals(96.0, f.area, 1e-6)
    }

    @Test
    fun `非 JSON 与无 features 都返回 null`() {
        assertNull(MapParse.geoJson("not json"))
        assertNull(MapParse.geoJson("{}"))
        assertNull(MapParse.manifest("not json"))
    }

    // ── bbox 等比映射 / y 轴翻转 ──────────────────────────

    @Test
    fun `等比映射并居中留边`() {
        // 1024x648 塞进同尺寸画布：scale=1、无偏移
        val same = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 1024f, 648f)
        assertEquals(1.0, same.scale, 1e-9)
        assertEquals(0.0, same.offsetX, 1e-6)
        assertEquals(0.0, same.offsetY, 1e-6)

        // 非等比画布 800x800：按 min(800/1024, 800/648) 取宽，等比不拉伸
        val square = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 800f, 800f)
        assertEquals(800.0 / 1024.0, square.scale, 1e-9)
        assertEquals((800.0 - 648.0 * square.scale) / 2.0, square.offsetY, 1e-6)
    }

    @Test
    fun `y 轴向上要翻成屏幕 y 向下`() {
        val t = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 1024f, 648f)
        // 源左下角 (0,0) → 屏幕左下（y=648）
        assertEquals(648f, t.screenY(0.0), 1e-3f)
        // 源顶部 y=648 → 屏幕 y=0
        assertEquals(0f, t.screenY(648.0), 1e-3f)
        // 中间不动
        assertEquals(324f, t.screenY(324.0), 1e-3f)
        // x 不翻
        assertEquals(0f, t.screenX(0.0), 1e-3f)
        assertEquals(1024f, t.screenX(1024.0), 1e-3f)
        // 反向也要能回去
        assertEquals(123.0, t.sourceY(t.screenY(123.0)), 1e-6)
        assertEquals(456.0, t.sourceX(t.screenX(456.0)), 1e-6)
    }

    @Test
    fun `用户缩放围绕画布中心且能反解`() {
        val base = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 1000f, 600f)
        val z2 = MapProjection.withUser(base, 2.0, 0f, 0f, 1000f, 600f)
        // 画布中心点缩放后不动
        val cx = base.screenX(512.0)
        val cy = base.screenY(324.0)
        assertEquals(cx, z2.screenX(512.0), 1e-3f)
        assertEquals(cy, z2.screenY(324.0), 1e-3f)
        // 比例翻倍
        assertEquals(base.scale * 2.0, z2.scale, 1e-9)
        // 缩放上限 8
        assertEquals(base.scale * 8.0, MapProjection.withUser(base, 99.0, 0f, 0f, 1000f, 600f).scale, 1e-9)
    }

    // ── 命中测试 ─────────────────────────────────────────

    @Test
    fun `射线法判面内且洞不算命中`() {
        val outer = listOf(
            MapPoint(0.0, 0.0), MapPoint(10.0, 0.0), MapPoint(10.0, 10.0), MapPoint(0.0, 10.0),
        )
        assertTrue(MapHit.pointInRing(5.0, 5.0, outer))
        assertFalse(MapHit.pointInRing(15.0, 5.0, outer))
        assertFalse(MapHit.pointInRing(-1.0, 5.0, outer))

        val withHole = MapFeature(
            id = "h", rings = listOf(
                outer + MapPoint(0.0, 0.0),
                listOf(MapPoint(4.0, 4.0), MapPoint(6.0, 4.0), MapPoint(6.0, 6.0), MapPoint(4.0, 6.0)),
            ),
        )
        assertFalse("洞里的点不算命中", MapHit.contains(withHole, 5.0, 5.0))
        assertTrue("洞外的点算命中", MapHit.contains(withHole, 2.0, 2.0))
    }

    @Test
    fun `命中多个取面积最小`() {
        val big = square("big", 0.0, 0.0, 100.0, 100.0)
        val small = square("small", 40.0, 40.0, 60.0, 60.0)
        val hit = MapHit.pick(listOf(big, small), 50.0, 50.0)
        assertEquals("小面优先，别被大绿地吞掉", "small", hit?.id)
        // 只在大面里时命中大面
        assertEquals("big", MapHit.pick(listOf(big, small), 10.0, 10.0)?.id)
    }

    @Test
    fun `射线未中时用距离兜底二十dp`() {
        val tiny = square("gate", 100.0, 100.0, 104.0, 104.0)   // 4x4 的小面
        assertEquals("点偏出去 2px 也该点中", "gate", MapHit.nearest(listOf(tiny), 106.0, 102.0, 5.0)?.id)
        assertNull("超出兜底半径就不给", MapHit.nearest(listOf(tiny), 200.0, 200.0, 5.0))

        // pickOrNearest：命中优先，未中再兜底（24dp 由调用方换算成源距离）
        val t = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 1024f, 648f)
        assertEquals("gate", MapHit.pickOrNearest(listOf(tiny), 102.0, 102.0, t, 24.0)?.id)
        assertEquals("gate", MapHit.pickOrNearest(listOf(tiny), 106.0, 102.0, t, 24.0)?.id)
        assertNull(MapHit.pickOrNearest(listOf(tiny), 300.0, 300.0, t, 24.0))
    }

    // ── 逻辑层拆分 ────────────────────────────────────────

    @Test
    fun `逻辑层按 filter kind 拆且不丢要素`() {
        val m = MapManifest(
            schema = 1,
            bbox = listOf(0.0, 0.0, 10.0, 10.0),
            layers = listOf(
                cn.dkarbon.enroute.data.map.MapLayer(
                    name = "b", label = "建筑",
                    filter = cn.dkarbon.enroute.data.map.MapFilter(listOf("teach")),
                ),
                cn.dkarbon.enroute.data.map.MapLayer(
                    name = "g", label = "绿地",
                    filter = cn.dkarbon.enroute.data.map.MapFilter(listOf("green")),
                ),
            ),
        )
        val fs = listOf(
            square("t1", 0.0, 0.0, 1.0, 1.0).copy(kind = "teach"),
            square("t2", 2.0, 0.0, 3.0, 1.0).copy(kind = "teach"),
            square("g1", 4.0, 0.0, 5.0, 1.0).copy(kind = "green"),
            square("x1", 6.0, 0.0, 7.0, 1.0).copy(kind = "water"),
        )
        val r = MapLayers.split(m, fs)
        assertEquals(2, r.groups[0].features.size)
        assertEquals(1, r.groups[1].features.size)
        // 没有层接住的要素进 unmatched（绝不静默丢掉）
        assertEquals(listOf("x1"), r.unmatched.map { it.id })
        // 图例只列有要素的层
        assertEquals(2, MapLayers.legend(m, fs).size)
    }

    // ── manifest 校验（四态 + 缺层）──────────────────────

    @Test
    fun `校验正常与坏 schema`() {
        val good = MapPackageRules.validate(res("manifest.json"), res("campus.geojson"))
        assertEquals(MapIssue.NONE, good.issue)
        assertTrue(good.ok)
        assertEquals(90, good.features.size)
        assertEquals("base@1x.webp", good.baseFile)

        val badSchema = res("manifest.json").replace("\"schema\": 1", "\"schema\": 9")
        assertEquals(MapIssue.BAD_SCHEMA, MapPackageRules.validate(badSchema, res("campus.geojson")).issue)
    }

    @Test
    fun `校验缺文件缺层与要素非面`() {
        assertEquals(MapIssue.MISSING_MANIFEST, MapPackageRules.validate(null, "{}").issue)
        assertEquals(MapIssue.PARSE_ERROR, MapPackageRules.validate("not json", "{}").issue)
        // manifest 指的文件没读到
        assertEquals(MapIssue.MISSING_FILE, MapPackageRules.validate(res("manifest.json"), null).issue)
        // 没有图层
        val noLayers = """{"schema":1,"bbox":[0,0,10,10],"layers":[]}"""
        assertEquals(MapIssue.MISSING_LAYERS, MapPackageRules.validate(noLayers, "{}").issue)
        // bbox 不合法
        val badBox = """{"schema":1,"bbox":[0,0,0,10],"layers":[{"name":"a","file":"x.geojson"}]}"""
        assertEquals(MapIssue.BAD_BBOX, MapPackageRules.validate(badBox, "{}").issue)
        // 要素不是 Polygon（v1 契约只收面）
        val pointGeo = """{"type":"FeatureCollection","features":[{"type":"Feature",
            "properties":{"id":"p"},"geometry":{"type":"Point","coordinates":[1,2]}}]}"""
        assertEquals(MapIssue.BAD_GEOMETRY, MapPackageRules.validate(res("manifest.json"), pointGeo).issue)
        // 描述是人话，不是枚举名
        assertTrue(MapPackageRules.describe(MapIssue.BAD_SCHEMA).contains("schema"))
    }

    // ── 标注：合并 / 全局字段 / 孤儿 / 导出 ───────────────

    private fun sampleEdits(): MapEdits {
        var e = MapEdits(mapVersion = "2026.11.14-1")
        e = MapEditsOps.addField(e, "层数")
        e = MapEditsOps.addField(e, "所属单位")
        e = MapEditsOps.setName(e, "f19", "九州体育场")
        e = MapEditsOps.setKind(e, "f19", "sport")
        e = MapEditsOps.setField(e, "f19", "层数", "3")
        e = MapEditsOps.setField(e, "f19", "所属单位", "体育部")
        return e
    }

    @Test
    fun `edits JSON 平铺形态能往返`() {
        val e = sampleEdits()
        val json = MapEditsCodec.toJson(e)
        assertTrue("自定义字段要平铺在要素对象里", json.contains("\"层数\":\"3\""))
        assertTrue(json.contains("\"kind\":\"sport\""))
        val back = MapEditsCodec.fromJson(json)!!
        assertEquals(e.fieldOrder, back.fieldOrder)
        assertEquals("九州体育场", back.features["f19"]?.name)
        assertEquals("3", back.features["f19"]?.fields?.get("层数"))
        assertEquals("体育部", back.features["f19"]?.fields?.get("所属单位"))
        // 坏 JSON / 不认识的 schema
        assertNull(MapEditsCodec.fromJson("not json"))
        assertNull(MapEditsCodec.fromJson("""{"schema":9,"features":{}}"""))
    }

    @Test
    fun `edits 覆盖 asset 且字段全局共享`() {
        val fs = features()
        val e = sampleEdits()
        val rows = MapEditsOps.merged(fs, e)
        val f19 = rows.first { it.feature.id == "f19" }
        assertEquals("九州体育场", f19.name)
        assertEquals("sport", f19.kind)
        // 自定义字段全局：没标过的要素也有这两列（值为空）
        val other = rows.first { it.feature.id == "f10" }
        assertEquals(listOf("层数", "所属单位"), other.fields.map { it.first })
        assertTrue(other.fields.all { it.second.isBlank() })

        // 空串 = 清空值，字段本身保留
        val cleared = MapEditsOps.setField(e, "f19", "层数", "")
        val r19 = MapEditsOps.merged(fs, cleared).first { it.feature.id == "f19" }
        assertEquals("", r19.fields.first { it.first == "层数" }.second)
        assertTrue("层数" in cleared.fieldOrder)
    }

    @Test
    fun `重复字段与保留名不许加`() {
        var e = MapEditsOps.addField(MapEdits(), "层数")
        val again = MapEditsOps.addField(e, " 层数 ")
        assertEquals(1, again.fieldOrder.size)
        e = MapEditsOps.addField(e, "name")          // 保留名
        assertEquals(1, e.fieldOrder.size)
        e = MapEditsOps.addField(e, "   ")           // 空白
        assertEquals(1, e.fieldOrder.size)
        // 删字段：连带删所有要素该列的值
        val withVal = MapEditsOps.setField(MapEditsOps.addField(e, "备注"), "f19", "备注", "含 A/B 座")
        assertEquals("含 A/B 座", withVal.features["f19"]?.fields?.get("备注"))
        val removed = MapEditsOps.removeField(withVal, "备注")
        assertFalse("备注" in removed.fieldOrder)
        assertNull(removed.features["f19"]?.fields?.get("备注"))
    }

    @Test
    fun `换图后找不到的 id 算孤儿且不丢`() {
        val e = sampleEdits()
        val fs = features()
        assertTrue(MapEditsOps.orphans(fs, e).isEmpty())
        // 新版地图少了 f19
        val newFs = fs.filter { it.id != "f19" }
        val orphans = MapEditsOps.orphans(newFs, e)
        assertEquals(listOf("f19"), orphans)
        // 标注本身还在（不静默丢弃），只是版本号被更新
        val merged = MapEditsOps.withVersion(e, "2026.12.01-1")
        assertEquals("2026.12.01-1", merged.mapVersion)
        assertEquals("九州体育场", merged.features["f19"]?.name)
    }

    @Test
    fun `导出 GeoJSON 与 CSV 是合并结果`() {
        val e = sampleEdits()
        val fs = listOf(
            square("f19", 0.0, 0.0, 10.0, 10.0).copy(name = "", kind = "", color = "#8DAB87", areaPx = 100.0),
            square("f20", 20.0, 0.0, 30.0, 10.0),
        )
        val geo = MapExport.geoJson(fs, e)
        assertTrue(geo.startsWith("{\"type\":\"FeatureCollection\""))
        assertTrue("名称要合并进来", geo.contains("\"name\":\"九州体育场\""))
        assertTrue("自定义字段要出现在 properties 里", geo.contains("\"层数\":\"3\""))
        assertTrue("矢量化色值要保留", geo.contains("\"color\":\"#8DAB87\""))
        // 坐标被压到 3 位小数
        assertTrue(geo.contains("[[0,0],[10,0],[10,10],[0,10],[0,0]]"))

        val csv = MapExport.csv(fs, e)
        assertTrue("CSV 必须带 UTF-8 BOM，否则 Excel 乱码", csv.startsWith("\uFEFF"))
        val lines = csv.removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals("id,name,kind,层数,所属单位", lines[0])
        assertEquals("f19,九州体育场,sport,3,体育部", lines[1])
        assertEquals("f20,f20,other,,", lines[2])

        assertEquals("campus_annotated_2026.11.14-1_20261114-1540.geojson",
            MapExport.fileName("2026.11.14-1", "20261114-1540", "geojson"))
    }

    @Test
    fun `CSV 里的逗号引号换行要转义`() {
        var e = MapEditsOps.addField(MapEdits(), "备注")
        e = MapEditsOps.setField(e, "f19", "备注", "含 A,B 座 \"括号\"")
        val csv = MapExport.csv(listOf(square("f19", 0.0, 0.0, 1.0, 1.0)), e)
        assertTrue(csv, csv.contains("\"含 A,B 座 \"\"括号\"\"\""))
    }

    @Test
    fun `图层配色缺省时用要素色兜底`() {
        // 真数据的 campus 层没给 fill/stroke → A 模式靠要素自身的 color
        val m = manifest()
        val layer = m.layers.first()
        assertEquals("", layer.fill)
        assertTrue(features().first().color.startsWith("#"))
    }

    @Test
    fun `mapKey 的变换在真数据上不越界`() {
        // 冒烟：真数据每个面的 bbox 都在 manifest bbox 内（矢量化没跑偏）
        val m = manifest()
        val b = m.bbox
        features().forEach { f ->
            val fb = f.bounds
            assertTrue("${f.id} 越出 bbox", fb[0] >= b[0] - 1e-6 && fb[2] <= b[2] + 1e-6)
            assertTrue("${f.id} 越出 bbox", fb[1] >= b[1] - 1e-6 && fb[3] <= b[3] + 1e-6)
        }
    }

    @Test
    fun `Transform 的源屏幕往返在缩放后仍成立`() {
        val base = MapProjection.fit(listOf(0.0, 0.0, 1024.0, 648.0), 900f, 700f, padding = 4f)
        val t: MapTransform = MapProjection.withUser(base, 3.5, 12f, -8f, 900f, 700f)
        assertEquals(321.0, t.sourceX(t.screenX(321.0)), 1e-6)
        assertEquals(654.0, t.sourceY(t.screenY(654.0)), 1e-6)
    }
}
