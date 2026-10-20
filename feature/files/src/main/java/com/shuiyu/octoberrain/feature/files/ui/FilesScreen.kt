package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.VolumeKeySink
import com.shuiyu.octoberrain.core.design.InkColors

/**
 * 文件页入口。
 *
 * 页面由路由的 `Destination.Files` 触发，内部委托 [BrowseScreen] 承担列表与网格浏览、
 * 分页、排序与缩略图加载；本类只保留稳定类型名并对宿主暴露音量键能力。
 *
 * 参数 startPath 为路由携带的起始目录，null 表示回到上次浏览位置。
 */
class FilesScreen(
    startPath: String?,
) : Screen,
    VolumeKeySink {
    private val delegate = BrowseScreen(startPath)

    /** 创建页面视图，参数 context 为宿主上下文。 */
    override fun createView(context: Context): View {
        val host = FrameLayout(context).apply { setBackgroundColor(InkColors.PAPER) }
        host.addView(
            delegate.createView(context),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        return host
    }

    /** 页面进入前台时刷新当前目录。 */
    override fun onResume() {
        delegate.onResume()
    }

    /** 页面退到后台时记录当前位置。 */
    override fun onPause() {
        delegate.onPause()
    }

    /** 处理返回，返回 true 表示页面已消费。 */
    override fun onBack(): Boolean = delegate.onBack()

    /** 页面销毁时释放协程与预取任务。 */
    override fun onDestroy() {
        delegate.onDestroy()
    }

    /** 转发音量键，上键翻到上一页，下键翻到下一页。 */
    override fun onVolumeKey(up: Boolean): Boolean = delegate.onVolumeKey(up)
}
