# 25: 主题自定义配色 —— 预设色板 + 明度/饱和度微调 + 对比度闸门

**What to build:** 在「固定 / 壁纸」两档取色之外，加第三档 **自定义配色**。形状由主人拍板：
**预设色板 + 微调**，不做自由取色环；选中色实时算对比度，不达标不给保存。

**Blocked by:** 无（`DynamicColorRules.override` 已在，缺的是第三档与取色入口）

**Status:** ready-for-human

---

## 1. 先说清「为什么不是拾色器」

这套配色的语义是**绑死在颜色上**的：砖红=错误/不及格、橄榄=通过、琥珀=待办。
自由取色十分钟就能做出「正文看不清」的主题，而用户不会认为那是自己选的色的问题 ——
他只会觉得「这 App 字好淡」。所以：

| 决定 | 理由 |
|---|---|
| 只用预设色块（每行 8 个） | 预设全部取自既有色阶（`Machine` / `Crop` / `Steel` / `RustRed` / `Wheat` / `Soil` / `Ledger`），**开箱即达标**，有单测逐个验 |
| 只放开 **明度 / 饱和度** 两条滑条 | 解决「这个绿再深一点就好了」；不放开色相，配色协调由色板本身负责 |
| 只覆盖 `primary` / `accent` | 与壁纸取色同一条纪律：纸底、墨色正文、砖红/橄榄/琥珀**一毫米不动**（数据诚实） |
| 浅色 / 深色**各存一套** | 纸白上达标的主色压到黑土上就是一团糊，两套不能共用 |
| 保存前过闸门 | 存在「先存进去再发现看不清」的路径就等于没闸门 |

## 2. 落地内容

### ① 第三档 `ColorSource.CUSTOM`

`DynamicColors.kt`：`FIXED(0) / WALLPAPER(1) / CUSTOM(2)`，
`isValid` / `normalize` / `name` / `shortName`（设置页按钮只放短名）同步扩展。
不认识的值仍回落 `FIXED`（老/坏数据不让界面变空白）。

### ② 预设色板（`ui/theme/CustomPalette.kt` · `PalettePresets`）

浅色行 8 + 深色行 8，主色/强调色各一行。**刻意剔除品牌橙 `#C74E17` 出主色行**：
它对纸白只有 4.15:1，当小字正文不达标（这正是 `primaryInk` 存在的理由），
但作强调色（色条/高亮线，3:1 门槛）是够的，所以留在强调行。

**对比度实测（本机脚本现算；浅色取硬朗/账页两套纸底的较差值，深色取两套墨底的较差值）**

浅色 · 主色（门槛 4.5:1，实测取「纸底 / 卡片 / 按钮反白」三者最小）

| 色块 | hex | 实测 |
|---|---|---|
| 农机橘（深）`Machine.n600` | `#A83D11` | **5.63:1** |
| 农机橘（更暗）`Machine.n700` | `#852F0D` | 7.80:1 |
| 锈红 `RustRed.n500` | `#9B2417` | 7.07:1 |
| 阜绿 `Crop.n600` | `#3F5A2E` | 6.93:1 |
| 钢青 `Steel.n500` | `#345A66` | 6.71:1 |
| 松针绿 `Ledger.Pine` | `#1F4A3D` | 8.93:1 |
| 麦秆黄（深）`Wheat.n600` | `#8F6713` | 4.56:1 |
| 黑土 `Soil.n700` | `#443E36` | 9.45:1 |

浅色 · 强调色（门槛 3.0:1，非文字图形）

| 色块 | hex | 实测 |
|---|---|---|
| 农械橘 `Machine.n400` | `#DE6A2E` | **3.03:1** |
| 品牌橙 `Machine.n500` | `#C74E17` | 4.15:1 |
| 麦秆黄 `Wheat.n500` | `#B4841B` | 3.00:1 |
| 阜绿（亮）`Crop.n400` | `#6D8148` | 3.85:1 |
| 钢青（亮）`Steel.n300` | `#6E8D94` | 3.19:1 |
| 锈红（亮）`RustRed.n300` | `#D46B58` | 3.11:1 |
| 陶土红 `Ledger.Clay` | `#B4553C` | 4.37:1 |
| 阜绿（深）`Crop.n500` | `#4E6535` | 5.81:1 |

深色 · 主色（门槛 4.5:1）

| 色块 | hex | 实测 |
|---|---|---|
| 农机橘 `Machine.n300` | `#EA8F5E` | **6.57:1** |
| 农机橘（浅）`Machine.n200` | `#F3B994` | 9.32:1 |
| 阜绿 `Crop.n200` | `#BCC79B` | 9.02:1 |
| 钢青 `Steel.n200` | `#A0B6BB` | 7.58:1 |
| 松针绿 `Ledger.PineDark` | `#8FBFA6` | 7.78:1 |
| 麦秆黄 `Wheat.n300` | `#DDB84D` | 8.45:1 |
| 锈红 `RustRed.n200` | `#E7A093` | 7.54:1 |
| 土白 `Soil.n200` | `#D7D0C4` | 10.49:1 |

