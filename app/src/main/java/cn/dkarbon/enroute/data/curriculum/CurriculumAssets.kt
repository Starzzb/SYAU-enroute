package cn.dkarbon.enroute.data.curriculum

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog
import kotlinx.serialization.json.JsonElement
import org.brotli.dec.BrotliInputStream

/**
 * 内置培养方案 + 课程大纲资产的读取层（票 09 / 2026-11-17 合包改造）。
 *
 * ## 资产形态（`assets/curriculum/`）
 * ```
 * syllabus/index.json.br            2066 门索引（id/code/codes/name/type/credit/hours/college/pack/line）
 * syllabus/<学院>.jsonl.br          14 个学院包，**一行一门**大纲
 * plans/index.json.br               168 份方案索引（按年份分组，带 line）
 * plans/pool.json.br                5574 条课程行共享池（一整行 JSON 数组）
 * plans/<年>.jsonl.br               2023/2024/2025 年包，一行一份方案，课程只存池下标
 * ```
 * 全部 brotli q11 压过（4.64 MB，原明文 42.34 MB）。为什么这么切见
 * `docs/规划-课程大纲内嵌压缩.md`：单文件进包时 deflate 无法跨文件共享字典，
 * 合成包后再压，同一批内容能小 40%。
 *
 * ## 四条纪律（沿用票 09，一条没放松）
 * 1. **按需读**：索引读一次留内存；正文包**解压后按行取**，LRU 只留 2 个学院包；
 * 2. **只在 IO 线程调**：主线程调用直接拒绝并记日志，绝不卡帧；
 * 3. 读不到就返回 null / 空，由界面说「没读到」，不抛异常；
 * 4. 数据诚实：池下标越界就保留原值（该行解析成空课程），**不拿相邻行冒充**。
 */
class CurriculumAssets(private val ctx: Context) {

    private val appCtx = ctx.applicationContext
    private val dir = "curriculum"

    // ── 索引与查找表（读一次留内存：索引合计约 30 KB）────────────
    private var cachedSyllabusIndex: List<SyllabusIndexEntry>? = null
    private var syllabusById: Map<String, SyllabusIndexEntry>? = null
    private var cachedPlanIndex: PlanIndex? = null
    private var planLineByKey: Map<String, Int>? = null

    /** 课程行池（解压后是 5574 个 JsonElement，约 3 MB；只在读方案时才有） */
    private var poolRows: List<JsonElement>? = null

