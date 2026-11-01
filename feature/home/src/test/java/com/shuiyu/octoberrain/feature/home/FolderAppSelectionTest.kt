package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.FolderChangeSummary
import com.shuiyu.octoberrain.core.common.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderAppSelectionTest {
    private fun app(
        pkg: String,
        label: String = pkg,
    ) = InstalledApp(pkg, label, null, 0L, false)

    private fun candidate(
        pkg: String,
        folder: String? = null,
        installed: Boolean = true,
    ) = FolderCandidate("app:$pkg", pkg, pkg, installed, folder, folder)

    @Test
    fun candidatesExcludeHiddenMembersAndKeepOtherFolderMembership() {
        val folders = listOf(DesktopFolder("f1", "阅读", listOf("app:hidden", "app:a", "app:gone")))
        val result =
            FolderAppSelection.candidates(
                listOf(app("hidden"), app("a"), app("b")),
                folders,
                setOf("hidden"),
                "f2",
            )
        assertEquals(listOf("app:a", "app:b"), result.map { it.key })
        assertEquals("f1", result.first().parentFolderId)
        assertEquals("阅读", result.first().parentFolderName)
        assertTrue(result.all { it.installed })
    }

    @Test
    fun sameLabelsSortByPackageAndDuplicateCatalogEntriesCollapse() {
        val result =
            FolderAppSelection.candidates(
                listOf(app("z", "Same"), app("a", "Same"), app("z", "Same")),
                emptyList(),
                emptySet(),
                null,
            )
        assertEquals(listOf("app:a", "app:z"), result.map { it.key })
    }

    @Test
    fun catalogInputOrderDoesNotChangeCandidateOrder() {
        val apps = listOf(app("b", "Beta"), app("a", "alpha"), app("c", "Beta"))
        assertEquals(
            FolderAppSelection.candidates(apps, emptyList(), emptySet(), null),
            FolderAppSelection.candidates(apps.reversed(), emptyList(), emptySet(), null),
        )
    }

    @Test
    fun searchMatchesLabelOrPackageWithoutChangingSelection() {
        val candidates =
            FolderAppSelection.candidates(
                listOf(app("org.reader", "Books"), app("other")),
                emptyList(),
                emptySet(),
                null,
            )
        val selected = setOf("app:other")
        assertEquals(listOf("app:org.reader"), FolderAppSelection.filter(candidates, "BOOK").map { it.key })
        assertEquals(listOf("app:org.reader"), FolderAppSelection.filter(candidates, "ORG.READ").map { it.key })
        assertEquals(candidates, FolderAppSelection.filter(candidates, ""))
        assertEquals(setOf("app:other"), selected)
    }

    @Test
    fun guardedToggleRejectsTargetMembersUninstalledAndUnknownKeys() {
        val candidates = listOf(candidate("own", "f1"), candidate("gone", installed = false), candidate("other", "f2"))
        listOf("app:own", "app:gone", "app:unknown").forEach { key ->
            assertTrue(FolderAppSelection.toggle(emptySet(), key, candidates, "f1").isEmpty())
        }
        val selected = FolderAppSelection.toggle(emptySet(), "app:other", candidates, "f1")
        assertEquals(setOf("app:other"), selected)
        assertTrue(FolderAppSelection.toggle(selected, "app:other", candidates, "f1").isEmpty())
    }

    @Test
    fun basicToggleIsImmutableAndRejectsNonAppKeys() {
        val original = setOf("app:a")
        assertEquals(setOf("app:a", "app:b"), FolderAppSelection.toggle(original, "app:b"))
        assertEquals(setOf("app:a"), original)
        assertEquals(original, FolderAppSelection.toggle(original, "special:files"))
        assertEquals(original, FolderAppSelection.toggle(original, "app:"))
    }

    @Test
    fun planCountsAddedMovedInvalidDuplicateAndTargetMembers() {
        val candidates =
            listOf(
                candidate("a"),
                candidate("b", "f2"),
                candidate("a"),
                candidate("c", "f1"),
                candidate("d", installed = false),
            )
        assertEquals(
            FolderChangeSummary(added = 1, moved = 1, skipped = 4),
            FolderAppSelection.plan(candidates, setOf("app:a", "app:b", "app:c", "app:d", "app:unknown"), "f1"),
        )
    }

    @Test
    fun submissionKeysFollowCandidatesInsteadOfClickOrder() {
        val candidates = listOf(candidate("a"), candidate("b", "f2"), candidate("c", "f1"), candidate("a"))
        assertEquals(
            listOf("app:a", "app:b"),
            FolderAppSelection.selectedKeys(candidates, linkedSetOf("app:b", "app:c", "app:a"), "f1"),
        )
    }

    @Test
    fun newFolderPlanTreatsUngroupedAppsAsAddedAndGroupedAppsAsMoved() {
        assertEquals(
            FolderChangeSummary(1, 1, 0),
            FolderAppSelection.plan(listOf(candidate("a"), candidate("b", "f1")), setOf("app:a", "app:b"), null),
        )
    }

    @Test
    fun revalidateSkipsUninstalledMalformedAndDuplicateKeysInStableOrder() {
        assertEquals(
            listOf("app:b", "app:a") to 4,
            FolderAppSelection.revalidate(
                listOf("app:b", "app:gone", "special:files", "app:a", "app:b", "folder:f1"),
                setOf("a", "b"),
            ),
        )
        assertEquals(emptyList<String>() to 1, FolderAppSelection.revalidate(listOf("app:a"), emptySet()))
    }

    @Test
    fun damagedMultipleOwnersStillDisableExistingTargetMember() {
        val folders =
            listOf(
                DesktopFolder("f1", "First", listOf("app:a")),
                DesktopFolder("f2", "Target", listOf("app:a")),
            )
        val candidates = FolderAppSelection.candidates(listOf(app("a")), folders, emptySet(), "f2")
        assertEquals("f2", candidates.single().parentFolderId)
        assertFalse(FolderAppSelection.toggle(emptySet(), "app:a", candidates, "f2").isNotEmpty())
    }
}
