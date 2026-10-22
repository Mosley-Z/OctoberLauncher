package com.shuiyu.octoberrain.feature.reader.text

import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset

/**
 * 一次搜索命中：文件字节位置、命中所在窗口起点与窗口内字符区间。
 *
 * [windowOffset] 为当前读取窗口的起始字节偏移；用 [readTextWindow] 从该偏移
 * 重新解码得到的文本，其 [charStart] 到 [charEnd] 必定等于查询词。
 */
internal data class TextMatch(
    val byteOffset: Long,
    val windowOffset: Long,
    val charStart: Int,
    val charEnd: Int,
)

/** 页面内字符区间，用于按页绘制高亮，不持有全文。 */
internal data class TextRange(
    val start: Int,
    val end: Int,
)

/**
 * 跨窗口查找首个命中。
 *
 * 每个窗口保留查询长度减一的尾部，命中可跨窗口边界；每窗口检查一次取消。
 * 返回的 [TextMatch.byteOffset] 为文件内精确字节偏移，
 * [TextMatch.charStart] 与 [TextMatch.charEnd] 为命中所在窗口文本的字符区间。
 */
internal fun findTextMatch(
    channel: SeekableByteChannel,
    from: Long,
    charset: Charset,
    query: String,
    checkpoint: () -> Unit,
): TextMatch? {
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
        if (match >= 0) {
            // joined 由上一次的尾部与当前窗口拼接，命中位置减去尾部长度即当前窗口内下标。
            val charStart = match - tail.length
            val byteOffset = base + joined.substring(0, match).toByteArray(charset).size
            return TextMatch(byteOffset, offset, charStart, charStart + query.length)
        }
        var keep = (joined.length - query.length + 1).coerceAtLeast(0)
        if (keep > 0 && keep < joined.length && Character.isLowSurrogate(joined[keep])) keep--
        tail = joined.substring(keep)
        offset += window.text.toByteArray(charset).size
    }
    checkpoint()
    return null
}

/** 跨窗口查找首个命中的字节偏移，保留原有调用契约。 */
internal fun findText(
    channel: SeekableByteChannel,
    from: Long,
    charset: Charset,
    query: String,
    checkpoint: () -> Unit,
): Long? = findTextMatch(channel, from, charset, query, checkpoint)?.byteOffset

/**
 * 计算页面文本内查询的全部字符区间（含重叠推进与相邻命中）。
 *
 * 输入为当前页可见文本，输出区间仅覆盖该页，用于绘制页内高亮；
 * 空查询与空文本返回空列表，重复出现全部返回。
 */
internal fun findTextRanges(
    text: String,
    query: String,
): List<TextRange> {
    if (text.isEmpty() || query.isEmpty()) return emptyList()
    val ranges = ArrayList<TextRange>()
    var index = text.indexOf(query)
    while (index >= 0) {
        ranges += TextRange(index, index + query.length)
        index = text.indexOf(query, index + query.length.coerceAtLeast(1))
    }
    return ranges
}
