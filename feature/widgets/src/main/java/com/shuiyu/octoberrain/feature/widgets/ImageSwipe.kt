package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_BOTH
import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_HORIZONTAL
import com.shuiyu.octoberrain.core.common.IMAGE_SWIPE_VERTICAL
import kotlin.math.abs

/**
 * 图片组件的滑动切图判定。
 *
 * 把一次手势序列的位移换算成轮播步进：向左或向上滑切到下一张，向右或向下滑切到上一张。
 * 方向由配置决定，只识别已启用的主轴；主轴位移未达阈值、两轴方向接近或方向关闭时返回 0。
 */
internal object ImageSwipe {
    /** 切到上一张图片的步进。 */
    const val STEP_PREVIOUS: Int = -1

    /** 切到下一张图片的步进。 */
    const val STEP_NEXT: Int = 1

    /** 未切图。 */
    const val STEP_NONE: Int = 0

    /** 双轴启用时主轴相对次轴的最小倍增，避免斜向手势被误判。 */
    private const val AXIS_BIAS: Float = 1.2f

    /**
     * 计算一次滑动的切图步进。
     *
     * @param direction 已归一化的切换方向，取 [IMAGE_SWIPE_HORIZONTAL]、[IMAGE_SWIPE_VERTICAL]、[IMAGE_SWIPE_BOTH]
     *   或关闭值
     * @param dx 相对按下点的横向位移，单位像素，向左为负
     * @param dy 相对按下点的纵向位移，单位像素，向上为负
     * @param threshold 判定所需的位移阈值，单位像素
     * @return [STEP_NEXT]、[STEP_PREVIOUS] 或 [STEP_NONE]
     */
    fun step(
        direction: Int,
        dx: Float,
        dy: Float,
        threshold: Float,
    ): Int {
        val horizontal = direction == IMAGE_SWIPE_HORIZONTAL || direction == IMAGE_SWIPE_BOTH
        val vertical = direction == IMAGE_SWIPE_VERTICAL || direction == IMAGE_SWIPE_BOTH
        if (threshold <= 0f || (!horizontal && !vertical)) return STEP_NONE
        val absX = abs(dx)
        val absY = abs(dy)
        return when {
            horizontal && absX >= absY * AXIS_BIAS -> horizontalStep(dx, absX, threshold)
            vertical && absY >= absX * AXIS_BIAS -> verticalStep(dy, absY, threshold)
            else -> STEP_NONE
        }
    }

    /** 横向主轴：向右滑切上一张，向左滑切下一张。 */
    private fun horizontalStep(
        dx: Float,
        distance: Float,
        threshold: Float,
    ): Int {
        if (distance < threshold) return STEP_NONE
        return if (dx < 0f) STEP_NEXT else STEP_PREVIOUS
    }

    /** 纵向主轴：向上滑切下一张，向下滑切上一张。 */
    private fun verticalStep(
        dy: Float,
        distance: Float,
        threshold: Float,
    ): Int {
        if (distance < threshold) return STEP_NONE
        return if (dy < 0f) STEP_NEXT else STEP_PREVIOUS
    }

    /**
     * 在图片总数内循环移动序号。
     *
     * @param index 当前序号
     * @param total 图片总数
     * @param step 切图步进
     * @return 首尾循环后的序号
     */
    fun wrap(
        index: Int,
        total: Int,
        step: Int,
    ): Int {
        if (total <= 0) return 0
        return ((index + step) % total + total) % total
    }
}
