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
## 前端设计纪律：全部作废（2026-11-15，主人指令）

**此前所有前端设计约定一律不再遵守**，包括：
- 「界面文案：按钮不超过 20 字」等文案纪律（已删）
- 形状 token 强制纪律（`ShapeTokenTest` 的全仓禁值、棘轮、8 档语义约束）
- `docs/设计-*.md`、`docs/评审-前端设计方案-v3.md`、`docs/评审-账页重构-v2*.md` 里的规范与红线

原因：主人判定按这些纪律做出来的前端仍不合格，**先拆除纪律，再重新设计、重新验证**。
纪律废除不等于代码要立刻回滚 —— token 结构可以留着当管道，但**不得再用测试或文档强制新设计遵守它**。

仍然有效的（非设计类，属数据与合规底线）：数据诚实、只读教务、只缓存自己的数据、
接口逆向留证据、robots 口径、构建纪律（并发构建/签名/日志判读）。
