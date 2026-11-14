#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""**分站跑**全量抓取：每个学院一个独立子进程，跑完一站落一站。

为什么不是一个大循环：
- 单站解析器炸了/超时了，不能拖垮其余 15 站（子进程隔离）
- 每站独立写 `assets/faculty/<code>.json`，中断了也能拿到已完成的部分
- 同站串行、间隔由 `scrape.py` 保证（≥1.2 秒）；这里**只串行、不并发**

用法：
    python tools/faculty/run_all.py                 # 全部 16 站
    python tools/faculty/run_all.py --only nxy slxy
    python tools/faculty/run_all.py --dry-run
"""
import argparse
import json
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))

# 控制台是 GBK：统一按 UTF-8 输出（否则子进程的中文行打印时会崩）
if hasattr(sys.stdout, 'buffer'):
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "run_summary.json")

# 顺序 = 源表里的顺序；信息量大的站先跑，跑挂了也先拿到大头
ORDER = ["nxy", "zbxy", "xdxy", "swxy", "jgxy", "myxy", "lin", "slxy",
         "spxy", "thxy", "wyb", "lxy", "gcxy", "tyb", "yyxy", "marx"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", default=None)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--at", default=None)
    args = ap.parse_args()

    at = args.at or time.strftime("%Y-%m-%dT%H:%M:%S%z")
    sites = [c for c in ORDER if not args.only or c in args.only]
    results = []

    for i, code in enumerate(sites, 1):
        cmd = [sys.executable, os.path.join(HERE, "scrape.py"), "--only", code, "--at", at]
        if args.dry_run:
            cmd.append("--dry-run")
        t0 = time.time()
        p = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
        dt = int(time.time() - t0)
        # scrape.py 的退出码：0=正常；2=有站点 0 条
        results.append({"code": code, "exit": p.returncode, "seconds": dt,
                        "stdout": (p.stdout or "").strip().splitlines()[-3:],
                        "stderr": (p.stderr or "").strip()[-400:]})
        tag = "OK " if p.returncode == 0 else "FAIL(%d)" % p.returncode
        print("[%2d/%2d] %-5s %-12s %3ds" % (i, len(sites), code, tag, dt))
        for ln in results[-1]["stdout"]:
            print("        " + ln)
        sys.stdout.flush()

    if not args.dry_run:
        with open(OUT, "w", encoding="utf-8", newline="\n") as f:
            json.dump({"generatedAt": at, "results": results}, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print("\n-> %s" % OUT)
    bad = [r for r in results if r["exit"] != 0]
    if bad:
        print("失败 %d 站：%s" % (len(bad), ", ".join(r["code"] for r in bad)))
        sys.exit(2)


if __name__ == "__main__":
    main()
