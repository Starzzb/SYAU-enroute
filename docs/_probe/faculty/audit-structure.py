"""Structural audit of the faculty probe corpus (read-only).

Answers the questions ticket 05 still needs:
  · list pages: which link shape, how many items, pagination mechanism
  · detail pages: which container ids/classes actually appear, do fields exist
  · is one parser enough, and where are the exceptions

Usage:  python audit-structure.py
Read-only, prints to stdout.
"""
import re
import sys
import pathlib
import json

HERE = pathlib.Path(__file__).parent
RAW = HERE / "raw"

LINK_RX = re.compile(r'<a\b([^>]*)>(.*?)</a>', re.I | re.S)
HREF_RX = re.compile(r'href\s*=\s*["\']([^"\']+)["\']', re.I)
TITLE_ATTR_RX = re.compile(r'title\s*=\s*["\']([^"\']*)["\']', re.I)
DATE_RX = re.compile(r'\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b')


def strip_tags(s):
    s = re.sub(r'(?is)<(script|style)\b.*?</\1>', ' ', s)
    s = re.sub(r'(?s)<[^>]+>', '', s)
    s = s.replace('&nbsp;', ' ')
    return re.sub(r'\s+', ' ', s).strip()


def audit_list(path):
    html = path.read_text(encoding='utf-8', errors='replace')
    info_links = re.findall(r'info/(\d{3,5})/(\d+)\.htm', html)
    jsp_links = re.findall(r'content\.jsp\?[^"\']*wbnewsid=(\d+)', html)
    col_ids = sorted({c for c, _ in info_links})
    art_ids = [a for _, a in info_links]
    # pagination evidence
    paging = []
    for pat, label in (
        (r'(?:共|总共)\s*(\d+)\s*条', '共N条'),
        (r'共\s*(\d+)\s*页', '共N页'),
        (r'(\d+)\s*/\s*(\d+)\s*页', 'N/M页'),
        (r'index_(\d+)\.htm', 'index_N.htm'),
        (r'changePage|GOPAGE|gotopagebut|fanye\d*|pageNum', 'JS翻页控件'),
        (r'createPageHTML|wbnewsid=|list\.jsp', 'list.jsp 分页'),
    ):
        m = re.findall(pat, html, re.I)
        if m:
            paging.append(f'{label}:{len(m)}')
    # sample list anchors that look like teacher entries
    anchors = []
    for m in LINK_RX.finditer(html):
        attrs, inner = m.group(1), m.group(2)
        href = (HREF_RX.search(attrs) or [None, ''])[1] if HREF_RX.search(attrs) else ''
        if not re.search(r'info/\d+/\d+\.htm|content\.jsp', href):
            continue
        t = TITLE_ATTR_RX.search(attrs)
        label = strip_tags(inner)
        anchors.append({
            'href': href,
            'title_attr': t.group(1).strip() if t else '',
            'text': label[:40],
            'has_img': '<img' in inner.lower(),
            'has_em': '<em' in inner.lower(),
        })
    dates = DATE_RX.findall(html)
    return {
        'file': path.name,
        'bytes': len(html),
        'info_link_count': len(info_links),
        'unique_articles': len(set(art_ids)),
        'column_ids': col_ids,
        'jsp_links': len(jsp_links),
        'paging_evidence': paging,
        'date_hits': len(dates),
        'anchor_samples': anchors[:6],
        'anchor_total': len(anchors),
    }


CONTAINER_CANDIDATES = [
    'vsb_content', 'vsb_content_2', 'vsb_content_4', 'vsb_content_7',
    'vsb_content_501', 'vsb_content_1042', 'vsb_content_1391_u81',
    'v_news_content', 'WspDetailContent', 'WspDetail', 'WspDetailBox',
    'WspDetailTitle',
]

FIELD_PATTERNS = {
    '职称(教授/副教授/讲师/研究员)': r'(教授|副教授|讲师|助教|研究员|副研究员|高级实验师|实验师)',
    '邮箱': r'[\w.\-+]+@[\w\-]+\.[a-z]{2,}',
    '电话': r'(?:024[-\s]?\d{7,8}|1[3-9]\d{9}|电话[:：]\s*[\d\-]+)',
    '办公地点/办公室': r'(?:办公地点|办公室|室\b|楼\d*室)',
    '研究方向': r'(?:研究方向|研究领域|研究兴趣|主要从事)',
    '主讲课程': r'(?:主讲|讲授|课程)',
    '研究生招生方向': r'(?:招生方向|研究生招生)',
    '教育/工作经历': r'(?:教育经历|学习和工作经历|受教育经历|工作经历|个人简况)',
    '论文': r'(?:发表论文|学术论文|代表性论文|论文)',
    '项目': r'(?:科研项目|主持.{0,6}项目|国家自然科学基金|国家重点研发)',
    '获奖': r'(?:获奖|奖励|科技进步奖|教学成果奖)',
    '出生年月': r'(19\d{2}\s*年\s*\d{1,2}\s*月生|\d{4}年\d{1,2}月生)',
    '性别': r'[，,]\s*[男女]\s*[，,]',
    '学位': r'(博士|硕士)(?:研究生)?(?:学历|学位)?',
    '通讯地址/邮编': r'(?:通讯地址|邮编|邮政编码|地址[:：])',
    '导师资格': r'(?:博士生导师|硕士生导师|博导|硕导|博士和硕士生导师)',
}


def audit_detail(path):
    html = path.read_text(encoding='utf-8', errors='replace')
    present = []
    for c in CONTAINER_CANDIDATES:
        if re.search(r'id="%s"' % re.escape(c), html, re.I) or \
           re.search(r'class="[^"]*\b%s\b[^"]*"' % re.escape(c), html, re.I):
            present.append(c)
    # take the first usable container body for field scanning
    body = html
    m = re.search(r'id="(WspDetailContent|v_news_content|vsb_content[^"]*)"', html, re.I)
    if not m:
        m = re.search(r'class="[^"]*\bv_news_content\b[^"]*"', html, re.I)
    if m:
        body = html[m.end():]
    text = strip_tags(body)
    fields = {k: bool(re.search(v, text, re.I)) for k, v in FIELD_PATTERNS.items()}
    # best-guess title for the sample
    t = re.search(r'<title>([^<]*)</title>', html, re.I)
    wdt = re.search(r'id="WspDetailTitle"[^>]*>(.*?)</', html, re.I | re.S)
    return {
        'file': path.name,
        'bytes': len(html),
        'title': (t.group(1).strip() if t else ''),
        'WspDetailTitle': strip_tags(wdt.group(1))[:40] if wdt else '',
        'containers': present,
        'vsb_content_exact': bool(re.search(r'id="vsb_content"', html)),
        'body_text_len': len(text),
        'body_text_head': text[:120],
        'fields': fields,
        'has_iframe': '<iframe' in html.lower(),
        'has_pdf': bool(re.search(r'\.pdf\b', html, re.I)),
        'has_tables': text.count('|') > 0,
    }


def main():
    cols = sorted(RAW.glob('col_*.html'))
    dets = sorted(RAW.glob('det_*.html'))
    out = {'list_pages': [], 'detail_pages': []}
    for p in cols:
        out['list_pages'].append(audit_list(p))
    for p in dets:
        out['detail_pages'].append(audit_detail(p))
    print(json.dumps(out, ensure_ascii=False, indent=1))


if __name__ == '__main__':
    main()
