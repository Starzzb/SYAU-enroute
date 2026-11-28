package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.timetable.TtAxis
import cn.dkarbon.enroute.ui.timetable.TtOverrides
import cn.dkarbon.enroute.ui.timetable.TtPresets
import cn.dkarbon.enroute.ui.timetable.TtSlots

/**
 * 设置 →「课程表卡片」。
 *
 * ## 版面口径（m41 v5，2026-11-23 重做）
 *
 * 主人拿 `docs/design/tt-settings-merged.html` 逐屏对照后判定「下方配置页面一点没变」。
 * 那一版把每条轴压成一行文字药丸，读得到名词读不到差别。现在照原型重排：
 *
 *   预览（与主课表同几何）→ 预设 rail（五枚真格子样 + 名字）→ 13 格刻度 →
 *   主体切换 → 逐条设置（点开才展开胶片，每枚取值一枚实样）→ 三个效果药丸
 *
 * 行头只写「名字 + 当前值」，被钉死的那条在值前面点一枚红点；
 * 展开后的胶片第一枚是「跟随」，其后每个取值都是按真实渲染路径画出来的实样 ——
 * 见 [TtAxisSample]。
 *
 * 默认值口径不变：老用户升级后什么都不点，课表一个像素不变。
 */
@Composable
fun CourseCardSettingsScreen(
    ui: AppState,
    vm: TimetableViewModel,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val slots = TtPresets.resolve(ui.ttPreset, overridesOf(ui))
    // 同一时刻只展开一条：十三条全摊开时一屏放不下，滚动位置也会丢
    var openAxis by remember { mutableStateOf(-1) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TtPreview(
            slots = slots,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m),
        )

        PresetRail(selected = ui.ttPreset, onPick = vm::setTtPreset)
        AxisRuler(slots = slots, ui = ui, onDot = { axis ->
            if (!isLocked(axis, slots)) openAxis = if (openAxis == axis) -1 else axis
        })
        GroupTitle("逐条设置")
        Divider()
        Column(Modifier.fillMaxWidth().background(t.card)) {
            for (axis in 0 until TtAxis.sizes.size) {
                val locked = isLocked(axis, slots)
                AxisRow(
                    axis = axis,
                    slots = slots,
                    override = overrideOf(ui, axis),
                    locked = locked,
                    child = TtAxis.parentOf(axis) >= 0,
                    open = openAxis == axis && !locked,
                    onToggle = { openAxis = if (openAxis == axis) -1 else axis },
                    onPick = { value -> vm.setTtOverride(axis, value) },
                )
                if (axis < TtAxis.sizes.size - 1) Divider()
            }
        }
        Divider()

        FxPills(ui = ui, vm = vm)
        Spacer(Modifier.height(Space.xxl))
    }
}

/** 一次性把 13 个覆盖位收成一个 TtOverrides（预览与课表用同一份解析逻辑） */
private fun overridesOf(ui: AppState): TtOverrides = TtOverrides(
    shape = ui.ttOvShape,
    decor = ui.ttOvDecor,
    meta = ui.ttOvMeta,
    badge = ui.ttOvBadge,
    erosion = ui.ttOvErosion,
    motion = ui.ttOvMotion,
    tone = ui.ttOvTone,
    header = ui.ttOvHeader,
    grid = ui.ttOvGrid,
    backdrop = ui.ttOvBackdrop,
    bgMotion = ui.ttOvBgMotion,
    tess = ui.ttOvTess,
    tessMode = ui.ttOvTessMode,
)

private fun overrideOf(ui: AppState, axis: Int): Int = when (axis) {
    TtAxis.SHAPE -> ui.ttOvShape
    TtAxis.DECOR -> ui.ttOvDecor
    TtAxis.META -> ui.ttOvMeta
    TtAxis.BADGE -> ui.ttOvBadge
    TtAxis.EROSION -> ui.ttOvErosion
    TtAxis.MOTION -> ui.ttOvMotion
    TtAxis.TONE -> ui.ttOvTone
    TtAxis.HEADER -> ui.ttOvHeader
    TtAxis.GRID -> ui.ttOvGrid
    TtAxis.BACKDROP -> ui.ttOvBackdrop
    TtAxis.BG_MOTION -> ui.ttOvBgMotion
    TtAxis.TESS -> ui.ttOvTess
    else -> ui.ttOvTessMode
}

/** 子轴在父轴关掉时整条锁住：改不动，也不显示当前值（写「—」）。 */
private fun isLocked(axis: Int, slots: TtSlots): Boolean =
    TtAxis.parentOf(axis) >= 0 && slots.tess == 0

