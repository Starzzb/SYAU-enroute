"""Detect which content container each faculty detail page uses.

Usage: python detect-container.py [dir]
"""
import re, sys, pathlib
RAW = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).parent / "raw")

CAND = ["vsb_content", "v_news_content", "content", "article", "details", "js-content",
        "singleShow", "zoom", "TRS_Editor", "cont_", "text", "show"]

for f in sorted(RAW.glob("*.html")):
    html = f.read_text(encoding="utf-8", errors="replace")
    ids = re.findall(r'id="([^"]{1,40})"', html)
    cls = re.findall(r'class="([^"]{1,60})"', html)
    hits = []
    for c in CAND:
        n_id = sum(1 for i in ids if c.lower() in i.lower())
        n_cls = sum(1 for i in cls if c.lower() in i.lower())
        if n_id or n_cls:
            hits.append(f"{c}(id{n_id}/cls{n_cls})")
    # print the largest text block container guess
    print(f.name, "len", len(html))
    print("   ids:", " ".join(sorted(set(ids))[:40]))
    print("   hits:", " ".join(hits))
    print()
