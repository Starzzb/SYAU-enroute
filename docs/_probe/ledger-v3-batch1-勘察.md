# 账页 v3 · 批次 1 勘察笔记（只读，未改码）

> 勘察日期：2026-11-15。依据：`docs/设计-账页v3-方案.md`、`AGENTS.md` 末节（前端纪律已作废）、
> `FeedScreen.kt`、`UserScreen.kt`、`ui/theme/`、`ui/ledger/`、`ui/user/`、`ui/home/`。
>
> ⚠️ 重要现状：**批 1 的资讯页已经账页化落地**（`ui/ledger/LedgerKit.kt` 已存在且被 FeedScreen 引用），
> 方案 §6「新建 ui/ledger/」已完成；「我的」页仍是旧磁贴网格，未动。

## 1. 资讯页渲染结构（FeedScreen.kt，共 1324 行）

| 构件 | 行号 | 函数 |
|---|---|---|
| 页面入口（路由分流 详情/列表） | 108–127 | `FeedScreen` |
| 列表容器（Box 包 Column 主骨架） | 179–278 | `FeedListView`（内部 Column 起于 180） |
| 常驻页眉（账页） | 181–186 | `LedgerHeader`（`ui/ledger` 包，非本文件） |
| 筛选栏（搜索+未读+藏+清） | 188；定义 374–446 | `SearchRow` |
| 截止提醒条 / 刷新横幅 | 201–218；定义 450/495 | `DeadlineBar` / `RefreshBanner` |
| 频道 chips（横滑） | 221–239；定义 540 | `LazyRow` + `ChannelChip` |
| meta 行 | 241；定义 577 | `ChannelMetaRow` |
| **pager** | 250–277 | `HorizontalPager`（`rememberPagerState`，`beyondViewportPageCount=0`） |
| **列表容器（每频道一页）** | 619–695 | `FeedPage`，内部 `LazyColumn` 起于 **647** |
| 分页脚 | 698–712 | `FeedPageFooter` |
| 详情页 | 1054–1292 | `FeedDetailView` |
| 长按菜单/页头菜单/失败明细 | 911/984/1024 | `FeedQuickActions` / `HeaderMenu` / `FailDetailSheet` |

- **卡片已不存在**：标准卡/紧凑卡/旧组头/分页脚已删除（714 行注释说明），条目改为 `LedgerRow`「行」（`ui/ledger`）。
- 筛选/密度切换不丢：搜索常驻页眉下；密度切换收进页头「⋯」菜单（`HeaderMenu`）。
- 条目行结构：页边槽日期（34dp 右对齐）＋标题 17sp/600＋摘要 13sp/22sp＋元信息 11.5sp（`LedgerKit.kt` 215–316）。

## 2. 我的页磁贴网格（UserScreen.kt，共 581 行）

- 入口与子页栈：`UserScreen`（103–187），`UserRoot` 根页（**209–435**）。
- 磁贴网格行号：
  - 顶条（刷新+齿轮）247–259
  - 教务组头 264；身份卡 274（`ui/home/HomeCards.kt:95 IdentityCard`）
  - 下一场考试 `WideTile` 287；学业两格 `TileRow`+`HalfTile`（培养方案 305 / 加权绩点 312）
  - 空闲教室 `WideTile` 324
  - 工具格 2×2：`TileRow` 349（查教师 350 / 课程大纲 355）、`TileRow` 362（历年课表 363 / 成绩查询 371）
  - 校园组 390–397（校园地图 392）；本机组 401–422（自定义课程 403；`FoldSection` 410 课表/周次/上课提醒/登录态）
  - 版本行 425、`WoodenFish` 432
- 磁贴组件本体在 `ui/user/UserTiles.kt`（`WideTile`/`TileRow`/`GroupHead`/`FoldSection`/`FoldRow`/`HalfTile`/`ToolTile`）；派生标签在 `ui/user/MyPageModel.kt` + `ui/home/HomeModel`。
- **数据来源**：`TimetableViewModel`（`vm`）＋ `AppState`（`ui`）两路注入；渲染层无自己的数据。
- **必须保留的入口**（替换渲染层时一行不能丢）：
  设置、考试、成绩、培养方案、课程大纲、历年课表、校园地图、自定义课程、空闲教室、查教师、登录（身份卡）、手动刷新、
  下一场考试、加权绩点、空闲教室、查教师、课程大纲、历年课表、成绩查询、校园地图、自定义课程、折叠区四项（课表/周次/上课提醒/登录态）。

