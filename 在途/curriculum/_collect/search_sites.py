# -*- coding: utf-8 -*-
import base64, re, json, urllib.parse, urllib.request, ssl, gzip, zlib
import recon
HOSTS = ["thxy.syau.edu.cn","nxy.syau.edu.cn","spxy.syau.edu.cn","jgxy.syau.edu.cn","xdxy.syau.edu.cn","gcxy.syau.edu.cn","slxy.syau.edu.cn","yyxy.syau.edu.cn","zbxy.syau.edu.cn","lin.syau.edu.cn","swxy.syau.edu.cn","lxy.syau.edu.cn","myxy.syau.edu.cn","marx.syau.edu.cn","tyb.syau.edu.cn","wyb.syau.edu.cn"]
kw = "大纲"
b64 = base64.b64encode(kw.encode("utf-8")).decode()
out={}
for h in HOSTS:
    url = "https://%s/ssjg.jsp?wbtreeid=1" % h
    body = urllib.parse.urlencode({"lucenenewssearchkey":b64,"_lucenesearchtype":"1","searchScope":"0","showkeycode":kw}).encode()
    req = urllib.request.Request(url, data=body, headers={"User-Agent":recon.UA,"Content-Type":"application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req, timeout=25, context=recon.CTX) as r:
            raw=r.read(); enc=r.headers.get("Content-Encoding","")
            if "gzip" in enc: raw=gzip.decompress(raw)
            html=raw.decode("utf-8","replace")
        res=[]
        for m in re.finditer(r'<h3><a href="([^"]+)">(.*?)</a></h3>.*?<p class="time">.*?(\d{4}-\d{2}-\d{2})', html, re.S):
            t=re.sub(r"<[^>]+>","",m.group(2))
            res.append({"标题":t,"url":m.group(1),"日期":m.group(3)})
        out[h]={"count":len(res),"items":res[:20]}
    except Exception as e:
        out[h]={"error":"%s: %s"%(type(e).__name__,e)}
json.dump(out,open("search_dagang.json","w",encoding="utf-8"),ensure_ascii=False,indent=1)
for h,v in out.items():
    print(h, v.get("count"), v.get("error",""))
    for it in v.get("items",[]): print("   ", it["日期"], it["标题"][:60], it["url"][:80])
