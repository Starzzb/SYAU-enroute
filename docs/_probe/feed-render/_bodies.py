
import os, re, json, ssl, io, urllib.request, urllib.error, struct
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
ctx=ssl.create_default_context(); ctx.check_hostname=False; ctx.verify_mode=ssl.CERT_NONE

def get(u, ref=None, timeout=30):
    h={"User-Agent":UA,"Accept-Language":"zh-CN,zh;q=0.9"}
    if ref: h["Referer"]=ref
    req=urllib.request.Request(u, headers=h)
    try:
        with urllib.request.urlopen(req,timeout=timeout,context=ctx) as r:
            return r.status, r.headers.get("Content-Type"), r.read()
    except urllib.error.HTTPError as e:
        return e.code, (e.headers.get("Content-Type") if e.headers else None), b""
    except Exception as e:
        return None, str(e)[:80], b""

def region(t, start_pat, end_pat=None):
    m=re.search(start_pat, t, re.I|re.S)
    if not m: return ""
    i=m.start()
    if end_pat:
        m2=re.search(end_pat, t[i:], re.I|re.S)
        if m2: return t[i:i+m2.start()]
    return t[i:i+20000]

def decode(fn,key):
    raw=open(os.path.join(D,fn),"rb").read()
    return raw, (raw.decode("gb18030","replace") if key=="jingsai" else raw.decode("utf-8","replace"))

BODIES={
 "cxcycomp":("cxcycomp-detail.html", r'<div class="content">'),
 "cxcydc":("cxcydc-detail.html", r'<div class="content">'),
 "cxcyart":("cxcyart-detail.html", r'<div class="content_detail">'),
 "tzb":("tzb-detail.html", r'<!-- 正文区域 -->'),
 "lnjyt":("lnjyt-detail.html", r'<div class="zoom">'),
 "cahe":("cahe-detail2.html", r'<div class="branch_text_box">'),
 "jingsai":("jingsai-detail.html", r'<td id="article_content">'),
 "fwwb":("fwwb-detail.html", r'<div class="show-content">'),
}
res={}
for key,(fn,pat) in BODIES.items():
    raw,t=decode(fn,key)
    body=region(t,pat)
    imgs=re.findall(r'<img\b[^>]*src\s*=\s*["\']([^"\']+)["\']', body, re.I)
    pdfs=re.findall(r'["\']([^"\']*\.pdf)["\']', body, re.I)
    atts=re.findall(r'["\']([^"\']*\.(?:docx?|xlsx?|zip|rar|pptx?))["\']', body, re.I)
    lazy=len(re.findall(r'data-(?:src|original|lazy)\s*=', body, re.I))
    txt=re.sub(r'<script.*?</script>','',body,flags=re.S|re.I)
    txt=re.sub(r'<[^>]+>','',txt); txt=re.sub(r'\s+','',txt)
    res[key]={"file":fn,"body_len":len(body),"text_len":len(txt),"imgs":imgs,"pdfs":pdfs,"atts":atts,"lazy":lazy}
    print("==",key,"body",len(body),"text",len(txt),"imgs",len(imgs),"pdfs",len(pdfs),"atts",len(atts),"lazy",lazy)
    for i in imgs[:6]: print("   IMG", i[:130])
    for i in pdfs[:4]: print("   PDF", i[:130])
    for i in atts[:4]: print("   ATT", i[:130])
print()
json.dump(res, open(os.path.join(D,"_bodies.json"),"w",encoding="utf-8"), ensure_ascii=False, indent=1)
