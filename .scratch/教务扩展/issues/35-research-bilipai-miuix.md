# 35: 调研 —— BiliPai 与 Miuix 的设置页与组件设计拆解（纯调研，不改代码）

**What to build:** 一份可直接开工的借鉴清单，回答主人那句
「参考 bilipai 这个仓库开源项目，我看他也有很多好的设计，设置页面，开源组件库」。
结论落在 `docs/调研-BiliPai与Miuix设计拆解.md`。

**Blocked by:** 无

**Status:** ready-for-human

---

## 边界（本票遵守情况）

- [x] 纯调研：**没有改动本项目任何代码**，只写文档
- [x] **没有编译、没有装机、没有跑 Gradle**（lead 在构建）
- [x] BiliPai GPL-3.0 → **一行代码都没进仓库**，只 clone 到 `O:\dev\refs\BiliPai` 读
- [x] Miuix Apache-2.0 → 只 clone 到 `O:\dev\refs\miuix` 读；**没有新增依赖**
- [x] 分节落盘（§0–§2 上一轮，§3–§5+附录本轮，每节写完即保存）

## 取证方式

`git clone --depth 1`：`jay3-yy/BiliPai@35135a1`、`compose-miuix-ui/miuix@d8efe18`；
逐文件读源码；依赖坐标另查 Maven Central `maven-metadata.xml`（2026-11-15 实测）。
文档附录列了全部取证路径。

## 关键结论（摘要，详见文档）

1. **两条流传的线索是假的**，先打假以免选型跑偏：
   - **Orbital**：全仓 grep `Orbital` **0 处命中**。它的共享元素过渡走 ITGSA
     `gsai.sdk:sharedview-kit:1.0.0.5`（不透明联盟 Nexus + 明文 HTTP，别碰）+ 自研控制器。
   - **compose-shimmer**：**被 BiliPai 主动移除**（根 `build.gradle.kts:507` 原话「全仓 0 处 import」，
     还有单测断言不再使用）。骨架屏是自研线性扫光 + 双色脉冲。
2. **Miuix 是真的**，但 BiliPai 用的是 `0.9.4-b7a5f08f-SNAPSHOT` + **GitHub Packages 要凭据**，
   还 fork 了 `miuix-navigation` 保留自家补丁 → **别按它的坐标抄**。
   Central 上 `top.yukonga.miuix.kmp:*` 有干净的 **0.9.4**；`miuix-glass` 在 Central **404**。
   Miuix 自身 `COMPILE_SDK=37 / MIN_SDK=24`，我们 `35/26` → minSdk 兼容，**compileSdk 是待验证风险项**。
3. **facade 是三层不是两层**：`design-tokens`（纯数据 7 文件）→ `components/App*`（唯一对外契约，63 文件）
   → `renderer/{miuix,material3}`（**internal**，两套各 15 文件一一对应，编译器逼补齐）。
   切换只靠一个 `LocalAppUiStyle`，业务层从不问当前是哪档。
4. **纪律 vs facade 的区别**（lead 问的那条）：纪律约束在**写代码的人**身上（违反→测试红→人工改回去），
   facade 把约束放到**结构**上（业务拿不到裸数值和后端组件，破例必须显式传参数）。
   结论：**我们现在不该照建这套层**（只有一个后端，双档收益=0），
   只搬它有用的那一半 = `policy 纯函数返回数据类 + JVM 单测钉死数值`。
5. **设置页最值得搬的一件事**：一行里 `title / subtitle(说明) / value(当前值)` 三种文字**各占固定位置、
   绝不合并**，且 subtitle **跟着开关状态换文案**；长解释一律进弹窗，
   但隐私与不可撤销操作**必须在决策点上给全**（它的详细日志开关是「不确认就不开」）。
6. **它的一条自身规矩可直接用**：分组标题不许复读上级名
   （源码注释：「避免"隐私与权限里还有隐私与权限"」）。

## ★ 第 3 节动效数值（已单独成节，交 home-api-fix-2 / 票 33）★

- **Pager**：`stiffness 322.2f`、`dampingRatio = 32.31/(2·√322.2) ≈ 0.899`、`visibilityThreshold 0.5f`
  （出处 Miuix `PagerGestureUtils.kt:80`，Apache-2.0）。
  程序翻页用 `MutatePriority.UserInput`（焦点滚动打不断翻页）；
  跨轴 fling 只吃横轴剩余速度（`onPostFling → Velocity(available.x, 0f)`）；
  `snapAnimationSpec` 传进 `PagerDefaults.flingBehavior`。
  建议课表：`322.2 / 0.90 / 0.5f`，要更跟手只把 stiffness 提到 ~420f；**别用 dampingRatio=1f**。
- **Folme 换算**：`stiffness = (2π/response)²` ⇒ response 0.35s 就是 322.2（BiliPai 与 Miuix 同一物理量）。
- 弹簧总表 11 条（BiliPai）+ Folme 表 10 条（Miuix），含按压「进快硬 1.0/0.2s、出慢软 0.95/0.35s」。
- 时长：Miuix 档 standard **180ms** / emphasized **240ms**；MD3 档 **200/300ms**（单一函数定义）。
- 缓动曲线 15 条含控制点。
- 入场：单 progress 派生 alpha/位移/缩放，三档（Reduced/Normal/Enhanced）参数表，
  `maxStagger` 有上限（72/160/220ms）。
- 骨架：扫光 `1000ms Linear`、`-0.5→1.5`、停靠 `0.1/0.3/0.5/0.7`、alpha light 0.06–0.11 / dark 0.10–0.16；
  **原地替换的骨架用固定色不用脉冲**（它注释解释得很清楚）。
- 减弱动效：`ANIMATOR_DURATION_SCALE == 0` → **完全不播**，不是减半。

## 验收对照

- [x] 5 个必需小节齐全（设置页 IA / 组件体系 / 动效数值 / 协议结论 / 适配判断）
- [x] 第 3 节单独标出并可直接给票 33
- [x] 逐条对照我们设置页被嫌弃的点（文档 §1.7 一张 8 行对照表）
- [x] 协议结论区分「能引依赖 / 只能看不能抄 / 别碰」
- [x] 误搬清单给足理由（文档 §5.2，7 条，每条写明「它为什么成立」）

## Comments

- 2026-11-15 faculty-research：完成。`docs/调研-BiliPai与Miuix设计拆解.md` 738 行。
  两条线索打假（Orbital / compose-shimmer）建议回写到任何引用它们的地方。
