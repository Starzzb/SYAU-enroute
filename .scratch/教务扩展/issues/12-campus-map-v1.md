# 12: 校园地图 v1（相对位置，可交互查看信息）

**What to build:** 一个**可缩放、可拖拽、可点选**的校园地图页。v1 **只做相对位置**（无 GPS、不申请定位权限）。
点中某个地标 → 底部信息卡（名称/类别/楼号/层数/所属单位/备注），并留出「这栋楼今天有什么课」的入口。

**Blocked by:** 主人的 SHP 交付（图纸未完成，2026-11-14 仍在规划中）。**代码可先行**：契约已定，用样例包开发。

**Status:** ready-for-human

---

## 0. 契约（已定稿，见 `docs/校园地图-数据契约与接入方案.md`）

**地图包**（插拔格式，换图不用重装 App）：

```
map/
  manifest.json          # schema / version / crs / bbox / layers[]（含 filter）/ colors
  campus.geojson         # v1 用单文件，全部要素都是 Polygon
```

- `crs = "local"`，`bbox` 为源图像素范围（如 `[0,0,1024,648]`）
- **单文件装全部**：建筑/绿地/水面/道路/POI 都用面，靠 `kind` 区分；
  `manifest.layers[]` 是**逻辑层**，可指向同一文件并用 `filter.kind` 过滤
- POI 是「面状符号」（小方块/小圆面），**不随缩放保持视觉大小** →
  命中测试要给**最小触控半径 ≥24dp** 兜底

运行期读取顺序：`filesDir/map/`（可替换）→ 回退 `assets/map/`（内置）
校验失败（schema 不认 / 缺 `manifest` / GeoJSON 解析失败 / 要素类型非 Polygon）→ **回退内置并提示**，不许白屏。

## 1. 先做样例包（不等图纸）

自己造一份小样例放 `app/src/main/assets/map/`：8~12 个面（3 栋教学楼、1 食堂、1 图书馆、绿地、水面、
2 个校门小面、1 个运动场），`id/name/kind` 齐全，bbox `[0,0,1024,648]`。
**标注 `"origin": "样例数据（真实图纸未到）"`**，并在页面角落显示版本与来源，绝不冒充真实校园。

## 2. 渲染（Compose Canvas 自绘，**不引地图库**）

- GeoJSON 解析：用现有 `kotlinx.serialization`（新增 `data/map/` 下模型 + 解析纯函数）
- 坐标映射：按 `bbox` **等比映射**到画布，保持长宽比并留边；y 轴方向要处理（地理 y 向上，屏幕 y 向下）
- 手势：双指缩放 1×~8×、单指拖拽、双击放大、越界回弹
- 点选：
  - 面命中用**射线法**，多个命中取**面积最小**者（小面优先，避免大绿地把建筑吞掉）
  - 若射线未命中，用「距离 ≤ 24dp 的最近面」兜底（因为 POI 面很小）
- 选中态：描边加粗 + 半透明填充，再点空白取消
- 配色**从 manifest 读**（`fill`/`stroke` 按 `kind` 分组），不写死；缺配色时用现有 token 兜底
- 性能：Path 按要素缓存（缩放级别变了才重建）；只对屏幕内的要素做命中测试；
  目标 416 面稳 60fps

## 3. 信息卡

点中后底部卡：`name`（大标题）/ `kind` 中文名 / `code` 楼号 / `floors` 层数 / `dept` 所属单位 / `note` 备注。
**空字段整行不显示**（不要出现「层数：—」这种）。
留按钮位：「这栋楼今天的课」（票 12b，本票先置灰或不做，别造假数据）。

## 4. 页面与入口

- `UserPage` 新增 `CampusMap`
- 顶部：标题「校园地图」+ 右侧「图例」开关（按 `kind` 列出颜色块）
- 顶部次级：当前地图版本 `version` + `generated`（来自 manifest，必须显示，让人知道这是哪版图）
- 左下浮标：「重置视图」按钮；右下浮标：缩放级别指示（可选）

