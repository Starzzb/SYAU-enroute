# 调研：BiliPai 与 Miuix 的设计拆解（票 35）

> 调研时间：2026-11-15。方法：`git clone --depth 1` 到 `O:\dev\refs\BiliPai`（`35135a1` main）
> 与 `O:\dev\refs\miuix`（`d8efe18` main），逐文件阅读源码；依赖坐标另查 Maven Central 元数据。
> **纯调研票：没有改动本项目任何代码，没有编译，没有装机。**
>
> ⚠️ **协议前提（贯穿全文）**：BiliPai 是 GPL-3.0，我们 MIT。下文所有 BiliPai 内容
> 都是「读完之后用自己的话记思想」，代码块只用于**说明它长什么样**，不得复制进我们仓库。
> 数值常量（弹簧、时长、dp）属思想层面的可抄项，见第 3、4 节的逐条标注。

---

## 0. 仓库事实核对（先纠正几条流传的线索）

| 线索 | 实测结论 |
|---|---|
| 用 Miuix | ✅ 真。`gradle/libs.versions.toml` 里 `miuix = "0.9.4-b7a5f08f-SNAPSHOT"`，`settings.gradle.kts` 从 **GitHub Packages**（`maven.pkg.github.com/compose-miuix-ui/miuix`）解析，且注释写明「即使公开包也需要认证」 |
| 用 Compose Cupertino | ⚠️ 半真。`io.github.alexzhirkevich:cupertino:0.1.0-alpha04`（+`-adaptive`、`-icons-extended`）**只用在「居中文本项」这一种形态**：`AdaptiveListItemPolicy.kt` 里 `AppClickableItemRenderer.CUPERTINO` 仅在 `centered == true` 时命中 |
| 用 Haze 毛玻璃 | ✅ 真。`dev.chrisbanes.haze:haze / -blur / -blur-materials` **2.0.1**，design-system 以 `api` 暴露 |
| **用 Orbital 做共享元素过渡** | ❌ **不成立**。全仓（含 `.kt/.kts/.md/.toml`）grep `Orbital` **命中 0 处**。它的共享元素/窗口过渡走的是 **ITGSA SharedViewKit**（`gsai.sdk:sharedview-kit:1.0.0.5`，一个联盟 Nexus 上的闭源倾向 SDK）+ 自研 Compose 控制器 |
| **用 Compose Shimmer 做骨架屏** | ❌ **不成立，而且它自己主动移除了**。根 `build.gradle.kts:507` 写着：「compose-shimmer 已移除：全仓 0 处 import，骨架屏早已改用自研的 `Modifier.shimmer()` / `shimmerEffect()`」，甚至有单测 `VideoDetailSkeletonStructureTest` 专门断言不再使用它 |
| 「BiliPai-miuix 那次 design-system 重构」 | ✅ 结构上能看出重构痕迹：`design-system` / `design-tokens` / `settings-core` / `brand-motion` / `miuix-navigation` 五个独立 Gradle 模块，组件按 `components`（对外契约）+ `renderer/miuix` + `renderer/material3`（两套后端）分目录 |

**这两条打假很重要**：如果照着「Orbital + compose-shimmer」去选型，我们会引入两个 BiliPai 根本没用的库。
它真正值得看的动效实现是**自研的那套**（第 3 节给数值）。

规模感：BiliPai 19 个 Gradle 模块；`design-system` 285 个 kt 文件；
`SettingsManager.kt` 单文件 **457KB**、`AppearanceSettingsScreen.kt` **180KB**、
`PlaybackSettingsScreen.kt` **180KB** —— 这是个把设置页当产品做的仓库，反面教材也在这里（见 §5）。

---

## 1. 设置页信息架构

### 1.1 三层结构：分类目录 → 分类页 → 叶子分组

BiliPai 的设置不是一个长列表，而是**三跳**：

