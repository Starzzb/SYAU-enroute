# 04: 教务登录态持久化，并区分「未登录」与「会话已过期」

**What to build:** 学生登录一次教务后，**冷启动 / 进程被杀 / 隔天再开**都不用重输账号密码；
会话真的过期时，界面说「会话已过期，点此续期」并一键重登，而不是让人以为从没登录过。

**Blocked by:** 01（崩溃会把会话带走，先止住才能准确评估过期路径）

**Status:** ready-for-human

## 现状

`TimetableViewModel.session` 是**普通内存字段**，进程一死就没。
`AppState` 里只有一个布尔 `loggedIn`，无法区分「从没登录」与「登过但过期」。
真机已两次因此被迫重新登录（一次是子代理重装包，一次是票 01 的崩溃）。

## 要做的事

- 会话 cookie 加密持久化：`EncryptedSharedPreferences`（Jetpack Security Crypto）或
  DataStore + Keystore 包装；**不要**明文写 SharedPreferences
- 启动时恢复会话，并**先用一个轻量只读接口验活**（例如空闲教室的校区列表，或学籍页），
  验活失败就标记「已过期」而不是静默清空
- 状态三态化：`未登录` / `已登录（会话有效）` / `已登录但已过期`
- 过期时的 UI 文案与入口：一键跳登录，并说明「密码只在校内网使用、不保存」
- 「清除所有数据」必须连带清掉持久化会话

## 安全与合规红线

- 密码**绝不持久化**，只存会话 cookie
- 日志（`DebugLog`）不得出现 cookie 值或姓名学号；现有代码已有隐去处理，改动后要保持
- 只缓存当前登录用户自己的数据

## 验收标准

- [ ] 杀进程后重开，主页/考试/成绩直接有数据，不需要重登（**需校园网**）
- [ ] 会话真过期（可手动让 cookie 失效或等服务端超时）时，显示「已过期」并可一键续期
- [x] 设置页「清除所有数据」后回到未登录态
- [x] 有单测覆盖三态判定（纯函数部分）
- [x] 全量单测通过，`assembleDebug` 成功
- [x] 日志里没有 cookie / 姓名 / 学号

## 边界

- 写盘范围：`data/UrpClient.kt`、`data/Prefs.kt`、`ui/TimetableViewModel.kt`、`ui/AppState.kt`、
  `ui/user/UserScreen.kt`、`ui/login/*`、测试

---

## Comments

### 2026-11-14 · 加密方案与选型理由

`app/build.gradle.kts` 里**没有** `androidx.security:security-crypto`，按票里的第二顺位处理：
**不新增依赖**，用 Android Keystore 自己包一层。

| 项 | 取值 / 理由 |
|---|---|
| 密钥 | `AndroidKeyStore` 里生成 `AES-256`，`PURPOSE_ENCRYPT\|DECRYPT` + `BLOCK_MODE_GCM` + `ENCRYPTION_PADDING_NONE`，别名 `enroute.urp.session.v1` |
| 加密 | `AES/GCM/NoPadding`，96 位随机 IV（每次新生成），128 位认证标签，AAD 固定串 `cn.dkarbon.enroute.session.v1` 把密文绑死用途 |
| 存放 | 密文 `Base64(IV‖密文)` 加 `v1:` 版本前缀，写进**现有** `enroute` SharedPreferences（`session_sealed` / `session_saved_at` / `session_expired`） |
| 为什么不加 `security-crypto` | 它内部做的也就是「Keystore AES key + EncryptedSharedPreferences」；这里要加密的只有**一个字符串**，自己包一层约 100 行，还能把加解密本身放进 JVM 单测（`SessionCrypto` 只依赖 `javax.crypto` + `java.util.Base64`）。少一个依赖=少一份升级负担，与本仓库一贯取舍一致 |
| `setUserAuthenticationRequired(false)` 的理由 | 会话恢复必须能在**无人值守的冷启动**完成；要求指纹/锁屏密码会把「打开就能用」堵死，那正好是这张票要解决的问题 |
| 存了什么 | cookie + 展示用姓名（同样加密，让冷启动后身份卡立刻有名字）。**密码、学号一个字节都没落盘** |
| 解不开怎么办 | 换机恢复备份/清数据/系统重置会让密文与 Keystore key 分离 → 解密返回 `null` → **当成没存过**（不崩、不猜、不拿半份 cookie 去登录），用户重登一次即可 |

