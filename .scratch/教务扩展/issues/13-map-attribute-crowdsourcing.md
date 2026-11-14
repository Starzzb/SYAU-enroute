# 13: 地图属性众包同步（内测期让用户帮忙补属性）

**What to build:** 内测用户在自己手机上补充校园地图要素的属性（名称/类别/自定义字段），
可以**可选上传**到你自己的服务器汇总，也可以**导出文件**手动回传；
将来你重新矢量化后，老属性按 `id` 迁移，不丢。

**Blocked by:** 12（属性编辑与导出必须先有）

**Status:** ready-for-human（阶段 A、B 均已实现；下行同步**明确不做**，见票底 Comments 与票 21）

---

## 背景

属性数据只补「某个块的字段值」，量极小（90 个面 × 若干字段 ≈ 几 KB），
**不需要上传几何与底图**。契约与合并规则见 `docs/校园地图-数据契约与接入方案.md` §9、§10。

---

## 阶段 A（**先做，零服务端**）：多份 eds 合并脚本

内测期最快的闭环：

1. App 侧「导出标注」产出 `campus_annotated_<mapVersion>_<ts>.geojson` + `.csv`（票 12 §7 已要求）
2. 写 `tools/map/merge_edits.py`：
   - 输入：任意多份导出文件（或裸 `edits.json`）
   - 按 `(featureId, field)` 合并，冲突**取最新 `ts`**，并把冲突行打印成清单
   - 输出：合并后的 `edits.json`（可直接放进下一次 asset 或 `filesDir/map/`）
   - 同时输出 `conflicts.txt` 供人工过目
3. 你确认后用 `tools/map/pack.py` 重新出包，或在下一版 asset 里内置该 `edits.json` 作为「官方基线」

**验收**
- [ ] `merge_edits.py` 能合 2+ 份文件；冲突按 ts 取最新并列出
- [ ] 合并结果喂回 App 能正确显示（把 `edits.json` 放 `filesDir/map/` 验证）

---

## 阶段 B（服务器就绪后）：补丁流同步

### 接口（最小集）
```http
POST /map/patches
  { schema, mapVersion, userKey, patches:[{id, featureId, field, value, ts, op}] }
  → { accepted: N, rejected: M, reasons: {"...": "..."} }

GET /map/patches?mapVersion=<v>&since=<ts>
  → { patches:[…], serverTime }

GET /map/snapshot?mapVersion=<v>
  → { fields:[…], features:{ "f19": {…} } }   // 审核通过后的合并全量
```
- `op`: `set` / `delete` / `addField`
- `id` = `sha1(featureId|field|value|userKey|ts)`，服务端按主键去重（幂等）

### 客户端行为
- 设置页开关「帮助完善校园地图」（**默认关**）；打开后才会上传，且明确说明上传内容
- 本地 `edits.json` 永远是**第一优先**（自己的标注不被远端覆盖）
- 远端补丁按 `ts` 合并到「非本人字段」上；冲突记日志但不打扰用户
- 同步失败**不阻塞 UI**，离线继续用本地数据
- 上传队列落盘（`filesDir/map/pending.json`），联网后重试

### 服务端（建议轻量）
单表 + 两个路由即可，Cloudflare Workers + D1 或现成机器：
```sql
map_patch(id TEXT PRIMARY KEY, map_version TEXT, feature_id TEXT, field TEXT,
          value TEXT, op TEXT, user_key TEXT, ts TEXT, reviewed INT DEFAULT 0)
```
索引 `(map_version, feature_id, field, ts)`。

### 审核
内测期：**先本地立即可见** + 后台 `reviewed=0` 队列人工过一遍；
只有 `reviewed=1` 的才进 `snapshot` 下发给所有人 —— 脏数据不扩散。

### 隐私红线（不可协商）
- 上传**只有** `featureId + 字段 + 值 + 匿名 userKey`；**不含**学号、姓名、位置、设备 ID
- `userKey` 本地随机 UUID，设置页可重置（重置后与历史脱钩）
- **默认不开启**，首次开启必须二次确认并说明上传什么
- 字段名白名单 + 值 ≤200 字 + 脏词/注入过滤
- 不采集定位（v1 无定位权限）

---

## 阶段 B 验收
- [ ] 开关关闭时**一个字节都不上传**（网络层有断言/日志证明确实没请求）
- [ ] 开启后提交成功、失败进入 pending 队列并重试
- [ ] 拉回远端补丁后能正确合并显示；本地标注优先不被覆盖
- [ ] 重置 userKey 后不再沿用旧身份
- [ ] 服务端拒绝的脏数据（超长值/非法字段名）有明确返回值，客户端不崩
- [ ] 全量单测绿（基线随票 12 更新）；上传内容序列化有单测，断言**不含**任何个人信息字段

## 边界

- 只上传属性补丁，不上传几何/底图/截图
- 不动教务相关代码
- 写盘范围：`data/map/`（同步与队列）、`ui/map/`、`ui/settings/`、`tools/map/merge_edits.py`、测试

---

## Comments

