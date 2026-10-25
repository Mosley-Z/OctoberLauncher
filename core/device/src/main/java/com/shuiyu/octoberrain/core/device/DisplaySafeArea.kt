package com.shuiyu.octoberrain.core.device

/** Physical cover-glass compensation, expressed in pixels rather than density units. */
object DisplaySafeArea {
    fun physicalSideInset(
        manufacturer: String,
        model: String,
    ): Int = if (manufacturer.equals("Eink", true) && model.equals("P4", true)) 12 else 0

    fun insets(
        side: Int,
        width: Int,
        height: Int,
    ): Insets = if (width > height) Insets(0, side, 0, side) else Insets(side, 0, side, 0)

    data class Insets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
