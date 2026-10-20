package com.shuiyu.octoberrain.feature.files.ui.negative

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.NegativeModule
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.core.render.loadOrDecode
import com.shuiyu.octoberrain.feature.files.data.MediaBuckets
import com.shuiyu.octoberrain.feature.files.data.MediaEntry
import com.shuiyu.octoberrain.feature.files.data.MediaIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 负一屏「最近图片」卡片的纯逻辑部分。
 *
 * 条目排序、容量截取、点击目标构造与网格换算集中在此处，卡片视图只负责绘制，
 * 因此这些规则可以在本地 JVM 用例中直接断言，不依赖 Android 运行环境。
 */
internal object RecentImages {
    /** 卡片最多展示的图片数量，两行三列。 */
    const val LIMIT: Int = 6

    /** 每行缩略图数量。 */
    const val COLUMNS: Int = 3

    /** 卡片标题。 */
    const val TITLE: String = "最近图片"

    /** 底部入口文案。 */
    const val ALL_LABEL: String = "全部图片"

    /** 无图片时的占位文案。 */
    const val EMPTY_TEXT: String = "暂无图片"

    /**
     * 按时间倒序取最近图片的路径。
     *
     * 参数 entries 为索引中的全部条目；limit 为条数上限。同一时间戳按路径升序，
     * 与相册页排序保持一致；非图片条目与超出上限的条目被丢弃。
     */
    fun recentPaths(
        entries: List<MediaEntry>,
        limit: Int = LIMIT,
    ): List<String> =
        if (limit <= 0) {
            emptyList()
        } else {
            MediaBuckets.sorted(entries.filter { it.isImage }).take(limit).map { it.path }
        }

    /**
     * 构造打开查看器的目的地。
     *
     * 参数 entries 为索引条目，index 为被点击的缩略图位置。返回的路径列表与卡片展示
     * 顺序一致，下标越界时夹取到有效范围；没有图片时返回 null，调用方据此忽略点击。
     */
    fun viewerTarget(
        entries: List<MediaEntry>,
        index: Int,
    ): Destination.PhotoViewer? {
        val paths = recentPaths(entries, LIMIT)
        if (paths.isEmpty()) return null
        return Destination.PhotoViewer(paths, index.coerceIn(0, paths.lastIndex))
    }

    /** 构造「全部图片」入口的目的地。 */
    fun filesTarget(): Destination.Files = Destination.Files()

    /** 取缩略图行数，空列表返回 0。 */
    fun gridRows(count: Int): Int = if (count <= 0) 0 else (count + COLUMNS - 1) / COLUMNS

    /**
     * 按卡片内容宽度换算单张缩略图的边长。
     *
     * 参数 contentWidth 为卡片去掉内边距后的可用宽度，gap 为缩略图间距。
     * 余数留在最右一列之外，保证每列等宽且至少 1 像素。
     */
    fun tileSize(
        contentWidth: Int,
        gap: Int,
    ): Int = ((contentWidth - gap * (COLUMNS - 1)) / COLUMNS).coerceAtLeast(1)
}

/**
 * 负一屏「最近图片」卡片。
 *
 * 缩略图取自 [MediaIndex] 的只读查询结果，位图统一经 [ThumbCacheRegistry]
 * 取用，卡片自身不解码原图、不扫描目录。没有图片或索引尚未就绪时展示占位文案，
 * 卡片底部始终保留「全部图片」入口。
 */
internal class FilesNegativeModule : NegativeModule {
    override val id: String = ID

    override val label: String = RecentImages.TITLE

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var host: NegativeHost? = null
    private var card: LinearLayout? = null
    private var body: LinearLayout? = null
    private var index: MediaIndex? = null

    private var entries: List<MediaEntry> = emptyList()
    private var loadJob: Job? = null
    private var loadToken: Int = 0
    private var skipNextResume: Boolean = false

    /**
     * 创建卡片视图。
     *
     * 参数 host 提供重绘与路由能力；视图创建后立即读取磁盘索引，读取失败按空态展示。
     * 返回值恒为卡片视图，宿主无需处理空值。
     */
    override fun createView(
        context: Context,
        host: NegativeHost,
    ): View {
        this.host = host
        index = MediaIndex(context)
        val card = buildCard(context)
        this.card = card
        render()
        reload()
        skipNextResume = true
        return card
    }

    /** 页面恢复时刷新最近条目，首次恢复跳过，避免创建后重复读取。 */
    override fun onResume() {
        if (skipNextResume) {
            skipNextResume = false
            return
        }
        reload()
    }

    /** 页面暂停时作废进行中的读取与缩略图任务。 */
    override fun onPause() {
        loadToken += 1
        loadJob?.cancel()
        loadJob = null
    }

