package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.RecentDocument
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最近阅读列表。
 *
 * 书架与负一屏共用：标题加若干行“名称 + 最近更新时间”，点击交给宿主打开文档。
 * 数据来自核心层 [com.shuiyu.octoberrain.core.common.RecentGateway]，与书源开关无关，
 * 因此本地目录与微信读书都关闭时仍可展示。
 */
internal object RecentBooksSection {
    /** 最多展示的文档数。 */
    const val LIMIT = 5

    /** 行内时间格式。 */
    private const val TIME_PATTERN = "MM-dd HH:mm"

    private const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

    /**
     * 后台读取最近阅读记录。
     *
     * 供宿主在 IO 线程内调用，返回空列表表示最近阅读入口尚未注册。
     *
     * @param limit 读取条数
     */
    suspend fun load(limit: Int = LIMIT): List<RecentDocument> =
        withContext(Dispatchers.IO) {
            runCatching { AppServices.recent?.recent(limit).orEmpty() }.getOrDefault(emptyList())
        }

    /**
     * 把最近阅读列表填充到宿主容器。
     *
     * 没有记录时不添加任何内容；容器由宿主创建并加入页面。
     *
     * @param host 目标纵向容器
     * @param items 最近阅读记录
     * @param onOpen 点击回调，参数为被点击的记录
     */
    fun attach(
        host: LinearLayout,
        items: List<RecentDocument>,
        onOpen: (RecentDocument) -> Unit,
    ) {
        host.removeAllViews()
        if (items.isEmpty()) return
        val tokens = DesignRuntime.tokens
        host.addView(
            InkWidgets.caption(host.context, "最近阅读").apply {
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = tokens.gapLarge
                    }
            },
        )
        items.take(LIMIT).forEach { item -> host.addView(row(host.context, item, onOpen)) }
    }

    /**
     * 构造单条记录行，右侧显示最近更新时间。
     *
     * @param context 页面上下文
     * @param item 最近阅读记录
     * @param onOpen 点击回调
     */
    private fun row(
        context: Context,
        item: RecentDocument,
        onOpen: (RecentDocument) -> Unit,
    ): View {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = tokens.gap
                    }
                isClickable = true
                isFocusable = true
                setOnClickListener { onOpen(item) }
            }
        row.addView(
            InkWidgets.body(context, item.name).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            },
        )
        row.addView(InkWidgets.caption(context, time(item.updated)))
        return row
    }

    /**
     * 把毫秒时间格式化为月日与时分。
     *
     * @param updated 最近更新时间，单位为毫秒
     */
    private fun time(updated: Long): String =
        runCatching {
            SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(updated))
        }.getOrDefault("")
}
