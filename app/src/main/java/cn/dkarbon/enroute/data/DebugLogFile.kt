package cn.dkarbon.enroute.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志的**落盘**（票 20 / D2）。
 *
 * 设计取舍（都来自 `docs/计划-调试日志v2.md`）：
 * 1. **按天一个文件**：`<root>/2026-11-14.log`，只追加不重写 —— 崩溃时已写入的行不会丢；
 * 2. **不指望 `onTerminate`**（Android 常常不调用它）：改成**每 500ms 或攒够 20 行就 flush**，
 *    牺牲一点 I/O 换「崩溃前最后几行还在」；
 * 3. **有上限**：保留 [MAX_DAYS] 天、总量封顶 [MAX_BYTES]，超了从最旧的删；
 * 4. **落盘即脱敏**：内存里丢就丢了，文件是可能被导出/被读的 —— 写之前必须把
 *    Cookie / CAS 票据 / 密码字段 / token 一律抹成 `***`；
 * 5. **失败一律吞掉**：写日志出错绝不允许影响业务。
 *
 * 根目录是**构造参数**（[root]），单测传临时目录，不碰 Android 的 `filesDir`。
 */
class DebugLogFile(private val root: File) {

    /** 收行 + 批量 flush 的锁 */
    private val lock = Any()
    private val buffer = ArrayList<String>(FLUSH_LINES)

    @Volatile
    private var lastFlushAt: Long = 0L

    /** 当前进程内写过的行数（调试页可以显示） */
    @Volatile
    var written: Long = 0L
        private set

    /** 追加一行（**已脱敏**由本函数负责） */
    fun append(line: String) {
        val safe = redact(line)
        var flushNow = false
        synchronized(lock) {
            buffer += safe
            if (buffer.size >= FLUSH_LINES) flushNow = true
            else if (System.currentTimeMillis() - lastFlushAt >= FLUSH_INTERVAL_MS) flushNow = true
        }
        if (flushNow) flush()
    }

    /** 把缓冲写下去。可安全并发调用；失败静默。 */
    fun flush() {
        val batch: List<String>
        synchronized(lock) {
            if (buffer.isEmpty()) {
                lastFlushAt = System.currentTimeMillis()
                return
            }
            batch = buffer.toList()
            buffer.clear()
            lastFlushAt = System.currentTimeMillis()
        }
        runCatching {
            root.mkdirs()
            File(root, dayFileName(todayString())).appendText(
                batch.joinToString("") { it + "\n" },
                Charsets.UTF_8,
            )
            written += batch.size
        }
    }

    /** 启动时跑一次：删过期天、再把总大小压到 [MAX_BYTES] 以内 */
    fun rotate(today: String = todayString()): List<String> {
        val deleted = mutableListOf<String>()
        runCatching {
            root.mkdirs()
            val files = logFiles().sortedBy { it.name }   // 文件名即日期，字典序 = 时间序
            val cutoff = dayFileName(shiftDay(today, -MAX_DAYS + 1))
            // ① 超过保留天数的
            files.filter { it.name < cutoff }.forEach { f ->
                if (f.delete()) deleted += f.name
            }
            // ② 总大小超限：从最旧的删到合格
            var total = logFiles().sumOf { it.length() }
            val remain = logFiles().sortedBy { it.name }.toMutableList()
            while (total > MAX_BYTES && remain.size > 1) {
                val oldest = remain.removeAt(0)
                val len = oldest.length()
                if (oldest.delete()) {
                    deleted += oldest.name
                    total -= len
                }
            }
        }
        return deleted
    }

    /** 目录下所有日志文件（按日期新→旧） */
    fun logFiles(): List<File> = runCatching {
        root.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }.getOrDefault(emptyList())

    /** 已占用字节数（调试页/设置页显示「已占用 412KB」） */
    fun usageBytes(): Long = runCatching { logFiles().sumOf { it.length() } }.getOrDefault(0L)

    /** 读某一天的日志；传 null 读全部（新→旧） */
    fun read(day: String? = null): String = runCatching {
        val targets = if (day == null) logFiles() else logFiles().filter { it.name == dayFileName(day) }
        targets.joinToString("") { f -> runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("") }
    }.getOrDefault("")

    /** 清空历史日志（**不动内存缓冲**） */
    fun clear(): Boolean = runCatching {
        logFiles().forEach { it.delete() }
        true
    }.getOrDefault(false)

