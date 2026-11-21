
import os, io
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
t=open(os.path.join(D,"jingsai-detail.html"),"rb").read().decode("gb18030","replace")
buf=io.StringIO()
buf.write("#### around article_content @16546 ####\n")
buf.write(t[16100:20000]+"\n\n")
buf.write("#### around 正文 @24624 ####\n")
buf.write(t[23800:27000]+"\n")
open(os.path.join(D,"_jingsai2.txt"),"w",encoding="utf-8").write(buf.getvalue())
print("len", len(t))
