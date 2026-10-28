package com.shuiyu.octoberrain.core.design

import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass

/**
 * 尺寸令牌：所有界面尺寸都由设备密度、断点、高度档与用户字号缩放派生。
 *
 * 构造参数 profile 为启动时算好的设备画像；textScale 为用户字号缩放，默认 1。
 * 绘制代码只读令牌，不出现像素字面量。
 */
class InkTokens(
    private val profile: DeviceProfile,
    val textScale: Float = 1f,
) {
    /** 设备画像只读引用。 */
    val device: DeviceProfile get() = profile

    /** 当前宽度断点。 */
    val sizeClass: SizeClass get() = profile.sizeClass

    /** 当前高度断点。 */
    val heightClass: HeightClass get() = profile.heightClass

    /** 一 dp 对应的像素数。 */
    val density: Float get() = profile.density

    /**
     * dp 转像素并取整。
     *
     * 参数 value 为设计稿中的 dp 数值。
     */
    fun dp(value: Float): Int = Math.round(value * density)

    /** dp 转像素，保留浮点。 */
    fun dpF(value: Float): Float = value * density

    /**
     * 以 sp 语义换算字号像素，叠加用户字号缩放。
     *
     * 参数 value 为设计稿中的 sp 数值。
     */
    fun textSize(value: Float): Float = value * textScale * density

    /** 页面左右留白。 */
    val pageGutter: Int get() = dp(if (sizeClass == SizeClass.Compact) 14f else 20f)

    /** 内容顶部留白。 */
    val pageTop: Int get() = dp(14f)

    /** 内容底部留白。 */
    val pageBottom: Int get() = dp(14f)

    /** 常规间距。 */
    val gap: Int get() = dp(if (sizeClass == SizeClass.Expanded) 10f else 8f)

    /** 大间距。 */
    val gapLarge: Int get() = dp(16f)

    /** 描边宽度，墨水屏上小于 1px 会发灰。 */
    val stroke: Int get() = maxOf(1, dp(1.2f))

    /** 卡片与按钮圆角。 */
    val radius: Int get() = dp(if (sizeClass == SizeClass.Compact) 6f else 8f)

    /** 小圆角，用于图标内框。 */
    val radiusSmall: Int get() = dp(3f)

    /** 页面标题字号。 */
    val textTitle: Float get() =
        textSize(
            when (sizeClass) {
                SizeClass.Compact -> 20f
                SizeClass.Medium -> 22f
                SizeClass.Expanded -> 26f
            },
        )

    /** 区块标题字号。 */
    val textHeading: Float get() = textSize(17f)

    /** 正文字号。 */
    val textBody: Float get() =
        textSize(
            when (sizeClass) {
                SizeClass.Compact -> 14f
                SizeClass.Medium -> 15f
                SizeClass.Expanded -> 17f
            },
        )

    /** 说明字号。 */
    val textCaption: Float get() = maxOf(20f, textSize(14f))

    /** 最小字号，仅用于不可省略的辅助文字。 */
    val textSmall: Float get() = maxOf(18f, textSize(13f))

    /** 状态栏字号。 */
    val textStatus: Float get() = textSize(if (sizeClass == SizeClass.Compact) 14f else 16f)

    /** 状态栏高度。 */
    val statusBarHeight: Int get() = dp(if (heightClass == HeightClass.Short) 28f else 34f)

    /** 状态栏图标尺寸。 */
    val statusIcon: Int get() = dp(16f)

    /** 状态栏时间区域宽度。 */
    val statusTimeWidth: Int get() = dp(if (sizeClass == SizeClass.Compact) 64f else 82f)

    /** 应用宫格的最小单元格宽度。 */
    val cellMinWidth: Int get() = dp(84f)

    /** 图标框允许的最大边长。 */
    val iconMax: Int get() = dp(if (heightClass == HeightClass.Short) 56f else 66f)

    /**
     * 根据单元格宽度推导图标框边长。
     *
     * 参数 cellWidth 为单元格宽度像素，取值约定为宽度的 58%，不超过上限。
     */
    fun iconFrame(cellWidth: Int): Int = minOf(iconMax, (cellWidth * 0.58f).toInt())

    /** 图标标签行高。 */
    val labelHeight: Int get() = dp(if (sizeClass == SizeClass.Compact) 22f else 24f)

    /** 标签按压下划线厚度。 */
    val labelUnderline: Int get() = maxOf(2, dp(2f))

    /** 列表行高。 */
    val listRowHeight: Int get() = dp(if (heightClass == HeightClass.Short) 40f else 46f)

    /** 列表图标尺寸。 */
    val listIcon: Int get() = dp(26f)

    /** 页码点直径。 */
    val dot: Int get() = dp(3f)

    /** 页码点间距。 */
    val dotGap: Int get() = dp(6f)

    /** 页码区域高度。 */
    val indicatorHeight: Int get() = dp(14f)

    /** 按钮高度。 */
    val buttonHeight: Int get() = dp(if (heightClass == HeightClass.Short) 36f else 42f)

    /** 按钮最小宽度。 */
    val buttonMinWidth: Int get() = dp(72f)

    /** 对话框内边距。 */
    val dialogPadding: Int get() = dp(18f)

    /** 托盘单行高度。 */
    val trayRow: Int get() = dp(if (heightClass == HeightClass.Short) 38f else 44f)

    /** 托盘档位条高度。 */
    val trayBar: Int get() = dp(10f)

    /** 托盘档位条触控高度。 */
    val trayTouch: Int get() = dp(24f)

    /** 托盘区左右留白。 */
    val trayGutter: Int get() = dp(18f)

    /** 轻提示高度。 */
    val toastHeight: Int get() = dp(36f)

    /** 字形描边宽度。 */
    val glyphStroke: Int get() = maxOf(2, dp(1.8f))

    /** 页首返回栏高度。 */
    val topBarHeight: Int get() = dp(if (heightClass == HeightClass.Short) 40f else 46f)

    /** 设置行内图标尺寸。 */
    val settingsIcon: Int get() = dp(18f)

    /** 进度条高度。 */
    val progressHeight: Int get() = dp(6f)
}
