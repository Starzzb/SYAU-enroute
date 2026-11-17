package cn.dkarbon.enroute

import cn.dkarbon.enroute.data.UpdateInstall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装包前那一步的决策。
 *
 * 为什么值得锁：`ACTION_VIEW` + `application/vnd.android.package-archive` 在没有
 * 「安装未知应用」权限时会**静默失败**（某些机型连异常都不抛，安装器根本不起来），
 * 用户看到的现象是「点了安装什么都没发生」。所以「没权限就必须先去设置页」不能靠人记，
 * 用单测钉住。
 */
class UpdateInstallTest {

    @Test
    fun `有权限就直接交给安装器`() {
        assertEquals(UpdateInstall.Step.Installer, UpdateInstall.step(canInstallPackages = true))
    }

    @Test
    fun `没权限先拉系统设置页 绝不直接开安装器`() {
        assertEquals(UpdateInstall.Step.Settings, UpdateInstall.step(canInstallPackages = false))
    }

    @Test
    fun `只有两个分支 不会两件事都做`() {
        // 枚举只有两个值：给定输入只可能落到其中一个，不存在「既开设置又开安装器」
        assertEquals(2, UpdateInstall.Step.entries.size)
    }

    @Test
    fun `安装未知应用页只认包名 不认 APK 路径`() {
        // 真机实测（2026-11-17）：传 APK 的 content URI 会 No Activity found to handle Intent，
        // 更新链路就断在最后一步。这条钉住数据 URI 的形状。
        val data = UpdateInstall.permissionData("cn.dkarbon.enroute")
        assertEquals("package:cn.dkarbon.enroute", data)
        assertTrue("不能是 content:// 或 file://", data.startsWith("package:"))
    }
}
