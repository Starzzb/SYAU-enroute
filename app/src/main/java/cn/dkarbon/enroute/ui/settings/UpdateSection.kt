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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.UpdateCenter
import cn.dkarbon.enroute.data.UpdateInfo
import cn.dkarbon.enroute.data.UpdateVersions
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.primaryInk
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File


/**
 * 设置页的「检查更新」区块（票 15）。
 *
 * ## 为什么自成一个文件、自带状态
 * 更新的状态（查/下/校验/装）是**页面级临时状态**，不该塞进 `TimetableViewModel`
 * （那是课表/教务的 VM，另一个代理正在改它）。这里用 `remember` + 自己的协程作用域，
 * 设置页只留一行调用，冲突面最小。
 *
 * ## 交互契约（票 15 红线）
 * - 点「检查更新」才会联网；启动时的静默检查在 [cn.dkarbon.enroute.EnRouteApp] 里，每天最多一次
 * - 未确认前**不下载**（要用户点「下载」），下载中可**取消**
 * - 下载完只到「已就绪」，**安装必须用户再点一次**；没有「未知来源」权限就明说去哪开
 * - 所有失败都是人话（由 [UpdateCenter] 提供），**不静默**
 */
@Composable
internal fun UpdateSection() {
    val t = InkTokens.current
    val f = Form.current
    val ctx = LocalContext.current
    val center = remember { UpdateCenter(ctx) }
    val scope = rememberCoroutineScope()

    // 状态机：Idle → Checking → (Latest | Available) → Downloading → Ready/Failed
    var state by remember { mutableStateOf<Ui>(Ui.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }

    // 「立即更新」跳转（文档 §1.4 / §3.4 方案 A）：弹窗点「立即更新」→ 请求自动下载，
    // 跳到设置页时这里消费请求，用户不必再点一次「下载」。未确认前不下载的契约不变
    // —— 点「立即更新」本身就是确认。
    val downloadRequest by UpdateCenter.downloadRequest.collectAsStateWithLifecycle()

    fun beginDownload(info: UpdateInfo) {
        state = Ui.Downloading(0L, info.size)
        job = scope.launch {
            when (val r = center.download(info) { done, total ->
                state = Ui.Downloading(done, total)
            }) {
                is UpdateCenter.DownloadResult.Ready ->
                    state = Ui.Ready(r.apk, info)
                UpdateCenter.DownloadResult.Cancelled ->
                    state = Ui.Available(info, UpdateVersions.isMandatory(info, BuildConfig.VERSION_CODE.toLong()))
                is UpdateCenter.DownloadResult.Failed -> state = Ui.Error(r.message)
            }
        }
    }

    LaunchedEffect(downloadRequest) {
        val info = downloadRequest ?: return@LaunchedEffect
        if (state is Ui.Idle || state is Ui.Hint) {
            beginDownload(info)
        }
        UpdateCenter.consumeDownloadRequest()
    }

    // 进页面时把「上次检查发现的版本」用上，省一次请求（红点逻辑同源）
    LaunchedEffect(Unit) {
        if (state is Ui.Idle) {
            center.pendingUpdateName()?.let { state = Ui.Hint("上次检查发现新版本 $it") }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("检查更新", fontSize = 13.sp, color = t.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    // 数据诚实：版本号直接来自 BuildConfig，不手写
                    "当前版本 ${BuildConfig.VERSION_NAME}（build ${BuildConfig.VERSION_CODE}）",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = t.inkFaint,
                )
            }
            Spacer(Modifier.width(8.dp))
            when (val s = state) {
                is Ui.Downloading -> {
                    PillButton(text = "取消", onClick = { job?.cancel() })
                }
                is Ui.Ready -> {
                    PillButton(
                        text = "安装",
                        primary = true,
                        onClick = {
                            if (center.canInstallPackages()) {
                                runCatching { ctx.startActivity(center.installIntent(s.apk)) }
                                    .onFailure {
                                        state = Ui.Error("打不开系统安装器：${it.message ?: "未知原因"}")
                                    }
                            } else {
                                // 权限被拒：给一条可执行的路，而不是只说「失败了」
                                runCatching { ctx.startActivity(center.unknownSourcesIntent(s.apk)) }
                                state = Ui.NeedPermission("系统还没允许本 App 安装应用。请在上面打开的设置里允许「安装未知应用」。")
                            }
                        },
                    )
                }
                is Ui.Checking -> PillButton(text = "检查中…", enabled = false, onClick = {})
                is Ui.Available -> PillButton(
                    text = "下载",
                    primary = true,
                    onClick = { beginDownload(s.info) },
                )
                else -> PillButton(
                    text = "检查",
                    onClick = {
                        state = Ui.Checking
                        job = scope.launch {
                            when (val r = center.check()) {
                                is UpdateCenter.CheckResult.Latest -> state = Ui.Latest(r.note)
                                is UpdateCenter.CheckResult.Available -> state = Ui.Available(r.info, r.mandatory)
                                is UpdateCenter.CheckResult.Failed -> state = Ui.Error(r.message)
                            }
                        }
                    },
                )
            }
        }

        // ── 状态详情 ─────────────────────────────────────
        when (val s = state) {
            is Ui.Idle -> Unit
            is Ui.Hint -> StatusLine(s.text, t.inkFaint)
            is Ui.Checking -> StatusLine("正在连接服务器…", t.inkFaint)
            is Ui.Latest -> StatusLine(s.note?.let { "已是最新（$it）" } ?: "已是最新版本", t.olive)
            is Ui.Available -> {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.info.versionName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = f.numberFamily,
                        color = primaryInk(),
                    )
                    if (s.info.sizeText.isNotBlank()) {
                        Text(" · ${s.info.sizeText}", fontSize = 10.5.sp, color = t.inkFaint)
                    }
                    if (s.mandatory) {
                        Spacer(Modifier.width(6.dp))
                        Badge("建议尽快更新", t.brick)
                    }
                }
                if (s.info.notes.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        s.info.notes,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = t.inkSoft,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            is Ui.Downloading -> {
                Spacer(Modifier.height(6.dp))
                val frac = if (s.total > 0) (s.done.toFloat() / s.total).coerceIn(0f, 1f) else 0f
                if (s.total > 0) {
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(Form.current.shapes.pill),
                        color = t.ink,
                        trackColor = t.rule,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "下载中 ${(frac * 100).toInt()}% · ${fmt(s.done)} / ${fmt(s.total)}",
                        fontSize = 10.5.sp,
                        color = t.inkFaint,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = t.ink,
                        trackColor = t.rule,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("下载中 ${fmt(s.done)}（服务器没给总大小）", fontSize = 10.5.sp, color = t.inkFaint)
                }
            }
            is Ui.Ready -> {
                Spacer(Modifier.height(6.dp))
                StatusLine("已下载并通过 sha256 校验，交给系统安装器（${fmt(s.apk.length())}）", t.olive)
            }
            is Ui.Error -> {
                Spacer(Modifier.height(6.dp))
                StatusLine(s.message, t.brick)
                Spacer(Modifier.height(2.dp))
                Text(
                    "不着急的话可以稍后再试；更新只是可选动作，不影响现在的使用。",
                    fontSize = 10.sp,
                    color = t.inkFaint,
                )
            }
            is Ui.NeedPermission -> {
                Spacer(Modifier.height(6.dp))
                StatusLine(s.text, t.amber)
            }
        }
    }
}

