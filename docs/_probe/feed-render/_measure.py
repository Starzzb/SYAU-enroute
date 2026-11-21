
import os, re, json, ssl, urllib.request, urllib.error, struct
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
def get(u, ua=UA, timeout=40):
    h={}
    if ua: h["User-Agent"]=ua
    req=urllib.request.Request(u, headers=h)
    try:
        with urllib.request.urlopen(req,timeout=timeout,context=ctx) as r: return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, b""
    except Exception as e: return None, str(e)[:60].encode()

def imgsize(d):
    if d[:8]==b"\x89PNG\r\n\x1a\n": return ("png",)+struct.unpack(">II", d[16:24])
    if d[:2]==b"\xff\xd8":
        i=2
        while i<len(d)-9:
            if d[i]!=0xFF: i+=1; continue
            m=d[i+1]
            if m in (0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF):
                h,w=struct.unpack(">HH", d[i+5:i+9]); return ("jpg",w,h)
            if m in (0xD8,0xD9) or 0xD0<=m<=0xD7: i+=2; continue
            i+=2+struct.unpack(">H", d[i+2:i+4])[0]
    return ("?",0,0)

# cxcyart 4 images
u="http://cxcy.syau.edu.cn/static/uploads/res/editor/202512/"
names=["20251208172845_51496.jpg","20251208172857_97808.jpg","20251208172908_26120.jpg","20251208172917_94803.jpg"]
print("== cxcyart images (art detail id=163) ==")
tot=0
for n in names:
    st,b=get(u+n)
    if b:
        f,w,h=imgsize(b); tot+=len(b)
        print(f"  {n} status={st} {len(b)/1024:.0f}KB {f} {w}x{h} max_edge={max(w,h)}")
print("  total bytes", tot)

# tzb body images of detail 15864
t=open(os.path.join(D,"tzb-detail.html"),"rb").read().decode("utf-8","replace")
i=t.find('<!-- 正文区域 -->')
j=t.find('<!-- 正文', i+10)
body=t[i:j if j>0 else i+20000]
ims=re.findall(r'<img[^>]*src="([^"]+)"', body)
print("== tzb detail 15864 body imgs:", len(ims))
u2="https://www.tiaozhanbei.net"
mx=0; sizes=[]
for x in ims[:4]:
    url=x if x.startswith("http") else u2+x
    st,b=get(url)
    if b:
        f,w,h=imgsize(b); sizes.append((x,w,h,len(b))); mx=max(mx,max(w,h))
        print(f"  {x[:60]} status={st} {len(b)/1024:.0f}KB {w}x{h}")
print("  max_edge(first4)", mx)

# anti-crawl: default python UA and blank
print("\n== anti-crawl probe (python-urllib UA / no UA) ==")
urls=[("cxcycomp","http://cxcy.syau.edu.cn/comp/front/comp/list"),
      ("tzb","https://www.tiaozhanbei.net/tzb"),
      ("lnjyt","https://jyt.ln.gov.cn/jyt/gk/zxtz/index.shtml"),
      ("cahe","https://www.cahe.edu.cn/site/term/373.html"),
      ("jingsai","https://www.52jingsai.com/bisai/xkjn/bangdan/"),
      ("fwwb","https://www.fwwb.org.cn/")]
for k,u in urls:
    s1,_=get(u,"Python-urllib/3.13")
    s2,_=get(u,None)
    print(f"  {k}: pyUA={s1} noUA={s2}")

# jingsai list order (dates)
raw=open(os.path.join(D,"jingsai-list.html"),"rb").read().decode("gb18030","replace")
ds=re.findall(r'20\d{2}-\d{1,2}-\d{1,2}', raw)
print("\njingsai list date seq:", ds[:12])
# tzb list order
t4=open(os.path.join(D,"tzb-list.html"),"rb").read().decode("utf-8","replace")
ds4=re.findall(r'(20\d{2}[-/]\d{1,2}[-/]\d{1,2})', t4)
print("tzb list date seq:", ds4[:12])