/** 预设 rail：五枚真格子样 + 名字，横向滚动 */
@Composable
private fun PresetRail(selected: Int, onPick: (Int) -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.l, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TtPresets.ALL.forEachIndexed { i, preset ->
            val on = selected == i
            Column(
                Modifier.clickable { onPick(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .border(
                            if (on) 1.5.dp else 0.75.dp,
                            if (on) t.primary else t.outline,
                            RectangleShape,
                        )
                        .padding(2.dp),
                ) {
                    TtSlotsSample(preset.slots)
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    preset.label,
                    fontSize = 9.5.sp,
                    color = if (on) t.ink else t.inkFaint,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * 13 格刻度：一枚点对应一条轴。
 *
 * 红实心 = 这条我自己改过（与当前预设不同）；空心 = 仍是预设给的值。
 * 这样一眼能看出「这套预设在哪些地方被我改过」，不用逐条读字。
 *
 * 2026-11-28 修：原来按「覆盖位 >= 0」判实心。可预设从 m42 起写的全是显式值，
 * 覆盖位恒 >= 0 —— 十三枚点永远全红，刻度等于没有。改按「与预设值比」判。
 */
@Composable
private fun AxisRuler(slots: TtSlots, ui: AppState, onDot: (Int) -> Unit) {
    val t = InkTokens.current
    val presetSlots = TtPresets.of(ui.ttPreset).slots
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (axis in 0 until TtAxis.sizes.size) {
            val touched = slots.axisValue(axis) != presetSlots.axisValue(axis)
            val d = if (touched) 5.dp else 3.5.dp
            Box(
                Modifier.size(d).clip(CircleShape)
                    .background(
                        if (touched) t.primary else Color.Transparent,
                    )
                    .then(
                        if (!touched) Modifier.border(0.75.dp, t.outline, CircleShape) else Modifier,
                    )
                    .clickable { onDot(axis) },
            )
        }
    }
}

/** 分组：13 条轴之前的一行小标题，把「预设 / 刻度」与「逐条设置」分开 */
@Composable
private fun GroupTitle(text: String) {
    val t = InkTokens.current
    Text(
        text,
        fontSize = 11.sp,
        color = t.inkFaint,
        modifier = Modifier.fillMaxWidth().padding(start = Space.l, top = 12.dp, bottom = 8.dp),
    )
}

/**
 * 一条轴：行头（名字 + 当前值），点开是胶片。
 *
 * 行头不写解释、不写取值清单 —— 想比差别就点开看实样（AGENTS.md：用设计，不用文字）。
 */
@Composable
private fun AxisRow(
    axis: Int,
    slots: TtSlots,
    override: Int,
    locked: Boolean,
    child: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val t = InkTokens.current
    val value = slots.axisValue(axis)
    val label = if (locked) "—" else TtAxis.options[axis][value.coerceIn(0, TtAxis.options[axis].lastIndex)]
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp)
                .clickable(enabled = !locked, onClick = onToggle)
                .padding(start = Space.l + if (child) 16.dp else 0.dp, end = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                TtAxis.names[axis],
                fontSize = 13.sp,
                color = if (locked) t.inkFaint else t.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(label, fontSize = 11.sp, color = t.inkFaint)
        }
        if (open) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = Space.l + if (child) 16.dp else 0.dp, end = Space.l, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (v in 0 until TtAxis.sizes[axis]) {
                    OptionChip(
                        axis = axis,
                        value = v,
                        slots = slots,
                        selected = override == v,
                        onClick = { onPick(v) },
                    )
                }
            }
        }
    }
}

/** 胶片里的一枚取值：实样 + 名字。互斥被改掉的取值画灰（它选下去也会被换掉） */
@Composable
private fun OptionChip(
    axis: Int,
    value: Int,
    slots: TtSlots,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    val dis = TtPresets.sanitize(slots.withAxis(axis, value)).axisValue(axis) != value
    Column(
        Modifier.width(sampleWidthOf(axis)).clickable(enabled = !dis, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .border(
                    if (selected) 1.5.dp else 0.75.dp,
                    if (selected) t.primary else t.outline,
                    RectangleShape,
                )
                .padding(2.dp),
        ) {
            TtAxisSample(
                axis = axis,
                value = value,
                slots = slots,
                modifier = if (dis) Modifier.alpha(0.3f) else Modifier,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            TtAxis.options[axis][value],
            fontSize = 9.sp,
            color = if (selected) t.ink else t.inkFaint,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 三个随时间的效果：小药丸，亮着 = 开 */
@Composable
private fun FxPills(ui: AppState, vm: TimetableViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.l, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FxPill("小幽灵", ui.ttCardGhost, vm::setTtCardGhost)
        FxPill("食物图标", ui.ttCardFood, vm::setTtCardFood)
        FxPill("当前课描边", ui.ttCardNowRing, vm::setTtCardNowRing)
    }
}

@Composable
private fun FxPill(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier.border(0.75.dp, if (on) t.primary else t.outline, RectangleShape)
            .clickable { onChange(!on) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(9.dp).clip(CircleShape)
                .background(if (on) t.primary else Color.Transparent)
                .then(if (on) Modifier else Modifier.border(0.75.dp, t.outline, CircleShape)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 10.5.sp, color = if (on) t.ink else t.inkFaint)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(0.75.dp).background(InkTokens.current.outline))
}
