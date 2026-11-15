package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.map.MapEditsOps
import cn.dkarbon.enroute.data.map.MapParse
import cn.dkarbon.enroute.ui.map.MapNudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地图共建提示条与引导卡文案（票 12 §8）。
 *
 * 为什么值得单测：这三句话里的数字必须**从实际数据算**（主人明确「不许写死」），
 * 而且有「一个没补 / 补了一部分 / 全齐」三种状态。写死在界面里，哪天数据变了就会开始骗人。
 */
class MapNudgeTest {

    private fun features() = MapParse.geoJson(
        javaClass.classLoader!!.getResourceAsStream("map/campus.geojson")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() },
    )!!.features

    @Test
    fun `真数据的现状文案与票面一字不差`() {
        // 真数据：90 个面、1 个有名称（九州体育场）、0 个有类别
        val fs = features()
        assertEquals(90, fs.size)
        assertEquals(1, fs.count { it.name.isNotBlank() })
        assertEquals(0, fs.count { it.kind.isNotBlank() })
        assertEquals(
            "地图仍在开发中：90 个地块中仅 1 个有名称、0 个有类别。欢迎一起补全",
            MapNudge.line(fs.size, 1, 0),
        )
    }

    @Test
    fun `一个都没补时也要说人话`() {
        assertEquals(
            "地图仍在开发中：90 个地块中仅 0 个有名称、0 个有类别。欢迎一起补全",
            MapNudge.line(90, 0, 0),
        )
    }

    @Test
    fun `补了一部分时数字跟着变`() {
        assertEquals(
            "地图仍在开发中：90 个地块中仅 2 个有名称、1 个有类别。欢迎一起补全",
            MapNudge.line(90, 2, 1),
        )
        assertEquals(
            "地图仍在开发中：90 个地块中仅 45 个有名称、12 个有类别。欢迎一起补全",
            MapNudge.line(90, 45, 12),
        )
    }

    @Test
    fun `全补齐时降级为感谢与纠错`() {
        assertEquals(
            "地图仍在开发中：地块信息已由大家补全，如有错漏欢迎修正",
            MapNudge.line(90, 90, 90),
        )
        // 名称全齐但类别没齐 → 还是要催
        assertTrue(MapNudge.line(90, 90, 89).contains("仅 90 个有名称、89 个有类别"))
    }

    @Test
    fun `数字越界与空数据不撒谎`() {
        // 传进来的数比总数还大（上游算错）时按总数封顶，不出现「10 个地块中 130 个有名称」
        assertEquals(
            "地图仍在开发中：10 个地块中仅 10 个有名称、5 个有类别。欢迎一起补全",
            MapNudge.line(10, 99, 5),
        )
        // 两个都超界 = 视作全齐 → 走降级文案，而不是报一个不可能的数字
        assertEquals(
            "地图仍在开发中：地块信息已由大家补全，如有错漏欢迎修正",
            MapNudge.line(10, 99, 99),
        )
        // 没有数据就直说没有，不硬报 0/0
        assertEquals("地图仍在开发中：这一版还没有地块数据。", MapNudge.line(0, 0, 0))
    }

    @Test
    fun `标注要算进提示条的数字`() {
        val fs = features()
        // 当前：1 个有名称、0 个有类别
        val before = MapEditsOps.merged(fs, cn.dkarbon.enroute.data.map.MapEdits())
        assertEquals(1, before.count { it.name.isNotBlank() })
        assertEquals(0, before.count { it.kind.isNotBlank() })

        // 用户给另一个面加了名称与类别 → 合并后应该是 2 / 1
        var edits = cn.dkarbon.enroute.data.map.MapEdits()
        edits = MapEditsOps.setName(edits, "f10", "第一教学楼")
        edits = MapEditsOps.setKind(edits, "f10", "teach")
        val after = MapEditsOps.merged(fs, edits)
        assertEquals(2, after.count { it.name.isNotBlank() })
        assertEquals(1, after.count { it.kind.isNotBlank() })
        assertTrue(MapNudge.line(after.size, 2, 1).contains("仅 2 个有名称、1 个有类别"))
    }

    @Test
    fun `引导卡五步与隐私小字都在`() {
        assertEquals(5, MapNudge.GUIDE_STEPS.size)
        assertEquals("选中地块", MapNudge.GUIDE_STEPS[0].first)
        assertEquals("想帮到所有人", MapNudge.GUIDE_STEPS[4].first)
        assertTrue(MapNudge.GUIDE_STEPS[2].second.contains("字段名是所有地块共用的"))
        assertTrue(MapNudge.GUIDE_FOOTNOTE.contains("先只存在本机"))
        assertTrue(MapNudge.GUIDE_FOOTNOTE.contains("不含任何个人信息"))
        assertEquals("怎么补？", MapNudge.ACTION)
        assertEquals("帮大家补地块信息", MapNudge.GUIDE_TITLE)
    }
}
