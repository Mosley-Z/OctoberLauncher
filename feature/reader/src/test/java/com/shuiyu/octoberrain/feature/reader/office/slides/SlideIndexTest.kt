package com.shuiyu.octoberrain.feature.reader.office.slides

import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SlideIndexTest {
    @Test fun presentationOrderAndTableTextArePreserved() {
        fixture(3).use { fixture ->
            ZipArchive(fixture.file).use { zip ->
                val index = SlideIndex(zip)
                assertEquals(
                    listOf("ppt/slides/slide3.xml", "ppt/slides/slide2.xml", "ppt/slides/slide1.xml"),
                    index.paths,
                )
                assertTrue(index.texts[0].contains("中文第3页"))
                assertTrue(index.texts[0].contains("单元格"))
            }
        }
    }

    @Test fun twoHundredSlidesProduceOnlyOneSlidePackage() {
        fixture(200).use { fixture ->
            ZipArchive(fixture.file).use { zip ->
                val index = SlideIndex(zip)
                assertEquals(200, index.paths.size)
                val bytes = SlidePackage(zip, index).build(150)
                assertSinglePackage(bytes, index.paths[150])
                assertTrue(index.warnings[150])
            }
        }
    }

    @Test fun extensionSlideIdsAreNotPresentationPages() {
        fixture(2, extensionIds = true).use { fixture ->
            ZipArchive(fixture.file).use { zip ->
                val index = SlideIndex(zip)
                assertEquals(listOf("ppt/slides/slide2.xml", "ppt/slides/slide1.xml"), index.paths)
                assertSinglePackage(SlidePackage(zip, index).build(1), index.paths[1])
            }
        }
    }

    private fun assertSinglePackage(
        bytes: ByteArray,
        selected: String,
    ) {
        val single = File.createTempFile("single-slide", ".pptx")
        try {
            single.writeBytes(bytes)
            ZipArchive(single).use { subset ->
                val slides = subset.items.filter { it.path.startsWith("ppt/slides/") && it.path.endsWith(".xml") }
                assertEquals(1, slides.size)
                assertTrue(subset.items.any { it.path == selected })
                assertEquals(1, SlideIndex(subset).paths.size)
                val xml = subset.open(selected).use { it.readBytes().toString(Charsets.UTF_8) }
                assertTrue(xml.contains("a:tbl"))
                assertFalse(xml.contains("c:chart"))
            }
        } finally {
            single.delete()
        }
    }

    @Test fun externalAndEscapingRelationshipsAreExcluded() {
        assertEquals("ppt/media/a.png", SlideIndex.resolve("ppt/slides/slide1.xml", "../media/a.png"))
        assertEquals(null, SlideIndex.resolve("ppt/slides/slide1.xml", "../../../private.xml"))
        assertEquals(null, SlideIndex.resolve("ppt/slides/slide1.xml", "https://example.invalid/a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun entitiesAreRejected() {
        SlideIndex.parse("<!DOCTYPE xml [<!ENTITY x SYSTEM 'file:///private'>]><xml/>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun nodeBudgetIsAppliedBeforeParsing() {
        SlideIndex.parse("<x>".repeat(50001))
    }

    private fun fixture(
        count: Int,
        extensionIds: Boolean = false,
    ): Fixture {
        val file = File.createTempFile("reader-slides", ".pptx")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(
                path: String,
                value: String,
            ) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                "<Types>" +
                    (1..count).joinToString("") {
                        "<Override PartName=\"/ppt/slides/slide$it.xml\" " +
                            "ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>"
                    } + "</Types>",
            )
            entry(
                "ppt/presentation.xml",
                "<p:presentation><p:sldIdLst>" +
                    (count downTo 1).joinToString("") {
                        "<p:sldId id=\"${256 + it}\" r:id=\"r$it\"/>"
                    } + "</p:sldIdLst>" +
                    (
                        if (extensionIds) {
                            "<p:extLst><p:ext><p14:sectionLst><p14:section><p14:sldIdLst>" +
                                "<p14:sldId id=\"257\"/></p14:sldIdLst></p14:section>" +
                                "</p14:sectionLst></p:ext></p:extLst>"
                        } else {
                            ""
                        }
                    ) + "</p:presentation>",
            )
            entry(
                "ppt/_rels/presentation.xml.rels",
                "<Relationships>" +
                    (1..count).joinToString("") {
                        "<Relationship Id=\"r$it\" Target=\"slides/slide$it.xml\"/>"
                    } + "</Relationships>",
            )
            for (i in 1..count) {
                entry(
                    "ppt/slides/slide$i.xml",
                    "<p:sld><a:t>中文第${i}页</a:t>" +
                        "<p:graphicFrame><a:graphic><a:tbl><a:tr><a:tc><a:t>单元格</a:t>" +
                        "</a:tc></a:tr></a:tbl></a:graphic></p:graphicFrame>" +
                        "<p:graphicFrame><c:chart r:id=\"chart\"/></p:graphicFrame></p:sld>",
                )
            }
        }
        return Fixture(file)
    }

    private class Fixture(
        val file: File,
    ) : java.io.Closeable {
        override fun close() {
            file.delete()
        }
    }
}
