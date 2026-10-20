package com.shuiyu.octoberrain.feature.files

import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.ui.FileText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件条目文本测试：核对大小、时间与目录、文件副标题的写法。 */
class FileTextTest {
    /** 字节数按 1024 进制换算并保留一位小数。 */
    @Test
    fun `大小格式化按 1024 进制`() {
        assertEquals("0.0 B", FileText.size(0))
        assertEquals("1023.0 B", FileText.size(1023))
        assertEquals("1.0 KB", FileText.size(1024))
        assertEquals("1.5 MB", FileText.size(1024L * 1024L * 3L / 2L))
        assertEquals("2.0 GB", FileText.size(1024L * 1024L * 1024L * 2L))
    }

    /** 非正时间戳返回空串，正时间戳输出分钟级时间。 */
    @Test
    fun `时间格式化输出分钟级文本`() {
        assertEquals("", FileText.time(0))
        assertEquals("", FileText.time(-1))
        assertTrue(FileText.time(1_700_000_000_000L).matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }

    /** 目录副标题只含时间，文件副标题含大小与时间。 */
    @Test
    fun `目录与文件副标题写法不同`() {
        val dir = FileItem.dir(name = "Books", path = "/storage/emulated/0/Books", mtime = 1_700_000_000_000L)
        val file =
            FileItem.file(
                name = "cover.png",
                path = "/storage/emulated/0/Books/cover.png",
                size = 2048,
                mtime = 1_700_000_000_000L,
                mime = "image/png",
            )
        assertEquals(FileText.time(dir.mtime), FileText.meta(dir))
        assertEquals("2.0 KB  " + FileText.time(file.mtime), FileText.meta(file))
    }
}
