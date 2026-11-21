
import re
D = r"O:/dev/enroute-android/docs/_probe/feed-render"
out=[]
n = open(D+r"\news-detail.html",'rb').read().decode('utf-8','replace')
i = n.find('vsb_content')
out.append("=== news detail around title/vsb_content ===")
out.append(re.sub(r'\s+',' ', n[max(0,i-1800):i+400]))
l = open(D+r"\news-list.html",'rb').read().decode('utf-8','replace')
j = l.find('info/1005/24442.htm')
out.append("=== news list ctx ===")
out.append(re.sub(r'\s+',' ', l[max(0,j-700):j+700]))
open(D+r"\_news.txt",'w',encoding='utf-8').write("\n".join(out))
print("ok")
