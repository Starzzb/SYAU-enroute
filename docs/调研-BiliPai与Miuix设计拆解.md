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


---

## 3. 动效参数实测 ★ 本节给票 33（课表 Pager）直接用 ★

> **抄数值的许可边界**：以下**数值**（stiffness / damping / response / ms / dp / alpha）
> 与**缓动曲线控制点**属思想层面，可以直接用；**函数名、注释文案、代码结构不得复制**。
> 每条都标了出处文件，便于回查。BiliPai 侧的出处是 GPL 仓库（只读数），
> Miuix 侧的出处是 Apache-2.0 仓库（可读数也可引依赖）。

### 3.1 ★ Pager：这是课表那侧要的三行 ★

```kotlin
// 出处：miuix / miuix-ui / top.yukonga.miuix.kmp.utils.PagerGestureUtils.kt:80（Apache-2.0，可直接采用）
val PagerNavigationSpringSpec = spring<Float>(
    stiffness           = 322.2f,
    dampingRatio        = 32.31f / (2f * sqrt(322.2f)),   // ≈ 0.899（欠阻尼，轻微过冲后收敛）
    visibilityThreshold = 0.5f,
)
```

配套用法（三条都是实测，不是猜的）：

| 项 | 值 / 做法 | 出处 |
|---|---|---|
| snap 动画 | `PagerDefaults.flingBehavior(state, snapAnimationSpec = PagerNavigationSpringSpec)` | BiliPai `AppHorizontalPager.kt` + Miuix 注释 |
| 程序翻页 | `scroll(MutatePriority.UserInput) { animateToPage(...) }` —— **用 UserInput 优先级，让焦点滚动打不断翻页** | Miuix `springAnimateToPage` |
| 跨轴手势 | 自定义 NestedScrollConnection：`onPostFling = Velocity(available.x, 0f)` —— **只吃横轴剩余速度，纵轴整条留给页面内容** | Miuix `PagerGestureNestedScrollConnection` |
| 翻页驱动方式 | `animate(0f → distance)` 累积 `scrollBy(delta)`，而不是 `animateScrollToPage`；**在同一个 mutation 内收口**，注释写明「取消时不会把新手势 snap 到旧目标」 | Miuix `animateToPage` |
| 默认 beyondViewportPageCount / snapPosition | 沿用 `PagerDefaults.BeyondViewportPageCount` 与 `SnapPosition.Start` | BiliPai 直接透传 |

**对课表周翻页的具体建议**：`dampingRatio ≈ 0.90` 是「有一点点落位感但不晃」的值。
一周 7 天这种短距离 Pager，我建议直接用 `stiffness=322.2f, dampingRatio=0.9f, visibilityThreshold=0.5f`；
若想更跟手就只把 stiffness 提到 `~420f`（BiliPai 自己的 `interactiveSnapSpring` 就是 420f/0.78）。
**别用 `dampingRatio=1f` 做 Pager** —— 那是「死到位」，滑动翻页会显得迟钝。

### 3.2 弹簧总表（BiliPai 侧，按用途）

| 用途 | dampingRatio | stiffness | 备注 | 出处 |
|---|---|---|---|---|
| 软落位（通用） | 0.86 | `StiffnessMediumLow`（≈1660） | 面板/卡片归位 | `AppMotionTokens.softLandingSpring` |
| 交互吸附 | **0.78** | **420** | 开关、拖拽吸附 | `interactiveSnapSpring` |
| 底栏展开 | **0.74** | **420** | 注释：一条连续轨迹 + 克制的过冲 | `scrollCompactExpansionSpring` |
| 下拉刷新释放 | **0.96** | **520** | 几乎临界阻尼，不回弹 | `pullRefreshReleaseSpring` |
| 表达性吸附 | 0.72 | 520 | internal | `expressiveSnapSpring` |
| 按压反馈 | **1f** | **1000** | visibilityThreshold `0.001f`，**不过冲** | `pressFeedbackSpring` |
| 选中态 | 0.82 | 500 | | `selectionSpring` |
| Tab 指示器 | 0.70 | `StiffnessMedium`（≈3800） | 弹性最明显的一档 | `indicatorSpring` |
| 空间/几何 | 0.82 | **380** | `resolveSpatialSpec`，尺寸变化 | `AppMotionTokens` |
| 底栏图标 scaleX | **0.60** | **250** | threshold 0.001 | `BottomBarMotionSpec` L62 |
| 底栏图标 scaleY | **0.70** | **250** | 与上行成对（X 比 Y 更弹） | `BottomBarMotionSpec` L67 |

