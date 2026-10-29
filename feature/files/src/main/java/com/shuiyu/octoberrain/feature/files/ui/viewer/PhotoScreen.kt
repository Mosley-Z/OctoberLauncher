package com.shuiyu.octoberrain.feature.files.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.WallpaperResult
import com.shuiyu.octoberrain.core.common.WallpaperScope
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.render.ImageDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.hypot

/** 查看器的来源，用于返回路径与分享策略的区分。 */
enum class PhotoSource {
    /** 文件浏览页进入，左右滑动在同一目录内切换。 */
    BROWSER,

    /** 外部应用通过 ACTION_VIEW 打开的单张图片。 */
    EXTERNAL,

    /** 应用内选图结果预览。 */
    PICKER,
}

/**
 * 图片查看页面。
 *
 * 承担路径列表的状态持有与视图创建，返回、动作面板与手势由 [PhotoSurface] 处理。
 * 参数 paths 为同目录图片列表；startIndex 为起始下标；source 为来源；onClose 为关闭出口。
 */
class PhotoScreen(
    private val paths: List<String>,
    private val startIndex: Int,
    private val source: PhotoSource = PhotoSource.BROWSER,
    private val onClose: () -> Unit = {},
) : Screen {
    private var surface: PhotoSurface? = null

    override fun createView(context: Context): View {
        val view = PhotoSurface(context, paths, startIndex, source, onClose)
        surface = view
        return view
    }

    override fun onResume() {
        surface?.refresh()
    }

    override fun onBack(): Boolean = surface?.handleBack() ?: false

    override fun onDestroy() {
        surface?.detach()
        surface = null
    }
}

/**
 * 查看器绘制与交互视图。
 *
 * 三档缩放、左右换图、邻图预取、动作面板与详情面板集中在此实现；
 * 解码在后台线程完成，结果按代际与下标校验后回填主线程。
 *
 * 参数 context 为视图上下文；paths 为路径列表；startIndex 为起始下标；
 * source 为来源；onClose 为关闭出口。
 */
