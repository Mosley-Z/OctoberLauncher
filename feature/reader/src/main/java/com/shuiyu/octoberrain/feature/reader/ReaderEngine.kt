package com.shuiyu.octoberrain.feature.reader

import android.graphics.Bitmap
import android.text.StaticLayout
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderLocator
import java.io.Closeable

/** 解析/排版/跳转由宿主在IO线程串行调用；帧发布后只读，关闭不能回收视图仍持有的帧。 */
internal interface ReaderEngine : Closeable {
    val capabilities: ReaderCapabilities

    suspend fun page(locator: ReaderLocator?): ReaderFrame

    suspend fun turn(delta: Int): ReaderFrame

    suspend fun findNext(query: String): ReaderFrame? = null
}

internal data class ReaderFrame(
    val locator: ReaderLocator,
    val status: String,
    val text: StaticLayout? = null,
    val bitmap: Bitmap? = null,
    val archive: com.shuiyu.octoberrain.feature.reader.archive.ArchivePage? = null,
    val web: com.shuiyu.octoberrain.feature.reader.web.WebRequest? = null,
)
