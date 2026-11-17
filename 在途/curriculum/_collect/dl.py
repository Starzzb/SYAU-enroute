# -*- coding: utf-8 -*-
"""教务处课程大纲附件下载器（带图形验证码）

用法：
  python dl.py status           # 看进度
  python dl.py next             # 为下一个待下文件取验证码，存 captcha.jpg
  python dl.py code 3968        # 用验证码提交当前文件
  python dl.py reset            # 清空状态（不删已下文件）

验证码图 60x20、4 位数字，由人（或模型）看图给出。
"""
import http.cookiejar, json, os, re, ssl, sys, time, urllib.request, urllib.parse
from urllib.error import HTTPError

BASE = os.path.dirname(os.path.abspath(__file__))
STATE = os.path.join(BASE, "state.json")
COOKIES = os.path.join(BASE, "cookies.txt")
SHOT = os.path.join(BASE, "captcha.jpg")
DEST = r"D:/Download/课程大纲2021"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")

# 实测：教务处 2021 版课程大纲文章 wbnewsid=7213，栏目 1235
FILES = [
    ("农学院", 11763700),
    ("植物保护学院", 11763701),
    ("园艺学院", 11763702),
    ("经济管理学院", 11763703),
    ("土地与环境学院", 11763704),
    ("动物科学与医学学院", 11763705),
    ("林学院", 11763706),
    ("工程学院", 11763707),
    ("信息与电气工程学院", 11763708),
    ("食品学院", 11763709),
    ("生物科学技术学院", 11763710),
    ("理学院", 11763711),
    ("公共课教学单位", 11763712),
    ("水利学院", 11763713),
]

def load_state():
    if os.path.exists(STATE):
        return json.load(open(STATE, encoding="utf-8"))
    return {"done": {}, "pending": None, "attempts": {}}

def save_state(st):
    json.dump(st, open(STATE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

def opener():
    jar = http.cookiejar.MozillaCookieJar(COOKIES)
    if os.path.exists(COOKIES):
        try:
            jar.load(ignore_discard=True, ignore_expires=True)
        except Exception:
            pass
    return urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar),
        urllib.request.HTTPSHandler(context=CTX),
    ), jar

def req(op, url, referer=None, timeout=60):
    r = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/pdf,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9",
        "Referer": referer or "https://jwc.syau.edu.cn/rcpy/kcdg.htm",
    })
    return op.open(r, timeout=timeout)

def url_for(fid):
    return ("https://jwc.syau.edu.cn/system/_content/download.jsp?"
            "urltype=news.DownloadAttachUrl&owner=1584116291&wbfileid=%d" % fid)

def do_next():
    st = load_state()
    if st.get("pending"):
        print("已有待提交文件：%s（先 code 或 cancel）" % st["pending"])
        return
    left = [(n, f) for n, f in FILES if str(f) not in st["done"]]
    if not left:
        print("全部完成")
        return
    name, fid = left[0]
    op, jar = opener()
    # 1) 取验证码页：建立会话
    resp = req(op, url_for(fid))
    page = resp.read()
    # 2) 取验证码图
    img_url = ("https://jwc.syau.edu.cn/system/resource/js/filedownload/"
               "createimage.jsp?randnum=%d" % int(time.time() * 1000))
    r2 = req(op, img_url)
    img = r2.read()
    open(SHOT, "wb").write(img)
    jar.save(ignore_discard=True, ignore_expires=True)
    st["pending"] = {"学院": name, "fileid": fid, "ts": time.time()}
    save_state(st)
    print("文件: %s  wbfileid=%d  验证码: %s (%d bytes)" % (name, fid, SHOT, len(img)))

def do_code(code):
    st = load_state()
    p = st.get("pending")
    if not p:
        print("没有待提交文件，先 next")
        return
    code = str(code).strip()
    if not re.fullmatch(r"[0-9A-Za-z]{4}", code):
        print("验证码应为 4 位，收到 %r" % code)
        return
    op, jar = opener()
    url = url_for(p["fileid"]) + "&codeValue=" + urllib.parse.quote(code)
    try:
        resp = req(op, url)
        data = resp.read()
        ctype = resp.headers.get("Content-Type", "")
        disp = resp.headers.get("Content-Disposition", "")
    except HTTPError as e:
        print("HTTP %s" % e.code)
        return
    jar.save(ignore_discard=True, ignore_expires=True)
    if data[:4] == b"%PDF":
        os.makedirs(DEST, exist_ok=True)
        fname = "沈阳农业大学%s本科课程大纲（2021版）.pdf" % p["学院"]
        path = os.path.join(DEST, fname)
        open(path, "wb").write(data)
        pages = 0
        try:
            import fitz
            with fitz.open(path) as d:
                pages = d.page_count
        except Exception:
            pass
        st["done"][str(p["fileid"])] = {
            "学院": p["学院"], "文件": path, "字节": len(data), "页数": pages,
            "下载于": time.strftime("%Y-%m-%d %H:%M:%S"),
        }
        st["pending"] = None
        save_state(st)
        print("OK %s  %d bytes  %d 页  -> %s" % (p["学院"], len(data), pages, path))
    else:
        st["attempts"][str(p["fileid"])] = st["attempts"].get(str(p["fileid"]), 0) + 1
        save_state(st)
        head = ""
        m = re.search(r"message[^>]*>([^<]{0,60})", data.decode("utf-8", "replace"))
        if m:
            head = m.group(1)
        print("不是 PDF（ctype=%s，%d bytes）%s —— 验证码可能错了，重新 next"
              % (ctype, len(data), head))
        st["pending"] = None
        save_state(st)

def do_status():
    st = load_state()
    print("已下 %d / %d" % (len(st["done"]), len(FILES)))
    for n, f in FILES:
        d = st["done"].get(str(f))
        print("  %-14s %-9s %s" % (n, f, ("%d 页 %d B" % (d["页数"], d["字节"])) if d else "—"))
    if st.get("pending"):
        print("待提交: %s (%d)" % (st["pending"]["学院"], st["pending"]["fileid"]))

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "status"
    if cmd == "next":
        do_next()
    elif cmd == "code":
        do_code(sys.argv[2])
    elif cmd == "status":
        do_status()
    elif cmd == "reset":
        save_state({"done": {}, "pending": None, "attempts": {}})
        print("reset")
    else:
        print(__doc__)
