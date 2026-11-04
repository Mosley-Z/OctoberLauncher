package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileOps
import com.shuiyu.octoberrain.feature.files.data.deleteFileEntryDeep
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
import java.nio.file.Path

/**
 * 批量复制、移动与删除测试。
 *
 * 覆盖文件与目录两种条目、同名自动追加序号、目标位于源内部时拒绝，
 * 以及目录连同内容一并删除的语义；全部在临时目录内执行，不触碰真实存储。
 */
class FileOpsBatchTest {
    @get:Rule
    val temporary = TemporaryFolder()

    /** 复制文件到目标目录时保留原名称与内容。 */
    @Test
    fun `复制文件保留原名称`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = writeText(source, "note.txt", "内容")

        val copied = FileOps.copyEntry(entry, target)

        assertEquals(File(target, "note.txt"), copied)
        assertEquals("内容", File(target, "note.txt").readText())
        assertTrue(entry.exists())
    }

    /** 复制同名文件时自动追加序号，目标目录原有文件保持不变。 */
    @Test
    fun `复制同名文件自动追加序号`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = writeText(source, "note.txt", "新内容")
        writeText(target, "note.txt", "旧内容")

        val copied = FileOps.copyEntry(entry, target)

        assertEquals(File(target, "note_2.txt"), copied)
        assertEquals("新内容", File(target, "note_2.txt").readText())
        assertEquals("旧内容", File(target, "note.txt").readText())
    }

    /** 复制目录时递归保留子目录与文件，同名目录同样追加序号。 */
    @Test
    fun `复制目录递归保留子结构`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val album = File(source, "album").apply { mkdirs() }
        writeText(album, "cover.txt", "封面")
        val nested = File(album, "sub").apply { mkdirs() }
        writeText(nested, "page.txt", "内页")
        File(target, "album").apply { mkdirs() }

        val copied = FileOps.copyEntry(album, target)

        assertEquals(File(target, "album_2"), copied)
        assertEquals("封面", File(target, "album_2/cover.txt").readText())
        assertEquals("内页", File(target, "album_2/sub/page.txt").readText())
        assertTrue(File(album, "cover.txt").exists())
    }

    /** 目标目录位于源目录内部时拒绝复制，也不留下部分结果。 */
    @Test
    fun `复制目录到自身内部被拒绝`() {
        val source = temporary.newFolder("source")
        val album = File(source, "album").apply { mkdirs() }
        val nested = File(album, "sub").apply { mkdirs() }

        assertNull(FileOps.copyEntry(album, album))
        assertNull(FileOps.copyEntry(album, nested))
        assertFalse(File(album, "album").exists())
        assertFalse(File(nested, "album").exists())
    }

    /** 移动文件后源文件消失，目标目录出现同名文件。 */
    @Test
    fun `移动文件后源文件消失`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = writeText(source, "note.txt", "内容")

        val moved = FileOps.moveEntry(entry, target)

        assertEquals(File(target, "note.txt"), moved)
        assertEquals("内容", File(target, "note.txt").readText())
        assertFalse(entry.exists())
    }

    /** 移动同名文件时自动追加序号，目标目录原有文件保持不变。 */
    @Test
    fun `移动同名文件自动追加序号`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = writeText(source, "note.txt", "新内容")
        writeText(target, "note.txt", "旧内容")

        val moved = FileOps.moveEntry(entry, target)

        assertEquals(File(target, "note_2.txt"), moved)
        assertEquals("新内容", File(target, "note_2.txt").readText())
        assertEquals("旧内容", File(target, "note.txt").readText())
        assertFalse(entry.exists())
    }

    /** 移动目录后源目录整体消失，内容完整迁移。 */
    @Test
    fun `移动目录后源目录消失`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val album = File(source, "album").apply { mkdirs() }
        writeText(album, "cover.txt", "封面")

        val moved = FileOps.moveEntry(album, target)

        assertEquals(File(target, "album"), moved)
        assertEquals("封面", File(target, "album/cover.txt").readText())
        assertFalse(album.exists())
    }

    /** 移动目录到自身或自身内部时拒绝执行，源目录保持原状。 */
    @Test
    fun `移动目录到自身内部被拒绝`() {
        val source = temporary.newFolder("source")
        val album = File(source, "album").apply { mkdirs() }
        val nested = File(album, "sub").apply { mkdirs() }

        assertNull(FileOps.moveEntry(album, album))
        assertNull(FileOps.moveEntry(album, nested))
        assertTrue(album.exists())
        assertTrue(nested.exists())
        assertEquals(1, album.listFiles()?.size ?: 0)
    }

    /** 删除目录时连同全部子内容一并移除。 */
    @Test
    fun `删除目录连同内容`() {
        val source = temporary.newFolder("source")
        val album = File(source, "album").apply { mkdirs() }
        val nested = File(album, "sub").apply { mkdirs() }
        writeText(album, "cover.txt", "封面")
        writeText(nested, "page.txt", "内页")

        assertTrue(deleteFileEntryDeep(album))
        assertFalse(album.exists())
    }

    /** 删除单个文件成功，删除不存在的条目返回失败。 */
    @Test
    fun `删除文件与不存在条目`() {
        val source = temporary.newFolder("source")
        val entry = writeText(source, "note.txt", "内容")

        assertTrue(deleteFileEntryDeep(entry))
        assertFalse(entry.exists())
        assertFalse(deleteFileEntryDeep(entry))
    }

    /** 批量语义下复制失败不删除源条目，返回 null 供调用方统计失败数。 */
    @Test
    fun `复制源不存在时返回失败`() {
        val target = temporary.newFolder("target")

        assertNull(FileOps.copyEntry(File(target, "missing.txt"), target))
        assertNull(FileOps.moveEntry(File(target, "missing.txt"), target))
    }

    /** 复制文件到原目录时允许执行，按序号生成同名副本且原文件保留。 */
    @Test
    fun `复制文件到原目录生成序号副本`() {
        val source = temporary.newFolder("source")
        val entry = writeText(source, "note.txt", "内容")

        val copied = FileOps.copyEntry(entry, source)

        assertEquals(File(source, "note_2.txt"), copied)
        assertEquals("内容", File(source, "note_2.txt").readText())
        assertEquals("内容", entry.readText())
    }

    /** 复制文件到源所在目录的子目录时允许执行。 */
    @Test
    fun `复制文件到原目录子目录`() {
        val source = temporary.newFolder("source")
        val entry = writeText(source, "note.txt", "内容")
        val sub = File(source, "sub").apply { mkdirs() }

        val copied = FileOps.copyEntry(entry, sub)

        assertEquals(File(sub, "note.txt"), copied)
        assertEquals("内容", File(sub, "note.txt").readText())
        assertTrue(entry.exists())
    }

    /** 复制目录到其父目录时生成同级序号副本，源目录保持不变。 */
    @Test
    fun `复制目录到父目录生成同级副本`() {
        val source = temporary.newFolder("source")
        val album = File(source, "album").apply { mkdirs() }
        writeText(album, "cover.txt", "封面")

        val copied = FileOps.copyEntry(album, source)

        assertEquals(File(source, "album_2"), copied)
        assertEquals("封面", File(source, "album_2/cover.txt").readText())
        assertTrue(File(album, "cover.txt").exists())
    }

    /** 移动文件到其所在目录时视为无操作，直接返回源文件且不生成副本。 */
    @Test
    fun `移动文件到原目录为空操作`() {
        val source = temporary.newFolder("source")
        val entry = writeText(source, "note.txt", "内容")

        val moved = FileOps.moveEntry(entry, source)

        assertEquals(entry, moved)
        assertEquals(listOf("note.txt"), source.list()?.toList())
        assertEquals("内容", entry.readText())
    }

    /** 移动目录到其所在目录时视为无操作，源目录与同级序号副本均不出现。 */
    @Test
    fun `移动目录到原目录为空操作`() {
        val source = temporary.newFolder("source")
        val album = File(source, "album").apply { mkdirs() }
        writeText(album, "cover.txt", "封面")

        val moved = FileOps.moveEntry(album, source)

        assertEquals(album, moved)
        assertTrue(File(album, "cover.txt").exists())
        assertFalse(File(source, "album_2").exists())
    }

    @Test
    fun `同卷移动文件和目录不回退复制`() {
        for (directory in listOf(false, true)) {
            val source = temporary.newFolder()
            val target = temporary.newFolder()
            val entry = File(source, "entry")
            val payload = "source".toByteArray()
            if (directory) {
                assertTrue(entry.mkdir())
                File(entry, "payload.txt").writeBytes(payload)
            } else {
                entry.writeBytes(payload)
            }
            var copyProbeCount = 0
            var traversalCount = 0
            val observed =
                object : File(entry.path) {
                    override fun isDirectory(): Boolean {
                        copyProbeCount++
                        return super.isDirectory()
                    }

                    override fun listFiles(): Array<File>? {
                        traversalCount++
                        return super.listFiles()
                    }
                }

            val destination = File(target, "entry")
            assertEquals(destination, FileOps.moveEntry(observed, target))

            val movedPayload = if (directory) File(destination, "payload.txt") else destination
            assertFalse(entry.exists())
            assertArrayEquals(payload, movedPayload.readBytes())
            assertEquals(0, copyProbeCount)
            assertEquals(0, traversalCount)
        }
    }

    @Test
    fun `文件自动命名耗尽失败且保留全部内容`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = writeText(source, "note.txt", "source")
        occupyNames(target, "note", ".txt")

        assertNull(FileOps.uniqueName(target, entry.name))
        assertNull(FileOps.copyEntry(entry, target))
        assertNull(FileOps.moveEntry(entry, target))
        assertEquals("source", entry.readText())
        assertOccupiedNames(target, "note", ".txt")
    }

    @Test
    fun `目录自动命名耗尽失败且不合并目录`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = File(source, "album").apply { mkdir() }
        writeText(entry, "new.txt", "source")
        occupyNames(target, "album", "", directories = true)

        assertNull(FileOps.copyEntry(entry, target))
        assertNull(FileOps.moveEntry(entry, target))
        assertEquals("source", File(entry, "new.txt").readText())
        assertOccupiedNames(target, "album", "", directories = true)
    }

    @Test
    fun `顶层命名后文件或目录被抢占时失败`() {
        for (directory in listOf(false, true)) {
            for (moving in listOf(false, true)) {
                val source = temporary.newFolder()
                val target = temporary.newFolder()
                val original = File(source, "entry")
                if (directory) {
                    assertTrue(original.mkdir())
                    writeText(original, "new.txt", "source")
                } else {
                    original.writeText("source")
                }
                val destination = File(target, "entry")
                val raced =
                    object : File(original.path) {
                        override fun toPath(): Path {
                            if (moving) occupyDestination(destination, directory)
                            return super.toPath()
                        }

                        override fun isDirectory(): Boolean {
                            if (!moving) occupyDestination(destination, directory)
                            return directory
                        }
                    }

                assertNull(if (moving) FileOps.moveEntry(raced, target) else FileOps.copyEntry(raced, target))
                if (directory) {
                    assertEquals(listOf("sentinel.txt"), destination.list()?.toList())
                    assertEquals("sentinel", File(destination, "sentinel.txt").readText())
                    assertEquals("source", File(original, "new.txt").readText())
                } else {
                    assertEquals("sentinel", destination.readText())
                    assertEquals("source", original.readText())
                }
            }
        }
    }

    @Test
    fun `递归命名耗尽返回失败并保留原目标`() {
        for (directory in listOf(false, true)) {
            val source = temporary.newFolder()
            val target = temporary.newFolder()
            val original = File(source, "album").apply { mkdir() }
            val child = File(original, "entry")
            if (directory) {
                assertTrue(child.mkdir())
                writeText(child, "new.txt", "source")
            } else {
                child.writeText("source")
            }
            val destination = File(target, "album")
            val observed =
                object : File(original.path) {
                    override fun listFiles(): Array<File> {
                        occupyNames(destination, "entry", "", directory)
                        return arrayOf(child)
                    }
                }

            assertNull(FileOps.copyEntry(observed, target))
            assertOccupiedNames(destination, "entry", "", directory)
            assertEquals("source", (if (directory) File(child, "new.txt") else child).readText())
        }
    }

    @Test
    fun `递归命名后文件或目录被抢占时失败`() {
        for (directory in listOf(false, true)) {
            val source = temporary.newFolder()
            val target = temporary.newFolder()
            val original = File(source, "album").apply { mkdir() }
            val child = File(original, "entry")
            if (directory) {
                assertTrue(child.mkdir())
                writeText(child, "new.txt", "source")
            } else {
                child.writeText("source")
            }
            val destination = File(target, "album/entry")
            val racedChild =
                object : File(child.path) {
                    override fun isDirectory(): Boolean {
                        if (directory) {
                            assertTrue(destination.mkdir())
                            writeText(destination, "sentinel.txt", "sentinel")
                        } else {
                            destination.writeText("sentinel")
                        }
                        return directory
                    }
                }
            val observed =
                object : File(original.path) {
                    override fun listFiles(): Array<File> = arrayOf(racedChild)
                }

            assertNull(FileOps.copyEntry(observed, target))
            if (directory) assertEquals(listOf("sentinel.txt"), destination.list()?.toList())
            assertEquals("sentinel", (if (directory) File(destination, "sentinel.txt") else destination).readText())
            assertEquals("source", (if (directory) File(child, "new.txt") else child).readText())
        }
    }

    @Test
    fun `递归已有同名文件或目录仍正常追加序号`() {
        for (directory in listOf(false, true)) {
            val source = temporary.newFolder()
            val target = temporary.newFolder()
            val original = File(source, "album").apply { mkdir() }
            val child = File(original, "entry")
            if (directory) {
                assertTrue(child.mkdir())
                writeText(child, "new.txt", "source")
            } else {
                child.writeText("source")
            }
            val destination = File(target, "album")
            val observed =
                object : File(original.path) {
                    override fun listFiles(): Array<File> {
                        val existing = File(destination, "entry")
                        if (directory) {
                            assertTrue(existing.mkdir())
                            writeText(existing, "sentinel.txt", "sentinel")
                        } else {
                            existing.writeText("sentinel")
                        }
                        return arrayOf(child)
                    }
                }

            assertEquals(destination, FileOps.copyEntry(observed, target))
            val existing = File(destination, "entry")
            val copied = File(destination, "entry_2")
            assertEquals("sentinel", (if (directory) File(existing, "sentinel.txt") else existing).readText())
            assertEquals("source", (if (directory) File(copied, "new.txt") else copied).readText())
            assertEquals("source", (if (directory) File(child, "new.txt") else child).readText())
        }
    }

    @Test
    fun `明确覆盖复制仍替换不同内容`() {
        val source = temporary.newFolder("source")
        val target = temporary.newFolder("target")
        val entry = writeText(source, "note.txt", "source")
        val existing = writeText(target, "note.txt", "old")

        assertTrue(FileOps.copy(entry, existing))
        assertEquals("source", existing.readText())
        assertEquals("source", entry.readText())
    }

    private fun occupyDestination(
        destination: File,
        directory: Boolean,
    ) {
        if (!destination.exists()) {
            if (directory) {
                assertTrue(destination.mkdir())
                writeText(destination, "sentinel.txt", "sentinel")
            } else {
                destination.writeText("sentinel")
            }
        }
    }

    private fun occupyNames(
        dir: File,
        base: String,
        extension: String,
        directories: Boolean = false,
    ) {
        for (index in 1..999) {
            val name = base + (if (index == 1) "" else "_$index") + extension
            val entry = File(dir, name)
            if (directories) {
                assertTrue(entry.mkdir())
                writeText(entry, "sentinel.txt", "sentinel-$index")
            } else {
                entry.writeText("sentinel-$index")
            }
        }
    }

    private fun assertOccupiedNames(
        dir: File,
        base: String,
        extension: String,
        directories: Boolean = false,
    ) {
        assertEquals(999, dir.list()?.size)
        for (index in 1..999) {
            val name = base + (if (index == 1) "" else "_$index") + extension
            val entry = File(dir, name)
            if (directories) assertEquals(listOf("sentinel.txt"), entry.list()?.toList())
            assertEquals("sentinel-$index", (if (directories) File(entry, "sentinel.txt") else entry).readText())
        }
    }

    private fun writeText(
        dir: File,
        name: String,
        content: String,
    ): File {
        val file = File(dir, name)
        assertNotNull(file.parentFile)
        file.writeText(content)
        return file
    }
}
