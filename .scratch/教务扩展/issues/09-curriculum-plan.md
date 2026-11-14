# 09: 培养方案（内置数据，按登录年级显示）

**What to build:** 学生登录后进入「培养方案」，App 按 TA 的**年级**（2024级/2025级）挑对应版本、
按**专业**定位到本人那份方案，显示：专业介绍、培养目标、毕业要求、主干学科、专业核心课程、
修业年限/授予学位、**学分结构表（最低毕业学分只认这张表）**、各学期学分分配、课程设置与教学进程。

**Blocked by:** 无（资产已生成）

**Status:** ready-for-human

---

## 已就绪的资产（不用自己提取 PDF）

`app/src/main/assets/curriculum/`，由 `tools/curriculum/pack.py` 生成（可复现）：

| 文件 | 内容 |
|---|---|
| `plan_index.json` | `{"2024":[{id,major,type,bytes,courses}], "2025":[…]}` |
| `plan_2024_<id>.json` | 单个专业的培养方案（55 个专业） |
| `plan_2025_<id>.json` | 同上（46 个专业） |
| `META.json` | 生成时间、来源 PDF、**诚实规则清单** |

单文件最大 62KB，按需读一份即可，不要预加载全部。

## 关键数据诚实规则（`META.json` 里有原文，必须遵守）

1. **学分合计 ≠ 课程行求和**：方案把各选修模块的全部候选课都列出，学生只修其中一部分。
   「最低毕业学分」**只读 `学分结构表`**，禁止用 `课程设置与教学进程` 求和后显示成"应修 X 学分"。
2. 课程性质空串 = 原文为空，不要补猜。
3. 同一课程代码在不同专业/版本学分学时可能不同，按专业取用。

## 交互

- 入口：`UserPage` 新增一页 `培养方案`（从「我的」进入），与「考试安排/成绩查询」同级
- 顶部：`2024级 · 土地资源管理`，右侧可**切换年级**（2024/2025）与**切换专业**（默认定位本人专业）
- 内容长，用分区折叠或分 tab：`专业介绍 / 培养目标与毕业要求 / 学分结构 / 课程设置`
- 课程设置与教学进程：按 `开课学期` 分组，每行显示 课程代码·课程名·学分·学时·课程性质；
  点击某门课 → **跳到票 10 的课程大纲页**（若该课有大纲），无大纲时给明确提示
- 数据来源标注：底部一行「数据来自 2024/2025 级本科人才培养方案（教务处 PDF，共 695/693 页）」

## 验收标准

- [ ] 登录用户是 2024 级时默认打开 2024 版，2025 级默认 2025 版；能手动切换
- [ ] 默认专业按登录信息（`StudentProfile.major`）自动定位，定位不到时不报错、让人手动选
- [ ] 学分结构表按原表渲染（保留行列），**不出现"课程行求和"得到的数字**
- [ ] 课程设置按学期分组，行内显示学分/学时/性质
- [ ] 点某门课能跳到大纲页（票 10），没有大纲时明确说「这本院级大纲册子里没有这门课」
- [ ] 单测：资产索引解析、按年级/专业选文件、学分结构表解析（用真实资产做夹具）
- [ ] 全量单测绿（基线 **238 条 / 17 类 / 0 失败**），`assembleDebug` 成功；**APK 增量控制在 2MB 内**
- [ ] 资产读取在 IO 线程 + 有加载态，不许在主线程解析

## 边界

- 只读本地资产，不打网络
- 不动 `ui/feed/`、`ui/timetable/`、`RoomsScreen.kt`、`TeacherScreen.kt`
- 写盘范围：`ui/academic/`（或新建 `ui/curriculum/`）、`data/curriculum/`、`AppState`、`TimetableViewModel`、`ui/user/UserScreen.kt`、测试

## Comments

### 2026-11-14 交付（agent）

**一条实测发现（写进了代码注释和单测，值得记录）**：我把 101 份方案的「课程行学分求和」与
「学分结构表·总学分」全部对了一遍 —— **有总学分行的那 82 份，82/82 的求和都高于总学分**
（最多高 52 学分：园林 217 vs 165、水土保持与荒漠化防治 226 vs 175）。另有 **18 份没有「总学分」行**、
**1 份整张学分结构表为空**（电子信息工程 2024 的表格只抽到 6 行）。所以「只能读表、不能求和」
不是口头纪律，是这 101 份数据里处处会踩的坑；缺行时界面必须说「暂缺」。

**改了什么**

- `data/curriculum/CurriculumModels.kt`：`PlanIndexEntry` 18 / `PlanIndex` 28（`years`/`majors`）/
  `CurriculumCourse` 43 / `PlanPoint` 64 / `PlanRequirement` 75（`text` 把 PDF 截断的「标题+内容」接起来）/
  `CurriculumPlan` 90（`creditTable` 保留**原表行列**、`pageCount`、`notes`）。
