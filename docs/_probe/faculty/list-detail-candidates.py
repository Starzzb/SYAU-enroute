"""List candidate detail URLs found on each saved column page (read-only)."""
import re
import pathlib

R3 = pathlib.Path(__file__).parent / "raw3"
HREF = re.compile(r'href\s*=\s*["\']([^"\']+)["\']', re.I)

for p in sorted(R3.glob("*.htm.html")):
    s = p.read_text(encoding="utf-8", errors="replace")
    links = []
    for m in re.finditer(r'<a\b([^>]*)>(.*?)</a>', s, re.I | re.S):
        hm = HREF.search(m.group(1))
        if not hm or not re.search(r'info/\d+/\d+\.htm', hm.group(1)):
            continue
        txt = re.sub(r'<[^>]+>', '', m.group(2)).strip()
        alt = re.search(r'(?:alt|title)="([^"]{1,30})"', m.group(1), re.I)
        links.append((hm.group(1), (alt.group(1) if alt else txt)[:24]))
    print("==", p.name, "->", len(links))
    for h, t in links[:4]:
        print("    ", h, "|", t)
