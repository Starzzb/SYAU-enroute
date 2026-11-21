
import re, os
D=r"O:\dev\enroute-android\docs\_probe\feed-render"
raw=open(os.path.join(D,"jingsai-list.html"),"rb").read().decode("gb18030","replace")
m=re.search(r'<base[^>]*>', raw, re.I); print("BASE:", m.group(0) if m else None)
for mm in re.finditer(r'.{120}article-24180-1\.html.{40}', raw, re.S):
    print("CTX:", mm.group(0).replace("\n"," ")[:220])
print("----")
for mm in re.finditer(r'.{160}article-24182-1\.html.{20}', raw, re.S):
    print("CTX2:", mm.group(0).replace("\n"," ")[:240])
