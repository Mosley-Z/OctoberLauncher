package com.shuiyu.octoberrain.feature.widgets

import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.PageWidgetConfig
import com.shuiyu.octoberrain.core.common.WidgetImageSelection
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

    @Test fun explicitSelectionPreservesOrderAndSourceCorrespondence() {
        val first = image("widget-image/a.img")
        val second = image("widget-image/b.img")
        image("widget-image/historical.img")
        val selections =
            listOf(
                WidgetImageSelection(second.path, "missing"),
                WidgetImageSelection(first.path, "available"),
            )
        val sources = mutableListOf<String?>()
        val result =
            WidgetImageFiles.resolveSelection(selections, second.path, null, temporary.root) {
                sources.add(it)
                it == "missing"
            }
        assertEquals(listOf("missing", "available"), sources)
        assertEquals(listOf(first), result)
        assertEquals(
            listOf(second, first),
            WidgetImageFiles.resolveSelection(selections, null, null, temporary.root) { false },
        )
        assertEquals(
            emptyList<File>(),
            WidgetImageFiles.resolveSelection(selections, first.path, null, temporary.root) { true },
        )
    }

    @Test fun explicitExternalSelectionNeverEnumeratesSiblingsOrFallsBack() {
        val selected = image("external/selected.png")
        image("external/unrelated.jpg")
        val selections = listOf(WidgetImageSelection(selected.path, "source"))
        assertEquals(
            listOf(selected),
            WidgetImageFiles.resolveSelection(selections, selected.path, null, temporary.root) { false },
        )
        selected.delete()
        assertEquals(
            emptyList<File>(),
            WidgetImageFiles.resolveSelection(selections, "external/unrelated.jpg", null, temporary.root) { false },
        )
    }

    @Test fun cleanupCountsEveryPageListAndLegacyPath() {
        val files = (1..4).map { image("widget-image/$it.img") }
        val desktop =
            DesktopConfig(
                widgetImagePath = files[0].path,
                widgetImages = listOf(WidgetImageSelection(files[1].path)),
                pageWidgets =
                    mapOf(
                        1 to
                            PageWidgetConfig(
                                widgetImagePath = files[2].path,
                                widgetImages = listOf(WidgetImageSelection(files[3].path)),
                            ),
                    ),
            )
        val references = WidgetImageFiles.references(desktop)
        files.forEach { assertNull(WidgetImageFiles.unusedManaged(it.path, temporary.root, references)) }
        val attempt = image("widget-image/attempt.img")
        assertEquals(attempt, WidgetImageFiles.unusedManaged(attempt.path, temporary.root, references))
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
