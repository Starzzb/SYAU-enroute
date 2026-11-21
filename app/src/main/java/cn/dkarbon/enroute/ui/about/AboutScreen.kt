package cn.dkarbon.enroute.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.dkarbon.enroute.BuildConfig
import cn.dkarbon.enroute.R
import cn.dkarbon.enroute.ui.common.Tesseract4D
import cn.dkarbon.enroute.ui.home.CardGap
import cn.dkarbon.enroute.ui.home.SectionLabel
import cn.dkarbon.enroute.ui.settings.UpdateSection
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.InkTokens
import cn.dkarbon.enroute.ui.theme.NumericFont
import cn.dkarbon.enroute.ui.theme.Space
import cn.dkarbon.enroute.ui.theme.Stroke
import cn.dkarbon.enroute.ui.theme.cardFloat
import cn.dkarbon.enroute.ui.timetable.TesseractBuffers
import cn.dkarbon.enroute.ui.timetable.drawTesseract4D
import cn.dkarbon.enroute.ui.timetable.tesseractAlpha

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

    Box(Modifier.fillMaxSize()) {
    /*
     * 背景：四维超立方体（票 29 / m31 轨道 E）。1.4 物理像素线宽 + 0.085~0.14 不透明度，
     * 只当纸纹 —— 它一旦和正文抢注意力就说明画重了。
     *
     * 为什么挂在「关于」页：这一页没有数据、常驻可达、留白最多；
     * 「我的」「设置」内容铺满，背景图几乎全被不透明卡片盖住，等于白做。
     *
     * 为什么卡片要半透明（m31 新增）：全不透明的卡把线框压得一条不剩，
     * 背景等于付了电费没交货。改成 0.62 / 0.58 之后，线框从卡片底下**隐约**透出来 ——
     * 看得见层次、看不见「装饰」。透出档来自 tesseractCardAlpha()，与 tesseractAlpha()
     * 是同一族数（线框强度与卡片透出程度必须成对调），对比度算式见 AboutCard。
     *
     * 速度不固定（m31）：相位不再按固定周期匀速长，而是 `phase += speed(t)·dt`，
     * 见 Tesseract4D.speedFactor —— 一圈 18.7–30.3 秒地摆，有「加速后又慢下来」的手感。
     * 省电约定（可见才跑 / 关动画就静止 / 每帧零分配）见 TesseractBackdrop。
     */
    TesseractBackdrop(
        modifier = Modifier.fillMaxSize(),
        color = t.ink,
        alpha = tesseractAlpha(Form.current.editorial, t.isDark),
    )

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

        // ── 隐私（义务告知，不是说明书：这里必须把「传了什么、没传什么」说清楚）──
        CardGap()
        SectionLabel("隐私")
        AboutCard {
            Text(
                "没有账号体系，课表、成绩、登录凭据都只存在这台手机上。",
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = t.inkFaint,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "匿名使用统计默认开启：只上传功能被用了多少次、失败了多少次，以及版本号与机型 ABI。" +
                    "不含学号、姓名、课程名、搜索词、坐标，也不含可长期追踪的设备标识。" +
                    "可在「设置 → 数据」里随时关掉，关掉即清空本机排队的记录。",
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                color = t.inkFaint,
            )
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
                    "课程大纲：14 个学院《本科课程大纲（2021 版）》PDF（14 册 11,146 页）。\n" +
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
                "· 账号密码只在登录那一次使用，不写入任何文件。\n" +
                    "· 登录会话（cookie）用设备密钥加密后只存本机，用于冷启动免重登；" +
                    "「清除所有数据」会连密钥一起销毁。\n" +
                    "· 若你主动开启「保持登录」，学号与密码同样以设备密钥加密后只存本机，" +
                    "且只会发往学校认证页 pass.syau.edu.cn。\n" +
                    "· 地图标注默认只存本机；导出并发送后才会离开手机。" +
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
    }   // ← 背景 Box（票 29）：上面的 `}` 闭合 Column，这一行闭合 Box
}

