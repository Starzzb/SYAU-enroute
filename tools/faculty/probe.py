#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学院官网「师资队伍」入口探测器（票 27 第一步）。

**它只做一件事**：把 18 个学院/教学部的官网首页抓下来，把「师资/教师/队伍/导师/教授」
这类导航链接挑出来，连同可判定的事实（HTTP 状态、页面长度、编码、是否 VSB 站群、
内容是不是动态加载）一起打出来。

它**不猜**名录页 URL：候选链接一定来自站点自己首页里的 `<a href>`。
挑不出来就如实报「需人工」，不编一个看着像的地址。

用法：
    python tools/faculty/probe.py                # 全部 18 个
    python tools/faculty/probe.py --only nxy     # 只探一个
    python tools/faculty/probe.py --refresh      # 忽略缓存重新抓

缓存：`.cache/faculty/<code>/home.html`（不进仓库，见 .gitignore）
频率：同一站点串行、每次请求间隔 ≥ 1.2 秒（票 27 纪律：别把学院站点打出问题）
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urljoin, urlparse

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE = os.path.join(ROOT, ".cache", "faculty")
OUT = os.path.join(ROOT, "tools", "faculty", "sources.json")

UA = "SYAU-Enroute-Research/0.1 (personal student research build; contact via repo)"
DELAY = 2.5   # 2.5 秒：慢一点没关系，别把学院站点打出问题

# 官方来源：https://www.syau.edu.cn/xybm/jxkybm.htm（「教学科研部门」，2026-11-14 抓取）
# 顺序、名称与官方页面一致；没有独立站点的会在这里如实标出（`url: None`）。
COLLEGES = [
    ("nxy", "农学院", "http://nxy.syau.edu.cn/"),
    ("zbxy", "植物保护学院", "http://zbxy.syau.edu.cn/"),
    ("yyxy", "园艺学院", "http://yyxy.syau.edu.cn/"),
    ("thxy", "土地与环境学院", "http://thxy.syau.edu.cn/"),
    ("jgxy", "经济管理学院", "http://jgxy.syau.edu.cn/"),
    ("myxy", "动物科学与医学学院", "http://myxy.syau.edu.cn/"),
    ("lin", "林学院", "http://lin.syau.edu.cn/"),
    ("gcxy", "工程学院", "http://gcxy.syau.edu.cn/"),
    ("xdxy", "信息与电气工程学院", "http://xdxy.syau.edu.cn/"),
    ("slxy", "水利学院", "http://slxy.syau.edu.cn/"),
    ("spxy", "食品学院", "http://spxy.syau.edu.cn/"),
    ("swxy", "生物科学技术学院", "http://swxy.syau.edu.cn/"),
    ("marx", "马克思主义学院", "https://marx.syau.edu.cn/"),
    ("jxjy", "继续教育学院", "http://jxjy.syau.edu.cn/"),
    ("gjjlc", "国际教育学院(国际交流处)", "http://gjjlc.syau.edu.cn/"),
    ("lxy", "理学院", "http://lxy.syau.edu.cn/"),
    ("tyb", "体育教学部", "http://tyb.syau.edu.cn/"),
    ("wyb", "外语教学部", "https://wyb.syau.edu.cn/"),
]

# 只在**导航链接文字**上匹配：宁可多给候选让人看，也不自动认定
KEYS = ["师资", "教师", "队伍", "导师", "教授", "人才", "团队", "名录", "简介", "教职工"]

A_RE = re.compile(r"<a\b[^>]*?href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", re.I | re.S)
TAG_RE = re.compile(r"<[^>]+>")
ENC_RE = re.compile(r"charset\s*=\s*[\"']?\s*([\w-]+)", re.I)


# 站点会挑「像蜘蛛的请求」：带 Cookie + Referer + gzip 协商，并对失败做退避重试。
# 之前被 `Remote end closed connection without response` 挡过一轮 —— 这是站点在自我保护，
# 我们不硬闯：重试 3 次、每次退避，仍然失败就**如实记进 problems**。
import http.cookiejar  # noqa: E402
import gzip           # noqa: E402

_COOKIES = http.cookiejar.CookieJar()
_OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(_COOKIES))


def fetch(url, timeout=25, retries=3):
    """返回 (status, body_text, content_type, final_url)；失败抛异常（已重试/退避）"""
    last = None
    for attempt in range(retries):
        try:
            return _fetch_once(url, timeout)
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(2.5 * (attempt + 1))
    raise last


