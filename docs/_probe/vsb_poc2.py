# -*- coding: utf-8 -*-
"""VSB 通用解析器契约验证 PoC v2：加 同站过滤 + 日期必需 + 去噪"""
import os, re, urllib.parse, json

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "raw")
DATE_RE = re.compile(r"(20\d{2})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})")
HREF_ART = re.compile(r"(?:^|/)(?:info|content)/(\d+)/(\d+)\.htm", re.I)
HREF_JSP = re.compile(r"wbnewsid=(\d+)", re.I)
HREF_COL = re.compile(r"wbtreeid=(\d+)", re.I)
TAG = re.compile(r"<[^>]+>")
WS = re.compile(r"[\s\u3000\xa0]+")

SITES = [
    ("www/tzgg",    "https://www.syau.edu.cn/index/tzgg.htm"),
    ("news/snyw",   "https://news.syau.edu.cn/snyw.htm"),
    ("jwc/jwtz",    "https://jwc.syau.edu.cn/index/jwtz.htm"),
    ("xsc/tzgg",    "http://xsc.syau.edu.cn/tzgg.htm"),
    ("grs/tzgg",    "https://grs.syau.edu.cn/tzgg.htm"),
    ("zbxy/tzgg",   "http://zbxy.syau.edu.cn/index/tzgg.htm"),
    ("spxy/tzgg",   "http://spxy.syau.edu.cn/index/tzgg.htm"),
    ("lib/xwgg",    "https://lib.syau.edu.cn/index/xwgg.htm"),
    ("gzc/zbgg",    "https://gzc.syau.edu.cn/tzgg/zbgg.htm"),
    ("jwc/list1248","https://jwc.syau.edu.cn/list.jsp?urltype=tree.TreeTempUrl&wbtreeid=1248"),
]

def clean(s):
    return WS.sub(" ", TAG.sub("", s)).strip()

def parse_list(html, base):
    base_host = urllib.parse.urlsplit(base).netloc
    items, seen = [], set()
    for m in re.finditer(r"(?is)<a\b([^>]*)>(.*?)</a>", html):
        attrs, inner = m.group(1), m.group(2)
        hm = re.search(r'href\s*=\s*"([^"]*)"', attrs)
        if not hm:
            continue
        href = hm.group(1)
        abs_url = urllib.parse.urljoin(base, href)
        if urllib.parse.urlsplit(abs_url).netloc.lower() != base_host.lower():
            continue                                    # 规则1: 同站
        art = HREF_ART.search(href)
        if art:
            col, aid = art.group(1), art.group(2)
        else:
            jsp = HREF_JSP.search(href)
            if not jsp:
                continue
            aid = jsp.group(1)
            cm = HREF_COL.search(href)
            col = cm.group(1) if cm else ""
        if abs_url in seen:
            continue
        win = html[max(0, m.start() - 350): m.end() + 450]
        dm = DATE_RE.search(win)
        if not dm:
            continue                                    # 规则2: 邻域必需日期（导航/装饰链接天然无日期）
        seen.add(abs_url)
        date = f"{dm.group(1)}-{int(dm.group(2)):02d}-{int(dm.group(3)):02d}"
        title = ""
        tm = re.search(r'title\s*=\s*"([^"]*)"', attrs)
        if tm and len(clean(tm.group(1))) >= 4:
            title = clean(tm.group(1))
        if not title:
            for pat in (r"(?is)<h\d[^>]*>(.*?)</h\d>", r"(?is)<em[^>]*>(.*?)</em>"):
                mm = re.search(pat, inner)
                if mm and len(clean(mm.group(1))) >= 4:
                    title = clean(mm.group(1)); break
        if not title:
            title = clean(inner)
        title = re.sub(r"^\[[^\]]{1,20}\]", "", title).strip()
        title = re.sub(r"(\.{3}|…+)$", "", title).strip()
        items.append({"id": aid, "col": col, "title": title, "date": date,
                      "truncated": title.endswith(("...", "…")) or "..." in inner, "url": abs_url})
    return items

print(f"{'site':14} {'KB':>7} {'items':>5} {'uniq_col':>8} {'id_range':>17}  top3(id/date)")
print("-" * 100)
out = {}
for key, url in SITES:
    p = os.path.join(CACHE, key.replace("/", "_") + ".html")
    if not os.path.exists(p):
        print(f"{key:14} (no cache)"); continue
    body = open(p, "rb").read()
    html = body.decode("utf-8", "replace")
    items = parse_list(html, url)
    ids = sorted(int(i["id"]) for i in items) if items else []
    cols = sorted({i["col"] for i in items})
    out[key] = items
    rng = f"{ids[0]}-{ids[-1]}" if ids else "-"
    print(f"{key:14} {len(body)/1024:7.1f} {len(items):5} {','.join(cols)[:8]:>8} {rng:>17}  " +
          " | ".join(f"{i['id']}/{i['date']}" for i in items[:3]))

json.dump(out, open(os.path.join(CACHE, "v2.json"), "w", encoding="utf-8"),
          ensure_ascii=False, indent=1)
print("\n=== 抽查两条完整记录 ===")
for key in ("jwc/jwtz", "gzc/zbgg"):
    for it in out.get(key, [])[:2]:
        print(key, json.dumps(it, ensure_ascii=False))
