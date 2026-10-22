package com.shuiyu.octoberrain.feature.reader.text

import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset

/** 跨窗口匹配只保留查询长度的尾部；每个窗口检查取消，不保留全文或结果全集。 */
internal fun findText(
    channel: SeekableByteChannel,
    from: Long,
    charset: Charset,
    query: String,
    checkpoint: () -> Unit,
): Long? {
    require(query.isNotEmpty() && query.length <= 256)
    var offset = from
    var tail = ""
    while (offset < channel.size()) {
        checkpoint()
        val window = readTextWindow(channel, offset, charset)
        if (window.text.isEmpty()) break
        val joined = tail + window.text
        val base = offset - tail.toByteArray(charset).size
        val match = joined.indexOf(query)
        if (match >= 0) return base + joined.substring(0, match).toByteArray(charset).size
        var keep = (joined.length - query.length + 1).coerceAtLeast(0)
        if (keep > 0 && keep < joined.length && Character.isLowSurrogate(joined[keep])) keep--
        tail = joined.substring(keep)
        offset += window.text.toByteArray(charset).size
    }
    checkpoint()
    return null
}
