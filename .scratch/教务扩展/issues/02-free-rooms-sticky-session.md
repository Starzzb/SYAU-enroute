# 02: 空闲教室真的能显示（补上「先 POST 选楼」这一步）

**What to build:** 学生选校区 → 选教学楼 → 选节次（或今天/明天/后天），页面列出该楼该节次的
空闲教室，含房号与座位数。现在无论怎么选都是「这一节没有空闲教室」。

**Blocked by:** None (can start immediately)

**Status:** ready-for-human

## 已定位的根因（实验数据，不要重复调查）

接口是**会话粘滞**的：`GET /student/teachingResources/freeClassroom/today/<节次>?dayplus=N`
只返回「本会话上一次 POST 选中的那栋楼」的空教室。App 现在只发 GET、从不 POST，
会话里没选中任何楼 → 恒空。

PC 登录态对照实验（`docs/_probe/urp/free_sticky.js`，2026-11-13）：

| 做法 | 结果 |
|---|---|
| 只 `GET /today/11?dayplus=0` | 空（len=37） |
| 先 `POST position=001_1&xqm=主校` 再 GET `/today/11` | 第一教学楼 **74 间** |
| 先 `POST position=001_6` 再 GET `/today/11` | 空（水利楼该节次确实没空教室） |
| 先 `POST position=001_2` 再 GET `/today/11` | 第二教学楼 **67 间** |
| 先 `POST position=001_2` 再 GET `/today/3` | 第二教学楼 **7 间** |
| `GET /today/11?dayplus=0&position=001_2`（参数当查询串传） | 空（**参数被忽略**） |

POST 形态：`application/x-www-form-urlencoded`，字段 `position=<校区号>_<楼号>`、`xqm=<校区名>`。

## 要做的事

- `fetchFreeRooms` 改成两步：先 POST 选楼，再 GET 节次；两步必须紧挨着（同一会话）
- 节次 1..12 与「今天/明天/后天」的映射保持现有交互
- 空结果要能区分三种情况：**真的没空教室** / 会话过期或不在校园网 / 楼没选上
- 同步更正 `docs/URP-学生端接口清单.md` §3 —— 那里现在写的是「纯 JSON 三支接口搞定」，
  漏了会话粘滞这个前提，属于文档错误

## 验收标准

- [ ] 校园网下选「主校 → 第二教学楼 → 第 11 节」能看到教室与座位数
- [ ] 换楼、换节次、换日期都能刷新出不同结果
- [ ] 移动数据下给出可执行提示（连校园网 / 重新登录），不显示误导性空态
- [ ] 两步流的解析有 JVM 单测（用 `docs/_probe/urp/free_*.json` 做夹具）
- [ ] 文档 §3 已更正会话粘滞说明
- [ ] 全量单测通过，`assembleDebug` 成功

## 边界

- 只读
- 不要顺手做「点教室看它的课表」——票 03
- 写盘范围：`data/UrpHomeApi.kt`、`ui/home/RoomsScreen.kt`、`ui/TimetableViewModel.kt`、
  `docs/URP-学生端接口清单.md`、对应测试

## Comments

### 2026-11-14 交付 + lead 复验

**改了什么**（代理实现，lead 复验）

- `data/UrpHomeApi.kt`
  - `fetchFreeRooms` 改为两步：`postForm(FREE_ROOMS, freeRoomsPickForm(...))` → `get(freeRoomsPath(...))`
  - 新增纯函数 `freeRoomsPickForm`（`position=<校区号>_<楼号>`、`xqm=<校区名>`）、
    `freeRoomsPath`（节次夹 1..12、dayplus 夹 0..2）、`judgeFreeRooms`、`looksLikeLoginPage`
  - 新增 `FreeRoomsIssue`（`OK / NOTHING_FREE / NO_SESSION / PICK_LOST`）与 `FreeBuilding.number`，
    用于核对「服务器答的是不是我选的那栋楼」
- `ui/home/RoomsScreen.kt:172` 空态改用 `HomeModel.freeRoomsEmptyHint(ui.freeRooms.issue, buildingName)`
- `ui/home/HomeModel.kt` 新增 `freeRoomsEmptyHint(issue, buildingName = "")`，三种原因三种说法
- 测试：`HomeParseTest` / `HomeModelTest` 增加两步流与三态空态用例

**lead 复验时修掉的一处**

- `HomeModelTest.kt:213` 用了 `val issue = …FreeRoomsIssue` 再 `issue.NOTHING_FREE` ——
  Kotlin 里枚举类不是值，导致 `compileDebugUnitTestKotlin FAILED`（4 处 `Unresolved reference`）。
  已改为导入 `FreeRoomsIssue` 并直接引用常量。

**验证**

- `build-test.bat`：`BUILD SUCCESSFUL`，`EXIT=0`
- 单测：**195 条 / 14 类 / 0 失败**（基线 185，票 02 +10）
- 改前实测证据见票首对照表与 `docs/_probe/urp/free_sticky.js`

**遗留（只能主人在校园网下验）**

- 主校→第二教学楼→第 11 节应出 67 间；换楼/换节次/换明天后天结果要变
- 移动数据下应显示 `NO_SESSION` 文案（「没拿到数据（不是没有空教室）…连校园网」），不得显示空态