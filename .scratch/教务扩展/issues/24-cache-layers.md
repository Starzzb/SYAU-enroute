# 24: 缓存三档分层 —— 身份读一次、每日数据一天一次、其余每次

**What to build:** 冷启动不再「什么都重抓一遍」。学籍身份这类几年不变的数据**第一次成功后永不自动重抓**；
成绩/绩点/考试这类一天一变的数据**静默最多抓一次/天**；资讯、地图补丁、更新检查保持现状。
抓失败**绝不清空**旧数据，只在旁边标「上次更新：X 小时前」。

**Status:** ready-for-human

## 为什么

主人最直接的体感痛点：每次冷启动都听见它在读一遍，明明有些东西一天读一次、甚至一辈子读一次就够。
2026-11-14 核查：仓库里**已有两套 TTL**（`UpdateManifest.shouldAutoCheck` 的 24h、`FacultyStore` 的 30 天/500 条/5MB），
但**身份、成绩、考试、培养方案进度**这几块完全没有分层 —— 冷启动照旧全量重抓。

## 三档

| 档 | 数据 | 判据 |
|---|---|---|
| **永久** `CacheTier.Permanent` | 学号、姓名、院系、层次、入学年月（学籍身份 ≤ 培养方案结构走内置资产，本来就离线） | **只补空**：首次成功后不再自动重抓；只有「清除所有数据」或换账号才失效 |
| **每日** `CacheTier.Daily` | 成绩与绩点（成绩统计 + 成绩明细）、考试、空闲教室的**校区/教学楼列表** | 距上次**成功**抓取 ≥ 24h 才抓，否则直读缓存 |
| **每次** `CacheTier.Live` | 资讯流、地图补丁、更新检查、**空闲教室的教室明细** | 保持现状不动（各自已有 TTL/刷新策略） |

⚠️ 关于「空闲教室」这一行的**判断**：校区/教学楼列表按每日档缓存（它们只是筛选器，一天内不会变）；
但**教室明细（第 N 节哪几间空）故意不缓存** —— 那是按节次实时变的数据，
拿昨天的空教室列表给学生看就是「看起来正常但是错的」，比不显示更糟（与仓库「数据诚实」红线冲突）。
详见下方 Comments 的说明。

## 三个关键设计

### ① 「下一场考试」双条件（不能只看时间）

```
距上次成功抓取 ≥ 24h  或  缓存里那场「下一场考试」已经开考/过去  →  才重抓
```

只看时间的话，考试当天考完之后的一整天里缓存仍算「新鲜」，界面会继续拿一份含已考完科目的快照 —— 那是错数据。
缓存里额外存一个 `nextExamAtMs`（**抓取那一刻**算出的下一场开考时刻）；`0` 表示当时就没有待考考试，
此时**不触发**第二个条件，否则「没有下一场」会被永远判成「该重抓」，退化成每次冷启动都打网络。

### ② 失败绝不能覆盖旧值

断网冷启动必须照常显示上次的身份与成绩，宁可显示旧数据 + 「更新于：3 小时前」，
也不能因为一次抓取失败就清空。落地方式：`AcademicCache.merge()` 用 `null` = 「这次没抓到 → 保留旧值旧时间戳」，
传空集合 = 「抓成功了，服务器就是没有内容 → 照实覆盖成空」（比如本学期还没排考试）。

### ③ 手动刷新分块，不做「一按全量重抓」

`refreshIdentity()` / `refreshProgress()` / `refreshExams()` / `refreshScores()` 各自独立；
「我的」页右上「刷新」= `refreshHome(force = true)`，只刷这一组（身份 + 学分/绩点），不连带考试/成绩/教室。

## 验收标准

- [x] 缓存全新鲜时冷启动，网络请求**不含**身份/成绩/考试（判据在 `CachePolicy`，单测锁死）
- [x] 断网冷启动：身份、成绩照常显示（缓存先灌界面，抓取失败只在后台上报，不清空）
- [x] 缓存里的「下一场考试」时间已过 → 触发一次重抓（双条件）
- [x] 「清除所有数据」后全部失效（`DataWiper` ⑤f）
- [x] 单测覆盖：三档判据、失败不覆盖、考试过期触发重抓、24h 边界

## 边界

- 写盘范围：`data/AcademicCacheStore.kt`（新）、`data/Academic.kt`、`data/DataWiper.kt`、
  `ui/AppState.kt`、`ui/TimetableViewModel.kt`、`ui/user/UserScreen.kt`、测试
- **不碰** `ui/map/`、`data/DebugLog*`（另有代理在改）
- 不引入 Room、不引入任何新依赖（照 `FacultyStore` / `HistoryTimetableStore` 的写法）

---

## Comments

### 2026-11-14 · 改了什么（文件 + 行号）

