package com.shuiyu.octoberrain.feature.reader.archive

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipException

/** Bound the central directory BEFORE java.util.zip allocates an entry/name index. */
internal object ZipPreflight {
    const val MAX_ENTRIES = 20_000
    const val MAX_DIRECTORY_BYTES = 4L * 1024 * 1024

    fun check(file: File) {
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            if (length < 22) throw ZipException("Missing ZIP directory")
            val tail = ByteArray(minOf(length, 65_557L).toInt())
            input.seek(length - tail.size)
            input.readFully(tail)
            val offset =
                (tail.size - 22 downTo 0).firstOrNull { index ->
                    number(tail, index, 4) == 0x06054b50L &&
                        index + 22 + number(tail, index + 20, 2) == tail.size.toLong()
                } ?: throw ZipException("Missing ZIP directory")
            validate(tail, offset, length - tail.size + offset)
        }
    }

    private fun validate(
        bytes: ByteArray,
        offset: Int,
        end: Long,
    ) {
        val disk = number(bytes, offset + 4, 2)
        val directoryDisk = number(bytes, offset + 6, 2)
        val diskCount = number(bytes, offset + 8, 2)
        val count = number(bytes, offset + 10, 2)
        val size = number(bytes, offset + 12, 4)
        val start = number(bytes, offset + 16, 4)
        val multiVolume = disk != 0L || directoryDisk != 0L || diskCount != count
        val zip64 = count == 65535L || size == 0xffffffffL || start == 0xffffffffL
        if (multiVolume || zip64) throw ZipException("Unsupported ZIP64 or multi-volume archive")
        checkBudget(count, size)
        if (start + size > end) throw ZipException("Invalid ZIP directory range")
    }

    private fun checkBudget(
        count: Long,
        size: Long,
    ) {
        if (count > MAX_ENTRIES || size > MAX_DIRECTORY_BYTES) throw ZipException("ZIP index budget exceeded")
    }

    private fun number(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Long =
        (0 until length).fold(0L) { result, index ->
            result or ((bytes[offset + index].toLong() and 255) shl (index * 8))
        }
}
