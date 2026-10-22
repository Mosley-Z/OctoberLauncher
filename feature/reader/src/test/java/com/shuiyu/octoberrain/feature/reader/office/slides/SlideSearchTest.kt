package com.shuiyu.octoberrain.feature.reader.office.slides

import com.shuiyu.octoberrain.core.common.ReaderLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SlideSearchTest {
    @Test fun styledRunsStayContiguousInsideParagraphs() =
        runBlocking {
            val xml =
                "<p:sld><a:p><a:r><a:t>检索</a:t></a:r>" +
                    "<a:r><a:rPr b=\"1\"/><a:t>目标</a:t></a:r></a:p>" +
                    "<a:p><a:r><a:t>检索目标</a:t></a:r></a:p></p:sld>"
            val text = SlideIndex.extractText(SlideIndex.parse(xml))
            assertEquals("检索目标\n检索目标", text)
            val results = SlideSearch.scan(listOf(text), false, "检索目标", 0)
            assertEquals(2, results.total)
            assertEquals(listOf(0, 5), results.hits.map { (it.locator as ReaderLocator.Slide).textOffset })
        }

    @Test fun scanFindsUnloadedLastSlide() =
        runBlocking {
            val texts = (1..200).map { "中文 第${it}页 hit_$it" }
            val page = SlideSearch.scan(texts, false, "hit_200", 0)
            assertEquals(1, page.total)
            val expected = ReaderLocator.Slide(199, false, texts[199].indexOf("hit_200"))
            assertEquals(expected, page.hits.single().locator)
        }

    @Test fun resultWindowCountsAllSlidesWithoutRetainingAllHits() =
        runBlocking {
            val page = SlideSearch.scan(List(200) { "match" }, true, "match", 128)
            assertEquals(200, page.total)
            assertEquals(64, page.hits.size)
            assertEquals(ReaderLocator.Slide(128, true, 0), page.hits.first().locator)
            assertEquals(ReaderLocator.Slide(191, true, 0), page.hits.last().locator)
        }

    @Test fun duplicateWordsRetainPreciseOffsetsAndVisualOrdinals() =
        runBlocking {
            val text = "match\nmatch\nmatch"
            val page = SlideSearch.scan(listOf(text), false, "match", 1)
            assertEquals(3, page.total)
            assertEquals(listOf(6, 12), page.hits.map { (it.locator as ReaderLocator.Slide).textOffset })
            assertEquals(0, SlideSearch.ordinal(text, "match", 0))
            assertEquals(1, SlideSearch.ordinal(text, "match", 6))
            assertEquals(2, SlideSearch.ordinal(text, "match", 12))
            assertEquals(-1, SlideSearch.ordinal(text, "match", 8))
        }
}
