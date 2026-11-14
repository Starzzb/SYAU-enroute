"""Grab one 园艺 and one 马院 individual teacher page, then stop fetching."""
import re
import time
import pathlib
import urllib.request
import urllib.parse

R3 = pathlib.Path(__file__).parent / "raw3"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")


def decode(raw, cs):
    if cs:
        try:
            return raw.decode(cs, errors="replace")
        except LookupError:
            pass
    for c in ("utf-8", "gb18030"):
        try:
            return raw.decode(c)
        except UnicodeDecodeError:
            continue
    return raw.decode("gb18030", errors="replace")


def first_links(page, n=3):
    s = (R3 / page).read_text(encoding="utf-8", errors="replace")
    out = []
    for m in re.finditer(r'<a\b([^>]*)>(.*?)</a>', s, re.I | re.S):
        hm = re.search(r'href\s*=\s*["\']([^"\']+)["\']', m.group(1))
        if not hm or not re.search(r'info/\d+/\d+\.htm', hm.group(1)):
            continue
        txt = re.sub(r'<[^>]+>', '', m.group(2)).strip()
        alt = re.search(r'(?:alt|title)="([^"]{1,30})"', m.group(1))
        out.append((hm.group(1), (alt.group(1) if alt else txt)[:20]))
    return out[:n]


for page in ("yyxy.syau.edu.cn_yjsjy1_dsdw.htm.html", "marx.syau.edu.cn_xkjs_dsjj.htm.html"):
    print("==", page)
    for h, t in first_links(page):
        print("   ", h, "|", t)

base = {"yyxy.syau.edu.cn_yjsjy1_dsdw.htm.html": "https://yyxy.syau.edu.cn/yjsjy1/dsdw.htm",
        "marx.syau.edu.cn_xkjs_dsjj.htm.html": "https://marx.syau.edu.cn/xkjs/dsjj.htm"}

for page, b in base.items():
    links = first_links(page, 1)
    if not links:
        continue
    rel = links[0][0]
    url = rel if rel.startswith("http") else urllib.parse.urljoin(b, rel)
    name = re.sub(r'[^\w.\-]', '_', re.sub(r'^https?://', '', url))
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=25) as r:
        raw, status, cs = r.read(), r.status, r.headers.get_content_charset()
    html = decode(raw, cs)
    (R3 / (name + ".html")).write_text(html, encoding="utf-8")
    t = re.search(r'<title>([^<]*)</title>', html, re.I)
    print(status, url, "->", name, "|", t.group(1).strip() if t else "")
    time.sleep(0.5)

