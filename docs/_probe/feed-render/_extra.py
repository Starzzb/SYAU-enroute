
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

# 1) cxcy $.post context
t=open(os.path.join(D,"cxcycomp-detail.html"),"rb").read().decode("utf-8","replace")
i=t.find("$.post")
out.write("#### cxcy $.post ctx ####\n"+t[max(0,i-900):i+700]+"\n\n")

# 2) robots
for f in ["www.tiaozhanbei.net.robots.txt","jyt.ln.gov.cn.robots.txt","www.52jingsai.com.robots.txt"]:
    p=os.path.join(D,f)
    if os.path.exists(p):
        out.write("#### "+f+" ####\n"+open(p,"rb").read().decode("utf-8","replace")[:1800]+"\n\n")

# 3) list pagination markers
for k in ["cxcycomp","cxcydc","cxcyart","tzb","lnjyt","cahe","jingsai","fwwb"]:
    raw=open(os.path.join(D,k+"-list.html"),"rb").read()
    t2 = raw.decode("gb18030","replace") if k=="jingsai" else raw.decode("utf-8","replace")
    marks=[]
    for pat in [r'共\s*(\d+)\s*[条页]', r'page=\d+', r'/page/\d+', r'index_\d+\.shtml', r'createPageHTML', r'下一页', r'>尾页<', r'totalpage', r'pageNum']:
        m=re.findall(pat, t2, re.I)
        if m: marks.append(f"{pat}->{len(m)}: {m[:4]}")
    out.write(f"## {k} pagination: {marks}\n")

open(os.path.join(D,"_extra.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
