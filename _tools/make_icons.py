# -*- coding: utf-8 -*-
"""由 ICON.png 生成十雨桌面的图标资源。

生成内容：
1. 自适应图标前景 ic_launcher_foreground.png（108dp 画布，图案内容极径约占画布 30.6%，
   即图案外径约为画布的 61%，落在自适应图标 72dp 可见区内且不触发裁切）；
2. 传统方图标 ic_launcher.png 与圆形图标 ic_launcher_round.png（各密度位图）。

纸底使用素材边缘的中位色，图案四周羽化过渡到同色，避免出现方形接缝。
"""

import os

import numpy as np
from PIL import Image, ImageDraw

# 工程根目录与输出目录
ROOT = r"D:\DSHStation\OctoberLauncher"
SRC_PATH = os.path.join(ROOT, "ICON.png")
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res")

# 素材实测参数：纸底中位色、图案内容中心、图案内容（含阴影）的最大极径
PAPER = (248, 248, 249)
CONTENT_CX = 870.0
CONTENT_CY = 864.5
CONTENT_RADIUS = 875.0

# 羽化宽度占图案方图边长的比例，用于把素材纸底平滑过渡到画布纸色
FEATHER_RATIO = 0.04

# 各图标相对画布的图案极径占比
FOREGROUND_RADIUS_FRAC = 33.0 / 108.0  # 108dp 画布中图案半径 33dp，外径约 61%
SQUARE_RADIUS_FRAC = 0.46              # 方图标：图案外径约占边长 92%
ROUND_RADIUS_FRAC = 0.43               # 圆形图标：图案外径约占直径 86%

# 各密度档位与对应的像素尺寸
DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

# 超采样倍数：先在放大后的画布上合成，再降采样，保证圆形蒙版与图案边缘平滑
SUPERSAMPLE = 4


def load_source():
    """读取素材图片并转换为 RGB。"""
    return Image.open(SRC_PATH).convert("RGB")


def feather_mask(size, feather_ratio=FEATHER_RATIO):
    """生成边缘线性羽化的灰度蒙版，返回 L 模式图像。

    参数 size 为蒙版宽高，feather_ratio 为羽化带占边长的比例；
    羽化只作用在图案方图的最外侧，图案本体位于更内侧，不受影响。
    """
    width, height = size
    feather = max(2.0, min(width, height) * feather_ratio)
    ys = np.arange(height)[:, None]
    xs = np.arange(width)[None, :]
    dist = np.minimum(
        np.minimum(xs, width - 1 - xs),
        np.minimum(ys, height - 1 - ys),
    ).astype(np.float32)
    alpha = np.clip(dist / feather, 0.0, 1.0)
    return Image.fromarray((alpha * 255).astype(np.uint8), "L")


def compose_on_paper(source, size, radius_frac):
    """在正方形纸色画布中央合成素材图案，返回 RGB 图像。

    size 为画布边长像素，radius_frac 为图案内容半径占画布边长的比例；
    素材按该比例缩放后以内容中心对齐画布中心，边缘羽化后贴入纸色画布。
    """
    canvas = Image.new("RGB", (size, size), PAPER)
    scale = radius_frac * size / CONTENT_RADIUS
    art_size = max(1, int(round(source.width * scale)))
    art = source.resize((art_size, art_size), Image.LANCZOS)
    mask = feather_mask((art_size, art_size))

    # 按内容中心对齐，计算贴图左上角，超出画布的部分显式裁剪
    left = int(round(size / 2 - CONTENT_CX * scale))
    top = int(round(size / 2 - CONTENT_CY * scale))
    x0, y0 = max(0, left), max(0, top)
    x1, y1 = min(size, left + art_size), min(size, top + art_size)
    if x1 <= x0 or y1 <= y0:
        return canvas
    sx0, sy0 = x0 - left, y0 - top
    sx1, sy1 = sx0 + (x1 - x0), sy0 + (y1 - y0)
    canvas.paste(art.crop((sx0, sy0, sx1, sy1)), (x0, y0), mask.crop((sx0, sy0, sx1, sy1)))
    return canvas


def make_square_icon(source, size):
    """生成传统方图标：纸色方形画布加居中图案，返回 RGB 图像。"""
    big = compose_on_paper(source, size * SUPERSAMPLE, SQUARE_RADIUS_FRAC)
    return big.resize((size, size), Image.LANCZOS)


