# 08: 教师详情页（新开页：课表 + 官网公开信息）

**What to build:** 学生在「查教师」搜到人之后**点得进去**，进入一个**独立页面**（不是浮层/弹窗），
页面里有两块：
1. **TA 这学期的课表**（周网格或列表，含课程、周次、星期/节次、校区教学楼教室、学分、考核方式、上课班级、人数）
2. **学院官网的公开资料**（职称、研究方向、办公地点、办公电话、校方邮箱…按实际可得的来），
   并标注「信息来自 XX 学院官网 · 原文链接 · 抓取于 YYYY-MM-DD」

**Blocked by:** 05（官网调研已 resolved）、01（教师列表已 ready-for-human）

**Status:** ready-for-human

---

## 已实测的接口（2026-11-14，结论不要重复调查）

### 教师课表（两步）
```http
① POST /student/teachingResources/teacherCurriculum/searchCurriculumInfoLo
   teacherNum=1995500023&teacherName=<urlencoded>&planCode=2026-2027-1-1&planName=<urlencoded>
   → 只是回执（100 字节），**必须发**（像考试接口的预热）

② GET /student/teachingResources/teacherCurriculum/searchCurriculumInfo/callback
        ?planCode=2026-2027-1-1&teacherNum=1995500023
   → 数组套数组 [[{slot}, …]]，取 [0]
```
slot 关键字段：`id.skzc`（**24 位周次位图**）、`skxq`（星期 1..7）、`skjc`（起始节次）+ `cxjc`（连上几节）、
`kcm` 课程名、`xf` 学分、`kslxmc` 考核方式、`xqm`/`jxlm`/`jasm` 校区/楼/教室、`bjh` 上课班级、`xss` 学生数、`zcsm` 人话周次。

完整字段表见 `docs/URP-学生端接口清单.md` §6.2；
样本：`docs/_probe/urp/teacher_schedule_callback.json`（真实、可直接当单测夹具）。

### 官网公开信息
见 `docs/学院官网-教师公开信息源调研.md`（票 05 结论）：
- VSB 站群，正文容器 `vsb_content` / `v_news_content` / `WspDetailContent`（多级兜底）
- 17 个学院栏目一院一配（表在调研文档 §1）
- **3/13 详情页朴素提取会失败**，失败要明说，不许显示空卡
- 缓存：按教师存结构化 JSON（键=工号），TTL 30 天，失败保留旧缓存并标注时间，上限 500 条/5MB
- **个人手机号 / QQ / 私人邮箱一律不落地**，只留教职工公务联系方式

---

## 交互要求（主人明确说的）

- **详情必须是新开的页面**，用现有的 `UserPage` 栈（`UserScreen` 里 `stack.add(...)`），
  不要用浮层、不要用 Dialog
- 入口：教师列表每一行可点（现在点不动，只能看）
- 页面结构建议：顶部教师身份（姓名/院系/职称/工号）→ 课表块 → 官网资料块 → 底部「在浏览器打开官网原文」
- 课表块：优先画成**周网格**（复用课表页的几何思路即可，不必复用其代码）；
  若某位老师课很多画不下，改用按天分组的列表，二选一但要在交付说明里写理由
- 官网资料缺失/解析失败：明说「学院官网没解析到这位老师的信息」+ 给原文搜索链接
- 课表为空：区分「这位老师本学期没排课」与「查询失败」——**不能混成一句**

## 实现要点

- 新文件放 `ui/home/TeacherDetailScreen.kt`（页面）+ 需要时 `data/faculty/`（官网抓取与缓存）
- 数据层加进 `data/UrpHomeApi.kt`（课表两步 + 解析纯函数），官网抓取另起文件别混
- `AppState` 加教师详情状态；`TimetableViewModel` 加 `openTeacherDetail/closeTeacherDetail`
- **复用现有会话**与 `SessionState` 三态、`friendlyError`、`noSessionHint`，不要另造一套错误文案
- `planCode` 用当前学期（`HomeModel.currentTermCode` 已有）；`planName` 从 `termLabel` 或课表取

## 验收标准

