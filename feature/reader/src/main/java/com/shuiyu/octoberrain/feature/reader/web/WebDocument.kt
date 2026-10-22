package com.shuiyu.octoberrain.feature.reader.web

import com.shuiyu.octoberrain.feature.reader.ReaderEngine
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import java.io.Closeable
import java.io.InputStream

internal data class WebResource(
    val mime: String,
    val stream: InputStream,
)

/** 打开相对资源只走当前来源，不能提供任意文件或网络读取。 */
internal interface WebResources : Closeable {
    fun open(path: String): WebResource?
}

internal data class WebDocument(
    val session: String,
    val body: String,
    val fontSp: Int,
    val resources: WebResources,
    val internalLinks: Set<String> = emptySet(),
    val styles: String = "",
)

internal data class WebPosition(
    val anchor: Int = 0,
    val fraction: Double = 0.0,
    val page: Int = 0,
    val pages: Int = 1,
)

internal data class WebRequest(
    val document: WebDocument,
    val position: WebPosition,
    val kind: String = "position",
    val delta: Int = 0,
    val query: String? = null,
)

/** WebView在主线程完成排版后，才接受定位并保存；IO引擎不接触WebView。 */
internal interface ReflowEngine : ReaderEngine {
    fun atAnchor(anchor: Int): ReaderFrame

    fun accept(position: WebPosition): ReaderFrame

    suspend fun followLink(url: String): ReaderFrame? = null
}
