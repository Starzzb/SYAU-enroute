package cn.dkarbon.enroute.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.data.ExamItem
import cn.dkarbon.enroute.data.SessionState
import cn.dkarbon.enroute.data.UrpHomeApi.CreditProgress
import cn.dkarbon.enroute.data.UrpHomeApi.ScoreStats
import cn.dkarbon.enroute.data.UrpHomeApi.StudentProfile
import cn.dkarbon.enroute.ui.SessionText
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import java.time.LocalDate

/**
 * 学业主页的卡片。
 *
 * 设计取向：**一张卡只说一件事**，数字用等宽字体、右对齐；卡片之间靠 1dp 边线分隔，
 * 不用阴影不用圆角（沿用全 App 的硬朗体系）。
 *
 * 数据纪律写在每张卡的空值分支里：拿不到就说「暂缺 / 连校园网」，不显示 0。
 */

/** 卡片底子：面 + 1dp 边线 + 16dp 内边距，可点 */
@Composable
fun CardGap() {
    Spacer(Modifier.height(Space.cardGap))
}

/** 一行等宽小字（用于卡片内的次要说明汇总） */
@Composable
fun HomeFootNote(text: String) {
    val t = InkTokens.current
    Text(
        text,
        fontSize = 9.5.sp,
        color = t.inkFaint,
        fontFamily = NumericFont,
        modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs),
    )
}

/** 分组小标题（页内分区用，不做卡片） */
@Composable
fun SectionLabel(text: String) {
    val t = InkTokens.current
    Row(
        Modifier.fillMaxWidth().padding(top = Space.l, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(text, fontSize = 11.sp, color = t.inkFaint, fontWeight = FontWeight.Medium)
    }
}