深色 · 强调色（门槛 3.0:1）

| 色块 | hex | 实测 |
|---|---|---|
| 农机橘 `Machine.n400` | `#DE6A2E` | 4.75:1 |
| 品牌橙 `Machine.n500` | `#C74E17` | **3.47:1** |
| 麦秆黄 `Wheat.n300` | `#DDB84D` | 8.45:1 |
| 阜绿 `Crop.n300` | `#93A46B` | 5.94:1 |
| 钢青 `Steel.n300` | `#6E8D94` | 4.52:1 |
| 锈红 `RustRed.n300` | `#D46B58` | 4.62:1 |
| 陶土红 `Ledger.ClayDark` | `#E0937A` | 6.60:1 |
| 土白 `Soil.n300` | `#BEB5A6` | 7.92:1 |

> 单测 `CustomPaletteTest.预设…全部达标` 把 8×8 组合 × 2 主题 × 2 明暗全跑一遍，
> 任何一个组合掉档都会红 —— 上表只是人读的快照，真正把关的是那条测试。

### ③ 微调（`HslTune`）

- 明度滑条 `-1..1` → 实际位移 `±0.24` 亮度
- 饱和度滑条 `-1..1` → 实际缩放 `±60%`
- **色相永不改**（单测锁：明度/饱和度滑完，色相漂移 < 1°）
- 结果强制不透明（ARGB 的 alpha 固定 `0xFF`，半透明色会让墙面发灰）

### ④ 对比度闸门（`PaletteGate`）

五条关系，任何一条不过 → 「不可保存」并逐行写出**是哪一对**、实测多少、门槛多少：

| # | 关系 | 门槛 |
|---|---|---|
| 1 | 主色 / 纸底（主色会被当正文色用） | 4.5:1 |
| 2 | 主色 / 卡片 | 4.5:1 |
| 3 | 按钮文字 / 主色（实底主按钮反白） | 4.5:1 |
| 4 | 强调色 / 纸底 | 3.0:1 |
| 5 | 强调色 / 卡片 | 3.0:1 |

校验用的是**被编辑那一套**的底色板（编辑深色槽时按墨底算，不是看当前界面）。
`baseTokenSet(styleSet, isDark)` 从 `Theme.kt` 抽出来，与 `EnRouteTheme` 同一处取值，避免两处漂移。

**没有动那条反向断言**：`PrimaryInkContrastTest.brand primary stays below 4_5 on light paper`
依旧要求品牌橙 `#C74E17` < 4.5:1 —— 它现在是「品牌橙进不了主色行」这条规则的证据，两件事互相印证。

### ⑤ 小字主色跟着换（`LocalPrimaryInk`）

原来 `primaryInk()` 写死 `Machine.n600`；主色一换，就会出现「按钮是松针绿、旁边小字还是品牌橙」。
- 自定义配色：闸门已保证主色本身 ≥4.5:1 → `primaryInk = 主色`
- 壁纸取色：没有闸门可拦 → `PaletteGate.inkVariant()` 保持色相，朝远离纸底方向每步 3% 明度推到 ≥4.5（尽力而为）
- 固定色板：`null`，走原来的规则（**老主题渲染一字不变**）

### ⑥ 持久化（`Prefs`，同「清除所有数据」一起清掉）

四个整数键，`0` = 该槽未设置（不是黑色）：

```
custom_primary_light / custom_accent_light / custom_primary_dark / custom_accent_dark
```

`Prefs.clearAll()` 是 `sp.edit().clear()`，所以「清除所有数据」自动带走；
`CustomPalette.withMode` 只写**当前编辑那一套**的两个槽，另一套原样保留 —— 这就是「切主题不丢」。

### ⑦ 设置页（`SettingsScreen` 的取色段 + 新文件 `ColorTunerPanel.kt`）

按 AGENTS.md「按钮不自带说明书」重排：原来那段 60 字的说明从列表里撤掉，按钮只留名字。

```
取色                         [固定] [壁纸] [自定义]
农业色板                       ← 状态一行，≤20 字
┌ 展开（选「自定义」时）──────────────────────────┐
│ 编辑 [浅色] [深色]                                │
│ 主色   ■ ■ ■ ■ ■ ■ ■ ■                           │
│        明度 ──●────  饱和度 ──●────              │
│ 强调色 ■ ■ ■ ■ ■ ■ ■ ■                           │
│        明度 ──●────  饱和度 ──●────              │
│ ┌ 纸底 / 主色文字 / 主按钮 / 强调条 / 5.63:1 ┐    │
│ 对比度达标 · 主色 5.63:1 · 强调 3.03:1            │
│ [为什么]                       [恢复默认] [应用并保存] │
└──────────────────────────────────────────────────┘
```

- 不达标时：逐行红字 `主色/纸底 3.90:1 未达 4.50:1`，保存按钮变成灰色「不可保存」
- 「为什么」→ 弹窗列出五条关系的实测值与门槛（长解释进弹窗，不进列表）
- 色块 `contentDescription` 带颜色名（色块不能只靠颜色表达），触区 38dp 外框 ≥48dp 命中区

