# 课程表卡片设置页 · 重设计原型任务书

主人原话（2026-11-23）：「课程表卡片设置页面，重新设计，还要支持实时修改的预览，
具体怎么设计分配代理写HTML看看效果再决定。」

## 1. 现在这页长什么样（要改掉的对象）

文件 `app/src/main/java/cn/dkarbon/enroute/ui/settings/CourseCardSettingsScreen.kt`，从上到下：

1. Section「版式」：一条 SegRow —— 纸卡 / 账页
2. Section「预设」：5 行（纸卡 / 账页 / 密排 / 日程 / 安静），每行左侧 45×52 小样 + 名字 + 一句说明
3. Section「逐条覆盖」：**11 条** SegRow，每条 = 轴名 + 一句把全部取值用「 / 」拼起来的说明 + 一排小按钮（「跟随」+ 各取值）
4. Section「随时间的效果」：3 个开关（小幽灵 / 食物图标 / 当前课描边）

问题：
- 11 条横排按钮（最多 7 个 + 跟随 = 8 个），393dp 宽一行根本放不下，「跟随/方卡/便签/细线/色块/标签」挤在一起；
- **改完看不见结果** —— 设置页没有预览，必须返回课表才知道选了什么；
- 每条轴下面的说明是把选项名再念一遍（「方卡 / 便签 / 细线 / 色块 / 标签」），属说明书式文字，主人明令禁止；
- 预设行和逐条覆盖是两块互不相干的东西，用户不知道「选了预设还能不能改」。

## 2. 必须画出来的真实约束

- 手机画布 **393×852**（1080×2400 / 2.75 的真实比例；设计按 393 宽的 dp 算）
- 课表格子 **45dp 宽 × 52dp 高**，7 列；格内可用净宽 **37dp**，9px 字一行正好 4 个汉字，第 5 字必须换行
- 课表页头 38–84dp（随 header 轴变化）、周条 28–46dp、底部导航 43dp
- 一屏可见节次：dense 11–12 节，editorial 9 节
- 颜色只许用下面这套 token，**不许自己调色**

### 浅色
```
paper   #FAF7F1   card    #FFFFFF   cardAlt #F1ECE1   bandTint #F7F3EA
ink     #16211B   inkSoft #3C4A42   inkFaint #5A6A60
rule    #91825F   outline #7B897F
pine    #1F4A3D   clay    #B4553C   indigo  #2E5266
olive   #3E5C34   ochre   #7A5714   brick   #8E2B1E
```
### 深色
```
paper   #141A17   card    #1B231F   cardAlt #232C27   bandTint #181F1B
ink     #F1EFE7   inkSoft #C6CDC6   inkFaint #9AA69D
rule    #647D71   outline #6E7B72
pine    #8FBFA6   clay    #E0937A   indigo  #A8C2CF
olive   #A9C496   ochre   #D9B96E   brick   #E5A08F
```
字体：系统黑体（-apple-system, "Noto Sans CJK SC", "Microsoft YaHei", sans-serif）。不用 web font。

## 3. 十一条轴（序号即契约，别改顺序）

| # | 轴 | 取值 |
|---|---|---|
| 0 | 形状 | 方卡 / 便签 / 细线 / 色块 / 标签 |
| 1 | 装饰 | 无 / 左脊 / 顶帽 / 角点 / 底线 / 淡底 / 斜纹 |
| 2 | 字段 | 三行 / 同行 / 底栏 / 只课名 |
| 3 | 角标 | 无 / 折角 / 斜纹带 |
| 4 | 侵蚀 | 不褪 / 上褪 / 斜线 / 整块淡 |
| 5 | 动效 | 关 / 呼吸 / 褪色呼吸 |
| 6 | 底色 | 四色 / 单墨 / 无色 |
| 7 | 页头 | 紧凑 / 标准 / 舒展 |
| 8 | 行距 | 紧凑 / 标准 / 宽松 |
| 9 | 底纹 | 无 / 横线 / 田垄 / 复相 / 纯色 |
| 10 | 底纹动效 | 关 / 缓移 |

