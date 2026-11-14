package cn.dkarbon.enroute

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import cn.dkarbon.enroute.ui.theme.FormSpec
import cn.dkarbon.enroute.ui.theme.ShapeSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 票 31：形状 token 的唯一出口（账页重构 v2 批次 1）。
 *
 * ## 为什么"正则只是兜底"（这条决定了整票成败）
 * 单靠正则扫源码，永远能被绕过去 —— `CircleShape`、`cutCorner(8.dp)`、
 * `RoundedCornerShape(50)`（百分比重载，没有 `.dp`）、`13f.dp`（`\d+` 后面跟 `f`）。
 * 所以真正的约束是**类型系统**：业务代码只能拿到 [ShapeSet] 里的 `Shape` 对象，
 * 拿不到"半径是多少 dp"，也就写不出自己的数字（见 `ShapeSet.kt` 文件头）。
 *
 * 本测试是第二道闸：
 * 1. [扫描源码] 抓**所有**已知写法（含上面那四种绕过姿势），而不是只认 `RoundedCornerShape(xx.dp)`；
 * 2. 扫描范围是**整棵 `cn.dkarbon.enroute` 源树**（含 `widget/`、含 `ui/timetable/`），
 *    不是只扫 `ui/` —— 否则会出现"UI 改了，桌面小组件还是圆角"；
 * 3. `ui/timetable/` 目前冻结成**基线数量**（那一包正在被别的票改动，本票不碰），
 *    只允许**变少**，不允许变多：想偷偷加一个圆角，这里立刻红。
 *
 * ## 为什么用"每文件计数"而不是"行号"
 * 行号会随无关改动漂移，锚行号等于给每个代理埋雷。计数只认一件事：**这个文件里有几个裸形状**。
 * 变多 = 有人在偷偷加；变少 = 有人清干净了（欢迎，把基线数字改小即可）。
 */
class ShapeTokenTest {

    // ── ① 形状档的值（主题规则本身）─────────────────────────

    @Test
    fun `HARD 全方角只有课卡是主人点名的 4dp 例外`() {
        val h = ShapeSet.Hard
        assertEquals("页面/通栏", RectangleShape, h.page)
        assertEquals("徽标", RectangleShape, h.badge)
        assertEquals("按钮与输入框", RectangleShape, h.control)
        assertEquals("通用卡片", RectangleShape, h.card)
        assertEquals("浮层", RectangleShape, h.sheet)
        assertEquals("胶囊：HARD 没有胶囊", RectangleShape, h.pill)
        assertEquals("课卡 4dp 是主人点名的局部例外", RoundedCornerShape(4.dp), h.courseCard)
        assertEquals("正圆（头像/状态点）", CircleShape, h.circle)
    }

    @Test
    fun `账页形状按规范取值`() {
        val l = ShapeSet.Ledger
        assertEquals(RectangleShape, l.page)
        assertEquals("徽标 6dp", RoundedCornerShape(6.dp), l.badge)
        assertEquals("按钮/输入框 6dp", RoundedCornerShape(6.dp), l.control)
        assertEquals("卡片 10dp", RoundedCornerShape(10.dp), l.card)
        assertEquals("课卡 10dp", RoundedCornerShape(10.dp), l.courseCard)
        assertEquals("浮层 14dp", RoundedCornerShape(14.dp), l.sheet)
        // 胶囊是**百分比**，不是 999dp：任意高度都正好半高
        assertEquals(RoundedCornerShape(percent = 50), l.pill)
        assertEquals(CircleShape, l.circle)
    }

