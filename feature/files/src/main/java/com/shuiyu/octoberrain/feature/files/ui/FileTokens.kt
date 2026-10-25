package com.shuiyu.octoberrain.feature.files.ui

import com.shuiyu.octoberrain.core.design.InkTokens
import com.shuiyu.octoberrain.core.device.DeviceProfile

/**
 * 文件页尺寸令牌。
 *
 * 页面几何与字号全部由设备密度与用户字号缩放派生，界面代码不出现像素字面量。
 * 与桌面令牌的差异集中在顶栏、底栏、列表行与网格间距：文件页顶栏略高，
 * 底栏承载翻页按钮，网格保持四列四行。
 *
 * 参数 profile 为启动时算好的设备画像；textScale 为用户字号缩放，默认 1。
 */
class FileTokens(
    profile: DeviceProfile,
    textScale: Float = 1f,
) {
    private val ink = InkTokens(profile, textScale)

    /** 设备画像只读引用。 */
    val device: DeviceProfile get() = ink.device

    /** 一 dp 对应的像素数。 */
    val density: Float get() = ink.density

    /** dp 转像素并取整。 */
    fun dp(value: Float): Int = ink.dp(value)

    /** 以 sp 语义换算字号像素，叠加用户字号缩放。 */
    fun textSize(value: Float): Float = ink.textSize(value)

    /** 描边宽度，沿用桌面令牌，保证墨水屏上不出现发灰细线。 */
    val stroke: Int get() = ink.stroke

    /** 页面左右留白。 */
    val padH: Int get() = dp(18f)

    /** 顶栏高度，容纳标题与右侧动作。 */
    val topBarH: Int get() = dp(56f)

    /** 底栏高度，容纳翻页按钮与页码。 */
    val bottomBarH: Int get() = dp(52f)

    /** 列表行高。 */
    val rowH: Int get() = dp(44f)

    /** 列表行内缩略图边长。 */
    val rowThumb: Int get() = dp(30f)

    /** 网格间距。 */
    val gap: Int get() = dp(6f)

    /** 列表缩略图与名称之间的间距，以及主要信息与次要信息的分隔距离。 */
    val gapLarge: Int get() = dp(10f)

    /** 网格单元标签高度。 */
    val cellLabelH: Int get() = dp(16f)

    /** 手势与拖动判定阈值。 */
    val slop: Int get() = dp(12f)

    /** 最小触控块尺寸。 */
    val touchMin: Int get() = dp(44f)

    /** 底栏翻页按钮宽度。 */
    val navButtonWidth: Int get() = dp(84f)

    /** 底栏右侧动作按钮宽度。 */
    val actionButtonWidth: Int get() = dp(72f)

    /** 列表行内上下留白。 */
    val rowPadding: Int get() = dp(7f)

    /** 标题字号，对应桌面视觉层级的 17sp。 */
    val titleText: Float get() = textSize(17f)

    /** 正文字号，对应桌面视觉层级的 14sp。 */
    val bodyText: Float get() = textSize(14f)

    /** Grid names remain secondary to the visual preview. */
    val gridLabelText: Float get() = textSize(11f)

    /** 辅助字号，对应桌面视觉层级的 12sp。 */
    val smallText: Float get() = textSize(12f)
}
