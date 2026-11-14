package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.Reminders
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.TimetableViewModel
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
        SectionTitle("外观")
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

        // 风格：管语汇（硬朗农业 / 账页），与上面的明暗正交
        // 学院纸本已于 2026-11-14 按主人要求下线（token 代码留着，UI 不再露出）
        Spacer(Modifier.height(10.dp))
        Text(
            "硬朗农业＝黑土＋农械橘的方角网格；" +
                "账页＝没有格线，用填充色带与隔行底色分区；衬线承担标题与数字，课程是浮起的便签（圆角、软阴影本套专属）。",
            fontSize = 10.5.sp,
            lineHeight = 15.sp,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cn.dkarbon.enroute.ui.theme.StyleSet.SELECTABLE.map { style ->
                style to cn.dkarbon.enroute.ui.theme.StyleSet.name(style)
            }.forEach { (style, label) ->
                val on = ui.styleSet == style
                Box(
                    Modifier
                        .clip(RectangleShape)
                        .background(if (on) t.cardAlt else t.card)
                        .clickable { vm.setStyleSet(style) }
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

        // ── 取色（Material You，票 16 追加项②）──
        // 只有 Android 12+ 才显示这一行；低版本**整块不出现**（不给灰色不可用项）
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("壁纸取色", fontSize = 12.5.sp, color = t.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "只把主色与强调色换成壁纸色；纸白底、墨色正文、砖红警示不变。" +
                            "当前取色：" + cn.dkarbon.enroute.ui.theme.ColorSource.name(ui.colorSource),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = t.inkFaint,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        cn.dkarbon.enroute.ui.theme.ColorSource.FIXED to "固定",
                        cn.dkarbon.enroute.ui.theme.ColorSource.WALLPAPER to "壁纸",
                    ).forEach { (src, label) ->
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
                                label,
                                fontSize = 11.sp,
                                color = if (on) t.ink else t.inkFaint,
                                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
        // ── 上课提醒 ──
        SectionTitle("上课提醒")
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
                        "⚠ 还没给通知权限，提醒不会响 —— 关一次再开一次会重新请求",
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
        SectionTitle("数据")
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
                        "本机保存的密码、校园地图标注。\n" +
                        "清完像新装一样，可以重新登录。",
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
                Spacer(Modifier.height(2.dp))
                Text(
                    "每次登录/抓取的请求、状态码、响应原文都在里面，可导出成文档发我",
                    fontSize = 10.5.sp,
                    color = t.inkFaint,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = t.inkFaint,
                modifier = Modifier.size(16.dp),
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
        Text(
            "接口诊断：课表/考试/成绩的真实接口是靠探测出来的，学校若升级版本，这里能先看出来。",
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RectangleShape)
                    .background(if (ui.probing) t.cardAlt else t.card)
                    .clickable(enabled = !ui.probing) { vm.runProbe() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    if (ui.probing) "探测中…" else "开始诊断",
                    fontSize = 12.sp,
                    color = if (ui.probing) t.inkFaint else t.ink,
                )
            }
            if (ui.probeMsg.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(ui.probeMsg, fontSize = 10.sp, color = t.inkFaint)
            }
        }

        if (ui.probe.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ui.probe.forEach { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RectangleShape)
                        .background(t.card)
                        .padding(8.dp),
                ) {
                    Text(
                        "${if (p.looksLikeJson) "✅" else "·"} ${p.status}  ${p.bytes}B  ${p.path}",
                        fontSize = 9.5.sp,
                        fontFamily = NumericFont,
                        color = if (p.looksLikeJson) t.olive else t.inkFaint,
                    )
                    if (p.sample.isNotBlank()) {
                        Text(
                            p.sample,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            color = t.inkFaint,
                            maxLines = 3,
                        )
                    }
                }
            }
        }

        // ── 关于 ──
        SectionTitle("关于")
        Text("沈农在途 · SYAU En Route", fontSize = 12.sp, color = t.ink)
        Spacer(Modifier.height(3.dp))
        Text(
            "课表来自沈阳农业大学教务系统（校内网 10.2.0.47）。校外请用 JSON 导入。\n" +
                "账号密码只在你点登录的那一次使用，不写入任何文件；登录会话（cookie）" +
                "用设备密钥加密后存在本机，用于冷启动免重登 —— 「清除所有数据」会连密钥一起销毁。",
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = t.inkFaint,
        )
        Spacer(Modifier.height(10.dp))
        // 开源信息（源码地址 / 许可证 / 依赖致谢 / 数据来源 / 免责）单独一页，
        // 与其它开源软件的习惯一致：这里只留入口，细节不挤在设置页
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Space.minTouch)
                .clickable(onClick = onOpenAbout)
                .padding(vertical = 10.dp),
        ) {
            Image(
                painter = painterResource(cn.dkarbon.enroute.R.drawable.ic_github),
                contentDescription = "GitHub",
                colorFilter = ColorFilter.tint(t.ink),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text("源码 · 许可证 · 数据来源", fontSize = 12.5.sp, color = t.ink, modifier = Modifier.weight(1f))
            Text("查看 ›", fontSize = 12.sp, color = t.accent)
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
