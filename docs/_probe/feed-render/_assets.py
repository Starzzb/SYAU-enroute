
import os, re, json, ssl, urllib.request, urllib.error, struct
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE

def get(u, ref=None, timeout=90, ua=UA):
    h={"User-Agent":ua} if ua else {}
    if ref: h["Referer"]=ref
    req=urllib.request.Request(u, headers=h)
    try:
        with urllib.request.urlopen(req,timeout=timeout,context=ctx) as r:
            return r.status, r.headers.get("Content-Type"), r.getheader("Content-Length"), r.read()
    except urllib.error.HTTPError as e:
        return e.code, (e.headers.get("Content-Type") if e.headers else None), None, b""
    except Exception as e:
        return None, str(e)[:100], None, b""

def pdf_pages(data):
    n=len(re.findall(rb'/Type\s*/Page[^s]', data))
    if n==0:
        m=re.search(rb'/Count\s+(\d+)', data)
        if m: n=int(m.group(1))
    return n

def img_size(data):
    if data[:8]==b"\x89PNG\r\n\x1a\n":
        w,h=struct.unpack(">II", data[16:24]); return ("png",w,h)
    if data[:2]==b"\xff\xd8":
        i=2
        while i<len(data)-9:
            if data[i]!=0xFF: i+=1; continue
            m=data[i+1]
            if m in (0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF):
                h,w=struct.unpack(">HH", data[i+5:i+9]); return ("jpg",w,h)
            if m in (0xD8,0xD9) or 0xD0<=m<=0xD7: i+=2; continue
            L=struct.unpack(">H", data[i+2:i+4])[0]; i+=2+L
        return ("jpg",None,None)
    if data[:6] in (b"GIF87a",b"GIF89a"):
        w,h=struct.unpack("<HH", data[6:10]); return ("gif",w,h)
    return ("?",None,None)

TASKS=[
 # (save name, url, referer, kind)
 ("lnjyt.pdf","https://jyt.ln.gov.cn/jyt/gk/zxtz/2026102110290659632/2026102110254421584.pdf",
   "https://jyt.ln.gov.cn/jyt/gk/zxtz/2026102110290659632/index.shtml","pdf"),
 ("fwwb.pdf","https://www.fwwb.org.cn/attached/file/20261120/20261120204900_532.pdf",
   "https://www.fwwb.org.cn/news/show/666","pdf"),
 ("fwwb2.pdf","https://www.fwwb.org.cn/attached/file/20261120/20261120204253_476.pdf",
   "https://www.fwwb.org.cn/news/show/665","pdf"),
 ("cahe.pdf","https://cmsfiles.zhongkefu.com.cn/caheedu/gdjy/file/20260928/1790586060381787.pdf",
   "https://www.cahe.edu.cn/site/content/19836.html","pdf"),
 ("cahe2.pdf","https://www.cahe.edu.cn/upload/gdjy/file/20250930/1759215429196221.pdf",
   "https://www.cahe.edu.cn/site/content/19836.html","pdf"),
 ("cxcyart-img1.jpg","http://cxcy.syau.edu.cn/static/uploads/res/editor/202512/20251208172845_51496.jpg",
   "http://cxcy.syau.edu.cn/comp/front/comp/articledetail?id=163","img"),
 ("tzb-img1.jpg","https://www.tiaozhanbei.net/media/ckeditor_uploads/genrui/2026/11/05/1____.jpg",
   "https://www.tiaozhanbei.net/article/15864/","img"),
 ("jingsai-img1.jpg","http://cdn1.52jingsai.com/portal/202611/04/104655xlxd533olleox23l.jpg",
   "https://www.52jingsai.com/article-24180-1.html","img"),
 ("fwwb-img1.jpeg","https://www.fwwb.org.cn/attached/image/20261120/20261120204717_669.jpeg",
   "https://www.fwwb.org.cn/news/show/666","img"),
]

res=[]
for name,url,ref,kind in TASKS:
    st,ct,cl,body = get(url, ref)
    rec={"name":name,"url":url,"status":st,"ct":ct,"bytes":len(body)}
    if body:
        open(os.path.join(D,name),"wb").write(body)
    if kind=="pdf" and body:
        rec["pages"]=pdf_pages(body)
        rec["magic"]=body[:8].decode("latin1")
    if kind=="img" and body:
        fmt,w,h=img_size(body); rec.update({"fmt":fmt,"w":w,"h":h})
    # direct link test without referer
    st2,ct2,_,b2 = get(url, None)
    rec["no_referer_status"]=st2; rec["no_referer_bytes"]=len(b2)
    # no-UA test
    st3,ct3,_,b3 = get(url, None, ua=None)
    rec["no_ua_status"]=st3
    res.append(rec)
    print(json.dumps(rec,ensure_ascii=False))
json.dump(res, open(os.path.join(D,"_assets.json"),"w",encoding="utf-8"), ensure_ascii=False, indent=1)
