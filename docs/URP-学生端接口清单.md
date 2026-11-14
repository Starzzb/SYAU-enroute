# URP 学生端接口清单（10.2.0.47）—— 2026-11-13 实测

> 方法：本机 Edge（已登录 CAS）+ Playwright CDP 接管，逐个打开菜单页并捕获其 XHR/JSON 响应。
> 原始证据：`docs/_probe/urp/`（`links.json` 菜单树、`pages.json` 逐页探测、`json_*.json` 原始响应体、`rollinfo.*`、`freeclassroom.*`）
> 原则：**只做 GET 浏览**，未点任何提交按钮、未改动任何教务数据。

---

## 0. 一句话结论

菜单共 **47 个学生端入口**。其中 **16 个页面背后有可直接读取的 JSON 接口**（比 App 现在用的 HTML 解析更稳、字段更全），
而且**学校端已经把绩点、学分统计、培养方案进度算好了**——不需要我们自己算，也不该算。

对 App 最值钱的三件事：
1. `gradePointScore` 每条成绩都有 → **GPA 不用猜**（现在代码里"不显示 GPA"的理由消失了）
2. 培养方案给了 `已得 106 / 需修 165` 学分 → **主页学分进度条**
3. 课表 callback 给了 `课程属性/考核方式/英文名/教师工号` → 课表详情能写全，且解析不再依赖 HTML 去猜

---

## 1. 完整菜单树（47 项）

来源：`/index` 页侧边栏 `<a href>`，见 `links.json`。