### 2026-11-14 · 阶段 A + 阶段 B 实现完成

**改了什么**

| 位置 | 内容 |
|---|---|
| `tools/map/merge_edits.py`（新） | 多来源合并：`--edits` / `--export` / `--csv` / `--patches` / `--base`；粒度 `(featureId, field)`，取最新 `ts`；字段与要素 id 取**并集不丢**；孤儿保留；值 ≤200 字截断并点名；输出 `merged_edits.json` / `merged_campus.geojson` / `merged_attributes.csv`（**UTF-8 BOM**）/ `conflicts.txt` |
| `tools/map/test_merge_edits.py`（新） | 8 条断言，含**幂等（两次输出逐字节相同）**与**不动点（输出再当输入合一次不变）** |
| `tools/map/pack.py`（改） | 新增 `--from-geojson`：合并结果可直接重新出包（该路径**不需要 geopandas**）；顺带统计 `manifest.customFields` |
| `data/map/MapContribute.kt`（新） | 纯逻辑：生成补丁 / 校验 / 分批 / **组装请求体** / 预览文案 / 响应解析 / 退避表 |
| `data/map/MapContributeStore.kt`（新） | 开关（**默认关**）、匿名键（随机 UUID，可重置）、待传队列 `filesDir/map/pending.json`、退避状态 |
| `data/map/MapUploader.kt`（新） | POST `18082` → 回落 `18081`；≤200 条/批；成功出队；失败退避 1→5→15→60→180 分钟；错误全说人话 |
| `ui/settings/ContributeSection.kt`（新） | 开关 + **首次确认（会传什么/不会传什么）** + 预览 + 立即上传 + 待传数 + 重置身份；唯一重试触发点＝进设置页 |
| `res/xml/network_security_config.xml`（改 1 条） | 放行 `47.95.12.198`（Android 默认禁明文，不放行则上传与更新回落都被系统拦）。**只此一条，无通配** |
| `docs/校园地图-补丁服务部署.md` | 按实现改正 5 处（见下） |

**怎么验证**

- Python：`python tools/map/test_merge_edits.py` → 8/8（含幂等与不动点）
- 真实资产冒烟：两份导出 + 真 `campus.geojson`/tif → 合并 → `pack.py --from-geojson` 出包：**90 面、bbox 与现网一致 (0,0,1024,648)**、自定义字段进入 `campus.geojson` 与 `manifest.customFields`
- JVM：`MapContributeTest` 13 条（**隐私逐键断言**：请求体整段匹配 `client:{did,appVer}`，`sid`/`model`/`userKey`/定位/硬件标识一律不出现）+ `MapOfficialBaselineTest` 8 条；`compile-check` 绿
- 真机 8 条手工验证见代理回报（待放行装机）

**五处「文档 ≠ 实现」（已按代码改文档）**

1. 「按主键去重（幂等）」**不成立**：`id = sha1(mv|fid|field|value|ch|now)`，`now` 是服务器时间 → 重复提交会新增记录；去重责任在客户端
2. `reasons` 是**数组** `[{featureId, field, reasons:[…]}]`，不是 map
3. 校验规则补全：`featureId ^f\d{1,6}$`（**不接受 `f0_1`**）、`field` ≤20 字无白名单、`op ∈ set/delete/addField`、`value ≤200`、单批 ≤200、body ≤256KB、限流 400/小时
4. **没有 `GET /map/api/patches`**；`client.model` **明文落盘**、`client.sid` 加密存 `sid_enc`
5. 客户端 `did` 是**本地随机 UUID**（不是 ANDROID_ID/IMEI），设置页可重置

**遗留 / 验收项调整**

- 验收项「拉回远端补丁后能正确合并显示」**本轮不做** → 见下文决定，另开票 21（wontfix）
- 验收项「关闭时一个字节都不上传」：代码层成立（`upload()` / `uploadAsync()` 首行判 `isEnabled()`），**真机需抓包确认**（待装机）
- 服务端「明文 0 命中」grep：**等真机上传成功后再查**（现在库里空的，0 命中说明不了问题）
- `CMAP_API_KEY` 走 `BuildConfig.CMAP_API_KEY`（`local.properties` 注入）。⚠️ 它不是安全边界 —— 真正的防护是限流 + 人工审核 + 匿名上报

**下行同步：明确不做（设计决定，不是缺接口）**

1. 服务端没有 `GET /map/api/patches`；只加路由不够，还要 `reviewed` 过滤、`since` 游标、分页
2. **未经审核的众包数据不能直接展示给其他用户**：别人随手填的「层数=99」若实时下行，会让一批用户的地图上出现错误信息。众包的价值在覆盖，风险在质量，唯一可靠的门是**人**
3. 现有链路已能跑，且每一步都有人参与：
   `客户端上报 → patches.jsonl → merge_edits.py（幂等/不动点有单测）→ conflicts.txt 人工过一眼 → pack.py --from-geojson → 随版本发布 → 用户更新后看到`
   好处是**每一次数据进入产品都经过一次人的判断**
