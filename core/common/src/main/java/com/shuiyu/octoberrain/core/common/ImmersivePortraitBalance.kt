package com.shuiyu.octoberrain.core.common

/** 竖屏富余纵向空间的连续权重；紧凑比例和横屏保持原布局。 */
fun immersivePortraitBalance(
    width: Float,
    height: Float,
): Float {
    val fraction = ((height / width.coerceAtLeast(1f) - 1.6f) / .4f).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}
