"""给校园图块 SHP 补一个唯一且稳定的 UID 字段（写入副本，绝不动原文件）。

背景：源图层 `ID` 字段有 43 个要素是 0（ArcGIS 重画后没重算），
而 id 是「App 里属性标注」的关联键，必须唯一。

策略（尽量保住已有值，避免已标注的属性错位）：
  · 原 ID > 0 且全表唯一  → UID = 原 ID（所以 f19 九州体育场等保持不变）
  · 原 ID = 0 或重复      → UID 从 max(有效ID)+1000 起递增（f1019… 这类，绝不撞车）

输出：与源同目录的 `校园图块_uid.shp`(+.dbf/.shx/.cpg)，源文件只读。
用法：python tools/map/add_uid.py
"""
import argparse
import os

import geopandas as gpd


def s(v):
    if v is None:
        return ""
    try:
        if v != v:
            return ""
    except Exception:
        pass
    t = str(v).strip()
    return "" if t.lower() in ("nan", "none") else t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shp", default=r"D:\Download\沈阳农大校园矢量化\图层\校园图块.shp")
    ap.add_argument("--out", default=r"D:\Download\沈阳农大校园矢量化\图层\校园图块_uid.shp")
    args = ap.parse_args()

    if os.path.abspath(args.shp) == os.path.abspath(args.out):
        raise SystemExit("输出不能覆盖源文件")

    g = gpd.read_file(args.shp)
    print(f"读入 {len(g)} 个要素: {args.shp}")

    raw = [int(r["ID"]) if s(r.get("ID")) else 0 for _, r in g.iterrows()]
    counts = {}
    for v in raw:
        counts[v] = counts.get(v, 0) + 1

    valid = [v for v in raw if v > 0 and counts[v] == 1]
    next_uid = (max(valid) if valid else 0) + 1000

    uids = []
    kept = 0
    assigned = 0
    for v in raw:
        if v > 0 and counts[v] == 1:
            uids.append(v)
            kept += 1
        else:
            uids.append(next_uid)
            next_uid += 1
            assigned += 1

    g["UID"] = uids
    # 放到 UID 之后便于查看；其余字段原样保留
    cols = ["UID"] + [c for c in g.columns if c not in ("UID", "geometry")] + ["geometry"]
    g = g[cols]

    assert len(set(uids)) == len(uids), "UID 仍有重复"
    g.to_file(args.out, encoding="utf-8")

    print(f"保留原 ID: {kept} 个；新分配: {assigned} 个（从 {max(valid)+1000 if valid else 1000} 起）")
    print(f"UID 唯一性: {len(set(uids))}/{len(uids)}  OK")
    print(f"写出: {args.out}")
    print(f"  UID 样例: {uids[:6]} … {uids[-3:]}")


if __name__ == "__main__":
    main()
