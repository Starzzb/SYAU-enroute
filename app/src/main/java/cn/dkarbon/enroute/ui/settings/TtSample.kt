package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Course
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.timetable.TesseractBuffers
import cn.dkarbon.enroute.ui.timetable.TtAxis
import cn.dkarbon.enroute.ui.timetable.TtCourseCell
import cn.dkarbon.enroute.ui.timetable.TtPresets
import cn.dkarbon.enroute.ui.timetable.TtSlots
import cn.dkarbon.enroute.ui.timetable.drawTesseract4D
import cn.dkarbon.enroute.ui.timetable.drawTtBackdrop

/**
 * 设置页「胶片」里的实样（m41 v5）。
 *
 * ## 为什么每条取值都要单独出样
 *
 * 主人 2026-11-23 原话：「跟你在 html 文件做出的效果大相径庭，下方配置页面一点没变」。
 * 之前的设置页把每条轴压成一行文字小药丸（「方卡 便签 细线 色块 标签」），
 * 用户读到的是一串名词，读不到差别 —— 方卡和便签到底差在哪，得点一下回课表看。
 *
 * 现在每个取值都按真实渲染路径画一枚实样：格子轴走 [TtCourseCell] 本体
 * （形状/装饰/字段/角标/侵蚀/动效/底色全部生效），其余轴各画各的：
 * 页头画三列日头、行距画横线疏密、底纹画图案块、超立方体按同一套 4D 投影画静态一帧。
 * 所以「看起来是这样」和「点下去会变成这样」是同一份代码算出来的，不可能互相打架。
 *
 * ## 假数据
 *
 * 课名用真课名（不是个人信息），教师一律「张三」，教室「一101」——
 * 依据 AGENTS.md：原型与截图里的假数据不许出现真实姓名。
 */
internal val TtSampleW = 45.dp
internal val TtSampleH = 52.dp

/** 4D 宽样（超立方体那两条轴）的宽度 */
internal val TtSampleWideW = 92.dp

/** 实样用的课：两个字太短看不出换行，五个字能看到「一行四个字」的真宽度 */
internal val ttSampleCourse = Course(name = "不动产估价", teacher = "张三", room = "一101")

/**
 * 「底色」那条轴的实样换一个蓝家族课程。
 *
 * 道理：不动产估价属于赭色家族，它的「四色」底色和「单墨」的黄灰几乎同色，
 * 两枚样张摆在一起看不出区别（2026-11-23 真机复核）。换蓝家族后，
 * 四色 = 蓝、单墨 = 灰、无色 = 透，三档一眼能分开。
 */
internal val ttToneCourse = Course(name = "马克思主义基本原理", teacher = "张三", room = "一101")

/** 把某条轴的取值写进一套槽位，其余轴不动 */
internal fun TtSlots.withAxis(axis: Int, value: Int): TtSlots = when (axis) {
    TtAxis.SHAPE -> copy(shape = value)
    TtAxis.DECOR -> copy(decor = value)
    TtAxis.META -> copy(meta = value)
    TtAxis.BADGE -> copy(badge = value)
    TtAxis.EROSION -> copy(erosion = value)
    TtAxis.MOTION -> copy(motion = value)
    TtAxis.TONE -> copy(tone = value)
    TtAxis.HEADER -> copy(header = value)
    TtAxis.GRID -> copy(grid = value)
    TtAxis.BACKDROP -> copy(backdrop = value)
    TtAxis.BG_MOTION -> copy(bgMotion = value == 1)
    TtAxis.TESS -> copy(tess = value)
    TtAxis.TESS_MODE -> copy(tessMode = value)
    else -> this
}

/** 读某条轴当前的取值 */
internal fun TtSlots.axisValue(axis: Int): Int = when (axis) {
    TtAxis.SHAPE -> shape
    TtAxis.DECOR -> decor
    TtAxis.META -> meta
    TtAxis.BADGE -> badge
    TtAxis.EROSION -> erosion
    TtAxis.MOTION -> motion
    TtAxis.TONE -> tone
    TtAxis.HEADER -> header
    TtAxis.GRID -> grid
    TtAxis.BACKDROP -> backdrop
    TtAxis.BG_MOTION -> if (bgMotion) 1 else 0
    TtAxis.TESS -> tess
    TtAxis.TESS_MODE -> tessMode
    else -> 0
}

/** 该轴是否属于格子里（45×52 真格子样）；否则是布局/背景轴，各画各的 */
internal fun isCellAxis(axis: Int): Boolean = axis <= TtAxis.TONE

/** 一枚实样的宽度：格子轴与布局轴窄、超立方体那两条轴宽 */
internal fun sampleWidthOf(axis: Int) = if (axis >= TtAxis.TESS) TtSampleWideW else TtSampleW

