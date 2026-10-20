package com.shuiyu.octoberrain.core.device

/**
 * 宽度断点，取代上游写死的 480×800 画布。
 *
 * 取值依据 Material 3 窗口尺寸分级，界面按该枚举选择列数、留白与字号。
 */
enum class SizeClass {
    /** 宽度小于 600dp，手机与小尺寸阅读器。 */
    Compact,

    /** 宽度 600 至 839dp，小平板与横向握持的阅读器。 */
    Medium,

    /** 宽度不小于 840dp，大屏设备。 */
    Expanded,

    ;

    companion object {
        /**
         * 由宽度 dp 值判断断点。
         *
         * 参数 widthDp 为当前窗口宽度，单位 dp。
         */
        fun fromWidth(widthDp: Int): SizeClass =
            when {
                widthDp < 600 -> Compact
                widthDp < 840 -> Medium
                else -> Expanded
            }
    }
}
