# 23: 地图手势重做（双击锚定 + 惯性甩动）+ 地图页共建入口

**What to build:** 只做主人拍板的两项手势改动 + 一个入口搬家。**范围外的（放宽 `panLimit`、改 `viewMargin`、重写手势框架）一律不做。**

**Blocked by:** 无

**Status:** ready-for-human（代码完成，待统一装机验证）

---

## 主人原话（范围由他拍板）

> 「放大默认还是中心放大」「松手就停，别扭」
> 「只做前两项」（双击锚定、惯性甩动）—— 明确不做放宽边界。

---

## ① 双击放大要落在点击点上

**问题**：双击以前是 `zoom *= 2`，以**画布中心**为不动点，手指点哪儿没参与计算 → 看起来永远朝中间涨。
（捏合缩放早就是对的：`CampusMapScreen` 用 centroid 当锚，没动。）

**改法**
- 新纯函数 `MapViewRules.doubleTapZoom(zoom, panX, panY, tapX, tapY, canvasW, canvasH, factor = 2f)`
  → 返回 `ZoomPan(zoom, panX, panY)`，内部复用 `panAfterZoomAt`（与捏合同一个模型）
- `CampusMapScreen` 双击分支改调它，之后仍走 `clampPan()`
- `factor` 默认 2（票面没要求改倍数）

## ② 惯性甩动

**问题**：松手就停。

**改法**（`CampusMapScreen` + `MapViewRules`）
- 手势里挂 `VelocityTracker`（只在 `pressed.size == 1` 时喂点，双指缩放不甩动）
- 松手按速度决定：
  - `MapViewRules.shouldFling(speed, minPxPerSec)` 为 false（< **100dp/s**，或 NaN）→ 直接 `snapBack()`，**不漂移**
  - 否则 `startFling(vx, vy)`：`withFrameNanos` 逐帧推进，每帧 `velocity *= flingDecay(dt)`
- 摩擦：`FLING_FRICTION_PER_FRAME = 0.85`，但用**连续换算** `0.85^(dt / 16.67ms)`
  —— 掉帧时滑行距离不变短，手感才稳
- 边界：每帧过 `flingStep()`，内部 `applyOverdrag` 施阻尼（越界部分留 35%），
  **不硬停**；滑完统一 `snapBack()` 回位
- 时长上限 `FLING_MAX_MS = 1200`，到点必停（票面要求 1.2 秒内停住）

## ③ 地图页「参与共建」入口（同属地图页，一起做）

- `ui/settings/ContributeSection.kt`：把「首次开启」确认弹窗抽成
  `internal fun MapContributeConsentDialog(onDismiss, onConfirm)`，设置页改为调用它
  （**文案只有一份**，两处看到同一段话）
- `CampusMapScreen` 导出栏加按钮：
  - 未开启 → 显示 `参与共建`，点了就地弹**同一个**确认弹窗
  - 已开启 → 显示 `参与共建 · 已开`，只显示状态，不再堆解释（AGENTS.md：按钮只留名字 + 状态）
- 状态来源：`data/map/MapContributeStore`（与设置页**同一个 store**，`setEnabled` / `setAcknowledged`），
  所以两处天然同步，不存在「各存一个开关」

---

## 红线（票面/主人明确）

- **不放宽 `panLimit`**、不改 `viewMargin`、不改边界 clamp 数值 —— 放宽会换来「能拖到空白」的新抱怨
- **不重写手势识别框架**（`awaitEachGesture` 那套是对的）
- 双击**不许顺带选中地块**（票 18 的 `DOUBLE_TAP_MS` 300ms 窗口 + 8dp 位移阈值保持原样）
- 只跑 `compile-check.bat`；不装机、不截图

---

## 涉及文件