### 2026-11-14 · 改了什么（文件 + 行号）

**新增（4 个纯逻辑 + 1 个存盘 + 1 个文案）**

| 文件 | 关键位置 | 作用 |
|---|---|---|
| `data/SessionCrypto.kt` | `SessionCrypto` L28 / `seal` L47 / `open` L63 | AES/GCM 加解密；认证失败一律返回 `null` |
| `data/SessionCodec.kt` | `SessionCodec` L19 / `encode` L40 / `decode` L50 | 明文记录编解码（`键<TAB>值`，只有 cookie + 姓名两行位）；坏行跳过，一条 cookie 都没有就 `null` |
| `data/SessionState.kt` | `SessionState` L10 / `SessionProbe` L29 / `SessionRules.stateOf` L51 / `canCall` L59 / `keepsExpiredMemory` L67 | 三态 + 验活结论（`Ok`/`Expired`/**`Unreachable`**）+ 判定规则（唯一来源） |
| `data/SessionStore.kt` | `class` L42 / `save` L62 / `restore` L83 / `markVerified` L121 / `markExpired` L131 / `clear` L137 / `forget` L149 / `openKey` L160 / `generateKey` L173 | Keystore 密钥 + Prefs 读写；`deleteEntry` 做 crypto-shred |
| `ui/SessionText.kt` | `EXPIRED_NOTE` L21 / `UNVERIFIED_NOTE` L26 / `stateLabel` L30 / `identitySubtitle` L43 / `fallbackName` L58 / `actionLabel` L65 / `noSessionHint` L72 / `settingsLine` L79 / `identityClickable` L86 | 三态文案集中一处，纯函数，单测锁定 |

**改动**

| 文件 | 位置 | 改动 |
|---|---|---|
| `data/Prefs.kt` | L98–123 | 新增 `sessionSealed` / `sessionSavedAt` / `sessionExpired` 三个键；`clearAll()` 顺带清掉会话密文（同文件） |
| `data/UrpClient.kt` | L44 `CAMPUS_LIST`；L75 `cookiesSnapshot()`；L98 `Session.fromStored()`；L201 `verifySession()`；L236 `judgeSession()`；L175 | 验活接口 = 空闲教室的**校区列表**（只读 GET、无参数、无 token、响应几百字节）；验活判定抽成纯函数；**顺手修掉日志里打姓名**（改成「已识别/未识别」） |
| `data/DataWiper.kt` | L43–49 | 「清除所有数据」多清一样：`SessionStore(app).forget()`（密文 + 过期标记 + **Keystore 密钥**）；末尾那行「登录会话本来就在内存里」的说法删掉 |
| `ui/AppState.kt` | L38 `sessionState`、L43 `sessionCheckMsg`、L113 `loggedIn` | 三态字段落地；`loggedIn` 变成**派生**属性（`sessionState == Active`），考试/成绩等旧调用点零改动 |
| `ui/TimetableViewModel.kt` | L84 调用、L118 `restoreSession()`、L160 `verifyRestoredSession()`、L272 `sessionStore.save()`、L355 `noSessionHint()`、L652 `wipeAllData()`、L667 `clearTimetable()` | 冷启动恢复 → 验活 → 落定三态；登录成功后加密存盘；清课表/清全量都连会话一起清；接口提示按三态分开说 |
| `ui/home/HomeCards.kt` | L95 `IdentityCard(...)`、L105 / L110 / L128 | 身份卡改吃三态：未登录=登录入口、有效=展示、**过期=可点+砖红「会话已过期·点此续期」**（`ui/home/RoomsScreen.kt`、`TeacherScreen.kt` 未动） |
| `ui/user/UserScreen.kt` | L192 身份卡、L203–215 过期常驻条 + 「续期登录」、L267 `StatRow("登录态")`、L277 教务组里的登录/续期入口 | 三态入口分开；`LaunchedEffect` 键从布尔换成 `sessionState` |
| `ui/user/LoginScreen.kt` | L47 签名加 `sessionState`、L64 / L80 | 同一个页面承担「首次登录」与「续期」；过期时标题改「续期登录」并说明**数据没丢、密码不保存** |
| `ui/settings/SettingsScreen.kt` | L160 / L185 / L328 | 数据块显示三态正名；「清除所有数据」说明加上会话存档与密钥；关于页补一句 cookie 加密存本机 |
| `README.md` | L14 / L30 | 功能表加「登录态持久化」行；单测数改成 228 条 / 17 个类 |

**测试（新增 3 个类 / 33 条）**

| 文件 | 条数 | 覆盖 |
|---|---|---|
| `app/src/test/java/cn/dkarbon/enroute/SessionStateTest.kt` | 18 | 三态判定全矩阵（含「连不上 ≠ 过期」「有 cookie 但服务器不认 = 过期」）、`canCall`、`keepsExpiredMemory`、`judgeSession`（JSON→Ok / 登录页 HTML→Expired / 空→Expired）、**三态文案互不相同且各带下一步动作** |
| `SessionCryptoTest.kt` | 7 | 真 AES/GCM 往返（含中文）、同一明文两次密文不同（IV 不复用）、改一字节解不开、换 key 解不开、垃圾输入返回 null、密文里不出现明文片段 |
| `SessionCodecTest.kt` | 8 | cookie/姓名往返、值里的 `=`/`;`/`%` 原样保留、无 cookie→null、坏行跳过、换行制表符被压平、空键丢弃、**结构里没有密码字段的位置** |

### 2026-11-14 · 怎么验证的

1. **编译 + 全量单测 + assembleDebug**：`cmd /c O:\dev\enroute-android\build-test.bat` → `BUILD SUCCESSFUL`，`EXIT=0`。
   JUnit XML 实数：**228 条 / 17 个类 / 0 失败 0 错误 0 跳过**（基线 195/14/0 → 本票 +33 条 / +3 类，红的一条都没有）。
   另外先单独跑过一遍 `:app:compileDebugUnitTestKotlin`（`BUILD SUCCESSFUL in 1m 20s`）——上一票就是测试代码编译不过栽的，这次先自查再全量。
2. **真机（vivo V2301A / 10AE1H1PYJ005ED，非校园网）**：装 debug 包，**未使用 `mobile_clear_app_data`**，课表 10 门课全程在位。
   - 未登录态：身份卡「未登录 · 点此登录教务 · 只在校内网可用」，「我的 → 本机数据」多一行 `登录态：未登录`，「教务」组首项是「登录教务」。
   - **过期态（用注入 prefs 的方式模拟 `markExpired()` 的落盘结果：`session_expired=true` + 无密文）**：身份卡变砖红
     「会话已过期 · 点此续期（密码只在校内网用一次，不保存）」，下方常驻「会话已过期 · 重新登录一次即可继续，课表还在　[续期登录]」，
     课表仍是 10 门课；点「续期登录」进的是「续期登录」页，带「上次的会话过期了…课表、成绩都还在本机」。
     **验证完已把 prefs 还原成注入前的原样（311 字节逐字节一致）并重启确认回到「未登录」。**
   - 设置页：数据块「未登录（教务接口要先登录，只在校内网可用）」；「清除所有数据」说明已含「登录会话（含加密存档与密钥）」。
   - ⚠️ 「清除所有数据」**没有在真机上按下去**（它会连带清掉主人的 10 门课表与资讯缓存，红线不允许）。
     这条走的是代码路径验证：`DataWiper.wipeAll` 里的 `SessionStore(app).forget()` + Prefs 同文件 `clearAll()` +
     `wipeAllData()` 直接重建 `AppState()`（`sessionState` 默认 `None`）。要真机确认请主人在自己方便时点一次。
3. **日志红线**：改动后 `DebugLog` 里与身份相关的只有「cookie N 项（内容不记）」「姓名未识别/已识别」「验活：N 字节 → 结论（首字符=x）」，无 cookie 值、无姓名、无学号。

### 2026-11-14 · 遗留（要主人在校园网下验的）

1. **真登录往返（唯一没验的一条）**：需要校园网。步骤 —— 登录一次 → 杀进程/重启 App → 期望
   「我的」身份卡直接有姓名、`登录态：已登录（会话有效）`、课表/考试/成绩不用重登就出数据。
   这一步同时验证 Keystore 建 key + 加密存盘 + 解密恢复 + 校区列表验活四条路。
2. **真过期路径**：等服务端会话超时（或清掉服务端会话）后冷启动，期望出现上面那张「会话已过期」的卡而不是「未登录」。
   本次只用注入 prefs 验证了**判定之后**的界面，`verifySession()` 在真机上返回 `Expired` 这一步没实测过。
3. **不在校园网时的诚实降级**：冷启动应显示「已恢复上次的登录；这次没连上教务…没法确认会话是否仍有效」，
   而不是报过期。这条依赖真会话，本次也没有真会话可测。
4. **验活接口的判定偏严格**：`judgeSession` 只认 `[`/`{` 开头。若哪天 URP 改用「JSON 里带错误码」的方式表达未登录
   （例如 `{"msg":"未登录"}`），会被误判成 `Ok`。风险已知、成本低（学生重登一次），等真机抓到这种样本再收紧。
5. **`android:allowBackup="true"`**：会话密文会随自动备份走，而 Keystore key 不迁移 → 换机恢复后解密失败、
   自动回到未登录（已实现且不会崩）。若想彻底不备份这个键，可后续加 `dataExtractionRules` 把 `session_sealed` 排除。
6. **`setUserAuthenticationRequired(false)`**：这是为「冷启动免交互」做的取舍，意味着拿到已解锁设备的人可以解出会话 cookie
   （和「App 自己能用」是同一把钥匙）。票里没要求生物识别门控，如果需要，改成 `true` + 每次启动解锁一次即可，但会牺牲免交互恢复。
7. 会话恢复成功后**没有**主动预抓考试/成绩（省流量）：课表来自本地文件，「我的」页进入时按 `sessionState == Active` 自动 `refreshHome`，
   考试/成绩页进入时各自 `refreshAcademic`。若主人希望冷启动就把三样都拉齐，加一次静默预热即可。

### 2026-11-14 lead 独立复验：通过

不是采信自述，逐项核过：

- `build-test.bat` → `BUILD SUCCESSFUL / EXIT=0`；单测 **228 条 / 17 类 / 0 失败**（与自述一致）
- **密码审计**：`grep password|passwd|pass\b|user\b` 于 `SessionStore.kt` **零命中** —— 只存 cookie 与展示姓名
- **加密参数**：`AES/GCM/NoPadding`、96 位随机 IV（每次现生成）、128 位 tag、AAD 绑用途、
  密文 `Base64(IV‖密文)` + `v1:` 版本前缀；解密认证失败返回 null（不吐半截明文）
- **三态可读**：`SessionRules.stateOf()` 一处判定；`SessionText.kt:27` 把「这次没连上教务…还没法确认」
  与「会话已过期」写成两句不同的话 —— 「连不上 ≠ 过期」这条守住了
- **禁区未被碰**：`RoomsScreen.kt` / `TeacherScreen.kt` / `FeedScreen.kt` / `TimetableScreen.kt` 时间戳
  仍是前几轮的（19:10 / 19:40 / 22:49 / 22:59）

留作观察（不阻塞）：
- `judgeSession` 只认 `[`/`{` 开头，若 URP 改用在 JSON 里塞错误码表达未登录，会误判成「正常」（代价=重登一次）
- `allowBackup="true"` 且无备份规则：密文会随备份走、Keystore key 不迁移 → 换机恢复后解密失败、
  按「没存过」降级（无安全风险，只是白存一份）。想彻底干净可加 `fullBackupContent/dataExtractionRules` 排除该 prefs 文件。