| # | 菜单 | 路径 | 数据口 | 备注 |
|---|---|---|---|---|
| 1 | 学生学籍信息 | `/student/rollManagement/rollInfo/index` | 无 JSON，**服务端渲染 HTML** | 学号/姓名/学院/专业/班级 + 一寸照 `/student/rollInfo/img` |
| 2 | 个人信息修改 | `/student/rollManagement/personalInfoUpdate/index` | 未探 | 写操作，不做 |
| 3 | 学籍异动 | `/student/rollManagement/rollChanges/index` | 无 JSON | 低频 |
| 4 | 奖惩信息 | `/student/rollManagement/rewardsAndPenalties/index` | 无 JSON | 低频 |
| 5 | 电子注册 | `/student/rollManagement/electronicRegistration/index` | 无 JSON | 学期注册状态 |
| 6 | 辅修方案注册 | `/student/rollManagement/minorProgramRegistration/index` | 未探 | 低频 |
| 7 | 我的学籍卡片 | `/student/rollManagement/myRollCard/index` | 无 JSON | 卡片式，可截图 |
| 8 | 学生证明打印 | `/student/studentCertificatePrinting/index` | 无 JSON | 开证明入口 |
| 9 | 个人申请 | `/student/application/index` | 未探 | 申请流 |
| 10 | 课程替代申请 | `/student/personalManagement/personalApplication/curriculumReplacement/index` | 未探 | 低频 |
| 11 | 申请成绩认定 | `/student/personalManagement/achievementDetermination/index` | 未探 | 低频 |
| 12 | 选课公告 | `/student/courseSelect/courseSelectNotice/index` | `/main/queryYcbxkc` → `{"kg":0}`（仅开关位） | 公告正文在 HTML 里 |
| 13 | 选课 | `/student/courseSelect/courseSelect/index` | 未探 | **写操作，绝不自动调** |
| 14 | 选课结果 | `/student/courseSelect/courseSelectResult/index` | `/student/courseSelect/thisSemesterCurriculum/callback`（25KB） | 同课表结构 |
| 15 | 退课 | `/student/courseSelect/quitCourse/index` | 未探 | **写操作** |
| 16 | 选课失败信息 | `/student/courseSelect/courseSelectFailed/index` | 无 JSON | |
| 17 | **本学期课表** | `/student/courseSelect/thisSemesterCurriculum/index` | ⭐ `.../ajaxStudentSchedule/callback`（22KB） | 见 §2 |
| 18 | 历年学期课表 | `/student/courseSelect/calendarSemesterCurriculum/index` | 同 17 + `/student/internship/sxbggl/findSxkb?xnxq=` | 可切学期 |
| 19 | 本学期周课表 | `/student/personalSenate/giveLessonInfo/thisSemesterSchedule/weekLySchedule` | `.../ajaxStudentSchedule/weekLySchedule/callback/{周次}` | 按周取，含周次参数 |
| 20 | 选定教材 | `/student/courseSelect/books/dealBooks/index` | 未探 | |
| 21 | **教学评估** | `/student/teachingEvaluation/newEvaluation/index` | ⭐ `/student/teachingAssessment/evaluation/queryAll`（6KB） | 18 份待评 → **主页提醒** |
| 22 | 教室课表 | `/student/teachingResources/classroomCurriculum/index` | `.../classroomCurriculum/search`（10KB） | 295 间教室，含容量/类型。**过滤参数未实测**，见 §6.1 |
| 23 | 教师课表 | `/student/teachingResources/teacherCurriculum/index` | `.../teacherCurriculum/search`（7.6KB） | 1008 条 |
| 24 | 班级课表 | `/student/teachingResources/classCurriculum/index` | `.../classCurriculum/search`（9KB） | 479 个班 |
| 25 | 课程课表 | `/student/teachingResources/courseCurriculum/index` | `.../courseCurriculum/search`（7.6KB） | 2177 门 |
| 26 | **空闲教室查询** | `/student/teachingResources/freeClassroom/index` | POST `/student/teachingResources/freeClassroom/today` | 见 §3（HTML 返回） |
| 27 | 教室使用状况 | `/student/teachingResources/classroomUseStatus/index` | 无 JSON | 同 26 思路 |
| 28 | 全部及格成绩 | `/student/integratedQuery/scoreQuery/allPassingScores/index` | ⭐ `.../<token>/allPassingScores/callback`（64KB） | 见 §4 |
| 29 | 课程属性成绩 | `.../coursePropertyScores/index` | `.../<token>/coursePropertyScores/callback` | 按课程属性分组 |
| 30 | 方案成绩 | `.../schemeScores/index` | `.../<token>/schemeScores/callback` | 按培养方案分组 |
| 31 | 不及格成绩 | `.../unpassedScores/index` | `.../<token>/unpassedScores/callback` | 结构同上，本次全 0 |
| 32 | 本学期成绩 | `.../thisTermScores/index` | `.../<token>/thisTermScores/data` | 本次返回空 `list:[]` |
| 33 | 成绩查询（全学期） | `.../allTermScores/index` | `.../<token>/allTermScores/data` | **App 现在用的就是这个** |
| 34 | **方案完成情况** | `.../planCompletion/index` | 无 JSON（服务端渲染） | 学分进度可从 §5 拿，不必爬这个 |
| 35 | **指导性教学计划** | `.../instructionPlanQuery/detail/index` | ⭐ `.../<token>/detail/callback`（65KB） | 见 §5 |
| 36 | 本学期课程安排 | `.../course/courseSchdule/index` | 无 JSON | |
| 37 | 课程基本信息 | `.../course/courseBasicInformation/basicInf` | 无 JSON | |
| 38 | 班级订书详单 | `.../teachingMaterial/bookList/index` | 无 JSON | |
| 39 | 班级订书查看 | `.../teachingMaterial/bookList/classMatericalIndex` | 未探 | |
| 40 | 计费学分确认 | `/student/courseSelect/creditCheck/index` | 无 JSON | |
| 41 | **考试安排** | `/student/examinationManagement/examPlan/index` | `.../examPlan/detail?start=&end=` | **App 已在用** |
| 42 | 考试报名 | `/student/examinationManagement/examSignUp/index` | 无 JSON | 报名是写操作 |
| 43 | 我的实习周报 | `/student/internship/mysxzb/index` | 无 JSON | |
| 44 | 实习执行计划填报 | `/student/internship/internship/execution/planCompletion/index` | 未探 | |
| 45 | 实习过程管理 | `/student/internship/sxgcgl/index` | `.../sxgcgl/findXszbs?pageNum=&pageSize=` | 周报列表 |
| 46 | 实习报告 | `/student/internship/sxbggl/uploadIndex` | 未探 | |
| 47 | 首页 | `/` | `/student/menu-recommend/page`（推荐卡） | |

**token 机制（重要）**：`/student/integratedQuery/scoreQuery/<token>/...` 与 `.../instructionPlanQuery/<token>/...` 里的 token
**每次加载都变**（实测 `k9EB2L1DuM`、`D8E10648k0`、`8yol2Qq034`…各不相同）。
所以必须"取 index 页 → 正则抠 token → 立刻查 data"，不能写死。这条已经在 `Academic.kt` 里落实了。

---

## 2. 课表 callback（比现在用的 HTML 解析强得多）

`GET /student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback`

