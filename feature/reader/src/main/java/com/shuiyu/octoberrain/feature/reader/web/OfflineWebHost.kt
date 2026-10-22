package com.shuiyu.octoberrain.feature.reader.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException

/** 单活动WebView，只有本地HTTPS资源；所有创建、消息与销毁均由主线程执行。 */
@SuppressLint("SetJavaScriptEnabled") // 只执行应用白名单脚本，正文先清洗，CSP禁止文档脚本/框架/连接。
internal class OfflineWebHost(
    private val context: Context,
    private val onTurn: (Int) -> Unit,
    private val onLocate: (Int) -> Unit,
    private val onLink: (Uri) -> Unit,
    private val onFailure: (Exception) -> Unit,
) : Closeable {
    val view: WebView
    private var document: WebDocument? = null

    @Volatile private var loader: WebViewAssetLoader? = null
    private var closed = false
    private var requestId = 0
    private var pending: CompletableDeferred<WebEvent>? = null

    init {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER,
            )
        ) {
            throw ReaderSourceException(ReaderFailure.UNSUPPORTED)
        }
        view = WebView(context)
        view.setBackgroundColor(android.graphics.Color.WHITE)
        view.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            domStorageEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = true
        }
        android.webkit.CookieManager
            .getInstance()
            .setAcceptThirdPartyCookies(view, false)
        view.setDownloadListener { _, _, _, _, _ ->
            // 阅读器不启动下载。
        }
        view.webChromeClient =
            object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    callback: android.webkit.ValueCallback<Array<Uri>>,
                    params: WebChromeClient.FileChooserParams,
                ): Boolean {
                    callback.onReceiveValue(null)
                    return true
                }

                override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                    request.deny()
                }
            }
        view.webViewClient =
            object : WebViewClient() {
                override fun shouldInterceptRequest(
                    web: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse = loader?.shouldInterceptRequest(request.url) ?: blocked()

                override fun shouldOverrideUrlLoading(
                    web: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val local = document?.let { indexUrl(it) } == request.url.toString()
                    if (!local && request.hasGesture() && request.isForMainFrame) {
                        if (request.url.scheme in listOf("http", "https")) onLink(request.url)
                    }
                    return !local
                }

                override fun onRenderProcessGone(
                    web: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    InkLog.w("ReaderWeb", "rendererGone crashed=${detail.didCrash()}")
                    val error = ReaderSourceException(ReaderFailure.RENDERER)
                    pending?.completeExceptionally(error)
                    close()
                    onFailure(error)
                    return true
                }
            }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                view,
                "ReaderBridge",
                setOf(WebMessagePolicy.ORIGIN),
            ) { _, message, origin, main, _ ->
                val current = document
                val validOrigin =
                    origin.scheme == "https" && origin.host == WebMessagePolicy.HOST && origin.port in listOf(-1, 443)
                val event =
                    current?.let {
                        WebMessagePolicy.parse(
                            message.data ?: "",
                            it.session,
                            if (validOrigin) WebMessagePolicy.ORIGIN else origin.toString(),
                            main,
                            closed,
                        )
                    }
                when (event?.kind) {
                    "ready" -> if (event.request == requestId) pending?.complete(event)
                    "locate" -> if (pending == null) event.position?.let { onLocate(it.anchor) }
                    "turn" -> if (pending == null) onTurn(event.delta)
                    "link" ->
                        if (pending == null) {
                            event.url?.let { url ->
                                val target = Uri.parse(url)
                                if (target.scheme in listOf("http", "https") &&
                                    (target.host != WebMessagePolicy.HOST || url in current?.internalLinks.orEmpty())
                                ) {
                                    onLink(target)
                                }
                            }
                        }
                    "error" -> {
                        val error = ReaderSourceException(ReaderFailure.RENDERER)
                        if (event.request == requestId) pending?.completeExceptionally(error)
                        onFailure(error)
                    }
                }
            }
        }
        InkLog.d("ReaderWeb", "webview=${WebView.getCurrentWebViewPackage()?.versionName} messageListener=true")
    }

    suspend fun present(request: WebRequest): WebEvent {
        check(!closed && Looper.myLooper() == Looper.getMainLooper())
        val result = CompletableDeferred<WebEvent>()
        pending = result
        val id = ++requestId
        val command =
            JSONObject()
                .put("request", id)
                .put("kind", request.kind)
                .put("delta", request.delta)
                .put("page", request.position.page)
                .put("anchor", request.position.anchor.coerceIn(0, 4_000_000))
                .put(
                    "fraction",
                    request.position.fraction
                        .takeIf { it.isFinite() }
                        ?.coerceIn(0.0, 1.0) ?: 0.0,
                ).put("query", request.query ?: "")
        try {
            if (document !== request.document) {
                document = request.document
                loader = buildLoader(request.document, command.toString())
                view.loadUrl(indexUrl(request.document))
            } else {
                view.evaluateJavascript("window.ReaderHost.command($command)", null)
            }
            return withTimeout(15000) { result.await() }
        } catch (_: TimeoutCancellationException) {
            throw ReaderSourceException(ReaderFailure.RENDERER)
        } finally {
            pending = null
        }
    }

    private fun indexUrl(doc: WebDocument): String = "${WebMessagePolicy.ORIGIN}/session/${doc.session}/index.html"

    private fun buildLoader(
        doc: WebDocument,
        command: String,
    ): WebViewAssetLoader =
        WebViewAssetLoader
            .Builder()
            .setDomain(WebMessagePolicy.HOST)
            .addPathHandler("/static/") { path ->
                staticResource(doc, path)
            }.addPathHandler("/session/${doc.session}/") { path ->
                try {
                    when {
                        path == "index.html" -> {
                            response("text/html", ByteArrayInputStream(indexHtml(doc, command)), doc.docxPreview)
                        }
                        path.startsWith("res/") ->
                            doc.resources.open(path.removePrefix("res/"))?.let { response(it.mime, it.stream) }
                                ?: blocked()
                        else -> blocked()
                    }
                } catch (_: IOException) {
                    blocked()
                } catch (_: SecurityException) {
                    blocked()
                }
            }.build()

    private fun staticResource(
        doc: WebDocument,
        path: String,
    ): WebResourceResponse {
        val scripts = setOf("docx-host.js", "jszip3.min.js", "docx-preview.min.js")
        return if (path !in setOf("host.js", "style.css") && !(doc.docxPreview && path in scripts)) {
            blocked()
        } else {
            try {
                response(
                    if (path.endsWith(".js")) "application/javascript" else "text/css",
                    context.assets.open("reader/web/$path"),
                )
            } catch (_: IOException) {
                blocked()
            }
        }
    }

    private fun indexHtml(
        doc: WebDocument,
        command: String,
    ): ByteArray {
        val bootstrap = Uri.encode(command)
        val font = doc.fontSp.coerceIn(14, 36)
        val scripts =
            if (doc.docxPreview) {
                "<script src=\"/static/jszip3.min.js\"></script>" +
                    "<script src=\"/static/docx-preview.min.js\"></script>" +
                    "<script src=\"/static/docx-host.js\"></script>"
            } else {
                "<script src=\"/static/host.js\"></script>"
            }
        val html =
            """
            <!doctype html><html lang="zh-CN" data-reader-session="${doc.session}"
            data-reader-command="$bootstrap"><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
            <link rel="stylesheet" href="/static/style.css"><style>:root{font-size:${font}px}${doc.styles}</style>
            <body><main id="reader-content">${if (doc.docxPreview) "" else doc.body}</main>
            $scripts</body></html>
            """.trimIndent()
        return html.toByteArray(Charsets.UTF_8)
    }

    private fun response(
        mime: String,
        stream: java.io.InputStream,
        connectLocal: Boolean = false,
    ): WebResourceResponse =
        WebResourceResponse(
            mime,
            "UTF-8",
            200,
            "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "X-Content-Type-Options" to "nosniff",
                "Content-Security-Policy" to
                    "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: blob:; font-src 'self' data:; " +
                    "connect-src ${if (connectLocal) "'self'" else "'none'"}; object-src 'none'; " +
                    "frame-src 'none'; worker-src 'none'; base-uri 'none'; form-action 'none'",
            ),
            stream,
        )

    private fun blocked(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Blocked",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0)),
        )

    override fun close() {
        if (!closed) {
            closed = true
            pending?.cancel()
            document = null
            loader = null
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.removeWebMessageListener(view, "ReaderBridge")
            }
            (view.parent as? ViewGroup)?.removeView(view)
            view.stopLoading()
            view.removeAllViews()
            view.destroy()
        }
    }

    companion object {
        fun supported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    }
}
