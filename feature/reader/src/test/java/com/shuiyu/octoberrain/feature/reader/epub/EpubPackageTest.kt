package com.shuiyu.octoberrain.feature.reader.epub

import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubPackageTest {
    @Test fun resolvesPackageSpineAndNavWithoutLoadingAllChapters() {
        fixture().use { book ->
            assertEquals(listOf("OPS/a.xhtml", "OPS/b.xhtml"), book.chapters.map { it.path })
            assertEquals(listOf(EpubToc("开篇", 0), EpubToc("第二章", 1, "end")), book.toc)
            assertEquals("<html><body>second</body></html>", book.read("OPS/b.xhtml"))
        }
    }

    @Test fun legacyNcxDirectoryResolvesSpineTargets() {
        fixture(ncx = true).use { book ->
            assertEquals(listOf(EpubToc("旧目录", 1, "end")), book.toc)
        }
    }

    @Test fun packageLinksAllowParentWithinArchiveButRejectEscapingAndNetwork() {
        assertEquals(EpubTarget("OPS/img/a b.png"), EpubPaths.resolve("OPS/text/a.xhtml", "../img/a%20b.png"))
        assertEquals(EpubTarget("OPS/a.xhtml", "end"), EpubPaths.resolve("OPS/a.xhtml", "#end"))
        assertNull(EpubPaths.resolve("OPS/a.xhtml", "../../secret"))
        assertNull(EpubPaths.resolve("OPS/a.xhtml", "https://remote/image.png"))
        assertNull(EpubPaths.resolve("OPS/a.xhtml", "//remote/image.png"))
    }

    @Test fun encryptedOrFixedLayoutBooksAreExplicitlyUnsupported() {
        assertThrows(ReaderSourceException::class.java) { fixture(encrypted = true).close() }
        assertThrows(ReaderSourceException::class.java) { fixture(fixed = true).close() }
    }

    private fun fixture(
        encrypted: Boolean = false,
        fixed: Boolean = false,
        ncx: Boolean = false,
    ): BookFixture {
        val file = File.createTempFile("reader-epub-", ".epub")
        val entries =
            linkedMapOf(
                "META-INF/container.xml" to
                    "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>",
                "OPS/book.opf" to
                    """<package><metadata>${if (fixed) "<meta property='rendition:layout'>pre-paginated</meta>" else ""}</metadata>
                <manifest><item id='a' href='a.xhtml' media-type='application/xhtml+xml'/>
                <item id='b' href='b.xhtml' media-type='application/xhtml+xml'/>
                <item id='nav' href='nav.xhtml' properties='nav'/></manifest>
                <spine><itemref idref='a'/><itemref idref='b'/></spine></package>""",
                "OPS/a.xhtml" to "<html><body>first</body></html>",
                "OPS/b.xhtml" to "<html><body>second</body></html>",
                "OPS/nav.xhtml" to "<nav><a href='a.xhtml'>开篇</a><a href='b.xhtml#end'>第二章</a></nav>",
            )
        if (ncx) {
            entries["OPS/book.opf"] =
                checkNotNull(entries["OPS/book.opf"])
                    .replace("id='nav' href='nav.xhtml' properties='nav'", "id='ncx' href='book.ncx'")
                    .replace("<spine>", "<spine toc='ncx'>")
            entries["OPS/book.ncx"] =
                "<ncx><navMap><navPoint><navLabel><text>旧目录</text></navLabel>" +
                "<content src='b.xhtml#end'/></navPoint></navMap></ncx>"
        }
        if (encrypted) entries["META-INF/encryption.xml"] = "<encryption/>"
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        var retained = false
        val zip = ZipArchive(file)
        try {
            val fixture = BookFixture(EpubPackage(zip), zip, file)
            retained = true
            return fixture
        } finally {
            if (!retained) {
                zip.close()
                file.delete()
            }
        }
    }

    private class BookFixture(
        private val book: EpubPackage,
        private val zip: ZipArchive,
        private val file: File,
    ) : java.io.Closeable {
        val chapters get() = book.chapters
        val toc get() = book.toc

        fun read(path: String): String = book.read(path)

        override fun close() {
            zip.close()
            file.delete()
        }
    }
}
