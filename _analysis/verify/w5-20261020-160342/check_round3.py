# 第二轮深查：统计资产时长分布、kernel_prefs 各键值结构、bills 各键内容字段。
import json
import os

BASE = r'D:\DSHStation\OctoberLauncher\_analysis\verify\w5-20261020-160342'

kp = json.load(open(os.path.join(BASE, 'r3-kernel-prefs.json'), encoding='utf-8'))
for k, v in kp.get('string', {}).items():
    s = v if isinstance(v, str) else json.dumps(v)
    print('KEY', k, 'len', len(s))
    try:
        obj = json.loads(s)
        if isinstance(obj, dict):
            print('   top keys:', list(obj)[:12])
            for probe in ('readTimes', 'totalReadTime', 'readLongest', 'items', 'books', 'baseTime', 'totalSec'):
                if probe in obj:
                    val = obj[probe]
                    size = len(val) if hasattr(val, '__len__') else val
                    print('   ', probe, '->', size)
    except Exception as exc:
        print('   not json:', exc)

bills = json.load(open(os.path.join(BASE, 'r3-bills.json'), encoding='utf-8'))
for k, v in bills.items():
    obj = json.loads(v) if isinstance(v, str) else v
    print('BILL', k, 'keys:', list(obj)[:12])

assets = json.load(open(os.path.join(BASE, 'r3-assets.json'), encoding='utf-8'))
books = assets.get('books', []) if isinstance(assets, dict) else assets
sec_pos = sum(1 for b in books if (b.get('sec') or 0) > 0)
pct_pos = sum(1 for b in books if (b.get('pct') or 0) > 0)
fin_pos = sum(1 for b in books if (b.get('fin') or 0) > 0)
print('assets total', len(books), 'sec>0', sec_pos, 'pct>0', pct_pos, 'fin>0', fin_pos)
top = sorted(books, key=lambda b: -(b.get('sec') or 0))[:5]
for b in top:
    print('  top:', b.get('title', '')[:20], 'sec', b.get('sec'), 'pct', b.get('pct'), 'fin', b.get('fin'))
