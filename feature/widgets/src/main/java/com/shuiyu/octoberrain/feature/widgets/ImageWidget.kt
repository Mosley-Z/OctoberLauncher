package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.effectiveImageCarouselSeconds
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.core.render.loadOrDecode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 图片组件：展示桌面配置中的自定义图片，图片目录内存在多张时按轮播间隔依次切换。
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

    /** 当前可轮播的图片，按文件名顺序排列。 */
    private var images: List<File> = emptyList()

    /** 当前展示的图片在 [images] 中的序号。 */
    private var index: Int = 0

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false

    /** 轮播定时任务，每次触发切到下一张并重新排程。 */
    private val tick =
        object : Runnable {
            override fun run() {
                advance()
                scheduleTick()
            }
        }

    override suspend fun loadData(force: Boolean): Boolean {
        val candidates = resolveImages()
        images = candidates
        if (candidates.isEmpty()) {
            releaseBitmap()
            decodedPath = null
            stopTicker()
            return true
        }
        index = index.coerceIn(0, candidates.lastIndex)
        val path = candidates[index].absolutePath
        if (path == decodedPath && bitmap != null) {
            startTicker()
            return true
        }
        val loaded = decode(path, maxOf(1, widthPx), maxOf(1, heightPx))
        releaseBitmap()
        bitmap = loaded
        decodedPath = path
        startTicker()
        return true
    }

    override fun onWidgetSizeChanged(
        width: Int,
        height: Int,
    ) {
        super.onWidgetSizeChanged(width, height)
        reload(force = true)
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
        val message = if (config.desktop.widgetImagePath.isNullOrBlank()) "轻触选择图片" else "图片无法读取"
        InkDraw.textCenter(canvas, message, area.centerX(), area.centerY() - InkDraw.lineHeight(paint) / 2f, paint)
    }

    /**
     * 解析可轮播的图片列表。
     *
     * 以配置中的图片路径所在目录为图片来源，目录内的图片文件按文件名升序排列；
     * 配置路径对应文件不在目录内时，仅使用配置路径本身。
     *
     * @return 图片文件列表，未选择图片时为空列表
     */
    private fun resolveImages(): List<File> {
        val path = config.desktop.widgetImagePath?.takeIf { it.isNotBlank() }
        val current = path?.let { File(it) }?.takeIf { it.isFile }
        val directory = current?.parentFile?.takeIf { it.isDirectory }
        val siblings =
            directory
                ?.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                .orEmpty()
        return siblings.ifEmpty { current?.let { listOf(it) }.orEmpty() }
    }

    /** 切到下一张图片并重新解码，仅在图片多于一张时生效。 */
    private fun advance() {
        if (images.size <= 1) return
        index = (index + 1) % images.size
        reload(force = true)
    }

    /** 启动轮播定时，图片少于两张或已在运行时直接返回。 */
    private fun startTicker() {
        if (!hostVisible || ticking || images.size <= 1) return
        ticking = true
        handler.postDelayed(tick, intervalMillis())
    }

    /** 重新排程下一次轮播。 */
    private fun scheduleTick() {
        if (images.size <= 1) {
            ticking = false
            return
        }
        handler.postDelayed(tick, intervalMillis())
    }

    override fun onHostVisible(visible: Boolean) {
        super.onHostVisible(visible)
        if (visible) startTicker() else stopTicker()
    }

    /** 停止轮播定时。 */
    private fun stopTicker() {
        ticking = false
        handler.removeCallbacks(tick)
    }

    /**
     * 轮播间隔毫秒数。
     *
     * 每次排程时从桌面配置读取 `effectiveImageCarouselSeconds`，非法值由该派生属性回退，
     * 配置变化后下一次排程即使用新间隔；配置服务未就绪时使用默认值。
     *
     * @return 间隔毫秒数
     */
    private fun intervalMillis(): Long {
        val seconds: Int =
            if (AppServices.isReady()) {
                AppServices.config.state.value.desktop.effectiveImageCarouselSeconds
            } else {
                FALLBACK_CAROUSEL_SECONDS
            }
        return seconds.coerceAtLeast(1).toLong() * 1_000L
    }

    /** 打开系统图片选择器，选中后复制到私有目录并写入桌面配置。 */
    private fun chooseImage() {
        if (!AppServices.hasPicker()) {
            notify("当前环境不支持选择图片")
            return
        }
        val started =
            AppServices.picker.pick(arrayOf("image/*")) { uri ->
                if (uri == null) return@pick
                scope.launch {
                    val target = withContext(Dispatchers.IO) { copyToInternal(uri) }
                    if (target == null) {
                        notify("图片读取失败")
                        return@launch
                    }
                    val previous = AppServices.config.state.value.desktop.widgetImagePath
                    AppServices.config.edit { config ->
                        config.copy(desktop = config.desktop.copy(widgetImagePath = target.absolutePath))
                    }
                    previous?.takeIf { it.isNotBlank() && it != target.absolutePath }?.let { old ->
                        ThumbCacheRegistry.invalidate(context, File(old))
                    }
                    RefreshScheduler.notifyContentChanged()
                    AppServices.refresh.requestPartial()
                    refreshConfigSnapshot()
                    reload(force = true)
                    notify("已更新图片组件")
                }
            }
        if (!started) notify("无法打开图片选择器")
    }

    /** 把选中的图片复制到 filesDir/widget-image/，返回新文件；失败时返回 null。 */
    private fun copyToInternal(uri: Uri): File? =
        try {
            val directory = File(context.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }
            val target = File(directory, "widget-${System.currentTimeMillis()}.img")
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                null
            } else {
                input.use { stream ->
                    FileOutputStream(target).use { output -> stream.copyTo(output) }
                }
                if (target.length() <= 0L) {
                    target.delete()
                    null
                } else {
                    target
                }
            }
        } catch (error: Exception) {
            InkLog.w("组件", "复制组件图片失败", error)
            null
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
        stopTicker()
        releaseBitmap()
    }

    private companion object {
        /** 组件图片的私有存储目录名。 */
        const val IMAGE_DIRECTORY = "widget-image"

        /** 配置服务未就绪时的轮播间隔秒数。 */
        const val FALLBACK_CAROUSEL_SECONDS: Int = 15
    }
}
