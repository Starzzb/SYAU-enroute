package cn.dkarbon.enroute.data.telemetry

/**
 * 使用数据采集的**唯一白名单**（`docs/规划-使用数据采集.md` §2）。
 *
 * ## 一句话立场
 *
 * 采「发生了什么」，不采「是谁发生的」。所以这里每个字段的取值都是**枚举串**，
 * 没有任何自由文本的位置 —— 想往里塞课程名、搜索词、学号，代码层面根本塞不进去：
 * 白名单外的值一律被替换成 [INVALID]。
 *
 * ## 加事件要动三处
 *
 * 本文件、`server/telemetry/schema.json`、`docs/实现-使用数据采集-m40.md`。
 * `TelemetrySchemaTest` 会拿 schema.json 对着本文件逐条核对，漏改一端测试就红。
 *
 * ## 值域怎么写
 *
 * 全部是字符串。布尔写 `"true"` / `"false"`；数值**先分档再上报**（`"1-5"` 而不是 `3`）。
 * 这样既没有精度可反推，也不需要为「记个数字」另开一条数值通道。
 */
object TelemetrySchema {

    /** schema 版本；改结构就加一，服务端按它决定认不认这一批 */
    const val VERSION = 1

    /** 落盘/上传一条事件的字节上限；超了直接丢（正常一条 < 200 字节） */
    const val EVENT_MAX_BYTES = 2048

    /** 白名单外的值统一替换成这个，服务端据此就能发现「有人在往里塞东西」 */
    const val INVALID = "invalid"

    /** 布尔档 */
    val BOOL: Set<String> = setOf("true", "false")

    fun bool(v: Boolean): String = if (v) "true" else "false"

    /** 面数档（地图标注用，三处共用一份，免得改一处漏两处） */
    val FACE_BUCKETS: Set<String> = setOf("0", "1", "2-5", "6-20", "20+")

    /**
     * 版本号的哨兵集合标识。
     *
     * 版本号没法预先枚举，用哨兵值表示「走 [isVersionLike] 校验」——
     * 比 `null` 好：调用方不必为它单开分支，加校验也只是换一个集合。
     */
    const val VERSION_SENTINEL = "\u0000ver"

