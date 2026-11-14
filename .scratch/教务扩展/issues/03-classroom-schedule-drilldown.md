# 03: 从空闲教室下钻，看这间教室今天的课表

**What to build:** 学生在空闲教室列表里点某一间（例如「719 · 72 座」），能看到这间教室**今天/明天/后天**
被哪些课占用（课程名、节次、教师），从而判断「这间什么时候真正能去」。

**Blocked by:** 02（空闲教室两步流）

**Status:** ready-for-human

## 已知接口

`POST /student/teachingResources/classroomCurriculum/search`（实测 2026-11-13，返回 10702B）：

```jsonc
[{"xqzs":"21","pageData":{"pageContext":{"totalCount":295},"pageNum":1,"pageSize":30,
  "records":[{"campusName":"主校","classNumberOfSeats":106.0,"classroomName":"5楼2机房",
              "classroomTypeDirections":"机房", …}]}}]
```

- 返回是**数组套 pageData** 的形态，且带 `totalCount=295`（全校教室数），说明它是分页搜索接口
- ⚠️ **请求参数形态尚未实测**（哪些字段能按教室/楼/日期过滤）。开工前先在 PC 登录态下
  用 `docs/_probe/urp/` 里的探针脚本摸清参数，再写代码；证据存进 `docs/_probe/urp/`

## 要做的事

- 先探参数（研究步骤），把实测结论补进 `docs/URP-学生端接口清单.md`
- 教室卡片可点 → 详情浮层显示该教室占用情况；查不到就明确说「没查到这间教室的占用记录」
- 与票 02 的两步流复用同一会话，不要另起一套 cookie 处理

## 验收标准

- [ ] 校园网下点某间空教室，能看到该教室的课表/占用（或明确的「查不到」）
- [ ] 参数形态与返回样本有实测记录（日期 + 请求体 + payload 位置）
- [ ] 解析逻辑有 JVM 单测锁 schema
- [ ] 全量单测通过，`assembleDebug` 成功

## 边界

- 只读
- 写盘范围：`data/UrpHomeApi.kt`、`ui/home/RoomsScreen.kt`、`docs/URP-学生端接口清单.md`、测试、`docs/_probe/urp/`

## Comments

### 2026-11-14 交付（agent，降级版：参数未实测）

**先说要紧的：这张票的「占用课表」没做成，原因是缺一次登录态实测，不是没做。**

`POST /student/teachingResources/classroomCurriculum/search` 的**过滤参数仍然没实测到**。
2026-11-14 10:12 用 CDP 接管本机 Edge（`edge-automation-profile`，19240）打开
`/student/teachingResources/classroomCurriculum/index`，被 302 到
`https://pass.syau.edu.cn/tpass/login?service=http://10.2.0.47/login` —— 昨天那份 CAS 登录态已过期。
按指示没有卡住等登录，改为降级交付（证据：`docs/_probe/urp/probe_classroom.txt`、
`docs/_probe/urp/classroom_page.html` 就是那张登录页）。

**参数研究结论（探到哪算哪，已补进 `docs/URP-学生端接口清单.md` §6.1）**

- 已知：端点 / 请求头 / 响应外层 `[{xqzs, pageData:{pageContext:{totalCount:295},pageNum,pageSize,records}}]` /
  记录字段（`campusName`、`classNumberOfSeats` 浮点、`classroomName`、`classroomTypeDirections`、
  `departmentName`、`executiveEducationPlanName`）/ 页面查询条件（学年学期、校区、教学楼、教室名称）/
  表格列（含「课表信息查看」「教室信息查看」两个行内链接）/ `xqzs` 含义不明，**不用它**。
- 仍缺：① 过滤字段名能不能被服务器认；② 行内「课表信息查看」的路由（这才是占用数据口）；
  ③ 能不能按日期/周次/节次过滤。三条都写进了 §6.1，并附上补齐用的命令。

**改了什么**

- `app/src/main/java/cn/dkarbon/enroute/data/UrpHomeApi.kt`
  - 45 行 `CLASSROOM_SEARCH` 常量；190 行 `ClassroomInfo`、208 行 `ClassroomPage`、221 行 `ClassroomLookup`。
  - 379 行 `findClassroom()`：**不猜过滤字段**，只发 `executiveEducationPlanNumber + pageNum + pageSize`，
    翻页拿回后在客户端匹配；翻了多少（`scanned`）/库里多少（`totalCount`）/是否被踢回登录页（`sessionLost`）
    全部回传，不把「没拿到数据」说成「没有这间教室」。
  - 425 行 `classroomSearchForm()`（纯函数，夹住页码/页大小）、698 行 `parseClassroomSearch()`
    （非 JSON / 被踢回登录页一律返回空页，不抛）、748 行 `pickClassroom()`
    （三档先严后宽：楼名+教室号 → 楼名+教室名 → 楼名+教室名以房号结尾且前一位非数字；**命中多条返回 null**，
    且**不跨楼认领同一房号**）。
  - `pathPreview()` 增加「教室档案」一行，写明过滤字段未实测。
