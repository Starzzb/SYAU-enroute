package cn.dkarbon.enroute.data

import kotlinx.serialization.Serializable

/**
 * 历年课表的半纯逻辑（票 11）：学期选项解析、请求体构造、缓存命名与时间标记。
 *
 * 接口实测结论（2026-11-14，见 `docs/URP-学生端接口清单.md` §6.4）：
 * - 切学期 = `POST .../ajaxStudentSchedule/callback`，表单 **`&planCode=<学期号>`**
 *   （开头那个 `&` 是首字段为空，照抄）；
 * - `GET ...?xnxq=` / `GET index?planCode=` 都**无效**，必须 POST；
 * - 返回 schema 与「本学期课表」完全同构 → 直接复用 [UrpClient.parseSchedule]，不另写解析器。
 */
object HistorySemesters {

    /** 学期下拉里的一项 */
    @Serializable
    data class Option(
        /** `2024-2025-1-1` */
        val code: String = "",
        /** 服务器给的原文标签（`2024-2025学年秋`，当前学期会带「(当前)」） */
        val label: String = "",
        /** 是不是服务器标出的「当前学期」 */
        val current: Boolean = false,
    )

    /**
     * 解析 `/student/courseSelect/calendarSemesterCurriculum/index` 里的
     * `select[name="planCode"]` 选项。
     *
     * **不写死学期**：实测服务器只按会话给最近 5 个（含当前），所以列表必须来自这里。
     * 容错：`<option>` 没写 `value=` 时用标签当值；`selected` 属性也算「当前」。
     */
    fun options(html: String): List<Option> {
        if (html.isBlank()) return emptyList()
        val select = Regex(
            """(?is)<select[^>]*name\s*=\s*["']planCode["'][^>]*>(.*?)</select>""",
        ).find(html)?.groupValues?.get(1) ?: return emptyList()
        val optRe = Regex("""(?is)<option([^>]*)>(.*?)</option>""")
        return optRe.findAll(select).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val text = strip(m.groupValues[2])
            val value = Regex("""(?i)value\s*=\s*["']([^"']*)["']""")
                .find(attrs)?.groupValues?.get(1)?.trim()
                .orEmpty()
                .ifBlank { text }
            if (value.isBlank()) return@mapNotNull null
            Option(
                code = value,
                label = text,
                current = text.contains("当前") || attrs.contains("selected", ignoreCase = true),
            )
        }.distinctBy { it.code }.toList()
    }

    /**
     * 切换学期的请求体：**开头就是一个 `&`**（首字段为空），照实测原样。
     * 少写这个 `&` 实测拿不到数据，所以用测试钉住。
     */
    fun scheduleBody(planCode: String): String =
        "&planCode=" + java.net.URLEncoder.encode(planCode.trim(), "UTF-8")

    /** 缓存文件名：`2024-2025-1-1.json`（学期号只含数字与连字符，可直接当文件名） */
    fun cacheFileName(planCode: String): String {
        val safe = planCode.trim().replace(Regex("""[^0-9A-Za-z_\-]"""), "_")
        return "$safe.json"
    }

    /**
     * 缓存命中判定：**历史学期永久有效**（不会再变），有文件就算命中。
     * 只有用户手动「刷新」时才绕过缓存。
     */
    fun isCacheHit(planCode: String, cached: String?): Boolean =
        planCode.isNotBlank() && !cached.isNullOrBlank()

    /**
     * 缓存时间标记：`本地缓存 · 抓于 11-14`（取 ISO 时间的 MM-DD）。
     * 时间解析不出就只写「本地缓存」，**不编日期**。
     */
    fun stamp(fetchedAt: String): String {
        val t = fetchedAt.trim()
        val mmdd = Regex("""^\d{4}-(\d{2})-(\d{2})""").find(t)
            ?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
        return if (mmdd != null) "本地缓存 · 抓于 $mmdd" else "本地缓存 · 抓取时间未知"
    }

    private fun strip(s: String): String =
        s.replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
