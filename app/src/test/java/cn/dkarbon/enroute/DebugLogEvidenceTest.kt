package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.data.DebugLogFile
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **证据生成器**（票 20 / D2 脱敏的命令级证据）。
 *
 * 它不删自己的产物：跑完在 `app/build/debuglog-evidence/` 留下一个真实的日志文件，
 * 然后用
 *
 * ```powershell
 * Select-String -Path app\build\debuglog-evidence\*.log `
 *   -Pattern 'JSESSIONID=[^*.s]|CASTGC=[^*.s]|ticket=[^*.s]|token=[^*.s]'
 * ```
 *
 * 搜不到任何一行 —— 这就是"落盘文件里没有凭证明文"的机器可验证证据。
 */
class DebugLogEvidenceTest {

    @Test
    fun `生成一份含凭证的日志文件用于命令级校验`() {
        val root = File("build/debuglog-evidence")
        root.mkdirs()
        root.listFiles()?.forEach { it.delete() }

        val sink = DebugLogFile(root)
        // 故意喂真实形态的敏感行（值的形状与线上一致）
        sink.append(
            "11:20:01.100  [URP] ← 302 0B 640ms GET 10.2.0.47/login?ticket=ST-EVIDENCE-REAL-998877",
        )
        sink.append(
            "11:20:01.200  [URP] ← 200 12.3KB 820ms GET jwc.syau.edu.cn/index/jwtz.htm",
        )
        sink.append(
            "11:20:01.300  [SESSION] Set-Cookie: JSESSIONID=EVIDENCEJSESSIONID123; Path=/; HttpOnly",
        )
        sink.append(
            "11:20:01.400  [SESSION] Set-Cookie: CASTGC=EVIDENCECASTGC456; Path=/tpass",
        )
        sink.append(
            "11:20:01.500  [CRED] POST body password=EVIDENCEPASSWORD789&execution=EVIDENCEEXEC1",
        )
        sink.append(
            "11:20:01.600  [API] GET api/score?token=EVIDENCETOKENabc&access_token=EVIDENCEAT2",
        )
        sink.flush()
        DebugLog.attachFile(null)

        val file = sink.logFiles().first()
        val text = file.readText(Charsets.UTF_8)
        listOf(
            "ST-EVIDENCE-REAL-998877", "EVIDENCEJSESSIONID123", "EVIDENCECASTGC456",
            "EVIDENCEPASSWORD789", "EVIDENCETOKENabc", "EVIDENCEAT2",
        ).forEach { secret ->
            assertTrue("证据文件里不该有 $secret", !text.contains(secret))
        }
        // 非敏感的那行必须完整保留（证明脱敏没有误杀正常内容）
        assertTrue(text.contains("jwc.syau.edu.cn/index/jwtz.htm"))
        assertTrue(file.length() > 0)
    }
}
