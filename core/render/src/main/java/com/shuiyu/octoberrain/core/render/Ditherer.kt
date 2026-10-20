package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 灰度抖动：把彩色或连续灰度图转换为墨水屏可显示的分档灰位图。
 *
 * 采用 Floyd–Steinberg 误差扩散，输出仍为 ARGB_8888 但只包含调色板内的灰阶值，
 * 避免在只支持少量灰阶的面板上出现色带。档位与灰度权值的纯计算见 [DitherCore]。
 */
object Ditherer {
    /** 误差扩散权重：右、左下、正下、右下，分母为 16。 */
    private const val DIFFUSION_DENOMINATOR = 16f

    /**
     * 对位图做四档抖动，保持既有调用方的观感。
     *
     * 参数 source 为源位图，函数不修改源图；返回同尺寸的新图。
     */
    fun dither(source: Bitmap): Bitmap = dither(source, DitherCore.DEFAULT_LEVELS)

    /**
     * 按指定档位做抖动。
     *
     * 参数 source 为源位图，函数不修改源图；levels 支持 2、4、8 档，其余值按 4 档处理。
     * 误差扩散算法与四档版本一致，仅把最近档位替换为对应调色板。
     */
    fun dither(
        source: Bitmap,
        levels: Int,
    ): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val palette = DitherCore.paletteOf(levels)
        val gray = lumaOf(source)
        diffuse(gray, palette, width, height)

        val output = IntArray(width * height)
        for (i in output.indices) {
            val level = gray[i].toInt().coerceIn(0, 255)
            output[i] = DitherCore.opaque(DitherCore.nearest(palette, level.toFloat()))
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * 按行扫描分摊量化误差。
     *
     * 参数 gray 为亮度数组，函数原地修改；palette 为档位调色板，width 与 height 为位图尺寸。
     */
    private fun diffuse(
        gray: FloatArray,
        palette: IntArray,
        width: Int,
        height: Int,
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val old = gray[index]
                val nearest = DitherCore.nearest(palette, old)
                gray[index] = nearest.toFloat()
                spread(gray, width, height, x, y, old - nearest)
            }
        }
    }

    /**
     * 把单个像素的量化误差按 Floyd–Steinberg 权重分给右、左下、正下与右下邻点。
     *
     * 参数 x 与 y 为当前像素坐标，error 为量化误差；边界外的邻点直接跳过。
     */
    private fun spread(
        gray: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        error: Float,
    ) {
        if (x + 1 < width) gray[y * width + x + 1] += error * 7f / DIFFUSION_DENOMINATOR
        if (y + 1 >= height) return
        if (x > 0) {
            gray[(y + 1) * width + x - 1] += error * 3f / DIFFUSION_DENOMINATOR
        }
        gray[(y + 1) * width + x] += error * 5f / DIFFUSION_DENOMINATOR
        if (x + 1 < width) {
            gray[(y + 1) * width + x + 1] += error * 1f / DIFFUSION_DENOMINATOR
        }
    }

    /**
     * 把位图转换为纯灰度图，供查看器默认灰度显示。
     *
     * 参数 source 为源位图，函数不修改源图；返回同尺寸的不透明灰度新图。
     */
    fun grayscale(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) return source
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        return Bitmap.createBitmap(
            DitherCore.grayscale(pixels),
            width,
            height,
            Bitmap.Config.ARGB_8888,
        )
    }

    /**
     * 把位图转换为纯黑白二值图，用于小尺寸图标。
     *
     * 参数 threshold 为分界值，默认 128；高于阈值的像素置白。
     */
    fun threshold(
        source: Bitmap,
        threshold: Int = 128,
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = IntArray(width * height)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = DitherCore.luma(r, g, b).toInt()
            output[i] = if (gray >= threshold) Color.WHITE else Color.BLACK
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    /** 读取源位图的亮度数组，长度与像素数一致。 */
    private fun lumaOf(source: Bitmap): FloatArray {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = FloatArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            gray[index] =
                DitherCore.luma(
                    r = (pixel shr 16) and 0xFF,
                    g = (pixel shr 8) and 0xFF,
                    b = pixel and 0xFF,
                )
        }
        return gray
    }
}
