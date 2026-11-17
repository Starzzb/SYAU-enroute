# -*- coding: utf-8 -*-
"""单附件下载工作单元（可并行：每个 wbfileid 独立 cookie / 验证码 / 状态）

  python w.py get 11763701        -> 取该附件的验证码到 captcha_11763701.jpg
  python w.py put 11763701 7635   -> 用验证码提交；成功则落盘 PDF 并打印结果
  python w.py state               -> 打印已落盘清单

成功判据：响应体以 %PDF 开头。失败（验证码错）返回非 0，调用方重新 get 再来。
"""
import http.cookiejar, json, os, re, ssl, sys, time, urllib.request, urllib.parse

BASE = os.path.dirname(os.path.abspath(__file__))
DEST = r"D:/Download/课程大纲2021"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
REF = "https://jwc.syau.edu.cn/rcpy/kcdg.htm"

NAMES = {
    11763700: "农学院", 11763701: "植物保护学院", 11763702: "园艺学院",
    11763703: "经济管理学院", 11763704: "土地与环境学院",
    11763705: "动物科学与医学学院", 11763706: "林学院", 11763707: "工程学院",
    11763708: "信息与电气工程学院", 11763709: "食品学院",
    11763710: "生物科学技术学院", 11763711: "理学院",
    11763712: "公共课教学单位", 11763713: "水利学院",
}

def jar_path(fid):
    return os.path.join(BASE, "jar_%d.txt" % fid)

def cap_path(fid):
    return os.path.join(BASE, "captcha_%d.jpg" % fid)

def opener(fid):
    jar = http.cookiejar.MozillaCookieJar(jar_path(fid))
    if os.path.exists(jar_path(fid)):
        try:
            jar.load(ignore_discard=True, ignore_expires=True)
        except Exception:
            pass
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar),
        urllib.request.HTTPSHandler(context=CTX),
    ), jar

def url_for(fid):
    return ("https://jwc.syau.edu.cn/system/_content/download.jsp?"
            "urltype=news.DownloadAttachUrl&owner=1584116291&wbfileid=%d" % fid)

def get(op):
    r = urllib.request.Request(REF, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return op.open(r, timeout=60).read()

def cmd_get(fid):
    op, jar = opener(fid)
    page = get(op)
    cap_url = ("https://jwc.syau.edu.cn/system/resource/js/filedownload/"
               "createimage.jsp?randnum=%d" % int(time.time() * 1000))
    img = op.open(urllib.request.Request(cap_url, headers={
        "User-Agent": UA, "Referer": REF, "Accept": "image/avif,image/webp,*/*"}), timeout=60).read()
    open(cap_path(fid), "wb").write(img)
    jar.save(ignore_discard=True, ignore_expires=True)
    print("CAPTCHA=%s" % cap_path(fid))
    print("文件=%s wbfileid=%d 图字节=%d 页面字节=%d"
          % (NAMES.get(fid, "?"), fid, len(img), len(page)))

def cmd_put(fid, code):
    code = code.strip()
    if not re.fullmatch(r"[0-9A-Za-z]{4}", code):
        print("BADCODE %r" % code)
        return 2
    op, jar = opener(fid)
    url = url_for(fid) + "&codeValue=" + urllib.parse.quote(code)
    r = urllib.request.Request(url, headers={
        "User-Agent": UA, "Referer": REF,
        "Accept": "application/pdf,text/html;q=0.9,*/*;q=0.8"})
    resp = op.open(r, timeout=180)
    data = resp.read()
    jar.save(ignore_discard=True, ignore_expires=True)
    if data[:4] != b"%PDF":
        m = re.search(r"(message[^>]*>|错误[^<]{0,40}|验证码[^<]{0,20})", data.decode("utf-8", "replace"))
        print("FAIL ctype=%s bytes=%d %s" % (resp.headers.get("Content-Type"), len(data),
                                             m.group(0)[:60] if m else ""))
        return 1
    os.makedirs(DEST, exist_ok=True)
    path = os.path.join(DEST, "沈阳农业大学%s本科课程大纲（2021版）.pdf" % NAMES.get(fid, fid))
    open(path, "wb").write(data)
    pages = 0
    try:
        import pymupdf
        with pymupdf.open(path) as d:
            pages = d.page_count
    except Exception:
        try:
            import fitz
            with fitz.open(path) as d:
                pages = d.page_count
        except Exception:
            pass
    meta = {"wbfileid": fid, "学院": NAMES.get(fid), "文件": path,
            "字节": len(data), "页数": pages,
            "时间": time.strftime("%Y-%m-%d %H:%M:%S")}
    json.dump(meta, open(os.path.join(BASE, "done_%d.json" % fid), "w", encoding="utf-8"),
              ensure_ascii=False, indent=1)
    print("OK %s %d bytes %d pages -> %s" % (meta["学院"], len(data), pages, path))
    return 0

def cmd_state():
    rows = []
    for p in sorted(os.listdir(BASE)):
        if p.startswith("done_") and p.endswith(".json"):
            rows.append(json.load(open(os.path.join(BASE, p), encoding="utf-8")))
    print("已落盘 %d / %d" % (len(rows), len(NAMES)))
    for r in rows:
        print("  %-14s %-9d %4d 页 %9d B  %s" % (r["学院"], r["wbfileid"], r["页数"], r["字节"], r["时间"]))

if __name__ == "__main__":
    c = sys.argv[1] if len(sys.argv) > 1 else "state"
    if c == "get":
        cmd_get(int(sys.argv[2]))
    elif c == "put":
        sys.exit(cmd_put(int(sys.argv[2]), sys.argv[3]))
    else:
        cmd_state()