    @Test
    fun `形状档与旧 Dp 字段不许漂移`() {
        // FormSpec 里的 radiusCard / radiusCourseCard / radiusSheet 还留着（课表那两个调用点在用），
        // 两套值必须一致，否则"同一张卡在两个页面圆角不同"。
        // 注意 0 半径的正规表示是 RectangleShape（`RoundedCornerShape(0.dp)` 与它像素等价但类型不同），
        // 所以按「0 → 直角，非 0 → 对应圆角」判。
        fun check(f: FormSpec, radius: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape, label: String) {
            if (radius.value == 0f) {
                assertEquals("$label：0 半径必须是直角", RectangleShape, shape)
            } else {
                assertEquals("$label：应等于 RoundedCornerShape($radius)", RoundedCornerShape(radius), shape)
            }
        }
        listOf(FormSpec.Hard, FormSpec.Ledger).forEach { f ->
            check(f, f.radiusCard, f.shapes.card, "card")
            check(f, f.radiusCourseCard, f.shapes.courseCard, "courseCard")
            check(f, f.radiusSheet, f.shapes.sheet, "sheet")
        }
    }

    @Test
    fun `主题切换取到对应形状组`() {
        assertEquals(ShapeSet.Hard, ShapeSet.of(editorial = false))
        assertEquals(ShapeSet.Ledger, ShapeSet.of(editorial = true))
        assertEquals(ShapeSet.Hard, FormSpec.of(cn.dkarbon.enroute.ui.theme.StyleSet.HARD).shapes)
        assertEquals(ShapeSet.Ledger, FormSpec.of(cn.dkarbon.enroute.ui.theme.StyleSet.EDITORIAL).shapes)
    }

    // ── ② 扫描器本身：四种绕过姿势都必须被抓到 ────────────────

    @Test
    fun `扫描器能抓住各种绕过写法`() {
        val snippet = """
            .clip(RoundedCornerShape(6.dp))
            .clip(CircleShape)
            .clip(CutCornerShape(8.dp))
            .clip(RoundedCornerShape(50))
            .clip(RoundedCornerShape(13f.dp))
            val r = 13
            .clip(RoundedCornerShape(r.dp))
            Modifier.cutCorner(6.dp)
            .clip(MaterialTheme.shapes.small)
            .clip(RoundedCornerShape(999.dp))
        """.trimIndent()
        val hits = ShapeScan.violationLines(snippet)
        assertEquals(
            "这 9 行都必须被抓到，实际只抓到这些行：$hits",
            listOf(1, 2, 3, 4, 5, 7, 8, 9, 10),
            hits,
        )
    }

    @Test
    fun `扫描器不误报 import 行与正常 token 用法`() {
        val snippet = """
            import androidx.compose.foundation.shape.CircleShape
            import androidx.compose.foundation.shape.RoundedCornerShape
            .clip(f.shapes.card)
            .border(1.dp, t.outline, Form.current.shapes.control)
            .clip(RectangleShape)
            val pill = f.shapes.pill
        """.trimIndent()
        assertEquals(emptyList<Int>(), ShapeScan.violationLines(snippet))
    }

    // ── ③ 真源码：可编辑范围必须归零，课表包只许变少 ──────────

    @Test
    fun `可编辑范围内裸形状必须为零`() {
        val root = ShapeScan.sourceRoot()
        val report = ShapeScan.scan(root)

        val editable = report.filterKeys { !it.startsWith("ui/timetable/") }
        assertTrue(
            "这些文件还有裸形状（形状请走 Form.current.shapes.*）：\n" +
                editable.entries.joinToString("\n") { "  ${it.key} × ${it.value}" },
            editable.isEmpty(),
        )
    }

