> ⚠️ **本文档已作废（2026-11-15，主人指令）**：前端设计纪律全部废除，不再遵守。
> 主人判定按这些规范做出来的前端不合格，先拆纪律、再重新设计、重新验证。
> 保留仅作历史参考，**不得作为新设计的约束依据**。

# 设计风格 token 审计：视觉 token 唯一出口核查

> 审计日期：2026-11-14　范围：`app/src/main/java/cn/dkarbon/enroute`（116 个 .kt）　
> 口径：只报事实。`✅`=走了 token；`❌`=裸值绕过 token；`缺失`=token 体系里没有对应档位。

## 0. 结论速览（token 是否有唯一出口）

**没有唯一出口。** 仓库里并存 4 套形态/间距 token（`InkTokenSet` 颜色、`Space` 间距、`Stroke` 线宽、`FormSpec` 形态），
同时大量调用点直接写数字裸值。`MaterialTheme` 里挂载的 `EnRouteTypography` / `EditorialTypography` 与 `HardShapes` / `EditorialShapes`
**从未被消费**：全仓 `MaterialTheme.typography` 引用 0 处、`MaterialTheme.shapes` 引用 0 处、`MaterialTheme.colorScheme` 引用 1 处（TimetableScreen.kt:1298）。
即：字号/行高/圆角三套 token 全部是死 token，实际渲染值散落在各文件的裸字面量里。

| 类别 | 裸值处数 | 备注 |
|---|---|---|
| 圆角 RoundedCornerShape | 21 个调用点，其中 16 处纯裸值 + 2 处部分裸值 | 999.dp 出现 7 处 |
| 间距 padding/Spacer 非标准值 | 294 行 | 标准 5 级 = 4/8/12/16/24 |
| 字阶 fontSize/lineHeight 裸值 | 748 行（fontSize 634 行、lineHeight 148 行） | MaterialTheme.typography 0 引用 |
| 线宽 border/Divider 裸厚度 | 28 行 | 另有大量走 Stroke.hairline/strong/emphasis token |
| 海拔 shadowElevation/tonalElevation | 0 处 | 阴影走 FormSpec.cardFloat（3 处）与未使用的 hardShadow |

## 1. 圆角

**token 定义（参考）**：`HardShapes` = 0/0/0/0/0；`EditorialShapes` = extraSmall 6 / small 6 / medium 10 / large 12 / extraLarge 14；
`FormSpec.radiusCard` = 0(Hard)/10(Ledger)、`FormSpec.radiusCourseCard` = 4(Hard)/10(Ledger)、`FormSpec.radiusSheet` = 0/14、`FormSpec.pillSize` = 20。

| 文件:行号 | 当前裸值 | 是否走 token / 应归属的 token |
|---|---|---|
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:631 | 999.dp / 0.dp | ❌ 裸值；999 缺失（整圆档无 token）；0 应 HardShapes.extraSmall |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:904 | 0.dp（else 分支） | ⚠️ 部分：ed 走 FormSpec.radiusCard，else 裸 0 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:938 | 6.dp | ❌ 裸值；应 EditorialShapes.small（HARD 无 6 档） |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:940 | 6.dp / 0.dp | ❌ 裸值；同上 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1063 | 0.dp（else 分支） | ⚠️ 部分：ed 走 FormSpec.radiusCard，else 裸 0 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:236 | 0.dp | ❌ 裸值；应 HardShapes.extraSmall |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:275 | 0.dp | ❌ 裸值；应 HardShapes.extraSmall |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:354 | 0.dp | ❌ 裸值；应 HardShapes.extraSmall |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:280 | 999.dp | ❌ 裸值；缺失（整圆档） |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:190 | 999.dp | ❌ 裸值；缺失（整圆档） |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:255 | 999.dp | ❌ 裸值；缺失（整圆档） |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:257 | 999.dp | ❌ 裸值；缺失（整圆档） |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:277 | 999.dp | ❌ 裸值；缺失（整圆档） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\ErodedCard.kt:54 | 4.dp | ❌ 裸值；应 FormSpec.radiusCourseCard=4（Shapes 无 4 档） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:73 | — | ✅ 走 token：f.radiusCourseCard |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:178 | — | ✅ 走 token：f.radiusCourseCard |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:225 | — | ✅ 走 token：f.pillSize/2 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:300 | 12.dp | ❌ 裸值；应 EditorialShapes.large（HARD 无 12 档） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:317 | 2.dp | ❌ 裸值；缺失（Shapes 无 2 档） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:352 | 999.dp | ❌ 裸值；缺失（整圆档） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:920 | 4.dp | ❌ 裸值；应 FormSpec.radiusCourseCard=4（Shapes 无 4 档） |