    companion object {

        /** 保留天数 */
        const val MAX_DAYS = 7

        /** 总量上限 */
        const val MAX_BYTES = 2 * 1024 * 1024

        /** 攒够这么多行就 flush */
        const val FLUSH_LINES = 20

        /** 或者距上次 flush 超过这么久（崩溃前最后几行尽量落盘） */
        const val FLUSH_INTERVAL_MS = 500L

        fun dayFileName(day: String): String = "$day.log"

        /**
         * 「全部导出」的文件名，带**日期范围**：`在途日志_2026-11-08_2026-11-14.txt`。
         *
         * @param days 已有的日志日期（顺序无所谓）；空列表时退化成 [today] 单日，不编范围
         */
        fun exportFileName(days: List<String>, today: String): String {
            val clean = days.filter { it.isNotBlank() }.sorted()
            val oldest = clean.firstOrNull() ?: today
            val newest = clean.lastOrNull() ?: today
            // 只有一天时**别把同一天写两遍**（真机 SAF 弹窗里 `_2026-11-14_2026-11-14` 就是这个问题）
            return if (oldest == newest) "在途日志_$newest.txt" else "在途日志_${oldest}_${newest}.txt"
        }

        fun todayString(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())

        fun shiftDay(day: String, deltaDays: Int): String = runCatching {
            java.time.LocalDate.parse(day).plusDays(deltaDays.toLong()).toString()
        }.getOrDefault(day)

        // ── 脱敏（落盘前必过）──────────────────────────────

        /**
         * 需要抹掉的模式。**顺序有意义**：先抹 `key=value` 型，再抹 Header 型。
         *
         * 每条都保留键名、只把值换成 `***` —— 这样日志仍然可读（知道有这个 cookie），
         * 但拿不到真凭证。
         */
        private val PATTERNS: List<Regex> = listOf(
            // Cookie / Session
            Regex("""(?i)\b(JSESSIONID|CASTGC|SESSION|PHPSESSID|student\.urpSoft\.cn)\s*=\s*[^;\s&"']+"""),
            // CAS 票据
            Regex("""(?i)\b(lt|ticket|execution|service_id)\s*=\s*[^;&\s"']+"""),
            // 密码类字段
            Regex("""(?i)\b(password|passwd|pwd|pl)\s*=\s*[^;&\s"']+"""),
            // 通用 token
            Regex("""(?i)\b(access_token|refresh_token|token|secret|api_key)\s*=\s*[^;&\s"']+"""),
            // 请求头形式：Authorization 整段抹掉（值里常带 bearer token，没有可读的结构）
            Regex("""(?i)\bAuthorization\s*:\s*[^\r\n]+"""),
            // 响应体 JSON 里的**已知敏感键**：抹值留键（`"studentId":"***"`）。
            // 真机实证：成绩接口响应体里 `"studentId":"2024156048"` 直接进了日志，
            // 而这份日志的主要用途就是「导出成文档发我」—— 等于每次反馈都把学号发出去。
            Regex(
                """(?i)"(studentId|studentid|student_id|studentNo|studentno|xh|sid|userName|username|realName|realname|idCard|idcard|sfzh)"\s*:\s*"[^"]*"""",
            ),
            // 学号形态兜底：10 位、以 20 开头的纯数字（学号口径固定）。
            // 特意卡 `(?<!\d)…(?!\d)` 与 9/8 位长度边界 —— 不能误伤 `courseNumber`（9 位）与 `20250101`（8 位日期）。
            Regex("""(?<!\d)20\d{8}(?!\d)"""),
        )

        /**
         * URL 路径段里的随机 token（真机实证 B：`Academic.kt` 每次加载随机生成，出现在路径里）。
         *
         * 两条规则，先点名后通用：
         * 1. 已知的「会挂随机段」的接口 → 该段无条件抹（不管内容长什么样）；
         * 2. 通用：6–24 位、**同时含字母和数字**的路径段 → 抹。
         *    这条特意要求「混合」，所以 `allPassingScores` / `callback`（纯字母）、
         *    `901040351`（纯数字）都不会被误伤。
         */
        private val NAMED_RANDOM_SEGMENT = Regex(
            """(?i)/(scoreQuery|courseQuery|examQuery|gradeQuery|calendarQuery)/[^/\s"']+/""",
        )
        private val MIXED_ALNUM_SEGMENT = Regex(
            """/(?=[A-Za-z0-9]{6,24}/)(?=[A-Za-z0-9]*[0-9])(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]+/""",
        )

        /** `Set-Cookie:` / `Cookie:` 头：**保留 cookie 名**，只抹每个 `name=value` 的值 */
        private val COOKIE_HEADER = Regex("""(?i)\b(Set-Cookie|Cookie)\s*:\s*([^\r\n]+)""")

        /** cookie 头里的单个 `name=value` 对 */
        private val COOKIE_PAIR = Regex("""([A-Za-z0-9_.\-]+)\s*=\s*([^;,\s]+)""")

        /**
         * 对一行文本脱敏。**纯函数**，单测直接打。
         */
        fun redact(line: String): String {
            var out = line
            for (p in PATTERNS) {
                out = p.replace(out) { m ->
                    val full = m.value
                    when {
                        // JSON 形态：`"studentId":"2024156048"` → `"studentId":"***"`（键留、值抹）
                        full.startsWith("\"") -> {
                            val sep = full.indexOf(':')
                            if (sep <= 0) "\"***\"" else full.substring(0, sep + 1) + "\"***\""
                        }
                        // 纯学号兜底
                        full.all { it.isDigit() } -> "***"

                        else -> {
                            val sep = full.indexOfFirst { it == '=' || it == ':' }
                            if (sep <= 0) {
                                "***"
                            } else {
                                // 保留键名与分隔符（等号两侧空格按原样）
                                val key = full.substring(0, sep).trimEnd()
                                val mark = full[sep]
                                if (mark == '=') "$key=***" else "$key: ***"
                            }
                        }
                    }
                }
            }
            // 路径段 token（先点名后通用）
            out = NAMED_RANDOM_SEGMENT.replace(out) { m -> "/" + m.groupValues[1] + "/***/" }
            out = MIXED_ALNUM_SEGMENT.replace(out) { "/***/" }
            // cookie 头：抹值、留名 —— 「知道有这么个 cookie」对排查有用，
            // 但未知的新 cookie 名也必须被挡住（所以是逐对抹，而不是白名单）
            out = COOKIE_HEADER.replace(out) { m ->
                val header = m.groupValues[1]
                val rest = COOKIE_PAIR.replace(m.groupValues[2]) { pair ->
                    pair.groupValues[1] + "=***"
                }
                "$header: $rest"
            }
            return out
        }
    }
}
