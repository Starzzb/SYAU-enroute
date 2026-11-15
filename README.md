# 沈农在途 · SYAU En Route

沈阳农业大学学生用的 Android 应用

课表、空闲教室、考试、成绩、培养方案、课程大纲、校园地图、校内资讯都在一个 App 里。

界面有内置两套主题语汇，浓厚AI前端，期待日后重构~~

## 零件构成

|         |                                                                    |
| ------- | ------------------------------------------------------------------ |
| 语言与工具链  | Kotlin 2.0.21、AGP 8.7.3、Gradle 8.11.1、JDK 17                       |
| 界面      | Jetpack Compose（BOM 2024.10.01）、Material 3、material-icons-extended |
| 最低与目标版本 | minSdk 26、targetSdk 35                                             |
| 版本      | `0.4.0+m21`／versionCode 220                                        |
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
├── EnRouteRoot.kt          三个 Tab：课表 / 资讯 / 我的
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
│   ├── theme/              两套主题的 token 与形态开关
│   ├── timetable/          课表网格与卡片
│   ├── academic/           考试、成绩
│   ├── curriculum/         培养方案、课程大纲
│   ├── map/                校园地图
│   ├── feed/               资讯三层漏斗
│   ├── user/               我的
│   └── settings/           设置（含取色与更新）
└── widget/TodayWidget.kt   Glance 桌面小组件
```

## 数据来源

三类。第一类是学校教务系统，通过网页表单登录后读页面；第二类是教务处和学院发的 PDF，离线解析成 JSON 打进安装包，不需要网络；第三类是校内各站点的公开页面，按源抓取并缓存正文。

培养方案的资产是 2023 到 2025 三个年级共 156 个专业，课程大纲目前只内置了土地与环境学院的 166 门课。地图是校园规划示意图矢量化出来的 90 个多边形，目前精力的妥协方案。

## 规模

Kotlin 源码 27392 行，单测 440 条分布在 35 个测试类里。R8 之后的 release 包 5.27 MB，内置资产占 10.4 MB（培养方案 337 个 JSON 加地图包），厚重的主要来源。debug 包没裁剪，体积是四倍多。

## 第三方资源

木鱼图形来自 IvanHanloth/wooden-fish，MIT。食物图标来自 Twemoji，CC-BY 4.0，转成了 VectorDrawable。主题字体用了 IBM Plex Mono（OFL）和 Noto Serif SC（OFL）。

项目本身以 MIT 许可发布，见仓库根目录的 `LICENSE`。
