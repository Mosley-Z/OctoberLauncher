package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件条目测试：核对类型判断、名称拆分与路径换算。 */
class FileItemTest {
    /** 图片按 MIME 前缀判断，目录不视为图片。 */
    @Test
    fun `图片按 MIME 前缀判断`() {
        assertTrue(itemOf("photo.png", "image/png").isImage)
        assertFalse(itemOf("notes.txt", "text/plain").isImage)
        assertFalse(FileItem.dir("photo.png", "/tmp/photo.png", 0L).isImage)
    }

    /** 可编辑图片排除动图与 HEIF。 */
    @Test
    fun `可编辑图片排除动图与 HEIF`() {
        assertTrue(itemOf("photo.jpg", "image/jpeg").isEditableImage)
        assertFalse(itemOf("anim.gif", "image/gif").isEditableImage)
        assertFalse(itemOf("photo.heif", "image/heif").isEditableImage)
        assertFalse(itemOf("notes.txt", "text/plain").isEditableImage)
    }

    /** 扩展名包含前导点，隐藏文件不拆分。 */
    @Test
    fun `扩展名包含前导点`() {
        assertEquals(".png", itemOf("photo.png", "image/png").extension())
        assertEquals("", itemOf("README", "text/plain").extension())
        assertEquals("", itemOf(".nomedia", "application/octet-stream").extension())
    }

    /** 基名去掉最后一个扩展名。 */
    @Test
    fun `基名去掉扩展名`() {
        assertEquals("photo", itemOf("photo.png", "image/png").baseName())
        assertEquals("archive.tar", itemOf("archive.tar.gz", "application/gzip").baseName())
        assertEquals(".nomedia", itemOf(".nomedia", "application/octet-stream").baseName())
    }

    /** 目录工厂不记录字节数与 MIME。 */
    @Test
    fun `目录工厂不记录字节数与 MIME`() {
        val directory = FileItem.dir("Pictures", "/storage/emulated/0/Pictures", 123L)
        assertTrue(directory.dir)
        assertEquals(0L, directory.size)
        assertEquals("", directory.mime)
        assertEquals(0L, directory.sizeOrZero)
    }

    /** 文件工厂在 MIME 缺失时保存空串。 */
    @Test
    fun `文件工厂处理空 MIME`() {
        val file = FileItem.file("unknown.bin", "/tmp/unknown.bin", 10L, 20L, null)
        assertFalse(file.dir)
        assertEquals("", file.mime)
        assertEquals(10L, file.sizeOrZero)
    }

    /** 负数字节数按 0 处理。 */
    @Test
    fun `负数字节数按零处理`() {
        assertEquals(0L, itemOf("broken.bin", "application/octet-stream", size = -1L).sizeOrZero)
    }

    /** 上级目录取最后一个分隔符之前的部分。 */
    @Test
    fun `上级目录取最后一段分隔符之前`() {
        val item = itemOf("a.png", "image/png", path = "/storage/emulated/0/Pictures/a.png")
        assertEquals("/storage/emulated/0/Pictures", item.parentPath)
        assertEquals("", itemOf("a.png", "image/png", path = "/a.png").parentPath)
        assertEquals("", itemOf("a.png", "image/png", path = "a.png").parentPath)
    }

    private fun itemOf(
        name: String,
        mime: String,
        size: Long = 0L,
        path: String = "/tmp/$name",
    ): FileItem = FileItem.file(name, path, size, 0L, mime)
}