def make_round_icon(source, size):
    """生成圆形图标：方图标内容套圆形蒙版，圆外完全透明，返回 RGBA 图像。"""
    big = compose_on_paper(source, size * SUPERSAMPLE, ROUND_RADIUS_FRAC)
    # 超采样尺寸下绘制圆形蒙版，降采样后边缘自带抗锯齿
    mask_big = Image.new("L", (size * SUPERSAMPLE, size * SUPERSAMPLE), 0)
    draw = ImageDraw.Draw(mask_big)
    draw.ellipse((0, 0, size * SUPERSAMPLE - 1, size * SUPERSAMPLE - 1), fill=255)
    icon_big = big.convert("RGBA")
    icon_big.putalpha(mask_big)
    # 颜色与透明度同时降采样，圆形边缘获得正确的抗锯齿
    return icon_big.resize((size, size), Image.LANCZOS)


def make_foreground(source, size):
    """生成自适应图标前景：108dp 纸色画布，图案内容位于中央安全区。"""
    big = compose_on_paper(source, size * 2, FOREGROUND_RADIUS_FRAC)
    return big.resize((size, size), Image.LANCZOS)


def save_png(image, path):
    """保存 PNG 并从文件回读，校验尺寸、模式与透明通道。"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path, "PNG", optimize=True)
    check = Image.open(path)
    alpha = check.getchannel("A") if check.mode == "RGBA" else None
    if alpha is None:
        alpha_info = "alpha=不透明(无通道)"
    else:
        alpha_info = "alpha min=%d max=%d" % (alpha.getextrema()[0], alpha.getextrema()[1])
    print("  %-58s %4dx%-4d %-5s %s" % (os.path.relpath(path, ROOT), check.width, check.height,
                                        check.mode, alpha_info))


def build_preview(source, path):
    """生成拼版预览：自适应图标模拟裁切、方图标、圆图标与深色底透明检查。"""
    cell = 220
    sheet = Image.new("RGBA", (cell * 3 + 40, cell + 40), (236, 238, 241, 255))

    # 自适应图标：前景叠加背景色，再按 72/108 可见区裁切并套圆角蒙版
    fg = make_foreground(source, 432).convert("RGBA")
    bg = Image.new("RGBA", fg.size, PAPER + (255,))
    adaptive = Image.alpha_composite(bg, fg)
    inner = int(432 * 72 / 108)
    adaptive = adaptive.crop(((432 - inner) // 2, (432 - inner) // 2,
                              (432 + inner) // 2, (432 + inner) // 2))
    adaptive = adaptive.resize((cell, cell), Image.LANCZOS)

    square = make_square_icon(source, 432).convert("RGBA").resize((cell, cell), Image.LANCZOS)
    round_icon = make_round_icon(source, 432).resize((cell, cell), Image.LANCZOS)

    # 深色底检查圆形图标的透明区域与方图标的边界
    dark = Image.new("RGBA", (cell, cell), (40, 44, 52, 255))
    round_on_dark = Image.alpha_composite(dark, round_icon)

    sheet.paste(adaptive, (10, 10), adaptive)
    sheet.paste(square, (cell + 20, 10), square)
    sheet.paste(round_on_dark, (cell * 2 + 30, 10), round_on_dark)
    sheet.save(path, "PNG")
    print("预览拼版：", os.path.relpath(path, ROOT))


def main():
    """按密度生成全部图标位图，并输出校验信息与预览图。"""
    source = load_source()
    print("素材：%dx%d，纸底色 %s，内容中心 (%.1f, %.1f)，内容极径 %.1f"
          % (source.width, source.height, PAPER, CONTENT_CX, CONTENT_CY, CONTENT_RADIUS))
    print("生成图标：")

    for density, factor in DENSITIES.items():
        folder = os.path.join(RES_DIR, "mipmap-" + density)
        square_px = int(round(48 * factor))
        round_px = square_px
        fg_px = int(round(108 * factor))

        save_png(make_foreground(source, fg_px),
                 os.path.join(folder, "ic_launcher_foreground.png"))
        save_png(make_square_icon(source, square_px),
                 os.path.join(folder, "ic_launcher.png"))
        save_png(make_round_icon(source, round_px),
                 os.path.join(folder, "ic_launcher_round.png"))

    build_preview(source, os.path.join(ROOT, ".iconwork", "preview.png"))


if __name__ == "__main__":
    main()
