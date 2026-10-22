package com.shuiyu.octoberrain.feature.reader

import android.content.Context
import android.os.Build
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.office.slides.SlideEngine
import com.shuiyu.octoberrain.feature.reader.pdf.PdfAdjustment
import com.shuiyu.octoberrain.feature.reader.pdf.PdfEngine
import com.shuiyu.octoberrain.feature.reader.search.SearchHit
import com.shuiyu.octoberrain.feature.reader.search.SearchPage
import com.shuiyu.octoberrain.feature.reader.search.SearchProvider
import com.shuiyu.octoberrain.feature.reader.search.SearchScan
import com.shuiyu.octoberrain.feature.reader.session.BookmarkRow
import com.shuiyu.octoberrain.feature.reader.session.ReadingRepository
import com.shuiyu.octoberrain.feature.reader.session.mergeAliases
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.DocumentKinds
import com.shuiyu.octoberrain.feature.reader.source.SourceResolver
import com.shuiyu.octoberrain.feature.reader.source.ZipContainerProbe
import com.shuiyu.octoberrain.feature.reader.web.ReflowEngine
import com.shuiyu.octoberrain.feature.reader.web.WebPosition
import com.shuiyu.octoberrain.feature.reader.web.WebSearchPage
import java.io.Closeable

/** 会话的文件、引擎与记录由同一个后台任务串行操作，退出关闭自己的资源。 */
@Suppress("TooManyFunctions") // 同一会话集中转发各引擎动作，UI不拥有解析器/文件/数据库。
internal class ReaderSession(
    private val context: Context,
    private val ref: DocumentRef,
    private val width: Int,
    private val height: Int,
    private val fontOverride: Int?,
    private val charset: String?,
) : Closeable {
    private val store = ReadingRepository(context)
    private var handle: DocumentHandle? = null
    private var engine: ReaderEngine? = null

    @Volatile private var frame: ReaderFrame? = null
    var fontSp = 20
        private set
    val name: String get() = handle?.metadata?.name ?: "文件阅读"
    val csv: Boolean get() = handle?.metadata?.let { DocumentKinds.isCsv(it.name, it.mime) } ?: false
    val capabilities get() = engine?.capabilities
    var format: ReaderFormat = ReaderFormat.UNKNOWN
        private set

    suspend fun open(anchor: ReaderLocator?): ReaderFrame {
        val opened = SourceResolver(context).open(ref)
        handle = opened
        val header =
            opened.openStream().use { input ->
                val buffer = ByteArray(1024)
                buffer.copyOf(input.read(buffer).coerceAtLeast(0))
            }
        // ZIP 容器内部标记优先于扩展名判定；预检失败或非 ZIP 文件返回 null 时回退原判定。
        val container =
            if (DocumentKinds.isZipHeader(header)) {
                ZipContainerProbe.probe(opened.archiveFile())
            } else {
                null
            }
        format = DocumentKinds.detect(opened.metadata.name, opened.metadata.mime, header, container)
        store.mergeAliases(opened.metadata)
        val saved = store.load(opened.metadata)
        fontSp = fontOverride ?: saved.fontSp
        val created =
            ReaderRegistry.create(
                context,
                ref,
                anchor ?: saved.locator,
                format,
                opened,
                width,
                height,
                fontSp,
                charset,
            )
        engine = created
        return created.page(anchor ?: saved.locator)
    }

    suspend fun followWebLink(url: String): ReaderFrame? = (engine as? ReflowEngine)?.followLink(url)

    fun locateWeb(anchor: Int): ReaderFrame = (checkNotNull(engine) as ReflowEngine).atAnchor(anchor)

    fun acceptWeb(position: WebPosition): ReaderFrame = (checkNotNull(engine) as ReflowEngine).accept(position)

    fun acceptTable(table: ReaderTable): ReaderFrame = (checkNotNull(engine) as TableEngine).acceptTable(table)

    fun sheetNames(): List<String> = (engine as? TableEngine)?.sheetNames.orEmpty()

    suspend fun locateSheet(
        sheet: Int,
        row: Int,
        column: Int,
    ): ReaderFrame? = (engine as? TableEngine)?.locate(sheet, row, column)

    fun slideOutline(): List<String> = (engine as? SlideEngine)?.outline().orEmpty()

    fun epubToc(): List<com.shuiyu.octoberrain.feature.reader.epub.EpubToc> =
        (engine as? com.shuiyu.octoberrain.feature.reader.epub.EpubEngine)?.toc.orEmpty()

    suspend fun locateEpub(target: com.shuiyu.octoberrain.feature.reader.epub.EpubToc): ReaderFrame? =
        (engine as? com.shuiyu.octoberrain.feature.reader.epub.EpubEngine)?.chapter(target)

    suspend fun locateArchive(directory: String): ReaderFrame =
        checkNotNull(engine).page(ReaderLocator.Archive(directory))

    suspend fun turn(delta: Int): ReaderFrame = checkNotNull(engine).turn(delta)

    suspend fun locatePdf(page: Int): ReaderFrame =
        checkNotNull(engine).page(
            when (format) {
                ReaderFormat.DOCX -> ReaderLocator.Docx(preview = true, page = page)
                ReaderFormat.SLIDES ->
                    (frame?.locator as? ReaderLocator.Slide ?: ReaderLocator.Slide()).copy(
                        slide = page,
                    )
                else -> ReaderLocator.Pdf(page)
            },
        )

    suspend fun adjustPdf(mode: PdfAdjustment): ReaderFrame? =
        if (Build.VERSION.SDK_INT >= 27) (engine as? PdfEngine)?.adjust(mode) else null

    suspend fun findNext(query: String): ReaderFrame? = checkNotNull(engine).findNext(query)

    suspend fun scanSearch(
        query: String,
        offset: Int,
    ): SearchScan = (checkNotNull(engine) as SearchProvider).scan(query, offset)

    suspend fun showSearchHit(
        hit: SearchHit,
        query: String,
    ): ReaderFrame = (checkNotNull(engine) as SearchProvider).showHit(hit, query)

    fun acceptSearch(result: WebSearchPage): SearchPage =
        SearchPage(result.offset, result.total, result.hits.map { SearchHit(searchLocator(it.position), it.snippet) })

    private fun searchLocator(position: WebPosition): ReaderLocator =
        when (format) {
            ReaderFormat.MARKDOWN -> ReaderLocator.Markdown(position.anchor)
            ReaderFormat.DOCX ->
                ReaderLocator.Docx(
                    preview = (frame?.locator as? ReaderLocator.Docx)?.preview ?: false,
                    textOffset = position.anchor,
                    page = -1,
                )
            ReaderFormat.SHEET -> ReaderLocator.Sheet(position.sheet, position.row, position.column)
            else -> error("Unexpected search coordinate type")
        }

    fun trimMemory() {
        engine?.trimMemory()
    }

    /** 主线程确认显示后才接受定位，后台取消的未显示帧不覆盖进度。 */
    fun remember(next: ReaderFrame) {
        frame = next
    }

    fun flush() {
        val shown = frame ?: return
        store.save(checkNotNull(handle).metadata, ref, shown.locator, fontSp)
    }

    fun bookmarks(): List<Pair<String, ReaderLocator>> = store.bookmarks(checkNotNull(handle).metadata.id)

    /** 读取书签行，id 供改名与删除使用；宿主切到 IO 线程调用。 */
    fun bookmarkRows(): List<BookmarkRow> = store.bookmarkRows(checkNotNull(handle).metadata.id)

    /** 修改书签标题；空白标题与未命中的主键都返回 false。 */
    fun renameBookmark(
        id: Long,
        label: String,
    ): Boolean = store.updateBookmark(id, label)

    /** 删除书签；未命中的主键返回 false。 */
    fun deleteBookmark(id: Long): Boolean = store.deleteBookmark(id)

    fun bookmark() {
        store.bookmark(checkNotNull(handle).metadata.id, checkNotNull(frame).locator, checkNotNull(frame).status)
    }

    override fun close() {
        try {
            engine?.close()
        } finally {
            handle?.close()
            store.close()
            engine = null
            handle = null
            frame = null
        }
    }
}
