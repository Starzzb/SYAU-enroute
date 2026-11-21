
import re, os, glob, struct, json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out=[]
def jpg_size(p):
    d=open(p,'rb').read(); i=2
    while i < len(d)-9:
        if d[i]!=0xFF: i+=1; continue
        mk=d[i+1]
        if mk in (0xC0,0xC1,0xC2,0xC3):
            h,w=struct.unpack('>HH', d[i+5:i+9]); return (w,h)
        if mk in (0xD8,0xD9): i+=2; continue
        i += 2+struct.unpack('>H', d[i+2:i+4])[0]
    return None
p=D+r"\news-img1-ori.jpg"
out.append("ori img: dim=%s bytes=%d" % (str(jpg_size(p)), os.path.getsize(p)))

# list item date formats
for k in ["jwc","www","xsc","news","job","cxcy","tw"]:
    t=open(D+"\\"+k+"-list.html",'rb').read().decode('utf-8','replace')
    m=re.search(r'href="(?:\.\./)?info/\d+/\d+\.htm"', t)
    s=max(0,m.start()-420); e=min(len(t), m.end()+120)
    out.append("### "+k+" list item raw:\n"+re.sub(r'\s+',' ', t[s:e]))

# max body image count per source
def body_of(t):
    m=re.search(r'<div[^>]*id="vsb_content(_\d+)?"', t)
    if not m: return ''
    seg=t[m.start():]
    ends=[]
    for mk in ['id="div_vote_id"','class="v_news_content_end','<div id="footer"','id="footer"','id="bottom"','copyright','id="wx_share']:
        i=seg.find(mk)
        if i>0: ends.append(i)
    return seg[:min(ends)] if ends else seg[:60000]
maximg={}
for f in sorted(glob.glob(D+r"\\*-*.html")):
    bn=os.path.basename(f)
    if bn.startswith('_') or '-list' in bn: continue
    k=bn.split('-')[0]
    b=body_of(open(f,'rb').read().decode('utf-8','replace'))
    if not b: continue
    n=len(re.findall(r'<img', b, re.I))
    maximg[k]=max(maximg.get(k,0), n)
out.append("max body img per source: "+json.dumps(maximg,ensure_ascii=True))
open(D+r"\_measure2.txt",'w',encoding='utf-8').write("\n".join(out))
print("ok")
