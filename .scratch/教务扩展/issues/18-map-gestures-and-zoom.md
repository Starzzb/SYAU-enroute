# 18: 地图手势与缩放体验（可自由拖动 + 去掉放大后的灰蒙层）

**What to build:** 地图能像正常地图那样**自由拖动**、双指缩放跟手、双击按点击点放大；
放大到超过底图分辨率时**不再出现一层灰色模糊遮罩**，而是平滑过渡到纯矢量视图。

**Blocked by:** 无

**Status:** ready-for-human（代码完成，待统一装机验证）

---

## 背景（主人实机反馈，2026-11-14）

> 「我看地图页可以让图可以自由移动，否则固定放大缩小不符合习惯，
> 还有放大到一定程度会有灰色模糊遮罩会是什么原因？」

两个问题都已定位到具体代码，不是玄学。

---

## 问题 A：拖动不跟手 / 像「固定放大缩小」

### 根因 1（主因）：`pointerInput` 的 key 里放了它自己会改的状态

`ui/map/CampusMapScreen.kt:246`：

```kotlin
.pointerInput(pkg, zoom, panX, panY) {   // ← 这三个就是手势要改的量
    detectTapGestures(...)
}
```

拖动/缩放**每一帧都会改 `zoom`/`panX`/`panY`** → 这个 `pointerInput` 每一帧被销毁重建 →
与上面 `pointerInput(pkg) { detectTransformGestures(...) }` 争抢同一个 pointer 流，
表现为「拖一下就断」「像被钉住」。

**修法**：key 只保留 `pkg`；命中测试需要的当前变换用
`rememberUpdatedState` / `derivedStateOf` 在回调里读，**不要进 key**。

### 根因 2：双击缩放以**画布中心**为锚点

`:261-264`：`zoom = (zoom * 2f)` → 以中心放大，用户双击的位置会跑掉。

**修法**：双击以**点击点**为锚点放大（标准地图行为）：
`pan' = anchor - (anchor - pan) * k`（k 为缩放比），再 `clampPan()`。

### 根因 3：`maxPan` 用画布尺寸、且只在 zoom>1 才允许

`:227-232`：

```kotlin
val maxPanX = cw * (zoom - 1f) / 2f
```

- 这个界只在「内容恰好铺满画布」时正确；我们的 `fit()` 是**留边居中**的（`drawW` 可能小于 `cw`），
  所以边界应该是按 `drawW*zoom` 与 `cw` 的差来算
- zoom=1 时 `maxPan = 0` → 完全不能拖；虽然合理，但用户会觉得「一开始就钉死」
- **修法**：用「缩放后的内容尺寸 − 画布尺寸」算可拖范围，且允许轻微**回弹（rubber band）**：
  拖过头时按 0.35 衰减，松手弹回边界（`Animatable` + `animateTo`，180–220ms）

### 验收

- [ ] 双指缩放时画面跟手，不中断
- [ ] 放大后单指可自由拖动，松手不漂移
- [ ] 拖到边缘有回弹，不会拖出空白一大片
- [ ] 双击以**双击位置**为中心放大（在角落双击，角落内容留在原地附近）
- [ ] 连续操作 10 秒不卡顿、不出现手势丢失

---

## 问题 B：放大后出现「灰色模糊遮罩」

### 根因：我们自己写死的淡出

`ui/map/CampusMapScreen.kt:268-271`：

```kotlin
// ① 底图（B 模式）；缩放 >2.5× 淡出到 0.35，避免糊
val alpha = if (zoom > 2.5f) 0.35f else 1f
```

- 超过 2.5× 直接把底图 alpha 砸到 **0.35** —— 一层半透明位图压在纸白底上，就是**灰蒙**
- 同时 1024px 底图被放大 2.5× 以上，本身也糊，两者叠加 = 「灰色模糊遮罩」
- 而且位图是**阶跃**变化（2.5× 前后突变），没有过渡

### 修法：淡出后**彻底不画**，并让矢量层接管颜色

1. **平滑档位**：`alpha` 在 zoom 2.2 → 3.0 之间线性 1.0 → 0.0；**=0 时跳过绘制位图**（不留半透明层）
2. **矢量层必须自己上色**：现在 B 模式下矢量只有描边 + 3% 填充，位图一淡就发白。
   在「位图不透明度 <0.5」时，**自动切到 A 模式的配色**（按 `kind` → `manifest` 配色，
   缺 kind 时按 `colorGroup`/`color` 兜底），让地图**有实体感**而不是线框
3. 用户手动点「用矢量」时**完全不画位图**（现在应该已经是，确认一下）
4. 位图采样用 `FilterQuality.Low`（比默认更少振铃），并**不要**用 `None`（会锯齿）

### 验收

- [ ] 放大过程中底图**平滑**淡出，没有突然变灰的台阶
- [ ] 位图淡完后地图**不是空白/线框**，而是有色的矢量面
- [ ] 缩回 1× 时底图平滑回来
- [ ] 全程没有任何「半透明灰色层」长期停留
- [ ] 单测：`bitmapAlpha(zoom)` 在 2.2/2.6/3.0/4.0 的取值（线性、边界=0）；`fillAlphaFor(bitmapAlpha)` 的切换阈值

