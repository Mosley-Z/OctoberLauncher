package com.shuiyu.octoberrain.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 同进程无障碍服务的通知面板入口；仅由主线程连接、调用和断开。 */
object NotificationPanelAccess {
    private var openAction: (() -> Boolean)? = null
    private val connected = MutableStateFlow(false)

    /** 只有服务实际连接且可接受请求时才为 true。 */
    val available: StateFlow<Boolean> = connected.asStateFlow()

    /** 由现有无障碍服务连接时提供系统动作。 */
    fun connect(action: () -> Boolean) {
        openAction = action
        connected.value = true
    }

    /** 断开后宿主恢复系统栏，避免留下没有通知入口的全屏窗口。 */
    fun disconnect() {
        openAction = null
        connected.value = false
    }

    /** 系统拒绝或服务异常时同样撤销可用状态，等待实际重新连接。 */
    fun open(): Boolean {
        val accepted = runCatching { openAction?.invoke() == true }.getOrDefault(false)
        if (!accepted) disconnect()
        return accepted
    }
}
