"""测量 S4 锁屏 PIN 校验耗时。

脚本依次点击四位密码，记录点击完成时刻，并持续截屏检测锁屏标题是否消失，
据此得到从输入完成到界面回到桌面的实际等待时间。

用法：python _tools/measure_unlock.py <序列号> <pin>
"""

import io
import subprocess
import sys
import time

from PIL import Image


def screencap(serial: str) -> Image.Image:
    """抓取一帧屏幕并返回图像对象。"""
    raw = subprocess.run(
        ["adb", "-s", serial, "exec-out", "screencap", "-p"],
        capture_output=True,
        check=True,
    ).stdout
    return Image.open(io.BytesIO(raw)).convert("L")


def locked(image: Image.Image) -> bool:
    """判断画面是否仍在锁屏：检测标题文字区域的暗像素占比。"""
    w, h = image.size
    box = image.crop((int(w * 0.28), int(h * 0.17), int(w * 0.72), int(h * 0.23)))
    dark = sum(1 for value in box.getdata() if value < 100)
    return dark > box.size[0] * box.size[1] * 0.01


def tap(serial: str, x: int, y: int) -> None:
    """点击一次屏幕坐标。"""
    subprocess.run(
        ["adb", "-s", serial, "shell", "input", "tap", str(x), str(y)],
        check=True,
    )


def main() -> int:
    """执行测量并打印时间线。"""
    if len(sys.argv) < 3:
        print("usage: measure_unlock.py <serial> <pin>")
        return 2
    serial, pin = sys.argv[1], sys.argv[2]
    keys = {
        "1": (112, 440),
        "2": (240, 440),
        "3": (368, 440),
        "4": (112, 520),
        "5": (240, 520),
        "6": (368, 520),
        "7": (112, 600),
        "8": (240, 600),
        "9": (368, 600),
        "0": (240, 680),
    }
    for digit in pin:
        x, y = keys[digit]
        tap(serial, x, y)
        time.sleep(0.25)
    start = time.monotonic()
    print(f"input done at t=0")
    deadline = start + 20.0
    while time.monotonic() < deadline:
        image = screencap(serial)
        elapsed = time.monotonic() - start
        if not locked(image):
            print(f"unlocked after {elapsed:.2f}s")
            return 0
        print(f"t={elapsed:.2f}s still locked")
        time.sleep(0.2)
    print("timeout: still locked after 20s")
    return 1


if __name__ == "__main__":
    sys.exit(main())
