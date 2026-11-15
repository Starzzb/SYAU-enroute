# ledger-v3 fix1 收尾清单

日期：2026-11-15
构建：`compile-check.bat` → BUILD SUCCESSFUL + EXIT=0（:app:compileDebugKotlin）

## 一、编译

- [x] BUILD SUCCESSFUL
- [x] EXIT=0

## 二、资讯页

- [x] 横幅（RefreshFailLine）、提示条（DeadlineRow）、筛选栏（SearchRow + ChannelFilterRow）三处均无圆角、无底色
- [x] 筛选栏高 36dp（ChannelFilterRow `.height(36.dp)`）
- [x] 日期与标题间隔 10dp（LedgerGutterGap = 10.dp，LedgerRow 日期槽 `end = LedgerGutterGap`）
- [x] 页眉自动收起（滚动位置单调增大收起 / 减小或到顶展开，180ms 高度动画）
- [x] 筛选栏吸顶保留（SearchRow + ChannelFilterRow 在 pager 外）
- [x] 「全部已读」在页头 ⋯ 菜单（HeaderMenu：本频道全部已读 / 全部频道全部已读）

## 三、我的页

- [x] 同组内数值右边缘齐（值列固定 96dp 右对齐、单位列固定 36dp 右对齐，两列常驻）
- [x] 点状引导线点距 11dp（Canvas step = 11.dp.toPx()）

## 四、课表

- [x] 数学底纹是固定背景层（TimetableScreen 外层 Box 第一子项，不参与 Pager/verticalScroll）
- [x] 内容层背景透明（网格格线只画线，格子本身透明；GridArea 不再画底纹）
- [x] 省电四条在（MathBackdropMotion：①只在可见时跑 ②每帧零分配 ③不逐帧重组 ④动画时长=0 时静止）

## 五、待 Lead 真机确认

- [ ] 资讯页：页眉收起/展开手感、筛选栏吸顶、⋯ 菜单里「全部已读」入口
- [ ] 资讯页：日期与标题 10dp 间隔、横幅/提示条/筛选栏无底色圆角的实际观感
- [ ] 我的页：数值列右对齐与点状引导线在真机上的间距观感
- [ ] 课表：底纹固定不动、内容透明叠底、省电动画在真机上的流畅度与耗电
