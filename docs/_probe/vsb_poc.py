# -*- coding: utf-8 -*-
"""VSB 通用解析器契约验证 PoC：一套启发式规则跑多站点"""
import os, re, time, urllib.request, json, io, sys

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw")
os.makedirs(CACHE, exist_ok=True)

SITES = [
    ("www/tzgg",   "https://www.syau.edu.cn/index/tzgg.htm"),
    ("www/list1144","https://www.syau.edu.cn/list.jsp?urltype=tree.TreeTempUrl&wbtreeid=1144"),
    ("news/snyw",  "https://news.syau.edu.cn/snyw.htm"),
    ("jwc/jwtz",   "https://jwc.syau.edu.cn/index/jwtz.htm"),
    ("xsc/tzgg",   "http://xsc.syau.edu.cn/tzgg.htm"),
    ("grs/tzgg",   "https://grs.syau.edu.cn/tzgg.htm"),
    ("zbxy/tzgg",  "http://zbxy.syau.edu.cn/index/tzgg.htm"),
    ("spxy/tzgg",  "http://spxy.syau.edu.cn/index/tzgg.htm"),
    ("lib/xwgg",   "https://lib.syau.edu.cn/index/xwgg.htm"),
    ("gzc/zbgg",   "https://gzc.syau.edu.cn/tzgg/zbgg.htm"),
]

DATE_RE = re.compile(r"(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})")
HREF_ART = re.compile(r"(?:^|/)(?:info|content)/(\d+)/(\d+)\.htm", re.I)
HREF_JSP = re.compile(r"wbnewsid=(\d+)", re.I)
HREF_COL = re.compile(r"wbtreeid=(\d+)", re.I)
TAG = re.compile(r"<[^>]+>")
WS = re.compile(r"[\s\u3000\xa0]+")

def fetch(url, key):
    path = os.path.join(CACHE, key.replace("/", "_") + ".html")
    if os.path.exists(path):
        return open(path, "rb").read(), "cache"
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Encoding": "identity"})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read()
        code = r.status
    open(path, "wb").write(body)
    time.sleep(0.8)
    return body, f"http {code}"

def decode(body):
    head = body[:2048].decode("ascii", "ignore").lower()
    for enc in ("utf-8", "gb18030"):
        if f'charset="{enc}"' in head or f"charset={enc}" in head:
            break
    else:
        enc = "utf-8"
    try:
        return body.decode(enc), enc
    except UnicodeDecodeError:
        return body.decode("gb18030", "replace"), "gb18030?"

def clean(s):
    return WS.sub(" ", TAG.sub("", s)).strip()

def parse_list(html, base):
    """通用契约：不依赖任何 class/id，只用 href 语义 + 邻域文本"""
    items, seen = [], set()
    for m in re.finditer(r"(?is)<a\b([^>]*)>(.*?)</a>", html):
        attrs, inner = m.group(1), m.group(2)
        href_m = re.search(r'href\s*=\s*"([^"]*)"', attrs)
        if not href_m:
            continue
        href = href_m.group(1)
        art = HREF_ART.search(href) or None
        if art:
            col, aid = art.group(1), art.group(2)
        else:
            jsp = HREF_JSP.search(href)
            if not jsp:
                continue
            aid = jsp.group(1)
            col = (HREF_COL.search(href).group(1) if HREF_COL.search(href) else "")
        if aid in seen:
            continue
        seen.add(aid)
        # 标题：title 属性 > heading > em > 锚文本
        title = ""
        tm = re.search(r'title\s*=\s*"([^"]*)"', attrs)
        if tm and len(clean(tm.group(1))) >= 4:
            title = clean(tm.group(1))
        if not title:
            for pat in (r"(?is)<h\d[^>]*>(.*?)</h\d>", r"(?is)<em[^>]*>(.*?)</em>"):
                mm = re.search(pat, inner)
                if mm and len(clean(mm.group(1))) >= 4:
                    title = clean(mm.group(1))
                    break
        if not title:
            title = clean(inner)
        title = re.sub(r"^\[[^\]]{1,20}\]", "", title).strip()
        title = re.sub(r"(\.{3}|…+)$", "", title).strip()
        # 日期：锚点前 300 / 后 400 字符窗口
        win = html[max(0, m.start() - 300): m.end() + 400]
        dm = DATE_RE.search(win)
        date = f"{dm.group(1)}-{int(dm.group(2)):02d}-{int(dm.group(3)):02d}" if dm else ""
        items.append({"id": aid, "col": col, "title": title, "date": date,
                      "url": urllib.parse.urljoin(base, href)})
    return items

import urllib.parse
report = []
for key, url in SITES:
    try:
        body, how = fetch(url, key)
        html, enc = decode(body)
        items = parse_list(html, url)
        total = re.search(r"共\s*(\d+)\s*条", html)
        dates = sum(1 for i in items if i["date"])
        report.append({"site": key, "how": how, "kb": round(len(body) / 1024, 1), "enc": enc,
                       "items": len(items), "with_date": dates,
                       "total_in_page": total.group(1) if total else "",
                       "sample": items[0] if items else None})
        print(f"[{key:14}] {how:9} {len(body)/1024:6.1f}KB {enc:8} items={len(items):3} dated={dates:3} total={total.group(1) if total else '-'}")
        if items:
            s = items[0]
            print(f"                 #1 id={s['id']} col={s['col']} date={s['date']} title={s['title'][:46]}")
    except Exception as e:
        print(f"[{key:14}] FAIL {type(e).__name__}: {e}")

json.dump(report, open(os.path.join(CACHE, "report.json"), "w", encoding="utf-8"),
          ensure_ascii=False, indent=1)
