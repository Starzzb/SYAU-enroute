#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""学院教师公开信息抓取（票 27 第三步）。**幂等 / 可 --dry-run / 出 diff**。

三条硬约束写进代码：
1. **只抓静态 `.htm`**（robots 实测：静态页 Allow、带 `?` 的 Disallow）—— URL 里出现 `?` 直接拒绝
2. **同站串行 + 间隔 ≥1.2 秒**（别把学院站点打出问题）
3. **解析出 0 条 = 失败退出**，绝不静默产出空文件（那样 App 里会"老师全消失"）

落库：`assets/faculty/<code>.json`（按学院分文件）+ `assets/faculty/_index.json`。
每人带 `source`（详情页 URL）与 `fetchedAt`。

用法：
    python tools/faculty/scrape.py --dry-run            # 只解析、只打报告，不写资产
    python tools/faculty/scrape.py --only nxy slxy      # 只跑指定学院
    python tools/faculty/scrape.py --at 2026-11-14T20:00:00+08:00   # 固定时间戳（幂等重跑）
    python tools/faculty/scrape.py --max-pages 2        # 调试：每站最多翻几页
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
from urllib.parse import urljoin, urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe import CACHE, DELAY, fetch, text_of  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "assets", "faculty")

A_RE = re.compile(r"<a\b[^>]*?href\s*=\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", re.I | re.S)
DETAIL_RE = re.compile(r"info/\d+/\d+\.htm", re.I)
HANZI_NAME = re.compile(r"^[\u4e00-\u9fa5]{2,4}$")
TAG_RE = re.compile(r"<[^>]+>")
SPACE_RE = re.compile(r"[ \t\u00a0\u3000]+")

# 导航词：出现在列表页里但**不是**人名（否则「学院概况」会被当成老师）
NAV_WORDS = {
    "学院概况", "学院简介", "组织机构", "现任领导", "师资队伍", "师资力量", "人才培养",
    "科学研究", "党建工作", "学生工作", "招生就业", "资料下载", "联系我们", "学院首页",
    "学校主页", "农大首页", "网站首页", "首页", "加入收藏", "设为首页", "打印本页", "关闭",
    "上页", "下页", "尾页", "首页", "下一页", "上一页", "更多", "详情", "本科生教育",
    "研究生教育", "专业设置", "学科建设", "科研团队", "学科团队", "教师名录", "教师队伍",
}

# 详情页字段：标签 → 输出键。**顺序即优先级**（同一人多个同义标签时取第一个有值的）
LABELS = [
    ("title",      ["职称", "专业技术职务", "职务职称"]),
    ("duty",       ["职务", "行政职务", "兼任"]),
    ("dept",       ["所在系", "系所", "所属系", "教研室", "所属部门", "单位", "院系"]),
    ("research",   ["研究方向", "研究领域", "主要研究方向"]),
    ("office",     ["办公地点", "办公地址", "办公室", "地点"]),
    ("email",      ["电子邮箱", "电子信箱", "邮箱", "Email", "E-mail", "email", "EMAIL"]),
    ("education",  ["教育经历", "学习经历", "教育背景"]),
    ("work",       ["工作经历", "工作简历", "任职经历"]),
]

