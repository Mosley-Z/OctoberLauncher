package com.shuiyu.octoberrain.feature.files.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/** 流式比对与复制使用的缓冲大小，8 KiB 在存储页对齐与内存占用之间取折中。 */
private const val BUFFER_SIZE = 8 * 1024

/**
 * 文件内容比对与流式写入工具。
 *
 * 供 [FileOps] 的复制与覆盖流程调用。比对与复制均按固定大小缓冲逐块进行，
 * 不把整个文件读入内存，适合处理相册中的大图。
 */
internal object FileStreams {
    /**
     * 按块比较两个文件内容。
     *
     * 参数 left 与 right 为已确认存在的普通文件。
     * 内容完全相同时返回 true；长度不同、任一方不可读或读取中断时返回 false。
     */
    fun sameContent(
        left: File,
        right: File,
    ): Boolean =
        runCatching {
            FileInputStream(left).use { inputLeft ->
                FileInputStream(right).use { inputRight ->
                    compareStreams(inputLeft, inputRight)
                }
            }
        }.getOrElse {
            // 任一方不可读或读取中断时无法证明内容相同，按不同处理。
            false
        }

    /**
     * 把源文件流式写入目标文件。
     *
     * 参数 src 为源文件；target 为目标文件，存在时覆盖。
     * 写入完整时返回 true；打开、读取或写入过程中出现 IO 异常时返回 false。
     */
    fun write(
        src: File,
        target: File,
    ): Boolean =
        runCatching {
            FileInputStream(src).use { input ->
                FileOutputStream(target).use { output ->
                    copyStream(input, output)
                }
            }
            true
        }.getOrElse {
            false
        }

    /** 逐块比较两个输入流，块长不同或任一字节不同即返回 false。 */
    private fun compareStreams(
        inputLeft: InputStream,
        inputRight: InputStream,
    ): Boolean {
        val bufferLeft = ByteArray(BUFFER_SIZE)
        val bufferRight = ByteArray(BUFFER_SIZE)
        var count = readFully(inputLeft, bufferLeft)
        // 每轮先读右侧同长块并比较长度与字节，两侧同时到达末尾时结束。
        while (count == readFully(inputRight, bufferRight) && sameBytes(bufferLeft, bufferRight, count)) {
            if (count == 0) return true
            count = readFully(inputLeft, bufferLeft)
        }
        return false
    }

    /** 比较两个缓冲前 count 个字节是否完全一致。 */
    private fun sameBytes(
        left: ByteArray,
        right: ByteArray,
        count: Int,
    ): Boolean {
        for (index in 0 until count) {
            if (left[index] != right[index]) return false
        }
        return true
    }

    /** 把输入流内容复制到输出流并刷新。 */
    private fun copyStream(
        input: InputStream,
        output: OutputStream,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) output.write(buffer, 0, read)
        }
        output.flush()
    }

    /** 从流中尽量读满缓冲，返回本次读到的字节数，0 表示已到流末尾。 */
    private fun readFully(
        input: InputStream,
        buffer: ByteArray,
    ): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }
}
