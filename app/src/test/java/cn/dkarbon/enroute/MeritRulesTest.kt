package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.MeritRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 电子木鱼「功德」的计数规则（票 18 追加项）。
 *
 * 要求来自主人：**功德对应到学号**、**每天敲多少按天刷新**。
 * 这里全部用**注入的日期**（`LocalDate` 参数），不依赖真实时钟 —— 否则跨天用例只能靠等午夜。
 */
class MeritRulesTest {

    private val today = LocalDate.of(2026, 11, 14)
    private val yesterday = LocalDate.of(2026, 11, 13)

    // ── 账户键 ──────────────────────────────────────────

    @Test
    fun `账户键登录用学号未登录用anon`() {
        assertEquals("2024156048", MeritRules.accountKey("2024156048"))
        assertEquals(MeritRules.ANON, MeritRules.accountKey(""))
        assertEquals(MeritRules.ANON, MeritRules.accountKey("   "))
        // 去空白，避免同一个学号因为空格记成两份
        assertEquals("2024156048", MeritRules.accountKey(" 2024156048 "))
        assertTrue(MeritRules.isAnonymous(MeritRules.ANON))
        assertFalse(MeritRules.isAnonymous("2024156048"))
    }

    // ── 跨天刷新 ────────────────────────────────────────

    @Test
    fun `昨天存的今日数今天要归零`() {
        val (day, count) = MeritRules.rollDay(
            storedDay = yesterday.toString(),
            today = today.toString(),
            storedToday = 56,
        )
        assertEquals(today.toString(), day)
        assertEquals(0, count)
    }

    @Test
    fun `同一天不重置`() {
        val (day, count) = MeritRules.rollDay(
            storedDay = today.toString(),
            today = today.toString(),
            storedToday = 12,
        )
        assertEquals(today.toString(), day)
        assertEquals(12, count)
    }

    @Test
    fun `从没存过日期时按今天重新开始`() {
        val (day, count) = MeritRules.rollDay(storedDay = "", today = today.toString(), storedToday = 0)
        assertEquals(today.toString(), day)
        assertEquals(0, count)
    }

    @Test
    fun `敲一下今日与累计同时加一`() {
        assertEquals(101 to 1, MeritRules.afterStrike(total = 100, todayCount = 0))
        assertEquals(101 to 13, MeritRules.afterStrike(total = 100, todayCount = 12))
        // 负数存档（坏数据）也能自愈
        assertEquals(1 to 1, MeritRules.afterStrike(total = -5, todayCount = -3))
    }

    @Test
    fun `跨天再敲只把今日从零算起累计照旧`() {
        // 昨天：total 100 / today 56
        val (day, tc) = MeritRules.rollDay(yesterday.toString(), today.toString(), 56)
        assertEquals(0, tc)
        val (nt, ntc) = MeritRules.afterStrike(100, tc)
        assertEquals(101, nt)          // 累计接上，不清零
        assertEquals(1, ntc)           // 今日从 1 开始
        assertEquals(today.toString(), day)
    }

    // ── 账户隔离 ────────────────────────────────────────

    @Test
    fun `换账号各算各的不合并`() {
        // 模拟两个账户的独立存档（键隔离由 Prefs 的前缀保证，这里验规则不串）
        val a = MeritRules.accountKey("2024001")
        val b = MeritRules.accountKey("2024002")
        assertTrue(a != b)
        val aTotal = listOf(1, 2, 3).fold(0) { acc, _ -> MeritRules.afterStrike(acc, 0).first }
        val bTotal = 0   // B 一下没敲
        assertEquals(3, aTotal)
        assertEquals(0, bTotal)
    }

    // ── anon 迁移 ───────────────────────────────────────

    @Test
    fun `首次登录把anon的功德并入学号`() {
        // 登录前敲了 7 下（anon=7），学号原本 0 → 并完 7
        assertEquals(7, MeritRules.mergeAnon(targetTotal = 0, anonTotal = 7))
        // 学号本来就有 5 → 并完 12
        assertEquals(12, MeritRules.mergeAnon(targetTotal = 5, anonTotal = 7))
        // anon 已经是 0（迁过了）→ 不再加，天然幂等
        assertEquals(12, MeritRules.mergeAnon(targetTotal = 12, anonTotal = 0))
        // 坏数据不出负数
        assertEquals(5, MeritRules.mergeAnon(targetTotal = 5, anonTotal = -3))
    }

    @Test
    fun `迁移只加一次`() {
        var studentTotal = 0
        var anonTotal = 9
        // 第一次登录
        studentTotal = MeritRules.mergeAnon(studentTotal, anonTotal)
        anonTotal = 0   // 迁完清 anon（Prefs.clearAnonMerit）
        assertEquals(9, studentTotal)
        // 再次登录（anon 已是 0）
        studentTotal = MeritRules.mergeAnon(studentTotal, anonTotal)
        assertEquals(9, studentTotal)
    }

    @Test
    fun `老key的功德一次性迁到当前账户不归零`() {
        // 主人现在的数字是 205（老 key merit），迁到学号上必须还是 205
        assertEquals(205, MeritRules.migrateLegacy(targetTotal = 0, legacyTotal = 205))
        // 该账户后来已经敲了几下 → 叠加，不覆盖
        assertEquals(210, MeritRules.migrateLegacy(targetTotal = 5, legacyTotal = 205))
        // 老 key 是 0 / 负数（坏数据）→ 不动
        assertEquals(7, MeritRules.migrateLegacy(targetTotal = 7, legacyTotal = 0))
        assertEquals(7, MeritRules.migrateLegacy(targetTotal = 7, legacyTotal = -9))
        // 迁过一次后（legacy 已删，传 0）再跑不会翻倍 —— 幂等
        var total = MeritRules.migrateLegacy(0, 205)
        total = MeritRules.migrateLegacy(total, 0)
        assertEquals(205, total)
    }

    // ── 日期口径 ────────────────────────────────────────

    @Test
    fun `日期串用ISO本地日期`() {
        assertEquals("2026-11-14", MeritRules.today(today))
        // 与 SchoolClock 同一口径：本地时区的 LocalDate，不做时区换算
        assertEquals(today.toString(), MeritRules.today(today))
    }
}
