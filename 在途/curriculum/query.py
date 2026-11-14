# -*- coding: utf-8 -*-
"""在本数据集上做查询的小工具（仅用标准库）。

用法：
  python query.py major 2024 农业资源与环境
  python query.py course 901040101
  python query.py search 土壤
  python query.py syllabus 土壤学
  python query.py stats
"""
import json, os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
OUTD = os.path.join(BASE, "json")

def load(n):
    return json.load(open(os.path.join(OUTD, n), encoding="utf-8"))

def cmd_major(year, name):
    plans = load("培养方案_%d.json" % int(year))
    hits = [p for p in plans if name in p["专业名称"]]
    for p in hits:
        print("=" * 60)
        print(p["年级"], p["专业名称"], p["培养类型"], "页码", p["章节页范围"])
        print("主干学科:", (p.get("主干学科") or "").replace("\n", " "))
        print("核心课程:", (p.get("专业核心课程") or "").replace("\n", " "))
        print("修业年限:", (p.get("修业年限") or "").replace("\n", ""))
        print("授予学位:", p.get("授予学位"))
        print("培养目标:", len(p.get("培养目标列表", [])), "条")
        print("毕业要求:", len(p.get("毕业要求列表", [])), "条 /",
              sum(len(r["指标点"]) for r in p.get("毕业要求列表", [])), "指标点")
        print("课程:", len(p.get("课程设置与教学进程", [])), "条")
    if not hits:
        print("未找到，可选：", [p["专业名称"] for p in plans][:40])

def cmd_course(code):
    idx = load("课程总索引.json")
    hits = [r for r in idx if r["课程代码"] == code] or [r for r in idx if r["归一名称"] == code]
    if not hits:
        print("未找到课程", code)
        return
    for r in hits:
        print("=" * 60)
        print(r["课程代码"], r["主名称"], "| 英文:", " / ".join(r["英文名称"][:2]))
        print("学分:", r["学分"], "总学时:", r["总学时"], "开课学期:", r["开课学期"])
        print("出现于:", r["出现于"])
        print("大纲类型:", r["大纲课程类型"], "| 教研室:", r["开课教研室"],
              "| 大纲页:", r["大纲页范围"])
        print("课程类别:", r["课程类别"])
        print("2024开设于", len(r["专业_2024"]), "个专业；2025开设于", len(r["专业_2025"]), "个专业")

def cmd_search(kw):
    idx = load("课程总索引.json")
    hits = [r for r in idx if kw in r["检索键"] or kw in (r["主名称"] or "")]
    print("命中", len(hits), "门课程")
    for r in hits[:60]:
        print(" ", r["课程代码"], r["主名称"], "|", "/".join(r["学分"]), "学分",
              "| 大纲" if r["有课程大纲"] else "| 无大纲")

def cmd_syllabus(kw):
    syl = load("课程大纲_2021_土地与环境学院.json")
    hits = [c for c in syl if kw in c["课程名称"] or kw in (c["英文名称"] or "")]
    for c in hits:
        print("=" * 60)
        print(c["课程名称"], "|", c["英文名称"], "|", c["课程类型"], "| 页", c["pdf页码范围"])
        b = c.get("基本信息", {})
        print("学分:", c.get("课程学分"), "学时:", c.get("理论学时"), "类别:", c.get("课程类别"))
        print("教材:", (b.get("选用教材") or "")[:80])
        print("目标:", [g["内容"][:30] for g in c.get("课程目标列表", [])][:4])
        print("章节:", list(c.get("章节", {}).keys())[:6], "...")
    if not hits:
        print("未找到，样例：", [c["课程名称"] for c in syl][:30])

def cmd_stats():
    m = load("文档清单.json")
    for k, v in m["统计"].items():
        print(" ", k, v)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(0)
    op = sys.argv[1]
    args = sys.argv[2:]
    if op == "major": cmd_major(args[0], args[1])
    elif op == "course": cmd_course(args[0])
    elif op == "search": cmd_search(args[0])
    elif op == "syllabus": cmd_syllabus(args[0])
    elif op == "stats": cmd_stats()
    else: print(__doc__)