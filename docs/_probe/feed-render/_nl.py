
import re
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out=[]
l = open(D+r"\news-list.html",'rb').read().decode('utf-8','replace')
out.append("=== news list date formats ===")
for pat in [r'\d{4}-\d{2}-\d{2}', r'\d{4}/\d{2}/\d{2}', r'\d{2}-\d{2}', r'date']:
    ms=list(re.finditer(pat,l))
    out.append(pat+" n="+str(len(ms)))
    for m in ms[:2]:
        out.append("   "+re.sub(r'\s+',' ', l[max(0,m.start()-140):m.start()+60]))
# find the news list block (list2 / txt list)
i = l.find('24442.htm')
seg = l[i-500:i+3000]
out.append("=== news list item block ===")
out.append(re.sub(r'\s+',' ', seg)[:2500])
open(D+r"\_newslist.txt",'w',encoding='utf-8').write("\n".join(out))
print("ok")
