package com.shuiyu.octoberrain.feature.files.ui

/**
 * 黑白滤镜参数。
 *
 * level 取 0 至 4，依次为原图灰阶、十六级分级抖动、高反差二值、8×8 有序抖动与柔和灰阶；
 * contrast 取 0 至 2，依次为 0.85、1.00 与 1.15 三档对比度；keepAlpha 为真时保留原图
 * 透明通道，为假时输出不透明像素。越界值在计算前夹取到合法区间。
 */
data class GrayParams(
    val level: Int = 0,
    val contrast: Int = 1,
    val keepAlpha: Boolean = true,
) {
    /** 夹取后的滤镜档位。 */
    val safeLevel: Int get() = level.coerceIn(0, GrayKernels.LEVELS - 1)

    /** 夹取后的对比度档位。 */
    val safeContrast: Int get() = contrast.coerceIn(0, GrayKernels.CONTRASTS - 1)
}

/**
 * 黑白滤镜的纯函数入口。
 *
 * 输入与输出均为 ARGB 像素数组，不引用 Android 类型，可在本地 JVM 单元测试中直接验证。
 * 五档滤镜的公式与查表数据见 [GrayKernels]；对比度在滤镜结果上围绕 128 缩放，
 * 与滤镜档位相互独立。调用方负责保证尺寸与像素数量一致。
 */
object Gray {
    /**
     * 按参数对像素数组执行黑白滤镜。
     *
     * 参数 pixels 为 ARGB 像素；width 与 height 为图像尺寸；params 为滤镜档位、
     * 对比度档位与透明通道策略。返回同尺寸的新数组，不修改输入。
     * 尺寸非正或与像素数量不一致时返回输入的副本，保证调用方拿到的数组长度稳定。
     */
    fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        params: GrayParams,
    ): IntArray {
        if (width <= 0 || height <= 0 || pixels.size != width * height) return pixels.copyOf()
        val level = params.safeLevel
        val contrast = params.safeContrast
        val output = IntArray(pixels.size)
        when (level) {
            GrayKernels.BANDED -> banded(pixels, output, width, height, contrast, params.keepAlpha)
            GrayKernels.HIGH_CONTRAST -> threshold(pixels, output, contrast, params.keepAlpha)
            GrayKernels.ORDERED -> ordered(pixels, output, width, contrast, params.keepAlpha)
            GrayKernels.SOFT -> soft(pixels, output, contrast, params.keepAlpha)
            else -> plain(pixels, output, contrast, params.keepAlpha)
        }
        return output
    }

    /** 原图灰阶：只计算亮度并应用对比度。 */
    private fun plain(
        pixels: IntArray,
        output: IntArray,
        contrast: Int,
        keepAlpha: Boolean,
    ) {
        for (index in pixels.indices) {
            val value = luminance(pixels[index])
            output[index] = pack(GrayKernels.contrast(value, contrast), alphaOf(pixels[index], keepAlpha))
        }
    }

    /** 十六级分级抖动：量化到 17 的倍数并把误差扩散给后续像素。 */
    private fun banded(
        pixels: IntArray,
        output: IntArray,
        width: Int,
        height: Int,
        contrast: Int,
        keepAlpha: Boolean,
    ) {
        val gray = FloatArray(pixels.size) { luminance(pixels[it]).toFloat() }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val quantized = GrayKernels.quantize16(gray[index].toInt())
                GrayKernels.spread(gray, width, height, x, y, gray[index] - quantized)
                output[index] =
                    pack(
                        GrayKernels.contrast(quantized, contrast),
                        alphaOf(pixels[index], keepAlpha),
                    )
            }
        }
    }

    /** 高反差二值：先按 1.5 倍拉伸，再以 128 为界输出黑白。 */
    private fun threshold(
        pixels: IntArray,
        output: IntArray,
        contrast: Int,
        keepAlpha: Boolean,
    ) {
        for (index in pixels.indices) {
            val stretched = GrayKernels.highContrast(luminance(pixels[index]))
            val binary = if (stretched >= 128) 255 else 0
            output[index] = pack(GrayKernels.contrast(binary, contrast), alphaOf(pixels[index], keepAlpha))
        }
    }

    /** 有序抖动：按 8×8 Bayer 阈值输出黑白。 */
    private fun ordered(
        pixels: IntArray,
        output: IntArray,
        width: Int,
        contrast: Int,
        keepAlpha: Boolean,
    ) {
        for (index in pixels.indices) {
            val x = index % width
            val y = index / width
            val value = luminance(pixels[index])
            val binary = if (value >= GrayKernels.bayerThreshold(x, y)) 255 else 0
            output[index] = pack(GrayKernels.contrast(binary, contrast), alphaOf(pixels[index], keepAlpha))
        }
    }

    /** 柔和灰阶：伽马 0.85 与固定对比度 0.9 的查表结果再叠加对比度档位。 */
    private fun soft(
        pixels: IntArray,
        output: IntArray,
        contrast: Int,
        keepAlpha: Boolean,
    ) {
        for (index in pixels.indices) {
            val softened = GrayKernels.soft(luminance(pixels[index]))
            output[index] = pack(GrayKernels.contrast(softened, contrast), alphaOf(pixels[index], keepAlpha))
        }
    }

    /** 计算单个像素的亮度。 */
    private fun luminance(pixel: Int): Int =
        GrayKernels.gray(
            r = (pixel shr 16) and 0xFF,
            g = (pixel shr 8) and 0xFF,
            b = pixel and 0xFF,
        )

    /** 取输出透明度，保留透明通道时沿用原值，否则固定不透明。 */
    private fun alphaOf(
        pixel: Int,
        keepAlpha: Boolean,
    ): Int = if (keepAlpha) (pixel ushr 24) and 0xFF else 0xFF
}

/** 把灰阶值与透明度组合为不透明或半透明的 ARGB 像素。 */
private fun pack(
    value: Int,
    alpha: Int,
): Int = (alpha shl 24) or (value shl 16) or (value shl 8) or value
