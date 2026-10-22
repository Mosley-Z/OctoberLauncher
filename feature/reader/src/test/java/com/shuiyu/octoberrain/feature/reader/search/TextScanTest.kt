package com.shuiyu.octoberrain.feature.reader.search

import com.shuiyu.octoberrain.core.common.ReaderLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset
import java.nio.file.Files

class TextScanTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun surrogateTailDoesNotCountCompletedMatchTwice() =
        runBlocking {
            val file = temporary.newFile()
            file.writeText("x".repeat(65531) + "😀a" + "next😀a")
            Files.newByteChannel(file.toPath()).use { channel ->
                val page = scanTextFile(channel, 0, Charsets.UTF_8, "😀a", 0)
                assertEquals(2, page.total)
                assertEquals(65531L, (page.hits.first().locator as ReaderLocator.Text).byteOffset)
            }
        }

    @Test fun crossWindowMatchesKeepExactEncodingOffsetsAndFullCounts() =
        runBlocking {
            for (name in listOf("UTF-8", "UTF-16LE", "UTF-16BE", "GB18030")) {
                val charset = Charset.forName(name)
                val prefix = "a".repeat(if (name.startsWith("UTF-16")) 32766 else 65534)
                val file = temporary.newFile()
                file.writeBytes((prefix + "中文𠀀中文𠀀").toByteArray(charset))
                Files.newByteChannel(file.toPath()).use { channel ->
                    val page = scanTextFile(channel, 0, charset, "中文𠀀", 0)
                    assertEquals(2, page.total)
                    assertEquals(
                        prefix.toByteArray(charset).size.toLong(),
                        (page.hits[0].locator as ReaderLocator.Text).byteOffset,
                    )
                    val second = (prefix + "中文𠀀").toByteArray(charset).size.toLong()
                    assertEquals(second, (page.hits[1].locator as ReaderLocator.Text).byteOffset)
                }
            }
        }

    @Test fun denseOverlappingMatchesHaveBoundedStableResultPages() =
        runBlocking {
            val file = temporary.newFile()
            file.writeText("a".repeat(100000))
            Files.newByteChannel(file.toPath()).use { channel ->
                val first = scanTextFile(channel, 0, Charsets.UTF_8, "aa", 0)
                val next = scanTextFile(channel, 0, Charsets.UTF_8, "aa", 64)
                assertEquals(99999, first.total)
                assertEquals(first.total, next.total)
                assertEquals(64, first.hits.size)
                assertEquals(64, next.hits.size)
                assertEquals(64L, (next.hits[0].locator as ReaderLocator.Text).byteOffset)
            }
        }

    @Test fun collectorUsesWholeContentCountWithoutKeepingWholeContent() =
        runBlocking {
            val collector = SearchCollector(64)
            collectMatches("目标".repeat(70), "目标", collector) { ReaderLocator.Markdown(it) }
            assertEquals(70, collector.page().total)
            assertEquals(6, collector.page().hits.size)
            assertEquals(128, (collector.page().hits[0].locator as ReaderLocator.Markdown).textOffset)
        }
}
