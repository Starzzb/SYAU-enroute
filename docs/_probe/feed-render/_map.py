
import json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
todo=json.load(open(D+r"\_todo.json"))
lines=[]
for k,u,o in todo:
    lines.append(k+"  "+o.split("\\")[-1]+"  "+u)
open(D+r"\_map.txt",'w',encoding='utf-8').write("\n".join(lines))
print(len(lines))
