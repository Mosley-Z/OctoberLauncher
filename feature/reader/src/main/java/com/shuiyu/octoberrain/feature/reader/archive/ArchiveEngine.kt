package com.shuiyu.octoberrain.feature.reader.archive

import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderEngine
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import java.nio.charset.Charset

internal data class ArchivePage(
    val directory: String,
    val items: List<ArchiveItem>,
)

internal class ArchiveEngine(
    source: DocumentHandle,
    encoding: String,
) : ReaderEngine {
    private val charset = encoding
    private val zip = ZipArchive(source.archiveFile(), Charset.forName(charset))
    private var directory = ""
    private var index = 0
    override val capabilities = ReaderCapabilities()

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val archive = locator as? ReaderLocator.Archive
        directory = ArchivePaths.normalize(archive?.directory.orEmpty())
        index = archive?.page?.coerceAtLeast(0) ?: 0
        return render()
    }

    override suspend fun turn(delta: Int): ReaderFrame {
        index = (index + delta.coerceIn(-1, 1)).coerceAtLeast(0)
        return render()
    }

    private fun render(): ReaderFrame {
        val children = zip.children(directory)
        val pages = ((children.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        index = index.coerceAtMost(pages - 1)
        return ReaderFrame(
            ReaderLocator.Archive(directory, index, charset),
            "${index + 1}/$pages · ${children.size} 项",
            archive = ArchivePage(directory, children.drop(index * PAGE_SIZE).take(PAGE_SIZE)),
        )
    }

    override fun close() = zip.close()

    private companion object {
        const val PAGE_SIZE = 100
    }
}
