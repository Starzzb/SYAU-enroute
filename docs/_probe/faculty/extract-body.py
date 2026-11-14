"""Extract the real content container of a VSB page.

Why this exists (2026-11-13 probe finding):
    app/src/main/java/cn/dkarbon/enroute/data/feed/FeedParser.kt anchors
    `id="vsb_content"` **exactly**. College teacher pages actually emit
    `vsb_content_2`, `vsb_content_501`, `vsb_content_7`, `vsb_content_1042`,
    `vsb_content_1391_u81`, or wrap the text in `id="WspDetailContent"`.
    So the news parser returns empty on every faculty detail page.

Usage:
    python extract-body.py <file.html> [<file2.html> ...]
    python extract-body.py --all          # every det_*.html in ./raw

Read-only.
"""
import re
import sys
import pathlib

HERE = pathlib.Path(__file__).parent

# Ordered by observed frequency on syau college sites.
CONTAINER_RES = [
    ("WspDetailContent", re.compile(r'id="WspDetailContent"', re.I)),
    ("v_news_content",   re.compile(r'class="[^"]*\bv_news_content\b[^"]*"', re.I)),
    ("vsb_content_*",    re.compile(r'id="vsb_content[^"]*"', re.I)),
    ("WspDetail",        re.compile(r'id="WspDetail"', re.I)),
]

CUT_MARKERS = [
    'id="div_vote_id"', 'class="footer', 'id="footer', '<!-- 底部',
    'class="main_bottom', 'class="share', 'id="share', 'id="WspDetailTools"',
    'class="qr_container', 'id="comment"',
]


def find_container(html):
    for name, rx in CONTAINER_RES:
        m = rx.search(html)
        if not m:
            continue
        tag_end = html.find(">", m.end())
        if tag_end < 0:
            continue
        body = html[tag_end + 1: tag_end + 1 + 150_000]
        cut = min((i for i in (body.find(k) for k in CUT_MARKERS) if i > 200), default=-1)
        if cut > 0:
            body = body[:cut]
        return name, body
    return None, ""


def to_text(html):
    s = re.sub(r"(?is)<(script|style|noscript)\b.*?</\1>", " ", html)
    s = re.sub(r"(?i)<br\s*/?>", "\n", s)
    s = re.sub(r"(?i)</(p|div|li|h1|h2|h3|h4|tr|dt|dd)>", "\n", s)
    s = re.sub(r"(?i)</td>", " | ", s)
    s = re.sub(r"(?s)<[^>]+>", "", s)
    for k, v in (("&nbsp;", " "), ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"),
                 ("&quot;", '"'), ("&#39;", "'"), ("&ldquo;", "“"), ("&rdquo;", "”"),
                 ("&mdash;", "—"), ("&hellip;", "…"), ("&middot;", "·")):
        s = s.replace(k, v)
    s = re.sub(r"&#(\d{2,5});", lambda m: chr(int(m.group(1))), s)
    s = s.replace("\r", "")
    s = re.sub(r"[ \t\u3000]+", " ", s)
    s = re.sub(r"\n[ ]+", "\n", s)
    s = re.sub(r"\n{2,}", "\n", s)
    return s.strip()


if __name__ == "__main__":
    args = sys.argv[1:]
    files = sorted((HERE / "raw").glob("det_*.html")) if args and args[0] == "--all" \
        else [pathlib.Path(a) for a in args]
    for f in files:
        html = f.read_text(encoding="utf-8", errors="replace")
        name, raw = find_container(html)
        text = to_text(raw)
        print("=" * 92)
        print(f"{f.name}  container={name}  raw={len(raw)}  text={len(text)}")
        print(text[:3000])
        print()
