
import json, subprocess, os
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
todo = json.load(open(D+r"\_todo.json"))
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
ok=0; fail=[]
for k,u,o in todo:
    r = subprocess.run(["curl","-sS","-o",o,"-w","%{http_code}","--max-time","30","-A",UA,u],capture_output=True,text=True)
    code = r.stdout.strip()
    if code=="200": ok+=1
    else: fail.append((k,u,code))
print("ok",ok,"fail",len(fail))
for f in fail[:10]: print(f)
