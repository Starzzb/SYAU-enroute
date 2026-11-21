package cn.dkarbon.enroute.ui.settings

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
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
        Section("卡片形态", PaperTex.GRID) {
            SegRow(
                title = "版式",
                detail = "纸卡：侵蚀质感，讲课名压在最上；账页：横线格 + 竖脊，字距更紧",
                options = listOf("纸卡", "账页"),
                selected = ui.ttCardLayout,
                onSelect = vm::setTtCardLayout,
            )
            SegRow(
                title = "底色",
                detail = "四色：按课程号固定分色；单墨：统一次级面；无色：与页面同底",
                options = listOf("四色", "单墨", "无色"),
                selected = ui.ttCardTint,
                onSelect = vm::setTtCardTint,
            )
            SwitchRow(
                title = "左侧竖脊",
                detail = "2.5dp 满彩竖条，是卡片上唯一的高彩度面积",
                checked = ui.ttCardSpine,
                onChange = vm::setTtCardSpine,
            )
        }

        Section("随时间的效果", PaperTex.CROSS) {
            SwitchRow(
                title = "上课进度侵蚀",
                detail = "一节课走完，卡片从顶部掉色；课名永远留在最上层、照样点得开",
                checked = ui.ttCardErode,
                onChange = vm::setTtCardErode,
            )
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