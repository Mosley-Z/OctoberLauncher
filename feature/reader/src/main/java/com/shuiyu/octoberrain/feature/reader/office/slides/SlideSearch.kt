package com.shuiyu.octoberrain.feature.reader.office.slides

import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.search.SearchCollector
import com.shuiyu.octoberrain.feature.reader.search.SearchPage
import com.shuiyu.octoberrain.feature.reader.search.collectMatches
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object SlideSearch {
    suspend fun scan(
        texts: List<String>,
        outline: Boolean,
        query: String,
        offset: Int,
    ): SearchPage {
        val collector = SearchCollector(offset)
        val context = currentCoroutineContext()
        texts.forEachIndexed { slide, text ->
            context.ensureActive()
            collectMatches(text, query, collector) { ReaderLocator.Slide(slide, outline, it) }
        }
        return collector.page()
    }

    fun ordinal(
        text: String,
        query: String,
        target: Int,
    ): Int {
        require(query.isNotEmpty())
        var match = text.indexOf(query)
        var ordinal = 0
        while (match >= 0 && match < target) {
            ordinal++
            match = text.indexOf(query, match + 1)
        }
        return if (match == target) ordinal else -1
    }
}
