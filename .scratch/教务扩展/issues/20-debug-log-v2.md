# 20: 调试日志 v2（D1 耗时 / D2 落盘+脱敏 / D3 分级）

**What to build:** 按 `docs/计划-调试日志v2.md` 实现三件事，**顺序不可调换**：
D1 耗时字段 → D2 落盘（含脱敏）→ D3 分级与错误保底。

**Blocked by:** 无

**Status:** ready-for-human

---

## 为什么这个顺序（主人已定）

> 「**要动，而且 D1 应该先于抓取优化。** 那份计划的第一步就是"量化基线"，
> 而基线的测量能力依赖 D1。顺序反了会卡住。」

配套计划 `docs/计划-抓取并行与条件请求.md` 的第 0 步已改为指向 D1，避免两处各写一遍。

## 三件事的要点（细节全在计划文档里，照它做）

### D1 耗时字段（最高优先）
`DebugLog.http()` 签名里没有时间参数，**全仓零耗时记录**。加 `elapsedMs`，
格式 `← 200 12.3KB 820ms GET ...`。这是抓取优化打基线的硬前置。

### D2 落盘（本机保存）
`filesDir/debug/YYYY-MM-DD.log`，按天分文件、保留 7 天、封顶 2MB、默认开启可关。

两个取舍必须照做：
- **不要指望 `onTerminate`**（Android 常不调用）→ 靠「每 500ms 或攒 20 行 flush」保证崩溃不丢
- **落盘后必须脱敏**：内存里丢了就丢了，落盘意味着文件可能被导出。
  `JSESSIONID` / `CASTGC` / `ticket=` / `token=` 这些**现在会原样进日志**
  （`Academic.kt:28-34` 的随机 token 会出现在 URL 里），必须挡。

### D3 分级与错误保底
现在 400 条缓冲被 FEED 的批量 HTTP 冲掉，登录失败这类关键事件反而看不见。
加 `level` + **单独留最近 50 条 ERROR**。**过滤放读取侧，不能在写入侧丢数据。**

## 现有能力（别重造轮子）
导出到本机 / 复制 / 清空**都已经有了**（`DebugScreen.kt:75-86` 走 SAF，不需要存储权限）。
不要重复实现。

## 验收
- [ ] `DebugLog` 每条 HTTP 都有耗时；debug 页能看出哪一步慢
- [ ] 日志按天落盘，重启 App 后历史还在；7 天前的自动清；单文件超 2MB 轮转
- [ ] **落盘文件里搜不到** `JSESSIONID`/`CASTGC`/`ticket=`/`token=` 的明文（给一条命令级证据）
- [ ] 关键事件（登录失败、培养方案读取失败）不被 FEED 批量日志冲掉
- [ ] 关闭落盘开关后不再写文件，且已写的能清掉
- [ ] 单测覆盖：耗时格式化、轮转与保留、脱敏（含 URL 里带 token 的用例）、ERROR 保底 FIFO
- [ ] 不依赖 Android 文件系统的地方用临时目录抽象，保持 JVM 可测

## 红线
- **写入侧不丢数据**（过滤只在读取侧）
- **脱敏不是可选项** —— 它是落盘带来的新风险
- 不动 `ui/timetable/`、`data/map/`、`ui/feed/` 的数据管线
- 基线 **406 条 / 31 类 / 0 失败**
- 只跑 `compile-check.bat`，**不要自己构建装机**；交「待真机验证清单」给我

## Comments

- 2026-11-14 lead：主人提供的计划文档质量足够，本票只是把它接到工单板上。
  D1 先做，D2 的脱敏与 D3 的分级可并行，但都不许压缩。

## Comments

### 2026-11-14 交付（agent）—— D1 + D2 + D3 全部完成

`compile-check.bat`：**BUILD SUCCESSFUL / EXIT=0**；全量单测 **437 条 / 35 类 / 0 失败**（基线 406 未弄红）。

#### D1 耗时（规格 §1）
- `DebugLog.http()` 增 `elapsedMs: Long = -1`（<0 不输出），输出
  `← 200 12.3KB 820ms GET jwc.syau.edu.cn/index/jwtz.htm`（`DebugLog.kt` 的 `http()` / `levelOf()`）
- `DebugLog.timed(tag, label) { }` 便捷包装，用 `System.nanoTime()`
- 计时点：`UrpClient.request()`（全部教务请求的唯一咽喉，含跳转循环的总耗时）、`FeedHttp.get()`（自计时并回填 `Resp.elapsedMs`）
- `FeedRepository`：每源 `教务通知：820ms · 1 页 · 15 条`；结束
  `刷新总计 xxxxms · N 源（成功 a / 失败 b）· 新增 n 条`；`SyncReport.elapsedMs` 已加
