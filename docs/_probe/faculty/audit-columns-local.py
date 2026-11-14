"""Analyse the saved column pages (raw2/col_*.html) for list-page rules & paging.

Read-only. Column pages were fetched 2026-11-13 by probe-columns-phase-a.ps1.
"""
import re
import pathlib
import json

RAW2 = pathlib.Path(__file__).parent / "raw2"
HREF = re.compile(r'href\s*=\s*["\']([^"\']+)["\']', re.I)

PAGING = [
    ("共N条", r'共\s*(\d+)\s*条'),
    ("共N页", r'共\s*(\d+)\s*页'),
    ("N/M页", r'(\d+)\s*/\s*(\d+)\s*页'),
    ("子页 X.htm", r'href="((?:\d+|index_\d+)\.htm)"'),
    ("JS 跳页", r'GOPAGE|gotopagebut|fanye\d*|changePage|createPageHTML'),
]


def stats(p):
    html = p.read_text(encoding="utf-8", errors="replace")
    t = re.search(r'<title>([^<]*)</title>', html, re.I)
    arts = re.findall(r'info/(\d{3,5})/(\d+)\.htm', html)
    cols = sorted({c for c, _ in arts})
    paging = {}
    for label, rx in PAGING:
        m = re.findall(rx, html, re.I)
        if m:
            paging[label] = m[:4] if label != "JS 跳页" else len(m)
    # anchor shape families for the article links
    fam = {"img": 0, "em": 0, "plain": 0, "title_attr": 0}
    for m in re.finditer(r'<a\b([^>]*)>(.*?)</a>', html, re.I | re.S):
        attrs, inner = m.group(1), m.group(2)
        if not re.search(r'info/\d+/\d+\.htm', attrs):
            continue
        if 'title=' in attrs.lower():
            fam["title_attr"] += 1
        if '<img' in inner.lower():
            fam["img"] += 1
        elif '<em' in inner.lower():
            fam["em"] += 1
        elif re.sub(r'<[^>]+>', '', inner).strip():
            fam["plain"] += 1
    # richtext fallback: is there a content container with real text?
    cont = re.search(r'id="(vsb_content[^"]*|WspDetailContent|v_news_content)"', html, re.I)
    return {
        "file": p.name,
        "title": (t.group(1).strip() if t else ""),
        "articles": len(set(a for _, a in arts)),
        "links": len(arts),
        "column_ids": cols,
        "paging": paging,
        "anchor_family": fam,
        "container": cont.group(1) if cont else None,
    }


out = [stats(p) for p in sorted(RAW2.glob("col_*.html"))]
print(json.dumps(out, ensure_ascii=False, indent=1))
