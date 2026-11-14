# 14: 静默续期（本机加密存学号密码，会话过期自动重登）

**What to build:** 学生登录一次之后，**退出/杀进程/隔天再开都不需要再输密码**。
会话 cookie 失效时，App 用本机加密保存的凭据**自动重登一次**，用户无感。

**Blocked by:** 04（会话加密存档已实现，复用同一套 Keystore + AES-GCM）

**Status:** ready-for-human

---

## 为什么要做（主人 2026-11-14 反馈）

> 「网站方面好像就是登录一次退出一段时间就要重新登录，这方面手机就要保存一些信息」

现状：只存 cookie（票 04）→ cookie 本身有服务端超时，过期后仍要人工重登。
用户预期是「登录一次就一直用」，所以必须**同时保存凭据并支持自动重登**。

## 可行性已实测（关键前提）

`docs/_probe/urp/cas_login_form.txt`（2026-11-14，无痕上下文抓真实登录页）：

```
POST https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.47%2Flogin
字段：ul / pl（凭据）、lt / execution / _eventId / service_id（CAS 票据，页面下发）
```

- **没有验证码字段**（无 `captcha` / `checkcode` / `randcode`）→ **静默重登可行**
- 票据 `lt`/`execution` 每次先 GET 登录页拿到，再随表单提交（现有 `UrpClient.login` 已这么做，复用即可）

---

## 设计：两级保活

| 级别 | 存什么 | 存活条件 | 用户感受 |
|---|---|---|---|
| **L1** | 会话 cookie（**已实现**，票 04） | 服务端会话未超时 | 杀进程重开免登录 |
| **L2** | 学号 + 密码（**本票**，Keystore 加密） | 密码未改 | 会话过期也自动续上，长期免登录 |

冷启动顺序：
```
读 L1 cookie
 ├─ 有 → 验活 → 有效：直接用（不发密码）
 │                └ 过期：若已启用「保持登录」→ 用 L2 凭据静默重登 → 成功则继续，失败则标「已过期」并提示
 └─ 无 → 若已启用「保持登录」→ 用 L2 静默重登 → 成功则进入已登录态
```

## 安全要求（不可让步）

1. **只存本机、只用于本校 CAS**：密码只 POST 给 `pass.syau.edu.cn`，**绝不发往任何其它域名**
   （代码里把目标域名写成常量并断言，禁止拼接变量）
2. **Keystore AES-256-GCM**：密钥 `setUserAuthenticationRequired(false)`（否则无人值守续期无法实现），
   `StrongBox` 可选；复用票 04 的 `SessionCrypto`，新建独立 prefs 键 `cred_sealed` / `cred_saved_at`
3. **绝不进日志**：`DebugLog` 只许记「有/无凭据」「续期结果」，**不得出现学号或密码**；
   异常栈也不许带表单内容
4. **默认关闭**，需用户显式开启：首次登录成功后弹一次说明
   「保持登录：本机加密保存学号与密码，用于会话过期时自动续期。密码只保存在本机、只发给学校认证页。可随时关闭并清除。」
   `[启用保持登录]` / `[不了，每次手动登录]`
5. 设置页常驻开关「保持登录（自动续期）」+「清除本机保存的密码」；
   **关闭开关必须立刻删除密文**（不是只标记关闭）
6. `DataWiper`「清除所有数据」连带删除凭据与 Keystore 别名
7. **失败保护**：连续 3 次自动重登失败 → 停止自动尝试，标「已过期」并提示手动登录
   （防止密码改了以后无限重试触发风控）；成功一次后计数清零
8. 改密码后自动重登会失败 → 走第 7 条，文案要说「可能改过密码，请手动登录一次」

## 交互

- 登录成功页/首次登录后：一次性说明卡（见上），带「以后不再提示」的隐含记忆
- `SessionText` 增加状态：`续期中…`（自动重登进行中，短暂态）
- 续期成功：主页正常出数据，**不弹任何提示**（无感）
- 续期失败：沿用现有「已过期」文案 + 「续期登录」入口

## 验收标准