    /**
     * 组装卡片骨架。
     *
     * 结构为标题行、内容区、分隔线与「全部图片」入口；内容区由 [render] 填充。
     */
    private fun buildCard(context: Context): LinearLayout {
        val tokens = DesignRuntime.tokens
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    InkWidgets.background(
                        tokens.radius,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK,
                    )
                setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
                layoutParams =
                    LinearLayout
                        .LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            topMargin = tokens.gap
                        }
            }
        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        header.addView(
            InkWidgets.heading(context, RecentImages.TITLE),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        card.addView(header)
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
        card.addView(body)
        this.body = body
        card.addView(InkWidgets.spacer(context, tokens.gap))
        card.addView(InkWidgets.divider(context))
        card.addView(
            InkWidgets.row(context, RecentImages.ALL_LABEL) {
                host?.open(RecentImages.filesTarget())
            },
        )
        return card
    }

    /** 依据当前条目重绘内容区：无图片时显示占位文案，有图片时排列两行三列缩略图。 */
    private fun render() {
        val body = body ?: return
        val context = body.context
        body.removeAllViews()
        val tokens = DesignRuntime.tokens
        if (entries.isEmpty()) {
            body.addView(
                InkWidgets.caption(context, RecentImages.EMPTY_TEXT).apply {
                    setPadding(0, tokens.gap, 0, tokens.gap)
                },
            )
            return
        }
        val paths = RecentImages.recentPaths(entries)
        val measured = card?.width ?: 0
        val contentWidth =
            if (measured > 0) {
                measured - tokens.gap * 2
            } else {
                context.resources.displayMetrics.widthPixels - tokens.pageGutter * 2 - tokens.gap * 2
            }
        val tile = RecentImages.tileSize(contentWidth, tokens.gap)
        val rows = RecentImages.gridRows(paths.size)
        for (row in 0 until rows) {
            body.addView(buildRow(context, paths, row, rows, tile))
        }
    }

    /**
     * 构造一行缩略图。
     *
     * 参数 paths 为按卡片顺序排列的路径列表，row 为行号，rowCount 为总行数，
     * tile 为单张缩略图边长；空位用透明占位视图补齐，保证列宽一致。
     */
    private fun buildRow(
        context: Context,
        paths: List<String>,
        row: Int,
        rowCount: Int,
        tile: Int,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val rowView =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
        for (column in 0 until RecentImages.COLUMNS) {
            val position = row * RecentImages.COLUMNS + column
            val cell =
                if (position < paths.size) {
                    thumbnail(context, paths[position], position, tile, row < rowCount - 1, column)
                } else {
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(tile, tile)
                    }
                }
            rowView.addView(cell)
        }
        return rowView
    }

    /**
     * 构造单张缩略图视图。
     *
     * 参数 hasBelow 表示下方还有行，用于保留纵向间距；column 用于判断是否保留右侧间距。
     */
    private fun thumbnail(
        context: Context,
        path: String,
        position: Int,
        tile: Int,
        hasBelow: Boolean,
        column: Int,
    ): ImageView {
        val tokens = DesignRuntime.tokens
        val image =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(InkColors.INK_4)
                layoutParams =
                    LinearLayout.LayoutParams(tile, tile).apply {
                        rightMargin = if (column < RecentImages.COLUMNS - 1) tokens.gap else 0
                        bottomMargin = if (hasBelow) tokens.gap else 0
                    }
                contentDescription = File(path).name
                setOnClickListener { openViewer(position) }
            }
        loadThumbnail(image, path, tile)
        return image
    }

    /**
     * 异步加载单张缩略图。
     *
     * 参数 view 为目标图片视图，path 为源文件路径，sidePx 为目标长边。位图由缓存持有，
     * 页面切换后回填前校验令牌，避免过期任务写回旧视图。
     */
    private fun loadThumbnail(
        view: ImageView,
        path: String,
        sidePx: Int,
    ) {
        val context = view.context.applicationContext ?: view.context
        val token = loadToken
        scope.launch(Dispatchers.IO) {
            val bitmap =
                runCatching {
                    ThumbCacheRegistry.get(context, sidePx).loadOrDecode(File(path)) {
                        ImageDecoder.decode(path, sidePx)
                    }
                }.getOrNull()
            if (!isActive) return@launch
            withContext(Dispatchers.Main.immediate) {
                if (token != loadToken) return@withContext
                if (bitmap != null) view.setImageBitmap(bitmap)
            }
        }
    }

    /** 读取索引：优先磁盘缓存，缺失或损坏时重建一次，读取结果按令牌回填。 */
    private fun reload() {
        val index = index ?: return
        loadToken += 1
        val token = loadToken
        loadJob?.cancel()
        loadJob =
            scope.launch {
                val data =
                    runCatching { index.loadCached() ?: index.rebuild() }.getOrNull()
                if (token != loadToken) return@launch
                entries = data?.entries.orEmpty().filter { it.isImage }
                render()
                host?.requestRedraw()
            }
    }

    /** 打开查看器并定位到被点击的缩略图。 */
    private fun openViewer(position: Int) {
        val target = RecentImages.viewerTarget(entries, position) ?: return
        host?.open(target)
    }

    companion object {
        /** 注册表中的模块标识。 */
        const val ID: String = "negative_files"
    }
}
