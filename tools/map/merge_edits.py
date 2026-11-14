#!/usr/bin/env python3
"""把多份「地图标注」合并成一份，供重新打包 / 内置为官方基线。

票 13 阶段 A（零服务端闭环）。三件事：**合**、**挑最新**、**不丢字段**。

## 输入（可任意组合、可重复给）

| 参数 | 是什么 | 时间戳来源 |
|---|---|---|
| `--edits FILE` | App 的 `filesDir/map/edits.json`（契约见 `MapEditsCodec`） | 文件名 `_YYYYMMDD-HHmm` → 否则文件 mtime |
| `--export FILE` | 「导出标注」产出的 `campus_annotated_<版本>_<ts>.geojson` | 同上 |
| `--csv FILE` | 导出的 `.csv`（带 BOM，`id,name,kind,<字段…>`） | 同上 |
| `--patches FILE` | 服务端补丁：`POST /map/api/patches` 的 body、`GET …/patches` 的响应、或 `patches.jsonl` 逐行 | **每条自带 `ts`** |
| `--base FILE` | `app/src/main/assets/map/campus.geojson`（源图层） | —（只用来补几何、判孤儿） |

`--at "2026-11-14T16:00:00+08:00"` 给**没有其它时间线索**的来源兜底。

## 输出（`--out DIR`，默认 `build/map-merge`）

```
merged_edits.json        ← 可直接放进 filesDir/map/ 或作为下一版 asset 基线
merged_campus.geojson    ← 有 --base 时输出：源图层 + 合并后的 name/kind/自定义字段
merged_attributes.csv    ← 带 UTF-8 BOM（Excel 直接打开不乱码）
conflicts.txt            ← 冲突清单 + 孤儿清单，供人工过一眼
```

## 合并规则（与 `docs/校园地图-数据契约与接入方案.md` §10.3 一致）

1. 粒度为 **`(featureId, field)`** —— 只比同一个格子的同一位，不做整行覆盖。
2. 冲突**取最新 `ts`**；`ts` 相同则按**命令行里靠后的来源**胜（并记为 `TIE`）。
3. `op`：`set` 写值、`delete` 清值（清掉后该格不写入）、`addField` 只登记字段名。
4. **字段表取并集**：先按各来源的 `fieldOrder`（按 ts 从旧到新）追加，再补「只在要素里出现过」的字段。
   → 谁加过的自定义字段都不会消失，别人的要素那一列留空。
5. **要素 id 取并集**：源图层里没有的 id（换图后的孤儿）**保留**，在 `conflicts.txt` 里列出来。
6. 值一律 `.strip()`；上限 200 字（服务端同口径），超长**截断并在报告里点名**，不静默。

## 幂等

同一批输入跑两次，输出**逐字节相同**（输出里不含任何「生成时间」）。把输出再当输入合一次，
结果是**不动点**（值和时间都已被记录，重跑不会改变）—— 已有单测锁住这两条。

## 用法

```bash
# 合三份用户导出 + 服务端补丁，并挂到源图层上
python tools/map/merge_edits.py \
  --base app/src/main/assets/map/campus.geojson \
  --export ~/Downloads/campus_annotated_2026.11.14-1_20261114-1530.geojson \
  --edits  ~/Downloads/edits_小明.json \
  --patches ~/Downloads/patches.json    # GET /map/api/patches 的响应体
  --out build/map-merge

# 只看结果、不写盘
python tools/map/merge_edits.py ... --dry-run
```

标准库实现（**不需要 geopandas**），Python 3.9+。
"""
from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import sys
from pathlib import Path

