# -*- coding: utf-8 -*-
"""APK 内 assets 压缩实况：分组统计原始大小 / APK 内压缩后大小"""
import zipfile, collections, sys

p = sys.argv[1] if len(sys.argv) > 1 else r"O:/dev/enroute-android/app/build/outputs/apk/release/app-release.apk"
z = zipfile.ZipFile(p)
agg = collections.defaultdict(lambda: [0, 0, 0])
for i in z.infolist():
    if not i.filename.startswith("assets/"):
        continue
    parts = i.filename.split("/")
    key = "/".join(parts[:3]) if len(parts) >= 3 else i.filename
    a = agg[key]
    a[0] += i.file_size
    a[1] += i.compress_size
    a[2] += 1

tot = [0, 0, 0]
lines = []
for k, v in sorted(agg.items(), key=lambda x: -x[1][1]):
    tot[0] += v[0]; tot[1] += v[1]; tot[2] += v[2]
    lines.append("%-46s raw %7.2f MB  apk %6.2f MB  %4d files" % (k[-46:], v[0] / 1048576, v[1] / 1048576, v[2]))
lines.append("TOTAL assets raw %.2f MB -> apk %.2f MB (%d files)" % (tot[0] / 1048576, tot[1] / 1048576, tot[2]))
lines.append("APK 总大小 %.2f MB" % (sum(i.compress_size for i in z.infolist()) / 1048576))
open(r"O:/dev/enroute-android/在途/_work/apk_probe.txt", "w", encoding="utf-8").write("\n".join(lines))
print("\n".join(lines))