- 真机数字待 lead 装机后填进 `docs/调试日志-实测记录.md` §1

#### D2 落盘（规格 §2）
- 新文件 `data/DebugLogFile.kt`（**根目录为构造参数** `DebugLogFile(root)`，单测用临时目录）
  - `filesDir/debug/YYYY-MM-DD.log`，**追加写**不重写
  - **每 20 行或每 500ms flush**（不依赖 `onTerminate`）
  - `MAX_DAYS = 7` / `MAX_BYTES = 2MB`，启动时 `rotate()` 删最旧
  - `usageBytes()` / `read()` / `clear()` / `logFiles()`
- `DebugLog.attachFile(sink)`；`Prefs.debugLogToFile`（默认开，release 同样生效，未用 `BuildConfig.DEBUG`）
- ViewModel 启动时 `rotate()` + `attachFile`（全程 `runCatching`）；`setDebugLogToFile()` 关掉即摘通道，**连文件都不再创建**
- 设置页：开关 + 「已占用 xxKB · 保留 7 天 · 上限 2MB」+ 「清空」（不动内存）
- 调试页：顶部「· 落盘 xxKB」+ 「清空历史」
- **脱敏**（红线）：`DebugLogFile.redact()` 命中 Cookie/Session、CAS 票据（lt/ticket/execution）、
  密码字段、通用 token、`Authorization:`/`Cookie:`/`Set-Cookie:` 头部，值一律 `***`；
  头部形态整段替换（未知 cookie 名也挡得住）

#### D3 分级（规格 §3）
- `Level = DEBUG/INFO/WARN/ERROR`；`log()→INFO`、`step()→DEBUG`、`error()→ERROR`、
  `http()` 按状态码（2xx INFO / 4xx·5xx WARN / -1 ERROR）
- **ERROR 保底**：主环形 400 之外另存最近 **50 条 ERROR**（独立 `StateFlow`）
- 调试页 chips：全部 / 警告+ / 仅错误（读 ERROR 缓冲）；**过滤只在读取侧**，写入不丢数据

#### 单测（规格 §5，要求 ≥6 条，实际 12 条）
`DebugLogFileTest`（11）：脱敏（8 组凭证全 `***`、保留键名、落盘文件搜不到真值）、
行格式与 `formatLine` 一致、轮转只留 7 天且删最旧、3MB→≤2MB、清空历史、
级别映射、**3 ERROR + 500 DEBUG 后「仅错误」仍在**、ERROR 只留最近 50 且有序、耗时字段落行。
`DebugLogEvidenceTest`（1）：生成 `app/build/debuglog-evidence/2026-11-14.log` 供命令级校验。

#### 命令级证据（lead 要求的「搜不到明文」）
```
$ Select-String -Path app\build\debuglog-evidence\*.log `
    -Pattern 'JSESSIONID=[^*.\s]|CASTGC=[^*.\s]|ticket=[^*.\s]|token=[^*.\s]|password=[^*.\s]'