### 3.3 Folme（HyperOS 官方阻尼体系）—— Miuix 侧，Apache-2.0

`folmeSpring` 的参数是 **(damping, response 秒)**，换算公式（记下来就有用）：

```
stiffness = (2π / response)²        →  response 0.35s ⇒ stiffness ≈ 322.2f
damping   = dampingRatio
```

**注意这条**：上面 Pager 那个 `322.2f` 就是 `response = 0.35s` 的换算结果。
也就是说 BiliPai 的 Pager 弹簧和 Miuix 的「默认交互弹簧」是**同一个物理量**，不是两套体系。

| 用途 | damping | response | 换算 stiffness | 出处 |
|---|---|---|---|---|
| Miuix 默认交互 | 0.95 | 0.35s | 322.2 | BiliPai `folmeDefaultSpring` / Miuix `PressExitSpring` |
| 弹窗窗口 | 0.90 | 0.30s | ≈438.6 | `folmeDialogSpring` |
| 底部抽屉回弹 | 0.85 | 0.40s | ≈246.7 | `folmeBottomSheetSpring` |
| 按压下沉回弹 | 0.80 | — | 直接用 **stiffness 600** | `folmePressSpring` |
| 按压进入 | **1.0** | **0.2s** | ≈986.9 | Miuix `PressEnterSpring` |
| 按压离开 | 0.95 | 0.35s | 322.2 | Miuix `PressExitSpring` |
| 悬停进入 | 1.0 | **0.6s** | ≈109.7 | Miuix `HoverEnterSpring` |
| 悬停离开 | 0.96 | 0.2s | ≈986.9 | Miuix `HoverExitSpring` |
| 展开（不折叠回弹） | **1.0** | **0.35s** | 322.2 | BiliPai `folmeExpandSpringSpec` |
| 展开时淡入 | **1.0** | **0.25s** | ≈631.7 | `folmeExpandFadeSpec` |

**按压的手感配方（这套值得整体搬）**：进**快且硬**（damping 1.0 / 0.2s），出**慢且软**（0.95 / 0.35s）。
配合 Miuix 的 alpha 增量表：hover `+0.06`、focus `+0.08`、press `+0.10`、holdDown `+0.10`（可叠加）。
`damping=1.0` 用在按压上是刻意的 —— 手指按下的反馈**绝对不能过冲**，否则像果冻。

### 3.4 过渡时长（单一真相源，两档各一套）

`AndroidNativeVariantThemePolicy.kt` → `resolveAndroidNativeChromeTokens(uiStyle)`：

| token | Miuix 档 | MD3 档 |
|---|---|---|
| `motionStandardMillis` | **180** | **200** |
| `motionEmphasizedMillis` | **240** | **300** |
| `expressiveMotionDurationMillis` | **180** | **200** |
| `resolveExpressiveSpec` 时长 | 150ms（曲线 EmphasizedExit） | 180ms（Md3EmphasizedAccelerate） |
| `containerCornerRadiusDp` | 20 | 24 |
| `pillCornerRadiusDp` | 22 | 28 |
| `selectedContainerAlpha` | 0.18 | 0.14 |
| `tonalSurfaceElevationDp` | **0**（Miuix 不用阴影，靠色差分层） | 3 |
| `denseHorizontalSpacingDp` | 16 | 18 |
| `rowMinTouchTargetDp` | 44 | 48 |

