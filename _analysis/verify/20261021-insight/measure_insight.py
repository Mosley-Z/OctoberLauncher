# 测量洞察页截图的版式像素：分区标题、柱图、排行行、刷新按钮、滚动视口
# 参数：截图路径；输出：关键水平线与深色像素分布
import sys

from PIL import Image


def load(path):
    image = Image.open(path).convert("L")
    return image, image.load(), image.size


def dark_runs(pixels, width, y, threshold=120):
    """返回某一行的深色连续区间 [(start, end)]，end 为闭区间"""
    runs = []
    start = None
    for x in range(width):
        if pixels[x, y] < threshold:
            if start is None:
                start = x
        elif start is not None:
            runs.append((start, x - 1))
            start = None
    if start is not None:
        runs.append((start, width - 1))
    return runs


def horizontal_lines(pixels, width, height, threshold=120, min_ratio=0.5):
    """返回整行深色占比超过阈值的横线 y 列表与左右端点"""
    result = []
    for y in range(height):
        first = None
        last = None
        count = 0
        for x in range(width):
            if pixels[x, y] < threshold:
                if first is None:
                    first = x
                last = x
                count += 1
        if count >= width * min_ratio:
            result.append((y, first, last, count))
    return result


def columns_black_profile(pixels, width, y0, y1, threshold=120):
    """统计 [y0, y1) 区间内每列深色像素数量，用于定位柱状图与文本块"""
    counts = [0] * width
    for x in range(width):
        for y in range(y0, y1):
            if pixels[x, y] < threshold:
                counts[x] += 1
    return counts


def block_columns(counts, min_count=4):
    """把列深色统计合并为连续块 [(start, end)]"""
    blocks = []
    start = None
    for x, count in enumerate(counts):
        if count >= min_count:
            if start is None:
                start = x
        elif start is not None:
            blocks.append((start, x - 1))
            start = None
    if start is not None:
        blocks.append((start, len(counts) - 1))
    return blocks


def main():
    path = sys.argv[1]
    image, pixels, (width, height) = load(path)
    print(f"file={path} size={width}x{height}")
    lines = horizontal_lines(pixels, width, height)
    print("horizontal_lines=" + repr(lines[:12]))
    # 柱图纵向窗口按截图类型由调用者指定
    if len(sys.argv) >= 4:
        y0 = int(sys.argv[2])
        y1 = int(sys.argv[3])
        counts = columns_black_profile(pixels, width, y0, y1)
        blocks = block_columns(counts)
        print(f"bars_y={y0}..{y1} blocks={blocks}")
        if blocks:
            widths = [b - a + 1 for a, b in blocks]
            gaps = [blocks[i + 1][0] - blocks[i][1] - 1 for i in range(len(blocks) - 1)]
            print(f"bar_widths={widths} gaps={gaps} left={blocks[0][0]} right={blocks[-1][1]}")


if __name__ == "__main__":
    main()