## 3. 改了哪些文件

| 文件 | 改动 |
|---|---|
| `ui/theme/CustomPalette.kt` | **新增**：`ContrastRules` / `ContrastCheck` / `HslTune` / `PalettePreset(s)` / `CustomPalette` / `toArgbInt` / `PaletteGate` |
| `ui/theme/DynamicColors.kt` | `ColorSource` 加 `CUSTOM`、`shortName()` |
| `ui/theme/Theme.kt` | `EnRouteTheme` 加 `customPalette` 参数并套用；抽出 `baseTokenSet()`；新增 `LocalPrimaryInk` 并下发 |
| `ui/theme/EditorialTokens.kt` | `primaryInk()` 先读 `LocalPrimaryInk`（固定色板下仍是原逻辑） |
| `ui/settings/ColorTunerPanel.kt` | **新增**：预设行 + 双滑条 + 预览条 + 闸门提示 + 「为什么」弹窗 + 保存/恢复默认 |
| `ui/settings/SettingsScreen.kt` | 取色段改三档 + 状态一行 + 展开面板；删掉原来那段长说明 |
| `data/Prefs.kt` | 四个自定义色键（**只加字段**，不碰其它） |
| `ui/AppState.kt` | 一个 `customPalette` 字段 |
| `ui/TimetableViewModel.kt` | 启动批量读四个槽；`setCustomPalette()` |
| `MainActivity.kt` | 把 `ui.customPalette` 传给主题 |
| `test/CustomPaletteTest.kt` | **新增**：14 条（三档 / 预设达标 / 拦截 / 浅深独立 / 微调 / inkVariant / 存储 / 公式同口径） |
| `test/ThemeDynamicTest.kt` | `isValid(2)` 断言从「非法」改为「CUSTOM 合法」 |

## 4. 验收对照

| 验收项 | 状态 |
|---|---|
| 设置页能切三档，切换即时生效 | ✅ `setColorSource` → `AppState.colorSource` → `EnRouteTheme` 重组 |
| 选预设 → 全局生效（课表/资讯/设置都变） | ✅ 只换 `primary`/`accent`，所有页面都读 `InkTokens.current` |
| 重启保持 | ✅ 四槽写 Prefs，启动时 `init` 批量读回 |
| 低对比组合 → 保存禁用 + 说清哪一项 | ✅ `PaletteGate.failures` 逐条渲染；`canSave` 控制按钮 |
| 切深色不覆盖浅色 | ✅ `withMode` 只写一侧；单测 `浅色与深色各存一套写在四个槽里` |
| `DynamicColorsTest`/主题单测全绿（含反向断言） | ✅ 见 §5（三个类 28 条全绿，0 失败） |
| 单测新增：三档 / 深色独立 / 对比度拦截 | ✅ `CustomPaletteTest` 17 条 |

## 5. Comments

- 2026-11-14 20:5x design-v3-2：实现完毕，**树是绿的**（不是半成品）。
  - `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin` → **BUILD SUCCESSFUL**（主源码 + 单测源码）。
  - 主题相关三个测试类实跑（`--tests` 定向，没跑全量）：

    | 测试类 | 用例 | 失败 |
    |---|---|---|
    | `CustomPaletteTest`（新增） | 17 | 0 |
    | `ThemeDynamicTest`（改 1 条断言） | 7 | 0 |
    | `PrimaryInkContrastTest`（**未改**，反向断言仍绿） | 4 | 0 |

  - **没有跑 `build-test.bat`**（5 个代理并行，会连累别人的构建）。`assembleDebug` 与全量单测
    由 Lead 安排时序后统一跑。
  - 期间工作树被其他代理的中间态打断过两次（`TimetableViewModel.kt` 575 行附近丢了一行 `if (list == null) {`、
    `EnRouteRoot`/`MainActivity` 的路由重构、`ExamScreen.kt` 89 行语法错）。**没有去改别人的文件**，
    等其落定后重跑编译 —— 一次全绿。
  - 一处顺带修正：`primaryInk()` 原来对主色硬编码，自定义/壁纸换色后会与小字割裂，改为先读 `LocalPrimaryInk`。
    固定色板下 `LocalPrimaryInk == null`，**老主题渲染不变**。
  - 已知取舍：面板草稿（选中的色 + 滑条）用 `remember` 不跨旋转保存 —— 旋转后回到「已保存值」。
    这是刻意的：把一堆 Float 塞进 `rememberSaveable` 需要额外 Saver，出错面比收益大。
  - 复查要点（给主人）：
    1. 设置页「取色」现在只有 `固定 / 壁纸 / 自定义` 三个按钮 + 一行状态，原来那段长说明已按新文案约定撤掉；
    2. 选「自定义」展开面板，挑一个浅黄当主色应看到红字 `主色/纸底 x.xx:1 未达 4.50:1` 且按钮变「不可保存」；
    3. 切「深色」分页再挑色 → 保存 → 切回浅色，浅色那套不该被覆盖。