```jsonc
{
  "allUnits": 12.25,                    // 本学期总学分 —— 主页可直接用
  "xkxx": [{ "902021102_01": {          // 以「课程号_课序号」为 key
      "courseName": "社会主义发展史",
      "englishCourseName": "History of the Development of Socialism",
      "courseCategoryName": "通识选修课",   // 必修课/限修课/通识选修课
      "coursePropertiesName": "选修课",
      "attendClassTeacher": "李洋* ",
      "unit": 2,                          // 学分
      "examTypeName": "考查（不安排考试）",
      "studyModeName": "正常",
      "programPlanName": "2024级土地资源管理培养方案",
      "selectCourseStatusName": "选中",    // 选中/置入
      "skzcs": "21周上",                   // 上课周次描述
      "timeAndPlaceList": [{
          "classDay": 6, "classSessions": 9, "continuingSession": 2,
          "classWeek": "000000000000000000001000",  // 24 位周次位图
          "campusName": "路南", "teachingBuildingName": "实践上课地点",
          "classroomName": "具体地点教师通知1", "weekDescription": "21周上"
      }]
  }}],
  "dateList": [{ "programPlanName": "...", "totalUnits": 12.25, "selectCourseList": [ ... ] }],
  "jcsjbs": [...],   // 节次时间表
  "csz": {...}
}
```

**价值**：
- `classWeek` 是 **24 位周次位图**（第 N 位 = 第 N 周），比现在的 `weekDescription` 字符串解析可靠；
- 课程属性/考核方式/英文名/选课状态 → 课表详情页可以显示成"结构化事实"，不用猜；
- `allUnits` → 主页"本学期 12.25 学分 / 8 门"。

配套：`GET /ajax/student/getSectionAndTime` → 作息时间表（含 `firstday`、每节起止）
→ **可以干掉现在硬编码的冬季/夏季作息**（现在 `SchoolClock` 是写死的）。

---

## 3. 空闲教室（**会话粘滞的两步流**，2026-11-13 更正）

> ⚠️ **本节此前写的是「纯 JSON，三支接口搞定」——那是错的。** 漏掉了最关键的前提：
> 第三支接口依赖**服务器会话里「当前选中的楼」**，只发 GET 永远返回空。
> 更正依据：`docs/_probe/urp/free_sticky.js` 的对照实验（2026-11-13，本机 Edge 登录态 + CDP 接管）。

> 一开始看到的是"POST `/today` 返回 153KB HTML"，那只是外壳页——真正的数据是页内 Ajax 拉的两支 JSON。
> 2026-11-13 直接打接口验证（`docs/_probe/urp/free_api_probe.txt` + `free_*.json`）。

### 3.1 两支列表接口（无状态，随便打）

| 用途 | 请求 | 返回 |
|---|---|---|
| 校区列表 | `GET /student/teachingResources/freeClassroom/queryCodeCampusList` | 270B，校区数组 |
| 楼栋列表 | `GET /student/teachingResources/freeClassroom/queryCodeTeaBuildingList?xqh=<校区号>` | 3388B，`[{id:{campusNumber,teachingBuildingNumber},teachingBuildingName,remark}]` |

### 3.2 空闲教室本体（**会话粘滞**）

```http
① POST /student/teachingResources/freeClassroom/today
   Content-Type: application/x-www-form-urlencoded
   position=<校区号>_<楼号>&xqm=<校区名>          ← 例：position=001_2&xqm=主校
   → 返回 153KB 的**外壳 HTML**（内容无用，作用是把这个楼记进服务器会话）

② GET /student/teachingResources/freeClassroom/today/<节次>?dayplus=<0今天|1明天|2后天>
   → 纯 JSON（只有①选中的那栋楼）
```

**对照组实验（同一会话，逐条独立执行）**：

| 做法 | 结果 |
|---|---|
| 只 `GET /today/11?dayplus=0` | 空（len=37） |
| ① `POST position=001_1&xqm=主校` → ② `GET /today/11` | 第一教学楼 **74 间** |
| ① `POST position=001_2` → ② `GET /today/11` | 第二教学楼 **67 间** |
| ① `POST position=001_2` → ② `GET /today/3` | 第二教学楼 **7 间** |
| ① `POST position=001_6` → ② `GET /today/11` | 空（水利馆该节次确实没空教室） |
| `GET /today/11?dayplus=0&position=001_2`（参数当**查询串**传） | 空 → **`position` 当查询参数无效，必须走 POST 表单** |

结论：**先把楼 POST 进去，再 GET 节次；两步必须同一个 cookie 会话，且中间不能重新登录。**
换楼、换节次、换日期都要重做 ①②（①②可以连续做，不必重新登录）。

```jsonc
// ② GET /freeClassroom/today/11?dayplus=0   ← 建议带 X-Requested-With: XMLHttpRequest
{
  "curxqm": "主校",
  "spareroomObjList": [
    { "acmcBuilding": "2", "acmcBuildingName": "第二教学楼",   // acmcBuilding = 楼号，可用于核对
      "claroom": [
        { "classroom": "719", "classNumberOfSeats": "72", "szlc": "7" },   // szlc = 所在楼层
        { "classroom": "713", "classNumberOfSeats": "1",  "szlc": "7" }
      ] }
  ]
}
```

