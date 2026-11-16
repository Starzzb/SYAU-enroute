# AGENTS.md

沈阳农业大学「沈农在途」Android App（Kotlin / Jetpack Compose，包名 `cn.dkarbon.enroute`）。

## 先读这些

- `README.md` —— 构建方式、接口现状、测试数量
- `docs/URP-学生端接口清单.md` —— 教务系统 47 个入口的实测结论（**逆向接口的一切证据都在这里**）
- `docs/SYAU-校内平台清单与可嵌入性评估.md` —— 校内站群（VSB）可嵌入性
- `docs/评审-*.md` —— 各轮设计/代码评审结论
- `docs/_probe/` —— 真机与 PC 登录态抓下来的原始 payload（已脱敏），可当单测夹具

## 硬规矩（本仓库长期遵守）

1. **数据诚实**：解析不出来就留空并显示「暂缺 / —」，绝不填 0、绝不猜日期、绝不用相近字段冒充。
   典型：竞赛条目 `publishedAt` 故意留空（报名起始日 ≠ 发布日）；绩点缺失显示「暂缺」而不是 0.00。
2. **只读教务**：选课、退课、考试报名、评教提交、个人信息修改一律不碰。
3. **只缓存自己的数据**：不缓存他人课表/成绩（合规红线）。
4. **接口逆向要留证据**：新接口必须写清实测日期、请求形态、返回样本位置，并配 JVM 单测锁 schema。
5. **`*.syau.edu.cn` 的 robots 实测口径**（2026-11-14 按 4 站取样，不是简单的 `Disallow: /`）：
   ```
   Allow: /$    Allow: /*.htm$    Allow: /*.html$
   Disallow: /info/*.htm$   ← 仅主站有这条，学院子站没有
   Disallow: /*?*           ← 带查询参数的一律禁止
   Disallow: /
   ```
   按最长匹配优先：**静态 `.htm/.html` 允许，带 `?` 禁止，主站 `/info/` 禁止**。
   抓取器据此钉死「URL 含 `?` 直接报错」。**但结论不变：仍是个人测试构建，分发需学校授权。**

## 构建与验证

- 编译 + 单测：`O:\dev\enroute-android\build-test.bat`（日志 `build-log.txt` 是 **GBK**，
  用 `docs\_probe\cxcy\convert-log.ps1` 转 `build-log-utf8.txt` 再读）
- Gradle 8.11.1 / JDK 17，只能通过 `cmd.exe /c build-test.bat` 起（无 shell 工具）
- 真机：vivo V2301A（Android 16，device id `10AE1H1PYJ005ED`）；
  `mobile_install_app` 常先超时，等十几秒重试即可；**禁止 `mobile_clear_app_data`**（会清掉用户课表）
- 校内接口（10.2.0.47 / cxcy.syau.edu.cn）**只在校内网可达**；手机走移动数据时失败是正常现象

## Agent skills

### Issue tracker

本地 markdown：工单在 `.scratch/<feature-slug>/issues/<NN>-<slug>.md`，一张票一个文件。
详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用五个默认角色串：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。
详见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文（根目录 `GLOSSARY.md` + `docs/adr/`，两者目前尚未创建，按需由 `/domain-modeling` 懒生成）。
详见 `docs/agents/domain.md`。

## 视觉效果先做原型，再写代码（2026-11-16，主人指令）

**凡是"看得见的效果"——动画、新组件、版式改造——先出 HTML/静态原型给主人看，确认后再写进 App、再编译。**

原话：“每次出效果都先写 HTML 或者其他呈现效果看之后再写入程序，编译”。

为什么：Kotlin 改一轮要编译、装包、截图，十几分钟才看到结果；HTML 改一轮几十秒，
而且能**一次性并排呈现多个状态**（待机 / 进行中 / 成功 / 失败），比真机截一张图信息量大得多。
用错方向的代价差一个数量级。

### 做法

