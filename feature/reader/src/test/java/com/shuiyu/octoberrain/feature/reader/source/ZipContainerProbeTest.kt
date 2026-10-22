package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.core.common.ReaderFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipContainerProbeTest {
    @Test fun epubIsDetectedFromMimetypeEntry() {
        withTempDirectory { directory ->
            val file = writeArchive(directory, "book.epub", listOf("mimetype" to "application/epub+zip"))
            assertEquals(ZipContainer.EPUB, ZipContainerProbe.probe(file))
        }
    }

    @Test fun epubIsDetectedFromContainerXmlWithoutMimetype() {
        withTempDirectory { directory ->
            val file =
                writeArchive(
                    directory,
                    "book.zip",
                    listOf("META-INF/container.xml" to "<container/>", "OEBPS/content.opf" to "<package/>"),
                )
            assertEquals(ZipContainer.EPUB, ZipContainerProbe.probe(file))
        }
    }

    @Test fun officeContainersAreDistinguishedByEntryNames() {
        withTempDirectory { directory ->
            val docx = writeArchive(directory, "a.docx", listOf("word/document.xml" to "<document/>"))
            val xlsx = writeArchive(directory, "b.xlsx", listOf("xl/workbook.xml" to "<workbook/>"))
            val pptx = writeArchive(directory, "c.pptx", listOf("ppt/presentation.xml" to "<presentation/>"))
            assertEquals(ZipContainer.DOCX, ZipContainerProbe.probe(docx))
            assertEquals(ZipContainer.SHEET, ZipContainerProbe.probe(xlsx))
            assertEquals(ZipContainer.SLIDES, ZipContainerProbe.probe(pptx))
        }
    }

    @Test fun docxWithoutDocumentXmlUsesContentTypesAndWordPart() {
        withTempDirectory { directory ->
            val file =
                writeArchive(
                    directory,
                    "a.zip",
                    listOf("[Content_Types].xml" to "<Types/>", "word/styles.xml" to "<styles/>"),
                )
            assertEquals(ZipContainer.DOCX, ZipContainerProbe.probe(file))
        }
    }

    @Test fun plainArchiveStaysZip() {
        withTempDirectory { directory ->
            val file = writeArchive(directory, "a.zip", listOf("readme.txt" to "hello"))
            assertEquals(ZipContainer.ZIP, ZipContainerProbe.probe(file))
        }
    }

    @Test fun unknownOrBrokenInputsReturnNull() {
        withTempDirectory { directory ->
            assertNull(ZipContainerProbe.probe(File(directory, "missing.zip")))
            val truncated = File(directory, "truncated.zip")
            truncated.writeBytes(byteArrayOf(80, 75, 3, 4))
            assertNull(ZipContainerProbe.probe(truncated))
            val garbage = File(directory, "garbage.zip")
            garbage.writeBytes(ByteArray(64) { (it * 31 and 255).toByte() })
            assertNull(ZipContainerProbe.probe(garbage))
        }
    }

    @Test fun extensionlessEpubResolvesThroughProbe() {
        withTempDirectory { directory ->
            val file = writeArchive(directory, "download", listOf("mimetype" to "application/epub+zip"))
            val container = ZipContainerProbe.probe(file)
            assertEquals(ZipContainer.EPUB, container)
            assertEquals(ReaderFormat.EPUB, DocumentKinds.detect("download", null, zipHeader(file), container))
        }
    }

    @Test fun docxContentNamedZipResolvesThroughProbe() {
        withTempDirectory { directory ->
            val file = writeArchive(directory, "a.zip", listOf("word/document.xml" to "<document/>"))
            val container = ZipContainerProbe.probe(file)
            assertEquals(ZipContainer.DOCX, container)
            assertEquals(ReaderFormat.DOCX, DocumentKinds.detect("a.zip", null, zipHeader(file), container))
        }
    }

    @Test fun plainZipWithOfficeExtensionFallsBackToZip() {
        withTempDirectory { directory ->
            val file = writeArchive(directory, "a.docx", listOf("readme.txt" to "hello"))
            val container = ZipContainerProbe.probe(file)
            assertEquals(ZipContainer.ZIP, container)
            assertEquals(ReaderFormat.ZIP, DocumentKinds.detect("a.docx", null, zipHeader(file), container))
        }
    }

    private companion object {
        /** 在临时目录内执行测试体，结束后递归删除目录。 */
        fun withTempDirectory(block: (File) -> Unit) {
            val directory = Files.createTempDirectory("zip-probe-").toFile()
            try {
                block(directory)
            } finally {
                directory.deleteRecursively()
            }
        }

        /** 按条目名与文本内容写入一个 ZIP 文件，返回该文件。 */
        fun writeArchive(
            directory: File,
            name: String,
            entries: List<Pair<String, String>>,
        ): File {
            val file = File(directory, name)
            ZipOutputStream(file.outputStream()).use { output ->
                entries.forEach { (path, text) ->
                    output.putNextEntry(ZipEntry(path))
                    output.write(text.toByteArray())
                    output.closeEntry()
                }
            }
            return file
        }

        /** 读取文件前四字节作为文件头，供格式判定使用。 */
        fun zipHeader(file: File): ByteArray = file.readBytes().copyOf(4)
    }
}
