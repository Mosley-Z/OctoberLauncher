package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.forWidgetPage
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.render.InkCanvas
import com.shuiyu.octoberrain.core.widget.BaseWidgetView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 组件视图基类：统一处理数据加载、重绘请求与绘制裁剪。
 *
 * 子类实现 [loadData] 与 [drawWidget]；数据在 IO 线程读取，读取完成后回到主线程请求宿主重绘。
 * 尺寸变化只触发一次加载，配置变化与强制刷新会重新读取。
 */
abstract class InkWidgetView(
    context: Context,
) : BaseWidgetView(context) {
    /** 组件内部协程作用域，供子类处理选图等异步流程，随视图销毁取消。 */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reloadJob: Job? = null
    private var reloadPending = false
    private var reloadForced = false
    private var configSnapshot: LauncherConfig? = null
    private var dataLoaded = false
    private var loadFailed = false
    private var released = false

    @Volatile protected var hostVisible = true
        private set

    override fun onHostVisible(visible: Boolean) {
        hostVisible = visible
    }

    /** 当前配置；尚未收到配置回调时读取全局快照。 */
    protected val config: LauncherConfig
        get() =
            configSnapshot
                ?: if (AppServices.isReady()) AppServices.config.state.value else LauncherConfig()

    /** 从全局快照重新读取配置，供组件自身写入配置后立即生效。 */
    protected fun refreshConfigSnapshot() {
        if (AppServices.isReady()) {
            configSnapshot =
                AppServices.config.state.value
                    .let { it.copy(desktop = it.desktop.forWidgetPage(pageIndex)) }
        }
    }

    /** 组件内容区的纵向内边距。 */
    protected val pad: Float
        get() = tokens.gap.toFloat()

    /** 组件内容区的左右内边距，不小于 12dp。 */
    protected val padX: Float
        get() = maxOf(tokens.gapLarge.toFloat(), tokens.dpF(12f))

    /** 高度紧张时压缩次要信息。 */
    protected val tight: Boolean
        get() = heightPx < tokens.dp(150f)

    final override fun onSizeChanged(
        width: Int,
        height: Int,
    ) {
        onWidgetSizeChanged(width, height)
    }

    /** 尺寸变化回调，默认在首次获得尺寸时触发一次加载。 */
    protected open fun onWidgetSizeChanged(
        width: Int,
        height: Int,
    ) {
        if (!dataLoaded) reload()
    }

    final override fun onConfigChanged(config: LauncherConfig) {
        configSnapshot = config
        onWidgetConfig(config)
        reload(force = true)
    }

    /** 配置变化回调，子类可在此重新排版。 */
    protected open fun onWidgetConfig(config: LauncherConfig) = Unit

    final override fun onRefresh(force: Boolean) {
        if (dataLoaded && !force) return
        reload(force)
    }

    /**
     * 加载组件数据，返回 true 表示数据可绘制。
     *
     * 参数 force 为 true 时忽略缓存，直接读取最新数据。
     */
    protected open suspend fun loadData(force: Boolean): Boolean = true

    /** 重新读取数据并请求重绘，加载期间的后续请求合并为一次补读，避免快速切图丢失最后状态。 */
    protected fun reload(force: Boolean = false) {
        if (released) return
        reloadForced = reloadForced || force
        if (reloadJob?.isActive == true) {
            reloadPending = true
            return
        }
        reloadJob =
            scope.launch {
                do {
                    reloadPending = false
                    val requestedForce = reloadForced
                    reloadForced = false
                    val result = runCatching { withContext(Dispatchers.IO) { loadData(requestedForce) } }
                    if (released) return@launch
                    result
                        .onSuccess {
                            dataLoaded = true
                            loadFailed = false
                            requestDraw()
                        }.onFailure {
                            loadFailed = true
                            requestDraw()
                            InkLog.w("组件", "加载组件数据失败", it)
                        }
                } while (reloadPending && !released)
            }
    }

    final override fun onDraw(canvas: Canvas) {
        if (widthPx <= 0 || heightPx <= 0) return
        canvas.save()
        canvas.clipRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        when {
            loadFailed -> drawHint(canvas, "--")
            !dataLoaded -> drawHint(canvas, "加载中")
            else -> drawWidget(canvas, widthPx, heightPx)
        }
        canvas.restore()
    }

    /** 绘制加载中与读取失败的居中提示。 */
    private fun drawHint(
        canvas: Canvas,
        message: String,
    ) {
        val paint = textPaint(tokens.textCaption, InkColors.INK_3)
        InkDraw.textCenter(
            canvas,
            message,
            widthPx / 2f,
            heightPx / 2f - InkDraw.lineHeight(paint) / 2f,
            paint,
        )
    }

    /**
     * 在组件可用区域内绘制。
     *
     * 参数 width 与 height 为可用宽高像素，坐标系原点位于组件左上角。
     */
    protected abstract fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    )

    /** 打开内置页面。 */
    protected fun navigateTo(destination: Destination) {
        val target =
            if (destination ==
                Destination.WidgetPicker
            ) {
                Destination.PageWidgetPicker(pageIndex)
            } else {
                destination
            }
        if (AppServices.hasRouter()) AppServices.router.navigate(target)
    }

    /** 展示轻提示。 */
    protected fun notify(message: String) {
        if (AppServices.hasRouter()) AppServices.router.toast(message)
    }

    /** 绘制统一的空状态文案，居中显示于给定区域。 */
    protected fun drawEmptyState(
        canvas: Canvas,
        area: RectF,
        message: String,
    ) {
        if (area.width() <= 0f || area.height() <= 0f) return
        val paint = textPaint(tokens.textCaption, InkColors.INK_2)
        InkDraw.textCenter(canvas, message, area.centerX(), area.centerY() - InkDraw.lineHeight(paint) / 2f, paint)
    }

    /** 绘制一条水平分隔线。 */
    protected fun drawHairline(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float,
    ) {
        InkCanvas.drawHairline(canvas, left, right, y, strokePaint(InkColors.INK_4))
    }

    /**
     * 长按事件：先交由子类按命中区域处理，未消费时打开组件选择页。
     *
     * @param x 局部坐标横轴，坐标未知时为 NaN
     * @param y 局部坐标纵轴，坐标未知时为 NaN
     */
    override fun onLongPress(
        x: Float,
        y: Float,
    ) {
        if (handleLongPress(x, y)) return
        navigateTo(Destination.WidgetPicker)
    }

    /**
     * 子类处理带坐标的长按事件。
     *
     * @param x 局部坐标横轴，坐标未知时为 NaN
     * @param y 局部坐标纵轴，坐标未知时为 NaN
     * @return 返回 true 表示事件已消费，不需要打开组件选择页
     */
    protected open fun handleLongPress(
        x: Float,
        y: Float,
    ): Boolean = false

    final override fun onDestroy() {
        released = true
        reloadJob?.cancel()
        scope.cancel()
        onWidgetDestroy()
    }

    /** 释放子类持有的位图等资源。 */
    protected open fun onWidgetDestroy() = Unit
}