**空结果要分三种，不能一律说「没有空闲教室」**（App 里已经这么做了，见
`UrpHomeApi.FreeRoomsIssue` + `HomeModel.freeRoomsEmptyHint`）：

| 现象 | 真实原因 | 该说什么 |
|---|---|---|
| JSON 正常、`claroom` 为空 | 这栋楼这一节真的没空教室 | 换节次 / 换「明天 / 后天」 |
| 响应是登录页 HTML / 空 / 非 JSON | 会话失效或不在校园网 | 重新登录 / 连校园网 |
| 返回的 `acmcBuilding` ≠ 请求的楼号 | ① 这一步没生效（会话粘在别的楼上） | 刷新重试；仍如此=接口行为变了 |

### 3.3 校区号 / 楼号实测映射

校区号：`001` 主校 / `2` 路南 / `004` 研究生部 / `005` 海城基地 / `006` 熊岳校区
（注意"路南"是单位数 `2`、研究生部是三位 `004`，**不要按位数猜**）。

主校楼号：`1` 第一教学楼、`2` 第二教学楼、`4` 综合楼、`6` 水利馆、`7` 土壤楼、`9` 农机楼、
`10` 农机化楼、`20` 理学楼、`21` 水利学科楼、`26` 图书馆、`n` 全部教学楼，外加
`ZZZYZX` 种质资源中心、`凡舟体育场`、`分析中心`、`食品创新`、`团委`、`游泳池`、`333` 线上考场。

### 3.4 App 侧实现（2026-11-13）

- 两步流在 `UrpHomeApi.fetchFreeRooms`：`POST position=<校区号>_<楼号>&xqm=<校区名>` →
  `GET <节次>?dayplus=N`，同一 `UrpClient.Session`，并顺手吸收 `Set-Cookie`
  （服务器若在这一步换 JSESSIONID，不更新 cookie 就丢粘滞）。
- 结局判定是纯函数 `UrpHomeApi.judgeFreeRooms(pickBody, roomsBody, buildingNo)`，单测锁 schema
  （夹具 `app/src/test/resources/home/urp_free_rooms*.json`，取自 `docs/_probe/urp/free_today_s3.json`
  与 `free_today_s11.json`）。

---

## 4. 成绩 callback —— 学校已经把统计算好了

`GET /student/integratedQuery/scoreQuery/<token>/allPassingScores/callback`

```jsonc
{
  "zxjxjhh": "2024-2025-1-1",
  "state": 1,
  "lnList": [{                       // 每个学期一段
    // ── 学期汇总（学校算的，不是我们算的）──
    "zxf": 24.05, "zxs": 446,        // 总学分 / 总学时
    "yxxf": 24.05, "yqxf": 14,       // 已修学分 / 已取得学分
    "zms": 13, "tgms": 13, "wtgms": 0,  // 总门数 / 通过门数 / 未通过门数
    "cjlx": "2024-2025学年秋(两学期)",
    "cjList": [{
      "courseName": "地理信息科学",
      "englishCourseName": "...",
      "cj": 81.0,                    // 成绩
      "credit": 3,                   // 学分
      "gradePointScore": 3.1,        // ⭐ 绩点（学校给的）
      "standardScore": null,         // 标准分（有就算）
      "percentileRankScore": null,   // 百分位排名
      "gradeScore": -95,             // 等级分（负值表示无）
      "courseAttributeName": "必修课",
      "termName": "秋", "termCode": "1",
      "examTime": "20260701",
      "makeupExaminationTypeCode": null,  // 补考标记
      "planName": "...", "remark": null
    }]
  }]
}
```

**这直接解决了 App 里那句注释**："不在这里算加权平均 —— 因为学分和成绩字段名各校不同，算错就是假数据"。
现在：**绩点逐条都有（学校算的）**，加权平均 = Σ(绩点×学分)/Σ学分，是**可复现的算术**，可以显示，并标注"按学校绩点计算"。
门数/学分统计也直接读 `tgms/zms/yxxf/zxf`，不要自己数。

---

## 5. 培养方案 —— 学分进度

`GET /student/integratedQuery/instructionPlanQuery/<token>/detail/callback`（65KB）

