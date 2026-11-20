# 讨论：Kotlin/Compose 的能力边界、项目底层与浏览器复现

日期：2026-11-20 · 对应原型：[docs/design/UI-浏览器复现与能力对照.html](UI-浏览器复现与能力对照.html)

> 本轮交付为讨论与原型，Kotlin 代码保持现状。原型是单文件自包含 HTML，顶部可切「浏览器全能力 / 真机 API 29 降级 / HARD 原始口径」三档与明暗。

## 1. 结论先给

**当前 UI 能在浏览器里 1:1 复现，而且已经复现了。** 色板、字阶、1dp 线、圆角、投影、折角、220ms 开口缓动、纸纹噪点，两边共用同一套数值；原型里的 CSS 变量名与 Kotlin 的 `InkTokenSet` 字段同名，不是照着画，是逐字段搬。

差的那一点**与 Kotlin 绘制能力无关，也与组件库无关**，是「背后要糊的内容从哪来」：

- 浏览器：`backdrop-filter: blur(20px)` 直接把身下任意层内容糊成雾，原型背后铺了列表文字与照片，所以雾感足。
- App：真机 API 29（Android 10）没有 `RenderEffect`，`Modifier.blur` 只模糊自身不模糊背后；且 App 背后是纯纸色列表，即使 API 31+ 打开系统模糊，糊出来的也只是**文字灰雾**，与照片雾不同。

所以「HTML 好看、真机不像」的归因顺序是：内容来源 > 系统版本 > 组件库。引入 haze / BlurView / 任何第三方 UI 库，在 29–30 上依然只能淡化，等于换皮。

## 2. Kotlin 能做到什么程度（四档）

| 档 | 效果 | 判定 |
|---|---|---|
| A | 布局、色板、字阶、线、圆角、投影、折角、缓动曲线、手势、滚动、Canvas 绘制 | **像素级一致**。dp/sp/tween/spring 与 CSS 是同一套数学 |
| B | 真背景模糊 | **换实现可到**。API 31+ 走 `RenderEffect.createBlurEffect`；API ≤30 走「快照 → CPU stack-blur → Bitmap 叠遮罩」，约 10ms，全版本一致 |
| C | 系统返回键、壁纸取色、通知、Glance 小组件、加密凭据、后台任务、离线 brotli 资产、传感器 | **App 独有**，浏览器侧没有对应物，属于平台能力差异 |
| D | 在 API ≤30 上让系统合成器实时模糊「别的窗口 / 背后内容」 | **只有这条受系统版本约束**，属于 Android 图形栈边界；绕路走 B |

Compose 反而比浏览器强的两块：原生嵌套滚动分发（`NestedScrollConnection` + `snapshotFlow`，无布局抖动）、`BackHandler` 接管系统返回。Canvas 级效果（4D 超立方体、考试轴 LED、地图 173 面）算法两边相同，只是 API 名不同。

## 3. 设计主题

**黑土 + 农机漆 + 田垄网格**（`app/src/main/java/cn/dkarbon/enroute/ui/theme/Theme.kt:13-30`）。

- HARD 档：圆角全 0、无柔和阴影（立体用 2dp `hardShadow` 硬投影）、1dp 实线 `#8F7E61`、农械橘 `#C74E17` 只占小面积（色条/图标/主按钮）、纸白 `#F5F2EC`、系统黑体 + 等宽数字、零纹理零渐变。
- 三套 `FormSpec`（HARD / SCHOLAR / EDITORIAL / Ledger）与明暗**正交**；`EnRouteTheme` 用 `CompositionLocalProvider` 下发 `LocalInkTokens` / `LocalPrimaryInk` / `LocalForm`。
- 壁纸取色与自定义配色**只覆盖 primary / accent 两槽**；砖红、橄榄、赭黄等语义色固定 —— 这是数据诚实红线，保持固定。
- 小字主色用 `Machine.n600 #A83D11`，因为 `#C74E17` 对纸底只有 4.15:1。
- 就近实装（`AnchoredCard.kt`）已经从 HARD 偏到「纸片派」：14dp 圆角、26dp 柔影、48×48 噪点 `ShaderBrush`、顶 1dp 高光 + 底 1dp 阴影、亮档 0.97 厚纸。**这是有意的形态 C 定案，与 Theme.kt 无关**；原型第三档可随时看回原始 HARD 口径。

