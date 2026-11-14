"""Field-level extraction pass over every teacher detail page in the corpus.

Purpose: the report's 字段抽取表 must be measured, not guessed. This script
behaves like the parser ticket 06 would ship:
  · pick the container with the LONGEST text (not the first match)
  · strip tags, normalise whitespace
  · probe each field with a permissive regex, record hit/miss + first hit
Read-only against local files.
"""
import re
import json
import pathlib

HERE = pathlib.Path(__file__).parent
SOURCES = [HERE / "raw", HERE / "raw3"]

CONTAINERS = [
    ("vsb_content*", r'id="(vsb_content[^"]*)"'),
    ("v_news_content", r'class="[^"]*\bv_news_content\b[^"]*"'),
    ("WspDetailContent", r'id="(WspDetailContent)"'),
]

CUT = ['id="div_vote_id"', 'class="footer', 'id="footer', '<!-- 底部',
       'class="share', 'id="share', 'class="qr_container', 'id="WspDetailTools"']

FIELDS = {
    "职称": r'(教授|副教授|讲师|助教|研究员|副研究员|高级实验师|实验师|工程师)',
    "导师资格": r'(博士生导师|硕士生导师|博导|硕导|博士和硕士生导师|博士、硕士生导师)',
    "邮箱": r'[\w.\-+]+@[\w\-]+\.[A-Za-z]{2,}',
    "办公电话": r'024[-\s]?8\d{6,7}',
    "手机": r'\b1[3-9]\d{9}\b',
    "办公室地点": r'(?:办公地点|办公室)[:：]?\s*([^\n，。；]{2,30})',
    "研究方向": r'(?:研究方向|研究领域|研究兴趣|主要从事)[:：]?\s*([^\n]{3,80})',
    "主讲课程": r'(?:主讲|讲授)([^\n]{2,80})',
    "研究生招生方向": r'(?:招生方向|研究生招生)[:：]?\s*([^\n]{2,60})',
    "出生年月": r'(\d{4}\s*年\s*\d{1,2}\s*月\s*生)',
    "性别": r'[，,]\s*([男女])\s*[，,]',
    "学位": r'(博士|硕士)(?:研究生)?(?:学历|学位)?',
    "通讯地址": r'(?:通讯地址|地址)[:：]\s*([^\n]{4,60})',
    "邮编": r'(110866|邮编[:：]\s*\d{6})',
    "教育/工作经历": r'(?:教育经历|学习和工作经历|受教育经历|工作经历|个人简况)',
    "论文": r'(?:发表.{0,6}论文|学术论文|代表性论文|论文)',
    "科研项目": r'(?:科研项目|主持.{0,8}项目|国家自然科学基金|国家重点研发|国家社科基金)',
    "获奖": r'(?:获奖|奖励与荣誉|科技进步奖|教学成果奖|自然科学奖)',
    "学术兼职": r'(?:学术兼职|社会兼职|兼任|学会)',
    "照片": r'<img[^>]+(?:WspContent|uploadfile|_upload)[^>]*>',
}


def to_text(html):
    s = re.sub(r"(?is)<(script|style|noscript)\b.*?</\1>", " ", html)
    s = re.sub(r"(?i)<br\s*/?>", "\n", s)
    s = re.sub(r"(?i)</(p|div|li|h1|h2|h3|h4|tr|dt|dd)>", "\n", s)
    s = re.sub(r"(?i)</td>", " | ", s)
    s = re.sub(r"(?s)<[^>]+>", "", s)
    for k, v in (("&nbsp;", " "), ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"),
                 ("&quot;", '"'), ("&ldquo;", "“"), ("&rdquo;", "”"), ("&mdash;", "—"),
                 ("&hellip;", "…"), ("&middot;", "·")):
        s = s.replace(k, v)
    s = re.sub(r"&#(\d{2,5});", lambda m: chr(int(m.group(1))), s)
    s = s.replace("\r", "")
    s = re.sub(r"[ \t\u3000]+", " ", s)
    s = re.sub(r"\n[ ]+", "\n", s)
    s = re.sub(r"\n{2,}", "\n", s)
    return s.strip()


def best_body(html):
    """Return (container_name, text) with the longest extracted text."""
    best = (None, "")
    for name, rx in CONTAINERS:
        m = re.search(rx, html, re.I)
        if not m:
            continue
        body = html[m.end():]
        cut = min((i for i in (body.find(k) for k in CUT) if i > 200), default=-1)
        if cut > 0:
            body = body[:cut]
        txt = to_text(body)
        if len(txt) > len(best[1]):
            best = (name, txt)
    return best


def page_name(html, fname):
    m = re.search(r'id="WspDetailTitle"[^>]*>(.*?)</', html, re.I | re.S)
    if m:
        n = to_text(m.group(1)).strip()
        if n:
            return n[:20]
    t = re.search(r'<title>([^<]*)</title>', html, re.I)
    if t:
        return re.split(r'[-—]', t.group(1).strip())[0][:20]
    return fname


def analyse(path):
    html = path.read_text(encoding="utf-8", errors="replace")
    container, text = best_body(html)
    hits = {}
    for field, rx in FIELDS.items():
        m = re.search(rx, text, re.I)
        hits[field] = bool(m) if field != "照片" else bool(re.search(rx, html, re.I))
    samples = {}
    for field in ("邮箱", "办公电话", "办公室地点", "研究方向"):
        m = re.search(FIELDS[field], text, re.I)
        if m:
            samples[field] = (m.group(1) if m.groups() else m.group(0))[:40]
    return {
        "file": path.name,
        "teacher": page_name(html, path.stem),
        "container": container,
        "text_len": len(text),
        "hits": hits,
        "samples": samples,
    }


rows = []
for src in SOURCES:
    if not src.exists():
        continue
    for p in sorted(src.glob("*.html")):
        n = p.name
        # only pages that look like a single-teacher detail page
        if src.name == "raw" and not n.startswith("det_"):
            continue
        if src.name == "raw3":
            if not re.search(r'_info_\d+_\d+\.htm\.html$', n):
                # still allow the 食品 list-article sample
                if "spxy.syau.edu.cn_info_1112_2821" not in n:
                    continue
        rows.append(analyse(p))

totals = {}
for r in rows:
    for f, v in r["hits"].items():
        totals.setdefault(f, 0)
        if v:
            totals[f] += 1

print(json.dumps({"pages": rows,
                  "n_pages": len(rows),
                  "field_hits": totals}, ensure_ascii=False, indent=1))
