# 26: 列表滚动记忆 + 「关于」收口到独立页

**What to build:** ① 任何列表「进子页 → 返回」都不回到顶部（同级回退恢复、换筛选回顶部、杀进程不恢复）；② 设置页「关于」只留一行入口，重复长文字全部搬到 `AboutScreen`，并在那页补齐版本号 / commit 短码 / 构建日期 / 更新入口。

**Blocked by:** 无

**Status:** ready-for-human

---

## 1. 为什么做（主人原话 + 现状证据）

> 「支持滑动记忆，不能进入下一个新的子页面回退上一个页面就重新头部开始阅读」

现状三条证据：

| 位置 | 改造前 | 后果 |
|---|---|---|
| `ui/feed/FeedScreen.kt` 详情分支 | `if (detail != null) FeedDetailView() else FeedListView()`（L113–117 一带），列表整棵被销毁 | 资讯滚到第 30 条 → 点开一条 → 返回，回到顶部，重新找位置 |
| `ui/feed/FeedScreen.kt` L757 | `rememberLazyListState()`（`remember`，不是 saveable） | 即使子树被保存也留不住位置 |
| `ui/EnRouteRoot.kt` L132–136、`ui/user/UserScreen.kt` L120–183 | `when (tab)` / `when (current)` 直接切内容，没有任何 saveable 容器 | 切 tab、进出子页，页内滚动位置全丢 |

「关于」一侧：设置页原来有一段「课表来自…账号密码只在你点登录那一次使用…」的长文字，
和独立页 `AboutScreen` 的「数据来源 / 隐私」两卡说的是同一件事 —— 同一句话两处维护，
改一处必忘另一处（AGENTS.md「按钮不自带说明书」也是同一类问题）。

## 2. 三条语义（照这个判断，不要一刀切）

1. **同级回退要恢复**：进详情 / 设置 / 关于再退回来，停在原处。
   做法是两件事一起做：`LazyListState` 变成 saveable（`ui/common/ScrollMemory.kt`），
   导航层用 `SaveableStateHolder.SaveableStateProvider(routeKey)` 把整棵子树的 saveable 状态存下来。
   只做一件都不生效 —— 这是这张票最容易踩的坑。
2. **换筛选条件要回顶部**：筛选变了是**新内容**，不是回退。所以筛选签名作为 `resetKey`
   进 `rememberSaveable(inputs)` —— key 一变就换一个全新的 `LazyListState`（index = 0）。
   已显式接线的：资讯（query / 只看未读 / 只看收藏 / 密度）、成绩（只看不及格）。
   折叠分组**不算**换内容（同一批数据的显隐），不进 key。
3. **跨进程不恢复**：只走 Bundle，不写盘。杀进程重开回顶部，这是刻意的（不做额外持久化）。

item key 仍然是前提：`LazyListState` 记的是 key + offset，数据变化后位置才不飘；
各页的 `key = { it.rowKey }` / `key = { cats[it] }` 已在票 05/23/25 那一批里接好，本票未改。

## 3. 做了什么（文件 + 行号）

| 文件 | 改动 |
|---|---|
| `ui/common/ScrollMemory.kt`（新） | `rememberListScrollState(resetKey)`（L27）：`rememberSaveable(resetKey, saver = LazyListState.Saver)`；另有 `rememberTransientListState()` 明确表达「这个列表故意不记忆」 |
| `ui/feed/FeedScreen.kt` | L117 `rememberSaveableStateHolder()`；L120 / L124 详情与列表两个分支各一个 `SaveableStateProvider("feed:detail")` / `("feed:list")`；L757 `FeedPage` 改用 `rememberListScrollState(resetKey)`，L774 传给 `LazyColumn`；`FeedPage` 新增 `resetKey` 参数，由 L~250 的 pager 页按 `query|onlyUnread|starredOnly|compact` 生成 |
| `ui/EnRouteRoot.kt` | L74 `rememberSaveableStateHolder()`；L138 `SaveableStateProvider("tab:$tab")` 包住三个 tab |
| `ui/user/UserScreen.kt` | L118 `rememberSaveableStateHolder()`；L125 `SaveableStateProvider("user:${current.name}")` 包住 `when (current)`（子页栈本身在票 16 已是 saveable，这里补的是**页内**状态） |
| `ui/academic/ExamScreen.kt` | L85 `rememberListScrollState()`（折叠不进 key）；L131 `LazyColumn(state = listState)` |
| `ui/academic/ScoreScreen.kt` | L73 `rememberListScrollState(if (onlyFailed) "failed" else "all")`；L133 `LazyColumn(state = listState)` |
| `ui/settings/SettingsScreen.kt` | 「关于」块重排：删掉与独立页重复的长文字；`UpdateSection()`(L500) 与 `ContributeSection(ui)` 作为功能保留；最后一行只留 `关于沈农在途 ›`（L505–515，点击 → `onOpenAbout`） |
| `ui/about/AboutScreen.kt` | 新增「版本」卡（L98：版本号 / 构建号 / commit 短码 / 构建日期，全部来自 `BuildConfig`）+「更新」区（L111–112 复用 `ui/settings/UpdateSection.kt` 的自带状态机区块）；原有 GitHub / 许可 / 依赖 / 数据来源 / 隐私 / 免责卡不动 |

