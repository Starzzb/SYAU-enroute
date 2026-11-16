package cn.dkarbon.enroute.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.map.MapDataset
import cn.dkarbon.enroute.data.map.MapDatasetClient
import cn.dkarbon.enroute.data.map.MapUploader
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 设置页「参与共建」区块（票 13 阶段 B）。
 *
 * ## 2026-11-16 简化：一次同意，之后直接上云
 * 旧流程在每次导出/上传前都再确认一遍（首次弹窗 + 预览 + shouldSend 多重闸门），
 * 主人真机反馈「服务共建过于谨慎」。现在：
 * - 开关**常驻**：开 → 弹一次同意说明（如实列出会传学号）；关 → 一行状态。
 * - 之后点「导出标注」＝本地导出 + 直接上传，这里不再重复问。
 * - 另提供「拉取最新标注」入口（按需，不自动拉）。
 */
@Composable
internal fun ContributeSection(ui: AppState) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val uploader = remember { MapUploader(ctx) }
    val datasetClient = remember { MapDatasetClient(ctx) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(uploader.isEnabled()) }
    var confirmEnable by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(uploader.lastMessage()) }
    var anonKey by remember { mutableStateOf(uploader.anonymousKey()) }
    var pulling by remember { mutableStateOf(false) }
    var pullNote by remember { mutableStateOf("") }

    val mapVersion = ui.mapPackage?.manifest?.version.orEmpty()
    val sid = ui.profile.studentNo
    val hasName = ui.profile.name.isNotBlank()

    // 退避重试只在**进设置页**时触发；队列只会在「导出标注」时被填充，不会偷偷传新东西
    LaunchedEffect(enabled, mapVersion) {
        if (enabled && mapVersion.isNotBlank()) {
            uploader.uploadAsync(mapVersion, sid = sid, hasName = hasName)
            uploader.lastMessage().takeIf { it.isNotBlank() }?.let { status = it }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("参与共建", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (enabled) {
                        "已开启 · 会传：地块/字段/值、学号（加密存服务端）、机型与系统版本"
                    } else {
                        "未开启 · 导出标注只存本地，不上传"
                    },
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (!on) {
                        enabled = false
                        uploader.setEnabled(false)
                        status = "已关闭：不会再上传任何内容"
                    } else {
                        confirmEnable = true   // 每次开启都先看一遍「会传什么」（一次同意，含学号）
                    }
                },
            )
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(status, fontSize = 9.5.sp, lineHeight = 14.sp, color = t.inkSoft)
        }

        // ── 按需拉取最新标注（2026-11-16）：不自动，点了才拉 ──
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("共享标注数据", fontSize = 12.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    pullNote.ifBlank { "按需拉取服务器最新属性" },
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
            SmallPill(
                text = if (pulling) "拉取中…" else "拉取最新标注",
                enabled = !pulling && mapVersion.isNotBlank(),
                onClick = {
                    pulling = true
                    pullNote = ""
                    scope.launch {
                        val before = datasetClient.current()
                        val ds = runCatching { datasetClient.sync(mapVersion, force = true) }.getOrNull()
                        pullNote = when {
                            ds == null && datasetClient.lastError().isNotBlank() -> datasetClient.lastError()
                            ds == null -> "已是最新"
                            else -> "已更新 ${MapDataset.updatedCount(before, ds)} 条"
                        }
                        pulling = false
                    }
                },
            )
        }

        // 匿名身份管理（保留：重置身份与历史脱钩）
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("匿名身份 ${anonKey.take(13)}…", fontSize = 9.sp, color = t.inkFaint)
            Spacer(Modifier.width(6.dp))
            Text(
                "重置",
                fontSize = 9.sp,
                color = t.inkSoft,
                modifier = Modifier
                    .heightIn(min = Space.minTouch)
                    .clickable {
                        anonKey = uploader.resetAnonymousKey()
                        uploader.clearQueue()
                        status = "已换新身份：之后的上传与历史彻底脱钩"
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    if (confirmEnable) {
        MapContributeConsentDialog(
            onDismiss = { confirmEnable = false },
            onConfirm = {
                confirmEnable = false
                enabled = true
                uploader.setEnabled(true)
                status = "已开启：之后「导出标注」直接上传"
            },
        )
    }
}

/** 小按钮：视觉紧凑，触区 48dp */
@Composable
private fun SmallPill(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val t = InkTokens.current
    val f = Form.current
    val shape = f.shapes.control
    Box(
        Modifier
            .heightIn(min = Space.minTouch)
            .clip(shape)
            .background(if (primary && enabled) t.ink.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (primary && enabled) t.ink.copy(alpha = 0.55f) else t.outline, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            fontFamily = if (f.editorial) f.displayFamily else null,
            color = if (enabled) t.ink else t.inkFaint,
        )
    }
}


/**
 * 「参与共建」的开启同意弹窗（票 23 / 2026-11-16 更新）。
 *
 * 为什么单独抽出来：隐私决策点的说明必须**就地**给（AGENTS.md 的例外条款），
 * 而入口有两个 —— 设置页的开关、地图页的「开启共建」按钮。两处必须看到**同一段话**，
 * 否则会出现「一个入口讲清楚了、另一个入口糊里糊涂」。文案只在这里维护一份。
 *
 * 2026-11-16 起这段说明**如实列出会传学号**：这是知情同意的底线，不能省。
 */
@Composable
internal fun MapContributeConsentDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启「参与共建」？", fontSize = 15.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "开启后，填好的属性会随「导出标注」一并上报到学校的补丁服务，" +
                        "审核通过后其他同学也能看到。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("会传这些：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    "· 地块编号（如 f19）\n· 字段名（如 层数）\n· 你填的值\n" +
                        "· 学号（加密后只存服务端，本机不落盘）\n" +
                        "· 姓名可用标记（不传姓名本身）\n" +
                        "· 本地随机串（用于限流与归并，可重置）\n" +
                        "· 机型与系统版本（如 vivo V2301A / Android 16）\n" +
                        "· 应用级设备标识（Android ID；重置身份后即更换）",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text("不会传这些：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    "· 姓名、手机号\n" +
                        "· 定位、通讯录、相册、任何文件\n" +
                        "· 课表、成绩、登录凭据",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "随时可以在设置里关掉；关掉后一个字节都不会上传。",
                    fontSize = 10.5.sp,
                    color = InkTokens.current.inkFaint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("我了解，开启", fontSize = 13.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("先不开", fontSize = 13.sp) }
        },
    )
}
