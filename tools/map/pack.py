"""把校园矢量化 SHP 打成 Android 可用的地图包。

输入：主人的 `校园图块.shp`（面，像素坐标，y 轴向上）+ `校园底图_像素坐标.tif`
输出：app/src/main/assets/map/
  manifest.json     契约入口（crs=local、bbox=底图像素范围、逻辑层与配色）
  campus.geojson    全部要素（Polygon），保留 名称/类别/色组/色值
  base@1x.webp      1024 宽底图（低位图先用着）

字段映射（源 → App）：
  ID   → id（"f19"）
  名称 → name
  类别 → kind（可空；空则由用户在图里补）
  色组 → colorGroup
  色值 → color
  面积 → areaPx

坐标：原样保留（y 轴向上）。**App 渲染时必须翻 y**：
  screenY = (bbox.maxY - y) * scale

可复现：图层变化后重跑本脚本。
  python tools/map/pack.py
"""
import argparse
import json
import os
import sys

import geopandas as gpd
from PIL import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shp", default=r"D:\Download\沈阳农大校园矢量化\图层\校园图块.shp")
    ap.add_argument("--base", default=r"D:\Download\沈阳农大校园矢量化\图层\校园底图_像素坐标.tif")
    ap.add_argument("--out", default=r"O:/dev/enroute-android/app/src/main/assets/map")
    ap.add_argument("--version", default="2026.11.14-1")
    args = ap.parse_args()

    out = args.out
    os.makedirs(out, exist_ok=True)

    # ── 底图：以底图像素尺寸定义 bbox（矢量必须在同一坐标系里）──
    im = Image.open(args.base)
    W, H = im.size
    base_out = os.path.join(out, "base@1x.webp")
    im.convert("RGB").save(base_out, "WEBP", quality=88, method=6)
    base_bytes = os.path.getsize(base_out)
    print(f"底图 {W}x{H} -> base@1x.webp {base_bytes/1024:.0f}KB")

    # ── 矢量 ──
    # .cpg 声明 UTF-8，但 dbf 里中文也可能是 GBK；按 cpg 走，失败再回退
    g = None
    for enc in (None, "utf-8", "gb18030"):
        try:
            g = gpd.read_file(args.shp, encoding=enc) if enc else gpd.read_file(args.shp)
            print(f"读取编码: {enc or '默认(按 .cpg)'} 要素={len(g)}")
            break
        except Exception as e:
            print(f"  编码 {enc} 失败: {str(e)[:80]}", file=sys.stderr)
    if g is None:
        print("!! 无法读取 shp", file=sys.stderr)
        sys.exit(1)

    def s(v):
        if v is None:
            return ""
        try:
            if v != v:  # NaN
                return ""
        except Exception:
            pass
        t = str(v).strip()
        return "" if t.lower() in ("nan", "none") else t

    # ── id 唯一性 ──
    # 优先用 UID（tools/map/add_uid.py 生成，1..N 唯一、且保留原有效 ID）；
    # 没有 UID 时退回 ID：源图层 ID 不可靠（2026-11-14 实测 90 个面里 43 个 ID=0），
    # 此时退化为 f<ID>_<序号> 并在 manifest 报告，让 App 顶部提示「建议重新导出」。
    if "UID" in g.columns:
        fids = [f"f{int(r['UID'])}" for _, r in g.iterrows()]
        dup_count = len(fids) - len(set(fids))
        print(f"  使用 UID 字段（唯一性 {len(set(fids))}/{len(fids)}）")
    else:
        raw_ids = [int(r["ID"]) if s(r.get("ID")) else 0 for _, r in g.iterrows()]
        counts = {}
        for v in raw_ids:
            counts[v] = counts.get(v, 0) + 1
        seen = {}
        fids = []
        dup_count = 0
        for v in raw_ids:
            if v > 0 and counts[v] == 1:
                fids.append(f"f{v}")
            else:
                seen[v] = seen.get(v, 0) + 1
                fids.append(f"f{v}_{seen[v]}")
                dup_count += 1
        if dup_count:
            print(
                f"  [warn] 源 ID 不可靠：{dup_count} 个要素 ID=0 或重复，已退化为 f<ID>_<序号>。"
                "建议先跑 tools/map/add_uid.py 生成 UID 再打包。"
            )

    feats = []
    kinds = {}
    for fid, (_, r) in zip(fids, g.iterrows()):
        kind = s(r.get("类别"))
        name = s(r.get("名称"))
        feats.append({
            "type": "Feature",
            "id": fid,
            "properties": {
                "id": fid,
                "name": name,
                "kind": kind,
                "colorGroup": int(r["色组"]) if s(r.get("色组")) else 0,
                "color": s(r.get("色值")),
                "areaPx": round(float(r["面积"]), 1) if s(r.get("面积")) else 0,
            },
            "geometry": r["geometry"].__geo_interface__,
        })
        if kind:
            kinds[kind] = kinds.get(kind, 0) + 1

    gj = {"type": "FeatureCollection", "features": feats}
    gj_path = os.path.join(out, "campus.geojson")
    with open(gj_path, "w", encoding="utf-8") as f:
        json.dump(gj, f, ensure_ascii=False, separators=(",", ":"))
    gj_bytes = os.path.getsize(gj_path)
    named = sum(1 for x in feats if x["properties"]["name"])
    print(f"campus.geojson 要素={len(feats)} 有名={named} 有类别={len(feats)-sum(1 for x in feats if not x['properties']['kind'])} {gj_bytes/1024:.0f}KB")
    if kinds:
        print("  既有类别: " + ", ".join(f"{k}×{v}" for k, v in sorted(kinds.items(), key=lambda x: -x[1])))

    # ── manifest：bbox 用【底图】范围，不用要素范围，否则底图与矢量错位 ──
    manifest = {
        "schema": 1,
        "version": args.version,
        "crs": "local",
        "bbox": [0, 0, W, H],
        "yAxis": "up",
        "origin": "校园规划示意图矢量化（校园图块.shp，%d 面）" % len(feats),
        # 源 ID 有 0/重复时如实报告，App 顶部据此提示「建议重新导出」，不静默
        "idReview": {
            "duplicates": dup_count,
            "note": "" if dup_count == 0 else "源图层 ID 字段存在 0 或重复值，已退化命名；建议在 ArcGIS 重算 ID 后重出图",
        },
        "generated": "2026-11-14",
        "base": {"file": "base@1x.webp", "width": W, "height": H},
        "style": {
            "mode": "overlay",
            "fill": "#000000",
            "fillAlpha": 0.03,
            "stroke": "#1A1611",
            "strokeAlpha": 0.22,
            "strokeWidth": 1.0,
            "selected": {"fillAlpha": 0.30, "strokeAlpha": 1.0, "strokeWidth": 2.0},
        },
        "layers": [
            {"name": "campus", "file": "campus.geojson", "geom": "polygon", "label": "校园要素", "filter": {}},
        ],
        # 类别码表：App 端图例与信息卡用它把 kind 显示成中文；空类别时按 colorGroup 上色不影响
        "kindLabels": {
            "teach": "教学楼", "lab": "实验楼", "library": "图书馆", "dorm": "宿舍",
            "office": "办公楼", "gym": "体育场馆", "hall": "会堂", "hospital": "校医院",
            "food": "食堂", "canteen": "小卖/报亭", "wc": "卫生间", "park": "停车场",
            "gate": "校门", "bus": "校车站", "sport": "运动场", "green": "绿地",
            "water": "水面", "road": "道路/广场", "other": "其他",
        },
    }
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    total = base_bytes + gj_bytes + os.path.getsize(os.path.join(out, "manifest.json"))
    print(f"包体积合计 {total/1024:.0f}KB -> {out}")


if __name__ == "__main__":
    main()