**规律**：Miuix 全线比 MD3 **快 20~60ms、更扁平（elevation 0）**。
`AppMotionTokens` 的注释也点明了这一点：「MIUIX resolves to denser tween durations」，
并且「Durations are single-sourced from `resolveAndroidNativeChromeTokens` so both token layers stay in sync」——
**时长只在一个函数里定义，motion token 层只是读它**。我们没有这层，值散在各屏。

### 3.5 缓动曲线全表（`AppMotionEasing.kt`，design-tokens）

| 名字 | cubic-bezier | 用途 / 注释 |
|---|---|---|
| `EmphasizedEnter` | **0.22, 1, 0.36, 1** | 入场（Apple 式强调减速） |
| `EmphasizedExit` | **0.32, 0, 0.67, 0** | 离场（先快出） |
| `Continuity` | **0.20, 0.90, 0.22, 1.00** | 连续位移（BiliPai 自定义，Miuix 档 standard 用它） |
| `GentleEnter` | 0.18, 0.80, 0.20, 1.00 | 柔和入场（internal） |
| `IosEaseInOut` | 0.42, 0, 0.58, 1 | 可逆的原位变形 |
| `SoftClear` | **0.40, 0, 0.55, 0.30** | 景深返回「先留住模糊再柔化」，注释：避免 Continuity 在 1→0 时过早掐清 |
| `M1Standard` | 0.4, 0, 0.2, 1 | 与平台 `FastOutSlowInEasing` **同值**，注释明确「替换仅收编引用来源，不改变任何现有手感」 |
| `M1Decelerate` | 0, 0, 0.2, 1 | |
| `Md3EmphasizedDecelerate` | 0.05, 0.70, 0.10, 1.00 | MD3 官方 |
| `Md3EmphasizedAccelerate` | 0.30, 0, 0.80, 0.15 | MD3 官方 |
| `Md3Standard` | 0.20, 0, 0, 1 | MD3 官方 |
| `Md3StandardDecelerate` | 0, 0, 0, 1 | MD3 官方 |
| `Md3StandardAccelerate` | 0.30, 0, 1, 1 | MD3 官方 |
| Tab 指示器 `ease` | 0.25, 0.1, 0.25, 1（Flutter 默认曲线） | 注释：Flutter TabController 的 ease + TabBar 弹性边缘 |
| Nagram 借鉴的 `EASE_OUT_QUINT` | 0.23, 1, 0.32, 1 | 出处见 `THIRD_PARTY_NOTICES.md`（GPL，仅数值） |

Tab 指示器还配了 `DurationMillis = 300` 与两条**三角函数边沿曲线**：
`decelerate = sin(ease(t) · π/2)`、`accelerate = 1 − cos(ease(t) · π/2)` —— 让指示器在首尾边缘弹性伸缩。

### 3.6 入场动效：一个 progress 派生三个属性（最值得搬的结构）

`AppEntranceMotion.kt`。核心思想：**不要用 `AnimatedVisibility` 分别淡入+平移+缩放**，
而是让一个 `spring` 驱动单一 `progress 0→1`，再从 progress **非线性派生**三个属性：

```
alpha        = clamp(progress / alphaLeadFraction, 0, 1)     ← 淡入快于位移：先看清、再落位
translationY = offsetDp · (1 − progress)
scale        = initialScale + (1 − initialScale) · progress
```

按设备屏宽分三档（`MotionTier`），实测参数：

| 档位 | dampingRatio | stiffness | staggerStep | maxStagger | offsetDp | initialScale | alphaLead |
|---|---|---|---|---|---|---|---|
| Reduced（小屏/降级） | 0.96 | 520 | **12ms** | **72ms** | 8dp | 0.99 | 0.70 |
| Normal（主流） | 0.90 | 380 | **22ms** | **160ms** | 16dp | 0.97 | 0.55 |
| Enhanced（大屏） | 0.82 | 320 | **26ms** | **220ms** | 22dp | 0.94 | 0.50 |

`progress` 弹簧阈值 `visibilityThreshold = 0.001f`；错峰延迟 `min(index · step, maxStagger)`。

