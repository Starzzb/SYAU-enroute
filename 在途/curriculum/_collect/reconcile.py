# -*- coding: utf-8 -*-
import os, json, pymupdf
from w import NAMES, DEST
rows=[]
for fid,name in NAMES.items():
    p=os.path.join(DEST,"沈阳农业大学%s本科课程大纲（2021版）.pdf"%name)
    if not os.path.exists(p): continue
    with open(p,"rb") as f: magic=f.read(4)
    if magic!=b"%PDF": print("坏文件",name,p); continue
    with pymupdf.open(p) as d:
        pages=d.page_count; title=(d.metadata or {}).get("title","")
    meta={"wbfileid":fid,"学院":name,"文件":p,"字节":os.path.getsize(p),"页数":pages,
          "pdf标题":title,"时间":"2026-11-17 (校验/补录)"}
    out=os.path.join(os.path.dirname(os.path.abspath(__file__)),"done_%d.json"%fid)
    if not os.path.exists(out):
        json.dump(meta,open(out,"w",encoding="utf-8"),ensure_ascii=False,indent=1)
        print("补录",name,pages,"页",os.path.getsize(p),"B")
    else:
        print("已有记录",name)
