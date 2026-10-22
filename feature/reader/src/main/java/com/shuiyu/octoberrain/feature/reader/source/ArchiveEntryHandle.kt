package com.shuiyu.octoberrain.feature.reader.source

import android.os.ParcelFileDescriptor
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.feature.reader.archive.ArchivePaths
import com.shuiyu.octoberrain.feature.reader.archive.LazyEntryChannel
import com.shuiyu.octoberrain.feature.reader.archive.ZipArchive
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset

/** The child owns its parent archive and its temporary files, never a user file. */
internal class ArchiveEntryHandle(
    private val parent: DocumentHandle,
    entry: String,
    charset: String,
    private val cache: SourceCache,
) : DocumentHandle {
    private val path = ArchivePaths.normalize(entry)
    private val zip = ZipArchive(parent.archiveFile(), Charset.forName(charset))
    private val resources = mutableListOf<Closeable>()
    private var closed = false
    private var materialized: SourceCache.Lease? = null
    override val metadata: DocumentMetadata

    init {
        var ready = false
        try {
            val item =
                zip.items.firstOrNull { it.path == path && !it.directory }
                    ?: throw ReaderSourceException(ReaderFailure.MISSING)
            metadata =
                DocumentMetadata(
                    "${parent.metadata.id}!$charset:$path:${item.crc}",
                    path.substringAfterLast('/'),
                    null,
                    item.size,
                    parent.metadata.modified,
                    parent.metadata.persistentAccess,
                    parent.metadata.aliases.map { "$it!$charset:$path:${item.crc}" },
                )
            ready = true
        } finally {
            if (!ready) zip.close()
        }
    }

    override fun openStream(): InputStream {
        check(!closed)
        return zip.open(path)
    }

    override fun openChannel(): SeekableByteChannel {
        check(!closed)
        val lease = cache.allocate(metadata.size)
        var retained = false
        try {
            val channel = LazyEntryChannel(openStream(), lease.file, checkNotNull(metadata.size))
            resources.add(lease)
            resources.add(channel)
            retained = true
            return channel
        } finally {
            if (!retained) lease.close()
        }
    }

    override fun openSeekable(): ParcelFileDescriptor =
        ParcelFileDescriptor.open(archiveFile(), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun archiveFile(): File {
        check(!closed)
        materialized?.let { return it.file }
        val lease = cache.allocate(metadata.size)
        var saved = false
        try {
            openStream().use { input -> lease.file.outputStream().use { copyBounded(input, it, lease.limit) } }
            lease.compact()
            materialized = lease
            saved = true
            return lease.file
        } finally {
            if (!saved) lease.close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            resources.asReversed().forEach { runCatching { it.close() } }
            materialized?.close()
        } finally {
            try {
                zip.close()
            } finally {
                parent.close()
            }
        }
    }
}
