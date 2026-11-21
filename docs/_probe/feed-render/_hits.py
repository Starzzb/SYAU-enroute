
import re, os, json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
KEYS = ["jwc","www","xsc","news","job","cxcy","tw"]
res = {}
for k in KEYS:
    for suf in ("-detail.html","-detail2.html","-list.html"):
        p = os.path.join(D,k+suf)
        if not os.path.exists(p): continue
        b = open(p,'rb').read()
        t = b.decode('utf-8','replace')
        hits = []
        for pat, tag in [(r'href="([^"]*download\.jsp[^"]*)"','DOWNLOAD'),
                         (r'href="([^"]*\.pdf[^"]*)"','PDFLINK'),
                         (r'<img[^>]{0,300}>','IMG'),
                         (r'<(?:iframe|embed|object)[^>]{0,300}>','EMBED'),
                         (r'附件[：:]','ATTACHWORD')]:
            for m in re.finditer(pat, t, re.I):
                s = max(0, m.start()-90); e = min(len(t), m.end()+90)
                ctx = re.sub(r'\s+',' ', t[s:e])
                hits.append({'t':tag,'at':m.start(),'ctx':ctx[:240]})
        res[k+suf] = {'bytes': len(b), 'hits': hits[:24], 'hits_total': len(hits)}
        # pagination markers
        res[k+suf]['page_marks'] = [re.sub(r'\s+',' ',t[m.start():m.start()+120]) for m in re.finditer(r'p_pages|共\s*\d+\s*条|totalpage|pageCount', t)][:3]
open(os.path.join(D,'_hits.json'),'w',encoding='ascii').write(json.dumps(res, ensure_ascii=True, indent=1))
print("OK", {k: v['hits_total'] for k,v in res.items()})
