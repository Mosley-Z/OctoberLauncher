package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FileListing
import com.shuiyu.octoberrain.feature.files.data.FilePrefs
import java.io.File
import java.util.Locale

/**
 * 选图页。
 *
 * 复用 [FilePager] 的分页、顶栏、底栏与手势，按行列出目录与符合过滤条件的图片，
 * 点击目录进入、点击图片回传。行数按内容区高度换算，单目录不超过 400 行。
 *
 * 参数 context 为宿主上下文；tokens 为尺寸令牌；prefs 提供最后路径；
 * accepts 判断文件名是否可被请求方接收；onPick 回传选中文件；onCancel 处理取消。
 */
internal class PickPane(
    context: Context,
    tokens: FileTokens,
    private val prefs: FilePrefs,
    root: File,
    private val accepts: (String) -> Boolean,
    private val onPick: (FileItem) -> Unit,
    private val onCancel: () -> Unit,
) : FilePager(context, tokens) {
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.bodyText
            color = InkColors.INK
        }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tokens.smallText
            color = InkColors.INK_3
        }
    private var currentDir: File = root
    private var items: List<FileItem> = emptyList()
    private var message = ""

    /** 顶栏标题取目录名称。 */
    override val pageTitle: String get() = currentDir.name.ifEmpty { "选择图片" }

    /** 顶栏副标题取当前路径。 */
    override val pageSubtitle: String get() = currentDir.absolutePath

    /** 顶栏右侧动作按钮返回上级目录。 */
    override val topActionLabel: String get() = "上级"

    /** 每页行数按内容区高度换算。 */
    override val pageSize: Int get() = geometry?.listRows ?: DEFAULT_ROWS

    /** 底栏动作按钮用于取消选择。 */
    override fun actionLabel(): String = "取消"

    init {
        load(currentDir)
        setPageListener { invalidate() }
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

    override fun onTopAction() {
        goUp()
    }

    override fun onAction() {
        onCancel()
    }

    override fun onContentGesture(
        x: Int,
        y: Int,
        longPress: Boolean,
    ) {
        val area = geometry
        val row = if (area == null) -1 else (y - area.contentTop) / tokens.rowH
        val item = if (longPress || row < 0) null else items.getOrNull(pagerState.firstIndex + row)
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
        for (index in start until end) {
            val item = items[index]
            val top = area.top + (index - start) * tokens.rowH
            val baseline = top + tokens.rowH / 2f - (bodyPaint.descent() + bodyPaint.ascent()) / 2f
            val text = if (item.dir) "${item.name}/" else item.name
            canvas.drawText(text, (area.left + tokens.padH).toFloat(), baseline, bodyPaint)
            if (!item.dir) {
                val meta = formatSize(item.size)
                val width = smallPaint.measureText(meta)
                canvas.drawText(meta, area.right - tokens.padH - width, baseline, smallPaint)
            }
            val lineY = (top + tokens.rowH).toFloat()
            if (index < items.lastIndex) {
                canvas.drawLine(area.left.toFloat(), lineY, area.right.toFloat(), lineY, smallPaint)
            }
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

    /** 字节数转为便于阅读的大小文案。 */
    private fun formatSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble() / 1024.0
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    private companion object {
        private const val DEFAULT_ROWS = 8
        private const val MAX_ROWS = 400
        private const val ROOT_GUARD = "/"
    }
}