```
设置（根）
  ├─ 分类目录卡片：每行 = 图标 + 标题 + 一句副标题 + 右侧细箭头（chevron 14dp）
  │    「外观与动画」/「主题、文字大小、图标、动画与振动」
  │    「播放设置」  /「画质、倍速、字幕、连播、小窗和后台播放」
  │    「首页与动态」/「视频卡片、推荐内容和动态页面」
  │    「导航布局」  /「底栏、首页分类栏、搜索分类和平板侧边栏」
  │    「隐私与权限」/「历史记录、身份验证、系统权限和屏蔽名单」
  │    ……（枚举见 SettingsRootCategory）
  ├─ 分类页：只放「该分类下会跳走的叶子」+ 少量必须就地决策的开关
  └─ 叶子页：真正的控件堆叠处（AppearanceSettingsScreen 等）
```

关键的一行代码态度，写在 `SettingsRootCategoryListSection` 里：
**目录页的每一行都必须有副标题**（`subtitle = stringResource(category.subtitleResource())`），
副标题写的是「这一页里有什么」，不是「这一页是干什么的」的宣传语。
例如「隐私与权限」的副标题直接列举 `历史记录、身份验证、系统权限和屏蔽名单` ——
用户扫一眼就知道自己要找的开关在不在这一页里，不需要点进去试。

### 1.2 一个我们该直接抄走的细节：分组名不许复读分类名

`SettingsRootCategoryContent` 里有一段原作者写的注释，值得原样记住：

> `// 分组名描述内容范围,不复读分类名,避免"隐私与权限里还有隐私与权限"。`

所以在「隐私与权限」分类页里，分组标题叫**「历史记录与权限」**而不是「隐私与权限」。
这是被自己的信息架构坑过之后才写得出的规矩。

同一处的第二个动作也值得一提：分类页对「点进去才知道没有内容」的类目，
显式列出 `FULLSCREEN_GESTURE, COMMENTS_CONTENT, ... -> Unit`（渲染空）——
**宁可空着，也不在目录页堆一层没有内容的壳**。

### 1.3 每一项的密度（实测数值）

分组卡片 + 组内行 + 行间分隔线，三件套。Miuix 档与 MD3 档的差别只在圆角和图标底：

| 度量 | 值 | 来源 |
|---|---|---|
| 行内水平内边距 | **16dp**（两档相同） | `resolveAdaptiveListRowVisualSpec` |
| 行内垂直内边距 | Miuix **16dp** / MD3 **12dp** | 同上 |
| 行最小可点高度 | **44dp**（不是 48！） | `AppChromeSizeTokens.CompactRowMinHeightDp` |
| 卡片圆角 | Miuix **16dp** / MD3 **24dp** | `AdaptiveListComponentVisualSpec` |
| 图标底方块 | Miuix **38dp**、MD3 **40dp**、彩色档 **32dp** | 同上 |
| 图标字形 | Miuix **20dp** / MD3 **22dp** | 同上 |
| 图标底圆角 | Miuix **10dp** / MD3 **12dp** | 同上 |
| 分组标题上/下留白 | **24dp / 8dp** | `SettingsSectionTitle` Miuix 分支 |
| 分隔线粗细 | **0dp**（Miuix 档不画线） | `dividerThicknessDp = 0f` |
| 搜索框高度 | **48dp**，圆角 14dp（MD3） | 有单测锁死 |

这里最反常识的是 **44dp** 和原作者写在代码里的理由，注释直译：
> 「Miuix 控件本身视觉更紧凑。给 48dp 的视觉最小值会让按钮和设置行显得虚胖；
> 可点区域的扩展交给控件内部处理，而不是放大它的可视容器。」

即：**视觉高度 44dp，命中区域另外补到可点标准**。这比「为了无障碍把整行拉到 48dp」聪明，
也直接解释了 BiliPai 设置页为什么看着比 MD3 默认紧凑得多。这个思路可以搬，具体 dp 值要按我们自己的字号重测。

### 1.4 说明文字：有，但**只有两句，而且各归各位**

