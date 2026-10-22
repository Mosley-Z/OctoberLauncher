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

    /** 宿主在动作队列空闲时调用，仅释放可重建缓存，保留当前显示帧。 */
    fun trimMemory() = Unit
}

internal data class ReaderFrame(
    val locator: ReaderLocator,
    val status: String,
    val text: StaticLayout? = null,
    val bitmap: Bitmap? = null,
    val archive: com.shuiyu.octoberrain.feature.reader.archive.ArchivePage? = null,
    val web: com.shuiyu.octoberrain.feature.reader.web.WebRequest? = null,
    val table: ReaderTable? = null,
)

/** 仅当前20行×10列进入原生视图；行列位置为0起点，值不执行公式。 */
internal data class ReaderTable(
    val sheet: Int,
    val sheets: List<String>,
    val row: Int,
    val column: Int,
    val totalRows: Int,
    val totalColumns: Int,
    val cells: List<List<String>>,
    val merges: List<TableMerge> = emptyList(),
    val highlight: Pair<Int, Int>? = null,
)

internal data class TableMerge(
    val row: Int,
    val column: Int,
    val rows: Int,
    val columns: Int,
)

internal interface TableEngine : ReaderEngine {
    val sheetNames: List<String>

    suspend fun locate(
        sheet: Int,
        row: Int,
        column: Int,
    ): ReaderFrame

    fun acceptTable(table: ReaderTable): ReaderFrame
}