- 原型放 `docs/design/`，文件名带功能名，如 `接口诊断-钥匙开锁.html`
- 单文件、自包含、无外链；配色取自 `HardTokens` / `EditorialTokens` 的真实色值，别自己调
- 同一页里放**静态分镜**（每个状态一格并排）＋ **可重播的动态演示**
- 用 Edge 无头截图自查后再交给主人：
  `msedge --headless=new --disable-gpu --screenshot=... --window-size=1400,1100 file:///...`
- 原型里的假数据一律用张三 / 2024000000，**不许出现真实姓名学号**

### 什么不用先做原型

数据层、解析器、缓存策略、接口对接、纯文案调整 —— 这些没有"效果"可看，直接写代码 + 单测。

## 图标：只用 `material-icons-core`，超出部分自己画（2026-11-16）

`material-icons-extended` 的 aar 有 **34 MB**（core 只有 0.8 MB）。release 有 R8 剥离，
最终 APK 差别不到 1 KB；但 **debug 不剥离**，开发期装包和构建都会被它拖慢。

规矩：

- 依赖只留 `androidx.compose.material:material-icons-core`
- core 里没有、又确实要用的图标，画在 `app/src/main/java/cn/dkarbon/enroute/ui/icons/AppIcons.kt`
- 画布一律 24dp、viewport 24×24，与 core 口径一致，这样 `Icon(size = 19.dp)` 的观感不变
- 颜色写 `Color.Black`，交给 `Icon(tint = ...)` 覆盖，不要在图标里写死主题色

现有自绘五个：`Article`（资讯）、`Today`（本周）、`StarBorder`（收藏未选中）、
`MarkEmailUnread` / `MarkEmailUnreadFilled`（未读）。再加之前先确认 core 里真的没有：
`Icons.Outlined.*` 在 core 里是**有**的（`Close`、`Search` 都在），别想当然地重画。
## 并发构建（血泪教训）

**同一时间只允许一个 Gradle 构建**。两个进程同时写 `app/build/intermediates` 会出现
`dexBuilderDebug` 失败、报 `desugar_graph/.../graph.bin` 找不到 —— 那不是代码问题。

- 迭代期用 `:app:compileDebugKotlin`（`compile-check.bat`），快且锁争小
- 完整 `build-test.bat` 前先确认没有别人在构建
- 撞上了先删 `app/build/intermediates/desugar_graph` 再重试，**不要 kill 别人的进程**
## 装包签名（反复踩过的坑）

