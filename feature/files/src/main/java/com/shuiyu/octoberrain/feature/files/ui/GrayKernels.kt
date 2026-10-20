package com.shuiyu.octoberrain.feature.files.ui

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 黑白滤镜的查表数据与误差扩散边界处理。
 *
 * 存放灰阶权重、对比度表、柔和档位的伽马表、8×8 Bayer 矩阵与十六级量化函数，
 * 供 [Gray] 与后续预览层共用。全部数据在类加载时构建，逐像素处理只做整数查表，
 * 避免在绘制线程内重复浮点运算。
 */
internal object GrayKernels {
    /** 原图灰阶档位。 */
    const val ORIGINAL = 0

    /** 十六级分级抖动档位。 */
    const val BANDED = 1

    /** 高反差二值档位。 */
    const val HIGH_CONTRAST = 2

    /** 8×8 有序抖动档位。 */
    const val ORDERED = 3

    /** 柔和灰阶档位。 */
    const val SOFT = 4

    /** 滤镜档位总数。 */
    const val LEVELS = 5

    /** 对比度档位总数。 */
    const val CONTRASTS = 3

    /** 误差扩散的权重分母。 */
    private const val DIFFUSION = 16f

    /** 灰度权重表，顺序为红、绿、蓝，分母为 1000。 */
    private val WEIGHTS = intArrayOf(299, 587, 114)

    /** 对比度倍率表，顺序对应三档，分母为 100。 */
    private val CONTRAST_RATIOS = intArrayOf(85, 100, 115)

    /** 对比度查表，行对应档位，列对应输入灰阶。 */
    private val CONTRAST_TABLE =
        Array(CONTRASTS) { index ->
            IntArray(256) { value -> contrastOf(value, CONTRAST_RATIOS[index]) }
        }

    /** 柔和档位的伽马与固定对比度查表。 */
    private val SOFT_TABLE = IntArray(256) { value -> softOf(value) }

    /** 8×8 Bayer 矩阵，元素范围 0 至 63。 */
    private val BAYER =
        intArrayOf(
            0,
            32,
            8,
            40,
            2,
            34,
            10,
            42,
            48,
            16,
            56,
            24,
            50,
            18,
            58,
            26,
            12,
            44,
            4,
            36,
            14,
            46,
            6,
            38,
            60,
            28,
            52,
            20,
            62,
            30,
            54,
            22,
            3,
            35,
            11,
            43,
            1,
            33,
            9,
            41,
            51,
            19,
            59,
            27,
            49,
            17,
            57,
            25,
            15,
            47,
            7,
            39,
            13,
            45,
            5,
            37,
            63,
            31,
            55,
            23,
            61,
            29,
            53,
            21,
        )

    /**
     * 按权重表计算单个像素的亮度。
     *
     * 参数 r、g、b 为 0 至 255 的通道值；返回 0 至 255 的亮度。
     */
    fun gray(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (r * WEIGHTS[0] + g * WEIGHTS[1] + b * WEIGHTS[2]) / 1000

    /**
     * 按档位应用对比度。
     *
     * 参数 value 为输入灰阶；level 越界时按四舍五入后的合法档位处理。
     */
    fun contrast(
        value: Int,
        level: Int,
    ): Int = CONTRAST_TABLE[level.coerceIn(0, CONTRASTS - 1)][value.coerceIn(0, 255)]

    /**
     * 应用柔和档位的伽马与固定对比度。
     *
     * 参数 value 为输入灰阶，返回 0 至 255 的结果。
     */
    fun soft(value: Int): Int = SOFT_TABLE[value.coerceIn(0, 255)]

    /**
     * 计算高反差档位的中间值。
     *
     * 输入灰阶先按 1.5 倍围绕 128 拉伸，再夹取到 0 至 255。
     */
    fun highContrast(value: Int): Int = clamp((value - 128) * 1.5f + 128f)

    /**
     * 取有序抖动的阈值。
     *
     * 参数 x 与 y 为像素坐标；返回 2 至 254 的阈值，Bayer 元素均匀覆盖 64 档。
     */
    fun bayerThreshold(
        x: Int,
        y: Int,
    ): Int = BAYER[(y and 7) * 8 + (x and 7)] * 4 + 2

    /**
     * 把亮度取整到十六级调色板。
     *
     * 级距为 17，输入先夹取到 0 至 255，结果同样落在该区间。
     */
    fun quantize16(value: Int): Int {
        val safe = value.coerceIn(0, 255)
        return ((safe + 8) / 17 * 17).coerceAtMost(255)
    }

    /**
     * 把量化误差按 Floyd–Steinberg 权重分给右、左下、正下与右下邻点。
     *
     * 参数 gray 为亮度数组，函数原地累加误差；x 与 y 为当前像素坐标，
     * error 为量化误差，边界外的邻点直接跳过。
     */
    fun spread(
        gray: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        error: Float,
    ) {
        if (x + 1 < width) gray[y * width + x + 1] += error * 7f / DIFFUSION
        if (y + 1 >= height) return
        if (x > 0) gray[(y + 1) * width + x - 1] += error * 3f / DIFFUSION
        gray[(y + 1) * width + x] += error * 5f / DIFFUSION
        if (x + 1 < width) gray[(y + 1) * width + x + 1] += error * 1f / DIFFUSION
    }

    /** 按倍率计算对比度查表项，结果夹取到 0 至 255。 */
    private fun contrastOf(
        value: Int,
        ratio: Int,
    ): Int = clamp((value - 128) * ratio / 100f + 128f)

    /** 计算柔和档位的查表项：先取 0.85 次幂，再按 0.9 倍对比度围绕 128 缩放。 */
    private fun softOf(value: Int): Int {
        val gamma = 255f * (value / 255f).pow(0.85f)
        return clamp((gamma - 128f) * 0.9f + 128f)
    }

    /** 四舍五入后夹取到 0 至 255。 */
    private fun clamp(value: Float): Int = value.roundToInt().coerceIn(0, 255)
}