这是主人嫌弃我们设置页的那一处，BiliPai 的答案是**把文字分成三个不同角色**，各放一个位置：

```kotlin
SettingSwitchItem(
    title    = settingItemTitle("home.dynamic_top_bar_collapse_on_scroll"),
    subtitle = if (dynamicTopBarCollapseOnScroll) {
        "浏览下方动态时收起分类栏；UP 主栏独立收起"
    } else {
        "分类栏固定在顶部；UP 主栏独立收起"
    },
    checked = dynamicTopBarCollapseOnScroll,
    ...
)
```

三个角色：

1. **title** —— 项目名，永远只说「这是什么开关」。
2. **subtitle（说明）** —— 说「打开会怎样」，而且**跟着当前状态换文案**。
   关着的时候告诉你关着是什么行为，开着的时候告诉你开着是什么行为。
   一行，不超过一屏宽，不解释「为什么要有这个功能」。
3. **value（当前值）** —— 说「现在选的是哪个」。
   `SettingClickableItem(title = "下载位置", value = if (customDownloadPath != null) "自定义" else "默认")`；
   `value = "$pluginCount 个已启用"`；`value = cacheSize`；`value = if (isCheckingUpdate) "检查中…" else updateStatusText`。

**它把「说明」和「当前值」当成两个不同字段，绝不合写进同一句。**
我们的 `保存日志到本机` 现在写的是
`"已开启 · 保留 7 天 · 上限 2MB（只存本机，不上传）"` ——
状态、周期、容量、隐私四件事挤在一行副标题里。BiliPai 会拆成
`value = "2 MB / 7 天"`（当前值，右对齐浅色）+ `subtitle = "只存本机，不上传"`（固定说明），
两者字色和位置都不同，用户一眼能分清哪句会变、哪句不变。

再一层：**真正长的解释，一律挪出列表，进弹窗或详情页。**
`DiagnosticsSection` 里那个「详细运行日志」开关，点开时**不写长说明**，
而是弹一个 `AppAlertDialog`，正文 5 句话把「记录什么、脱敏什么、存哪、多大、何时清」说清：

```
将记录应用版本、设备与系统环境、请求结果、播放器状态和错误堆栈。
账号凭据、用户标识、视频标识、搜索词和用户输入会在写入前脱敏。
详细日志仅保存在应用私有目录（最多 256KB），不会自动上传；关闭后会清除详细日志。
```

而且这个弹窗是**决策点闸门**：`onCheckedChange = { if (it) showConsent = true else onChange(false) }` ——
不确认就不开。这就是「隐私/不可撤销的操作必须在决策点上说明」的实现形态。
AGENTS.md 现在那条「界面文案：按钮不自带说明书」已被作废，但这套**分层**仍然成立：
它不是「限制字数」，是「按信息角色决定它出现在哪一层」。

### 1.5 搜索设置：一份手工索引 + 七档打分 + 拼音兜底

`SettingsSearchPolicy.kt` 是全仓最实在的一份可借鉴代码（思想，非抄写）：

- **索引是手写的**：`SETTINGS_SEARCH_INDEX` 每条 = `title / subtitle / section / aliases / focusId / settingId`。
  `aliases` 是给搜索用的同义词表，例如「缓存」这一条挂了
  `["缓存","清理","释放空间","清缓存","删除缓存","空间清理","自动清理","每周清理","5gb","缓存上限"]`。
  **这不是自动生成能替代的东西** —— 它记的是「用户会怎么称呼这个设置」，不是「我们怎么命名它」。
- **打分分七档**（`scoreSettingsSearchMatch` 返回 `SettingsMatch(tier, coverage)`）：

  | tier | 条件 |
  |---|---|
  | 7 | 标题完全等于查询 |
  | 6 | 查询**完全等于**某个别名 |
  | 5 | 查询包含某个 ≥2 字的完整别名/标题 |
  | 4 | 标题或别名以查询开头 |
  | 3 | 标题或别名包含查询 |
  | 2 | **拼音**匹配（`PinyinUtils.matches`，可打 "hs" 命中「缓存」） |
  | 1 | 只有副标题或分组名命中 |

  排序 `tier desc → coverage desc → settingId asc`，再 `distinctBy { settingId }`，取前 20。