    @Test
    fun `课表包的裸形状只许变少不许变多`() {
        val root = ShapeScan.sourceRoot()
        val report = ShapeScan.scan(root)

        // 基线：本票不动 ui/timetable（另一票正在改那边的左右滑动）。
        // 数字只能减不能增 —— 想偷偷加一个圆角，这里立刻红。
        val frozen = mapOf(
            "ui/timetable/ErodedCard.kt" to 1,
            "ui/timetable/LedgerTimetable.kt" to 6,
            "ui/timetable/TimetableScreen.kt" to 2,
        )
        frozen.forEach { (file, allowed) ->
            val actual = report[file] ?: 0
            assertTrue(
                "$file 的裸形状从基线 $allowed 涨到了 $actual —— 请改用 Form.current.shapes.*",
                actual <= allowed,
            )
            if (actual < allowed) {
                println("[ShapeTokenTest] $file 已清到 $actual（基线 $allowed）：把冻结值改小即可")
            }
        }
        // 课表包里不许出现**新的**裸形状文件
        val extra = report.keys.filter { it.startsWith("ui/timetable/") && it !in frozen.keys }
        assertTrue("课表包出现新的裸形状文件：$extra", extra.isEmpty())
    }

    @Test
    fun `扫描覆盖整棵源树而不是只扫 ui`() {
        val root = ShapeScan.sourceRoot()
        val files = ShapeScan.kotlinFiles(root)
        // 防止"目录找错 → 扫到 0 个文件 → 永远绿"
        assertTrue("只扫到 ${files.size} 个 .kt，源树根疑似找错了：$root", files.size > 50)
        val paths = files.map { it.relativeTo(root).invariantSeparatorsPath }
        // 这几处必须真被扫到（票面点名的漏扫区）
        assertTrue("widget/ 没被扫到", paths.any { it.startsWith("widget/") })
        assertTrue("ui/timetable/ 没被扫到", paths.any { it.startsWith("ui/timetable/") })
        assertTrue("ui/settings/ 没被扫到", paths.any { it.startsWith("ui/settings/") })
    }

}

/**
 * 源码形状扫描器。
 *
 * 独立成对象（而不是写在测试方法里）是为了能用**合成代码片段**验证它真的抓得住 ——
 * 一个只会对真仓库跑、却没人验过它抓不抓得住的 gate，等于没有 gate。
 */
object ShapeScan {

    /** 已知的所有"自己造形状"写法 */
    private val FORBIDDEN = listOf(
        Regex("""RoundedCornerShape\s*\("""),
        Regex("""\bCircleShape\b"""),
        Regex("""\bCutCornerShape\s*\("""),
        Regex("""\bAbsoluteRoundedCornerShape\s*\("""),
        Regex("""\bGenericShape\b"""),
        Regex("""\.cutCorner\s*\("""),
        // M3 的形状表是 small/medium/large，语义上等于"随手挑个圆角"，也属于绕过
        Regex("""MaterialTheme\s*\.\s*shapes\b"""),
        // 999dp 这个魔数本身就该绝迹（胶囊走百分比）
        Regex("""\b999\s*\.\s*dp\b"""),
    )

    /** 单行是否是违规；import 行不算（只用不 import 是不可能的，且 import 本身不产生形状） */
    fun violationLines(source: String): List<Int> {
        val out = mutableListOf<Int>()
        source.lineSequence().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("import ")) return@forEachIndexed
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
            if (FORBIDDEN.any { it.containsMatchIn(raw) }) out += i + 1
        }
        return out
    }

    /** 模块源码根（单测工作目录 = 模块目录 `app/`） */
    fun sourceRoot(): File {
        val candidates = listOf(
            File("src/main/java/cn/dkarbon/enroute"),
            File("app/src/main/java/cn/dkarbon/enroute"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("找不到源树，候选=${candidates.map { it.absolutePath }}，工作目录=${File(".").absolutePath}")
    }

    fun kotlinFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * 扫描整棵源树（**排除 `ui/theme/`** —— 那里是 token 的定义处，本来就该出现形状构造）。
     *
     * @return 相对路径 → 该文件里的违规行数（只列 > 0 的）
     */
    fun scan(root: File): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        kotlinFiles(root).forEach { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (rel.startsWith("ui/theme/")) return@forEach
            val n = violationLines(f.readText()).size
            if (n > 0) out[rel] = n
        }
        return out
    }
}
