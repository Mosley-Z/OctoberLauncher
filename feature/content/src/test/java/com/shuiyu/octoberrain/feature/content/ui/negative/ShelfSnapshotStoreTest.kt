package com.shuiyu.octoberrain.feature.content.ui.negative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShelfSnapshotStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun snapshotSurvivesNewRepositoryAndRejectsDifferentDirectory() {
        val root = temporary.newFolder()
        val saved = ShelfSnapshot(directory = "Books", updatedAt = 100, attemptedAt = 110)
        ShelfSnapshotStore(root).save("local", saved)
        assertEquals(saved, ShelfSnapshotStore(root).load("local", "Books"))
        assertNull(ShelfSnapshotStore(root).load("local", "Other"))
        assertNull(ShelfSnapshotStore(root).load("weread", "Books"))
    }
}
