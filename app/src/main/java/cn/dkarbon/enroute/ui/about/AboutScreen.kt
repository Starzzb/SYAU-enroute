package cn.dkarbon.enroute.ui.about

import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.ColumnScope
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.R
import cn.dkarbon.enroute.ui.home.CardGap
import cn.dkarbon.enroute.ui.home.SectionLabel
import cn.dkarbon.enroute.ui.settings.UpdateSection
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke

/**
 * 关于 / 开源信息。
 *
 * 为什么要有独立一页而不是塞在设置底部的两行小字：
 * 这个 App 的数据全部来自学校系统、接口是探测出来的，使用者有权知道
 * 「它是什么、代码在哪、许可怎么算、出了事找谁」。开源软件的「关于」页就是承担这件事的。
 *
 * 三条自律：
 *  1. **仓库地址只写真实的一个**，不写「即将开源」这种空话；
 *  2. **许可证写实际的**（见仓库根 LICENSE），没加就不写；
 *  3. **免责与数据来源必须显式**，不藏在折叠里 —— 这是唯一能解释「数字可能不准」的地方。
 */
@Composable
fun AboutScreen() {
    val t = InkTokens.current
    val ctx = LocalContext.current
    val repo = "https://github.com/Starzzb/SYAU-enroute"

    fun open(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        // ── 刊头 ──
        Text("沈农在途", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Text("SYAU En Route", fontSize = 12.sp, color = t.inkFaint, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                BuildConfig.VERSION_NAME,
                fontSize = 13.sp,
                fontFamily = NumericFont,
                fontWeight = FontWeight.Bold,
                color = t.ink,
            )
            Text(
                "  build ${BuildConfig.VERSION_CODE}" +
                    (BuildConfig.GIT_COMMIT.takeIf { it.isNotBlank() }?.let { " · ${it.take(7)}" } ?: "") +
                    (BuildConfig.BUILD_TIME.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                fontSize = 11.sp,
                fontFamily = NumericFont,
                color = t.inkFaint,
            )
        }

        // ── 版本（票 26 ②：版本号 / commit 短码 / 构建日期三样必须在这一页看得到）──
        CardGap()
        SectionLabel("版本")
        AboutCard {
            KvRow("版本号", BuildConfig.VERSION_NAME)
            Line()
            KvRow("构建号", "${BuildConfig.VERSION_CODE}")
            Line()
            KvRow("commit", BuildConfig.GIT_COMMIT.takeIf { it.isNotBlank() }?.take(7) ?: "—")
            Line()
            KvRow("构建日期", BuildConfig.BUILD_TIME.takeIf { it.isNotBlank() } ?: "—")
        }

        // ── 更新（复用设置页那个自带状态机的区块：查 → 下载 → 校验 → 交系统安装器）──
        CardGap()
        SectionLabel("更新")
        UpdateSection()

        // ── GitHub ──
        CardGap()
        SectionLabel("源代码")
        AboutCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Space.minTouch)
                    .clickable { open(repo) }
                    .padding(vertical = 10.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = "GitHub",
                    colorFilter = ColorFilter.tint(t.ink),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Starzzb / SYAU-enroute", fontSize = 13.sp, color = t.ink)
                    Text(
                        "github.com/Starzzb/SYAU-enroute",
                        fontSize = 11.sp,
                        fontFamily = NumericFont,
                        color = t.inkFaint,
                    )
                }
                Text("打开 ›", fontSize = 12.sp, color = t.accent)
            }
        }

        // ── 许可 ──
        CardGap()
        SectionLabel("许可证")
        AboutCard {
            KvRow("项目许可", "MIT License")
            Line()
            KvRow("怎么用都行", "可自由使用/修改/分发，请保留版权声明")
            Line()
            KvRow("不提供担保", "作者不对使用后果负责（MIT 原文）")
        }

        // ── 依赖与致谢 ──
        CardGap()
        SectionLabel("开源依赖")
        AboutCard {
            listOf(
                "Kotlin · JetBrains · Apache-2.0",
                "Jetpack Compose · Google · Apache-2.0",
                "kotlinx.serialization · JetBrains · Apache-2.0",
                "IBM Plex Mono · IBM · OFL-1.1",
            ).forEachIndexed { i, s ->
                if (i > 0) Line()
                Text(s, fontSize = 11.5.sp, color = t.inkSoft, modifier = Modifier.padding(vertical = 7.dp))
            }
        }

        CardGap()
        SectionLabel("数据来源")
        AboutCard {
            Text(
                "课表 / 考试 / 成绩 / 空闲教室 / 教师：沈阳农业大学本科教务系统（URP，校内网 10.2.0.47）。\n" +
                    "培养方案：教务处《2023 级 / 2024 级 / 2025 级本科人才培养方案》PDF。\n" +
                    "课程大纲：土地与环境学院《本科课程大纲（2021 版）》PDF。\n" +
                    "教师公开信息：各学院官网（西安博达 VSB 站群）。\n" +
                    "校园地图：校园规划示意图矢量化后的图块，标注由使用者共同补充。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkSoft,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // ── 隐私 ──
        CardGap()
        SectionLabel("隐私")
        AboutCard {
            Text(
                "· 账号密码只用在你点登录的那一次，不写入任何文件。\n" +
                    "· 登录会话（cookie）用设备密钥加密后只存本机，用于冷启动免重登；" +
                    "「清除所有数据」会连密钥一起销毁。\n" +
                    "· 若你主动开启「保持登录」，学号与密码同样以设备密钥加密后只存本机，" +
                    "且只会发往学校认证页 pass.syau.edu.cn。\n" +
                    "· 地图标注默认只存本机；点「导出标注」并发送后才会离开手机。" +
                    "若你开启共建上传，只上传「地块 + 字段 + 值」，不含学号姓名与位置。\n" +
                    "· 本 App 不申请定位权限。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkSoft,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // ── 免责 ──
        CardGap()
        SectionLabel("免责")
        AboutCard {
            Text(
                "本 App 是非官方的个人项目，与沈阳农业大学及其任何部门无关，未获授权或认可。\n" +
                    "所有数据取自公开可访问的校内页面，解析结果可能有误、可能因学校改版失效；" +
                    "一切以教务系统与学院官网原文为准。涉及考试、学分、毕业等事项请勿据此决策。\n" +
                    "项目按学术/个人学习用途开发并提供，分发与使用请遵守学校相关规定。",
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = t.inkFaint,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Made with Kotlin & Jetpack Compose",
            fontSize = 10.5.sp,
            color = t.inkFaint,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "GitHub 图标为 GitHub, Inc. 商标，仅用于指向本仓库链接。",
            fontSize = 10.sp,
            color = t.inkFaint,
        )
    }
}

@Composable
private fun KvRow(k: String, v: String) {
    val t = InkTokens.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.minTouch)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(k, fontSize = 12.5.sp, color = t.inkSoft)
        Text(v, fontSize = 12.sp, color = t.ink)
    }
}

@Composable
private fun Line() {
    val t = InkTokens.current
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(Stroke.hairline)
            .background(t.rule),
    )
}


/** 本页卡片：与首页 HomeCard 同款外观（方角 + card 底 + 1dp outline），
 *  但 HomeCard 是 private，跨文件复用得先把它提成 internal —— 那属于首页模块的改动，
 *  这里自带一份更稳妥，也不影响别人。 */
@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    val t = InkTokens.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(t.card)
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.cardPad, vertical = Space.m),
        content = content,
    )
}