// ═══════════════════════════════════════════════════════════
// 背景层：自驱变速的四维超立方体（m31 轨道 E）
// ═══════════════════════════════════════════════════════════

/** 静态姿态（关动画 / 首帧）用的相位：0.31 是三条棱都不与视线重合的角度，最不「扁」 */
private const val ABOUT_INITIAL_PHASE = 0.31f

/**
 * 单帧步长上限（秒）。
 *
 * 掉帧、从后台回来、调试器断点之后，`now - last` 可能是几百毫秒；不夹的话
 * `phase += speed·dt` 会一步跨掉大半个姿态，视觉上就是「瞬移」而不是「转过去」。
 * 50ms = 20fps，比这更长的间隔一律当成 20fps 处理（慢一点，但不跳）。
 */
private const val ABOUT_MAX_FRAME_SECONDS = 0.05f

/**
 * 「关于」页背景：逐帧推进相位的四维超立方体。
 *
 * ## 为什么不直接用 MathBackdropMotion
 * 那个封装只有两种模式：匀速自转、手势驱动。这一版要的是**速度自己变**
 * （加速一阵、再松下来），相位循环只能另起；而且它所在的 `ui/timetable` 不归本轨改。
 * 绘制仍然复用同一个 `drawTesseract4D` —— 线宽、近实远虚、外接半径全是同一套数，
 * 换掉的只是「相位怎么长出来」，所以两页的线看起来是同一支笔画的。
 *
 * ## 参考的公开实现
 * 投影链路照 Jonathan Doolittle 的 4D hypercube 演示翻的：4D 里 6 个旋转平面 →
 * 逐平面正交旋转 → 透视除法 `k = 1/(W_DISTANCE - w)` → 再叠相机俯仰/偏航。
 * 数学部分早就落在 Tesseract4D 里（那边不 import 任何 Compose/Android 类，可跑 JVM 单测），
 * 这里只管时间。
 *
 * ## 时间轴
 * `clock` 是「动画真正跑着的秒数」，不是墙上时间 —— 退到后台不累加。速度是 clock 的函数、
 * 相位是它的积分，所以中断恢复时速度接得上（不会突然从最快速冲起来），相位也不会跳。
 *
 * ## 省电（四条，代码里都能看出来）
 * 1. **不可见就不跑**：[LaunchedEffect] 的 key 里有 [visible]，不到 STARTED 直接不进循环。
 * 2. **关动画就静止**：`ANIMATOR_DURATION_SCALE == 0`（开发者选项"移除动画" / 无障碍
 *    "减少动态效果"）时不进循环，停在 [ABOUT_INITIAL_PHASE] 的姿态 —— 是一张静止的图，
 *    不是空白，也不是继续空转烧电。
 * 3. **每帧零分配**：顶点/棱/角度在 [TesseractBuffers] 里复用；`withFrameMillis { it }` 的
 *    lambda 不捕获任何变量，Kotlin 把它编成单例（写成捕获块的形式，60fps 下每秒 60 个短命闭包）。
 * 4. **不逐帧重组**：相位存在 `mutableFloatStateOf` 里，**只在 Canvas 的绘制 lambda 里读**，
 *    Compose 会把它算成"绘制阶段依赖"，只重绘、不重组。
 */
