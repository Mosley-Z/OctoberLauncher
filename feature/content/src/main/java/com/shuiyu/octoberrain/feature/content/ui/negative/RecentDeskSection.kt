package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ContentRegistry
import com.shuiyu.octoberrain.feature.content.ui.ContentUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 墨台的最近图片分区。
 *
 * 分区按墨台版式列出最近保存或拍摄的图片：缩略图、文件名与修改时间。点击图片行不跳转
 * 页面，由 [onOpen] 交给宿主在当前页面展开大图浮层。数据取文件管理的最近文件列表并按
 * 图片扩展名筛选。
 *
 * @param context 页面上下文。
 * @param onOpen 图片行点击回调，参数为被点击的文件。
 */
internal class RecentDeskSection(
    context: Context,
    private val onOpen: (File) -> Unit,
) : LinearLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tokens = DesignRuntime.tokens

    private val header = DeskSectionStyle.buildHeader(context, TITLE, ACTION_REFRESH) { reload() }

    private val body =
        LinearLayout(context).apply {
            orientation = VERTICAL
            val padding = DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels)
            setPadding(padding, 0, padding, tokens.gap)
        }

    private var loadJob: Job? = null

    private var loaded = false

    init {
        orientation = VERTICAL
        addView(
            header,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            body,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /** 页面回到前台时读取一次数据，已读取过则跳过重复请求。 */
    fun onResume() {
        if (!loaded) reload()
    }

    /** 页面离开前台时取消读取任务。 */
    fun onPause() {
        loadJob?.cancel()
        loadJob = null
    }

    /** 释放协程作用域。 */
    fun release() {
        scope.cancel()
    }

    /** 重新读取最近图片并渲染图片行。 */
    fun reload() {
        loadJob?.cancel()
        body.removeAllViews()
        body.addView(DeskSectionStyle.buildEmpty(context, LOADING))
        val library = ContentRegistry.source()?.files
        if (library == null) {
            body.removeAllViews()
            body.addView(DeskSectionStyle.buildEmpty(context, EMPTY))
            return
        }
        loadJob =
            scope.launch {
                val files =
                    withContext(Dispatchers.IO) {
                        library
                            .recentFiles()
                            .filter { !it.isDirectory && library.isImage(it.file) }
                            .map { it.file }
                            .take(LIMIT)
                    }
                loaded = true
                render(files)
            }
    }

    /**
     * 渲染图片行。
     *
     * @param files 最近图片文件列表。
     */
    private fun render(files: List<File>) {
        body.removeAllViews()
        if (files.isEmpty()) {
            body.addView(DeskSectionStyle.buildEmpty(context, EMPTY))
            return
        }
        files.forEachIndexed { index, file ->
            if (index > 0) body.addView(DeskSectionStyle.buildDivider(context))
            body.addView(buildRow(file))
        }
    }

    /**
     * 构造单行图片。
     *
     * @param file 图片文件。
     * @return 可点击的图片行。
     */
    private fun buildRow(file: File): LinearLayout {
        val side = tokens.listIcon * 2
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = DeskSectionStyle.rowHeightPx(context)
                isClickable = true
                isFocusable = true
                setOnClickListener { onOpen(file) }
            }
        val thumbnail =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(InkColors.INK_4)
            }
        row.addView(
            thumbnail,
            LayoutParams(side, side).apply { marginEnd = tokens.gap },
        )
        loadThumbnail(thumbnail, file, side)
        val textColumn =
            LinearLayout(context).apply {
                orientation = VERTICAL
            }
        textColumn.addView(InkWidgets.body(context, file.name).apply { maxLines = 1 })
        textColumn.addView(
            InkWidgets.caption(context, ContentUi.formatDateTime(file.lastModified())).apply {
                setTextColor(InkColors.INK_3)
            },
        )
        row.addView(textColumn, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /**
     * 后台解码缩略图并写入视图。
     *
     * @param target 目标图片视图。
     * @param file 源文件。
     * @param sidePx 目标边长，单位为像素。
     */
    private fun loadThumbnail(
        target: ImageView,
        file: File,
        sidePx: Int,
    ) {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeThumbnail(file, sidePx) }
            if (bitmap != null) target.setImageBitmap(bitmap)
        }
    }

    /**
     * 按目标边长采样解码位图，避免整张大图占用内存。
     *
     * @param file 源文件。
     * @param sidePx 目标边长，单位为像素。
     * @return 解码结果，失败返回空。
     */
    private fun decodeThumbnail(
        file: File,
        sidePx: Int,
    ): Bitmap? =
        runCatching {
            val bounds =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > sidePx * 2 && bounds.outHeight / sample > sidePx * 2) {
                sample *= 2
            }
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                }
            BitmapFactory.decodeFile(file.absolutePath, options)
        }.getOrNull()

    private companion object {
        /** 分区标题。 */
        const val TITLE = "最近图片"

        /** 抬头右侧动作文案。 */
        const val ACTION_REFRESH = "刷新"

        /** 图片行数量上限。 */
        const val LIMIT = 4

        /** 读取中的占位文案。 */
        const val LOADING = "正在读取最近图片…"

        /** 空态文案。 */
        const val EMPTY = "暂无最近图片"
    }
}
