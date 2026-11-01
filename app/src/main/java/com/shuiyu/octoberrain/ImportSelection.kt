package com.shuiyu.octoberrain

internal fun <T> orderedImportSelection(
    clips: List<T>,
    data: T?,
): List<T> = (clips + listOfNotNull(data)).distinct()
