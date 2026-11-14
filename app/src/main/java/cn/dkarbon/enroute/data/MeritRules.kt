package cn.dkarbon.enroute.data

import java.time.LocalDate

/**
 * 功德（木鱼）的计数规则 —— **纯函数**，日期由调用方注入（票 18 追加项）。
 *
 * ## 为什么不再是一个整数
 *
 * 以前 `Prefs.merit` 是全设备一个整数：换账号还是同一份、没有「今天敲了多少」、也不跨天。
 * 现在按**学号**分开记（未登录固定 `anon`），并且分「累计 / 今日」两栏：
 * - 主数字仍是**累计**（功德是累计的笑点，不改）
 * - 小字补**今日**，跨天自动归零
 *
 * 不上网、不上传 —— 纯玩具，别混进正经数据里。
 */
object MeritRules {

    /** 未登录时的固定账户键（真学号不会等于它） */
    const val ANON = "anon"

    /** 账户键：登录了用学号，没登录用 [ANON] */
    fun accountKey(studentNo: String): String =
        studentNo.trim().ifBlank { ANON }

    fun today(now: LocalDate = LocalDate.now()): String = now.toString()

    /**
     * 跨天滚动：`merit_day_<key> != 今天` → 今日归零。
     *
     * @param storedDay 存档里的日期串（`yyyy-MM-dd`，空 = 没存过）
     * @param today 今天的日期串
     * @param storedToday 存档里的今日计数
     * @return `first` = 应该写的日期，`second` = 应该写的今日计数
     */
    fun rollDay(storedDay: String, today: String, storedToday: Int): Pair<String, Int> =
        if (storedDay == today) today to storedToday.coerceAtLeast(0) else today to 0

    /** 敲一下之后的 (total, today) */
    fun afterStrike(total: Int, todayCount: Int): Pair<Int, Int> =
        (total.coerceAtLeast(0) + 1) to (todayCount.coerceAtLeast(0) + 1)

    /**
     * 首次登录时把 `anon` 的累计并入学号 —— 否则「登录前敲了一下午，登录后归零」。
     *
     * 只并一次：调用方并完把 anon 置 0（见 [Prefs.clearAnonMerit]），所以重复调用是幂等的。
     *
     * @return 并入后的该账户累计；`anonTotal <= 0` 时原样返回 [targetTotal]
     */
    fun mergeAnon(targetTotal: Int, anonTotal: Int): Int =
        targetTotal.coerceAtLeast(0) + anonTotal.coerceAtLeast(0)

    /**
     * 老 key `merit`（全设备一个整数）迁到当前账户上的算法。
     *
     * 与 [mergeAnon] 同款加法，但**语义不同**（迁移 vs 并账），所以单列一个函数，
     * 让「老用户打开后发现功德归零」这条回归有自己的一条测试。
     */
    fun migrateLegacy(targetTotal: Int, legacyTotal: Int): Int =
        targetTotal.coerceAtLeast(0) + legacyTotal.coerceAtLeast(0)

    /** 是否值得显示「本机」小标（没绑账号） */
    fun isAnonymous(key: String): Boolean = key == ANON
}
