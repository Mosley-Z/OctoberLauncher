package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自动成组规则测试。
 *
 * 规则只依赖目录快照与桌面配置，用例覆盖建组条件、手动安排保留、旧包级引用确认与幂等。
 */
class FolderAutoGroupTest {
    @Test
    fun groupsMultiEntryPackageAtFirstMemberPosition() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:b/B1", "special:files"),
                sortMode = SortMode.LABEL,
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.run(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2")),
                mapOf("b" to "B 应用"),
            )
        val updated = requireNotNull(result)
        assertEquals(
            listOf("app:a/A", "folder:f1", "special:files"),
            updated.order,
        )
        val folder = updated.folders.single()
        assertEquals("f1", folder.id)
        assertEquals("B 应用", folder.name)
        assertEquals(listOf("app:b/B1", "app:b/B2"), folder.memberKeys)
        assertEquals(FolderAutoGroup.GROUPED, updated.folderAutoGroup["b"])
        assertEquals(SortMode.MANUAL, updated.sortMode)
    }

    @Test
    fun skipsPackageWithSingleComponent() {
        val desktop =
            DesktopConfig(order = listOf("app:a/A", "app:b/B1"), componentKeyMigrated = true)
        val result =
            FolderAutoGroup.run(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1")),
            )
        assertNull(result)
    }

    @Test
    fun keepsManualFolderArrangement() {
        val desktop =
            DesktopConfig(
                order = listOf("folder:f1", "app:a/A"),
                folders = listOf(DesktopFolder("f1", "手动", memberKeys = listOf("app:b/B1"))),
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.run(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2")),
            )
        val updated = requireNotNull(result)
        assertEquals(FolderAutoGroup.KEPT, updated.folderAutoGroup["b"])
        assertEquals(desktop.order, updated.order)
        assertEquals(desktop.folders, updated.folders)
        assertEquals(desktop.sortMode, updated.sortMode)
    }

    @Test
    fun processedPackageIsNotGroupedAgain() {
        val desktop =
            DesktopConfig(
                order = listOf("folder:f1", "special:files"),
                folders = listOf(DesktopFolder("f1", "B 应用", memberKeys = listOf("app:b/B1", "app:b/B2"))),
                folderAutoGroup = mapOf("b" to FolderAutoGroup.GROUPED),
                sortMode = SortMode.MANUAL,
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.run(
                desktop,
                listOf(app("b", "B", "B1"), app("b", "B", "B2")),
            )
        assertNull(result)
    }

    @Test
    fun hiddenPackageIsSkipped() {
        val desktop =
            DesktopConfig(
                order = listOf("app:b/B1"),
                hiddenPackages = setOf("b"),
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.run(
                desktop,
                listOf(app("b", "B", "B1"), app("b", "B", "B2")),
            )
        assertNull(result)
    }

    @Test
    fun emptyCatalogReturnsNull() {
        val desktop =
            DesktopConfig(order = listOf("app:b"), componentKeyMigrated = true)
        assertNull(FolderAutoGroup.run(desktop, emptyList()))
    }

    @Test
    fun pendingLegacyListsTopLevelEntries() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:b"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val pending =
            FolderAutoGroup
                .pendingLegacy(
                    desktop,
                    listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2")),
                    mapOf("b" to "B 应用"),
                ).single()
        assertEquals("b", pending.packageName)
        assertEquals("B 应用", pending.folderName)
        assertEquals(listOf("app:b"), pending.entryKeys)
        assertNull(pending.hostFolderId)
        assertEquals(2, pending.entryCount)
    }

    @Test
    fun pendingLegacySkipsSingleComponentPackage() {
        val desktop =
            DesktopConfig(
                order = listOf("app:b"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val pending =
            FolderAutoGroup.pendingLegacy(
                desktop,
                listOf(app("b", "B", "B1")),
            )
        assertTrue(pending.isEmpty())
    }

    @Test
    fun pendingLegacySkipsKeysNoLongerPresent() {
        val desktop =
            DesktopConfig(
                order = listOf("special:files"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val pending =
            FolderAutoGroup.pendingLegacy(
                desktop,
                listOf(app("b", "B", "B1"), app("b", "B", "B2")),
            )
        assertTrue(pending.isEmpty())
    }

    @Test
    fun acceptedLegacyReplacesTopLevelEntryInPlace() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:b", "special:files"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.resolveLegacy(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2")),
                "b",
                accepted = true,
                labels = mapOf("b" to "B 应用"),
            )
        val updated = requireNotNull(result)
        assertEquals(listOf("app:a/A", "folder:f1", "special:files"), updated.order)
        assertEquals(listOf("app:b/B1", "app:b/B2"), updated.folders.single().memberKeys)
        assertEquals(FolderAutoGroup.GROUPED, updated.legacyComponentDecisions["app:b"])
        assertEquals(SortMode.MANUAL, updated.sortMode)
    }

    @Test
    fun declinedLegacyOnlyRecordsDecision() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:b"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                sortMode = SortMode.LABEL,
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.resolveLegacy(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2")),
                "b",
                accepted = false,
            )
        val updated = requireNotNull(result)
        assertEquals(desktop.order, updated.order)
        assertTrue(updated.folders.isEmpty())
        assertEquals(FolderAutoGroup.DECLINED, updated.legacyComponentDecisions["app:b"])
        assertEquals(SortMode.LABEL, updated.sortMode)
    }

    @Test
    fun acceptedLegacyInsideHostFolderIsNotNested() {
        val desktop =
            DesktopConfig(
                order = listOf("folder:f1", "special:files"),
                folders =
                    listOf(
                        DesktopFolder("f1", "托管", memberKeys = listOf("app:b", "app:a/A")),
                    ),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.resolveLegacy(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2")),
                "b",
                accepted = true,
                labels = mapOf("b" to "B 应用"),
            )
        val updated = requireNotNull(result)
        assertEquals(listOf("folder:f1", "folder:f2", "special:files"), updated.order)
        val host = updated.folders.first { it.id == "f1" }
        assertEquals(listOf("app:a/A"), host.memberKeys)
        val grouped = updated.folders.first { it.id == "f2" }
        assertEquals("B 应用", grouped.name)
        assertEquals(listOf("app:b/B1", "app:b/B2"), grouped.memberKeys)
    }

    @Test
    fun acceptedLegacyWithSingleComponentResolvesKeyInPlace() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:b"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val result =
            FolderAutoGroup.resolveLegacy(
                desktop,
                listOf(app("a", "A", "A"), app("b", "B", "B1")),
                "b",
                accepted = true,
            )
        val updated = requireNotNull(result)
        assertEquals(listOf("app:a/A", "app:b/B1"), updated.order)
        assertTrue(updated.folders.isEmpty())
        assertEquals(FolderAutoGroup.GROUPED, updated.legacyComponentDecisions["app:b"])
        assertEquals(SortMode.MANUAL, updated.sortMode)
    }

    @Test
    fun resolveLegacyWithoutPendingEntryReturnsNull() {
        val desktop =
            DesktopConfig(order = listOf("app:a/A"), componentKeyMigrated = true)
        val result =
            FolderAutoGroup.resolveLegacy(
                desktop,
                listOf(app("a", "A", "A"), app("a", "A", "A2")),
                "a",
                accepted = true,
            )
        assertNull(result)
    }

    @Test
    fun pendingLegacyPackageIsNotGroupedSilently() {
        val desktop =
            DesktopConfig(
                order = listOf("app:other/O", "app:b", "app:b/B1", "app:b/B2", "special:settings"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val apps = listOf(app("other", "O", "O"), app("b", "B", "B1"), app("b", "B", "B2"))
        // 仍待确认的包不参与自动成组，确认流程才能取到待处理项
        assertNull(FolderAutoGroup.run(desktop, apps))
        val pending = FolderAutoGroup.pendingLegacy(desktop, apps).single()
        assertEquals("b", pending.packageName)
        assertEquals(listOf("app:b"), pending.entryKeys)
        assertEquals(2, pending.entryCount)
        assertNull(pending.hostFolderId)
    }

    @Test
    fun pendingLegacyPackageInsideHostFolderIsNotGroupedSilently() {
        val desktop =
            DesktopConfig(
                order = listOf("app:other/O", "folder:f9", "app:b/B1", "app:b/B2", "special:files"),
                folders = listOf(DesktopFolder("f9", "旧组", memberKeys = listOf("app:b"))),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val apps = listOf(app("other", "O", "O"), app("b", "B", "B1"), app("b", "B", "B2"))
        // 宿主文件夹与成员保持原样，待确认项带出宿主信息
        assertNull(FolderAutoGroup.run(desktop, apps))
        val pending = FolderAutoGroup.pendingLegacy(desktop, apps).single()
        assertEquals("f9", pending.hostFolderId)
        assertEquals("旧组", pending.hostFolderName)
    }

    @Test
    fun resolveLegacyAcceptedRemovesKeysFromOrderAndFolder() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "folder:f5", "special:files"),
                folders = listOf(DesktopFolder("f5", "旧组", memberKeys = listOf("app:b"))),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                sortMode = SortMode.MANUAL,
                componentKeyMigrated = true,
            )
        val apps = listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2"))
        val updated =
            requireNotNull(FolderAutoGroup.resolveLegacy(desktop, apps, "b", accepted = true))
        // 宿主文件夹保持原位，新组紧随其后，旧键从宿主成员中消失
        assertEquals(listOf("app:a/A", "folder:f5", "folder:f6", "special:files"), updated.order)
        assertTrue(
            updated.folders
                .first { it.id == "f5" }
                .memberKeys
                .isEmpty(),
        )
        assertEquals(listOf("app:b/B1", "app:b/B2"), updated.folders.first { it.id == "f6" }.memberKeys)
        assertEquals(FolderAutoGroup.GROUPED, updated.legacyComponentDecisions["app:b"])
        assertTrue(FolderAutoGroup.pendingLegacy(updated, apps).isEmpty())
    }

    @Test
    fun unambiguousPackageIsGroupedWhilePendingPackageWaits() {
        val desktop =
            DesktopConfig(
                order = listOf("app:other/O", "app:b", "app:b/B1", "app:b/B2", "app:c/C1", "app:c/C2"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val apps =
            listOf(
                app("other", "O", "O"),
                app("b", "B", "B1"),
                app("b", "B", "B2"),
                app("c", "C", "C1"),
                app("c", "C", "C2"),
            )
        val updated = requireNotNull(FolderAutoGroup.run(desktop, apps))
        // 无歧义包照常成组，待确认包原样保留并继续出现在待处理列表
        assertEquals(
            listOf("app:other/O", "app:b", "app:b/B1", "app:b/B2", "folder:f1"),
            updated.order,
        )
        assertEquals(FolderAutoGroup.GROUPED, updated.folderAutoGroup["c"])
        assertNull(updated.folderAutoGroup["b"])
        assertEquals(FolderAutoGroup.PENDING, updated.legacyComponentDecisions["app:b"])
        assertEquals(listOf("b"), FolderAutoGroup.pendingLegacy(updated, apps).map { it.packageName })
    }

    @Test
    fun declinedLegacyPackageIsNotGroupedLater() {
        val desktop =
            DesktopConfig(
                order = listOf("app:other/O", "app:b", "app:b/B1", "app:b/B2"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val apps = listOf(app("other", "O", "O"), app("b", "B", "B1"), app("b", "B", "B2"))
        val declined = requireNotNull(FolderAutoGroup.resolveLegacy(desktop, apps, "b", accepted = false))
        // 拒绝后记录结论，既不再弹确认也不再自动建组
        assertEquals(FolderAutoGroup.DECLINED, declined.folderAutoGroup["b"])
        assertNull(FolderAutoGroup.run(declined, apps))
        assertTrue(FolderAutoGroup.pendingLegacy(declined, apps).isEmpty())
    }

    @Test
    fun unmigratedDesktopIsNotGrouped() {
        // 旧包级键尚未解析时不做归组：归组会消费旧键，使用户看不到旧引用确认浮层
        val desktop =
            DesktopConfig(
                order = listOf("app:b", "special:files"),
            )
        val result =
            FolderAutoGroup.run(
                desktop,
                listOf(app("b", "B", "B1"), app("b", "B", "B2")),
            )
        assertNull(result)
        assertEquals(listOf("app:b", "special:files"), desktop.order)
        assertTrue(desktop.folders.isEmpty())
    }

    @Test
    fun pendingLegacyReferenceIsNotConsumedByAutoGroup() {
        // 迁移已完成但旧键仍待确认时，归组必须跳过该包，保留旧键与待确认状态
        val desktop =
            DesktopConfig(
                order = listOf("app:other/O", "app:b"),
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
                componentKeyMigrated = true,
            )
        val apps = listOf(app("other", "O", "O"), app("b", "B", "B1"), app("b", "B", "B2"))
        assertNull(FolderAutoGroup.run(desktop, apps))
        assertEquals(listOf("app:other/O", "app:b"), desktop.order)
        assertTrue(desktop.folders.isEmpty())
        val pending = FolderAutoGroup.pendingLegacy(desktop, apps).single()
        assertEquals("app:b", pending.entryKeys.single())
    }

    @Test
    fun groupsAtVisiblePositionWhenOrderIsEmpty() {
        // order 为空时按当前有效呈现顺序定位，文件夹落在成员原本位置，其他条目相对顺序不变
        val desktop =
            DesktopConfig(
                sortMode = SortMode.LABEL,
                componentKeyMigrated = true,
            )
        val apps = listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2"), app("c", "C", "C"))
        val visibleOrder = listOf("app:a/A", "app:b/B1", "app:b/B2", "app:c/C", "special:files")
        val updated = requireNotNull(FolderAutoGroup.run(desktop, apps, visibleOrder = visibleOrder))
        assertEquals(listOf("app:a/A", "folder:f1", "app:c/C", "special:files"), updated.order)
        assertEquals(listOf("app:b/B1", "app:b/B2"), updated.folders.single().memberKeys)
        assertEquals(FolderAutoGroup.GROUPED, updated.folderAutoGroup["b"])
        assertEquals(SortMode.MANUAL, updated.sortMode)
    }

    @Test
    fun keptDecisionPreservesSortMode() {
        // 仅记录保留结论时不得切换排序模式，界面仍按原排序模式呈现
        val desktop =
            DesktopConfig(
                order = listOf("folder:f1", "app:a/A"),
                folders = listOf(DesktopFolder("f1", "手动", memberKeys = listOf("app:b/B1"))),
                sortMode = SortMode.LABEL,
                componentKeyMigrated = true,
            )
        val apps = listOf(app("a", "A", "A"), app("b", "B", "B1"), app("b", "B", "B2"))
        val updated = requireNotNull(FolderAutoGroup.run(desktop, apps))
        assertEquals(FolderAutoGroup.KEPT, updated.folderAutoGroup["b"])
        assertEquals(SortMode.LABEL, updated.sortMode)
        assertEquals(desktop.order, updated.order)
    }

    @Test
    fun migrationGateKeepsLayoutUntilLegacyKeysResolved() {
        // 阶段一：首个非空快照，旧键尚未解析，归组不得执行
        val apps = listOf(app("b", "B", "B1"), app("b", "B", "B2"), app("c", "C", "C"))
        val unmigrated = DesktopConfig(order = listOf("app:b", "app:c/C"))
        assertNull(FolderAutoGroup.run(unmigrated, apps))
        assertEquals(listOf("app:b", "app:c/C"), unmigrated.order)

        // 阶段二：迁移完成但旧键仍待确认，归组继续跳过该包
        val pending =
            unmigrated.copy(
                componentKeyMigrated = true,
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
            )
        assertNull(FolderAutoGroup.run(pending, apps))
        assertEquals(listOf("app:b", "app:c/C"), pending.order)

        // 阶段三：确认后旧键被解析为组件键，归组在组件键位置建立文件夹
        val resolved =
            pending.copy(
                order = listOf("app:b/B1", "app:b/B2", "app:c/C"),
                legacyComponentDecisions = emptyMap(),
            )
        val updated = requireNotNull(FolderAutoGroup.run(resolved, apps))
        assertEquals(listOf("folder:f1", "app:c/C"), updated.order)
        assertEquals(listOf("app:b/B1", "app:b/B2"), updated.folders.single().memberKeys)
        assertEquals(FolderAutoGroup.GROUPED, updated.folderAutoGroup["b"])
    }

    @Test
    fun declinedPackageCanBeReopenedManually() {
        val apps = listOf(app("b", "B", "B1"), app("b", "B", "B2"))
        // 拒绝时布局保持原样，旧包级键仍留在顶层顺序中。
        val declined =
            DesktopConfig(
                order = listOf("app:b", "app:c/C"),
                componentKeyMigrated = true,
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.DECLINED),
                folderAutoGroup = mapOf("b" to FolderAutoGroup.DECLINED),
            )

        // 已拒绝的包没有待确认引用，因此不会自动弹窗，但可作为主动入口重新发起。
        assertEquals(listOf("b"), FolderAutoGroup.declinedPackages(declined))
        assertTrue(FolderAutoGroup.pendingLegacy(declined, apps).isEmpty())

        val reopened = requireNotNull(FolderAutoGroup.reopenDeclined(declined, "b"))
        assertEquals(FolderAutoGroup.PENDING, reopened.legacyComponentDecisions["app:b"])
        assertNull(reopened.folderAutoGroup["b"])
        assertEquals(listOf("b"), FolderAutoGroup.pendingLegacy(reopened, apps).map { it.packageName })
        assertEquals(listOf("app:b", "app:c/C"), reopened.order)

        // 再次拒绝只记录决定，不改动布局，也不会重新变为待确认。
        val declinedAgain = requireNotNull(FolderAutoGroup.resolveLegacy(reopened, apps, "b", false))
        assertEquals(FolderAutoGroup.DECLINED, declinedAgain.legacyComponentDecisions["app:b"])
        assertEquals(FolderAutoGroup.DECLINED, declinedAgain.folderAutoGroup["b"])
        assertEquals(reopened.order, declinedAgain.order)
        assertTrue(FolderAutoGroup.pendingLegacy(declinedAgain, apps).isEmpty())
        assertEquals(listOf("b"), FolderAutoGroup.declinedPackages(declinedAgain))
    }

    @Test
    fun reopenDeclinedIgnoresPackagesWithoutDeclinedReference() {
        val grouped = DesktopConfig(order = listOf("app:b/B1"), folderAutoGroup = mapOf("b" to FolderAutoGroup.GROUPED))
        assertTrue(FolderAutoGroup.declinedPackages(grouped).isEmpty())
        assertNull(FolderAutoGroup.reopenDeclined(grouped, "b"))
    }

    @Test
    fun legacyKeyInMiddleOfPersistedOrderInsertsFolderAtThatPosition() {
        // 迁移后的旧包级键保留在持久顺序中间，确认后文件夹应落在该位置，其余条目顺序不变
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:other/O", "app:b", "app:c/C", "special:files"),
                sortMode = SortMode.MANUAL,
                componentKeyMigrated = true,
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
            )
        val apps =
            listOf(
                app("a", "A", "A"),
                app("other", "O", "O"),
                app("b", "B", "B1"),
                app("b", "B", "B2"),
                app("c", "C", "C"),
            )
        // 界面只呈现组件键，旧包级键不在可见顺序中
        val visibleOrder = listOf("app:a/A", "app:other/O", "app:c/C", "special:files")
        val updated =
            requireNotNull(
                FolderAutoGroup.resolveLegacy(desktop, apps, "b", accepted = true, visibleOrder = visibleOrder),
            )
        assertEquals(
            listOf("app:a/A", "app:other/O", "folder:f1", "app:c/C", "special:files"),
            updated.order,
        )
        assertEquals(listOf("app:b/B1", "app:b/B2"), updated.folders.single().memberKeys)
        assertEquals(FolderAutoGroup.GROUPED, updated.legacyComponentDecisions["app:b"])
        assertEquals(SortMode.MANUAL, updated.sortMode)
    }

    @Test
    fun effectiveOrderAppendsUnpersistedVisibleKeys() {
        // 可见但尚未持久化的条目追加在持久顺序之后，已持久化部分不被重排
        val desktop =
            DesktopConfig(
                order = listOf("app:a/A", "app:b", "app:c/C"),
                componentKeyMigrated = true,
                legacyComponentDecisions = mapOf("app:b" to FolderAutoGroup.PENDING),
            )
        val apps = listOf(app("a", "A", "A"), app("b", "B", "B1"), app("c", "C", "C"), app("d", "D", "D"))
        val visibleOrder = listOf("app:a/A", "app:d/D", "app:c/C")
        val updated =
            requireNotNull(
                FolderAutoGroup.resolveLegacy(desktop, apps, "b", accepted = true, visibleOrder = visibleOrder),
            )
        assertEquals(listOf("app:a/A", "app:b/B1", "app:c/C", "app:d/D"), updated.order)
        assertEquals(FolderAutoGroup.GROUPED, updated.folderAutoGroup["b"])
        assertEquals(SortMode.MANUAL, updated.sortMode)
    }

    /** 构造目录条目；组件键由包名与 Activity 拼接。 */
    @Test
    fun freezesReverseAlphabeticalMembersBeforeReplacingOrder() {
        val apps = listOf(app("b", "Alpha", "B1"), app("b", "Zulu", "B2"))
        for (persisted in listOf(true, false)) {
            val order = listOf("app:b/B2", "app:b/B1")
            val desktop =
                DesktopConfig(
                    order = if (persisted) order else emptyList(),
                    componentKeyMigrated = true,
                )
            val updated = requireNotNull(FolderAutoGroup.run(desktop, apps, visibleOrder = order))
            assertEquals(order, updated.folders.single().memberKeys)
        }
    }

    @Test
    fun canReopenSecondPackageWhileFirstRemainsDeclined() {
        val desktop =
            DesktopConfig(
                order = listOf("app:a", "app:b"),
                componentKeyMigrated = true,
                folderAutoGroup = mapOf("a" to FolderAutoGroup.DECLINED, "b" to FolderAutoGroup.DECLINED),
                legacyComponentDecisions =
                    mapOf("app:a" to FolderAutoGroup.DECLINED, "app:b" to FolderAutoGroup.DECLINED),
            )
        val choices = FolderAutoGroup.declinedPackages(desktop)
        val updated = requireNotNull(FolderAutoGroup.reopenDeclined(desktop, choices[1]))
        assertEquals(FolderAutoGroup.DECLINED, updated.legacyComponentDecisions["app:a"])
        assertEquals(FolderAutoGroup.PENDING, updated.legacyComponentDecisions["app:b"])
        assertEquals(listOf("a"), FolderAutoGroup.declinedPackages(updated))
        assertEquals(desktop.order, updated.order)
    }

    private fun app(
        packageName: String,
        label: String,
        activityName: String,
    ): InstalledApp = InstalledApp(packageName, label, activityName, 0L, false)
}
