package com.shuiyu.octoberrain.feature.reader.search

import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.text.readTextWindow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset

/** 内存文本的匹配顺序和高亮坐标一致；按字符推进1，支持重叠命中。 */
internal suspend fun collectMatches(
    text: String,
    query: String,
    collector: SearchCollector,
    locator: (Int) -> ReaderLocator,
) {
    require(query.length in 1..256)
    val context = currentCoroutineContext()
    var match = text.indexOf(query)
    while (match >= 0) {
        context.ensureActive()
        collector.addText(text, match, locator)
        match = text.indexOf(query, match + 1)
    }
}

/** 64KiB解码窗口，保留词长尾部；计数时不构造整文件或所有匹配列表。 */
internal suspend fun scanTextFile(
    channel: SeekableByteChannel,
    start: Long,
    charset: Charset,
    query: String,
    offset: Int,
): SearchPage {
    require(query.length in 1..256)
    val context = currentCoroutineContext()
    val collector = SearchCollector(offset)
    var position = start
    var tail = ""
    while (position < channel.size()) {
        context.ensureActive()
        val window = readTextWindow(channel, position, charset).text
        if (window.isEmpty()) break
        val joined = tail + window
        collectWindow(
            joined,
            query,
            position - tail.toByteArray(charset).size,
            charset,
            collector,
            tail.length,
        ) { context.ensureActive() }
        var keep = (joined.length - query.length + 1).coerceAtLeast(0)
        if (keep in 1 until joined.length && Character.isLowSurrogate(joined[keep])) keep--
        tail = joined.substring(keep)
        position += window.toByteArray(charset).size
    }
    return collector.page()
}

private fun collectWindow(
    text: String,
    query: String,
    base: Long,
    charset: Charset,
    collector: SearchCollector,
    tailLength: Int,
    checkpoint: () -> Unit,
) {
    var match = text.indexOf(query)
    val locator: (Int) -> ReaderLocator = {
        ReaderLocator.Text(base + text.substring(0, it).toByteArray(charset).size, charset.name())
    }
    while (match >= 0) {
        checkpoint()
        if (match + query.length > tailLength) collector.addText(text, match, locator)
        match = text.indexOf(query, match + 1)
    }
}
