package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.FolderFailure
import com.shuiyu.octoberrain.core.common.FolderResult
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.SortMode
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 模拟存储吞掉写盘异常后的状态，确保调用者可以保留选择并重试。 */
class FolderStoreTest {
    private var previous: ConfigStore? = null
    private lateinit var store: MemoryStore

    @Before
    fun prepareStore() {
        previous = runCatching { AppServices.config }.getOrNull()
        store = MemoryStore()
        AppServices.config = store
    }

    @After
    fun restoreStore() {
        previous?.let { AppServices.config = it }
    }

    @Test
    fun successUsesOneTransactionAndPreservesInitializationFlag() {
        val desktop =
            DesktopConfig(
                folders = listOf(DesktopFolder("f1", "组")),
                installOrderInitialized = true,
            )
        assertNull(FolderStore.apply(FolderResult.Success(desktop), store.state.value.desktop))
        assertEquals(1, store.edits)
        assertEquals(desktop.copy(sortMode = SortMode.MANUAL), store.state.value.desktop)
        assertEquals(LauncherConfig().appearance, store.state.value.appearance)
    }

    @Test
    fun swallowedWriteFailureReturnsUnknownAndKeepsOldState() {
        val original = store.state.value
        store.publish = false
        val desktop = DesktopConfig(folders = listOf(DesktopFolder("f1", "组")))
        assertEquals(FolderFailure.UNKNOWN, FolderStore.apply(FolderResult.Success(desktop), store.state.value.desktop))
        assertEquals(original, store.state.value)
        assertEquals(1, store.edits)
        store.publish = true
        assertNull(FolderStore.apply(FolderResult.Success(desktop), store.state.value.desktop))
    }

    @Test
    fun failureDoesNotEditAndFalseInitializationFlagIsPreserved() {
        assertEquals(
            FolderFailure.INVALID_NAME,
            FolderStore.apply(FolderResult.Failure(FolderFailure.INVALID_NAME), store.state.value.desktop),
        )
        assertEquals(0, store.edits)
        assertNull(
            FolderStore.apply(
                FolderResult.Success(DesktopConfig(installOrderInitialized = false)),
                store.state.value.desktop,
            ),
        )
        assertEquals(false, store.state.value.desktop.installOrderInitialized)
    }

    @Test
    fun staleSourceReturnsUnknownAndKeepsCurrentState() {
        store.state.value = LauncherConfig(desktop = DesktopConfig(installOrderInitialized = true))
        val original = store.state.value
        val source = DesktopConfig()
        val desktop = source.copy(folders = listOf(DesktopFolder("f1", "Books")))
        assertEquals(FolderFailure.UNKNOWN, FolderStore.apply(FolderResult.Success(desktop), source))
        assertEquals(original, store.state.value)
        assertEquals(1, store.edits)
    }

    @Test
    fun autoGroupWritesRuleOutputInOneTransaction() {
        // 规则决定排序模式：实际改变布局时已切到手动排序，保存入口不再二次改写
        val source = store.state.value.desktop
        val expected =
            source.copy(
                order = listOf("folder:f1", "special:files"),
                folders = listOf(DesktopFolder("f1", "组", memberKeys = listOf("app:b/B1", "app:b/B2"))),
                folderAutoGroup = mapOf("b" to "grouped"),
                sortMode = SortMode.MANUAL,
            )
        assertTrue(FolderStore.applyAutoGroup(expected, source))
        assertEquals(1, store.edits)
        assertEquals(expected, store.state.value.desktop)
    }

    @Test
    fun autoGroupKeepsSortModeWhenOnlyRecordingState() {
        // 仅记录保留或拒绝结论时保持原排序模式，界面不因状态写入改变排列方式
        val source =
            store.state.value.desktop
                .copy(sortMode = SortMode.LABEL)
        store.state.value = LauncherConfig(desktop = source)
        val expected = source.copy(folderAutoGroup = mapOf("b" to "kept"))
        assertTrue(FolderStore.applyAutoGroup(expected, source))
        assertEquals(SortMode.LABEL, store.state.value.desktop.sortMode)
        assertEquals(expected, store.state.value.desktop)
    }

    @Test
    fun autoGroupSwallowedWriteReturnsFalseWithoutPublishing() {
        val source = store.state.value.desktop
        val expected = source.copy(folderAutoGroup = mapOf("b" to "grouped"))
        store.publish = false
        assertFalse(FolderStore.applyAutoGroup(expected, source))
        assertEquals(source, store.state.value.desktop)
        store.publish = true
        val stale = source.copy(folderAutoGroup = mapOf("b" to "declined"))
        assertFalse(FolderStore.applyAutoGroup(expected, stale))
        assertEquals(source, store.state.value.desktop)
    }

    private class MemoryStore : ConfigStore {
        override val state = MutableStateFlow(LauncherConfig())
        var edits = 0
        var publish = true

        override fun edit(transform: (LauncherConfig) -> LauncherConfig) {
            edits += 1
            val next = transform(state.value)
            if (publish) state.value = next
        }
    }
}
