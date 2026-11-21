
import os, re, json, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

def dec(fn,key):
    raw=open(os.path.join(D,fn),"rb").read()
    return raw.decode("gb18030","replace") if key=="jingsai" else raw.decode("utf-8","replace")

def slice_between(t, s, e):
    i=t.find(s)
    if i<0: return ""
    j=t.find(e, i+len(s))
    return t[i:(j+len(e)) if j>0 else i+20000]

# boundaries
CFG = {
 "cxcycomp": ("cxcycomp-detail.html", '<div class="content">', '<!-- 版权'),
 "cxcydc":   ("cxcydc-detail.html",   '<div class="content">', '<!-- 版权'),
 "cxcyart":  ("cxcyart-detail.html",  '<div class="content_detail">', '</div>'),
 "tzb":      ("tzb-detail.html",      '<!-- 正文区域 -->', '<!-- 正文区域结束'),
 "lnjyt":    ("lnjyt-detail.html",    '<div class="zoom">', '<div class="foot-main">'),
 "cahe":     ("cahe-detail2.html",    '<div class="branch_text_box">', '</div>'),
 "jingsai":  ("jingsai-detail.html",  '<td id="article_content">', '</td>'),
 "fwwb":     ("fwwb-detail.html",     '<div class="show-content">', '</div>'),
}
for k,(fn,s,e) in CFG.items():
    t=dec(fn,k)
    b=slice_between(t,s,e)
    out.write(f"{k}: s_found={t.find(s)} e_found={t.find(e)} bodylen={len(b)}\n")
    if not b:
        # show nearby region to adjust
        i=t.find(s)
        out.write("   CTX: "+t[max(0,i-100):i+400].replace("\n"," ")[:500]+"\n")
open(os.path.join(D,"_bounds.txt"),"w",encoding="utf-8").write(out.getvalue())
print(out.getvalue())