## 3. InkTokens 颜色档（theme/Theme.kt:71–101 + 31–61）

- `InkTokenSet` 三档墨色（账页方案 §1.2 只认这三档做「淡出」）：**ink / inkSoft / inkFaint**（76–78 行）。
- 其余是面/语义色：paper、card、cardAlt、rule（纸/面/线）；brick、slate、olive、amber、rust（语义）；
  新字段 primary、accent、outline、outlineStrong、hardShadow、info、infoBg（88–100）。
- 亮/暗各一套值；取用入口 `InkTokens.current`（147–152），主色小字用 `primaryInk()`（EditorialTokens.kt:364）。

## 4. NumericFont / 衬线等宽

- `NumericFont`（HardTokens.kt:189）= **打包进 APK 的 IBM Plex Mono**（OFL，约 230KB），三档字重，
  供数字/时间竖向对齐（等宽，但**不是衬线**）。
- **衬线**：`SerifFont = FontFamily.Serif`（EditorialTokens.kt:109，系统衬线 → Noto Serif CJK）。
- **衬线等宽（方案 §3 行号 10.5sp 的答案）**：`LedgerTnum = TextStyle(fontFamily = SerifFont, fontFeatureSettings = "tnum")`
  （LedgerKit.kt:58）—— 衬线 + tnum 数字等宽。已用于行号/页边槽日期/folio。
- 真正的「衬线 + 等宽」字体文件**未打包**，当前组合是系统衬线 + tnum 特性，够用但非严格 mono。

## 5. 两页共用的 Composable 清单（改了互相影响）

- **真正两边都依赖的只有 theme 层**：`InkTokens.current`、`primaryInk()`、`NumericFont`、`SerifFont`、`Space`、`Stroke`、`Form.current`/`FormSpec`。
- **ledger 包（当前只有 Feed 用）**：`LedgerHeader`、`LedgerGroupTitle`、`LedgerGroupBreak`、`LedgerRow`、`LedgerTnum`、`Modifier.ledgerPress`、`LedgerFooter`（已备好未挂）。改这里会动资讯页。
- **FeedScreen 私有共用**：`TextAction`、`SheetScaffold`、`MenuRow`、`categoryColor`、`shortDate`/`hostOf`（本文件内部复用，不影响 UserScreen）。
- **UserScreen 私有共用**：`SubHeader`、`GroupCard`、`Divider`、`StatRow`、`NavRow`（本文件）；`UserTiles.kt` 六件磁贴；`MyPageModel`。
- **跨页共享的外部件**：`ui/home/HomeCards.kt` 的 `IdentityCard`、`HomeFootNote`（UserRoot 用；Feed 不用）→ 改它们会影响我的页。
- 结论：改 `ui/ledger` 影响资讯页；改 `ui/user/UserTiles.kt`/`MyPageModel` 影响我的页；改 `ui/theme` 两页同变。

## 6. 现成按压态工具

- **有**：`Modifier.ledgerPress(ink, onClick, onLongClick, ...)`（LedgerKit.kt:70–100）——
  整行按压 = 行底色 +2% 墨（`ink.copy(alpha=0.02f)`）、`indication = null`（无水波纹/胶囊）、
  基于 `MutableInteractionSource.collectIsPressedAsState`，支持可空长按。正合方案 §3「整行可点 + 2% 按压底色」。
- 旧式裸 `.clickable`（默认水波纹）仍散落两页多处（如 SearchRow 48dp 块、MenuRow、磁贴）；无全局统一收口。
- 批 1 若把「我的」账页化，直接复用 `ledgerPress` 即可。

## 替换边界（给实施者的最短摘要）

- 资讯页：**已完成**（`FeedScreen.kt` 渲染层 + `ui/ledger/LedgerKit.kt`），数据/VM 未动。
- 我的页：替换边界 = `UserScreen.kt` 的 `UserRoot`（209–435）+ `ui/user/UserTiles.kt`（磁贴六件）→
  改为账目行（复用 `LedgerRow` 思路或新账目行件），12 个入口与数据源（`AppState` + `TimetableViewModel`）不动。
- 共用件：theme 层（InkTokens/primaryInk/NumericFont/SerifFont/Space/Stroke）+ `ledgerPress` 按压态。
- 缺：我的页账目行/页脚 folio 尚未做；`LedgerFooter` 已备但壳（底部导航）不在本批范围；无真正的衬线等宽字体（用 SerifFont+tnum 顶）。
