
import json,io,sys
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
j = json.load(open(D+r"\_hits.json"))
out = []
for k in ["cxcy-detail.html","cxcy-detail2.html","tw-detail.html","tw-detail2.html","job-detail.html","cxcy-list.html","tw-list.html"]:
    v = j.get(k)
    if not v: continue
    out.append("### "+k+" page="+str(v.get("page_marks")))
    for h in v["hits"]:
        if h["t"]=="IMG": continue
        out.append(" ["+h["t"]+"] "+h["ctx"])
open(D+r"\_sel.txt","w",encoding="utf-8").write("\n".join(out))
print(len(out))