## 2. 间距（padding / Spacer 中 4/8/12/16/24 之外的数字）

**token 定义（参考）**：`Space` = xxs 2 / xs 4 / s 8 / m 12 / l 16 / xl 20 / xxl 24 / huge 32 / gutter 16 / cardPad 16 / cardGap 12 / appBar 56 / bottomBar 80 / minTouch 48 / timeColumn 44。
下表只列「padding/Spacer 参数里出现 4/8/12/16/24 之外数字」的行。

| 文件:行号 | 当前裸值 | 应归属的 token |
|---|---|---|
| app\src\main\java\cn\dkarbon\enroute\ui\EnRouteRoot.kt:201 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:93 | 32 | Space.huge |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:98 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:145 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:189 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:245 | 18 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:146 | 6 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:169 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:225 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:231 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:264 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:290 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:301 | 10 / 6 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:325 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:333 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:422 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:459 | 20 | Space.xl |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:474 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:479 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:511 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:520 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:539 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:557 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:270 | 10 / 6 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:279 | 14 / 10 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:293 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:302 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:332 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:400 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:450 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:483 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:487 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:496 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:121 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:163 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:272 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:347 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:356 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:380 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:95 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:104 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:163 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:391 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:543 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:167 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:172 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:194 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:81 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:105 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:178 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:180 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:198 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:272 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:299 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:307 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:321 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:339 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:97 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:128 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:142 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:215 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:217 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:449 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:459 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:504 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:541 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:558 | 10 / 6 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:566 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:186 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:209 | 28 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:243 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:252 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:261 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:292 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:350 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:441 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:911 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1068 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1201 | 56 | Space.appBar |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1357 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1358 | 27 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1396 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1452 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1479 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1573 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1708 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1711 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:126 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:147 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:218 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:301 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:382 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:393 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:269 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:271 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:287 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:333 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:75 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:79 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:204 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:250 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:251 | 34 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:263 | 1.5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:277 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:280 | 2 / 3 | Space.xxs / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:310 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:313 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:153 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:165 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:194 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:202 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:213 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:224 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:539 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:544 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:596 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:617 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:665 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:704 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:832 | 10 / 5 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:877 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:906 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:909 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:913 | 0.5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:930 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1097 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1116 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1138 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1143 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1172 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1177 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1193 | 10 / 6 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:127 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:129 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:141 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:155 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:164 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:168 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:173 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:227 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:240 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:304 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:323 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:327 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:329 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:337 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:358 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:388 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:394 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:396 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:402 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:410 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:84 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:88 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:126 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:129 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:159 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:169 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:176 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:189 | 6 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:251 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:288 | 13 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:89 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:103 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:111 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:122 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:136 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:162 | 10 / 5 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:203 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:229 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:249 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:255 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:279 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:298 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:302 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:308 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:330 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:345 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:350 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:367 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:372 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:406 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:415 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:420 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:449 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:458 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:501 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:504 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:516 | 30 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:534 | 20 | Space.xl |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:541 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:78 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:82 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:155 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:168 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:173 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:185 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:194 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:206 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:211 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:215 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:217 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:225 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:247 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:258 | 6 / 1 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:285 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:129 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:152 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:173 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:194 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:227 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:237 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:277 | 3.5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:319 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:323 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:376 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:118 | 14 / 10 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:130 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:216 | 14 / 13 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:225 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:233 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:133 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:231 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:252 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:131 | 3.5 / 3 / 2.5 / 2 | 缺失 / 缺失 / 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:185 | 3.5 / 3 / 2.5 | 缺失 / 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:190 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:240 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:305 | 14 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:314 | 10 / 9 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:318 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:358 | 7 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:509 | 5 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:526 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:537 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:681 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:812 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:880 | 2.5 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:890 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:993 | 14 / 6 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1004 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1037 | 14 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1041 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1045 | 9 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1082 | 14 / 10 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1089 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1099 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1145 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1168 | 7 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1173 | 5 / 1 | 缺失 / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1185 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1300 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1313 | 18 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1338 | 26 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1340 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1342 | 20 | Space.xl |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1348 | 18 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1387 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1391 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1412 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1420 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1481 | 1 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1484 | 2 / 0 | Space.xxs / 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:76 | 11 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:86 | 11 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:113 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:70 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:79 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:88 | 20 | Space.xl |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:115 | 20 | Space.xl |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:125 | 14 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:140 | 30 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:193 | 6 / 2 | 缺失 / Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:246 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:380 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:385 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:424 | 22 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:431 | 18 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:433 | 28 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:495 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:506 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:542 | 10 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:572 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:103 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:153 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:174 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:191 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:242 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:253 | 6 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:260 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:188 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:258 | 3 | 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:260 | 2 | Space.xxs |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:155 | 1 | 缺失 |

