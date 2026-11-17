# -*- coding: utf-8 -*-
import http.cookiejar, urllib.request, recon, os
jar=http.cookiejar.MozillaCookieJar("cookies.txt"); jar.load(ignore_discard=True,ignore_expires=True)
op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), urllib.request.HTTPSHandler(context=recon.CTX))
for fid in (11763701,11763705,11763713):
    u="https://jwc.syau.edu.cn/system/_content/download.jsp?urltype=news.DownloadAttachUrl&owner=1584116291&wbfileid=%d"%fid
    r=op.open(urllib.request.Request(u,headers={"User-Agent":recon.UA,"Referer":"https://jwc.syau.edu.cn/rcpy/kcdg.htm"}),timeout=90)
    d=r.read(200000)
    print(fid, r.headers.get("Content-Type"), len(d), d[:4])
