# -*- coding: utf-8 -*-
"""查询 CLI：
  python query.py stats                     级别总览
  python query.py years                     有哪些级别
  python query.py major 2023 农业资源与环境  某年级某专业
  python query.py compare 农业资源与环境      同专业各年级对照
  python query.py course 901040101          课程跨文档信息
  python query.py search 土壤               按名检索
  python query.py syllabus 土壤学            大纲详情
  python query.py diff 901040101            同一课程的年级差异
"""
import json, os, sys

BASE = os.path.dirname(os.path.abspath(__file__))
OUTD = os.path.join(BASE, "json")

def load(n):
    return json.load(open(os.path.join(OUTD, n), encoding="utf-8"))

def manifest():
    return load("文档清单.json")

def years():
    return [k.replace("培养方案专业数_", "") for k in manifest()["统计"]
            if k.startswith("培养方案专业数_")]

def cmd_years():
    m = manifest()
    print("级别划分：", m["级别划分"]["说明"])
    for d in m["文档清单"]:
        print("  %-16s %-22s %4d页  %4d条  %s" % (
            d["级别"], d["文档类型"], d["pdf页数"], d["条目数"], d["文件名"][:30]))

def cmd_stats():
    for k, v in manifest()["统计"].items():
        print(" ", k, v)

def cmd_major(year, name):
    plans = load("培养方案_%s.json" % year)
    hits = [p for p in plans if name in p["专业名称"]]
    if not hits:
        print("未找到。可选专业：", sorted(set(p["专业名称"] for p in plans)))
        return
    for p in hits:
        print("=" * 62)
        print("%s | %s | %s | 页码 %s" % (p["级别"], p["专业名称"], p["培养类型"], p["章节页范围"]))
        print("主干学科:", (p.get("主干学科") or "暂缺").replace("\n", " "))
        print("核心课程:", (p.get("专业核心课程") or "暂缺").replace("\n", " "))
        print("修业年限:", (p.get("修业年限") or "暂缺").replace("\n", ""))
        print("授予学位:", p.get("授予学位") or "暂缺")
        print("培养目标:", len(p.get("培养目标列表", [])), "条")
        print("毕业要求:", len(p.get("毕业要求列表", [])), "条 /",
              sum(len(r["指标点"]) for r in p.get("毕业要求列表", [])), "指标点")
        print("课程:", len(p.get("课程设置与教学进程", [])), "条")

def cmd_compare(name):
    m = manifest()
    rows = [r for r in m["年级对照"] if name in r["专业"]]
    if not rows:
        print("未找到，可选：", [r["专业"] for r in m["年级对照"]][:40])
        return
    ys = years()
    for r in rows:
        print("=" * 62)
        print(r["专业"])
        for y in ys:
            v = r.get("%s级" % y)
            if v:
                print("   %s级: 课程 %d 条, 方案页 %s" % (y, v[0], v[1]))
            else:
                print("   %s级: —（该年级无此专业）" % y)

def cmd_course(code):
    idx = load("课程总索引.json")
    hits = [r for r in idx if r["课程代码"] == code] or \
           [r for r in idx if r["归一名称"] == code]
    if not hits:
        print("未找到课程", code)
        return
    for r in hits:
        print("=" * 62)
        print(r["课程代码"], r["主名称"], "| 英文:", " / ".join(r["英文名称"][:2]))
        print("学分:", r["学分"], "总学时:", r["总学时"], "开课学期:", r["开课学期"])
        print("出现于:", r["出现于"])
        print("大纲类型:", r["大纲课程类型"] or "暂缺", "| 教研室:", r["开课教研室"] or "暂缺",
              "| 大纲页:", r["大纲页范围"])
        print("课程类别:", r["课程类别"])
        for y in years():
            prof = r.get("专业_%s" % y) or []
            print("   %s级: %d 个专业开设%s" % (y, len(prof),
                  ("（" + "、".join(prof[:3]) + "…）") if len(prof) > 3 else
                  ("（" + "、".join(prof) + "）") if prof else ""))

def cmd_diff(code):
    idx = load("课程总索引.json")
    hits = [r for r in idx if r["课程代码"] == code]
    if not hits:
        print("未找到", code)
        return
    r = hits[0]
    print(r["课程代码"], r["主名称"])
    print("  课程名称各版本:", r["课程名称"])
    print("  学分各版本  :", r["学分"])
    print("  学时各版本  :", r["总学时"])
    print("  开课学期    :", r["开课学期"])
    print("  出现于      :", r["出现于"])
    print("  页码线索    :")
    for y in years():
        print("    %s级 方案页 %s" % (y, r.get("方案页_%s" % y)))
    print("    2021版大纲页", r["大纲页范围"])

def cmd_search(kw):
    idx = load("课程总索引.json")
    hits = [r for r in idx if kw in r["检索键"] or kw in (r["主名称"] or "")]
    print("命中", len(hits), "门课程")
    for r in hits[:80]:
        print(" ", r["课程代码"], r["主名称"], "|", "/".join(r["学分"]), "学分 |",
              "大纲" if r["有课程大纲"] else "无大纲", "|", ",".join(r["出现在年级"]))

def cmd_syllabus(kw):
    syl = load("课程大纲_2021_土地与环境学院.json")
    hits = [c for c in syl if kw in c["课程名称"] or kw in (c["英文名称"] or "")]
    if not hits:
        print("未找到，样例：", [c["课程名称"] for c in syl][:30])
        return
    for c in hits:
        print("=" * 62)
        print(c["课程名称"], "|", c["英文名称"], "|", c["课程类型"], "| 页", c["pdf页码范围"])
        b = c.get("基本信息", {})
        print("学分:", c.get("课程学分") or "暂缺", "学时:", c.get("理论学时") or "暂缺",
              "类别:", c.get("课程类别") or "暂缺")
        print("教材:", (b.get("选用教材") or "暂缺")[:80])
        print("目标:", [g["内容"][:30] for g in c.get("课程目标列表", [])][:4])
        print("章节:", list(c.get("章节", {}).keys())[:6], "...")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(0)
    op, args = sys.argv[1], sys.argv[2:]
    table = {"stats": cmd_stats, "years": cmd_years}
    if op in table:
        table[op]()
    elif op == "major":
        cmd_major(args[0], args[1])
    elif op == "compare":
        cmd_compare(args[0])
    elif op == "course":
        cmd_course(args[0])
    elif op == "diff":
        cmd_diff(args[0])
    elif op == "search":
        cmd_search(args[0])
    elif op == "syllabus":
        cmd_syllabus(args[0])
    else:
        print(__doc__)