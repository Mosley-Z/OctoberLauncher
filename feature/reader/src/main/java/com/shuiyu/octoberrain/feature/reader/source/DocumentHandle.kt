package com.shuiyu.octoberrain.feature.reader.source

import android.os.ParcelFileDescriptor
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.ReaderFailure
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

internal class ReaderSourceException(
    val failure: ReaderFailure,
) : IOException(failure.name)

/** 流/FD由借用方关闭；handle只清理自己创建的临时文件，关闭后不可再次借用。 */
internal interface DocumentHandle : Closeable {
    val metadata: DocumentMetadata

    fun openStream(): InputStream

    fun openSeekable(): ParcelFileDescriptor

    fun openChannel(): SeekableByteChannel = ParcelFileDescriptor.AutoCloseInputStream(openSeekable()).channel

    fun archiveFile(): File
}
