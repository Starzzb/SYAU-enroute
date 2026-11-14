#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""教师照片缩略图（票 27，lead 2026-11-14 决定：**存 URL + 下载 96px 缩略图**）。

为什么不能只存 URL：学院站点的图片可能防盗链、可能改版失效，App 里就是一排破图 ——
**宁可显示首字头像，也不要假图**（和「数据诚实」同一条道理）。

规则（lead 定的，写进代码）：
- 长边 **≤96px**、单张 **≤8KB**（JPEG，质量自适应下调）
- **全站总量 ≤5MB**，超预算就停（按「信息完整度」优先：有职称+有研究方向的先下）
- 存 `assets/faculty/photos/<hash>.jpg`；原 URL 留在记录里（点开看大图用）
- **下载失败不影响记录**：只标记跳过，`photoThumb` 留空 → App 走首字头像
- 幂等：文件已存在就不再下载（可反复跑）
- 同站串行、间隔 ≥1.2 秒

用法：
    python tools/faculty/photos.py --dry-run --limit 20
    python tools/faculty/photos.py                    # 按预算跑满
    python tools/faculty/photos.py --max-total-mb 3
"""
import argparse
import hashlib
import io
import json
import os
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, "assets", "faculty")
PHOTOS = os.path.join(ASSETS, "photos")

UA = "SYAU-Enroute-Research/0.1 (personal student research build)"
DELAY = 1.2
MAX_EDGE = 96
MAX_BYTES = 8 * 1024
MAX_TOTAL = 5 * 1024 * 1024


def fetch_image(url, timeout=25):
    if "?" in url:
        raise ValueError("带查询串的 URL 一律不抓（robots Disallow: /*?*）")
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": url})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def make_thumb(raw):
    """原图字节 → (jpeg字节, 宽, 高)；失败返回 (None, 0, 0)"""
    from PIL import Image
    try:
        im = Image.open(io.BytesIO(raw))
        im.load()
    except Exception:  # noqa: BLE001
        return None, 0, 0
    if im.mode not in ("RGB", "L"):
        im = im.convert("RGB")
    # 长边先压到 96，质量从 72 往下试，直到 ≤8KB（再不行缩尺寸）
    for edge in (MAX_EDGE, 80, 64, 48):
        t = im.copy()
        t.thumbnail((edge, edge), Image.LANCZOS)
        for q in (72, 64, 56, 48, 40, 32):
            buf = io.BytesIO()
            t.save(buf, format="JPEG", quality=q, optimize=True, progressive=True)
            data = buf.getvalue()
            if len(data) <= MAX_BYTES:
                return data, t.width, t.height
    return None, 0, 0


def name_for(url):
    return hashlib.sha1(url.encode("utf-8")).hexdigest()[:16] + ".jpg"


def priority(rec):
    """信息完整的人先上图：职称+研究方向 → 有职称 → 其他"""
    has_t = bool(rec.get("title"))
    has_r = bool(rec.get("research"))
    if has_t and has_r:
        return 0
    if has_t:
        return 1
    return 2


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--limit", type=int, default=0, help="最多处理多少张（调试用）")
    ap.add_argument("--max-total-mb", type=float, default=MAX_TOTAL / 1024 / 1024)
    ap.add_argument("--only", nargs="*", default=None)
    args = ap.parse_args()

    budget = int(args.max_total_mb * 1024 * 1024)
    os.makedirs(PHOTOS, exist_ok=True)

    files = sorted(f for f in os.listdir(ASSETS) if f.endswith(".json") and not f.startswith("_"))
    used = 0
    done = skipped = failed = 0
    per_college = []

    for fn in files:
        code = fn[:-5]
        if args.only and code not in args.only:
            continue
        path = os.path.join(ASSETS, fn)
        data = json.load(open(path, encoding="utf-8"))
        rows = data.get("teachers", [])
        # 每个学院内部排序：信息完整的先来
        order = sorted(range(len(rows)), key=lambda i: (priority(rows[i]), rows[i]["name"]))
        got = 0
        for idx in order:
            rec = rows[idx]
            url = (rec.get("photo") or "").strip()
            if not url.startswith("http"):
                skipped += 1
                continue
            fname = name_for(url)
            target = os.path.join(PHOTOS, fname)
            if os.path.exists(target) and os.path.getsize(target) > 0:
                rec["photoThumb"] = "photos/" + fname          # 幂等：直接复用
                got += 1
                continue
            if used >= budget:
                skipped += 1
                continue
            if args.limit and (done + failed) >= args.limit:
                continue
            if args.dry_run:
                print("    [dry] %s -> %s" % (url[:80], fname))
                done += 1
                continue
            try:
                raw = fetch_image(url)
                time.sleep(DELAY)
                jpg, w, h = make_thumb(raw)
                if not jpg:
                    failed += 1
                    continue
                if used + len(jpg) > budget:
                    skipped += 1
                    continue
                with open(target, "wb") as f:
                    f.write(jpg)
                used += len(jpg)
                rec["photoThumb"] = "photos/" + fname
                done += 1
                got += 1
            except Exception:  # noqa: BLE001 —— 单张失败绝不影响记录
                failed += 1
                time.sleep(0.2)
        # 没缩略图的：把 photoThumb 清掉（App 走首字头像）
        for rec in rows:
            if not rec.get("photoThumb"):
                rec.pop("photoThumb", None)
        if not args.dry_run:
            data["photosAt"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
            data["withThumb"] = sum(1 for r in rows if r.get("photoThumb"))
            tmp = path + ".tmp"
            with open(tmp, "w", encoding="utf-8", newline="\n") as f:
                json.dump(data, f, ensure_ascii=False, indent=1)
                f.write("\n")
            os.replace(tmp, path)
        per_college.append((code, got, len(rows)))
        print("[%s] %-14s 缩略图 %d/%d" % ("dry" if args.dry_run else "ok ", code, got, len(rows)))
        sys.stdout.flush()

    print("\n合计：新增 %d 张，复用/跳过 %d，失败 %d，占用 %.2f MB / 预算 %.2f MB"
          % (done, skipped, failed, used / 1024 / 1024, budget / 1024 / 1024))


if __name__ == "__main__":
    main()
