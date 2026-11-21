
import os, re, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
out=io.StringIO()

# 1) fwwb full
t=open(os.path.join(D,"fwwb-detail.html"),"rb").read().decode("utf-8","replace")
out.write("#### FWWB FULL ####\n"+t+"\n\n")

# 2) jingsai: locate postmessage / t_f
t2=open(os.path.join(D,"jingsai-detail.html"),"rb").read().decode("gb18030","replace")
for pat in [r'id="postmessage_\d+"', r'class="t_f"', r'<td class="t_f"', r'id="p_\d+"']:
    for mm in list(re.finditer(pat, t2))[:3]:
        out.write("JINGSAI HIT "+pat+" @"+str(mm.start())+"\n")
i=t2.find('class="t_f"')
if i>0: out.write("#### JINGSAI t_f REGION ####\n"+t2[i-500:i+3000]+"\n\n")

# 3) cxcyart body
t3=open(os.path.join(D,"cxcyart-detail.html"),"rb").read().decode("utf-8","replace")
i=t3.find("articledetail")
# find main body: look for editor images context
j=t3.find("/static/uploads/res/editor/")
if j>0: out.write("#### CXCYART BODY REGION ####\n"+t3[max(0,j-2000):j+1200]+"\n\n")

open(os.path.join(D,"_dump.txt"),"w",encoding="utf-8").write(out.getvalue())
print("ok")
