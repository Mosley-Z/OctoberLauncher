package com.shuiyu.octoberrain.feature.files.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.device.ScreenSizeProvider
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.feature.files.data.CropMath
import com.shuiyu.octoberrain.feature.files.data.CropRect
import com.shuiyu.octoberrain.feature.files.data.EditExport
import com.shuiyu.octoberrain.feature.files.data.EditOp
import com.shuiyu.octoberrain.feature.files.data.EditSession
import com.shuiyu.octoberrain.feature.files.data.ExportResult
import com.shuiyu.octoberrain.feature.files.data.FrameMath
import com.shuiyu.octoberrain.feature.files.data.FrameMode
import com.shuiyu.octoberrain.feature.files.data.FrameSpec
import com.shuiyu.octoberrain.feature.files.data.PrefsRotationStore
import com.shuiyu.octoberrain.feature.files.ui.FileTokens
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * 编辑页的绘制与交互视图。
 *
 * 顶栏提供取消与保存，底栏提供裁剪、旋转、滤镜、另存四个页签，页签上方的控制条按页签
 * 显示对应操作。预览图在后台线程按会话操作重放并缩放到预览尺寸，滤镜在缩小后的像素数组上
 * 计算；保存时按导出预算重新解码并重放完整操作，写入成功后通知缩略图缓存与桌面刷新。
 *
 * 参数 context 为视图上下文；sourcePath 为源图片绝对路径；tokens 为页面尺寸令牌；
 * onFinish 在保存成功或用户放弃修改后调用。
 *
 * 面积超过阈值：布局、绘制、手势与后台任务共享同一编辑状态，拆分会引入跨对象转发，
 * 因此保留单类并抑制 LargeClass。
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class EditSurface(
    context: Context,
    private val sourcePath: String,
    private val tokens: FileTokens,
    private val onFinish: () -> Unit,
) : View(context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    private val worker = Executors.newSingleThreadExecutor()
    private val rotationStore = PrefsRotationStore(context.applicationContext)
    private val session = EditSession(sourcePath, rotationStore)
    private val cropView = CropView(tokens)
    private var state = EditUiState(operationCount = session.size, rotationQuarters = session.rotationCount)

    private var previewSource: Bitmap? = null
    private var geometryBase: Bitmap? = null
    private var display: Bitmap? = null
    private var baseValid = false
    private var loading = true
    private var failed = false
    private var saving = false
    private var chips: List<EditChip> = emptyList()

    private var topBar = IntRect(0, 0, 0, 0)
    private var previewArea = IntRect(0, 0, 0, 0)
    private var controlArea = IntRect(0, 0, 0, 0)
    private var tabs: List<IntRect> = emptyList()
    private var cancelRect = IntRect(0, 0, 0, 0)
    private var saveRect = IntRect(0, 0, 0, 0)

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var dragTarget = CropTarget.OUTSIDE
    private var grayDragging = false
    private var loadStarted = false

    private val paperPaint = Paint().apply { color = InkColors.PAPER }
    private val fillPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
            style = Paint.Style.FILL
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
            style = Paint.Style.STROKE
            strokeWidth = tokens.stroke.toFloat()
        }
    private val bitmapPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK
            textSize = tokens.titleText
        }
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK
            textSize = tokens.bodyText
        }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK
            textSize = tokens.smallText
        }
    private val grayPaint =
        Paint().apply {
            style = Paint.Style.FILL
        }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        layoutChrome(width, height)
        refreshChips()
        if (!loadStarted && width > 0 && height > 0) {
            loadStarted = true
            loadSource()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(InkColors.PAPER)
        if (loading) {
            drawCenteredText(canvas, EditStrings.LOADING)
            drawChrome(canvas)
            return
        }
        if (failed) {
            drawCenteredText(canvas, EditStrings.LOAD_FAILED)
            drawChrome(canvas)
            return
        }
        drawPreview(canvas)
        drawCropOverlay(canvas)
        drawChrome(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> onMove(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                if (grayDragging) {
                    grayDragging = false
                } else if (dragging) {
                    dragging = false
                } else {
                    handleTap(event.x.toInt(), event.y.toInt())
                }
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                grayDragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    /** 处理返回动作，存在未保存修改时先确认。 */
    fun handleBack(): Boolean {
        requestClose()
        return true
    }

    /** 释放显示位图与后台线程；预览源图交由内存回收器释放，避免与在途任务竞争。 */
    fun detach() {
        generation.incrementAndGet()
        worker.shutdownNow()
        display?.recycle()
        display = null
        previewSource = null
        geometryBase = null
    }

    /** 解码源图，按预览区域长边限制尺寸。 */
    private fun loadSource() {
        val edge = PreviewSizing.decodeEdge(previewArea.width, previewArea.height)
        worker.execute {
            val bitmap = runCatching { ImageDecoder.decode(sourcePath, edge) }.getOrNull()
            mainHandler.post {
                loading = false
                if (bitmap == null) {
                    failed = true
                } else {
                    previewSource = bitmap
                    rebuildAll()
                }
                invalidate()
            }
        }
    }

    /** 按会话操作与界面档位重建预览。 */
    private fun rebuildAll() {
        baseValid = false
        rebuild()
    }

    /** 仅按当前档位重建滤镜结果，复用已重放的操作结果。 */
    private fun rebuildFilter() {
        if (previewSource == null || !baseValid) {
            rebuildAll()
            return
        }
        rebuild()
    }

    /** 提交后台预览计算，按代际丢弃过期结果。 */
    private fun rebuild() {
        val source = previewSource ?: return
        val gen = generation.incrementAndGet()
        val reuseBase = baseValid
        val filter = state.filterLevel
        val contrast = state.contrastLevel
        val existingBase = geometryBase
        worker.execute {
            if (source.isRecycled) return@execute
            val base =
                if (reuseBase && existingBase != null && !existingBase.isRecycled) {
                    existingBase
                } else {
                    session.applyTo(source)
                }
            if (base.isRecycled) return@execute
            val result = FilterPanel.bitmap(base, filter, contrast)
            mainHandler.post {
                if (gen != generation.get()) {
                    if (!base.isRecycled && base !== previewSource && base !== geometryBase) base.recycle()
                    if (!result.isRecycled) result.recycle()
                    return@post
                }
                if (base !== geometryBase) {
                    geometryBase?.recycle()
                    geometryBase = base
                    baseValid = true
                }
                display?.recycle()
                display = result
                invalidate()
            }
        }
    }

    /** 计算顶栏、预览区、控制区与底栏矩形，并重建页签与按钮命中区。 */
    private fun layoutChrome(
        width: Int,
        height: Int,
    ) {
        topBar = IntRect(0, 0, width, tokens.topBarH)
        val bottomTop = height - tokens.bottomBarH
        val controlTop = bottomTop - tokens.rowH * CONTROL_ROWS
        controlArea = IntRect(0, controlTop, width, bottomTop)
        previewArea = IntRect(0, tokens.topBarH, width, controlTop)
        tabs =
            (0 until EditTab.entries.size).map { index ->
                val tabWidth = width / EditTab.entries.size
                IntRect(index * tabWidth, bottomTop, (index + 1) * tabWidth, height)
            }
        cancelRect = IntRect(tokens.padH, 0, tokens.padH + tokens.actionButtonWidth, tokens.topBarH)
        saveRect = IntRect(width - tokens.padH - tokens.actionButtonWidth, 0, width - tokens.padH, tokens.topBarH)
    }

    /** 按当前页签生成控制条按钮与状态。 */
    private fun refreshChips() {
        val rows = chipRows()
        val rowHeight = tokens.rowH
        chips =
            rows.flatMapIndexed { rowIndex, row ->
                if (row.isEmpty()) return@flatMapIndexed emptyList()
                val count = row.size
                val available = controlArea.width - tokens.padH * 2 - tokens.gap * (count - 1)
                val cellWidth = (available / count).coerceAtLeast(tokens.touchMin)
                row.mapIndexed { index, chip ->
                    val left = tokens.padH + (cellWidth + tokens.gap) * index
                    val top = controlArea.top + rowIndex * rowHeight
                    chip.copy(rect = IntRect(left, top, left + cellWidth, top + rowHeight))
                }
            }
    }

    /** 返回当前页签的控制条行定义。 */
    private fun chipRows(): List<List<EditChip>> =
        when (state.tab) {
            EditTab.CROP -> CropPanel.rows(state, ::selectAspect, ::selectFrameMode, ::applyCrop, ::resetCrop)
            EditTab.ROTATE -> RotatePanel.rows(::rotate, ::undo)
            EditTab.FILTER -> FilterPanel.rows(state, ::selectFilter, ::selectContrast)
            EditTab.SAVE -> SavePanel.rows(::requestSave)
        }

    /** 选择裁剪纵横比；选择本机尺寸时读取完整屏幕像素并锁定比例。 */
    private fun selectAspect(aspect: CropAspect) {
        if (aspect == CropAspect.NATIVE) {
            val pixels = ScreenSizeProvider.read(context)
            if (!pixels.isValid) {
                toast(EditStrings.NATIVE_UNAVAILABLE)
                return
            }
            val target = NativeTarget(pixels.widthPx, pixels.heightPx)
            state = state.withNativeTarget(target).withFrameMode(FrameMode.CROP)
            state = state.withCrop(centeredCrop(target.ratio))
        } else {
            state = state.withAspect(aspect)
            state = state.withCrop(aspect.ratio?.let(::centeredCrop) ?: NormRect.FULL)
        }
        refreshChips()
        invalidate()
    }

    /** 计算与目标比例一致的居中裁剪框，图片缺失时保持整幅。 */
    private fun centeredCrop(ratio: Float): NormRect {
        val bitmap = display?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        return bitmap?.let {
            val rect = FrameMath.centeredCrop(ratio = ratio, imageWidth = it.width, imageHeight = it.height)
            NormRect(rect.left, rect.top, rect.right, rect.bottom).clamp()
        } ?: NormRect.FULL
    }

    /** 选择本机尺寸处理模式。 */
    private fun selectFrameMode(mode: FrameMode) {
        state = state.withFrameMode(mode)
        refreshChips()
        invalidate()
    }

    /** 重置裁剪选择。 */
    private fun resetCrop() {
        state = state.resetSelection()
        refreshChips()
        invalidate()
    }

    /** 选择滤镜档位并重算预览。 */
    private fun selectFilter(index: Int) {
        state = state.withFilterLevel(index)
        rebuildFilter()
        refreshChips()
        invalidate()
    }

    /** 选择对比度档位并重算预览。 */
    private fun selectContrast(index: Int) {
        state = state.withContrastLevel(index)
        rebuildFilter()
        refreshChips()
        invalidate()
    }

    /** 撤销一步编辑，恢复旋转计数与裁剪选择。 */
    private fun undo() {
        session.undo()
        state =
            state.copy(
                operationCount = session.size,
                rotationQuarters = session.rotationCount,
                crop = NormRect.FULL,
            )
        rebuildAll()
        refreshChips()
        invalidate()
    }

    /** 处理按下，开始裁剪拖动、灰度调整或记录点击位置。 */
    private fun onDown(
        x: Float,
        y: Float,
    ) {
        downX = x
        downY = y
        lastX = x
        lastY = y
        dragging = false
        dragTarget = CropTarget.OUTSIDE
        if (state.tab == EditTab.CROP && !loading && !failed) {
            val bar = grayBarArea()
            val area = imageArea()
            when {
                !bar.isEmpty && bar.contains(x.toInt(), y.toInt()) -> beginGray(x)
                area.isEmpty || !area.contains(x.toInt(), y.toInt()) -> Unit
                else -> beginCropDrag(area, x, y)
            }
        }
    }

    /** 进入灰度拖动并更新当前灰度。 */
    private fun beginGray(x: Float) {
        grayDragging = true
        updateGray(x)
    }

    /** 以按下位置判定裁剪拖动目标。 */
    private fun beginCropDrag(
        area: IntRect,
        x: Float,
        y: Float,
    ) {
        val frame = state.crop.toIntRect(area)
        dragTarget = CropHitTest.hit(x.toInt(), y.toInt(), frame, cropView.handleRadius)
        dragging = dragTarget != CropTarget.OUTSIDE
    }

    /** 拖动手柄、平移裁剪框或调整背景灰度。 */
    private fun onMove(
        x: Float,
        y: Float,
    ) {
        if (grayDragging) {
            updateGray(x)
        } else if (dragging) {
            moveSelection(x, y)
        }
    }

    /** 按拖动目标平移或缩放裁剪框。 */
    private fun moveSelection(
        x: Float,
        y: Float,
    ) {
        val area = imageArea()
        if (area.isEmpty) return
        val dx = (x - lastX) / area.width
        val dy = (y - lastY) / area.height
        lastX = x
        lastY = y
        state =
            if (dragTarget == CropTarget.INSIDE) {
                state.withCrop(CropDrag.move(state.crop, dx, dy))
            } else {
                state.withCrop(
                    CropDrag.resize(
                        state.crop,
                        dragTarget,
                        dx,
                        dy,
                        state.effectiveRatio()?.let {
                            it * area.height /
                                area.width
                        },
                        NormRect.MIN_SIZE,
                    ),
                )
            }
        invalidate()
    }

    /** 处理点击：顶栏、页签、控制条按钮。 */
    private fun handleTap(
        x: Int,
        y: Int,
    ) {
        when {
            cancelRect.contains(x, y) -> requestClose()
            saveRect.contains(x, y) -> requestSave()
            else -> {
                tabs.firstOrNull { it.contains(x, y) }?.let { rect ->
                    val index = tabs.indexOf(rect)
                    state = state.withTab(EditTab.entries[index])
                    refreshChips()
                    invalidate()
                    return
                }
                chips.firstOrNull { it.rect.contains(x, y) }?.let { chip ->
                    chip.action()
                }
            }
        }
    }

    /** 提交当前裁剪选择：本机尺寸下提交本机参数，其余比例提交裁剪框。 */
    private fun applyCrop() {
        val base = geometryBase ?: return
        if (state.aspect == CropAspect.NATIVE && state.pendingFrame != null) {
            applyFrame(base)
        } else {
            commitCrop(base)
        }
    }

    /** 提交普通裁剪框；选区过小则提示并保持原状态。 */
    private fun commitCrop(base: Bitmap) {
        val rect = cropRectOf(state)
        if (CropMath.isLargeEnough(rect, base.width, base.height, CropMath.minEdge(base.width))) {
            session.push(EditOp.Crop(rect))
            state = state.copy(crop = NormRect.FULL, aspect = CropAspect.FREE, operationCount = session.size)
            rebuildAll()
            refreshChips()
            invalidate()
        } else {
            toast(EditStrings.CROP_TOO_SMALL)
        }
    }

    /** 把本机尺寸参数提交为操作；裁剪模式一并提交当前裁剪框。 */
    private fun applyFrame(base: Bitmap) {
        val spec = state.pendingFrame ?: return
        val crop = if (spec.mode == FrameMode.CROP) cropRectOf(state) else null
        val tooSmall =
            crop != null &&
                !CropMath.isLargeEnough(crop, base.width, base.height, CropMath.minEdge(base.width))
        when {
            tooSmall -> toast(EditStrings.CROP_TOO_SMALL)
            !session.push(EditOp.Frame(spec, crop)) -> toast(EditStrings.OPERATION_LIMIT)
            else -> {
                state = state.resetSelection().copy(operationCount = session.size)
                rebuildAll()
                refreshChips()
                invalidate()
            }
        }
    }

    /** 把界面裁剪框转换为数据层矩形。 */
    private fun cropRectOf(source: EditUiState): CropRect =
        CropRect(
            left = source.crop.left,
            top = source.crop.top,
            right = source.crop.right,
            bottom = source.crop.bottom,
        )

    /** 顺时针旋转并清空待应用裁剪框。 */
    private fun rotate() {
        val pushed = session.push(EditOp.Rotate(1))
        if (!pushed) {
            toast(EditStrings.OPERATION_LIMIT)
            return
        }
        state = state.withRotation().copy(operationCount = session.size)
        rebuildAll()
        refreshChips()
        invalidate()
    }

    /** 请求保存，先检查是否有修改。 */
    private fun requestSave() {
        if (saving) return
        if (!EditExportPlan.canSave(state) && !session.canUndo) {
            toast(EditStrings.NOTHING_TO_SAVE)
            return
        }
        InkDialogs.confirm(
            context = context,
            title = EditStrings.SAVE_TITLE,
            message = EditStrings.SAVE_MESSAGE,
            confirmLabel = EditStrings.SAVE_CONFIRM,
            onConfirm = ::save,
        )
    }

    /** 在后台导出完整操作序列，成功后刷新缩略图并关闭页面。 */
    private fun save() {
        saving = true
        invalidate()
        val frameOp = pendingFrameOp()
        val ops = EditExportPlan.ops(session.exportOps(), state.filterLevel, state.contrastLevel, frameOp)
        worker.execute {
            val result =
                runCatching {
                    EditExport.export(context, sourcePath, ops)
                }.getOrElse { error -> ExportResult.Failure(error.message ?: "保存失败") }
            mainHandler.post {
                saving = false
                invalidate()
                when (result) {
                    is ExportResult.Success -> afterSaved()
                    is ExportResult.Failure -> toast(EditStrings.SAVE_FAILED + result.message)
                }
            }
        }
    }

    /** 把尚未应用的本机尺寸选择转换为导出操作，未选择时为空。 */
    private fun pendingFrameOp(): EditOp.Frame? {
        val spec = state.pendingFrame ?: return null
        val crop = if (spec.mode == FrameMode.CROP) cropRectOf(state) else null
        return EditOp.Frame(spec, crop)
    }

    /** 保存成功后的缓存失效、桌面刷新与关闭。 */
    private fun afterSaved() {
        runCatching {
            ThumbCacheRegistry.invalidate(context, File(sourcePath))
            RefreshScheduler.notifyContentChanged()
            AppServices.refresh.requestPartial()
        }
        toast(EditStrings.SAVED)
        onFinish()
    }

    /** 请求关闭，存在未保存修改时先确认。 */
    private fun requestClose() {
        if (saving) return
        if (!state.hasEdits && !session.canUndo) {
            onFinish()
            return
        }
        InkDialogs.confirm(
            context = context,
            title = EditStrings.DISCARD_TITLE,
            message = EditStrings.DISCARD_MESSAGE,
            confirmLabel = EditStrings.DISCARD_CONFIRM,
            onConfirm = onFinish,
        )
    }

    /** 绘制预览位图；本机尺寸的拉伸与背景模式按目标比例显示效果。 */
    private fun drawPreview(canvas: Canvas) {
        val bitmap = display ?: return
        val frame = state.pendingFrame
        if (frame != null && state.tab == EditTab.CROP && frame.mode != FrameMode.CROP) {
            drawFramePreview(canvas, bitmap, frame)
        } else {
            val area = imageArea()
            if (!area.isEmpty) {
                val source = Rect(0, 0, bitmap.width, bitmap.height)
                val destination = Rect(area.left, area.top, area.right, area.bottom)
                canvas.drawBitmap(bitmap, source, destination, bitmapPaint)
            }
        }
    }

    /** 按本机尺寸比例绘制拉伸或背景预览。 */
    private fun drawFramePreview(
        canvas: Canvas,
        bitmap: Bitmap,
        frame: FrameSpec,
    ) {
        val area = frameArea(frame)
        if (area.isEmpty) return
        when (frame.mode) {
            FrameMode.CROP -> Unit
            FrameMode.STRETCH -> {
                val source = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(bitmap, source, Rect(area.left, area.top, area.right, area.bottom), bitmapPaint)
            }
            FrameMode.BACKGROUND -> {
                val gray = frame.safeGray
                grayPaint.color = android.graphics.Color.rgb(gray, gray, gray)
                canvas.drawRect(
                    area.left.toFloat(),
                    area.top.toFloat(),
                    area.right.toFloat(),
                    area.bottom.toFloat(),
                    grayPaint,
                )
                val fit = FrameMath.fit(area.width, area.height, bitmap.width, bitmap.height)
                if (fit.width > 0 && fit.height > 0) {
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(
                            (area.left + fit.left).toFloat(),
                            (area.top + fit.top).toFloat(),
                            (area.left + fit.left + fit.width).toFloat(),
                            (area.top + fit.top + fit.height).toFloat(),
                        ),
                        bitmapPaint,
                    )
                }
            }
        }
    }

    /** 目标比例在预览区内的居中绘制区域。 */
    private fun frameArea(frame: FrameSpec): IntRect {
        val area = previewArea
        if (area.isEmpty || !frame.isValid) return IntRect(0, 0, 0, 0)
        val scale = minOf(area.width.toFloat() / frame.width, area.height.toFloat() / frame.height)
        val width = (frame.width * scale).toInt().coerceAtLeast(1)
        val height = (frame.height * scale).toInt().coerceAtLeast(1)
        val left = area.left + (area.width - width) / 2
        val top = area.top + (area.height - height) / 2
        return IntRect(left, top, left + width, top + height)
    }

    /** 背景灰度滑条区域，仅背景模式显示。 */
    private fun grayBarArea(): IntRect {
        val visible = state.tab == EditTab.CROP && state.pendingFrame?.mode == FrameMode.BACKGROUND
        val area = previewArea
        return if (visible && !area.isEmpty) {
            val top = (area.bottom - tokens.touchMin).coerceAtLeast(area.top)
            IntRect(area.left + tokens.padH, top, area.right - tokens.padH, area.bottom)
        } else {
            IntRect(0, 0, 0, 0)
        }
    }

    /** 按触点横坐标更新灰度，左端为白、右端为黑。 */
    private fun updateGray(x: Float) {
        val bar = grayBarArea()
        if (bar.isEmpty) return
        val trackLeft = bar.left + tokens.padH
        val trackRight = bar.right - tokens.padH
        val range = (trackRight - trackLeft).coerceAtLeast(1)
        val t = ((x - trackLeft) / range).coerceIn(0f, 1f)
        state = state.withFrameGray((FrameMath.MAX_GRAY * (1f - t)).roundToInt())
        invalidate()
    }

    /** 绘制灰度滑条：轨道、游标与当前数值。 */
    private fun drawGrayBar(canvas: Canvas) {
        val bar = grayBarArea()
        if (bar.isEmpty) return
        val centerY = bar.top + bar.height / 2f
        val trackLeft = (bar.left + tokens.padH).toFloat()
        val trackRight = (bar.right - tokens.padH).toFloat()
        canvas.drawLine(trackLeft, centerY, trackRight, centerY, linePaint)
        val gray = FrameMath.clampGray(state.frameGray)
        val t = 1f - gray.toFloat() / FrameMath.MAX_GRAY
        val knobX = trackLeft + (trackRight - trackLeft) * t
        val radius = tokens.dp(6f).toFloat()
        canvas.drawCircle(knobX, centerY, radius, fillPaint)
        drawCenteredIn(
            canvas,
            EditStrings.GRAY_LABEL + gray,
            smallPaint,
            IntRect(bar.left, bar.top, bar.left + tokens.dp(72f), bar.bottom),
        )
    }

    /** 裁剪页绘制暗区、边框与手柄；拉伸与背景模式不显示裁剪框。 */
    private fun drawCropOverlay(canvas: Canvas) {
        val pending = state.pendingFrame
        val allowCrop = pending == null || pending.mode == FrameMode.CROP
        if (state.tab == EditTab.CROP && display != null && allowCrop) {
            val area = imageArea()
            if (!area.isEmpty) cropView.draw(canvas, area, state.crop, state.crop.toIntRect(area))
        }
    }

    /** 绘制顶栏、控制条与底栏。 */
    private fun drawChrome(canvas: Canvas) {
        canvas.drawRect(
            topBar.left.toFloat(),
            topBar.top.toFloat(),
            topBar.right.toFloat(),
            topBar.bottom.toFloat(),
            fillPaint,
        )
        canvas.drawRect(
            topBar.left.toFloat(),
            topBar.bottom.toFloat(),
            topBar.right.toFloat(),
            topBar.bottom + tokens.stroke.toFloat(),
            linePaint,
        )
        val titleRect =
            IntRect(tokens.padH * 4, 0, width - tokens.padH * 4, topBar.height)
        drawCenteredIn(canvas, EditStrings.TITLE, titlePaint, titleRect)
        drawCenteredIn(canvas, EditStrings.CANCEL, bodyPaint, cancelRect)
        val saveColorPaint = if (canSaveNow()) bodyPaint else disabledPaint()
        drawCenteredIn(canvas, EditStrings.SAVE, saveColorPaint, saveRect)
        if (tabs.size != EditTab.entries.size) return
        drawControl(canvas)
        drawGrayBar(canvas)
        drawTabs(canvas)
        if (saving) {
            drawCenteredText(canvas, EditStrings.SAVING)
        }
    }

    /** 绘制控制条按钮与另存页摘要。 */
    private fun drawControl(canvas: Canvas) {
        canvas.drawRect(
            controlArea.left.toFloat(),
            controlArea.top.toFloat(),
            controlArea.right.toFloat(),
            controlArea.bottom.toFloat(),
            paperPaint,
        )
        canvas.drawRect(
            controlArea.left.toFloat(),
            controlArea.top.toFloat(),
            controlArea.right.toFloat(),
            controlArea.top + tokens.stroke.toFloat(),
            linePaint,
        )
        if (state.tab == EditTab.SAVE) {
            val summary =
                EditExportPlan.summary(
                    operationCount = session.size,
                    filterLabel = state.filterLabel,
                    contrastLabel = state.contrastLabel,
                    crop = state.crop,
                    frame = state.pendingFrame,
                )
            val textTop = controlArea.top + tokens.smallText.toInt()
            drawLines(canvas, summary, smallPaint, controlArea.left + tokens.padH, textTop)
        }
        for (chip in chips) {
            if (chip.selected) {
                canvas.drawRect(
                    chip.rect.left.toFloat(),
                    chip.rect.top.toFloat(),
                    chip.rect.right.toFloat(),
                    chip.rect.bottom.toFloat(),
                    fillPaint,
                )
            }
            drawCenteredIn(canvas, chip.label, bodyPaint, chip.rect)
        }
    }

    /** 绘制底部四页签，选中项反白。 */
    private fun drawTabs(canvas: Canvas) {
        EditTab.entries.forEachIndexed { index, tab ->
            val rect = tabs[index]
            if (tab == state.tab) {
                canvas.drawRect(
                    rect.left.toFloat(),
                    rect.top.toFloat(),
                    rect.right.toFloat(),
                    rect.bottom.toFloat(),
                    fillPaint,
                )
            } else {
                canvas.drawLine(
                    rect.left.toFloat(),
                    rect.top.toFloat(),
                    rect.right.toFloat(),
                    rect.top.toFloat(),
                    linePaint,
                )
            }
            drawCenteredIn(canvas, tab.label, bodyPaint, rect)
        }
    }

    /** 当前图片在预览区内的等比绘制区域。 */
    private fun imageArea(): IntRect {
        val bitmap = display
        val area = previewArea
        if (bitmap == null || area.isEmpty) return IntRect(0, 0, 0, 0)
        val scale = minOf(area.width.toFloat() / bitmap.width, area.height.toFloat() / bitmap.height)
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val left = area.left + (area.width - width) / 2
        val top = area.top + (area.height - height) / 2
        return IntRect(left, top, left + width, top + height)
    }

    /** 是否允许保存。 */
    private fun canSaveNow(): Boolean = !loading && !failed && !saving && EditExportPlan.canSave(state)

    /** 绘制居中的单行提示。 */
    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
    ) {
        canvas.drawText(
            text,
            (width - bodyPaint.measureText(text)) / 2f,
            height / 2f,
            bodyPaint,
        )
    }

    /** 在矩形内绘制单行水平垂直居中文字。 */
    private fun drawCenteredIn(
        canvas: Canvas,
        text: String,
        paint: Paint,
        rect: IntRect,
    ) {
        val x = rect.left + (rect.width - paint.measureText(text)) / 2f
        val y = rect.top + (rect.height + paint.textSize) / 2f - paint.descent()
        canvas.drawText(text, x, y, paint)
    }

    /** 绘制多行文本。 */
    private fun drawLines(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Int,
        y: Int,
    ) {
        var baseline = y.toFloat()
        for (line in text.split('\n')) {
            canvas.drawText(line, x.toFloat(), baseline, paint)
            baseline += paint.textSize + tokens.dp(4f)
        }
    }

    /** 禁用态画笔，保持正文颜色体系。 */
    private fun disabledPaint(): Paint =
        Paint(bodyPaint).apply {
            color = InkColors.INK_3
        }

    /** 显示轻提示。 */
    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /** 控制条行数。 */
        const val CONTROL_ROWS = 2
    }
}
