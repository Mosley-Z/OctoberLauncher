package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.NegativeHost

/**
 * 墨台中的内核分区。
 *
 * 墨单、阅读资产、阅读指纹三个分区由微读墨记内核按原版版式绘制，分区抬头（墨竖线、标题与
 * 右侧动作）也由内核画出，因此本类只承载内核页面并转发生命周期，不再额外叠加原生抬头，
 * 避免同一分区出现两行标题。
 *
 * @param context 页面上下文。
 * @param host 负一屏宿主，用于请求重绘。
 * @param moduleId 内核模块标识，取 bill、rank、fp。
 * @param onSettings 内核请求打开墨台设置态的回调。
 */
internal class DeskKernelSection(
    context: Context,
    screenHost: NegativeHost,
    moduleId: String,
    onSettings: () -> Unit = {},
) : LinearLayout(context) {
    private val sectionView: WereadDeskSectionView =
        WereadDeskSectionView(context, screenHost, moduleId, onSettings)

    init {
        orientation = VERTICAL
        addView(
            sectionView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /** 页面回到前台时重新读取外观配置与原盘数据。 */
    fun onResume() {
        sectionView.reload()
    }

    /** 页面离开前台时的处理，分区不持有后台任务。 */
    fun onPause() = Unit

    /** 打开壁纸预览。 */
    fun generateWallpaper() {
        sectionView.generateWallpaper()
    }

    /** 打开阅读指纹选书对话框。 */
    fun openFingerprintPicker() {
        sectionView.openFingerprintPicker()
    }

    /** 释放内核页面持有的数据缓存与图像资源。 */
    fun release() {
        sectionView.release()
    }
}
