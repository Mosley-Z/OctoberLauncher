package com.shuiyu.octoberrain.feature.reader.text

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** 单页读取的固定窗口字节数。 */
internal const val TEXT_WINDOW_BYTES = 64 * 1024

/** 稀疏索引的默认块字节数。 */
internal const val TEXT_CHUNK_BYTES = 256 * 1024

/** 字符边界探测最多向后尝试的字节数，覆盖 UTF-8 三字节与 UTF-16 代理对。 */
private const val TEXT_BOUNDARY_PROBE_BYTES = 4

/** 字符边界探测读取的样本字节数，仅用于判别首字节是否落在字符起点。 */
private const val TEXT_BOUNDARY_SAMPLE_BYTES = 8

internal data class TextWindow(
    val offset: Long,
    val text: String,
)

/** 固定64KiB窗口；解码器保留尾部未完成字符，下页从真实已消费字节继续。 */
internal fun readTextWindow(
    channel: SeekableByteChannel,
    offset: Long,
    charset: Charset,
    end: Long = channel.size(),
): TextWindow {
    val count = minOf(TEXT_WINDOW_BYTES.toLong(), (end - offset).coerceAtLeast(0)).toInt()
    val bytes = ByteBuffer.allocate(count)
    channel.position(offset)
    while (bytes.hasRemaining() && channel.read(bytes) >= 0) { /* 固定窗口，永不读完整文件。 */ }
    bytes.flip()
    val chars = CharBuffer.allocate(count + 2)
    val decoder =
        charset
            .newDecoder()
            .onMalformedInput(
                CodingErrorAction.REPORT,
            ).onUnmappableCharacter(CodingErrorAction.REPORT)
    val result = decoder.decode(bytes, chars, offset + bytes.limit() >= end)
    if (result.isError) result.throwException()
    chars.flip()
    return TextWindow(offset, chars.toString())
}

/** 反向定位只读前一窗口；尝试跳过最多3个窗口首部碎片，不从文件起点重扫。 */
internal fun previousTextWindow(
    channel: SeekableByteChannel,
    end: Long,
    charset: Charset,
    start: Long,
): TextWindow {
    val first = (end - TEXT_WINDOW_BYTES).coerceAtLeast(start)
    var error: Exception? = null
    for (skip in 0..3) {
        val offset = first + skip
        if (offset > end) break
        try {
            return readTextWindow(channel, offset, charset, end)
        } catch (failure: java.nio.charset.CharacterCodingException) {
            error = failure
        }
    }
    throw error ?: java.io.IOException("无法定位前一页")
}

/**
 * 稀疏块索引。
 *
 * 以 [chunkSize] 字节为块粒度建立块起始字节偏移表，表项数量等于文件块数，
 * 50MiB 文件约 200 项，256KiB 块内不再记录更细的偏移。块表由块数算术生成，
 * 建立与查询过程都不读取文件内容，也不从文件起点顺序扫描。
 *
 * 索引只承担定位与字符边界对齐，页面读取仍沿用固定 64KiB 窗口与字节锚点，
 * 因此重排后的页码、锚点与既有翻页语义保持不变。
 */