@Composable
private fun TesseractBackdrop(modifier: Modifier, color: Color, alpha: Float) {
    val ctx = LocalContext.current
    // 只读一次：这个系统值在会话中不会变，不值得为它挂监听
    val animScaleOff = remember(ctx) {
        runCatching {
            android.provider.Settings.Global.getFloat(
                ctx.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }

    val phase = remember { mutableFloatStateOf(Tesseract4D.normalizePhase(ABOUT_INITIAL_PHASE)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var visible by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> visible = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> visible = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(visible, animScaleOff) {
        // 离开组合（换页 / 退出）时 Compose 直接取消这个协程 —— 不需要额外的 onDispose 收尾
        if (!visible || animScaleOff) return@LaunchedEffect

        var clock = 0f
        var lastFrame = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val dt = ((now - lastFrame) / 1000f).coerceIn(0f, ABOUT_MAX_FRAME_SECONDS)
            lastFrame = now
            clock += dt
            // 速度不固定：基准 1/26 圈每秒 × (1 + 0.55·sin0.17t + 0.25·sin(0.53t+1.1))
            phase.floatValue = Tesseract4D.advancePhase(phase.floatValue, clock, dt)
        }
    }

    val buffers = remember { TesseractBuffers() }
    Canvas(modifier) {
        drawTesseract4D(
            color = color,
            phase = phase.floatValue,   // ← 只在绘制阶段读：只触发重绘
            alpha = alpha,
            // 1.4 物理像素，不乘 density：要的是"纸上一根细线"，不是 1dp 的 UI 元素
            // （与课表页同一档，见 MathBackdrop.drawTesseract4D 的调用点注释）
            strokeWidth = 1.4f,
            buffers = buffers,
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


/**
 * 本页卡片：方角 + **半透明** card 底 + 1dp outline + 减一档的浮影。
 * （HomeCard 是 private，跨文件复用得先把它提成 internal —— 那属于首页模块的改动，
 *  这里自带一份更稳妥，也不影响别人。）
 *
 * ## 为什么是半透明（m31 轨道 E）
 * 背景那张四维线框如果被全不透明卡片盖死，等于画了个看不见的东西。
 * 档位取自 `tesseractCardAlpha()`：亮色 0.62 / 暗色 0.58 —— 线框能隐约透出来，
 * 但正文仍是「底色上写字」而不是「画上写字」。
 *
 * ## 对比度自检（实算，不是估的）
 * 合成顺序是**先画线、后叠卡**，所以最深的那一像素是：
 * ```
 * bg = a·card + (1-a)·line ，  line = α·ink + (1-α)·paper   （α = tesseractAlpha 的档位）
 * ```
 * 拿 HARD 亮色做一遍：line = 0.12·(26,22,17) + 0.88·(245,242,236) = (218.7,215.6,209.7)；
 * bg = 0.62·(250,248,244) + 0.38·(218.7,215.6,209.7) = (238.1,235.7,231.0)，相对亮度 L=0.8378。
 * 其余三套同法（`(L+0.05)/(L2+0.05)`，WCAG 2.x）：
 *
 * | 主题 | 卡片 α | 合成面 L | ink | inkFaint | 结论 |
 * |---|---|---|---|---|---|
 * | HARD 亮 | 0.62 | 0.8378 | 15.9:1 | **6.01:1** | ✅ |
 * | HARD 暗 | 0.58 | 0.01440 | 13.6:1 | **5.02:1** | ✅ 全表最低，仍过线 |
 * | 账页 亮 | 0.62 | 0.9130 | 15.3:1 | **5.25:1** | ✅ |
 * | 账页 暗 | 0.58 | 0.02126 | 12.1:1 | **5.84:1** | ✅ |
 *
 * 最低的 inkFaint 档也有 5.02:1（要求 4.5:1），正文 ink 全部 ≥12:1 —— 所以 0.62/0.58
 * 是「线框透得出来」与「小字仍然读得清」的交点，不是随手取的好看数。
 * 顺带记一笔错法：若按「卡先叠纸、线再压上去」算（把顺序弄反），暗色会算出 4.33:1 的假失败。
 *
 * 描边保持不透明（outline 全 alpha）：半透明底 + 实线边，卡片的边界才不会被背后的线吃掉。
 */
@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    val t = InkTokens.current
    val spec = Form.current
    Column(
        Modifier
            .fillMaxWidth()
            // 浮影减一档：账页主题的纸片投影 alpha 减半（HARD 本来就不投影，这一行空转）
            .cardFloat(spec.copy(shadowAlpha = spec.shadowAlpha * 0.5f), t.hardShadow)
            .clip(RectangleShape)
            .background(t.card.copy(alpha = Tesseract4D.tesseractCardAlpha(t.isDark)))
            .border(Stroke.hairline, t.outline)
            .padding(horizontal = Space.cardPad, vertical = Space.m),
        content = content,
    )
}