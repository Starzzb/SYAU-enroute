"""Check whether 食品学院 has per-teacher detail pages at all (read-only)."""
import re
import time
import pathlib
import urllib.request

OUT = pathlib.Path(__file__).parent / "raw3"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")
URLS = [
    "https://spxy.syau.edu.cn/szdw.htm",
    "https://spxy.syau.edu.cn/szdw/jsml1.htm",
    "https://spxy.syau.edu.cn/szdw/cxrc.htm",
]


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


for u in URLS:
    name = re.sub(r'[^\w.\-]', '_', re.sub(r'^https?://', '', u))
    req = urllib.request.Request(u, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            raw, status, cs = r.read(), r.status, r.headers.get_content_charset()
        html = decode(raw, cs)
        (OUT / (name + ".html")).write_text(html, encoding="utf-8")
        t = re.search(r'<title>([^<]*)</title>', html, re.I)
        arts = list(dict.fromkeys(re.findall(r'info/(\d{3,5})/(\d+)\.htm', html)))
        cols = sorted({c for c, _ in arts})
        print(f"{status} {len(html):>7} arts={len(arts):<4} cols={cols} {name} | {(t.group(1).strip() if t else '')[:34]}")
    except Exception as e:  # noqa: BLE001
        print(f"ERR {type(e).__name__}: {e}  {name}")
    time.sleep(0.4)

# what is inside the 导师风采 list-article?
p = OUT / "spxy.syau.edu.cn_info_1112_2821.htm.html"
if p.exists():
    s = p.read_text(encoding="utf-8", errors="replace")
    m = re.search(r'id="vsb_content"', s)
    body = s[m.end():] if m else s
    txt = re.sub(r'(?s)<[^>]+>', ' ', body)
    txt = re.sub(r'\s+', ' ', txt)
    print("\n-- 导师风采 article text head:\n", txt[:900])