## 3. 字号与行高（fontSize / lineHeight 裸值，未走 MaterialTheme.typography）

**token 定义（参考）**：`EnRouteTypography`（HARD）= labelSmall 11/15、labelMedium 12/16、labelLarge 14/20、bodySmall 12/18、bodyMedium 14/21、bodyLarge 16/24、titleSmall 14/20、titleMedium 16/22、titleLarge 18/24、headlineSmall 20/26、headlineMedium 24/28；`LabelMicro` 9/12。
`EditorialTypography`（Ledger）= headlineLarge 30/36、headlineMedium 22/28、headlineSmall 21/27、titleLarge 17/24、titleMedium 15/21、titleSmall 13/19、bodyLarge 16/23、bodyMedium 14/20、bodySmall 13/19、labelLarge 13/18、labelMedium 11/16、labelSmall 10/14；`LedgerDisplay` 28/34、`LedgerNumeric` 12/16、`LedgerMicro` 9/12。
全仓 `MaterialTheme.typography` 引用 = 0 处；下表每一行都是未走 typography 的裸值。

| 文件:行号 | 当前裸值 | 应归属的 token |
|---|---|---|
| app\src\main\java\cn\dkarbon\enroute\ui\EnRouteRoot.kt:189 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\EnRouteRoot.kt:204 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:96 | fs 26 | fs 26 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:97 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:102 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:111 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:155 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:158 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:163 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:189 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:202 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:203 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:222 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:223 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:238 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:239 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:248 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:254 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:272 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\about\AboutScreen.kt:273 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:97 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:104 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:115 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:131 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:144 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:167 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:170 | fs 10；lh 15 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:173 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:229 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:232 | fs 10.5；lh 15 | fs 10.5 -> 缺失；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:239 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:265 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:268 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:269 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:291 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:303 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:316 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:317 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:328 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:334 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:337 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:397 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:427 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:461 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:470 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:471 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:483 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:484 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:510 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:516 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:517 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:536 | fs 10.5；lh 16 | fs 10.5 -> 缺失；lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:551 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:555 | fs 9.5；lh 14 | fs 9.5 -> 缺失；lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:558 | fs 9；lh 13 | fs 9 -> LabelMicro 9/12；lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:566 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\AcademicSheets.kt:567 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:122 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:123 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:283 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:287 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:296 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:303 | fs 11；lh 16 | fs 11 -> labelSmall 11/15；lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:327 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:335 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:341 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:347 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:407 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:414 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:455 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:466 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:476 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:484 | fs 11；lh 16 | fs 11 -> labelSmall 11/15；lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:490 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ExamScreen.kt:497 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:110 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:111 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:120 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:124 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:160 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:161 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:252 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:265 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:270 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:285 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:293 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:340 | fs 13.5 | fs 13.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:350 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:373 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:385 | fs 17 | fs 17 -> 缺失(HARD) / Editorial titleLarge 17/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\academic\ScoreScreen.kt:393 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:86 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:96 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:106 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:168 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:173 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:221 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:222 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:258 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:264 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:270 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:271 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:309 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:313 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:317 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:322 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:323 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:327 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:331 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:332 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:356 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:363 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:396 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:408 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:409 | lh 19 | lh 19 -> 缺失(HARD) / Editorial bodySmall 13/19 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:424 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:425 | fs 11；lh 18 | fs 11 -> labelSmall 11/15；lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:433 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:434 | fs 11；lh 18 | fs 11 -> labelSmall 11/15；lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:449 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:453 | fs 11；lh 18 | fs 11 -> labelSmall 11/15；lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:458 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:465 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:466 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:480 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:481 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:494 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:512 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:513 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:527 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:549 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:555 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:564 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:68 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:69 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:113 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:128 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:129 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:171 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:176 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:180 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:199 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:72 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:83 | fs 11；lh 17 | fs 11 -> labelSmall 11/15；lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:113 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:117 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:121 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:132 | fs 11；lh 17 | fs 11 -> labelSmall 11/15；lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:158 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:161 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:177 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:183 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:188 | fs 10.5；lh 16 | fs 10.5 -> 缺失；lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:208 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:213 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:219 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:220 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:226 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:227 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:266 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:267 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:273 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:274 | fs 10；lh 15 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:284 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:298 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:310 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:326 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:327 | lh 12 | lh 12 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:340 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:356 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:366 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSheet.kt:367 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:82 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:92 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:93 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:98 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:108 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:109 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:120 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:125 | fs 8 | fs 8 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:136 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:139 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:144 | fs 10；lh 15 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:156 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:157 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:162 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:166 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:181 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:185 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:186 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:214 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:216 | fs 10.5；lh 15 | fs 10.5 -> 缺失；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:218 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:265 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:292 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:318 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:327 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:349 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:373 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:424 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:453 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:460 | fs 9.5；lh 14 | fs 9.5 -> 缺失；lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:504 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:520 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:521 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:544 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:559 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:567 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:135 | fs 17 | fs 17 -> 缺失(HARD) / Editorial titleLarge 17/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:152 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:162 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:172 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:174 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:177 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:180 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:195 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:202 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:212 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:213 | lh 19 | lh 19 -> 缺失(HARD) / Editorial bodySmall 13/19 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:248 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:255 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:264 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:265 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:295 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:313 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:317 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:318 | lh 20 | lh 20 -> labelLarge 14/20 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:326 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:330 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:349 | fs 20 | fs 20 -> headlineSmall 20/26 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:357 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:362 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:364 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:366 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:373 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:383 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:399 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:433 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:439 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:444 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:453 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:473 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:501 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:533 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:542 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:550 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:580 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:589 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:596 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:607 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:659 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:669 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:705 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:714 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:727 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:826 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:836 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:842 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:876 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:945 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:957 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:965 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:969 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:986 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:987 | lh 21 | lh 21 -> bodyMedium 14/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:997 | fs 12；lh 18 | fs 12 -> labelMedium / bodySmall 12；lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1014 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1021 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1025 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1037 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1039 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1042 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1091 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1092 | lh 20 | lh 20 -> labelLarge 14/20 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1103 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1104 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1113 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1148 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1162 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1203 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1206 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1208 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1228 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1253 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1261 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1325 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1348 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1352 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1358 | fs 9.5；lh 13 | fs 9.5 -> 缺失；lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1385 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1390 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1399 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1451 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1453 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1478 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1480 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1487 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1488 | fs 11；lh 16 | fs 11 -> labelSmall 11/15；lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1531 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1544 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1552 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1569 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1572 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1574 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1579 | fs 20；lh 28 | fs 20 -> headlineSmall 20/26；lh 28 -> headlineMedium 24/28 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1609 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1610 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1639 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1647 | fs 14；lh 25 | fs 14 -> labelLarge / bodyMedium / titleSmall 14；lh 25 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1651 | fs 12；lh 19 | fs 12 -> labelMedium / bodySmall 12；lh 19 -> 缺失(HARD) / Editorial bodySmall 13/19 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1661 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1666 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1685 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1690 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1691 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1697 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1713 | fs 12；lh 18 | fs 12 -> labelMedium / bodySmall 12；lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1729 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1735 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:1747 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:71 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:76 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:118 | fs 18 | fs 18 -> titleLarge 18/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:125 | fs 17 | fs 17 -> 缺失(HARD) / Editorial titleLarge 17/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:134 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:150 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:153 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:196 | fs 26 | fs 26 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:209 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:218 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:238 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:246 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:275 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:278 | fs 26 | fs 26 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:286 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:289 | fs 20 | fs 20 -> headlineSmall 20/26 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:299 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:302 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:311 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:346 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:357 | fs 22 | fs 22 -> 缺失(HARD) / Editorial headlineMedium 22/28 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:368 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:378 | fs 13.5 | fs 13.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:386 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:394 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:414 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\HomeCards.kt:430 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:92 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:98 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:156 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:192 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:195 | lh 19 | lh 19 -> 缺失(HARD) / Editorial bodySmall 13/19 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:201 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:203 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:273 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:277 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:278 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:290 | fs 11；lh 17 | fs 11 -> labelSmall 11/15；lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:306 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:308 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:336 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:340 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:394 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:68 | fs 17 | fs 17 -> 缺失(HARD) / Editorial titleLarge 17/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:76 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:82 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:89 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:137 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:140 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:141 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:152 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:154 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:185 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:188 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:196 | fs 11；lh 17 | fs 11 -> labelSmall 11/15；lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:205 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:208 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:209 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:227 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:255 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:266 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:290 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:291 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:319 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:325 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:328 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:331 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherDetailScreen.kt:339 | fs 9.5；lh 14 | fs 9.5 -> 缺失；lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:67 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:68 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:83 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:91 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:107 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:109 | lh 19 | lh 19 -> 缺失(HARD) / Editorial bodySmall 13/19 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:114 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:159 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:166 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:172 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\TeacherScreen.kt:179 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:175 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:179 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:191 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:192 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:199 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:200 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:210 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:211 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:221 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:222 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:540 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:546 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:558 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:559 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:575 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:579 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:580 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:599 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:614 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:615 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:689 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:709 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:717 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:725 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:726 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:756 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:757 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:762 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:764 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:801 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:806 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:808 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:823 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:834 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:860 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:861 | lh 12 | lh 12 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:871 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:880 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:902 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:915 | fs 7.5 | fs 7.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:920 | fs 8 | fs 8 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:934 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:943 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:972 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:975 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:981 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:982 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:986 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:992 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:995 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1102 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1103 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1110 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1117 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1137 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1139 | fs 10.5；lh 15 | fs 10.5 -> 缺失；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1146 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1153 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1154 | fs 10；lh 15 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1163 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1164 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1174 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1179 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1195 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:126 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:180 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:187 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:188 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:195 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:207 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:226 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:245 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:297 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:326 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:332 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:342 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:363 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:379 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:385 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:386 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:391 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:392 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:405 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:406 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:413 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:414 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:418 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:87 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:91 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:92 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:121 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:122 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:160 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:166 | fs 9.5；lh 14 | fs 9.5 -> 缺失；lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:173 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:179 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:218 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:227 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:228 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:235 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:236 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:240 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:244 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:246 | fs 10；lh 14 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14；lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:252 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:259 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:260 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:265 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:293 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:316 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:322 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:323 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:326 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:331 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:332 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:335 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:340 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:341 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:346 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:352 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:355 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:93 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:107 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:108 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:126 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:143 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:166 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:182 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:202 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:206 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:213 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:228 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:233 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:238 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:254 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:258 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:259 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:278 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:282 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:283 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:288 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:295 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:296 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:307 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:313 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:314 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:319 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:333 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:334 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:349 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:353 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:371 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:379 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:380 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:395 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:401 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:419 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:424 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:425 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:430 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:438 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:439 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:453 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:459 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:476 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:483 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:484 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:513 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:514 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:524 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:526 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:527 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:537 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:81 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:86 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:87 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:159 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:165 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:176 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:177 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:197 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:207 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:220 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:248 | fs 10.5；lh 15 | fs 10.5 -> 缺失；lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:260 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:290 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:145 | fs 19 | fs 19 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:146 | lh 24 | lh 24 -> bodyLarge / titleLarge 24 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:160 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:183 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:230 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:239 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:257 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:264 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:278 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:281 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:282 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:315 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:317 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:362 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:134 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:147 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:190 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:221 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:229 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:236 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:237 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:102 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:103 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:108 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:122 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:123 | lh 19 | lh 19 -> 缺失(HARD) / Editorial bodySmall 13/19 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:139 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:149 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:236 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:255 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:134 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:135 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:146 | fs 8 | fs 8 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:147 | lh 10 | lh 10 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:156 | fs 7.5 | fs 7.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:157 | lh 9.5 | lh 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:193 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:194 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:231 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:232 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:243 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:244 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:278 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:279 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:322 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:323 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:336 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:337 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:362 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:514 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:515 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:531 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:532 | lh 12 | lh 12 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:686 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:687 | lh 12 | lh 12 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:817 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:818 | lh 13 | lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:827 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:828 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:883 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:884 | lh 11 | lh 11 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:894 | fs 8 | fs 8 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:895 | lh 9.5 | lh 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:904 | fs 7.5 | fs 7.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:905 | lh 9 | lh 9 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:998 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1007 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1049 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1060 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1086 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1094 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1108 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1109 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1160 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1161 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1176 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1188 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1291 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1296 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1297 | lh 18 | lh 18 -> bodySmall 12/18 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1307 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1311 | fs 20 | fs 20 -> headlineSmall 20/26 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1316 | fs 16 | fs 16 -> bodyLarge / titleMedium 16 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1339 | fs 17 | fs 17 -> 缺失(HARD) / Editorial titleLarge 17/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1341 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1356 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1357 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1364 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1377 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1392 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1400 | fs 13.5 | fs 13.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1404 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1411 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1415 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1416 | lh 15 | lh 15 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1425 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1431 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1437 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1462 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\TimetableScreen.kt:1486 | fs 7；lh 9 | fs 7 -> 缺失；lh 9 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:57 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:64 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:65 | lh 17 | lh 17 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:79 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:89 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:95 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:96 | lh 14 | lh 14 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:120 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:66 | fs 18 | fs 18 -> titleLarge 18/24 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:73 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:74 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:82 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:83 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:89 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:97 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:103 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\LoginScreen.kt:129 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:204 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:253 | fs 12 | fs 12 -> labelMedium / bodySmall 12 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:332 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:340 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:427 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:458 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:459 | lh 16 | lh 16 -> labelMedium 12/16 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:475 | fs 26 | fs 26 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:476 | lh 30 | lh 30 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:482 | fs 9 | fs 9 -> LabelMicro 9/12 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:491 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:499 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:507 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:545 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:547 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:568 | fs 13 | fs 13 -> 缺失(HARD) / Editorial titleSmall·bodySmall 13 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserScreen.kt:571 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:84 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:87 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:147 |  |  |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:148 | lh 30 | lh 30 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:154 | fs 9.5；lh 13 | fs 9.5 -> 缺失；lh 13 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:172 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:175 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:194 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:201 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:241 | fs 12.5 | fs 12.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:243 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:275 | fs 11.5 | fs 11.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\UserTiles.kt:277 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:162 | fs 10.5 | fs 10.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:166 | fs 20 | fs 20 -> headlineSmall 20/26 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:175 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:182 | fs 8 | fs 8 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:200 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:245 | fs 15 | fs 15 -> 缺失(HARD) / Editorial titleMedium 15/21 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:256 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:259 | fs 9.5 | fs 9.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\ui\widget\WoodenFish.kt:263 | fs 8.5 | fs 8.5 -> 缺失 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:132 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:137 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:143 | fs 14 | fs 14 -> labelLarge / bodyMedium / titleSmall 14 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:149 | fs 11 | fs 11 -> labelSmall 11/15 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:158 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:163 | fs 10 | fs 10 -> 缺失(HARD) / Editorial labelSmall 10/14 |
| app\src\main\java\cn\dkarbon\enroute\widget\TodayWidget.kt:170 | fs 9 | fs 9 -> LabelMicro 9/12 |

