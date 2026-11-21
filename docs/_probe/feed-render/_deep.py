
import os, re, json, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()
def dec(fn,key):
    raw=open(os.path.join(D,fn),"rb").read()
    return raw, (raw.decode("gb18030","replace") if key=="jingsai" else raw.decode("utf-8","replace"))

def sl(t,s,e):
    i=t.find(s)
    if i<0: return ""
    j=t.find(e,i+len(s))
    return t[i:(j+len(e)) if j>0 else len(t)]

BODY={
 "cxcycomp":("cxcycomp-detail.html",'<div class="content">','<div class="tml_footer'),
 "cxcycomp2":("cxcycomp-detail2.html",'<div class="content">','<div class="tml_footer'),
 "cxcydc":("cxcydc-detail.html",'<div class="content">','<div class="tml_footer'),
 "cxcydc2":("cxcydc-detail2.html",'<div class="content">','<div class="tml_footer'),
 "cxcyart":("cxcyart-detail.html",'<div class="content_detail">','<div class="upload_list">'),
 "cxcyart2":("cxcyart-detail2.html",'<div class="content_detail">','<div class="upload_list">'),
 "tzb":("tzb-detail.html",'<!-- 正文区域 -->','</div>\n\t\t</div>'),
 "tzb2":("tzb-detail2.html",'<!-- 正文区域 -->','</div>\n\t\t</div>'),
 "lnjyt":("lnjyt-detail.html",'<div class="zoom">','<div class="foot-main">'),
 "lnjyt2":("lnjyt-detail2.html",'<div class="zoom">','<div class="foot-main">'),
 "cahe":("cahe-detail.html",'<div class="branch_text_box">','<div class="page_turn'),
 "cahe2":("cahe-detail2.html",'<div class="branch_text_box">','<div class="page_turn'),
 "jingsai":("jingsai-detail.html",'<td id="article_content">','<!--[diy=diycontentbottom]'),
 "jingsai2":("jingsai-detail2.html",'<td id="article_content">','<!--[diy=diycontentbottom]'),
 "fwwb":("fwwb-detail.html",'<div class="show-content">','</div>\n</div>'),
 "fwwb2":("fwwb-detail2.html",'<div class="show-content">','</div>\n</div>'),
}
for k,(fn,s,e) in BODY.items():
    key = "jingsai" if k.startswith("jingsai") else k
    raw,t = dec(fn, "jingsai" if k.startswith("jingsai") else "x")
    b=sl(t,s,e)
    imgs=re.findall(r'<img\b[^>]*>', b, re.I)
    attrs=[]
    for im in imgs[:3]:
        attrs.append(re.sub(r'\s+',' ',im)[:170])
    txt=re.sub(r'\s+','',re.sub(r'<[^>]+>','',re.sub(r'<script.*?</script>','',b,flags=re.S|re.I)))
    out.write(f"## {k} body={len(b)} text={len(txt)} imgs={len(imgs)}\n")
    for a in attrs: out.write("   IMG> "+a+"\n")
    # XHR / SPA markers in whole doc
    for pat in [r'\$\.ajax', r'\$\.get\(', r'\$\.post\(', r'axios', r'fetch\(', r'XMLHttpRequest', r'new Vue', r'__INITIAL_STATE__', r'nuxt']:
        n=len(re.findall(pat, t))
        if n: out.write(f"   JS {pat} x{n}\n")
    out.write("\n")

open(os.path.join(D,"_deep.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
