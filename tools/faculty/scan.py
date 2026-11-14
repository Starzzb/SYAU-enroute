#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""逐个名录页「看结构」（票 27 第二步）：**只看，不批量抓**。

对每个学院的师资列表页报告可判定的事实：
  · HTTP 状态 / 页面字节 / 是否 VSB
  · 列表里**像教师条目的链接数**（含名字特征或指向 info/detail 的链接）
  · 分页线索：页码链接、`下一页`、`共 N 条`、`createPageHTML` 参数（VSB 常见）
  · 抽样 1 个详情页，把页面上的**标签词**抓出来，据此判断哪些字段真有值

输出：`tools/faculty/scan.json` + 控制台表格。
**不写资产、不写数据库** —— 批量抓取要等人看过这份报告再放行。

用法：
    python tools/faculty/scan.py
    python tools/faculty/scan.py --only nxy
    python tools/faculty/scan.py --refresh
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe import CACHE, DELAY, fetch, text_of  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TARGETS = os.path.join(ROOT, "tools", "faculty", "targets.json")
OUT = os.path.join(ROOT, "tools", "faculty", "scan.json")

A_RE = re.compile(r"<a\b[^>]*?href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", re.I | re.S)

# 详情页上「有值」的判定：标签词出现即认为该字段在这一站可能可采
FIELD_HINTS = {
    "职称": ["职称", "教授", "副教授", "讲师", "研究员", "高级实验师"],
    "职务": ["职务", "院长", "系主任", "党支部书记", "副主任"],
    "院系/系所": ["所在系", "系所", "系（所", "教研室", "所属部门", "单位"],
    "研究方向": ["研究方向", "研究领域", "主要研究"],
    "办公地点": ["办公地点", "办公地址", "办公电话", "办公室", "地点"],
    "工作邮箱": ["电子邮箱", "邮箱", "Email", "E-mail", "email"],
    "教育经历": ["教育经历", "学习经历", "教育背景", "毕业院校"],
    "工作经历": ["工作经历", "工作简历", "任职经历"],
    "照片": ["<img", "照片"],
    "个人主页/简介": ["个人简介", "教师简介", "个人主页", "简历"],
}

NAME_RE = re.compile(r"^[\u4e00-\u9fa5]{2,4}$")


def get_cached(code, tag_name, url, refresh=False):
    d = os.path.join(CACHE, code)
    os.makedirs(d, exist_ok=True)
    f = os.path.join(d, tag_name + ".html")
    if os.path.exists(f) and not refresh:
        return open(f, encoding="utf-8", errors="replace").read(), True
    status, html, ctype, final = fetch(url)
    open(f, "w", encoding="utf-8").write(html)
    time.sleep(DELAY)
    return html, False


def looks_tvsb(html):
    return "_sitegray" in html or "vsb" in html.lower()


def paginate_hints(html):
    """把分页线索抓出来：VSB 的 createPageHTML(cur, total, 'index', 'htm') 最可靠"""
    hints = {"createPageHTML": None, "pageLinks": 0, "next": False, "totalText": None}
    m = re.search(r"createPageHTML\(\s*(\d+)\s*,\s*(\d+)", html)
    if m:
        hints["createPageHTML"] = {"current": int(m.group(1)), "total": int(m.group(2))}
    hints["pageLinks"] = len(set(re.findall(r"index_(\d+)\.htm", html)))
    if re.search(r">\s*(下一页|下页)\s*<", html):
        hints["next"] = True
    m2 = re.search(r"共\s*(\d+)\s*(条|篇|个)", html)
    if m2:
        hints["totalText"] = int(m2.group(1))
    return hints