## 4. 线宽（border / Divider 厚度）

**token 定义（参考）**：`Stroke` = hairline 1 / strong 1.5 / emphasis 2 / accentBar 3 / accentBarThick 4；`FormSpec.ruleWidth` = 1/0.8、`FormSpec.ruleHeavy` = 2/1.5。
全仓 border/Divider 相关 117 行；下表只列**裸数字厚度**（走 `Stroke.*` / `f.ruleWidth` 的未列入）。
裸厚度取值全集：`0 / 0.75 / 0.8 / 1 / 1.5 / 2` dp。

| 文件:行号 | 当前裸值 | 应归属的 token |
|---|---|---|
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:305 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\CurriculumScreen.kt:389 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\curriculum\SyllabusSearchScreen.kt:192 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\custom\CustomCourseScreen.kt:539 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\debug\DebugScreen.kt:290 | 1.5.dp / 0.8.dp | Stroke.strong=1.5；0.8=FormSpec.ruleWidth(Ledger)，此处裸值 |
| app\src\main\java\cn\dkarbon\enroute\ui\feed\FeedScreen.kt:644 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:553 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:830 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\map\CampusMapScreen.kt:1132 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:238 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:278 | 2.dp / 1.dp | Stroke.emphasis=2 / Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:309 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:322 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ColorTunerPanel.kt:356 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\ContributeSection.kt:286 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\SettingsScreen.kt:160 | 1.5.dp / 0.dp | Stroke.strong=1.5；0.dp 缺失（border 开关技巧） |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:257 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\settings\UpdateSection.kt:283 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:122 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:172 | 2.dp / 1.dp | Stroke.emphasis=2 / Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseDetail.kt:342 | 2.dp / 0.75.dp / 1.dp | Stroke.emphasis=2；0.75 缺失（低于 hairline）；hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\CourseQuick.kt:112 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\ErodedCard.kt:59 | 0.75.dp | 缺失（低于 Stroke.hairline=1） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\HistoryTimetableScreen.kt:229 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:83 | 1.5.dp | Stroke.strong=1.5（else 分支走 f.ruleWidth token） |
| app\src\main\java\cn\dkarbon\enroute\ui\timetable\LedgerTimetable.kt:357 | 1.dp | Stroke.hairline=1 |
| app\src\main\java\cn\dkarbon\enroute\ui\home\RoomsScreen.kt:387 | 1.5.dp | Stroke.strong=1.5 |
| app\src\main\java\cn\dkarbon\enroute\ui\user\KeepLoginPrompt.kt:52 | 1.5.dp | Stroke.strong=1.5 |

