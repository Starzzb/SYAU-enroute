package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.DebugLogFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 调试日志 v2（票 20）：脱敏、行格式、轮转、大小上限、分级、错误保底。
 *
 * 落盘用**临时目录注入**（`DebugLogFile(root)`），不碰 Android 的 `filesDir`。
 */
class DebugLogFileTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dbglog").toFile()
        DebugLog.attachFile(null)
        DebugLog.clear()
    }

    @After
    fun tearDown() {
        DebugLog.attachFile(null)
        DebugLog.clear()
        dir.deleteRecursively()
    }

    // ── 脱敏（红线：落盘可能被导出）──────────────────────

    @Test
    fun `落盘行里的凭证一律抹掉`() {
        val cases = listOf(
            "Cookie: JSESSIONID=ABC123DEF; path=/",
            "JSESSIONID=ABC123DEF",
            "CASTGC=TGT-1234-abcdef",
            "http://10.2.0.47/login?ticket=ST-998877",
            "https://pass.syau.edu.cn/tpass/login?lt=LT-112233&execution=e1s1",
            "password=Hunter2&pwd=x&passwd=y",
            "&token=xyz&access_token=aaa",
            "Authorization: Bearer sk-abc",
        )
        cases.forEach { raw ->
            val out = DebugLogFile.redact(raw)
            listOf("ABC123DEF", "TGT-1234-abcdef", "ST-998877", "LT-112233", "e1s1", "Hunter2", "xyz", "aaa", "sk-abc")
                .forEach { secret -> assertFalse("$raw 里的 $secret 没被抹掉 → $out", out.contains(secret)) }
            assertTrue("$raw 没被处理 → $out", out.contains("***"))
        }
    }

    @Test
    fun `学号与路径段随机token也要抹掉`() {
        // 真机实证 A：成绩接口响应体里的学号
        val json = DebugLogFile.redact(
            """{"lnList":[{"cjList":[{"id":{"courseNumber":"901040351","studentId":"2024156048","startTime":"20250101"}}]}]}""",
        )
        assertFalse("学号还在：$json", json.contains("2024156048"))
        assertTrue(json.contains("\"studentId\":\"***\""))
        // 不能误伤：9 位课程号、8 位日期照旧
        assertTrue("课程号被误伤：$json", json.contains("901040351"))
        assertTrue("日期被误伤：$json", json.contains("20250101"))
        // 键值对形态也挡
        assertFalse(DebugLogFile.redact("studentId=2024156048").contains("2024156048"))
        // 裸学号（10 位、20 开头）兜底
        assertFalse(DebugLogFile.redact("学号 2024156048 已登录").contains("2024156048"))

        // 真机实证 B：URL 路径段里的随机 token（Academic.kt 每次加载随机生成）
        val url = DebugLogFile.redact(
            "→ GET /student/integratedQuery/scoreQuery/Ob67Fhu5HY/allPassingScores/callback",
        )
        assertFalse("路径 token 还在：$url", url.contains("Ob67Fhu5HY"))
        assertTrue(url.contains("scoreQuery/***/"))
        // 普通路径不许被抹
        val plain = DebugLogFile.redact("GET jwc.syau.edu.cn/index/jwtz.htm")
        assertEquals("GET jwc.syau.edu.cn/index/jwtz.htm", plain)
    }

    @Test
    fun `脱敏保留键名仍然可读`() {
        val out = DebugLogFile.redact("JSESSIONID=SECRET1; CASTGC=SECRET2")
        assertTrue(out.contains("JSESSIONID="))
        assertTrue(out.contains("CASTGC="))
        assertTrue(out.contains("***"))
        assertFalse(out.contains("SECRET1"))
        assertFalse(out.contains("SECRET2"))
        // 普通文本不动
        assertEquals("普通一行没有凭证", DebugLogFile.redact("普通一行没有凭证"))
    }

    @Test
    fun `内存与导出文档里也搜不到凭证明文`() {
        // 真机复测的洞：原来只在落盘那层脱敏 → 屏幕、复制、导出文档全是明文。
        // 现在脱敏在 DebugLog.write()（进内存之前），三条下游一起覆盖。
        DebugLog.log("URP", "GET 10.2.0.47/login?ticket=ST-MEMORY-SECRET")
        DebugLog.log("SESSION", "Cookie: JSESSIONID=MEMORYJS; CASTGC=MEMORYCASTGC")
        DebugLog.warn("CRED", "password=MEMORYPWD")
        val screen = DebugLog.entries.value.joinToString("\n") { DebugLog.formatLine(it) }
        val dump = DebugLog.dump()
        listOf("ST-MEMORY-SECRET", "MEMORYJS", "MEMORYCASTGC", "MEMORYPWD").forEach {
            assertFalse("屏幕路径还有 $it", screen.contains(it))
            assertFalse("导出文档还有 $it", dump.contains(it))
        }
        assertTrue(screen.contains("ticket=***"))
    }

    @Test
    fun `落盘文件里搜不到凭证明文`() {
        val sink = DebugLogFile(dir)
        sink.append("11:00:00.000  [URP]  ← 200 1.2KB 820ms GET 10.2.0.47/login?ticket=ST-REAL-SECRET")
        sink.append("11:00:01.000  [URP]  Cookie: JSESSIONID=REALJSESSIONID; CASTGC=REALCASTGC")
        sink.flush()
        val text = sink.read()
        listOf("ST-REAL-SECRET", "REALJSESSIONID", "REALCASTGC").forEach {
            assertFalse("落盘文件里还有 $it", text.contains(it))
        }
        // 命令级证据：文本里只有 ***
        assertTrue(text.contains("***"))
    }

    // ── 行格式（与 dump() 共用 formatLine）───────────────

    @Test
    fun `落盘行与内存导出格式一致`() {
        val e = DebugLog.Entry(at = 0L, tag = "FEED", msg = "教务通知：820ms · 1 页 · 15 条", level = DebugLog.Level.INFO)
        val line = DebugLog.formatLine(e)
        assertTrue(line.contains("[FEED]"))
        assertTrue(line.contains("教务通知：820ms"))
        // 时间戳在最前、TAG 在方括号里
        assertTrue(Regex("""^\d{2}:\d{2}:\d{2}\.\d{3}  \[FEED] .+""").matches(line))
    }

    // ── 轮转 ────────────────────────────────────────────

    @Test
    fun `只保留最近七天且删最旧`() {
        // 伪造 8 个日期文件
        (1..8).forEach { d ->
            File(dir, DebugLogFile.dayFileName("2026-11-%02d".format(d))).writeText("x")
        }
        assertEquals(8, DebugLogFile(dir).logFiles().size)
        val deleted = DebugLogFile(dir).rotate(today = "2026-11-08")
        assertEquals(1, deleted.size)
        assertEquals("2026-11-01.log", deleted.first())
        assertEquals(7, DebugLogFile(dir).logFiles().size)
        // 今天仍保留
        assertTrue(DebugLogFile(dir).logFiles().any { it.name == "2026-11-08.log" })
    }

    @Test
    fun `总量超上限时从最旧的删`() {
        // 造 3MB：三天各 1MB
        val mib = 1024 * 1024
        (5..7).forEach { d ->
            File(dir, DebugLogFile.dayFileName("2026-11-%02d".format(d))).writeBytes(ByteArray(mib))
        }
        assertTrue(DebugLogFile(dir).usageBytes() > DebugLogFile.MAX_BYTES)
        DebugLogFile(dir).rotate(today = "2026-11-07")
        assertTrue(DebugLogFile(dir).usageBytes() <= DebugLogFile.MAX_BYTES)
        // 最旧的先被删
        assertFalse(DebugLogFile(dir).logFiles().any { it.name == "2026-11-05.log" })
        assertTrue(DebugLogFile(dir).logFiles().any { it.name == "2026-11-07.log" })
    }

    @Test
    fun `清空历史不留文件`() {
        val sink = DebugLogFile(dir)
        sink.append("a"); sink.flush()
        assertTrue(sink.usageBytes() > 0)
        assertTrue(sink.clear())
        assertEquals(0L, sink.usageBytes())
    }

    // ── 全部导出（跨天闭环）─────────────────────────────

    @Test
    fun `导出文件名带日期范围`() {
        val days = listOf("2026-11-14", "2026-11-08", "2026-11-11")   // 顺序乱也能算对
        assertEquals("在途日志_2026-11-08_2026-11-14.txt", DebugLogFile.exportFileName(days, "2026-11-14"))
        // 只有一天
        assertEquals("在途日志_2026-11-14.txt", DebugLogFile.exportFileName(listOf("2026-11-14"), "2026-11-14"))
        // 没有任何落盘文件 → 退化成今天单日，不编范围
        assertEquals("在途日志_2026-11-20.txt", DebugLogFile.exportFileName(emptyList(), "2026-11-20"))
        // 空串日期被忽略
        assertEquals("在途日志_2026-11-20.txt", DebugLogFile.exportFileName(listOf("", "  "), "2026-11-20"))
    }

    @Test
    fun `二次脱敏是幂等的`() {
        // 导出前会再过一遍 redact()（第二次离开设备的机会）—— 已抹过的不能被抹坏
        val once = DebugLogFile.redact("Cookie: JSESSIONID=ABC; ticket=ST-1; token=xyz")
        val twice = DebugLogFile.redact(once)
        assertEquals(once, twice)
        assertFalse(twice.contains("ABC"))
        assertTrue(twice.contains("***"))
    }

    // ── 分级与错误保底（D3）─────────────────────────────

    @Test
    fun `状态码分级`() {
        assertEquals(DebugLog.Level.INFO, DebugLog.levelOf(200))
        assertEquals(DebugLog.Level.INFO, DebugLog.levelOf(204))
        assertEquals(DebugLog.Level.WARN, DebugLog.levelOf(404))
        assertEquals(DebugLog.Level.WARN, DebugLog.levelOf(500))
        assertEquals(DebugLog.Level.ERROR, DebugLog.levelOf(-1))
    }

    @Test
    fun `错误保底不被大量调试日志冲掉`() {
        // 3 条 ERROR + 500 条 DEBUG（主缓冲只有 400）
        repeat(3) { DebugLog.error("LOGIN", "登录失败 $it") }
        repeat(500) { DebugLog.debug("FEED", "批量抓取 $it") }
        // 主缓冲被冲掉，但错误缓冲还在
        assertEquals(3, DebugLog.errors.value.size)
        assertTrue(DebugLog.errors.value.all { it.level == DebugLog.Level.ERROR })
        assertEquals("登录失败 0", DebugLog.errors.value.first().msg)
        // 主缓冲不超过 400
        assertTrue(DebugLog.entries.value.size <= 400)
        // 主缓冲里确实看不到那三条了（证明保底有意义）
        assertFalse(DebugLog.entries.value.any { it.msg.startsWith("登录失败") })
    }

    @Test
    fun `错误缓冲只留最近五十条且顺序正确`() {
        repeat(60) { DebugLog.error("LOGIN", "e$it") }
        val errs = DebugLog.errors.value
        assertEquals(50, errs.size)
        assertEquals("e10", errs.first().msg)
        assertEquals("e59", errs.last().msg)
    }

    @Test
    fun `耗时字段会进日志行`() {
        DebugLog.attachFile(DebugLogFile(dir))
        DebugLog.http("FEED", "GET", "https://jwc.syau.edu.cn/x.htm", 200, 12_600, elapsedMs = 820)
        val line = DebugLog.entries.value.last().msg
        assertTrue(line.contains("820ms"))
        assertTrue(line.contains("12.3KB"))
        // elapsedMs 为负（未计时）时不输出
        DebugLog.http("FEED", "GET", "https://jwc.syau.edu.cn/x.htm", 200, 100)
        assertFalse(DebugLog.entries.value.last().msg.contains("ms  GET"))
    }
}
