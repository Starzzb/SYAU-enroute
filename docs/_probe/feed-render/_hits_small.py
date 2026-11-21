
import json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
j = json.load(open(D+r"\_hits.json"))
out = {}
for k,v in j.items():
    non_img = [ "["+h["t"]+"] "+h["ctx"] for h in v["hits"] if h["t"]!="IMG" ][:8]
    imgs = [ h["ctx"] for h in v["hits"] if h["t"]=="IMG" ][:3]
    out[k] = {"page": v.get("page_marks"), "attach": non_img, "img": imgs}
s = json.dumps(out, ensure_ascii=True, indent=0)
open(D+r"\_hits_small.json","w",encoding="ascii").write(s)
print(len(s))
