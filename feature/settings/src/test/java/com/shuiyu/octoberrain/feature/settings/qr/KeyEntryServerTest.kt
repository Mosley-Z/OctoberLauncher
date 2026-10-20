package com.shuiyu.octoberrain.feature.settings.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 校验局域网密钥录入服务的请求路由与令牌校验。
 *
 * 服务绑定本机地址，测试直接通过返回的地址发起 HTTP 请求；
 * 通过关闭弹窗与服务回收避免测试进程残留端口占用。
 */
class KeyEntryServerTest {
    /** 记录收到的密钥，供提交用例断言。 */
    private val received = AtomicReference<String?>(null)

    /** 收到合法密钥时计数。 */
    private val latch = CountDownLatch(1)

    /** 当前被测试服务。 */
    private var server: KeyEntryServer? = null

    /** 启动服务，无局域网地址时跳过用例。 */
    private fun startServer(): String {
        val entry =
            KeyEntryServer().also { server = it }
        val address =
            entry.start(
                object : KeyEntryServer.Listener {
                    override fun onKey(key: String) {
                        received.set(key)
                        latch.countDown()
                    }

                    override fun onClosed(reason: String) = Unit
                },
            )
        assumeTrue("本机无可用局域网地址，跳过", address != null)
        return address!!
    }

    /** 结束用例后关闭服务。 */
    private fun stopServer() {
        server?.stop()
        server = null
    }

    /** 缺少令牌的请求返回 403。 */
    @Test
    fun `令牌不匹配时拒绝访问`() {
        val address = startServer()
        try {
            val url = URL(address.substringBefore("?t="))
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            assertEquals(403, connection.responseCode)
            connection.disconnect()
        } finally {
            stopServer()
        }
    }

    /** 带令牌的 GET 请求返回录入表单。 */
    @Test
    fun `带令牌的页面包含表单`() {
        val address = startServer()
        try {
            val body = readBody(URL(address), "GET", null)
            assertTrue("缺少表单元素", body.contains("<form"))
            assertTrue("缺少密钥输入框", body.contains("name=\"key\""))
        } finally {
            stopServer()
        }
    }

    /** 提交合法密钥触发回调并关闭服务。 */
    @Test
    fun `提交合法密钥后回调`() {
        val address = startServer()
        try {
            val body = readBody(URL(address), "POST", "key=wrk-abcdefgh12345678")
            assertTrue("缺少成功提示", body.contains("密钥已接收"))
            assertTrue("未收到回调", latch.await(5, TimeUnit.SECONDS))
            assertEquals("wrk-abcdefgh12345678", received.get())
        } finally {
            stopServer()
        }
    }

    /** 提交非法密钥返回表单与错误说明。 */
    @Test
    fun `提交非法密钥返回错误提示`() {
        val address = startServer()
        try {
            val body = readBody(URL(address), "POST", "key=wrong")
            assertTrue("缺少错误提示", body.contains("提交未通过"))
            assertTrue("不应触发回调", received.get() == null)
        } finally {
            stopServer()
        }
    }

    /** 发起请求并读取响应正文。 */
    private fun readBody(
        url: URL,
        method: String,
        body: String?,
    ): String {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 5000
        connection.requestMethod = method
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val stream = BufferedInputStream(connection.inputStream)
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        connection.disconnect()
        return text
    }
}
