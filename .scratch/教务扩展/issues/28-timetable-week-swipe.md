# 28: 课表左右滑动切周（手势 + 过渡）

**What to build:** 课表网格支持**手指左右滑**切换周次，带 180–220ms 平移淡入过渡；三个按钮全部保留。

**Blocked by:** 无

**Status:** ready-for-human

---

## 主人要求

> 「课表要支持左右手势滑动切换周次。」

---

## ① 手势（判定抽成纯函数 `WeekSwipeRules`）

`ui/timetable/WeekSwipeRules.kt`（全部纯函数，单测直打）：

| 常量/函数 | 值/语义 |
|---|---|
| `MIN_SWIPE_DP` | **64dp** 横向位移阈值 |
| `DOMINANCE` | 横向必须 > 纵向 × **1.5** |
| `FLING_VELOCITY_DP_S` | **800dp/s** 速度兜底（快速轻扫位移不够也切） |
| `shouldClaim(dx,dy,minPx)` | 够横向意图才**接管**手势（在此之前一个事件都不 consume） |
| `decide(dx,dy,vx,minPx,flingPx)` | `-1` 上一周（右滑）/ `+1` 下一周（左滑）/ `0` 不切 |
| `canSwipeTo / outcomeFor` | 边界判定 → `SWITCHED` / `BOUNCE_AT_EDGE` / `SNAP_BACK` |
| `dragOffset / alphaFor` | 拖动阻尼（超一屏宽只留 25%）与淡出（上限 55%） |
| `TRANSITION_MS` | **200ms**（票面 180–220 区间内） |

## ② 纵向优先（最关键的一条）

三重保险，缺一不可：
1. `decide` 里 `abs(dy) > abs(dx)` **直接返回 0** —— 纵向占优一律不切；
2. 横向还得 > 纵向 ×1.5 才算"横向意图"（斜滑不算）；
3. **接管之前不 consume 任何事件** —— 手指没横着走够阈值时，`verticalScroll` 完全正常。
   `shouldClaim` 就是这条的分界线，单测里专门有一条 `纵向滚动时连接管都不该发生`。

## ③ 不破坏既有交互

- 点卡片看详情、长按复制/跳周、`HeaderTapPad` 50dp 点击区：**都没动**。
  因为手势容器只在"横向够阈值"之后才 `consume()`，之前的一切事件照原样流向子组件与父滚动。
- 卡片上起手横滑 → 同样切周（容器在 GridArea 外层，卡片没机会吃掉），长按仍是长按（长按期间没有位移，不触发 `shouldClaim`）。

## ④ 过渡

- 切周时：`swipeOffset = step * width` 再动画回 `0`（`tween(200)`）——
  新一周从**手指来的那一侧**推进来；拖动中按 `alphaFor` 线性淡出（最多 55%），不弹跳。
- 到边界：`animateSwipeTo(0f)` —— **回弹**，周次不变，不静默。
- **账页主题无色带错位**：平移施加在**整块网格容器**的 `graphicsLayer.translationX` 上，
  所有分区（色带、圆角、阴影）一起位移，不存在"分区各自位移"导致的错位。

## ⑤ 切周后回到顶部

`GridArea` 内 `LaunchedEffect(week) { vScroll.scrollTo(0) }` —— 新内容从顶部开始，
与票 26「滚动记忆」的语义一致（记忆只在同一周内成立）。

## 按钮全保留

`上一周 / 下一周 / 回到本周` 三个按钮一行没动（有人用手势、有人用按钮，两条路都在）。

---

## 单测（`WeekSwipeRulesTest`，11 条）

横向够阈值切周且方向正确 / 位移不够且不甩不切 / **纵向占优一律不切** /
**纵向滚动时连接管都不该发生** / 快速轻扫位移不够也切 / 速度兜底也要求横向占优且方向不矛盾 /
第 1 周往回与最后一周往后都不切换且回弹 / 拖动超一屏宽施阻尼 / 拖动越远越淡且不超上限 /
坏数据（NaN / 零宽）不炸也不切 / 过渡时长落在 180–220。

---

## Comments

### 2026-11-14 交付（agent）

`compile-check.bat`：**BUILD SUCCESSFUL / EXIT=0**
全量单测：**539 条 / 40 类 / 0 失败**；新增 `WeekSwipeRulesTest` **11 条全绿**。

**落点**
- 新增 `ui/timetable/WeekSwipeRules.kt`（纯函数，全部判定在此，Composable 里只喂数字）
- `ui/timetable/TimetableScreen.kt`：`GridArea` 外包一层 `BoxWithConstraints` → `graphicsLayer(translationX, alpha)` + `pointerInput(Unit)` 的 `awaitEachGesture`；
  `GridArea` 里加 `LaunchedEffect(week) { vScroll.scrollTo(0) }`

**一处自查纠错**：滑入方向第一版写成了 `-step * width`，那会让新一周**从反方向倒着进来**
（向左滑本该从右侧进）。已改成 `step * width` 并在注释里写明原因 —— 这类符号错只影响观感、不影响任何断言，最容易被漏掉。

**范围**：只碰 `ui/timetable/`；没动 `ui/map/`、`ui/theme/`、`data/DebugLog*`。
