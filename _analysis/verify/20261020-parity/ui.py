# -*- coding: utf-8 -*-
"""在 P4 设备上转储 UI 层级并列出带文本节点的精确边界。

用法：python ui.py dump <本地输出路径>
     python ui.py list <本地 xml 路径>
    python ui.py tap  <本地 xml 路径> <文本片段>
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = r"D:\DSHStation\.toolchain\android-sdk\platform-tools\adb.exe"
SERIAL = "P4L202610130006"


def run(args):
    """执行 adb 命令并返回标准输出文本。"""
    return subprocess.run([ADB, "-s", SERIAL] + args, capture_output=True, text=True, encoding="utf-8", errors="replace").stdout


def dump(path):
    """在设备上转储 UI 层级并拉取到本地路径。"""
    run(["shell", "uiautomator", "dump", "/sdcard/ui.xml"])
    run(["pull", "/sdcard/ui.xml", path])


def nodes(path):
    """读取转储文件并返回文本节点列表（文本、边界、类名）。"""
    root = ET.parse(path).getroot()
    out = []
    for n in root.iter("node"):
        txt = (n.get("text") or "").strip()
        desc = (n.get("content-desc") or "").strip()
        label = txt or desc
        if not label:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        out.append((label, (x1 + x2) // 2, (y1 + y2) // 2, n.get("class"), n.get("bounds")))
    return out


def main():
    """分派 dump、list、tap 三个子命令。"""
    cmd = sys.argv[1] if len(sys.argv) > 1 else "list"
    if cmd == "dump":
        dump(sys.argv[2])
        return
    path = sys.argv[2]
    if cmd == "list":
        for label, cx, cy, cls, bounds in nodes(path):
            print(f"{label}\t({cx},{cy})\t{bounds}\t{cls}")
        return
    if cmd == "tap":
        key = sys.argv[3]
        for label, cx, cy, cls, bounds in nodes(path):
            if key in label:
                run(["shell", "input", "tap", str(cx), str(cy)])
                print(f"tap {label} at ({cx},{cy})")
                return
        print(f"未找到文本包含 {key} 的节点")


if __name__ == "__main__":
    main()
