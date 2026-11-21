
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

def load(k):
    raw=open(os.path.join(D,k+"-list.html"),"rb").read()
    return raw.decode("gb18030","replace") if k=="jingsai" else raw.decode("utf-8","replace")

for k in ["cxcycomp","cxcydc","cxcyart","tzb","lnjyt","cahe","jingsai","fwwb"]:
    t=load(k)
    out.write("="*30+" "+k+"\n")
    imgs=re.findall(r'<img\b[^>]*>', t, re.I)
    lazy=sum(1 for i in imgs if re.search(r'data-(?:src|original|lazy|echo)', i, re.I))
    out.write(f"  imgs={len(imgs)} lazy(data-*)={lazy}\n")
    for i in imgs[:3]:
        out.write("   "+re.sub(r'\s+',' ',i)[:170]+"\n")
    # dates present
    ds=re.findall(r'20\d{2}[-/年]\d{1,2}[-/月]\d{1,2}', t)
    out.write(f"  dates({len(ds)}): {ds[:8]}\n")
    # pagination links
    pg=set(re.findall(r'href="([^"]*(?:page|index_\d+)[^"]*)"', t, re.I))
    out.write(f"  page_hrefs: {list(pg)[:8]}\n")
    out.write("\n")

open(os.path.join(D,"_lists.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
