package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WidgetImageSelection
import com.shuiyu.octoberrain.core.common.effectiveImageCarouselMinutes
import com.shuiyu.octoberrain.core.common.effectiveImageSwipeDirection
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.core.render.loadOrDecode
import com.shuiyu.octoberrain.core.widget.WidgetTouchAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 图片组件：展示桌面配置中的自定义图片，私有导入仅显示选中图片，历史外部目录保留轮播。
 *
 * 图片写入应用私有目录后记录绝对路径；绘制时等比铺满可用区域并居中裁剪。
 * 单击在图片多于一张时切到下一张，单张时打开系统图片选择器，长按沿用组件基类行为。
 * 未选择图片或解码失败时绘制虚线空状态，不绘制多余的内层边框。
 */
@Suppress("TooManyFunctions") // 解码、轮播排程、命中与绘制同属组件职责，拆分后状态共享成本更高。
class ImageWidget(
    context: Context,
) : InkWidgetView(context) {
    private var bitmap: Bitmap? = null
    private var decodedPath: String? = null
    private var decodedEdge: Int = 0
    private var decodedMtime: Long = 0L

    /** 当前可轮播的图片，按文件名顺序排列。 */
    private var images: List<File> = emptyList()

    /** 当前展示的图片在 [images] 中的序号。 */
    private var index: Int = 0
    private var selectedPath: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    /** 当前手势序列的按下点，用于计算切图位移。 */
    private var pressX: Float = 0f
    private var pressY: Float = 0f

    /** 当前手势序列是否已经切过一次，保证一次序列只切一张。 */
    private var swipeStep = ImageSwipe.STEP_NONE

    /** 轮播定时任务，每次触发切到下一张并重新排程。 */
    private val tick =
        object : Runnable {
            override fun run() {
                if (!loadVersions.mayRunResources() || !ticking) return
                advance()
                scheduleTick()
            }
        }

    private var observer: FileObserver? = null
    private var observerKey: ImageDirectoryCache.Key? = null

    @Volatile
    private var observerWorking = false
    private val directoryCache =
        ImageDirectoryCache(
            probe = { key ->
                val dir = File(key.canonicalParent)
                directoryStamp(dir)
            },
            scan = { key ->
                WidgetImageFiles.resolveSelection(
                    emptyList(),
                    key.legacyPath,
                    key.legacySource,
                    context.filesDir,
                ) { WidgetImageSource.missing(context, it) }
            },
        )

    private fun directoryStamp(directory: File): Long? =
        when {
            !observerWorking -> null
            !directory.isDirectory || !directory.canRead() -> null
            else -> directory.lastModified().takeIf { it > 0L }
        }

    private fun clearDirectory() {
        observer?.stopWatching()
        observer = null
        observerKey = null
        observerWorking = false
        directoryCache.clear()
    }

    private fun observe(key: ImageDirectoryCache.Key) {
        if (!loadVersions.mayRunResources() || observerKey == key) return
        clearDirectory()
        observerKey = key
        val bound = directoryCache.request(key)
        try {
            observer =
                object : FileObserver(key.canonicalParent, ImageLoadPolicy.EVENT_MASK) {
                    override fun onEvent(
                        event: Int,
                        path: String?,
                    ) {
                        if (ImageLoadPolicy.isMutation(event)) directoryCache.onEvent(bound)
                    }
                }
            observer?.startWatching()
            observerWorking = true
        } catch (_: Exception) {
            observer?.stopWatching()
            observer = null
            observerWorking = false
        }
    }

    private val loadVersions = ImageLoadVersions()
    private var source: Source? = null
    private var loadWidth = 0
    private var loadHeight = 0

    private data class Source(
        val page: Int,
        val selections: List<WidgetImageSelection>,
        val path: String?,
        val uri: String?,
    )

    private data class LoadRequest(
        val source: Source,
        val index: Int,
        val selectedPath: String?,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap?,
        val decodedPath: String?,
        val decodedEdge: Int,
        val decodedMtime: Long,
        val token: ImageLoadVersions.Token,
    )

    private fun synchronizeSource(): Source {
        val desktop = config.desktop
        val current =
            Source(pageIndex, desktop.widgetImages.toList(), desktop.widgetImagePath, desktop.widgetImageSourceUri)
        if (source != current) {
            clearDirectory()
            source = current
            loadVersions.sourceChanged()
        }
        return current
    }

    override fun onWidgetConfig(config: LauncherConfig) {
        super.onWidgetConfig(config)
        synchronizeSource()
    }

    private suspend fun captureRequest(): LoadRequest =
        withContext(Dispatchers.Main.immediate) {
            LoadRequest(
                synchronizeSource(),
                index,
                selectedPath,
                widthPx,
                heightPx,
                bitmap,
                decodedPath,
                decodedEdge,
                decodedMtime,
                loadVersions.capture(),
            )
        }

    private fun eligibleLegacySource(source: Source): Boolean =
        source.selections.isEmpty() && !WidgetImageSource.missing(context, source.uri)

    private fun readableFile(file: File): Boolean = file.isFile && file.canRead()

    private fun validExternalFile(file: File): Boolean =
        readableFile(file) && !WidgetImageFiles.isManaged(file, context.filesDir)

    private fun readableTarget(path: String?): Boolean {
        if (path == null) return false
        return readableFile(File(path))
    }

    private fun directoryKey(request: LoadRequest): ImageDirectoryCache.Key? {
        val legacy = request.source.path?.let(::File)
        return when {
            !eligibleLegacySource(request.source) -> null
            legacy == null -> null
            !validExternalFile(legacy) -> null
            else ->
                ImageDirectoryCache.Key(
                    request.source.page,
                    legacy.canonicalFile.parent.orEmpty(),
                    request.source.path,
                    request.source.uri,
                )
        }
    }

    override suspend fun loadData(force: Boolean): Boolean {
        val request = captureRequest()
        val key = directoryKey(request)
        val result = resolveLoad(request, key, force) ?: return false
        return publishLoad(request, result)
    }

    private data class LoadResult(
        val candidates: List<File>,
        val nextIndex: Int,
        val path: String?,
        val edge: Int,
        val mtime: Long,
        val loaded: Bitmap?,
        val cacheRequest: ImageDirectoryCache.Request?,
        val selectionChanged: Boolean,
    )

    private suspend fun prepareDirectory(
        request: LoadRequest,
        key: ImageDirectoryCache.Key?,
    ): ImageDirectoryCache.Request? =
        withContext(Dispatchers.Main.immediate) {
            if (key != null && loadVersions.canPublish(request.token)) {
                observe(key)
                directoryCache.request(key)
            } else {
                null
            }
        }

    // Discard guards keep superseded loads from acquiring or publishing a replacement cache request.
    @Suppress("ReturnCount")
    private suspend fun resolveLoad(
        request: LoadRequest,
        key: ImageDirectoryCache.Key?,
        force: Boolean,
    ): LoadResult? {
        var cacheRequest = prepareDirectory(request, key)
        var candidates =
            if (key != null) {
                directoryCache.load(cacheRequest ?: return null, force)?.files ?: return null
            } else {
                WidgetImageFiles.resolveSelection(
                    request.source.selections,
                    request.source.path,
                    request.source.uri,
                    context.filesDir,
                ) { WidgetImageSource.missing(context, it) }
            }
        val selectionChanged = request.source.path != request.selectedPath
        val targetIndex =
            if (selectionChanged) {
                candidates.indexOfFirst { it.absolutePath == request.source.path }.coerceAtLeast(0)
            } else {
                request.index
            }
        var nextIndex = if (candidates.isEmpty()) 0 else targetIndex.coerceIn(0, candidates.lastIndex)
        var path = candidates.getOrNull(nextIndex)?.absolutePath
        if (key != null && needsTargetRetry(path)) {
            val retry =
                withContext(Dispatchers.Main.immediate) {
                    if (!loadVersions.canPublish(request.token)) return@withContext null
                    directoryCache.invalidate()
                    directoryCache.request(key)
                } ?: return null
            cacheRequest = retry
            candidates = directoryCache.load(retry, force = true)?.files ?: return null
            nextIndex = if (candidates.isEmpty()) 0 else targetIndex.coerceIn(0, candidates.lastIndex)
            path = candidates.getOrNull(nextIndex)?.absolutePath
        }
        val edge = maxOf(1, request.width, request.height)
        val mtime = path?.let { File(it).lastModified() } ?: 0L
        val loaded = loadBitmap(request, path, edge, mtime, force)
        return LoadResult(candidates, nextIndex, path, edge, mtime, loaded, cacheRequest, selectionChanged)
    }

    private fun needsTargetRetry(path: String?): Boolean = path != null && !readableTarget(path)

    private fun reusableBitmap(
        request: LoadRequest,
        path: String,
        edge: Int,
        mtime: Long,
    ): Boolean =
        when {
            request.bitmap == null -> false
            path != request.decodedPath -> false
            request.decodedEdge < edge -> false
            else -> ImageLoadPolicy.sameContent(request.decodedMtime, mtime)
        }

    private fun loadBitmap(
        request: LoadRequest,
        path: String?,
        edge: Int,
        mtime: Long,
        force: Boolean,
    ): Bitmap? =
        when {
            path == null -> null
            !force && reusableBitmap(request, path, edge, mtime) -> request.bitmap
            !readableTarget(path) -> null
            else -> decode(path, edge, edge)
        }

    private suspend fun publishLoad(
        request: LoadRequest,
        result: LoadResult,
    ): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (!loadVersions.canPublish(request.token) ||
                result.cacheRequest?.let { !directoryCache.isCurrent(it) } == true
            ) {
                false
            } else {
                if (result.selectionChanged) stopTicker()
                images = result.candidates
                index = result.nextIndex
                selectedPath = request.source.path
                bitmap = result.loaded
                decodedMtime = result.mtime
                decodedPath = result.path
                decodedEdge =
                    if (result.loaded != null && result.loaded === request.bitmap) {
                        request.decodedEdge
                    } else {
                        result.edge
                    }
                if (result.candidates.isEmpty()) stopTicker() else startTicker()
                true
            }
        }

    override fun onWidgetSizeChanged(
        width: Int,
        height: Int,
    ) {
        if (loadWidth != width || loadHeight != height) {
            loadWidth = width
            loadHeight = height
            loadVersions.dimensionsChanged()
        }
        super.onWidgetSizeChanged(width, height)
        reload(force = false)
    }

    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        val area = RectF(padX, pad, width - padX, height - pad)
        if (area.width() <= 0f || area.height() <= 0f) return

        val image = bitmap
        if (image == null || image.isRecycled) {
            drawEmptyState(canvas, area)
            return
        }

        // 先以纸白填底，再等比放大到覆盖可用区域并取中心部分，避免边缘露出底色
        canvas.drawRect(area, fillPaint(InkColors.PAPER))
        val scale = maxOf(area.width() / image.width, area.height() / image.height)
        val sourceWidth = (area.width() / scale).roundToInt().coerceIn(1, image.width)
        val sourceHeight = (area.height() / scale).roundToInt().coerceIn(1, image.height)
        val sourceLeft = (image.width - sourceWidth) / 2
        val sourceTop = (image.height - sourceHeight) / 2
        val source = Rect(sourceLeft, sourceTop, sourceLeft + sourceWidth, sourceTop + sourceHeight)
        val bitmapPaint = fillPaint(InkColors.INK).apply { isFilterBitmap = true }
        canvas.drawBitmap(image, source, area, bitmapPaint)
    }

    /** 绘制虚线边框与居中提示，作为尚未选择图片时的空状态。 */
    private fun drawEmptyState(
        canvas: Canvas,
        area: RectF,
    ) {
        InkDraw.dashedRect(canvas, area, tokens.radius.toFloat(), strokePaint(InkColors.INK_3))
        val paint = textPaint(tokens.textCaption, InkColors.INK_2)
        val message =
            if (config.desktop.widgetImages.isEmpty() && config.desktop.widgetImagePath.isNullOrBlank()) {
                "轻触选择图片"
            } else {
                "图片无法读取"
            }
        InkDraw.textCenter(canvas, message, area.centerX(), area.centerY() - InkDraw.lineHeight(paint) / 2f, paint)
    }

    /** 切到下一张图片并重新解码，仅在图片多于一张时生效。 */
    private fun advance() {
        if (images.size <= 1) return
        loadVersions.selectionChanged()
        index = ImageSwipe.wrap(index, images.size, ImageSwipe.STEP_NEXT)
        reload(force = false)
    }

    /**
     * 处理手势序列的按下、移动与结束。
     *
     * 按下记录起点并重置本序列的切图标记；移动按配置方向记录步进，抬起才提交切图，
     * 手动切图后重排自动轮播；取消只清标记，短滑未到阈值时保持当前图片。
     */
    override fun onTouch(
        action: WidgetTouchAction,
        x: Float,
        y: Float,
    ): Boolean {
        when (action) {
            WidgetTouchAction.DOWN -> {
                pressX = x
                pressY = y
                swipeStep = ImageSwipe.STEP_NONE
            }
            WidgetTouchAction.MOVE -> handleSwipe(x - pressX, y - pressY)
            WidgetTouchAction.UP -> {
                handleSwipe(x - pressX, y - pressY)
                if (swipeStep != ImageSwipe.STEP_NONE) {
                    stopTicker()
                    loadVersions.selectionChanged()
                    index = ImageSwipe.wrap(index, images.size, swipeStep)
                    reload(force = false)
                }
                swipeStep = ImageSwipe.STEP_NONE
            }
            WidgetTouchAction.CANCEL -> swipeStep = ImageSwipe.STEP_NONE
        }
        // 先在系统触摸阈值认领方向，实际切图仍需36dp，避免桌面较短的翻页阈值抢走序列。
        return action == WidgetTouchAction.MOVE &&
            images.size > 1 &&
            ImageSwipe.step(
                config.desktop.effectiveImageSwipeDirection,
                x - pressX,
                y - pressY,
                android.view.ViewConfiguration
                    .get(context)
                    .scaledTouchSlop
                    .toFloat(),
            ) != ImageSwipe.STEP_NONE
    }

    /** 依据配置方向判断本次移动是否切图，切图后重排自动轮播。 */
    private fun handleSwipe(
        dx: Float,
        dy: Float,
    ) {
        if (images.size <= 1) return
        val step =
            ImageSwipe.step(
                config.desktop.effectiveImageSwipeDirection,
                dx,
                dy,
                tokens.dpF(SWIPE_THRESHOLD_DP),
            )
        swipeStep = step
    }

    /** 启动轮播定时，图片少于两张或已在运行时直接返回。 */
    private fun startTicker() {
        if (!loadVersions.mayRunResources() || ticking || images.size <= 1) return
        ticking = true
        handler.postDelayed(tick, intervalMillis())
    }

    /** 重新排程下一次轮播。 */
    private fun scheduleTick() {
        if (!loadVersions.mayRunResources() || !ticking || images.size <= 1) {
            ticking = false
            return
        }
        handler.postDelayed(tick, intervalMillis())
    }

    override fun onHostVisible(visible: Boolean) {
        val hiding = ImageLoadPolicy.isHiding(hostVisible, visible)
        if (hostVisible != visible) clearDirectory()
        loadVersions.setVisible(visible)
        super.onHostVisible(visible)
        if (visible) {
            reload()
            startTicker()
        } else {
            stopTicker()
            if (hiding) {
                releaseBitmap()
                decodedPath = null
            }
        }
    }

    /** 停止轮播定时。 */
    private fun stopTicker() {
        ticking = false
        handler.removeCallbacks(tick)
    }

    /**
     * 轮播间隔毫秒数。
     *
     * 每次排程时从桌面配置读取 `effectiveImageCarouselMinutes`，非法值由该派生属性回退，
     * 配置变化后下一次排程即使用新间隔；配置服务未就绪时使用默认值。
     *
     * @return 间隔毫秒数
     */
    private fun intervalMillis(): Long {
        val minutes: Int =
            if (AppServices.isReady()) {
                config.desktop.effectiveImageCarouselMinutes
            } else {
                FALLBACK_CAROUSEL_MINUTES
            }
        return minutes.coerceAtLeast(1).toLong() * 60_000L
    }

    /** 打开共用图片选择入口，更新后重新加载本组件。 */
    private fun chooseImage() {
        ImageWidgetSettings.choose(context, scope, pageIndex = pageIndex) {
            refreshConfigSnapshot()
            synchronizeSource()
            reload(force = true)
        }
    }

    /**
     * 按目标尺寸取样解码图片，结果经缩略图缓存量化后返回。
     *
     * 参数 path 为图片文件路径，targetWidth 与 targetHeight 为组件内可用区域宽高；
     * 缓存键的目标长边取两者较大值，未命中时经 [ImageDecoder] 解码。返回的位图由
     * 缓存持有，调用方不得回收。
     */
    private fun decode(
        path: String,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val file = File(path).takeIf { it.isFile } ?: return null
        val edge = maxOf(targetWidth, targetHeight).coerceAtLeast(1)
        return ThumbCacheRegistry.get(context, edge).loadOrDecode(file) {
            ImageDecoder.decode(path, edge)
        }
    }

    /** 释放对位图的引用；位图由缩略图缓存持有，不在此回收。 */
    private fun releaseBitmap() {
        bitmap = null
        decodedMtime = 0L
    }

    override fun onClick(
        x: Float,
        y: Float,
    ): Boolean {
        if (images.size > 1) {
            advance()
            return true
        }
        chooseImage()
        return true
    }

    override fun onWidgetDestroy() {
        loadVersions.release()
        clearDirectory()
        stopTicker()
        releaseBitmap()
    }

    private companion object {
        /** 配置服务未就绪时的轮播间隔分钟数。 */
        const val FALLBACK_CAROUSEL_MINUTES: Int = 15

        /** 判定切图所需的滑动距离，单位 dp。 */
        const val SWIPE_THRESHOLD_DP: Float = 36f
    }
}
