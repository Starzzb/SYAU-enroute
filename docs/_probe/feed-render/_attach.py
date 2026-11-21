
import os, re, json, ssl, urllib.request, urllib.error, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
def head(u, ref=None):
    h={"User-Agent":UA}
    if ref: h["Referer"]=ref
    req=urllib.request.Request(u, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30, context=ctx) as r:
            return {"status":r.status,"ct":r.headers.get("Content-Type"),
                    "cd":r.headers.get("Content-Disposition"),"len":r.headers.get("Content-Length"),
                    "final":r.geturl(), "first": r.read(8)[:8].decode("latin1")}
    except urllib.error.HTTPError as e:
        return {"status":e.code,"ct":e.headers.get("Content-Type") if e.headers else None}
    except Exception as e:
        return {"status":None,"err":str(e)[:80]}
out=io.StringIO()
res={}
for name,u,ref in [
  ("cxcyart download aid=163&id=150","http://cxcy.syau.edu.cn/comp/front/comp/articledownload?aid=163&id=150","http://cxcy.syau.edu.cn/comp/front/comp/articledetail?id=163"),
  ("cxcyart download aid=154&id=140","http://cxcy.syau.edu.cn/comp/front/comp/articledownload?aid=154&id=140","http://cxcy.syau.edu.cn/comp/front/comp/articledetail?id=154"),
  ("fwwb public/index","https://www.fwwb.org.cn/public/index",None),
  ("jingsai article page2","https://www.52jingsai.com/article-24180-2.html",None),
]:
    r=head(u,ref); res[name]=r
    out.write(f"{name}: {json.dumps(r,ensure_ascii=False)}\n")

# lnjyt js array full
for fn in ["lnjyt-detail.html","lnjyt-detail2.html"]:
    t=open(os.path.join(D,fn),"rb").read().decode("utf-8","replace")
    arr=re.findall(r"attachHuilanMediaArray\.push\(\{([^}]*)\}\)", t)
    imgarr=re.findall(r"imgHuilanMediaArray\.push\(\{([^}]*)\}\)", t)
    out.write(f"{fn}: attach pushes={len(arr)} img pushes={len(imgarr)}\n")
    for a in arr[:4]: out.write("   ATT> "+a[:200]+"\n")
    for a in imgarr[:3]: out.write("   IMG> "+a[:200]+"\n")

# fwwb public/index content
st,_,_,_,_,body = None,None,None,None,None,None
try:
    req=urllib.request.Request("https://www.fwwb.org.cn/public/index", headers={"User-Agent":UA})
    with urllib.request.urlopen(req,timeout=30,context=ctx) as r:
        b=r.read()
    tt=b.decode("utf-8","replace")
    items=re.findall(r'href="(/[a-z]+/show/\d+)"', tt)
    out.write(f"fwwb /public/index items={len(set(items))} sample={sorted(set(items))[:6]}\n")
except Exception as e:
    out.write("fwwb public err "+str(e)[:80]+"\n")

open(os.path.join(D,"_attach.txt"),"w",encoding="utf-8").write(out.getvalue())
print(out.getvalue())
