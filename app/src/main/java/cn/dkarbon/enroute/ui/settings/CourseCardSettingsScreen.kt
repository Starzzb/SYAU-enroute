package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.kit.PaperTex
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 设置 →「课程表卡片」。
 *
 * ## 这一页为什么存在（m39，2026-11-21）
 *
 * m34 把「主题风格」开关拔掉之后，`Form.current.editorial` 那条分支**还在编进包里跑**：
 * `LedgerCourseCell / LedgerAxisRow / LedgerBand / LedgerWeekStamp / LedgerUnscheduledRow`
 * 五个组件（`ui/timetable/LedgerTimetable.kt`，326 行）从来没被删除，课表卡片也照常加载，
 * 只是**没有任何入口能切过去**。这一页把那套设计接回界面，顺手把原本写死的四种效果也做成开关。
 *
 * ## 默认值口径
 *
 * 每一项的默认值都等于改造前的实际行为 —— 老用户升级后进来什么都不点，课表一个像素不变。
 * 设置在 `Prefs`（`tt_card_*`），改动即时推给 `TtCardPrefs` 让网格重绘。
 */
@Composable
fun CourseCardSettingsScreen(
    ui: AppState,
    vm: TimetableViewModel,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = Space.l, vertical = Space.s),
    ) {
        Section("版式", PaperTex.GRID) {
            SegRow(
                title = "课表主体",
                detail = "纸卡：格子自绘；账页：横线格 + 竖脊的另一套主体，一屏多一节",
                options = listOf("纸卡", "账页"),
                selected = ui.ttCardLayout,
                onSelect = vm::setTtCardLayout,
            )
        }

        Section("预设", PaperTex.CROSS) {
            Column(Modifier.fillMaxWidth()) {
                cn.dkarbon.enroute.ui.timetable.TtPresets.ALL.forEachIndexed { i, preset ->
                    PresetRow(
                        preset = preset,
                        selected = ui.ttPreset == i,
                        onClick = { vm.setTtPreset(i) },
                    )
                }
            }
        }

        Section("逐条覆盖", PaperTex.DOTS) {
            // 11 条轴各自可选「跟随预设」：换预设只松开这一次钉死的那几条，
            // 其余仍听预设。轴的定义在 TtAxis —— 设置页、ViewModel、渲染层共用一份。
            for (axis in 0 until cn.dkarbon.enroute.ui.timetable.TtAxis.sizes.size) {
                val ov = overrideOf(ui, axis)
                val options = listOf("跟随") + cn.dkarbon.enroute.ui.timetable.TtAxis.options[axis].toList()
                SegRow(
                    title = cn.dkarbon.enroute.ui.timetable.TtAxis.names[axis],
                    detail = cn.dkarbon.enroute.ui.timetable.TtAxis.options[axis].joinToString(" / "),
                    options = options,
                    selected = ov + 1,
                    onSelect = { vm.setTtOverride(axis, if (it == 0) -1 else it - 1) },
                )
            }
        }

        Section("随时间的效果", PaperTex.CROSS) {
            SwitchRow(
                title = "小幽灵",
                detail = "沿当前时刻推进，走过哪节课就吃掉哪个格子",
                checked = ui.ttCardGhost,
                onChange = vm::setTtCardGhost,
            )
            SwitchRow(
                title = "食物图标",
                detail = "未排课列表左侧的小图标；关掉后只留文字",
                checked = ui.ttCardFood,
                onChange = vm::setTtCardFood,
            )
            SwitchRow(
                title = "当前课描边",
                detail = "正在上的那节加 2dp 主色描边",
                checked = ui.ttCardNowRing,
                onChange = vm::setTtCardNowRing,
            )
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

/** 读某条轴的覆盖值（-1 = 跟随预设） */
private fun overrideOf(ui: AppState, axis: Int): Int = when (axis) {
    cn.dkarbon.enroute.ui.timetable.TtAxis.SHAPE -> ui.ttOvShape
    cn.dkarbon.enroute.ui.timetable.TtAxis.DECOR -> ui.ttOvDecor
    cn.dkarbon.enroute.ui.timetable.TtAxis.META -> ui.ttOvMeta
    cn.dkarbon.enroute.ui.timetable.TtAxis.BADGE -> ui.ttOvBadge
    cn.dkarbon.enroute.ui.timetable.TtAxis.EROSION -> ui.ttOvErosion
    cn.dkarbon.enroute.ui.timetable.TtAxis.MOTION -> ui.ttOvMotion
    cn.dkarbon.enroute.ui.timetable.TtAxis.TONE -> ui.ttOvTone
    cn.dkarbon.enroute.ui.timetable.TtAxis.HEADER -> ui.ttOvHeader
    cn.dkarbon.enroute.ui.timetable.TtAxis.GRID -> ui.ttOvGrid
    cn.dkarbon.enroute.ui.timetable.TtAxis.BACKDROP -> ui.ttOvBackdrop
    else -> ui.ttOvBgMotion
}

/**
 * 预设行：左边一枚**真的按槽位画的**小样 —— 不是色块示意。
 *
 * 用真槽位画而不是截图：预设改了颜色/形状，这一行自动跟着变，
 * 不会出现「设置页说纸卡、课表里是标签」这种自相矛盾。
 */
@Composable
private fun PresetRow(
    preset: cn.dkarbon.enroute.ui.timetable.TtPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) t.primary.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetSwatch(preset.slots)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(preset.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = t.ink)
            Text(
                preset.note,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = t.inkFaint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 选中态不靠文字：一条墨色竖标
        Box(
            Modifier
                .width(2.dp)
                .height(28.dp)
                .background(if (selected) t.primary else Color.Transparent),
        )
    }
}

/** 45×52 的小样：形状 / 装饰 / 字段 / 底色四条轴照真值画 */
@Composable
private fun PresetSwatch(slots: cn.dkarbon.enroute.ui.timetable.TtSlots) {
    val t = InkTokens.current
    val shape = cn.dkarbon.enroute.ui.timetable.shapeOf(slots.shape)
    val fill = when (slots.tone) {
        1 -> t.cardAlt
        2 -> Color.Transparent
        else -> t.primary.copy(alpha = 0.16f)
    }
    val ink = when (slots.tone) { 2 -> t.inkSoft; else -> t.ink }
    Box(
        Modifier
            .width(45.dp)
            .height(52.dp)
            .clip(shape)
            .background(fill)
            .border(0.75.dp, if (slots.shape == 2 || slots.tone == 2) t.outline else t.primary.copy(alpha = 0.35f), shape),
    ) {
        if (slots.decor == 1) {
            Box(Modifier.width(2.5.dp).fillMaxHeight().background(t.primary.copy(alpha = 0.7f)))
        }
        Column(Modifier.padding(start = if (slots.decor == 1) 5.dp else 5.dp, top = 4.dp, end = 3.dp)) {
            // 三条字条代表字段行数：只课名就一条，三行就三条
            val lines = when (slots.meta) { 3 -> 1; 1 -> 2; 2 -> 3; else -> 3 }
            repeat(lines) { i ->
                Box(
                    Modifier
                        .padding(top = if (i == 0) 0.dp else 2.5.dp)
                        .fillMaxWidth(if (i == 0) 1f else 0.66f)
                        .height(if (i == 0) 3.dp else 2.dp)
                        .background(ink.copy(alpha = if (i == 0) 0.85f else 0.35f)),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, tex: PaperTex, content: @Composable () -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier.fillMaxWidth().padding(top = Space.l),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            title,
            fontSize = 10.sp,
            color = t.inkFaint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Space.xs),
        )
        Column(
            Modifier.fillMaxWidth().background(t.card).border(Stroke.hairline, t.outline),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) { content() }
    }
}

@Composable
private fun SegRow(
    title: String,
    detail: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = t.ink)
            Spacer(Modifier.height(2.dp))
            Text(detail, fontSize = 10.sp, lineHeight = 15.sp, color = t.inkFaint)
        }
        Spacer(Modifier.width(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier.heightIn(min = 32.dp).clip(RectangleShape)
                        .background(if (on) t.cardAlt else t.card)
                        .border(if (on) 1.5.dp else Stroke.hairline, if (on) t.primary else t.outline)
                        .clickable { onSelect(i) }
                        .padding(horizontal = Space.s, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (on) t.ink else t.inkSoft,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = t.ink)
            Spacer(Modifier.height(2.dp))
            Text(detail, fontSize = 10.sp, lineHeight = 15.sp, color = t.inkFaint)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}