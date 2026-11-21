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

## 编译与验证永远放最后一步（2026-11-21，主人指令，长期有效）

**一个需求一批改完再编译。改一处编译一次是浪费，也是错的。**

原话：「编译在所有需求和任务完成之后才进行，否则就提问，验证总是留在最后一步的。」

### 怎么做

- 主人一次性给出多条改动 → **全部改完**，一次性 `compile-check.bat`；中间不编译、不装机、不截图
- 编译、单测、打 release、装机、截图 —— 这些都是**验证**，只在需求全部落地之后跑一次
- 中途确实需要确认某件事（某个 Compose API 存不存在、某个字段叫什么、某个符号是死是活），
  **先问主人，或者先读代码确认**；不靠「编一次看看报什么错」来探路
- 唯一例外：定位一个**已经出现**的编译错误时，允许为了复现/确认修复而编译，但复现完就把剩下的活干完再验

### 自检标准

> 这一轮我编译了几次？
> 如果需求还没改完就编译过，那次的编译结果不该被当成进度 —— 它是浪费。

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
- **点安装弹窗前先认包名，不许只按文字匹配**（2026-11-17 血的教训）：
  脚本用 `text="安装"` 找按钮时，前台可能已经不是安装器，而是华为应用市场的
  「安装成功」页——那页的「值得一试」推荐位也全是「安装」按钮，于是连点五个，
  把快手极速版 / 智联招聘 / 扫描全能王 / 国泰君安期货装到了人家手机上。
  正确姿势：每次 `uiautomator dump` 后先取 `package="…"`，
  **只在 `com.android.packageinstaller`（`PackageInstallerActivity`）/ `com.huawei.appmarket` 的
  安装确认浮层里、且待装包名与本轮 push 的一致时**才 tap；
  认不出包名就停下来等人看，宁可失败也不要盲点。

## adb 装机要人工点两段确认（2026-11-21，主人指令）

`adb install -r` **不会自己过去**。release 包与已装版本的签名不一致时（换过证书、之前装的是 debug 包或别处的包），
系统必定弹风险提示，脚本只能停下来等人点。

1. `uiautomator dump` 后**先取 `package="…"`**，确认是 `com.android.packageinstaller`，且标题里的应用名与本轮待装包一致
2. 点「继续安装」（1080×2400 上约 `768,2253`）
3. 出现第二屏时再点一次「继续安装」（约 `783,2262`）；没有第二屏说明第一段已放行
4. 装完前台若是 `com.huawei.appmarket` 的「安装成功」页，**只点「完成」**（约 `540,2262`）——
   那页所有「安装」都是「值得一试」推荐位（见上一节）
5. 判据是 `adb shell dumpsys package cn.dkarbon.enroute | Select-String lastUpdateTime` 的时间戳变了。
   **`versionName` 不能用来判断**：更新后它常常还显示旧版本号（例如 `0.8.1+m33`）
6. 没等到两段确认就记「装包未完成」，不许连点、不许改用 `text="安装"` 全文匹配、不许为了装上而卸载

> 人不在电脑前就别起安装：脚本会卡在确认页上等，白占着 adb。

## local.properties 写法

必须**逐行**写入，每行以换行结尾。曾经用字符串拼接导致 `sdk.dir=` 与 `ENROUTE_*` 粘成一行、
密码键出现两份（一份错），构建时好时坏。写入后自检：

```powershell
Get-Content local.properties   # 应为 5 行：sdk.dir + 4 个 ENROUTE_*
```
## 版本号约定：第二位 +0.1，不经允许不上 1.0（2026-11-21，主人指令）

- 每版只把版本号的**第二位**加 0.1：`0.9.x` → `0.10.0` → `0.11.0`……`versionCode` 同时 +1。
- **没有主人明说，不许跨到 1.x。** 1.0.0 不是"功能齐了"的自动结论，是主人点头才有的编号。
- 第三位留给补丁（`0.10.1`）；`+mNN` 是本轮迭代标识，与语义版本无关，不参与排序。
- 改版本号时三处一起改：`app/build.gradle.kts` 的 `versionCode` / `versionName`、`CHANGELOG.md` 新条目、
  `releases/` 里的归档文件名（`SYAU-enroute-mNN.apk`）。

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

### 发版必须先问；Release 正文 ≤200 字（2026-11-21，主人指令，两次修正后的定稿）

**每次发版本都要先问主人。不许自己改版本号、打 tag、发 Release。**

原话（2026-11-21 下午，收掉同日上午的放开）：
「发版的描述放开之后又写这么多，不应该写这么多，只需要写解决了什么问题就行，
这是个普通用户看的，压缩到200字以内这种，修正约定。」

