"""Dump plain text of the vsb_content block for each faculty sample.

Usage: python dump-body.py [dir]
Read-only: prints to stdout.
"""
import re, sys, pathlib

RAW = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else pathlib.Path(__file__).parent / "raw")

def strip(html):
    html = re.sub(r"(?is)<(script|style|noscript)\b.*?</\1>", " ", html)
    html = re.sub(r"(?i)<br\s*/?>", "\n", html)
    html = re.sub(r"(?i)</(p|div|li|h1|h2|h3|h4|tr|dt|dd)>", "\n", html)
    html = re.sub(r"(?i)</td>", " | ", html)
    html = re.sub(r"(?s)<[^>]+>", "", html)
    for k, v in (("&nbsp;", " "), ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"),
                 ("&quot;", '"'), ("&#39;", "'"), ("&ldquo;", "“"), ("&rdquo;", "”"),
                 ("&mdash;", "—"), ("&hellip;", "…"), ("&middot;", "·")):
        html = html.replace(k, v)
    html = re.sub(r"&#(\d{2,5});", lambda m: chr(int(m.group(1))), html)
    html = html.replace("\r", "")
    html = re.sub(r"[ \t\u3000]+", " ", html)
    html = re.sub(r"\n[ ]+", "\n", html)
    html = re.sub(r"\n{2,}", "\n", html)
    return html.strip()

def body(html):
    i = html.find('id="vsb_content"')
    if i < 0:
        return ""
    j = html.find(">", i)
    w = html[j + 1: j + 1 + 120000]
    for mk in ('id="div_vote_id"', 'class="footer', 'id="footer', '<!-- 底部',
               'class="main_bottom', 'class="share', 'id="share', "</div>\n<script"):
        k = w.find(mk)
        if k >= 0:
            w = w[:k]
            break
    return w

for f in sorted(RAW.glob("det_*.html")):
    html = f.read_text(encoding="utf-8", errors="replace")
    t = re.search(r"<title>([^<]*)</title>", html)
    print("=" * 90)
    print(f.name, "|", (t.group(1).strip() if t else ""), "| bytes", len(html))
    b = strip(body(html))
    print(b[:2600] if b else "!! NO vsb_content")
    print()
