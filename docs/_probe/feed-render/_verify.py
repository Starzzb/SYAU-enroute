
import os, re, json, ssl, urllib.request, urllib.error, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
def get(u):
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    try:
        with urllib.request.urlopen(req,timeout=30,context=ctx) as r: return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, b""
    except Exception as e: return None, str(e)[:50].encode()
out=io.StringIO()

# jingsai article page 2 / 3 existence + body len
for p in [1,2,3]:
    st,b=get(f"https://www.52jingsai.com/article-24180-{p}.html")
    if b:
        t=b.decode("gb18030","replace")
        i=t.find('<td id="article_content">'); j=t.find('diycontentbottom', i)
        body=t[i:j] if i>0 and j>0 else ""
        txt=re.sub(r'\s+','',re.sub(r'<[^>]+>','',body))
        out.write(f"jingsai -24180-{p}: {st} bytes={len(b)} bodytext={len(txt)}\n")
    else:
        out.write(f"jingsai -24180-{p}: {st}\n")

# cxcy list ?page=2
st,b=get("http://cxcy.syau.edu.cn/comp/front/comp/list?page=2")
if b:
    t=b.decode("utf-8","replace")
    ids=re.findall(r'comp/info\?id=(\d+)', t)
    out.write(f"cxcy list?page=2: {st} bytes={len(b)} ids={ids[:8]}\n")
st,b=get("http://cxcy.syau.edu.cn/comp/front/comp/list")
t=b.decode("utf-8","replace")
out.write("cxcy list p1 ids="+str(re.findall(r'comp/info\?id=(\d+)', t)[:8])+"\n")

# cxcy detail: any 附件 keyword
for fn in ["cxcycomp-detail.html","cxcydc-detail.html","cxcycomp-detail2.html"]:
    tt=open(os.path.join(D,fn),"rb").read().decode("utf-8","replace")
    has=("附件" in tt)
    out.write(f"{fn}: 附件_in_page={has}\n")

# cahe list count & 业务公告 term
out.write("cahe term title check: ")
tt=open(os.path.join(D,"cahe-list.html"),"rb").read().decode("utf-8","replace")
m=re.search(r'<title>(.*?)</title>', tt, re.S)
out.write((m.group(1).strip() if m else "?")+"\n")

open(os.path.join(D,"_verify.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
