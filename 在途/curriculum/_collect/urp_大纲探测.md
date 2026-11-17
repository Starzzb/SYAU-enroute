# URP「课程教学大纲」入口探测（未登录结构探测）

- 探测时间：2026-11-17 15:41–15:50（Asia/Hong_Kong）
- 探测主机：Windows，校内网直连（10.2.0.47:80 / 10.2.0.48:80 均可达）
- 探测方式：`System.Net.Http.HttpClient`，`AllowAutoRedirect=false`、`UseCookies=false`、UA 为固定探针串
- 请求总数：**91 次，全部 GET**。无 POST、无表单提交、无登录尝试、无爆破
- 原始数据：`_probe_raw.json`、`_probe_raw2.json`、`_probe_raw3.json`、`_probe_raw4.json`、`_probe_raw5.json`（同目录）
- 观测口径：只看「未登录时服务端第一跳返回什么」，不跟随后续跳转

---

## 0. 一句话结论

**没有匿名可读的课程大纲页。** 91 次 GET 里只有 6 个 `200`：其中 5 个是
`/ajax/student/getSectionAndTime`（作息节次表，308B JSON，**不含任何课程内容**）与 1 个
`/favicon.ico`（807B 图标）；其余所有教务路由一律 `302 → /gotoLogin → /login → CAS`。
所以抓 2025 版大纲**必须同时具备三样东西：有效 CAS 会话 cookie、校内网可达、正确的 `service` 回跳参数**。

---

## 1. 本次探测附带查明的一个坑（比结论本身更重要）

**「302」在 `/student/**`、`/teacher/**` 下不是"页面存在"的证据。**

URP 的鉴权过滤器在**路由分发之前**就拦掉了这些前缀，所以：

| 请求 | 状态 | 说明 |
|---|---|---|
| `GET /zzzNotExist` | **404**（1642B「页面404错误」） | 不存在的路径确实会 404 |
| `GET /main/index` | **404** | 同上，`/main/*` 没有通配过滤器 |
| `GET /main/queryYcbxkc` | **302 → /gotoLogin** | 已注册且受保护的路由才会 302 |
| `GET /student/zzzNotExist/index` | **302 → /gotoLogin** | ⚠️ 路径根本不存在，照样 302 |
| `GET /teacher/zzzNotExist/index` | **302 → /gotoLogin** | ⚠️ 同上 |
| `GET /student/zzzNotExist/index.css` | **302 → /gotoLogin** | ⚠️ 连静态后缀都拦 |
| `GET /studentX/zzz/index` | **302 → /gotoLogin** | 过滤条件比 `/student/` 前缀更宽（疑似按字符串包含匹配） |
| `GET /Student/zzzNotExist/index` | **404** | 大小写敏感，`Student` 不在过滤范围内 |

**推论**：本文表格里所有"猜测路由 302"的记录，只能证明「它落在了鉴权过滤器的覆盖范围内」，
**不能证明该页面真实存在**。要确认某个大纲路由是否存在，只能登录后看菜单 / 看 404 与正常页的差别。

对照组：`/favicon.ico` 200（807B JPEG 图标）、`/upload/`、`/files/`、`/attachment/` 均 404
→ 没有任何可匿名遍历的附件目录。

---

## 2. 路由清单表

### 2.1 入口与跳转链（关键证据）

| URL | 状态码 | 跳转目标 | 含「大纲」 |
|---|---|---|---|
| `http://10.2.0.47/` | 302 | `/gotoLogin` | 否（64B，仅 `The URL has moved`） |
| `http://10.2.0.47/gotoLogin` | 302 | `http://10.2.0.47/login` | 否 |
| `http://10.2.0.47/login` | 302 | `https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.47%2Flogin` | 否（113B） |
| `http://10.2.0.47/index` | 302 | `http://10.2.0.47/login` | 否 |
| `http://10.2.0.47/main/index` | **404** | — | 否 |
| `http://10.2.0.47/main/queryYcbxkc` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/logout` | 302 | `/enterOut` | 否 |
| `http://10.2.0.47/enterOut` | 302 | `https://pass.syau.edu.cn/tpass/logout?service=http://10.2.0.47` | 否（99B） |
| `https://10.2.0.47/` | — | **TCP 拒绝**（443 未开） | — |
| `https://pass.syau.edu.cn/tpass/login?service=...` | **200**（12229B） | — | 否（CAS 登录页，见 §3.2） |
| `http://10.2.0.48/` | 302 | `/login`（**无 `/gotoLogin` 这一跳**） | 否（60B） |
| `http://10.2.0.48/login` | 302 | `https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.48%2Flogin` | 否（113B） |
| `http://10.2.0.48/gotoLogin` | 302 | `http://10.2.0.48/login` | 否 |
| `http://10.2.0.48/zzzNotExist` | **404** | — | 否 |
| `http://10.2.0.48/ajax/student/getSectionAndTime` | **404**（1890B） | — | 否（教师端没有这个接口） |