## 5. 验收标准

- [ ] 进页面能看到校园平面图，双指缩放与拖拽流畅（无明显掉帧）
- [ ] 点建筑/绿地/校门能弹出对应信息卡，字段正确、空字段不显示
- [ ] 点空白取消选中；小面积 POI 面能被点中（24dp 兜底生效）
- [ ] 放一个新的 `map/` 到 `filesDir/map/` 后重进页面 → 用上新图，且版本号变（**插拔验证**）
- [ ] 故意给一份坏 `manifest`（schema 不认 / 缺文件）→ 回退内置并提示，不崩溃
- [ ] 页面上显示当前地图版本与来源，样例数据时明确写「样例」
- [ ] 单测：GeoJSON 解析（Polygon + 多环洞）、bbox 等比映射（含非等比图幅）、射线法命中（含洞）、
      manifest 校验（正常/坏 schema/缺图层）、坐标 y 轴翻转
- [ ] 全量单测绿（基线 **296 条 / 21 类 / 0 失败**）；`assembleDebug` 成功；**APK 增量 ≤1MB**
- [ ] **不申请任何定位权限**（`AndroidManifest.xml` 不得新增 `ACCESS_*_LOCATION`）

## 6. 边界

- 只读本地资产，不打网络；v1 不涉及 GPS
- 不动 `ui/feed/`、`ui/home/*`（`HallCards`/`RoomsScreen`/`Teacher*`）、`ui/timetable/` 的网格几何
- 写盘范围：`data/map/`（新）、`ui/map/`（新）、`app/src/main/assets/map/`、`AppState`、
  `TimetableViewModel`、`ui/user/UserScreen.kt`、测试

## Comments

- 2026-11-14 lead：主人拍板「第一版只做相对位置地图，校园各种地标建筑也都是面可以表示」。
  据此把契约从「必须点图层」改为「单文件面图层 + `kind` 过滤」，`crs=local`、不做 GPS、不申请定位权限。
  契约全文：`docs/校园地图-数据契约与接入方案.md`。

## Comments（补充决策）

- 2026-11-14 lead：看过源资料后定「底图叠加」路线。源 `校园底图_像素坐标.tif`（1024×648）
  **本身就是成品示意图**（标题/图例 12 项/指北针/楼名/操场跑道），不是卫星影像。
  故 v1 做**双模式**：
  - **B 默认**：位图底图（原示意图）+ 416 面半透明热区（填充 alpha 10~14%）+ 选中描边
  - **A 开关**：纯矢量自绘（高倍放大/深色主题时用）
  底图与矢量共享像素坐标，**天然对齐、零配准**（见 `docs/校园地图-数据契约与接入方案.md` §3.5）。

  **实现必须注意**
  1. 源数据 y 轴**向上**（左下原点），屏幕 y 向下 → `screenY = (bbox.maxY - y) * scale`，漏了整图翻转
  2. 需要 `base@1x.webp`（1024）与 `base@2x.webp`（2048，**由原始 JPG 高清版栅格化，不要拿 1024 插值放大**）
  3. 缩放 >2.5× 时位图淡出到 alpha 0.35 或切 A 模式，避免糊
  4. 底图已含楼名 → 信息卡必须给**底图没有的**：层数/所属单位/备注/「这栋楼今天的课」，否则点开没附加值

  **样例包相应调整**：`assets/map/` 除了 `campus.geojson` + `manifest.json`，
  再加一张**占位底图**（用现有 1024 示意图转 WebP，标 `"origin": "样例底图（来自规划示意图）"`），
  验证 B 模式的叠加与淡出。
---

## 7. 属性编辑（主人 2026-11-14 追加，**本票核心功能**）

> 原话：「支持用户点击添加字段图层所属的属性和添加字段，用那张低分辨率的先用着」

