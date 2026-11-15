# 账页 v3 批次 1 勘察笔记（只读，未改码）

日期：2026-11-15　勘察人：前端实施代理
前置：已读 `docs/设计-账页v3-方案.md`、`AGENTS.md`（末节「前端设计纪律：全部作废」）、
`FeedScreen.kt`、`UserScreen.kt`、`ui/theme/` 下 EditorialTokens / HardTokens / Theme。

---

## 0. 与方案 §6 的对齐结论

方案说「替换 FeedScreen / UserScreen / SettingsScreen 的渲染层，数据与 ViewModel 一行不动」。
勘察确认这条路走得通：

- 资讯页：数据全在 `FeedViewModel` + `FeedRows`（纯函数），渲染在 `FeedScreen.kt`。
  替换边界就是 `FeedListView` / `FeedPage` / `StandardCard` / `CompactCard` / `GroupHeader` / `PageFooter`。
- 我的页：数据全在 `AppState`（`ui`）+ `TimetableViewModel`（`vm`）+ `MyPageModel`（纯函数派生）。
  替换边界就是 `UserRoot` + `ui/user/UserTiles.kt` 的一整套磁贴件。
- 两页**没有**共用任何页面级卡片/行组件，只有 theme 层共用（见 §4）。

---

## 1. 资讯页现行渲染结构（`ui/feed/FeedScreen.kt`，共 1772 行）

| 结构 | 位置 | 说明 |
|---|---|---|
| 入口 | `FeedScreen` L108 | detail/list 双路由（`SaveableStateHolder`） |
| 列表主容器 | `FeedListView` L132 | Box → Column：页头/搜索/待办/横幅/chips/meta/pager 逐层叠 |
| 页头 | `FeedHeader` L340（调用点 L167） | 「资讯」20sp + 未读数/条数/更新时间 |
| 搜索栏 | `SearchRow` L407（调用点 L173） | BasicTextField 34dp 高 + 「未读/藏」toggle + 清空 |
| 筛选 toggle | `FilterToggle` L479 | 「未读」「藏」小方块，描边/底色切换 |
| **筛选栏（频道 chips）** | `LazyRow` **L205–224**（调用点）；`ChannelChip` **L615** 定义 | 数字=未读数；长按=频道已读 |
| meta 行 | `ChannelMetaRow` L682（调用点 L226） | 「全部已读」入口 |
| **分页器（频道横向翻页）** | `rememberPagerState` L135；`HorizontalPager` **L235–262** | `beyondViewportPageCount=0`，key=频道名 |
| 页容器/列表容器 | `FeedPage` L744；**`LazyColumn` L771–799** | `contentPadding`、`spacedBy(8dp)`；行窗口 ≤30+组头 |
| 分组头（sticky） | `GroupHeader` **L803**（`stickyHeader` L781） | 组名 + 条数 + 导引线（HARD 实线 / 账页点线） |
| **分页器（条目渲染分页脚）** | `PageFooter` **L868**（`item` L794） | 「上滑加载 · 已显示 shown/total」，本地渲染分页 30+30 |
| **卡片（标准）** | `StandardCard` **L893** | 32dp 身份块 + 标题 15sp + 摘要 2 行 + 底栏；紧急竞赛左侧 3dp brick 色条 |
| **卡片（紧凑）** | `CompactCard` **L1053** | 3dp 频道色条 + 标题 + 单行元信息 |
| 详情页 | `FeedDetailView` L1503 | 分支 2，不在本批范围 |

分页数据：`FeedRows.build`（`ui/feed/FeedRows.kt`，PAGE=30，L57）在 `FeedViewModel` 里算好，
按频道缓存进 `FeedUiState.rowsByChannel`（FeedViewModel L34/L58）。`FeedViewModel.CATEGORIES`
委托 `FeedSources.CATEGORIES`（FeedModels.kt L286：全部/竞赛/教务/就业/通知/学工/新闻）。

---

## 2. 我的页磁贴网格（`ui/user/UserScreen.kt` 581 行 + `UserTiles.kt` 279 行）

- 入口 `UserScreen` L103：子页栈（`UserPage` enum L70–86，saveable），`UserRoot` 是根页。
- **磁贴网格 = `TileRow`（Row + spacedBy）**，定义在 `ui/user/UserTiles.kt` **L115**。
  调用点（`UserScreen.kt`）：L304（学业 1×1 两个 HalfTile）、L349+L362（工具格 2×2）、
  L391（校园）、L402（本机）。
- 磁贴件全在 `UserTiles.kt`：`TileSurface` L61（面 + 1dp 边线 + 方角）、`WideTile` L94、
  `HalfTile` L134、`ToolTile` L164、`GroupHead` L188、`FoldSection` L216、`FoldRow` L269。
- **数据来源 ViewModel**：`UserScreen(ui: AppState, vm: TimetableViewModel)`（L103）——
  状态来自 `AppState`（`ui.profile / ui.credit / ui.scoreStats / ui.freeRooms / ui.syllabusIndex / …`），
  命令来自 `TimetableViewModel`（`refreshHome` 等）。文案派生全部走 `MyPageModel.kt`（纯函数，L16 object）。
- 注意：**「我的」没有独立 UserViewModel**，直接复用 `TimetableViewModel`。v3 只动渲染层即可。

---

## 3. InkTokens / NumericFont / Form 现状

### InkTokens（`ui/theme/Theme.kt`）
- `data class InkTokenSet` 定义在 **Theme.kt L71**，共 **20 个字段**；
  `object InkTokens { val current }` 在 **L147**（`LocalInkTokens` 提供，L135）。
