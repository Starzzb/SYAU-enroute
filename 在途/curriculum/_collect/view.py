import json
d=json.load(open("recon.json",encoding="utf-8"))
lines=[]
for h,r in d.items():
    if r["大纲线索"]:
        lines.append("## %s %s"%(r["学院"],h))
        for hit in r["大纲线索"]:
            lines.append("  栏目=%s %s | 标题=%s | 条目=%d"%(hit["栏目"],hit["url"],hit["页面标题"][:40],hit["条目数"]))
            for it in hit["条目"][:8]:
                lines.append("     - %s | %s"%(it["文本"][:60], it["url"][:110]))
open("recon_view.txt","w",encoding="utf-8").write("\n".join(lines))
print(len(lines))
