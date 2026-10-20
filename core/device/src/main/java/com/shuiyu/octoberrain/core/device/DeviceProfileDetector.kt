package com.shuiyu.octoberrain.core.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * 设备画像探测器。
 *
 * 只在 core:device 内读取 DisplayMetrics，其他模块经 DeviceProfile 获取尺寸与能力。
 * 屏幕准入不再限制像素值：任何分辨率都进入桌面，布局按断点与高度分级。
 */
object DeviceProfileDetector {
    /** 已知墨水屏厂商与型号片段，命中即视为墨水屏。 */
    private val EINK_MARKERS =
        listOf(
            "s4",
            "s5",
            "s6",
            "ireader",
            "tango",
            "inkpalm",
            "onyx",
            "boox",
            "boyue",
            "likebook",
            "meebook",
            "moaan",
            "hisense",
            "eink",
            "pocketbook",
            "dasung",
            "supernote",
            "reinkstone",
            "bigme",
            "oasis",
            "kindle",
            "kobo",
            "tolino",
        )

    /** 前光暖光设置项键，与系统能力实现写入的键一致。 */
    private const val KEY_FRONT_LIGHT = "screen_brightness1"

    /**
     * 计算当前设备画像。
     *
     * 参数 context 用于取资源与窗口指标；调用方通常在 Application 创建时传入。
     */
    fun detect(context: Context): DeviceProfile {
        val resources = context.resources
        val metrics: DisplayMetrics = resources.displayMetrics
        val config = resources.configuration

        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val density = if (metrics.density > 0f) metrics.density else 1f
        val widthDp = if (config.screenWidthDp > 0) config.screenWidthDp else (widthPx / density).toInt()
        val heightDp = if (config.screenHeightDp > 0) config.screenHeightDp else (heightPx / density).toInt()
        val sizeClass = SizeClass.fromWidth(widthDp)
        val heightClass = HeightClass.fromHeight(heightDp)
        val display = detectDisplay()

        return DeviceProfile(
            widthPx = widthPx,
            heightPx = heightPx,
            widthDp = widthDp,
            heightDp = heightDp,
            density = density,
            sizeClass = sizeClass,
            heightClass = heightClass,
            display = display,
            hasFrontLight = resolveFrontLight(display.isEink) { probeFrontLight(context) },
            statusBarInsetPx = systemBarHeight(context, "status_bar_height"),
            navigationInsetPx = systemBarHeight(context, "navigation_bar_height"),
            longEdgePx = maxOf(widthPx, heightPx),
            shortEdgePx = minOf(widthPx, heightPx),
        )
    }

    /**
     * 读取系统栏高度。
     *
     * 参数 name 为状态栏或导航栏资源名；读取失败返回 0。
     */
    private fun systemBarHeight(
        context: Context,
        name: String,
    ): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    /**
     * 判断显示类型。
     *
     * 先看厂商与型号关键字，命中视为墨水屏；未命中按液晶屏处理。
     * 刷新能力由后续的厂商探测补充，默认取 EinkFull。
     */
    private fun detectDisplay(): DisplayClass {
        val identity =
            listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE)
                .joinToString(" ")
                .lowercase()
        val knownEink = EINK_MARKERS.any { identity.contains(it) }
        return if (knownEink) DisplayClass.EinkFull else DisplayClass.Lcd
    }

    /**
     * 结合墨水屏判定与前光探测得出前光能力。
     *
     * 液晶屏直接判定无前光；墨水屏以探测结果为准，探测抛出异常或返回假时判定无前光，
     * 避免对不具备前光硬件的机型写入屏幕亮度设置项。
     *
     * @param isEink 是否为墨水屏
     * @param probe 前光探测，返回真表示存在前光硬件
     * @return 前光硬件存在返回真
     */
    internal fun resolveFrontLight(
        isEink: Boolean,
        probe: () -> Boolean,
    ): Boolean {
        if (!isEink) return false
        return runCatching(probe).getOrDefault(false)
    }

    /**
     * 探测前光硬件是否存在。
     *
     * 暖光设置项由厂商在系统设置中登记，键不存在时读取抛出 SettingNotFoundException，
     * 由 [resolveFrontLight] 统一降级为不存在。
     *
     * @param context 用于读取系统设置的内容解析器
     * @return 键存在返回真
     */
    private fun probeFrontLight(context: Context): Boolean {
        Settings.System.getInt(context.contentResolver, KEY_FRONT_LIGHT)
        return true
    }

    /**
     * 判断当前窗口是否为横屏。
     *
     * 参数 context 用于读取窗口管理器；异常时按宽度大于高度判断。
     */
    fun isLandscape(context: Context): Boolean =
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            @Suppress("DEPRECATION")
            val rotation = wm.defaultDisplay.rotation
            rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
        } catch (e: Exception) {
            val metrics = context.resources.displayMetrics
            metrics.widthPixels > metrics.heightPixels
        }
}
