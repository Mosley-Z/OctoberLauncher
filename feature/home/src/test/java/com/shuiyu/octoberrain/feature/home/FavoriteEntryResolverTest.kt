package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.LauncherEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteEntryResolverTest {
    @Test
    fun legacyFavoriteRequiresUniqueCandidateButExactAliasStillWorks() {
        val first = LauncherEntry("app:p/A", EntryType.APP, "A", packageName = "p")
        val second = LauncherEntry("app:p/B", EntryType.APP, "B", packageName = "p")
        assertEquals(first, FavoriteEntryResolver.resolve("app:p", listOf(first)))
        assertNull(FavoriteEntryResolver.resolve("app:p", listOf(first, second)))
        assertEquals(second, FavoriteEntryResolver.resolve("app:p/B", listOf(first, second)))
        assertNull(FavoriteEntryResolver.resolve("app:p/C", listOf(first, second)))
    }
}
