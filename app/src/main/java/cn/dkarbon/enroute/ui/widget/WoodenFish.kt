package cn.dkarbon.enroute.ui.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.R
import cn.dkarbon.enroute.data.MeritSound
import cn.dkarbon.enroute.data.Prefs
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 安慰剂按钮 · 电子木鱼（2026-11-10 主人指定）。
 *
 * ## 图形是「借」的，不是自己画的
 * 木鱼轮廓直接取自开源项目 **IvanHanloth/wooden-fish（MIT License）** 的内联 SVG
 * （`viewBox="0 0 1365 1024"`，单条 path，见 `res/drawable/ic_muyu_light.xml` 顶部注释）。
 * 连它的敲击动效也照抄了原实现的做法 —— 原版是 `scale3d(0.93, 0.93, 1)`，
 * 这里就是「整只木鱼轻微一缩」，比我上一版手画的槌子更像真东西。
 *
 * ## 交互照抄业界标准形态
 * 点一下：功德 +1、飘字、音效、震动；按住连敲（每 190ms 一下）。
 * 参考：[木鱼敲敲](https://apps.apple.com/us/app/id6806579107)、[在线电子木鱼](https://fish.leixf.cn)。
 *
 * ## 没照抄什么
 * ❌ 皮肤商城 / 自动挂机 / 全屏「功德圆满」光效 —— 这是课表工具里的一个小玩具，不是独立产品
 *
 * ## 诚实底线（专家定的）
 * 按钮上明写「不联网」；**绝不给**「✓ 已核对课表」这类它会撒谎的回执 ——
 * 一旦真调课了，承担后果（走错楼、迟到、记名）的是用户。
 */
@Composable
fun WoodenFish(
    /** 学号（未登录传空）—— 功德按它分账，未登录时挂到 `anon` 上 */
    studentNo: String = "",
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx.applicationContext) }

    // 账户键：登录了用学号，没登录用 anon（换账号各算各的，不合并）
    val accountKey = cn.dkarbon.enroute.data.MeritRules.accountKey(studentNo)
    // 老 key `merit` 一次性迁移到当前账户（老用户不该发现功德归零）
    remember(accountKey) { prefs.migrateLegacyMerit(accountKey) }
    // 首次登录：把 anon 的累计并进学号，然后清空 anon（只并一次，幂等）
    remember(accountKey) {
        if (!cn.dkarbon.enroute.data.MeritRules.isAnonymous(accountKey)) {
            val anon = prefs.meritTotal(cn.dkarbon.enroute.data.MeritRules.ANON)
            if (anon > 0) {
                prefs.setMeritTotal(
                    accountKey,
                    cn.dkarbon.enroute.data.MeritRules.mergeAnon(prefs.meritTotal(accountKey), anon),
                )
                prefs.clearAnonMerit()
            }
        }
    }

    val today = cn.dkarbon.enroute.data.MeritRules.today()
    var merit by rememberSaveable(accountKey) { mutableIntStateOf(prefs.meritTotal(accountKey)) }
    var todayCount by rememberSaveable(accountKey) {
        mutableIntStateOf(
            cn.dkarbon.enroute.data.MeritRules
                .rollDay(prefs.meritDay(accountKey), today, prefs.meritToday(accountKey))
                .second,
        )
    }
    var soundOn by rememberSaveable { mutableStateOf(prefs.meritSound) }

    /**
     * 跨天滚动：**挂在页面重组上也要判**（否则挂着页面过了午夜，显示还是昨天的数）。
     * 日期口径与 `SchoolClock` 一致：本地时区的 `LocalDate.now()`。
     */
    fun rollIfNewDay() {
        val (d, c) = cn.dkarbon.enroute.data.MeritRules
            .rollDay(prefs.meritDay(accountKey), cn.dkarbon.enroute.data.MeritRules.today(), todayCount)
        if (d != prefs.meritDay(accountKey) || c != todayCount) {
            todayCount = c
            prefs.setMeritToday(accountKey, d, c)
        }
    }
    androidx.compose.runtime.LaunchedEffect(accountKey) { rollIfNewDay() }

    // 敲击：整只鱼轻微一缩（原版 scale3d 0.93 的做法）
    val strike = remember { Animatable(0f) }
    // 飘字
    val float = remember { Animatable(0f) }

    fun knock() {
        val todayNow = cn.dkarbon.enroute.data.MeritRules.today()
        val (day, tc) = cn.dkarbon.enroute.data.MeritRules
            .rollDay(prefs.meritDay(accountKey), todayNow, todayCount)
        val (nt, ntc) = cn.dkarbon.enroute.data.MeritRules.afterStrike(merit, tc)
        merit = nt
        todayCount = ntc
        prefs.setMeritTotal(accountKey, nt)
        prefs.setMeritToday(accountKey, day, ntc)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (soundOn) MeritSound.play(ctx)
        scope.launch {
            strike.snapTo(0f)
            strike.animateTo(1f, tween(60))
            strike.animateTo(0f, tween(130))
        }
        scope.launch {
            float.snapTo(0f)
            float.animateTo(1f, tween(700))
        }
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 不画卡片底、不画框：木鱼直接坐在页面底色上（主人 2026-11-14：「就像原本就在背景里面一样」）。
        // 去掉框以后和上方「本机」折叠区失去边界，所以先用一条同款 hairline 分隔线划开，
        // 再给足纵向留白（12dp → 18dp）让它有呼吸。
        Box(Modifier.fillMaxWidth().height(Stroke.hairline).background(t.rule))
        Spacer(Modifier.size(18.dp))

        // 抬头：功德数 + 声音开关
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("功德", fontSize = 10.5.sp, color = t.inkSoft)
            Spacer(Modifier.size(6.dp))
            Text(
                "$merit",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NumericFont,
                color = t.primary,
            )
            Spacer(Modifier.size(6.dp))
            // 今日小字：主数字永远是**累计**（功德是累计的笑点），今日只作补充
            Text(
                "今日 $todayCount",
                fontSize = 9.5.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
            )
            // 没绑账号时诚实标一句：这份计数不分账号（换个号还是这一份）
            if (cn.dkarbon.enroute.data.MeritRules.isAnonymous(accountKey)) {
                Spacer(Modifier.size(4.dp))
                Text("本机", fontSize = 8.sp, color = t.inkFaint)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .border(Stroke.hairline, t.outline)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            soundOn = !soundOn
                            prefs.meritSound = soundOn
                            if (soundOn) MeritSound.ensure(ctx)
                        }
                    },
            ) {
                Text(
                    if (soundOn) "声音 开" else "声音 关",
                    fontSize = 9.5.sp,
                    color = if (soundOn) t.inkSoft else t.inkFaint,
                )
            }
        }

        // 木鱼：点它 / 按住它
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        knock()
                        while (true) {
                            val ev = withTimeoutOrNull(190) { awaitPointerEvent() }
                            if (ev == null) {
                                knock()
                                continue
                            }
                            if (ev.changes.all { !it.pressed }) break
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(
                    if (t.isDark) R.drawable.ic_muyu_dark else R.drawable.ic_muyu_light,
                ),
                contentDescription = "木鱼，点一下加一功德",
                modifier = Modifier
                    .size(width = 196.dp, height = 147.dp)
                    .graphicsLayer {
                        val s = 1f - 0.07f * strike.value
                        scaleX = s
                        scaleY = s
                    },
            )

            // 飘字「功德 +1」
            if (float.value > 0f && float.value < 1f) {
                Text(
                    "功德 +1",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.primary.copy(alpha = (1f - float.value).coerceIn(0f, 1f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (26 - 22 * float.value).dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.Center) {
            Text("点一下 +1 · 按住连敲", fontSize = 10.sp, color = t.inkFaint)
        }
        Spacer(Modifier.height(3.dp))
        Text("赛博功德 · 不联网 · 只是给你按的", fontSize = 9.5.sp, color = t.inkFaint)
        Spacer(Modifier.height(2.dp))
        Text(
            "木鱼图形：IvanHanloth/wooden-fish（MIT）",
            fontSize = 8.5.sp,
            color = t.inkFaint.copy(alpha = 0.7f),
        )
    }
}
