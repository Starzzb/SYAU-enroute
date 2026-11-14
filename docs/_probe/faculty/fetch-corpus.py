"""Polite fetch of the faculty-corpus URLs with correct GBK/UTF-8 decoding.

Why a Python fetcher: PowerShell 5.1 decodes these VSB pages as Latin-1, which
turned every Chinese title into mojibake and silently broke keyword matching.
Python + explicit charset sniffing keeps the corpus honest.

Writes UTF-8 copies into docs/_probe/faculty/raw3/ . Read-only against the web.
"""
import re
import time
import pathlib
import urllib.request
import urllib.error

OUT = pathlib.Path(__file__).parent / "raw3"
OUT.mkdir(exist_ok=True)
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/124.0 Safari/537.36")

URLS = [
    # homepages (nav evidence)
    "https://nxy.syau.edu.cn/",
    "https://zbxy.syau.edu.cn/",
    "https://yyxy.syau.edu.cn/",
    "https://thxy.syau.edu.cn/",
    "https://jgxy.syau.edu.cn/",
    "https://myxy.syau.edu.cn/",
    "https://lin.syau.edu.cn/",
    "https://gcxy.syau.edu.cn/",
    "https://xdxy.syau.edu.cn/",
    "https://slxy.syau.edu.cn/",
    "https://spxy.syau.edu.cn/",
    "https://swxy.syau.edu.cn/",
    "https://marx.syau.edu.cn/",
    "https://lxy.syau.edu.cn/",
    "https://tyb.syau.edu.cn/",
    "https://wyb.syau.edu.cn/",
    # faculty columns
    "https://nxy.syau.edu.cn/szdw1.htm",
    "https://zbxy.syau.edu.cn/xkjs/szll.htm",
    "https://yyxy.syau.edu.cn/xkjs/szdw.htm",
    "https://thxy.syau.edu.cn/szdw/jsxl/zgjzc.htm",
    "https://thxy.syau.edu.cn/szdw/bssds.htm",
    "https://jgxy.syau.edu.cn/szdw/bssds.htm",
    "https://jgxy.syau.edu.cn/szdw/xz_jfry.htm",
    "https://myxy.syau.edu.cn/szdw/js.htm",
    "https://myxy.syau.edu.cn/szdw/xwbssds.htm",
    "https://lin.syau.edu.cn/szdw/zyjs.htm",
    "https://gcxy.syau.edu.cn/szdw1/jsml.htm",
    "https://xdxy.syau.edu.cn/szdw.htm",
    "https://slxy.syau.edu.cn/szdw2.htm",
    "https://spxy.syau.edu.cn/szdw/zysz.htm",
    "https://spxy.syau.edu.cn/szdw/dsfc.htm",
    "https://swxy.syau.edu.cn/szdw/zzjs.htm",
    "https://marx.syau.edu.cn/szll/sxddyfzjys.htm",
    "https://lxy.syau.edu.cn/szdw1.htm",
    "https://tyb.syau.edu.cn/bmgk/jsdw.htm",
    "https://wyb.syau.edu.cn/szdw.htm",
    "https://wyb.syau.edu.cn/szdw/szdw.htm",
]


def decode(raw, header_charset):
    if header_charset:
        try:
            return raw.decode(header_charset, errors="replace")
        except LookupError:
            pass
    m = re.search(rb'charset\s*=\s*["\']?\s*([\w-]+)', raw[:4000], re.I)
    if m:
        cs = m.group(1).decode("ascii", "ignore")
        try:
            return raw.decode(cs, errors="replace")
        except LookupError:
            pass
    for cs in ("utf-8", "gb18030"):
        try:
            return raw.decode(cs)
        except UnicodeDecodeError:
            continue
    return raw.decode("gb18030", errors="replace")


def slug(u):
    s = re.sub(r'^https?://', '', u)
    s = re.sub(r'[^\w.\-]', '_', s)
    return s or "root"


def main():
    for u in URLS:
        name = slug(u)
        req = urllib.request.Request(u, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                raw = r.read()
                status = r.status
                cs = r.headers.get_content_charset()
            html = decode(raw, cs)
            (OUT / (name + ".html")).write_text(html, encoding="utf-8")
            t = re.search(r'<title>([^<]*)</title>', html, re.I)
            print(f"{status} {len(html):>7}  {name:<62} {(t.group(1).strip() if t else '')[:40]}")
        except urllib.error.HTTPError as e:
            print(f"HTTP {e.code}  {name}")
        except Exception as e:  # noqa: BLE001
            print(f"ERR {type(e).__name__}: {e}  {name}")
        time.sleep(0.4)


if __name__ == "__main__":
    main()
