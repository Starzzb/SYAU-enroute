package cn.dkarbon.enroute.ui.timetable

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import cn.dkarbon.enroute.ui.theme.Form
import cn.dkarbon.enroute.ui.theme.FormSpec

/**
 * 课表卡片的一套显示配置（m39）。
 *
 * 全部默认值 = 改造前行为，所以老用户升级后一个像素不变。
 */
@Immutable
data class TtCardConfig(
    /** 版式：0 = 侵蚀纸卡 / 1 = 账页格 */
    val layout: Int = 0,
    /** 七轴槽位（m41）：最终取值，预设与覆盖已在 ViewModel 侧合并完 */
    val slots: TtSlots = TtSlots(),
    /** 当前预设下标（设置页显示选中态用） */
    val preset: Int = 0,
    /** 底色来源：0 = 四色系 / 1 = 单墨 / 2 = 无色 */
    val tint: Int = 0,
    val spine: Boolean = true,
    val erode: Boolean = true,
    val ghost: Boolean = true,
    val food: Boolean = true,
    val nowRing: Boolean = true,
) {
    val ledger: Boolean get() = layout == 1
}

/**
 * 课表卡片的显示配置（CompositionLocal）。
 *
 * ## 为什么是 CompositionLocal 而不是全局单例
 *
 * 课表渲染链有 6 层（TimetableScreen → GridArea → CourseCell → CourseCard …），
 * 一路透传参数会让签名全变、「历年课表」那条只读分支也得跟着改，所以走隐式传递。
 *
 * ## ⚠️ 真机踩过三次（2026-11-21，务必别再退回单例）
 *
 * 1. `@Volatile var` 存每个开关 —— 切「账页」后课表毫无变化。`@Volatile` 只保证多线程
 *    可见性，**Compose 的快照系统根本不认识它**，读它不建立任何依赖。
 * 2. 加 `revision: Int` 手动触发 —— 同样无效，读一个普通 Int 属性也不会订阅。
 * 3. object 里 `by mutableStateOf(...)` —— 理论可行，但配置的**真身**在 AppState（StateFlow）里，
 *    中间抄一份等于有两份真相；一旦抄写那一环没跑到，UI 显示已切换、画面却不动。
 *
 * 现在：配置只在 AppState 里存一份，由 [cn.dkarbon.enroute.ui.EnRouteRoot] 顶层 provide，
 * 课表侧读 [LocalTtCardConfig]。AppState 是 StateFlow，设置一改整棵树就重组。
 *
 * 4. **回退到 `Form.current` 也不行**（2026-11-21 真机第四次）：老用户的 `styleSet`
 *    存的是 `EDITORIAL(2)`，`Form.current` 本身就是 Ledger —— 于是「纸卡 / 账页」两个选项
 *    都渲染成账页，看起来像切换失效。形态**必须完全由本页配置决定**，不许回退。
 */
val LocalTtCardConfig = compositionLocalOf { TtCardConfig() }

/** 课表侧读配置的简写（Composable 内可用） */
val TtCardPrefs: TtCardConfig
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalTtCardConfig.current

/**
 * 课表专用的形态取值：**设置里选了账页就用账页**，否则沿用旧 `styleSet` 判定。
 *
 * 所有原来读 `Form.current` 的地方都改走这里，避免「外层读了新值、内层还是旧值」。
 */
val TtCardForm: FormSpec
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = if (LocalTtCardConfig.current.ledger) FormSpec.Ledger else FormSpec.Hard