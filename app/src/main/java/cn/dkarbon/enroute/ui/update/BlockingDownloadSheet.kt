package cn.dkarbon.enroute.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.UpdateCenter
import cn.dkarbon.enroute.data.UpdateInfo
import cn.dkarbon.enroute.ui.kit.PaperTex
import cn.dkarbon.enroute.ui.kit.paperTexture
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import java.io.File
import java.util.Locale

/**
 * 阻塞式下载浮层（m37）。
 *
 * 全屏纸底 —— 它不是「弹窗盖在页面上」，而是这一刻 App 只有这件事。
 * 下载中不给出口：阻塞一旦能随手关掉，就等于没阻塞；
 * 但失败必须给出路（重试 / 稍后），否则阻塞会变成卡死。
 */
@Composable
fun BlockingDownloadSheet(
    state: UpdateCenter.DownloadState,
    onInstall: (File) -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 只有失败态才放行返回键
    BackHandler(enabled = true) {
        if (state is UpdateCenter.DownloadState.Failed) onLater()
    }

    val t = InkTokens.current
    val shape = RoundedCornerShape(10.dp)

    val info: UpdateInfo? = when (state) {
        is UpdateCenter.DownloadState.Downloading -> state.info
        is UpdateCenter.DownloadState.Ready -> state.info
        is UpdateCenter.DownloadState.Failed -> state.info
        UpdateCenter.DownloadState.Idle -> null
    }
    val done: Long = (state as? UpdateCenter.DownloadState.Downloading)?.done ?: 0L
    val total: Long = (state as? UpdateCenter.DownloadState.Downloading)?.total ?: (info?.size ?: 0L)
    val frac: Float = when (state) {
        is UpdateCenter.DownloadState.Downloading ->
            if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

        is UpdateCenter.DownloadState.Ready -> 1f
        is UpdateCenter.DownloadState.Failed ->
            if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

        UpdateCenter.DownloadState.Idle -> 0f
    }

    // 速度：每秒采一次，够看就行，不做平滑
    var speed by remember { mutableLongStateOf(0L) }
    var markAt by remember { mutableLongStateOf(0L) }
    var markDone by remember { mutableLongStateOf(0L) }
    if (state is UpdateCenter.DownloadState.Downloading) {
        val now = System.currentTimeMillis()
        if (markAt == 0L) {
            markAt = now
            markDone = done
        } else if (now - markAt >= 900) {
            val gained = done - markDone
            if (gained > 0) speed = gained * 1000L / (now - markAt)
            markAt = now
            markDone = done
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(t.paper)
            .paperTexture(PaperTex.GRID, t, radius = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(shape)
                .background(t.card)
                .border(1.dp, t.outline, shape)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = when (state) {
                    is UpdateCenter.DownloadState.Downloading -> "正在下载"
                    is UpdateCenter.DownloadState.Ready -> "下载完成"
                    is UpdateCenter.DownloadState.Failed -> "下载中断"
                    UpdateCenter.DownloadState.Idle -> ""
                },
                fontSize = 9.5.sp,
                letterSpacing = 0.22.sp,
                color = if (state is UpdateCenter.DownloadState.Failed) t.brick else t.inkFaint,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = info?.let { "沈农在途 ${it.versionName}" } ?: "沈农在途",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = info?.let { "versionCode ${it.versionCode} · ${fmt(it.size)}" } ?: "",
                fontSize = 10.sp,
                color = t.inkFaint,
                fontFamily = NumericFont,
            )

            Spacer(Modifier.height(22.dp))
            ProgressRing(
                fraction = frac,
                percent = (frac * 100f).toInt(),
                failed = state is UpdateCenter.DownloadState.Failed,
                accent = if (state is UpdateCenter.DownloadState.Failed) t.brick else t.ink,
                track = t.rule,
            )
            Spacer(Modifier.height(18.dp))

            when (state) {
                is UpdateCenter.DownloadState.Downloading -> {
                    Text(
                        text = speedLine(done, total, speed),
                        fontSize = 10.5.sp,
                        color = t.inkSoft,
                        fontFamily = NumericFont,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "下载完成后 sha256 校验，不合格会自动丢弃",
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                    )
                }

                is UpdateCenter.DownloadState.Ready -> {
                    Text(
                        text = "下载完整，可以安装",
                        fontSize = 10.5.sp,
                        color = t.olive,
                    )
                    Spacer(Modifier.height(20.dp))
                    PrimaryAction(text = "安装", accent = t.ink, ink = t.paper, onClick = { onInstall(state.apk) })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "覆盖升级，保留课表与登录状态",
                        fontSize = 9.5.sp,
                        color = t.inkFaint,
                    )
                }

                is UpdateCenter.DownloadState.Failed -> {
                    Text(
                        text = state.message,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = t.inkSoft,
                    )
                    if (total > 0 && done > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "已下 ${fmt(done)} / ${fmt(total)}",
                            fontSize = 9.5.sp,
                            color = t.inkFaint,
                            fontFamily = NumericFont,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostAction(text = "稍后", modifier = Modifier.weight(1f), onClick = onLater)
                        GhostAction(text = "重试", modifier = Modifier.weight(1f), onClick = onRetry, strong = true)
                    }
                }

                UpdateCenter.DownloadState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ProgressRing(
    fraction: Float,
    percent: Int,
    failed: Boolean,
    accent: Color,
    track: Color,
) {
    val t = InkTokens.current
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2f + 1.dp.toPx()
            val side = size.minDimension - inset * 2f
            drawArc(
                color = track.copy(alpha = 0.45f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(side, side),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(side, side),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$percent",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (failed) t.brick else t.ink,
                fontFamily = NumericFont,
            )
            Text(text = "%", fontSize = 9.sp, color = t.inkFaint, fontFamily = NumericFont)
        }
    }
}

@Composable
private fun PrimaryAction(text: String, accent: Color, ink: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(accent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun GhostAction(
    text: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: () -> Unit,
) {
    val t = InkTokens.current
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .border(1.dp, if (strong) t.ink else t.outline, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (strong) t.ink else t.inkSoft,
        )
    }
}

private fun fmt(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
}

private fun speedLine(done: Long, total: Long, speed: Long): String {
    val parts = mutableListOf<String>()
    if (total > 0) parts += "${fmt(done)} / ${fmt(total)}"
    if (speed > 0) {
        parts += if (speed >= 1024 * 1024) {
            String.format(Locale.CHINA, "%.1f MB/s", speed / 1024.0 / 1024.0)
        } else {
            "${speed / 1024} KB/s"
        }
        if (total > done) {
            val secs = (total - done) / speed
            parts += if (secs >= 60) "约 ${secs / 60} 分" else "约 ${secs.coerceAtLeast(1)} 秒"
        }
    }
    return parts.joinToString(" · ")
}
