"""Full href inventory for the five column pages (read-only)."""
import re
import pathlib
from collections import Counter

RAW = pathlib.Path(__file__).parent / "raw"
NAMES = ["col_thxy_zgjzc", "col_yyxy_szdw", "col_tyb_jsdw", "col_wyb_szdw", "col_marx_szll"]

HREF = re.compile(r'href\s*=\s*["\']([^"\']+)["\']', re.I)

for n in NAMES:
    html = (RAW / (n + ".html")).read_text(encoding="utf-8", errors="replace")
    hrefs = HREF.findall(html)
    print("=" * 100)
    print(n, "href total", len(hrefs))
    # anything that looks like a content/article target
    art = [h for h in hrefs if re.search(r'info/\d+/\d+\.htm|content\.jsp|wbnewsid=|showmsg|\.htm\?', h, re.I)]
    print("  article-ish hrefs:", len(art))
    for h in dict.fromkeys(art):
        print("    ", h)
    # list-ish / column-ish
    cols = [h for h in hrefs if re.search(r'list\.jsp|/index\.htm|\.htm$', h, re.I) and not re.search(
        r'info/\d+/\d+\.htm', h, re.I)]
    print("  other .htm hrefs (unique, first 25):")
    for h in list(dict.fromkeys(cols))[:25]:
        print("    ", h)
    # pagination-ish text
    for m in re.finditer(r'(共\s*\d+\s*条|\d+\s*/\s*\d+|下一页|尾页|createPageHTML)', html):
        print("   PAGING MARK:", m.group(0))
        break
    print()
