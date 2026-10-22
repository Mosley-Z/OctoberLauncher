package com.shuiyu.octoberrain.feature.reader

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.VolumeKeySink
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.feature.reader.pdf.PdfAdjustment
import com.shuiyu.octoberrain.feature.reader.source.openExternalDocument
import com.shuiyu.octoberrain.feature.reader.web.OfflineWebHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 一个可见会话，暂停先取消结果再后台释放；旧会话关闭后才重新打开。
 * 生命周期与工具栏动作共享会话代次；提供者、解析器、SQLite异常在此UI边界归一，避免使桌面退出。
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
internal class ReaderScreen(
    private val ref: DocumentRef,
) : Screen,
    VolumeKeySink,
    BookmarkHost {
    private lateinit var chrome: ReaderChrome
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var action: Job? = null
    private var cleanup: Job? = null
    private var session: ReaderSession? = null
    private var generation = 0
    private var visible = false
    private var font: Int? = null
    private var charset: String? = null
    private var frame: ReaderFrame? = null
    private var position: ReaderLocator? = null
    private val refresh = ReaderRefresh()
    private lateinit var preferences: ReaderPreferences
    private var trimPending = false
    private val memoryCallbacks =
        object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() = requestTrim()

            @Suppress("DEPRECATION") // Android 11目标机仍会派发运行中内存压力等级。
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) requestTrim()
            }
        }
    private var openedWidth = 0
    private var openedHeight = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var relayoutPending = false
    private var format = ReaderFormat.UNKNOWN
    private var webHost: OfflineWebHost? = null
    private val bookmarks = ReaderBookmarks(this)

    override val context: Context get() = chrome.context

    override val bookmarkSession: ReaderSession? get() = session

    override val bookmarkVisible: Boolean get() = visible

    override val bookmarkGeneration: Int get() = generation

    override val bookmarkScope: CoroutineScope get() = scope

    override fun bookmarkBusy(): Boolean = action?.isActive == true

    override fun setBookmarkCharset(value: String?) {
        charset = value
    }

    override fun openBookmark(locator: ReaderLocator) {
        open(locator)
    }

    override fun setBookmarkAction(job: Job?) {
        action = job
        val mark = generation
        job?.invokeOnCompletion { finishAction(mark) }
    }

    override fun showBookmarkError(error: Exception) {
        showError(error)
    }

    override fun createView(context: Context): View {
        preferences = ReaderPreferences(context)
        context.applicationContext.registerComponentCallbacks(memoryCallbacks)
        chrome = ReaderChrome(context, { AppServices.router.back() }, ::turn, ::options)
        chrome.page.sizeListener = ::onPageSize
        return chrome
    }

    /**
     * 旋转或窗口尺寸变化后按当前定位重排。
     *
     * 每次变化都更新目标尺寸，打开中的变化留给打开完成回调消费；会话就绪且空闲时
     * 立即重排，有动作进行中则登记待重排标记，由 [finishAction] 统一消费。
     */
    private fun onPageSize(
        width: Int,
        height: Int,
    ) {
        targetWidth = width
        targetHeight = height
        if (!visible) return
        traceLayout("resize")
        val decision =
            ReaderRelayout.onSizeChanged(
                visible = true,
                sessionReady = session != null,
                busy = action?.isActive == true,
                targetWidth = width,
                targetHeight = height,
                openedWidth = openedWidth,
                openedHeight = openedHeight,
            )
        when (decision) {
            ReaderRelayout.Decision.NONE -> Unit
            ReaderRelayout.Decision.QUEUE -> relayoutPending = true
            ReaderRelayout.Decision.RELAYOUT -> relayoutNow()
        }
    }

    override fun onResume() {
        visible = true
        chrome.page.post { if (visible) open(position) }
    }

    override fun onPause() {
        visible = false
        trimPending = false
        generation++
        relayoutPending = false
        targetWidth = 0
        targetHeight = 0
        webHost?.close()
        webHost = null
        val pending = action
        pending?.cancel()
        val previous = session
        session = null
        chrome.page.removeAllViews()
        chrome.page.frame = null
        frame = null
        val previousCleanup = cleanup
        cleanup =
            scope.launch(NonCancellable + Dispatchers.IO) {
                previousCleanup?.join()
                pending?.join()
                runCatching { previous?.flush() }.onFailure { failure ->
                    InkLog.w("阅读器", "退出保存失败：${failure.javaClass.simpleName}")
                }
                previous?.close()
            }
    }

    override fun onDestroy() {
        chrome.context.applicationContext.unregisterComponentCallbacks(memoryCallbacks)
        onPause()
        scope.cancel()
    }

    override fun onVolumeKey(up: Boolean): Boolean {
        turn(if (up) -1 else 1)
        return true
    }

    private fun open(anchor: ReaderLocator?) {
        // 打开前保留最新目标尺寸：onPause 会清空待调度状态，重排时仍按目标尺寸构造。
        val wantedWidth = targetWidth
        val wantedHeight = targetHeight
        onPause()
        visible = true
        targetWidth = wantedWidth
        targetHeight = wantedHeight
        val mark = ++generation
        val startedAt = SystemClock.elapsedRealtime()
        chrome.busy(true)
        chrome.status.text = "正在打开…"
        val job =
            scope.launch {
                cleanup?.join()
                val page = chrome.page
                // 使用打开前的目标尺寸构造引擎，打开期间视图再变化时不会掩盖本次尺寸。
                val engineWidth = if (targetWidth > 0) targetWidth else page.width
                val engineHeight = if (targetHeight > 0) targetHeight else page.height
                val reader =
                    ReaderSession(
                        chrome.context,
                        ref,
                        (engineWidth - page.inset * 2).coerceAtLeast(1),
                        (engineHeight - page.inset * 2).coerceAtLeast(1),
                        font,
                        charset,
                    )
                var retained = false
                try {
                    val ready = withContext(Dispatchers.IO) { reader.open(anchor) }
                    if (visible && mark == generation) {
                        session = reader
                        retained = true
                        font = reader.fontSp
                        format = reader.format
                        openedWidth = engineWidth
                        openedHeight = engineHeight
                        chrome.title.text = reader.name
                        publish(ready)
                        if (chrome.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                            InkLog.d("ReaderTiming", "prepareMs=${SystemClock.elapsedRealtime() - startedAt}")
                        }
                        withContext(Dispatchers.IO) { reader.flush() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (visible && mark == generation) {
                        format = reader.format
                        chrome.title.text = reader.name
                        showError(failure)
                    }
                } finally {
                    if (!retained) withContext(NonCancellable + Dispatchers.IO) { reader.close() }
                    if (visible && mark == generation) chrome.busy(false)
                }
            }
        action = job
        job.invokeOnCompletion { finishAction(mark) }
    }

    /**
     * 动作完成后的统一收尾：消费动作期间登记的尺寸变化。
     *
     * 回调在动作结束之后执行，避免动作自身的完成回调再次触发重排；旧 generation
     * 的回调直接丢弃，避免离开页面后又被旧任务重新打开。
     */
    private fun finishAction(mark: Int) {
        if (!visible || mark != generation) return
        scope.launch {
            if (!visible || mark != generation || session == null) return@launch
            val queued =
                ReaderRelayout.afterAction(
                    visible = true,
                    sessionReady = true,
                    queued = relayoutPending,
                )
            val sizeChanged =
                targetWidth > 0 &&
                    (targetWidth != openedWidth || targetHeight != openedHeight)
            if (queued || sizeChanged) {
                relayoutPending = false
                relayoutNow()
            } else if (trimPending) {
                trimPending = false
                perform {
                    trimMemory()
                    null
                }
            }
        }
    }

    /** Debug 验收只记录调度、尺寸和文本字节锚点，不记录正文或来源 URI。 */
    private fun traceLayout(stage: String) {
        if (chrome.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val anchor = (position as? ReaderLocator.Text)?.byteOffset
            InkLog.d(
                "ReaderLayout",
                "$stage busy=${action?.isActive == true} target=${targetWidth}x$targetHeight " +
                    "opened=${openedWidth}x$openedHeight anchor=$anchor",
            )
        }
    }

    /** 按最新目标尺寸重开当前会话；会话未就绪或尺寸未变化时不动作。 */
    private fun relayoutNow() {
        val ready = visible && session != null && action?.isActive != true
        val sizeChanged =
            targetWidth > 0 &&
                targetHeight > 0 &&
                (targetWidth != openedWidth || targetHeight != openedHeight)
        if (ready && sizeChanged) open(frame?.locator)
    }

    private fun requestTrim() {
        if (!visible) return
        trimPending = true
        if (action?.isActive != true) finishAction(generation)
    }

    private fun turn(delta: Int) = perform(pageTurn = true) { turn(delta) }

    private fun perform(
        pageTurn: Boolean = false,
        operation: suspend ReaderSession.() -> ReaderFrame?,
    ) {
        val reader = session ?: return
        if (action?.isActive == true || !visible) return
        val startedAt = SystemClock.elapsedRealtime()
        chrome.busy(true)
        val mark = generation
        val job =
            scope.launch {
                try {
                    val next = withContext(Dispatchers.IO) { reader.operation() }
                    if (visible && mark == generation && next != null) {
                        publish(next, pageTurn)
                        if (chrome.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                            InkLog.d("ReaderTiming", "actionMs=${SystemClock.elapsedRealtime() - startedAt}")
                        }
                        withContext(Dispatchers.IO) { reader.flush() }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (visible && mark == generation) showError(failure)
                } finally {
                    if (visible && mark == generation) chrome.busy(false)
                }
            }
        action = job
        job.invokeOnCompletion { finishAction(mark) }
    }

    private suspend fun publish(
        next: ReaderFrame,
        pageTurn: Boolean = false,
    ) {
        val web = next.web
        val shown =
            if (web == null) {
                webHost?.close()
                webHost = null
                showArchive(next)
                next
            } else {
                var host = webHost
                if (host == null) {
                    host = createWebHost()
                    webHost = host
                    chrome.page.addView(host.view, android.widget.FrameLayout.LayoutParams(-1, -1))
                }
                val event = host.present(web)
                if (!event.found) {
                    AppServices.router.toast("已搜索到文末，未找到后续匹配")
                    return
                }
                checkNotNull(session).acceptWeb(checkNotNull(event.position))
            }
        val turned = pageTurn && frame?.locator != shown.locator
        frame = shown
        position = shown.locator
        session?.remember(shown)
        chrome.page.frame = shown
        chrome.status.text = shown.status
        traceLayout("frame")
        if (refresh.needsFull(preferences.refreshPages, RefreshScheduler.fullRefreshVersion, turned)) {
            RefreshScheduler.requestFullRefresh(RefreshReason.CONTENT)
        } else {
            RefreshScheduler.notifyContentChanged()
        }
    }

    private fun showArchive(next: ReaderFrame) {
        chrome.page.removeAllViews()
        val archive = next.archive ?: return
        val context = chrome.context
        val rows = android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }

        fun row(
            label: String,
            click: () -> Unit,
        ) {
            rows.addView(
                com.shuiyu.octoberrain.core.design.InkWidgets
                    .button(context, label, onClick = click),
            )
        }
        if (archive.directory.isNotEmpty()) {
            row("上一级 · /${archive.directory}") {
                perform { locateArchive(archive.directory.substringBeforeLast('/', "")) }
            }
        }
        archive.items.forEach { item ->
            val label = (if (item.directory) "目录 · " else "") + item.path.substringAfterLast('/')
            row(label) {
                if (item.directory) {
                    perform { locateArchive(item.path) }
                } else {
                    val encoding = (next.locator as ReaderLocator.Archive).charset
                    AppServices.reader?.open(DocumentRef.ArchiveEntry(ref, item.path, encoding))
                }
            }
        }
        val scroll = android.widget.ScrollView(context).apply { addView(rows) }
        chrome.page.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
    }

    private fun createWebHost(): OfflineWebHost {
        val mark = generation
        return OfflineWebHost(
            chrome.context,
            onTurn = { delta -> if (visible && mark == generation) turn(delta) },
            onLocate = { anchor -> if (visible && mark == generation) perform { locateWeb(anchor) } },
            onLink = { uri ->
                if (visible &&
                    mark == generation &&
                    uri.host == com.shuiyu.octoberrain.feature.reader.web.WebMessagePolicy.HOST
                ) {
                    perform { followWebLink(uri.toString()) }
                } else if (visible && mark == generation) {
                    try {
                        chrome.context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (_: ActivityNotFoundException) {
                        AppServices.router.toast("未找到可打开此链接的浏览器")
                    }
                }
            },
            onFailure = { error ->
                if (visible && mark == generation) {
                    webHost = null
                    showError(error)
                }
            },
        )
    }

    private fun addEpubOptions(actions: MutableList<Pair<String, () -> Unit>>) {
        if (format != ReaderFormat.EPUB) return
        actions += "章节目录" to {
            val toc = session?.epubToc().orEmpty()
            AlertDialog
                .Builder(chrome.context)
                .setTitle("章节目录")
                .setItems(toc.map { it.title }.toTypedArray()) { _, index -> perform { locateEpub(toc[index]) } }
                .show()
        }
    }

    private fun addArchiveOptions(actions: MutableList<Pair<String, () -> Unit>>) {
        if (format == ReaderFormat.ZIP) {
            listOf("UTF-8", "GB18030").forEach { encoding ->
                actions += "文件名编码 $encoding" to {
                    charset = encoding
                    open(ReaderLocator.Archive(charset = encoding))
                }
            }
        }
    }

    private fun options() {
        val capabilities = session?.capabilities
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (capabilities?.reflow == true) {
            actions += "字号增加" to {
                font = ((font ?: 20) + 2).coerceAtMost(36)
                open(frame?.locator)
            }
            actions += "字号减小" to {
                font = ((font ?: 20) - 2).coerceAtLeast(14)
                open(frame?.locator)
            }
        }
        if (frame?.locator is ReaderLocator.Text || session == null) {
            listOf("UTF-8", "GB18030", "UTF-16LE", "UTF-16BE").forEach { name ->
                actions += "编码 $name" to {
                    charset = name
                    open(frame?.locator)
                }
            }
        }
        addArchiveOptions(actions)
        addEpubOptions(actions)
        addDocxOptions(actions)
        if (format == ReaderFormat.MARKDOWN) {
            if (frame?.locator !is ReaderLocator.Text) {
                actions += "纯文本阅读（从头，不显示图片和表格排版）" to {
                    charset = "UTF-8"
                    open(ReaderLocator.Text())
                }
            } else {
                actions += "排版阅读（从头）" to {
                    charset = null
                    open(ReaderLocator.Markdown())
                }
            }
        }
        if (capabilities?.pageJump == true) actions += "跳到指定页" to ::jumpPage
        if (capabilities?.zoom == true) addPdfOptions(actions)
        if (capabilities?.bookmark == true) {
            actions += "添加书签" to ::addBookmark
            actions += "查看书签" to { bookmarks.show() }
        }
        if (capabilities?.search == true) actions += "搜索下一个" to ::search
        actions += "翻页全刷" to ::chooseRefresh
        if (ref is DocumentRef.ContentUri) actions += "重新选择文档" to ::chooseDocument
        actions += "其他应用打开" to { openExternalDocument(chrome.context, ref) }
        AlertDialog
            .Builder(chrome.context)
            .setTitle("阅读设置")
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .show()
    }

    private fun addPdfOptions(actions: MutableList<Pair<String, () -> Unit>>) {
        listOf(
            "放大" to PdfAdjustment.ZOOM_IN,
            "缩小" to PdfAdjustment.ZOOM_OUT,
            "视口左移" to PdfAdjustment.LEFT,
            "视口右移" to PdfAdjustment.RIGHT,
            "视口上移" to PdfAdjustment.UP,
            "视口下移" to PdfAdjustment.DOWN,
            "切换裁边5%" to PdfAdjustment.CROP,
            "适合整页" to PdfAdjustment.FIT,
        ).forEach { (label, mode) ->
            actions += label to { perform { adjustPdf(mode) } }
        }
    }

    private fun addDocxOptions(actions: MutableList<Pair<String, () -> Unit>>) {
        if (format != ReaderFormat.DOCX) return
        val current = frame?.locator as? ReaderLocator.Docx ?: ReaderLocator.Docx()
        actions += (if (current.preview) "正文阅读" else "原版式预览") to {
            open(current.copy(preview = !current.preview, page = -1))
        }
    }

    private fun chooseRefresh() {
        val choices = listOf(0, 6, 12)
        AlertDialog
            .Builder(chrome.context)
            .setTitle("翻页全刷")
            .setSingleChoiceItems(
                arrayOf("关闭", "每6页", "每12页"),
                choices.indexOf(preferences.refreshPages),
            ) { dialog, index ->
                preferences.refreshPages = choices[index]
                refresh.reset()
                dialog.dismiss()
            }.setNegativeButton("返回", null)
            .show()
    }

    private fun chooseDocument() {
        val started =
            AppServices.picker.pick(emptyArray()) { uri ->
                if (uri != null) AppServices.reader?.open(DocumentRef.ContentUri(uri.toString()))
            }
        if (!started) AppServices.router.toast("系统文件选择器不可用，请从文件管理器重新打开")
    }

    private fun jumpPage() {
        val input =
            EditText(chrome.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(8))
                val page =
                    when (val locator = frame?.locator) {
                        is ReaderLocator.Pdf -> locator.page
                        is ReaderLocator.Docx -> locator.page
                        else -> 0
                    }
                setText(String.format(Locale.ROOT, "%d", page + 1))
                selectAll()
            }
        AlertDialog
            .Builder(chrome.context)
            .setTitle("跳到指定页（从1开始）")
            .setView(input)
            .setNegativeButton("返回", null)
            .setPositiveButton("跳转") { _, _ ->
                val page = input.text.toString().toIntOrNull()
                if (page != null && page > 0) perform { locatePdf(page - 1) }
            }.show()
    }

    private var searchQuery = ""

    private fun search() {
        if (session?.capabilities?.search != true || action?.isActive == true) return
        val input =
            EditText(chrome.context).apply {
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(256))
                setText(searchQuery)
            }
        AlertDialog
            .Builder(chrome.context)
            .setTitle("搜索下一个（从当前位置）")
            .setView(input)
            .setNegativeButton("返回", null)
            .setPositiveButton("查找") { _, _ ->
                val query = input.text.toString()
                if (query.isNotEmpty()) {
                    searchQuery = query
                    findNext(query)
                }
            }.show()
    }

    private fun findNext(query: String) {
        val reader = session ?: return
        val mark = generation
        chrome.busy(true)
        val cancelSearch = {
            action?.cancel()
            if (visible && mark == generation) open(frame?.locator)
        }
        val progress =
            AlertDialog
                .Builder(chrome.context)
                .setMessage("正在分块查找…")
                .setNegativeButton("取消") { _, _ -> cancelSearch() }
                .setOnCancelListener { cancelSearch() }
                .show()
        val job =
            scope.launch {
                try {
                    val found = withContext(Dispatchers.IO) { reader.findNext(query) }
                    if (visible && mark == generation) {
                        if (found == null) {
                            AppServices.router.toast("已搜索到文末，未找到后续匹配")
                        } else {
                            publish(found)
                            withContext(Dispatchers.IO) { reader.flush() }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (visible && mark == generation) showError(failure)
                } finally {
                    progress.dismiss()
                    if (visible && mark == generation) chrome.busy(false)
                }
            }
        action = job
        job.invokeOnCompletion { finishAction(mark) }
    }

    private fun addBookmark() {
        val reader = session ?: return
        if (action?.isActive == true) return
        val mark = generation
        val job =
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { reader.bookmark() }
                    if (visible && mark == generation) AppServices.router.toast("书签已保存")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    InkLog.w("阅读器", "书签保存失败：${failure.javaClass.simpleName}")
                    if (visible && mark == generation) AppServices.router.toast("书签保存失败")
                }
            }
        action = job
        job.invokeOnCompletion { finishAction(mark) }
    }

    /** 把异常转换为状态栏文案。 */
    private fun showError(error: Exception) {
        chrome.status.text = ReaderErrors.message(error, format)
    }
}
