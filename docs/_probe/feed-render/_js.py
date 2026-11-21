
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

t=open(os.path.join(D,"jingsai-detail.html"),"rb").read().decode("gb18030","replace")
# find where the article text lives: search for a long chinese run
m=re.search(r'[\u4e00-\u9fff]{30,}', t[20000:])
for kw in ["postmessage","t_f","article_content","content_box","news_content","文章","正文"]:
    idx=[mm.start() for mm in re.finditer(kw, t)][:5]
    out.write(f"jing kw={kw} -> {idx}\n")
# print the middle chunk
i=t.find("postmessage")
out.write("---- jingsai around 'postmessage' @%d ----\n" % i)
if i>0: out.write(t[i-3000:i+3000]+"\n")

open(os.path.join(D,"_jingsai.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
