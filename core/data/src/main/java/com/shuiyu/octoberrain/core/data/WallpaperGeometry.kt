package com.shuiyu.octoberrain.core.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 壁纸裁剪窗口，坐标为源位图像素坐标，右边界与下边界为开区间。
 */
data class CropWindow(
    /** 左边界像素坐标。 */
    val left: Int,
    /** 上边界像素坐标。 */
    val top: Int,
    /** 右边界像素坐标，不含该列。 */
    val right: Int,
    /** 下边界像素坐标，不含该行。 */
    val bottom: Int,
) {
    /** 窗口宽度，单位像素。 */
    val width: Int
        get() = right - left

    /** 窗口高度，单位像素。 */
    val height: Int
        get() = bottom - top
}

/**
 * 壁纸几何：计算按比例覆盖目标尺寸所需的居中裁剪窗口。
 *
 * 与拉伸填充不同，居中覆盖保持源图比例：源图宽高比大于目标时裁左右，
 * 否则裁上下。目标尺寸与源尺寸非法时返回 null，由调用方回退空白。
 */
object WallpaperGeometry {
    /**
     * 计算居中覆盖裁剪窗口。
     *
     * 参数 srcWidth 与 srcHeight 为源图尺寸，dstWidth 与 dstHeight 为目标尺寸。
     * 返回的窗口宽高比等于目标宽高比，并夹取到源图范围内；
     * 任一尺寸非正时返回 null。
     */
    fun centerCrop(
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
    ): CropWindow? {
        if (invalidSize(srcWidth, srcHeight) || invalidSize(dstWidth, dstHeight)) return null
        val scale = max(dstWidth.toDouble() / srcWidth, dstHeight.toDouble() / srcHeight)
        val cropWidth = (dstWidth / scale).roundToInt().coerceIn(1, srcWidth)
        val cropHeight = (dstHeight / scale).roundToInt().coerceIn(1, srcHeight)
        val left = ((srcWidth - cropWidth) / 2.0).roundToInt().coerceIn(0, srcWidth - cropWidth)
        val top = ((srcHeight - cropHeight) / 2.0).roundToInt().coerceIn(0, srcHeight - cropHeight)
        return CropWindow(
            left = left,
            top = top,
            right = left + cropWidth,
            bottom = top + cropHeight,
        )
    }

    /** 判断宽或高是否非正，供尺寸校验复用。 */
    private fun invalidSize(
        width: Int,
        height: Int,
    ): Boolean = width <= 0 || height <= 0
}