- [ ] 列表点某位老师 → 进入独立详情页（返回键可退回列表）
- [ ] 详情页显示该教师本学期课表（用 `skzc` 位图 + `skjc/cxjc` 定位，不用解析文字周次）
- [ ] 显示学院官网公开资料，含来源与抓取时间；有「浏览器打开原文」
- [ ] 官网抓取成功后有缓存，断网再进同一位老师仍能看到上次内容（标注时间）
- [ ] 三种失败各自可辨：没登录/不在校园网、这位老师没排课、官网没解析到
- [ ] 单测：课表 JSON 解析（真实夹具）、官网字段抽取（含 3 个失败样本→返回空而非乱码）、缓存键与 TTL 判定
- [ ] 全量单测绿（基线 **238 条 / 17 类 / 0 失败**），`assembleDebug` 成功
- [ ] 日志无 cookie / 姓名 / 学号；个人手机号与 QQ 不进缓存

## 边界

- 只读教务；官网只抓教师公开信息页；不抓学生信息
- 不动 `ui/feed/`、`ui/timetable/`、`RoomsScreen.kt`（票 03 刚交付，暂不回流）
- 本票吸收原票 06 的「官网信息接入」部分；票 06 关闭并指向本票

## Comments

- 2026-11-14 lead：本票由主人实测反馈催生 ——「查教师之后详情点不进去，应该之后做的，详情新开页，
  有课表和详细的官网公开信息」。教师课表路由已由 lead 用活的登录态实测并落盘（见上），
  开工不需要再探这条。

### 2026-11-14 交付（agent）

**课表路由没重复探**：按 lead 给的两步流直接实现，样本 `teacher_schedule_callback.json` 原样当夹具。

**改了什么**

- `data/UrpHomeApi.kt`
  - 常量 48/52 行：`TEACHER_SCHEDULE_LO` / `TEACHER_SCHEDULE_CB`。
  - 模型 243 行 `TeacherSlot`（含 `sections`/`place`/`weekText`）、286 行 `TeacherScheduleResult`
    （`schedule` + `sessionLost`，把「没排课」和「没拿到」分成两种）、292 行 `TeacherSchedule`（`courseCount` 去重计门数）。
  - 526 行 `fetchTeacherSchedule`：① 预热 POST（不发它 ② 空）→ ② GET callback；非 JSON / 登录页 →
    `sessionLost=true`，不当成「没排课」。
  - 547 行 `teacherScheduleLoForm`、560 行 `teacherSchedulePath`（参数 URL 编码）。
  - 913 行 `parseTeacherSchedule`：`[[…]]` 取 `[0]`（也容忍 `[…]`）；周次**只**用 `id.skzc` 位图
    （复用 `UrpClient.decodeWeekBitmap`，没另写一份）；`skxq` 数字优先、外层字符串兜底；空/坏 JSON 返回空课表不抛。
- `data/faculty/`（新目录，官网抓取与缓存，没有塞进 UrpHomeApi）
  - `FacultySources.kt` 34/75 行：16 院一院一配（`college/host/columns/aliases`），`of()` 三级匹配；
    `图书`/`全院` 这类没有师资栏目的返回 null，**不拿别的学院顶替**。
  - `FacultyParser.kt` 59 行 `extractBody`（5 级容器兜底 + 页脚截断 + 整页兜底 `whole_page`）、
    126 行 `fieldsOf`（职称/学历/导师/职务/研究方向/办公地点/公务电话/校方邮箱/性别/出生年月）、
    213 行 `findDetailLinks`（按姓名精确匹配锚点文字 → 绝对地址，多个=重名交回调用方）、
    237 行 `parseDetail`、273 行 `FacultyPrivacy`（手机号 / QQ / 非校方邮箱**抽取阶段就丢**）。
  - `FacultyStore.kt` 17 行 `FacultyCache`：30 行 `key`（优先工号，无工号用 姓名@域名 SHA-1 取 16 位）、
    45 行 `isFresh`（TTL 30 天，时间解析不出→当作不新鲜，未来时间不认）、64 行 `evictionPlan`（500 条/5MB 按最旧淘汰）；
    92 行 `FacultyStore`：`filesDir/faculty/<键>.json`，`.tmp` 改名原子写、`trim()`、`clear()`。
  - `FacultyRepository.kt` 34 行 `load`：缓存新鲜→直返；否则栏目页→详情页→解析→落盘；
    失败分类 `NoSource/NotFound/NotParsed/Ambiguous/Network`，**失败但有旧缓存→返回旧缓存并标 `stale`**。
