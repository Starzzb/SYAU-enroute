package cn.dkarbon.enroute.ui.academic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import cn.dkarbon.enroute.data.ScoreItem
import cn.dkarbon.enroute.ui.AppState
import cn.dkarbon.enroute.ui.TimetableViewModel
import cn.dkarbon.enroute.ui.theme.InkTokens

/**
 * 成绩。
 *
 * 和 [ExamScreen] 一样：接口是猜的，所以抓空时给可执行提示，不假装「没有成绩」。
 *
 * 不在这里算加权平均 —— 因为「学分」和「成绩」字段名各校不同，万一解析到空串，
 * 算出来的 GPA 是假的。宁可不算，也不给错数字。
 */
@Composable
fun ScoreScreen(ui: AppState, vm: TimetableViewModel, modifier: Modifier = Modifier) {
    val t = InkTokens.current

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("成绩", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.width(8.dp))
            Text("${ui.scores.size} 条", fontSize = 11.sp, color = t.inkFaint)
            Spacer(Modifier.weight(1f))
            if (ui.scoresLoading) {
                Text("抓取中…", fontSize = 11.sp, color = t.inkFaint)
            } else {
                TextButton(onClick = { vm.refreshAcademic() }) {
                    Text("刷新", fontSize = 12.sp)
                }
            }
        }

        if (ui.scoresMsg.isNotBlank()) {
            Text(
                ui.scoresMsg,
                fontSize = 11.sp,
                color = if (ui.scores.isEmpty()) t.brick else t.inkFaint,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        if (ui.scores.isEmpty()) {
            EmptyState(
                title = "还没有成绩",
                body = if (!ui.loggedIn) {
                    "登录教务后自动获取。"
                } else {
                    "这一学期可能还没出成绩。"
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(
                    items = ui.scores,
                    key = { s -> "${s.name}|${s.term}|${s.score}" },
                    contentType = { "score" },
                ) { s ->
                    ScoreRow(s)
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(s: ScoreItem) {
    val t = InkTokens.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(cn.dkarbon.enroute.ui.theme.Stroke.hairline, t.outline)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                s.name.ifBlank { "（未命名课程）" },
                fontSize = 13.sp,
                color = t.ink,
                fontWeight = FontWeight.Medium,
            )
            if (s.term.isNotBlank() || s.credit.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (s.term.isNotBlank()) append(s.term)
                        if (s.credit.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append("${s.credit} 学分")
                        }
                    },
                    fontSize = 10.sp,
                    color = t.inkFaint,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                s.score.ifBlank { "—" },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (s.score.startsWith("不及格") || s.score == "0") t.brick else t.ink,
            )
            if (s.grade.isNotBlank()) {
                Text("等级 ${s.grade}", fontSize = 9.5.sp, color = t.inkFaint)
            }
        }
    }
}