> 47 与 48 的第一跳不同：**47 是 `/` → `/gotoLogin` → `/login` → CAS（三跳），48 是 `/` → `/login` → CAS（两跳）**。
> App 侧若做跳转次数判断，别按同一套写死。

### 2.2 文档已枚举、且与「课程 / 教学任务 / 培养方案」相关的路由

来源：`docs/URP-学生端接口清单.md` §1 菜单树。相关判定见「相关度」列（★=直接涉及课程内容或方案，○=课表/成绩等间接）。

| # | 菜单 | URL（`http://10.2.0.47` 起） | 状态码 | 跳转目标 | 含「大纲」 | 相关度 |
|---|---|---|---|---|---|---|
| 12 | 选课公告 | `/student/courseSelect/courseSelectNotice/index` | 302 | `/gotoLogin` | 否 | ○ |
| 13 | 选课 | `/student/courseSelect/courseSelect/index` | 302 | `/gotoLogin` | 否 | ○ 写操作 |
| 14 | 选课结果 | `/student/courseSelect/courseSelectResult/index` | 302 | `/gotoLogin` | 否 | ○ |
| 17 | 本学期课表 | `/student/courseSelect/thisSemesterCurriculum/index` | 302 | `/gotoLogin` | 否 | ○ |
| — | 课表 JSON | `/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback` | 302 | `/gotoLogin` | 否 | ○ |
| 18 | 历年学期课表 | `/student/courseSelect/calendarSemesterCurriculum/index` | 302 | `/gotoLogin` | 否 | ○ |
| 19 | 周课表 | `/student/personalSenate/giveLessonInfo/thisSemesterSchedule/weekLySchedule` | 302 | `/gotoLogin` | 否 | ○ |
| — | 实验课安排 | `/student/courseSelect/thisSemesterCurriculum/index/queryExperiment` | 302 | `/gotoLogin` | 否 | ○ |
| 20 | 选定教材 | `/student/courseSelect/books/dealBooks/index` | 302 | `/gotoLogin` | 否 | ○ |
| 25 | **课程课表**（2177 门） | `/student/teachingResources/courseCurriculum/index` | 302 | `/gotoLogin` | 否 | ★ |
| 23 | 教师课表 | `/student/teachingResources/teacherCurriculum/index` | 302 | `/gotoLogin` | 否 | ○ |
| 24 | 班级课表 | `/student/teachingResources/classCurriculum/index` | 302 | `/gotoLogin` | 否 | ○ |
| 22 | 教室课表 | `/student/teachingResources/classroomCurriculum/index` | 302 | `/gotoLogin` | 否 | ○ |
| 28 | 全部及格成绩 | `/student/integratedQuery/scoreQuery/allPassingScores/index` | 302 | `/gotoLogin` | 否 | ○ |
| 30 | **方案成绩** | `/student/integratedQuery/scoreQuery/schemeScores/index` | 302 | `/gotoLogin` | 否 | ★ |
| 34 | **方案完成情况** | `/student/integratedQuery/planCompletion/index` | 302 | `/gotoLogin` | 否 | ★ |
| 35 | **指导性教学计划** | `/student/integratedQuery/instructionPlanQuery/detail/index` | 302 | `/gotoLogin` | 否 | ★ |
| — | 培养方案 JSON | `/student/integratedQuery/instructionPlanQuery/detail/callback` | 302 | `/gotoLogin` | 否 | ★ |
| 36 | **本学期课程安排** | `/student/integratedQuery/course/courseSchdule/index` | 302 | `/gotoLogin` | 否 | ★ |
| 37 | **课程基本信息** | `/student/integratedQuery/course/courseBasicInformation/basicInf` | 302 | `/gotoLogin` | 否 | ★ **最像大纲的入口** |
| 37+ | 同上带参数 | `…/basicInf?kch=901040302`、`…?kch=1` | 302 | `/gotoLogin` | 否 | ★ 参数不影响鉴权判断 |
| 37+ | 猜测 index | `/student/integratedQuery/course/courseBasicInformation/index` | 302 | `/gotoLogin` | 否 | ★ 存在性未证 |
| 37+ | 猜测 callback | `/student/integratedQuery/course/courseBasicInformation/callback` | 302 | `/gotoLogin` | 否 | ★ 存在性未证 |
| 38/39 | 班级订书 | `/student/teachingMaterial/bookList/index` | 302 | `/gotoLogin` | 否 | ○ |
| 40 | 计费学分 | `/student/courseSelect/creditCheck/index` | 302 | `/gotoLogin` | 否 | ○ |
| 1 | 学籍信息（对照） | `/student/rollManagement/rollInfo/index` | 302 | `/gotoLogin` | 否 | 对照 |
| 21 | 教学评估（对照） | `/student/teachingAssessment/evaluation/queryAll` | 302 | `/gotoLogin` | 否 | 对照 |
| 47 | 首页推荐 | `/student/menu-recommend/page` | 302 | `/gotoLogin` | 否 | 对照 |

