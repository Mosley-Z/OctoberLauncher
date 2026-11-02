package com.shuiyu.octoberrain.feature.files.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.TimeZone

/**
 * [EditExport] 覆盖写入的纯 JVM 测试。
 *
 * 覆盖临时文件与源文件同目录、只读源文件拒绝写入、原子替换后内容更新与临时文件清理、
 * 替换失败时的回退与临时文件删除、扩展名到编码格式的映射，以及另存文件名规则保持不变。
 * MediaStore 同步、JPEG 写盘后的解码与带透明度位图的铺底依赖 Android 框架，
 * 放在真机验收覆盖。
 */
class EditExportOverwriteTest {
    /** 覆盖计划把临时文件放在源文件同目录，并以固定前缀命名，同时带上 PNG 编码与统一质量。 */
    @Test
    fun `覆盖计划与源文件同目录`() {
        withTempDir { dir ->
            val source = File(dir, "photo.png").apply { writeText("old") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            assertEquals(source.absolutePath, plan.target.absolutePath)
            assertEquals(dir.absolutePath, plan.temp.parentFile?.absolutePath)
            assertTrue(plan.temp.name.startsWith(OVERWRITE_TEMP_PREFIX))
            assertEquals(OverwriteEncoding.PNG, plan.encoding)
            assertEquals(100, plan.quality)
        }
    }

    /** 扩展名到编码格式的映射覆盖 jpg、jpeg、png 与 webp，并忽略大小写。 */
    @Test
    fun `扩展名映射编码格式`() {
        assertEquals(OverwriteEncoding.JPEG, overwriteEncodingFor("photo.jpg", 30))
        assertEquals(OverwriteEncoding.JPEG, overwriteEncodingFor("photo.jpeg", 30))
        assertEquals(OverwriteEncoding.PNG, overwriteEncodingFor("photo.png", 30))
        assertEquals(OverwriteEncoding.WEBP, overwriteEncodingFor("photo.webp", 30))
        assertEquals(OverwriteEncoding.JPEG, overwriteEncodingFor("PHOTO.JPG", 30))
        assertEquals(OverwriteEncoding.JPEG, overwriteEncodingFor("Photo.JpEg", 30))
        assertEquals(OverwriteEncoding.PNG, overwriteEncodingFor("photo.PNG", 30))
        assertEquals(OverwriteEncoding.WEBP, overwriteEncodingFor("photo.WeBp", 30))
    }

    /** Android 10 以下不支持 WebP 覆盖，映射返回空。 */
    @Test
    fun `Android 10 以下 WebP 不可覆盖`() {
        assertNull(overwriteEncodingFor("photo.webp", 29))
        assertNull(overwriteEncodingFor("photo.WEBP", 26))
    }

    /** 不受支持的扩展名与无扩展名映射为空，且不生成覆盖计划。 */
    @Test
    fun `不支持格式不生成覆盖计划`() {
        val names =
            listOf("photo.heic", "photo.heif", "photo.dng", "photo.gif", "photo.bmp", "photo.avif", "photo")
        withTempDir { dir ->
            for (name in names) {
                assertNull("扩展名 $name 应映射为空", overwriteEncodingFor(name, 30))
                val source = File(dir, name).apply { writeText("old") }
                assertNull("扩展名 $name 不应生成覆盖计划", planOverwrite(source))
            }
        }
    }

    /** JPEG 源文件的覆盖计划选择 JPEG 编码，临时文件保留原文件名。 */
    @Test
    fun `JPEG 源文件选择 JPEG 编码`() {
        withTempDir { dir ->
            val source = File(dir, "photo.jpg").apply { writeText("old") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            assertEquals(OverwriteEncoding.JPEG, plan.encoding)
            assertEquals(OVERWRITE_TEMP_PREFIX + "photo.jpg", plan.temp.name)
        }
    }

    /** 源文件不可写时不生成覆盖计划，由调用方提示改用另存。 */
    @Test
    fun `只读源文件不生成计划`() {
        withTempDir { dir ->
            val source = File(dir, "photo.png").apply { writeText("old") }
            source.setReadOnly()
            try {
                Assume.assumeFalse("当前文件系统未提供只读标记，跳过该用例", source.canWrite())
                assertNull(planOverwrite(source))
            } finally {
                source.setWritable(true)
            }
        }
    }

    /** 原子替换后源文件内容更新，临时文件不再存在。 */
    @Test
    fun `原子替换更新内容并清理临时文件`() {
        withTempDir { dir ->
            val source = File(dir, "photo.png").apply { writeText("old") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            plan.temp.writeText("new")
            assertTrue(replaceAtomically(plan))
            assertEquals("new", source.readText())
            assertFalse(plan.temp.exists())
        }
    }

    /** 替换失败时删除临时文件，源路径保持原状。 */
    @Test
    fun `替换失败删除临时文件`() {
        withTempDir { dir ->
            val source = File(dir, "photo.png").apply { writeText("old") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            // 删除源文件并在原路径建立非空目录，使替换操作无法覆盖
            assertTrue(source.delete())
            assertTrue(File(source, "child").apply { parentFile?.mkdirs() }.createNewFile())
            plan.temp.writeText("new")
            assertFalse(replaceAtomically(plan))
            assertFalse(plan.temp.exists())
            assertTrue(source.isDirectory)
        }
    }

    /** 源文件不存在时不生成覆盖计划。 */
    @Test
    fun `缺失源文件不生成计划`() {
        withTempDir { dir ->
            assertNull(planOverwrite(File(dir, "missing.png")))
        }
    }

    /** 覆盖写入不改变另存的文件名规则。 */
    @Test
    fun `另存文件名规则不变`() {
        assertEquals("photo-编-19700101-000000.png", EditExport.displayName("photo", 0L, UTC))
        val dir = Files.createTempDirectory("edit-overwrite").toFile()
        try {
            assertEquals("photo-编-19700101-000000.png", EditExport.uniqueName(dir, "photo", 0L, UTC))
        } finally {
            dir.deleteRecursively()
        }
    }

    /** 在临时目录中执行用例，结束时递归清理。 */
    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("edit-overwrite").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        private val UTC: TimeZone = TimeZone.getTimeZone("GMT")
    }
}
