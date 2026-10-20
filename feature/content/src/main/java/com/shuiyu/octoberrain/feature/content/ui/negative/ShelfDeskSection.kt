package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 墨台的书架分区。
 *
 * 分区按墨台版式列出最近阅读的书目行：封面缩略、书名、作者与阅读进度。点击书目行不跳转
 * 页面，由 [onOpen] 交给宿主在当前页面展开浮层。数据取微信读书书架，与负一屏其余分区
 * 共用同一份配置与刷新时机。
 *
 * @param context 页面上下文。
 * @param onOpen 书目行点击回调，参数为被点击的书目。
 */
internal class ShelfDeskSection(
    context: Context,
    private val onOpen: (WereadShelfItem) -> Unit,
) : LinearLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tokens = DesignRuntime.tokens

    private val header: LinearLayout = DeskSectionStyle.buildHeader(context, TITLE, ACTION_REFRESH) { reload() }

    private val body: LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels),
                0,
                DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels),
                tokens.gap,
            )
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

    /** 重新读取书架并渲染书目行。 */
    fun reload() {
        loadJob?.cancel()
        renderLoading()
        loadJob =
            scope.launch {
                // 书架条目来自原盘读盘与解析，数据量大时在主线程执行会阻塞输入事件，
                // 因此取数切到 IO 线程，渲染仍回到主线程。
                val items =
                    withContext(Dispatchers.IO) {
                        runCatching { AppServices.content.wereadShelf(LIMIT) }.getOrDefault(emptyList())
                    }
                loaded = true
                render(items)
            }
    }

    /** 渲染读取中的占位行。 */
    private fun renderLoading() {
        body.removeAllViews()
        body.addView(DeskSectionStyle.buildEmpty(context, LOADING))
    }

    /**
     * 渲染书目行。
     *
     * @param items 微信读书书架条目，按服务端返回顺序。
     */
    private fun render(items: List<WereadShelfItem>) {
        body.removeAllViews()
        if (items.isEmpty()) {
            body.addView(DeskSectionStyle.buildEmpty(context, EMPTY))
            return
        }
        items.forEachIndexed { index, item ->
            if (index > 0) body.addView(DeskSectionStyle.buildDivider(context))
            body.addView(buildRow(item))
        }
    }

    /**
     * 构造单行书目。
     *
     * @param item 书架条目。
     * @return 可点击的书目行。
     */
    private fun buildRow(item: WereadShelfItem): LinearLayout {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = DeskSectionStyle.rowHeightPx(context)
                isClickable = true
                isFocusable = true
                setOnClickListener { onOpen(item) }
            }
        val cover = ContentUi.coverView(context, item.coverPath, item.title, tokens.listIcon * 2)
        row.addView(
            cover,
            LayoutParams(
                tokens.listIcon * 2,
                (tokens.listIcon * 2 * 4) / 3,
            ).apply { marginEnd = tokens.gap },
        )
        val textColumn =
            LinearLayout(context).apply {
                orientation = VERTICAL
            }
        textColumn.addView(InkWidgets.body(context, item.title).apply { maxLines = 1 })
        textColumn.addView(InkWidgets.caption(context, item.author).apply { maxLines = 1 })
        textColumn.addView(
            InkWidgets.caption(context, progressText(item.progress)).apply {
                setTextColor(InkColors.INK_3)
            },
        )
        row.addView(textColumn, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /**
     * 把阅读进度转成展示文案。
     *
     * @param progress 进度，取值 0 到 1。
     * @return 进度文案。
     */
    private fun progressText(progress: Float): String {
        if (progress <= 0f) return PROGRESS_NONE
        return PROGRESS_PREFIX + (progress * 100).toInt() + "%"
    }

    private companion object {
        /** 分区标题。 */
        const val TITLE = "书架"

        /** 抬头右侧动作文案。 */
        const val ACTION_REFRESH = "刷新"

        /** 书目行数量上限。 */
        const val LIMIT = 5

        /** 读取中的占位文案。 */
        const val LOADING = "正在读取书架…"

        /** 空态文案。 */
        const val EMPTY = "书架暂无可展示的书目"

        /** 未开始阅读的进度文案。 */
        const val PROGRESS_NONE = "未开始"

        /** 阅读进度文案前缀。 */
        const val PROGRESS_PREFIX = "已读 "
    }
}