### 2.3 本轮按命名规律猜测的大纲类路由（学生端）

> 全部 302。**按 §1 的结论，这不代表页面存在**，只代表被鉴权过滤器覆盖。留档以便登录后逐个核对。

| URL（`http://10.2.0.47` 起） | 状态码 | 跳转目标 | 含「大纲」 |
|---|---|---|---|
| `/student/teachingTask/index` | 302 | `/gotoLogin` | 否 |
| `/student/teachingTask/teachingTaskQuery/index` | 302 | `/gotoLogin` | 否 |
| `/student/teachingTask/teachingTaskQuery/callback` | 302 | `/gotoLogin` | 否 |
| `/student/courseInfo/index` | 302 | `/gotoLogin` | 否 |
| `/student/courseInfo/courseOutline/index` | 302 | `/gotoLogin` | 否 |
| `/student/courseManage/courseOutline/index` | 302 | `/gotoLogin` | 否 |
| `/student/courseOutline/index` | 302 | `/gotoLogin` | 否 |
| `/student/syllabus/index` | 302 | `/gotoLogin` | 否 |
| `/student/integratedQuery/course/courseOutline/basicInf?kch=901040302` | 302 | `/gotoLogin` | 否 |
| `/student/integratedQuery/course/zzzNotExist/index`（阴性对照） | 302 | `/gotoLogin` | 否 |

### 2.4 本轮猜测的教师端路由（10.2.0.47 与 10.2.0.48）

| URL | 状态码 | 跳转目标 | 含「大纲」 |
|---|---|---|---|
| `http://10.2.0.47/teacher/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/teacher/teachingTask/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/teacher/teachingTaskManagement/teachingTaskQuery/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/teacher/courseManage/courseOutline/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/teacher/courseOutlineManage/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/teacher/courseInfoManage/courseBasicInformation/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.47/teacher/syllabus/index` | 302 | `/gotoLogin` | 否 |
| `http://10.2.0.48/teacher/index` | 302 | `/login` | 否 |
| `http://10.2.0.48/teacher/teachingTask/index` | 302 | `/login` | 否 |
| `http://10.2.0.48/teacher/teachingTaskManagement/teachingTaskQuery/index` | 302 | `/login` | 否 |
| `http://10.2.0.48/teacher/courseManage/courseOutline/index` | 302 | `/login` | 否 |
| `http://10.2.0.48/teacher/courseInfoManage/courseBasicInformation/index` | 302 | `/login` | 否 |
| `http://10.2.0.48/teacher/zzzNotExist/index`（阴性对照） | 302 | `/login` | 否 |

### 2.5 未登录唯一返回 200 的接口

| URL | 状态码 | 大小 | 内容 | 含「大纲」 |
|---|---|---|---|---|
| `http://10.2.0.47/ajax/student/getSectionAndTime` | **200** | 308B | 作息节次 JSON | **否** |
| `http://10.2.0.47/ajax/student/getSectionAndTime?xqh=001` | **200** | 308B | 同上（参数被忽略，返回一致） | **否** |

完整响应（已实测，可作为"白名单接口"证据）：

```json
{"status":200,"msg":"OK","data":{"firstday":1,"section":"{\"bz\":\"\",\"codeTermSectionPK\":{\"jclxdm\":\"01\",\"xqdm\":\"1\",\"xqlxdm\":\"1\"},\"swjc\":4,\"tjc\":12,\"wsjc\":4,\"xqzs\":23,\"xwjc\":4,\"zc\":\"111111111111111111111110\",\"zcsm\":\"1-23周\",\"zdlsjcs\":4,\"zs\":23,\"zslsjcs\":2,\"zts\":7}"}}
```

