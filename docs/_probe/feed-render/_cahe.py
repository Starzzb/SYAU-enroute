
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()
t=open(os.path.join(D,"cahe-list.html"),"rb").read().decode("utf-8","replace")
ids=re.findall(r'/site/content/(\d+)\.html', t)
out.write(f"cahe list content ids={len(set(ids))}\n")
# pagination markup near 'page'
for m in list(re.finditer(r'(page_content|page_item|pagelist|layui-laypage|共\s*\d+\s*页|共\s*\d+\s*条)', t))[:6]:
    out.write("CAHE: "+t[max(0,m.start()-120):m.start()+300].replace("\n"," ")[:400]+"\n")
open(os.path.join(D,"_cahe.txt"),"w",encoding="utf-8").write(out.getvalue())
print(out.getvalue())
