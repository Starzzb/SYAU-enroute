
import re, os
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out=[]
for k in ["news","tw","www","job","cxcy","xsc","jwc"]:
    t = open(D+"\\"+k+"-detail.html",'rb').read().decode('utf-8','replace')
    out.append("### "+k)
    for pat in [r'发布时间[^<]{0,60}', r'时间：\s*[\d\-年月日\.]{6,20}', r'发布日期[^<]{0,40}',
                r'\d{4}-\d{2}-\d{2}', r'\d{4}年\d{1,2}月\d{1,2}日', r'点击数', r'作者[：:]']:
        ms = list(re.finditer(pat, t))
        if ms:
            out.append("  ["+pat+"] n="+str(len(ms))+" :: "+re.sub(r'\s+',' ', t[max(0,ms[0].start()-50):ms[0].start()+80]))
open(D+r"\_meta.txt",'w',encoding='utf-8').write("\n".join(out))
print("ok")
