
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

def dec(fn,key):
    raw=open(os.path.join(D,fn),"rb").read()
    return raw, (raw.decode("gb18030","replace") if key=="jingsai" else raw.decode("utf-8","replace"))

CASES=[("cxcycomp","cxcycomp-detail.html",r'<div class="content"'),
       ("cxcyart","cxcyart-detail.html",r'<div class="content"'),
       ("cxcydc","cxcydc-detail.html",r'<div class="content"'),
       ("cahe","cahe-detail2.html",r'<div class="news_content'),
       ("lnjyt","lnjyt-detail.html",r'<div class="zoom"'),
       ("tzb","tzb-detail.html",None),
       ("jingsai","jingsai-detail.html",None),
       ("fwwb","fwwb-detail.html",r'<div class="content"')]

for key,fn,marker in CASES:
    raw,t=dec(fn,key)
    out.write("="*70+"\n"+fn+"\n")
    if key=="tzb":
        # find article body: look for 正文 markers
        for kw in ["article-content","articleContent","post-content","content_html","class=\"content","prose"]:
            i=t.find(kw)
            out.write(f"  kw {kw} -> {i}\n")
        m=re.search(r'(<div[^>]*class="[^"]*(?:article|detail|content)[^"]*"[^>]*>)', t, re.I)
        out.write("  FIRST: "+(m.group(0) if m else "none")+"\n")
        idx=[mm.start() for mm in re.finditer(r'<p\b', t, re.I)]
        out.write(f"  p positions: {idx[:5]} .. total {len(idx)}\n")
        if idx:
            out.write("  SAMPLE P:\n"+t[idx[0]-300:idx[0]+700]+"\n")
        continue
    if key=="jingsai":
        for mm in re.finditer(r'<div[^>]*(?:id="postmessage_\d+"|class="t_f"[^>]*)>', t, re.I):
            out.write("  POST: "+mm.group(0)[:120]+"\n")
        for mm in re.finditer(r'id="postmessage_\d+"', t):
            out.write("  ID: "+mm.group(0)+"\n")
        i=t.find('id="postmessage_')
        if i>0:
            out.write("  REGION:\n"+t[i-200:i+2500]+"\n")
        continue
    i=t.find(marker.replace("\\",""))
    if i<0:
        # fallback search
        i=t.find(marker[m].value if False else "")
    if i<0: out.write("  MARKER NOT FOUND\n"); continue
    out.write(t[i:i+2200]+"\n")

open(os.path.join(D,"_regions.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
