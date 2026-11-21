
import re, os
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
t=open(D+r"\www-list.html",'rb').read().decode('utf-8','replace')
i=t.find('10999.htm')
seg=t[i-200:i+900]
open(D+r"\_www.txt",'w',encoding='utf-8').write(re.sub(r'\s+',' ',seg))
print("ok")
