
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()
def dec(fn,k="x"):
    raw=open(os.path.join(D,fn),"rb").read()
    return raw.decode("gb18030","replace") if k=="jingsai" else raw.decode("utf-8","replace")

# cahe list items with date
t=dec("cahe-list.html")
rows=re.findall(r'/site/content/(\d+)\.html"[^>]*>.*?(\d{4})/(\d{2})', t, re.S)
out.write(f"cahe list dated={len(rows)} sample={rows[:5]}\n")
# count li in main list
m=re.search(r'<ul class="[^"]*list[^"]*">(.*?)</ul>', t, re.S)
out.write(f"cahe main ul found={bool(m)}\n")

# cxcyart list items
t2=dec("cxcyart-list.html")
arts=re.findall(r'articledetail\?id=(\d+)', t2)
out.write(f"cxcyart list items={len(set(arts))} ids={sorted(set(arts))}\n")

# fwwb /news/index items
out.write("fwwb list total dated 9 (含 5 条 大赛新闻 + 4 条 大赛公告)\n")

# cahe detail body text sizes both
for fn in ["cahe-detail.html","cahe-detail2.html"]:
    tt=dec(fn)
    i=tt.find('<div class="branch_text_box">'); j=tt.find('<div class="page_turn', i)
    b=tt[i:j if j>0 else i+12000]
    txt=re.sub(r'\s+','',re.sub(r'<[^>]+>','',b))
    out.write(f"{fn}: body={len(b)} text={len(txt)}\n")

# cxcydc body text
for fn in ["cxcydc-detail.html","cxcydc-detail2.html"]:
    tt=dec(fn)
    i=tt.find('<div class="content">'); j=tt.find('tml_footer', i)
    b=tt[i:j if j>0 else i+18000]
    txt=re.sub(r'\s+','',re.sub(r'<[^>]+>','',re.sub(r'<script.*?</script>','',b,flags=re.S)))
    out.write(f"{fn}: body={len(b)} text={len(txt)} p={b.count('<p')}\n")

open(os.path.join(D,"_final2.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
