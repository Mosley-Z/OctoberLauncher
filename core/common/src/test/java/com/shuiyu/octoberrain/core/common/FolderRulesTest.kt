package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 规则不读取应用目录，所有修改均返回新的不可变配置。 */
class FolderRulesTest {
    @Test
    fun createKeepsSelectionOrderAndInsertionPosition() {
        val original = DesktopConfig(order = listOf("special:files", "app:a", "app:b", "app:c"))
        val result = success(FolderRules.create(original, " 阅读 ", listOf("app:b", "app:a"), "f1", "special:files"))
        assertEquals(
            "阅读",
            result.desktop.folders
                .single()
                .name,
        )
        assertEquals(
            listOf("app:b", "app:a"),
            result.desktop.folders
                .single()
                .memberKeys,
        )
        assertEquals(listOf("special:files", "folder:f1", "app:c"), result.desktop.order)
        assertEquals(FolderChangeSummary(added = 2), result.summary)
        assertEquals(4, original.order.size)
    }

    @Test
    fun crossFolderMoveAndDuplicateAddKeepSingleMembership() {
        val desktop = DesktopConfig(folders = listOf(folder("f1", "app:a"), folder("f2", "app:b")))
        val result = success(FolderRules.addMembers(desktop, "f2", listOf("app:a", "app:a", "app:b", "app:c")))
        assertTrue(
            result.desktop.folders
                .first()
                .memberKeys
                .isEmpty(),
        )
        assertEquals(
            listOf("app:b", "app:a", "app:c"),
            result.desktop.folders
                .last()
                .memberKeys,
        )
        assertEquals(FolderChangeSummary(added = 1, moved = 1, skipped = 2), result.summary)
        val repeated = success(FolderRules.addMembers(result.desktop, "f2", listOf("app:a", "app:b")))
        assertEquals(result.desktop, repeated.desktop)
        assertEquals(FolderChangeSummary(skipped = 2), repeated.summary)
    }

    @Test
    fun createMovesMembersFromExistingFolder() {
        val desktop = DesktopConfig(folders = listOf(folder("f1", "app:a")))
        val result = success(FolderRules.create(desktop, "新组", listOf("app:a"), "f2"))
        assertTrue(
            result.desktop.folders
                .first()
                .memberKeys
                .isEmpty(),
        )
        assertEquals(FolderChangeSummary(moved = 1), result.summary)
    }

    @Test
    fun removeAppendsMemberAndKeepsEmptyFolder() {
        val desktop = DesktopConfig(folders = listOf(folder("f1", "app:a")), order = listOf("folder:f1", "app:b"))
        val result = success(FolderRules.removeMember(desktop, "f1", "app:a")).desktop
        assertEquals(listOf("folder:f1", "app:b", "app:a"), result.order)
        assertTrue(
            result.folders
                .single()
                .memberKeys
                .isEmpty(),
        )
        assertEquals(result, success(FolderRules.removeMember(result, "f1", "app:a")).desktop)
    }

    @Test
    fun dissolveRestoresMembersAtFolderPosition() {
        val desktop =
            DesktopConfig(
                folders = listOf(folder("f1", "app:b", "app:a")),
                order = listOf("app:c", "folder:f1", "special:files"),
            )
        val result = success(FolderRules.dissolve(desktop, "f1")).desktop
        assertEquals(listOf("app:c", "app:b", "app:a", "special:files"), result.order)
        assertTrue(result.folders.isEmpty())
    }

    @Test
    fun hiddenAndUninstalledMembersRemainReferenced() {
        val desktop =
            DesktopConfig(
                hiddenPackages = setOf("hidden"),
                folders = listOf(folder("f1", "app:hidden", "app:uninstalled")),
            )
        val normalized = FolderRules.normalize(desktop)
        assertEquals(desktop.folders, normalized.folders)
        assertEquals(desktop.folders, FolderRules.normalize(normalized.copy(hiddenPackages = emptySet())).folders)
    }

    @Test
    fun damagedReferencesNormalizeIdempotentlyWithoutDuplicateOrder() {
        val desktop =
            DesktopConfig(
                folders =
                    listOf(
                        folder("f1", "app:a", "app:a", "special:files", "folder:f2", "app:"),
                        folder("f1", "app:b"),
                        folder("f2", "app:a", "app:c"),
                        folder(""),
                        folder("f3"),
                    ),
                order = listOf("app:a", "folder:missing", "folder:f1", "folder:f1", "unknown:key", "unknown:key"),
            )
        val result = FolderRules.normalize(desktop)
        assertEquals(listOf(folder("f1", "app:a"), folder("f2", "app:c"), folder("f3")), result.folders)
        assertEquals(listOf("folder:f1", "unknown:key", "folder:f2", "folder:f3"), result.order)
        assertSame(result, FolderRules.normalize(result))
    }

    @Test
    fun legacyWithoutFoldersKeepsExactInstance() {
        val desktop = DesktopConfig(order = listOf("app:a", "app:a", "folder:missing"))
        assertSame(desktop, FolderRules.normalize(desktop))
    }

    @Test
    fun specialEntriesCannotBecomeMembersAndFailureKeepsInput() {
        val desktop = DesktopConfig(folders = listOf(folder("f1")))
        assertEquals(
            FolderResult.Failure(FolderFailure.NO_VALID_MEMBERS),
            FolderRules.create(desktop, "组", listOf("special:files", "folder:f1", "app:"), "f2"),
        )
        val result = success(FolderRules.addMembers(desktop, "f1", listOf("special:files", "folder:f1")))
        assertTrue(
            result.desktop.folders
                .single()
                .memberKeys
                .isEmpty(),
        )
        assertEquals(2, result.summary.skipped)
        assertEquals(listOf(folder("f1")), desktop.folders)
    }

    @Test
    fun renameValidatesAndTruncatesAndMissingOperationsFail() {
        val desktop = DesktopConfig(folders = listOf(folder("f1")))
        val invalid = FolderResult.Failure(FolderFailure.INVALID_NAME)
        assertEquals(invalid, FolderRules.rename(desktop, "f1", "  "))
        assertEquals(invalid, FolderRules.create(desktop, "  ", listOf("app:a"), "f2"))
        val renamed = success(FolderRules.rename(desktop, "f1", "名".repeat(41))).desktop
        assertEquals(
            40,
            renamed.folders
                .single()
                .name.length,
        )
        assertEquals(
            FolderResult.Failure(FolderFailure.DUPLICATE_ID),
            FolderRules.create(desktop, "组", listOf("app:a"), "f1"),
        )
        val missing = FolderResult.Failure(FolderFailure.FOLDER_NOT_FOUND)
        assertEquals(missing, FolderRules.rename(desktop, "missing", "组"))
        assertEquals(missing, FolderRules.addMembers(desktop, "missing", listOf("app:a")))
        assertEquals(missing, FolderRules.removeMember(desktop, "missing", "app:a"))
        assertEquals(missing, FolderRules.dissolve(desktop, "missing"))
    }

    @Test
    fun nextIdIncrementsLargestNumericSuffix() {
        assertEquals("f1", FolderRules.nextFolderId(DesktopConfig()))
        assertEquals("f13", FolderRules.nextFolderId(DesktopConfig(folders = listOf(folder("f2"), folder("old12")))))
    }

    private fun folder(
        id: String,
        vararg members: String,
    ): DesktopFolder = DesktopFolder(id, id, members.toList())

    private fun success(result: FolderResult): FolderResult.Success = result as FolderResult.Success
}