@Suppress("TooManyFunctions")
class PhotoSurface(
    context: Context,
    private val paths: List<String>,
    startIndex: Int,
    private val source: PhotoSource,
    private val onClose: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val state = ViewerState(paths, startIndex)
    private val generation = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefetched = HashMap<Int, Bitmap>()

    /** 壁纸设置任务的协程作用域，页面销毁时取消。 */
    private val wallpaperScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 在途的壁纸设置任务。 */
    private var wallpaperJob: Job? = null
    private var metrics: ViewerMetrics? = null
    private var chrome: ViewerChrome? = null
    private var bitmap: Bitmap? = null
    private var loading = false
    private var failed = false
    private var detached = false
    private var panel = Panel.NONE
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    private var swipeTracking = false
    private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.PAPER }
    private val bitmapPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = InkColors.PAPER }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
            strokeWidth = maxOf(1f, 1.2f * density)
            style = Paint.Style.STROKE
        }
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK
            textSize = 14f * scaledDensity
        }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_3
            textSize = 12f * scaledDensity
        }
    private val destination = RectF()
    private var longPressFired = false
    private val longPressTask =
        Runnable {
            longPressFired = true
            openPanel(Panel.ACTIONS)
        }

    init {
        isClickable = true
        isFocusable = true
        refresh()
    }

    /** 重新解码当前图片，用于进入页面、换图与档位切换。 */
    fun refresh() {
        val content = metrics?.content
        if (content == null || content.isEmpty || state.isEmpty) return
        val request = ViewerDecoder.requestFor(state, content.width, content.height)
        val current = generation.incrementAndGet()
        loading = true
        failed = false
        val index = state.index
        val cached = prefetched.remove(index)
        if (cached != null) {
            accept(index, current, cached)
        } else {
            startDecode(index, current, request)
        }
    }

    /** 在守护线程解码当前图片，回主线程前用代际与下标双重校验。 */
    private fun startDecode(
        index: Int,
        current: Int,
        request: ViewerDecoder.Request,
    ) {
        val path = state.path
        if (path.isEmpty()) return
        val worker =
            Thread {
                val started = System.currentTimeMillis()
                val decoded = ViewerDecoder.decode(path, request)
                val elapsed = System.currentTimeMillis() - started
                InkLog.d(TAG, "解码 ${File(path).name} 下标 $index 耗时 ${elapsed}ms")
                post {
                    if (detached || current != generation.get()) {
                        decoded?.recycle()
                    } else {
                        accept(index, current, decoded)
                    }
                }
            }
        worker.isDaemon = true
        worker.start()
    }

    /** 释放位图与预取缓存。 */
    fun detach() {
        detached = true
        wallpaperScope.cancel()
        generation.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
        bitmap?.recycle()
        bitmap = null
        for (cached in prefetched.values) cached.recycle()
        prefetched.clear()
    }

    /** 处理返回：面板打开时先关闭，否则由宿主出栈。 */
    fun handleBack(): Boolean {
        if (panel != Panel.NONE) {
            panel = Panel.NONE
            invalidate()
            return true
        }
        return false
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val built =
            ViewerMetrics(
                width = width,
                height = height,
                topBarHeight = (64f * density).toInt(),
                rowHeight = (44f * density).toInt(),
                minTouch = (44f * density).toInt(),
                bottomBarHeight = (52f * density).toInt(),
            )
        metrics = built
        chrome = ViewerChrome(context, built)
        refresh()
    }

    override fun onDraw(canvas: Canvas) {
        val size = metrics ?: return
        canvas.drawRect(0f, 0f, size.screenWidth.toFloat(), size.screenHeight.toFloat(), paperPaint)
        drawPicture(canvas, size)
        if (bitmap == null) drawPlaceholder(canvas, size)
        chrome?.draw(canvas, fileName(), state.pageLabel)
        when (panel) {
            Panel.ACTIONS -> drawActions(canvas, size)
            Panel.DETAILS -> drawDetails(canvas, size)
            Panel.NONE -> Unit
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeDownX = event.x
                swipeDownY = event.y
                swipeTracking = true
                longPressFired = false
                mainHandler.postDelayed(longPressTask, LONG_PRESS_MILLIS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeTracking && distance(event.x, event.y) > metricsTouchSlop()) {
                    mainHandler.removeCallbacks(longPressTask)
                }
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressTask)
                if (longPressFired) {
                    // 长按已打开面板，同一次抬起不再按点击处理，避免面板立即关闭。
                    longPressFired = false
                } else if (swipeTracking) {
                    handleUp(event.x, event.y, event.x - swipeDownX, event.y - swipeDownY)
                }
                swipeTracking = false
            }
            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressTask)
                swipeTracking = false
            }
        }
        return true
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                turnPage(forward = false)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                turnPage(forward = true)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }

    /** 当前图片路径，供分享与旋转使用。 */
    fun currentPath(): String? = state.path.ifEmpty { null }

    /** 来源枚举，供结果回传使用。 */
    fun currentSource(): PhotoSource = source

    private fun accept(
        index: Int,
        current: Int,
        decoded: Bitmap?,
    ) {
        if (current != generation.get() || index != state.index) {
            decoded?.recycle()
            return
        }
        bitmap?.recycle()
        bitmap = decoded
        loading = false
        failed = decoded == null
        invalidate()
        prefetchNeighbors()
    }

    private fun prefetchNeighbors() {
        val content = metrics?.content ?: return
        val request = ViewerDecoder.requestFor(state, content.width, content.height)
        for (index in state.prefetchIndices()) {
            val path = state.paths.getOrNull(index)
            if (path != null && !prefetched.containsKey(index)) startPrefetch(index, path, request)
        }
    }

    /** 预取单张相邻图片，结果按代际与当前状态决定回收或登记。 */
    private fun startPrefetch(
        index: Int,
        path: String,
        request: ViewerDecoder.Request,
    ) {
        val worker =
            Thread {
                val decoded = ViewerDecoder.decode(path, request)
                if (decoded == null) return@Thread
                post {
                    if (detached || prefetched.containsKey(index)) {
                        decoded.recycle()
                    } else {
                        prefetched[index] = decoded
                        trimPrefetch()
                    }
                }
            }
        worker.isDaemon = true
        worker.start()
    }

    private fun trimPrefetch() {
        while (prefetched.size > PREFETCH_LIMIT) {
            val distant = prefetched.keys.maxByOrNull { abs(it - state.index) } ?: return
            prefetched.remove(distant)?.recycle()
        }
    }

    private fun handleUp(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
    ) {
        val size = metrics ?: return
        val threshold = SWIPE_THRESHOLD_DP * density
        val thumb = THUMB_DP * density
        when {
            panel != Panel.NONE -> handlePanelTap(y.toInt(), size)
            abs(dx) > threshold && abs(dx) > abs(dy) -> turnPage(forward = dx < 0)
            abs(dx) < thumb && abs(dy) < thumb -> {
                val action = ViewerHitTest.hit(x.toInt(), y.toInt(), size)
                if (action != null) handleAction(action) else cycleZoom()
            }
        }
    }

    private fun turnPage(forward: Boolean) {
        if (state.isEmpty) return
        val moved = if (forward) state.next() else state.previous()
        if (!moved) {
            toast("已到${if (forward) "最后" else "第一"}张")
            return
        }
        bitmap?.recycle()
        bitmap = null
        panel = Panel.NONE
        refresh()
        invalidate()
    }

    private fun cycleZoom() {
        state.cycleZoom()
        bitmap?.recycle()
        bitmap = null
        refresh()
        invalidate()
    }

    private fun handleAction(action: ViewerAction) {
        when (action) {
            ViewerAction.BACK -> if (!handleBack()) onClose()
            ViewerAction.INFO -> openPanel(Panel.DETAILS)
            ViewerAction.ROTATE,
            ViewerAction.CROP,
            ViewerAction.FILTER,
            ViewerAction.SAVE,
            -> openEdit()
            ViewerAction.SET_WALLPAPER -> openWallpaperMenu()
        }
    }

    /** 底栏编辑类动作进入编辑页，编辑页负责裁剪、旋转、滤镜与另存。 */
    private fun openEdit() {
        val path = state.path
        if (path.isEmpty()) return
        panel = Panel.NONE
        AppServices.router.navigate(Destination.PhotoEditor(path, SOURCE_VIEWER))
    }

    private fun openPanel(target: Panel) {
        panel = target
        invalidate()
    }

    private fun share() {
        val path = state.path
        if (path.isEmpty()) return
        val file = File(path)
        val uri = shareUri(file)
        if (uri == null) {
            toast("无法分享该图片")
            return
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(file.name)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(intent, "分享图片")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }.onFailure { toast("没有可用的分享应用") }
    }

    private fun shareUri(file: File): Uri? {
        val direct = uriFor(file)
        val target = if (direct == null) copyToShareCache(file) else file
        return direct ?: target?.let { uriFor(it) }
    }

    /** 通过本应用的 FileProvider 生成分享 Uri，失败返回 null。 */
    private fun uriFor(file: File): Uri? =
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()

    private fun copyToShareCache(file: File): File? =
        runCatching {
            val directory = File(context.cacheDir, "share").apply { mkdirs() }
            val target = File(directory, file.name)
            file.inputStream().use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            target
        }.getOrNull()

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }

    private fun handlePanelTap(
        y: Int,
        size: ViewerMetrics,
    ) {
        val rows = if (panel == Panel.ACTIONS) ACTION_ROWS.size + 1 else DETAIL_ROWS.size
        val row = size.panelRowAt(0, y, rows)
        if (row < 0) {
            panel = Panel.NONE
            invalidate()
            return
        }
        if (panel == Panel.DETAILS) {
            panel = Panel.NONE
            invalidate()
            return
        }
        panel = Panel.NONE
        invalidate()
        when (ACTION_ROWS.getOrNull(row)) {
            "分享" -> share()
            "旋转" -> openEdit()
            "详情" -> openPanel(Panel.DETAILS)
            "量化" -> {
                state.toggleGrayscale()
                bitmap?.recycle()
                bitmap = null
                refresh()
            }
            else -> Unit
        }
    }

    /** 打开壁纸目标选择菜单。 */
    private fun openWallpaperMenu() {
        val path = state.path
        val channel = AppServices.wallpaper
        when {
            path.isEmpty() -> Unit
            channel == null -> toast("当前版本不含壁纸通道")
            !channel.supports() -> toast("当前设备不支持壁纸设置")
            channel.isBusy() -> toast("正在设置壁纸")
            else -> chooseWallpaperScope(path)
        }
    }

    /** 弹出休眠壁纸、关机壁纸与同时设置三个选项。 */
    private fun chooseWallpaperScope(path: String) {
        val labels = WallpaperScope.entries.map { it.label }
        InkDialogs.choose(context, "设置为", labels) { index ->
            WallpaperScope.entries.getOrNull(index)?.let { scope -> startWallpaper(path, scope) }
        }
    }

    /** 执行壁纸设置，完成后按逐目标结果给出提示。 */
    private fun startWallpaper(
        path: String,
        scope: WallpaperScope,
    ) {
        val channel = AppServices.wallpaper ?: return
        wallpaperJob?.cancel()
        toast("正在设置${scope.label}")
        wallpaperJob =
            wallpaperScope.launch {
                val result =
                    runCatching {
                        channel.apply(path, scope)
                    }.getOrElse { error ->
                        WallpaperResult.Failed(emptyList(), error.message ?: "设置失败")
                    }
                if (!detached) toast(resultMessage(result))
            }
    }

    /** 把逐目标结果整理为单行提示。 */
    private fun resultMessage(result: WallpaperResult): String =
        when (result) {
            is WallpaperResult.AllDone -> result.outcomes.joinToString("；") { "${it.target.label}完成" }
            is WallpaperResult.Partial ->
                "部分完成：" +
                    result.outcomes.joinToString("；") { outcome ->
                        "${outcome.target.label}${if (outcome.success) "完成" else "失败"}"
                    }
            is WallpaperResult.Failed ->
                if (result.outcomes.isEmpty()) {
                    result.message
                } else {
                    "设置失败：" +
                        result.outcomes.joinToString("；") { outcome ->
                            "${outcome.target.label}${if (outcome.success) "完成" else "失败"}"
                        }
                }
            is WallpaperResult.Unavailable -> result.message
        }

    private fun drawPicture(
        canvas: Canvas,
        size: ViewerMetrics,
    ) {
        val image = bitmap ?: return
        val content = size.content
        if (content.isEmpty) return
        val scale = scaleFor(image, content)
        val drawWidth = image.width * scale
        val drawHeight = image.height * scale
        val left = content.left + (content.width - drawWidth) / 2f
        val top = content.top + (content.height - drawHeight) / 2f
        destination.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(image, null, destination, bitmapPaint)
    }

    private fun scaleFor(
        image: Bitmap,
        content: ViewerArea,
    ): Float =
        when (state.zoom) {
            ViewerZoom.FIT -> minOf(content.width.toFloat() / image.width, content.height.toFloat() / image.height)
            ViewerZoom.FILL -> maxOf(content.width.toFloat() / image.width, content.height.toFloat() / image.height)
            ViewerZoom.ORIGINAL -> 1f
        }

    private fun drawPlaceholder(
        canvas: Canvas,
        size: ViewerMetrics,
    ) {
        val content = size.content
        val text =
            when {
                failed -> "无法打开图片"
                loading -> "正在加载"
                else -> ""
            }
        if (text.isEmpty()) return
        bodyPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, content.left + content.width / 2f, content.top + content.height / 2f, bodyPaint)
        bodyPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawActions(
        canvas: Canvas,
        size: ViewerMetrics,
    ) {
        val rows = ACTION_ROWS.size + 1
        drawPanel(canvas, size, rows)
        ACTION_ROWS.forEachIndexed { index, label ->
            val text = if (label == "量化") "量化：${if (state.grayscale) "开" else "关"}" else label
            drawRow(canvas, text, size.rowArea(rows, index), bodyPaint)
        }
        drawRow(canvas, "左右滑动切换图片", size.rowArea(rows, rows - 1), smallPaint)
    }

    private fun drawDetails(
        canvas: Canvas,
        size: ViewerMetrics,
    ) {
        val rows = DETAIL_ROWS.size
        drawPanel(canvas, size, rows)
        val values = detailValues()
        DETAIL_ROWS.forEachIndexed { index, label ->
            val area = size.rowArea(rows, index)
            drawRow(canvas, label, area, smallPaint)
            val right = area.right - (DETAIL_PADDING * density).toInt()
            val width = bodyPaint.measureText(values[index])
            canvas.drawText(values[index], right - width, rowBaseline(area), bodyPaint)
        }
    }

    private fun drawPanel(
        canvas: Canvas,
        size: ViewerMetrics,
        rows: Int,
    ) {
        val area = size.panelArea(rows)
        canvas.drawRect(
            area.left.toFloat(),
            area.top.toFloat(),
            area.right.toFloat(),
            area.bottom.toFloat(),
            panelPaint,
        )
        canvas.drawLine(
            area.left.toFloat(),
            area.top.toFloat(),
            area.right.toFloat(),
            area.top.toFloat(),
            linePaint,
        )
    }

    private fun drawRow(
        canvas: Canvas,
        label: String,
        area: ViewerArea,
        paint: Paint,
    ) {
        canvas.drawText(label, area.left + DETAIL_PADDING * density, rowBaseline(area), paint)
    }

    private fun rowBaseline(area: ViewerArea): Float {
        val offset = (bodyPaint.descent() + bodyPaint.ascent()) / 2f
        return area.top + area.height / 2f - offset
    }

    private fun detailValues(): List<String> {
        val path = state.path
        if (path.isEmpty()) return List(DETAIL_ROWS.size) { "--" }
        val file = File(path)
        val size = ImageDecoder.size(path)
        return listOf(
            if (size != null) "${size[0]} × ${size[1]}" else "--",
            ViewerText.formatSize(file.length()),
            ViewerText.formatTime(file.lastModified()),
            path,
            ViewerText.formatLabel(file.name),
        )
    }

    private fun fileName(): String = File(state.path).name.ifEmpty { "图片" }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun distance(
        x: Float,
        y: Float,
    ): Float = hypot((x - swipeDownX).toDouble(), (y - swipeDownY).toDouble()).toFloat()

    private fun metricsTouchSlop(): Float = 12f * density

    /** 当前面板形态。 */
    private enum class Panel {
        NONE,
        ACTIONS,
        DETAILS,
    }

    private companion object {
        private const val TAG = "图片查看"
        private const val SOURCE_VIEWER = "viewer"
        private const val LONG_PRESS_MILLIS = 350L
        private const val SWIPE_THRESHOLD_DP = 48f
        private const val THUMB_DP = 8f
        private const val PREFETCH_LIMIT = 2
        private const val DETAIL_PADDING = 18f
        private val ACTION_ROWS = listOf("分享", "旋转", "详情", "量化")
        private val DETAIL_ROWS = listOf("尺寸", "大小", "时间", "路径", "格式")
    }
}