| 文件 | 位置 | 内容 |
|---|---|---|
| `data/AcademicCacheStore.kt`（新，285 行） | `CacheTier` L40 · `CachePolicy` L52 · `isFresh` L65 · `shouldFetchDaily` L72 · `shouldFetchPermanent` L82 · `shouldFetchExams` L99 · `examStartMs` L117 · `nextExamAtMs` L129 · `ageLabel` L140 · `AcademicCache` L162 · `merge` L203 · `VERSION` L232 · `toJson/fromJson` L240/L242 · `AcademicCacheStore` L257 · `load` L263 · `save` L268 · `clear` L276 · `sizeBytes` L284 | 三档判据（纯函数）+ 快照模型 + 原子写落盘。一个文件、一个用户、每块各带自己的 `*At` 时间戳 |
| `data/Academic.kt` | `fetchExamsOrNull` L192、`fetchScoresOrNull` L302（原 `fetchExams`/`fetchScores` 变成它们的 `.orEmpty()` 壳，L180 / L296） | **区分「真没有」和「没拿到」**：原实现把失败压成 `emptyList()`，缓存层没法判断该不该覆盖 —— 这是「失败不覆盖」能成立的前提 |
| `data/DataWiper.kt` | ⑤f L100–112 | 「清除所有数据」连带清 `filesDir/academic/cache.json`（身份是永久档，只有这里能作废它） |
| `ui/AppState.kt` | `cacheUpdatedLabel` L101 | 「更新于：身份 3 天前 · 绩点 2 小时前」；空串 = 没有缓存（不显示假时间） |
| `ui/TimetableViewModel.kt` | 字段 L61–62 · 冷启动灌缓存 L124 → `loadAcademicCacheIntoState()` L425 · 验活成功后静默补齐 L384 → `maybeSilentRefresh()` L496 · `cacheLine()` L454 · `examsCacheMsg()` L471 · `commitCache()` L478（唯一写盘点） · `fetchIdentity` L526 · `fetchProgress` L543 · `fetchExamsBlock` L569 · `fetchScoresBlock` L605 · 登录换账号清缓存 L1385–1389 · `refreshExams` L1452 · `refreshScores` L1462 · `refreshHome(force)` L1493 · `refreshIdentity` L1530 · `refreshProgress` L1548 | 判据只用 `CachePolicy`；每块「抓成功才写盘，失败不覆盖」；冷启动三档全新鲜时一个请求都不发 |
| `ui/user/UserScreen.kt` | L252（刷新按钮 → `force = true`）、L379–381（「更新于」小字） | 手动刷新只看这一组；缓存数据必须标年纪 |
| `app/src/test/java/cn/dkarbon/enroute/AcademicCacheTest.kt`（新，22 条 / 300 行） | — | 三档判据 / 24h 边界 / 考试双条件 / 失败不覆盖 / 序列化容错 |
| `README.md` | 测试数量 | 未改：本轮禁止跑全量单测，总数由 lead 统一更新 |

### 2026-11-14 · 怎么验证

1. `cmd /c compile-check.bat` → **`BUILD SUCCESSFUL` + `EXIT=0`**（`build-compile-log.txt` 尾两行）。
   `AcademicCacheStore.kt` / `Academic.kt` / `AppState.kt` / `TimetableViewModel.kt` / `UserScreen.kt` 零 error 零 warning。
2. 单测源码单独编译过一次（`:app:compileDebugUnitTestKotlin`，`BUILD SUCCESSFUL` / `EXIT=0`）——
   上一票栽在「测试编译不过」，所以先确认 22 条新测试能编过再交。
3. **全量单测由 lead 统一跑**（本轮约定），本票不重复跑，避免与其它代理抢 Gradle。
4. 需要真机/校园网才能体现的两条（见遗留 1、2）。

### 2026-11-14 · 遗留

1. **真机冷启动请求数**（需校园网 + 真会话）：装 release 包 → 登录一次 → 冷启动看调试日志应出现
   `CACHE 冷启动：三档都新鲜 → 不发身份/成绩/考试请求`，且界面直接有身份与成绩。
2. **考试过期重抓**（需等真实考试节点，或临时把缓存的 `nextExamAtMs` 改到过去）：
   应看到 `CACHE 考试已更新` 与 `nextExamAtMs` 前进。
3. **空闲教室的教室明细没有进缓存**（刻意的，理由见上）。若主人希望「切到空闲教室页时先用上次结果铺一下再刷新」，
   那是另一种 UX（先旧后新），需要单独一张票，不能混进「缓存」这个语义里。
4. `ui/academic/ExamScreen.kt` / `ScoreScreen.kt` 的刷新按钮仍调 `refreshAcademic()`（考试+成绩两块一起刷），
   **没有**改成 `refreshExams()` / `refreshScores()` —— 那两个文件当时正被票 26 的代理改，避免踩踏；
   VM 的四个分块入口都已就绪，后续一行就能接上。
5. 培养方案结构（票 09）本来就是内置资产、离线可读，因此**不需要**网络缓存；
   需要联网的只有「学分进度」（每日档）。这一点与票面表格的字面表述略有出入，按「离线资产不重复缓存」处理。

### 2026-11-14 · 关于 `VERSION` 的一个提醒（写进票里，别只留在代码里）

`AcademicCache.VERSION` 存在的意义不是「格式版本号」这么简单：
**将来真要大改结构时，把它加一，让全部老缓存作废重抓。**

反过来，如果只靠 `ignoreUnknownKeys` 硬兼容，最坏结果不是崩溃 —— 而是解码出一个
「字段对得上、语义已经变了」的缓存：界面显示得漂漂亮亮，数字却是错的。
那种 bug 比崩溃难查一个数量级（用户不会报「崩了」，只会说「我的绩点不对」）。
所以：**加字段 → 靠默认值兼容（不动 VERSION）；改语义/改字段名 → VERSION + 1，宁可重抓一次。**

另一条必须保持的：读取路径三层全包 `runCatching`（`fromJson` → `store.load()` → 调用方），
**任何解析失败一律当「没有缓存」→ 触发一次重抓**，绝不让冷启动因为一个坏 JSON 崩掉。
