package cn.dkarbon.enroute.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.data.UpdateDialogPolicy
import cn.dkarbon.enroute.data.UpdateInfo
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.primaryInk

/**
 * 更新提醒弹窗 · m31 版（去掉里程表翻牌）。
 *
 * 版面三层，顺序固定：
 *   1. 标题：`可更新到 0.7.4+m31`（版本号来自 latest.json，不写死）
 *   2. 副行：`当前 0.7.3+m30 · 8.1 MB`
 *   3. 主体：Release 公告。`notes` 是分条格式就按条渲染（行距 21sp、可滚动），
 *      是一句话就在正文区居中显示。**弹窗不联网**：全部内容来自 [UpdateInfo.notes]。
 *
 * 页脚固定：`立即更新` 独占一行，两个次要出口同行等分；强制更新时移除「不再提醒」。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    fromCode: Long,
    mandatory: Boolean,
    onUpdateNow: () -> Unit,
    onGotIt: () -> Unit,
    onDismissVersion: () -> Unit,
) {
    val t = InkTokens.current
    val f = Form.current
    // fromCode 只用于调用方判定 mandatory，版面不再显示 versionCode
    val body = remember(info.notes) { UpdateDialogPolicy.presentNotes(info.notes) }
    val size = remember(info.size) { UpdateDialogPolicy.sizeLabel(info.size) }

    Box(Modifier.fillMaxSize()) {
        // 遮罩不设点击：三个出口必须显式选一个（频率语义靠这个不跑偏）
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.60f)))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(t.paper, f.shapes.sheet)
                .border(1.5.dp, t.outline, f.shapes.sheet),
        ) {
            // ── 标题 + 副行 ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 26.dp, top = 24.dp, bottom = 18.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 12.sp, color = t.inkFaint)) {
                            append("可更新到 ")
                        }
                        withStyle(
                            SpanStyle(
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = NumericFont,
                                color = t.ink,
                            ),
                        ) {
                            append(info.versionName)
                        }
                    },
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    buildString {
                        append("当前 ")
                        append(BuildConfig.VERSION_NAME)
                        if (size.isNotBlank()) {
                            append(" · ")
                            append(size)
                        }
                    },
                    fontSize = 11.sp,
                    fontFamily = NumericFont,
                    letterSpacing = 0.04.sp,
                    color = t.inkSoft,
                )
            }

            // ── 主体：Release 公告 ──
            if (!body.isEmpty) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp)
                        .height(1.dp)
                        .background(t.rule),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 26.dp, vertical = 14.dp),
                ) {
                    // 摘要行（分条格式里 bullet 之前的引导句；纯一句话时就是全部内容）
                    if (body.summary.isNotBlank()) {
                        Text(
                            body.summary,
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            color = if (body.items.isEmpty()) t.ink else t.inkSoft,
                            textAlign = if (body.items.isEmpty()) TextAlign.Center else TextAlign.Start,
                            modifier = if (body.items.isEmpty()) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.fillMaxWidth().padding(bottom = 10.dp)
                            },
                        )
                    }
                    body.items.forEach { note ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 8.dp)
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(primaryInk()),
                            )
                            Spacer(Modifier.size(11.dp))
                            Text(
                                note,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                color = t.inkSoft,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── 页脚固定：立即更新独占一行，两个次要按钮同行等分 ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp)
                    .height(1.dp)
                    .background(t.rule),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 16.dp)) {
                PrimaryButton(text = "立即更新", onClick = onUpdateNow)
                Spacer(Modifier.height(9.dp))
                if (UpdateDialogPolicy.showsDismiss(mandatory)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        GhostButton("我知道了", Modifier.weight(1f), onClick = onGotIt)
                        GhostButton("不再提醒本次更新", Modifier.weight(1f), onClick = onDismissVersion)
                    }
                } else {
                    // 强制更新：整块移除「不再提醒」，只剩两个出口（可催，不能锁死）
                    GhostButton("我知道了", Modifier.fillMaxWidth(), onClick = onGotIt)
                }
                if (mandatory) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前版本已停止支持",
                        fontSize = 9.5.sp,
                        letterSpacing = 0.06.sp,
                        color = t.inkFaint,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(f.shapes.control)
            .background(t.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (t.isDark) Color(0xFF100E0B) else Color.White,
        )
    }
}

@Composable
private fun GhostButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    val t = InkTokens.current
    val f = Form.current
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(f.shapes.control)
            .background(Color.Transparent)
            .border(1.dp, t.outline, f.shapes.control)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = t.ink,
        )
    }
}