def item_links(html, base):
    """列表页里「可能是教师条目」的链接：文字是 2–4 个汉字，或指向 info/ detail"""
    from urllib.parse import urljoin
    out = []
    seen = set()
    for m in A_RE.finditer(html):
        href, inner = m.group(1), m.group(2)
        label = text_of(inner)
        if not label or len(label) > 16:
            continue
        if href.lower().startswith(("javascript:", "mailto:", "#")):
            continue
        looks_name = bool(NAME_RE.match(label))
        looks_info = ("/info/" in href) or ("detail" in href.lower()) or ("content" in href.lower())
        if not (looks_name or looks_info):
            continue
        full = urljoin(base, href)
        if full in seen:
            continue
        seen.add(full)
        out.append({"text": label, "url": full, "nameLike": looks_name})
    return out


def field_hits(html):
    low = html
    hits = {}
    for name, hints in FIELD_HINTS.items():
        got = [h for h in hints if (h in low)]
        if got:
            hits[name] = got[:3]
    return hits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None)
    ap.add_argument("--refresh", action="store_true")
    args = ap.parse_args()

    targets = json.load(open(TARGETS, encoding="utf-8"))
    report = {"generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "colleges": []}

    for c in targets["colleges"]:
        code, name = c["code"], c["name"]
        if args.only and code != args.only:
            continue
        rec = {"code": code, "name": name, "lists": [], "note": None}
        if not c["list"]:
            rec["note"] = "首页导航里没有师资/教师入口（继续教育/国际教育这类非教学主体，可能真没有）"
            report["colleges"].append(rec)
            print("[--] %-16s %s" % (name, rec["note"]))
            sys.stdout.flush()
            continue

        for i, url in enumerate(c["list"]):
            item = {"url": url, "ok": False, "error": None}
            try:
                html, cached = get_cached(code, "list%d" % i, url, args.refresh)
            except urllib.error.HTTPError as e:
                item["error"] = "HTTP %s" % e.code
                rec["lists"].append(item)
                continue
            except Exception as e:  # noqa: BLE001
                item["error"] = "%s: %s" % (type(e).__name__, e)
                rec["lists"].append(item)
                continue

            item["ok"] = True
            item["fromCache"] = cached
            item["bytes"] = len(html.encode("utf-8", "ignore"))
            item["vsb"] = looks_tvsb(html)
            links = item_links(html, url)
            item["itemLinks"] = len(links)
            item["nameLinks"] = sum(1 for l in links if l["nameLike"])
            item["samples"] = links[:5]
            item["paginate"] = paginate_hints(html)

            # 抽 1 个详情页看字段
            detail_url = next((l["url"] for l in links if "/info/" in l["url"]), None) \
                or (links[0]["url"] if links else None)
            if detail_url and detail_url != url:
                try:
                    dhtml, dcached = get_cached(code, "detail%d" % i, detail_url, args.refresh)
                    item["detail"] = {
                        "url": detail_url,
                        "fromCache": dcached,
                        "bytes": len(dhtml.encode("utf-8", "ignore")),
                        "fields": field_hits(dhtml),
                        "title": (re.search(r"<title>(.*?)</title>", dhtml, re.S).group(1).strip()
                                  if re.search(r"<title>(.*?)</title>", dhtml, re.S) else ""),
                    }
                except Exception as e:  # noqa: BLE001
                    item["detail"] = {"url": detail_url, "error": "%s: %s" % (type(e).__name__, e)}
            rec["lists"].append(item)

        report["colleges"].append(rec)
        first = rec["lists"][0]
        print("[%s] %-16s items=%s names=%s page=%s" % (
            "OK " if first.get("ok") else "ERR", name,
            first.get("itemLinks"), first.get("nameLinks"),
            (first.get("paginate") or {}).get("createPageHTML"),
        ))
        for l in rec["lists"]:
            if l.get("detail", {}).get("fields"):
                print("      detail fields: %s" % ", ".join(sorted(l["detail"]["fields"])))
        sys.stdout.flush()

    if not args.only:
        with open(OUT, "w", encoding="utf-8") as fh:
            json.dump(report, fh, ensure_ascii=False, indent=2)
        print("\n-> %s" % OUT)


if __name__ == "__main__":
    main()
