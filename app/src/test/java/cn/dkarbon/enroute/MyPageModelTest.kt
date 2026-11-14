package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.ui.user.MyPageModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的」页数据派生（票 16 A 段）。
 *
 * 票面边界要求「改分组模型先写 JVM 单测」——这里锁的正是评审查出来的两类真缺陷：
 * **未知不能显示成 0**（A2）与 **徽标只放真实状态**（A8），外加常驻状态行（A7）。
 */
class MyPageModelTest {

    // ── A2：课表为空时不许写「0 门课」────────────────────

    @Test
    fun `课表没同步时是破折号不是零`() {
        assertEquals("—", MyPageModel.courseCountLabel(null))
        // 真的同步过、真的一节课都没有，才允许写 0
        assertEquals("0 门课", MyPageModel.courseCountLabel(0))
        assertEquals("10 门课", MyPageModel.courseCountLabel(10))
        // 三种状态必须互相区分（不许把「没同步」和「真的 0」压成一个符号）
        assertTrue(
            MyPageModel.courseCountLabel(null) != MyPageModel.courseCountLabel(0),
        )
    }

    @Test
    fun `周次未校准要说未校准`() {
        assertEquals("第 12 周", MyPageModel.weekLabel(12))
        assertEquals("未校准", MyPageModel.weekLabel(null))
        assertEquals("未校准", MyPageModel.weekLabel(0))
    }

    @Test
    fun `本机折叠摘要把没同步和没校准分开说`() {
        assertEquals("课表 10 门 · 第 12 周", MyPageModel.localSummary(10, 12))
        // 没同步 → 课表那一半写 —，周次照常
        assertEquals("课表 — · 第 12 周", MyPageModel.localSummary(null, 12))
        // 没校准 → 周次说未校准
        assertEquals("课表 10 门 · 未校准", MyPageModel.localSummary(10, null))
        assertEquals("课表 — · 未校准", MyPageModel.localSummary(null, null))
    }

    // ── 教务组磁贴：缺失一律「—/暂缺」，不许 0 ─────────────

    @Test
    fun `培养方案百分比缺失时是破折号`() {
        assertEquals("—", MyPageModel.planPercentLabel(CreditProgress()))
        assertEquals("64%", MyPageModel.planPercentLabel(CreditProgress(requiredXf = 100.0, earnedXf = 64.0)))
        // 需修 0 时不能算出 0%（那是假进度）
        assertEquals("—", MyPageModel.planPercentLabel(CreditProgress(requiredXf = 0.0, earnedXf = 10.0)))
    }

    @Test
    fun `培养方案副行说还差多少学分`() {
        val c = CreditProgress(requiredXf = 100.0, earnedXf = 64.5)
        assertTrue(MyPageModel.planRemainLabel(c).startsWith("还差 "))
        assertTrue(MyPageModel.planRemainLabel(c).endsWith("学分"))
        assertEquals("进度暂缺", MyPageModel.planRemainLabel(CreditProgress()))
    }

    @Test
    fun `绩点缺失是破折号绝不给零点零零`() {
        assertEquals("—", MyPageModel.gpaTileLabel(ScoreStats()))
        assertEquals("3.42", MyPageModel.gpaTileLabel(ScoreStats(weightedGpa = 3.42)))
        assertFalse(MyPageModel.gpaTileLabel(ScoreStats()).contains("0.00"))
        assertEquals("暂无成绩", MyPageModel.gpaSubLabel(ScoreStats()))
        assertEquals("24 门已过", MyPageModel.gpaSubLabel(ScoreStats(total = 24, passed = 24)))
        assertTrue(MyPageModel.gpaSubLabel(ScoreStats(total = 24, failed = 2)).contains("未过"))
    }

    @Test
    fun `空闲教室没查过时是破折号`() {
        assertEquals("—", MyPageModel.freeRoomsTile(0, 0))
        assertEquals("第 5 节 · 47 间", MyPageModel.freeRoomsTile(5, 47))
        // 节次越界（0 或 7）也不硬编
        assertEquals("—", MyPageModel.freeRoomsTile(7, 47))
    }

    // ── A7：状态行常驻，不再条件插入 ────────────────────

    @Test
    fun `登录态说法三态齐全且都非空`() {
        SessionState.entries.forEach { s ->
            assertTrue("${s} 的状态行不能是空串", MyPageModel.sessionRowLabel(s).isNotBlank())
        }
        assertTrue(MyPageModel.sessionRowLabel(SessionState.None).contains("登录"))
        assertTrue(MyPageModel.sessionRowLabel(SessionState.Expired).contains("过期"))
        // Active 也要有一行（常驻），否则续期那一刻下方内容整体上移 → 误点（A7）
        assertEquals(true, MyPageModel.sessionRowLabel(SessionState.Active).isNotBlank())
        assertFalse(MyPageModel.sessionRowNeedsAction(SessionState.Active))
        assertTrue(MyPageModel.sessionRowNeedsAction(SessionState.None))
        assertTrue(MyPageModel.sessionRowNeedsAction(SessionState.Expired))
    }

    @Test
    fun `组头短状态三态齐全`() {
        // 组头空间只有几个字：必须是短词，不能是整句
        assertEquals("未登录", MyPageModel.sessionPremiseLabel(SessionState.None))
        assertEquals("已登录", MyPageModel.sessionPremiseLabel(SessionState.Active))
        assertEquals("会话已过期", MyPageModel.sessionPremiseLabel(SessionState.Expired))
        SessionState.entries.forEach { assertTrue(MyPageModel.sessionPremiseLabel(it).length <= 6) }
    }

    // ── A8：徽标只放真实状态，噪声留空 ──────────────────

    @Test
    fun `徽标未知时留空不是零`() {
        assertEquals("", MyPageModel.examBadge(count = 0, loading = false))
        assertEquals("3 场", MyPageModel.examBadge(count = 3, loading = false))
        assertEquals("读取中", MyPageModel.examBadge(count = 0, loading = true))

        assertEquals("", MyPageModel.scoreBadge(count = 0, loading = false))
        assertEquals("21 条", MyPageModel.scoreBadge(count = 21, loading = false))

        assertEquals("", MyPageModel.syllabusBadge(0))
        assertEquals("166 门", MyPageModel.syllabusBadge(166))

        assertEquals("", MyPageModel.historyBadge(""))
        assertEquals("2024-2025 秋", MyPageModel.historyBadge("2024-2025 秋"))
    }

    @Test
    fun `被判定为噪声的三个徽标一律留空`() {
        // 「相对位置」是属性不是状态
        assertEquals("", MyPageModel.mapBadge())
        // 「N 位」是上次搜索的结果数，一清空就变
        assertEquals("", MyPageModel.teacherBadge())
        // 专业名是上下文不是状态 —— 已从 NavRow 移除，页面里不再出现这一列
        assertEquals("", MyPageModel.mapBadge())
    }
}