**真数据已就位**（我打好了，别再造样例）：`app/src/main/assets/map/`
`manifest.json` + `campus.geojson`（**90 个面**，`名称` 只有 1 条「九州体育场」，`类别` 全空）+ `base@1x.webp`（1024，低分辨率先用着）。
生成器：`tools/map/pack.py`（图层更新后重跑）。

### 要做的

点中要素后，信息卡变成**可编辑的属性面板**：

1. **固定字段**：`名称`（文本框）、`类别`（从 `manifest.kindLabels` 选，19 个码）
2. **动态字段**：底下一个「＋ 添加字段」→ 输入字段名（如 `层数` / `所属单位` / `备注` / `教室号`）
   → 出现该字段的值输入框；**字段名全局共享**（一个要素加了「层数」，其他要素也有这一列，值为空）
3. 已有值可改可清空；清空＝删这一条的值（不删字段本身）
4. 保存**自动生效**（不需要"编辑模式"开关，点了就能改，改完即存）

### 持久化（关键：不能把用户手填的字锁在手机里）

- 写 `filesDir/map/edits.json`，**不要改 assets**（assets 只读）：

```jsonc
{
  "schema": 1,
  "mapVersion": "2026.11.14-1",       // 记录是在哪版地图上标注的
  "fieldOrder": ["层数", "所属单位"],  // 自定义字段，全局有序
  "features": {
    "f19": {"name": "九州体育场", "kind": "sport", "层数": "3", "所属单位": "体育部"}
  }
}
```
- 载入时：asset 的 properties **叠加** edits 里的同名键（edits 优先）
- **原子写**（`.tmp` → rename），每次编辑后落盘；不要在 UI 线程做 IO
- **地图版本变了**：仍按 `id` 合并（契约要求 `id` 稳定）；
  若 edits 里有 id 在新版不存在 → 顶部提示「有 N 条标注在新版地图里找不到对应要素」，
  **绝不静默丢弃**（保留在 edits.json 里，等用户处理）

### 导出（**必须做**，否则他填的数据出不来）

属性面板底部或设置页给「导出标注」：
- 合并 asset + edits → 写 `filesDir/map/export/campus_annotated.geojson` 和 `.csv`（UTF-8 BOM，Excel 可读）
- 同时用 `Intent.ACTION_CREATE_DOCUMENT`（或 share）让用户存到手机/发给自己
- 文件名带版本与时间：`campus_annotated_2026.11.14-1_20261114-1540.geojson`

### 交互细节

- 输入框在屏幕下半部分时，键盘弹起要能看见（`imePadding` / `BringIntoView`）
- 字段值长文本（如「备注」）用多行框
- 保存后信息卡上的值立刻刷新，无需重进页面
- 删除自定义字段入口：长按字段名 → 「删除这个字段」（连带删所有要素该列的值，要二次确认）

### 验收标准（追加）

- [ ] 点要素 → 面板显示 `名称` 与 `类别`；`九州体育场`（f19）能读到、能改
- [ ] 「＋ 添加字段」加一个 `层数` → 该要素填值 → **再点另一个要素，`层数` 这一列也在**（字段全局）
- [ ] 杀掉 App 重进 → 改动还在（edits.json 生效）
- [ ] 换一份新版地图（改 `filesDir/map/`）→ 旧标注按 id 合并；找不到的 id 有提示、不丢
- [ ] 「导出标注」能产出 GeoJSON 与 CSV，内容 = asset + edits 合并结果
- [ ] 单测：edits 合并优先级、字段名全局共享、原子写、版本变更后的孤儿 id 检测、导出序列化
- [ ] 全量单测绿（基线 **307 条 / 22 类 / 0 失败**）；`assembleDebug` 成功
## Comments

### 2026-11-14 交付（agent，含 lead 中途追加的「属性编辑」）

