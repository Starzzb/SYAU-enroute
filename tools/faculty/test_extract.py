#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`scrape.py` 解析器的离线单测（票 27）。**只用本地夹具，不联网。**

夹具都是**真实页面**（2026-11-14 从各学院站抓下来的原样 HTML，未裁剪）：
如果这些站点改版，夹具会立刻暴露解析器失效 —— 这正是我们要的。

    python tools/faculty/test_extract.py
"""
import json
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))

# 控制台是 GBK：统一按 UTF-8 输出，并把勾叉换成 ASCII（否则 Windows 上直接崩）
if hasattr(sys.stdout, 'buffer'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.path.insert(0, HERE)

import scrape  # noqa: E402

FIX = os.path.join(HERE, "fixtures")
FAILS = []


def fixture(name):
    with open(os.path.join(FIX, name), encoding="utf-8", errors="replace") as f:
        return f.read()


def check(cond, msg):
    if not cond:
        FAILS.append(msg)
        print("  [FAIL] %s" % msg)
    else:
        print("  [ok]   %s" % msg)


def test_norm():
    h = "<div>职称：教授</div><script>x=1</script><p>研究方向：土壤</p>"
    t = scrape.norm(h)
    check("职称：教授" in t, "norm 保留可见文本")
    check("x=1" not in t, "norm 去掉 script 内容")


def test_list_mode_a():
    html = fixture("slxy_list0.html")
    rows = scrape.list_entries(html, "https://slxy.syau.edu.cn/szdw2/js.htm", "A")
    check(len(rows) >= 5, "水利学院列表页解出 ≥5 条（实际 %d）" % len(rows))
    check(all(scrape.DETAIL_RE.search(r["url"]) for r in rows), "全部指向 /info/ 详情页")
    names = [r["name"] for r in rows if r["name"]]
    check(len(names) >= 5, "至少 5 条有姓名（实际 %d）" % len(names))
    check(not (set(names) & scrape.NAV_WORDS), "导航词没有被当成人名")


def test_list_mode_b_js_rendered_site():
    """体育教学部实测：**列表由 JS 渲染**（`u_u9_resultstr+=`），静态 HTML 里没有姓名，
    只有 12 个 `display:none` 的 /info/ 锚点。所以：
      · 静态模式下解出 0 条是**正确结论**，不许硬凑出假姓名
      · 上层必须因此报 problems（见 test_zero_result_…）
    该站的正确做法已记进源表：从**详情页 title** 取名（29 条 ≈ 29 个请求）。
    """
    html = fixture("tyb_list0.html")
    rows = scrape.list_entries(html, "https://tyb.syau.edu.cn/bmgk/jsdw.htm", "B")
    check(isinstance(rows, list), "B 模式不崩")
    check(all(len(r["name"]) <= 4 for r in rows), "若解出人名，必须是 2–4 字（当前 %d 条）" % len(rows))
    bad = [r["name"] for r in rows if r["name"] in scrape.NAV_WORDS]
    check(not bad, "绝不把导航词当人名（误收：%s）" % bad)
    check("u_u9_resultstr" in html, "夹具确认是 JS 渲染页（证据）")


def test_list_mode_c():
    """马克思主义学院：按教研室罗列，没有 /info/ 详情页"""
    html = fixture("marx_list0.html")
    rows = scrape.list_entries(html, "https://marx.syau.edu.cn/szll/sxddyfzjys.htm", "C")
    check(isinstance(rows, list), "C 模式返回列表（不改结构、不崩）")
    # 这一页实测没有「姓名+职称」紧邻文本 → 允许 0 条，但**必须由上层报问题**
    print("    （C 模式解得 %d 条；0 条时上层必须报 problems）" % len(rows))


def test_detail_fields_rich():
    html = fixture("zbxy_detail0.html")
    f = scrape.detail_fields(html)
    check(bool(f.get("title")), "植保详情页能取到职称：%r" % f.get("title"))
    check(bool(f.get("email")), "植保详情页能取到邮箱：%r" % f.get("email"))
    check(bool(f.get("research")) or bool(f.get("education")), "植保详情页至少还有研究方向/教育经历之一")
    check(bool(f.get("photo")), "能取到照片 URL")


def test_detail_fields_sparse():
    html = fixture("lxy_detail0.html")
    f = scrape.detail_fields(html)
    check(bool(f.get("title")), "理学院（字段稀疏）仍能取到职称：%r" % f.get("title"))
    check(not f.get("email"), "理学院详情页**确实没有**邮箱 → 留空，不许编")


def test_detail_fields_no_invention():
    """农学院详情页只有照片/研究方向/院系：缺的字段必须留空，不能用相近字段冒充"""
    html = fixture("nxy_detail0.html")
    f = scrape.detail_fields(html)
    check(bool(f.get("research")), "农学院取到研究方向：%r" % f.get("research"))
    check(not f.get("email"), "农学院详情页没有邮箱 → 留空（数据诚实）")
    check(not f.get("title"), "农学院详情页没有职称 → 留空，不拿「教授」两字去猜")


def test_url_guard():
    """robots 实测：`Disallow: /*?*` —— 带查询串的 URL 一律拒绝"""
    try:
        scrape.fetch_page("https://nxy.syau.edu.cn/szdw1.htm?page=2", os.path.join(tempfile.gettempdir(), "x.html"))
        check(False, "带 ? 的 URL 必须被拒绝")
    except ValueError as e:
        check("查询串" in str(e) or "?" in str(e), "带 ? 的 URL 被拒绝：%s" % e)


def test_write_json_idempotent():
    obj = {"schema": 1, "count": 1, "teachers": [{"name": "张三", "source": "u"}]}
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "a.json")
        scrape.write_json(p, obj)
        b1 = open(p, "rb").read()
        scrape.write_json(p, obj)
        b2 = open(p, "rb").read()
        check(b1 == b2, "同一份数据写两次字节完全相同（幂等）")
        check(json.loads(b2.decode("utf-8"))["teachers"][0]["name"] == "张三", "JSON 可回读")


def test_zero_result_is_a_failure_not_a_silent_empty():
    """解析出 0 条必须报 problems（上层据此非零退出），绝不静默产出空文件"""
    cfg = {"code": "_t", "name": "测试", "mode": "A",
           "base": "https://example.invalid/", "lists": ["/nope.htm"]}
    # 直接测「空 entries 时 problems 会被记」这条逻辑：用假 fetch 注入空页面
    orig = scrape.fetch_page
    try:
        scrape.fetch_page = lambda url, key, refresh=False: "<html><body>无内容</body></html>"
        data = scrape.scrape_college(cfg, at="2026-11-14T00:00:00+08:00")
        check(data["count"] == 0, "空页面 → 0 条")
        check(len(data["problems"]) >= 1, "空页面必须记 problems（实际：%s）" % data["problems"][:1])
    finally:
        scrape.fetch_page = orig


def test_dedup_keeps_same_name_different_source():
    """同名不同人不能被合并：去重键是 (name, source)"""
    cfg = {"code": "_t2", "name": "测试", "mode": "A",
           "base": "https://e.invalid/", "lists": ["/a.htm"]}
    page = ('<a href="info/1/1.htm">张三</a>'
            '<a href="info/2/2.htm">张三</a>')
    orig = scrape.fetch_page
    try:
        scrape.fetch_page = lambda url, key, refresh=False: page if "a.htm" in url else "<div>职称：教授</div>"
        data = scrape.scrape_college(cfg, at="2026-11-14T00:00:00+08:00")
        check(data["count"] == 2, "同名两条不同来源 → 保留 2 条（实际 %d）" % data["count"])
    finally:
        scrape.fetch_page = orig


def main():
    print("== scrape.py 解析器离线测试（真实页面夹具，不联网）==")
    for fn in [test_norm, test_list_mode_a, test_list_mode_b_js_rendered_site, test_list_mode_c,
               test_detail_fields_rich, test_detail_fields_sparse, test_detail_fields_no_invention,
               test_url_guard, test_write_json_idempotent,
               test_zero_result_is_a_failure_not_a_silent_empty,
               test_dedup_keeps_same_name_different_source]:
        print("- %s" % fn.__name__)
        fn()
    print()
    if FAILS:
        print("失败 %d 项" % len(FAILS))
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
