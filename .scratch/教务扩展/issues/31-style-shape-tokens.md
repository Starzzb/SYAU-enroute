# 31: 账页重构 v2 · 批次 1 —— 形状 token 统一

**What to build:** 全仓圆角收敛到**八个语义 token**，业务代码**拿不到半径数值**；
配源码扫描测试兜底，并证明它能抓到故意违规。这是「前端风格统一」的第一刀。

**Blocked by:** 无（`FormSpec` / `LocalForm` 已在，缺的是"形状"这一轴）

**Status:** ready-for-human

---

## 0. 一句话原则（照票面执行）

> 这活的成败不在"改了多少处"，而在**以后还能不能偷偷加一个 6dp 圆角**。

所以本票的两个交付不是"17 处替换"，而是：**① 类型系统上拿不到 Dp；② 扫描测试抓得住**。两者缺一都不算做完。

---

## 1. 五条阻断的处理（按 Lead 结论，不照规范原文）

| 规范原文 | 问题 | 本票实际做法 |
|---|---|---|
| 只写正则 gate（`\d+\.dp`） | `CircleShape` / `cutCorner` / `RoundedCornerShape(50)` / `13f.dp` 全绕过 | **形状收进 `ShapeSet`，只导出 `Shape`**，业务层拿不到半径 → 写不出自己的数字；扫描器作为**第二道闸**，并单测验证它抓得住那四种绕过姿势 |
| 只扫 `ui/` | `widget/TodayWidget.kt`、`ui/timetable/TimetableTokens.kt` 漏网 | 扫描**整棵 `cn.dkarbon.enroute` 源树**；单测断言 `widget/`、`ui/timetable/`、`ui/settings/` 都被扫到，且扫到 >50 个 .kt（防"目录找错→扫 0 个→永远绿"） |
| `pillSize = 999` | 999 是"圆角半径"，`pillSize` 是**直径 20dp**，两个量纲 | `pillSize` 不动（直径 20dp）；胶囊改用 **`RoundedCornerShape(50)` 百分比** —— 任意高度恒为半高，不再需要魔数。999 已被扫进禁用列表 |
| 重启方案 | 同进程 `killProcess` 不落地 + `apply()` 异步可能丢配置 | **批次 1 不做重启**，主题继续热切（票 25 已验证）；形状是运行时 token，本来不需要重启 |
| `SpaceTokenTest` 必红 | 允许集凭想象定 | **本批不写间距测试**。先把真实值收全（见 §6：**705 处、63 种取值**），留给批次 2 定允许集 |

---

## 2. 形状 token（`ui/theme/ShapeSet.kt`，新增）

| 语义档 | 用在哪 | HARD | 账页 | 说明 |
|---|---|---|---|---|
| `page` | 页面块、通栏列表、表格行、分割区 | 0 | 0 | 账页本体不许弯 |
| `badge` | 徽标、角标、小色块、缩略图 | 0 | 6dp | |
| `control` | **按钮**、输入框、chips、搜索条 | 0 | 6dp | 主人点名的「帮助完善地图」按钮走这一档 |
| `card` | 通用卡片、便签 | 0 | 10dp | |
| `courseCard` | 课卡 | **4dp** | 10dp | 4dp 是主人点名的 HARD 局部例外，不动 |
| `sheet` | 对话框、底单、浮层 | 0 | 14dp | |
| `pill` | 胶囊 | 0 | **50%** | 百分比，不是 999 |
| `circle` | 正圆：头像、6dp 状态点 | 圆 | 圆 | 与 `pill` 分开：circle 要宽高相等，pill 是胶囊 |

**怎么保证"拿不到 Dp"**：`ShapeSet` 的八个字段类型都是 `Shape`，没有 `Dp`、没有 `cornerRadiusDp()` 之类的回读 API。
`FormSpec` 新增一个字段 `shapes: FormSpec.shapes`（`HARD → ShapeSet.Hard`，`账页 → ShapeSet.Ledger`），
业务代码只能写 `Form.current.shapes.card`。**它没有数字可用，也就写不出自己的圆角。**

> 旧字段 `radiusCard / radiusCourseCard / radiusSheet`（Dp）保留：`ui/timetable/` 两个调用点还在读，
> 那包本票不碰。为防两套值漂移，单测 `形状档与旧 Dp 字段不许漂移` 逐字段比对。

---

## 3. 替换清单（17 处 → 语义 token）