- **文字色档位 = 3 档**：`ink`（正文）/ `inkSoft`（二级）/ `inkFaint`（三级），
  值定义在 `Ink` object（Theme.kt L36–38，农业色板）；语义色另有 brick/slate/olive/amber/rust +
  primary/accent/outline/outlineStrong/hardShadow/info/infoBg 等。
- 三套主题集：HARD（`InkTokenSet.Light/Dark` L103/L118）、SCHOLAR（`ScholarTokens.kt` L109）、
  LEDGER/账页（`EditorialTokens.kt` L113/L136，亮色 `Ledger.Paper` 暖白、`Ink #16211B`、
  `InkSoft #3C4A42`、`InkFaint #5A6A60`）。方案 §1.2 说「只用 ink/inkSoft/inkFaint 三档」→ 已够用。

### NumericFont（`ui/theme/HardTokens.kt` **L189**）
- **IBM Plex Mono**（打包进 APK，OFL，约 230KB），三个字重 regular/semibold/bold。
- 用途：数字/时间竖排对齐；`hardStyle` 给 `tnum, lnum`（L217）。
- 账页（Ledger）模式在 `FormSpec.Ledger.numberFamily = SerifFont`（EditorialTokens.kt L311），
  即账页数字**不用** NumericFont，改用衬线 + tnum。

### 现成「衬线等宽」？
- `SerifFont = FontFamily.Serif`（系统 serif → Noto Serif CJK，EditorialTokens.kt L109）。
- **没有**打包的衬线等宽字体；也没有任何 monospace 衬线。现有的两种竖排方案是：
  ① `NumericFont`（IBM Plex Mono，无衬线等宽）② `SerifFont` + `tnum`（衬线但非等宽）。
- 方案 §3 的「10.5sp 衬线等宽行号」→ **缺**：要么接受 `SerifFont+tnum`（0–9 宽度一致其实已够行号对齐），
  要么新打包一款等宽衬线字体。二选一是批 1 需要拍板的点。

### Form（`ui/theme/EditorialTokens.kt`）
- `data class FormSpec` L236（20+ 字段，HARD 现值 + 账页开关），`LocalForm` L337，
  `object Form.current` L340，`primaryInk()` L366。
- `FormSpec.Hard` = 现状零回归；`FormSpec.Ledger`（L300）= 圆角 10dp / 软阴影 / 交错行带 /
  衬线数字。注意：方案说「token 结构留着当管道，不新增强制语义」——`FormSpec` 里已有的
  `shapes`（ShapeSet）旧纪律已废，但结构还在，可继续当版式参数包用。

---

## 4. 两页共用的 Composable（改了会互相影响）

结论：**页面级组件零共用**；共用面只有 theme 层与一个滚动工具。

共用清单（FeedScreen 与 UserScreen 都直接 import/使用）：
1. `InkTokens` / `InkTokenSet`（Theme.kt）—— 改色板两页全变。
2. `NumericFont`（HardTokens.kt）—— 改字体两页数字全变。
3. `Space` / `Stroke`（HardTokens.kt 常量）—— 间距/线宽两页共用。
4. （仅 Feed）`Form` / `primaryInk` / `cardFloat`（EditorialTokens.kt）—— UserScreen **没**用 Form；
   但 `primaryInk` 的取值逻辑在 Theme.kt，全仓生效。

不共用（各自私有，可放心改）：
- Feed：`FeedHeader / SearchRow / FilterToggle / ChannelChip / ChannelMetaRow / GroupHeader /
  StandardCard / CompactCard / PageFooter / FeedDetailView`（全在 FeedScreen.kt）。
- User：`WideTile / HalfTile / ToolTile / TileSurface / TileHead / GroupHead / FoldSection / FoldRow`
  （全在 UserTiles.kt）+ `IdentityCard / HomeFootNote`（HomeCards.kt，只有 UserScreen 用，
  FeedScreen 不用）。
- `rememberListScrollState`（ui/common/ScrollMemory.kt）—— FeedScreen（L756）与 Exam/Score 页共用，
  UserScreen 没用（UserRoot 用 `rememberScrollState`）。

---

## 5. 现成的按压态工具函数

**没有。** 全仓没有 press-scale / press-fade / 「行底色 +2%」这类统一按压工具。
现状只有两种写法：
- 默认 `clickable` / `combinedClickable`（水波纹 ripple，Material 默认）；
- `indication = null` + 自绘（AcademicSheets L208、FeedScreen L1302、CourseDetail L109/L354、
  CourseQuick L99/L184/L212、LedgerTimetable L88、TimetableScreen L809/L1211）。
方案 §3「按压态 = 行底色 +2%」需要**新建**一个 Modifier/工具（例如 `pressInk(delta=0.02)`），
批 1 内两个页面可共用这一个新工具，放到 `ui/ledger/` 即可，不动旧页。

---

## 6. 批 1 落地缺口速览

| 缺什么 | 位置建议 | 说明 |
|---|---|---|
| 页眉/页脚/行号页边/组短横线/folio 组件 | 新建 `ui/ledger/` | 方案 §6 已指定，只服务这套主题 |
| 「衬线等宽」字族 | theme 或 ledger 内 | 现无；可用 `SerifFont+tnum` 替代或新打包字体 |
| 按压态工具（行底色 +2%） | 新建（ledger 内） | 现无任何统一按压工具 |
| 无卡片行布局（账目行右对齐成列） | `ui/ledger/` | UserScreen 磁贴整套替换时新建 |
| folio 真实组序号 | 数据侧读取现有 rows/分组 | 方案 §7.4 要求不编造 |

不动：`data/`、`FeedViewModel/FeedRows/MyPageModel/TimetableViewModel`、`ui/map/`、票 33 Pager 接口。
