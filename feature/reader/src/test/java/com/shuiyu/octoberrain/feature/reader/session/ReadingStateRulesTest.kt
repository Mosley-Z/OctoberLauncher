package com.shuiyu.octoberrain.feature.reader.session

import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingStateRulesTest {
    @Test fun trimsLabelAndRejectsBlank() {
        assertEquals("书签一", ReadingStateRules.normalizeLabel("  书签一 \n"))
        assertNull(ReadingStateRules.normalizeLabel("   "))
        assertNull(ReadingStateRules.normalizeLabel(""))
    }

    @Test fun truncatesLabelByCodePoint() {
        assertEquals(80, ReadingStateRules.normalizeLabel("a".repeat(80))!!.length)
        assertEquals(80, ReadingStateRules.normalizeLabel("a".repeat(81))!!.length)

        // 表情占两个 UTF-16 单元，截断必须按码点计算，结果不能出现半个代理对
        val label = ReadingStateRules.normalizeLabel("😀".repeat(81))!!
        assertEquals(80, label.codePointCount(0, label.length))
        assertEquals(160, label.length)
    }

    @Test fun fallsBackWhenLabelIsBlank() {
        assertEquals(ReadingStateRules.BOOKMARK_LABEL_FALLBACK, ReadingStateRules.labelOrFallback("   "))
        assertEquals("标题", ReadingStateRules.labelOrFallback(" 标题 "))
    }

    @Test fun clampsRequestedLimit() {
        assertEquals(0, ReadingStateRules.clampLimit(-1))
        assertEquals(0, ReadingStateRules.clampLimit(0))
        assertEquals(20, ReadingStateRules.clampLimit(20))
        assertEquals(ReadingStateRules.RECENT_LIMIT_MAX, ReadingStateRules.clampLimit(500))
        assertEquals(5, ReadingStateRules.clampLimit(5, 5))
    }

    @Test fun ordersRecentByUpdatedThenRefKey() {
        val items =
            listOf(
                RecentReading(DocumentRef.LocalPath("/b"), ReaderLocator.Text(byteOffset = 3), 200L, false),
                RecentReading(DocumentRef.LocalPath("/a"), ReaderLocator.Text(byteOffset = 1), 200L, true),
                RecentReading(DocumentRef.ContentUri("content://x/c"), ReaderLocator.Image(2), 300L, false),
            )

        val ordered = ReadingStateRules.orderRecent(items, 2)
        assertEquals(listOf("uri:content://x/c", "path:/a"), ordered.map { ReadingStateRules.refKey(it.ref) })
        assertEquals(3, ReadingStateRules.orderRecent(items, 10).size)
        assertTrue(ReadingStateRules.orderRecent(items, 0).isEmpty())
        assertTrue(ReadingStateRules.orderRecent(emptyList(), 5).isEmpty())
    }

    @Test fun resolvesLocalPathName() {
        assertEquals(
            "三体.epub",
            ReadingStateRules.displayName(DocumentRef.LocalPath("/storage/emulated/0/Download/三体.epub")),
        )
        assertEquals("a.txt", ReadingStateRules.displayName(DocumentRef.LocalPath("E:\\Books\\a.txt")))
        assertEquals("books", ReadingStateRules.displayName(DocumentRef.LocalPath("/data/books/")))
    }

    @Test fun resolvesContentUriName() {
        assertEquals(
            "三体.epub",
            ReadingStateRules.displayName(
                DocumentRef.ContentUri(
                    "content://com.android.providers.downloads.documents/document/%E4%B8%89%E4%BD%93.epub?x=1#f",
                ),
            ),
        )
        // 字面量加号与 %2B 都解码为加号，不会变成空格
        assertEquals("a+b.pdf", ReadingStateRules.displayName(DocumentRef.ContentUri("content://x/y/a+b.pdf")))
        assertEquals("a+b.pdf", ReadingStateRules.displayName(DocumentRef.ContentUri("content://x/y/a%2Bb.pdf")))
    }

    @Test fun resolvesArchiveEntryName() {
        val archive =
            DocumentRef.ArchiveEntry(
                archive = DocumentRef.LocalPath("/sdcard/合集.zip"),
                entry = "chapters/01.txt",
            )
        assertEquals("合集.zip/01.txt", ReadingStateRules.displayName(archive))
    }

    @Test fun refKeyDistinguishesSources() {
        assertEquals("path:/a", ReadingStateRules.refKey(DocumentRef.LocalPath("/a")))
        assertEquals("uri:content://x/a", ReadingStateRules.refKey(DocumentRef.ContentUri("content://x/a")))
        assertEquals(
            "archive:path:/s.zip!/a.txt",
            ReadingStateRules.refKey(
                DocumentRef.ArchiveEntry(archive = DocumentRef.LocalPath("/s.zip"), entry = "a.txt"),
            ),
        )
    }
}