- `ui/home/TeacherDetailScreen.kt`（新文件，55 行）：独立页面。身份头 → 课表块（239 行 `WeekGrid`
  6 大节 × 7 天 + 305 行 `SlotRow` 明细）→ 官网块（字段 + 小节 + 来源与抓取日期 + 「在浏览器打开官网原文」）
  → 同名候选入口。**网格 + 明细并存**的理由：网格回答「哪天满」，明细才装得下月次/教室/班级/人数/考核方式。
- `ui/home/TeacherScreen.kt` 50/144 行：行变可点（`clickable(onClickLabel=…)`）+ 右侧 `›` 提示；
  新增 `onOpenTeacher` 回调。
- `ui/user/UserScreen.kt` 67/117/118/121 行：`UserPage.TeacherDetail("教师详情")` 入栈，点老师 → 压页。
- `ui/AppState.kt`：教师详情 13 个字段；`ui/TimetableViewModel.kt` 607/636/643/663/704 行：
  `openTeacherDetail/closeTeacherDetail/refreshTeacherDetail` + 课表与官网两条独立加载线。
- `ui/home/HomeModel.kt` 266~336 行：`termPlanName`、`weekdayLabel`、`sectionsLabel`、`scheduleHeadline`、
  `slotWhenLabel`、`compressWeeks`、`facultySourceLine`、`facultyMsg`（五种失败五句话）。
- `data/DataWiper.kt`：⑤b 清除时连带清 `filesDir/faculty/`，并报「清了几位」。
- 日志纪律：教师课表与官网日志**只记工号/院系/条数**，不记姓名。

**怎么验证**

- 先单独跑 `:app:compileDebugUnitTestKotlin`（BUILD SUCCESSFUL），再跑 `build-test.bat`：
  `BUILD SUCCESSFUL in 1m 4s` / `EXIT=0`（`:app:testDebugUnitTest` + `:app:assembleDebug` 都执行了，
  APK 2026-11-14 10:51:15 重生成）。
- 单测：**266 条 / 19 类 / 0 失败 0 错误**（基线 238/17，本轮 +28 条 / +2 类）。
- 新增测试文件：`TeacherScheduleParseTest`（7 条，真实夹具）、`FacultyParseTest`（15 条）；
  `HomeModelTest` +6 条。
- 重点断言：副教授不被认成教授；周次来自 24 位位图（`111010011111111000000000` → 12 周）；
  `det_jgxy_zhoumi`（无容器）走整页兜底；只有图+姓名的页面 `parseDetail` 返回 null；
  `det_zbxy_1808` 里的手机号 `13464032492`、QQ `1147888178`、`snzhibao@163.com` **一个都不能出现**，
  而校方邮箱 `fanghong@syau.edu.cn` 必须留；TTL 29 天新鲜 / 31 天过期 / 坏时间与未来时间都不新鲜；
  淘汰按最旧；五种失败文案互不相同。

**遗留 / 只能主人做的**

1. **真机验证**（`10AE1H1PYJ005ED` 本轮未连接）：点某位老师 → 详情页出课表 + 官网资料；返回键退回列表。
2. **官网抓取只在有网时验证过解析口径**：本轮用 13 个真实页面离线验证解析与清洗，
   但「栏目页 → 详情页」的在线链路（含 GBK 编码、重定向）没在设备上跑过；
   若某位老师显示「栏目里没找到」，多半是他只在博导/其他专栏，需要把该栏路径补进
   `FacultySources`（一院一配，不许猜）。
3. 同名教师走「候选原文」入口，没有做候选消歧 UI（研究文档建议让用户选，这里给了链接让他自己看）。
4. 课表未做「按周过滤」（显示全部时段 + 周次文字）；如果要「只看本周」，
   需要接 `TermCalendar.currentWeek()` —— 那属于课表页的能力，本票没扩。
5. 票 03 的低优先补充（服务端编码过滤 `classroomNumber` 等 + 行内占用路由）未做，按 lead 指示排在 08 之后。