> ⚠️ **先说一个源数据问题，请主人/lead 处理**：`campus.geojson` 里 **id 不唯一** ——
> 90 个面只有 **48 个唯一 id**，其中 **`f0` 出现 43 次**（其余 `f10`…`f89` 各一次）。
> 这会让「标注」变成 43 个面共用一条、逻辑层互相顶掉。我做了两件事：
> 1. 解析层**临时**给重复 id 加序号（`f0#2`、`f0#3`…）保证能用；
> 2. 顶部提示「这份地图里有 42 个要素 id 重复，建议重新导出」。
> **建议在 `tools/map/pack.py` 里改成 `f<index>` 之类必定唯一的 id**，然后重出图；
> 出图后旧标注会按 id 对不上 → 走「孤立标注」提示（数据不丢，但要重新标）。

**edits 存储路径**：`filesDir/map/edits.json`（原子写 `.tmp`→rename，只在 IO 线程；
**assets 只读，一个字节都没改**）。结构就是票里那份契约的平铺形态。

**导出入口**：地图页底部一行 —— 「导出标注」→ 写 `filesDir/map/export/campus_annotated_<版本>_<yyyyMMdd-HHmm>.geojson` 与同名 `.csv`（UTF-8 BOM，Excel 直接开），
export 完成后同一行出现「另存 GeoJSON」「另存 CSV」两个按钮，用 `ACTION_CREATE_DOCUMENT`
（`CreateDocument("application/geo+json")` / `("text/csv")`）让用户存到手机或发给自己。

**我改/删的资产文件（只有一处）**：删掉 `assets/map/base@2x.webp`（43KB）——**那是我上一轮生成的样例占位底图**，
不是 `pack.py` 的产物，manifest 也不再引用它，留着会让人误以为有 2x 档。
其余 `manifest.json` / `campus.geojson` / `base@1x.webp` 我**只读没改**。

**改了什么**

- `data/map/MapModels.kt`（新）：`MapManifest` 13（含 `yAxis` / `style` / `kindLabels` / `kindOptions()`）、
  `MapBase` 48（`file/width/height`）、`MapStyle` 60（fillAlpha 0.03 / strokeAlpha 0.22 / selected 0.3 与 1.0）、
  `MapLayer` 72 + `MapFilter` 83、`MapPoint` 88、`MapFeature` 97（多带 `colorGroup`/`color`/`areaPx`，`area` 优先用数据里的）。
- `data/map/MapParse.kt`（新）：`manifest` 20 按真数据解析；`geoJson` 102 —— **重复 id 加序号并计数**（见上）。
- `data/map/MapProjection.kt`（新）：`MapTransform` 20 —— `screenX` 29 / **`screenY` 32（y 轴翻转：`offsetY + (maxY - y) * scale`）**
  / `sourceX` 34 / `sourceY`；`MapProjection.fit`（等比 + 居中留边）、`withUser`（围绕画布中心缩放，上限 8×）。
- `data/map/MapHit.kt`（新）：`pointInRing` 15（射线法）、`contains` 31（**含洞**）、`pick` 40（**命中多个取面积最小**）、
  `nearest` 50、`pickOrNearest` 62（未命中再走 24dp 兜底）。
- `data/map/MapLayers.kt`（新）：`split` 26（按 `filter.kind` 拆逻辑层，**没被接住的要素进 unmatched 不丢**）、`legend` 42。
- `data/map/MapPackage.kt`（新）：`MapPackage` 48、`MapPackageRules.validate` 79（**六态**：正常 / MISSING_MANIFEST /
  PARSE_ERROR / BAD_SCHEMA / BAD_BBOX / MISSING_LAYERS / MISSING_FILE / BAD_GEOMETRY）、`describe` 113、`MapPackageLoader` 130
  （**先 filesDir/map 外部包，坏了回退 assets/map 并给出人话提示**，绝不白屏）。