```jsonc
{
  "famc": "2024级土地资源管理培养方案",
  "planNum": "202403112440",
  "xqzxf": 165,      // ⭐ 需修学分
  "ydhxf": 106,      // ⭐ 已得学分
  "tgmc": 55,        // 通过门数
  "wtgmc": 0,        // 未通过门数
  "xnxqList": [ { 学期列表 } ],
  "list": [ { "xdmc": "...", "xdxf": 已修学分, "cjList": [...], "jhwcjList": [...] } ]  // 按课程属性大类分段
}
```

→ 主页学分进度：**106 / 165 = 64%**，按大类（必修/限修/通识…）还能拆。

---

## 6. 其它可用小接口

| 接口 | 用途 |
|---|---|
| `GET /student/rollInfo/img`、`/main/queryStudent/img` | 学籍照片 / 头像（HTML 页渲染，可做身份卡） |
| `GET /student/teachingAssessment/evaluation/queryAll` | 待评问卷 18 条 → **红点** |
| `POST /student/teachingResources/classroomCurriculum/search` | 295 间教室（`classroomTypeDirections` 机房、`classNumberOfSeats` 容量） |
| `POST /student/teachingResources/teacherCurriculum/search` | 1008 条教师课表（选课前查老师） |
| `POST .../classCurriculum/search`、`.../courseCurriculum/search` | 479 班 / 2177 门 |
| `GET /student/courseSelect/thisSemesterCurriculum/index/queryExperiment` | 实验课安排（含实验室、周次、节次） |
| `GET /student/internship/sxbggl/findSxkb?xnxq=` | 实习排课（12-07~12-11 等） |
| `GET /student/internship/sxgcgl/findXszbs` | 实习周报列表 |
| `GET /student/menu-recommend/page` | 首页推荐卡片 |

---

## 6.1 教室课表检索（`classroomCurriculum/search`）—— 参数研究状态（2026-11-14）

> 票 03 要求「先探参数再写代码」。**这次没探成**：CDP 接管本机 Edge（`edge-automation-profile`）
> 打开 `/student/teachingResources/classroomCurriculum/index` 时被 302 到
> `https://pass.syau.edu.cn/tpass/login?service=http://10.2.0.47/login` —— 登录态已于 2026-11-13 之后过期。
> 所以下面把「已知」和「缺哪次实测」分开写清楚，App 侧按「已知部分」实现并**明确标注暂缺**。

### 已知（2026-11-13 实测，来源 `docs/_probe/urp/pages.json`）

| 项 | 值 |
|---|---|
| 端点 | `POST /student/teachingResources/classroomCurriculum/search`（返回 10702B） |
| 请求头 | `application/x-www-form-urlencoded; charset=UTF-8`（页面 XHR，带 `X-Requested-With`） |
| 响应外层 | `[{"xqzs":"21","pageData":{"pageContext":{"totalCount":295},"pageNum":1,"pageSize":30,"records":[…]}}]` |
| 记录字段（样本里看得到的） | `campusName`、`classNumberOfSeats`（浮点，106.0）、`classroomName`、`classroomTypeDirections`、`departmentName`、`executiveEducationPlanName` |
| 页面查询条件 | 学年学期（下拉，默认 `2026-2027学年秋`）、校区（全部/海城基地/熊岳校区/主校/路南/研究生部）、教学楼（全部/…）、教室名称（全部/…） |
| 页面表格列 | 序号、课表信息查看、教室信息查看、校区名、教学楼名、教室号、教室名、上课座位数、所属院系、教室类型 |
| `xqzs` | 样本值 `"21"`，含义不明 —— **不要用**（猜它等于校区号会错） |

### 还没测到（票 03 的核心未知）

1. **过滤字段名**：`classroomName` / `teachingBuildingName` / `campusName` 这些名字能不能作表单字段被服务器认，
   未验证（同族接口里 `teacherNum` 传了也被忽略，说明不能想当然）。
2. **行内「课表信息查看」的路由**：这才是「这间教室今天被哪些课占着」的数据口，
   点一次就能在请求列表里看到；本次没点到。
3. 是否支持按**日期 / 周次 / 节次**过滤 —— 完全未知（这决定下钻能不能做「明天/后天」）。

### 补齐需要的那一次实测（脚本已写好）

```powershell
# 1) 在这个 profile 里手动登录一次 CAS（登录态会持久）：
Start-Process 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe' `
  -ArgumentList '--remote-debugging-port=19240 --user-data-dir="C:\Users\28759\.dsh\edge-automation-profile" --no-first-run'