- `app/src/main/java/cn/dkarbon/enroute/ui/home/HomeModel.kt`
  - 266 行 `roomDetailSubtitle()`、283 行 `occupancyHint()`（占用那段固定说「暂缺 + 为什么 + 以现场为准」）、
    291 行 `classroomMissHint()`（查不到时交代「翻过 30 / 共 295 间教室」）。
- `app/src/main/java/cn/dkarbon/enroute/ui/home/RoomsScreen.kt`
  - 61 行 `RoomsScreen` 改成 `Box` 叠加结构（与考试页同一套，**不用 Dialog**：AcademicSheet 内部有 BackHandler）；
    81 行抽出 `RoomsBody`（原列表本体，内容未动）；240 行 `RoomDetailSheet`（浮层：校区/教学楼/教室号/
    教室名/座位/楼层/类型/院系/学期 + 「这间教室的占用」暂缺说明 + 查询状态）；
    325 行 `RoomCell` 变可点（`clickable(onClickLabel = "查看教室 719")`）。
- `app/src/main/java/cn/dkarbon/enroute/ui/AppState.kt` 90 行起：`roomDetailRoom/Loading/Info/Msg/Total/Scanned` 六个字段。
- `app/src/main/java/cn/dkarbon/enroute/ui/TimetableViewModel.kt`
  - 536 行 `openRoomDetail()`、594 行 `closeRoomDetail()`：**复用票 02 的同一个 `session`**，
    学期号复用 `HomeModel.currentTermCode`，错误文案复用 `noSessionHint()` / `friendlyError()`。
- 测试：`HomeParseTest` +7（档案字段、非 JSON 容错、表单只带学期分页、教室号优先匹配、不跨楼认领、
  房号尾巴不误匹配、同档多条则不给）；`HomeModelTest` +3（副标题缺项、占用必须写暂缺且**断言不出现**
  「没有课」这类措辞、查不到时的覆盖面交代）。
- 夹具：`app/src/test/resources/home/urp_classroom_search.json`，**顶层 `_note` 明确标注「未实测」**，
  只有「第一教学楼 2机房 / 5楼2机房 / 106 / 信电 / 机房」这一行来自页面真实表头。
- 探针脚本：`docs/_probe/urp/probe_classroom.js`（本次写好并跑过；登录态有效时会自动抓
  ① 页面首屏 search 的**真实 POST body** ② 行内「课表信息查看」的请求 ③ 弹层文本）。

**怎么验证**

- `build-test.bat`：`BUILD SUCCESSFUL in 31s`，`EXIT=0`（`:app:testDebugUnitTest` + `:app:assembleDebug`）。
- 单测：**238 条 / 17 类 / 0 失败 0 错误**（接手时基线 228，本轮 +10）。
- 交付前先单独跑过 `:app:compileDebugUnitTestKotlin`（吸取昨天 `HomeModelTest` 枚举写法的教训）。

**遗留 / 只能主人做的**

1. **一次 CAS 登录**（在这个 Edge profile 里手动登录一次），再跑
   `node docs\_probe\urp\probe_classroom.js`，就能拿到 search 的真实请求体 + 行内「课表信息查看」的路由；
   拿到后本票才能从「档案」升级成真正的「占用课表」。
2. 在校园网下点某间空教室，确认浮层里的档案字段（座位/类型/院系/学期）跟页面一致；
   若「没查到这间教室」，看浮层里「翻过 N / 共 295 间」判断是页没翻够还是教室没录库。
3. 真机 `10AE1H1PYJ005ED` 本轮未连接，UI 只做了编译级验证，没跑真机。

**验收标准对照**

- [x] 参数形态与返回样本有记录（但**标注为未实测**，见 §6.1 —— 这条严格说是半完成）
- [x] 解析逻辑有 JVM 单测锁 schema（夹具已标注「未实测，据已知形态推断」）
- [x] 全量单测通过，`assembleDebug` 成功
- [ ] 校园网下点某间空教室看到占用 —— **未达成**（占用路由未探明，浮层如实显示「暂缺」，
      不存在假装查到的空课表）
