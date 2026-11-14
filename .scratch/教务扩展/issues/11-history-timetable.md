# 11: 历年课表（按学期查询 + 缓存）

**What to build:** 学生能看**往年学期的课表**：选一个学期 → 显示那一学期的周网格；
切回上次看过的学期**不再打网络**（走缓存），并能手动刷新。

**Blocked by:** 无（接口已实测，schema 与现有解析器同构）

**Status:** ready-for-human

---

## 已实测的接口（2026-11-14，别重复探）

```http
POST /student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback
Content-Type: application/x-www-form-urlencoded
&planCode=<学期号>          ← 开头那个 & 是首字段为空，照抄
→ 200，与「本学期课表」完全同构（rwbgLists / allUnits / xkxx / csz / jcsjbs / dateList）
```

- 学期号形如 `2024-2025-1-1`；**学期列表不要写死** —— 从
  `/student/courseSelect/calendarSemesterCurriculum/index` 的 `select[name="planCode"]` 里取，
  实测只给最近 5 个（含当前）
- **`GET callback?xnxq=` 无效**、**`GET index?planCode=` 无效**，必须 POST（实测证据见文档 §6.4）
- 真实样本：`docs/_probe/urp/calendar_schedule_after_switch.json`（2024-2025 秋，42KB）
- 文档：`docs/URP-学生端接口清单.md` §6.4

## 复用现有解析器（关键）

返回 schema 与「本学期课表」**完全一致**，所以**直接复用 `UrpClient.parseSchedule`**，
不要另写一个解析器。需要改的只是「取数据」那一步的 planCode。

## 交互

- 入口：`UserPage` 新增 `历年课表`；或在课表页的周次/学期切换处加入口（二选一做，说明理由）
- 顶部：学期选择（来自服务器给的 5 个，显示成 `2025-2026 春` 这种，用现有 `HomeModel.termLabel`）
- 主体：复用课表页的**只读周网格**（历史学期不显示「当前时刻」推进条、不做侵蚀 —— 那不是今天）
- 只读：历史学期不允许校准周次、不允许看「今日消化」

## 缓存

- 按 `planCode` 缓存解析后的 `Timetable`（JSON），放 `filesDir/timetable/history/<planCode>.json`
- 命中即渲染并标「本地缓存 · 抓于 MM-DD」；过期策略：**永久有效**（历史学期不会再变），
  但提供手动刷新入口（教务可能补录/更正）
- 「清除所有数据」连带清空该目录
- 体积：单学期解析后约几十 KB，5 个学期 < 500KB

## 验收标准

- [ ] 能列出服务器给的全部学期（不写死），默认当前学期
- [ ] 选某历史学期能出周网格；课程/教师/地点/周次与教务页面一致（校园网下由主人核对）
- [ ] 再次进入同一学期**不打网络**（有缓存），界面标出抓取时间
- [ ] 手动刷新能重新拉取并覆盖缓存
- [ ] 历史学期不显示「当前时刻」推进条与侵蚀效果
- [ ] 失败态可辨：没登录/不在校园网 vs 该学期确实没排课（本学期可能为空）
- [ ] 单测：planCode 表单构造（含开头 `&`）、学期选项解析、缓存键与命中判定、
      以及「历史学期不侵蚀」的派生逻辑（用真实样本做夹具）
- [ ] 全量单测绿（基线 **238 条 / 17 类 / 0 失败**），`assembleDebug` 成功

## 边界

- 只读教务（POST 只用于「取数据」这一种语义，不改任何数据）
- 不动 `ui/feed/`、`ui/home/RoomsScreen.kt`、`ui/home/TeacherScreen.kt`、`ui/home/HomeCards.kt`
- 写盘范围：`data/UrpClient.kt`（加按学期取课表）、`data/TimetableStore.kt` 或新建 history store、
  `ui/timetable/`（只读模式与入口）、`ui/user/UserScreen.kt`、`AppState`、`TimetableViewModel`、测试

## Comments

### 2026-11-14 交付（agent）

**入口选在「我的 → 历年课表」，没放课表页的学期切换处**。理由：课表页是每天十几次的高频界面，
塞进学期切换会让「看今天」变复杂；历年课表是低频回顾，与考试/成绩同级更稳
（票里给了二选一，这是我的理由）。

**改了什么**

- `data/UrpClient.kt`
  - `fetchScheduleForTerm` 270：**POST** `ajaxStudentSchedule/callback`，表单 `&planCode=<学期>`
    （开头那 `&` 照抄实测）；先回一次 `/index` 预热。
  - `fetchSemesterCalendarPage` 291：读 `calendarSemesterCurriculum/index` 页面（只为抠学期下拉）。
  - 返回**直接交给现有 `UrpClient.parseSchedule`**，没写第二个解析器（票的硬要求）。
