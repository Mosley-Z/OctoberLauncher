package com.shuiyu.octoberrain.feature.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.concurrent.CancellationException

class TextSearchTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun findsCrossWindowQueryAtExactByteOffsetForAllEncodings() {
        for (name in listOf("UTF-8", "UTF-16LE", "GB18030")) {
            val charset = Charset.forName(name)
            val prefix = "a".repeat(if (name == "UTF-16LE") 32766 else 65534)
            val file = temporary.newFile()
            file.writeBytes((prefix + "中文𠀀后续").toByteArray(charset))
            Files.newByteChannel(file.toPath()).use { channel ->
                assertEquals(prefix.toByteArray(charset).size.toLong(), findText(channel, 0, charset, "中文𠀀") {})
                assertNull(findText(channel, 0, charset, "没有匹配") {})
            }
        }
    }

    @Test fun cancellationStopsBeforeReadingSecondWindow() {
        val file = temporary.newFile()
        file.writeText("a".repeat(200000))
        var checkpoints = 0
        Files.newByteChannel(file.toPath()).use { channel ->
            try {
                findText(channel, 0, Charsets.UTF_8, "不存在") {
                    checkpoints++
                    if (checkpoints == 2) throw CancellationException()
                }
                throw AssertionError("Expected cancellation")
            } catch (_: CancellationException) {
                assertEquals(2, checkpoints)
                assertEquals(65536L, channel.position())
            }
        }
    }

    @Test fun matchCarriesWindowRangeForPageHighlight() {
        val file = temporary.newFile()
        file.writeText("a".repeat(65530) + "中文目标后")
        Files.newByteChannel(file.toPath()).use { channel ->
            val match = requireNotNull(findTextMatch(channel, 0, Charsets.UTF_8, "目标") {})
            assertEquals(65536L, match.byteOffset)
            // 命中位于第二个窗口起点，窗口起点与命中起点重合。
            assertEquals(65536L, match.windowOffset)
            assertEquals(0, match.charStart)
            assertEquals(2, match.charEnd)
            val text = readTextWindow(channel, match.windowOffset, Charsets.UTF_8).text
            assertEquals("目标", text.substring(match.charStart, match.charEnd))
        }
    }

    @Test fun pageRangesCoverRepeatedAndAdjacentMatches() {
        assertEquals(listOf(TextRange(0, 2), TextRange(2, 4)), findTextRanges("abab", "ab"))
        assertEquals(listOf(TextRange(0, 3), TextRange(3, 6)), findTextRanges("aaaaaa", "aaa"))
        assertTrue(findTextRanges("文本", "").isEmpty())
        assertTrue(findTextRanges("", "文本").isEmpty())
        assertTrue(findTextRanges("文本", "不存在").isEmpty())
    }
}
