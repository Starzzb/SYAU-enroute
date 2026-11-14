#!/usr/bin/env python3
"""`merge_edits.py` 的单测（stdlib unittest，无需 geopandas/pytest）。

为什么不放 JVM 单测：这是**打包侧**的 Python 工具，不进 APK；
放进 Gradle 测试只会让「改脚本」变成「改构建」。这里用 `python -m unittest` 直接跑：

    python tools/map/test_merge_edits.py
    python -m unittest tools.map.test_merge_edits -v

覆盖的 8 条（对应票 13 阶段 A 的验收）：
 1. 合 2 份 edits.json，同格取**最新 ts**
 2. 冲突被写进 conflicts.txt，且说明谁胜谁负
 3. **不丢字段**：两份各自的字段都进 fieldOrder；只在要素里出现的字段也补进表
 4. **幂等**：同一批输入跑两次 → 三个输出逐字节相同
 5. **不动点**：输出再当输入合一次 → 结果不变
 6. CSV 带 UTF-8 BOM；公式注入被 `'` 前缀挡住
 7. 服务端补丁：`set` / `delete` / `addField` 三种 op 都正确落地
 8. 换图孤儿：base 里没有的 id **保留**并列入 conflicts.txt
"""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent))

import merge_edits as me  # noqa: E402


def w(path: Path, obj) -> Path:
    path.write_text(json.dumps(obj, ensure_ascii=False), encoding="utf-8")
    return path