internal class SparseTextIndex(
    private val channel: SeekableByteChannel,
    val chunkSize: Int = TEXT_CHUNK_BYTES,
) {
    init {
        require(chunkSize > 0) { "块大小必须为正数" }
    }

    /** 建立索引时记录的文件字节数；索引生命周期内文件大小视为不变。 */
    val size: Long = channel.size()

    /** 块数量；空文件为 0，非空文件按向上取整计算。 */
    val chunkCount: Int
        get() = if (size <= 0L) 0 else (((size - 1) / chunkSize) + 1).toInt()

    /** 返回 [byteOffset] 所在块的序号，文件尾归属最后一块。 */
    fun chunkIndexOf(byteOffset: Long): Int {
        if (size <= 0L) return 0
        val limit = (size - 1).coerceAtLeast(0L)
        return (byteOffset.coerceIn(0L, limit) / chunkSize).toInt()
    }

    /** 返回 [byteOffset] 所在块的起始字节偏移，直接由块号计算，不读取文件。 */
    fun locate(byteOffset: Long): Long = chunkIndexOf(byteOffset).toLong() * chunkSize

    /** 返回全部块起始字节偏移；50MiB 文件为 200 项。 */
    fun chunkOffsets(): List<Long> = (0 until chunkCount).map { it.toLong() * chunkSize }

    /**
     * 将字节偏移对齐到字符起点。
     *
     * UTF-16 先按偶数偏移对齐，再用解码探针排除孤立代理项；UTF-8 由续字节判定；
     * GB18030 的尾字节可能本身是合法单字节字符，只能识别解码报错的错位，
     * 无法在本地判定所有边界，此时沿用未对齐偏移并由解码器暴露错误。
     */
    fun alignToCharBoundary(
        byteOffset: Long,
        charset: Charset,
    ): Long {
        val clipped = byteOffset.coerceIn(0L, size.coerceAtLeast(0L))
        val candidate = charStartAtOrBefore(clipped, charset)
        return when {
            size <= 0L -> 0L
            clipped >= size -> size
            candidate != null -> candidate
            else -> clipped
        }
    }

    /** 在探针预算内从 [byteOffset] 起寻找字符起点，找不到时返回 null。 */
    private fun charStartAtOrBefore(
        byteOffset: Long,
        charset: Charset,
    ): Long? {
        var candidate = parityAligned(byteOffset, charset)
        var attempts = 0
        while (candidate < size && attempts <= TEXT_BOUNDARY_PROBE_BYTES) {
            if (isCharStart(candidate, charset)) return candidate
            candidate = parityAligned(candidate + 1, charset)
            attempts++
        }
        return null
    }

    /**
     * 按字节偏移直接定位所在块的起始窗口。
     *
     * 返回块首的固定 64KiB 窗口（文件尾为短块），供块级读取与边界确认使用；
     * 页面锚点读取仍由 [readTextWindow] 按原始锚点执行。
     */
    fun seek(
        byteOffset: Long,
        charset: Charset,
    ): TextWindow {
        val blockStart = locate(byteOffset)
        val aligned = alignToCharBoundary(blockStart, charset)
        return readTextWindow(channel, aligned, charset)
    }

    /** UTF-16 字符起点位于偶数偏移；其余编码不做奇偶调整。 */
    private fun parityAligned(
        byteOffset: Long,
        charset: Charset,
    ): Long {
        if (!isUtf16(charset)) return byteOffset
        return if (byteOffset % 2L == 0L) byteOffset else (byteOffset + 1).coerceAtMost(size)
    }

    /** 读取样本字节并以报错模式解码，判断 [offset] 是否落在字符起点。 */
    private fun isCharStart(
        offset: Long,
        charset: Charset,
    ): Boolean {
        val sample = ByteBuffer.allocate(TEXT_BOUNDARY_SAMPLE_BYTES)
        channel.position(offset)
        while (sample.hasRemaining() && channel.read(sample) > 0) { /* 最多8字节探针。 */ }
        sample.flip()
        if (!sample.hasRemaining()) return true
        val chars = CharBuffer.allocate(sample.limit())
        val decoder =
            charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(sample, chars, offset + sample.limit() >= size)
        return !result.isError
    }

    private fun isUtf16(charset: Charset): Boolean = charset.name().uppercase(Locale.ROOT).startsWith("UTF-16")
}

/** 按字节偏移直接读取所在块首窗口，是 [SparseTextIndex.seek] 的便捷入口。 */
internal fun seekTextWindow(
    channel: SeekableByteChannel,
    byteOffset: Long,
    charset: Charset,
    chunkSize: Int = TEXT_CHUNK_BYTES,
): TextWindow = SparseTextIndex(channel, chunkSize).seek(byteOffset, charset)
