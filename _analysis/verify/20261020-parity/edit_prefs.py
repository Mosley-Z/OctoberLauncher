import json, io
src = r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\prefs-p4-before.json"
dst = r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\prefs-p4-lab.json"
with io.open(src, encoding="utf-8") as f:
    data = json.load(f)
print(list(data.keys()))
bools = data.setdefault("bool", {})
bools["app_lab_enabled"] = True
with io.open(dst, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
print("written", len(json.dumps(data)))