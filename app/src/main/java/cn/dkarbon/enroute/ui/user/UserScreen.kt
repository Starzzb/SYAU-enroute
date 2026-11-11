package cn.dkarbon.enroute.ui.user

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.Reminders
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.academic.ExamScreen
import cn.dkarbon.enroute.ui.academic.ScoreScreen
import cn.dkarbon.enroute.ui.debug.DebugScreen
import cn.dkarbon.enroute.ui.settings.SettingsScreen
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.widget.WoodenFish
import cn.dkarbon.enroute.ui.timetable.SchoolClock
import cn.dkarbon.enroute.ui.timetable.TermCalendar
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

/** 用户页下的子页面（用一个小栈做导航，系统返回键直接可用） */
private enum class UserPage(val title: String) {
    Root("我的"),
    Exam("考试"),
    Score("成绩"),
    Settings("设置"),
    Debug("调试日志"),
    Login("登录"),
}

/**
 * 用户页 —— 底部导航只剩「课表 / 我的」两栏（2026-11-10 主人定）。
 *
 * 结构：
 * ```
 * 我的
 *  ├ 右上齿轮 → 设置
 *  │            └ 调试日志
 *  ├ 考试
 *  └ 成绩
 * ```
 * 为什么把考试/成绩塞进「我的」而不是底部：它们是**低频**页面（一学期看几次），
 * 而课表是每天十几次。底部导航给高频留位置，低频收进个人中心。
 */
@Composable
fun UserScreen(ui: AppState, vm: TimetableViewModel) {
    val stack = remember { mutableStateListOf(UserPage.Root) }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }
    val current = stack.last()

    Column(Modifier.fillMaxSize()) {
        if (current != UserPage.Root) {
            SubHeader(current.title, onBack = { stack.removeAt(stack.lastIndex) })
        }
        Box(Modifier.weight(1f)) {
            when (current) {
                UserPage.Root -> UserRoot(
                    ui = ui,
                    vm = vm,
                    onOpenSettings = { stack.add(UserPage.Settings) },
                    onOpenExam = { stack.add(UserPage.Exam) },
                    onOpenScore = { stack.add(UserPage.Score) },
                    onOpenLogin = { stack.add(UserPage.Login) },
                )
                UserPage.Exam -> ExamScreen(ui = ui, vm = vm)
                UserPage.Score -> ScoreScreen(ui = ui, vm = vm)
                UserPage.Settings -> SettingsScreen(
                    ui = ui,
                    vm = vm,
                    onOpenDebug = { stack.add(UserPage.Debug) },
                )
                UserPage.Debug -> DebugScreen()
                UserPage.Login -> LoginScreen(vm = vm, onDone = { stack.removeAt(stack.lastIndex) })
            }
        }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "返回",
                modifier = Modifier.size(22.dp),
                tint = t.inkSoft,
            )
        }
        Text(title, fontSize = 15.sp, color = t.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun UserRoot(
    ui: AppState,
    vm: TimetableViewModel,
    onOpenSettings: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenScore: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val cal = remember { TermCalendar(ctx.applicationContext) }
    val week = remember(ui.timetable) { cal.currentWeek() }
    val summer = remember { SchoolClock.isSummerTime(LocalDate.now()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ── 抬头：头像 + 姓名 + 齿轮 ──
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !ui.loggedIn, onClick = onOpenLogin)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(t.cardAlt),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ui.userName.take(1).ifBlank { "?" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ui.userName.ifBlank { if (ui.loggedIn) "已登录" else "未登录" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        !ui.loggedIn -> "点此登录"
                        ui.userName.isBlank() -> "已登录"
                        else -> "已登录 · 本次运行有效"
                    },
                    fontSize = 10.5.sp,
                    color = t.inkFaint,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "设置", tint = t.inkSoft)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 数据概览 ──
        GroupCard {
            StatRow("课表", "${ui.timetable?.courses?.size ?: 0} 门课")
            Divider()
            StatRow(
                "周次",
                when {
                    week != null -> "第 $week 周 · ${SchoolClock.hintOf(summer)}"
                    else -> "未校准（去课表页校准一次）"
                },
            )
            Divider()
            StatRow("上课提醒", if (ui.remindersEnabled) "开 · 课前 ${Reminders.LEADS_MIN.joinToString(" / ")} 分钟" else "关")
        }

        Spacer(Modifier.height(14.dp))

        // ── 子页面入口 ──
        GroupCard {
            NavRow("考试安排", if (ui.exams.isEmpty()) "—" else "${ui.exams.size} 场", onOpenExam)
            Divider()
            NavRow("成绩查询", if (ui.scores.isEmpty()) "—" else "${ui.scores.size} 条", onOpenScore)
            Divider()
            NavRow("设置", "", onOpenSettings)
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "沈农在途 ${BuildConfig.VERSION_NAME}",
            fontSize = 10.sp,
            fontFamily = NumericFont,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(18.dp))
        WoodenFish()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { content() }
}

@Composable
private fun Divider() {
    val t = InkTokens.current
    Box(Modifier.fillMaxWidth().height(0.6.dp).background(t.rule))
}

@Composable
private fun StatRow(label: String, value: String) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.5.sp, color = t.inkSoft)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 11.5.sp, color = t.ink, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NavRow(label: String, badge: String, onClick: () -> Unit) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = t.ink)
        Spacer(Modifier.weight(1f))
        if (badge.isNotBlank()) {
            Text(badge, fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = null,
            tint = t.inkFaint,
            modifier = Modifier.size(16.dp),
        )
    }
}
