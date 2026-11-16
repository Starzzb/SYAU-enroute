package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.AcademicCache
import cn.dkarbon.enroute.data.CachePolicy
import cn.dkarbon.enroute.data.CacheTier
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.HistorySemesters
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.data.UrpHomeApi.CampusOption
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.ProfileField
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 缓存**三档分层**的判据与「失败不覆盖」纪律（票 24）。
 *
 * 为什么这块必须单测：判据错了不会崩，只会**给错数据** ——
 * 「考完了还显示那场考试」「断网把成绩抹了」「每次冷启动又重抓一遍」全都长这样，
 * 上真机看一眼是看不出来的。所以把判据做成纯函数，在这里把边界钉死。
 */
class AcademicCacheTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 一个基准时刻（2026-11-14 08:00 +08:00） */
    private val t0 = LocalDateTime.of(2026, 11, 14, 8, 0).atZone(zone).toInstant().toEpochMilli()

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    private fun exam(date: String, clock: String = "10:00") = ExamItem(
        name = "高等数学",
        date = date,
        startClock = clock,
        startAt = "$date $clock",
    )

    private fun profile() = StudentProfile(
        fields = listOf(
            ProfileField("学号", "2024156048"),
            ProfileField("姓名", "张三"),
            ProfileField("院系", "土地与环境学院"),
            ProfileField("培养层次", "本科"),
        ),
    )

    // ── ① 永久档：身份读一次就够 ────────────────────────────

    @Test
    fun `永久档 缓存空才抓 有身份就再也不自动抓`() {
        assertTrue("没缓存过 → 要抓", CachePolicy.shouldFetchPermanent(cachedIsEmpty = true))
        assertFalse("已有身份 → 不抓（学号姓名院系不会变）", CachePolicy.shouldFetchPermanent(cachedIsEmpty = false))
    }

    @Test
    fun `永久档 身份不同字段的时间戳不受每日档影响`() {
        // 身份 3 天前抓的、绩点 2 小时前抓的，两块各自成立
        val c = AcademicCache(profile = profile(), profileAt = t0 - 3 * day)
            .merge(t0, scoreStats = ScoreStats(total = 40, passed = 39))
        assertFalse(CachePolicy.shouldFetchPermanent(c.profile.isEmpty))
        assertTrue(CachePolicy.isFresh(t0, c.scoreStatsAt))
        assertEquals(t0 - 3 * day, c.profileAt)   // 抓绩点没有刷新身份的时间戳
    }

    // ── ② 每日档：24h 边界 ────────────────────────────────

    @Test
    fun `每日档 23小时59分还算新鲜 满24小时就该抓`() {
        assertTrue(CachePolicy.isFresh(t0, t0 - day + 60_000))
        assertTrue(CachePolicy.isFresh(t0, t0 - hour))
        assertFalse("整 24 小时 → 不新鲜（边界归该抓）", CachePolicy.isFresh(t0, t0 - day))
        assertFalse(CachePolicy.isFresh(t0, t0 - day - 1))
        assertFalse(CachePolicy.isFresh(t0, t0 - 3 * day))
    }

    @Test
    fun `每日档 从没抓过要抓 时钟回拨也要抓`() {
        assertTrue(CachePolicy.shouldFetchDaily(t0, 0L))
        assertFalse(CachePolicy.isFresh(t0, 0L))
        // 用户把系统时间调回去：不能因此让自动刷新永久停摆
        assertTrue(CachePolicy.shouldFetchDaily(t0, t0 + hour))
        assertTrue(CachePolicy.shouldFetchExams(t0, t0 + hour, 0L, day))
    }

    @Test
    fun `每日档 刚抓过就不抓`() {
        assertFalse(CachePolicy.shouldFetchDaily(t0, t0 - 60_000))
        assertFalse(CachePolicy.shouldFetchDaily(t0, t0))
    }

    @Test
    fun `每日档显示用获取时刻取较新的那次 没抓过为0`() {
        assertEquals(0L, CachePolicy.dailyBlockAt(0L, 0L))
        assertEquals(t0, CachePolicy.dailyBlockAt(t0, 0L))
        assertEquals(t0 - hour, CachePolicy.dailyBlockAt(0L, t0 - hour))
        assertEquals(t0, CachePolicy.dailyBlockAt(t0 - hour, t0))
    }

    // ── ③ 考试：双条件（票 24 的关键设计）──────────────────

    @Test
    fun `考试 新鲜且下一场还没到 不重抓`() {
        val nextExam = t0 + 2 * day
        assertFalse(CachePolicy.shouldFetchExams(t0, t0 - hour, nextExam))
    }

    @Test
    fun `考试 新鲜但缓存里那场下一场已经开考 必须重抓`() {
        // 缓存是 1 小时前抓的（时间上「新鲜」），但里面那场考试 10 分钟前已经开考了 ——
        // 只按时间就会把一份含已考完科目的快照继续显示一整天，那是错数据。
        val nextExam = t0 - 10 * 60_000
        assertTrue(CachePolicy.shouldFetchExams(t0, t0 - hour, nextExam))
        // 正好开考那一刻也算「已过」
        assertTrue(CachePolicy.shouldFetchExams(t0, t0 - hour, t0))
    }

    @Test
    fun `考试 当时就没有下一场 不因为考试条件反复重抓`() {
        // 考完了/还没排：nextExamAt = 0。若把它判成「该抓」，就会变成每次冷启动都打网络。
        assertFalse(CachePolicy.shouldFetchExams(t0, t0 - hour, 0L))
    }

    @Test
    fun `考试 超过24小时要抓 从没抓过要抓 时钟回拨要抓`() {
        val future = t0 + 5 * day
        assertTrue(CachePolicy.shouldFetchExams(t0, t0 - day, future))
        assertTrue(CachePolicy.shouldFetchExams(t0, 0L, future))
        assertTrue(CachePolicy.shouldFetchExams(t0, t0 + hour, future))
    }

    @Test
    fun `考试 开考时刻解析`() {
        val expect = LocalDateTime.of(2026, 11, 15, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expect, CachePolicy.examStartMs("2026-11-15", "10:00", zone))
        // 没有时刻 → 当天 0 点（不猜一个「大概十点」）
        assertEquals(
            LocalDateTime.of(2026, 11, 15, 0, 0).atZone(zone).toInstant().toEpochMilli(),
            CachePolicy.examStartMs("2026-11-15", "", zone),
        )
        // 日期都没有 → null（判据退回只看 24h）
        assertNull(CachePolicy.examStartMs("", "10:00", zone))
        assertNull(CachePolicy.examStartMs("待定", "10:00", zone))
    }

    @Test
    fun `考试 下一场取最早的那场未来考试`() {
        val list = listOf(
            exam("2026-11-14", "08:00"),   // 已经开考（早于 t0）
            exam("2026-12-01", "14:00"),
            exam("2026-11-20", "09:00"),   // ← 最近的未来一场
        )
        assertEquals(
            LocalDateTime.of(2026, 11, 20, 9, 0).atZone(zone).toInstant().toEpochMilli(),
            CachePolicy.nextExamAtMs(list, t0, zone),
        )
    }

    @Test
    fun `考试 全都考完或日期解析不出 记为0 而不是瞎猜`() {
        assertEquals(0L, CachePolicy.nextExamAtMs(emptyList(), t0, zone))
        assertEquals(0L, CachePolicy.nextExamAtMs(listOf(exam("2026-11-13")), t0, zone))
        assertEquals(0L, CachePolicy.nextExamAtMs(listOf(exam("待定")), t0, zone))
    }

    // ── ③b 总判据：三档全新鲜 → 一个请求都不发 ──────────────

    /** 造一份「刚抓完、什么都新鲜」的缓存（模拟上次联网成功后的状态） */
    private fun freshCache(): AcademicCache = AcademicCache()
        .merge(
            t0 - hour,
            profile = profile(),
            credit = CreditProgress(planName = "2024 方案", requiredXf = 165.0, earnedXf = 106.0),
            scoreStats = ScoreStats(total = 40, passed = 39, weightedGpa = 3.21),
            scores = listOf(ScoreItem(name = "生态学基础", score = "73")),
            exams = listOf(exam("2026-12-01", "09:00")),
        )

    @Test
    fun `全新鲜时一个请求都不发`() {
        val c = freshCache()
        assertFalse(
            "这是「冷启动不发身份/成绩/考试请求」的机器可验证据",
            CachePolicy.anyBlockStale(
                nowMs = t0,
                profileIsEmpty = c.profile.isEmpty,
                creditAt = c.creditAt,
                scoreStatsAt = c.scoreStatsAt,
                examsAt = c.examsAt,
                nextExamAtMs = c.nextExamAtMs,
                scoresAt = c.scoresAt,
            ),
        )
    }

    @Test
    fun `任一块过期就发那块的请求`() {
        val c = freshCache()
        fun stale(
            profileIsEmpty: Boolean = c.profile.isEmpty,
            creditAt: Long = c.creditAt,
            scoreStatsAt: Long = c.scoreStatsAt,
            examsAt: Long = c.examsAt,
            nextExamAtMs: Long = c.nextExamAtMs,
            scoresAt: Long = c.scoresAt,
        ) = CachePolicy.anyBlockStale(t0, profileIsEmpty, creditAt, scoreStatsAt, examsAt, nextExamAtMs, scoresAt)

        assertTrue("身份没缓存过", stale(profileIsEmpty = true))
        assertTrue("学分进度超过一天", stale(creditAt = t0 - day))
        assertTrue("绩点超过一天", stale(scoreStatsAt = t0 - day))
        assertTrue("考试超过一天", stale(examsAt = t0 - day))
        assertTrue("缓存里那场考试已经开考", stale(nextExamAtMs = t0 - 60_000))
        assertTrue("成绩明细超过一天", stale(scoresAt = t0 - day))
    }

    @Test
    fun `只有永久档缺失时 也判定为「有东西要抓」而不是全新鲜`() {
        val c = freshCache().copy(profile = StudentProfile(), profileAt = 0L)
        assertTrue(
            CachePolicy.anyBlockStale(
                nowMs = t0,
                profileIsEmpty = c.profile.isEmpty,
                creditAt = c.creditAt,
                scoreStatsAt = c.scoreStatsAt,
                examsAt = c.examsAt,
                nextExamAtMs = c.nextExamAtMs,
                scoresAt = c.scoresAt,
            ),
        )
    }

    // ── ④ 「上次更新」人话 ────────────────────────────────
    @Test
    fun `更新时间说法 没有时间就不显示假文字`() {
        assertEquals("", CachePolicy.ageLabel(t0, 0L))
        assertEquals("刚刚", CachePolicy.ageLabel(t0, t0 - 30_000))
        assertEquals("刚刚", CachePolicy.ageLabel(t0, t0 + hour))   // 时钟回拨不显示负数
        assertEquals("5 分钟前", CachePolicy.ageLabel(t0, t0 - 5 * 60_000))
        assertEquals("3 小时前", CachePolicy.ageLabel(t0, t0 - 3 * hour))
        assertEquals("2 天前", CachePolicy.ageLabel(t0, t0 - 2 * day))
    }

    @Test
    fun `页顶数据取自 没抓过不显示 刚刚更新 否则绝对时刻`() {
        assertEquals("", CachePolicy.fetchStamp(t0, 0L))
        assertEquals("刚刚更新", CachePolicy.fetchStamp(t0, t0 - 30_000))
        assertEquals("刚刚更新", CachePolicy.fetchStamp(t0, t0 + hour))   // 时钟回拨
        // t0 = 2026-11-14 08:00 Asia/Shanghai → 满 1 分钟就显示绝对时刻，不写「几小时前」这种相对说法
        assertEquals("数据取自 11-14 07:00", CachePolicy.fetchStamp(t0, t0 - hour, zone))
        assertEquals("数据取自 11-13 08:00", CachePolicy.fetchStamp(t0, t0 - day, zone))
    }

    // ── ⑤ 失败不覆盖（数据诚实底线）─────────────────────

    @Test
    fun `抓失败时一个字段都不许覆盖`() {
        val good = AcademicCache(
            profile = profile(),
            profileAt = t0 - 3 * day,
            credit = CreditProgress(planName = "2024 土地资源管理", requiredXf = 165.0, earnedXf = 106.0),
            creditAt = t0 - 3 * day,
            scoreStats = ScoreStats(total = 40, passed = 39),
            scoreStatsAt = t0 - 3 * day,
            scores = listOf(ScoreItem(name = "生态学基础", score = "73")),
            scoresAt = t0 - 3 * day,
            exams = listOf(exam("2026-12-01")),
            examsAt = t0 - 3 * day,
            nextExamAtMs = t0 + 5 * day,
        )
        // 断网冷启动：每块都抓失败 → 传 null → 与原来**逐字段相同**
        val after = good.merge(t0)
        assertEquals(good, after)
        assertEquals(good.profileAt, after.profileAt)
        assertEquals(good.examsAt, after.examsAt)
        assertEquals(1, after.exams.size)
        assertEquals(1, after.scores.size)
    }

    @Test
    fun `抓成功才更新值和时间戳`() {
        val old = AcademicCache(profile = profile(), profileAt = t0 - 3 * day)
            .merge(t0 - 2 * day, exams = listOf(exam("2026-12-01")))
        val newProfile = profile().let { StudentProfile(fields = it.fields + ProfileField("专业", "土地资源管理")) }
        val after = old.merge(t0, profile = newProfile)
        assertEquals(t0, after.profileAt)                    // 身份时间戳前进
        assertEquals(t0 - 2 * day, after.examsAt)            // 没抓的块时间戳不动
        assertEquals("土地资源管理", after.profile.major)
    }

    @Test
    fun `服务端说没有考试 是有效结果 要覆盖成空并记下下一场为0`() {
        val old = AcademicCache()
            .merge(t0 - 5 * day, exams = listOf(exam("2026-11-13")))
        assertTrue(old.nextExamAtMs > 0L)
        // 考完了，服务端答「没有考试」→ 照实写空，别一直抱着旧名单
        val after = old.merge(t0, exams = emptyList())
        assertTrue(after.exams.isEmpty())
        assertEquals(t0, after.examsAt)
        assertEquals(0L, after.nextExamAtMs)
        // 而且之后不会因为「下一场已过」再反复重抓
        assertFalse(CachePolicy.shouldFetchExams(t0, after.examsAt, after.nextExamAtMs))
    }

    @Test
    fun `只更新一块时 其他块原样`() {
        val base = AcademicCache(profile = profile(), profileAt = t0 - day)
            .merge(t0 - day, campuses = listOf(CampusOption("001", "主校")))
        val after = base.merge(t0, scores = listOf(ScoreItem(name = "高数", score = "88")))
        assertEquals(base.profile, after.profile)
        assertEquals(base.campuses, after.campuses)
        assertEquals(base.roomsListsAt, after.roomsListsAt)
        assertEquals(t0, after.scoresAt)
        assertEquals(1, after.scores.size)
    }

    @Test
    fun `历史学期候选和教学楼校区号 抓成功才写 失败不覆盖`() {
        val terms = listOf(HistorySemesters.Option("2026-2027-1-1", "2026-2027学年秋", true))
        val base = AcademicCache().merge(t0, historyTerms = terms, buildingsCampusNo = "001")
        assertEquals(terms, base.historyTerms)
        assertEquals(t0, base.historyTermsAt)
        assertEquals("001", base.buildingsCampusNo)
        // 什么都没抓到 → 逐字段相同（历史学期永久档：有候选就不再自动重抓）
        assertEquals(base, base.merge(t0 + hour))
        assertEquals(t0, base.merge(t0 + hour).historyTermsAt)
    }

    @Test
    fun `空缓存识别`() {
        assertTrue(AcademicCache().isEmpty)
        assertFalse(AcademicCache(profile = profile()).isEmpty)
        // 「抓到了，但服务端就是没有考试」不算有缓存内容（不该让界面以为有数据）
        assertTrue(AcademicCache().merge(t0, exams = emptyList()).isEmpty)
    }

    // ── ⑥ 落盘格式：改 DTO 不许让老用户崩 ──────────────────

    @Test
    fun `序列化往返`() {
        val c = AcademicCache(profile = profile(), profileAt = t0)
            .merge(
                t0,
                credit = CreditProgress(planName = "2024 方案", requiredXf = 165.0, earnedXf = 106.0),
                scoreStats = ScoreStats(total = 40, passed = 39, weightedGpa = 3.21),
                scores = listOf(ScoreItem(name = "生态学基础", score = "73")),
                exams = listOf(exam("2026-12-01")),
                campuses = listOf(CampusOption("001", "主校")),
            )
        val back = AcademicCache.fromJson(AcademicCache.toJson(c))
        assertNotNull(back)
        assertEquals(c.profile, back!!.profile)
        assertEquals(c.credit, back.credit)
        assertEquals(c.scoreStats, back.scoreStats)
        assertEquals(c.scores, back.scores)
        assertEquals(c.exams, back.exams)
        assertEquals(c.nextExamAtMs, back.nextExamAtMs)
        assertEquals(c.examsAt, back.examsAt)
        assertEquals(c.campuses, back.campuses)
    }

    @Test
    fun `多了未知字段照读 少了字段走默认值`() {
        // 模拟「以后给 DTO 加字段」：老缓存里没有它们 → 用默认值，不抛异常
        val json = """
            {"version":1,
             "profile":{"fields":[{"label":"学号","value":"2024156048"}],"brandNewField":"x"},
             "profileAt":1700000000000,
             "somethingFromTheFuture":{"a":1}}
        """.trimIndent()
        val c = AcademicCache.fromJson(json)
        assertNotNull(c)
        assertEquals("2024156048", c!!.profile.studentNo)
        assertEquals(1700000000000L, c.profileAt)
        assertTrue(c.exams.isEmpty())
    }

    @Test
    fun `版本不认识或内容坏了 当作没有缓存 让调用方重抓`() {
        assertNull(AcademicCache.fromJson("""{"version":99,"profileAt":1}"""))
        assertNull(AcademicCache.fromJson("这不是 json"))
        assertNull(AcademicCache.fromJson(""))
    }

    @Test
    fun `三档的名字不许改动 界面文案依赖它`() {
        assertEquals(listOf("Permanent", "Daily", "Live"), CacheTier.entries.map { it.name })
    }
}
