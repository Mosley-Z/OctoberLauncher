package com.shuiyu.octoberrain.core.data.wereadimport

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 迁移数据落位器单元测试。
 *
 * 覆盖首次导入、旧数据备份替换、非法条目名拒绝与空数据拒绝四条路径，
 * 全部使用临时目录，不接触真实应用数据。
 */
class WereadImportStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** 首次导入没有旧数据，写入全部条目且不产生备份目录。 */
    @Test
    fun `首次导入写入全部条目`() {
        val root = File(folder.root, "weread")
        val backupRoot = File(folder.root, "weread-backup")
        val store = WereadImportStore(root, backupRoot)
        val entries =
            mapOf(
                "nt_index.json" to """{"books":[1]}""".toByteArray(),
                "kernel_prefs.json" to """{"string":{},"long":{},"int":{},"bool":{}}""".toByteArray(),
            )

        val result = store.install(entries, timestamp = 1000L)

        assertTrue(result is WereadImportInstallResult.Success)
        result as WereadImportInstallResult.Success
        assertNull(result.backup)
        assertEquals(2, result.files.size)
        assertArrayEquals(entries["nt_index.json"], File(root, "nt_index.json").readBytes())
        assertArrayEquals(entries["kernel_prefs.json"], File(root, "kernel_prefs.json").readBytes())
    }

    /** 已有旧数据时先备份再替换，备份目录保留旧内容。 */
    @Test
    fun `旧数据备份后整体替换`() {
        val root = File(folder.root, "weread")
        root.mkdirs()
        File(root, "old.json").writeBytes("old".toByteArray())
        val backupRoot = File(folder.root, "weread-backup")
        val store = WereadImportStore(root, backupRoot)

        val result = store.install(mapOf("new.json" to "new".toByteArray()), timestamp = 2000L)

        assertTrue(result is WereadImportInstallResult.Success)
        result as WereadImportInstallResult.Success
        assertNotNull(result.backup)
        assertArrayEquals("old".toByteArray(), File(result.backup!!, "old.json").readBytes())
        assertArrayEquals("new".toByteArray(), File(root, "new.json").readBytes())
        assertFalse(File(root, "old.json").exists())
    }

    /** 条目名含路径跳转时整体拒绝，旧数据保持不变。 */
    @Test
    fun `非法条目名被拒绝`() {
        val root = File(folder.root, "weread")
        root.mkdirs()
        File(root, "keep.json").writeBytes("keep".toByteArray())
        val store = WereadImportStore(root, File(folder.root, "weread-backup"))

        val result = store.install(mapOf("../evil.json" to "x".toByteArray()))

        assertTrue(result is WereadImportInstallResult.Failure)
        assertArrayEquals("keep".toByteArray(), File(root, "keep.json").readBytes())
    }

    /** 空数据不触碰目录。 */
    @Test
    fun `空数据被拒绝`() {
        val root = File(folder.root, "weread")
        val store = WereadImportStore(root, File(folder.root, "weread-backup"))

        val result = store.install(emptyMap())

        assertTrue(result is WereadImportInstallResult.Failure)
        assertFalse(root.exists())
    }
}
