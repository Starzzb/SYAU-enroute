package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Reminders
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.academic.ProbePanel
import cn.dkarbon.enroute.ui.kit.PaperTex
import cn.dkarbon.enroute.ui.kit.paperTexture
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space

/**
 * 设置（从「我的」页右上齿轮进入）。
 *
 * 五块：外观 / 上课提醒 / 数据清理 / 调试 / 关于。
 *
 * 「清除所有数据」是 2026-11-10 主人要的：登录态、课表、周次校准、提醒任务、
 * 调试日志分散在好几处，只清课表会留下「周次还记着上次的」「提醒还在响」这类鬼问题。
 * 所以这里做的是**全清**，并且要二次确认。
 */
@Composable
fun SettingsScreen(
    ui: AppState,
    vm: TimetableViewModel,
    onOpenDebug: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    var confirmClearTimetable by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val canNotify = remember(ui.remindersEnabled) { Reminders.canNotify(ctx) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // ── 外观 ──
        SettingsSection("外观", PaperTex.GRID) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色").forEach { (mode, label) ->
                val on = ui.themeMode == mode
                Box(
                    Modifier
                        .clip(RectangleShape)
                        .background(if (on) t.cardAlt else t.card)
                        .clickable { vm.setThemeMode(mode) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        label,
                        fontSize = 11.5.sp,
                        color = if (on) t.ink else t.inkFaint,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }

        // 主题风格切换已下线（2026-11-20，m34 墨洗改版）：只保留一套 INK，明暗正交。
        // 明暗由上面的三档控制；SCHOLAR / EDITORIAL 的 token 代码保留，UI 不再露出。

        // ── 取色（票 25：固定 / 壁纸 / 自定义）──
        // AGENTS.md 界面文案约定：按钮上只放名字，解释进点开后的面板/弹窗。
        Spacer(Modifier.height(10.dp))
        val darkEff = when (ui.themeMode) {
            1 -> false
            2 -> true
            else -> androidx.compose.foundation.isSystemInDarkTheme()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("取色", fontSize = 12.5.sp, color = t.ink)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val sources = buildList {
                    add(cn.dkarbon.enroute.ui.theme.ColorSource.FIXED)
                    // 壁纸取色只在 Android 12+ 露出（低版本给了也不生效，不给灰色项）
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        add(cn.dkarbon.enroute.ui.theme.ColorSource.WALLPAPER)
                    }
                    add(cn.dkarbon.enroute.ui.theme.ColorSource.CUSTOM)
                }
                sources.forEach { src ->
                    val on = ui.colorSource == src
                    Box(
                        Modifier
                            .clip(RectangleShape)
                            .background(if (on) t.cardAlt else t.card)
                            .border(if (on) 1.5.dp else 0.dp, if (on) t.primary else t.card)
                            .clickable { vm.setColorSource(src) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            cn.dkarbon.enroute.ui.theme.ColorSource.shortName(src),
                            fontSize = 11.sp,
                            color = if (on) t.ink else t.inkFaint,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        // 状态一行：只说"现在是什么"，不讲道理
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                ui.colorSource != cn.dkarbon.enroute.ui.theme.ColorSource.CUSTOM -> "农业色板"
                !ui.customPalette.complete(darkEff) -> "自定义 · 这一套还没设，暂用主题色"
                else -> "自定义 · 浅色与深色各存一套"
            },
            fontSize = 10.5.sp,
            color = t.inkFaint,
        )
        // 自定义展开调色区（预设 + 微调 + 对比度闸门）
        if (ui.colorSource == cn.dkarbon.enroute.ui.theme.ColorSource.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            ColorTunerPanel(
                saved = ui.customPalette,
                styleSet = ui.styleSet,
                startDark = darkEff,
                onSave = { p ->
                    vm.setCustomPalette(p)
                    vm.setColorSource(cn.dkarbon.enroute.ui.theme.ColorSource.CUSTOM)
                },
            )
        }
        // ── 上课提醒 ──
        }

        SettingsSection("上课提醒", PaperTex.CROSS) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("课前提醒", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    "课前 ${Reminders.LEADS_MIN.joinToString(" / ")} 分钟各提醒一次",
                    fontSize = 10.5.sp,
                    color = t.inkFaint,
                )
                if (ui.remindersEnabled && !canNotify) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚠ 还没给通知权限，提醒不会响",
                        fontSize = 10.sp,
                        color = t.brick,
                    )
                }
            }
            Switch(
                checked = ui.remindersEnabled,
                onCheckedChange = { vm.setRemindersEnabled(it) },
            )
        }

        // ── 数据 ──
        }

        SettingsSection("数据", PaperTex.WEAVE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("已载入 ${ui.timetable?.courses?.size ?: 0} 门课", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                // 三态分开说（票 04）：过期就说过期，不混成一句「未登录」
                Text(
                    SessionText.settingsLine(ui.sessionState),
                    fontSize = 10.5.sp,
                    color = if (ui.sessionState == SessionState.Expired) t.brick else t.inkFaint,
                )
            }
            TextButton(onClick = { confirmClearTimetable = true }) {
                Text("只清课表", fontSize = 12.sp, color = t.inkSoft)
            }
        }
        if (confirmClearTimetable) {
            ConfirmRow(
                text = "清空课表和已排提醒？",
                onCancel = { confirmClearTimetable = false },
                onConfirm = { vm.clearTimetable(); confirmClearTimetable = false },
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── 保持登录（票 14）──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("保持登录（自动续期）", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    ui.credStatus.ifBlank { if (ui.credEnabled) "已开启" else "未开启" },
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
            Switch(
                checked = ui.credEnabled,
                onCheckedChange = { on ->
                    if (on) {
                        // 打开：没有当次登录凭据就提示去登录一次（绝不凭空编凭据）
                        vm.enableKeepLogin()
                    } else {
                        vm.disableKeepLogin()
                    }
                },
            )
        }
        if (ui.credEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("清除本机保存的密码", fontSize = 12.sp, color = t.brick)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "立刻删除本机密文与设备密钥，之后会话过期要手动登录一次。",
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = t.inkFaint,
                    )
                }
                TextButton(onClick = { vm.clearSavedPassword() }) {
                    Text("清除", fontSize = 12.sp, color = t.brick)
                }
            }
        }
        if (ui.reloginMsg.isNotBlank()) {
            Text(
                ui.reloginMsg,
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = t.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── 全部清除 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("清除所有数据", fontSize = 13.sp, color = t.brick)
                Spacer(Modifier.height(2.dp))
                Text(
                    "课表、周次校准、主题、提醒开关、已排提醒、调试日志、登录会话（含加密存档与密钥）、" +
                        "本机保存的密码、校园地图标注。",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
            TextButton(onClick = { confirmWipe = true }) {
                Text("清除", fontSize = 12.sp, color = t.brick)
            }
        }
        if (confirmWipe) {
            ConfirmRow(
                text = "确定清除全部？不可恢复。",
                onCancel = { confirmWipe = false },
                onConfirm = { vm.wipeAllData(); confirmWipe = false },
            )
        }
        if (ui.wipeMsg.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "已清除：${ui.wipeMsg}",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = t.olive,
            )
        }

        // ── 调试 ──
        }

        SectionTitle("调试")
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDebug)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("调试日志", fontSize = 13.sp, color = t.ink)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = t.inkFaint,
                modifier = Modifier.size(16.dp),
            )
        }

        // 票 20 / D2：日志落盘开关 + 占用状态（落盘 = 崩溃/被杀也能事后看，默认开）
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("保存日志到本机", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (ui.debugFileEnabled) {
                        ui.debugFileUsage.ifBlank { "已开启 · 保留 7 天 · 上限 2MB" } + "（只存本机，不上传）"
                    } else {
                        "已关闭 · 日志只留在内存，退出即丢"
                    },
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
            androidx.compose.material3.Switch(
                checked = ui.debugFileEnabled,
                onCheckedChange = { vm.setDebugLogToFile(it) },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "清空历史日志",
                fontSize = 10.5.sp,
                color = t.inkFaint,
                modifier = Modifier.weight(1f),
            )
            Text(
                "清空",
                fontSize = 12.sp,
                color = t.brick,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clickable { vm.clearDebugHistory() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        // 示例数据入口：只在 debug 源码集存在（release 变体 available = false，这里整块不显示）
        if (cn.dkarbon.enroute.data.AcademicSample.available) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("载入示例考试 / 成绩", fontSize = 13.sp, color = t.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "示例数据（仅调试包）：真实抓包的 4 场考试 + 2 条成绩。" +
                            "考试/成绩接口只在校内网可用，这个入口用来在没网时自查版式。",
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = t.inkFaint,
                    )
                }
                TextButton(onClick = { vm.loadAcademicSample() }) {
                    Text("载入", fontSize = 12.sp, color = t.inkSoft)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ProbePanel(t = t, ui = ui, onRun = { vm.runProbe() })

        // ── 关于 ──
        // 票 26 ②：这一块只留**功能**，长文字搬家到独立页 AboutScreen。
        // 理由（AGENTS.md「按钮不自带说明书」+ 单一来源）：同一句「数据从哪来」原来
        // 设置页和关于页各写一份，改一处必忘另一处。
        SectionTitle("关于")
        // 应用内更新（票 15）：查 latest.json → 下载 → sha256 校验 → 交系统安装器。
        // 只在用户点「检查」时联网；启动时的静默检查每天最多一次（见 EnRouteApp）。
        UpdateSection()
        Spacer(Modifier.height(10.dp))
        // 地图共建（票 13 阶段 B）：默认关，开启需先看「传什么/不传什么」，上传要手动点
        ContributeSection(ui)
        Spacer(Modifier.height(10.dp))
        // 独立页入口 —— 设置页的最后一行，就这一行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.minTouch)
                .clickable(onClick = onOpenAbout),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("关于沈农在途", fontSize = 13.sp, color = t.ink, modifier = Modifier.weight(1f))
            Text("›", fontSize = 14.sp, color = t.inkFaint)
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ConfirmRow(text: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val t = InkTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = 11.sp, color = t.brick)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) { Text("取消", fontSize = 12.sp) }
        TextButton(onClick = onConfirm) { Text("确定", fontSize = 12.sp, color = t.brick) }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    tex: PaperTex,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = InkTokens.current
    Spacer(Modifier.height(22.dp))
    Text(
        title,
        fontSize = 9.sp,
        letterSpacing = 0.2.em,
        color = t.inkFaint,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .paperTexture(tex = tex, t = t, radius = 4.dp)
            .border(1.dp, t.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    val t = InkTokens.current
    Spacer(Modifier.height(20.dp))
    Text(
        text,
        fontSize = 10.5.sp,
        color = t.inkFaint,
        letterSpacing = 0.6.sp,
    )
    Spacer(Modifier.height(7.dp))
}
