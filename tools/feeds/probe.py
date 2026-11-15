#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第三方资讯源探测（票 34）：robots 先读 → 只认站点自己的链接 → 结构报告。

与教师抓取同一套纪律（`tools/faculty/probe.py` 的 fetch 直接复用：Cookie/Referer/gzip + 退避重试）：
- **不猜 URL**：候选页一定来自站点首页里的 `<a href>`
- **robots 优先**：打印解析后的规则，`Disallow` 命中的路径不抓
- **找 JSON 接口**：把 HTML 里出现的 `/api/`、`.json`、`ajax`、`fetch(`、`XMLHttpRequest` 线索打出来
- 只读探测，串行，间隔 ≥2 秒

用法：
    python tools/feeds/probe.py --site neea
    python tools/feeds/probe.py --site chsi saikr
"""
import argparse
import json
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools", "faculty"))
from probe import fetch  # noqa: E402  —— 共用同一套 HTTP（Cookie/Referer/gzip/退避）

PROBE_DIR = os.path.join(ROOT, "docs", "_probe")
OUT = os.path.join(HERE, "probe.json")
DELAY = 2.0

A_RE = re.compile(r"<a\b[^>]*?href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", re.I | re.S)
TAG_RE = re.compile(r"<[^>]+>")

SITES = {
    "neea": {"name": "中国教育考试网", "home": "https://www.neea.edu.cn/", "kind": "证书公告",
             "kws": ["通知", "公告", "考试", "四六级", "CET", "教师资格", "计算机等级", "NCRE", "报名"]},
    "chsi": {"name": "学信网", "home": "https://www.chsi.com.cn/", "kind": "学历/证书公告",
             "kws": ["通知", "公告", "资讯", "学历", "证书", "考试"]},
    "saikr": {"name": "赛氪", "home": "https://www.saikr.com/", "kind": "竞赛聚合",
              "kws": ["竞赛", "比赛", "赛事", "分类", "数学建模", "英语", "计算机"]},
}


def text_of(h):
    return re.sub(r"\s+", " ", TAG_RE.sub(" ", h)).strip()


def robots_rules(base):
    """抓 robots.txt 并抽出 Disallow 规则（这是「能不能抓」的第一道门）"""
    url = base.rstrip("/") + "/robots.txt"
    try:
        st, body, ct, fin = fetch(url, timeout=20, retries=2)
        rules = [ln.strip() for ln in body.splitlines()
                 if ln.strip().lower().startswith(("user-agent", "allow", "disallow", "sitemap", "crawl-delay"))]
        return {"url": url, "ok": True, "rules": rules[:40]}
    except Exception as e:  # noqa: BLE001
        return {"url": url, "ok": False, "error": "%s: %s" % (type(e).__name__, e), "rules": []}


def api_hints(html):
    """JSON 接口线索：前端调 XHR 的痕迹"""
    hints = set()
    for pat in [r"/api/[\w\-/\.]+", r"[\w\-/]+\.json[\w?=&%\-]*", r"[\w\-/]*ajax[\w\-/\.]*",
                r"XMLHttpRequest", r"fetch\(", r"\$\.getJSON", r"axios"]:
        for m in re.finditer(pat, html, re.I):
            s = m.group(0)
            if len(s) < 80:
                hints.add(s)
    return sorted(hints)[:20]


def probe(code):
    cfg = SITES[code]
    home = cfg["home"]
    rec = {"code": code, "name": cfg["name"], "home": home, "kind": cfg["kind"],
           "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "robots": robots_rules(home),
           "ok": False, "error": None}
    try:
        st, html, ct, fin = fetch(home, timeout=25, retries=2)
        time.sleep(DELAY)
    except Exception as e:  # noqa: BLE001
        rec["error"] = "%s: %s" % (type(e).__name__, e)
        return rec

    rec.update(ok=True, status=st, finalUrl=fin, bytes=len(html.encode("utf-8", "ignore")),
               contentType=ct, apiHints=api_hints(html), title=(re.search(r"<title>(.*?)</title>", html, re.S).group(1).strip()
                                                             if re.search(r"<title>(.*?)</title>", html, re.S) else ""))
    # 候选栏目页：只认首页里出现的链接
    cands = []
    seen = set()
    for m in A_RE.finditer(html):
        href, inner = m.group(1), m.group(2)
        label = text_of(inner)
        if not label or len(label) > 20:
            continue
        if not any(k.lower() in label.lower() for k in cfg["kws"]):
            continue
        from urllib.parse import urljoin
        full = urljoin(fin, href)
        if full in seen:
            continue
        seen.add(full)
        cands.append({"text": label, "url": full})
    rec["candidates"] = cands[:20]

    # 存原始首页，留证据
    d = os.path.join(PROBE_DIR, code)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "home.html"), "w", encoding="utf-8") as f:
        f.write(html)
    return rec


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--site", nargs="+", required=True, choices=list(SITES) + [["all"]] if False else None)
    args = ap.parse_args()
    codes = list(SITES) if args.site == ["all"] else args.site

    out = {}
    if os.path.exists(OUT):
        out = json.load(open(OUT, encoding="utf-8"))
    for code in codes:
        r = probe(code)
        out[code] = r
        print("== %s (%s)" % (r["name"], r["home"]))
        print("   robots: %s" % ("OK" if r["robots"]["ok"] else r["robots"].get("error")))
        for rule in r["robots"]["rules"][:12]:
            print("      %s" % rule)
        if r["ok"]:
            print("   home: %s bytes=%d title=%s" % (r["status"], r["bytes"], r["title"][:40]))
            if r.get("apiHints"):
                print("   API 线索: %s" % ", ".join(r["apiHints"][:8]))
            for c in r["candidates"][:10]:
                print("      - %-12s %s" % (c["text"][:12], c["url"]))
        else:
            print("   ERR %s" % r["error"])
        sys.stdout.flush()
        time.sleep(DELAY)

    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("\n-> %s" % OUT)


if __name__ == "__main__":
    if hasattr(sys.stdout, "buffer"):
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    main()
