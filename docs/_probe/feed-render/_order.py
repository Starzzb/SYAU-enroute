
import re, os, json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out={}
# jwc/xsc/news/job/cxcy: date near link; tw: separate
for k in ["jwc","www","xsc","news","job","cxcy","tw"]:
    t = open(D+"\\"+k+"-list.html",'rb').read().decode('utf-8','replace')
    pairs=[]
    for m in re.finditer(r'href="(?:\.\./)?info/\d+/\d+\.htm"', t):
        s=max(0,m.start()-260); e=min(len(t), m.end()+400)
        seg=t[s:e]
        d=re.search(r'(\d{4}-\d{2}-\d{2})', seg)
        ti=re.search(r'title="([^"]{6,80})"', seg)
        pairs.append((d.group(1) if d else None, (ti.group(1) if ti else "")[:30]))
    out[k]=pairs[:12]
print(json.dumps(out, ensure_ascii=True, indent=0)[:4000])
