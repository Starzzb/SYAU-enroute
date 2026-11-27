# 沈农在途 · SYAU En Route

沈阳农业大学学生用的 Android 应用

课表、空闲教室、考试、成绩、培养方案、课程大纲、校园地图、校内资讯都在一个 App 里。

界面是墨洗纸本一套语汇：宣纸底色、墨分五色、线细如毛笔，没有第二套主题，只分明暗两档。

## 零件构成

|         |                                                                    |
| ------- | ------------------------------------------------------------------ |
| 语言与工具链  | Kotlin 2.0.21、AGP 8.7.3、Gradle 8.11.1、JDK 17                       |
| 界面      | Jetpack Compose（BOM 2024.10.01）、Material 3、material-icons-core |
| 最低与目标版本 | minSdk 26、targetSdk 35                                             |
| 版本      | `0.12.0+m43`／versionCode 510                                            |
| 协程与并发   | kotlinx-coroutines 1.9.0                                           |
| 序列化     | kotlinx-serialization-json 1.7.3                                   |
| 生命周期    | lifecycle 2.8.7（runtime／viewmodel-compose／runtime-compose）         |
| 后台任务    | work-runtime-ktx 2.9.1                                             |
| 桌面小组件   | Glance AppWidget 1.1.1                                             |
| 基础库     | core-ktx 1.13.1、activity-compose 1.9.3                             |

没有用 Room，本地数据是 SharedPreferences 加 JSON 文件。没有引依赖注入框架，ViewModel 手工构造。地图没有用任何地图 SDK，只是一张底图叠上 GeoJSON 矢量层和自绘的命中测试。

## 代码树总览

```
app/src/main/java/cn/dkarbon/enroute/
├── MainActivity.kt         启动入口
├── data/                   抓取、解析、存储
│   ├── UrpClient.kt        教务登录与会话
│   ├── UrpHomeApi.kt       课表 / 空闲教室 / 教师 / 历年课表
│   ├── Academic.kt         考试与成绩
│   ├── SessionCrypto.kt    Keystore 密钥与 AES-GCM 加解密
│   ├── curriculum/         培养方案与课程大纲（读内置资产）
│   ├── custom/             自定义课程（本机）
│   ├── map/                地图解析、投影、命中、标注
│   ├── feed/               资讯多源抓取与正文缓存
│   ├── faculty/            学院官网教师公开信息
│   └── DebugLog*.kt        调试日志（内存 + 落盘）
├── ui/
│   ├── EnRouteRoot.kt      三个 Tab：课表 / 资讯 / 我的，细节换页与浮层也在这里
│   ├── kit/                墨洗零件：底栏、压印、纸纹、加载、详情骨架
│   ├── theme/              墨洗一套 token（InkTokenSet 明暗两档）
│   ├── timetable/          课表网格与卡片
│   ├── academic/           考试、成绩
│   ├── curriculum/         培养方案、课程大纲
│   ├── map/                校园地图
│   ├── feed/               资讯三层漏斗
│   ├── user/               我的
│   ├── update/             阻塞式更新浮层
│   ├── auth/               登录卡
│   └── settings/           设置（含取色与更新）
└── widget/TodayWidget.kt   Glance 桌面小组件
```

## 数据来源

三类。第一类是学校教务系统，通过网页表单登录后读页面；第二类是教务处和学院发的 PDF，离线解析成 JSON 打进安装包，不需要网络；第三类是校内各站点的公开页面，按源抓取并缓存正文。

培养方案的资产是 2023 到 2025 三个年级共 168 份，课程大纲是 14 份学院册共 2066 门课——覆盖 13 个学院加马克思主义学院、体育教学部、外语教学部；继续教育学院与国际教育学院没有全日制本科课程，不在此列。地图是校园规划示意图矢量化出来的 90 个多边形，目前精力的妥协方案。

## 规模

Kotlin 主代码 38,000+ 行，690 个用例分布在 61 个测试类里（1 条跳过，0 失败）。R8 之后的 release 包 7.69 MiB（8,061,612 B），解压后分布：内置资产 4.67 MiB（大纲与培养方案 4.36 MB、地图数据 0.53 MB）、dex 4.21 MiB、res 1.16 MiB、resources.arsc 0.54 MiB、lib 37 KB。

资产是体积第一大头（`curriculum/packed_meta.json` 记录：大纲 2066 门 4,185,043 B、培养方案 168 份 171,417 B，数据资产 4,356,460 B，加上元数据 1,071 B 共 4,357,531 B），2026-11-21 做过一轮瘦身：大纲「简介」只留中文、培养方案「培养目标/毕业要求」压成一句话并标原 PDF 页码，资产 4.87 → 4.35 MB，APK 省 505 KB（见 `docs/设计-资产瘦身-m38.md`）。m39 给大纲索引补了 `pages` 页码范围（+6,872 B）。debug 包没裁剪，体积更大。

## 第三方资源

木鱼图形来自 IvanHanloth/wooden-fish，MIT。食物图标来自 Twemoji，CC-BY 4.0，转成了 VectorDrawable。等宽数字用 IBM Plex Mono（OFL），已随包打进 res/font 三个字重；正文与标题走系统字体，不再打包中文字体。

项目本身以 MIT 许可发布，见仓库根目录的 `LICENSE`。



