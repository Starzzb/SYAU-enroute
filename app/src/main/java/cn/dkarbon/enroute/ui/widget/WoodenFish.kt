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
fun WoodenFish(modifier: Modifier = Modifier) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(ctx.applicationContext) }

    var merit by rememberSaveable { mutableIntStateOf(prefs.merit) }
    var soundOn by rememberSaveable { mutableStateOf(prefs.meritSound) }

    // 敲击：整只鱼轻微一缩（原版 scale3d 0.93 的做法）
    val strike = remember { Animatable(0f) }
    // 飘字
    val float = remember { Animatable(0f) }

    fun knock() {
        merit += 1
        prefs.merit = merit
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
        modifier
            .fillMaxWidth()
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
