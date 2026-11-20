package cn.dkarbon.enroute.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 「保持登录」一次性说明卡（票 14）。
 *
 * 只在**首次登录成功、且用户还没表过态**时弹一次：
 * - 默认关闭 → 卡片是「邀请」而不是「通知」，两个按钮同等显眼；
 * - 文案要把「存什么、发到哪、怎么关」说全，不能只写「保持登录」四个字骗人点；
 * - 点任一个按钮都会记下「问过了」，以后不再弹（设置页里常驻开关可随时改）。
 *
 * 挂在根节点（[cn.dkarbon.enroute.ui.EnRouteRoot]），这样不论登录页关得多快都不会漏掉。
 */
@Composable
fun KeepLoginPrompt(vm: TimetableViewModel) {
    val t = InkTokens.current
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl)
                .background(t.card)
                .border(1.5.dp, t.outline)
                .padding(Space.l),
        ) {
            Text(
                SessionText.KEEP_LOGIN_TITLE,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
            )
            Spacer(Modifier.height(Space.s))
            Text(
                SessionText.KEEP_LOGIN_BODY,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkSoft,
            )
            Spacer(Modifier.height(Space.m))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                // 主按钮：启用（实心）
                Box(
                    Modifier.weight(1f)
                        .background(t.primary)
                        .clickable { vm.enableKeepLogin() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("启用保持登录", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = t.paper)
                }
                // 次按钮：不了（描边）
                Box(
                    Modifier.weight(1f)
                        .border(Stroke.hairline, t.outline)
                        .clickable { vm.dismissKeepLoginPrompt() }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("不了，每次手动登录", fontSize = 12.sp, color = t.inkSoft)
                }
            }
        }
    }
}
