package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.NegativeModule

/**
 * 墨台容器模块。
 *
 * 负一屏在检测到本模块后，直接以其返回的视图铺满页面，不再按普通卡片模块渲染。视图内部
 * 包含墨台顶栏、五个分区、设置态与页内浮层，所有交互都在同一页面完成。左侧滑动或顶栏
 * 返回入口通过路由返回桌面。
 */
internal class DeskNegativeModule : NegativeModule {
    override val id: String = ID

    override val label: String = LABEL

    override val defaultEnabled: Boolean = true

    override val orderHint: Int = 0

    private var container: DeskContainerView? = null

    override fun createView(
        context: Context,
        host: NegativeHost,
    ): View {
        val view = DeskContainerView(context, host) { closeNegativeScreen() }
        container = view
        return view
    }

    /** 回到负一屏时刷新各分区数据与更新时间。 */
    override fun onResume() {
        container?.onResumeSections()
    }

    /** 离开负一屏时暂停各分区读取。 */
    override fun onPause() {
        container?.onPauseSections()
    }

    /** 释放各分区与内核资源。 */
    override fun onDestroy() {
        container?.releaseSections()
        container = null
    }

    /** 关闭负一屏回到桌面。 */
    private fun closeNegativeScreen() {
        runCatching { AppServices.router.back() }
    }

    internal companion object {
        /** 模块标识。 */
        const val ID: String = "desk"

        /** 模块名称。 */
        const val LABEL: String = "墨台"
    }
}
