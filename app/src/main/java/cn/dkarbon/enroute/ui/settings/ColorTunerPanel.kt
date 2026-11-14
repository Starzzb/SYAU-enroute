package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.ui.theme.ContrastCheck
import cn.dkarbon.enroute.ui.theme.ContrastRules
import cn.dkarbon.enroute.ui.theme.CustomPalette
import cn.dkarbon.enroute.ui.theme.HslTune
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.PaletteGate
import cn.dkarbon.enroute.ui.theme.PaletteParams
import cn.dkarbon.enroute.ui.theme.PalettePresets
import cn.dkarbon.enroute.ui.theme.baseTokenSet
import cn.dkarbon.enroute.ui.theme.toArgbInt

/**
 * 自定义配色面板（票 25）：**预设色板 + 明度/饱和度微调 + 对比度闸门**。
 *
 * ## 为什么不是拾色器
 * 见 `CustomPalette.kt` 文件头。一句话：自由取色十分钟就能做出「正文看不清」的主题，
 * 而用户不会认为那是自己选的色的问题。所以只给既有色阶的预设，微调也只放开明度/饱和度。
 *
 * ## 布局（360dp 宽，八块色卡一行放得下）
 * ```
 * 编辑 [浅色] [深色]                          ← 浅色/深色各存一套
 * 主色   ■ ■ ■ ■ ■ ■ ■ ■                      ← 8 个预设
 *        明度 ──●────  饱和度 ──●────
 * 强调色 ■ ■ ■ ■ ■ ■ ■ ■
 *        明度 ──●────  饱和度 ──●────
 * ┌───────────────────────────────┐
 * │ 纸底 / 主色文字 / 主按钮 / 强调条 │          ← 实时预览条
 * └───────────────────────────────┘
 * 主色/纸底 3.90:1 未达 4.5        为什么 ›      ← 只报不达标的那一对
 * [恢复默认]            [应用并保存]
 * ```
 *
 * ## 保存语义
 * 点「应用并保存」才写盘：**不达标直接禁用**（禁用原因就写在上面那行），
 * 所以不存在"先存进去再发现看不清"的路径。
 */
