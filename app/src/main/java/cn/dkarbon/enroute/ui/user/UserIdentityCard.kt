package cn.dkarbon.enroute.ui.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.ui.kit.inkPress
import cn.dkarbon.enroute.ui.ledger.LedgerTnum
import cn.dkarbon.enroute.ui.ledger.ledgerPress
import cn.dkarbon.enroute.ui.theme.InkTokens
import kotlinx.coroutines.delay

/**
 * 「我的」页首屏第一块：可下拉撑开的身份卡（2026-11-20 m34 墨洗改版，第二稿）。
 *
 * 形态（照 uiverse `yellow-panda-85` 的压印质感，见 [inkPress]）：
 * 一张压了浅凹槽的宣纸，上缘吃光、下缘受光，左上一线亮边、右下一线暗边，
 * 底色由上到下极浅地变深。右上角翘起一个折角 —— 这是唯一的「能摊开」线索，不写字。
 *
 * 交互：点一下**向下撑开**（高度 animateContentSize 增长，明细延迟淡入 + 上移归位），
 * 再点收起。没有 3D 翻页 —— 摊开一张纸是「抽出来」，不是「翻过去」。
 *
 * 收起态露：头像 · 姓名 · 学号 · 专业年级 · 三格读数（绩点 / 学分 / 排名）。
 * 摊开后露：分隔线 + 四条可点明细（含培养方案入口）。
 * 未登录 / 过期：整卡是登录门禁，不画折角、不摊开。
 */
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
    val radius = 14.dp

    if (state != SessionState.Active) {
        LoginGateCard(state = state, onLogin = onLogin)
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) 0.992f else 1f,
        animationSpec = tween(140),
        label = "identityCardScale",
    )

    // 折角呼吸（收起时才动）与按压抬起：与旧版一致的手感线索
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
    val foldLift = breathLift.value + if (pressed) 2.5f else 0f

    // 展开与收起都用「高度 + 透明度」同步的竖向动画，纸张和字一起动：
    //   展开 —— 从顶部往下摊开 520ms（末端略微过冲），字 150ms 后淡入 300ms
    //   收起 —— 向顶部卷回 420ms（加速、无过冲），字 140ms 内先退净，纸随即跟上
    // 这样不会出现「字没了、纸还撑着」的空壳帧。
    val foldAlpha by animateFloatAsState(
        targetValue = if (expanded) 0f else 1f,
        // 展开时折角先退（60ms 后 180ms 淡出）；收起时等纸收到大半（340ms）再浮现，
        // 免得收到一半时折角突然出现。
        animationSpec = tween(
            durationMillis = 180,
            delayMillis = if (expanded) 60 else 340,
        ),
        label = "identityFoldFade",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .inkPress(
                radius = radius,
                paperTop = t.card,
                paperBottom = t.cardAlt,
                dark = t.isDark,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle),
    ) {
        Column {
            IdentityHead(name = name, studentNo = studentNo, subtitle = subtitle)
            StatStrip(cells = cells)

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(
                        durationMillis = 520,
                        easing = CubicBezierEasing(0.22f, 0.9f, 0.24f, 1f),
                    ),
                    expandFrom = Alignment.Top,
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 300, delayMillis = 150),
                ),
                exit = shrinkVertically(
                    animationSpec = tween(
                        durationMillis = 420,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(
                    // 淡出与卷回同曲线、同时长：字始终填满纸面，全程没有空壳帧
                    animationSpec = tween(
                        durationMillis = 420,
                        easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f),
                    ),
                ),
            ) {
                DetailList(cells = cells, expanded = expanded)
            }
        }

        if (foldAlpha > 0.01f) {
            DogEarFold(
                foldSize = 30.dp,
                liftDp = foldLift,
                foldColor = t.cardAlt,
                creaseColor = t.inkFaint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(30.dp)
                    .graphicsLayer { alpha = foldAlpha },
            )
        }
    }
}

/** 头栏：46dp 墨圈头像（姓名首字）+ 姓名 + 学号右对齐，下一行专业 · 年级。 */
@Composable
private fun IdentityHead(name: String, studentNo: String, subtitle: String) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(t.cardAlt)
                .border(1.dp, t.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = t.ink,
                letterSpacing = 0.06.em,
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    fontSize = 15.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    letterSpacing = 0.08.em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    studentNo,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                    letterSpacing = 0.10.em,
                    style = LedgerTnum,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = t.inkFaint,
                letterSpacing = 0.06.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 三格读数：大数字 + 下方小标签。数值缺失时如实显示「暂缺」，不填 0。
 * 只取前三条 —— 第四条（培养方案）是入口而不是读数，留在摊开后的明细里。
 */
@Composable
private fun StatStrip(cells: List<UserStatCell>) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        cells.take(3).forEach { cell ->
            Column {
                if (cell.missing) {
                    Text(
                        text = "暂缺",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = t.inkFaint,
                    )
                } else {
                    Text(
                        text = cell.value.ifBlank { "—" },
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = t.ink,
                        style = LedgerTnum,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = cell.label,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    color = t.inkFaint,
                    letterSpacing = 0.12.em,
                )
            }
        }
    }
}

/**
 * 摊开后的明细：顶部一道 1dp 分隔线（纸上的折痕），下面四条账目行。
 * 行式而非方格 —— 与页面下方各分组同族，读起来是「更多条目」而不是重复的读数卡。
 */
@Composable
private fun DetailList(cells: List<UserStatCell>, expanded: Boolean) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(t.rule),
        )
        cells.forEach { cell ->
            DetailRow(cell = cell, clickable = expanded && cell.onClick != null)
        }
    }
}

/** 一行账目：左标签（微字距）· 右值（衬线等宽）+ 可点时的 › 指示。 */
@Composable
private fun DetailRow(cell: UserStatCell, clickable: Boolean) {
    val t = InkTokens.current
    val action = cell.onClick
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (clickable && action != null) Modifier.clickable(onClick = action) else Modifier)
            .dottedBottom(t.rule.copy(alpha = 0.55f))
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cell.label,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = t.inkFaint,
            letterSpacing = 0.06.em,
        )
        Spacer(Modifier.weight(1f))
        if (cell.missing) {
            Text(
                text = "暂缺",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = t.inkFaint,
            )
        } else {
            Text(
                text = cell.value.ifBlank { "—" },
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
                style = LedgerTnum,
            )
        }
        if (clickable) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = "›",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = t.inkFaint,
            )
        }
    }
}

/** 1dp 点线（账页的行底）：2dp 实、3dp 空，横向铺满。 */
private fun Modifier.dottedBottom(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
    )
}

/** 未登录 / 过期：整卡门禁，显示「未登录」+「登录教务」描边按钮；不画折角、不摊开。 */
@Composable
private fun LoginGateCard(state: SessionState, onLogin: () -> Unit) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(14.dp)
    val title = if (state == SessionState.None) "未登录" else "会话已过期"
    val sub = if (state == SessionState.None) "只在校内网可用" else "密码只在校内网用一次，不保存"
    val action = if (state == SessionState.None) "登录教务" else "续期登录"
    Column(
        Modifier
            .fillMaxWidth()
            .inkPress(
                radius = 14.dp,
                paperTop = t.card,
                paperBottom = t.cardAlt,
                dark = t.isDark,
            )
            .ledgerPress(ink = t.ink, onClickLabel = action, onClick = onLogin)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(t.cardAlt)
                    .border(1.dp, t.outline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.inkFaint,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.5.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    letterSpacing = 0.08.em,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    sub,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .border(1.5.dp, t.ink, RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                action,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.ink,
                letterSpacing = 0.06.em,
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
