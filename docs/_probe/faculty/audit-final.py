"""Final structural analysis over the correctly-decoded corpus (raw3/).

Produces: per-college faculty column(s), list-page rule, article counts,
pagination evidence, anchor shape family, and candidate detail URLs.
Read-only.
"""
import re
import pathlib
import json

R3 = pathlib.Path(__file__).parent / "raw3"
HREF = re.compile(r'href\s*=\s*["\']([^"\']+)["\']', re.I)

COLLEGES = {
    "nxy": "农学院", "zbxy": "植物保护学院", "yyxy": "园艺学院",
    "thxy": "土地与环境学院", "jgxy": "经济管理学院", "myxy": "动物科学与医学学院",
    "lin": "林学院", "gcxy": "工程学院", "xdxy": "信息与电气工程学院",
    "slxy": "水利学院", "spxy": "食品学院", "swxy": "生物科学技术学院",
    "marx": "马克思主义学院", "lxy": "理学院", "tyb": "体育教学部",
    "wyb": "外语教学部",
}

FAC_RX = re.compile(r'师资|教师队伍|教师风采|导师|队伍|人员')
PAGING = [
    ("共N条", r'共\s*\d+\s*条'),
    ("共N页", r'共\s*\d+\s*页'),
    ("N/M页", r'\d+\s*/\s*\d+\s*页'),
    ("子页 N.htm", r'href="(?:\d+|index_\d+)\.htm"'),
    ("JS跳页", r'GOPAGE|gotopagebut|fanye\d*|createPageHTML'),
]


def read(p):
    return p.read_text(encoding="utf-8", errors="replace")


def home_nav(college):
    p = R3 / f"{college}.syau.edu.cn_.html"
    if not p.exists():
        return []
    s = read(p)
    out = []
    for m in re.finditer(r'<a\b([^>]*)>(.*?)</a>', s, re.I | re.S):
        txt = re.sub(r'<[^>]+>', '', m.group(2))
        txt = re.sub(r'\s+', '', txt)
        if not FAC_RX.search(txt):
            continue
        hm = HREF.search(m.group(1))
        if not hm:
            continue
        h = hm.group(1)
        if h.startswith("javascript") or h.startswith("#"):
            continue
        out.append((txt[:16], h))
    seen, uniq = set(), []
    for t, h in out:
        if h in seen:
            continue
        seen.add(h)
        uniq.append((t, h))
    return uniq


def column_stats(p):
    s = read(p)
    t = re.search(r'<title>([^<]*)</title>', s, re.I)
    arts = re.findall(r'info/(\d{3,5})/(\d+)\.htm', s)
    cols = sorted({c for c, _ in arts})
    paging = {}
    for label, rx in PAGING:
        n = len(re.findall(rx, s, re.I))
        if n:
            paging[label] = n
    fam = {"img": 0, "em": 0, "plain": 0, "title_attr": 0}
    for m in re.finditer(r'<a\b([^>]*)>(.*?)</a>', s, re.I | re.S):
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
    cont = re.search(r'id="(vsb_content[^"]*)"', s, re.I) or \
        re.search(r'id="(WspDetailContent)"', s, re.I)
    return {
        "file": p.name,
        "title": t.group(1).strip() if t else "",
        "articles": len(set(a for _, a in arts)),
        "column_ids": cols,
        "paging": paging,
        "anchor_family": fam,
        "container": cont.group(1) if cont else None,
    }


def main():
    out = {}
    for key, cn in COLLEGES.items():
        nav = home_nav(key)
        cols = [column_stats(p) for p in sorted(R3.glob(f"{key}.syau.edu.cn_*.html"))]
        out[cn] = {"host": f"{key}.syau.edu.cn", "nav": nav, "columns": cols}
    print(json.dumps(out, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