- **具体项压制泛入口**：`concreteEntries`（真实设置项目录 `settingsItemDirectory`）优先，
  然后 `legacyEntries = SETTINGS_SEARCH_INDEX.filterNot { it.title in concreteTitles }`，
  并且把已经被具体项认领走的别名从泛条目里剔掉：
  `entry.copy(aliases = entry.aliases.filterNot { normalize(it) in assignedTerms })`。
  注释写得很直白：「避免泛条目靠堆叠别名压过具体结果」。
- **命中后不是打开页面就完事**：`focusId` 走 `SettingsSearchFocusController.submit(target, focusId, settingId, query)` ——
  落地到具体分组并**把命中的文字高亮**（`appSearchHighlightedText`，配套 `AppTextSearchHighlight.kt`），
  长描述只显示命中那一段：`resolveSettingsSearchDescriptionSnippet` 按 `；`/换行切段取含查询的那段，
  超过 140 字则从命中位置前 40 字截窗，前后加省略号。
- **结果行还带面包屑路径**：`path = listOfNotNull("设置", owner, group).joinToString(" / ")`，
  即「设置 / 播放设置 / 播放速度」，用户点之前就知道会被带到哪。
- **搜索历史**：`SettingsSearchHistoryStore` + 空查询时展示历史，`viewModel.recordSearchQuery` 记录。

### 1.6 深浅色与动态取色

- 明暗三态：跟随系统 / 浅 / 深，且在外观页给每个选项配**迷你屏幕示意图**
  （`// 每个选项配迷你屏幕示意图：跟随系统为浅深对角拼接，浅色/深色…`）——
  不是让用户猜「跟随系统」长什么样。
- 深色另有**独立「深色主题风格」**选项与按当前种子生成的深色预览（`DynamicColorPreview`）。
- 定时深色模式是一条**单独的设置项**（`appearance.dark_theme_schedule`，副标题
  「自选开始和结束时间，支持跨午夜自动切换」），不塞在明暗三选一里。
- 动态取色走 **Material Kolor**（`com.materialkolor.dynamiccolor.ColorSpec.SpecVersion`，
  有 SpecVersion 枚举可选），取色来源分层：主题色 / 壁纸 / 自定义 hex / 预设；
  还有 `ThemeContrastPolicy.kt` 做对比度策略。
- **一个值得注意的门控**：`LocalDynamicColorActive` 是 composition local，
  下游组件用它判断「现在是不是动态色生效中」，从而**主动关掉自己的自定义颜色**——
  避免出现「开了动态取色但某个卡片还硬编码品牌色」的脏状态。我们票 25 的自定义色板正缺这层。

### 1.7 逐条对照：我们设置页现在的问题 → BiliPai 的解法

我们 `SettingsScreen.kt`（542 行，单页五块，全部平铺）现状与对照：

