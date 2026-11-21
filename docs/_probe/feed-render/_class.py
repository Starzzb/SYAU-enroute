
import re, os, json, glob
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
KEYS = ["jwc","www","xsc","news","job","cxcy","tw"]
def body_of(t):
    m = re.search(r'<div[^>]*id="vsb_content(_\d+)?"', t)
    if not m: return ''
    seg = t[m.start():]
    ends=[]
    for mk in ['id="div_vote_id"','class="v_news_content_end','<div id="footer"','id="footer"','id="bottom"','copyright','id="wx_share']:
        i=seg.find(mk)
        if i>0: ends.append(i)
    return seg[:min(ends)] if ends else seg[:60000]
rows={}
for f in sorted(glob.glob(D+r"\\*.html")):
    b = os.path.basename(f)
    key = b.split('-')[0]
    if key not in KEYS: continue
    if '-list' in b: continue
    t = open(f,'rb').read().decode('utf-8','replace')
    body = body_of(t)
    if not body: rows[b]={"cls":"?","note":"no vsb_content"}; continue
    txt = re.sub(r'<(script|style).*?</\1>','',body,flags=re.S|re.I)
    txt = re.sub(r'<[^>]+>',' ',txt).replace('&nbsp;',' ')
    txt = re.sub(r'\s+',' ',txt).strip()
    imgs = re.findall(r'<img[^>]*>', body, re.I)
    pdfs = re.findall(r'\.pdf', body, re.I)
    dl = re.findall(r'download\.jsp', body)
    ifr = re.findall(r'<iframe[^>]*\.pdf', body, re.I)
    rows[b]={"key":key,"text":len(txt),"img":len(imgs),"pdf":len(pdfs),"dljsp":len(dl),
             "iframe_pdf":len(ifr),"head":txt[:60]}
open(D+r"\_class.json","w",encoding="ascii").write(json.dumps(rows,ensure_ascii=True,indent=0))
# aggregate
agg={}
for b,v in rows.items():
    k=v.get("key"); agg.setdefault(k,[]).append(v)
summ={}
for k,vs in agg.items():
    summ[k]={"n":len(vs)}
print(json.dumps(summ,ensure_ascii=True))
for b,v in list(rows.items()):
    print(b, v)
