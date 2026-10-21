package com.shuiyu.octoberrain.feature.wereadapp.ui.qr

import com.shuiyu.octoberrain.core.common.InkLog
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 局域网密钥录入服务。
 *
 * 服务监听本机随机端口，页面地址携带一次性令牌，令牌不匹配的请求直接拒绝。
 * 存活期间只接受一次合法提交，校验通过后回调 [Listener.onKey] 并自动关闭。
 *
 * 关闭原因通过 [Listener.onClosed] 回调，页面侧据此更新提示文本。
 */
internal class KeyEntryServer {
    /** 服务事件回调。 */
    interface Listener {
        /**
         * 收到格式合法的密钥。
         *
         * @param key 已去除首尾空白的密钥
         */
        fun onKey(key: String)

        /**
         * 服务关闭。
         *
         * @param reason 可直接展示给用户的关闭说明
         */
        fun onClosed(reason: String)
    }

    /** 服务监听套接字，未启动时为空。 */
    private var serverSocket: ServerSocket? = null

    /** 服务线程，未启动时为空。 */
    private var worker: Thread? = null

    /** 一次性令牌，用于限制页面访问。 */
    private var token: String = ""

    /** 服务是否处于运行状态。 */
    private val running = AtomicBoolean(false)

    /** 过期通知是否已发出，避免轮询与请求处理重复回调。 */
    private val expiredNotified = AtomicBoolean(false)

    /** 服务启动时刻，用于计算存活时长。 */
    private var startedAt: Long = 0L

    /**
     * 启动服务并返回扫码地址。
     *
     * @param listener 服务事件回调，在服务线程中调用
     * @return 可编码为二维码的页面地址，无局域网地址或端口不可用时返回 null
     */
    fun start(listener: Listener): String? {
        if (running.get()) return null
        return prepare()?.let { (socket, address) ->
            serverSocket = socket
            running.set(true)
            worker =
                thread(start = true, isDaemon = true, name = "weread-key-entry") {
                    serve(socket, listener)
                }
            address
        }
    }

    /** 关闭服务并释放端口，重复调用无副作用。 */
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /** 准备监听套接字与页面地址，任一步失败返回 null。 */
    private fun prepare(): Pair<ServerSocket, String>? {
        val host = lanAddress()
        val socket = runCatching { ServerSocket(0) }.getOrNull()
        return if (host == null || socket == null) {
            null
        } else {
            token = randomToken()
            startedAt = System.currentTimeMillis()
            socket to "http://$host:${socket.localPort}/k?t=$token"
        }
    }

    /** 读取本机局域网地址，优先返回私有网段地址。 */
    private fun lanAddress(): String? =
        runCatching { NetworkInterface.getNetworkInterfaces().toList() }
            .getOrDefault(emptyList())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { card -> card.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .let { candidates ->
                candidates.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") }
                    ?: candidates.firstOrNull()
            }

    /** 生成一次性令牌，取十六进制文本。 */
    private fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /** 服务主循环，逐条接受连接并处理。 */
    private fun serve(
        socket: ServerSocket,
        listener: Listener,
    ) {
        try {
            while (running.get() && !expired(listener)) {
                val client = accept(socket)
                if (client != null && dispatch(client, listener)) return
            }
        } finally {
            running.set(false)
            stop()
        }
    }

    /** 处理一次连接，收到合法密钥时返回 true。 */
    private fun dispatch(
        client: Socket,
        listener: Listener,
    ): Boolean {
        // 连接可能在上一次轮询之后才到达，处理前再核对一次有效期，
        // 避免超时后的旧链接仍然提交密钥。
        val key =
            if (expired(listener)) {
                runCatching { client.close() }
                null
            } else {
                KeyEntryHandler { token }.handle(client)
            }
        if (key != null) listener.onKey(key)
        return key != null
    }

    /** 判断服务是否超过存活上限，首次超时时通知调用方。 */
    private fun expired(listener: Listener): Boolean {
        val overtime = System.currentTimeMillis() - startedAt > LIFETIME_MS
        if (overtime && expiredNotified.compareAndSet(false, true)) {
            listener.onClosed("页面已过期，请重新生成")
        }
        return overtime
    }

    /**
     * 有界等待一个连接。
     *
     * 监听套接字设置轮询超时，超时返回 null 让主循环重新核对有效期；
     * 套接字已被关闭时同样返回 null，主循环依据 running 标志退出。
     *
     * @param socket 监听套接字
     * @return 已建立的连接，超时或关闭时返回 null
     */
    private fun accept(socket: ServerSocket): Socket? {
        socket.soTimeout = ACCEPT_POLL_MS
        return runCatching { socket.accept() }.getOrElse { error ->
            if (error !is SocketTimeoutException) InkLog.w(TAG, "接受连接失败：${error.message}")
            null
        }
    }

    /** 服务内部常量。 */
    private companion object {
        /** 服务最长存活时间，单位毫秒。 */
        const val LIFETIME_MS = 5 * 60 * 1000L

        /** 接受连接的轮询间隔，单位毫秒，用于在无连接时也能按时过期。 */
        const val ACCEPT_POLL_MS = 1_000

        /** 令牌随机字节数。 */
        const val TOKEN_BYTES = 16

        /** 日志标签。 */
        const val TAG = "KeyEntryServer"
    }
}