- `data/map/MapEdits.kt`（新，本票核心）：`MapEdits` 25 / `MapEdit` 47、`MapEditsCodec` 66（平铺 JSON 往返）、
  `MapEditsOps` 126（`addField` 129 全局字段 / `removeField` 138 连带删值 / `setName` / `setKind` / `setField` 154 /
  `withVersion` 171 / `merged` 187 / `rowOf` 198 / **`orphans` 212 孤儿检测**）、
  `MapExport` 223（`geoJson` 228 合并导出 / `csv` 266 带 BOM / `fileName` 279）。
- `data/map/MapEditsStore.kt`（新）：`read` 21 / `write` 28（原子写）/ `export` 55（写到 `filesDir/map/export/`）/ `clear` 85。
- `ui/map/CampusMapScreen.kt`（新）：`CampusMapScreen` 93 —— 顶栏（模式切换/图例）+ 版本来源常显 + 重复 id 提示 +
  孤儿标注提示 + Canvas（B 模式底图叠加、缩放>2.5× 淡出 0.35；A 模式按要素 `color` 上色；热区 alpha 全读 `manifest.style`）
  + 浮标（重置视图 / 缩放倍数）+ 图例 + `PropertyPanel` 411（名称 / 类别 19 码 chip / 自定义字段 / ＋添加字段 / 长按删字段 /
  「这栋楼今天的课」置灰占位）+ 底部导出行 358/360（`ACTION_CREATE_DOCUMENT`）；根 `imePadding()`，输入框不被键盘挡。
- `ui/AppState.kt` 192 起 12 个字段；`ui/TimetableViewModel.kt`：`openCampusMap` 556、`selectMapFeature` 588、
  `clearMapSelection` 592、`updateEdits` 597（改一次存一次）、`setMapFeatureName/Kind/Field` 606/610/614、
  `addMapField` 619、`removeMapField` 624、`exportMapAnnotations` 632。
- `ui/user/UserScreen.kt` 69/113/123：「我的 → 校园地图」入口。
- `data/DataWiper.kt` ⑤d：清数据时连带清 `filesDir/map/`（标注 + 导出）。
- 测试：`MapTest`（22 条）+ 夹具 `app/src/test/resources/map/`（真 `manifest.json` 与 `campus.geojson` 原样拷贝）。

**怎么验证**

- 先单独 `:app:compileDebugUnitTestKotlin` → 绿；`build-test.bat`：`BUILD SUCCESSFUL` / `EXIT=0`。
- 单测：**329 条 / 23 类 / 0 失败**（基线 307/22，本轮 +22 条 / +1 类）。
- 关键断言：真 manifest（schema/yAxis=up/style.fillAlpha=0.03/19 个 kindLabels/base.file）；
  真数据 90 面、只有 1 条有名字、`f0` 重复 42 次被加序号兜住；多环洞面积 = 100-4；
  **等比映射**（1024×648 → 800×800 时 scale=800/1024 且纵向居中）；**y 轴翻转**（`y=0` → 屏幕 648、`y=648` → 0）与往返；
  缩放围绕中心且上限 8×；射线命中/**洞不算命中**/多命中取最小面积/24dp 兜底（含"超出就不给"）；
  逻辑层按 kind 拆且 unmatched 不丢；校验四态 + 缺层 + 坏 bbox；
  edits 平铺 JSON 往返、edits 覆盖 asset、**字段全局共享（没标过的要素也有这一列）**、清空值不删字段、
  保留名/重复名不许加、删字段连带删值、换图孤儿检测、导出 GeoJSON/CSV（BOM/表头/转义/3 位小数）。
- **APK**：22,705,357 → 22,880,576 字节（**+0.17MB**，远低于 1MB 预算）。
- **未新增任何权限**：`AndroidManifest.xml` 里没有 `ACCESS_*_LOCATION`（已 grep 确认）。

**遗留 / 只能主人做的**