**两个设计决定值得注意**：
1. **`maxStagger` 有上限** —— 第 30 项和第 8 项延迟一样，长列表尾部不会等到天荒地老。
   这是所有 staggered 入场都应该有、但常常没有的一行。
2. **`alphaLeadFraction < 1` 的意思**是「alpha 比几何先到 1」，
   注释：「让淡入快于位移收敛，视觉上先看清、再落位，更干净」。
   这条能直接治「入场动画看着糊成一团」的问题。

### 3.7 骨架屏 / Shimmer（★ 这里要纠正线索 ★）

**BiliPai 没有用 compose-shimmer，而且是主动移除的**（根 `build.gradle.kts:507` 原话：
「compose-shimmer 已移除：全仓 0 处 import」，并有 `VideoDetailSkeletonStructureTest` 断言不再使用）。
它换成了自研的**线性扫光 + 双色脉冲**，实测参数：

```
扫光位移：infiniteRepeatable(tween(1000ms, LinearEasing), RepeatMode.Restart)
          initialPosition = -0.5f → targetValue = 1.5f     （即 2.0 个宽度 / 秒）
渐变停靠： 0.1f→base  0.3f→highlight  0.5f→highlight  0.7f→base
渐变端点： start = (w·pos, h·0.35)   end = (w·pos + w, h·0.95)    ← 斜着扫，不是纯横向
脉冲底色 alpha（light）：min 0.06 / max 0.11      ← 极淡
脉冲底色 alpha（dark） ：min 0.10 / max 0.16
静态骨架 alpha（light / dark）：0.10 / 0.14       ← 取脉冲区间中值
骨架块几何：封面内缩 inset = 高度 · 0.18；文字行宽 0.9 / 0.5、0.92 / 0.62 / 0.4 等
```

四条**判得比我们细的规则**，比数值更有价值：

1. **`NaN` 当哨兵表示「静态占位」**：`rememberSkeletonSweepState` 在不该动时返回 `Float.NaN`，
   下游 `if (!position.isFinite()) return SolidColor(base)` —— **不调动画、不建 infiniteTransition**。
   省的是每帧 recomposition，不是 if 判断。
2. **四重门控**：`!animated || !用户开关 || 页面不可见(rememberVisualWorkActive) || 系统减弱动效` → 一律静态。
   骨架呼吸默认**关**（`SkeletonSettingsStore breathingEnabled ?: false`），是个用户可开选项。
3. **「原地替换」的骨架必须用固定色**，注释解释得非常清楚：
   > 「与脉冲扫光不同：凡是『骨架与真实内容共用同一个 LazyList、靠 item 身份原地替换』的页面，
   > 都应该用固定色。运动中的灰块与随后的内容淡入叠加，观感上就是『闪』；
   > 固定色只负责占位，切换时只剩『内容淡入』这一件事。」
4. 骨架里 `userScrollEnabled = false` —— 加载中的列表**不给滚**。

### 3.8 系统「减弱动效」的处理（一行代码的量，但必须做）

`ReduceMotion.kt`：读 `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`，
并用 `ContentObserver` 注册 `Settings.Global.getUriFor(ANIMATOR_DURATION_SCALE)` 实时跟随，
注释特别写了「注册后再同步一次，避免注册窗口内的变更被漏掉」。
命中时入场参数直接换成 `INSTANT_ENTRANCE_SPEC`（`animate=false`，等价于定格终态）——
**不是把时长除以 2，是直接不播**。

### 3.9 其它零散数值（可查）

- 底栏形变/惯性：`baseResistance 1.04`、`overscrollResistance 0.34`、
  `flingProjectionTimeSeconds 0.24`、`movingVelocityThresholdPxPerSecond 32`、
  `speedProgressDivisorPxPerSecond 1150`、`dragProgressFloor 0.24`、`panelOffsetMaxDp 6`、
  `deformationScaleXDelta 0.42`、`deformationScaleYCompressionRatio 0.58`（`BottomBarMotionSpec.kt`）。