    /**
     * 学院包行缓存：解压一次 3~4 MB **文本**，留 2 个够覆盖「同一门课反复看」与
     * 「课表里连点两门同院课程」两种真实用法，再多就是白占内存。
     */
    private val packCache = object : LinkedHashMap<String, List<String>>(4, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<String>>?,
        ): Boolean = size > 2
    }

    private var yearCacheYear: String = ""
    private var yearCacheLines: List<String> = emptyList()

    // ── 索引 ──────────────────────────────────────────────

    /** 读培养方案索引（168 份，按年份分组） */
    fun index(): PlanIndex? = synchronized(this) {
        cachedPlanIndex?.takeIf { !it.isEmpty }?.let { return it }
        val idx = readText("plans/index.json.br")?.let(CurriculumParse::index)
            ?: return null
        if (idx.isEmpty) return null
        cachedPlanIndex = idx
        planLineByKey = buildMap {
            idx.byYear.forEach { (year, list) ->
                list.forEach { e -> if (e.line >= 0) put("$year|${e.id}", e.line) }
            }
        }
        idx
    }

    /**
     * 读某个专业的方案。
     *
     * @param id 索引里的 id（`CurriculumParse` 的命名口径，含实验班后缀）
     */
    fun plan(year: String, id: String): CurriculumPlan? {
        val y = year.trim()
        val key = id.trim()
        if (y.isBlank() || key.isBlank()) return null

        val line = synchronized(this) { planLineByKey?.get("$y|$key") } ?: planLineOf(y, key)
        if (line != null && line >= 0) {
            val text = yearLines(y)?.getOrNull(line)
            if (text != null) {
                val pool = pool() ?: emptyList()
                return CurriculumParse.plan(text, pool).takeIf { !it.isEmpty }
            }
        }
        // 兼容旧资产（`plan_<年>_<id>.json`）：换了包但文件名还在时仍能读
        val legacy = readText(CurriculumParse.fileName(y, key)) ?: return null
        return CurriculumParse.plan(legacy).takeIf { !it.isEmpty }
    }

    /** 大纲索引（2066 门，14 个学院） */
    fun syllabusIndex(): List<SyllabusIndexEntry> {
        cachedSyllabusIndex?.let { return it }
        val list = readText("syllabus/index.json.br")?.let(SyllabusParse::index).orEmpty()
        if (list.isNotEmpty()) {
            synchronized(this) {
                cachedSyllabusIndex = list
                syllabusById = buildMap {
                    list.forEach { e ->
                        e.id.takeIf { it.isNotBlank() }?.let { put(it, e) }
                        e.code.takeIf { it.isNotBlank() && !containsKey(it) }?.let { put(it, e) }
                    }
                }
            }
        }
        return list
    }

    /** 读单门大纲（索引 id 或课程代码都能定位） */
    fun syllabus(id: String): Syllabus? {
        val key = id.trim()
        if (key.isBlank()) return null
        if (cachedSyllabusIndex == null) syllabusIndex()
        val entry = synchronized(this) { syllabusById?.get(key) }

        if (entry != null && entry.pack.isNotBlank() && entry.line >= 0) {
            val lines = packLines(entry.pack)
            val text = lines?.getOrNull(entry.line)
            if (text != null) return SyllabusParse.syllabus(text).takeIf { !it.isEmpty }
        }
        // 兼容旧资产（`syllabus_<id>.json`）
        val legacy = readText("syllabus_$key.json") ?: return null
        return SyllabusParse.syllabus(legacy).takeIf { !it.isEmpty }
    }

    /**
     * 这门课有没有大纲（课表侧与培养方案侧的入口判断）。
     *
     * 依据索引 —— 现在覆盖**全校 14 个学院 2066 门**，通识课与研究生课仍可能不在册内，
     * 「没有」是正常现象。
     */
    fun hasSyllabus(code: String): Boolean {
        val c = SyllabusParse.codeOf(code)
        if (c.isBlank()) return false
        return syllabusIndex().any { SyllabusParse.codeOf(it.code) == c }
    }

    /** 大纲册子到底覆盖多少门（用于界面说明「这本册子里没有」） */
    fun syllabusCount(): Int = syllabusIndex().size

    /**
     * 课表课程 → 大纲（票 10 的匹配入口）。
     *
     * 返回唯一命中 / 多条候选 / 没有三种结局，界面据此说三种话。
     */
    fun matchSyllabus(code: String, name: String): SyllabusMatch =
        SyllabusParse.match(syllabusIndex(), code, name)

    // ── 包读取 ────────────────────────────────────────────

    /** 某学院包的行（LRU 2）；读不到返回 null，由调用方说「没读到」 */
    private fun packLines(pack: String): List<String>? = synchronized(this) {
        packCache[pack]?.let { return it }
        val text = readText("syllabus/$pack") ?: return null
        val lines = text.split('\n')
        packCache[pack] = lines
        lines
    }

    /** 某年方案包的行（只缓存一年；切年就重解，1 MB 级的活儿） */
    private fun yearLines(year: String): List<String>? = synchronized(this) {
        if (yearCacheYear == year && yearCacheLines.isNotEmpty()) return yearCacheLines
        val text = readText("plans/$year.jsonl.br") ?: return null
        val lines = text.split('\n')
        yearCacheYear = year
        yearCacheLines = lines
        lines
    }

    private fun pool(): List<JsonElement>? = synchronized(this) {
        poolRows?.let { return it }
        val text = readText("plans/pool.json.br") ?: return null
        val rows = CurriculumParse.pool(text)
        if (rows.isEmpty()) return null
        poolRows = rows
        rows
    }

    private fun planLineOf(year: String, id: String): Int? {
        index() ?: return null
        return synchronized(this) { planLineByKey?.get("$year|$id") }
    }

    /**
     * 读一个 brotli 资产并解成文本。
     *
     * 资产是 `.br` 且 `build.gradle.kts` 里声明了 `noCompress("br")` —— 进包时不再 deflate，
     * 所以这里拿到的是**原始 brotli 流**，必须走 [BrotliInputStream]。
     */
    private fun readText(name: String): String? {
        // 主线程守卫：宁可这次不显示，也不要为了 3 MB 解压卡帧
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            DebugLog.error("CURRICULUM", "在主线程读资产 $name —— 已拒绝（必须在 IO 线程）")
            return null
        }
        return runCatching {
            appCtx.assets.open("$dir/$name").use { input ->
                BrotliInputStream(input).use { br ->
                    br.readBytes().toString(Charsets.UTF_8)
                }
            }
        }.onFailure {
            DebugLog.error("CURRICULUM", "读资产失败 $name：${it.javaClass.simpleName} ${it.message}")
        }.getOrNull()
    }
}