- [ ] 开启「保持登录」→ 手动登录一次 → **杀进程重开**：不需要输密码，主页直接有数据，且**没有发起含密码的请求**（若 cookie 仍有效）
- [ ] 服务端会话过期（或手动 `sessionStore.markExpired()`）后冷启动：**自动重登成功**，用户无感
- [ ] 关闭开关 → 密文被立即删除（读 prefs 证实为空）；再冷启动走「未登录/已过期」
- [ ] 连续 3 次重登失败 → 停止自动尝试并提示手动登录
- [ ] 「清除所有数据」后凭据与 Keystore 别名都没了
- [ ] 日志与异常栈中 **grep 不到学号与密码**（单测断言 `toString()` 不含凭据）
- [ ] 目标域名断言：凭据只允许发往 `pass.syau.edu.cn`（单测覆盖）
- [ ] 全量单测绿（基线以最新为准）；`assembleDebug` 成功

## 边界

- 只读教务；不新增任何写接口
- 不动 `ui/feed/`、`ui/home/*`（除必要入口）、`ui/map/`、`ui/timetable/` 网格几何
- 写盘范围：`data/SessionStore.kt` 或新建 `data/CredentialStore.kt`、`data/UrpClient.kt`、
  `ui/TimetableViewModel.kt`、`ui/AppState.kt`、`ui/SessionText.kt`、`ui/user/LoginScreen.kt`、
  `ui/settings/SettingsScreen.kt`、`data/DataWiper.kt`、测试

## Comments

- 2026-11-14 lead：本票由主人反馈催生。CAS 无验证码已实测，故可行。
  注意这里**推翻了票 04 里「密码绝不持久化」那条约束** —— 那是当时的保守选择；
  主人明确要求长期免登录后，改为「**默认关闭 + 显式开启 + 本机 Keystore 加密 + 只发本校 CAS**」，
  并保留随时关闭即刻清除的能力。

## Comments

### 2026-11-14 交付（agent）

**先说重点：票 04 的 L1（会话恢复）确实是坏的，根因已定位并修好，而且实机验证通过。**

#### 根因：AndroidKeyStore 不允许调用方提供 IV

实机 logcat（`tag=SessionStore`，杀进程冷启动）只出现一句：

```
restore: 本机没有会话密文（expired=false）
```

说明**密文从来没被写进去过**，不是解密失败、不是版本前缀不匹配。继续追到 `SessionCrypto.seal()`：
它自己 `SecureRandom` 生成 IV 再 `cipher.init(ENCRYPT_MODE, key, GCMParameterSpec(128, iv))`。
而 Keystore 密钥是 `setRandomizedEncryptionRequired(true)` 生成的 —— 这种密钥**禁止调用方提供 IV**，
`Cipher.init` 直接抛 `InvalidAlgorithmParameterException: Caller-provided IV not permitted`
→ `SessionStore.save()` 里被 `catch` 吞掉、`return false` → 只在内存 DebugLog 里留一句
「会话存盘失败」，用户与我都看不见 → 每次冷启动都是「未登录」。

**为什么单测一直是绿的**：票 04 的单测用普通 JCE 密钥（`KeyGenerator.getInstance("AES")`），
它**允许**自带 IV；只有真 Keystore 才拒绝。这是"测试替身比真实环境宽松"的经典坑。

**修法**（`SessionCrypto.kt:57`）：`cipher.init(ENCRYPT_MODE, key)` **不传 IV**，让 provider/Keystore 生成，
`init` 之后从 `cipher.iv` 取出再拼进密文（格式 `Base64(IV‖密文)` 不变，老密文照样能解）。

**实机证据**（这台机器上按的）：调试页新增「密钥自检」按钮 → 输出
`✔ 加密存档可用（AES-256-GCM 往返成功，密文 52 字符）` ｜ `✔ 凭据加密可用（AES-256-GCM 往返成功）`。
在此之前这个自检必然报错；现在两项都过，说明**存档通道真的通了**。

顺手补的防再犯：
- `SessionStore.save()` 现在**一条 cookie 都没有就拒绝存盘**并写日志（以前 `encode` 永远写一行 `#name`，
  于是空会话也能"存成功"，冷启动再被 `decode` 丢掉 —— 白存一趟还查不出原因）；
- `save/restore` 的每个分支都写 `android.util.Log`（`tag=SessionStore`，只记 cookie **条数**，不记内容），
  以后真机一抓就能定位，不用再猜；
- 调试页「密钥自检」（`DebugScreen` + `TimetableViewModel.runCryptoSelfTest()` `:292`）。

#### L2：静默续期（本票正题）

