"""把 curriculum/json 下的提取结果压成 Android assets（按需加载，不要全量塞进内存）。

为什么要打包而不是直接塞原始 JSON：
- 原始四个文件 14MB+，全量解析会吃掉几十 MB 堆，冷启动明显卡；
- App 只需要「某年级某专业」和「某一门课的大纲」，按 id 切成小文件后
  一次只读几 KB，且可被 Gson/kotlinx 直接反序列化。

输出（默认写进 app/src/main/assets/curriculum/）：
  plan_index.json           {"2024":[{id,major,type,pages}], "2025":[...]}
  plan_2024_<id>.json       单专业培养方案（只留展示要用的字段）
  syllabus_index.json       [{id,code,name,credit,hours,type}]
  syllabus_<id>.json        单门课程大纲
  META.json                 生成时间、来源文件、校验和、诚实规则版本

可复现：源数据变化后重跑本脚本即可（README 里有完整提取链路）。
用法：
  python tools/curriculum/pack.py
  python tools/curriculum/pack.py --src "O:/dev/enroute-android/在途/curriculum/json" --out "app/src/main/assets/curriculum"
"""
import argparse
import hashlib
import json
import os
import re
import sys

# 培养方案里要保留的展示字段（其余是原表矩阵/说明，体积大且手机上看不清）
PLAN_KEEP = [
    "年级", "专业名称", "培养类型", "章节页范围",
    "专业介绍", "培养目标", "毕业要求", "主干学科", "专业核心课程",
    "主要实践性教学环节", "专业方向", "修业年限", "授予学位",
    "培养目标列表", "毕业要求列表", "课程设置与教学进程",
    "学分结构表", "各学期学分分配表",
    "开课单位说明", "课程替代情况说明",
]

# 课程大纲要保留的字段
SYL_KEEP = [
    "课程名称", "英文名称", "课程类型", "课程代码", "课程学分",
    "理论学时", "实验学时", "课程类别", "开课教研室", "执笔人", "制定时间",
    "基本信息", "章节", "教学章节", "教学内容学时一览表", "课程目标列表", "实践内容",
]


def slug(s: str) -> str:
    """稳定的文件名片段：中文直接留（asset 支持），去掉路径分隔与空白。"""
    s = re.sub(r"[\s/\\:*?\"<>|]+", "", s or "")
    return s or "未知"


def short_hash(s: str) -> str:
    return hashlib.md5(s.encode("utf-8")).hexdigest()[:6]


def dump(path, obj):
    raw = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    with open(path, "wb") as f:
        f.write(raw)
    return len(raw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=r"O:/dev/enroute-android/在途/curriculum/json")
    ap.add_argument("--out", default=r"O:/dev/enroute-android/app/src/main/assets/curriculum")
    args = ap.parse_args()

    src, out = args.src, args.out
    os.makedirs(out, exist_ok=True)

    plan_index = {"2024": [], "2025": []}
    total_bytes = 0
    report = []

    # ── 培养方案 ────────────────────────────────────────────
    for year in ("2024", "2025"):
        fp = os.path.join(src, f"培养方案_{year}.json")
        if not os.path.exists(fp):
            print(f"!! 缺 {fp}", file=sys.stderr)
            continue
        with open(fp, encoding="utf-8") as f:
            majors = json.load(f)
        for m in majors:
            name = (m.get("专业名称") or "").strip()
            mtype = (m.get("培养类型") or "").strip()
            if not name:
                continue
            mid = slug(name) + ("_" + short_hash(mtype) if mtype and mtype != "普通班" else "")
            keep = {k: m.get(k) for k in PLAN_KEEP if m.get(k) not in (None, "", [], {})}
            n = dump(os.path.join(out, f"plan_{year}_{mid}.json"), keep)
            total_bytes += n
            plan_index[year].append({
                "id": mid,
                "major": name,
                "type": mtype or "普通班",
                "bytes": n,
                "courses": len(m.get("课程设置与教学进程") or []),
            })
        report.append(f"{year}: {len(plan_index[year])} 个专业")

    total_bytes += dump(os.path.join(out, "plan_index.json"), plan_index)

    # ── 课程大纲 ────────────────────────────────────────────
    syl_fp = os.path.join(src, "课程大纲_2021_土地与环境学院.json")
    syl_index = []
    if os.path.exists(syl_fp):
        with open(syl_fp, encoding="utf-8") as f:
            syllabi = json.load(f)
        seen = {}
        for s in syllabi:
            name = (s.get("课程名称") or "").strip()
            if not name:
                continue
            code = (s.get("课程代码") or "").strip()
            sid = code if code else ("n" + short_hash(name))
            # 同名同码重复出现时保留信息更全的那条
            prev = seen.get(sid)
            if prev is not None:
                if len(json.dumps(s, ensure_ascii=False)) <= len(json.dumps(prev, ensure_ascii=False)):
                    continue
            seen[sid] = s
            keep = {k: s.get(k) for k in SYL_KEEP if s.get(k) not in (None, "", [], {})}
            n = dump(os.path.join(out, f"syllabus_{sid}.json"), keep)
            total_bytes += n
            syl_index.append({
                "id": sid,
                "code": code,
                "name": name,
                "type": s.get("课程类型") or "",
                "credit": s.get("课程学分") or "",
                "hours": [s.get("理论学时") or "", s.get("实验学时") or ""],
                "bytes": n,
            })
        # 名字排序便于检索展示
        syl_index.sort(key=lambda x: x["name"])
        report.append(f"大纲: {len(syl_index)} 门")
    total_bytes += dump(os.path.join(out, "syllabus_index.json"), syl_index)

    # ── META（诚实规则与来源）────────────────────────────────
    meta = {
        "generated": "2026-11-14",
        "source": {
            "2024级培养方案": "2024级沈阳农业大学本科人才培养方案.pdf（695 页）",
            "2025级培养方案": "2025级沈阳农业大学本科人才培养方案.pdf（693 页）",
            "课程大纲": "沈阳农业大学土地与环境学院本科课程大纲（2021版）.pdf（892 页）",
        },
        "honesty": [
            "学分合计不可用课程行求和：培养方案把各选修模块的全部候选课都列出，实际只修其中一部分。",
            "「最低毕业学分」只认学分结构表（原文表格），见 creditsStructure。",
            "课程性质列原文纵向合并，空串表示原文为空，不要补猜。",
            "同一课程代码在不同专业/版本的学分学时可能不同，按专业取用，不要跨专业套用。",
            "课程大纲仅覆盖土地与环境学院 167 门；通识课（形式与政策、社会主义发展史等）不在此册内，缺失属正常。",
        ],
        "asset_bytes": total_bytes,
    }
    dump(os.path.join(out, "META.json"), meta)

    print("\n".join(report))
    print(f"合计 asset 体积: {total_bytes/1_048_576:.2f} MB")
    print(f"输出目录: {out}")


if __name__ == "__main__":
    main()
