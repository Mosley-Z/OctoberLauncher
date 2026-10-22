package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.common.NegativePageConfig
import com.shuiyu.octoberrain.core.common.RecentDocument
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import com.shuiyu.octoberrain.feature.content.ui.RecentBooksSection
import com.shuiyu.octoberrain.feature.content.ui.shelf.ShelfBookCard

/**
 * 书架分区中的一本条目。
 *
 * 负一屏书架按配置的书源只展示一侧数据，本地书籍与微信读书书目在此统一为同一种条目，
 * 卡片的书名、作者、封面与进度从条目读取，点击交由宿主区分处理。
 */
internal sealed interface ShelfEntry {
    /** 书名。 */
    val title: String

    /** 作者。 */
    val author: String

    /** 封面文件路径，可为空。 */
    val coverPath: String?

    /** 阅读进度，取值 0 到 1；本地书籍未接入进度时按 0 处理。 */
    val progress: Float

    /**
     * 微信读书书目。
     *
     * @param item 微信读书书架条目
     */
    data class Weread(
        val item: WereadShelfItem,
    ) : ShelfEntry {
        override val title: String get() = item.title
        override val author: String get() = item.author
        override val coverPath: String? get() = item.coverPath
        override val progress: Float get() = item.progress
    }

    /**
     * 本地书籍。
     *
     * @param item 书架条目
     */
    data class Local(
        val item: BookItem,
    ) : ShelfEntry {
        override val title: String get() = item.title
        override val author: String get() = item.author
        override val coverPath: String? get() = item.coverPath
        override val progress: Float get() = 0f
    }
}

/**
 * 墨台的书架分区。
 *
 * 分区以双列封面卡片展示书目：封面铺满卡片并按圆角裁切，书名与作者压在封面中下部的
 * 腰封内，卡片底边覆盖进度条与状态图标，右上角为百分比角标。卡片宽度由容器宽度均分
 * 得出，高度按封面比例从宽度推导，因此任意屏宽下同行卡片等高。
 *
 * 展示哪一侧数据由负一屏配置的书源决定：微信读书书源读取微信读书书架，本地书源读取
 * 配置目录中的书籍；本地书源在卡片网格之后附「最近阅读」。展示行数由 [NegativePageConfig.shelfRows]
 * 决定，每行两本，取满时不超过 [LIMIT] 本；行数与书源在墨台设置页调整后由宿主重建分区。
 *
 * 分区只负责取数与分列排版，单张卡片的绘制由 [ShelfBookCard] 承担，点击不跳转页面，
 * 由宿主决定展开浮层还是交给阅读器。
 *
 * @param context 页面上下文。
 * @param onOpen 书目点击回调。
 * @param onOpenRecent 最近阅读点击回调。
 */
