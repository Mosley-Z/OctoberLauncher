package com.shuiyu.octoberrain.feature.files.data

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.IOException
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
    /** 覆盖计划只校验源文件，并带上 PNG 编码与统一质量，不创建临时文件。 */
    @Test
    fun `覆盖计划与源文件同目录`() {
        withTempDir { dir ->
            val source = File(dir, "photo.png").apply { writeText("old") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            assertEquals(source.absolutePath, plan.target.absolutePath)
            assertEquals(OverwriteEncoding.PNG, plan.encoding)
            assertEquals(100, plan.quality)
            assertEquals(listOf(source.name), dir.list()?.toList())
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

    /** JPEG 源文件的覆盖计划选择 JPEG 编码，不创建临时文件。 */
    @Test
    fun `JPEG 源文件选择 JPEG 编码`() {
        withTempDir { dir ->
            val source = File(dir, "photo.jpg").apply { writeText("old") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            assertEquals(OverwriteEncoding.JPEG, plan.encoding)
            assertEquals(listOf(source.name), dir.list()?.toList())
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
            val sentinel = File(dir, OVERWRITE_TEMP_PREFIX + source.name).apply { writeText("sentinel") }
            val plan = planOverwrite(source)
            assertNotNull(plan)
            plan ?: return@withTempDir
            val result =
                writeOverwrite(plan, { temp ->
                    assertEquals(dir.absolutePath, temp.parentFile?.absolutePath)
                    assertTrue(temp.isFile)
                    assertTrue(temp.name.startsWith(OVERWRITE_TEMP_PREFIX))
                    assertFalse(temp == sentinel)
                    temp.writeText("new")
                    true
                })
            assertEquals(ExportResult.Success(source.name, source.absolutePath), result)
            assertEquals("new", source.readText())
            assertEquals("sentinel", sentinel.readText())
            assertEquals(setOf(source.name, sentinel.name), dir.list()?.toSet())
        }
    }

    /** 原子替换失败时源路径保持原状，临时文件由调用方拥有并清理。 */
    @Test
    fun `替换失败删除临时文件`() {
        withTempDir { dir ->
            val source = File(dir, "photo.png").apply { writeText("old") }
            val temp = File.createTempFile(OVERWRITE_TEMP_PREFIX, ".tmp", dir).apply { writeText("new") }
            // 删除源文件并在原路径建立非空目录，使替换操作无法覆盖
            assertTrue(source.delete())
            assertTrue(File(source, "child").apply { parentFile?.mkdirs() }.createNewFile())
            assertFalse(replaceAtomically(temp, source))
            assertEquals("new", temp.readText())
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

    @Test
    fun `计划校验保留陌生文件且不创建临时文件`() {
        withTempDir { dir ->
            val source = File(dir, "photo.jpg").apply { writeText("old") }
            val sentinel = File(dir, OVERWRITE_TEMP_PREFIX + source.name).apply { writeText("sentinel") }
            assertNotNull(planOverwrite(source))
            assertNotNull(planOverwrite(source))
            assertEquals("old", source.readText())
            assertEquals("sentinel", sentinel.readText())
            assertEquals(setOf(source.name, sentinel.name), dir.list()?.toSet())
        }
    }

    @Test
    fun `渲染失败保留源图与陌生文件`() {
        withTempDir { dir ->
            val source = File(dir, "photo.jpg").apply { writeText("invalid image") }
            val sentinel = File(dir, OVERWRITE_TEMP_PREFIX + source.name).apply { writeText("sentinel") }
            val result = EditExport.overwrite(ContextWrapper(null), source.absolutePath, emptyList())
            assertTrue(result is ExportResult.Failure)
            assertEquals("invalid image", source.readText())
            assertEquals("sentinel", sentinel.readText())
            assertEquals(setOf(source.name, sentinel.name), dir.list()?.toSet())
        }
    }

    @Test
    fun `写入及替换失败仅清理本次临时文件`() {
        for (stage in listOf("write false", "write throws", "replace false", "replace throws")) {
            withTempDir { dir ->
                val source = File(dir, "photo.jpg").apply { writeText("old") }
                val sentinel = File(dir, OVERWRITE_TEMP_PREFIX + source.name).apply { writeText("sentinel") }
                val plan = requireNotNull(planOverwrite(source))
                val result =
                    writeOverwrite(
                        plan,
                        write = { temp ->
                            temp.writeText("partial")
                            if (stage == "write throws") throw IOException("write failed")
                            stage != "write false"
                        },
                        replace = { _, _ ->
                            assertFalse(stage.startsWith("write"))
                            if (stage == "replace throws") throw IOException("replace failed")
                            false
                        },
                    )
                assertTrue(stage, result is ExportResult.Failure)
                assertEquals(stage, "old", source.readText())
                assertEquals(stage, "sentinel", sentinel.readText())
                assertEquals(stage, setOf(source.name, sentinel.name), dir.list()?.toSet())
            }
        }
    }

    @Test
    fun `同时覆盖各自独占临时文件`() {
        withTempDir { dir ->
            val source = File(dir, "photo.jpg").apply { writeText("old") }
            val sentinel = File(dir, OVERWRITE_TEMP_PREFIX + source.name).apply { writeText("sentinel") }
            val plan = requireNotNull(planOverwrite(source))
            val result =
                writeOverwrite(plan, { outer ->
                    outer.writeText("outer")
                    val nested =
                        writeOverwrite(plan, { inner ->
                            assertFalse(outer == inner)
                            inner.writeText("inner")
                            true
                        })
                    assertTrue(nested is ExportResult.Success)
                    assertEquals("outer", outer.readText())
                    true
                })
            assertTrue(result is ExportResult.Success)
            assertEquals("outer", source.readText())
            assertEquals("sentinel", sentinel.readText())
            assertEquals(setOf(source.name, sentinel.name), dir.list()?.toSet())
        }
    }

    private companion object {
        private val UTC: TimeZone = TimeZone.getTimeZone("GMT")
    }
}
