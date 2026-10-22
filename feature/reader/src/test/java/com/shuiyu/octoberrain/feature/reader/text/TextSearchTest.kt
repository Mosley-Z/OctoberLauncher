package com.shuiyu.octoberrain.feature.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
