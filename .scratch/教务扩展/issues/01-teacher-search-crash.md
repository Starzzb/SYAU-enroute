# 01: 教师搜索不再崩溃，并按学期收敛结果

**What to build:** 学生在「查教师」里输入姓名（支持单字，如「董」）搜索，页面稳定显示教师列表，
不再闪退，也不会因为闪退把教务登录态一起带走（现在崩溃 = 必须重新登录）。
结果限定在当前学年学期，同一位教师只出现一次，能看到姓名 / 院系 / 职称 / 工号。

**Blocked by:** None (can start immediately)

**Status:** ready-for-human

## 已定位的根因（不要重复调查）

真机崩溃日志（`mobile_list_crashes` → `2026-11-13_22:17:47.769_9701`）：

```
java.lang.IllegalArgumentException: Key "1995500023" was already used.
  If you are using LazyColumn/Row please make sure you provide a unique key for each item.
```

`1995500023` 是某教师工号。三处叠加造成：

1. `UrpHomeApi.searchTeachers()` 的 `executiveEducationPlanNumber` 传了**空串** →
   服务器返回全部学期（实测 `totalCount=1008`），同一工号跨学期重复出现；
2. 解析层不去重；
3. `TeacherScreen` 用 `key = { it.number.ifBlank { it.name } }` → 重复 key，主线程抛异常。

## 要做的事

- 学期代码取当前学期（`2026-2027-1-1` 这种格式；项目里已有学期/周次推算的地方，复用它，别再造一套）
- 解析层按工号去重（保留第一条），并给出**可单测的纯函数**
- 列表 key 改成唯一组合（工号 + 学期 + 序号之类），保证即使将来又出现重复也不崩
- 搜索期间给用户可见反馈；无结果时的文案区分「没输入」与「搜了没有」

## 验收标准

- [ ] 真机搜「董」不闪退，能看到结果（**需校园网**；移动数据下应显示可执行提示而不是崩溃或空白）
- [ ] 同一位教师不重复出现
- [ ] 新增/修改的派生逻辑有 JVM 单测覆盖「重复工号 → 去重后唯一」
- [ ] 全量单测通过，`assembleDebug` 成功
- [ ] 崩溃不再导致需要重新登录（若仍会，另记为票 04 的输入）

## 边界

- 只读。不要碰任何写操作接口
- 不要在这一票里实现「查看某位教师的完整课表」——那是票 03 的范围，且路由尚未探明
- 写盘范围：`data/UrpHomeApi.kt`、`ui/home/TeacherScreen.kt`、`ui/home/HomeModel.kt`、对应测试

## Comments

### 2026-11-13 修复（agent）

**改了什么**

- `app/src/main/java/cn/dkarbon/enroute/data/UrpHomeApi.kt`
  - `TeacherHit` 加 `term`（156 行）—— 每条记录自带的 `zxjxjhh`，缺则取 `id.executiveEducationPlanNumber`。
  - `searchTeachers`（215 行）：学期号从「默认空串」改为**必带**；带学期搜到 0 条时退回「不带学期 +
    按记录自带学期号过滤」，避免第四段（未实测）猜错导致整页空白。两次请求共用同一套解析/去重。
  - `parseTeachers(text, termCode)`（385 行）：学期比对取**前三段**前缀；学期字段缺失的记录保留（不丢数据）。
  - 新增纯函数 `dedupeTeachers`（416 行，按工号保留第一条，工号空的不合并）、`termPrefix`（428 行）。
- `app/src/main/java/cn/dkarbon/enroute/ui/home/HomeModel.kt`
  - 新增 `currentTermCode(today, serverTerm)`（208 行）：优先用服务器课表给的 `Timetable.term`
    （`2026-2027-1`，由 `UrpClient.parseSchedule` 从 `executiveEducationPlanNumber` 截得），
    **但只在它落在当前学年时**才用（本地缓存里可能躺着上学期课表）；否则按日历推
    （8 月~次年 1 月 = 第 1 学期）。
  - `termLabel`（225 行，`2026-2027-1-1` → `2026-2027 秋`，认不出原样返回）、
    `teacherResultLine`（234 行，`找到 11 位 · 2026-2027 秋`）。
- `app/src/main/java/cn/dkarbon/enroute/ui/home/TeacherScreen.kt`
  - 列表 key 从 `it.number.ifBlank { it.name }` 改为
    `itemsIndexed(ui.teachers, key = { i, hit -> "${hit.number}|${hit.term}|$i" })`（129 行）——
    工号重复也不再抛 `Key "..." was already used`。
  - 空态三分：搜索中 / 有错误或已搜过（显示 `teacherMsg`）/ 还没搜（`teacherEmptyHint`）（100 行起）；
    有结果时顶部显示「找到 N 位 · 学期」，不再同一句话出现两遍。
- `app/src/main/java/cn/dkarbon/enroute/ui/TimetableViewModel.kt`
  - `searchTeachers()`（405 行）带上学期号（416 行），结果条数/异常写入 `DebugLog`。
- 测试：`HomeParseTest` +7（重复工号保留第一条、工号空不合并、学期过滤、第四段不同算同学期、
  缺学期号不丢、`termPrefix`、真机夹具收敛后工号唯一）；`HomeModelTest` +5（日历推算、
  服务器学期优先、过期学期忽略、季节名、结果行）。

**怎么验证**

- `build-test.bat`（`:app:testDebugUnitTest :app:assembleDebug`）：`BUILD SUCCESSFUL in 5m 59s`，`EXIT=0`。
- 单测汇总：**185 条 / 14 个类 / 0 失败 0 错误 0 跳过**（基线 173，本轮 +12）。
- 证据：`app/build/test-results/testDebugUnitTest/*.xml`、`build-log-utf8.txt`。

**遗留 / 只能主人做的**

- 真机搜「董」不闪退、能看到 11 位教师 —— **必须校园网**（10.2.0.47 是内网）。本轮手机在移动数据，
  抓不到真数据；移动数据下现在是 `friendlyError` 的「连不上教务系统…请连校园网或 VPN」，
  不再崩溃、不再空白。
- 学期号第四段只有秋学期样本 `2026-2027-1-1`；**春学期（`-2-?`）未实测**。代码已兜底
  （带学期搜到 0 条就退回不带学期 + 按记录自带学期过滤），建议 2027 春季学期
  用 `docs/_probe/urp/probe_teacher_eval.js` 同型脚本复核一次。
- 「查看某位教师的完整课表」仍未实现（本票边界内不做）。
