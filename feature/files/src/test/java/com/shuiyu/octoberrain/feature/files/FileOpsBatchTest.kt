package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileOps
import com.shuiyu.octoberrain.feature.files.data.deleteFileEntryDeep
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
