package cn.dkarbon.enroute

import android.app.Application
import cn.dkarbon.enroute.data.UpdateCenter

/**
 * 进程级单例。
 *
 * 现在只做一件事：**启动时的静默更新检查**（票 15）。
 * 规则：每天最多一次、失败不打扰、绝不后台下载 —— 见 [UpdateCenter.maybeAutoCheckAsync]。
 * 为什么放这里而不是 MainActivity：它在 `setContent` 之前跑，不占用首帧；
 * 也避免往 TimetableViewModel 里塞与课表无关的状态。
 */
class EnRouteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { UpdateCenter(this).maybeAutoCheckAsync() }
    }
}
