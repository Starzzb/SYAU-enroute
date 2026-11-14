"""Why do some column pages yield zero article links? Read-only.

Examines col_marx_szll / col_wyb_szdw / col_yyxy_szdw (0 info/ links) and
col_tyb_jsdw (12 links, empty anchor text), plus pagination widgets.
"""
import re
import pathlib

RAW = pathlib.Path(__file__).parent / "raw"

TARGETS = ["col_marx_szll", "col_wyb_szdw", "col_yyxy_szdw", "col_tyb_jsdw"]


def dump(name, patterns, limit=6, width=200):
    p = RAW / (name + ".html")
    html = p.read_text(encoding="utf-8", errors="replace")
    print("=" * 100)
    print(name, len(html))
    for label, rx in patterns:
        hits = [m.group(0) for m in re.finditer(rx, html, re.I | re.S)][:limit]
        print(f"-- {label}  ({len(hits)} shown)")
        for h in hits:
            print("   ", re.sub(r"\s+", " ", h)[:width])
    print()


PATS = [
    ("href 全量样本(href=...)", r'href\s*=\s*["\'][^"\']+["\']'),
    ("表单/检索", r'<form[^>]*>'),
    ("js/ajax", r'(?:\.ajax|fetch\(|getJSON|xmlhttp|\$\.post)'),
    ("分页", r'(?:createPageHTML|fanye|GOPAGE|gotopage|pageNum|changePage)[^>]{0,80}'),
    ("iframe", r'<iframe[^>]*>'),
]

for t in TARGETS:
    dump(t, PATS)
