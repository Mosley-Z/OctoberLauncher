# 量测负一屏截图：顶栏底线、分区分隔线、墨竖线、空态高度、抬头次数
# 用法：python measure_neg2.py <image> [--top]
import sys

from PIL import Image


def load(path):
    image = Image.open(path).convert("RGB")
    return image, image.load(), image.size


def near(value, target, tolerance=12):
    return all(abs(a - b) <= tolerance for a, b in zip(value, target))


def find_hlines(pixels, width, height, color, tolerance=12, min_ratio=0.55):
    """查找指定颜色的横线，返回 (y, x0, x1, 像素数)"""
    lines = []
    for y in range(height):
        first = None
        last = None
        total = 0
        for x in range(width):
            if near(pixels[x, y], color, tolerance):
                if first is None:
                    first = x
                last = x
                total += 1
        if total >= width * min_ratio:
            lines.append((y, first, last, total))
    return lines


def find_vlines(pixels, width, height, color, tolerance=12, min_ratio=0.02):
    """查找指定颜色的竖线，返回 (x, y0, y1, 像素数)"""
    lines = []
    for x in range(width):
        first = None
        last = None
        total = 0
        for y in range(height):
            if near(pixels[x, y], color, tolerance):
                if first is None:
                    first = y
                last = y
                total += 1
        if total >= height * min_ratio:
            lines.append((x, first, last, total))
    return lines


def main():
    path = sys.argv[1]
    image, pixels, (width, height) = load(path)
    print(f"file={path} size={width}x{height}")
    # #D8D8D8 分隔线与顶栏底线
    light = find_hlines(pixels, width, height, (216, 216, 216), min_ratio=0.5)
    print(f"light_lines(D8)={light}")
    # 纯黑竖线（自建抬头 3px 墨竖线）
    black = find_vlines(pixels, width, height, (0, 0, 0), min_ratio=0.02)
    print(f"black_vlines={black[:12]}")


if __name__ == "__main__":
    main()