/** 页面级状态（不进 VM，页面退出即丢；只有「有新版本」的名字落盘） */
private sealed interface Ui {
    data object Idle : Ui
    data class Hint(val text: String) : Ui
    data object Checking : Ui
    data class Latest(val note: String?) : Ui
    data class Available(val info: UpdateInfo, val mandatory: Boolean) : Ui
    data class Downloading(val done: Long, val total: Long) : Ui
    data class Ready(val apk: File, val info: UpdateInfo) : Ui
    data class Error(val message: String) : Ui
    data class NeedPermission(val text: String) : Ui
}

@Composable
private fun StatusLine(text: String, color: androidx.compose.ui.graphics.Color) {
    Spacer(Modifier.height(6.dp))
    Text(text, fontSize = 10.5.sp, lineHeight = 15.sp, color = color)
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color) {
    val badgeShape = Form.current.shapes.badge
    Box(
        Modifier
            .clip(badgeShape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.5f), badgeShape)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/**
 * 小按钮：**触区 ≥48dp**（`heightIn(min = Space.minTouch)`），但视觉上是紧凑的胶囊 ——
 * 在设置页这种密集列表里，把 48dp 都画成实心按钮会抢走注意力。
 */
@Composable
private fun PillButton(
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
            .background(if (primary) t.ink.copy(alpha = 0.06f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(1.dp, if (primary) t.ink.copy(alpha = 0.55f) else t.outline, shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp),
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

/** 字节数 → 人话体积（下载进度用）；数据诚实：0/未知不编成 0.0 MB */
private fun fmt(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%d KB".format(bytes / 1024L)
    else -> "$bytes B"
}
