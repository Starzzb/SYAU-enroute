# -*- coding: utf-8 -*-
import base64, re, urllib.parse, urllib.request, json
import recon
kw="课程大纲"
b64=base64.b64encode(kw.encode()).decode()
for scope in ("0","1","2"):
    body=urllib.parse.urlencode({"lucenenewssearchkey":b64,"_lucenesearchtype":"1","searchScope":scope,"showkeycode":kw}).encode()
    req=urllib.request.Request("https://www.syau.edu.cn/ssjg.jsp?wbtreeid=1001",data=body,headers={"User-Agent":recon.UA,"Content-Type":"application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req,timeout=30,context=recon.CTX) as r: raw=r.read()
    except Exception as e:
        print("scope",scope,"ERR",e); continue
    h=raw.decode("utf-8","replace")
    res=[]
    for m in re.finditer(r'<h3><a href="([^"]+)">(.*?)</a></h3>.*?<p class="time">.*?(\d{4}-\d{2}-\d{2})',h,re.S):
        res.append((m.group(3),re.sub(r"<[^>]+>","",m.group(2))[:60],m.group(1)[:90]))
    print("scope",scope,"n=",len(res))
    for x in res[:25]: print("  ",x)
