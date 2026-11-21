package cn.dkarbon.enroute.data.curriculum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 培养方案资产的**纯解析与选择逻辑**（票 09）。
 *
 * 全部是纯函数：喂 JSON 字符串 / 索引，返回模型。单测直接打这些，不需要 Android。
 * 读资产的那一层在 [CurriculumAssets]（要 Context，且必须在 IO 线程）。
 */
object CurriculumParse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val NUMBER = Regex("""^\d+(\.\d+)?$""")

    /** 开课学期的汉字 → 排序权重；认不出排最后（`其余`），不猜它是第几学期 */
    private val TERM_ORDER = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4,
        "五" to 5, "六" to 6, "七" to 7, "八" to 8,
    )

    const val TERM_UNKNOWN = "未标注学期"

    // ── 索引 ──────────────────────────────────────────────

    /** 解析 `plan_index.json`：`{"2024":[{id,major,type,bytes,courses}], "2025":[…]}` */
    fun index(text: String): PlanIndex {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return PlanIndex()
        val byYear = LinkedHashMap<String, List<PlanIndexEntry>>()
        root.forEach { (year, value) ->
            val arr = value as? JsonArray ?: return@forEach
            val entries = arr.filterIsInstance<JsonObject>().map { o ->
                PlanIndexEntry(
                    id = str(o, "id"),
                    major = str(o, "major"),
                    type = str(o, "type"),
                    bytes = num(o, "bytes"),
                    courses = num(o, "courses"),
                    line = num(o, "line").takeIf { o.containsKey("line") } ?: -1,
                )
            }.filter { it.id.isNotBlank() }
            if (entries.isNotEmpty()) byYear[year] = entries
        }
        return PlanIndex(byYear)
    }

    /** 资产文件名（**与 pack.py 的命名约定一致**）：`plan_2024_土地资源管理.json` */
    fun fileName(year: String, id: String): String = "plan_${year.trim()}_${id.trim()}.json"

    /**
     * 按年级选版本：`2024级` → `2024`。
     *
     * 年级识别不出来（没登录 / 学籍没这个字段）或该年没有资产时，**回落到最新的一年**——
     * 新生儿总是最新年级，这比不显示更不容易误导；界面上会标明是哪一版。
     */
    fun pickYear(grade: String, years: Collection<String>): String {
        val available = years.filter { it.isNotBlank() }.sorted()
        if (available.isEmpty()) return ""
        val y = Regex("""(20\d{2})""").find(grade.trim())?.groupValues?.get(1)
        if (y != null && y in available) return y
        return available.last()
    }

    /**
     * 在某一年里按专业名定位。
     *
     * 规则：先精确同名；同名多条时优先「普通班」（其余是实验班，课程更少）；
     * 精确匹配不到再试「互相包含」（学籍写「土地资源管理专业」这种情况）。
     * **都匹配不到返回 null** —— 由界面让用户手动选，不猜。
     */
    fun pickPlan(index: PlanIndex, year: String, major: String): PlanIndexEntry? {
        val entries = index.entries(year)
        if (entries.isEmpty()) return null
        val target = major.trim().replace("专业", "").trim()
        if (target.isBlank()) return null
        val exact = entries.filter { it.major.trim() == target }
        val pool = exact.ifEmpty {
            entries.filter { it.major.contains(target) || target.contains(it.major) }
        }
        if (pool.isEmpty()) return null
        return pool.firstOrNull { it.type.contains("普通班") } ?: pool.first()
    }

    // ── 单专业方案 ────────────────────────────────────────

    /**
     * 解析单专业方案 JSON。字段缺失一律空串/空表，不抛异常、不补猜。
     *
     * @param pool 课程行共享池（`plans/pool.json.br` 解出来的 `[{课程代码,课程名称,…}]`）。
     *   2026-11-17 起资产的「课程设置与教学进程」**存的是池下标**（`[0,1,2,1253,…]`）——
     *   17232 行课程只有 5574 种不同文本，抽池后 brotli 体积再降 40%。
     *   传空池 = 旧格式（原样是对象数组），保持兼容。
     */
    fun plan(text: String, pool: List<JsonElement> = emptyList()): CurriculumPlan {
        val raw = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return CurriculumPlan()
        val o = if (pool.isEmpty()) raw else expandPool(raw, pool)
        val courses = (o["课程设置与教学进程"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.map { c ->
                CurriculumCourse(
                    code = str(c, "课程代码"),
                    name = str(c, "课程名称"),
                    englishName = str(c, "英文名称"),
                    category = str(c, "课程类别"),
                    // 空串保持空串：原文空就是空，绝不拿「课程类别」冒充
                    nature = str(c, "课程性质"),
                    credit = str(c, "学分"),
                    hours = str(c, "总学时"),
                    theoryHours = str(c, "理论学时"),
                    labHours = str(c, "实验学时"),
                    term = str(c, "开课学期"),
                    creditRequirement = str(c, "学分要求"),
                )
            }
            ?.filter { !it.isEmpty }
            .orEmpty()

        val notes = listOf("开课单位说明", "课程替代情况说明", "备注")
            .mapNotNull { k -> str(o, k).takeIf { it.isNotBlank() }?.let { k to it } }

        return CurriculumPlan(
            year = str(o, "年级"),
            major = str(o, "专业名称"),
            type = str(o, "培养类型"),
            pageRange = (o["章节页范围"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty(),
            intro = tidy(str(o, "专业介绍")),
            goals = tidy(str(o, "培养目标")),
            requirements = tidy(str(o, "毕业要求")),
            mainDiscipline = tidy(str(o, "主干学科")),
            coreCourses = tidy(str(o, "专业核心课程")),
            practice = tidy(str(o, "主要实践性教学环节")),
            duration = tidy(str(o, "修业年限")),
            degree = tidy(str(o, "授予学位")),
            goalList = points(o["培养目标列表"]),
            requirementList = requirements(o["毕业要求列表"]),
            courses = courses,
            creditTable = table(o["学分结构表"]),
            semesterTable = table(o["各学期学分分配表"]),
            notes = notes.map { (k, v) -> k to tidy(v) },
            goalPages = (o["目标与要求页码"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty(),
        )
    }

    /** 课程行共享池：`plans/pool.json.br` 是**一整行 JSON 数组**（5574 条课程行）。 */
    fun pool(text: String): List<JsonElement> =
        runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()?.toList().orEmpty()

    /**
     * 把「课程设置与教学进程」里的**池下标**换回原始课程行。
     *
     * 只替换能对上下标的元素；对不上（越界/非数字）的元素**原样保留**——
     * 宁可这一行解析成空课程，也不许拿相邻行冒充（数据诚实）。
     */
    private fun expandPool(o: JsonObject, pool: List<JsonElement>): JsonObject {
        val arr = o["课程设置与教学进程"] as? JsonArray ?: return o
        val expanded = JsonArray(
            arr.map { el ->
                val i = (el as? JsonPrimitive)?.content?.trim()?.toIntOrNull()
                if (i != null && i in pool.indices) pool[i] else el
            },
        )
        return JsonObject(o.toMutableMap().apply { put("课程设置与教学进程", expanded) })
    }

    /**
     * **最低毕业学分 —— 只认学分结构表。**
     *
     * 表里找首列含「总学分」的那一行，取它后面第一个纯数字单元格（`165` / `175`）。
     * 找不到（有的专业 PDF 该页没抽全，实测 101 份里有 **19 份**没有这一行/整张表为空）
     * → 返回空串，界面显示「暂缺」，**绝不拿课程行求和顶上**。
     */
    fun minCredits(plan: CurriculumPlan): String {
        val row = plan.creditTable.firstOrNull { it.firstOrNull()?.contains("总学分") == true }
            ?: return ""
        return row.drop(1).map { it.trim() }.firstOrNull { NUMBER.matches(it) }.orEmpty()
    }

    /** 学分结构表是不是压根没抽到（界面据此解释为什么没有学分） */
    fun creditTableMissing(plan: CurriculumPlan): Boolean = plan.creditTable.isEmpty()

    /**
     * 课程设置与教学进程 → 按开课学期分组（一…八，认不出的归「未标注学期」排最后）。
     *
     * 组内保持原表顺序（原表是按模块排的，重排会让人对不上 PDF）。
     */
    fun coursesByTerm(plan: CurriculumPlan): List<Pair<String, List<CurriculumCourse>>> {
        val groups = LinkedHashMap<String, MutableList<CurriculumCourse>>()
        plan.courses.forEach { c ->
            val key = c.term.trim().ifBlank { TERM_UNKNOWN }
            groups.getOrPut(key) { mutableListOf() }.add(c)
        }
        return groups.entries
            .sortedWith(
                compareBy(
                    { TERM_ORDER[it.key] ?: Int.MAX_VALUE },
                    { it.key },
                ),
            )
            .map { it.key to it.value.toList() }
    }

    /** `一` → `第 一 学期`；未知返回 [TERM_UNKNOWN] */
    fun termLabel(term: String): String {
        val t = term.trim()
        return if (TERM_ORDER.containsKey(t)) "第 $t 学期" else TERM_UNKNOWN
    }

    // ── 工具 ──────────────────────────────────────────────

    /**
     * 整理 PDF 抽出来的段落换行。
     *
     * PDF 的断行经常落在**词中间**（实测「地理信息科学、不动产\n估价」），直接上屏会出现
     * 「不动产 / 估价」这种断词；而句子之间的换行是要保留的（毕业要求一条一行）。
     * 规则：上一行以句末标点结尾 → 保留换行；否则把两行接回去（中文不需要补空格）。
     * **只动空白，不动任何字**。
     */
    fun tidy(raw: String): String {
        if (raw.isBlank()) return ""
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.trim() }
        val out = StringBuilder()
        for (line in lines) {
            if (line.isEmpty()) {
                if (out.isNotEmpty() && !out.endsWith("\n")) out.append('\n')
                continue
            }
            if (out.isNotEmpty() && !out.endsWith("\n")) {
                val last = out.last()
                if (last in "。；！？") out.append('\n')
            }
            out.append(line)
        }
        return out.toString().replace(Regex("""\n{2,}"""), "\n").trim()
    }

    private fun str(o: JsonObject?, key: String): String {
        if (o == null) return ""
        val v = o[key] ?: return ""
        if (v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content?.trim().orEmpty()
    }

    private fun num(o: JsonObject?, key: String): Int {
        val t = str(o, key)
        return t.toDoubleOrNull()?.toInt() ?: 0
    }

    /**
     * `培养目标列表` / `指标点`：原文是 `[{编号, 内容}]`，也容忍纯字符串数组。
     */
    private fun points(e: JsonElement?): List<PlanPoint> =
        (e as? JsonArray)?.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> PlanPoint(content = tidy(el.content)).takeIf { !it.isEmpty }
                is JsonObject -> PlanPoint(
                    no = str(el, "编号"),
                    content = tidy(str(el, "内容")),
                ).takeIf { !it.isEmpty }
                else -> null
            }
        }.orEmpty()

    /** `毕业要求列表`：`[{编号, 标题, 内容, 指标点:[{编号,内容}]}]` */
    private fun requirements(e: JsonElement?): List<PlanRequirement> =
        (e as? JsonArray)?.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> PlanRequirement(content = tidy(el.content)).takeIf { !it.isEmpty }
                is JsonObject -> PlanRequirement(
                    no = str(el, "编号"),
                    title = tidy(str(el, "标题")),
                    content = tidy(str(el, "内容")),
                    indicators = points(el["指标点"]),
                ).takeIf { !it.isEmpty || it.indicators.isNotEmpty() }
                else -> null
            }
        }.orEmpty()

    /** `[[ ["表头","表头"], … ]]` → 行列表（只取第一张表，保持原始单元格文本） */
    private fun table(e: JsonElement?): List<List<String>> {
        val outer = e as? JsonArray ?: return emptyList()
        val first = outer.firstOrNull() as? JsonArray ?: return emptyList()
        return first.filterIsInstance<JsonArray>().map { row ->
            row.map { cell -> (cell as? JsonPrimitive)?.content?.trim().orEmpty() }
        }
    }
}