| 我们的现状（代码里能看到） | 问题 | BiliPai 的对应做法 |
|---|---|---|
| 「硬朗农业＝黑土＋农械橘的方角网格；账页＝没有格线，用填充色带与隔行底色分区；衬线承担标题与数字，课程是浮起的便签…」10.5sp 三行长文，直接铺在风格按钮上方 | 这是**给设计方案的说明书**，不是设置项说明。用户只是要选一个风格 | 长解释一律进弹窗/详情页；列表里只留 `title + value(当前选择)`。真要解释，做成点开风格后的预览页 |
| 「取色」一行三个小方按钮 + 下面一行 `农业色板 / 自定义 · 这一套还没设，暂用主题色` | 状态行写得像错误提示，且「当前值」和「警告」混在一句 | `value` 字段右对齐显示当前值（"自定义" / "默认" / "2 MB"），状态异常才换字色，且单独成项 |
| 「课前提醒」副标题 `课前 5 / 10 分钟各提醒一次` + 条件追加 `⚠ 还没给通知权限，提醒不会响 —— 关一次再开一次会重新请求` | 第二句是**操作教程**塞进了列表行 | 权限缺失应做成**可点的行**（跳系统设置），提示语进弹窗；副标题只描述行为 |
| 「清除所有数据」副标题两行、列举 8 类被删数据 + 「清完像新装一样，可以重新登录。」 | 唯一一处该长的地方，但它铺在列表里，且和确认按钮分离 | 完全对应它的 consent-dialog 模式：**列表里只留「清除所有数据 · 不可恢复」，8 类明细搬进二次确认弹窗正文** |
| 「已载入 N 门课」+ `SessionText.settingsLine(...)` 一行状态 + 右侧 `只清课表` 文本按钮 | 三个不同角色（计数 / 会话状态 / 动作）挤在一行 | 计数与状态是两件事，应拆成两行；动作放右侧但用 `value` 样式而非说明文字 |
| 全页一个 `verticalScroll` 的 `Column`，没有卡片容器、没有分组标题的统一间距 | 视觉层级只靠 `SectionTitle` 文字撑 | 分组卡片（Miuix 16dp 圆角 / MD3 24dp）+ 标题上下 24/8dp + 行 44dp 最小高 + Miuix 档**不画分隔线**（0dp），靠留白分行 |
| 没有搜索 | 设置项一多就找不到 | §1.5 那套手工索引（我们量小，索引 30 条够，但**别名表 + 拼音 + 落地高亮**三件事都值得做） |
| 图标：几乎没有 | 目录页扫读全靠文字 | 每个设置项一个语义图标，同组图标用 `rememberSettingsSiblingIconTints(n, paletteOffset)` 取**同一色系的不同色**，一组看起来成套 |

其中 `rememberSettingsSiblingIconTints(count, paletteOffset)` 这个手法单独提一下：
它不是给每项随机配色，而是**按「这一组有 n 项」从调色环上均匀取 n 个色**，
`paletteOffset` 让不同分组错开起点。结果是同一卡片内图标互不重复、跨卡片又保持同一色系。
我们做「外观」那一组（主题/风格/取色/字重）时能直接受益。

---

## 2. 组件体系：facade 怎么分层

### 2.1 三层，不是两层

BiliPai 的 design-system 不是「把 Miuix 再包一层」这么简单，它是**三层**：

```
第 1 层  design-tokens           纯数据，7 个 kt 文件：颜色/形状/间距/字体/缓动/进度轨
         AppShapeTokens · AppSpacingTokens · AppTextStyles · Color
         AppMotionEasing（缓动曲线表）· FeedContentTextRoles · MediaProgressTrack
                    ↓ 被谁读
第 2 层  design-system / components     对外唯一契约：App* 前缀的 @Composable
         AppSwitch · AppSlider · AppText · AppIcon · AppCard · AppBadge · AppCheckbox
         AppRadioButton · AppSegmentedControl · AppTextField · AppAlertDialog · AppSnackbar
         AppPreferenceSectionTitle · AppPreferenceGroup · AppSwitchPreference
         AppSliderPreference · AppPreference · AppPreferenceDivider · AppPreferenceGridItem
         AppSearchField · AppHorizontalPager · AppLazyColumn · AppStaggeredGrid
         AppColorPicker · AppNumberPicker · AppDurationPicker · AppGlassMenu …（共 63 个文件）
                    ↓ 按 AppUiStyle 二选一
第 3 层  design-system / renderer       internal，两套 15 文件**一一对应**
         renderer/miuix/AppMiuixSwitch.kt        → 调 top.yukonga.miuix.kmp.basic.Switch
         renderer/material3/AppMaterial3Switch.kt → 调 androidx.compose.material3.Switch
```

第 3 层的两条硬规矩，从代码里能直接看出来：

