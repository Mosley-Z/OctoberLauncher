package com.shuiyu.octoberrain.core.render

/**
 * 抖动算法的纯计算部分。
 *
 * 提供灰度权值、档位调色板与最近档位选择，不引用 Android 类型，可在本地 JVM 单元测试中验证。
 * 误差扩散的扫描顺序与权重仍由 [Ditherer] 负责。
 */
object DitherCore {
    /** 默认档位，与既有四档输出保持一致。 */
    const val DEFAULT_LEVELS = 4

    /** 支持的档位数。 */
    val supportedLevels: Set<Int> = setOf(2, 4, 8)

    /** 四档档位的既有取值，由浅到深。 */
    private val LEGACY_FOUR = intArrayOf(0xFF, 0xAC, 0x60, 0x00)

    /**
     * 把调用方传入的档位归一为受支持的值。
     *
     * 支持 2、4、8，其余值按 [DEFAULT_LEVELS] 处理。
     */
    fun normalizeLevels(levels: Int): Int = if (levels in supportedLevels) levels else DEFAULT_LEVELS

    /**
     * 生成档位的灰阶调色板，按由浅到深排列。
     *
     * 四档沿用既有取值，保证接入前后观感一致；二档与八档按等距灰阶生成。
     * 参数 levels 会先经 [normalizeLevels] 归一。
     */
    fun paletteOf(levels: Int): IntArray {
        val count = normalizeLevels(levels)
        if (count == DEFAULT_LEVELS) return LEGACY_FOUR.copyOf()
        return IntArray(count) { index ->
            val step = 255 * index / (count - 1)
            255 - step
        }
    }

    /**
     * 按亮度权值计算灰度值。
     *
     * 参数 r、g、b 为 0 至 255 的分量，返回值范围同为 0 至 255 的浮点数。
     */
    fun luma(
        r: Int,
        g: Int,
        b: Int,
    ): Float = 0.299f * r + 0.587f * g + 0.114f * b

    /** 找到与灰度值最接近的档位，参数 palette 为已排序的调色板。 */
    fun nearest(
        palette: IntArray,
        gray: Float,
    ): Int {
        var best = palette.firstOrNull() ?: 0
        var bestDistance = Float.MAX_VALUE
        for (level in palette) {
            val distance = kotlin.math.abs(gray - level)
            if (distance < bestDistance) {
                bestDistance = distance
                best = level
            }
        }
        return best
    }

    /**
     * 把 ARGB 像素数组转换为灰度 ARGB 数组。
     *
     * 参数 pixels 为源像素，返回同长度的新数组，透明通道固定为不透明。
     */
    fun grayscale(pixels: IntArray): IntArray {
        val output = IntArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val value =
                luma(
                    r = (pixel shr 16) and 0xFF,
                    g = (pixel shr 8) and 0xFF,
                    b = pixel and 0xFF,
                ).toInt().coerceIn(0, 255)
            output[index] = opaque(value)
        }
        return output
    }

    /** 把灰阶值组装为全不透明 ARGB 颜色。 */
    fun opaque(gray: Int): Int = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
}