- 导航滑动弹簧的**时长↔刚度换算**（`NavigationSlideSpring.kt`）：
  基准 `350ms ↔ stiffness 700`，公式 `stiffness = 700 · (350/duration)²`，上限 `2000`，`dampingRatio 1f`。
  意思是「想让某段滑动更快，只调时长，刚度按平方反比自动跟上」—— 避免只缩短时长导致动画没劲。
- 搜索弹性反馈：`strength 0.6f`；高光叠加 `White.copy(alpha = 0.08 · amount)`（BlendMode.Plus）与 `0.25 · amount`。

### 3.10 一句总结给票 33

**Pager 用 `stiffness 322.2 / dampingRatio ≈0.90 / visibilityThreshold 0.5f`；
程序翻页用 `MutatePriority.UserInput`；跨轴 fling 只吃横轴剩余速度；
时长统一到 180（Miuix 档）/ 200（MD3 档）ms；入场若要加，用单 progress 派生 + stagger 上限 160ms。**
数值出处全部标了文件，抄数值合规，抄代码不合规（见 §4）。


---

## 4. 协议结论

### 4.1 三张牌照，三种做法

| 对象 | 协议 | 实测出处 | 我们能做什么 |
|---|---|---|---|
| **BiliPai**（`jay3-yy/BiliPai`） | **GPL-3.0** | 仓库 `LICENSE` 首行 GPLv3；README 徽章 `License-GPLv3`；`THIRD_PARTY_NOTICES.md` 多处声明派生代码按 GPL-3.0 分发 | ❌ **一行代码、一个资源文件都不能进我们仓库**。我们 MIT，引入 GPL 源码会把整个组合作品的分发条件变成 GPL。只能读、只能记思想 |
| **Miuix**（`compose-miuix-ui/miuix`） | **Apache-2.0** | `LICENSE` = Apache 2.0；每个源文件头两行 `SPDX-License-Identifier: Apache-2.0` | ✅ **可以直接引依赖**。若引，需在关于我们页列 Apache-2.0 声明 + NOTICE（若有） |
| **Haze**（`chrisbanes/haze` 2.0.1） | **Apache-2.0** | 上游 `LICENSE` 实测为 Apache 2.0 | ✅ 可引 |
| **Compose Cupertino**（`alexzhirkevich` 0.1.0-alpha04） | ⚠️ **本轮未取得 LICENSE 文件**（raw 抓取 404，未继续核对） | 未确认 | ❌ **别引**。且我们只需要它「居中文本项」那一个形态，自己写 30 行比引入一个 alpha 依赖划算 |
| **ITGSA SharedViewKit**（`gsai.sdk:sharedview-kit:1.0.0.5`） | **未知**，来自联盟 Nexus `http://nexus.itgsa.com:5566`（明文 HTTP） | BiliPai `settings.gradle.kts` + notices | ❌ 别碰：来源不透明 + 非 HTTPS 仓库 |
| **Telegram / Nagram / PiliPlus 资源与动效代码** | GPL-2.0-or-later / GPL-3.0 | notices 逐条列了 commit 号 | ❌ 这些是 BiliPai 里**传染性最强**的部分（ThanosEffect 着色器、Profile 头部几何、缓冲 webp），更是碰都不能碰 |

### 4.2 「抄数值」这条线画在哪

- **纯数值不构成受版权保护的表达**：`stiffness = 322.2f`、`durationMillis = 180`、
  `cubic-bezier(0.22, 1, 0.36, 1)`、`offset 0.3/0.5/0.7 的渐变停靠`、`alpha 0.06/0.11` ——
  这些是**参数事实**，不是代码。BiliPai 自己也是这么处理上游的：
  notices 里写「Motion parameter observations were taken from a Xiaomi HyperOS device ROM…
  The application fallback motion controller is **authored locally** with Compose animation APIs;
  **no decompiled ROM implementation is bundled**」。
  它划的线就是「观察数值 → 自己写实现」。我们照这条线走。
- **反过来，函数名 + 注释文案 + 代码结构是表达**。
  尤其 BiliPai 那些中文注释（「分组名描述内容范围,不复读分类名」这类）是作者原话，
  **不要往我们代码里粘**。要在文档里引用可以（文档注明出处），进源码不行。