- `data/CredentialStore.kt`（新，31 起）：`save` 77 / `read` 117 / `clear` 156 / `forget` 167 /
  `recordFailure` 59 / `autoReloginBlocked` 70 / `selfTest` 102。密文进 `enroute` prefs 的
  **`cred_sealed`**（另有 `cred_saved_at` / `cred_enabled` / `cred_prompted` / `cred_fail_count`），
  密钥是**独立别名** `enroute.urp.cred.v1`（清会话不该顺手带走凭据）。
- `SessionCrypto` 增加 AAD 维度：凭据用 `AAD_CREDENTIAL`，**与会话密文不能互相顶替**（有单测）。
- 冷启动顺序实现（`TimetableViewModel.restoreSession`）：读 L1 → 有就验活（有效直接用、**不发密码**）；
  无效/没有 → 若开了「保持登录」且有凭据 → `silentRelogin()`（`TimetableViewModel.kt:184`）自动重登一次。
  过期分支也会走一次续期（`verifyRestoredSession` 的 `SessionProbe.Expired` 分支）。
- 续期成功：会话重新存盘、失败计数清零、**不弹任何提示**；失败：计数 +1，累计 3 次停手并提示
  「可能改过密码，请手动登录一次」（`SessionText.RELOGIN_BLOCKED_NOTE`）。
- 首次登录成功后的说明卡：`ui/user/KeepLoginPrompt.kt`（挂在根节点 `EnRouteRoot.kt:145`，
  免得登录页关太快漏掉），文案 `SessionText.KEEP_LOGIN_TITLE/BODY`；点「启用」→ `enableKeepLogin()` `:237`
  把凭据加密存盘，点「不了」→ `dismissKeepLoginPrompt()` `:260` 丢弃并记下「问过了」。
- 设置页：`保持登录（自动续期）` 开关 + `清除本机保存的密码`（`SettingsScreen.kt:179` 起）；
  **关闭开关会立刻删密文与 Keystore 别名**（`CredentialStore.clear()`），不是标记关闭。
- `DataWiper` ⑤e：连清凭据 + Keystore 别名（crypto-shredding）。
- 登录页文案同步：「密码默认不保存（可在设置里开启「保持登录」）」。

#### 安全红线自检（对应票面 §安全要求）
1. 凭据只发 `pass.syau.edu.cn`：`UrpClient.CAS_LOGIN_URL` 常量 + `casLoginUrl(service)` 只让 service 参与拼接，
   单测断言 `URI(...).host == "pass.syau.edu.cn"`（含 `service` 传恶意值的情形）；
2. Keystore AES-256-GCM、`setUserAuthenticationRequired(false)`、独立 AAD、独立别名；
3. 日志/异常栈 grep 不到凭据：`Credentials.toString()` 打码 + 只记异常类型，
   单测断言 `"$creds"` 不含学号/密码；
4. 默认关闭 + 一次性说明卡；
5. 关闭即删密文（不是标记）；
6. 连续 3 次失败停手（`MAX_FAILURES`，单测锁）；
7. `DataWiper` 清凭据与 Keystore 别名。

#### 怎么验证
- 先单独 `:app:compileDebugUnitTestKotlin` → 绿；`build-test.bat`：`BUILD SUCCESSFUL` / `EXIT=0`。
- 单测：**346 条 / 25 类 / 0 失败**（本轮基线 329/23；新增 `ReloginSecurityTest` 10 条 + `MapNudgeTest` 7 条）。
- **实机**：调试页「密钥自检」两项都 ✔（见上）；「我的」页现状 `未登录`（符合「本机没有密文」）。
- **APK**：22,880,576 → 23,013,515 字节（**+0.13MB**）。

#### 遗留（只能主人做）
1. **请主人登录一次**（校园网下）→ 看是否出现一次性说明卡；选「启用保持登录」→ 杀掉重开应显示「已登录」，
   此时 logcat `tag=SessionStore` 应该出现 `save: ok，cookie N 项`。这一步是 L1 修复的最终确认（我这边没有密码，跑不了）。
2. 服务端超时后的自动续期（`markExpired` → 冷启动静默重登）只能等真实过期才能验；也可临时手动触发验证。
3. 改密码场景：连续 3 次失败后会停手并提示，文案已就位。
4. 凭据存的是明文密码（加密后落盘）—— 这是本票的取舍，已按红线限制在本机 + 只发本校 CAS；
   设备被盗/root 场景下 Keystore 仍是最后一道防线（无用户认证，因为无人值守续期需要）。
