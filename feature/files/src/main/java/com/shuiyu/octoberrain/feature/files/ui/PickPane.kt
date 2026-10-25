package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FileListing
import com.shuiyu.octoberrain.feature.files.data.FilePrefs
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.Locale

/**
 * 选图页。
 *
 * 复用 [FilePager] 的分页、顶栏、底栏与手势，按当前视图形态列出目录与符合过滤条件的文件：
 * 列表模式每行左置缩略图，网格模式按方向和内容高度排布并绘制居中裁剪的封面；两种模式都显示目录项，
 * 点击目录进入、点击文件回传。列表行数按内容区高度换算，网格容量来自当前布局，
 * 单目录不超过 400 项。顶栏右侧动作按钮在两种视图之间切换，切换只改本次会话的显示方式，
 * 不写回界面偏好；目录返回仍由顶栏返回按钮完成。
 *
 * 参数 context 为宿主上下文；tokens 为尺寸令牌；prefs 提供最后路径与初始视图；
 * accepts 判断文件名是否可被请求方接收；onPick 回传选中文件；onCancel 处理取消；
 * scope 为缩略图预取使用的页面级协程作用域。
 */
internal class PickPane(
    context: Context,
    tokens: FileTokens,
    private val prefs: FilePrefs,
    root: File,
    private val accepts: (String) -> Boolean,
    private val onPick: (FileItem) -> Unit,
    private val onCancel: () -> Unit,
    scope: CoroutineScope,
) : FilePager(context, tokens) {
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.bodyText
            color = InkColors.INK
        }
    private val gridPane = PickGridPane(tokens)
    private val listPane = PickListPane(tokens)
    private val thumbs = PickThumbs(context, scope) { invalidate() }
    private var grid: Boolean = prefs.grid
    private var currentDir: File = root
    private var items: List<FileItem> = emptyList()
    private var message = ""

    /** 顶栏标题取目录名称。 */
    override val pageTitle: String get() = currentDir.name.ifEmpty { "选择图片" }

    /** 顶栏副标题取当前路径。 */
    override val pageSubtitle: String get() = currentDir.absolutePath

    /** 顶栏右侧动作按钮在两种视图之间切换。 */
    override val topActionLabel: String get() = if (grid) BrowseStrings.LIST else BrowseStrings.GRID

    /** List rows and grid capacity both follow the measured content area. */
    override val pageSize: Int
        get() = if (grid) geometry?.pageSize ?: 1 else geometry?.listRows ?: DEFAULT_ROWS

    /** 底栏动作按钮用于取消选择。 */
    override fun actionLabel(): String = "取消"

    init {
        load(currentDir)
        setPageListener { page ->
            thumbs.request(items, page)
            invalidate()
        }
    }

    /** 进入上级目录；已在根目录时取消选择。 */
    fun goUp() {
        val parent = currentDir.parentFile
        if (parent == null) {
            onCancel()
            return
        }
        if (parent == currentDir || parent.absolutePath == ROOT_GUARD) {
            onCancel()
            return
        }
        load(parent)
    }

    override fun onBackAction() {
        goUp()
    }

    /** 切换列表与网格，按新的每页条目数重排当前目录并重算缩略图目标尺寸。 */
    override fun onTopAction() {
        grid = !grid
        val metrics = geometry
        if (metrics != null) {
            val edge = if (grid) minOf(metrics.cellW, metrics.gridCellDrawH) else tokens.rowThumb
            thumbs.update(edge, pageSize, epub = grid)
        }
        pagerState.pageSize = pageSize
        thumbs.request(items, pagerState.page)
        invalidate()
    }

    override fun onAction() {
        onCancel()
    }

    override fun onGeometryChanged(rebuilt: FileMetrics) {
        val edge = if (grid) minOf(rebuilt.cellW, rebuilt.gridCellDrawH) else tokens.rowThumb
        thumbs.update(edge, pageSize, epub = grid)
        thumbs.request(items, pagerState.page)
    }

    override fun onContentGesture(
        x: Int,
        y: Int,
        longPress: Boolean,
    ) {
        val metrics = geometry ?: return
        if (longPress) return
        val position =
            if (grid) {
                gridPane.indexAt(x, y, metrics, pagerState.firstIndex)
            } else {
                listPane.indexAt(x, y, metrics, pagerState.firstIndex)
            }
        val item = items.getOrNull(position)
        when {
            item == null -> Unit
            item.dir -> load(File(item.path))
            else -> onPick(item)
        }
    }

    override fun onDrawContent(
        canvas: Canvas,
        area: Rect,
        start: Int,
        end: Int,
    ) {
        if (items.isEmpty()) {
            val text = message.ifEmpty { "没有可选择的图片" }
            canvas.drawText(text, (area.left + tokens.padH).toFloat(), area.top + tokens.rowH.toFloat(), bodyPaint)
            return
        }
        val metrics = geometry ?: return
        if (grid) {
            gridPane.draw(canvas, metrics, items, start, end, thumbs::bitmapOf)
        } else {
            listPane.draw(canvas, area, items, start, end, thumbs::bitmapOf)
        }
    }

    /** 读取目录并刷新列表；只保留目录与符合过滤条件的文件。 */
    private fun load(directory: File) {
        val target = if (directory.isDirectory && directory.canRead()) directory else currentDir
        currentDir = target
        prefs.lastPath = target.absolutePath
        val children = target.listFiles().orEmpty()
        message = if (children.isEmpty()) "目录为空" else ""
        val directories = children.filter { it.isDirectory && !it.name.startsWith(".") }
        val files = children.filter { it.isFile && !it.name.startsWith(".") && accepts(it.name) }
        val orderedDirs = directories.sortedBy { it.name.lowercase(Locale.US) }
        val orderedFiles = files.sortedBy { it.name.lowercase(Locale.US) }
        val candidates = orderedDirs + orderedFiles
        val ordered = ArrayList<FileItem>(minOf(candidates.size, MAX_ROWS))
        for (file in candidates) {
            if (ordered.size >= MAX_ROWS) break
            ordered += toItem(file)
        }
        items = ordered
        setPage(0)
        submitItemCount(ordered.size)
        invalidate()
    }

    /** 把文件系统条目转成列表条目，目录不记录大小与 MIME。 */
    private fun toItem(file: File): FileItem =
        if (file.isDirectory) {
            FileItem.dir(file.name, file.absolutePath, file.lastModified())
        } else {
            FileItem.file(
                file.name,
                file.absolutePath,
                file.length(),
                file.lastModified(),
                FileListing.mimeOf(file.name),
            )
        }

    private companion object {
        private const val DEFAULT_ROWS = 8
        private const val MAX_ROWS = 400
        private const val ROOT_GUARD = "/"
    }
}
