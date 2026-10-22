package com.shuiyu.octoberrain.feature.reader.archive

import java.io.Closeable
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

internal data class ArchiveItem(
    val path: String,
    val directory: Boolean,
    val size: Long,
    val crc: Long,
)

/** Index only. Entry content is inflated when a consumer requests a stream; no whole-archive extraction. */
internal class ZipArchive(
    file: File,
    charset: Charset = Charsets.UTF_8,
) : Closeable {
    private val zip: ZipFile
    private val entries = linkedMapOf<String, ZipEntry>()
    val items: List<ArchiveItem>

    init {
        ZipPreflight.check(file)
        zip = ZipFile(file, charset)
        var ready = false
        try {
            val all = zip.entries()
            while (all.hasMoreElements()) {
                val entry = all.nextElement()
                val path = ArchivePaths.normalize(entry.name)
                if (path.isNotEmpty()) {
                    if (entries.size >= ZipPreflight.MAX_ENTRIES || entries.put(path, entry) != null) {
                        throw ZipException("Ambiguous or oversized ZIP index")
                    }
                }
            }
            items = entries.map { (path, entry) -> ArchiveItem(path, entry.isDirectory, entry.size, entry.crc) }
            ready = true
        } finally {
            if (!ready) zip.close()
        }
    }

    fun children(directory: String): List<ArchiveItem> {
        val normalized = ArchivePaths.normalize(directory)
        val prefix = if (normalized.isEmpty()) "" else "$normalized/"
        val result = linkedMapOf<String, ArchiveItem>()
        items.filter { it.path.startsWith(prefix) && it.path != normalized }.forEach { item ->
            val remainder = item.path.removePrefix(prefix)
            val child = remainder.substringBefore('/')
            val path = prefix + child
            result[path] =
                if ('/' in remainder) ArchiveItem(path, true, 0, 0) else item
        }
        return result.values.sortedWith(compareBy<ArchiveItem> { !it.directory }.thenBy { it.path })
    }

    fun open(
        path: String,
        limit: Long = MAX_ENTRY_BYTES,
    ): InputStream {
        require(limit in 1..MAX_ENTRY_BYTES)
        val entry = entries[ArchivePaths.normalize(path)] ?: throw ZipException("Missing ZIP entry")
        if (entry.isDirectory || entry.method !in setOf(ZipEntry.STORED, ZipEntry.DEFLATED)) {
            throw ZipException("Unsupported ZIP entry")
        }
        checkSize(entry, limit)
        return CheckedEntryStream(zip.getInputStream(entry), entry.size, entry.crc, limit)
    }

    private fun checkSize(
        entry: ZipEntry,
        limit: Long,
    ) {
        if (entry.size < 0 || entry.size > limit) throw ZipException("ZIP entry budget exceeded")
    }

    override fun close() = zip.close()

    companion object {
        const val MAX_ENTRY_BYTES = 128L * 1024 * 1024
    }
}

/** Validate actual output and CRC at EOF. Partial preview closes without draining the remaining entry. */
internal class CheckedEntryStream(
    input: InputStream,
    private val expectedSize: Long,
    private val expectedCrc: Long,
    private val limit: Long,
) : FilterInputStream(input) {
    private val crc = CRC32()
    private var count = 0L

    override fun read(): Int {
        val value = `in`.read()
        if (value >= 0) {
            account(1)
            crc.update(value)
        } else {
            verify()
        }
        return value
    }

    override fun read(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val size = `in`.read(bytes, offset, length)
        if (size > 0) {
            account(size)
            crc.update(bytes, offset, size)
        } else if (size < 0) {
            verify()
        }
        return size
    }

    override fun skip(length: Long): Long {
        val buffer = ByteArray(8192)
        var remaining = length.coerceAtLeast(0)
        while (remaining > 0) {
            val size = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (size < 0) break
            remaining -= size
        }
        return length.coerceAtLeast(0) - remaining
    }

    private fun account(size: Int) {
        count += size
        if (count > limit || count > expectedSize) throw ZipException("ZIP output budget exceeded")
    }

    private fun verify() {
        if (count != expectedSize || crc.value != expectedCrc) throw ZipException("ZIP entry integrity failure")
    }
}
