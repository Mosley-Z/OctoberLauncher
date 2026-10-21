package com.shuiyu.octoberrain.core.common

/**
 * 独立数据同步入口。
 *
 * 应用层把网关同步服务装配到 [AppServices.wereadSync]，设置页据此显示同步状态、
 * 触发一次手动同步；负一屏与整体卡片在独立模式下发现本地数据为空时也会调用
 * [syncNow] 补齐数据。同步只写本地数据目录，不改变界面，界面由内容刷新入口重绘。
 */
interface WereadSyncHost {
    /** 当前是否正在同步，重入调用返回 false。 */
    fun isRunning(): Boolean

    /** 最近一次同步的状态文案，含结果与时刻。 */
    fun statusText(): String

    /**
     * 触发一次同步。
     *
     * @param onFinished 结束回调，第一个参数为成功与否，第二个参数为状态文案
     * @return 已受理返回 true，密钥缺失或同步进行中返回 false
     */
    fun syncNow(onFinished: (Boolean, String) -> Unit): Boolean

    /** 按当前阅读形态强制取远端数据，并补拉用户开启的绑定目标。 */
    fun refresh(
        request: WereadRefreshRequest,
        onFinished: (Boolean, String) -> Unit,
    ): Boolean = syncNow(onFinished)
}

/** 卡片刷新意图；起点为 0 表示当前期，书目为空表示读取共享选书偏好。 */
data class WereadRefreshRequest(
    val mode: String,
    val baseTimeSec: Long = 0L,
    val bookId: String = "",
)