SCHEMA = 1
RESERVED = ("name", "kind", "id", "colorGroup", "color", "areaPx")
# 服务端 / 用户可能用中文名指同一个固定字段
ALIASES = {"名称": "name", "类别": "kind", "name": "name", "kind": "kind"}
MAX_VALUE = 200
FILENAME_TS = re.compile(r"_(\d{8})-(\d{4})\.[A-Za-z]+$")
# 「导出标注」文件名里带版本：campus_annotated_<mapVersion>_<stamp>.<ext>
FILENAME_VER = re.compile(r"^campus_annotated_(.+?)_\d{8}-\d{4}\.[A-Za-z]+$")
ID_NUM = re.compile(r"f(\d+)(?:_(\d+))?$")


# ─────────────────────────── 时间戳 ───────────────────────────

def parse_ts(text):
    """ISO8601（带或不带时区）→ epoch 秒；认不出返回 None。"""
    if not text:
        return None
    s = str(text).strip().replace("Z", "+00:00")
    for fmt in (None, "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            if fmt is None:
                d = _dt.datetime.fromisoformat(s)
            else:
                d = _dt.datetime.strptime(s, fmt)
        except ValueError:
            continue
        if d.tzinfo is None:
            d = d.replace(tzinfo=_dt.timezone(_dt.timedelta(hours=8)))  # 全校都在 +08:00
        return int(d.timestamp())
    return None


def ts_of_file(path: Path) -> int:
    """文件名里的 `_YYYYMMDD-HHmm` 优先，其次 mtime。"""
    m = FILENAME_TS.search(path.name)
    if m:
        try:
            d = _dt.datetime.strptime(m.group(1) + m.group(2), "%Y%m%d%H%M")
            return int(d.replace(tzinfo=_dt.timezone(_dt.timedelta(hours=8))).timestamp())
        except ValueError:
            pass
    return int(path.stat().st_mtime)


def iso(epoch):
    if epoch is None:
        return "?"
    return _dt.datetime.fromtimestamp(epoch, _dt.timezone(_dt.timedelta(hours=8))).isoformat()


# ─────────────────────────── 读取各样输入 ───────────────────────────

class Collector:
    """把「各来源的各条值」收敛成一张 (featureId, field) → 候选列表。"""

    def __init__(self):
        self.cells = {}        # (fid, field) -> [(epoch, src_idx, label, value, op)]
        self.field_seen = {}   # field -> (epoch, src_idx)  第一次出现
        self.versions = []     # [(epoch, src_idx, mapVersion)]
        self.truncated = []    # [(fid, field, 原长)]
        self.bad_field = []    # [(label, field, 原因)]
        self.aliased = []      # [(label, 原字段名, 归一后)]

    def field_name(self, raw, label):
        f = (raw or "").strip()
        if not f:
            self.bad_field.append((label, raw, "空字段名"))
            return None
        if f in ALIASES:
            norm = ALIASES[f]
            if norm != f:
                self.aliased.append((label, f, norm))
            return norm
        if len(f) > 20:
            self.bad_field.append((label, f, "字段名超过 20 字"))
            return f[:20]
        return f

    def note_field(self, field, epoch, src_idx):
        if field in RESERVED:
            return
        cur = self.field_seen.get(field)
        cand = (epoch if epoch is not None else 0, src_idx)
        if cur is None or cand > cur:
            self.field_seen[field] = cand

    def add(self, fid, field, value, epoch, src_idx, label, op="set"):
        fid = (fid or "").strip()
        if not fid:
            return
        v = (value or "").strip()
        if len(v) > MAX_VALUE:
            self.truncated.append((fid, field, len(v)))
            v = v[:MAX_VALUE]
        self.cells.setdefault((fid, field), []).append((epoch, src_idx, label, v, op))
        self.note_field(field, epoch, src_idx)

    def set_version(self, version, epoch, src_idx):
        if version:
            self.versions.append((epoch if epoch is not None else 0, src_idx, version.strip()))


def load_edits(doc: dict, label, epoch, idx, col: Collector):
    """契约：{"schema":1,"mapVersion":…,"fieldOrder":[…],"features":{"f19":{…}}}"""
    schema = doc.get("schema", SCHEMA)
    if int(schema) != SCHEMA:
        raise SystemExit(f"[{label}] schema={schema} 不认识（本工具只认 {SCHEMA}）")
    col.set_version(doc.get("mapVersion"), epoch, idx)
    # 先按 fieldOrder 登记（顺序有意义），再扫要素
    for f in (doc.get("fieldOrder") or []):
        name = col.field_name(f, label)
        if name:
            col.note_field(name, epoch, idx)
    for fid, obj in (doc.get("features") or {}).items():
        if not isinstance(obj, dict):
            continue
        for k, v in obj.items():
            if v is None:
                continue
            if isinstance(v, (dict, list)):
                col.bad_field.append((label, k, "值不是标量，已跳过"))
                continue
            field = col.field_name(k, label)
            if field:
                col.add(fid, field, str(v), epoch, idx, label)


def load_export_geojson(doc: dict, label, epoch, idx, col: Collector):
    """「导出标注」产出的 geojson：properties 里平铺 id/name/kind/自定义字段。"""
    m = FILENAME_VER.match(label)
    if m:
        # 导出体里没有 mapVersion，**文件名是唯一线索**：
        # campus_annotated_<mapVersion>_<YYYYMMDD-HHmm>.geojson
        col.set_version(m.group(1), epoch, idx)
    feats = doc.get("features") or []
    for feat in feats:
        props = feat.get("properties") or {}
        fid = props.get("id") or feat.get("id")
        for k, v in props.items():
            if k in ("id", "colorGroup", "color", "areaPx"):
                continue  # 几何派生量，不是标注
            if v is None or isinstance(v, (dict, list)):
                continue
            field = col.field_name(k, label)
            if field:
                col.add(fid, field, str(v), epoch, idx, label)


def load_csv(path: Path, label, epoch, idx, col: Collector):
    """带 BOM 的 `id,name,kind,<字段…>`。逐行解析（不引 csv 模块以保留原值）。"""
    text = path.read_text(encoding="utf-8-sig", errors="replace")
    rows = _split_csv(text)
    if not rows:
        return
    header = rows[0]
    for row in rows[1:]:
        if not row or not row[0]:
            continue
        fid = row[0]
        for i, head in enumerate(header[1:], start=1):
            val = row[i] if i < len(row) else ""
            field = col.field_name(head, label)
            if field and val:
                col.add(fid, field, val, epoch, idx, label)


def load_patches(path: Path, label, idx, col: Collector, fallback_epoch):
    """服务端补丁：{patches:[…]} / 直接数组 / patches.jsonl（每行一条）。"""
    raw = path.read_text(encoding="utf-8", errors="replace").strip()
    items = []
    if raw.startswith("{"):
        doc = json.loads(raw)
        items = doc.get("patches") or []
        col.set_version(doc.get("mapVersion"), fallback_epoch, idx)
    elif raw.startswith("["):
        items = json.loads(raw)
    else:  # jsonl
        for line in raw.splitlines():
            line = line.strip()
            if line.startswith("{"):
                items.append(json.loads(line))
    for it in items:
        fid = it.get("featureId") or it.get("fid")
        field = col.field_name(it.get("field"), label)
        if not fid or not field:
            continue
        op = (it.get("op") or "set").strip()
        epoch = parse_ts(it.get("ts")) or fallback_epoch
        col.set_version(it.get("mapVersion") or it.get("mv"), epoch, idx)
        if op == "addField":
            col.note_field(field, epoch, idx)
            continue
        col.add(fid, field, it.get("value"), epoch, idx, label, op=op)


def _split_csv(text: str):
    """极简 CSV 解析：支持引号包裹与 "" 转义、CRLF。"""
    rows, row, cell, in_q, i = [], [], [], False, 0
    while i < len(text):
        c = text[i]
        if in_q:
            if c == '"':
                if i + 1 < len(text) and text[i + 1] == '"':
                    cell.append('"')
                    i += 2
                    continue
                in_q = False
            else:
                cell.append(c)
        else:
            if c == '"':
                in_q = True
            elif c == ",":
                row.append("".join(cell))
                cell = []
            elif c in "\r\n":
                if cell or row:
                    row.append("".join(cell))
                    rows.append(row)
                    row, cell = [], []
                if c == "\r" and i + 1 < len(text) and text[i + 1] == "\n":
                    i += 1
            else:
                cell.append(c)
        i += 1
    if cell or row:
        row.append("".join(cell))
        rows.append(row)
    return [r for r in rows if any(x.strip() for x in r)]


# ─────────────────────────── 合并 ───────────────────────────

def id_sort_key(fid: str):
    m = ID_NUM.match(fid)
    if m:
        return (0, int(m.group(1)), int(m.group(2) or 0), fid)
    return (1, 0, 0, fid)


def merge(col: Collector):
    """挑出每格的最新值，返回 (edits, conflicts, deleted)。"""
    winners, conflicts, deleted = {}, [], []
    for (fid, field), cands in col.cells.items():
        # 排序：epoch → 来源序号（命令行靠后者优先）
        ordered = sorted(cands, key=lambda c: ((c[0] if c[0] is not None else 0), c[1]))
        top = ordered[-1]
        distinct = {c[3] for c in ordered if c[4] != "delete"}
        if len(distinct) > 1:
            kind = "TIE" if len({c[0] for c in ordered}) == 1 else "LATEST-WINS"
            conflicts.append({
                "featureId": fid, "field": field, "kind": kind,
                "winner": {"value": top[3], "ts": iso(top[0]), "source": top[2], "op": top[4]},
                "others": [
                    {"value": c[3], "ts": iso(c[0]), "source": c[2], "op": c[4]}
                    for c in ordered[:-1] if c[4] != "delete"
                ],
            })
        if top[4] == "delete" or not top[3]:
            deleted.append((fid, field, iso(top[0]), top[2]))
            continue
        winners[(fid, field)] = top[3]

    # 字段表：并集，按 (第一次出现的 ts, 来源序号) 升序
    fields = [f for f, _ in sorted(col.field_seen.items(), key=lambda kv: (kv[1][0], kv[1][1], kv[0]))]
    # 有的值可能落在没登记过的字段上（自定义字段只出现在要素里）
    for (_, field) in winners:
        if field not in RESERVED and field not in fields:
            fields.append(field)

    fids = sorted({fid for (fid, _) in winners}, key=id_sort_key)
    feats = {}
    for fid in fids:
        obj = {}
        if winners.get((fid, "name")):
            obj["name"] = winners[(fid, "name")]
        if winners.get((fid, "kind")):
            obj["kind"] = winners[(fid, "kind")]
        for f in fields:
            v = winners.get((fid, f))
            if v:
                obj[f] = v
        if obj:
            feats[fid] = obj

    version = ""
    if col.versions:
        version = sorted(col.versions, key=lambda v: (v[0], v[1]))[-1][2]

    edits = {"schema": SCHEMA, "mapVersion": version, "fieldOrder": fields, "features": feats}
    return edits, conflicts, deleted


# ─────────────────────────── 输出 ───────────────────────────

def write_edits(edits: dict, path: Path):
    path.write_text(json.dumps(edits, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def csv_cell(s: str, guard: bool) -> str:
    if not s:
        return ""
    out = s
    if guard and out[0] in "=+-@":
        out = "'" + out          # Excel 公式注入防护（只加在 CSV 里，edits.json 原样）
    if any(c in out for c in ',"\n\r'):
        out = '"' + out.replace('"', '""') + '"'
    return out


def write_csv(edits: dict, path: Path, guard=True):
    fields = edits["fieldOrder"]
    header = ["id", "name", "kind"] + list(fields)
    lines = [",".join(csv_cell(h, False) for h in header)]
    for fid, obj in edits["features"].items():
        cells = [fid, obj.get("name", ""), obj.get("kind", "")]
        cells += [obj.get(f, "") for f in fields]
        lines.append(",".join(csv_cell(c, guard) for c in cells))
    path.write_text("\ufeff" + "\r\n".join(lines) + "\r\n", encoding="utf-8")  # BOM：Excel 认


def build_campus(base: dict, edits: dict):
    """源图层 + 标注 → 合并后的 FeatureCollection（edits 覆盖 name/kind，几何原样保留）。"""
    feats = []
    for feat in base.get("features") or []:
        props = dict(feat.get("properties") or {})
        fid = props.get("id") or feat.get("id")
        e = (edits["features"] or {}).get(fid) or {}
        if e.get("name"):
            props["name"] = e["name"]
        if e.get("kind"):
            props["kind"] = e["kind"]
        for f in edits["fieldOrder"]:
            v = e.get(f)
            if v:
                props[f] = v
        feats.append({"type": "Feature", "id": fid, "properties": props,
                      "geometry": feat.get("geometry")})
    return {"type": "FeatureCollection", "features": feats}


def write_conflicts(col, conflicts, deleted, orphans, path: Path, args, base_ids):
    L = []
    L.append("地图标注合并 · 冲突与异常清单")
    L.append("=" * 60)
    L.append(f"来源 {len(args_sources(args))} 个：" + "、".join(f"{k}={v}" for k, v in args_sources(args)))
    L.append("")
    L.append(f"## 冲突（同一要素同一字段出现 ≥2 个不同值）：{len(conflicts)} 条")
    for c in conflicts:
        L.append(f"- {c['featureId']} / {c['field']}  [{c['kind']}]")
        w = c["winner"]
        L.append(f"    取：{w['value']!r}  ts={w['ts']}  来源={w['source']}  op={w['op']}")
        for o in c["others"]:
            L.append(f"    弃：{o['value']!r}  ts={o['ts']}  来源={o['source']}  op={o['op']}")
    if not conflicts:
        L.append("- （无）")
    L.append("")
    L.append(f"## 被 delete 清掉的格子：{len(deleted)} 条")
    for fid, field, ts, src in deleted:
        L.append(f"- {fid} / {field}  ts={ts}  来源={src}")
    if not deleted:
        L.append("- （无）")
    L.append("")
    if base_ids is not None:
        L.append(f"## 孤儿标注（id 不在当前源图层里，**已保留**）：{len(orphans)} 条")
        for o in orphans:
            L.append(f"- {o}")
        if not orphans:
            L.append("- （无）")
        L.append("")
    if col.truncated:
        L.append(f"## 值超长被截断到 {MAX_VALUE} 字：{len(col.truncated)} 条")
        for fid, field, n in col.truncated:
            L.append(f"- {fid} / {field}  原长 {n}")
        L.append("")
    if col.bad_field:
        L.append(f"## 字段名异常：{len(col.bad_field)} 条")
        for label, field, why in col.bad_field:
            L.append(f"- [{label}] {field!r}：{why}")
        L.append("")
    if col.aliased:
        L.append(f"## 中文字段名已归一到契约名：{len(col.aliased)} 条")
        for label, raw, norm in col.aliased:
            L.append(f"- [{label}] {raw} → {norm}")
        L.append("")
    path.write_text("\n".join(L) + "\n", encoding="utf-8")


# ─────────────────────────── 入口 ───────────────────────────

def args_sources(args):
    """[(role, path)]：**取值来源**（`base` 是底图层，只补几何，不算来源）。"""
    out = []
    for role in ("edits", "export", "csv", "patches"):
        for p in (getattr(args, role) or []):
            out.append((role, p))
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description="合并多份地图标注（票 13 阶段 A）")
    ap.add_argument("--edits", action="append", default=[], metavar="FILE", help="edits.json（可多次）")
    ap.add_argument("--export", action="append", default=[], metavar="FILE", help="campus_annotated_*.geojson")
    ap.add_argument("--csv", action="append", default=[], metavar="FILE", help="campus_annotated_*.csv")
    ap.add_argument("--patches", action="append", default=[], metavar="FILE", help="服务端补丁 json/jsonl")
    ap.add_argument("--base", action="append", default=[], metavar="FILE", help="源图层 campus.geojson")
    ap.add_argument("--at", default=None, help="没有时间线索的来源用它兜底（ISO8601）")
    ap.add_argument("--version", default=None,
                    help="强制指定输出里的 mapVersion（默认取各自来源里最新的）")
    ap.add_argument("--out", default="build/map-merge", help="输出目录（默认 build/map-merge）")
    ap.add_argument("--no-csv-guard", action="store_true", help="关掉 CSV 公式注入防护")
    ap.add_argument("--dry-run", action="store_true", help="只打印，不写盘")
    args = ap.parse_args(argv)

    if not (args.edits or args.export or args.csv or args.patches):
        ap.error("至少给一个数据来源（--edits / --export / --csv / --patches）")

    fallback = parse_ts(args.at)
    col = Collector()
    idx = 0

    def next_idx():
        nonlocal idx
        idx += 1
        return idx

    # 顺序即优先级兜底：命令行靠后的来源在 ts 相同时胜
    for p in args.export:
        path = Path(p)
        doc = json.loads(path.read_text(encoding="utf-8-sig"))
        load_export_geojson(doc, path.name, ts_of_file(path) if fallback is None else fallback, next_idx(), col)
    for p in args.edits:
        path = Path(p)
        doc = json.loads(path.read_text(encoding="utf-8-sig"))
        load_edits(doc, path.name, ts_of_file(path) if fallback is None else fallback, next_idx(), col)
    for p in args.csv:
        path = Path(p)
        load_csv(path, path.name, ts_of_file(path) if fallback is None else fallback, next_idx(), col)
    for p in args.patches:
        path = Path(p)
        load_patches(path, path.name, next_idx(), col, fallback if fallback is not None else ts_of_file(path))

    base_doc, base_ids = None, None
    for p in args.base:
        base_doc = json.loads(Path(p).read_text(encoding="utf-8-sig"))
        base_ids = {(f.get("properties") or {}).get("id") or f.get("id") for f in base_doc.get("features") or []}
        break  # 只用第一份 base（多份无意义）

    edits, conflicts, deleted = merge(col)
    if args.version:
        edits["mapVersion"] = args.version.strip()
    orphans = sorted([k for k in edits["features"] if base_ids is not None and k not in base_ids], key=id_sort_key)

    out = Path(args.out)
    print(f"来源 {len(args_sources(args))} 个，要素 {len(edits['features'])}，字段 {len(edits['fieldOrder'])}"
          f"，冲突 {len(conflicts)}，清空 {len(deleted)}，孤儿 {len(orphans)}，版本 {edits['mapVersion'] or '?'}")

    if args.dry_run:
        print(json.dumps(edits, ensure_ascii=False, indent=2)[:1200])
        return 0

    out.mkdir(parents=True, exist_ok=True)
    write_edits(edits, out / "merged_edits.json")
    write_csv(edits, out / "merged_attributes.csv", guard=not args.no_csv_guard)
    write_conflicts(col, conflicts, deleted, orphans, out / "conflicts.txt", args, base_ids)
    if base_doc is not None:
        campus = build_campus(base_doc, edits)
        (out / "merged_campus.geojson").write_text(
            json.dumps(campus, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(f"merged_campus.geojson 要素 {len(campus['features'])}")
    print(f"→ {out}/  (merged_edits.json / merged_attributes.csv / conflicts.txt"
          f"{' / merged_campus.geojson' if base_doc else ''})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
