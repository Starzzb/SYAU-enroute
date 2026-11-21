package cn.dkarbon.enroute.data.curriculum

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 课程大纲的解析与匹配（票 10）—— 全是纯函数。
 *
 * ## 匹配为什么要有「归一」
 * 课表侧给的名字与大纲册子的名字经常对不上，实测的真实差异有两类：
 * 1. **罗马数字写法不同**：`形势与政策Ⅴ`（U+2164）vs `形势与政策V`（ASCII V）；
 * 2. 全角/半角、空格、以及课表课程号带课序号（`901040302_01`）。
 * 所以先 [codeOf] 拆课序号、按**课程代码精确**匹配；代码不中再比 [normalizeCourseName] 归一名。
 */
object SyllabusParse {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 索引的 `hours` 是数组，兜底也接受字符串 */
    fun index(text: String): List<SyllabusIndexEntry> {
        val arr = runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
            ?: return emptyList()
        return arr.filterIsInstance<JsonObject>().map { o ->
            val hours = (o["hours"] as? JsonArray)?.map { (it as? JsonPrimitive)?.content.orEmpty() }
                .orEmpty()
            SyllabusIndexEntry(
                id = str(o, "id").ifBlank { str(o, "code") },
                code = str(o, "code"),
                name = str(o, "name"),
                type = str(o, "type"),
                credit = str(o, "credit"),
                theoryHours = hours.getOrNull(0).orEmpty(),
                labHours = hours.getOrNull(1).orEmpty(),
                bytes = str(o, "bytes").toIntOrNull() ?: 0,
                pack = str(o, "pack"),
                line = str(o, "line").toIntOrNull() ?: -1,
                college = str(o, "college"),
                pageRange = (o["pages"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty(),
            )
        }.filter { it.name.isNotBlank() && it.id.isNotBlank() }
    }

    /** 解析单门大纲。结构见 `syllabus_<id>.json`；缺字段一律空，不抛异常。 */
    fun syllabus(text: String): Syllabus {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return Syllabus()
        val basic = (o["基本信息"] as? JsonObject)
            ?.mapNotNull { (k, v) ->
                val s = (v as? JsonPrimitive)?.content?.trim().orEmpty()
                if (s.isBlank()) null else k to s
            }
            .orEmpty()
        val sections = (o["章节"] as? JsonObject)
            ?.mapNotNull { (k, v) ->
                val t = (v as? JsonPrimitive)?.content?.trim().orEmpty()
                // 原文可能只有标题没有正文（`八、本课程的先修课程` 实测就 5 个字）；
                // 这种也保留，界面显示「原文这一节为空」而不是假装没这一节
                SyllabusSection(title = k, text = CurriculumParse.tidy(t))
            }
            .orEmpty()
        val chapters = (o["教学章节"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.map { c ->
                SyllabusChapter(
                    title = str(c, "条目"),
                    unit = str(c, "单位"),
                    note = str(c, "说明"),
                )
            }
            ?.filter { it.title.isNotBlank() }
            .orEmpty()
        val hours = (o["教学内容学时一览表"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.map { r ->
                SyllabusHoursRow(
                    content = str(r, "教学内容"),
                    online = str(r, "线上学时"),
                    offline = str(r, "线下学时"),
                    lab = str(r, "实验学时数"),
                    total = str(r, "学时合计"),
                )
            }
            ?.filter { it.content.isNotBlank() }
            .orEmpty()
        val goals = (o["课程目标列表"] as? JsonArray)
            ?.mapNotNull { el ->
                when (el) {
                    is JsonPrimitive -> PlanPoint(content = CurriculumParse.tidy(el.content))
                        .takeIf { !it.isEmpty }
                    is JsonObject -> PlanPoint(
                        no = str(el, "编号"),
                        content = CurriculumParse.tidy(str(el, "内容")),
                    ).takeIf { !it.isEmpty }
                    else -> null
                }
            }
            .orEmpty()

        return Syllabus(
            code = str(o, "课程代码"),
            name = str(o, "课程名称"),
            englishName = str(o, "英文名称"),
            type = str(o, "课程类型"),
            credit = str(o, "课程学分"),
            theoryHours = str(o, "理论学时"),
            labHours = str(o, "实验学时"),
            category = str(o, "课程类别"),
            department = str(o, "开课教研室"),
            writer = str(o, "执笔人"),
            madeAt = str(o, "制定时间"),
            basicInfo = basic,
            sections = sections,
            chapters = chapters,
            hoursTable = hours,
            goals = goals,
        )
    }

    /**
     * 课表课程号 → 大纲课程代码。
     *
     * 课表侧的 `kch` 有时带课序号（实测 `901040302_01`），大纲册子里只有 9 位课程代码，
     * 所以把 `_` 之后的部分拆掉；非数字的尾巴（如 `901040302A`）保留。
     */
    fun codeOf(raw: String): String {
        val t = raw.trim()
        if (t.isBlank()) return ""
        val head = t.substringBefore('_').substringBefore(' ').trim()
        return if (head.isNotBlank()) head else t
    }

    /** 罗马数字（Ⅰ…Ⅻ 与 ⅰ…ⅻ）→ ASCII */
    private val ROMAN = mapOf(
        'Ⅰ' to "I", 'Ⅱ' to "II", 'Ⅲ' to "III", 'Ⅳ' to "IV",
        'Ⅴ' to "V", 'Ⅵ' to "VI", 'Ⅶ' to "VII", 'Ⅷ' to "VIII",
        'Ⅸ' to "IX", 'Ⅹ' to "X", 'Ⅺ' to "XI", 'Ⅻ' to "XII",
        'ⅰ' to "I", 'ⅱ' to "II", 'ⅲ' to "III", 'ⅳ' to "IV",
        'ⅴ' to "V", 'ⅵ' to "VI", 'ⅶ' to "VII", 'ⅷ' to "VIII",
        'ⅸ' to "IX", 'ⅹ' to "X", 'ⅺ' to "XI", 'ⅻ' to "XII",
    )

    /**
     * 课程名归一：**只做形态归一，不改语义**。
     *
     * 1. 罗马数字 → ASCII（`Ⅴ` → `V`，`Ⅱ` → `II`）；
     * 2. 全角字母数字 → 半角（`Ａ` → `A`、`１` → `1`）；
     * 3. 去掉所有空白与常见分隔符（`·`、`-`、`（）` 保留中文括号内的内容本身）；
     * 4. ASCII 字母统一大写。
     *
     * 归一只用于**比对**，上屏永远用原文。
     */
    fun normalizeCourseName(raw: String): String {
        if (raw.isBlank()) return ""
        val sb = StringBuilder()
        raw.forEach { ch ->
            val roman = ROMAN[ch]
            when {
                roman != null -> sb.append(roman)
                ch in '！'..'～' -> sb.append((ch.code - 0xFEE0).toChar()) // 全角 ASCII 区
                ch == '　' -> Unit                                       // 全角空格
                else -> sb.append(ch)
            }
        }
        return sb.toString()
            .replace(Regex("""[\s·\-—_/、,，.。()（）【】\[\]"'“”]+"""), "")
            .uppercase()
    }

    /**
     * 课表/搜索 → 大纲索引的匹配。优先级**严格**：
     * 1. 课程代码精确（[codeOf] 去课序号后）
     * 2. 归一课程名精确
     * 3. 归一课程名互相包含（最后手段）
     *
     * 任何一级命中多条 → 只返回 [SyllabusMatch.candidates]（交给用户选），**不猜**。
     */
    fun match(entries: List<SyllabusIndexEntry>, code: String, name: String): SyllabusMatch {
        if (entries.isEmpty()) return SyllabusMatch()
        val c = codeOf(code)
        if (c.isNotBlank()) {
            val byCode = entries.filter { codeOf(it.code) == c || codeOf(it.id) == c }
            if (byCode.size == 1) return SyllabusMatch(byCode.first(), by = "code")
            if (byCode.size > 1) return SyllabusMatch(candidates = byCode, by = "code")
        }
        val n = normalizeCourseName(name)
        if (n.isBlank()) return SyllabusMatch()
        val exact = entries.filter { normalizeCourseName(it.name) == n }
        if (exact.size == 1) return SyllabusMatch(exact.first(), by = "name")
        if (exact.size > 1) return SyllabusMatch(candidates = exact, by = "name")
        val fuzzy = entries.filter {
            val en = normalizeCourseName(it.name)
            en.isNotBlank() && (en.contains(n) || n.contains(en))
        }
        return when {
            fuzzy.size == 1 -> SyllabusMatch(fuzzy.first(), by = "fuzzy")
            fuzzy.size > 1 -> SyllabusMatch(candidates = fuzzy, by = "fuzzy")
            else -> SyllabusMatch()
        }
    }

    /** 索引里的类型去重（理论课/实验课/实践课），给筛选 chip 用，保持原始顺序 */
    fun types(entries: List<SyllabusIndexEntry>): List<String> =
        entries.map { it.type }.filter { it.isNotBlank() }.distinct()

    /**
     * 搜索页的过滤：名称/代码**包含**即命中（搜索框就该宽松），类型精确。
     *
     * @param query 空白 = 不过滤
     * @param type 空白 = 全部类型
     */
    fun filter(
        entries: List<SyllabusIndexEntry>,
        query: String,
        type: String,
        colleges: Set<String> = emptySet(),
    ): List<SyllabusIndexEntry> {
        val q = normalizeCourseName(query)
        val t = type.trim()
        return entries.filter { e ->
            val typeOk = t.isBlank() || e.type == t
            // 多选：空集 = 不过滤；否则命中任一选中学院即可
            val collegeOk = colleges.isEmpty() || e.college in colleges
            val qOk = q.isBlank() ||
                normalizeCourseName(e.name).contains(q) ||
                e.code.contains(query.trim()) ||
                e.name.contains(query.trim())
            typeOk && collegeOk && qOk
        }
    }

    private fun str(o: JsonObject?, key: String): String {
        if (o == null) return ""
        val v = o[key] ?: return ""
        if (v is JsonNull) return ""
        return (v as? JsonPrimitive)?.content?.trim().orEmpty()
    }
}