@Composable
fun ColorTunerPanel(
    saved: CustomPalette,
    styleSet: Int,
    /** 打开面板时默认编辑哪一套（跟随当前生效的明暗） */
    startDark: Boolean,
    onSave: (CustomPalette) -> Unit,
) {
    val t = InkTokens.current
    var isDark by remember { mutableStateOf(startDark) }

    // 当前编辑的那一套：初始值取已保存的槽，没有就用预设第一档
    fun savedPrimary(dark: Boolean): Color =
        saved.primary(dark) ?: PalettePresets.defaultPrimary(dark)

    fun savedAccent(dark: Boolean): Color =
        saved.accent(dark) ?: PalettePresets.defaultAccent(dark)

    /*
     * 草稿状态只用 remember（不进 Bundle）：这是一个临时编辑器，
     * 旋转/进程重建时回到「已保存值」是可接受的语义 —— 相反，把一堆 Float 塞进
     * rememberSaveable 需要额外的 Saver，出错面更大。
     * 切浅色/深色时由 SlotChip 显式重载槽值与滑条（不用 key() 的隐式重置）。
     */
    var basePrimary by remember { mutableStateOf(savedPrimary(startDark).toArgbInt()) }
    var baseAccent by remember { mutableStateOf(savedAccent(startDark).toArgbInt()) }
    // 微调量归一化到 -1..1，实际幅度由 HslTune 的上下限决定
    var lightShiftP by remember { mutableStateOf(0f) }
    var satShiftP by remember { mutableStateOf(0f) }
    var lightShiftA by remember { mutableStateOf(0f) }
    var satShiftA by remember { mutableStateOf(0f) }
    var showWhy by remember { mutableStateOf(false) }

    // 换槽：色值与滑条一起归位（换的是色，不是接着上一套的微调）
    val switchSlot: (Boolean) -> Unit = { dark ->
        isDark = dark
        basePrimary = savedPrimary(dark).toArgbInt()
        baseAccent = savedAccent(dark).toArgbInt()
        lightShiftP = 0f; satShiftP = 0f; lightShiftA = 0f; satShiftA = 0f
    }

    val primary = HslTune.tune(Color(basePrimary), lightShiftP * HslTune.LIGHT_RANGE, satShiftP * HslTune.SAT_RANGE)
    val accent = HslTune.tune(Color(baseAccent), lightShiftA * HslTune.LIGHT_RANGE, satShiftA * HslTune.SAT_RANGE)

    // 校验用的底色板：**被编辑的那一套**（浅色纸白 / 深色墨底），与当前界面明暗无关
    val base = baseTokenSet(styleSet, isDark)
    val checks = PaletteGate.checks(base, primary, accent)
    val failures = checks.filter { !it.pass }
    val canSave = failures.isEmpty()

    val params: PaletteParams = PalettePresets.params(isDark)

    Column(Modifier.fillMaxWidth()) {
        // ── 编辑哪一套 ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("编辑", fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.width(6.dp))
            SlotChip("浅色", !isDark) { switchSlot(false) }
            Spacer(Modifier.width(6.dp))
            SlotChip("深色", isDark) { switchSlot(true) }
        }
        Spacer(Modifier.height(8.dp))

        // ── 主色 ──
        RowLabel("主色")
        SwatchRow(
            presets = params.primary,
            selected = basePrimary,
            onPick = { basePrimary = it.toArgbInt(); lightShiftP = 0f; satShiftP = 0f },
        )
        Spacer(Modifier.height(2.dp))
        TunerRow(
            label = "明度",
            value = lightShiftP,
            onChange = { lightShiftP = it },
            sample = primary,
        )
        TunerRow(
            label = "饱和度",
            value = satShiftP,
            onChange = { satShiftP = it },
            sample = primary,
        )

        Spacer(Modifier.height(10.dp))

        // ── 强调色 ──
        RowLabel("强调色")
        SwatchRow(
            presets = params.accent,
            selected = baseAccent,
            onPick = { baseAccent = it.toArgbInt(); lightShiftA = 0f; satShiftA = 0f },
        )
        Spacer(Modifier.height(2.dp))
        TunerRow("明度", lightShiftA, { lightShiftA = it }, accent)
        TunerRow("饱和度", satShiftA, { satShiftA = it }, accent)

        Spacer(Modifier.height(10.dp))

        // ── 实时预览条：纸底上排主色文字 + 主按钮 + 强调线 ──
        PreviewStrip(base = base, primary = primary, accent = accent)

        Spacer(Modifier.height(6.dp))

        // ── 校验结果：只报不达标项（达标就一行绿字，不刷屏）──
        if (failures.isEmpty()) {
            Text(
                "对比度达标 · 主色 ${ContrastRules.fmt(checks[0].ratio)}:1 · " +
                    "强调 ${ContrastRules.fmt(checks[3].ratio)}:1",
                fontSize = 10.5.sp,
                color = t.olive,
            )
        } else {
            failures.forEach { f ->
                Text(
                    "${f.text} 未达 ${ContrastRules.fmt(f.min)}:1",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.brick,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showWhy = true }, modifier = Modifier.height(36.dp)) {
                Text("为什么", fontSize = 11.sp, color = t.inkSoft)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    onSave(saved.cleared(isDark))
                    basePrimary = savedPrimary(isDark).toArgbInt()
                    baseAccent = savedAccent(isDark).toArgbInt()
                    lightShiftP = 0f; satShiftP = 0f; lightShiftA = 0f; satShiftA = 0f
                },
                modifier = Modifier.height(36.dp),
            ) {
                Text("恢复默认", fontSize = 11.sp, color = t.inkSoft)
            }
            Spacer(Modifier.width(4.dp))
            SaveButton(enabled = canSave) {
                onSave(saved.withMode(isDark, primary, accent))
            }
        }
    }

    if (showWhy) {
        ContrastWhyDialog(checks = checks, isDark = isDark, onDismiss = { showWhy = false })
    }
}

