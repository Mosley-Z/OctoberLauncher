package com.shuiyu.octoberrain.feature.reader.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer

class LazyEntryChannelTest {
    @Test fun firstWindowDoesNotInflateWholeEntryAndBackwardReadReusesPrefix() {
        val data = ByteArray(8 * 1024 * 1024) { (it % 251).toByte() }
        val source = ByteArrayInputStream(data)
        val file = File.createTempFile("reader-lazy-", ".cache")
        LazyEntryChannel(source, file, data.size.toLong()).use { channel ->
            val window = ByteBuffer.allocate(64 * 1024)
            assertEquals(window.capacity(), channel.read(window))
            assertEquals(window.capacity().toLong(), file.length())
            assertEquals(data.size - window.capacity(), source.available())
            channel.position(10)
            val byte = ByteBuffer.allocate(1)
            channel.read(byte)
            assertEquals(data[10], byte.array()[0])
            assertEquals(data.size - window.capacity(), source.available())
        }
        assertFalse(file.exists())
    }
}
