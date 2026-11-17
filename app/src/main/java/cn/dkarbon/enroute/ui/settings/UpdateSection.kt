package cn.dkarbon.enroute.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
import cn.dkarbon.enroute.data.UpdateInstall
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
 * - 下载完只到「已就绪」，**安装必须用户再点一次**；没有「安装未知应用」权限时
 *   App 自己拉起那一页（`ACTION_MANAGE_UNKNOWN_APP_SOURCES`），回来自动接着装
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

    // 「安装未知应用」是**按 App** 授权的系统开关，App 勾不上它，但可以自己把人送到那一页、
    // 回来接着装：下载完发现没权限就自动拉一次，回来立刻交给安装器，用户不用再点第二下，
    // 界面上也不必写「请去设置里允许…」这种说明书（AGENTS.md）。
    var pendingApk by remember { mutableStateOf<File?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val apk = pendingApk ?: return@rememberLauncherForActivityResult
        pendingApk = null
        if (center.canInstallPackages()) {
            runCatching { ctx.startActivity(center.installIntent(apk)) }
                .onFailure { state = Ui.Error("打不开系统安装器：${it.message ?: "未知原因"}") }
        }
        // 还是没允许：什么都不说，按钮仍是「安装」，再点就再走一次
    }

    fun install(apk: File) {
        when (UpdateInstall.step(center.canInstallPackages())) {
            UpdateInstall.Step.Installer ->
                runCatching { ctx.startActivity(center.installIntent(apk)) }
                    .onFailure { state = Ui.Error("打不开系统安装器：${it.message ?: "未知原因"}") }

            UpdateInstall.Step.Settings -> {
                pendingApk = apk
                runCatching { permissionLauncher.launch(center.unknownSourcesIntent(apk)) }
                    .onFailure {
                        pendingApk = null
                        state = Ui.Error("打不开系统设置：${it.message ?: "未知原因"}")
                    }
            }
        }
    }

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
                is UpdateCenter.DownloadResult.Failed -> state = Ui.Error(r.message, info.url, helpTextFor(info, r.message))
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

    // 下载完先要权限：没有「安装未知应用」就自己把人送到那一页，回来自动接着装
    LaunchedEffect(state) {
        val s = state
        if (s is Ui.Ready && !center.canInstallPackages()) install(s.apk)
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
                        onClick = { install(s.apk) },
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
                Spacer(Modifier.height(8.dp))
                // GitHub Release 在部分网络下不通（App 内 HttpURLConnection 过不了，
                // 但系统浏览器有时能过 —— 两者的代理/TLS 栈不同）。
                // 三个出口全是动作名，不写"为什么会失败"之类的说明（AGENTS.md）。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            // 用外部浏览器打开 Release 页；连 info.url 都拿不到时退到仓库 releases 列表
                            val target = s.url.ifBlank { RELEASES_PAGE }
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text("浏览器下载", fontSize = 12.sp) }

                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            val cb = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as? android.content.ClipboardManager
                            cb?.setPrimaryClip(
                                android.content.ClipData.newPlainText("更新求助", s.helpText),
                            )
                            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text("复制求助文本", fontSize = 12.sp) }

                    Spacer(Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_PAGE))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.heightIn(min = 40.dp),
                    ) { Text("联系开发者", fontSize = 12.sp) }
                }
            }
        }
    }
}


/** 仓库 issues 页：下载不通时给用户一个能说话的入口 */
private const val ISSUES_PAGE = "https://github.com/Starzzb/SYAU-enroute/issues"
private const val RELEASES_PAGE = "https://github.com/Starzzb/SYAU-enroute/releases"

/**
 * 「求助文本」：复制到剪贴板让用户直接发给开发者。
 *
 * 带三样东西就够了 —— 从哪版到哪版、失败原因、一句话诉求。
 * 版本号**动态拼**，不写死（否则下个版本又得改）。
 */
private fun helpTextFor(info: UpdateInfo, reason: String): String = buildString {
    append("我在更新到 ${info.versionName} 时下载失败（当前 ${BuildConfig.VERSION_NAME}）。\n")
    append("失败原因：$reason\n")
    append("请给我一个可用下载地址，谢谢。")
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
    /**
     * 出错状态。
     *
     * @param message 给用户看的一句话原因
     * @param url 已知的下载地址（有就用作「浏览器下载」的目标；拿不到时退到 releases 列表页）
     * @param helpText 预填的求助文本（复制到剪贴板，用户粘给开发者）
     */
    data class Error(
        val message: String,
        val url: String = "",
        val helpText: String = "",
    ) : Ui
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