## 5. 阴影 / 海拔

**事实**：
- `shadowElevation` / `tonalElevation` 字面量：全仓 **0 处**。
- M3 `Card()` / `ElevatedCard()` / `OutlinedCard()` / `Surface()`：全仓 **0 处**（所有 `*Card` 均为自定义 Composable，用 `Box/Column + background + border` 实现）。
- `hardShadow()`（HardTokens.kt:315，2dp 偏移硬投影）：**定义存在但 0 调用点**。
- 实际“浮起”只有 `FormSpec.cardFloat()`（EditorialTokens.kt:376）：两层 drawBehind 矩形，偏移 2dp/5dp、alpha = shadowAlpha×0.55 / ×0.30；调用点 3 处：FeedScreen.kt:912、FeedScreen.kt:1069、LedgerTimetable.kt:79。
- `FormSpec.shadowRadius`（0/6dp）：**声明但未被 cardFloat 读取**（cardFloat 只用 shadowAlpha + 写死 2dp/5dp 偏移），属死 token。

| 文件:行号 | 当前裸值 | 应归属的 token |
|---|---|---|
| FeedScreen.kt:912 | cardFloat（shadowAlpha=0.07、偏移 2/5dp） | ✅ 走 token：FormSpec.cardFloat |
| FeedScreen.kt:1069 | cardFloat（同上） | ✅ 走 token：FormSpec.cardFloat |
| LedgerTimetable.kt:79 | cardFloat（同上） | ✅ 走 token：FormSpec.cardFloat |
| EditorialTokens.kt:376 | cardFloat 实现内偏移 2.dp / 5.dp | ⚠️ 偏移值硬编码在 Modifier 内，未参数化 |
| HardTokens.kt:315 | hardShadow dx/dy = 2.dp | ⚠️ token 存在但 0 调用 |
| EditorialTokens.kt:250/298 | shadowRadius = 0/6.dp | ⚠️ 死 token：无读取点 |

