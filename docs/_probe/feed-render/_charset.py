
import re, json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out={}
for k in ["jwc","www","xsc","news","job","cxcy","tw"]:
    b=open(D+"\\"+k+"-list.html",'rb').read()
    m=re.search(rb'charset\s*=\s*["\']?([\w-]+)', b[:3000], re.I)
    declared=m.group(1).decode() if m else None
    utf8_ok=True
    try: b.decode('utf-8')
    except UnicodeDecodeError as e: utf8_ok=False
    gbk_ok=True
    try: b.decode('gb18030')
    except: gbk_ok=False
    bom = b[:3]==b'\xef\xbb\xbf'
    out[k]={"declared":declared,"utf8_decodable":utf8_ok,"gbk_decodable":gbk_ok,"bom":bom}
open(D+r"\_charset.json","w",encoding="ascii").write(json.dumps(out,indent=1))
print(json.dumps(out,indent=1))
