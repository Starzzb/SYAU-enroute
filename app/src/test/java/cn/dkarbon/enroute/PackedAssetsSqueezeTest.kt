package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.curriculum.CurriculumParse
import cn.dkarbon.enroute.data.curriculum.SyllabusParse
import org.brotli.dec.BrotliInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内置资产「精简」后的不变量锁（对应 packed_meta.json 的「精简」段）。
 *
 * 2026-11-21 为压包切掉两类冗余：
 *  1. 课程大纲的「课程简介 / 实践课程简介 / 实验课程简介」只留中文 —— 英文是同一段话的译本，
 *     结构化字段（课程名称 / 英文名称）一个没动；
 *  2. 培养方案的「培养目标 + 毕业要求」正文压缩成一句话，原 PDF 页码写进「目标与要求页码」。
 *
 * 两者都是有损改动，所以必须有测试盯着：
 *  - 简介里不许再有大段英文（瘦身没生效、或旧资产被覆盖回来都要红）；
 *  - 页码必须存在，且落在该专业的「章节页范围」内（越界就是编的页号）；
 *  - 课程行 / 学分表 / 索引这些事实字段一个都不许少。
 *
 * 原文全量留档在 在途/curriculum/json/（不进包），要核对去那里查。
 */
class PackedAssetsSqueezeTest {

    private val dir: File = sequenceOf("src/main/assets/curriculum", "app/src/main/assets/curriculum")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("找不到 assets/curriculum")

    private fun text(rel: String): String =
        BrotliInputStream(File(dir, rel).inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun lines(rel: String): List<String> = text(rel).split('\n').filter { it.isNotBlank() }

    /** 像英文正文：没有汉字、字母不少于 6 个、且字母占了大多数 */
    private fun englishLike(line: String): Boolean {
        val s = line.trim()
        if (s.isEmpty()) return false
        if (s.any { it.code in 0x4E00..0x9FFF }) return false
        val letters = s.count { it in 'A'..'Z' || it in 'a'..'z' }
        return letters >= 6 && letters * 100 >= s.length * 45
    }

    @Test
    fun `课程简介里不再有大段英文`() {
        val index = SyllabusParse.index(text("syllabus/index.json.br"))
        val cache = HashMap<String, List<String>>()
        var checked = 0
        val offenders = ArrayList<String>()
        for (e in index) {
            val pack = cache.getOrPut(e.pack) { lines("syllabus/${e.pack}") }
            val s = SyllabusParse.syllabus(pack[e.line])
            for (sec in s.sections) {
                if (!sec.title.contains("简介")) continue
                checked++
                val en = sec.text.split('\n').count { englishLike(it) }
                if (en > 0) offenders.add(s.name + " / " + sec.title + "：还有 " + en + " 行英文")
            }
        }
        assertTrue("没扫到任何简介节，资产结构变了？", checked > 1500)
        assertEquals("简介里仍有英文：" + offenders.take(5) + "，共 " + offenders.size + " 处",
            0, offenders.size)
    }

    @Test
    fun `简介的中文正文没有被一起删掉`() {
        val index = SyllabusParse.index(text("syllabus/index.json.br"))
        var best = ""
        for (e in index.take(40)) {
            val s = SyllabusParse.syllabus(lines("syllabus/${e.pack}")[e.line])
            val sec = s.sections.firstOrNull { it.title.contains("简介") } ?: continue
            if (sec.text.count { it.code in 0x4E00..0x9FFF } > best.count { it.code in 0x4E00..0x9FFF }) {
                best = sec.text
            }
        }
        val cjk = best.count { it.code in 0x4E00..0x9FFF }
        assertTrue("抽到的简介中文只剩 " + cjk + " 字：" + best.take(60), cjk >= 20)
    }

    @Test
    fun `培养方案的目标与要求只剩一句话且页码真实`() {
        val pool = CurriculumParse.pool(text("plans/pool.json.br"))
        val bad = ArrayList<String>()
        var withPages = 0
        for (yr in listOf("2023", "2024", "2025")) {
            for (line in lines("plans/$yr.jsonl.br")) {
                val p = CurriculumParse.plan(line, pool)
                val tag = yr + " " + p.major
                if (p.requirements.isNotBlank()) bad.add(tag + " 毕业要求没删干净")
                if (p.requirementList.isNotEmpty()) bad.add(tag + " 毕业要求列表没清空")
                if (p.goalList.isNotEmpty()) bad.add(tag + " 培养目标列表没清空")
                if (p.goals.length > 120) bad.add(tag + " 培养目标仍有 " + p.goals.length + " 字")
                if (p.goalPages.isEmpty()) continue
                withPages++
                val a = p.goalPages[0]
                val b = if (p.goalPages.size >= 2) p.goalPages[1] else a
                if (a <= 0 || b < a) bad.add(tag + " 页码区间非法 " + a + "-" + b)
                if (p.pageRange.size >= 2 && (a < p.pageRange[0] || b > p.pageRange[1])) {
                    bad.add(tag + " 页码 " + a + "-" + b + " 越出章节页范围 " + p.pageRange)
                }
            }
        }
        assertEquals("没有任何方案带页码，精简字段没写进去", 168, withPages)
        assertEquals("培养方案精简不符合预期：" + bad.take(5) + "，共 " + bad.size + " 处", 0, bad.size)
    }

    @Test
    fun `事实字段一个都没少`() {
        val pool = CurriculumParse.pool(text("plans/pool.json.br"))
        assertEquals("课程行池不能因为瘦身缩水", 5574, pool.size)
        var noCourses = 0
        var noIntro = 0
        for (yr in listOf("2023", "2024", "2025")) {
            for (line in lines("plans/$yr.jsonl.br")) {
                val p = CurriculumParse.plan(line, pool)
                if (p.courses.isEmpty()) noCourses++
                if (p.intro.isBlank()) noIntro++
            }
        }
        assertEquals("有方案展开后没有课程行", 0, noCourses)
        assertEquals("有方案的专业介绍被清空", 0, noIntro)
    }
}
