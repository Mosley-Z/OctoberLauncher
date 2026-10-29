package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class WereadCoverDownloadTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `HTTP failure must fail rather than notify ready without a cache`() {
        withResponse(404, byteArrayOf(1)) { url ->
            val target = temporary.root.resolve("missing.img")
            assertThrows(IOException::class.java) { WereadCoverStore.download(url, target) }
            assertFalse(target.exists())
        }
    }

    @Test fun `empty response cannot become a successful cover cache`() {
        withResponse(200, byteArrayOf()) { url ->
            val target = temporary.root.resolve("empty.img")
            assertThrows(IOException::class.java) { WereadCoverStore.download(url, target) }
            assertFalse(target.exists())
            assertFalse(temporary.root.resolve("empty.img.part").exists())
        }
    }

    @Test fun `successful response is committed before returning ready`() {
        val bytes = byteArrayOf(1, 2, 3)
        withResponse(200, bytes) { url ->
            val target = temporary.root.resolve("cover.img")
            WereadCoverStore.download(url, target)
            assertArrayEquals(bytes, target.readBytes())
            assertFalse(temporary.root.resolve("cover.img.part").exists())
        }
    }

    private fun withResponse(
        status: Int,
        bytes: ByteArray,
        block: (String) -> Unit,
    ) {
        val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val worker =
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    socket.getOutputStream().use { output ->
                        output.write(
                            "HTTP/1.1 $status Test\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n\r\n"
                                .toByteArray(),
                        )
                        output.write(bytes)
                    }
                }
            }
        try {
            block("http://127.0.0.1:${server.localPort}/cover")
        } finally {
            server.close()
            worker.join(5000)
        }
    }
}
