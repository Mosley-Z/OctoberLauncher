package com.shuiyu.octoberrain.feature.reader

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.EditText
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.VolumeKeySink
import com.shuiyu.octoberrain.feature.reader.pdf.PdfAdjustment
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
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
    VolumeKeySink {
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
    private var turns = 0
    private var format = ReaderFormat.UNKNOWN
    private var webHost: OfflineWebHost? = null

    override fun createView(context: Context): View {
        chrome = ReaderChrome(context, { AppServices.router.back() }, ::turn, ::options)
        return chrome
    }

    override fun onResume() {
        visible = true
        chrome.page.post { if (visible) open(position) }
    }

    override fun onPause() {
        visible = false
        generation++
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
        onPause()
        scope.cancel()
    }

    override fun onVolumeKey(up: Boolean): Boolean {
        turn(if (up) -1 else 1)
        return true
    }

    private fun open(anchor: ReaderLocator?) {
        onPause()
        visible = true
        val mark = ++generation
        val startedAt = SystemClock.elapsedRealtime()
        chrome.busy(true)
        chrome.status.text = "正在打开…"
        action =
            scope.launch {
                cleanup?.join()
                val page = chrome.page
                val reader =
                    ReaderSession(
                        chrome.context,
                        ref,
                        (page.width - page.inset * 2).coerceAtLeast(1),
                        (page.height - page.inset * 2).coerceAtLeast(1),
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
    }

    private fun turn(delta: Int) = perform { turn(delta) }

    private fun perform(operation: suspend ReaderSession.() -> ReaderFrame?) {
        val reader = session ?: return
        if (action?.isActive == true || !visible) return
        val startedAt = SystemClock.elapsedRealtime()
        chrome.busy(true)
        val mark = generation
        action =
            scope.launch {
                try {
                    val next = withContext(Dispatchers.IO) { reader.operation() }
                    if (visible && mark == generation && next != null) {
                        publish(next)
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
    }

    private suspend fun publish(next: ReaderFrame) {
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
        frame = shown
        position = shown.locator
        session?.remember(shown)
        chrome.page.frame = shown
        chrome.status.text = shown.status
        turns++
        if (turns % 6 == 0) {
            AppServices.refresh.requestFullRefresh(RefreshReason.CONTENT)
        } else {
            AppServices.refresh.requestPartial()
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
        if (format == ReaderFormat.MARKDOWN) {
            if (frame?.locator !is ReaderLocator.Text) {
                actions += "纯文本阅读（从头）" to {
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
            actions += "查看书签" to ::showBookmarks
        }
        if (capabilities?.search == true) actions += "搜索下一个" to ::search
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

    private fun jumpPage() {
        val input =
            EditText(chrome.context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(8))
                setText(String.format(Locale.ROOT, "%d", (frame?.locator as? ReaderLocator.Pdf)?.page?.plus(1) ?: 1))
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
        action =
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
    }

    private fun addBookmark() {
        val reader = session ?: return
        if (action?.isActive == true) return
        val mark = generation
        action =
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
    }

    private fun showBookmarks() {
        val reader = session ?: return
        if (action?.isActive == true) return
        val mark = generation
        action =
            scope.launch {
                try {
                    val marks = withContext(Dispatchers.IO) { reader.bookmarks() }
                    if (visible && mark == generation) {
                        if (marks.isEmpty()) {
                            AppServices.router.toast("暂无书签")
                        } else {
                            AlertDialog
                                .Builder(
                                    chrome.context,
                                ).setTitle("书签")
                                .setItems(marks.map { it.first }.toTypedArray()) { _, index ->
                                    val locator = marks[index].second
                                    charset = (locator as? ReaderLocator.Text)?.charset
                                    open(locator)
                                }.show()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    if (visible && mark == generation) showError(failure)
                }
            }
    }

    private fun unsupportedMessage(): String =
        if (format == ReaderFormat.EPUB) {
            "当前仅支持无加密资源的 UTF-8 重排版 EPUB，可使用其他应用打开"
        } else {
            "暂不支持此文档类型，可使用其他应用打开"
        }

    private fun showError(error: Exception) {
        chrome.status.text =
            when {
                error is java.util.zip.ZipException -> "ZIP损坏、超出预算或使用不支持的加密/压缩格式，可使用其他应用打开"
                error is java.io.FileNotFoundException -> "源文件不存在或读取授权已失效"
                error is SecurityException -> "文档授权已失效，请从文件选择器重新打开"
                error is java.nio.charset.CharacterCodingException -> "编码无法识别，请在阅读设置中选择正确编码"
                error is ReaderSourceException ->
                    when (error.failure) {
                        ReaderFailure.MISSING -> "源文件不存在"
                        ReaderFailure.PERMISSION -> "无法读取文档，请重新授权"
                        ReaderFailure.UNSUPPORTED -> unsupportedMessage()
                        ReaderFailure.LIMIT -> "文档超出当前阅读预算，请在阅读设置中选择其他打开方式"
                        ReaderFailure.PASSWORD -> "PDF受密码保护，当前系统引擎不支持解密，可用其他应用打开"
                        ReaderFailure.RENDERER -> "文档显示已中断或超时，请返回后重新打开"
                        else -> "文档无法解析"
                    }
                else -> "文档读取失败，可返回后重新打开"
            }
    }
}