# ── 站点配置：**每一条都来自 docs/学院官网-教师信息源表.md 的实测** ──
# mode A = 列表页有 /info/ 详情链接；B = 姓名在正文/锚文本为空；C = 无详情页（只有名单）
COLLEGES = [
    {"code": "nxy", "name": "农学院", "mode": "A", "lists": ["/szdw1.htm"], "base": "https://nxy.syau.edu.cn/"},
    {"code": "zbxy", "name": "植物保护学院", "mode": "A", "lists": ["/xkjs/szll.htm"], "base": "https://zbxy.syau.edu.cn/"},
    {"code": "thxy", "name": "土地与环境学院", "mode": "A", "lists": ["/szdw/jsxl/zgjzc.htm", "/szdw/bssds.htm"], "base": "https://thxy.syau.edu.cn/"},
    {"code": "jgxy", "name": "经济管理学院", "mode": "A", "lists": ["/szdw/nljjglzy.htm", "/szdw/bssds.htm", "/szdw/njxsds.htm", "/szdw/jjxsds.htm"], "base": "https://jgxy.syau.edu.cn/"},
    {"code": "myxy", "name": "动物科学与医学学院", "mode": "A", "lists": ["/szdw/js.htm", "/szdw/fjs.htm"], "base": "https://myxy.syau.edu.cn/"},
    {"code": "lin", "name": "林学院", "mode": "A", "lists": ["/szdw/zyjs.htm", "/szdw/jzds.htm"], "base": "https://lin.syau.edu.cn/"},
    {"code": "xdxy", "name": "信息与电气工程学院", "mode": "A", "lists": ["/szdw.htm"], "base": "https://xdxy.syau.edu.cn/"},
    {"code": "slxy", "name": "水利学院", "mode": "A", "lists": ["/szdw2/js.htm", "/szdw2/fjs.htm"], "base": "https://slxy.syau.edu.cn/"},
    {"code": "spxy", "name": "食品学院", "mode": "A", "lists": ["/szdw/jsml1.htm", "/szdw/dsfc.htm"], "base": "https://spxy.syau.edu.cn/"},
    {"code": "swxy", "name": "生物科学技术学院", "mode": "A", "lists": ["/szdw/zzjs.htm"], "base": "https://swxy.syau.edu.cn/"},
    {"code": "lxy", "name": "理学院", "mode": "A", "lists": ["/szdw1/js.htm", "/szdw1/fjs.htm"], "base": "https://lxy.syau.edu.cn/"},
    {"code": "wyb", "name": "外语教学部", "mode": "A", "lists": ["/szdw/szdw.htm"], "base": "https://wyb.syau.edu.cn/"},
    {"code": "gcxy", "name": "工程学院", "mode": "B", "lists": ["/szdw1/jsml.htm"], "base": "https://gcxy.syau.edu.cn/"},
    # D = 列表由 JS 渲染（静态 HTML 没有姓名），只留隐藏的 /info/ 锚点 → 逐条去详情页 title 取名
    {"code": "tyb", "name": "体育教学部", "mode": "D", "lists": ["/bmgk/jsdw.htm"], "base": "https://tyb.syau.edu.cn/"},
    {"code": "yyxy", "name": "园艺学院", "mode": "C", "lists": ["/xkjs/szdw.htm"], "base": "https://yyxy.syau.edu.cn/"},
    # C = 按教研室分页罗列「姓名（职称）」，没有详情页
    {"code": "marx", "name": "马克思主义学院", "mode": "C", "base": "https://marx.syau.edu.cn/",
     "lists": ["/szll/sxddyfzjys.htm", "/szll/zgjxdsjys.htm", "/szll/mkszyjbyljys.htm",
               "/szll/xsyzcjys.htm", "/szll/yjssxzzllkjys.htm"]},
]


def norm(html):
    """HTML → 规整文本（保留换行，去掉标签与连续空白）"""
    t = re.sub(r"<script.*?</script>", " ", html, flags=re.I | re.S)
    t = re.sub(r"<style.*?</style>", " ", t, flags=re.I | re.S)
    t = re.sub(r"<br\s*/?>", "\n", t, flags=re.I)
    t = re.sub(r"</(p|div|tr|li|h\d)>", "\n", t, flags=re.I)
    t = TAG_RE.sub(" ", t)
    t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    lines = [SPACE_RE.sub(" ", ln).strip() for ln in t.splitlines()]
    return "\n".join(ln for ln in lines if ln)


def fetch_page(url, cache_key, refresh=False):
    if "?" in url:
        raise ValueError("拒绝抓取带查询串的 URL（robots Disallow: /*?*）：%s" % url)
    d = os.path.dirname(cache_key)
    os.makedirs(d, exist_ok=True)
    if os.path.exists(cache_key) and not refresh:
        return open(cache_key, encoding="utf-8", errors="replace").read()
    status, html, ctype, final = fetch(url)
    open(cache_key, "w", encoding="utf-8").write(html)
    time.sleep(DELAY)
    return html


