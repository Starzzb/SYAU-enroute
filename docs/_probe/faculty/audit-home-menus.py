"""Do the college homepages expose a faculty column link in plain HTML? (read-only)"""
import re
import pathlib

RAW2 = pathlib.Path(__file__).parent / "raw2"
HREF = re.compile(r'href\s*=\s*["\']([^"\']+)["\']', re.I)

for p in sorted(RAW2.glob("home_*.html")):
    s = p.read_text(encoding="utf-8", errors="replace")
    hrefs = list(dict.fromkeys(HREF.findall(s)))
    fac = [h for h in hrefs if re.search(r'szdw|szll|jsdw|jsxl', h, re.I)]
    # also dump every anchor whose inner text has any CJK and looks like a menu entry
    menus = []
    for m in re.finditer(r'<a\b([^>]*)>(.*?)</a>', s, re.I | re.S):
        txt = re.sub(r'<[^>]+>', '', m.group(2))
        txt = re.sub(r'\s+', '', txt)
        if 2 <= len(txt) <= 8 and re.search(r'[\u4e00-\u9fff]', txt):
            href = (HREF.search(m.group(1)) or [None, ''])[1]
            if href and not href.lower().startswith('javascript'):
                menus.append(f"{txt}->{href}")
    print("=" * 90)
    print(p.name, "hrefs:", len(hrefs), "| faculty-ish:", len(fac))
    for h in fac[:10]:
        print("   FAC", h)
    print("   menus(first 20):", " | ".join(dict.fromkeys(menus))[:700])