> 这是**节次时间表**（第 1 节起、共 12 节、1–23 周），与文档 §2「配套 `getSectionAndTime` 给作息时间表」一致。
> 它的价值是：**证明 URP 存在"白名单接口"这一层** —— 登录后如果有大纲数据口，很可能也长这样（`/ajax/…` 纯 JSON，无外壳 HTML）。
> 但它本身不含任何课程名、课程号、大纲文本。

---

## 3. 结论：要不要凭据？要谁的、要什么参数

### 3.1 必须凭据 —— 且是 CAS 会话，不是教务系统自己的账号密码

未登录时**没有任何一条**能看到课程大纲或课程简介的路径。判定依据：

1. §2 全部 91 条探测里，唯一的 200 业务响应是作息表（另一个 200 是 `favicon.ico` 图标）；课程/方案/课表类路由**无一例外** 302。
2. 302 的落点是**两级跳**，最终都停在同一个 CAS 端点：
   `http://10.2.0.47/login` 与 `http://10.2.0.48/login` 都被 302 到同一台：
   ```
   https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.47%2Flogin
   https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.48%2Flogin
   ```
3. 教务系统本身没有独立登录表单（`/login` 不做页面渲染，只做跳转），所以**账号密码在 CAS 侧校验**，
   拿到的是 `pass.syau.edu.cn` 域的会话票据，再由 `service` 回跳把凭据换成教务系统的 JSESSIONID。

### 3.2 需要向谁要、要哪三样

| 需要的东西 | 具体值 / 形态 | 向谁要 |
|---|---|---|
| **校内网可达** | `10.2.0.47:80`（学生端）、`10.2.0.48:80`（教师端）；443 **未开**，只能走 http | 校园网 / VPN。移动数据不通（与 AGENTS.md 记录一致） |
| **CAS 账号** | 学号 + 密码（学生）；工号 + 密码（教师，才能进 48 的教师端与「录入大纲」模块） | 本人。**教师端权限才是录入 2025 版大纲的入口**，学生账号进不去 |
| **CAS 服务地址（service）** | 学生端 `http://10.2.0.47/login`；教师端 `http://10.2.0.48/login`。URL 里必须是 **URL 编码后的** `http%3A%2F%2F10.2.0.47%2Flogin` | 由上面两个 302 的 `Location` 头直接给出，**不要自己拼** |
| CAS 端点 | `https://pass.syau.edu.cn/tpass/login`（登录）、`https://pass.syau.edu.cn/tpass/logout?service=http://10.2.0.47`（登出） | 实测已确认 |
| 会话载体 | 登录后教务系统下发的 cookie（文档记录为 `JSESSIONID`；`Set-Cookie` 在跳转过程中可能更换，必须逐跳吸收） | — |

### 3.3 CAS 登录页可达性（只 GET 了登录页，未提交）

`GET https://pass.syau.edu.cn/tpass/login?service=http%3A%2F%2F10.2.0.47%2Flogin` → **200，12229B**。
页面是金智 `tpass` 标准登录页（含 `cas_hash` 处理脚本）。**这证明 `service` 参数被接受、CAS 服务在跑**，
也证明"未登录 → 被送到这里"这条链是通的，而不是 DNS/防火墙问题。

### 3.4 有没有"不需要登录就能看课程简介"的页面

**在本轮覆盖范围内没有。** 已排除的方向：

| 方向 | 结论 |
|---|---|
| URP 课程基本信息页（`courseBasicInformation/basicInf`） | 302，需登录。这是文档里最接近"课程简介"的入口 |
| URP 各猜测大纲路由（`courseOutline` / `syllabus` / `teachingTask`） | 全部 302；且因过滤器先于路由，**连"是否存在"都无法用状态码判断** |
| `/ajax/*` 白名单接口 | 只找到作息表一支，不含课程内容 |
| 静态附件目录（`/upload/`、`/files/`、`/attachment/`） | 全部 404，无目录遍历 |
| 教师端（48）匿名访问 | 更严：`/ajax/…` 直接 404，根路径少一跳但同样进 CAS |

> 注：教务处站群（`jwc.syau.edu.cn`）的**培养方案 PDF** 是对外公开的，但那是"培养方案"不是"课程教学大纲"，
> 且附件下载要过图形验证码 —— 此结论已记录在 `docs/URP-学生端接口清单.md` §6.5，本轮未重复探测。

### 3.5 「含大纲字样」命中的 URL

