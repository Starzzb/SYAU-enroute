package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.DebugLogFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **脱敏证据生成器**（票 20 / D2；真机复测后扩大到**三条下游路径**）。
 *
 * 真机复测暴露的洞：原来只在落盘那层脱敏，于是
 * 「屏幕上显示的行」和「复制/导出文档（读内存缓冲）」全是明文 ——
 * 真机上 CAS 票据 `ST-277817-…` 就明晃晃印在调试页。
 *
 * 现在脱敏挪到 `DebugLog.write()`（进内存之前），本类把**三条路径的产物**都写成文件留在
 * `app/build/debuglog-evidence/`（不自动删），供命令级校验：
 *
 * ```powershell
 * Select-String -Path app\build\debuglog-evidence\* `
 *   -Pattern 'JSESSIONID=[^*.\s]|CASTGC=[^*.\s]|ticket=[^*.\s]|token=[^*.\s]|password=[^*.\s]'
 * ```
 *
 * 期望 **0 命中**。
 */
class DebugLogEvidenceTest {

    private val root = File("build/debuglog-evidence")

    @After
    fun detach() {
        DebugLog.attachFile(null)
        DebugLog.clear()
    }

    @Test
    fun `三条下游路径都脱敏`() {
        root.mkdirs()
        root.listFiles()?.forEach { it.delete() }
        DebugLog.clear()

        val sink = DebugLogFile(root)
        DebugLog.attachFile(sink)

        // 故意喂真实形态的敏感行（值与线上同形）
        DebugLog.error(
            "URP",
            "← 302 0B  GET 10.2.0.47/login?errorCode=invalidSession&ticket=ST-EVIDENCE-270WFt4Yi1VkhaY3Dd53vC",
        )
        DebugLog.log("SESSION", "Set-Cookie: JSESSIONID=EVIDENCEJSESSIONID123; Path=/; HttpOnly")
        DebugLog.log("SESSION", "Set-Cookie: CASTGC=EVIDENCECASTGC456; Path=/tpass")
        DebugLog.warn("CRED", "POST body password=EVIDENCEPASSWORD789&execution=EVIDENCEEXEC1")
        DebugLog.log("API", "GET api/score?token=EVIDENCETOKENabc&access_token=EVIDENCEAT2")
        DebugLog.http(
            "FEED", "GET", "https://jwc.syau.edu.cn/index/jwtz.htm?ticket=ST-EVIDENCE-HTTP", 200, 12_600, elapsedMs = 820,
        )
        sink.flush()

        val secrets = listOf(
            "ST-EVIDENCE-270WFt4Yi1VkhaY3Dd53vC", "EVIDENCEJSESSIONID123", "EVIDENCECASTGC456",
            "EVIDENCEPASSWORD789", "EVIDENCETOKENabc", "EVIDENCEAT2", "ST-EVIDENCE-HTTP",
            "EVIDENCEEXEC1",
        )

        // ── 路径 ①：屏幕（调试页渲染的就是 DebugLog.entries）──
        val screen = DebugLog.entries.value.joinToString("\n") { DebugLog.formatLine(it) }
        File(root, "screen.txt").writeText(screen, Charsets.UTF_8)
        // ── 路径 ②：复制 / 「导出文档」（DebugLog.dump()）──
        val dump = DebugLog.dump()
        File(root, "dump.txt").writeText(dump, Charsets.UTF_8)
        // ── 路径 ③：落盘文件 ──
        val diskFile = sink.logFiles().first()
        val disk = diskFile.readText(Charsets.UTF_8)

        secrets.forEach { s ->
            assertFalse("屏幕路径还有 $s\n$screen", screen.contains(s))
            assertFalse("复制/导出文档还有 $s\n$dump", dump.contains(s))
            assertFalse("落盘文件还有 $s\n$disk", disk.contains(s))
        }
        // 脱敏不能误杀：正常内容必须完整保留
        assertTrue(screen.contains("jwc.syau.edu.cn/index/jwtz.htm"))
        assertTrue(screen.contains("820ms"))
        assertTrue(disk.contains("jwc.syau.edu.cn/index/jwtz.htm"))
        // 键名保留（还能看出「有个票据」）
        assertTrue(screen.contains("ticket=***"))
        // 落盘行数 = 内存条数（单消费者 + 同一 formatLine）
        assertEquals(DebugLog.entries.value.size, disk.trim().lines().size)
    }
}
