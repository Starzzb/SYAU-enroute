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
) {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val entries by DebugLog.entries.collectAsState()
    val listState = rememberLazyListState()
    var lastSave by remember { mutableStateOf("") }

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
            Text("${entries.size} 条", fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.weight(1f))
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
                    e.msg.startsWith("✘") -> t.brick
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
