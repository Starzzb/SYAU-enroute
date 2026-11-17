# -*- coding: utf-8 -*-
"""全校学院站「课程大纲 / 培养方案」栏目侦察
输出：_collect/recon.json  + 控制台摘要
"""
import io, json, os, re, ssl, sys, time, urllib.request, gzip, zlib
from urllib.parse import urljoin, urlparse

OUT = os.path.dirname(os.path.abspath(__file__))
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")

COLLEGES = [
    ("土地与环境学院", "thxy.syau.edu.cn"),
    ("农学院", "nxy.syau.edu.cn"),
    ("食品学院", "spxy.syau.edu.cn"),
    ("经济管理学院", "jgxy.syau.edu.cn"),
    ("信息与电气工程学院", "xdxy.syau.edu.cn"),
    ("工程学院", "gcxy.syau.edu.cn"),
    ("水利学院", "slxy.syau.edu.cn"),
    ("园艺学院", "yyxy.syau.edu.cn"),
    ("植物保护学院", "zbxy.syau.edu.cn"),
    ("林学院", "lin.syau.edu.cn"),
    ("生物科学技术学院", "swxy.syau.edu.cn"),
    ("理学院", "lxy.syau.edu.cn"),
    ("动物科学与医学学院", "myxy.syau.edu.cn"),
    ("马克思主义学院", "marx.syau.edu.cn"),
    ("体育部", "tyb.syau.edu.cn"),
    ("外语部", "wyb.syau.edu.cn"),
    ("教务处", "jwc.syau.edu.cn"),
]

def get(url, timeout=25):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
    })
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
        raw = r.read()
        enc = r.headers.get("Content-Encoding", "")
        if "gzip" in enc:
            raw = gzip.decompress(raw)
        elif "deflate" in enc:
            raw = zlib.decompress(raw, -zlib.MAX_WBITS)
        ctype = r.headers.get("Content-Type", "")
        m = re.search(r"charset=([\w-]+)", ctype, re.I)
        cs = m.group(1) if m else None
        txt = None
        if cs and cs.lower() not in ("utf-8", "utf8"):
            try:
                txt = raw.decode(cs)
            except (LookupError, UnicodeDecodeError):
                txt = None
        if txt is None:
            for cand in ("utf-8-sig", "utf-8", "gb18030"):
                try:
                    txt = raw.decode(cand)
                    break
                except UnicodeDecodeError:
                    continue
        if txt is None:
            txt = raw.decode("utf-8", "replace")
        return r.status, txt

LINK = re.compile(r'<a\s[^>]*href="([^"]+)"[^>]*>(.*?)</a>', re.S | re.I)

def links(base, html):
    out = []
    for m in LINK.finditer(html):
        href, inner = m.group(1), m.group(2)
        text = re.sub(r"<[^>]+>", "", inner)
        text = re.sub(r"&nbsp;|\s+", " ", text).strip()
        if not text or len(text) > 40:
            continue
        if href.startswith("javascript") or href.startswith("#"):
            continue
        out.append((text, urljoin(base, href)))
    return out

def clean(html):
    t = re.sub(r"(?is)<(script|style)[^>]*>.*?</\1>", " ", html)
    t = re.sub(r"<[^>]+>", " ", t)
    t = t.replace("&nbsp;", " ")
    return re.sub(r"\s+", " ", t)

def main():
    result = {}
    for name, host in COLLEGES:
        base = "https://" + host + "/"
        rec = {"学院": name, "主机": host, "首页": None, "大纲线索": [], "培养方案线索": [], "错误": None}
        try:
            st, home = get(base)
            rec["首页"] = st
            ls = links(base, home)
            # 站点内部链接去重，按文本关键字筛
            cand = []
            for text, url in ls:
                if urlparse(url).netloc and host not in urlparse(url).netloc:
                    continue
                if re.search(r"大纲|培养方案|本科教学|教学工作|人才培养|教学管理", text):
                    cand.append((text, url))
            seen = set()
            uniq = []
            for t, u in cand:
                if u in seen:
                    continue
                seen.add(u)
                uniq.append((t, u))
            rec["导航命中"] = [{"文本": t, "url": u} for t, u in uniq][:40]
            # 抓所有命中栏目页（限 3 层深度内的一批）
            queue = list(uniq[:24])
            visited = set()
            hits = []
            for t, u in queue:
                if u in visited:
                    continue
                visited.add(u)
                if len(visited) > 40:
                    break
                try:
                    st2, page = get(u)
                except Exception as e:
                    continue
                title = re.search(r"(?is)<title>(.*?)</title>", page)
                title = re.sub(r"\s+", " ", title.group(1)).strip() if title else ""
                body = clean(page)
                if re.search(r"大纲", body):
                    # 抓页面里带「大纲」的条目链接
                    items = []
                    for tt, uu in links(u, page):
                        if "大纲" in tt and uu not in [x["url"] for x in items]:
                            items.append({"文本": tt, "url": uu})
                    hits.append({"栏目": t, "url": u, "页面标题": title,
                                 "含大纲": True, "条目数": len(items), "条目": items[:30]})
                if re.search(r"培养方案", body):
                    items = []
                    for tt, uu in links(u, page):
                        if "培养方案" in tt and uu not in [x["url"] for x in items]:
                            items.append({"文本": tt, "url": uu})
                    rec["培养方案线索"].append({"栏目": t, "url": u, "条目": items[:20]})
            rec["大纲线索"] = hits
        except Exception as e:
            rec["错误"] = "%s: %s" % (type(e).__name__, e)
        result[host] = rec
        n = sum(h["条目数"] for h in rec.get("大纲线索", []))
        print("[%s] %s 首页=%s 栏目命中=%d 大纲条目=%d 错误=%s" % (
            name, host, rec["首页"], len(rec.get("大纲线索", [])), n, rec["错误"]))
        time.sleep(0.6)
    with open(os.path.join(OUT, "recon.json"), "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print("saved recon.json")

if __name__ == "__main__":
    main()
