package cn.dkarbon.enroute.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import android.widget.Toast
import cn.dkarbon.enroute.data.DebugLog
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志页（从「我的 → 设置 → 调试日志」进入，不在底部导航里）。
 *
 * 两个出口（2026-11-10 主人要求）：
 *   · **导出文档** —— 走系统「另存为」，落一个 `.txt` 到你自己选的位置，能发微信/邮件
 *   · 复制到剪贴板 —— 想直接粘贴时用
 *
 * 看几个典型特征：
 *   · `不可达 / Connect failed / timeout` → 没连校园网（10.2.0.47 是内网）
 *   · `302 → .../gotoLogin` → 登录态没建立（CAS 回跳没成功）
 *   · `200` 但响应含 `loginForm` → 账号密码不对
 *   · 成绩那条 `token=未找到` → 学校改版了，把这份日志发我
 */
@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    onSelftest: () -> Unit = {},
    /** 传进来才显示「落盘 xxKB」与「清空历史」；没传（老调用点）行为不变 */
    ui: cn.dkarbon.enroute.ui.AppState? = null,
    onClearHistory: () -> Unit = {},
    /** 「全部导出」的文件名（带日期范围）；空串 = 不显示这个按钮 */
    exportAllName: () -> String = { "" },
    /** 「全部导出」的正文（**VM 侧已再脱敏一遍**） */
    exportAllText: () -> String = { "" },
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val allEntries by DebugLog.entries.collectAsState()
    val errorEntries by DebugLog.errors.collectAsState()
    val listState = rememberLazyListState()
    var lastSave by remember { mutableStateOf("") }

    // 票 20 / D3：**过滤只在读取侧做**，写入侧不丢数据。
    // 「仅错误」直接读 DebugLog 的 ERROR 保底缓冲（最近 50 条），不受 400 条主缓冲滚动影响。
    var onlyErrors by rememberSaveable { mutableStateOf(false) }
    var warnPlus by rememberSaveable { mutableStateOf(false) }
    val entries = when {
        onlyErrors -> errorEntries
        warnPlus -> allEntries.filter { it.level != DebugLog.Level.DEBUG && it.level != DebugLog.Level.INFO }
        else -> allEntries
    }

    // 系统「另存为」：用户自己选目录，App 不需要存储权限
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(DebugLog.dump().toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        lastSave = if (ok) "已导出到所选位置" else "导出失败"
        Toast.makeText(ctx, lastSave, Toast.LENGTH_SHORT).show()
    }

    // 「全部导出」：7 天落盘日志打包成一个 txt，仍走系统「另存为」——
    // 与上面的「导出文档」完全同一套 SAF 写法，不新造存储路径、不需要存储权限。
    val exportAllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(exportAllText().toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        val msg = if (ok) "已导出全部历史日志" else "导出失败（没写进去）"
        lastSave = msg
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("调试日志", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.width(8.dp))
            Text(
                "${entries.size} 条" +
                    (if (ui?.debugFileEnabled == true) " · 落盘 " + ui.debugFileUsage.substringBefore(" ·") else ""),
                fontSize = 11.sp,
                color = t.inkFaint,
            )
            Spacer(Modifier.weight(1f))
            if (exportAllName().isNotBlank()) {
                TextButton(
                    onClick = {
                        val name = exportAllName()
                        exportAllLauncher.launch(name.ifBlank { "在途日志.txt" })
                    },
                ) { Text("导出全部", fontSize = 12.sp) }
            }
            TextButton(
                enabled = entries.isNotEmpty(),
                onClick = {
                    exportLauncher.launch(
                        "enroute-debug-" +
                            SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date()) + ".txt",
                    )
                },
            ) { Text("导出文档", fontSize = 12.sp) }
            TextButton(enabled = entries.isNotEmpty(), onClick = { copyAll(ctx) }) {
                Text("复制", fontSize = 12.sp)
            }
            TextButton(onClick = { onSelftest() }) {
                Text("密钥自检", fontSize = 12.sp)
            }
            TextButton(enabled = entries.isNotEmpty(), onClick = { DebugLog.clear() }) {
                Text("清空", fontSize = 12.sp, color = t.brick)
            }
        }

        // 级别过滤（票 20 / D3）：全部 / 警告+ / 仅错误
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LevelChip("全部", !onlyErrors && !warnPlus) { onlyErrors = false; warnPlus = false }
            LevelChip("警告+", !onlyErrors && warnPlus) { onlyErrors = false; warnPlus = true }
            LevelChip("仅错误 (${errorEntries.size})", onlyErrors) { onlyErrors = true; warnPlus = false }
            Spacer(Modifier.weight(1f))
            if (ui != null) {
                TextButton(onClick = { onClearHistory() }) { Text("清空历史", fontSize = 11.sp) }
            }
        }

        if (lastSave.isNotBlank()) {
            Text(
                lastSave,
                fontSize = 10.sp,
                color = t.olive,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "还没有日志。\n去「课表」页登录一次，这里就会记下每一步请求。",
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = t.inkFaint,
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(
                items = entries,
                key = { i, e -> "$i-${e.at}" },
                contentType = { _, _ -> "log" },
            ) { _, e ->
                val color = when {
                    e.level == DebugLog.Level.ERROR -> t.brick
                    e.level == DebugLog.Level.WARN -> t.olive
                    e.msg.contains("← 200") -> t.olive
                    e.msg.startsWith("→") || e.msg.startsWith("←") -> t.inkSoft
                    else -> t.inkFaint
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .background(t.card)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Row {
                        Text(
                            e.timeLabel(),
                            fontSize = 9.sp,
                            fontFamily = NumericFont,
                            color = t.inkFaint,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            e.tag,
                            fontSize = 9.sp,
                            fontFamily = NumericFont,
                            color = if (e.tag == "API") t.slate else t.inkFaint,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        e.msg,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        fontFamily = NumericFont,
                        color = color,
                    )
                }
            }
        }
    }
}

private fun copyAll(ctx: Context) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("enroute-debug", DebugLog.dump()))
    Toast.makeText(ctx, "已复制 ${DebugLog.entries.value.size} 条日志", Toast.LENGTH_SHORT).show()
}

/** 级别过滤小胶囊（0 圆角、细描边，沿用调试页的朴素风格） */
@Composable
private fun LevelChip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = InkTokens.current
    Box(
        Modifier
            .heightIn(min = 30.dp)
            .clip(RectangleShape)
            .background(if (on) t.cardAlt else t.card)
            .border(if (on) 1.5.dp else 0.8.dp, if (on) t.primary else t.outline)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 10.5.sp, color = if (on) t.ink else t.inkSoft)
    }
}