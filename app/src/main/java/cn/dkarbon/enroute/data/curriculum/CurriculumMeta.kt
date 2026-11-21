package cn.dkarbon.enroute.data.curriculum

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 资产来源说明（票 09 底部那行字）。
 *
 * ## 数字从哪来（2026-11-21 改正）
 *
 * 上一版这里写着「数字全部来自 META.json」，但**代码从来没有读过任何 meta 文件** ——
 * `GENERATED` 与 `TOTAL_PAGES` 都是硬编码常量。后果：资产重打包后忘了改这里，
 * 界面上的「生成于」会一直显示旧日期，且没有任何机制报警。
 *
 * 现在改成真读 `assets/curriculum/packed_meta.json`，并拆成两层：
 * · [parse] / [sourceLine] 是**纯函数**（JVM 单测直接喂 JSON 文本，不需要 Context）；
 * · [load] / [sourceLine] 的 Context 重载负责读资产并缓存。
 *
 * 读不到就退化成空串 / 0，界面自动省略那半句 —— 不拿常量冒充事实。
 *
 * 旧的 `META.json`（1,006 B）已删除：全仓没有一处 `assets.open` 读它，它只是被注释指着。
 */
object CurriculumMeta {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 从 packed_meta.json 抽出来的两样东西；其余字段界面用不到 */
    data class Meta(
        /** `2026-11-21`；空串 = 文件里没有或读不到 */
        val generated: String = "",
        /** 年级 → 培养方案 PDF 总页数 */
        val pages: Map<String, Int> = emptyMap(),
    )

    /** 解析 meta 文本（纯函数，可单测）。字段缺就缺，不猜。 */
    fun parse(text: String): Meta {
        val o = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return Meta()
        val generated = (o["生成时间"] as? JsonPrimitive)?.content.orEmpty().take(10)
        val pagesObj = o["源PDF页数"] as? JsonObject
        val pages = pagesObj?.mapNotNull { (k, v) ->
            val n = (v as? JsonPrimitive)?.content?.toIntOrNull()
            if (n != null && n > 0) k to n else null
        }?.toMap().orEmpty()
        return Meta(generated = generated, pages = pages)
    }

    // ── Context 重载：真读资产 ─────────────────────────────

    @Volatile
    private var cached: Meta? = null

    /** 读一次留内存（759 B 的文件，不值得每次进页面都解一遍） */
    fun load(ctx: Context): Meta {
        cached?.let { return it }
        val text = runCatching {
            ctx.applicationContext.assets.open("curriculum/packed_meta.json").use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return Meta()
        val m = parse(text)
        cached = m
        return m
    }

    /** 清理内存副本（换包 / 测试用） */
    fun invalidate() {
        cached = null
    }

    fun generated(ctx: Context): String = load(ctx).generated

    fun totalPages(ctx: Context, year: String): Int = load(ctx).pages[year.trim()] ?: 0

    fun sourceLine(ctx: Context, year: String, plan: CurriculumPlan? = null): String =
        sourceLine(load(ctx), year, plan)

    // ── 纯函数：排版那行字 ────────────────────────────────

    /**
     * `数据来自 2024 级本科人才培养方案（教务处 PDF，共 695 页），本专业收录 93 门课程，
     * 本专业位于 PDF 第 223–234 页 · 内置资产生成于 2026-11-21`
     *
     * 缺哪一段就省哪一段：没有页数就不写「共 N 页」，没有生成日期就不写「生成于」。
     */
    fun sourceLine(meta: Meta?, year: String, plan: CurriculumPlan? = null): String {
        val total = meta?.pages?.get(year.trim()) ?: 0
        val head = if (total > 0) {
            "数据来自 $year 级本科人才培养方案（教务处 PDF，共 $total 页）"
        } else {
            "数据来自内置培养方案资产"
        }
        val range = plan?.pageRange?.takeIf { it.size >= 2 }
            ?.let { "，本专业位于 PDF 第 ${it[0]}–${it[1]} 页" }.orEmpty()
        val inner = if (plan != null && plan.courses.isNotEmpty()) {
            "，本专业收录 ${plan.courses.size} 门课程$range"
        } else {
            ""
        }
        val gen = meta?.generated.orEmpty()
        val tail = if (gen.isNotBlank()) " · 内置资产生成于 $gen" else ""
        return "$head$inner$tail"
    }
}