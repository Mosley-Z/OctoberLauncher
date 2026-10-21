package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.NegativePageConfig
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.feature.content.ui.shelf.ShelfBookCard
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
 * 分区以双列封面卡片展示最近阅读的书目：封面铺满卡片并按圆角裁切，书名与作者压在
 * 封面中下部的腰封内，卡片底边覆盖进度条与状态图标，右上角为百分比角标。
 * 卡片宽度由容器宽度均分得出，高度按封面比例从宽度推导，因此任意屏宽下同行卡片等高。
 *
 * 分区只负责取数与分列排版，单张卡片的绘制由 [ShelfBookCard] 承担。点击卡片不跳转页面，
 * 由 [onOpen] 交给宿主在当前页面展开浮层。数据取微信读书书架，与负一屏其余分区共用同一份
 * 配置与刷新时机。
 *
 * 展示行数由负一屏配置的 [NegativePageConfig.shelfRows] 决定，每行两本，取满时不超过
 * [LIMIT] 本；行数在墨台设置页调整后由宿主重建分区，分区按新行数重新排版。
 *
 * @param context 页面上下文。
 * @param onOpen 卡片点击回调，参数为被点击的书目。
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
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        addView(
            body,
            LinearLayout.LayoutParams(
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

    /** 重新读取书架并渲染卡片网格。 */
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
     * 渲染卡片网格。
     *
     * 只展示配置行数范围内的书目，行数由负一屏配置给出并夹取到合法区间，因此调整行数后
     * 内容区高度随行数变化，滚动容器结构保持不变。
     *
     * @param items 微信读书书架条目，按服务端返回顺序，每两本组成一行。
     */
    private fun render(items: List<WereadShelfItem>) {
        body.removeAllViews()
        val visible = items.take(AppServices.config.state.value.desktop.negativePage.shelfVisibleLimit)
        if (visible.isEmpty()) {
            body.addView(DeskSectionStyle.buildEmpty(context, EMPTY))
            return
        }
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

    /**
     * 构造一行封面卡片。
     *
     * 行内卡片按等权重均分容器可用宽度，列间距取设计令牌的常规间距，因此单张卡片宽度恒为
     * 容器宽度减去左右留白与列间距后的二分之一。条目数为奇数时补一个同权重占位视图，
     * 占位视图没有内容，仅用于阻止末行唯一卡片被拉伸至整行宽度。
     *
     * @param chunk 本行书目，长度不超过 [COLUMNS]。
     * @param rowIndex 行序，首行之外的行在顶部追加一个常规间距。
     * @return 可直接加入分区内容区的卡片行。
     */
    private fun buildRow(
        chunk: List<WereadShelfItem>,
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
        chunk.forEachIndexed { column, item -> row.addView(buildCard(item, column, cardWidth, cardHeight)) }
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
     * 卡片宽度由行容器的权重分配决定，高度在卡片自身的测量阶段按封面比例从宽度推导；
     * 首列在右侧保留一个常规间距作为列间距。
     *
     * @param item 书架条目。
     * @param column 列序，零为左列。
     * @return 可点击的封面卡片。
     */
    private fun buildCard(
        item: WereadShelfItem,
        column: Int,
        cardWidth: Int,
        cardHeight: Int,
    ): View {
        val card =
            ShelfBookCard.create(
                context = context,
                title = item.title,
                author = item.author,
                coverPath = item.coverPath,
                progress = item.progress,
                onClick = { onOpen(item) },
            )
        card.layoutParams =
            LinearLayout.LayoutParams(cardWidth, cardHeight).apply {
                marginEnd = if (column == 0) tokens.gap else 0
            }
        return card
    }

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

        /** 空态文案。 */
        const val EMPTY = "书架暂无可展示的书目"
    }
}