- **BiliPai 的 `App*` facade 命名风格别照抄成 `App*`**。
  它叫 `AppSwitch` 是因为它要同时桥接 Miuix 与 MD3；我们只有 Material 3 一套后端，
  这层桥接目前没有收益（理由见 §5.3）。

### 4.3 若真要引 Miuix：可行性核对（本轮实测）

**坐标可用**：Maven Central 实测 `top.yukonga.miuix.kmp` 下有
`miuix-ui / miuix-ui-android / miuix-core / miuix-preference(-android) / miuix-blur / miuix-icons / miuix-squircle / miuix-shader / miuix-nav`，
最新 **release = 0.9.4**（历史 0.9.0→0.9.4，含 0.9.4-rc01）。

⚠️ 两个必须知道的坑：

1. **BiliPai 用的不是 Central 的那份**。它锁 `0.9.4-b7a5f08f-SNAPSHOT`，走 GitHub Packages 且要凭据
   （`settings.gradle.kts` 注释：「Authentication is required even for this public package」）。
   也就是说它跑的是**带自家补丁的快照版**（另外还把 `miuix-navigation` 整个 fork 进仓库，
   注释写「to retain the video-return patches」）。**别按 BiliPai 的坐标抄**。
   `miuix-glass` 在 Central 上实测 **404**（只有源码仓库里有）—— 玻璃那套我们引不到。
2. **SDK 版本**：Miuix 自身 `BuildConfig.COMPILE_SDK = 37`、`MIN_SDK = 24`。
   我们 `compileSdk = 35 / minSdk = 26` —— minSdk 兼容，
   **compileSdk 37 是风险项**（引依赖前必须单独验证，不是本轮结论）。

**我的判断**：票 35 之后先**不要引**。理由是 §5.3 —— 它的价值在我们用不上的那一半，
而我们要的那一小块（`SwitchPreference` 的行结构、扫光参数）自己实现的成本低于引一个 KMP 依赖的风险。

---

## 5. 与我们场景的适配判断

### 5.1 两个 App 根本不在同一种内容密度上

| 维度 | BiliPai | 沈农在途 |
|---|---|---|
| 设置项总量 | 数百项（`SettingsManager.kt` 单文件 **457KB**） | 约 20 项（`SettingsScreen.kt` 542 行一屏到底） |
| 用户主动找设置吗 | **是**：画质/倍速/弹幕/解码/手势，人人调、天天调 | **基本不**：装完机调一次主题+提醒，之后不再进 |
| 每屏信息密度 | 视频卡片流，缩略图+标题+UP+时间+播放数，必须做减法 | 课表/成绩/通知，字段少而**必须准** |
| 主题是不是卖点 | **是**：Miuix/MD3 双档、液态玻璃、壁纸取色、开屏动画、图标、彩蛋 | 不是。主人要的是「像我们自己的」，不是「能换皮肤」 |
| 动效预算 | 有：内容型 App，手感是留存 | 有限：工具型，卡顿一秒比不炫更伤 |

**推论**：BiliPai 大量设计是「**因为它内容密度高才成立**」。搬错方向会让我们的设置页变成
一个只有 20 项内容却穿着三级导航的壳。

### 5.2 别误搬清单（每一条都给了「它为什么成立」）

