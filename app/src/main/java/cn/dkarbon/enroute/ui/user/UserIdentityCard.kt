package cn.dkarbon.enroute.ui.user

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.ui.ledger.LedgerTnum
import cn.dkarbon.enroute.ui.ledger.ledgerPress
import cn.dkarbon.enroute.ui.theme.InkTokens
import kotlinx.coroutines.delay

/**
 * 「我的」页首屏第一块：可展开身份卡（2026-11-15 主人定，折角版）。
 *
 * 收起态：一张与教务/校园/本机同族的描边卡（1dp outline + 10dp 圆角 + 无底色），
 * 右上角一片狗耳折角；内容只有姓名 / 学号（衬线等宽右对齐）/ 专业·年级。
 * 展开态：折角消失、角落恢复平整 —— 这是「已展开」的唯一信号；
 * 卡内变成左右对开，左栏身份、右栏成绩 2×2，中间一道竖直折痕线。
 * 未登录 / 过期：整卡是登录门禁，显示「未登录」与「登录教务」描边按钮，不画折角、不展开。
 *
 * 折角三件套：折角正面（比纸色略亮 = card 色）、折痕线（1dp inkFaint）、
 * 下沿一条极淡投影（黑 6%）。呼吸与按压都在折角上做幅度变化，不靠任何说明文字。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun UserIdentityCard(
    state: SessionState,
    name: String,
    studentNo: String,
    subtitle: String,
    cells: List<UserStatCell>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLogin: () -> Unit,
) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(10.dp)

    if (state != SessionState.Active) {
        LoginGateCard(state = state, onLogin = onLogin)
        return
    }

    // ── 已登录：可展开身份卡 ──
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // 整卡按压：scale → 0.992，松手回落
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) 0.992f else 1f,
        animationSpec = tween(140),
        label = "identityCardScale",
    )
    // 按压时折角沿对角线抬到 2.5dp
    val pressLift by animateFloatAsState(
        targetValue = if (pressed) 2.5f else 0f,
        animationSpec = tween(140),
        label = "identityFoldPressLift",
    )

    // 折角呼吸：4.5s 一次，1.5dp 起落 320ms；系统动画时长关闭时不动
    val breathLift = remember { Animatable(0f) }
    val ctx = LocalContext.current
    val animScaleOff = remember(ctx) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
    LaunchedEffect(animScaleOff, expanded) {
        if (animScaleOff || expanded) {
            breathLift.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            delay(4180)
            breathLift.animateTo(1.5f, tween(160, easing = FastOutSlowInEasing))
            breathLift.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        }
    }
    val foldLift = breathLift.value + pressLift

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .border(1.dp, t.outline, shape)
            .clip(shape)
            .background(if (pressed) t.ink.copy(alpha = 0.02f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(tween(180)) togetherWith fadeOut(tween(180))) using
                    SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(180) })
            },
            label = "identityCardSpread",
        ) { isExpanded ->
            if (isExpanded) {
                ExpandedSpread(name, studentNo, subtitle, cells)
            } else {
                CollapsedRow(name, studentNo, subtitle)
            }
        }

        if (!expanded) {
            DogEarFold(
                foldSize = 26.dp,
                liftDp = foldLift,
                foldColor = t.card,
                creaseColor = t.inkFaint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp),
            )
        }
    }
}

/** 收起态：姓名 17sp/600 + 专业·年级 11.5sp/inkSoft，右侧学号衬线等宽。 */
@Composable
private fun CollapsedRow(name: String, studentNo: String, subtitle: String) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = t.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            studentNo,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            color = t.ink,
            style = LedgerTnum,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 展开态：左 0.42 身份 / 右 0.58 成绩 2×2，中间 1dp 竖直折痕线（略短于卡高）。 */
@Composable
private fun ExpandedSpread(
    name: String,
    studentNo: String,
    subtitle: String,
    cells: List<UserStatCell>,
) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(0.42f).padding(end = 10.dp)) {
            Text(
                name,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                studentNo,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                color = t.ink,
                style = LedgerTnum,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = t.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .padding(vertical = 6.dp)
                .background(t.inkFaint.copy(alpha = 0.35f)),
        )
        Column(Modifier.weight(0.58f).padding(start = 10.dp)) {
            UserStatGrid(cells)
        }
    }
}

/** 未登录 / 过期：整卡门禁，显示「未登录」+「登录教务」描边按钮；不画折角、不展开。 */
@Composable
private fun LoginGateCard(state: SessionState, onLogin: () -> Unit) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(10.dp)
    val title = if (state == SessionState.None) "未登录" else "会话已过期"
    val sub = if (state == SessionState.None) "只在校内网可用" else "密码只在校内网用一次，不保存"
    val action = if (state == SessionState.None) "登录教务" else "续期登录"
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, t.outline, shape)
            .clip(shape)
            .ledgerPress(ink = t.ink, onClickLabel = action, onClick = onLogin)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = t.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            sub,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = t.inkSoft,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .border(1.dp, t.outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                action,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
            )
        }
    }
}

/**
 * 右上狗耳折角。
 *
 * 几何：以 [foldSize]×[foldSize] 的正方形右上三角为折角正面，对角线即折痕。
 * 折痕线固定在纸上（原始对角线）；折角正面随 [liftDp] 沿对角线向卡内平移，
 * 下沿投影跟着走 —— 抬起来时角与纸之间露出缝隙，是「翘起的纸角」而不是装饰三角。
 */
@Composable
private fun DogEarFold(
    foldSize: Dp,
    liftDp: Float,
    foldColor: Color,
    creaseColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val s = foldSize.toPx()
        // 沿对角线（右下 45°）抬起的位移，大小 = liftDp
        val lift = liftDp.dp.toPx()
        val dx = -lift * 0.7071f
        val dy = lift * 0.7071f

        // 折角下沿投影：黑 6%，沿下沿再往左下偏 1.5dp，圆帽软边
        val shadowOff = 1.5.dp.toPx()
        drawLine(
            color = Color.Black.copy(alpha = 0.06f),
            start = Offset(dx - shadowOff, dy + shadowOff),
            end = Offset(s + dx - shadowOff, s + dy + shadowOff),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 折角正面：比纸色略亮（foldColor），右上的直角三角
        val flap = Path().apply {
            moveTo(dx, dy)
            lineTo(s + dx, dy)
            lineTo(s + dx, s + dy)
            close()
        }
        drawPath(flap, foldColor)

        // 折痕线：1dp inkFaint，固定在原始对角线上
        drawLine(
            color = creaseColor,
            start = Offset(0f, 0f),
            end = Offset(s, s),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
