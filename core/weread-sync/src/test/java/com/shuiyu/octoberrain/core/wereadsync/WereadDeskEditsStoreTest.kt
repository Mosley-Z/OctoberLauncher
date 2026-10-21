package com.shuiyu.octoberrain.core.wereadsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 人工资产编辑的跨实例持久化、原版兼容与失败结果。 */
class WereadDeskEditsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** 手填价覆盖迁移价格，删除后不会从旧文件重新出现。 */
    @Test
    fun manualPriceSurvivesRecreationAndOverridesImportedPrice() {
        val root = folder.newFolder()
        File(root, "menu_prices.json").writeText("""{"book":1500,"other":2200}""")
        val store = WereadDeskEditsStore(root)
        assertEquals(1500, store.priceFen("book"))
        assertTrue(store.setPrice("book", 0))
        assertEquals(0, WereadDeskEditsStore(root).priceFen("book"))
        assertTrue(store.setPrice("book", 3500))
        assertEquals(3500, WereadDeskEditsStore(root).priceFen("book"))
        assertTrue(store.setPrice("book", -1))
        assertEquals(-1, WereadDeskEditsStore(root).priceFen("book"))
        assertEquals(2200, store.priceFen("other"))
    }

    /** 重建和普通偏好同步不丢价，不覆盖待办等其他用户数据。 */
    @Test
    fun unrelatedWritesPreserveManualEdits() {
        val root = folder.newFolder()
        val prefs = WereadStore(root)
        prefs.putString("todo_list", "[]")
        val store = WereadDeskEditsStore(root)
        assertTrue(store.setPrice("book", 1234))
        assertTrue(store.hide("book"))
        prefs.putString("asset_books", "{\"books\":[]}")
        prefs.putBool("bind_refresh", true)
        assertEquals(1234, WereadDeskEditsStore(root).priceFen("book"))
        assertEquals(setOf("book"), WereadDeskEditsStore(root).hiddenIds())
        assertEquals("[]", prefs.getString("todo_list"))
        assertEquals("1234", prefs.getString("menu_prices.book"))
    }

    /** 首次编辑兼容旧隐藏项，恢复空快照后不再从旧配置或文件回流。 */
    @Test
    fun hiddenSnapshotAndRestorePersistAcrossRecreation() {
        val root = folder.newFolder()
        File(root, "menu_hidden.json").writeText("""{"ids":"old, book,old"}""")
        val store = WereadDeskEditsStore(root)
        assertEquals(setOf("old", "book", "config"), store.hiddenIds(listOf("config")))
        assertTrue(store.hide("new", listOf("config")))
        assertEquals(setOf("old", "book", "config", "new"), WereadDeskEditsStore(root).hiddenIds())
        assertTrue(store.restoreHidden())
        assertTrue(WereadDeskEditsStore(root).hiddenIds(listOf("config")).isEmpty())
    }

    /** 空书目与不可写目录不能向界面报告保存成功。 */
    @Test
    fun failedWritesReturnFalse() {
        val root = folder.newFile()
        val store = WereadDeskEditsStore(root)
        assertFalse(store.setPrice("", 10))
        assertFalse(store.hide(""))
        assertFalse(store.setPrice("book", 10))
        assertFalse(store.hide("book"))
        assertFalse(store.restoreHidden())
    }

    /** 不合法的迁移字段按未知处理。 */
    @Test
    fun malformedLegacyPriceIsUnknown() {
        val root = folder.newFolder()
        File(root, "menu_prices.json").writeText("""{"book":{},"other":"bad"}""")
        val store = WereadDeskEditsStore(root)
        assertEquals(-1, store.priceFen("book"))
        assertEquals(-1, store.priceFen("other"))
        assertEquals(-1, store.priceFen("missing"))
    }

    /** 已预热的实例能看见其他编辑实例与普通偏好写入者的修改、清价和恢复。 */
    @Test
    fun warmedCacheObservesExternalWriters() {
        val root = folder.newFolder()
        val reader = WereadDeskEditsStore(root)
        val writer = WereadDeskEditsStore(root)
        assertEquals(-1, reader.priceFen("book"))
        assertTrue(reader.hiddenIds().isEmpty())
        assertTrue(writer.setPrice("book", 1234))
        assertEquals(1234, reader.priceFen("book"))
        assertTrue(writer.hide("book"))
        assertEquals(setOf("book"), reader.hiddenIds())
        WereadStore(root).putString("menu_prices.book", "5678")
        assertEquals(5678, reader.priceFen("book"))
        assertTrue(writer.setPrice("book", -1))
        assertEquals(-1, reader.priceFen("book"))
        assertTrue(writer.restoreHidden())
        assertTrue(reader.hiddenIds().isEmpty())
    }

    /** 原子替换即使长度、毫秒时间一致也不能复用旧快照；删除后兼容文件仍可回落。 */
    @Test
    fun cacheDetectsReplacementAndDeletion() {
        val root = folder.newFolder()
        val file = File(root, "kernel_prefs.json")
        file.writeText("""{"string":{"menu_prices.book":"1234"}}""")
        val reader = WereadDeskEditsStore(root)
        assertEquals(1234, reader.priceFen("book"))
        val replacement = File(root, "replacement.json")
        replacement.writeText("""{"string":{"menu_prices.book":"5678"}}""")
        replacement.setLastModified(file.lastModified())
        assertTrue(file.delete())
        assertTrue(replacement.renameTo(file))
        assertEquals(5678, reader.priceFen("book"))
        assertTrue(file.delete())
        File(root, "menu_prices.json").writeText("""{"book":7000}""")
        assertEquals(7000, reader.priceFen("book"))
        assertTrue(File(root, "menu_prices.json").delete())
        assertEquals(-1, reader.priceFen("book"))
    }

    /** 旧价格、隐藏文件变化可见，本实例写入后也立即丢弃缓存。 */
    @Test
    fun legacyChangesAndLocalWritesInvalidateCache() {
        val root = folder.newFolder()
        val price = File(root, "menu_prices.json")
        val hidden = File(root, "menu_hidden.json")
        price.writeText("""{"book":100}""")
        hidden.writeText("""{"ids":"first"}""")
        val store = WereadDeskEditsStore(root)
        assertEquals(100, store.priceFen("book"))
        assertEquals(setOf("first"), store.hiddenIds())
        price.writeText("""{"book":20000}""")
        hidden.writeText("""{"ids":"second,third"}""")
        assertEquals(20000, store.priceFen("book"))
        assertEquals(setOf("second", "third"), store.hiddenIds())
        assertTrue(store.setPrice("book", 0))
        assertEquals(0, store.priceFen("book"))
        assertTrue(store.restoreHidden())
        assertTrue(store.hiddenIds().isEmpty())
    }
}