NO PLAINTEXT CREDENTIALS FOUND (0 matches)
```
落盘文件实际内容（6 行，敏感值全为 `***`）见 `docs/调试日志-实测记录.md` §3。

#### 未做（本轮范围外，如实记录）
- 「全部导出 7 天日志」按钮：`vm.exportAllDebug()` 已就绪（返回拼好的文本），
  但设置页还没接 SAF 启动器 —— 现有「导出文档（当日内存）」够用，等 lead 决定要不要加。

**Status:** ready-for-human（待统一装机验证）

### 2026-11-14 追加：「全部导出 7 天日志」已接上（lead 要求）

**为什么该做**：原来的「导出文档」导的是**当日内存**缓冲，而落盘的价值恰恰在**跨天** ——
用户昨天出问题、今天来反馈，得能拿到昨天的日志。

- `DebugScreen.kt`：第二个 SAF 启动器（照 `:75-86` 同一套 `CreateDocument` 写法，不新造存储路径、
  不需要存储权限）+ 工具条「导出全部」按钮 + 成功/失败 `Toast` 人话提示
- 文件名带**日期范围**：`在途日志_2026-11-08_2026-11-14.txt`
  （纯函数 `DebugLogFile.exportFileName(days, today)`；没有落盘文件时退化成今天单日，**不编范围**）
- **导出前再脱敏一遍**（`vm.exportAllDebugText()` 对每一行再过 `DebugLogFile.redact`）：
  导出是日志**第二次离开设备**的机会（SAF 到 SD 卡 / 被别的 App 读 / 被同步盘上传都在这一步之后），
  不假设「落盘时脱过就安全」。`redact` 幂等，重复调用无副作用。
- 导出头三行写清楚：只存本机、不上传、导出前已再次脱敏。

**新增单测 2 条**（`DebugLogFileTest`）：
`导出文件名带日期范围`（乱序/单日/无文件退化/空串忽略）、`二次脱敏是幂等的`。

**当前总量：440 条 / 35 类 / 0 失败**；`compile-check.bat` BUILD SUCCESSFUL / EXIT=0。

### 2026-11-14 真机复测后修正（三条，第 ④ 条按 lead 复议作废）

**① 🔴 脱敏只在落盘层 → 已提到内存唯一入口**

真机证据：调试页上明文出现 `ticket=ST-277817-0WFt4Yi1VkhaY3Dd53vC-tpass`。
原因：`DebugLogFile.append()` 才脱敏，而 `_entries`（屏幕/复制/「导出文档」的数据源）从头到尾没脱敏。

改法：脱敏挪进 `DebugLog.write()`（**进内存之前**，唯一写入口），四条下游一次覆盖：
屏幕 / 复制 / 导出文档 / 落盘。落盘层 `redact` 保留（幂等，纵深防御）。

代价：调试时看不到票据原文 —— 认了，安全优先；**键名仍保留**（知道有票、拿不到票）。

**② 🟡 导出文件名同日重复** → 单日退化成 `在途日志_2026-11-14.txt`；
多日才 `_起_止`（`DebugLogFile.exportFileName:144`）。

**③ 🟡「落盘 xxKB」空白** → 改成直接问落盘通道 `usageBytes()`、按日志条数变化重算；
未启用时明确写「落盘未启用」。**留空和 0 一样是信息缺失**。

**④ 更新回落 http 地址：保留（lead 复议，我原改动作废）**
`47.95.12.198` 已由 `network_security_config.xml` 放行明文（地图上报必须走它），回落可用。
安全性论证不变：`PINNED_RELEASE_PREFIX` 把 APK 入口钉死在本仓库 `releases/download/` 下，
攻击者无本仓库写权限 → 提供不出可安装的包，**最坏只是下载失败**。
`Cleartext HTTP traffic to 47.95.12.198 not permitted` 那条 ERROR **留作探针**（重建后还出现＝config 没生效）。

#### 新的脱敏证据（覆盖屏幕 + 导出文档 + 落盘三条路径）
`DebugLogEvidenceTest` 把三条路径的产物都写到 `app/build/debuglog-evidence/`
（`screen.txt` / `dump.txt` / `2026-11-14.log`，不自动删）：

```
18:06:36.979  [URP] ← 302 0B  GET 10.2.0.47/login?errorCode=invalidSession&ticket=***
18:06:36.987  [SESSION] Set-Cookie: JSESSIONID=***; Path=***; HttpOnly
18:06:36.987  [SESSION] Set-Cookie: CASTGC=***; Path=***
18:06:36.989  [CRED] POST body password=***&execution=***
18:06:36.989  [API] GET api/score?token=***&access_token=***
18:06:36.989  [FEED] ← 200 12.3KB  820ms  GET jwc.syau.edu.cn/index/jwtz.htm?ticket=***
```

```powershell
Select-String -Path app\build\debuglog-evidence\* `
  -Pattern 'JSESSIONID=[^*.\s]|CASTGC=[^*.\s]|ticket=[^*.\s]|token=[^*.\s]|password=[^*.\s]|ST-EVIDENCE'
# → NO PLAINTEXT CREDENTIALS IN screen.txt / dump.txt / *.log (0 matches)
```

顺带把 cookie 头从「整段 `***`」改成**抹值留名**（`Set-Cookie: JSESSIONID=***; Path=***`）：
排查时「有没有这个 cookie」有用，且未知新 cookie 名也能挡住（逐对抹，不是白名单）。

**单测**：`DebugLogFileTest` 14 条（新增 `内存与导出文档里也搜不到凭证明文`）+ `DebugLogEvidenceTest` 1 条。
**当前：454 条 / 36 类 / 0 失败**（含另一个代理新增的类）；`compile-check.bat` BUILD SUCCESSFUL / EXIT=0。