1. **`internal`**：`internal fun AppMiuixSwitch(...)` —— 业务层根本 import 不到后端，
   只能走 `AppSwitch`。这杜绝了「有人图省事直接用了 Miuix 原生组件，结果 MD3 档下长得不一样」的事故。
2. **文件级一一对应**：15 个 miuix 后端对应 15 个 material3 后端，
   名字严格 `App{Miuix|Material3}{Switch|Slider|Text|Icon|Card|Badge|Checkbox|RadioButton|
   SegmentedControl|Surface|ProgressIndicator|IconButton|ActionPrimitives}`。
   新增一个组件，两侧各加一个，编译器逼你补齐 —— 这比写一条「新组件要双后端」的纪律可靠得多。

### 2.2 切换开关只有一个

```kotlin
enum class AppUiStyle { MIUIX, MATERIAL3 }
val LocalAppUiStyle = staticCompositionLocalOf { AppUiStyle.MATERIAL3 }
```

业务代码**永远不问现在是哪一档**。需要按档分叉时，也不许散着写 `if (LocalAppUiStyle.current == ...)`，
而是走三个专门的**纯函数 policy 文件**：

```
AppPrimitiveRendererPolicy.kt     →  shouldUseMiuixActionPrimitive(uiStyle)
                                     shouldUseMiuixOutlinedTextField(uiStyle, hasPrefix, hasSuffix, …)
                                     resolveMiuixNonGlassChipMetrics()      ← 返回一个数据类，不是散数值
                                     resolveMiuixFabMinSizeDp(small)
AdaptiveListItemPolicy.kt         →  resolveAppClickableItemRenderer(uiStyle, onClick, showChevron, centered)
                                     enum { CUPERTINO, MD3_BASIC, MIUIX_ARROW, MIUIX_BASIC }
AppSegmentedControl.kt            →  resolveAppSegmentedRenderer(usesNativeTabRow)
```

注意两个手法：

- **policy 是纯函数，输入 `AppUiStyle`，输出枚举/数据类**，不碰 Composable。
  所以它能被普通 JVM 单测直接断言 —— `AdaptiveListComponentPolicyTest` 里就是
  `assertEquals(24, spec.groupCornerRadiusDp)`、`assertEquals(0.14f, spec.iconBackgroundAlpha, 0.0001f)`
  这种写法。设计规格被单测钉死，改数值必须同时改测试。
- **`AdaptiveListVisualCapabilities` 这个「能力包」**：
  它把 `componentSpec`（12 个度量）+ `rowSpec`（5 个度量）+ `showExplicitActionChevron`（行为开关）
  打包成一个对象交给 feature 层。注释写得很准：
  > 「Semantic list capabilities consumed by feature screens **without exposing the active UI style**.」

  即：业务页拿到的不是「现在是 Miuix 所以圆角 16」，而是「这一组该用 16、该行该 44 高、这里该画箭头」。
  **业务层读的是结论，不是原因。**

### 2.3 为什么 facade 是活管道、纪律是死规矩（回答 lead 那个问题）

上一轮 `ShapeTokenTest` 那类纪律被主人作废。对照 BiliPai 能看清区别在哪：

| | 纪律 / 测试禁值 | facade / policy |
|---|---|---|
| 约束落在哪 | **写代码的人**身上（「你不许写 `RoundedCornerShape(12.dp)`」） | **结构**上（业务层拿不到后端组件，也拿不到裸数值） |
| 违反时的表现 | 测试红，人工回去改，改的是**测试**而不是设计 | 写不出来，或必须显式加一个 policy 函数（走 PR 讨论） |
| 设计改版时 | 全仓 grep 替换字面量，测试跟着改，越改越脆 | 只改 `resolve*()` 里一处，全部下游自动跟随 |
| 想破例 | 要么忍、要么改纪律，两种都带摩擦 | 加一个 `presentation = FLAT` 之类的**显式参数**，破例被记在代码里 |
| 谁来保证 | 人 + CI | 编译器 + 类型 |

