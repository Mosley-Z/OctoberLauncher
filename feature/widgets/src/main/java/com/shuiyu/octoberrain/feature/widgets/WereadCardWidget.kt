package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.Destination

/**
 * 微信读书卡片组件的公共基类。
 *
 * 统一解析三态、绘制不可用文案并在可读状态下响应点击跳转。子类只需实现数据加载
 * 与有数据时的绘制，几何全部由传入宽高与设计令牌计算。
 */
abstract class WereadCardWidget(
    context: Context,
) : InkWidgetView(context) {
    /** 最近一次加载解析出的组件状态。 */
    protected var state: WereadWidgetState = WereadWidgetState.EMPTY
        private set

    /**
     * 依据当前配置与数据可用性更新三态。
     *
     * 参数 hasData 表示本次读取是否得到可展示的数据；返回值同时写入 [state]。
     */
    protected fun resolveState(hasData: Boolean): WereadWidgetState {
        val integrations = config.integrations
        val resolved =
            WereadWidgetLogic.stateOf(
                key = integrations.wereadApiKey,
                enabled = integrations.wereadEnabled,
                shelfEnabled = integrations.bookshelfWereadEnabled,
                hasData = hasData,
            )
        state = resolved
        return resolved
    }

    /**
     * 在数据不可用时绘制提示文案。
     *
     * 参数 width 与 height 为组件可用宽高像素。返回 true 表示已处理，调用方应结束绘制。
     */
    protected fun drawUnavailableIfNeeded(
        canvas: Canvas,
        width: Int,
        height: Int,
    ): Boolean {
        if (state == WereadWidgetState.READY) return false
        val message = WereadWidgetLogic.messageOf(state)
        drawEmptyState(canvas, contentArea(width, height), message)
        return true
    }

    /** 依据组件宽高与令牌计算内容区，四周留出统一内边距。 */
    protected fun contentArea(
        width: Int,
        height: Int,
    ) = RectF(
        padX,
        pad,
        width - padX,
        height - pad,
    )

    override fun onClick(
        x: Float,
        y: Float,
    ): Boolean {
        if (state == WereadWidgetState.READY) navigateTo(Destination.Weread)
        return true
    }
}
