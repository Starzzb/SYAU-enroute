
import re
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out=[]
t = open(D+r"\tw-list.html",'rb').read().decode('utf-8','replace')
for m in list(re.finditer(r'href="info/1053/\d+\.htm"', t))[:2]:
    s=max(0,m.start()-800); e=min(len(t), m.end()+800)
    out.append("---TW CTX---\n"+re.sub(r'\s+',' ', t[s:e])[:900])
n = open(D+r"\news-detail.html",'rb').read().decode('utf-8','replace')
out.append("=== news detail dates ===")
for m in list(re.finditer(r'\d{4}-\d{2}-\d{2}', n))[:6]:
    out.append(re.sub(r'\s+',' ', n[max(0,m.start()-110):m.start()+40]))
out.append("=== jwc detail dates ===")
j = open(D+r"\jwc-detail.html",'rb').read().decode('utf-8','replace')
for m in list(re.finditer(r'\d{4}-\d{2}-\d{2}', j))[:4]:
    out.append(re.sub(r'\s+',' ', j[max(0,m.start()-110):m.start()+30]))
open(D+r"\_dates.txt",'w',encoding='utf-8').write("\n".join(out))
print("ok")