def page_url(base, path):
    return urljoin(base, path.lstrip("/"))


def next_page_url(html, current_url, page_no):
    """翻页：VSB 常见 `index_2.htm` 或 `xxx/2.htm`；只认页面自己给出的链接"""
    for m in A_RE.finditer(html):
        href, inner = m.group(1), m.group(2)
        label = text_of(inner)
        if label in ("下页", "下一页", "后页"):
            return urljoin(current_url, href)
    m = re.search(r'index_(\d+)\.htm', current_url)
    if m:
        return urljoin(current_url, "index_%d.htm" % (page_no + 1))
    return None


def list_entries(html, page_url_, mode):
    """列表页 → [(name, detail_url|None)]；正则只认 `/info/` 链接，避开导航"""
    out, seen = [], set()
    for m in A_RE.finditer(html):
        href, inner = m.group(1), m.group(2)
        if href.lower().startswith(("javascript:", "mailto:", "#")):
            continue
        full = urljoin(page_url_, href)
        label = text_of(inner)
        if mode == "A":
            if not DETAIL_RE.search(href):
                continue
            if full in seen:
                continue
            # 姓名常见三处：锚文本 / `title` 属性（图链，水利就是这种）/ 紧跟的正文
            nm = label if HANZI_NAME.match(label) else ""
            if not nm:
                mt = re.search(r'title\s*=\s*["\']([\u4e00-\u9fa5]{2,4})["\']', m.group(0))
                if mt:
                    nm = mt.group(1)
            if not nm:
                m2 = re.match(r"^([\u4e00-\u9fa5]{2,4})(教授|副教授|讲师|研究员|高级实验师)", label)
                if m2:
                    nm = m2.group(1)
            seen.add(full)
            out.append({"name": nm, "url": full})
        else:  # mode B/C：从正文里找「姓名 + 职称」
            pass

    if mode == "D":
        # 列表是 JS 渲染的：静态 HTML 里只有 display:none 的 /info/ 锚点，**没有姓名**。
        # 不在列表页猜姓名（也不从 URL/文件名猜），全部交给详情页 title。
        for m in A_RE.finditer(html):
            href = m.group(1)
            if not DETAIL_RE.search(href):
                continue
            full = urljoin(page_url_, href)
            if full in seen:
                continue
            seen.add(full)
            out.append({"name": "", "url": full})

    if mode in ("B", "C"):
        # 先按表格行抽：体育教学部这类「姓名 | 职称」的可见列表在 <tr> 里，
        # /info/ 链接被放在 display:none 的 div 中（按 id 顺序映射太脆，这里不赌）
        for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", html, re.I | re.S):
            cells = [norm(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", tr, re.I | re.S)]
            cells = [c for c in cells if c]
            if not cells:
                continue
            nm = ""
            for c in cells:
                first = c.split("\n")[0].strip()
                if HANZI_NAME.match(first) and first not in NAV_WORDS:
                    nm = first
                    break
            if not nm or nm in seen:
                continue
            seen.add(nm)
            t = ""
            for c in cells:
                mt = re.search(r"(教授|副教授|讲师|研究员|副研究员|高级实验师|实验师|助教)", c)
                if mt:
                    t = mt.group(1)
                    break
            out.append({"name": nm, "url": None, "titleFromList": t})

        txt = norm(html)
        # ① 「姓名（职称）」/「姓名(职称)」—— 马院教研室页就是这种写法
        for m in re.finditer(
            r"([\u4e00-\u9fa5]{2,4})\s*[（(]\s*(教授|副教授|讲师|研究员|副研究员|高级实验师|实验师|助教)\s*[）)]", txt
        ):
            nm = m.group(1)
            if nm in NAV_WORDS or nm in seen:
                continue
            seen.add(nm)
            out.append({"name": nm, "url": None, "titleFromList": m.group(2)})
        # ② 「姓名 职称」紧邻（其它站点的紧凑列表）
        for m in re.finditer(
            r"(?:^|\n)([\u4e00-\u9fa5]{2,4})\s*(教授|副教授|讲师|研究员|副研究员|高级实验师|实验师|助教)", txt
        ):
            nm = m.group(1)
            if nm in NAV_WORDS or nm in seen:
                continue
            seen.add(nm)
            out.append({"name": nm, "url": None, "titleFromList": m.group(2)})
    return out


def detail_fields(html):
    txt = norm(html)
    got = {}
    for key, aliases in LABELS:
        for alias in aliases:
            m = re.search(re.escape(alias) + r"\s*[:：]?\s*([^\n]{1,120})", txt)
            if not m:
                continue
            v = m.group(1).strip(" :：")
            if not v or re.fullmatch(r"[:：\-—]+", v):
                continue
            if key == "email":
                # 只认真正的邮箱地址：不然会把页脚的「辽ICP备…」一起吞进来
                me = re.search(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}", v)
                if not me:
                    continue
                v = me.group(0)
            got[key] = v
            break
    # 职称常直接写成「XXX 教授」而不是「职称：教授」
    if "title" not in got:
        m = re.search(r"(教授|副教授|讲师|研究员|副研究员|高级实验师|实验师|助教)", txt)
        if m:
            got["title"] = m.group(1)
    m = re.search(r'<img[^>]+src="([^"]+)"', html, re.I)
    if m:
        got["photo"] = m.group(1)
    return got


def scrape_college(cfg, at, refresh=False, max_pages=20, dry=False):
    code = cfg["code"]
    teachers, problems = {}, []
    for li, path in enumerate(cfg["lists"]):
        url = page_url(cfg["base"], path)
        for page in range(1, max_pages + 1):
            key = os.path.join(CACHE, code, "p%d_%d.html" % (li, page))
            try:
                html = fetch_page(url, key, refresh)
            except urllib.error.HTTPError as e:
                problems.append("%s HTTP %s" % (url, e.code))
                break
            except Exception as e:  # noqa: BLE001
                problems.append("%s %s" % (url, e))
                break

            entries = list_entries(html, url, cfg["mode"])
            if page == 1 and not entries:
                problems.append("%s 解析出 0 条（%s 模式）" % (url, cfg["mode"]))
                break

            for e in entries:
                nm = e["name"]
                # mode D 的姓名本来就要去详情页 title 取，列表页为空是正常的
                if not nm and cfg["mode"] != "D":
                    continue
                rec = {
                    "name": nm,
                    "title": e.get("titleFromList", ""),
                    "duty": "", "dept": "", "research": "", "office": "", "email": "",
                    "education": "", "work": "", "photo": "",
                    "source": e["url"] or url,
                    "fetchedAt": at,
                }
                if e["url"]:
                    dkey = os.path.join(CACHE, code, "d_" + re.sub(r"\W+", "_", e["url"][-40:]) + ".html")
                    try:
                        dhtml = fetch_page(e["url"], dkey, refresh)
                        if not nm:
                            # mode D：姓名只在详情页 title 里（形如「张三-体育教学部」）
                            mt = re.search(r"<title>(.*?)</title>", dhtml, re.S)
                            if mt:
                                cand = re.split(r"[-\u2014|_]", mt.group(1).strip())[0].strip()
                                if HANZI_NAME.match(cand) and cand not in NAV_WORDS:
                                    nm = cand
                                    rec["name"] = cand
                        for k, v in detail_fields(dhtml).items():
                            if v and not rec.get(k):
                                # 照片要绝对化：详情页里常是 `/__local/…png` 这种相对路径，
                                # 不补成完整 URL 的话，下游（缩略图/大图）根本用不了
                                if k == "photo" and not v.startswith("http"):
                                    v = urljoin(e["url"], v)
                                rec[k] = v
                    except Exception as ex:  # noqa: BLE001 —— 单页失败不毁整站，但要记
                        problems.append("详情页失败 %s: %s" % (e["url"], ex))
                # 去重键：姓名 + 来源（同名不同人 → 两条，宁重不错并）
                teachers[(rec["name"], rec["source"])] = rec

            nxt = next_page_url(html, url, page)
            if not nxt or nxt == url:
                break
            url = nxt

    rows = sorted(teachers.values(), key=lambda r: (r["name"], r["source"]))
    out = {
        "schema": 1,
        "college": cfg["name"],
        "code": code,
        "mode": cfg["mode"],
        "generatedAt": at,
        "count": len(rows),
        "withTitle": sum(1 for r in rows if r["title"]),
        "withEmail": sum(1 for r in rows if r["email"]),
        "withResearch": sum(1 for r in rows if r["research"]),
        "withPhoto": sum(1 for r in rows if r["photo"]),
        "problems": problems,
        "teachers": rows,
    }
    return out


def diff_prev(code, new):
    p = os.path.join(ASSETS, code + ".json")
    if not os.path.exists(p):
        return {"firstRun": True, "added": new["count"], "gone": 0, "changed": 0}
    old = json.load(open(p, encoding="utf-8"))
    ok = {(t["name"], t["source"]): t for t in old.get("teachers", [])}
    nk = {(t["name"], t["source"]): t for t in new["teachers"]}
    added = sorted(set(nk) - set(ok))
    gone = sorted(set(ok) - set(nk))
    changed = sorted(k for k in (set(nk) & set(ok)) if nk[k] != ok[k])
    return {"firstRun": False, "added": len(added), "gone": len(gone),
            "changed": len(changed),
            "addedSample": ["%s %s" % k for k in added[:5]],
            "goneSample": ["%s %s" % k for k in gone[:5]]}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1, sort_keys=False)
        f.write("\n")
    os.replace(tmp, path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--only", nargs="*", default=None)
    ap.add_argument("--at", default=None, help="固定 fetchedAt（幂等重跑用）")
    ap.add_argument("--refresh", action="store_true")
    ap.add_argument("--max-pages", type=int, default=20)
    args = ap.parse_args()

    at = args.at or time.strftime("%Y-%m-%dT%H:%M:%S%z")
    index, failed = [], []

    for cfg in COLLEGES:
        if args.only and cfg["code"] not in args.only:
            continue
        data = scrape_college(cfg, at, args.refresh, args.max_pages, args.dry_run)
        if data["count"] == 0:
            failed.append("%s(%s): 0 条 —— %s" % (cfg["name"], cfg["code"],
                                                  "; ".join(data["problems"]) or "无详情"))
            print("[FAIL] %-16s 0 条  %s" % (cfg["name"], data["problems"][:2]))
            continue
        d = diff_prev(cfg["code"], data)
        index.append({"code": cfg["code"], "college": cfg["name"], "count": data["count"],
                      "withTitle": data["withTitle"], "withEmail": data["withEmail"],
                      "withResearch": data["withResearch"], "withPhoto": data["withPhoto"],
                      "problems": len(data["problems"])})
        print("[OK]   %-16s %4d 人  职称%3d 邮箱%3d 研究方向%3d  新增%s 消失%s 变更%s%s" % (
            cfg["name"], data["count"], data["withTitle"], data["withEmail"],
            data["withResearch"], d["added"], d["gone"], d["changed"],
            "  (首跑)" if d.get("firstRun") else ""))
        for p in data["problems"][:2]:
            print("         ! %s" % p)
        sys.stdout.flush()
        if not args.dry_run:
            write_json(os.path.join(ASSETS, cfg["code"] + ".json"), data)

    print("\n合计：%d 个学院、%d 人" % (len(index), sum(r["count"] for r in index)))
    if failed:
        print("失败/空结果：")
        for f in failed:
            print("  - %s" % f)
    if not args.dry_run and index:
        write_json(os.path.join(ASSETS, "_index.json"),
                   {"schema": 1, "generatedAt": at, "total": sum(r["count"] for r in index),
                    "colleges": index})
    if failed:
        sys.exit(2)   # 有站点 0 条时必须非零退出，禁止「静默成功」


if __name__ == "__main__":
    main()
