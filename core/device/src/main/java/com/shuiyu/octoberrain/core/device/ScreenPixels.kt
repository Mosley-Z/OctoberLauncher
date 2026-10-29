package com.shuiyu.octoberrain.core.device

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi

/**
 * 完整屏幕逻辑像素尺寸。
 *
 * 尺寸包含状态栏与导航栏，既不扣除系统栏，也不取 Activity 或 View 的内容区，
 * 供本机尺寸处理与壁纸输出共用。方向由宽高关系表示，宽大于高为横向。
 */
data class ScreenPixels(
    /** 水平逻辑像素数。 */
    val widthPx: Int,
    /** 垂直逻辑像素数。 */
    val heightPx: Int,
) {
    /** 是否横向显示。 */
    val isLandscape: Boolean get() = widthPx > heightPx

    /** 宽高是否均为正值，用于判断读取是否成功。 */
    val isValid: Boolean get() = widthPx > 0 && heightPx > 0

    /** 供界面显示的方向文案。 */
    val orientationLabel: String get() = if (isLandscape) "横向" else "纵向"

    /** 供界面显示的像素文案。 */
    val label: String get() = "$widthPx × $heightPx"
}

/**
 * 完整屏幕尺寸读取入口。
 *
 * Android 11 及以上读取显示屏的最大窗口边界，该值随当前方向变化且不受分屏、自由窗口
 * 或应用内容区影响；更低版本走 `Display.getRealSize` 兼容路径。读取失败返回宽高为 0
 * 的结果，调用方据此提示，不退回应用窗口尺寸。
 */
object ScreenSizeProvider {
    /**
     * 读取当前完整屏幕逻辑像素尺寸。
     *
     * 参数 context 为任意上下文，内部使用应用上下文，避免持有界面引用。
     * 返回值在读取失败时宽高均为 0，调用方需检查 [ScreenPixels.isValid]。
     */
    fun read(context: Context): ScreenPixels =
        runCatching { readInternal(context.applicationContext) }
            .getOrDefault(ScreenPixels(0, 0))
            .takeIf { it.isValid } ?: ScreenPixels(0, 0)

    /** 按系统版本选择读取路径。 */
    private fun readInternal(context: Context): ScreenPixels =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readMaximumWindowMetrics(context)
        } else {
            readRealSize(context)
        }

    /** Android 11 及以上读取显示屏最大窗口边界。 */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun readMaximumWindowMetrics(context: Context): ScreenPixels {
        val manager = context.getSystemService(WindowManager::class.java) ?: return ScreenPixels(0, 0)
        val bounds = manager.maximumWindowMetrics.bounds
        return ScreenPixels(bounds.width(), bounds.height())
    }

    /** 低版本按默认显示屏的真实尺寸读取。 */
    private fun readRealSize(context: Context): ScreenPixels {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        if (display == null) return ScreenPixels(0, 0)
        val point = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(point)
        return ScreenPixels(point.x, point.y)
    }
}
