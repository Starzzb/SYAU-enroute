
import re, os, glob
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out=[]
def body_of(t):
    m = re.search(r'<div[^>]*id="vsb_content(_\d+)?"', t)
    if not m: return ''
    seg=t[m.start():]
    ends=[]
    for mk in ['id="div_vote_id"','class="v_news_content_end','<div id="footer"','id="footer"','id="bottom"','copyright','id="wx_share']:
        i=seg.find(mk)
        if i>0: ends.append(i)
    return seg[:min(ends)] if ends else seg[:60000]

for k in ["jwc","news","tw"]:
    t = open(D+"\\"+k+"-detail.html",'rb').read().decode('utf-8','replace')
    b = body_of(t)
    out.append("### "+k+" /__local image context")
    for m in re.finditer(r'<img[^>]*__local[^>]*>', b):
        out.append("  "+re.sub(r'\s+',' ', b[max(0,m.start()-160):m.end()+80]))

out.append("=== E-class sweep over all sampled detail pages ===")
cnt=0; imgonly=[]
for f in sorted(glob.glob(D+r"\\*-*.html")):
    bn=os.path.basename(f)
    if '-' not in bn or bn.startswith('_') or '-list' in bn: continue
    t=open(f,'rb').read().decode('utf-8','replace'); b=body_of(t)
    if not b: continue
    cnt+=1
    tx=re.sub(r'<(script|style).*?</\1>','',b,flags=re.S|re.I)
    tx=re.sub(r'<[^>]+>',' ',tx).replace('&nbsp;',' '); tx=re.sub(r'\s+',' ',tx).strip()
    n_img=len(re.findall(r'<img', b, re.I))
    if len(tx)<40 and n_img>=1: imgonly.append((bn,len(tx),n_img))
out.append("pages with vsb_content checked: "+str(cnt))
out.append("image-only candidates (<40 chars text, >=1 img): "+str(imgonly))
out.append("=== xsc iframe exact markup ===")
x=open(D+r"\xsc-detail.html",'rb').read().decode('utf-8','replace')
for m in re.finditer(r'<iframe[^>]*>', x):
    out.append("  "+m.group(0))
open(D+r"\_final.txt",'w',encoding='utf-8').write("\n".join(out))
print("ok")