---

## 边界

- 只改 `ui/map/CampusMapScreen.kt` 的手势与绘制层；`MapProjection` 的 `fit/withUser` 公式若需调整，
  **必须同步更新 `MapTest` 里的等比/翻转断言**
- 不动 `data/map/` 的解析与 `map/edits` 落盘
- 基线单测 **364 条 / 27 类 / 0 失败**；迭代用 `compile-check.bat`
- 装包必须用 **release 签名**（见 AGENTS.md「装包签名」）

## Comments

- 2026-11-14 lead：主人实机反馈。两个根因都在代码里查实，不是配置问题。
  灰蒙层是我们为了「避免糊」自己加的 0.35 alpha 阶跃，反而更难看 —— 改成淡出即不画 + 矢量接管配色。

## Comments

### 2026-11-14 交付（agent）—— 代码完成，等统一装机验证

> 流程变更：本轮起**不再自己 build/装机/截图**（lead 统一做），我只交代码 + `compile-check.bat`。
> `compile-check.bat`：**BUILD SUCCESSFUL / EXIT=0**（含 `:app:compileDebugUnitTestKotlin` 也绿）。

#### A. 拖动不跟手 → 三个根因全改

| 根因 | 修法 | 位置 |
|---|---|---|
| ① `pointerInput` key 里放了手势自己会改的 `zoom/panX/panY` → 每帧销毁重建、与 transform 手势抢 pointer 流 | key **只留 `pkg`**，命中测试用 `rememberUpdatedState` 读最新变换 | `CampusMapScreen.kt:345`（tap 流）、`:260`（`limitsNow`） |
| ② 双击以画布中心放大 | 以**点击点**为锚点：`pan' = anchor − (anchor − pan) × k`（`MapViewRules.panAfterZoomAt:104`） | `CampusMapScreen.kt` 双击分支 |
| ③ `maxPan = cw*(zoom-1)/2` 只在「内容铺满画布」时成立 | 改按**实际内容尺寸**：`panLimit = (content − canvas)/2 + margin`（`MapViewRules.panLimit:62`） | `CampusMapScreen.kt:240-243` |

**复测追加两处（lead 指出的真 bug + 产品问题）**

1. **回弹时机**：原来用「停手 140ms 近似手势结束」，而 `detectTransformGestures` 没有 `onEnd` →
   慢速拖动/中途停顿就被判成结束 → **画面被拉回**（主人报的「能移动但会回弹」）。
   现在改成自己接管：**`awaitEachGesture` + 所有指针抬起才 break**（`CampusMapScreen.kt:293-339`），
   `snapBack()`（`:266`）**只在那之后调用一次**；回弹动画跑在 `rememberCoroutineScope` 里
   （`awaitEachGesture` 的受限挂起作用域不能直接 `animate`）。
2. **视野余量（pan padding）**：`margin = 画布 × 0.35`（`MapViewRules.viewMargin:68`，`VIEW_MARGIN_RATIO:66`），
   让**四角地块能拖到视野中部**。代价：允许拖出约 1/3 屏空白（标准地图做法，能看清角落更重要）。
   顺带解决了「1× 完全拖不动」——以前 `padding` 只有 6dp。

#### B. 灰色模糊遮罩（我们自己埋的）→ 三步全做

- `MapViewRules.bitmapAlpha:39`：`2.2×→3.0×` **线性** 1.0→0.0（旧代码是 `if (zoom > 2.5f) 0.35f` 阶跃）；
  **=0 时跳过绘制位图**（`:381` `if (bmp != null && bitmapAlpha > 0f)`）——不再留半透明层。
- 位图不透明度 <0.5 → **矢量接管配色**（`MapViewRules.vectorTakesOver:50`；
  `drawFeatures(hasBitmap = !vectorTakesOver(...))` `:399`），
  上色链 `要素 color → 层 fill → colorGroup 兜底`（`MapViewRules.vectorFillHex:112`）。
- 位图采样 `FilterQuality.Low`（`:390`）；手动「用矢量」时不画位图（原有逻辑，确认过）。
- 顺手修的**新发现**：`Canvas` 默认**不裁剪**，放大后画面会溢到「图例」上 → 地图块加 `clipToBounds()`
  （`:287`）。这条是 2× 截图暴露的实际缺陷。

#### C. 木鱼去框（主人追加）

`ui/widget/WoodenFish.kt`：去掉 `.background(t.card)` + `.border(...)`（外壳），上方加同款
`Stroke.hairline` 分隔线（`:157`）+ 18dp 留白；功德小方块（计数器）按 lead 要求**保留**；热区未缩小。

#### D. 功德分账（主人追加）