每条轴还有第 0 态「跟随预设」。互斥（画的时候要禁掉，别让用户选了看不出效果）：
- 角标=斜纹带 ⇒ 字段只能是「只课名」
- 装饰=角点 与 角标=折角 抢同一角落
- 形状=细线 ⇒ 淡底/斜纹无意义
- 底色=无色 ⇒ 形状=色块 退化为描边

## 4. 五套预设（左小样要按真槽位画，不是色块）

```
纸卡 (shape0 decor1 meta0 badge0 erosion1 motion0 tone0 header1 grid1 backdrop0 bgMotion0)
账页 (shape3 decor4 meta1 badge0 erosion0 motion0 tone1 header0 grid0 backdrop1 bgMotion0)
密排 (shape2 decor0 meta3 badge0 erosion0 motion0 tone1 header0 grid0 backdrop0 bgMotion0)
日程 (shape1 decor2 meta2 badge0 erosion3 motion1 tone0 header2 grid2 backdrop2 bgMotion0)
安静 (shape4 decor5 meta0 badge0 erosion0 motion0 tone2 header1 grid1 backdrop3 bgMotion1)
```

## 5. 预览必须是真的课表

别画一个色块示意。预览区要能认出这是课表：7 列周条（一 23 / 二 24 / …）、上午/下午/晚上分段、
节次 1-2 08:00 … 11-12 20:20、格子里是**真课名**（用假数据）：

```
周一：不动产估价 · 二606 · 董秀茹（3-4 节）
周一：国土空间规划 · 二408 · 吴佳倩（5-6 节）
周二：地球表层系统概论 · 二610 · 孙仲秀（3-4 节）
周三：不动产估价 · 二606 · 董秀茹（3-4 节）
周四：马克思主义基本原理 · 一101 · 李想（1-2 节）
周五：土地资源调查与评价 · 三205 · 王立新（7-8 节）
```
未排课 / 缺数据一律显示「暂缺」或「—」，**不许填 0、不许编时间**。

## 6. 每份原型必须做到

1. **实时预览**：改任何一条轴，预览里的格子当帧就变（不许「保存后生效」、不许刷新页面）
2. **改什么看什么**：改「角标」时，用户必须能立刻在预览里找到角标在哪 —— 定位不了等于没预览
3. 一屏之内同时看得见「预览」和「正在改的那条轴」，不用来回滚
4. 393×852 手机框，**至少 3 台并排**：① 默认浅色 ② 改过几条轴之后 ③ 深色。
   三台各自独立可交互，不要联动
5. 预设那一栏要能看出「我已经选了哪套」以及「我在它上面又改了几条」（改了哪几条要能点回「跟随」）
6. 界面上**不许出现说明书文字**：「轻点展开」「点击查看」「可拖动」「向下滚动查看更多」之类一律不许。
   靠形态表达（折角、露边、被截断、投影、位置指示）
7. 单文件、自包含、无外链、无 CDN、无图表库。中文注释写清每块干什么

## 7. 交付

- `docs/design/tt-settings-<方向>.html`
- `docs/design/_shots/tt-settings-<方向>.png`（Edge 无头截图，宽度 ≥1400）
- 回复里给：设计思路 3–5 句、你解决「一屏同时看见预览和参数」的具体手法、你在预览里怎么标出「这条轴改的是哪里」、以及你认为实现进 Compose 时最可能翻车的一点

截图命令（中文路径要 URL 编码，文件名请用 ASCII）：
```
"C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe" --headless=new --disable-gpu --hide-scrollbars --screenshot="<abs>/docs/design/_shots/x.png" --window-size=1440,1100 "file:///O:/dev/enroute-android/docs/design/tt-settings-a.html"
```
（找不到就 `where msedge` 或直接 `msedge --headless=new …`。）
