package cn.dkarbon.enroute.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.icons.AppIcons
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 失败态的**唯一文案口径**（2026-11-20，主人要求）。
 *
 * 学校的教务、资讯、地图服务都只在校内网可达，学生用移动数据时失败率极高。
 * 以前每处各写一句（「没连上学院官网（校园网/网络不通都可能这样）」「刷新失败」……），
 * 用户看不出「该怎么解决」。这里统一成三类可行动的说法：
 *
 * - [NetFailure.Unreachable]：请求根本到不了 → 「需要校园网内网或 mangoVPN 才能加载」
 * - [NetFailure.ServerError]：到了但服务端报错 → 说明是服务端问题，别让用户怀疑自己的网络
 * - [NetFailure.Parse]：内容变了 → 说清是站点改版，不必反复重试
 *
 * 用法：Compose 里 `CampusNetworkHint(failure)`；非 Compose 场景用 [campusNetworkLine]。
 */

/** 加载失败的三种性质。判定逻辑在各自的仓库层，这里只管怎么说。 */
enum class NetFailure {
    /** 连接超时 / DNS 失败 / 无网络 / 证书错 —— 多数是没连校园网或没开 VPN。 */
    Unreachable,

    /** 连上了，但 HTTP 4xx/5xx。 */
    ServerError,

    /** 连上了、也 200，但内容解析不出来（站点改版）。 */
    Parse,
}

/** 一行说明（列表行、Toast、日志都用它，保证全 App 一个字都不差）。 */
fun campusNetworkLine(failure: NetFailure): String = when (failure) {
    NetFailure.Unreachable -> "需要校园网内网或 mangoVPN 才能加载"
    NetFailure.ServerError -> "服务端暂时出错，稍后再试"
    NetFailure.Parse -> "内容解析不了，可能站点改版了"
}

/** 更完整的一句，给空态/详情浮层这类有空间的地方。 */
fun campusNetworkDetail(failure: NetFailure): String = when (failure) {
    NetFailure.Unreachable ->
        "这个页面在学校内网才能访问。连上校园网，或先开 mangoVPN 再回来刷新。"
    NetFailure.ServerError ->
        "请求到了服务端但被拒绝了（可能是临时故障或维护）。等一会儿再试；持续失败就是服务端的问题。"
    NetFailure.Parse ->
        "数据拿到了，但格式和预期不一样 —— 多半是站点改版。留一条反馈，我们跟着改解析。"
}

/**
 * 失败提示行：左侧 [AppIcons.NetworkOff]，右侧一行字。无底色无圆角，跟着账页的细线风格走。
 *
 * @param compact true 时不显示图标、字号更小（塞进列表行/页脚时用）
 */
@Composable
fun CampusNetworkHint(
    failure: NetFailure,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val t = InkTokens.current
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!compact) {
            Icon(
                imageVector = AppIcons.NetworkOff,
                contentDescription = null,
                tint = t.inkFaint,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = campusNetworkLine(failure),
            fontSize = if (compact) 9.5.sp else 11.sp,
            color = t.inkSoft,
        )
    }
}
