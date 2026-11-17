package cn.dkarbon.enroute.data.faculty

import kotlinx.serialization.Serializable

/** 官网一条教师公开信息（缓存单元，键 = 工号） */
@Serializable
data class FacultyInfo(
    /** 缓存键：工号（教务侧稳定唯一），没有时退化成 `姓名@域名` 的短哈希 */
    val key: String = "",
    val name: String = "",
    /** 教务侧院系（`土环`）——用来决定去哪个站找，也给界面显示 */
    val dept: String = "",
    /** 官网站点显示名（`土地与环境学院`） */
    val collegeName: String = "",
    /** 原文地址 */
    val sourceUrl: String = "",
    /** 站点域名（`thxy.syau.edu.cn`），展示「信息来自 XX 学院官网」用 */
    val sourceHost: String = "",
    /** 抓取时间，ISO-8601 带偏移：`2026-11-14T10:30:00+08:00` */
    val fetchedAt: String = "",
    /** 命中的正文容器（排障用：`vsb_content` / `v_news_content` / `WspDetail` / `whole_page`） */
    val template: String = "",
    val fields: FacultyFields = FacultyFields(),
    /** 长文本小节（教育经历/工作经历/论文…），前端折叠显示 */
    val sections: List<FacultySection> = emptyList(),
    /** 正文纯文本兜底（≤8KB，与官网原文一致，不做删改） */
    val rawText: String = "",
    /** 正文里的图片绝对地址（官网原样，缓存时下到本地） */
    val images: List<String> = emptyList(),
    /** 缓存结构版本：字段语义变了就加一，旧条目自动作废重抓 */
    val schema: Int = SCHEMA,
) {
    companion object {
        /** 2 = 不再做隐私删改、加入图片（2026-11-17） */
        const val SCHEMA = 2
    }
}

@Serializable
data class FacultySection(val title: String = "", val text: String = "")

/**
 * 从官网正文里抽出来的结构化字段。
 *
 * 只收**教职工公务信息**（职称/研究方向/办公地点/办公电话/校方邮箱…）。
 * 个人手机号、QQ、私人邮箱在抽取阶段就被 [FacultyPrivacy] 丢掉，不进缓存（票 05 §6）。
 */
@Serializable
data class FacultyFields(
    val title: String = "",
    val degree: String = "",
    val supervisor: String = "",
    val research: String = "",
    val role: String = "",
    val office: String = "",
    val phone: String = "",
    val email: String = "",
    val sex: String = "",
    val birth: String = "",
) {
    /** 有没有「真正有用」的内容：职称/研究方向/办公/电话/邮箱 全空就算没抽到 */
    val isEmpty: Boolean
        get() = listOf(title, research, office, phone, email).all { it.isBlank() }

    /** 界面上一行「标签 → 值」，空值不参与 */
    fun rows(): List<Pair<String, String>> = listOf(
        "职称" to title,
        "学历学位" to degree,
        "导师资格" to supervisor,
        "行政职务" to role,
        "研究方向" to research,
        "办公地点" to office,
        "电话" to phone,
        "邮箱" to email,
        "性别" to sex,
        "出生年月" to birth,
    ).filter { it.second.isNotBlank() }
}

/** 抓取失败的分类（界面三种说法：没排课 / 官网没解析到 / 网络或登录态） */
enum class FacultyFail {
    /** 没问题 */
    None,

    /** 教务给的院系在官网没有师资栏目（`图书`、`全院`…） */
    NoSource,

    /** 去了官网，但栏目页里没有这个名字 */
    NotFound,

    /** 找到链接但正文抽不出东西（页面只有图/只有姓名） */
    NotParsed,

    /** 同名多人：不猜，把候选甩给用户 */
    Ambiguous,

    /** 网络不通 / 超时 / 非 200 */
    Network,
}

/** 抓取结局 */
data class FacultyResult(
    val info: FacultyInfo? = null,
    val fail: FacultyFail = FacultyFail.None,
    /** 这次是直接读缓存（没过期） */
    val fromCache: Boolean = false,
    /** 刷新失败，展示的是旧缓存（界面要标「更新失败，显示 X 的缓存」） */
    val stale: Boolean = false,
    /** [FacultyFail.Ambiguous] 时的候选原文地址 */
    val candidates: List<String> = emptyList(),
    /** 原始异常摘要（只进调试日志，不上屏） */
    val error: String = "",
)

