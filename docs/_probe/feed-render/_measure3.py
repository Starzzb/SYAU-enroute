
import os, re, io, ssl, struct, urllib.request, urllib.error
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
def get(u):
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":UA}),timeout=40,context=ctx) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, b""
    except Exception as e: return None, b""
def sz(d):
    if d[:2]==b"\xff\xd8":
        i=2
        while i<len(d)-9:
            if d[i]!=0xFF: i+=1; continue
            m=d[i+1]
            if m in (0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF):
                h,w=struct.unpack(">HH", d[i+5:i+9]); return w,h
            if m in (0xD8,0xD9) or 0xD0<=m<=0xD7: i+=2; continue
            i+=2+struct.unpack(">H", d[i+2:i+4])[0]
    if d[:8]==b"\x89PNG\r\n\x1a\n":
        w,h=struct.unpack(">II", d[16:24]); return w,h
    return 0,0
out=io.StringIO()

# cxcyart2 attachments
out.write("== cxcyart id=154 attachments ==\n")
tot=0; mx=0
for i in [140,141,142]:
    st,b=get(f"http://cxcy.syau.edu.cn/comp/front/comp/articledownload?aid=154&id={i}")
    if b:
        w,h=sz(b); tot+=len(b); mx=max(mx,w,h)
        out.write(f"  id={i} {st} {len(b)/1024:.0f}KB {w}x{h}\n")
out.write(f"  total={tot/1024/1024:.2f}MB max_edge={mx}\n")

# tzb all body imgs
t=open(os.path.join(D,"tzb-detail.html"),"rb").read().decode("utf-8","replace")
i=t.find('<!-- 正文区域 -->'); m=re.search(r'<!--', t[i+60:]); j=i+60+m.start() if m else i+20000
ims=re.findall(r'<img[^>]*src="([^"]+)"', t[i:j])
out.write(f"\n== tzb detail 15864 body imgs={len(ims)} ==\n")
tot=0; mx=0
for x in ims:
    u=x if x.startswith("http") else "https://www.tiaozhanbei.net"+x
    st,b=get(u)
    if b:
        w,h=sz(b); tot+=len(b); mx=max(mx,w,h)
out.write(f"  total={tot/1024/1024:.2f}MB max_edge={mx}\n")

# fwwb list item count with dates
tt=open(os.path.join(D,"fwwb-list.html"),"rb").read().decode("utf-8","replace")
items=re.findall(r'<span class="time">(\d{4}-\d{2}-\d{2})</span><a\s+href="(/news/show/\d+)"', tt)
out.write(f"\nfwwb list dated items={len(items)} first={items[:4]}\n")
# lnjyt list item count
tl=open(os.path.join(D,"lnjyt-list.html"),"rb").read().decode("utf-8","replace")
li=re.findall(r'<span class="l-gl-main-num">(\d{4}-\d{2}-\d{2})</span>', tl)
out.write(f"lnjyt list items page1={len(li)} dates={li[:4]} total_pages=18\n")
# cahe list item count
tc=open(os.path.join(D,"cahe-list.html"),"rb").read().decode("utf-8","replace")
ci=re.findall(r'/site/content/(\d+)\.html', tc)
out.write(f"cahe list items page1={len(set(ci))}\n")
# jingsai list item count
tj=open(os.path.join(D,"jingsai-list.html"),"rb").read().decode("gb18030","replace")
ji=set(re.findall(r'href="article-(\d+)-\d+\.html"', tj))
out.write(f"jingsai list items page1={len(ji)} total_pages=12\n")

open(os.path.join(D,"_measure3.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