| 文件 | 改动 |
|---|---|
| `ui/map/MapViewRules.kt` | 新增 `doubleTapZoom` / `ZoomPan` / 甩动常量与 `flingDecay` / `shouldFling` / `flingStep` |
| `ui/map/CampusMapScreen.kt` | 双击改走 `doubleTapZoom`；`VelocityTracker` + `startFling`；导出栏加共建按钮 + 弹窗 |
| `ui/settings/ContributeSection.kt` | 抽出 `MapContributeConsentDialog`，设置页改为调用 |
| `app/src/test/.../MapViewRulesTest.kt` | 新增 5 条：双击锚点（3 位置 × 3 缩放档）、缩放上限、摩擦连续换算、轻推不滑、滑行位移与速度成正比、边界阻尼 |

> 注：`MapViewRules.kt` 实际在 `ui/map/` 而不是 `data/map/`（票面写的是后者），本次没搬家 —— 搬文件不在范围内。

---

## Comments

- 2026-11-14 lead：主人拍板只做两项手势 + 共建入口搬家，范围外不做。

## Comments

### 2026-11-14 交付（agent）

`compile-check.bat`：**BUILD SUCCESSFUL / EXIT=0**；`MapViewRulesTest` **20 条全绿**（新增 5 条）；
全量 **525 条 / 39 类 / 0 失败**（含并行代理新增的类）。

#### ① 双击锚定（`MapViewRules.doubleTapZoom`）
以前 `zoom *= 2` 以画布中心为不动点。现在与捏合同一个模型（复用 `panAfterZoomAt`），
锚点 = **点击点**。单测锁：3 个位置（中心 / 左上 / 右下）× 3 个缩放档（1× / 2× / 4×），
放大后点击点下的内容位移 **< 0.5px**（票面要求 40dp，实际严了近 80 倍）。
另锁「双击到 8× 封顶后不再漂」。

#### ② 惯性甩动
- `VelocityTracker` 只在**单指**拖动时喂点（双指缩放不甩动）
- 松手 `shouldFling(speed, 100dp/s)`：不满足（含 NaN）→ 直接 `snapBack()`，**不漂移**
- 满足 → `startFling`：`withFrameNanos` 逐帧推进，`velocity *= 0.85^(dt/16.67ms)`（连续换算，掉帧不变手感），
  每帧 `flingStep()` 内部 `applyOverdrag` 施边界阻尼，**最多 1200ms**，滑完统一 `snapBack()`
- 用 `withFrameNanos` 而非 `animate`：滑行中要按帧判断越界并改道，`animate` 只会沿预设曲线走到底

#### ③ 地图页「参与共建」入口
- `ContributeSection.kt` 抽出 `internal fun MapContributeConsentDialog(onDismiss, onConfirm)`，
  设置页改为调用它（**文案只有一份**）
- 地图页导出栏：未开启显示 `参与共建` → 就地弹同一弹窗；已开启显示 `参与共建 · 已开`（只写名字+状态）
- 状态来自**同一个** `MapContributeStore`（`setEnabled` / `setAcknowledged`），两处天然同步

#### 一次测试自纠（值得记一笔）
`滑到边界施阻尼而不是硬停` 第一版断言错了：写了魔数 `600f` 当「未加阻尼的落点」，
但那隐含「起点是 0」，而实际起点是 `90f` —— 断言与实现静默错位（看起来在测、其实测错了）。
**实现是对的，改的是测试**：现在把 `start / rawStep / rawLanding / over` 全写成命名变量，
公式自己说话；另加两条方向性断言（阻尼后的越界量必须小于原始越界量；界内一点不衰减），
比钉死单个数值更能防回归。

**范围确认**：没放宽 `panLimit`、没改 `viewMargin`、没改边界 clamp 数值、没重写手势框架；
双击的 `DOUBLE_TAP_MS = 300` + `TAP_SLOP_DP = 8` 原样保留（双击仍不会顺带选中地块）。
**一处如实说明**：`MapViewRules.kt` 实际位于 `ui/map/`（票面写的是 `data/map/`），本次**没有搬家**。
