#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全局汇总：把 `assets/faculty/*.json` 折成一份 `_index.json`（票 27）。

为什么单独一个脚本：`run_all.py` 是**逐站跑**的，每次只知道自己那一家
—— 它写出来的 `_index.json` 只是"本次跑到的学院"，**不能当统计口径**
（曾出现过 `_index.json` 只剩 1 个学院、总人数 3 的情况）。

本脚本**永远重扫整个目录**，所以：
- 任何时刻跑一次，都得到「当前 assets 的真实全局状态」
- 也是下一段做 UI 的人判断「哪家能显示什么」的唯一入口

用法：
    python tools/faculty/summary.py
"""
import json
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "assets", "faculty")

FIELDS = ["title", "duty", "dept", "research", "office", "email", "education", "work", "photo", "photoThumb"]


def main():
    cols, total = [], 0
    for fn in sorted(os.listdir(ASSETS)):
        if not fn.endswith(".json") or fn.startswith("_"):
            continue
        d = json.load(open(os.path.join(ASSETS, fn), encoding="utf-8"))
        rows = d.get("teachers", [])
        total += len(rows)
        cov = {f: sum(1 for r in rows if r.get(f)) for f in FIELDS}
        cols.append({
            "code": d.get("code", fn[:-5]),
            "college": d.get("college", ""),
            "mode": d.get("mode", ""),
            "count": len(rows),
            "coverage": cov,
            "problems": len(d.get("problems", [])),
            "generatedAt": d.get("generatedAt", ""),
        })

    out = {
        "schema": 1,
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "colleges": len(cols),
        "total": total,
        "fields": FIELDS,
        "byCollege": cols,
        "totals": {f: sum(c["coverage"][f] for c in cols) for f in FIELDS},
        "note": "本文件每次都由 tools/faculty/summary.py 重扫整个 assets 目录生成，不是逐站中间产物",
    }
    p = os.path.join(ASSETS, "_index.json")
    tmp = p + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1, sort_keys=False)
        f.write("\n")
    os.replace(tmp, p)

    print("学院 %d 家，合计 %d 人" % (len(cols), total))
    for c in cols:
        print("  %-5s %-18s %4d 人  职称%3d 邮箱%3d 研究%3d 照片%3d" % (
            c["code"], c["college"], c["count"],
            c["coverage"]["title"], c["coverage"]["email"],
            c["coverage"]["research"], c["coverage"]["photo"]))
    print("-> %s" % p)


if __name__ == "__main__":
    if hasattr(sys.stdout, "buffer"):
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    main()