    /** `ver` 专用：只认 `0.9.1+m39` / `1.0.0` 这种形态，不认任何别的串 */
    fun isVersionLike(v: String): Boolean =
        Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}(\+m\d{1,5})?$""").matches(v)

    /** 事件 → 字段 → 允许的取值 */
    val EVENTS: Map<String, Map<String, Set<String>>> = buildEvents()

    private fun buildEvents(): Map<String, Map<String, Set<String>>> {
        val ver = setOf(VERSION_SENTINEL)
        return mapOf(

            // ── 生命周期 ─────────────────────────────────
            "app.start" to mapOf(
                "cold" to BOOL,
                // 安装后第几天：档位，不报精确天数（精确天数能和别的数据对上号）
                // 与 180 天保留期对齐（见 Telemetry.bucketDaysSinceInstall）
                "ms_since_install" to setOf("0", "1-7", "8-30", "31-90", "91-180", "180+"),
                "ver" to ver,
                // ABI 只到位数粒度，不报机型
                "abi" to setOf("arm64", "arm32", "x86", "unknown"),
            ),
            "app.background" to mapOf(
                "session_s" to setOf("0-30", "30-120", "120-600", "600+"),
            ),
            "app.crash" to mapOf(
                // 只记「哪儿」，不记堆栈：堆栈里可能带路径、课程名、学号
                "where" to setOf("ui", "io", "net", "parse", "other"),
                "ver" to ver,
                "ms_since_start" to setOf("0-5", "5-30", "30-120", "120+"),
            ),

            // ── 课程大纲（当前最该知道的一块）──────────────
            "syllabus.open" to mapOf(
                "from" to setOf("timetable", "detail", "search"),
                "hit" to BOOL,
            ),
            "syllabus.miss" to mapOf(
                "has_code" to BOOL,
            ),
            "syllabus.search" to mapOf(
                // 搜索词**原文绝不进管道**，只记长度档与结果数档
                "qlen" to setOf("0", "1-2", "3+"),
                "results" to setOf("0", "1-5", "6-20", "20+"),
            ),

            // ── 培养方案 ─────────────────────────────────
            "plan.open" to mapOf(
                "year" to setOf("2023", "2024", "2025", "unknown"),
                "from" to setOf("curriculum", "course", "search"),
            ),
            "plan.course_tap" to mapOf(
                "has_syllabus" to BOOL,
            ),

            // ── 地图共建 ─────────────────────────────────
            "map.edit.save" to mapOf(
                "faces" to FACE_BUCKETS,
                "fields" to setOf("1", "2-5", "6+"),
                "custom_kinds" to setOf("0", "1-2", "3+"),
            ),
            "map.edit.export" to mapOf(
                "format" to setOf("geojson", "csv"),
                "faces" to FACE_BUCKETS,
            ),
            "map.edit.import" to mapOf(
                "ok" to BOOL,
                "faces" to FACE_BUCKETS,
                "skipped" to setOf("0", "1-5", "6+"),
            ),
            "map.sheet.detent" to mapOf(
                "detent" to setOf("0.30", "0.62", "0.92"),
            ),

            // ── 导入与失败面 ─────────────────────────────
            "import.json.try" to mapOf(
                "ok" to BOOL,
                "rows" to setOf("0", "1-50", "51-500", "500+"),
                "err" to setOf("format", "schema", "empty", "other"),
            ),
            "net.fail" to mapOf(
                "where" to setOf("urp", "feed", "cxcy", "map", "update"),
                "kind" to setOf("unreachable", "server", "parse"),
                // 只要状态码类别，不要完整 URL 与响应体
                "http" to setOf("(none)", "4xx", "5xx"),
            ),

            // ── 更新通道 ─────────────────────────────────
            "update.check" to mapOf(
                "result" to setOf("none", "has", "mandatory"),
                // manifest 的六条候选谁常胜出（1 起，按列表顺序；1 = 自建口）
                "channel" to setOf("1", "2", "3", "4", "5", "6", "(none)"),
            ),
            "update.download" to mapOf(
                "ok" to BOOL,
                "sha_ok" to BOOL,
                "ms" to setOf("0-3", "3-10", "10-30", "30+"),
            ),

            // ── 性能（粗粒度）───────────────────────────
            "perf.first_frame" to mapOf(
                "ms" to setOf("<300", "300-800", "800-1500", "1500+"),
            ),
            "perf.asset_read" to mapOf(
                "asset" to setOf("syllabus", "plan", "pool"),
                "ms" to setOf("0-50", "50-200", "200-600", "600+"),
            ),
        )
    }

    /**
     * 洗一条事件。
     *
     * @return 洗好的 props；事件名不在白名单里返回 `null`（调用方直接丢弃）
     *
     * 规则（每条都有单测）：
     *  · 事件名必须完全命中 [EVENTS]；
     *  · 字段名必须在该事件的白名单里，**多余字段直接丢**，不做近似匹配；
     *  · 值不在允许集合里 → 换成 [INVALID]（不是丢掉整条：值错了，但事件确实发生了）；
     *  · 值是空白 → 丢掉该字段（「缺省就不写」，不写空串占位）。
     */
    fun sanitize(event: String, props: Map<String, String>): Map<String, String>? {
        val allowed = EVENTS[event] ?: return null
        val out = LinkedHashMap<String, String>(allowed.size)
        for ((k, v) in props) {
            val domain = allowed[k] ?: continue
            val value = v.trim()
            if (value.isEmpty()) continue
            val ok = if (domain.size == 1 && domain.first() == VERSION_SENTINEL) {
                isVersionLike(value)
            } else {
                value in domain
            }
            out[k] = if (ok) value else INVALID
        }
        return out
    }

    /** 这条事件名在白名单里吗（热路径先问一句，免得白拼 map） */
    fun accepts(event: String): Boolean = EVENTS.containsKey(event)
}