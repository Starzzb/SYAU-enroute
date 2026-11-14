# 13: 地图属性众包同步（内测期让用户帮忙补属性）

**What to build:** 内测用户在自己手机上补充校园地图要素的属性（名称/类别/自定义字段），
可以**可选上传**到你自己的服务器汇总，也可以**导出文件**手动回传；
将来你重新矢量化后，老属性按 `id` 迁移，不丢。

**Blocked by:** 12（属性编辑与导出必须先有）

**Status:** ready-for-agent（**建议分两阶段：A 先做，B 等服务器**）

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
