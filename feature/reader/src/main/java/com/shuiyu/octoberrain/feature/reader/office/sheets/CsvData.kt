package com.shuiyu.octoberrain.feature.reader.office.sheets

import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder

/** Independent streams let search read all records without changing the visible window or EOF state. */
internal class CsvData(
    private val source: DocumentHandle,
    private val charset: Charset,
) {
    fun open(
        offset: Long,
        checkpoint: () -> Unit,
    ): CsvRecords {
        val reader = InputStreamReader(source.openStream(), charset.newDecoder()).buffered()
        var retained = false
        try {
            var left = offset
            val buffer = CharArray(8192)
            while (left > 0) {
                checkpoint()
                val count = reader.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                if (count < 0) break
                left -= count
            }
            var initial = offset
            if (offset == 0L) {
                reader.mark(1)
                if (reader.read() != 0xFEFF) reader.reset() else initial = 1
            }
            return CsvRecords(reader, initial, checkpoint).also { retained = true }
        } finally {
            if (!retained) reader.close()
        }
    }

    fun next(records: CsvRecords): List<String>? =
        try {
            records.next()
        } catch (_: SheetLimitException) {
            throw ReaderSourceException(ReaderFailure.LIMIT)
        } catch (_: IllegalArgumentException) {
            throw ReaderSourceException(ReaderFailure.CORRUPT)
        }

    /** Old text bookmarks use byte anchors; CSV locations use logical records, including quoted newlines. */
    fun legacyRow(
        byteOffset: Long,
        checkpoint: () -> Unit,
    ): Int {
        val target = charsBefore(byteOffset.coerceAtLeast(0), checkpoint)
        var row = 0
        return open(0, checkpoint).use { records ->
            var record = next(records)
            while (record != null && records.position <= target) {
                checkpoint()
                row++
                record = next(records)
                if (record != null && row >= SheetLimits.ROWS) throw ReaderSourceException(ReaderFailure.LIMIT)
            }
            if (record == null) (row - 1).coerceAtLeast(0) else row
        }
    }

    private fun charsBefore(
        byteOffset: Long,
        checkpoint: () -> Unit,
    ): Long {
        val decoder = charset.newDecoder()
        val bytes = ByteBuffer.allocate(8192)
        val chars = CharBuffer.allocate(8192)
        var remaining = byteOffset
        var characters = 0L
        source.openStream().use { stream ->
            while (remaining > 0) {
                checkpoint()
                val count =
                    stream.read(
                        bytes.array(),
                        bytes.position(),
                        minOf(remaining, bytes.remaining().toLong()).toInt(),
                    )
                if (count < 0) break
                bytes.position(bytes.position() + count)
                bytes.flip()
                characters += decodeCount(bytes, chars, decoder)
                bytes.compact()
                remaining -= count
            }
        }
        return characters
    }

    private fun decodeCount(
        bytes: ByteBuffer,
        chars: CharBuffer,
        decoder: CharsetDecoder,
    ): Long {
        var characters = 0L
        var more: Boolean
        do {
            chars.clear()
            // An anchor inside a multibyte character or BOM excludes that incomplete character.
            val result = decoder.decode(bytes, chars, false)
            if (result.isError) result.throwException()
            characters += chars.position()
            more = result.isOverflow
        } while (more)
        return characters
    }
}
