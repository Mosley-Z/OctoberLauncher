package com.shuiyu.octoberrain.core.device

import android.content.Context

/**
 * 高度断点，补充宽度断点在短屏与横屏下的信息。
 *
 * 768×552 这类横向握持的墨水屏宽度充足而高度有限，单靠宽度断点会高估可用空间。
 */
enum class HeightClass {
    /** 内容高度小于 500dp，需要压缩组件槽与行数。 */
    Short,

    /** 内容高度 500 至 719dp。 */
    Normal,

    /** 内容高度不小于 720dp。 */
    Tall,

    ;

    companion object {
        /**
         * 由内容区域高度判断断点。
         *
         * 参数 heightDp 为扣除系统栏后的高度。
         */
        fun fromHeight(heightDp: Int): HeightClass =
            when {
                heightDp < 500 -> Short
                heightDp < 720 -> Normal
                else -> Tall
            }
    }
}

/**
 * 设备画像：应用启动时计算一次，配置变化时重算。
 *
 * 界面层与渲染层只读本对象，禁止再次调用 DisplayMetrics 或读取像素常量。
 *
 * 参数 widthPx 与 heightPx 为当前窗口像素尺寸；widthDp 与 heightDp 为对应 dp 尺寸；
 * density 为像素密度；sizeClass 为宽度断点；heightClass 为高度断点；
 * display 为显示类型；hasFrontLight 表示是否具备前光通道；
 * statusBarInsetPx 为系统状态栏高度；navigationInsetPx 为导航栏高度；
 * longEdgePx 与 shortEdgePx 为长短边像素值。
 */
data class DeviceProfile(
    val widthPx: Int,
    val heightPx: Int,
    val widthDp: Int,
    val heightDp: Int,
    val density: Float,
    val sizeClass: SizeClass,
    val heightClass: HeightClass,
    val display: DisplayClass,
    val hasFrontLight: Boolean,
    val statusBarInsetPx: Int,
    val navigationInsetPx: Int,
    val longEdgePx: Int,
    val shortEdgePx: Int,
) {
    /** 宽高比，供横竖屏与折叠态判断使用。 */
    val aspectRatio: Float get() = if (heightPx == 0) 0f else widthPx.toFloat() / heightPx

    /** 是否为横向窗口。 */
    val isLandscape: Boolean get() = widthPx > heightPx

    /** 墨水屏刷新策略：短屏设备减少动画与整屏闪烁频率。 */
    val preferPartialRefresh: Boolean
        get() = display == DisplayClass.EinkFast || (display.isEink && heightClass != HeightClass.Tall)

    /** 桌面应用区列数，使用默认最小单元格宽度。 */
    val gridColumns: Int get() = gridColumns()

    /**
     * 桌面应用区列数，由断点与最小单元格宽度共同决定。
     *
     * 参数 minCellDp 为单元格最小宽度，默认 88dp。
     */
    fun gridColumns(minCellDp: Int = 88): Int {
        val byWidth = (widthDp / minCellDp).coerceIn(2, 8)
        val cap =
            when (sizeClass) {
                SizeClass.Compact -> 4
                SizeClass.Medium -> 6
                SizeClass.Expanded -> 8
            }
        return byWidth.coerceAtMost(cap)
    }
}

/**
 * 设备画像运行时持有者。
 *
 * Application 创建时调用 install，配置变化时调用 refresh；
 * 其他模块通过 DeviceRuntime.profile 读取。
 */
object DeviceRuntime {
    @Volatile
    lateinit var profile: DeviceProfile
        private set

    /** 计算并保存设备画像。 */
    fun install(context: Context) {
        profile = DeviceProfileDetector.detect(context.applicationContext)
    }

    /** 重新计算设备画像，用于横竖屏切换与分辨率变化。 */
    fun refresh(context: Context) {
        install(context)
    }

    /** 是否已初始化。 */
    fun isReady(): Boolean = this::profile.isInitialized
}
