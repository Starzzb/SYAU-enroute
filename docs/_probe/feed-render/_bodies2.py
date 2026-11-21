
import os, re, json, ssl, io, urllib.request, urllib.error, struct
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE

def get(u, ref=None, timeout=60):
    h={"User-Agent":UA}
    if ref: h["Referer"]=ref
    req=urllib.request.Request(u, headers=h)
    try:
        with urllib.request.urlopen(req,timeout=timeout,context=ctx) as r:
            return r.status, r.headers.get("Content-Type"), r.read()
    except urllib.error.HTTPError as e:
        return e.code, (e.headers.get("Content-Type") if e.headers else None), b""
    except Exception as e:
        return None, str(e)[:100], b""

def dec(fn,key):
    raw=open(os.path.join(D,fn),"rb").read()
    return raw.decode("gb18030","replace") if key=="jingsai" else raw.decode("utf-8","replace")

def sl(t,s,e):
    i=t.find(s)
    if i<0: return ""
    j=t.find(e,i+len(s))
    return t[i:(j+len(e)) if j>0 else len(t)]

CFG = {
 "cxcycomp": ("cxcycomp-detail.html", '<div class="content">', '<div class="tml_footer'),
 "cxcydc":   ("cxcydc-detail.html",   '<div class="content">', '<div class="tml_footer'),
 "cxcyart":  ("cxcyart-detail.html",  '<div class="content_detail">', '<div class="upload_list">'),
 "tzb":      ("tzb-detail.html",      '<!-- 正文区域 -->', '<!-- 正文'),
 "lnjyt":    ("lnjyt-detail.html",    '<div class="zoom">', '<div class="foot-main">'),
 "cahe":     ("cahe-detail2.html",    '<div class="branch_text_box">', '<div class="page_turn'),
 "jingsai":  ("jingsai-detail.html",  '<td id="article_content">', '<!--[diy=diycontentbottom]'),
 "fwwb":     ("fwwb-detail.html",     '<div class="show-content">', '</div>'),
}
info={}
for k,(fn,s,e) in CFG.items():
    t=dec(fn,k)
    b=sl(t,s,e)
    imgs=re.findall(r'<img\b[^>]*?\bsrc\s*=\s*["\']([^"\']+)["\']', b, re.I)
    imgs=[i for i in imgs if not re.search(r'(logo|icon|weixin|template/|/static/images/)', i, re.I)]
    lazy=len(re.findall(r'data-(?:src|original|lazy)\s*=', b, re.I))
    txt=re.sub(r'<[^>]+>','',re.sub(r'<script.*?</script>','',b,flags=re.S|re.I))
    txt=re.sub(r'\s+','',txt)
    pdfs=sorted(set(re.findall(r'["\']([^"\']*\.pdf)["\']', b, re.I)))
    docs=sorted(set(re.findall(r'["\']([^"\']*\.(?:docx?|xlsx?|zip|rar|pptx?))["\']', b, re.I)))
    info[k]={"body_chars":len(b),"text_chars":len(txt),"imgs":imgs,"n_img":len(imgs),"lazy":lazy,"pdfs":pdfs,"docs":docs}
    print(k,"| body",len(b),"text",len(txt),"imgs",len(imgs),"lazy",lazy,"pdfs",len(pdfs),"docs",len(docs))
    for i in imgs[:6]: print("    IMG",i[:120])
    for p in pdfs[:3]: print("    PDF",p[:120])
    for p in docs[:3]: print("    DOC",p[:120])
json.dump(info, open(os.path.join(D,"_bodies.json"),"w",encoding="utf-8"), ensure_ascii=False, indent=1)