这台真机上装的包**必须是 release 签名**，否则 `adb install` 会报
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`（debug 与 release 证书不同）。

- **默认用 release 包**：`build-release.bat` → `app/build/outputs/apk/release/app-release.apk`
- 需要装 debug 包时，先按 release 密钥重签再装（能保住用户数据，不必卸载）：
  ```powershell
  & "O:\dev\tools\android\sdk\build-tools\35.0.0\apksigner.bat" sign `
      --ks O:\dev\keys\enroute-release.jks --ksAlias enroute `
      --in app-debug.apk --out app-debug-release-signed.apk
  ```
- **绝不能为了装上而卸载**：会清掉课表缓存、地图标注、已保存的登录凭据

## local.properties 写法

必须**逐行**写入，每行以换行结尾。曾经用字符串拼接导致 `sdk.dir=` 与 `ENROUTE_*` 粘成一行、
密码键出现两份（一份错），构建时好时坏。写入后自检：

```powershell
Get-Content local.properties   # 应为 5 行：sdk.dir + 4 个 ENROUTE_*
```
## 判断构建结果只看这两行

`build-test.bat` / `compile-check.bat` / `build-release.bat` 都是「跑 gradle → 写日志 → 收尾」的包装。
**不要用 PowerShell 的 `$LASTEXITCODE` 判断成败**：如果上一条命令是管道（如 `| Select-String`），
`$LASTEXITCODE` 取到的是**管道末端命令**的退出码，gradle 失败也会显示 0 —— 我们已经被这个坑骗过一次。

正确做法：读日志末尾两行

```powershell
Get-Content build-compile-log.txt | Select-String 'BUILD SUCCESSFUL|BUILD FAILED|^EXIT='
# 同时看到 BUILD SUCCESSFUL 与 EXIT=0 才算过
```
## 界面提示：用设计，不用文字（2026-11-15，主人指令，长期有效）

**能被操作的东西，靠形态让用户看出来，不靠文字告诉他。**

这里说的"文字提示"指：`轻点展开`、`可点开`、`点击查看`、`支持滑动切换`、`按住可拖拽` 这类**说明书式文字**。
一律禁止。

### 该怎么做

用**设计手段**表达可操作性 —— 设计师的无声默契：

| 要表达的 | 用设计 | 不许用文字 |
|---|---|---|
| 这个能点开 | **折角 / 露边 / 内容被截断** / 层次投影 / 微弱持续动效 | 「轻点展开」「点击查看」 |
| 这里有更多 | 内容露出一条边被渐隐吞掉 | 「还有 3 条」式说明 |
| 可以滑 | 内容在边缘的连续性、位置指示 | 「左右滑动」 |
| 现在是展开的 | 折角消失/翻转、版面改为对开 | 「已展开」 |
| 这是可拖动的 | 抓手/投影/拖动时的即时跟手 | 「长按拖动排序」 |

**例外**：按钮上的**动作名称**不是提示（`看成绩`、`保存`、`删除` 是名字，不是说明书）。
被禁的是"解释怎么操作"的话，不是"这个按钮叫什么"。

### 自检标准

> **把界面里所有说明性文字删掉，用户还能不能看出这里能操作？**
> 能 → 合格。不能 → 说明设计没做到位，不是文字不够多。

### 变更说明写文档，不写界面（2026-11-16，主人指令）

**改了什么东西、为什么改、怎么用 —— 一律写进 `docs/` 或 commit message，不许出现在 UI 上。**

被禁止的典型：

- 页面里贴「本次更新：……」「新增了 XX 功能」
- 组件旁挂一行小字解释"这个功能是干什么的"
- 空态、引导页、首次进入提示里写功能说明
- 为了解释某个设计改动而在界面上加注（如"现在改为底部面板"）

**理由**：界面是给人用的，不是给人读说明的。用户不需要知道我们改了什么、为什么这么改 ——
那是开发记录该干的事。界面上的每一句解释，都是设计没做到位的补丁。

**唯一例外**：法律/隐私类必须知情同意的内容（如"会传输学号"），这类属于**义务告知**，
必须出现在决策点上，且要如实、完整。

### Release 说明只写一句话（2026-11-16，主人指令）

GitHub Release 的 description **一句话讲清这版是什么**，然后指向 CHANGELOG。

- 不写分节小标题、不列改动清单、不贴截图说明
- 详细内容一律放仓库 `CHANGELOG.md`（以及 `docs/_probe/` 里的实现清单）
- `latest.json` 的 `notes` 仍然是**一句话**（它在弹窗里显示，长了会撑爆版面）

示例：

```
修复更新弹窗里程表真机空白。详见 CHANGELOG.md。
```



## 前端设计纪律：全部作废（2026-11-15，主人指令）

**此前所有前端设计约定一律不再遵守**，包括：
- 「界面文案：按钮不超过 20 字」等文案纪律（已删）
- 形状 token 强制纪律（`ShapeTokenTest` 的全仓禁值、棘轮、8 档语义约束）
- `docs/设计-*.md`、`docs/评审-前端设计方案-v3.md`、`docs/评审-账页重构-v2*.md` 里的规范与红线

原因：主人判定按这些纪律做出来的前端仍不合格，**先拆除纪律，再重新设计、重新验证**。
纪律废除不等于代码要立刻回滚 —— token 结构可以留着当管道，但**不得再用测试或文档强制新设计遵守它**。

仍然有效的（非设计类，属数据与合规底线）：数据诚实、只读教务、只缓存自己的数据、
接口逆向留证据、robots 口径、构建纪律（并发构建/签名/日志判读）。
