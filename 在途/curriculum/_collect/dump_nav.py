# -*- coding: utf-8 -*-
import recon, re, json
from urllib.parse import urljoin
st, h = recon.get("https://thxy.syau.edu.cn/")
ls = recon.links("https://thxy.syau.edu.cn/", h)
seen=set(); out=[]
for t,u in ls:
    if u in seen: continue
    seen.add(u)
    out.append({"t":t,"u":u})
open("nav_thxy.json","w",encoding="utf-8").write(json.dumps(out,ensure_ascii=False,indent=0))
print(len(out))
