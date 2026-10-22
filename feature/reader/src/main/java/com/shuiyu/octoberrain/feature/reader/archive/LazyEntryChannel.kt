package com.shuiyu.octoberrain.feature.reader.archive

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import java.util.zip.ZipException

/** Forward inflation on demand; backward paging uses the already inflated prefix, not a second unzip. */
@Suppress("TooManyFunctions") // SeekableByteChannel requires these methods; inflation remains a single owned channel.
internal class LazyEntryChannel(
    private val input: InputStream,
    private val file: File,
    private val length: Long,
    private val reserve: (Int) -> Unit = {},
) : SeekableByteChannel {
    private val cache = RandomAccessFile(file, "rw")
    private var opened = true
    private var position = 0L
    private var available = 0L
    private var verified = false

    init {
        require(length in 0..ZipArchive.MAX_ENTRY_BYTES)
    }

    override fun read(destination: ByteBuffer): Int {
        checkOpen()
        return if (!destination.hasRemaining()) 0 else readContent(destination)
    }

    private fun readContent(destination: ByteBuffer): Int {
        val count = minOf(destination.remaining().toLong(), length - position).coerceAtLeast(0).toInt()
        inflateTo(minOf(position + count, length))
        if (count == 0) return -1
        val bytes = ByteArray(minOf(count, 32 * 1024))
        var remaining = count
        cache.seek(position)
        while (remaining > 0) {
            val size = minOf(remaining, bytes.size)
            cache.readFully(bytes, 0, size)
            destination.put(bytes, 0, size)
            remaining -= size
        }
        position += count
        return count
    }

    private fun inflateTo(target: Long) {
        val bytes = ByteArray(32 * 1024)
        cache.seek(available)
        while (available < target) {
            val count = input.read(bytes, 0, minOf(target - available, bytes.size.toLong()).toInt())
            if (count < 0) throw ZipException("Truncated ZIP entry")
            if (count == 0) continue
            reserve(count)
            cache.write(bytes, 0, count)
            available += count
        }
        if (available == length && !verified) {
            if (input.read() != -1) throw ZipException("ZIP entry size mismatch")
            verified = true
        }
    }

    override fun position(): Long {
        checkOpen()
        return position
    }

    override fun position(value: Long): SeekableByteChannel {
        checkOpen()
        require(value >= 0)
        position = value
        return this
    }

    override fun size(): Long {
        checkOpen()
        return length
    }

    override fun write(source: ByteBuffer): Int = throw NonWritableChannelException()

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = opened

    override fun close() {
        if (!opened) return
        opened = false
        try {
            input.close()
        } finally {
            try {
                cache.close()
            } finally {
                file.delete()
            }
        }
    }

    private fun checkOpen() {
        if (!opened) throw ClosedChannelException()
    }
}
