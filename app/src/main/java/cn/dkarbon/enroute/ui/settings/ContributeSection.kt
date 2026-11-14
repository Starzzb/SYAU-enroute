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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import cn.dkarbon.enroute.data.map.MapContribute
import cn.dkarbon.enroute.data.map.MapUploader
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * 设置页「帮助完善校园地图（参与共建）」区块（票 13 阶段 B）。
 *
 * ## 三条不可协商（都用界面语言明说，不藏在文档里）
 * 1. **默认关**：开关不动，一个字节都不上传
 * 2. **只传三样**：地块 id + 字段名 + 值。**不传**姓名、学号、设备号、位置
 * 3. **传之前能看清**：首次开启要过一道「会传什么 / 不会传什么」的确认，
 *    之后还能随时点「预览」逐条看
 *
 * 上传失败不打扰：只记到队列 + 退避时间，界面上一行小字说明下次什么时候重试。
 */
@Composable
internal fun ContributeSection(ui: AppState) {
    val t = InkTokens.current
    val f = Form.current
    val ctx = LocalContext.current
    val uploader = remember { MapUploader(ctx) }
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(uploader.isEnabled()) }
    var confirmEnable by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(uploader.lastMessage()) }
    var busy by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableStateOf(uploader.pendingCount()) }
    var anonKey by remember { mutableStateOf(uploader.anonymousKey()) }

    val mapVersion = ui.mapPackage?.manifest?.version.orEmpty()

    // 退避重试的唯一触发点：**进设置页**（跟着 `enabled` 与地图版本变化）。
    // 只重试「队列里已经存在」的东西 —— 队列只会在用户手动点「立即上传」时被填充，
    // 所以这里不会变成「偷偷把新标注传上去」。
    LaunchedEffect(enabled, mapVersion) {
        if (enabled && mapVersion.isNotBlank()) {
            uploader.uploadAsync(mapVersion)   // 内部按 nextTryAt 判退避，不满足就立刻返回
            pendingCount = uploader.pendingCount()
            uploader.lastMessage().takeIf { it.isNotBlank() }?.let { status = it }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("帮助完善校园地图", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    "把你自己填的地块属性匿名上报，让下一个人也能看到。默认关闭。",
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
                    } else if (!uploader.hasAcknowledged()) {
                        confirmEnable = true          // 首次开启 → 必须先看清传什么
                    } else {
                        enabled = true
                        uploader.setEnabled(true)
                        status = "已开启"
                    }
                },
            )
        }

        // 上传内容一句话说清（关闭时也显示，让用户知道「如果开」会传什么）
        Spacer(Modifier.height(4.dp))
        Text(
            "会传：地块编号 + 字段名 + 你填的值 + 本地随机串\n" +
                "　　+ 机型与系统版本（如 vivo V2301A / Android 16）\n" +
                "　　+ 应用级设备标识（重置身份后即更换）\n" +
                "不传：姓名、学号、定位、通讯录、相册、任何文件",
            fontSize = 9.5.sp,
            lineHeight = 14.sp,
            color = t.inkFaint,
        )

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallPill("预览要传什么", onClick = { showPreview = true })
            Spacer(Modifier.width(6.dp))
            SmallPill(
                text = if (busy) "上传中…" else "立即上传",
                primary = true,
                enabled = enabled && !busy,
                onClick = {
                    if (mapVersion.isBlank()) {
                        status = "地图包还没加载出来，稍后再试"
                        return@SmallPill
                    }
                    // 把当前标注整理进队列（不入队就不传：先看后传）
                    val clean = uploader.queueFromEdits(ui.mapEdits, mapVersion)
                    pendingCount = uploader.pendingCount()
                    busy = true
                    scope.launch {
                        val r = uploader.upload(mapVersion, force = true)
                        status = when (r) {
                            is MapUploader.Outcome.Ok -> r.resp.summary()
                            is MapUploader.Outcome.Failed -> r.message
                            MapUploader.Outcome.NothingToDo -> "没有可上传的内容（先在校园地图里补几条属性）"
                        }
                        if (clean.dropped.isNotEmpty()) {
                            status += "；另有 ${clean.dropped.size} 条不合格已跳过（${clean.dropped.first().second}）"
                        }
                        pendingCount = uploader.pendingCount()
                        busy = false
                    }
                },
            )
            if (pendingCount > 0) {
                Spacer(Modifier.width(6.dp))
                Text("待传 $pendingCount 条", fontSize = 9.5.sp, color = t.amber)
            }
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(status, fontSize = 9.5.sp, lineHeight = 14.sp, color = t.inkSoft)
        }

        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "匿名身份 ${anonKey.take(13)}…",
                fontSize = 9.sp,
                color = t.inkFaint,
            )
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
                        pendingCount = 0
                        status = "已换新身份：之后的上传与历史彻底脱钩"
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }

    // ── 首次开启：把「传什么/不传什么」摆到眼前 ──
    if (confirmEnable) {
        AlertDialog(
            onDismissRequest = { confirmEnable = false },
            title = { Text("开启「参与共建」？", fontSize = 15.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "开启后，你在地图里填的属性可以手动上报到学校的补丁服务，" +
                            "审核通过后其他同学也能看到。默认不会自动上传。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("会传这些：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "· 地块编号（如 f19）\n· 字段名（如 层数）\n· 你填的值\n· 本地随机串（用于限流与归并，可重置）\n" +
                            "· 机型与系统版本（如 vivo V2301A / Android 16）\n" +
                            "· 应用级设备标识（Android ID；重置身份后即更换）",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("不会传这些：", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "· 姓名、学号、手机号\n" +
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
                TextButton(onClick = {
                    confirmEnable = false
                    enabled = true
                    uploader.setEnabled(true)
                    uploader.setAcknowledged(true)
                    status = "已开启：上传要你自己点「立即上传」"
                }) { Text("我了解，开启", fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnable = false }) { Text("先不开", fontSize = 13.sp) }
            },
        )
    }

    // ── 预览：逐条看清 ──
    if (showPreview) {
        val patches = MapContribute.patchesFrom(ui.mapEdits)
        val clean = MapContribute.sanitize(patches, mapVersion)
        val fields = clean.ok.map { it.field }.distinct()
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("将要上传的内容", fontSize = 15.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        MapContribute.preview(
                            clean.ok.size,
                            clean.ok.map { it.featureId }.distinct().size,
                            fields,
                        ),
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = InkTokens.current.inkSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    clean.ok.take(20).forEach { p ->
                        Text(
                            "${p.featureId} · ${p.field} = ${p.value.ifBlank { "（清空）" }}",
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    if (clean.ok.size > 20) {
                        Text("…还有 ${clean.ok.size - 20} 条", fontSize = 10.5.sp, color = InkTokens.current.inkFaint)
                    }
                    if (clean.dropped.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("不会上传（不合格）：", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        clean.dropped.take(5).forEach { (p, why) ->
                            Text("${p.featureId} · ${p.field}：$why", fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                    // 随附的设备信息也列出来 —— 弹窗承诺「会传什么」，这里就不能藏东西
                    val ci = uploader.clientInfo()
                    Spacer(Modifier.height(10.dp))
                    Text("随附信息（同样会上传）：", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    val device = listOf(ci.manufacturer, ci.model).filter { it.isNotBlank() }.joinToString(" ")
                    Text(
                        "· 机型：${device.ifBlank { "（取不到）" }}\n" +
                            "· 系统：Android ${ci.release.ifBlank { "?" }}（API ${ci.sdkInt}）\n" +
                            "· 应用级设备标识：" + (if (ci.ssaid.isBlank()) "（本机取不到，留空）" else ci.ssaid.take(8) + "…") + "\n" +
                            "· 本地随机串：${ci.did.take(13)}…",
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        color = InkTokens.current.inkSoft,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("知道了", fontSize = 13.sp) } },
        )
    }
}

/** 小按钮：视觉紧凑，触区 48dp（与 UpdateSection 的 PillButton 同一套说法） */
@Composable
private fun SmallPill(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val t = InkTokens.current
    val f = Form.current
    val shape = RoundedCornerShape(999.dp)
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
