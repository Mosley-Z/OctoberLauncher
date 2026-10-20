package com.shuiyu.octoberrain.feature.widgets.kernel

import com.shuiyu.octoberrain.feature.widgets.WereadBoardKernelBridge

/**
 * 内核工厂注册入口：把微读内核装配到整体卡片接缝。
 *
 * 注册只写入一次工厂引用，不读取配置、不装配数据、不初始化网络，
 * 组件在未配置微信读书时同样创建内核并绘制占位形态。
 */
internal object KernelBridgeInstaller {
    /** 注册状态，避免重复写入工厂引用。 */
    @Volatile
    private var installed: Boolean = false

    /**
     * 注册内核工厂，重复调用直接返回。
     *
     * 工厂每次创建新的内核适配实例，组件在构造与销毁之间复用同一实例。
     */
    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            WereadBoardKernelBridge.factory = { _, _ -> KernelWereadBoard() }
            installed = true
        }
    }
}