- `data/HistorySemesters.kt`（新，纯函数）：`options` 31（解析 `select[name=planCode]` 的 option，
  认得「(当前)」与 `selected`；**不写死学期**）、`scheduleBody` 57（`&planCode=…`）、
  `cacheFileName` 61（学期号 → 文件名，替换异常字符防跑出目录）、`isCacheHit` 70（历史学期**永久有效**）、
  `stamp` 77（`本地缓存 · 抓于 11-14`，时间解析不出就写「未知」，不编日期）。
- `data/HistoryTimetableStore.kt`（新）：`HistoryEntry` 21（planCode + fetchedAt + 解析后的 `Timetable`）+
  `HistoryTimetableStore` 28：读 33 / **原子写**（`.tmp` 改名）42 / clear 52 / count 57；
  路径 `filesDir/timetable/history/<planCode>.json`。存**解析后的模型**而不是原始 payload：
  打开历史学期不用再跑解析，也不受学校改字段影响。
- `ui/timetable/TimetableScreen.kt`：**只加一个开关** —— `GridArea` 由 `private` 改 `internal`（320）、
  加 `readOnly: Boolean = false`（331）、`readOnly` 时不画日期号数（371）。
  `GridMetrics` / `gridRowTop` / `yOfRow` 等几何逻辑**一行没动**。
- `ui/timetable/HistoryTimetableScreen.kt`（新）：`HistoryTimetableScreen` 58（学期 chips + 状态行 +
  周前后翻 + 只读网格 + 课程详情浮层）、`HistoryRules` 188（`isThisWeek` 191 / `progress` 194 /
  `weekRange` 200 / `cellsOf` 211，**纯函数，单测直接打**）。
- `ui/AppState.kt` 176 起 7 个字段；`ui/TimetableViewModel.kt`：`historyStore` 413、
  `openHistoryTimetable` 420（先学期列表 → 默认当前学期）、`selectHistoryTerm` 466、
  `refreshHistoryTerm` 472、`loadHistoryTerm` 481（**缓存优先，命中就打不到网络**）。
- `ui/user/UserScreen.kt` 68/111/120/176/311：入口与导航。
- `data/DataWiper.kt` 76：⑤c 连清 `filesDir/timetable/history/`，并报「清了几个学期」。
- 测试：`HistoryTimetableTest`（11 条）+ 夹具 `home/history_schedule_2024_2025_1.json`
  （真实 42KB 响应）与 `home/history_semester_select.html`（**值为实测真实 5 个学期**，
  标记按 URP 常规 `select` 形态重建 —— 当时只存了提取结果、没存整页）。

**只读口径怎么落实（票的验收点）**

- 传 `isThisWeek = false` → 今天竖带、**当前时刻推进条**、侵蚀进度、小幽灵**全部天然不出现**
  （网格里这几处本来就以 `isThisWeek` 为门）；
- 另外 `readOnly = true` 时**不显示日期号数** —— 历史学期我们不知道那一周的真实公历日期，
  硬显示一个号数就是编数据；
- 不允许校准周次（校准是「现在第几周」，对历史学期无意义）；
- 详情浮层传 `todayWeek = -1`，所以不会出现「本周」高亮。

**怎么验证**

- 先单独 `:app:compileDebugUnitTestKotlin` → 绿；`build-test.bat`：`BUILD SUCCESSFUL` / `EXIT=0`。
- 单测：**307 条 / 22 类 / 0 失败**（基线 296/21，本轮 +11 条 / +1 类）。
- 关键断言：学期下拉解析出实测那 5 个（`2026-2027-1-1`…`2024-2025-1-1`）且只有一个「当前」、
  别的 select 不混入；`scheduleBody` 以 `&` 开头；**真实 42KB 样本用现有解析器就能解析**
  （课程/周次/星期/节次齐全，term=2024-2025-1）；周范围从课程周次推、空课表给 1..16；
  `cellsOf` 键恒为 (星期 1..7, 大节 1..6) 且不存在的周返回空；`HistoryRules.progress` 非本周恒 0；
  缓存文件名/命中（永久但有内容才算）/时间戳不编日期。
- **APK**：22,513,056 → 22,705,357 字节（**+0.18MB**），无新资产。

**遗留 / 只能主人做的**

1. 真机验证（`10AE1H1PYJ005ED` 本轮未连接）：校园网下进「我的 → 历年课表」应列出 5 个学期、
   默认当前学期；切历史学期出周网格；**退出再进同一学期不打网络**（状态行显示「本地缓存 · 抓于 MM-DD」）；
   「刷新」能覆盖缓存；历史网格上没有橙色推进条、没有侵蚀、没有日期号数。
2. 学期候选依赖 `select[name=planCode]` 的 HTML 形态：本轮用重建的夹具验证解析，
   **在线页面的真实 DOM 没在设备上跑过**（校园网登录态才拿得到）。若解析不到，
   界面会明确说「没拿到学期列表（页面可能改版）」，不会静默空白。
3. 同一学期里若学校补录了课程，需要手动「刷新」才会更新（永久缓存的代价，票面接受）。
4. 只读网格没做「未排课课程列表」（课表页有）；历史学期的未排课课程如果需要看，得另加——票面没要求。
