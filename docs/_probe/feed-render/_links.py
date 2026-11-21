
import re, os, json, subprocess, sys
D = r"O:/dev/enroute-android/docs/_probe\feed-render"
D = D.replace("feed-render","feed-render")
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
KEYS = ["jwc","www","xsc","news","job","cxcy","tw"]
BASE = {"jwc":"https://jwc.syau.edu.cn/index/","www":"https://www.syau.edu.cn/index/",
        "xsc":"https://xsc.syau.edu.cn/","news":"https://news.syau.edu.cn/",
        "job":"https://job.syau.edu.cn/","cxcy":"https://job.syau.edu.cn/cxcy1/",
        "tw":"https://tw.syau.edu.cn/"}
links = {}
for k in KEYS:
    t = open(D+"\\"+k+"-list.html",'rb').read().decode('utf-8','replace')
    hs = re.findall(r'href="([^"]*info/\d+/\d+\.htm)"', t)
    seen=[]; 
    for h in hs:
        if h not in seen: seen.append(h)
    links[k]=seen
open(D+r"\_links.json","w",encoding="ascii").write(json.dumps(links,ensure_ascii=True,indent=0))
print({k:len(v) for k,v in links.items()})
