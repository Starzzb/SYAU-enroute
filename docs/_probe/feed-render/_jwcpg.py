
import re
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
for f in ["jwc-list.html","jwc-list-p2.html","jwc-list-p2b.html"]:
    t=open(D+"\\"+f,'rb').read().decode('utf-8','replace')
    ds=re.findall(r'(\d{4}-\d{2}-\d{2})', t)
    pg=re.search(r'共\s*\d+\s*条|p_no', t)
    print(f, "dates:", ds[:2], "...", ds[-2:] if ds else [], "n=",len(ds))
