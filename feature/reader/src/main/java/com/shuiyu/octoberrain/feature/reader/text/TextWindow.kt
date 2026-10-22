package com.shuiyu.octoberrain.feature.reader.text

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

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
    val count = minOf(64 * 1024L, (end - offset).coerceAtLeast(0)).toInt()
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
    val first = (end - 64 * 1024).coerceAtLeast(start)
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
