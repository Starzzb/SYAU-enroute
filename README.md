# 沈农在途 · SYAU En Route

沈阳农业大学学生的随身工具。包名 `cn.dkarbon.enroute`。

**它不是教务系统的替代品，是「今天要干嘛」的那一层。**

---

## 现在有什么

| 模块 | 状态 | 数据来源 |
|---|---|---|
| 课程表 | 可用 | URP 登录（校内网）／JSON 导入 |
| 上课提醒 | 可用 | 本地（课前 60 / 30 分钟） |
| 桌面小组件 | 可用 | 本地（今日课程） |
| 考试安排 | 可用 | URP（需先预热考试页，见下） |
| 成绩查询 | 可用 | URP |
| 资讯聚合 | 可用 | 9 个校内/省级/全国源，按需抓正文并永久缓存 |
| 电子木鱼 | 可用 | 纯本地，不联网 |

## 怎么编

```bash
# JDK 17 必需
export JAVA_HOME=/path/to/jdk-17

./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 单测（当前 69 条）
```

Release 包需要自己签名（仓库里只有 `debug.keystore`）：

```bash
apksigner sign --ks debug.keystore --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey \
  --out enroute-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

`local.properties` 里的 `sdk.dir` 要指向本机 Android SDK，这个文件**不进仓库**。

## 目录

```
app/src/main/java/cn/dkarbon/enroute/
├── MainActivity.kt          启动入口（onCreate 只做 setContent）
├── EnRouteRoot.kt           三个 Tab：课表 / 资讯 / 我的
├── data/
│   ├── UrpClient.kt         SSO 登录 + 课表解析（timeAndPlaceList 是关键）
│   ├── Academic.kt          考试 / 成绩接口
│   ├── feed/                资讯：多源抓取 + 三种解析族 + 正文缓存
│   ├── Reminders.kt         上课提醒
│   └── DataWiper.kt         一键清空本地数据
├── ui/
│   ├── theme/HardTokens.kt  硬朗农业风设计系统（0 圆角 / 实线 / 等宽数字）
│   ├── timetable/           竖线时间轴 + 食物卡片 + 程序化幽灵
│   ├── feed/                资讯信息流（知乎式层级 + 微博式时间线）
│   └── user/                我的：登录 / 设置 / 调试
└── widget/TodayWidget.kt    Glance 桌面小组件

app/src/test/resources/feed/  真实抓包样本（学号已脱敏为 0000000000）
docs/                         方案、信息源清单、平台评估
```

## 几条不能破的规矩

设计（`ui/theme/HardTokens.kt` 有完整说明）：

1. **圆角一律 0**（例外：头像圆、5–6dp 状态点）
2. **不用渐变、不用 elevation 阴影**（要阴影用 `hardShadow`，偏移 2dp 模糊 0）
3. **数字全部等宽 + tabular**——课表时间、学分、成绩必须能竖向对齐

实现：

4. **启动不等网络**：`onCreate` 只做 `enableEdgeToEdge` + `setContent`，网络在首帧之后
5. **密码只在内存里用**，不落盘、不进日志
6. **抓取只读**，只发 GET，不写学校系统
7. **不编数据**：没拿到的就显示「没有」，绝不用假数字填版面

## 依赖的接口（都会变，改版时先看这里）

| 用途 | 路径 | 坑 |
|---|---|---|
| SSO 登录 | `pass.syau.edu.cn/tpass/login` | 账号密码要 RSA 加密（先 POST `/tpass/rsa` 取公钥） |
| 课表 | `/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback` | `timeAndPlaceList` 是**数组**，`classWeek` 是 24 位周次位图 |
| 考试 | `/student/examinationManagement/examPlan/detail` | **必须先 GET `/examPlan/index`**，否则恒返回 `status:203` |
| 成绩 | `/student/integratedQuery/scoreQuery/allTermScores/index` → `/{token}/allTermScores/data` | token 每次加载都变，要先正则抠出来 |

## 许可与合规

- 木鱼图形：IvanHanloth/wooden-fish（MIT）
- 食物图标：Twemoji `jdecked/twemoji`（**CC-BY 4.0**，转成 VectorDrawable）
- 字体：IBM Plex Mono（OFL）
- 校内各站 `robots.txt` 是 `Allow: /$` + `Disallow: /`。**当前仅供个人测试**，
  要分发给同学用之前，`jwc` / `xsc` / `job` / `cxcy` / `jobsyau` 这几个源需要学校授权。
