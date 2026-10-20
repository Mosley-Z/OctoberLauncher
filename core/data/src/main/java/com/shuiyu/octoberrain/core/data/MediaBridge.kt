package com.shuiyu.octoberrain.core.data

/**
 * 媒体控制命令，由托盘或按键转发给通知监听服务。
 *
 * 服务取得 MediaSession 后调用 transmit 执行真正的播放控制。
 */
enum class MediaCommand {
    TOGGLE,
    PREVIOUS,
    NEXT,
}

/**
 * 媒体命令桥：核心数据层与服务层之间的单向通道。
 *
 * 服务安装处理器后，界面侧调用 command 即可转发；未安装时命令被丢弃。
 */
object MediaBridge {
    private var handler: ((MediaCommand) -> Unit)? = null

    /** 安装处理器，通知监听服务连接时调用。 */
    fun install(handler: ((MediaCommand) -> Unit)?) {
        this.handler = handler
    }

    /** 发送命令。 */
    fun command(command: MediaCommand) {
        handler?.invoke(command)
    }
}
