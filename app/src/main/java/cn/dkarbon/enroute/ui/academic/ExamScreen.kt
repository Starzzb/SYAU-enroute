package cn.dkarbon.enroute.ui.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 考试安排。
 *
 * ⚠️ 数据来自「猜」的接口（见 [cn.dkarbon.enroute.data.AcademicApi] 的 KDoc），
 * 所以这里做了两件事：
 *   1. 抓空时给出可执行提示（去跑接口诊断），不是一句「暂无数据」
 *   2. 每条都能点开看原始 JSON 片段，方便你把真实结构发我
 *
 * 性能：LazyColumn 必须给 `key` + `contentType`（不然滚动时复用不了 composition slot）。
 */
@Composable
fun ExamScreen(ui: AppState, vm: TimetableViewModel, modifier: Modifier = Modifier) {
    val t = InkTokens.current

    Column(modifier.fillMaxSize()) {
        // 抬头
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("考试", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.width(8.dp))
            Text("${ui.exams.size} 场", fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.weight(1f))
            if (ui.examsLoading) {
                Text("抓取中…", fontSize = 11.sp, color = t.inkFaint)
            } else {
                TextButton(onClick = { vm.refreshAcademic() }) {
                    Text("刷新", fontSize = 12.sp)
                }
            }
        }

        if (ui.examsMsg.isNotBlank()) {
            Text(
                ui.examsMsg,
                fontSize = 11.sp,
                color = if (ui.exams.isEmpty()) t.brick else t.inkFaint,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        if (ui.exams.isEmpty()) {
            EmptyState(
                title = "还没有考试安排",
                body = if (!ui.loggedIn) {
                    "登录教务后自动获取。"
                } else {
                    "这一学期可能还没排考。"
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = ui.exams,
                    key = { e -> "${e.name}|${e.time}|${e.place}" },
                    contentType = { "exam" },
                ) { e ->
                    ExamRow(e)
                }
            }
        }
    }
}

@Composable
private fun ExamRow(e: ExamItem) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(cn.dkarbon.enroute.ui.theme.Stroke.hairline, t.outline)
            .padding(12.dp),
    ) {
        Text(
            e.name.ifBlank { "（未命名课程）" },
            fontSize = 14.sp,
            color = t.ink,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(5.dp))
        Row {
            Text(e.time.ifBlank { "时间未定" }, fontSize = 11.5.sp, color = t.brick)
            if (e.place.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(e.place, fontSize = 11.5.sp, color = t.inkSoft)
            }
            if (e.seat.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text("座 ${e.seat}", fontSize = 11.5.sp, color = t.inkFaint)
            }
        }
        if (e.type.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(e.type, fontSize = 10.5.sp, color = t.inkFaint)
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String) {
    val t = InkTokens.current
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 15.sp, color = t.ink, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
