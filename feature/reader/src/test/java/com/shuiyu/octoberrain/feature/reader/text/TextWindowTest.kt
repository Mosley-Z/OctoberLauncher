package com.shuiyu.octoberrain.feature.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.file.Files

class TextWindowTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun utf8CharacterAcrossWindowBoundaryIsNotLost() {
        val file = temporary.newFile()
        val prefix = "a".repeat(65535)
        file.writeText(prefix + "中文🙂尾部")
        Files.newByteChannel(file.toPath()).use { channel ->
            val first = readTextWindow(channel, 0, Charsets.UTF_8)
            assertEquals(prefix, first.text)
            val next =
                readTextWindow(
                    channel,
                    first.text
                        .toByteArray()
                        .size
                        .toLong(),
                    Charsets.UTF_8,
                )
            assertEquals("中文🙂尾部", next.text)
        }
    }

    @Test fun utf16SurrogateAndGb18030CharactersSurviveBoundaries() {
        for (name in listOf("UTF-16LE", "GB18030")) {
            val charset = Charset.forName(name)
            val prefix = "a".repeat(if (name == "UTF-16LE") 32767 else 65535)
            val content = prefix + "𠀀中文"
            val file = temporary.newFile()
            file.writeBytes(content.toByteArray(charset))
            Files.newByteChannel(file.toPath()).use { channel ->
                val first = readTextWindow(channel, 0, charset)
                val next =
                    readTextWindow(
                        channel,
                        first.text
                            .toByteArray(charset)
                            .size
                            .toLong(),
                        charset,
                    )
                assertEquals(content, first.text + next.text)
            }
        }
    }

    @Test fun fiftyMiBFileStillReadsOneBoundedWindowAndReverseWindowEndsAtAnchor() {
        val file = temporary.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(50L * 1024 * 1024) }
        Files.newByteChannel(file.toPath()).use { channel ->
            val first = readTextWindow(channel, 0, Charsets.UTF_8)
            assertEquals(65536, first.text.length)
            val anchor = 49L * 1024 * 1024
            val previous = previousTextWindow(channel, anchor, Charsets.UTF_8, 0)
            assertEquals(anchor, previous.offset + previous.text.toByteArray().size)
            assertTrue(previous.text.length <= 65536)
        }
    }
}