## 4. 验收（每条都能单独演示）

**滚动记忆**
- [ ] 资讯频道滚到第 30 条 → 点开任一卡片 → 返回：停在原处（不是顶部）。
- [ ] 资讯里改关键词 / 开「只看未读」/ 改密度 → 回顶部（这是新内容）。
- [ ] 资讯在「教务」页滚到中部 → 切到「竞赛」再切回「教务」：教务停在原处。
- [ ] 资讯滚到中部 → 切到课表 tab → 切回资讯：停在原处。
- [ ] 考试页长按 → 详情浮层 → 关闭：时间轴停在原处；折叠 / 展开「已结束」不清空位置。
- [ ] 成绩页滚到第 3 学期 → 打开详情 → 返回：原位；切「只看不及格」→ 回顶部。
- [ ] 设置页滚到底 → 进「关于沈农在途」→ 返回：设置页停在底部附近。
- [ ] 杀进程重开：所有列表回顶部（明确不做跨进程恢复）。

**关于收口**
- [ ] 设置页「关于」区只剩：检查更新 / 地图共建 / 一行「关于沈农在途 ›」；**没有**任何长段落。
- [ ] 点「关于沈农在途」进独立页，能看到：版本号、构建号、commit 短码（7 位）、构建日期。
- [ ] 独立页里有「检查更新」入口，行为与设置页那个完全一致（同一个 `UpdateSection`）。
- [ ] 独立页保留 MIT / 依赖致谢 / 数据来源 / 隐私 / 免责 五块。

## 5. 本票**未**接线（留给各票主人，避免并行撞车）

这些列表页目前仍是 `rememberLazyListState()`，行为不变（进子页返回回顶部）。
它们分别属于不同票的文件，本票没碰，接线照 §3 的模式两行即可：

| 文件 | 现状 | 建议 resetKey |
|---|---|---|
| `ui/curriculum/CurriculumScreen.kt`（L150 / L201 两个 LazyColumn） | `rememberLazyListState` / 无 state | 培养方案：`年级+专业`；学期内展开表不重置 |
| `ui/curriculum/SyllabusSearchScreen.kt`（L135） | 无 state | 搜索词变化 → 回顶部（每次新结果） |
| `ui/home/RoomsScreen.kt`（L209） | 无 state | 日期 / 教学楼筛选 → 回顶部 |
| `ui/home/TeacherScreen.kt`（L121）/ `TeacherDetailScreen` | 无 state | 搜索词变化 → 回顶部 |
| `ui/timetable/TimetableScreen.kt`（L1228） | 自带周视图滚动逻辑 | 不建议动（周视图是固定行高的表，位置由周次决定） |

## 6. 怎么验证

```powershell
cd O:\dev\enroute-android
cmd /c compile-check.bat
Get-Content build-compile-log.txt | Select-String 'BUILD SUCCESSFUL|BUILD FAILED|^EXIT='
# 期望同时看到 BUILD SUCCESSFUL 与 EXIT=0
```

本轮按纪律**未装机、未截图**（5 个代理并行，装机签名/进程会互相踩）。
真机手测按 §4 清单走，装机时注意 AGENTS.md 的「必须 release 签名，先重签再装，绝不卸载」。

## 7. 遗留

1. **设置页仍有超过 20 字的小字说明**（「保存日志到本机」「载入示例考试/成绩」「接口诊断」等，均为票 20 / 17 的产物）。
   本票只清了「关于」块；整页清一遍需要把说明挪进各自的弹窗，属于票 20 的范围，未擅自改。
2. 上面 §5 的五个列表页尚未接线。
3. 「资讯页码（pager 当前频道）」已随 `SaveableStateProvider` 一起保住，但**横向 pager 的滚动惯性**不保存（`PagerState` 只存页码），这是 Compose 的既有行为，不打算绕。
4. 未做「记住位置并在数据刷新后按 key 重新定位」的高级策略：刷新后条目可能被过滤掉，
   此时 `LazyListState` 会自然停到最近的合法位置，这是可接受行为。

## Comments

**2026-11-14 · academic-ui**

- 改了什么：见 §3 表格（8 个文件，含 1 个新文件）。
- 怎么验证：`compile-check.bat` → `BUILD SUCCESSFUL` / `EXIT=0`（1m 9s）。
  过程中曾把 `ExamScreen.kt` 两行粘成一行导致整树编译红 10 余分钟，已修（`}` 与 `val` 之间换行），
  之后改为「每改一个文件立刻 compile-check」。
- 遗留：见 §7。
