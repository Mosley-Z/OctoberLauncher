package com.shuiyu.octoberrain.feature.reader.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/** 来源准备和复制只在IO线程；普通本地文件不复制，管道URI按需落盘并限制总缓存。 */
internal class SourceResolver(
    context: Context,
) {
    private val resolver = context.applicationContext.contentResolver
    private val cacheDir = File(context.applicationContext.cacheDir, "reader-sources")
    private val cache = SourceCache(cacheDir)

    suspend fun open(ref: DocumentRef): DocumentHandle =
        withContext(Dispatchers.IO) {
            when (ref) {
                is DocumentRef.LocalPath -> local(ref)
                is DocumentRef.ContentUri -> content(ref)
                is DocumentRef.ArchiveEntry -> archiveEntry(ref)
            }
        }

    private suspend fun archiveEntry(ref: DocumentRef.ArchiveEntry): DocumentHandle {
        if (ref.archive is DocumentRef.ArchiveEntry) throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
        val parent = open(ref.archive)
        var retained = false
        try {
            val child = ArchiveEntryHandle(parent, ref.entry, ref.charset, cache)
            retained = true
            return child
        } finally {
            if (!retained) parent.close()
        }
    }

    private fun local(ref: DocumentRef.LocalPath): DocumentHandle {
        val file = File(ref.path).canonicalFile
        if (!file.isFile) throw ReaderSourceException(ReaderFailure.MISSING)
        if (!file.canRead()) throw ReaderSourceException(ReaderFailure.PERMISSION)
        val metadata = DocumentMetadata("path:${file.path}", file.name, null, file.length(), file.lastModified(), true)
        return Handle(metadata, {
            FileInputStream(file)
        }, { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }, file)
    }

    private fun content(ref: DocumentRef.ContentUri): DocumentHandle {
        val uri = Uri.parse(ref.uri)
        if (uri.scheme != "content") throw ReaderSourceException(ReaderFailure.PERMISSION)
        var name = uri.lastPathSegment ?: "文档"
        var size: Long? = null
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        // 授权可能在查询期间被撤销：失败时保留 URI 末段名称与未知大小，由后续读取给出失败原因。
        runCatching {
            resolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0 && !cursor.isNull(column)) name = cursor.getString(column)
                    val length = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (length >= 0 && !cursor.isNull(length)) size = cursor.getLong(length).takeIf { it >= 0 }
                }
            }
        }
        val persisted = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val metadata = DocumentMetadata("uri:${uri.normalizeScheme()}", name, mime, size, null, persisted)
        return Handle(
            metadata,
            { readDocument { resolver.openInputStream(uri) } },
            { readDocument { resolver.openFileDescriptor(uri, "r") } },
        )
    }

    /**
     * 执行一次来源读取，并把常见的授权失败转换为统一的来源异常。
     *
     * @param block 打开输入流或文件描述符的调用
     * @return 非空的输入流或文件描述符
     */
    private fun <T : Any> readDocument(block: () -> T?): T {
        val outcome = runCatching { requireDocument(block()) }
        val error = outcome.exceptionOrNull()
        val failure = error?.let { failureOf(it) }
        if (error != null && failure != null) {
            throw ReaderSourceException(failure).also { it.initCause(error) }
        }
        return outcome.getOrThrow()
    }

    /**
     * 把来源读取异常映射为对外失败分类。
     *
     * @param error 读取过程中抛出的异常
     * @return 对应的失败分类；无需转换的异常返回 null
     */
    private fun failureOf(error: Throwable): ReaderFailure? =
        when (error) {
            is SecurityException -> ReaderFailure.PERMISSION
            is FileNotFoundException -> ReaderFailure.MISSING
            is IllegalArgumentException -> ReaderFailure.PERMISSION
            else -> null
        }

    private fun <T> requireDocument(value: T?): T = value ?: throw ReaderSourceException(ReaderFailure.MISSING)

    private inner class Handle(
        override val metadata: DocumentMetadata,
        private val stream: () -> InputStream,
        private val descriptor: () -> ParcelFileDescriptor,
        private val local: File? = null,
    ) : DocumentHandle {
        private var closed = false
        private var temporary: SourceCache.Lease? = null

        override fun openStream(): InputStream {
            check(!closed)
            return stream()
        }

        @Synchronized override fun openSeekable(): ParcelFileDescriptor {
            check(!closed)
            return temporary?.let { ParcelFileDescriptor.open(it.file, ParcelFileDescriptor.MODE_READ_ONLY) } ?: run {
                val fd = descriptor()
                val seekable = runCatching { Os.lseek(fd.fileDescriptor, 0L, OsConstants.SEEK_CUR) }.isSuccess
                if (seekable) {
                    fd
                } else {
                    fd.close()
                    copyToCache()
                }
            }
        }

        @Synchronized override fun archiveFile(): File {
            check(!closed)
            return local ?: temporary?.file ?: copyFile()
        }

        private fun copyToCache(): ParcelFileDescriptor =
            ParcelFileDescriptor.open(copyFile(), ParcelFileDescriptor.MODE_READ_ONLY)

        private fun copyFile(): File {
            val lease = cache.allocate(metadata.size)
            var saved = false
            try {
                openStream().use { input ->
                    lease.file.outputStream().use { output -> copyBounded(input, output, lease.limit) }
                }
                lease.compact()
                temporary = lease
                saved = true
                return lease.file
            } finally {
                if (!saved) lease.close()
            }
        }

        @Synchronized override fun close() {
            closed = true
            temporary?.close()
            temporary = null
        }
    }
}
