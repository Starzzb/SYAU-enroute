"""Dump the readable body of the three 'name-list' column pages (read-only)."""
import re
import pathlib

RAW = pathlib.Path(__file__).parent / "raw"
NAMES = ["col_yyxy_szdw", "col_wyb_szdw", "col_marx_szll"]

CUT = ['id="div_vote_id"', 'class="footer', 'id="footer', '<!-- 底部', 'class="share']


def to_text(html):
    s = re.sub(r"(?is)<(script|style|noscript)\b.*?</\1>", " ", html)
    s = re.sub(r"(?i)<br\s*/?>", "\n", s)
    s = re.sub(r"(?i)</(p|div|li|h1|h2|h3|h4|tr|dt|dd|td)>", "\n", s)
    s = re.sub(r"(?s)<[^>]+>", "", s)
    for k, v in (("&nbsp;", " "), ("&amp;", "&"), ("&ldquo;", "“"), ("&rdquo;", "”")):
        s = s.replace(k, v)
    s = re.sub(r"[ \t\u3000]+", " ", s)
    s = re.sub(r"\n{2,}", "\n", s)
    return s.strip()


for n in NAMES:
    html = (RAW / (n + ".html")).read_text(encoding="utf-8", errors="replace")
    m = re.search(r'id="(vsb_content[^"]*|WspDetailContent)"', html, re.I)
    print("=" * 100)
    print(n, "container found:", bool(m), m.group(1) if m else None)
    if m:
        body = html[m.end():]
        cut = min((i for i in (body.find(k) for k in CUT) if i > 200), default=-1)
        if cut > 0:
            body = body[:cut]
        text = to_text(body)
        print("-- text len", len(text))
        print(text[:2500])
    # also: images with alt or title (photo-grid style listing)
    imgs = re.findall(r'<img[^>]+(?:alt|title)="([^"]{2,30})"[^>]*>', html, re.I)
    print("-- img alt/title values:", imgs[:20])
    print()
