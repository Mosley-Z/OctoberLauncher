package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.common.sortByRecentRead

/** Full candidate metadata is ordered before limiting; local assets match the widget's recent-read source. */
internal data class WereadShelfCandidate(
    val item: WereadShelfItem,
    val sortKey: Long,
)

internal fun selectWereadShelf(
    candidates: List<WereadShelfCandidate>,
    localTimes: Map<String, Long>,
    limit: Int,
): List<WereadShelfItem> {
    val times = candidates.associate { it.item.bookId to maxOf(it.sortKey, localTimes[it.item.bookId] ?: 0L) }
    return sortByRecentRead(candidates, { it.item.bookId }, times).take(limit).map { it.item }
}
