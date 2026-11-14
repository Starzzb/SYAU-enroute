"""Print the per-page extraction summary in a compact table."""
import json
import pathlib

d = json.loads((pathlib.Path(__file__).parent / "_out-fields.json.txt").read_text(encoding="utf-8"))
for p in d["pages"]:
    hit = sum(1 for v in p["hits"].values() if v)
    print(f"{p['file'][:52]:<52} {str(p['container'])[:16]:<16} len={p['text_len']:<5} f={hit:<3} {p['teacher']}")
print()
print("n_pages =", d["n_pages"])
for k, v in sorted(d["field_hits"].items(), key=lambda kv: -kv[1]):
    print(f"  {k:<14} {v:>2}/{d['n_pages']}  {100*v/d['n_pages']:.0f}%")
