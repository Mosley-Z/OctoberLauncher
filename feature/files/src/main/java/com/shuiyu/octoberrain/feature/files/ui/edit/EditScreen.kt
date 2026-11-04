package com.shuiyu.octoberrain.feature.files.ui.edit

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.feature.files.ui.FileTokens

/**
 * 编辑页入口来源。
 *
 * 用于记录页面由文件列表、查看器还是外部应用打开；外部应用交付的是缓存副本，
 * 保存流程只提供另存，其余来源保留覆盖。
 */
enum class EditSource {
    /** 由文件列表打开。 */
    FILES,

    /** 由查看器打开。 */
    VIEWER,

    /** 由外部应用 ACTION_VIEW 打开，源路径是应用缓存副本。 */
    EXTERNAL,

    ;

    companion object {
        /** 文件列表路由标识。 */
        private const val ROUTE_FILES = "files"

        /** 外部打开路由标识。 */
        private const val ROUTE_EXTERNAL = "external"

        /**
         * 按路由来源标识映射编辑页入口来源。
         *
         * 参数 source 为 `Destination.PhotoEditor.source`；未指定或无法识别时按查看器处理。
         */
        fun fromRoute(source: String?): EditSource =
            when (source) {
                ROUTE_FILES -> FILES
                ROUTE_EXTERNAL -> EXTERNAL
                else -> VIEWER
            }
    }
}

/**
 * 图片编辑页面。
 *
 * 按 [Screen] 契约创建自绘视图，负责尺寸令牌组装与生命周期转发；绘制、手势与导出由
 * [EditSurface] 处理。参数 path 为源图片绝对路径；source 为入口来源；onClose 在保存成功
 * 或放弃修改后调用，默认返回上层页面。
 */
class EditScreen(
    private val path: String,
    val source: EditSource = EditSource.FILES,
    private val onClose: () -> Unit = { AppServices.router.back() },
) : Screen {
    private var surface: EditSurface? = null
    private var closing = false

    override fun createView(context: Context): View {
        val tokens =
            FileTokens(
                DeviceRuntime.profile,
                AppServices.config.state.value.appearance.textScale,
            )
        val view = EditSurface(context, path, tokens, source, ::close)
        surface = view
        return view
    }

    override fun onResume() {
        surface?.invalidate()
    }

    override fun onBack(): Boolean {
        if (closing) {
            closing = false
            return false
        }
        return surface?.handleBack() ?: false
    }

    override fun onDestroy() {
        surface?.detach()
        surface = null
    }

    /** 关闭页面；标记关闭中，使后续返回键直接交给宿主弹栈。 */
    private fun close() {
        if (closing) return
        closing = true
        onClose()
    }
}
