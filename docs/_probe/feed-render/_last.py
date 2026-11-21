
import os, re, io, ssl, urllib.request, urllib.error
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
def get(u, ref=None):
    h={"User-Agent":UA}
    if ref: h["Referer"]=ref
    try:
        with urllib.request.urlopen(urllib.request.Request(u,headers=h),timeout=30,context=ctx) as r:
            return r.status, r.headers.get("Content-Type"), r.read()
    except urllib.error.HTTPError as e: return e.code, (e.headers.get("Content-Type") if e.headers else None), b""
    except Exception as e: return None, str(e)[:60], b""
out=io.StringIO()

# cxcy attachment without referer
for label,u in [("cxcy docx no-ref","http://cxcy.syau.edu.cn/comp/front/comp/articledownload?aid=163&id=150"),
                ("cxcy jpg no-ref","http://cxcy.syau.edu.cn/comp/front/comp/articledownload?aid=154&id=140")]:
    st,ct,b=get(u)
    out.write(f"{label}: {st} {ct} bytes={len(b)}\n")

# jingsai cdn http vs https
for u in ["http://cdn1.52jingsai.com/portal/202611/04/104655xlxd533olleox23l.jpg",
          "https://cdn1.52jingsai.com/portal/202611/04/104655xlxd533olleox23l.jpg"]:
    st,ct,b=get(u); out.write(f"jingsai cdn {u.split(':')[0]}: {st} {ct} bytes={len(b)}\n")

# tzb image no referer
st,ct,b=get("https://www.tiaozhanbei.net/media/ckeditor_uploads/genrui/2026/11/05/1____.jpg")
out.write(f"tzb img no-ref: {st} {ct} bytes={len(b)}\n")

# encoding byte evidence
raw=open(os.path.join(D,"jingsai-list.html"),"rb").read()
out.write("jingsai-list first bytes: "+repr(raw[:120])+"\n")
try: raw.decode("utf-8"); out.write("utf8 decode: OK\n")
except UnicodeDecodeError as e: out.write(f"utf8 decode FAILED at byte {e.start}: {e.reason}\n")
dec=raw.decode("gb18030")
out.write("gb18030 head text: "+dec[:150].replace("\n"," ")+"\n")
m=re.search(r'charset=([\w-]+)', dec[:3000], re.I)
out.write("declared charset: "+(m.group(1) if m else "none")+"\n")

# cxcy detail: confirm body is server-rendered (no XHR placeholders in body)
t=open(os.path.join(D,"cxcycomp-detail.html"),"rb").read().decode("utf-8","replace")
i=t.find('<div class="content">'); j=t.find('tml_footer')
body=t[i:j if j>0 else i+15000]
out.write(f"cxcycomp body: len={len(body)} has_<p>={body.count('<p')} has_script_in_body={'<script' in body} has_ajax={'ajax' in body.lower()}\n")
# count body text distinct
txt=re.sub(r'\s+','',re.sub(r'<[^>]+>','',body))
out.write(f"cxcycomp body plaintext={len(txt)} chars; sample: {txt[:80]}\n")

open(os.path.join(D,"_last.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