def read(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


class MergeTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="merge-test-"))
        self.out1 = self.tmp / "out1"
        self.out2 = self.tmp / "out2"

    # ── 工具 ──
    def run_merge(self, argv, out):
        code = me.main(list(argv) + ["--out", str(out)])
        self.assertEqual(code, 0)

    def base_edits(self, ver="2026.11.14-1", fields=None, feats=None):
        return {"schema": 1, "mapVersion": ver,
                "fieldOrder": fields or [], "features": feats or {}}

    # 1 + 2 + 3
    def test_latest_ts_wins_and_conflict_listed(self):
        a = w(self.tmp / "campus_annotated_v_20261114-1000.geojson", {
            "type": "FeatureCollection",
            "features": [
                {"type": "Feature", "properties": {"id": "f19", "name": "老名字", "kind": "sport", "层数": "2"}},
                {"type": "Feature", "properties": {"id": "f20", "name": "食堂", "所属单位": "后勤"}},
            ],
        })
        b = w(self.tmp / "campus_annotated_v_20261114-1500.geojson", {
            "type": "FeatureCollection",
            "features": [
                {"type": "Feature", "properties": {"id": "f19", "name": "九州体育场", "层数": "3"}},
            ],
        })
        self.run_merge(["--export", str(a), "--export", str(b)], self.out1)
        edits = read(self.out1 / "merged_edits.json")

        # 最新（15:00）胜
        self.assertEqual(edits["features"]["f19"]["name"], "九州体育场")
        self.assertEqual(edits["features"]["f19"]["层数"], "3")
        # 只有旧的才有的值不丢（f20 来自 10:00 那份）
        self.assertEqual(edits["features"]["f20"]["所属单位"], "后勤")
        # 08:00 那份的 kind 没有任何人反驳 → 保留
        self.assertEqual(edits["features"]["f19"]["kind"], "sport")

        # 字段表并集，且「层数」「所属单位」都在
        self.assertIn("层数", edits["fieldOrder"])
        self.assertIn("所属单位", edits["fieldOrder"])

        txt = (self.out1 / "conflicts.txt").read_text(encoding="utf-8")
        self.assertIn("f19 / name", txt)
        self.assertIn("LATEST-WINS", txt)
        self.assertIn("九州体育场", txt)
        self.assertIn("老名字", txt)

    # 4
    def test_idempotent_bytes(self):
        a = w(self.tmp / "a_20261114-1000.geojson", {"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"id": "f1", "name": "A", "层数": "1"}}]})
        b = w(self.tmp / "b_20261114-1200.geojson", {"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"id": "f1", "层数": "2"}}]})
        argv = ["--export", str(a), "--export", str(b)]
        self.run_merge(argv, self.out1)
        self.run_merge(argv, self.out2)
        for name in ("merged_edits.json", "merged_attributes.csv"):
            self.assertEqual(
                (self.out1 / name).read_bytes(), (self.out2 / name).read_bytes(),
                f"{name} 两次结果不一致（幂等性被破坏）",
            )
        # conflicts.txt 也不许带运行时间
        self.assertEqual(
            (self.out1 / "conflicts.txt").read_bytes(), (self.out2 / "conflicts.txt").read_bytes())

    # 5
    def test_output_is_fixed_point(self):
        a = w(self.tmp / "a_20261114-1000.geojson", {"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"id": "f1", "name": "A", "层数": "1"}}]})
        b = w(self.tmp / "b_20261114-1200.geojson", {"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"id": "f1", "层数": "2", "所属单位": "x"}}]})
        self.run_merge(["--export", str(a), "--export", str(b)], self.out1)
        once = (self.out1 / "merged_edits.json").read_bytes()
        # 把输出当输入再合一次（时间戳取最新，值相同 → 结果必须一样）
        self.run_merge(["--export", str(a), "--export", str(b),
                        "--edits", str(self.out1 / "merged_edits.json")], self.out2)
        self.assertEqual(once, (self.out2 / "merged_edits.json").read_bytes())

    # 6
    def test_csv_has_bom_and_guards_formulas(self):
        p = w(self.tmp / "e_20261114-1000.geojson", {"type": "FeatureCollection", "features": [
            {"type": "Feature", "properties": {"id": "f1", "name": "=SUM(A1:A9)", "备注": "正常,含逗号"}}]})
        self.run_merge(["--export", str(p)], self.out1)
        raw = (self.out1 / "merged_attributes.csv").read_bytes()
        self.assertTrue(raw.startswith(b"\xef\xbb\xbf"), "CSV 必须带 UTF-8 BOM（Excel 中文才不乱码）")
        text = raw.decode("utf-8-sig")
        self.assertIn("'=SUM(A1:A9)", text)      # 公式被 ' 挡住
        self.assertIn('"正常,含逗号"', text)       # 逗号值被引号包住
        # edits.json 里的值不许被改写
        self.assertEqual(read(self.out1 / "merged_edits.json")["features"]["f1"]["name"], "=SUM(A1:A9)")

    # 7
    def test_server_patch_ops(self):
        patches = w(self.tmp / "patches.json", {"mapVersion": "2026.11.14-1", "patches": [
            {"featureId": "f19", "field": "层数", "value": "3", "op": "set", "ts": "2026-11-14T10:00:00+08:00"},
            {"featureId": "f19", "field": "所属单位", "value": "体育部", "op": "set", "ts": "2026-11-14T10:01:00+08:00"},
            {"featureId": "f19", "field": "所属单位", "value": "", "op": "delete", "ts": "2026-11-14T11:00:00+08:00"},
            {"featureId": "f20", "field": "开放时间", "op": "addField", "ts": "2026-11-14T10:02:00+08:00"},
        ]})
        self.run_merge(["--patches", str(patches)], self.out1)
        edits = read(self.out1 / "merged_edits.json")
        self.assertEqual(edits["features"]["f19"]["层数"], "3")
        self.assertNotIn("所属单位", edits["features"]["f19"], "delete 应清掉该格")
        self.assertIn("开放时间", edits["fieldOrder"], "addField 应登记字段名")
        self.assertIn("f19 / 所属单位", (self.out1 / "conflicts.txt").read_text(encoding="utf-8"))

    # 8
    def test_orphans_kept_and_reported(self):
        base = w(self.tmp / "campus.geojson", {"type": "FeatureCollection", "features": [
            {"type": "Feature", "id": "f1", "properties": {"id": "f1", "name": "", "kind": ""},
             "geometry": {"type": "Polygon", "coordinates": [[[0, 0], [1, 0], [1, 1], [0, 0]]]}},
        ]})
        e = w(self.tmp / "e_20261114-1000.json", self.base_edits(feats={
            "f1": {"name": "教学楼"},
            "f99": {"name": "老图才有的块"},   # 换图后不存在了
        }))
        self.run_merge(["--base", str(base), "--edits", str(e)], self.out1)
        edits = read(self.out1 / "merged_edits.json")
        self.assertIn("f99", edits["features"], "孤儿标注绝不能丢")
        self.assertIn("f99", (self.out1 / "conflicts.txt").read_text(encoding="utf-8"))
        campus = read(self.out1 / "merged_campus.geojson")
        self.assertEqual(campus["features"][0]["properties"]["name"], "教学楼")

    # 边界：值超长被截断且点名
    def test_long_value_truncated_and_reported(self):
        long = "字" * 260
        e = w(self.tmp / "e_20261114-1000.json", self.base_edits(fields=["备注"], feats={"f1": {"备注": long}}))
        self.run_merge(["--edits", str(e)], self.out1)
        edits = read(self.out1 / "merged_edits.json")
        self.assertEqual(len(edits["features"]["f1"]["备注"]), me.MAX_VALUE)
        self.assertIn("超长", (self.out1 / "conflicts.txt").read_text(encoding="utf-8"))

    # 边界：不带任何来源 → argparse 报错（不该静默产出空文件）
    def test_no_source_is_an_error(self):
        with self.assertRaises(SystemExit):
            me.main(["--out", str(self.tmp / "x")])


if __name__ == "__main__":
    unittest.main(verbosity=2)
