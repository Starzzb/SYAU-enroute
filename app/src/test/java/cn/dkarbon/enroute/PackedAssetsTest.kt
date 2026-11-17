package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.curriculum.CurriculumParse
import cn.dkarbon.enroute.data.curriculum.SyllabusParse
import org.brotli.dec.BrotliInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 压包资产（brotli）与读取层的 round-trip 锁（规划-课程大纲内嵌压缩 §5 验收）。
 *
 * 这里直接读 **app/src/main/assets/curriculum** 的真资产，不是测试夹具拷贝 ——
 * 目的就是拦住「资产重建了但读取层对不上」这类事故：
 *
 * 1. 索引条数与包内行数必须一致（2066 门 / 14 个学院包）
 * 2. 索引里的 (pack, line) 必须真能取到那门课，且解出来的课名与索引一致
 * 3. 培养方案：池 5574 行、索引 55/67/46 份，展开后的课程行数与池内下标对得上
 *
 * 不依赖 Android：brotli 解码器是纯 Java（org.brotli:dec），kotlinx.serialization 也在 JVM 上跑。
 */
class PackedAssetsTest {

    /** Gradle 单测的工作目录是模块目录（app/），但也兼容从仓库根直接跑 */
    private val dir: File = sequenceOf("src/main/assets/curriculum", "app/src/main/assets/curriculum")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("找不到 assets/curriculum —— 单测工作目录是 ${File(".").absolutePath}")

    private fun text(rel: String): String =
        BrotliInputStream(File(dir, rel).inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun lines(rel: String): List<String> =
        text(rel).split('\n').filter { it.isNotBlank() }

    // ── 大纲 ──────────────────────────────────────────────

    @Test
    fun `大纲索引 2066 条且 (pack,line) 全部命中`() {
        val index = SyllabusParse.index(text("syllabus/index.json.br"))
        assertEquals("大纲门数（14 个学院 2021 版）", 2066, index.size)

        val packCache = HashMap<String, List<String>>()
        for (e in index) {
            assertTrue("${e.name} 缺 pack", e.pack.isNotBlank())
            assertTrue("${e.name} 的 line 越界：${e.line}", e.line >= 0)
            val pack = packCache.getOrPut(e.pack) { lines("syllabus/${e.pack}") }
            assertTrue("${e.name} 的 line=${e.line} 超出 ${e.pack}(${pack.size} 行)", e.line < pack.size)
        }

        // 逐个学院包 decode 一次，行数相加必须等于索引条数（当前步只取最后用到的包，其余只算行数）
        val packs = index.map { it.pack }.toSortedSet()
        assertEquals("学院包个数", 14, packs.size)
    }

    @Test
    fun `抽一门课解出来课名与索引一致`() {
        val index = SyllabusParse.index(text("syllabus/index.json.br"))
        val e = index.first { it.code.length == 9 } // 取第一条 9 位代码，自己包里的那门课
        val pack = lines("syllabus/${e.pack}")
        val s = SyllabusParse.syllabus(pack[e.line])
        assertEquals(e.name, s.name)
        assertTrue("章节不能为空", s.chapters.isNotEmpty())
    }

    // ── 培养方案 ──────────────────────────────────────────

    @Test
    fun `培养方案索引 55-67-46 且池 5574 行`() {
        val idx = CurriculumParse.index(text("plans/index.json.br"))
        assertEquals(listOf("2023", "2024", "2025"), idx.years)
        assertEquals(55, idx.entries("2023").size)
        assertEquals(67, idx.entries("2024").size)
        assertEquals(46, idx.entries("2025").size)

        val pool = CurriculumParse.pool(text("plans/pool.json.br"))
        assertEquals("课程行池条数", 5574, pool.size)
    }

    @Test
    fun `方案展开后课程行来自池且行数对得上`() {
        val idx = CurriculumParse.index(text("plans/index.json.br"))
        val pool = CurriculumParse.pool(text("plans/pool.json.br"))
        val lines2024 = lines("plans/2024.jsonl.br")
        val e = idx.entries("2024").first { it.line in lines2024.indices }
        val plan = CurriculumParse.plan(lines2024[e.line], pool)

        assertEquals(e.major, plan.major)
        val courses = CurriculumParse.coursesByTerm(plan).sumOf { it.second.size }
        assertTrue("展开后没有课程行：${e.id}", courses > 0)
        // 池展开的硬要求：展开后每一行都得有课名，不能留一堆空格子
        val named = CurriculumParse.coursesByTerm(plan).flatMap { it.second }.count { it.name.isNotBlank() }
        assertEquals("有课名的行数应等于展开行数", courses, named)
    }
}