/**
 * 组件绘制辅助函数：文本对齐、截断、折行与虚线框。
 *
 * 所有函数只读取传入的画笔，不修改画布状态，绘制代码可按需组合。
 */
internal object InkDraw {
    /** 单行文本的行高。 */
    fun lineHeight(paint: Paint): Float = paint.fontMetrics.descent - paint.fontMetrics.ascent

    /** 绘制顶部对齐的一行文本，返回下一行顶部的纵坐标。 */
    fun text(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        paint: Paint,
    ): Float {
        canvas.drawText(text, x, top - paint.fontMetrics.ascent, paint)
        return top + lineHeight(paint)
    }

    /** 绘制顶部对齐的右端文本。 */
    fun textRight(
        canvas: Canvas,
        text: String,
        right: Float,
        top: Float,
        paint: Paint,
    ) {
        canvas.drawText(text, right - paint.measureText(text), top - paint.fontMetrics.ascent, paint)
    }

    /** 绘制顶部对齐的居中文本。 */
    fun textCenter(
        canvas: Canvas,
        text: String,
        centerX: Float,
        top: Float,
        paint: Paint,
    ) {
        canvas.drawText(
            text,
            centerX - paint.measureText(text) / 2f,
            top - paint.fontMetrics.ascent,
            paint,
        )
    }

    /** 以给定纵坐标为中心基线绘制居中文本。 */
    fun textCenterBaseline(
        canvas: Canvas,
        text: String,
        centerX: Float,
        centerY: Float,
        paint: Paint,
    ) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, centerX - paint.measureText(text) / 2f, baseline, paint)
    }

    /** 按宽度截断文本并追加省略号。 */
    fun ellipsize(
        text: String,
        paint: Paint,
        maxWidth: Float,
    ): String {
        if (text.isEmpty()) return text
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) + ellipsisWidth > maxWidth) {
            end--
        }
        return if (end <= 0) "" else text.substring(0, end) + ellipsis
    }

    /** 缩小字号直到文本宽度不超过上限。 */
    fun fitSize(
        text: String,
        paint: Paint,
        maxWidth: Float,
        desired: Float,
        minimum: Float,
    ): Float {
        if (maxWidth <= 0f) return minimum
        var size = desired
        paint.textSize = size
        while (size > minimum && paint.measureText(text) > maxWidth) {
            size = maxOf(minimum, size - maxOf(1f, desired * 0.04f))
            paint.textSize = size
        }
        return size
    }

    /** 按宽度折行，最多保留行数，超出部分以省略号结尾。 */
    fun wrap(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int,
    ): List<String> {
        val lines = mutableListOf<String>()
        if (text.isEmpty() || maxWidth <= 0f || maxLines <= 0) return lines
        var start = 0
        while (start < text.length && lines.size < maxLines) {
            var end = start + 1
            var lastFit = start + 1
            while (end <= text.length && paint.measureText(text, start, end) <= maxWidth) {
                lastFit = end
                end++
            }
            if (lastFit <= start) lastFit = start + 1
            var line = text.substring(start, lastFit)
            val exhausted = lastFit >= text.length
            if (!exhausted && lines.size == maxLines - 1) {
                line = ellipsize(text.substring(start), paint, maxWidth)
                lines.add(line)
                return lines
            }
            lines.add(line.trimEnd())
            start = lastFit
        }
        return lines
    }

    /** 绘制虚线圆角矩形边框。 */
    fun dashedRect(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        paint: Paint,
    ) {
        val dash = DashPathEffect(floatArrayOf(paint.strokeWidth * 4f, paint.strokeWidth * 3f), 0f)
        paint.style = Paint.Style.STROKE
        paint.pathEffect = dash
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.pathEffect = null
    }
}
