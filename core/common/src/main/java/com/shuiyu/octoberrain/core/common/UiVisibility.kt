package com.shuiyu.octoberrain.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 宿主生命周期与屏幕广播的交集；视图计时和自动联网共用，悬浮栏只依赖亮屏状态。 */
object UiVisibility {
    private var foreground = false
    private val screen = MutableStateFlow(false)
    private val visible = MutableStateFlow(false)
    val interactive = screen.asStateFlow()
    val active = visible.asStateFlow()

    fun setForeground(value: Boolean) {
        foreground = value
        visible.value = foreground && screen.value
    }

    fun setInteractive(value: Boolean) {
        screen.value = value
        visible.value = foreground && value
    }
}