1. **真机验收**（`10AE1H1PYJ005ED` 本轮未连接）：双指缩放/拖拽是否顺、点小面是否好点、
   属性面板在键盘弹起时是否可见、导出后文件能否在「文件」里找到。
2. **源数据 id 要去重**（见开头）：这是唯一会连带影响标注功能的数据问题。
3. 底图只有 `base@1x.webp`（1024）：放大到 2.5× 以后就看淡出效果了；等高清图到了放进 manifest 即可（模型已按单文件实现）。
4. 「这栋楼今天的课」是票 12b，本票只放了一个置灰占位，**没有编数据**。
5. 标注只按 `id` 关联，没有做「撤销 / 历史版本」；改错只能再改回来（未在票面要求内）。

---

## 8. 「持续开发中 + 共建引导」（主人 2026-11-14 追加）

> 原话：「地图方面注明一下仍旧持续开发中，需要用户共同维护数据，地图数据差，需要补充数据
> 并教怎么为某个地块添加数据」

### 8.1 页内常驻提示条（地图区下方，不可关闭）

一行，数字必须**从实际数据算**，不许写死：

```
地图仍在开发中：90 个地块中仅 1 个有名称、0 个有类别。欢迎一起补全          [怎么补？]
```

规则：
- `90` = 载入的 GeoJSON 要素总数
- `仅 1 个有名称` = `name` 非空的要素数
- `0 个有类别` = `kind` 非空的要素数
- 若两个都补齐了（等于总数），文案降级为「地图仍在开发中：地块信息已由大家补全，如有错漏欢迎修正」
- 右侧「怎么补？」→ 打开 §8.2 引导卡
- 点击整条提示条也打开引导卡（不只是那个按钮）

### 8.2 引导卡（「怎么补？」打开；**首次进入地图页自动弹一次**）

标题：**帮大家补地块信息**
副标题：地图还在完善，你补的每一条都会让下一个人少走冤枉路。

正文（五步，编号 + 短句，别写成长文）：

1. **点中地块** —— 在地图上点任何一块（教学楼、场馆、操场、绿地都行），底部会弹出编辑面板。
2. **填名称、选类别** —— 名称写楼名（如「第二教学楼」）；类别决定它的颜色和图例分组。
3. **不够就加字段** —— 点「＋ 添加字段」，可以加 `层数`／`所属单位`／`开放时间` 这类信息；
   字段名是所有地块共用的，你加一次，别的楼也会多这一列（值留空）。
4. **改完自动保存** —— 不用点「保存」，退出 App 也不会丢。
5. **想帮到所有人** —— 点「导出标注」，把生成的 `.geojson` 发给开发者，
   下一版地图就会带上你的标注。

底部小字（必须显示，数据诚实 + 隐私）：
> 你的标注先只存在本机；点「导出标注」并发送后才会离开手机。不含任何个人信息。

底部两个按钮：`[知道了]`（关闭）· `[开始标注]`（关闭并提示「去点地图上任意一块」）

### 8.3 首启记忆

- 用现有 `Prefs` 存一个 `mapGuideSeen` 布尔；弹过一次就不再自动弹（「怎么补？」随时可再看）
- 「清除所有数据」把它一起清掉（下次重新引导）

### 8.4 验收（追加）

- [ ] 提示条数字与实际数据一致（90 / 1 / 0）；改一个要素的名称与类别后，重新进入页面数字变成 `1 个有名称 → 2 个`、`0 → 1`
- [ ] 首次进入地图页自动弹引导卡；关掉后重进不再自动弹；「怎么补？」能再次打开
- [ ] 引导卡五步文案与上面一致，底部隐私小字存在
- [ ] 数据全补齐时文案降级为「已由大家补全，如有错漏欢迎修正」
- [ ] 单测：提示条文案生成（含 0 名称 / 部分 / 全部三种情况）、`mapGuideSeen` 读写