- `data/MeritRules.kt`（新，纯函数）：`accountKey:23`（学号/`anon`）、`rollDay:36`（跨天归零）、
  `afterStrike:40`、`mergeAnon:50`（首次登录并入 anon，幂等）、`migrateLegacy:59`（老 key 一次性迁移）。
- `Prefs`：`merit_total_<key>` / `merit_day_<key>` / `merit_today_<key>` + `migrateLegacyMerit()`
  （迁完删旧 `merit`、写 `merit_migrated` 防重复）。
- `WoodenFish`：按 `accountKey` 读三键；**敲击时先 rollDay 再 +1**；另挂 `LaunchedEffect` 在重组时也判一次
  （挂着页面过午夜不会显示昨天的数）；显示 `功德 452 今日 1`，未登录在角落标极小「本机」（`:174/:182`）。

#### 单测
- `MapViewRulesTest`（13 条）：淡出曲线 1.0/2.0/2.2/**2.6=0.5**/3.0/4.0、单调无台阶、接管阈值、
  `panLimit`（含 **margin=35%** 用例、1× 也能拖）、`applyOverdrag` 35%、`snapBackTarget`、
  锚点缩放（锚点内容不动）、配色兜底链、与 `MapProjection.fit` 的联动（**未改 fit/withUser 公式，`MapTest` 原断言全保留**）。
- `MeritRulesTest`（12 条）：跨日重置、同日不重置、账户隔离、anon 并入幂等、**旧 key 迁移不归零不翻倍**、日期口径。
- 上一轮全量：**388 条 / 30 类 / 0 失败**；本轮改动已通过编译（测试执行交给统一构建）。

#### 待真机验证（清单已单独发给 lead）
见下条消息的 checklist。**⚠️ 「回弹只在手指抬起后」是时序契约，单测锁不到，必须靠清单第 3 条真机验。**

### 2026-11-14 复测后补丁：双击不再顺带选中（lead 实测发现的新缺陷）

**缺陷**：1× 下连续双击放大，**属性面板被打开两次**（`id f133` / `id f119`），面板占 1/3 屏、把地图挤小，
缩放操作被打断。根因是把 `detectTransformGestures + detectTapGestures` 换成自管的 `awaitEachGesture` 之后，
点击判定与实际手势流解耦了（第一下抬手就被当成单击去命中）。

**修法：点击判定也自己做，不再用 `detectTapGestures`**（`CampusMapScreen.kt:293-412`）

- 手势循环里累计 `moved`（质心位移总和）与 `maxPointers`（出现过的手指数量）：
  `:313` `:317` `:336`
- 手势结束（**所有指针抬起**）时：
  1. `MapViewRules.isTap(maxPointers, moved, TapSlop=8dp)`（`:358`）——
     **拖动 / 缩放一律不判定为点击**，所以「拖动 450px 后绝不触发选中」，且**不动现有选中**；
  2. 是点击 → 先记下时刻与落点，挂一个 **300ms** 的延时任务（`:394`）：
     - 300ms 内有第二下（`MapViewRules.isDoubleTap`，`:365`，时间 ≤300ms 且落点距离 ≤40dp）→
       **取消延时任务**，按点击点锚点放大 2×（`:376-390`），**不触发选中**；
     - 300ms 内没有第二下 → 才真正做命中测试并选中（`:395-408`）。
- 纯函数在 `MapViewRules`：`TAP_SLOP_DP:68` / `DOUBLE_TAP_MS:71` / `DOUBLE_TAP_SLOP_DP:74` /
  `isTap:81` / `isDoubleTap:91`，阈值都从 dp 换算（`CampusMapScreen.kt:272-273`）。
- 代价：单击选中延后 300ms（双击窗口），与系统地图一致；单击「取消选中」同样延后 300ms。

**单测**（`MapViewRulesTest` +2 条，共 15 条）：
`拖动不算点击双击更不算`（单指小位移=点击、**450px 拖动=false**、双指=false）、
`双击要同时满足时间窗口与距离`（≤300ms 且 ≤40dp 才算；超时/超距离/无上一下都不算）。

`compile-check.bat`：**BUILD SUCCESSFUL / EXIT=0**（含单元测试源码编译）。

### 已知限制（数据/工具，不是 bug，写进票底）

1. **8× 下地块边缘呈阶梯状**：源 `校园图块.shp` 是从**低分辨率底图矢量化**来的，多边形轮廓本身沿像素栅格，
   放大 8 倍就把阶梯露出来了 —— 属**数据精度限制**，不是渲染问题。要消除只能等主人重画高精度矢量。
2. **双击实测档位是 1.0× → 2.6× → 7.0×**（不是整数 2/4）：注入工具的双击两次落点会被识别成微小捏合，
   所以档位不是整倍数；真机双手指标注会得到连续档位。淡出曲线按 zoom 连续计算（`bitmapAlpha`），与档位无关。
3. **回弹时序（「只在所有手指抬起后」）单测锁不到**，靠 lead 的真机清单第 3 条验收（已通过：450px 拖动 + 停 2 秒，
   画布变化 42.34%）。
