
import os, re, json, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

FILES = [
 ("cxcycomp","cxcycomp-detail.html"),("cxcycomp","cxcycomp-detail2.html"),
 ("cxcydc","cxcydc-detail.html"),("cxcydc","cxcydc-detail2.html"),
 ("cxcyart","cxcyart-detail.html"),("cxcyart","cxcyart-detail2.html"),
 ("tzb","tzb-detail.html"),("tzb","tzb-detail2.html"),
 ("lnjyt","lnjyt-detail.html"),("lnjyt","lnjyt-detail2.html"),
 ("cahe","cahe-detail.html"),("cahe","cahe-detail2.html"),
 ("jingsai","jingsai-detail.html"),("jingsai","jingsai-detail2.html"),
 ("fwwb","fwwb-detail.html"),("fwwb","fwwb-detail2.html"),
]

def decode(raw, key):
    if key=="jingsai": return raw.decode("gb18030","replace")
    m=re.search(rb'charset=["\']?([\w-]+)', raw[:4000], re.I)
    return raw.decode("utf-8","replace")

for key,fn in FILES:
    p=os.path.join(D,fn)
    if not os.path.exists(p): 
        out.write(f"### {fn} MISSING\n\n"); continue
    raw=open(p,"rb").read()
    t=decode(raw,key)
    out.write(f"### {fn}  ({len(raw)} B)\n")
    # container candidates
    for cid in ["article-content","content","artical","article","news_content","TRS_Editor","zoom","text","detail","main"]:
        for m in re.finditer(r'<(?:div|td|article|section)[^>]*(?:id|class)="[^"]*\b'+re.escape(cid)+r'\b[^"]*"[^>]*>', t, re.I):
            out.write("   CONT: "+m.group(0)[:150]+"\n"); break
    # iframe/embed/object
    for m in re.finditer(r'<(iframe|embed|object)\b[^>]*>', t, re.I):
        out.write("   EMBED: "+m.group(0)[:220]+"\n")
    # pdf links
    pdfs=set(re.findall(r'["\']([^"\']*?\.pdf[^"\']*)["\']', t, re.I))
    for x in list(pdfs)[:6]: out.write("   PDF: "+x[:150]+"\n")
    # attachments
    atts=set(re.findall(r'["\']([^"\']*?\.(?:docx?|xlsx?|zip|rar|pptx?)["\'])', t, re.I))
    for x in list(atts)[:6]: out.write("   ATT: "+x[:150]+"\n")
    locs=set(re.findall(r'["\'](/__local/[^"\']+)["\']', t, re.I))
    for x in list(locs)[:5]: out.write("   LOCAL: "+x[:140]+"\n")
    # img stats
    imgs=re.findall(r'<img\b[^>]*>', t, re.I)
    out.write(f"   IMG_COUNT={len(imgs)}\n")
    lazy=sum(1 for i in imgs if re.search(r'data-(?:src|original|url)\s*=', i, re.I))
    rlazy=sum(1 for i in imgs if re.search(r'\blazy\w*=', i, re.I))
    out.write(f"   IMG_LAZY(data-*)={lazy}  IMG_LAZY(class-ish)={rlazy}\n")
    for i in imgs[:4]: out.write("   IMG: "+i[:200]+"\n")
    # xhr markers
    xhr = re.findall(r'(?:axios|\$\.ajax|\$\.get|fetch\(|XMLHttpRequest|vue|__NUXT__|__INITIAL_STATE__|window\.__)', t, re.I)
    out.write("   XHR/VUE markers: "+str(sorted(set(x.lower() for x in xhr))[:8])+"\n")
    # body text length of biggest <p> cluster
    ps=re.findall(r'<p\b[^>]*>(.*?)</p>', t, re.S|re.I)
    ptext="".join(re.sub(r'<[^>]+>','',x) for x in ps)
    out.write(f"   P_COUNT={len(ps)} P_TEXT_LEN={len(ptext.strip())}\n")
    out.write("\n")

open(os.path.join(D,"_shape.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
