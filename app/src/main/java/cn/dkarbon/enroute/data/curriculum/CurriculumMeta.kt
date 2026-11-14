package cn.dkarbon.enroute.data.curriculum

/**
 * 资产来源说明（票 09 底部那行字）。
 *
 * 数字全部来自 `assets/curriculum/META.json`：`source` 里写明每份 PDF 的页数。
 * 这里只做「年份 → 总页数」的映射，**不重新计数**，也不猜。
 */
object CurriculumMeta {

    /** assets 生成日期（META.json 的 `generated`）；写进界面让用户知道这份内置数据多新 */
    const val GENERATED = "2026-11-14"

    private val TOTAL_PAGES = mapOf("2023" to 689, "2024" to 695, "2025" to 693)

    fun totalPages(year: String): Int = TOTAL_PAGES[year.trim()] ?: 0

    /**
     * `数据来自 2024 级本科人才培养方案（教务处 PDF，共 695 页）· 内置资产生成于 2026-11-14`
     *
     * @param plan 当前打开的方案；给了就再补一句「本专业位于 PDF 第 223–234 页」，
     *   方便用户拿纸质版/PDF 对照（[CurriculumPlan.pageRange] 是实测页码）
     */
    fun sourceLine(year: String, plan: CurriculumPlan? = null): String {
        val total = totalPages(year)
        val head = if (total > 0) {
            "数据来自 $year 级本科人才培养方案（教务处 PDF，共 $total 页）"
        } else {
            "数据来自内置培养方案资产（年份未知）"
        }
        val range = plan?.pageRange?.takeIf { it.size >= 2 }?.let { "，本专业位于 PDF 第 ${it[0]}–${it[1]} 页" }
            .orEmpty()
        val inner = if (plan != null && plan.courses.isNotEmpty()) {
            "，本专业收录 ${plan.courses.size} 门课程$range"
        } else {
            ""
        }
        return "$head$inner · 内置资产生成于 $GENERATED"
    }
}
