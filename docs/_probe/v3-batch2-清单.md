# v3 batch2 自查清单

> 执行：2026-11-15　构建：`build-verify.bat`（testDebugUnitTest + assembleRelease）

## 1. 对比度

- 文档 `docs/设计-对比度实测.md`：**已生成**（88 行，2026-11-15 11:42）。
- 关键配对数：HARD/账页 × 明/暗 共 **4 套主题状态 × 5 配对 = 20 对**，实测表全部 ✅（正文 ≥4.5、次要/三级文字 ≥4.5、rule/outline ≥3.0）。
- 佐证：单测 `ContrastAuditTest.kt` 存在，本次全量单测 561 条全绿（含该测试），同口径明细另写 `app/build/reports/contrast-audit.md`。

## 2. 账页 → 笺

- 用户可见文案：**已全部改「笺」**。主题选择项用 `StyleSet.name(EDITORIAL) → "笺"`（ScholarTokens.kt:102），设置页说明用「笺＝没有格线…」（SettingsScreen.kt:106）。
- `app/src/main` 内剩余「账页」均在注释/KDoc（如 EditorialTokens、LedgerKit、UserLedger 的说明），**无一处是 UI 可见字符串**；strings.xml 无「账页」。
- 仅测试断言消息里还有「账页」（CustomPaletteTest.kt:78，测试内部文本，不上屏）。

## 3. 我的页

- 功能入口全部有归属 box（UserScreen.kt）：
  - 教务 box（6）：考试安排 / 成绩查询 / 查教师 / 课程大纲 / 历年课表 / 空闲教室
  - 校园 box（1）：校园地图
  - 本机 box（4）：自定义课程 / 周次校准 / 设置 / 关于
- 身份/学籍（1 行）与学业（4 行：加权绩点/已修学分/排名/培养方案）**按主人要求保持无盒**（身份=单行门禁，学业=数值账目行，UserLedger.kt:248）。
- 入口无丢失：UserPage 枚举 14 个子页（Exam/Score/Curriculum/Syllabus/History/CampusMap/CustomCourses/Rooms/Teacher/TeacherDetail/Settings/About/Debug/Login）均可到达；设置入口同时保留在「本机」box 与页头 ⋯ 菜单（刷新+设置，UserHeaderMenu）。

## 4. 培养方案表格四件事（CurriculumScreen.kt TableBlock）

- 列宽固定：单一 `columnWidth(j, numeric)` 映射（数字 56dp / 首列 88dp / 其余 72dp），表头与数据同一套。
- 换行：`softWrap = true`，长文最多 3 行再省略（表头放宽到 4 行），`TextOverflow.Ellipsis`。
- 横向滚动：`horizontalScroll(rememberScrollState())`，表头随表一起滚。
- 数字右对齐：`isNumericColumn` 判定（`^\d+(\.\d+)?%?$`，— 视为空），`TextAlign.End`；文字列 `TextAlign.Start`。
- 空值原样（空格子=原表为空，绝不填 0）。

## 5. 版本号一致性

- `app/build.gradle.kts`：versionCode **210**，versionName **"0.3.0+m20"**。
- `releases/latest.json`：versionCode **210**，versionName **"0.3.0+m20"**，url `download/m20/app-release.apk`。
- 其它（README 版本表、CHANGELOG 顶部 SemVer 示例、MapContributeTest 夹具、docs/校园地图-补丁服务部署.md 示例 payload）均已同步到 0.3.0+m20。
- 注：历史记录里的 m19 字样（CHANGELOG m19 条目、m19-交付说明、评审-代码审查-m19、真机验收-m19.1 等）**保留不动**，属里程碑档案，不是当前版本引用。
