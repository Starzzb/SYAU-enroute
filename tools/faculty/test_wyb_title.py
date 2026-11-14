#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""wyb（外语教学部）职称修复的**离线**测试：只用 .cache/faculty/wyb 里的缓存 HTML，不联网。

票 30。跑法：
    python tools/faculty/test_wyb_title.py
"""
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if hasattr(sys.stdout, "buffer"):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.path.insert(0, HERE)

import scrape  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(HERE))
LIST_HTML = os.path.join(ROOT, ".cache", "faculty", "wyb", "p0_1.html")
LIST_URL = "https://wyb.syau.edu.cn/szdw/szdw.htm"

FAILS = []


def check(cond, msg):
    print(("  [ok]   " if cond else "  [FAIL] ") + msg)
    if not cond:
        FAILS.append(msg)


def main():
    cfg = next(c for c in scrape.COLLEGES if c["code"] == "wyb")
    html = open(LIST_HTML, encoding="utf-8", errors="replace").read()

    rows = scrape.list_entries(html, LIST_URL, cfg["mode"], cfg)
    print("== 分组标题职称（缓存列表页，离线）==")
    check(len(rows) == 54, "仍是 54 人（实际 %d）" % len(rows))
    n_title = sum(1 for r in rows if r.get("titleFromList"))
    check(n_title >= 50, "列表分组能给出职称的人数 ≥50（实际 %d）" % n_title)

    by = {}
    for r in rows:
        col = scrape.re.search(r"info/(\d+)/", r["url"]).group(1)
        by.setdefault(col, []).append(r.get("titleFromList", ""))
    for col in sorted(by):
        vals = set(by[col])
        check(len(vals) <= 1,
              "栏目 %s（%d 人）职称一致：%s" % (col, len(by[col]), sorted(vals) or ["（空）"]))
        if col == "1098":
            check(all(v == "" for v in by[col]),
                  "工勤岗不写职称（站点本身没给，宁缺毋滥）")

    # 空标题行不得被当成组名
    m = scrape.group_title_map(html, LIST_URL)
    check(not any(k in scrape.NAV_WORDS for k in (v or "" for v in m.values())),
          "导航词没有被当成职称组名")

    # 别的站点不受影响：没有 titleGroups 配置 → 一律为空
    other = next(c for c in scrape.COLLEGES if c["code"] == "slxy")
    rows2 = scrape.list_entries(html, LIST_URL, other["mode"], other)
    check(all(not r.get("titleFromList") for r in rows2),
          "未开 titleGroups 的配置从同一页面取不到任何职称（全局规则没放宽）")

    print()
    if FAILS:
        print("失败 %d 项" % len(FAILS))
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
