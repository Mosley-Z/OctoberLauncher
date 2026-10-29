"""Prepare the WorkBuddy site after publishing an OctoberLauncher Release."""
import argparse
import os
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--workspace', type=Path, default=Path('D:/WorkBuddyStation/2026-10-03-03-42-56'))
a = p.parse_args()
root = Path(__file__).resolve().parents[1]
version = (root / 'VERSION').read_text().strip()
site = a.workspace / 'wereadmoji-site'
manifest = json.loads((root / f'dist/{version}/update.json').read_text(encoding='utf-8-sig'))
old = json.loads((site / 'update-october.json').read_text(encoding='utf-8-sig'))
assert manifest['versionCode'] >= old['versionCode'], 'Refusing version downgrade'
name = f'OctoberLauncher-v{version}.apk'
apk = root / f'dist/{version}/{name}'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == manifest['sha256']
assert apk.stat().st_size == manifest['sizeBytes']
html = site / 'index.html'
raw = html.read_bytes()
previous = old['versionName'].encode()
# Only lines concerning OctoberLauncher; other products retain their versions.
lines = raw.splitlines(keepends=True)
for i, line in enumerate(lines):
    if b'OctoberLauncher' in line or '十雨'.encode() in line:
        lines[i] = re.sub(rb'(?<![0-9.])' + re.escape(previous) + rb'(?![0-9.])', version.encode(), line)
updated = b''.join(lines)
assert updated.count(b'\r\n') == raw.count(b'\r\n'), 'CRLF changed'
shutil.copyfile(apk, site / 'apk' / name)
manifest['downloadUrls'] = [manifest['downloadUrl']]
manifest['downloadUrl'] = f'https://wereadmoji.app.workbuddy.host/apk/{name}'
(site / 'update-october.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
html.write_bytes(updated)
contracts = list((a.workspace / '_baseline_contract').glob('contract_*.txt'))
latest = max(contracts, key=lambda x: x.stat().st_mtime)
entries = latest.read_text(encoding='utf-8-sig').splitlines()
changed = {'update-october.json', f'apk/{name}'}
# Preserve every unrelated frozen hash; homepage may or may not be frozen.
if any(line.split()[-1] == 'index.html' for line in entries if line.split()):
    changed.add('index.html')
entries = [line for line in entries if not line.split() or line.split()[-1] not in changed]
for rel in sorted(changed):
    data = (site / rel).read_bytes()
    entries.append(f'{hashlib.sha256(data).hexdigest()}  {len(data):10d}  {rel}')
contract = a.workspace / f'_baseline_contract/contract_october{version.replace(".", "")}.txt'
contract.write_text('\n'.join(entries) + '\n', encoding='utf-8')
python = Path('C:/Users/MosleyZ/.workbuddy/binaries/python/versions/3.13.12/python.exe')
result = subprocess.run([str(python), str(a.workspace / '_tools/preflight.py')], env=dict(os.environ, PYTHONIOENCODING='utf-8'), capture_output=True, text=True, encoding='utf-8')
print(result.stdout)
print(result.stderr)
assert result.returncode == 0, 'Preflight failed; do not deploy'
files = '\n'.join(f'   - {rel}: {(site / rel).stat().st_size} 字节' for rel in ['index.html', 'update-october.json', f'apk/{name}'])
receipt = a.workspace / f'待发布_十雨V{version}.md'
receipt.write_text(f'# 待发布 · 十雨桌面 V{version}\n\n1. 改了什么：十雨版本、下载入口和更新清单升至{version}/{manifest["versionCode"]}。\n2. 涉及文件：\n{files}\n   - 契约：{contract.name}；其余冻结项保持。\n3. 自检结果：退出码0。\n```\n' + '\n'.join(result.stdout.strip().splitlines()[-3:]) + f'\n```\n4. 用户在WorkBuddy会话中说：“按本待发布单上线”。\n5. 部署后按上线卡运行preflight.py --online；核验首页版本、下载链接HTTP200、APK {manifest["sizeBytes"]}字节及SHA256 {manifest["sha256"]}。\n\n本次仅本地准备；按上线卡以python/http-service部署，未执行云端部署。\n', encoding='utf-8')
print(f'Ready: {receipt}')
