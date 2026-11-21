
import os, re, io, json, ssl, urllib.request, urllib.error
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE
def get(u):
    req=urllib.request.Request(u, headers={"User-Agent":UA})
    try:
        with urllib.request.urlopen(req,timeout=30,context=ctx) as r: return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, b""
    except Exception as e: return None, str(e)[:60].encode()
out=io.StringIO()

# 1) cxcy whole page attachment scan
for fn in ["cxcycomp-detail.html","cxcycomp-detail2.html","cxcydc-detail.html","cxcydc-detail2.html"]:
    t=open(os.path.join(D,fn),"rb").read().decode("utf-8","replace")
    pdfs=re.findall(r'["\']([^"\']*\.pdf)["\']', t, re.I)
    docs=re.findall(r'["\']([^"\']*\.(?:docx?|xlsx?|zip|rar))["\']', t, re.I)
    dl=re.findall(r'href="([^"]*download[^"]*)"', t, re.I)
    out.write(f"{fn}: pdf={len(pdfs)} doc={len(docs)} dl-links={dl[:3]}\n")
    if pdfs: out.write("   "+str(pdfs[:2])+"\n")

# 2) jingsai multipage marker
t=open(os.path.join(D,"jingsai-detail.html"),"rb").read().decode("gb18030","replace")
for pat in [r'共\s*\d+\s*页', r'article-\d+-\d+\.html', r'<a[^>]*>\s*\d+\s*</a>']:
    m=re.findall(pat, t)
    out.write(f"jingsai detail {pat} -> {m[:6]} (n={len(m)})\n")

# 3) list pagination probes
probes=[
 ("lnjyt idx2","https://jyt.ln.gov.cn/jyt/gk/zxtz/index_1.shtml"),
 ("cahe page1","https://www.cahe.edu.cn/site/term/373.html?page=1"),
 ("fwwb public index","https://www.fwwb.org.cn/public/index"),
 ("tzb tzb p2","https://www.tiaozhanbei.net/tzb?page=2"),
]
for name,u in probes:
    st,b=get(u)
    out.write(f"{name}: status={st} bytes={len(b)}\n")
    if st==200 and b:
        tt=b.decode("utf-8","replace")
        ds=re.findall(r'20\d{2}[-/年]\d{1,2}[-/月]\d{1,2}', tt)[:6]
        out.write(f"   dates={ds}\n")
open(os.path.join(D,"_misc.txt"),"w",encoding="utf-8").write(out.getvalue())
print("done")
