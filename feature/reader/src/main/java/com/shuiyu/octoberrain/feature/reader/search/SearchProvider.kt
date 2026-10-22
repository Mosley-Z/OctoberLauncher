package com.shuiyu.octoberrain.feature.reader.search

import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderEngine
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.web.WebRequest

/** 查询不改变阅读位置；最多返回64条定位，完整计数无需保留全部命中。 */
internal data class SearchHit(
    val locator: ReaderLocator,
    val snippet: String,
)

internal data class SearchPage(
    val offset: Int,
    val total: Int,
    val hits: List<SearchHit>,
)

/** Web引擎在主线程扫描自己的受限内容；原生引擎在IO完成page。 */
internal data class SearchScan(
    val page: SearchPage? = null,
    val web: WebRequest? = null,
)

internal interface SearchProvider : ReaderEngine {
    suspend fun scan(
        query: String,
        offset: Int,
    ): SearchScan

    suspend fun showHit(
        hit: SearchHit,
        query: String,
    ): ReaderFrame {
        val target = page(hit.locator)
        return target.web?.let { target.copy(web = it.copy(kind = "hit", query = query)) } ?: target
    }
}

/** 按自然顺序计数，只保留请求页；文本按匹配位置，表格按匹配单元格计数。 */
internal class SearchCollector(
    val offset: Int,
) {
    private val hits = mutableListOf<SearchHit>()
    private var total = 0

    init {
        require(offset >= 0)
    }

    fun add(
        locator: ReaderLocator,
        snippet: String,
    ) {
        if (total >= offset && hits.size < PAGE_SIZE) hits += SearchHit(locator, snippet.take(160))
        total++
    }

    fun addText(
        text: String,
        anchor: Int,
        locator: (Int) -> ReaderLocator,
    ) {
        if (total >= offset && hits.size < PAGE_SIZE) {
            hits += SearchHit(locator(anchor), text.substring(anchor, minOf(text.length, anchor + 160)))
        }
        total++
    }

    fun page(): SearchPage = SearchPage(offset, total, hits.toList())

    companion object {
        const val PAGE_SIZE = 64
    }
}
