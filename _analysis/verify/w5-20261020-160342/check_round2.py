# 第二轮同步产物检查：统计 kernel_prefs 键、bills 条目与 assets 首条字段。
import json
import os

BASE = r'D:\DSHStation\OctoberLauncher\_analysis\verify\w5-20261020-160342'

kp = json.load(open(os.path.join(BASE, 'r2-kernel-prefs.json'), encoding='utf-8'))
print('groups =', {k: len(v) for k, v in kp.items() if isinstance(v, dict)})
s = kp.get('string', {})
print('string keys =', len(s))
for k in list(s)[:12]:
    print('   ', k, '->', (s[k][:40] if isinstance(s[k], str) else s[k]))
longs = kp.get('long', {})
print('long keys =', len(longs), list(longs)[:8])
ints = kp.get('int', {})
bools = kp.get('bool', {})
print('int keys =', len(ints), 'bool keys =', len(bools))

bills = json.load(open(os.path.join(BASE, 'r2-bills.json'), encoding='utf-8'))
print('bill keys =', len(bills))
for k, v in bills.items():
    try:
        sheet = json.loads(v) if isinstance(v, str) else v
        print('   ', k, 'items =', len(sheet.get('items', [])), 'totalSec =', sheet.get('totalSec'),
              'periodStart =', sheet.get('periodStart'))
    except Exception as exc:
        print('   ', k, 'parse-fail', exc)

assets = json.load(open(os.path.join(BASE, 'r2-assets.json'), encoding='utf-8'))
books = assets.get('books', []) if isinstance(assets, dict) else assets
print('assets count =', len(books))
if books:
    first = books[0]
    print('first =', {k: first.get(k) for k in ('id', 'title', 'sec', 'pct', 'fin', 'progress', 'finished', 'price', 'paid')})