| 它的做法 | 它为什么需要 | 我们搬了会怎样 |
|---|---|---|
| **三跳结构**（目录 → 分类页 → 叶子页） | 设置项数量到「一屏放不下、搜索才有意义」的量级 | 20 项做三级，用户点两次才看到开关，比现在更差 |
| **设置搜索 + 手工别名表 + 拼音** | 数百项找不到是真实投诉来源 | 收益≈0，成本很高（别名表要维护）。**等我们超过 50 项再考虑** |
| **Miuix / MD3 双 UI 风格档** | 它把「换皮肤」当产品功能卖 | 我们上一轮刚因为「按纪律做出来仍不合格」把设计约定全废；再开双档等于把同一份设计做两遍 |
| **液态玻璃 / Haze 全套** | Android 12+ 的视觉噱头，且有 `SDK_INT >= 33` 门控与降级 | 我们是工具 App，玻璃只让文字对比度变差；且真机是 vivo Android 16，不是它的目标场景 |
| **`App*` facade + 双 renderer 30 个文件** | 为了后端可换 | 见 5.3，我们现在只有一套后端，这层是纯开销 |
| **每行一个语义图标 + 同组多彩取色** | 数百行同构内容需要扫读锚点 | 20 行加图标会显得拥挤且像系统设置抄来的。**只在入口型行（调试日志/关于）用箭头不用图标** |
| **44dp 视觉高度 + 内部补命中区** | 它的 Miuix 档字号小、行多 | 方向对，但具体 dp 要按我们自己的 13sp/10.5sp 字阶重测 |
| **Consent 弹窗那 5 行长文** | 它的日志会记录请求与播放器状态，法务敏感 | 结构可以搬（见 5.4 第 4 条），**内容别搬** |

### 5.3 关于「facade 是不是我们现在需要的东西」

Lead 问「纪律是死规矩，facade 是活管道，区别在哪」—— 区别见 §2.3。但**要不要现在就建**是另一问。

BiliPai 的 facade 之所以值钱，是因为它有**两个真实后端**且**用户会在运行时切换**。
我们只有一个 Material 3、没有切换需求。此时套同款三层会得到：

- 63 个契约文件 + 30 个 renderer 文件，**全部只有一个实现**；
- 每次改设计要穿两层空壳；
- 「活管道」的收益（一处 `resolve*()` 改动全局跟随）**在只有一档时等于零**。

**所以正确答案不是照它的结构建层，而是把它最有用的那一半单独拿过来**：
`policy = 纯函数返回数据类 + JVM 单测钉死数值`。
即：不建 `AppSwitch` 这种转发壳，**建 `SettingsMetrics(style): RowMetrics` 这种函数**，
让所有屏幕从这里取 44dp / 16dp / 24-8dp，测试断言 `assertEquals(...)`。
这样「纪律」变成「有单一来源的可查数值」，而不是「禁值列表 + 棘轮」——
既不重演上一轮被作废的禁值纪律，又拿到 BiliPai 分层里真正解决问题的部分。

### 5.4 该搬的（就这 8 条，按性价比排）

1. **`title / subtitle(说明) / value(当前值)` 三字段分离**，且 **subtitle 随状态换文案**。
   → 直接治「设置项 + 长小字堆在一起」。落点：`保存日志到本机` 拆成
   `value = "2 MB / 7 天"` + `subtitle = "只存本机，不上传"`。
2. **长解释一律进弹窗/详情页，但隐私与不可撤销操作必须在决策点上说全**。
   → 「清除所有数据」的 8 类明细从列表行搬进二次确认弹窗正文；
   「保持登录（自动续期）」开启时也上确认弹窗（它涉及存密码，正好符合 AGENTS.md 那条仍然有效的例外）。
3. **分组标题不许复读上级名**（「隐私与权限里还有隐私与权限」）。我们「外观」块下的取色/风格分组同样适用。
4. **行的密度做单一来源**：`insideHorizontal 16 / insideVertical 12–16 / minTouch 44（视觉）+ 内部补命中 / 分组标题上 24 下 8 / 卡片圆角 16 或 24`，
   用纯函数 + 单测锁住（§5.3）。**注意 Miuix 档 `dividerThickness = 0f` —— 靠留白分行而不画线**，
   这跟我们账页风格「没有格线、用色带分区」是同一个手感方向。
5. **Pager 与入场的动效参数整包搬**（§3.1 / §3.6）：
   `322.2 / 0.90 / 0.5f`、`MutatePriority.UserInput`、单 progress 派生 + `alphaLeadFraction`、
   `maxStagger` 上限、`ANIMATOR_DURATION_SCALE == 0` 即完全不播。
   这几条与内容密度**无关**，是通用手感，可以照搬。
