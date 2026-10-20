package com.shuiyu.octoberrain.feature.home

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopPreviewProvider
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.device.DeviceRuntime

/**
 * 桌面预览提供者。
 *
 * 为美化模式创建与真实桌面同比例、含组件的只读视图。视图按设备像素尺寸完成测量与排布，
 * 调用方把返回视图缩放到任意容器后即得到缩略图；预览不接收触摸，也不写入全局布局快照。
 */
internal object HomeDesktopPreviewProvider : DesktopPreviewProvider {
    /**
     * 创建一页桌面的预览视图。
     *
     * 参数 pageIndex 为页码，从 0 开始；order 为预览使用的条目顺序，为空时沿用当前配置。
     * 返回视图已按屏幕宽度与桌面内容高度完成测量与排布；配置、应用目录或设备信息未就绪时
     * 返回 null，由调用方自行降级。
     */
    override fun createPreviewView(
        context: Context,
        pageIndex: Int,
        order: List<String>?,
    ): View? {
        if (!AppServices.isReady() || !DeviceRuntime.isReady()) return null
        val config = AppServices.config.state.value
        val apps = AppServices.catalog.apps.value
        // 调用方提供顺序时覆盖配置中的顺序，便于展示尚未保存的排序结果。
        val desktop =
            if (order.isNullOrEmpty()) {
                config.desktop
            } else {
                config.desktop.copy(order = order)
            }
        val previewConfig = config.copy(desktop = desktop)
        val view =
            HomeView(context).apply {
                previewMode = true
                isClickable = false
                isFocusable = false
                setWillNotDraw(false)
            }
        view.applyData(apps, previewConfig)
        val width = DeviceRuntime.profile.widthPx
        // 真实桌面视图位于状态栏下方，预览高度扣除状态栏，保证行数、间距与真实桌面一致。
        val height =
            (DeviceRuntime.profile.heightPx - DesignRuntime.tokens.statusBarHeight)
                .coerceAtLeast(1)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        // 排布完成后设置页码，同时让组件按当前数据刷新一次静态内容。
        view.setPreviewPage(pageIndex)
        return view
    }
}
