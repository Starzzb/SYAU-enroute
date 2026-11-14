package cn.dkarbon.enroute.data.curriculum

import android.content.Context
import cn.dkarbon.enroute.data.DebugLog

/**
 * 内置培养方案资产的读取层（票 09）。
 *
 * 三条纪律：
 * 1. **按需读一份**：索引（约 7KB）在进页面时读一次；单专业方案（最大 62KB）只在选定专业时读，
 *    不预加载、不缓存全量（101 份 × 40KB 撑不住）。
 * 2. **只在 IO 线程调**：ViewModel 统一包 `withContext(Dispatchers.IO)`；这里再挡一道，
 *    主线程调用直接记日志并返回 null，免得有人「顺手」在主线程读 62KB JSON 卡帧。
 * 3. 读不到就返回 null，由界面说「没读到」，不抛异常。
 */
class CurriculumAssets(private val ctx: Context) {

    private val appCtx = ctx.applicationContext
    private val dir = "curriculum"

    /** 大纲索引缓存（166 条，22KB；只读一次） */
    private var cachedSyllabusIndex: List<SyllabusIndexEntry>? = null

    /** 读 `plan_index.json` */
    fun index(): PlanIndex? = readText("plan_index.json")?.let(CurriculumParse::index)
        ?.takeIf { !it.isEmpty }

    /** 读某个专业的方案；文件名按 [CurriculumParse.fileName] 的约定拼 */
    fun plan(year: String, id: String): CurriculumPlan? {
        val name = CurriculumParse.fileName(year, id)
        val text = readText(name) ?: return null
        return CurriculumParse.plan(text).takeIf { !it.isEmpty }
    }

    /** 大纲索引（166 门）。首次用时读一次并留在内存（22KB，值这个内存） */
    fun syllabusIndex(): List<SyllabusIndexEntry> {
        cachedSyllabusIndex?.let { return it }
        val list = readText("syllabus_index.json")?.let(SyllabusParse::index).orEmpty()
        cachedSyllabusIndex = list
        return list
    }

    /** 读单门大纲（最大 ~19KB），按索引 id 取 */
    fun syllabus(id: String): Syllabus? {
        val key = id.trim()
        if (key.isBlank()) return null
        val text = readText("syllabus_$key.json") ?: return null
        return SyllabusParse.syllabus(text).takeIf { !it.isEmpty }
    }

    /**
     * 这门课有没有大纲（课表侧与培养方案侧的入口判断）。
     *
     * 依据 `syllabus_index.json` —— 它只覆盖**土地与环境学院 166 门**，
     * 通识课与其它学院的课不在册内，「没有」是正常现象。
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

    private fun readText(name: String): String? {
        // 主线程守卫：宁可这次不显示，也不要在主线程解析 62KB
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            DebugLog.error("CURRICULUM", "在主线程读资产 $name —— 已拒绝（必须在 IO 线程）")
            return null
        }
        return runCatching {
            appCtx.assets.open("$dir/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onFailure {
            DebugLog.error("CURRICULUM", "读资产失败 $name：${it.javaClass.simpleName} ${it.message}")
        }.getOrNull()
    }
}
