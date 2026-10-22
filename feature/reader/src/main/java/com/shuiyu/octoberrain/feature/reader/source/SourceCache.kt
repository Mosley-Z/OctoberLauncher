package com.shuiyu.octoberrain.feature.reader.source

import android.annotation.SuppressLint
import com.shuiyu.octoberrain.core.common.ReaderFailure
import java.io.Closeable
import java.io.File

/** Shared reservations include unopened parts of lazy entries, preventing concurrent cache oversubscription. */
internal class SourceCache(
    private val directory: File,
) {
    fun allocate(size: Long?): Lease =
        synchronized(reservations) {
            directory.mkdirs()
            if (initialized.add(directory)) {
                directory
                    .listFiles()
                    .orEmpty()
                    .filter {
                        it.isFile && it.name.startsWith("source-") && it.name.endsWith(".cache")
                    }.forEach { it.delete() }
            }
            val existing = directory.listFiles().orEmpty().sumOf { if (it in reservations) 0L else it.length() }
            val reserved = reservations.filterKeys { it.parentFile == directory }.values.sum()
            val remaining = minOf(MAX_BYTES - existing - reserved, freeBudget())
            val requested = size ?: remaining
            if (requested < 0 || requested > remaining || remaining <= 0) {
                throw ReaderSourceException(ReaderFailure.LIMIT)
            }
            val file = File.createTempFile("source-", ".cache", directory)
            reservations[file] = requested
            Lease(file, requested)
        }

    @SuppressLint("UsableSpace") // Keep half of currently free space; do not request system eviction.
    private fun freeBudget(): Long = directory.usableSpace / 2

    class Lease internal constructor(
        val file: File,
        val limit: Long,
    ) : Closeable {
        fun compact() =
            synchronized(reservations) {
                check(file.length() <= limit)
                reservations[file] = file.length()
            }

        override fun close() =
            synchronized(reservations) {
                file.delete()
                reservations.remove(file)
                Unit
            }
    }

    companion object {
        const val MAX_BYTES = 128L * 1024 * 1024
        private val initialized = mutableSetOf<File>()
        private val reservations = mutableMapOf<File, Long>()
    }
}
