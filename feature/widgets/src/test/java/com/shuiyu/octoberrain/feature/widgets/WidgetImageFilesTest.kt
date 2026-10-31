package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WidgetImageFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun image(path: String): File =
        File(temporary.root, path).apply {
            parentFile.mkdirs()
            writeText("fixture")
        }

    @Test fun `换图不把旧私有副本变成轮播`() {
        image("widget-image/old.img")
        val selected = image("widget-image/new.img")
        assertEquals(listOf(selected), WidgetImageFiles.resolve(selected.path, temporary.root))
        assertEquals(emptyList<File>(), WidgetImageFiles.resolve(null, temporary.root))
        selected.delete()
        assertEquals(emptyList<File>(), WidgetImageFiles.resolve(selected.path, temporary.root))
    }

    @Test fun `旧目录轮播只包含图片并保留选中路径`() {
        val first = image("external/a.png")
        val second = image("external/b.jpg")
        image("external/notes.txt")
        assertEquals(listOf(first, second), WidgetImageFiles.resolve(second.path, temporary.root))
    }

    @Test fun `跨页仍引用的副本和外部原图不可清理`() {
        val selected = image("widget-image/page-1/new.img")
        assertNull(WidgetImageFiles.unusedManaged(selected.path, temporary.root, listOf(selected.path)))
        assertEquals(selected, WidgetImageFiles.unusedManaged(selected.path, temporary.root, emptyList()))
        val external = image("external/a.png")
        assertNull(WidgetImageFiles.unusedManaged(external.path, temporary.root, emptyList()))
    }

    @Test fun `历史私有目录和分页面保持单图`() {
        for (directory in listOf("widgets", "widget-image/page-2")) {
            image("$directory/old.img")
            val selected = image("$directory/new.img")
            assertEquals(listOf(selected), WidgetImageFiles.resolve(selected.path, temporary.root))
        }
    }
}
