package com.shuiyu.octoberrain.feature.widgets

/** Stage the entire selection before publishing any configuration. */
internal fun <S, T : Any> importImageBatch(
    sources: List<S>,
    copy: (S) -> T?,
    discard: (T) -> Unit,
): List<T>? {
    val staged = mutableListOf<T>()
    return runCatching {
        sources.forEach { source -> staged.add(copy(source) ?: error("Image import failed")) }
        staged.toList()
    }.getOrElse {
        staged.forEach(discard)
        null
    }
}