# 2) 跑探针（会自动抓真实 POST 表单 + 行内「课表信息查看」的请求 + 弹层文本）：
& 'C:\Users\28759\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe' O:\dev\enroute-android\docs\_probe\urp\probe_classroom.js
```

产物：`probe_classroom.txt`（人读）、`classroom_reqs_initial.json`（首屏全部请求，含 search 的真实 body）、
`classroom_modal.txt`（行内弹层文本）、`classroom_page.html`（页面 DOM）。

### App 侧当前实现（降级版，2026-11-14）

- **不猜过滤字段**：只发 `executiveEducationPlanNumber` + `pageNum` + `pageSize`（后两个形态与教师课表同族），
  拿回整页后在**客户端**按「楼名 + 教室号/教室名」精确匹配（`UrpHomeApi.pickClassroom`，
  分三档、命中多条就返回 null，绝不把隔壁教室顶替上来）。
- 找得到 → 浮层显示教室档案（校区/教学楼/教室号/教室名/座位/楼层/类型/院系/学期）；
  找不到 → 「没查到这间教室的记录（翻过 30 / 共 295 间教室）」。
- **占用课表：明写「暂缺」**（`HomeModel.occupancyHint()`）—— 路由未探明之前，
  绝不摆一张空课表冒充「这间教室今天没人用」。
- 解析夹具 `app/src/test/resources/home/urp_classroom_search.json` 顶部带 `_note` 字段，
  标明「未实测，据已知返回形态 + 页面表头推断」，避免以后被当成学校契约。

---

## 6.5 培养方案：入口在哪、能不能下载（2026-11-13 实测）

**结论：URP 里只能"在线看"，没有下载口；官方 PDF 在教务处站群，但下载要过验证码。**

### ① URP 内部（结构化的那份）
| 入口 | 路径 | 能力 |
|---|---|---|
| 指导性教学计划 | `/student/integratedQuery/instructionPlanQuery/detail/index` | 在线看方案全量课程；**无导出/打印/下载按钮**（页面上只有一个"保存"= 保存查询条件） |
| 培养方案完成情况 | `/student/integratedQuery/planCompletion/index` | 服务端渲染 HTML，无 JSON |

实测：`instructionPlanQuery` 页 HTML 里出现的唯一路径是 `/student/studentCertificatePrinting/index`，
抓包期间也没有任何 export/print/download/xls/pdf 请求 → **确认无导出**。

### ② 学生证明打印里也没有培养方案
`/student/studentCertificatePrinting/index` 可选证明类型只有：
中文成绩单、英文成绩单、主修中文成绩单、主修英文成绩单、辅导员成绩单、辅修中文成绩单、
中文成绩单（无公章）、在读证明_中文、在读证明_英文。
**没有"培养方案"类文件**，想从这里导出方案是行不通的。

### ③ 官方 PDF：教务处站群（对外公开，但下载需验证码）
```
栏目：https://jwc.syau.edu.cn/rcpy/pyfa.htm          （人才培养 → 培养方案）
文章：{2024级本科人才培养方案}
      https://jwc.syau.edu.cn/content.jsp?urltype=news.NewsContentUrl&wbtreeid=1234&wbnewsid=9354
附件：2024级沈阳农业大学本科人才培养方案.pdf
      https://jwc.syau.edu.cn/system/_content/download.jsp?urltype=news.DownloadAttachUrl&owner=1584116291&wbfileid=11804018
      （另一份：2024沈阳农业大学本科人才培养方案（专升本联合培养）.pdf）
