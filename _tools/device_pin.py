#!/usr/bin/env python3
"""设备锁屏 PIN 临时替换与配置恢复工具。

用途：验收时设备已设置未知 PIN，需要进入桌面查看书架与桌面阅读组件。
本脚本先备份设备配置，再用已知 PIN 生成摘要覆盖配置并重启应用；
验收结束后恢复备份，保证失败计数与摘要回到原始状态。

用法：
    python device_pin.py backup  <serial> <备份文件>
    python device_pin.py set-pin <serial> <备份文件> <四位数字>
    python device_pin.py restore <serial> <备份文件>

备份文件为设备 shared_prefs/octoberrain_config.xml 的原始内容。
set-pin 复用备份中的 salt 与迭代次数，只替换 pinHash、清零失败计数与锁定截止时间。
"""

import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET

PACKAGE = "com.shuiyu.octoberrain"
CONFIG_NAME = "shared_prefs/octoberrain_config.xml"
ACTIVITY = f"{PACKAGE}/.MainActivity"


def run(args, serial=None, stdin=None):
    """执行命令并返回完成对象，失败时打印标准错误。"""
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    result = subprocess.run(cmd, input=stdin, capture_output=True)
    if result.returncode != 0:
        sys.stderr.write(result.stderr.decode("utf-8", "replace"))
    return result


def read_config(serial):
    """读取设备配置文件原始字节。"""
    out = run(["exec-out", "run-as", PACKAGE, "cat", CONFIG_NAME], serial)
    if out.returncode != 0 or not out.stdout:
        raise SystemExit("读取设备配置失败")
    return out.stdout


def write_config(serial, data):
    """覆盖设备配置文件并重启应用使配置生效。"""
    run(["shell", "am", "force-stop", PACKAGE], serial)
    run(
        ["shell", f"run-as {PACKAGE} sh -c 'cat > {CONFIG_NAME}'"],
        serial,
        stdin=data,
    )
    run(["shell", "am", "start", "-n", ACTIVITY], serial)


def load_json(data):
    """从 SharedPreferences XML 中取出 launcher_config 的 JSON 对象。"""
    root = ET.fromstring(data)
    node = root.find("string")
    if node is None or not node.text:
        raise SystemExit("配置结构不符合预期")
    return json.loads(node.text)


def dump_xml(config):
    """把配置对象序列化回 SharedPreferences XML。"""
    text = json.dumps(config, ensure_ascii=False, separators=(",", ":"))
    escaped = (
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace('"', "&quot;").replace("'", "&apos;")
    )
    return (
        "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
        "<map>\n"
        f'    <string name="launcher_config">{escaped}</string>\n'
        "</map>\n"
    ).encode("utf-8")


def cmd_backup(serial, path):
    """备份设备配置到本地文件。"""
    data = read_config(serial)
    with open(path, "wb") as handle:
        handle.write(data)
    print(f"已备份 {len(data)} 字节到 {path}")


def cmd_set_pin(serial, path, pin):
    """用备份文件生成已知 PIN 的配置并写入设备。"""
    if len(pin) != 4 or not pin.isdigit():
        raise SystemExit("PIN 必须为四位数字")
    with open(path, "rb") as handle:
        config = load_json(handle.read())
    lock = config["lock"]
    digest = hashlib.pbkdf2_hmac(
        "sha256", pin.encode(), bytes.fromhex(lock["salt"]), int(lock["iterations"])
    ).hex()
    lock["pinHash"] = f"pbkdf2${lock['iterations']}${digest}"
    lock["failedAttempts"] = 0
    lock["lockoutUntilMillis"] = 0
    write_config(serial, dump_xml(config))
    print(f"已写入临时 PIN {pin}，摘要长度 {len(lock['pinHash'])}")


def cmd_restore(serial, path):
    """恢复备份配置并核对摘要与失败计数。"""
    with open(path, "rb") as handle:
        raw = handle.read()
    original = load_json(raw)
    write_config(serial, raw)
    current = load_json(read_config(serial))
    same_hash = original["lock"]["pinHash"] == current["lock"]["pinHash"]
    same_salt = original["lock"]["salt"] == current["lock"]["salt"]
    failed = current["lock"]["failedAttempts"]
    print(f"摘要一致={same_hash} 盐一致={same_salt} 失败计数={failed}")


def main(argv):
    """命令行入口，按子命令分发。"""
    if len(argv) < 3:
        print(__doc__)
        return 1
    action, serial = argv[1], argv[2]
    if action == "backup" and len(argv) == 4:
        cmd_backup(serial, argv[3])
    elif action == "set-pin" and len(argv) == 5:
        cmd_set_pin(serial, argv[3], argv[4])
    elif action == "restore" and len(argv) == 4:
        cmd_restore(serial, argv[3])
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
