
import re, os, json
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
KEYS = ["jwc","www","xsc","news","job","cxcy","tw"]
out = {}
for k in KEYS:
    for suf in ("-detail.html","-detail2.html"):
        p = D+"\\"+k+suf
        if not os.path.exists(p): continue
        t = open(p,'rb').read().decode('utf-8','replace')
        out[k+suf] = {
          "data_src": len(re.findall(r'data-src\s*=', t)),
          "data_original": len(re.findall(r'data-original\s*=', t)),
          "lazy_class": len(re.findall(r'class="[^"]*lazy', t)),
          "vsl_attr": len(re.findall(r'vurl=', t)),
          "vsbhref": len(re.findall(r'vsbhref', t)),
          "img_total_doc": len(re.findall(r'<img', t)),
          "content_wrapper": [m.group(0)[:80] for m in re.finditer(r'<div[^>]*(id="vsb_content[^"]*"|class="v_news_content")[^>]*>', t)][:3],
        }
print(json.dumps(out, ensure_ascii=True, indent=0))
