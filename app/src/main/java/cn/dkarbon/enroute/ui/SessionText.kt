package cn.dkarbon.enroute.ui

import cn.dkarbon.enroute.data.SessionState

/**
 * 登录态三态的全部**界面文案**（票 04）。
 *
 * 为什么把文案抽成纯函数：这是票 04 的验收点之一 ——
 * 「过期」和「未登录」必须说人话、而且必须**是两句不同的话**。
 * 写在 Composable 里只能靠上真机看，抽出来就能用 JVM 单测钉住，
 * 免得哪天顺手改一个字，又把三态改回一句「未登录」。
 *
 * 文案纪律：
 *   · 过期就说过期，不假装还登着，也不说成「未登录」（那是冤枉用户）；
 *   · 每条都带**下一步动作**（点此登录 / 点此续期 / 连校园网），不给死胡同；
 *   · 涉及密码只说事实：只在校内网用、只用一次、不保存。
 */
object SessionText {

    /** [SessionState.Expired] 的通用说明：既讲清发生了什么，也讲清密码怎么处理 */
    const val EXPIRED_NOTE: String =
        "上次登录的会话已过期（教务系统那边超时了）。点此重新登录一次即可继续，课表和成绩都还在；" +
            "密码只在校内网那一次登录时使用，不保存。"

    /** 验活没做成（多半不在校园网）时的说明：保留会话，但**不谎称已确认有效** */
    const val UNVERIFIED_NOTE: String =
        "已恢复上次的登录；这次没连上教务（多半不在校园网），所以还没法确认会话是否还有效。"

    /** 自动续期进行中（短暂态，票 14） */
    const val RELATING_NOTE: String = "正在用本机保存的凭据自动续期…"

    /** 连续 3 次自动续期失败后的说明：停手，别把风控惹毛 */
    const val RELOGIN_BLOCKED_NOTE: String =
        "自动续期连续失败 3 次，已暂停自动尝试（可能改过密码）。请手动登录一次；" +
            "登录成功后自动续期会重新可用。"

    /** 首次登录后的「保持登录」说明卡（票 14：默认关闭，需用户显式开启） */
    const val KEEP_LOGIN_TITLE: String = "保持登录（自动续期）？"

    const val KEEP_LOGIN_BODY: String =
        "开启后，本机用设备密钥加密保存学号与密码；教务会话过期时自动重登，你不用再输密码。\n\n" +
            "· 密码只保存在这台手机上，只发给学校认证页（pass.syau.edu.cn），不经任何第三方\n" +
            "· 随时可在设置里关闭，关闭即刻删除本机密文与密钥\n" +
            "· 不开也能用，只是会话过期后要手动登录一次"

    /** 三态的正名（设置页、诊断行都能用） */
    fun stateLabel(s: SessionState): String = when (s) {
        SessionState.None -> "未登录"
        SessionState.Active -> "已登录（会话有效）"
        SessionState.Expired -> "已登录但会话已过期"
    }

    /**
     * 身份卡副标题。
     *
     * @param profileSubtitle 「24土管1 · 土地资源管理 · 2024级」这类学籍摘要（可能为空）
     * @param loading 正在读学籍
     * @param checkMsg 验活说明（[AppState.sessionCheckMsg]）
     */
    fun identitySubtitle(
        state: SessionState,
        profileSubtitle: String,
        loading: Boolean,
        checkMsg: String,
    ): String = when {
        state == SessionState.Expired -> "会话已过期 · 点此续期（密码只在校内网用一次，不保存）"
        state == SessionState.None -> "点此登录教务 · 只在校内网可用"
        profileSubtitle.isNotBlank() -> profileSubtitle
        loading -> "正在读取学籍…"
        checkMsg.isNotBlank() -> checkMsg
        else -> "已登录 · 会话已加密保存，下次打开不用重登"
    }

    /** 身份卡上「名字」位置在没有真名时的占位 */
    fun fallbackName(s: SessionState): String = when (s) {
        SessionState.None -> "未登录"
        SessionState.Active -> "已登录"
        SessionState.Expired -> "会话已过期"
    }

    /** 登录/续期入口的标题（入口本身是同一个登录页，但话得说对） */
    fun actionLabel(s: SessionState): String = when (s) {
        SessionState.None -> "登录教务"
        SessionState.Active -> "重新登录"
        SessionState.Expired -> "续期登录（会话已过期）"
    }

    /** 没有可用会话时，各接口面板的提示 */
    fun noSessionHint(s: SessionState): String = when (s) {
        SessionState.None -> "还没登录。回「课表」页登录一次即可（只在校内网可用）"
        SessionState.Active -> "" // 有会话却走到这里说明是别的错误，不给误导性提示
        SessionState.Expired -> "会话已过期。去「我的」页点身份卡续期一次即可（只在校内网可用）"
    }

    /** 设置页「数据」块那一行小字 */
    fun settingsLine(s: SessionState): String = when (s) {
        SessionState.None -> "未登录（教务接口要先登录，只在校内网可用）"
        SessionState.Active -> "已登录（会话 cookie 加密存本机）"
        SessionState.Expired -> "会话已过期（cookie 已清，需重新登录一次）"
    }

    /** 身份卡是否应该可点（点 = 去登录/续期） */
    fun identityClickable(s: SessionState): Boolean = s != SessionState.Active
}
