package com.shuiyu.octoberrain.core.render

/**
 * 解码尺寸决策。
 *
 * 纯计算对象，不引用 Android 类型，可在本地 JVM 单元测试中直接验证。
 * 常量取值沿用墨盘 `ImageDecode.java`：长边上限 3200 像素、采样目标上限 1600 像素、
 * 像素总量上限 8000 万、RGB_565 每像素 2 字节。
 */
object ImageSizing {
    /** 解码结果的长边上限，像素。 */
    const val MAX_EDGE = 3200

    /** 采样目标的边长上限：结果允许达到目标的两倍，故取上限的一半。 */
    const val MAX_TARGET = MAX_EDGE / 2

    /** 允许解码的像素总数上限，超过后按无法解码处理。 */
    const val LIMIT_PIXELS = 80_000_000L

    /** RGB_565 的每像素字节数，用于估算解码所需的堆空间。 */
    const val BYTES_PER_PIXEL = 2

    /** ARGB_8888 的每像素字节数，壁纸解码使用该配置。 */
    const val ARGB_BYTES_PER_PIXEL = 4

    /** 按堆预算继续降采样时允许的倍增次数，超过后按无法解码处理。 */
    private const val BUDGET_SAMPLE_STEPS = 8

    /** 图标缓存管线的像素总量上限，与既有 `BitmapPipe` 语义保持一致。 */
    const val ICON_MAX_PIXELS = 4_000_000

    /**
     * 判断边界是否可用。
     *
     * 宽高必须为正，且像素总数不超过 [LIMIT_PIXELS]。
     */
    fun withinLimit(
        width: Int,
        height: Int,
    ): Boolean = hasPositiveSize(width, height) && width.toLong() * height <= LIMIT_PIXELS

    /**
     * 判断边界是否可解码为正尺寸图片。
     *
     * 宽高均为正即视为有效，不施加 [LIMIT_PIXELS] 上限。壁纸保存等场景只要求文件能被识别为图片，
     * 超大图的解码压力由调用方的 [sampleFor] 采样与 [fitsHeap] 堆预算判断承担。
     */
    fun hasPositiveSize(
        width: Int,
        height: Int,
    ): Boolean = width > 0 && height > 0

    /**
     * 把调用方期望的长边压到合法区间。
     *
     * 小于 1 的值按 1 处理，超过 [MAX_TARGET] 的值按 [MAX_TARGET] 处理。
     */
    fun targetEdge(minPx: Int): Int = if (minPx < 1) 1 else minPx.coerceAtMost(MAX_TARGET)

    /**
     * 计算 2 的幂采样倍率。
     *
     * 从 1 开始逐次乘 2，直到长边除以采样值不大于目标的两倍，保证结果长边不超过
     * [MAX_EDGE]。目标先压到 [MAX_TARGET] 以内，因此调用方传入任意值都不会解出超大位图。
     */
    fun sampleFor(
        longEdge: Int,
        minPx: Int,
    ): Int {
        val limit = targetEdge(minPx).toLong() * 2
        var sample = 1
        while (sample.toLong() * 2 <= MAX_EDGE && longEdge / sample > limit) {
            sample *= 2
        }
        return sample
    }

    /**
     * 按目标宽高计算采样倍率，供 `BitmapPipe` 保持原有行为。
     *
     * 两个方向同时满足才继续降采样，之后按像素总量做兜底，避免长条图绕过尺寸判断。
     * 两个目标值都不大于 0 时返回 1。
     */
    fun requestSample(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        if (reqWidth <= 0 && reqHeight <= 0) return 1
        var sample = 1
        while (rawWidth / (sample * 2) >= reqWidth.coerceAtLeast(1) &&
            rawHeight / (sample * 2) >= reqHeight.coerceAtLeast(1)
        ) {
            sample *= 2
        }
        while (sampledPixels(rawWidth, rawHeight, sample) > ICON_MAX_PIXELS) {
            sample *= 2
        }
        return sample
    }

    /** 计算采样后的像素总数，宽高至少按 1 计。 */
    fun sampledPixels(
        width: Int,
        height: Int,
        sample: Int,
    ): Long {
        val safeSample = sample.coerceAtLeast(1)
        val w = (width / safeSample).coerceAtLeast(1).toLong()
        val h = (height / safeSample).coerceAtLeast(1).toLong()
        return w * h
    }

    /**
     * 按可用堆预算调整采样倍率。
     *
     * 在调用方已确定的采样倍率上继续倍增，直到 ARGB 位图与旋转产生的中间位图合计不超过可用堆；
     * 倍增次数超过 [BUDGET_SAMPLE_STEPS] 仍不满足时返回 null，由调用方按解码失败处理。
     *
     * @param width 原图宽
     * @param height 原图高
     * @param sample 调用方已确定的采样倍率
     * @param availableBytes 当前可用堆字节数
     * @return 满足预算的采样倍率；预算不足时返回 null
     */
    fun sampleWithinBudget(
        width: Int,
        height: Int,
        sample: Int,
        availableBytes: Long,
    ): Int? {
        if (!hasPositiveSize(width, height) || availableBytes <= 0L) return null
        val budgetPixels = availableBytes / (ARGB_BYTES_PER_PIXEL * 2)
        var current = sample.coerceAtLeast(1)
        var steps = 0
        while (steps < BUDGET_SAMPLE_STEPS && sampledPixels(width, height, current) > budgetPixels) {
            current *= 2
            steps += 1
        }
        return if (sampledPixels(width, height, current) <= budgetPixels) current else null
    }

    /**
     * 判断堆余量是否足够解码。
     *
     * 目标位图字节数按 RGB_565 与采样后尺寸估算，可用堆需要达到目标字节数的两倍，
     * 为旋转产生的中间位图留出一份余量。
     */
    fun fitsHeap(
        width: Int,
        height: Int,
        sample: Int,
        availableBytes: Long,
    ): Boolean {
        val bytes = sampledPixels(width, height, sample) * BYTES_PER_PIXEL
        return bytes > 0 && availableBytes >= bytes * 2
    }
}
