# -*- coding: utf-8 -*-
"""从 P4 截图测量周期行、箭头与弹层的像素几何。

输出分为四段：周期行上下边线、箭头外接框、下拉列表行边界、选书弹层的标题与列表行。
"""
import numpy as np
from PIL import Image

BASE = r"_analysis/verify/20261020-parity"


def ink_rows(img, x0, x1, y0, y1, ratio=0.5):
    """统计区域内每行深色像素占比，返回超过 ratio 的行号与占比。"""
    arr = np.array(img.convert("L"))
    seg = arr[y0:y1, x0:x1]
    dark = (seg < 128).mean(axis=1)
    return [(y0 + i, round(float(v), 3)) for i, v in enumerate(dark) if v >= ratio]


def ink_cols(img, x0, x1, y0, y1, ratio=0.3):
    """统计区域内每列深色像素占比，返回超过 ratio 的列号与占比。"""
    arr = np.array(img.convert("L"))
    seg = arr[y0:y1, x0:x1]
    dark = (seg < 128).mean(axis=0)
    return [(x0 + i, round(float(v), 3)) for i, v in enumerate(dark) if v >= ratio]


def bbox_dark(img, x0, x1, y0, y1, threshold=128):
    """返回区域内深色像素的外接矩形，用于量箭头尺寸。"""
    arr = np.array(img.convert("L"))[y0:y1, x0:x1]
    ys, xs = np.where(arr < threshold)
    if len(xs) == 0:
        return None
    return (x0 + int(xs.min()), y0 + int(ys.min()), x0 + int(xs.max()), y0 + int(ys.max()))


def main():
    """逐图测量并打印结果。"""
    week = Image.open(f"{BASE}/p4-picker-03-reading.png")
    print("== 周期行（本周形态，未步进）==")
    print("横向界线：", ink_rows(week, 0, 552, 96, 150, 0.6))
    print("左箭头外接框：", bbox_dark(week, 8, 60, 105, 145, 100))
    print("右箭头外接框：", bbox_dark(week, 514, 552, 105, 145, 200))
    print("主标签行：", ink_rows(week, 60, 500, 108, 160, 0.15))
    print("副标签行：", ink_rows(week, 200, 360, 138, 165, 0.1))

    prev = Image.open(f"{BASE}/p4-picker-04-prev-week.png")
    print("== 步进后右箭头颜色（可取不可取）==")
    print("右箭头外接框：", bbox_dark(prev, 514, 552, 105, 145, 200))
    print("右箭头列分布：", ink_cols(prev, 516, 548, 112, 140, 0.1))

    drop = Image.open(f"{BASE}/p4-picker-05-dropdown.png")
    print("== 下拉列表 ==")
    print("边界线：", ink_rows(drop, 0, 552, 95, 345, 0.6))
    print("列表底边：", ink_rows(drop, 0, 552, 300, 350, 0.6))

    book = Image.open(f"{BASE}/p4-picker-08-book-pick.png")
    print("== 选书弹层 ==")
    print("标题行文字区：", bbox_dark(book, 0, 200, 90, 130, 100))
    print("搜索框下划线：", ink_rows(book, 20, 530, 150, 200, 0.6))
    print("取消按钮上边线：", ink_rows(book, 20, 532, 690, 720, 0.6))

    todo = Image.open(f"{BASE}/p4-todo-05.png")
    print("== 待办页 ==")
    print("页签下分隔线：", ink_rows(todo, 0, 552, 90, 130, 0.6))


if __name__ == "__main__":
    main()
