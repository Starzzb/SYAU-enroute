"""Second fetch pass: exception colleges + detail-page samples (read-only web).

Targets
  · yyxy / yjsjy1/dsdw.htm  导师队伍  — does 园艺 have per-teacher pages at all?
  · marx / xkjs/dsjj.htm    导师简介  — same question for 马院
  · spxy / info/1112/2821.htm        — 食品"导师风采"到底是一人一页还是名单文章
  · wyb  / info/1094/4588.htm        — 外语部个人页样本
  · tyb  / info/1019/1850.htm        — 体育部个人页样本
"""
import re
import time
import pathlib
import urllib.request

OUT = pathlib.Path(__file__).parent / "raw3"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")

URLS = [
    "https://yyxy.syau.edu.cn/yjsjy1/dsdw.htm",
    "https://marx.syau.edu.cn/xkjs/dsjj.htm",
    "https://spxy.syau.edu.cn/info/1112/2821.htm",
    "https://wyb.syau.edu.cn/info/1094/4588.htm",
    "https://tyb.syau.edu.cn/info/1019/1850.htm",
]


def decode(raw, cs):
    if cs:
        try:
            return raw.decode(cs, errors="replace")
        except LookupError:
            pass
    m = re.search(rb'charset\s*=\s*["\']?\s*([\w-]+)', raw[:4000], re.I)
    if m:
        try:
            return raw.decode(m.group(1).decode("ascii", "ignore"), errors="replace")
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
        arts = len(set(re.findall(r'info/(\d{3,5})/(\d+)\.htm', html)))
        cont = re.findall(r'id="(vsb_content[^"]*|WspDetailContent|v_news_content)"', html)
        print(f"{status} {len(html):>7} arts={arts:<4} cont={cont[:3]} {name} | {(t.group(1).strip() if t else '')[:36]}")
    except Exception as e:  # noqa: BLE001
        print(f"ERR {type(e).__name__}: {e}  {name}")
    time.sleep(0.4)