// ── 小组件 ────────────────────────────────────────────────

@Composable
private fun RowLabel(text: String) {
    val t = InkTokens.current
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = t.inkSoft)
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun SlotChip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(0.dp))
            .background(if (on) t.primary else t.card)
            .border(1.dp, if (on) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) (if (t.isDark) Color(0xFF100E0B) else Color.White) else t.inkSoft,
        )
    }
}

/**
 * 一行预设色块。触区 48dp、视觉 32dp —— 色块不能只靠颜色表达（读屏用 `contentDescription`）。
 */
@Composable
private fun SwatchRow(
    presets: List<cn.dkarbon.enroute.ui.theme.PalettePreset>,
    selected: Int,
    onPick: (Color) -> Unit,
) {
    val t = InkTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        presets.forEach { p ->
            val on = p.color.toArgbInt() == selected
            Box(
                Modifier
                    .size(38.dp)
                    .semantics { contentDescription = "${p.name}${if (on) "，已选中" else ""}" }
                    .clickable { onPick(p.color) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (on) 28.dp else 24.dp)
                        .clip(RoundedCornerShape(0.dp))
                        .background(p.color)
                        .border(
                            if (on) 2.dp else 1.dp,
                            if (on) t.ink else t.outline.copy(alpha = 0.55f),
                        ),
                )
            }
        }
    }
}

/** 一条滑条：`明度 ────●────` + 右侧当前值的色块，一眼看出调的是哪个色 */
@Composable
private fun TunerRow(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    sample: Color,
) {
    val t = InkTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = t.inkFaint, modifier = Modifier.width(34.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            modifier = Modifier.weight(1f).height(32.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(14.dp)
                .background(sample)
                .border(1.dp, t.outline),
        )
    }
}

/** 实时预览：纸底 + 主色文字 + 主按钮（白字）+ 强调色条 */
@Composable
private fun PreviewStrip(base: cn.dkarbon.enroute.ui.theme.InkTokenSet, primary: Color, accent: Color) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(base.paper)
            .border(1.dp, t.outline)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("本周", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primary)
        Spacer(Modifier.width(10.dp))
        // 主按钮：实心底 + 反白字（第 3 条校验就是它）
        Box(Modifier.background(primary).padding(horizontal = 9.dp, vertical = 4.dp)) {
            Text(
                "按钮",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (base.isDark) base.paper else Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(18.dp).height(3.dp).background(accent))
        Spacer(Modifier.weight(1f))
        Text(
            ContrastRules.fmt(ContrastRules.ratio(primary, base.paper)),
            fontSize = 10.sp,
            color = base.inkFaint,
        )
    }
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(0.dp))
            .background(if (enabled) t.primary else t.cardAlt)
            .border(1.dp, if (enabled) t.primary else t.outline)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enabled) "应用并保存" else "不可保存",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) (if (t.isDark) Color(0xFF100E0B) else Color.White) else t.inkFaint,
        )
    }
}

/**
 * 「为什么」弹窗：五条对比关系逐条列出实测值。
 *
 * 按 AGENTS.md 的界面文案约定：解释性长文进弹窗，列表里只留名字与状态。
 */
@Composable
private fun ContrastWhyDialog(checks: List<ContrastCheck>, isDark: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("对比度怎么算的", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "自定义色会自动算这五对。任意一对不达标就不给保存 —— " +
                        "不是审美问题：正文看不清等于信息没送达。",
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "当前编辑：${if (isDark) "深色" else "浅色"}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                checks.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(if (c.pass) Color(0xFF3F5A2E) else Color(0xFF9B2417)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${c.text}（门槛 ${ContrastRules.fmt(c.min)}:1）",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "公式按 WCAG 2.1 相对亮度：正文 4.5:1，大字与图形 3:1。",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了", fontSize = 12.sp) } },
    )
}
