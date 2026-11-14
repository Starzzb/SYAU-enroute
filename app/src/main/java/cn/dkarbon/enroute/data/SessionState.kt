package cn.dkarbon.enroute.data

/**
 * 登录态三态（票 04）。
 *
 * 为什么必须三态：只有布尔 `loggedIn` 时，「从没登录过」和「登过但会话过期了」
 * 在界面上长得一模一样 —— 都是一片「暂缺」。学生分不清是自己没登录、
 * 还是 App 把登录态弄丢了，只能重输一遍密码碰运气。
 */
enum class SessionState {
    /** 本机没有可用会话：真的一次都没登过，或者存档解不开/被清掉了 */
    None,

    /** 有会话，且（验活通过 / 还没验）—— 可以打教务接口 */
    Active,

    /** 有会话但服务器已经不认了：需要重新登录（一键续期），数据与课表都还在 */
    Expired,
}

/**
 * 一次「验活」的结论。
 *
 * ⚠️ 必须与 [SessionState] 分开：验活**连不上**的时候我们并不知道会话死没死。
 * 把「连不上」当成「过期」会让离校的学生每次打开都被告知要重登（假警报），
 * 把「连不上」当成「有效」又是在假装。所以留一个 [Unreachable]，
 * 界面照实说「这次没连上教务，还无法确认」。
 */
enum class SessionProbe {
    /** 只读接口正常返回了数据：会话确实有效 */
    Ok,

    /** 服务器把我们踢回登录页 / 只给了空响应：会话死了 */
    Expired,

    /** 连不上教务（多半不在校园网）：**不知道**死没死 */
    Unreachable,
}

/**
 * 三态判定的**唯一**规则来源（纯函数，单测锁死）。
 *
 * 界面、ViewModel、存档层都只调这里，避免三处各写一遍 `if` 然后互相不一致。
 */
object SessionRules {

    /**
     * @param hasSession 手上是否握着一份会话（内存里的，或刚从加密存档恢复出来的）
     * @param probe 验活结论；`null` = 还没验
     */
    fun stateOf(hasSession: Boolean, probe: SessionProbe?): SessionState = when {
        // 验活明确失败 → 过期（哪怕手上还握着 cookie，服务器已经不认了）
        probe == SessionProbe.Expired -> SessionState.Expired
        hasSession -> SessionState.Active
        else -> SessionState.None
    }

    /** 现在能不能打教务接口（[SessionState.Active] 才试，过期/未登录直接给提示别打网络） */
    fun canCall(s: SessionState): Boolean = s == SessionState.Active

    /**
     * 该不该把「会话已过期」这件事继续记在本机。
     *
     * 过期后我们**不删课表**、也不假装没登录过；这条事实要留到用户下一次登录成功为止，
     * 否则冷启动又会退回「未登录」，票 04 要解决的正是这个歧义。
     */
    fun keepsExpiredMemory(s: SessionState): Boolean = s == SessionState.Expired
}
