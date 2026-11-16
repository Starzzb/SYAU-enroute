# v4 batch-a 收尾清单

> 执行：2026-11-16　构建：`build-verify.bat`（testDebugUnitTest + assembleRelease）

## 0. 构建与单测

- `BUILD SUCCESSFUL in 2m 41s` + `EXIT=0`（build-verify-log.txt 末尾两行，72 个 task：12 executed / 60 up-to-date）。
- 单测：**44 个测试类 / 570 条 / 0 失败 / 0 错误 / 0 跳过**（app/build/test-results/testDebugUnitTest 的 XML 统计）。

## 1. 上屏说明性文字扫描（要求为空）

- `docs/_probe/scan-hint-copy.ps1`（剥离注释 / contentDescription / FeedSummary.kt）→ **SCAN_RESULT=EMPTY**。
- 本批修掉 1 处：`ContributeSection.kt:235` 共建同意弹窗原文「开启后，你**点**「导出标注」会直接…」命中「点…」说明书式文案，改为「开启后，填好的属性会随「导出标注」一并上报…」，仍保留「会传学号」等知情同意内容（AGENTS.md 例外：义务告知须在决策点如实完整）。
- 变更说明复查（正则：本次更新/新增功能/更新内容/…）：全仓 .kt 字符串字面量仅 2 处命中，**均非 UI 变更说明**：
  - `TimetableViewModel.kt:619`「考试已更新：…」—— `DebugLog.log("CACHE", …)` 内部日志，不上屏。
  - `UpdateDialog.kt:172`「不再提醒本次更新」—— 按钮动作名，不是变更说明。
- 结论：**没有任何「本次更新/新增功能」类变更说明出现在 UI 上**。更新内容（`notes`）只出现在更新弹窗与设置页「检查更新」结果里——这两处是主人 2026-11-16「要说明更新内容」钦定的更新通知面，属许可例外，不是页面里贴变更说明。

## 2. 弹窗自查（更新提醒 · 方案 B 里程表）

- **时机**：`EnRouteRoot.kt:95–102` —— 订阅 `UpdateCenter.available`，`withFrameNanos {}` 首帧后 + `delay(500ms)` 落定，且 `tab==0`、无 `syllabusOpenId`、无 `credPromptVisible` 才弹（与其他弹层互斥，启动不挡首帧）。
- **频率**：`UpdateDialogPolicy.shouldShow` = `versionCode != dismissedCode`（「不再提醒」只压一个版本号）；「我知道了」只关弹窗（`dialogInfo=null`），不写 dismissedCode → 下次启动继续弹；`UpdateCenter.dismissedCode()/dismissVersion(code)` 落 Prefs。
- **三按钮**：`UpdateDialog.kt:159–171` —— 「立即更新」独占一行主色实心（点后 `requestDownload` + 跳「我的」设置页）；「我知道了 / 不再提醒本次更新」同行等分描边。
- **表盘只滚变化位**：`Odometer`/`ReelDigit`（UpdateDialog.kt:246–286）逐位比较，`changed` 的位才滚（450ms tween 无回弹、逐位错开），未变化位静止灰字；`prefers-reduced-motion` 等价开关直接跳到位。
- **强制版无不再提醒**：`UpdateDialogPolicy.showsDismiss(mandatory) = !mandatory`；`isMandatory` 时整块移除「不再提醒本次更新」，只剩「立即更新 + 我知道了」（票 15 红线：可以催，不锁死）。

## 3. 强调色自查（资讯频道强调色，只两处、无底色块）

- 调用点收敛到**两处**（`feedChannelInk` 全仓 2 个调用）：
  1. 频道筛选栏选中项：`FeedScreen.kt:582`（文字 + 2dp 短横线，色条/高亮线）。
  2. 列表条目分类标签：`LedgerKit.kt:295`（11.5sp 文字）。
- **无底色块**：`FeedQuickActions`（长按快捷操作浮层）本批已去强调色——分类角标改 `Color.Transparent` 底 + `t.outline` 边 + `t.inkSoft` 字，浮层顶色条 `SheetScaffold(accent = t.ink)`（中性墨）。不再有 `feedChannelInk` 做背景填充。
- 对比度：全部从现有 token 派生，纸底最低 5.39:1（`docs/设计-对比度实测.md`「资讯频道强调色」）。

## 4. 缓存自查（学业 / 考试 / 成绩页）

- **进页面不再无条件刷新**：`TimetableViewModel.maybeSilentRefresh`（:505–519）先过 `CachePolicy.anyBlockStale`——三档全新鲜直接读缓存、一个请求不发；各块再按 `shouldFetchPermanent/shouldFetchDaily/shouldFetchExams` 分判（身份只补空、每日档 ≥24h、考试 ≥24h 或「下一场」已过）。Exam/Score 屏自身**进页不发请求**（只渲染 ui 状态），无 entry-fetch。
- **获取日期显示**：`ExamScreen.kt:108–115` / `ScoreScreen.kt:97–104` 页顶 `CachePolicy.fetchStamp` → 「刚刚更新 / 数据取自 MM-dd HH:mm」；没抓过（时间戳 0）不显示假时间。
- **手动刷新有效**：`vm.refreshExams()` / `vm.refreshScores()`（:1484/:1494）直接 `fetchExamsBlock/fetchScoresBlock` 真抓，不受缓存判据拦截；失败保留旧值并在提示里说明「显示的是多久前的数据」。

## 5. 本批改动汇总

- `MapContributeTest.kt`：client 声明键集合补 `hasName`（与 `MapContribute.body` 一致，票 22 主人要求收集上传者信息）；首条断言补 `"hasName":false`。
- `ContributeSection.kt`：共建同意弹窗去「点「导出标注」」说明书式表述（见 §1）。
- `FeedScreen.kt`：长按快捷操作浮层去频道强调色（角标中性化 + 顶色条中性墨），落实「只两处、无底色块」。

## 6. 需真机确认

- 更新弹窗：冷启动落定后才弹（非白屏上弹）、「我知道了」重进仍弹、「不再提醒」不再弹、强制版只剩两按钮、表盘只滚变化位的手感与 reduced-motion 直跳。
- 资讯页：频道筛选选中强调色（文字+短横线）、列表分类标签强调色、长按浮层去强调色后的观感。
- 学业/考试/成绩页：全新鲜时进页不再打网络（看「数据取自」行是否直读缓存）、手动刷新后时间戳更新。
- 共建同意弹窗新文案（「随「导出标注」一并上报」）在真机上的知情同意可读性。