6. **骨架屏的三条判断**（§3.7）：不可见/减弱动效/未开开关时**不建 infiniteTransition**；
   原地替换的骨架用**固定色**不用脉冲；加载列表 `userScrollEnabled = false`。
   → 我们课表/成绩的加载态正好是「原地替换」那种，第 2 条能直接解决"闪一下"的问题。
7. **目录行带一句「这页里有什么」的副标题**（不是宣传语）。
   → 我们若把「关于」「调试」「数据」做成入口行，副标题写成内容列举。
8. **`value` 显示当前值而不是显示状态机输出串**。
   → 我们的 `ui.credStatus.ifBlank { … }` 把状态文本当值，BiliPai 的写法是
   `value = if (path != null) "自定义" else "默认"`：**值域是枚举的、短的**。

### 5.5 一句话给主人

BiliPai 教我们的不是「把设置页做成三级 + 搜索 + 玻璃」，而是两件事：
**① 一行设置里，"是什么 / 会怎样 / 现在是哪个" 是三种不同文字，各占一个固定位置；
② 长解释不在列表里，但在不可撤销的那一下必须给全。**
再配上 §3 那批动效数值 —— 那是唯一一处我们可以直接、无损用上的硬通货。

---

## 附：本轮取证位置（复现用）

```
O:\dev\refs\BiliPai@35135a1
  gradle/libs.versions.toml · settings.gradle.kts · build.gradle.kts:431,501-517
  THIRD_PARTY_NOTICES.md · LICENSE
  design-tokens/.../AppMotionEasing.kt · AppShapeTokens.kt · AppSpacingTokens.kt
  design-system/.../theme/AndroidNativeVariantThemePolicy.kt · UiPreset.kt · DensityScale.kt
  design-system/.../ui/motion/AppMotionTokens.kt · AppEntranceMotion.kt · AppTabIndicatorMotion.kt
      · NavigationSlideSpring.kt · VerticalContentRevealMotionPolicy.kt · BottomBarMotionSpec.kt
  brand-motion/.../ReduceMotion.kt
  design-system/.../ui/components/  AppSwitch.kt AppSlider.kt AppHorizontalPager.kt
      AdaptiveListItemPolicy.kt AdaptivePreferenceComponents.kt(76KB) AppPrimitiveRendererPolicy.kt
      AppSelectionPreferenceComponents.kt AppPrimitiveComponents.kt(69KB) AppGlassMenu.kt
  design-system/.../ui/renderer/{miuix,material3}/  各 15 文件
  design-system/src/test/.../AdaptiveListComponentPolicyTest.kt
  app/.../core/ui/skeleton/{SkeletonBreathing.kt,ContentLoadingSkeletons.kt}
  app/.../core/store/SkeletonSettingsStore.kt · SettingsManager.kt(457KB)
  app/.../feature/settings/SettingsSearchPolicy.kt · ui/SettingsSections.kt(2452 行)
  app/.../feature/settings/screen/SettingsSearchScreen.kt · AppearanceSettingsScreen.kt(180KB)

O:\dev\refs\miuix@d8efe18
  LICENSE(Apache-2.0) · README.md（依赖坐标）
  miuix-ui/.../anim/MiuixEasing.kt（folmeSpring 与 stiffness=(2π/response)² 换算）
  miuix-ui/.../utils/PagerGestureUtils.kt:80（PagerNavigationSpringSpec）
  miuix-ui/.../utils/MiuixIndication.kt:25-33（按压/悬停弹簧与 alpha 增量）
  miuix-preference/.../preference/{SwitchPreference,ArrowPreference,SliderPreference,
      RadioButtonPreference,CheckboxPreference,OverlaySpinnerPreference}.kt
  miuix-ui/.../basic/{ListPopup.kt,Card,…} · layout/ListPopupLayout.kt · theme/MiuixTheme.kt
  build-logic/src/main/kotlin/BuildConfig.kt:11,14（COMPILE_SDK 37 / MIN_SDK 24）

Maven Central 实测（2026-11-15）：top.yukonga.miuix.kmp:* release=0.9.4；miuix-glass → 404
```

