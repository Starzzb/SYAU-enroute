package cn.dkarbon.enroute.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 资讯频道语义强调色（2026-11-16：主人真机反馈「资讯页的分类可以新增强调色」）。
 *
 * 纪律：**不引入新硬编码色板**。每个频道的色都从现有 token 派生 ——
 * 已有字段直接取（brick / slate / olive / ink），缺的色相用 [HslTune] 从钢青 [Steel] 派生
 * （学工转色相到紫、新闻只提亮不动色相），主色走 [primaryInk]（自动落到 ≥4.5:1 的小字档）。
 * 对比度口径与 `ContrastAuditTest` 一致（WCAG 2.1 相对亮度），全部 ≥4.5:1，
 * 实测值见 `docs/设计-对比度实测.md`「资讯频道强调色」一节。
 *
 * 色系语义（每个频道为什么是这系色）：
 * - 竞赛 → 锈红 brick：报名截止的紧迫感，热而不过火（偏热）。
 * - 证书 → 钢青/靛 slate：证书是「正式、可信、盖章」的物事，用沉稳的深蓝靛。
 * - 教务 → 主色：教务是学校主航道（选课/成绩/考试都在此），跟品牌主色走。
 * - 就业 → 阜绿 olive：就业是「出路/丰收」，取庄稼成熟的阜绿。
 * - 学工 → 偏紫灰：由钢青转色相到紫（276°）再压灰 —— 学工介于「规训的蓝」与「生活的暖」之间。
 * - 通知 → 中性墨 ink：事务性通知不站队，用最中性的正文墨色。
 * - 新闻 → 偏青：由 slate 提亮一档（明度 +0.05）—— 新闻要轻快、有时效，比证书浅一档。
 * - 全部/其它 → 主色：收口用品牌主色。
 */
@Composable
@ReadOnlyComposable
fun feedChannelInk(category: String): Color {
    val t = InkTokens.current
    return when (category) {
        "竞赛" -> t.brick
        "证书" -> t.slate
        "就业" -> t.olive
        "学工" -> studentAffairsInk(t.isDark)
        "通知" -> t.ink
        "新闻" -> HslTune.tune(t.slate, lightness = 0.05f, saturation = 0f)
        // 教务 / 全部 / 其它：品牌主色（当文字时落到 primaryInk 的达标档）
        else -> primaryInk()
    }
}

/**
 * 学工「偏紫灰」：从钢青 [Steel.n500] 出发，色相 194°→276°，饱和度 ×0.92、明度微调。
 * 亮色压到 l≈0.29 得 #4F3461（HARD 纸底 9.41:1 / 账页纸底 9.84:1）；
 * 暗色提到 l≈0.62 得 #A482BB（HARD 墨底 5.96:1 / 账页墨底 5.45:1）。
 */
private fun studentAffairsInk(isDark: Boolean): Color {
    val (_, s, l) = HslTune.toHsl(Steel.n500)
    return if (isDark) HslTune.of(276f, s * 0.92f, l + 0.32f)
    else HslTune.of(276f, s * 0.92f, l - 0.01f)
}