## 4. 项目底层

```
MainActivity · setContent { … }
└── EnRouteTheme(themeMode, styleSet, colorSource, customPalette)
    ├── LocalInkTokens / LocalPrimaryInk / LocalForm   ← 设计主题全部来源
    │   └── MaterialTheme(colorScheme, typography, shapes)
    └── EnRouteRoot(ui, vm)
        └── when (tab) { 今日 / 资讯 / 课表 / 我的 }   ← 切页靠 when 分发，未引入 NavHost

TimetableViewModel (AndroidViewModel)
└── MutableStateFlow<AppState>   ← 一个大 data class 装全屏状态
```

- **单 Activity，未引入 NavHost / rememberNavController / 第三方 UI 组件库**。依赖只有 Compose BOM 2024.10.01 + material3 + material-icons-core + foundation + kotlinx.serialization + coroutines + brotli-dec + Glance + WorkManager + core-ktx。
- 数据层六件套，全部手写：`SharedPreferences`（偏好/凭据开关/更新/共建）、`filesDir` + kotlinx.serialization（课表/资讯/成绩/标注 JSON）、`HttpURLConnection`（教务 URP / 21 资讯源 / 更新清单）、`assets.open` + `BrotliInputStream`（2066 门大纲 + 168 份方案，4.64 MB 离线）、AndroidKeyStore AES/GCM（凭据）、WorkManager（提醒/小组件）。未引入 Room / DataStore / Retrofit。
- minSdk 26 / compileSdk 35 / JDK 17 / release 开 R8；`.br` 以 stored 进包（`noCompress.add("br")`）。

## 5. 浏览器复现口径

原型 `docs/design/UI-浏览器复现与能力对照.html`：

- 左侧 412×915 手机 = 当前资讯页 + 锚定操作卡片（形态 C），无说明性文字，假数据用「张三 / 2024000000」口径（本页无姓名学号）。
- 右侧三张表：逐效果「浏览器 / Kotlin / 真机 API 29」三列对照、项目底层树、四档能力分级。
- 顶部三档切换：`full` 用 `backdrop-filter: blur(20px) saturate(1.10)`；`api29` 关掉真模糊、纸底提到 0.97、遮罩 0.38；`hard` 回到 0 圆角 / 零渐变 / 硬投影。明暗两套色值与 `InkTokens.Light/Dark` 同名同值。

已用 Edge `--headless=new` 截图自查四组（亮全能力 / 亮 API29 / 暗 API29 / 亮 HARD），工具栏暗色字、卡片折角、纸纹、选中态左缘 3dp 条均正常。

## 6. 下一步三条路线（待定，本轮不动手）

| 路线 | 做法 | 代价 | 效果 |
|---|---|---|---|
| A 快照模糊 | 浮层打开时把背后列表渲成 Bitmap，CPU stack-blur 后叠在卡片下 | 约 10ms / 次，纯 Compose，API 26+ 全版本一致 | 真雾感，与系统版本解耦 |
| B 底图影像 | 卡片背后铺一层低饱和校园影像（assets 内，离线） | 加约 60–90 KB 资产 | 不模糊也有雾，与 A 叠用最接近原型 |
| C 保持纸片派 | 现状：纸纹 + 硬投影 + 折角 + 双线边 | 0 | 干净、可读，观感停在纸片 |

建议 A + B；若只想零成本收敛，就把原型 `full` 档的 alpha 从 0.72 再往上调，接受「像纸不像玻璃」。三条均无需引入组件库。

## 7. 关联状态

- m33（`versionCode 410 / 0.8.1+m33`）已装机验证，未发版、未 commit/tag。
- 换钥迁移方案见 `docs/发布-签名更换与迁移.md`。
- 图标三版与替换口径见 `docs/设计-图标三版方案.md`。