一个具体例子，`SettingsCardGroup`（feature 层）：

```kotlin
SettingsGroup(
    presentation = if (LocalAppUiStyle.current == AppUiStyle.MIUIX)
        AppPreferenceGroupPresentation.CARD else AppPreferenceGroupPresentation.FLAT,
)
```

它没有禁止谁用 FLAT，也没有用测试锁「设置页必须 CARD」。
它把「卡片 vs 平铺」提升成**一个参数**，于是「首页要平铺、设置要成组」这种需求
只是传不同值，不需要修改任何纪律、不需要新增任何豁免注释。
这就是「管道」的含义：**变化被吸进参数里，而不是撞在规则上。**

反面也真实存在：facade 不是免费的。它的代价见 §5。

### 2.4 值得学的五个组件形态

**① 开关行（`AppSwitchPreference`）** —— 参数即信息架构：

```
icon? · title · subtitle? · value? · checked · onCheckedChange
· startAction?（图标位）· endActions?（行尾除开关外的额外控件）· bottomAction?（行下方展开区）
· enabled · holdDownState
```

`startAction / endActions / bottomAction` 三个插槽是关键设计：
「一行」被拆成**主文本 + 头部图标 + 尾部动作 + 下方展开**四个可独立填充的位置，
于是一个组件能吃下几乎所有变体（普通开关行、开关+删除按钮、开关+展开子选项）
而不需要新增组件类。我们的设置页目前每个变体都在手写 `Row`。

**② 单选选择器（`AppSliderDialogPreference` / `SettingsSingleChoicePreference`）** ——
短选项用**分段控件**（segmented），不用弹窗不用下拉：

```kotlin
SettingsSingleChoicePreference(
    title    = "首页推荐来源",
    subtitle = feedApiType.description,     // 当前选择的展开解释
    options  = resolveFeedApiSegmentOptions(),  // AppSegmentOption(value, label)
    selectedValue = feedApiType,
    onSelectionChange = onFeedApiTypeChange,
)
```

`options` 是 `数据类(value, label)` 列表而非字符串列表 —— 值与显示文案分离，
所以枚举可以直接当 key，不用 `indexOf` 猜。超过 3~4 个选项时才升级为
`AppGlassMenu` / `OverlaySpinnerPreference`（Miuix 的下拉/滚轮）。

**③ 滑块（`AppSliderDialogPreference`）** —— 滑块**默认藏在弹窗里**，列表行只显示当前值。
行上写 `缓存容量上限 / 默认 5 GB`，点开才给 `valueRange = 1f..20f, steps = 18` 加一个
`valueFormatter = { "${it.roundToInt()} GB" }`。
理由很清楚：拖动滑块需要按住和精度，直接铺在滚动列表里会和列表手势打架。
另外注意 `AppSlider` 里那段触感代码：**按档触发 + 55ms 节流**
（`nextBucket != lastHapticBucket && nowMs - lastHapticTimeMs >= 55L`），
且 Miuix 后端**不叠加**本地触感（注释：原生滑块自行提供边缘触感）。

**④ 分组头（`AppPreferenceSectionTitle`）** —— 只有一行标题，
但两侧渲染策略完全不同：Miuix 档走 `SmallTitle`（上 24 / 下 8 / 左右 16），
MD3 档走普通 `Text` + `appSearchHighlightedText(title)`（标题也能被搜索词高亮）。
**分组头本身参与搜索高亮**，这处细节说明搜索是当一等公民做的，不是加个搜索框完事。

**⑤ 搜索入口（`AppSearchField` + `AppSearchEntry`）** ——
搜索框本身也在 facade 里（`shouldUseNativeMiuixSearchBar(uiStyle)` 决定用 Miuix 原生还是 MD3），
并且 `showExplicitActionChevron = !shouldUseNativeMiuixSearchBar(uiStyle)`
这一行把「Miuix 档用原生搜索栏时，设置行不画箭头」串了起来 ——
一个能力开关同时决定两处外观，保证成套。