三条：

1. **发版前先问** —— 改 `versionCode`/`versionName`、打 tag、`gh release create`、改 `releases/latest.json`，
   每一步都要主人点头。日常 commit 与 push 照旧（主人明说「直接提交」的那次除外）
2. **GitHub Release 正文 ≤ 200 字，只回答「这版解决了什么问题」** —— 它是给普通用户看的，不是开发日志：
   - 不列改动清单、不写技术推理、不写「为什么这么改」
   - 不出现类名 / 文件名 / 函数名 / 行号 / 十六进制色值 / 内部代号
     （`LedgerCourseCell`、`m34`、`#272219`、`FeedImages` 这类一律不许出现）
   - 最多 4–5 个短句，能合并就合并
   - 自检：念给一个没用过这个 App 的同学听，他能不能听懂「这版修了什么」
3. **App 内公告（`releases/latest.json` 的 `notes`）由主人确认后统一发布** —— 它显示在更新弹窗里，
   屏幕就那么宽。草稿先给主人看，措辞定稿后才写进 `latest.json`，写完再发 Release。不许先发后补

> 收紧过程：2026-11-16「只写一句话」→ 2026-11-21 上午「可以写实、写多长都行」（m39 因此写了两千多字，
> 被主人点名）→ 2026-11-21 下午 **回到 200 字以内、只讲解决了什么**，即当前口径。
> `CHANGELOG.md` 与 `docs/` 承载全量细节；Release 页面只负责让用户知道「这版对我有什么好处」。

### 发版前检查清单（2026-11-21，主人指令）

**发版不是「改号 + 打包 + push」，是把仓库里所有说「现在是什么样」的地方对齐一次。**
每一条都要亲眼看过；没看过就当没过。

1. **版本号** —— `app/build.gradle.kts` 的 `versionCode` / `versionName` 按
   `docs/版本控制与发版流程.md` §2 的锚点算（`400 + (NN-32)×10`），两个字段同一次改完
2. **CHANGELOG.md** —— 这一版改了什么按小节写全（主题 / 组件 / 数据 / 清理分开），别攒到下一版
3. **README.md（最容易烂的一处）** —— 逐行过：版本号与 versionCode、依赖清单、代码树里的目录
   是否真实存在、内置资产的门数份数与 `app/src/main/assets/curriculum/packed_meta.json` 是否一致、
   包体与瘦身数字是否与最新一次 `build-release-log.txt` 一致
4. **docs/** —— 这一版动过的设计写进对应 `docs/设计-*.md`（参数、落点、踩过的坑）。
   `docs/_probe/`、`docs/评审-*`、`docs/调研-*` 是历史记录，不追改，但也别拿它们当现状依据
5. **`releases/latest.json`** —— `versionCode` / `versionName` / `url` / `sha256` / `size` / `notes`
   六项齐全；`sha256` 用 `certutil -hashfile app\build\outputs\apk\release\app-release.apk SHA256`
   现算，`size` 用文件字节数；`notes` 草稿先给主人确认
6. **产物一致** —— `releases/SYAU-enroute-mNN.apk` 与 tag 上挂的资产必须是同一个文件（比 sha256）
7. **单测** —— `build-test.bat` 日志末尾同时有 `BUILD SUCCESSFUL` 与 `EXIT=0`
8. **真机** —— 这一版的主路径亲手点过；没点过的写进交付说明，不许默认「应该没问题」

> 自检：把 README 交给一个没参与开发的人，他照着能不能说清这版是什么、里面有什么。
> 答不上来就是没检查完。




## 前端设计纪律：全部作废（2026-11-15，主人指令）

**此前所有前端设计约定一律不再遵守**，包括：
- 「界面文案：按钮不超过 20 字」等文案纪律（已删）
- 形状 token 强制纪律（`ShapeTokenTest` 的全仓禁值、棘轮、8 档语义约束）
- `docs/设计-*.md`、`docs/评审-前端设计方案-v3.md`、`docs/评审-账页重构-v2*.md` 里的规范与红线

原因：主人判定按这些纪律做出来的前端仍不合格，**先拆除纪律，再重新设计、重新验证**。
纪律废除不等于代码要立刻回滚 —— token 结构可以留着当管道，但**不得再用测试或文档强制新设计遵守它**。

仍然有效的（非设计类，属数据与合规底线）：数据诚实、只读教务、只缓存自己的数据、
接口逆向留证据、robots 口径、构建纪律（并发构建/签名/日志判读）。