internal class ShelfDeskSection(
    context: Context,
    private val onOpen: (ShelfEntry) -> Unit,
    private val onOpenRecent: (RecentDocument) -> Unit,
) : LinearLayout(context) {
    private val tokens = DesignRuntime.tokens
    private var active = false
    private var state = ShelfDisplayState()
    private var header: LinearLayout = buildShelfHeader(context, ::rebind, ::refresh)
    private val body =
        LinearLayout(context).apply {
            orientation = VERTICAL
            val inset = DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels)
            setPadding(inset, 0, inset, tokens.gap)
        }
    private val recentHost = LinearLayout(context).apply { orientation = VERTICAL }
    private val loader: ShelfLoader =
        ShelfLoader(context) { next ->
            state = next
            header
                .findViewById<android.widget.TextView>(
                    com.shuiyu.octoberrain.feature.content.R.id.shelf_refresh,
                ).apply {
                    isEnabled = !next.busy
                    text = if (next.busy) "刷新中" else "刷新"
                }
            val saved = next.snapshot
            val entries =
                if (page().effectiveShelfSource == ShelfSourceSupport.LOCAL) {
                    if (ContentPaths.hasStorageAccess()) {
                        saved?.local.orEmpty().map {
                            ShelfEntry.Local(
                                it,
                            )
                        }
                    } else {
                        emptyList()
                    }
                } else {
                    saved?.weread.orEmpty().map { ShelfEntry.Weread(it) }
                }
            render(entries, next.recent)
        }

    init {
        orientation = VERTICAL
        addView(header)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun onResume() {
        active = true
        rebind()
    }

    fun onPause() {
        active = false
        loader.pause()
    }

    fun release() {
        active = false
        loader.release()
    }

    private fun refresh() {
        loader.refresh()
    }

    /** 行数与书源变更只重读保存的展示，不触发数据获取。 */
    private fun rebind() {
        post {
            if (!active) return@post
            loader.pause()
            removeView(header)
            header = buildShelfHeader(context, ::rebind, ::refresh)
            addView(header, 0)
            loader.resume()
        }
    }

    /**
     * 渲染卡片网格与最近阅读。
     *
     * 只展示配置行数范围内的书目；本地书源追加最近阅读，无记录时不占位。
     *
     * @param entries 当前书源的书目，按各自来源的顺序排列。
     * @param recent 最近阅读记录，与书源无关，可为空。
     */
    private fun render(
        entries: List<ShelfEntry>,
        recent: List<RecentDocument>,
    ) {
        body.removeAllViews()
        state.message?.let {
            body.addView(
                com.shuiyu.octoberrain.core.design.InkWidgets
                    .caption(context, it),
            )
        }
        if (entries.isEmpty()) {
            body.addView(DeskSectionStyle.buildEmpty(context, emptyText()))
        } else {
            val visible = entries.take(page().shelfVisibleLimit)
            // 卡片宽度按分区内容宽显式二等分，高度按封面三比四换算后直接下发，
            // 避免权重测量与自定义测量互相覆盖导致行内溢出或高度偏差。
            val contentWidth =
                resources.displayMetrics.widthPixels -
                    2 * DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels)
            val cardWidth = (contentWidth - tokens.gap) / COLUMNS
            val cardHeight = cardWidth * CARD_RATIO_HEIGHT / CARD_RATIO_WIDTH
            visible.chunked(COLUMNS).forEachIndexed { rowIndex, chunk ->
                body.addView(buildRow(chunk, rowIndex, cardWidth, cardHeight))
            }
        }
        if (recent.isEmpty()) return
        body.addView(recentHost)
        RecentBooksSection.attach(recentHost, recent, onOpenRecent)
    }

    /**
     * 构造一行封面卡片。
     *
     * 条目数为奇数时补一个同宽度占位视图，阻止末行唯一卡片被拉伸至整行宽度。
     *
     * @param chunk 本行书目，长度不超过 [COLUMNS]。
     * @param rowIndex 行序，首行之外的行在顶部追加一个常规间距。
     * @param cardWidth 单张卡片宽度。
     * @param cardHeight 单张卡片高度。
     * @return 可直接加入分区内容区的卡片行。
     */
    private fun buildRow(
        chunk: List<ShelfEntry>,
        rowIndex: Int,
        cardWidth: Int,
        cardHeight: Int,
    ): LinearLayout {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            if (rowIndex > 0) topMargin = tokens.gap
                        }
            }
        chunk.forEachIndexed { column, entry -> row.addView(buildCard(entry, column, cardWidth, cardHeight)) }
        repeat(COLUMNS - chunk.size) {
            row.addView(
                View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight)
                },
            )
        }
        return row
    }

    /**
     * 构造单本书的封面卡片。
     *
     * @param entry 书架条目。
     * @param column 列序，零为左列。
     * @param cardWidth 卡片宽度。
     * @param cardHeight 卡片高度。
     * @return 可点击的封面卡片。
     */
    private fun buildCard(
        entry: ShelfEntry,
        column: Int,
        cardWidth: Int,
        cardHeight: Int,
    ): View {
        val card =
            ShelfBookCard.create(
                context = context,
                title = entry.title,
                author = entry.author,
                coverPath = entry.coverPath,
                progress = entry.progress,
                onClick = { onOpen(entry) },
            )
        card.layoutParams =
            LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                marginEnd = if (column == 0) tokens.gap else 0
            }
        return card
    }

    /**
     * 空态文案，按书源与存储权限区分原因。
     *
     * 负一屏内不做页面跳转，权限缺失只以文字说明，引导用户到系统设置授权。
     */
    private fun emptyText(): String =
        if (state.busy && state.snapshot == null) {
            LOADING
        } else if (state.snapshot?.updatedAt == null || state.snapshot?.updatedAt == 0L) {
            "点击刷新加载书架"
        } else if (ShelfSourceSupport.of(page()) == ShelfSourceSupport.LOCAL) {
            if (ContentPaths.hasStorageAccess()) EMPTY_LOCAL else EMPTY_PERMISSION
        } else {
            EMPTY_WEREAD
        }

    /** 当前负一屏配置。 */
    private fun page(): NegativePageConfig = AppServices.config.state.value.desktop.negativePage

    private companion object {
        /** 分区标题。 */
        const val TITLE = "书架"

        /** 抬头右侧动作文案。 */
        const val ACTION_REFRESH = "刷新"

        /**
         * 书目数量上限。
         *
         * 取行数上限与每行本数之积，行数取满五行时恰好十本；取书在 IO 线程一次完成，
         * 渲染再按配置行数截断。
         */
        const val LIMIT: Int = NegativePageConfig.SHELF_ITEM_LIMIT

        /** 每行卡片列数，与负一屏配置的每行本数一致。 */
        const val COLUMNS: Int = NegativePageConfig.SHELF_COLUMNS

        /** 封面宽高比：宽度三份、高度四份。 */
        const val CARD_RATIO_WIDTH = 3
        const val CARD_RATIO_HEIGHT = 4

        /** 读取中的占位文案。 */
        const val LOADING = "正在读取书架…"

        /** 微信读书书源的空态文案。 */
        const val EMPTY_WEREAD = "书架暂无可展示的书目"

        /** 本地书源的空态文案。 */
        const val EMPTY_LOCAL = "本地目录暂无书籍"

        /** 缺少存储权限时的提示文案。 */
        const val EMPTY_PERMISSION = "未授予存储权限，无法读取本地书籍"
    }
}
