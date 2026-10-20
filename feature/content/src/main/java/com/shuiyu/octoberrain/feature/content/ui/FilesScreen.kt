package com.shuiyu.octoberrain.feature.content.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import com.shuiyu.octoberrain.feature.content.data.FileLibrary
import com.shuiyu.octoberrain.feature.content.data.LibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 文件管理页面。
 *
 * 总览模式展示书籍、图片、字体与全部文件四个入口，以及存储占用和最近修改；
 * 目录模式按每页九项分页浏览，支持进入目录、上级返回、打开文件、长按重命名、
 * 复制、移动与删除，并可新建目录。
 */
class FilesScreen(
    private val startPath: String? = null,
) : ContentScreen("文件管理") {
    private companion object {
        private const val PAGE_SIZE = FileLibrary.PAGE_SIZE
        private const val ACTION_OPEN = 0
        private const val ACTION_RENAME = 1
        private const val ACTION_COPY = 2
        private const val ACTION_MOVE = 3
        private const val ACTION_DELETE = 4
    }

    private var currentDirectory: File? = null
    private var pageIndex = 0
    private var startApplied = false

    override fun onResume() {
        if (!startApplied) {
            startApplied = true
            startPath?.let { path ->
                val file = File(path)
                currentDirectory =
                    when {
                        file.isDirectory -> file
                        file.isFile -> file.parentFile
                        else -> null
                    }
            }
        }
        load { container -> render(container) }
    }

    /** 依据当前状态渲染总览或目录。 */
    private suspend fun render(container: LinearLayout) {
        val library = source()?.files ?: return showMissingModule(container)
        val hasStorage = ContentPaths.hasStorageAccess()
        val directory = currentDirectory
        if (directory != null && directory.isDirectory) {
            renderBrowser(container, library, directory, hasStorage)
        } else {
            currentDirectory = null
            renderOverview(container, library, hasStorage)
        }
    }

    /** 模块未安装时的提示。 */
    private fun showMissingModule(container: LinearLayout) {
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = "文件管理尚未就绪",
                message = "桌面服务尚未完成初始化，请稍后重新进入。",
            ),
        )
    }

    /** 总览模式：入口卡、存储占用与最近修改。 */
    private suspend fun renderOverview(
        container: LinearLayout,
        library: FileLibrary,
        hasStorage: Boolean,
    ) {
        if (!hasStorage) {
            container.addView(
                ContentUi.noticeCard(
                    context = context,
                    message = "未获得文件访问权限，文件数量显示为零。",
                    actionLabel = "授权",
                    onAction = { requestStoragePermission() },
                ),
            )
        }
        val overview = io { library.overview() }
        container.addView(
            rootCard(
                title = "书籍",
                detail = "${overview.booksCount} 个文件 · ${library.booksDirectory().absolutePath}",
                onClick = { enter(library.booksDirectory()) },
            ),
        )
        container.addView(
            rootCard(
                title = "图片",
                detail = "${overview.picturesCount} 个文件 · ${library.picturesDirectory().absolutePath}",
                onClick = { enter(library.picturesDirectory()) },
            ),
        )
        container.addView(
            rootCard(
                title = "字体",
                detail = "${overview.fontsCount} 个文件 · ${library.fontsDirectory().absolutePath}",
                onClick = { enter(library.fontsDirectory()) },
            ),
        )
        container.addView(
            rootCard(
                title = "全部文件",
                detail = "内容目录合计 ${ContentUi.formatSize(overview.totalBytes)}",
                onClick = { enter(library.externalDirectory()) },
            ),
        )
        container.addView(storageCard(overview))
        val recent = io { library.recentFiles() }
        if (recent.isNotEmpty()) {
            ContentUi.sectionTitle(container, "最近修改")
            val card = ContentUi.card(context)
            recent.forEachIndexed { index, entry ->
                if (index > 0) card.addView(InkWidgets.divider(context))
                card.addView(recentRow(library, entry))
            }
            container.addView(card)
        }
    }

    /** 四个入口卡片的单个条目。 */
    private fun rootCard(
        title: String,
        detail: String,
        onClick: () -> Unit,
    ): LinearLayout {
        val card = ContentUi.card(context)
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        row.addView(
            InkWidgets.body(context, title).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            },
        )
        row.addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON, tokens.settingsIcon))
        card.addView(row)
        card.addView(
            InkWidgets.caption(context, detail).apply {
                setPadding(0, tokens.gap / 2, 0, 0)
            },
        )
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { onClick() }
        return card
    }

    /** 存储占用卡片。 */
    private fun storageCard(overview: com.shuiyu.octoberrain.feature.content.data.LibraryOverview): LinearLayout {
        val card = ContentUi.card(context)
        card.addView(
            ContentUi.keyValue(
                context,
                "存储占用",
                "${ContentUi.formatCapacity(overview.usedBytes)} / ${ContentUi.formatCapacity(overview.capacityBytes)}",
            ),
        )
        if (overview.capacityBytes > 0L) {
            val ratio = (overview.usedBytes.toFloat() / overview.capacityBytes.toFloat()).coerceIn(0f, 1f)
            val width = tokens.dp(220f)
            card.addView(
                ContentUi.progressBar(context, ratio, width).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(width, tokens.progressHeight).apply {
                            topMargin = tokens.gap
                        }
                },
            )
        }
        card.addView(
            InkWidgets
                .caption(
                    context,
                    "书籍、图片、字体合计 ${ContentUi.formatSize(overview.totalBytes)}",
                ).apply {
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
        )
        return card
    }

    /** 最近修改文件的行。 */
    private fun recentRow(
        library: FileLibrary,
        entry: LibraryEntry,
    ): View {
        val detail = "${ContentUi.formatSize(entry.sizeBytes)} · ${ContentUi.formatDateTime(entry.modifiedAt)}"
        val row =
            ContentUi.entryRow(
                context = context,
                icon = iconFor(library, entry),
                title = entry.name,
                detail = detail,
                onClick = { openFile(entry.file) },
            )
        row.setOnLongClickListener {
            promptEntryActions(library, entry)
            true
        }
        return row
    }

    /** 目录模式：路径栏、操作按钮与分页列表。 */
    private suspend fun renderBrowser(
        container: LinearLayout,
        library: FileLibrary,
        directory: File,
        hasStorage: Boolean,
    ) {
        if (!hasStorage) {
            container.addView(
                ContentUi.noticeCard(
                    context = context,
                    message = "未获得文件访问权限，目录内容可能为空。",
                    actionLabel = "授权",
                    onAction = { requestStoragePermission() },
                ),
            )
        }
        val bar = ContentUi.card(context)
        bar.addView(InkWidgets.body(context, library.displayName(directory)))
        bar.addView(
            InkWidgets.caption(context, directory.absolutePath).apply {
                setPadding(0, tokens.gap / 2, 0, 0)
            },
        )
        val actions =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = tokens.gap
                    }
            }
        actions.addView(
            InkWidgets.button(context, "上级目录", onClick = { navigateUp() }).apply {
                layoutParams = LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f)
            },
        )
        actions.addView(
            InkWidgets.button(context, "新建目录", onClick = { promptNewFolder(library) }).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f).apply {
                        marginStart = tokens.gap
                    }
            },
        )
        actions.addView(
            InkWidgets.button(context, "刷新", onClick = { reload() }).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f).apply {
                        marginStart = tokens.gap
                    }
            },
        )
        bar.addView(actions)
        container.addView(bar)

        val entries = io { library.list(directory) }
        val totalPages = maxOf(1, ceil(entries.size.toDouble() / PAGE_SIZE.toDouble()).toInt())
        if (pageIndex >= totalPages) pageIndex = totalPages - 1
        if (pageIndex < 0) pageIndex = 0
        ContentUi.sectionTitle(container, "共 ${entries.size} 项")
        val card = ContentUi.card(context)
        if (entries.isEmpty()) {
            card.addView(InkWidgets.caption(context, "当前目录为空，可新建目录或放入文件后刷新。"))
        } else {
            val pageEntries = entries.drop(pageIndex * PAGE_SIZE).take(PAGE_SIZE)
            pageEntries.forEachIndexed { index, entry ->
                if (index > 0) card.addView(InkWidgets.divider(context))
                card.addView(fileRow(library, entry))
            }
            if (totalPages > 1) {
                val paging =
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams =
                            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                                topMargin = tokens.gap
                            }
                    }
                paging.addView(
                    InkWidgets.button(context, "上一页", onClick = { turnPage(-1) }).apply {
                        layoutParams = LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f)
                        isEnabled = pageIndex > 0
                        alpha = if (isEnabled) 1f else 0.4f
                    },
                )
                paging.addView(
                    InkWidgets
                        .textView(
                            context,
                            "${pageIndex + 1} / $totalPages",
                            tokens.textCaption,
                        ).apply {
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f)
                        },
                )
                paging.addView(
                    InkWidgets.button(context, "下一页", onClick = { turnPage(1) }).apply {
                        layoutParams = LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f)
                        isEnabled = pageIndex < totalPages - 1
                    },
                )
                card.addView(paging)
            }
        }
        container.addView(card)
    }

    /** 目录条目行，单击打开或进入，长按弹出操作菜单。 */
    private fun fileRow(
        library: FileLibrary,
        entry: LibraryEntry,
    ): View {
        val detail =
            if (entry.isDirectory) {
                "目录"
            } else {
                "${ContentUi.formatSize(entry.sizeBytes)} · ${ContentUi.formatDateTime(entry.modifiedAt)}"
            }
        val row =
            ContentUi.entryRow(
                context = context,
                icon = iconFor(library, entry),
                title = entry.name,
                detail = detail,
                onClick = {
                    if (entry.isDirectory) enter(entry.file) else openFile(entry.file)
                },
            )
        row.setOnLongClickListener {
            promptEntryActions(library, entry)
            true
        }
        return row
    }

    /** 依据类型选择图标。 */
    private fun iconFor(
        library: FileLibrary,
        entry: LibraryEntry,
    ): InkGlyphs.Kind =
        when {
            entry.isDirectory -> InkGlyphs.Kind.FOLDER
            library.isImage(entry.file) -> InkGlyphs.Kind.LIST
            library.isTextFile(entry.file) -> InkGlyphs.Kind.LIST
            else -> InkGlyphs.Kind.BOOK
        }

    /** 条目操作菜单。 */
    private fun promptEntryActions(
        library: FileLibrary,
        entry: LibraryEntry,
    ) {
        val labels = listOf("打开", "重命名", "复制到…", "移动到…", "删除")
        InkDialogs.choose(context, entry.name, labels) { index ->
            when (index) {
                ACTION_OPEN -> if (entry.isDirectory) enter(entry.file) else openFile(entry.file)
                ACTION_RENAME -> promptRename(library, entry)
                ACTION_COPY -> promptTransfer(library, entry, move = false)
                ACTION_MOVE -> promptTransfer(library, entry, move = true)
                ACTION_DELETE -> promptDelete(library, entry)
            }
        }
    }

    /** 重命名输入框。 */
    private fun promptRename(
        library: FileLibrary,
        entry: LibraryEntry,
    ) {
        InkDialogs.input(
            context = context,
            title = "重命名",
            hint = entry.name,
            initial = entry.name,
        ) { value ->
            if (value.isBlank() || value == entry.name) return@input
            runTask {
                val result = io { library.rename(entry.file, value) }
                toast(result.message)
                if (result.success) reload()
            }
        }
    }

    /** 复制或移动的目标选择。 */
    private fun promptTransfer(
        library: FileLibrary,
        entry: LibraryEntry,
        move: Boolean,
    ) {
        val roots = library.roots().filterNot { it.absolutePath == entry.file.parentFile?.absolutePath }
        if (roots.isEmpty()) {
            toast("当前目录已是唯一可用的目标目录")
            return
        }
        val labels = roots.map { library.displayName(it) }
        InkDialogs.choose(context, if (move) "移动到" else "复制到", labels) { index ->
            val target = roots.getOrNull(index) ?: return@choose
            runTask {
                val result = io { library.copyOrMove(entry.file, target, move) }
                toast(result.message)
                if (result.success) reload()
            }
        }
    }

    /** 删除确认。 */
    private fun promptDelete(
        library: FileLibrary,
        entry: LibraryEntry,
    ) {
        InkDialogs.confirm(
            context = context,
            title = "删除",
            message = "确定删除 ${entry.name}？此操作不可恢复。",
            onConfirm = {
                runTask {
                    val result = io { library.delete(entry.file) }
                    toast(result.message)
                    if (result.success) reload()
                }
            },
        )
    }

    /** 新建目录输入框。 */
    private fun promptNewFolder(library: FileLibrary) {
        val directory = currentDirectory ?: return
        InkDialogs.input(context, "新建目录", hint = "目录名称") { value ->
            if (value.isBlank()) return@input
            runTask {
                val result = io { library.createDirectory(directory, value) }
                toast(result.message)
                if (result.success) reload()
            }
        }
    }

    /** 进入目录并回到第一页。 */
    private fun enter(directory: File) {
        currentDirectory = directory
        pageIndex = 0
        reload()
    }

    /** 返回上级目录，父目录离开外部存储根范围时回到总览。 */
    private fun navigateUp() {
        val directory = currentDirectory ?: return
        val root = ContentPaths.externalRoot()
        val rootPath = root.absolutePath
        val currentPath = directory.absolutePath
        val parent = directory.parentFile
        currentDirectory =
            if (currentPath == rootPath ||
                parent == null ||
                !parent.absolutePath.startsWith(rootPath)
            ) {
                // 已到存储根或父目录越出存储范围，退回总览，避免浏览到系统目录
                null
            } else {
                parent
            }
        pageIndex = 0
        reload()
    }

    /** 翻页。 */
    private fun turnPage(delta: Int) {
        val next = pageIndex + delta
        if (next < 0) return
        pageIndex = next
        reload()
    }

    /** 重新渲染当前页面。 */
    private fun reload() {
        onResume()
    }

    /** 在 IO 线程执行阻塞的文件操作。 */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
