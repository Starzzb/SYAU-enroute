
import re, os, json, urllib.parse
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
KEYS = ["jwc","www","xsc","news","job","cxcy","tw"]

def load(p):
    b = open(p,'rb').read()
    for enc in ('utf-8','gb18030'):
        try: return b.decode(enc), b, enc
        except Exception: pass
    return b.decode('utf-8','replace'), b, 'utf8-replace'

def body_of(txt):
    # locate the VSB content div and cut at its true end (markers used by VSB themes)
    m = re.search(r'<div[^>]*id="vsb_content(_\d+)?"', txt)
    if not m: return ''
    s = m.start()
    seg = txt[s:]
    ends = []
    for mk in ['<div id="div_vote_id"', 'id="div_vote_id"', 'class="v_news_content_end',
               '<div id="footer"', 'id="footer"', 'class="footer"', 'id="bottom"',
               '<div class="bottom"', 'copyright', 'id="wx_share']:
        i = seg.find(mk)
        if i > 0: ends.append(i)
    e = min(ends) if ends else min(len(seg), 60000)
    return seg[:e]

def attr(tag, name):
    m2 = re.search(r'(?:^|\s)'+name+r'\s*=\s*["\']([^"\']*)', tag, re.I)
    return m2.group(1) if m2 else None

def analyze(path):
    txt, raw, enc = load(path)
    m = re.search(r'charset\s*=\s*["\']?([\w-]+)', txt[:3000], re.I)
    body = body_of(txt)
    imgs = re.findall(r'<img[^>]*>', body, re.I)
    img_info = [{'src':attr(t,'src'),'lazy':attr(t,'data-src') or attr(t,'data-original') or attr(t,'_src'),
                 'w':attr(t,'width'),'h':attr(t,'height'),'style':attr(t,'style')} for t in imgs]
    pdfs = re.findall(r'href="([^"]+\.pdf)"', body, re.I)
    docs = re.findall(r'href="([^"]+\.(?:docx?|xlsx?|zip|rar|7z|wps))"', body, re.I)
    dl = re.findall(r'href="(/system/_content/download\.jsp\?[^"]+)"', body)
    dl_txt = re.findall(r'>([^<>]{0,80}\.(?:pdf|docx?|xlsx?|zip|rar|pptx?))<', body, re.I)
    emb = re.findall(r'<(iframe|embed|object)[^>]*>', body, re.I)
    srcs = re.findall(r'(?:src|data)\s*=\s*["\']([^"\']*\.pdf[^"\']*)', body, re.I)
    text = re.sub(r'<(script|style).*?</\1>','',body,flags=re.S|re.I)
    text = re.sub(r'<[^>]+>',' ',text)
    text = text.replace('&nbsp;',' ').replace('&#160;',' ')
    text = re.sub(r'[ \t\r\n]+',' ',text).strip()
    # image src domains / path kinds
    kinds = set()
    for i in img_info:
        s = i['src'] or ''
        if '/__local/' in s: kinds.add('__local')
        elif s.startswith('http'): kinds.add('abs:'+urllib.parse.urlparse(s).netloc)
        elif s.startswith('../../') or s.startswith('../'): kinds.add('relative')
    return {
      'bytes': len(raw), 'enc': enc, 'meta_charset': (m.group(1) if m else '?'),
      'has_vsb_content': 'vsb_content' in txt, 'body_chars': len(body),
      'text_chars': len(text), 'text_head': text[:150],
      'p': len(re.findall(r'<p[ >]', body, re.I)), 'br': len(re.findall(r'<br', body, re.I)),
      'img_n': len(imgs), 'img_kinds': sorted(kinds), 'imgs': img_info[:8],
      'local_refs': len(re.findall(r'/__local/', body)),
      'pdf_links': [urllib.parse.unquote(p) for p in pdfs][:6],
      'office_links': docs[:8], 'dl_jsp': dl[:4], 'attach_names': dl_txt[:8],
      'embed': emb[:4], 'pdf_in_src': srcs[:3],
    }

out = {}
for k in KEYS:
    for suf in ('-detail.html','-detail2.html'):
        p = os.path.join(D, k+suf)
        if os.path.exists(p):
            out[k+suf] = analyze(p)
open(os.path.join(D,'_analysis.json'),'w',encoding='ascii').write(json.dumps(out, ensure_ascii=True, indent=1))
print("OK", len(out))