| 文件 | 原写法 | 现在 | 主题效果 |
|---|---|---|---|
| `ui/settings/ContributeSection.kt` | `RoundedCornerShape(999.dp)`（SmallPill） | `shapes.control` | HARD 方钮 / 账页 6dp |
| `ui/settings/UpdateSection.kt` ×3 | `999.dp`（Badge / PillButton / 进度条） | `badge` / `control` / `pill` | 同上；进度条在账页变 1.5dp 圆头（3dp 高的半高） |
| `ui/settings/ColorTunerPanel.kt` ×3 | `RoundedCornerShape(0.dp)` | `control` / `badge` / `control` | HARD 0 / 账页 6dp |
| `ui/feed/FeedScreen.kt` ×5 | `999.dp` 药丸 / `6.dp` 徽 / `radiusCard` / `0.dp` | `pill` / `badge` / `card` | HARD 全 0；账页胶囊 + 6dp 徽 + 10dp 卡 |
| `ui/feed/FeedScreen.kt` ×3 | `.clip(CircleShape)`（6dp 未读点） | `shapes.circle` | 两主题都是圆（不变） |
| `ui/home/HomeCards.kt` ×1 | `.clip(CircleShape)`（46dp 头像） | `shapes.circle` | 不变 |

**HARD 的可见变化（诚实列出）**：ContributeSection / UpdateSection 的 3 个按钮与徽标、feed 的频道 chips，
原本是 **999dp 全圆**，现在 HARD 下变**方角** —— 这正是"统一"的必然结果（HARD 的规矩是全方角），
也是主人说「哪都有点圆角、哪都没层级」的那个病根。账页下这些位置从 0 变 6dp，同样是有意的。

`ui/timetable/` 的 9 处**未动**（见 §5），`RectangleShape`（≈25 处，含 `ui/map/`）本批不动也不禁：
它是"零圆角"的字面量，不产生圆角，且 map 包刚加完手势。

---

## 4. 扫描测试（`ShapeTokenTest`，9 条）

`app/src/test/java/cn/dkarbon/enroute/ShapeTokenTest.kt` + 扫描器 `ShapeScan`（独立对象，可用合成片段验证）。

**禁用写法**（逐行匹配，`import`/注释行豁免）：
`RoundedCornerShape(` · `CircleShape` · `CutCornerShape(` · `AbsoluteRoundedCornerShape(` ·
`GenericShape` · `.cutCorner(` · `MaterialTheme.shapes` · `999.dp`

**九条断言**：
1–3. 形状档取值（HARD 全方角+课卡 4dp / 账页各档 / 与旧 Dp 字段一致）；
4. 主题切换取到对应形状组；
5. **扫描器能抓住 9 种写法**（含四种绕过姿势，逐行定位，断言行号列表相等）；
6. 扫描器不误报 import 行与正常 token 用法；
7. **可编辑范围内裸形状 = 0**；
8. 课表包冻结基线「只许变少不许变多」；
9. 覆盖整棵源树（`widget/`、`ui/timetable/`、`ui/settings/` 都在，且 >50 个 .kt）。

### 自证：故意违规 → 红 → 删除 → 绿（已实跑）

在 `ui/settings/UpdateSection.kt` 的 import 之后临时插入：

```kotlin
// 【临时·票31自证】这一行用来验证 ShapeTokenTest 能抓到违规，验证完立刻删除
private val gateProbeShape = RoundedCornerShape(6.dp)
```

```
> Task :app:testDebugUnitTest FAILED
ShapeTokenTest > 可编辑范围内裸形状必须为零 FAILED
    java.lang.AssertionError: 这些文件还有裸形状（形状请走 Form.current.shapes.*）：
      ui/settings/UpdateSection.kt × 1
tests=9 failures=1     ← 抓到，且指名到文件与条数
```

删除该行后重跑：`tests=9 failures=0`。**红→绿闭环成立。**

---

## 5. `ui/timetable/` 的 9 处为什么还在（本票唯一的缺口，如实报）

Lead 的纪律是「**不碰 `ui/timetable/`**（那边刚加完左右滑动）」，而验收又要求"全仓裸值只在 token 文件里"。
两条撞在一起时我选择遵守**前者**（碰了会毁别人的在途改动），用**棘轮**兜住风险：

```kotlin
// 基线：本票不动 ui/timetable（另一票正在改那边的左右滑动）。
// 数字只能减不能增 —— 想偷偷加一个圆角，这里立刻红。
ErodedCard.kt      → 1
LedgerTimetable.kt → 6
TimetableScreen.kt → 2
```

