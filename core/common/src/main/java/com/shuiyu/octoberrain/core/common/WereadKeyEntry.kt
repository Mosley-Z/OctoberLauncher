package com.shuiyu.octoberrain.core.common

/**
 * 二维码录密钥服务的会话信息。
 *
 * @property payload 二维码承载的完整内容，形如 http://192.168.1.10:9453/k?t=令牌。
 * @property address 展示给用户的局域网地址，不含一次性令牌。
 * @property expiresAtMillis 会话失效时刻的毫秒时间戳。
 */
data class WereadKeyEntrySession(
    val payload: String,
    val address: String,
    val expiresAtMillis: Long,
)

/**
 * 二维码录密钥服务。
 *
 * 服务在设置页打开期间监听局域网端口，用户在电脑或另一台设备上访问二维码给出的地址，
 * 在网页表单中提交接口密钥，密钥经本机回写配置。服务只接受一次性令牌，
 * 达到尝试次数上限、会话超时或用户关闭设置页时立即停止监听并释放端口。
 *
 * 实现由应用层装配注入 [AppServices.wereadKeyEntry]，未装配时设置页隐藏入口。
 */
interface WereadApiKeyEntry {
    /** 会话关闭原因。 */
    enum class CloseReason {
        /** 会话超出有效时长。 */
        EXPIRED,

        /** 连续提交失败次数达到上限。 */
        ATTEMPTS_EXCEEDED,

        /** 用户主动停止或页面关闭。 */
        STOPPED,
    }

    /** 会话事件监听。回调在服务线程上执行，实现方负责切换线程更新界面。 */
    interface Listener {
        /**
         * 收到格式合法的密钥时回调。
         *
         * @param apiKey 用户提交的接口密钥，已去除首尾空白。
         */
        fun onAccepted(apiKey: String)

        /**
         * 会话结束时回调。
         *
         * @param reason 会话关闭原因。
         */
        fun onClosed(reason: CloseReason)
    }

    /** 当前是否在监听。 */
    val isRunning: Boolean

    /**
     * 启动监听。
     *
     * @param listener 会话事件监听。
     * @return 启动成功时返回会话信息，端口占用或重复启动时返回 null。
     */
    fun start(listener: Listener): WereadKeyEntrySession?

    /** 停止监听并释放端口，重复调用无副作用。 */
    fun stop()
}