/**
 * 画一枚实样：`slots` 是当前整套槽位，只把 `axis` 换成 `value`，其余照原样参与渲染。
 *
 * 期间会跑一遍 [TtPresets.sanitize]：互斥被改动时（例如角标选「斜纹带」会强制「只课名」），
 * 实样显示的就是真的会生效的那个组合，而不是一个不存在的组合。
 */
@Composable
internal fun TtAxisSample(axis: Int, value: Int, slots: TtSlots, modifier: Modifier = Modifier) {
    val s = TtPresets.sanitize(slots.withAxis(axis, value))
    when {
        isCellAxis(axis) -> Box(modifier.width(TtSampleW).height(TtSampleH)) {
            TtCourseCell(
                course = if (axis == TtAxis.TONE) ttToneCourse else ttSampleCourse,
                progress = 0.45f,
                slots = s,
                tint = s.tone,
                onClickLabel = "",
                modifier = Modifier.fillMaxSize(),
            )
        }
        axis == TtAxis.HEADER -> MiniHeader(value, modifier)
        axis == TtAxis.GRID -> MiniRules(value, modifier)
        axis == TtAxis.BACKDROP -> MiniBackdrop(value, false, modifier)
        axis == TtAxis.BG_MOTION -> MiniBackdrop(s.backdrop, value == 1, modifier)
        axis == TtAxis.TESS -> MiniTess(
            alpha = when (value) { 0 -> 0f; 1 -> 0.26f; else -> 0.40f },
            mode = s.tessMode,
            modifier = modifier,
        )
        else -> MiniTess(alpha = 0.42f, mode = value, modifier = modifier)
    }
}

/** 页头三档：日头那一行的高度与字号 */
@Composable
private fun MiniHeader(v: Int, modifier: Modifier = Modifier) {
    val t = InkTokens.current
    val h = when (v) { 0 -> 22.dp; 2 -> 34.dp; else -> 28.dp }
    val fs = when (v) { 0 -> 9.sp; 2 -> 12.sp; else -> 10.5.sp }
    Column(modifier.width(78.dp).height(TtSampleH)) {
        Row(
            Modifier.fillMaxWidth().height(h).padding(horizontal = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("一", "二", "三").forEach {
                Text(it, fontSize = fs, color = t.inkSoft)
            }
        }
        Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.outline))
    }
}

/** 行距三档：同样的格子高度里塞 5 / 4 / 3 条线 */
@Composable
private fun MiniRules(v: Int, modifier: Modifier = Modifier) {
    val t = InkTokens.current
    val n = when (v) { 0 -> 5; 2 -> 3; else -> 4 }
    val gap = when (v) { 0 -> 8.dp; 2 -> 14.dp; else -> 11.dp }
    Column(
        modifier.width(TtSampleW).height(TtSampleH),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(n) {
            Box(Modifier.fillMaxWidth().height(0.75.dp).background(t.outline))
        }
    }
}

/** 底纹样：把真课表的底纹画布装进 45×52 的格子 */
@Composable
private fun MiniBackdrop(style: Int, drift: Boolean, modifier: Modifier = Modifier) {
    val t = InkTokens.current
    Canvas(modifier.width(TtSampleW).height(TtSampleH)) {
        drawTtBackdrop(
            style = style,
            drift = if (drift) 0.35f else 0f,
            axisW = 0f,
            colW = size.width / 3f,
            rowH = size.height / 4f,
            bandH = 0f,
            rule = t.ink.copy(alpha = 0.22f),
            band = t.ink.copy(alpha = 0.05f),
        )
    }
}

/** 超立方体：同一套 4D 投影画一帧静止的；正交投影那一档真的换成 Schlegel 图 */
@Composable
private fun MiniTess(alpha: Float, mode: Int, modifier: Modifier = Modifier) {
    val t = InkTokens.current
    val buffers = remember { TesseractBuffers() }
    Canvas(modifier.width(TtSampleWideW).height(TtSampleH)) {
        if (alpha <= 0f) return@Canvas
        drawTesseract4D(
            color = t.ink,
            phase = TESS_PHASE[mode.coerceIn(0, TESS_PHASE.lastIndex)],
            alpha = alpha,
            strokeWidth = 1f,
            buffers = buffers,
            radiusFactor = 0.5f,
            ortho = mode == 4,
        )
    }
}

/** 每档动法给一个不同的相位（定格是 0），静态截图也能看出五档不一样 */
private val TESS_PHASE = floatArrayOf(0.35f, 1.15f, 0.78f, 0f, 0.55f)

/** 整套槽位的真样（预设 rail 用）：与课表同一个渲染器，改一条轴它自动跟着变 */
@Composable
internal fun TtSlotsSample(slots: TtSlots, modifier: Modifier = Modifier) {
    val s = TtPresets.sanitize(slots)
    Box(modifier.width(TtSampleW).height(TtSampleH)) {
        TtCourseCell(
            course = ttSampleCourse,
            progress = 0.45f,
            slots = s,
            tint = s.tone,
            onClickLabel = "",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

