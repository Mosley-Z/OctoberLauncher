package com.shuiyu.octoberrain.feature.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.file.Files

class SparseTextIndexTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun chunkTableIsArithmeticAndDoesNotScanFile() {
        val file = temporary.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(50L * 1024 * 1024) }
        Files.newByteChannel(file.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            assertEquals(200, index.chunkCount)
            // 建立索引只调用 channel.size()，不读取文件内容。
            assertEquals(0L, channel.position())
            assertEquals(0L, index.locate(0))
            assertEquals(0L, index.locate(262143))
            assertEquals(262144L, index.locate(262144))
            val lastBlock = 199L * TEXT_CHUNK_BYTES
            assertEquals(lastBlock, index.locate(50L * 1024 * 1024 - 1))
            // 文件尾偏移归属最后一块，便于按字节锚点定位。
            assertEquals(lastBlock, index.locate(50L * 1024 * 1024))
            assertEquals(200, index.chunkOffsets().size)
            assertEquals(lastBlock, index.chunkOffsets().last())
        }
    }

    @Test fun seekFiftyMiBReadsOnlyTheContainingChunk() {
        val file = temporary.newFile()
        RandomAccessFile(file, "rw").use { it.setLength(50L * 1024 * 1024) }
        Files.newByteChannel(file.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            val first = index.seek(0, Charsets.UTF_8)
            assertEquals(0L, first.offset)
            assertEquals(65536, first.text.length)
            // 首屏只读首块内的一个固定窗口。
            assertTrue(channel.position() <= TEXT_CHUNK_BYTES.toLong())
            val anchor = 49L * 1024 * 1024
            val last = index.seek(anchor, Charsets.UTF_8)
            assertEquals(anchor, last.offset)
            assertEquals(65536, last.text.length)
            // 跳到文件尾附近的锚点只读取该块，不从文件起点重扫。
            assertTrue(channel.position() <= anchor + TEXT_WINDOW_BYTES)
        }
    }

    @Test fun tailShortChunkIsReturnedAsShortWindow() {
        val file = temporary.newFile()
        file.writeText("a".repeat(299 * 1024) + "文件尾")
        Files.newByteChannel(file.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            assertEquals(2, index.chunkCount)
            val window = index.seek(channel.size() - 1, Charsets.UTF_8)
            assertEquals(262144L, window.offset)
            assertEquals(306185L, channel.size())
            // 读完文件尾 44041 字节，解码为 44032 个 ASCII 字符加 3 个汉字。
            assertEquals(44035, window.text.length)
            assertTrue(window.text.endsWith("文件尾"))
        }
    }

    @Test fun utf8MultibyteBoundaryIsAdvancedPastContinuationBytes() {
        val prefix = "a".repeat(262143)
        val file = temporary.newFile()
        file.writeText(prefix + "中文")
        Files.newByteChannel(file.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            assertEquals(2, index.chunkCount)
            // 块首 262144 落在“中”的第三个字节上，应前进到“文”的起始字节。
            val window = index.seek(262144, Charsets.UTF_8)
            assertEquals(262146L, window.offset)
            assertTrue(window.text.startsWith("文"))
            assertEquals(262146L, index.alignToCharBoundary(262144, Charsets.UTF_8))
        }
    }

    @Test fun utf16SurrogatePairBoundaryIsAlignedToCharStart() {
        val charset = Charset.forName("UTF-16LE")
        val prefix = "a".repeat(131071)
        val file = temporary.newFile()
        file.writeBytes((prefix + "🙂中文").toByteArray(charset))
        Files.newByteChannel(file.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            // 代理对占 262142..262145，块首 262144 位于低位代理项内部。
            val window = index.seek(262144, charset)
            assertEquals(262146L, window.offset)
            assertTrue(window.text.startsWith("中"))
            // 奇数偏移先按偶数对齐。
            assertEquals(262146L, index.alignToCharBoundary(262145, charset))
        }
    }

    @Test fun gb18030MultibyteBoundaryProducesDecodableWindow() {
        val charset = Charset.forName("GB18030")
        val file = temporary.newFile()
        file.writeBytes(("a".repeat(262144) + "中文").toByteArray(charset))
        Files.newByteChannel(file.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            // 块首恰好是双字节字符起点时无需调整。
            val clean = index.seek(262144, charset)
            assertEquals(262144L, clean.offset)
            assertEquals("中文", clean.text)
        }
        val misaligned = temporary.newFile()
        misaligned.writeBytes(("a".repeat(262142) + "𠀀中文").toByteArray(charset))
        Files.newByteChannel(misaligned.toPath()).use { channel ->
            val index = SparseTextIndex(channel)
            // 四字节字符的第三字节落在块首时解码报错，对齐后窗口仍可解码。
            val window = index.seek(262144, charset)
            assertTrue(window.offset in 262144L..262147L)
            assertTrue(window.text.isNotEmpty())
        }
    }
}