历年：2017/2018/2019/2020/2021/2022/2023/2024 级 + 2025 版，同级不同 wbnewsid
```
⚠️ **附件直链会返回"请输入验证码下载附件"的 CAPTCHA 页**（4 位图形码，`createimage.jsp`），
程序无法直接抓取。**所以 App 不要试图代理下载/缓存这个 PDF**，只能：
- 用系统浏览器打开**文章页**（公开可访问），让用户自己点附件并过验证码；或
- 直接给结构化数据（`instructionPlanQuery`），那才是和学生本人挂钩、且永远最新的那份。

### ④ 一般还能在哪里拿到
- **学院网站**：各学院"人才培养 → 培养方案"通常挂本院各专业 PDF（土地资源管理在土地与环境学院）
- **学院教务老师 / 辅导员**：印刷版与电子版都有
- **新生入学教育材料**、专业导论课讲义里常附方案摘要

## 7. 对「主页」的具体建议（按性价比排序）

| 优先级 | 卡片 | 数据来源 | 说明 |
|---|---|---|---|
| **P0** | 身份条 | `rollInfo` HTML | 张三 · 2024000000 · 24土管1 · 土地资源管理 · 2024级；右侧一寸照 |
| **P0** | 学分进度 | `instructionPlanQuery` | 106/165，横条 + 百分比；点开按必修/限修/通识拆 |
| **P0** | 成绩概览 | `allPassingScores` | 加权绩点（学校绩点算）+ 通过 13/13 门 + 不及格 0 门 |
| **P1** | 下一场考试 | `examPlan` | 「11-15 形势与政策Ⅴ · 线上考场 · 2 天后」倒计时 |
| **P1** | 待办提醒 | `evaluation/queryAll` | 「18 份教学评估未完成」→ 红点（不评教通常影响查分/选课） |
| **P1** | 空闲教室 | `freeClassroom/today` 两步流 | 「现在哪间教室空着」——自习刚需。**先 POST 选楼、再 GET 节次**（会话粘滞，见 §3）。主人点名要做 |
| **P1** | 查老师课表 | `teacherCurriculum/search` | 1008 条教师课表；选课/找导师前查"这位老师什么时候在哪个教室"（主人点名要做） |
| **P2** | 本学期 | `ajaxStudentSchedule` | 8 门 · 12.25 学分 · 今日 0 节 |
| **P3** | 查班级/查课程 | `classCurriculum/search`、`courseCurriculum/search` | 选课季才有用 |

**不做的**：选课/退课/报名/评教提交（写操作，风险高且需要用户确认即时性）；个人信息修改；证明打印。

---

## 8. 工程注意

1. **token 现取现用**，不要缓存跨请求。
2. **写操作全部不碰**：`courseSelect`、`quitCourse`、`examSignUp`、`evaluation` 提交、`personalInfoUpdate`。
3. 这些接口全部**校内网**可达；公网/移动数据不通 → UI 必须给"连校园网"的可执行提示（现有 `Academic.kt` 已有这套失败文案）。
4. 只缓存**当前登录用户自己**的数据，不缓存他人课表（合规红线，见 `docs/SYAU-校内平台清单与可嵌入性评估.md`）。
5. 抓到的原始响应体在 `docs/_probe/urp/json_*.json`，可以当单测 fixture 用（注意：含真实学号姓名，**入仓前必须脱敏**）。

---

## 6.2 教师课表（**2026-11-14 实测，补齐票 03 遗留**）

「查教师 → 查看」走两步，页面上的按钮是
`onclick="searchCurriculumInfo('<planCode>','<teacherNum>','<planName>','<teacherName>')"`：

```http
① POST /student/teachingResources/teacherCurriculum/searchCurriculumInfoLo
   Content-Type: application/x-www-form-urlencoded
   teacherNum=1995500023&teacherName=<urlencoded>&planCode=2026-2027-1-1&planName=<urlencoded>
   → 200，只是回执：{"planName":"2026-2027学年秋","planCode":"…","teacherNum":"…","teacherName":"…"}

② GET /student/teachingResources/teacherCurriculum/searchCurriculumInfo/callback
        ?planCode=2026-2027-1-1&teacherNum=1995500023
   → 200，真正的课表数据（见下）
