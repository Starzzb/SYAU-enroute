
import os, json, ssl, urllib.request, urllib.error
OUT=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
def get(u):
    req=urllib.request.Request(u, headers={"User-Agent":UA,"Accept-Language":"zh-CN,zh;q=0.9"})
    try:
        with urllib.request.urlopen(req,timeout=25,context=ctx) as r: return r.status,r.headers.get("Content-Type"),r.read(),r.geturl()
    except urllib.error.HTTPError as e: return e.code,(e.headers.get("Content-Type") if e.headers else None),b"",u
    except Exception as e: return None,None,b"",u
res=[]
for name,u in [("jingsai-detail.html","https://www.52jingsai.com/article-24180-1.html"),
               ("jingsai-detail2.html","https://www.52jingsai.com/article-24182-1.html")]:
    st,ct,body,fin=get(u)
    if body: open(os.path.join(OUT,name),"wb").write(body)
    res.append({"file":name,"url":u,"status":st,"ct":ct,"bytes":len(body)})
print(json.dumps(res,ensure_ascii=False))
