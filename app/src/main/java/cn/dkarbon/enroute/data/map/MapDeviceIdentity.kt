package cn.dkarbon.enroute.data.map

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 收集随补丁上传的**设备/应用信息**（票 22）。
 *
 * ## 边界：能拿到什么、拿不到什么
 * | 字段 | 来源 | 说明 |
 * |---|---|---|
 * | `manufacturer` / `model` / `device` | `Build.*` | `vivo` / `V2301A` / `PD2301` |
 * | `sdkInt` / `release` | `Build.VERSION.*` | 36 / `16` |
 * | `ssaid` | `Settings.Secure.ANDROID_ID` | **按「应用+签名+用户」派生，不是硬件序列号**；拿不到就空串 |
 * | `installId` | 本地随机 UUID | 安装级；重置身份时一并更换 |
 * | `did` | 本地随机串 | 服务端 `ch` 的来源，可重置 |
 *
 * **拿不到真正的硬件标识**：Android 10+ 起 IMEI/序列号对普通应用完全不可读
 * （`READ_PHONE_STATE` 也不行，只有设备所有者/运营商应用可以）。
 * 所以代码里**不申请**任何读手机状态的权限，也**不写**「采集 IMEI」这种做不到的承诺。
 *
 * **`sid`（学号）不在这个类里，也不会被发出去。**
 */
object MapDeviceIdentity {

    /**
     * @param did 本地随机串（[MapContributeStore.anonymousKey]）
     * @param installId 安装级 UUID（[MapContributeStore.installId]）
     */
    fun collect(
        ctx: Context,
        did: String,
        installId: String,
        appVersion: String,
    ): MapContribute.ClientInfo = MapContribute.ClientInfo(
        did = did,
        appVer = appVersion,
        manufacturer = safe { Build.MANUFACTURER },
        model = safe { Build.MODEL },
        device = safe { Build.DEVICE },
        sdkInt = runCatching { Build.VERSION.SDK_INT }.getOrDefault(0),
        release = safe { Build.VERSION.RELEASE },
        ssaid = androId(ctx),
        installId = installId,
    )

    /**
     * `Settings.Secure.ANDROID_ID`。
     *
     * 拿不到 / 空 / 已知脏值（个别 ROM 会返回 `"unknown"` 或全零）一律返回**空串** ——
     * 空串在请求体里就是 `"ssaid":""`，服务端落盘也是空。
     * **绝不编一个占位串**：那会让"没有这个信息"和"有但不同"变成无法区分。
     */
    fun androId(ctx: Context): String {
        val raw = runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().trim()
        if (raw.isEmpty()) return ""
        if (raw.equals("unknown", ignoreCase = true)) return ""
        if (raw.all { it == '0' }) return ""
        if (raw.length > 64) return ""          // 异常 ROM 的脏值，不要
        return raw
    }

    /** `Build.*` 里偶尔有空值/超长脏值，取不到就空串 */
    private fun safe(get: () -> String): String = runCatching {
        get().trim().take(40)
    }.getOrDefault("")
}
