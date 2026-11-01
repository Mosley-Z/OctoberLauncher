package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.LauncherEntry

internal object FavoriteEntryResolver {
    fun resolve(
        key: String,
        entries: List<LauncherEntry>,
    ): LauncherEntry? =
        entries.firstOrNull { it.key == key }
            ?: EntryKeys.legacyPackageOf(key)?.let { packageName ->
                entries.singleOrNull { it.packageName == packageName }
            }
}
