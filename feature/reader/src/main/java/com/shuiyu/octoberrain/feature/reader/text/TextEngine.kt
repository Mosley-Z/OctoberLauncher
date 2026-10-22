package com.shuiyu.octoberrain.feature.reader.text

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.feature.reader.ReaderEngine
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.search.SearchHit
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.search.scanTextFile
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.charset.Charset

/** 原生文本分页只保留一个64KiB窗口，位置使用字节锚点，不因字号变化丢失进度。 */
internal class TextEngine(
    private val handle: DocumentHandle,
    private val width: Int,
    private val height: Int,
    fontPx: Float,
    private val encoding: String? = null,
) : ReaderEngine,
    SearchProvider {
    private val channel = handle.openChannel()

    /** 稀疏块索引只用于定位与字符边界对齐，页面读取仍走固定64KiB窗口。 */
    private val index = SparseTextIndex(channel)
    private val paint =
        TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = fontPx
            typeface = DesignRuntime.typeface()
        }
    private var charset = Charsets.UTF_8
    private var start = 0L
    private var offset = 0L
    private var next = 0L
    private var current: ReaderFrame? = null
    private val history = ArrayDeque<Long>()
    override val capabilities = ReaderCapabilities(search = true, reflow = true, bookmark = true)

    private var lastQuery: String? = null
    private var lastMatch: Long? = null

    /** 当前查询词；渲染每页时重算页内区间，用于在翻页与重排后继续显示高亮。 */
    private var highlightQuery: String? = null

    override suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan = handle.openChannel().use { SearchScan(page = scanTextFile(it, start, charset, query, offset)) }

    override suspend fun showHit(
        hit: SearchHit,
        query: String,
    ): ReaderFrame {
        highlightQuery = query
        history.clear()
        return page(hit.locator)
    }

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val prefix = java.nio.ByteBuffer.allocate(3)
        channel.position(0)
        channel.read(prefix)
        val bytes = prefix.array().map { it.toInt() and 0xff }
        val detected =
            when {
                bytes.take(2) == listOf(255, 254) -> "UTF-16LE" to 2L
                bytes.take(2) == listOf(254, 255) -> "UTF-16BE" to 2L
                bytes == listOf(239, 187, 191) -> "UTF-8" to 3L
                else -> "UTF-8" to 0L
            }
        val saved = locator as? ReaderLocator.Text
        charset = Charset.forName(encoding ?: saved?.charset ?: detected.first)
        start = detected.second
        val restore = if (encoding == null || encoding == saved?.charset) saved?.byteOffset else null
        val requested = (restore ?: start).coerceIn(start, channel.size().coerceAtLeast(start))
        // 恢复的锚点可能来自旧编码或旧版本文档，先对齐到字符起点再分页。
        val anchor = if (restore == null) requested else index.alignToCharBoundary(requested, charset)
        return render(anchor)
    }

    override suspend fun turn(delta: Int): ReaderFrame =
        if (delta > 0 && next > offset && next < channel.size()) {
            if (history.size >= 64) history.removeFirst()
            history.addLast(offset)
            render(next)
        } else if (delta < 0 && offset > start) {
            val previous = if (history.isNotEmpty()) history.removeLast() else previousOffset()
            render(previous)
        } else {
            checkNotNull(current)
        }

    override suspend fun findNext(query: String): ReaderFrame? {
        val context = currentCoroutineContext()
        val old = lastMatch
        val from =
            if (query == lastQuery && old == offset) {
                val first = readTextWindow(channel, offset, charset).text
                val count = if (first.isEmpty()) 0 else Character.charCount(first.codePointAt(0))
                offset + first.take(count).toByteArray(charset).size
            } else {
                offset
            }
        val found = findText(channel, from, charset, query) { context.ensureActive() } ?: return null
        context.ensureActive()
        lastQuery = query
        lastMatch = found
        highlightQuery = query
        history.clear()
        return render(index.alignToCharBoundary(found, charset))
    }

    private fun previousOffset(): Long {
        val window = previousTextWindow(channel, offset, charset, start)
        val full = layout(window.text)
        val bottom = full.getLineBottom(full.lineCount - 1)
        var first = full.lineCount - 1
        while (first > 0 && bottom - full.getLineTop(first - 1) <= height) first--
        return window.offset +
            window.text
                .substring(0, full.getLineStart(first))
                .toByteArray(charset)
                .size
    }

    private fun render(anchor: Long): ReaderFrame {
        offset = anchor
        val window = readTextWindow(channel, anchor, charset)
        val full = layout(window.text)
        var lines = 1
        while (lines < full.lineCount && full.getLineBottom(lines) <= height) lines++
        val end = full.getLineEnd(lines - 1).coerceAtLeast(0)
        val text = window.text.substring(0, end)
        next = anchor + text.toByteArray(charset).size
        val percent = if (channel.size() == 0L) 100 else (anchor * 100L / channel.size()).toInt()
        // 高亮只作用于当前页文本；翻页与重排后按新页重算，不保留跨页 span。
        val query = highlightQuery
        val pageRanges = if (query.isNullOrEmpty()) emptyList() else findTextRanges(text, query)
        return ReaderFrame(
            ReaderLocator.Text(anchor, charset.name()),
            "$percent% · ${charset.name()}",
            layout(if (pageRanges.isEmpty()) text else highlightRanges(text, pageRanges)),
        ).also {
            current =
                it
        }
    }

    private fun layout(text: CharSequence): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.25f)
            .build()

    override fun close() {
        current = null
        highlightQuery = null
        lastQuery = null
        lastMatch = null
        history.clear()
        channel.close()
    }
}
