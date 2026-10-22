"""把 Markdown 合成样本写入探针私有 files 目录，供文档加目录授权用例复测。"""
from pathlib import Path
import subprocess

BASE = ['D:/DSHStation/.toolchain/android-sdk/platform-tools/adb.exe', '-s', 'SA4B03CN2608081096X']
PROBE = 'com.shuiyu.octoberrain.readerprobe'
ROOT = Path(__file__).resolve().parent / 'fixtures'
NAMES = ['sample.md', 'image.png', 'hostile.md', 'wide.md']

for name in NAMES:
    data = (ROOT / name).read_bytes()
    # 通过标准输入写入应用私有目录，避免依赖设备外部存储权限。
    result = subprocess.run(
        BASE + ['exec-out', 'run-as', PROBE, 'sh', '-c', f'cat > files/{name}'],
        input=data, capture_output=True, timeout=60)
    print(name, result.returncode, len(data))

listing = subprocess.run(BASE + ['shell', 'run-as', PROBE, 'ls', '-l', 'files'],
                         capture_output=True, timeout=60)
print(listing.stdout.decode('utf-8', 'replace'))