```

返回是**数组套数组**：`[[{…slot…}, {…}, …]]`（取 `[0]`）。

单条 slot 字段（实测样本）：

| 字段 | 含义 | 例 |
|---|---|---|
| `id.zxjxjhh` | 学期号 | `2026-2027-1-1` |
| `id.jsh` / `jsm` / `xb` | 教师工号 / 姓名 / 性别 | `1995500023` / `董秀茹` / `女` |
| `id.kch` / `kxh` / `kcm` | 课程号 / 课序号 / 课程名 | `901040302` / `01` / `不动产估价` |
| `id.skzc` + `zcsm` | **24 位周次位图** + 人话周次 | `111010011111111000000000` / `1-3,5,8-15周上` |
| `skxq` | 星期（1=周一；注意既是 `id` 内数字也是外层字符串） | `2` |
| `skjc` / `cxjc` | 起始节次 / 连上几节 | `3` / `2` |
| `xqh`/`xqm`、`jxlh`/`jxlm`、`jash`/`jasm` | 校区、教学楼、教室（号+名） | `001`/`主校`、`2`/`第二教学楼`、`2-606`/`606` |
| `xf` | 学分 | `3` |
| `kslxdm`/`kslxmc` | 考核方式 | `02`/`常规考试（闭卷）`、`03`/`考查（不安排考试）` |
| `kkxsh`/`kkxsm` | 开课学院（号/名） | `104`/`土环` |
| `bjh` | 上课班级 | `24土管1,24土管2` |
| `xss` / `bkskyl` | 学生数 / 本科生源 | `62` / `36` |

配套 `POST /ajax/student/getSectionAndTime`（body `planNumber=&ff=f&xqh=<校区号>`）给节次作息时间。
样本落盘：`docs/_probe/urp/teacher_schedule_callback.json`、`teacher_schedule_lo.json`，
探针：`docs/_probe/urp/probe_teacher_schedule.js`、`probe_teacher_view2.js`。

**用途**：教师详情页的课表网格可直接用 `skzc` 位图 + `skxq`/`skjc`/`cxjc` 画，不需要解析文字周次。

### 6.2.1 App 侧实现（票 08，2026-11-14）

- 两步流在 `UrpHomeApi.fetchTeacherSchedule`：`teacherScheduleLoForm`（表单四字段，实测原名）
  → `teacherSchedulePath`（`planCode` + `teacherNum`，值先 URL 编码）→ `parseTeacherSchedule`。
- `planCode` = `HomeModel.currentTermCode()`（`2026-2027-1-1`）；
  `planName` = `HomeModel.termPlanName()`（`2026-2027学年秋`，只是回执字段，不参与定位）。
- 周次只认 `id.skzc` 位图（复用 `UrpClient.decodeWeekBitmap`），`zcsm` 原文仅作对照显示。
- 「没排课」与「没拿到」分开：`TeacherScheduleResult.sessionLost` 为 true（被踢回登录页 / 非 JSON）
  时界面说登录态/校园网问题；只有数组为空才说「这位老师本学期没有排课」。
- 单测夹具：`app/src/test/resources/home/urp_teacher_schedule.json`（= 本文样本原样）。

**官网公开资料**（票 05 的落地，代码在 `data/faculty/`）：17 院一院一配常量表 → 栏目页按姓名找详情链接
→ 多容器兜底抽正文 → 字段抽取 + 隐私清洗（手机号/QQ/私人邮箱抽取阶段就丢）→ 按工号缓存 TTL 30 天。
实现细节见 `docs/学院官网-教师公开信息源调研.md`；缓存文件在 `filesDir/faculty/<工号>.json`。

## 6.3 教室课表（**2026-11-14 补齐过滤参数**）

`POST /student/teachingResources/classroomCurriculum/search` 的真实表单（页面自己发的）：

```
executiveEducationPlanNumber=2026-2027-1-1&campusNumber=&teachingBuildingNumber=&classRoomNumber=&pageNum=1&pageSize=30
```

- **过滤按编码，不按名字**：`campusNumber` / `teachingBuildingNumber` / `classRoomNumber`
  （实测传 `classroomName=`、`teachingBuildingName=`、`campusName=` 一律被忽略，返回仍是全量 295 条）
- `pageSize=400` 可一次取回全部 295 间（105KB）
- 走廊仍缺：行内「课表信息查看」的占用路由尚未抓（**按教师课表的经验，很可能也是
  `searchCurriculumInfoLo` → `searchCurriculumInfo/callback` 同型**，待实测确认）
## 6.4 历年学期课表（**2026-11-14 实测**）

页面 `/student/courseSelect/calendarSemesterCurriculum/index` 的学期下拉是
`select[name="planCode"]`，选项由服务端按「最近 5 个学期」动态给出（实测：
`2026-2027-1-1`(当前) / `2025-2026-2-1` / `2025-2026-1-1` / `2024-2025-2-1` / `2024-2025-1-1`）。

切学期 = **发一个 POST 把 planCode 送进去，服务器按会话返回该学期数据**（与空闲教室同型的会话粘滞）：

```http
POST /student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback
Content-Type: application/x-www-form-urlencoded
&planCode=2024-2025-1-1          ← 注意开头那个 &（首字段为空），照抄即可

→ 200，42KB，**与「本学期课表」完全同构**：
  {"rwbgLists":[], "allUnits":24.05, "xkxx":[{ "901010216_02":{…} }], "csz":…, "jcsjbs":…, "dateList":[…]}
```

同一次切换还会连带三支（可选，按需）：
- `POST /ajax/student/getSectionAndTime`　body `planNumber=&ff=f&xqh=<校区号>`（节次作息）
- `GET /student/internship/sxbggl/findSxkb?xnxq=<planCode>&flag=&_=<ts>`（实习课表）
- `POST /student/courseSelect/thisSemesterCurriculum/index/queryExperiment`　body `zxjxjhh=<planCode>`（实验课）

⚠️ **`GET ...callback?xnxq=<学期>` 参数无效**（实测返回的仍是当前学期，22KB 固定长度）；
`GET index?planCode=<学期>` 也无效（页面恒显示当前学期）。**必须 POST。**

**用途**：App 里「历年课表」可以直接复用现有 `UrpClient.parseSchedule`（schema 相同），
只需把 planCode 换成目标学期；样本落盘 `docs/_probe/urp/calendar_schedule_after_switch.json`，
探针 `docs/_probe/urp/probe_calendar3.js`。