- `data/curriculum/CurriculumParse.kt`（全纯函数）：`index` 33、`fileName` 54（与 pack.py 命名约定一致）、
  `pickYear` 62（`2024级`→2024；年级缺/未收录→回落最新年份）、`pickPlan` 77（精确→去「专业」→包含；
  多条优先「普通班」；**匹配不到返回 null**）、`plan` 93、`minCredits` 150（**只认学分结构表的「总学分」行**）、
  `creditTableMissing` 157、`coursesByTerm` 164（一…八排序，无学期归「未标注学期」排最后）、
  `termLabel` 181、`tidy` 196（PDF 断行落在词中间时接回去，句末标点后保留换行）。
- `data/curriculum/CurriculumAssets.kt`：`index` 25 / `plan` 29 按需读单文件；`hasSyllabus` 41（票 10 入口判断，
  依据 `syllabus_index.json` 的 167 个课程代码）；**主线程守卫**（在主线程调用直接记日志并返回 null）。
- `data/curriculum/CurriculumMeta.kt`：`totalPages` 16（695/693 来自 META.json）、`sourceLine` 24（底部来源行）。
- `ui/curriculum/CurriculumScreen.kt`（新文件）：`CurriculumScreen` 63 —— 头顶「2024 级 · 土地资源管理」+
  「换专业」面板 + 版本年份 chips + 培养类型 chips + 四个分区（`introItems` 235 / `goalItems` 281 /
  `creditItems` 299 / `courseItems` 346）；`TableBlock` 493 按原表行列渲染（7 列横向可滚，不拆不并）；
  `CourseRow` 537 点击查大纲（票 10 入口）。
- `ui/AppState.kt` 135 起：11 个培养方案字段；`ui/TimetableViewModel.kt`：`curriculum` 懒加载 205、
  `openCurriculum` 213（IO 线程读索引+本人专业）、`selectPlanYear` 265、`selectPlanMajor` 276、
  `loadPlan` 283、`requestSyllabus` 306（只判断「册子里有没有」，不伪造大纲）。
- `ui/user/UserScreen.kt` 65/106/113/167/298：「我的」→「培养方案」同级入口，走既有 `stack` 导航。
- 测试：新文件 `CurriculumParseTest`（17 条）+ 夹具 `app/src/test/resources/curriculum/`
  （`plan_index.json`、`plan_2024_tudi.json`=土地资源管理、`plan_2025_tudi.json`、`plan_2024_dzxx.json`=电子信息工程；
  内容与资产逐字节相同，只是把中文文件名换成 ASCII 以免平台差异）。

**怎么验证**

- 先单独 `:app:compileDebugUnitTestKotlin` → 绿；再 `build-test.bat`：`BUILD SUCCESSFUL` / `EXIT=0`
  （`:app:testDebugUnitTest` + `:app:assembleDebug`）。
- 单测：**282 条 / 20 类 / 0 失败**（基线 266/19，本轮 +16 条 / +1 类）。
- 关键断言：索引 55+46 条；`pickYear` 四种情形；`pickPlan` 定位不到返回 null；
  `minCredits(土地资源管理 2024) == "165"` 且**反向断言**「课程行求和 184.5 > 165」；
  电子信息工程（无总学分行）→ `minCredits == ""` 且 `creditTableMissing == false`（界面说「表里没有总学分行」）；
  整表缺失 → `creditTableMissing == true`；课程性质空串保持空串；`tidy` 断词接回/句末保换行；
  分组按一…八且不丢课程；来源行含「共 695 页」「第 223–234 页」「93 门课程」。
- **APK 体积**：22,124,997 → **22,349,554 字节（+0.21MB）**，远在 2MB 预算内。
  内置资产（270 个文件，原始 7.4MB / 压缩进包 1.86MB）是上一轮打 08 时就已在包内的，本轮只多了代码。

**遗留 / 只能主人做的**

1. 真机验证（`10AE1H1PYJ005ED` 本轮未连接）：进「我的 → 培养方案」应默认落在本人年级+专业；
   切 2024/2025 版、换专业、四个分区、点课程出一句「有/没有大纲」。
2. **票 10 入口已留**：`CurriculumAssets.hasSyllabus(code)` + `requestSyllabus` 现在只给一句话，
   票 10 落地时把 `requestSyllabus` 换成「压一页大纲详情」即可，页面不用改结构。
3. 专业定位用「精确 → 去『专业』后缀 → 互相包含」三级；学籍里若写的是**专业方向**（如「土地资源管理（某方向）」）
   可能定位不到，会落到「请手动选专业」——这是有意为之，没有做模糊评分。
4. 培养方案正文里 2025 版多出的「开课单位说明 / 课程替代情况说明」按原文整段展示，未做结构化。
5. 学分结构表按原表 7 列横向滚动渲染；若主人希望「竖排成标签+值」（更好读）我可以再改，但那就不再是原表行列了。
