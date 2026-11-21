
import json, os, re, subprocess
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
links = json.load(open(D+r"\_links.json"))
BASE = {"jwc":"https://jwc.syau.edu.cn/index/","www":"https://www.syau.edu.cn/index/",
        "xsc":"https://xsc.syau.edu.cn/","news":"https://news.syau.edu.cn/",
        "job":"https://job.syau.edu.cn/","cxcy":"https://job.syau.edu.cn/cxcy1/",
        "tw":"https://tw.syau.edu.cn/"}
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
def norm(k, h):
    if h.startswith("http"): return h
    if h.startswith("../"): 
        b = BASE[k]
        return os.path.normpath(b + h).replace("\\","/").replace("https:/","https://").replace("http:/","http://")
    return BASE[k] + h.lstrip("./")
todo=[]
for k,v in links.items():
    picks = v[2:8]  # skip 2 already fetched
    for i,h in enumerate(picks):
        u = norm(k,h)
        out = f"{D}\\{k}-s{i+1}.html"
        todo.append((k,u,out))
open(D+r"\_todo.json","w",encoding="ascii").write(json.dumps(todo,ensure_ascii=True,indent=0))
print(len(todo))
for k,u,o in todo[:6]: print(k,u,os.path.basename(o))