def _fetch_once(url, timeout=25):
    host = urlparse(url).netloc
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.6",
            "Accept-Encoding": "gzip, deflate",
            "Referer": "http://%s/" % host,
            "Connection": "keep-alive",
            "Upgrade-Insecure-Requests": "1",
        },
    )
    with _OPENER.open(req, timeout=timeout) as r:
        raw = r.read()
        if (r.headers.get("Content-Encoding") or "").lower() == "gzip":
            raw = gzip.decompress(raw)
        ctype = r.headers.get("Content-Type", "")
        final = r.geturl()
    m = ENC_RE.search(ctype) or ENC_RE.search(raw[:2048].decode("ascii", "ignore"))
    enc = (m.group(1).lower() if m else "utf-8")
    if enc in ("gb2312", "gbk", "gb18030"):
        enc = "gb18030"
    try:
        text = raw.decode(enc, "replace")
    except LookupError:
        text = raw.decode("utf-8", "replace")
    return 200, text, ctype, final


def text_of(html):
    return re.sub(r"\s+", " ", TAG_RE.sub(" ", html)).strip()


def detect_stack(html):
    flags = []
    if "_sitegray" in html or "vsb" in html.lower():
        flags.append("VSB站群")
    if re.search(r"<meta[^>]+name=[\"']?generator[\"']?[^>]*content=[\"']?([^\"'>]+)", html, re.I):
        m = re.search(r"<meta[^>]+name=[\"']?generator[\"']?[^>]*content=[\"']?([^\"'>]+)", html, re.I)
        flags.append("generator=" + m.group(1).strip())
    if "wp-content" in html or "wp-json" in html:
        flags.append("WordPress")
    if "$(" in html and ("ajax" in html.lower() or ".load(" in html):
        flags.append("含AJAX")
    if "iframe" in html.lower():
        flags.append("含iframe")
    return flags


def candidates(html, base):
    out = []
    seen = set()
    for m in A_RE.finditer(html):
        href, inner = m.group(1), m.group(2)
        label = text_of(inner)
        if not label or len(label) > 24:
            continue
        if href.lower().startswith(("javascript:", "mailto:", "#")):
            continue
        if not any(k in label for k in KEYS):
            continue
        full = urljoin(base, href)
        if full in seen:
            continue
        seen.add(full)
        out.append({"text": label, "url": full})
    return out


def probe(code, name, url, refresh=False):
    d = os.path.join(CACHE, code)
    os.makedirs(d, exist_ok=True)
    f = os.path.join(d, "home.html")
    rec = {"code": code, "name": name, "home": url, "reachable": False,
           "status": None, "error": None, "bytes": 0, "stack": [],
           "candidates": [], "fetchedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z")}

    if os.path.exists(f) and not refresh:
        html = open(f, encoding="utf-8", errors="replace").read()
        rec["fromCache"] = True
    else:
        try:
            status, html, ctype, final = fetch(url)
            open(f, "w", encoding="utf-8").write(html)
            rec.update(status=status, finalUrl=final, contentType=ctype)
        except urllib.error.HTTPError as e:
            rec["error"] = "HTTP %s" % e.code
            return rec
        except Exception as e:  # noqa: BLE001 —— 网络异常都要如实记，不能静默跳过
            rec["error"] = "%s: %s" % (type(e).__name__, e)
            return rec
        time.sleep(DELAY)

    rec["reachable"] = True
    rec["bytes"] = len(html.encode("utf-8", "ignore"))
    rec["stack"] = detect_stack(html)
    rec["candidates"] = candidates(html, rec.get("finalUrl") or url)
    return rec


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default=None, help="只探某个学院 code")
    ap.add_argument("--refresh", action="store_true")
    args = ap.parse_args()

    rows = []
    for code, name, url in COLLEGES:
        if args.only and code != args.only:
            continue
        if not url:
            rows.append({"code": code, "name": name, "home": None, "reachable": False,
                         "error": "官方页面未给出独立站点", "candidates": []})
            continue
        r = probe(code, name, url, args.refresh)
        rows.append(r)
        flag = "OK " if r["reachable"] else "ERR"
        print("[%s] %-14s %-5s %s" % (flag, name, r.get("status") or "", r.get("error") or ""))
        for c in r["candidates"][:8]:
            print("      - %s  ->  %s" % (c["text"], c["url"]))
        sys.stdout.flush()

    if not args.only:
        with open(OUT, "w", encoding="utf-8") as fh:
            json.dump({"generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                       "note": "候选链接只来自各站首页导航，未做任何猜测", "colleges": rows},
                      fh, ensure_ascii=False, indent=2)
        print("\n-> %s" % OUT)


if __name__ == "__main__":
    main()