**零命中。** 91 次 GET 中没有任何响应体包含「大纲」二字：

- 302 响应体只有 `The URL has moved <a href="…">here</a>`（60–113B），没有正文
- 404 页面是统一的「页面404错误」模板（1642B），不含业务文案
- 唯一的 200（作息表）是纯节次 JSON
- CAS 登录页（12229B）是登录表单，无「大纲」

---

## 4. 下一步建议

### 4.1 登录后第一站：先看菜单，别猜 URL

按 §1 的教训，**猜 URL 在这套系统上是无效劳动**（302 不区分存在与否）。正确顺序：

1. 用**教师账号**登录 `http://10.2.0.48/`（这是「课程负责人录入大纲」的那一端）。
2. 落 `http://10.2.0.48/index`，**dump 侧边栏全部 `<a href>`**（学生端 47 项菜单就是这么拿到的，见 `docs/_probe/urp/links.json`）。
   在菜单文本里搜关键词：`教学大纲`、`大纲`、`课程简介`、`教学任务`、`课程管理`、`培养方案`。
3. 找到候选菜单后，**只 GET 该页**，看有没有导出/打印/下载按钮（文档 §6.5 的经验：URP 大纲/方案页普遍**无导出**）。
4. 同时抓该页的 XHR —— 如果大纲是结构化存储，很可能有 `/ajax/…` 或 `/…/callback` 纯 JSON 口
   （对照：`getSectionAndTime` 白名单 JSON、`ajaxStudentSchedule/callback` 22KB JSON）。

### 4.2 学生端并行一条低成本路线

学生端 `/student/integratedQuery/course/courseBasicInformation/basicInf`（菜单 37「课程基本信息」）
是唯一在文档里被点名"课程"的详情页。登录后打开它，看：
- 页面上的课程列表是不是**全校 2177 门**（对照菜单 25「课程课表」的 2177 条）
- 每门课点进去有没有「课程简介 / 教学目标 / 教学内容」段落 —— 如果有，**那就是大纲的摘要版**，
  即使没有完整大纲，也足以作为 2025 版抓取的字段基线

### 4.3 抓取前的三项准备（避免返工）

1. **`service` 参数照抄 302 的 `Location`**，不要手拼；47 与 48 的 service 值不同。
2. **逐跳吸收 `Set-Cookie`**：`/` → `/gotoLogin` → `/login` → CAS → 回跳，中间可能换 JSESSIONID
   （空闲教室两步流已经踩过这个坑，见文档 §3.4）。
3. **教师端与学端 cookie 不可混用**：两个 host（47/48）即使同域 CAS，也各自有独立会话。

### 4.4 本轮未做、建议下一轮做的事

| 事项 | 为什么现在没做 |
|---|---|
| 登录并枚举教师端菜单 | 本轮规则：不登录、不提交表单 |
| 探 48 端的大纲录入/查看路由 | 同上；且无菜单证据时猜 URL 无效 |
| 教务处站群 `jwc.syau.edu.cn` 的 2025 版培养方案 PDF | 非本轮范围；且附件需过验证码（已记录在案） |
| 检查是否有独立的大纲管理系统子域（如 `dg.syau.edu.cn`） | 需要子域枚举，超出"只探 47/48"的范围 |

---

## 5. 探测统计（可复查）

| 批次 | 内容 | 请求数 | 结果分布 |
|---|---|---|---|
| `_probe_raw.json` | 基础入口 + 文档路由 + 首轮猜测 | 26 | 25×302，1×200 |
| `_probe_raw2.json` | 跳转链、`/ajax`、静态资源、https | 12 | 7×302，2×200，2×404，1×连接拒绝 |
| `_probe_raw3.json` | 404 判据测试 + 扩展猜测 | 19 | 17×302，2×404 |
| `_probe_raw4.json` | CAS 登录页、`/logout`、`/main/*` | 4 | 2×302，1×200，1×404 |
| `_probe_raw5.json` | 补全文档内课程/方案相关路由 | 12 | 12×302 |
| 附加批（已并入 §2 表格） | 过滤器前缀特征、`/student` 空路径、`favicon` | 10 | 8×302，1×404，1×200 |
| 附加批（已并入 §2 表格） | 教师端 48、静态目录、带参 `basicInf` | 8 | 3×302，4×404，1×200 |
| **合计** | | **91** | **302 占 74；404 占 10；200 占 6；连接拒绝 1** |

**全程无 POST、无登录、无凭据使用、未修改任何仓库文件。**