即：**这些文件里新增任何裸形状 → 测试立刻红**（"偷偷加"这条路已经堵死）；清理它们则被鼓励（数字改小即可）。
另有断言拦住"课表包里冒出新文件"。

**待那票落定后的一次机械替换**（token 已就位，可直接照做）：

| 位置 | 现值 | 应改 |
|---|---|---|
| `ErodedCard.kt:54` | `RoundedCornerShape(4.dp)` | `f.shapes.courseCard`（HARD 仍是 4dp，零变化） |
| `LedgerTimetable.kt:73/178` | `RoundedCornerShape(f.radiusCourseCard)` | `f.shapes.courseCard` |
| `LedgerTimetable.kt:225` | `RoundedCornerShape(f.pillSize / 2)` | `f.shapes.pill`（语义更准：直径的一半 = 胶囊） |
| `LedgerTimetable.kt:300` | `RoundedCornerShape(12.dp)` | `f.shapes.card` 或 `sheet`（按该块语义定） |
| `LedgerTimetable.kt:317` | `RoundedCornerShape(2.dp)` | `f.shapes.badge` |
| `LedgerTimetable.kt:352` | `RoundedCornerShape(999.dp)` | `f.shapes.pill` |
| `TimetableScreen.kt:920` | `RoundedCornerShape(4.dp)` | `f.shapes.courseCard` |
| `TimetableScreen.kt:1267` | `.clip(CircleShape)` | `f.shapes.circle` |

> `ErodedCard` 不在账页路径上（只由 `TimetableScreen.kt:860` 调用，账页走 `LedgerTimetable`），
> 所以它那 4dp **只会在 HARD 出现**——按各自主题的规则算，它现在是**对的**，只是还没 token 化。

---

## 6. 验收

| 项 | 结果 |
|---|---|
| 可编辑范围裸形状 = 0 | ✅ `ShapeTokenTest.可编辑范围内裸形状必须为零` |
| 课表包不新增 | ✅ 棘轮基线（见 §5） |
| `compile-check` 绿 | ✅ `:app:compileDebugKotlin` BUILD SUCCESSFUL |
| 全量单测绿 | ✅ **42 类 / 557 条 / 0 失败**（基线 539/40 + 票 29 的 9 条 + 本票 9 条） |
| 扫描测试抓得住违规 | ✅ 红→绿闭环（§4，已实跑并留日志） |
| 4 个点名组件按主题规则 | ✅ ContributeSection→`control`、UpdateSection→`control/badge/pill`、ColorTunerPanel→`control/badge`、ErodedCard 4dp 只在 HARD 渲染（=HARD 规则） |

**未做（明确在批次外）**：阴影（批 4）、字阶（批 2）、间距（批 2）、重启（批 5）。
间距的实测家底已备好，供批次 2 定允许集用：

> 非 theme 源码里 **705 处 `.dp` 字面量、63 种取值**（`2×75 / 6×71 / 8×64 / 4×53 / 10×48 / 3×39 / 12×38 / 1×31 …`）。
> 规范 §4.1 的允许集 `{0,2,4,8,12,16,20,24,32,44,48,56,80}` 只能覆盖约三成 ——
> 这正是"`SpaceTokenTest` 按定义必红"的量化证据：**先收家底、再定允许集**，别倒过来。

---

## 7. Comments

- 2026-11-14 22:4x design-v3-2：批次 1 完成。
  - 新增 `ui/theme/ShapeSet.kt`（八档语义形状 + `ShapeSet.Hard/Ledger`）；`FormSpec` 加 `shapes` 字段；
    17 处裸形状改走 token；`ui/timetable/` 9 处按纪律冻结成棘轮基线。
  - 新增 `ShapeTokenTest`（9 条，含扫描器自证与覆盖范围断言）。
  - 自证过程实跑：插违规行 → `ui/settings/UpdateSection.kt × 1` 精确报出 → 删除 → 全绿。
  - 三处诚实标注：① HARD 下 4 个按钮/chips 从"全圆"变"方角"（统一的必然结果，方向正确）；
    ② `ui/timetable/` 9 处仍在（Lead 纪律 vs 全仓归零的冲突，用棘轮兜住，并给出 8 行替换表）；
    ③ 间距类测试本批**故意不写**（705 处 / 63 种取值，先收家底再定允许集）。
  - 顺手删了 6 个已失效的 `import RoundedCornerShape/CircleShape`（避免"看起来还在用"的误导）。
