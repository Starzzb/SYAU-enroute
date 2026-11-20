package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapEdit
import cn.dkarbon.enroute.data.map.MapEdits
import cn.dkarbon.enroute.data.map.MapEditsCodec
import cn.dkarbon.enroute.data.map.MapEditsOps
import cn.dkarbon.enroute.data.map.MapFeature
import cn.dkarbon.enroute.data.map.MapFieldOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * m31 轨道 F 的数据侧锁定：**同名覆盖优先 / 撤销后回落官方值 / 序列化往返一致**。
 *
 * 纯 JVM：不读 assets、不需要 Context —— 夹具直接造一个带官方 `props` 的 [MapFeature]。
 * 「官方值」= asset 里那版 GeoJSON 的 `properties`（[MapFeature.props]），用户那版在 `edits.json`。
 */
class MapEditsTest {

    /** 官方基线里本来就有「层数」「所属单位」：三教官方值 3 层 / 后勤处 */
    private fun sanjiao() = MapFeature(
        id = "f19",
        name = "三教",
        kind = "teaching",
        props = mapOf("层数" to "3", "所属单位" to "后勤处"),
    )

    private val officialFields = listOf("层数", "所属单位")

    @Test
    fun `同名覆盖优先——自己填的值压过官方值`() {
        // 「层数」是官方字段名，仍然可以当自定义列加进来（这正是「覆盖」的表达方式）
        var edits = MapEditsOps.addField(MapEdits(), "层数")
        assertTrue("官方字段名应当允许作为覆盖列加进来", "层数" in edits.fieldOrder)
        // 加两次不产生重复列（字段表全局共享，重复列会让 CSV 出现两列同名）
        edits = MapEditsOps.addField(edits, "层数")
        assertEquals(listOf("层数"), edits.fieldOrder)

        edits = MapEditsOps.setField(edits, "f19", "层数", "5")

        val f = sanjiao()
        assertTrue(MapFieldOverride.overridesOfficial(officialFields, "层数"))
        assertEquals("覆盖值优先", "5", MapFieldOverride.valueOf(f, edits, "层数"))
        assertTrue(MapFieldOverride.isOverriding(f, edits, officialFields, "层数"))
        // 官方那份原封不动：撤销覆盖靠的就是它还在
        assertEquals("3", f.props["层数"])
        // 官方没被覆盖的列照旧
        assertEquals("后勤处", MapFieldOverride.valueOf(f, edits, "所属单位"))
    }

    @Test
    fun `撤销覆盖后回落官方值`() {
        val f = sanjiao()
        var edits = MapEditsOps.addField(MapEdits(), "层数")
        edits = MapEditsOps.setField(edits, "f19", "层数", "5")
        assertEquals("5", MapFieldOverride.valueOf(f, edits, "层数"))

        // ① 清空这一格（列还在）→ 回落
        val cleared = MapEditsOps.setField(edits, "f19", "层数", "")
        assertEquals("3", MapFieldOverride.valueOf(f, cleared, "层数"))
        assertFalse(MapFieldOverride.isOverriding(f, cleared, officialFields, "层数"))

        // ② 连整列一起删 → 同样回落，且字段表里不再有这一列
        val removed = MapEditsOps.removeField(edits, "层数")
        assertFalse("层数" in removed.fieldOrder)
        assertEquals("3", MapFieldOverride.valueOf(f, removed, "层数"))

        // ③ 官方没有、自己也没填 → 空串（留空显示「—」，不填 0、不猜）
        assertEquals("", MapFieldOverride.valueOf(f, edits, "备注"))
    }

    @Test
    fun `保留名不能当自定义字段`() {
        // name / kind 是固定槽位，不是「自定义字段」——否则会出现两套同名来源
        for (reserved in MapEdits.RESERVED) {
            val e = MapEditsOps.addField(MapEdits(), reserved)
            assertTrue("$reserved 不该进 fieldOrder", e.fieldOrder.isEmpty())
        }
    }

    @Test
    fun `序列化往返一致——覆盖值穿过落盘再读回仍在`() {
        val original = MapEdits(
            schema = MapEdits.SCHEMA,
            mapVersion = "2026.11.14-1",
            fieldOrder = listOf("楼号", "层数"),
            features = mapOf(
                "f19" to MapEdit(
                    name = "三教",
                    kind = "teaching",
                    fields = mapOf("楼号" to "3 号楼 · 东门旁", "层数" to "5"),
                ),
                "f20" to MapEdit(name = "九州体育场"),
            ),
        )

        val text = MapEditsCodec.toJson(original)
        val back = MapEditsCodec.fromJson(text)

        assertEquals("往返后整份标注应当相等", original, back)
        assertEquals("2026.11.14-1", back?.mapVersion)
        assertEquals(listOf("楼号", "层数"), back?.fieldOrder)
        // 覆盖关系穿过了落盘：读回来仍然是「5」而不是官方的「3」
        assertEquals("5", MapFieldOverride.valueOf(sanjiao(), requireNotNull(back), "层数"))
        // 再写一次应当字节级一致（幂等：否则每次保存都在改文件内容）
        assertEquals(text, MapEditsCodec.toJson(back))

        // schema 不认识 → 读不出（返回 null），不能当成「空标注」把人家的文件覆盖掉
        assertNull(MapEditsCodec.fromJson("""{"schema":2,"fieldOrder":[],"features":{}}"""))
        assertNull(MapEditsCodec.fromJson("不是一个 JSON"))
    }

    @Test
    fun `字段表外的孤儿列读回时补进字段表`() {
        // 老文件（或别人手改过的文件）里字段表没列这一格 —— 值不能丢
        val text = """{"schema":1,"mapVersion":"2026.11.14-1","fieldOrder":[],""" +
            """"features":{"f19":{"name":"三教","层数":"5"}}}"""
        val edits = MapEditsCodec.fromJson(text)
        assertEquals(listOf("层数"), edits?.fieldOrder)
        assertEquals("5", edits?.features?.get("f19")?.fields?.get("层数"))
    }
}
