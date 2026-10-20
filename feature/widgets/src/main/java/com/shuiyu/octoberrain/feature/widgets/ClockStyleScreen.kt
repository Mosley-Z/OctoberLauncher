package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenProvider
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.util.Calendar

/**
 * 钟面样式选择页。
 *
 * 页面按当前配置列出全部钟面样式，每项以约 120dp 的方形区域实时预览，
 * 点击或按 ENTER 写入外观配置，键盘上下键移动焦点，返回键交宿主处理。
 */
class ClockStyleScreen : Screen {
    private var list: ClockStylePreviewList? = null

    override fun createView(context: Context): View {
        val config = AppServices.config.state.value
        val current = ClockFace.fromId(config.appearance.clockFace)
        val page =
            InkPage(context, "钟面样式", showBack = true) {
                AppServices.router.back()
            }
        page.content.addView(
            InkWidgets.caption(context, "选择桌面时钟的钟面样式。上下键移动焦点，ENTER 确认，返回键退出。"),
        )
        page.content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gap))
        val previewList =
            ClockStylePreviewList(
                context = context,
                currentFace = current,
                twelveHour = config.appearance.twelveHour,
            ) { face -> select(face) }
        previewList.layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        page.content.addView(previewList)
        list = previewList
        previewList.post { previewList.requestFocus() }
        return page
    }

    override fun onResume() {
        syncSelection()
    }

    override fun onPause() {
    }

    override fun onDestroy() {
        list = null
    }

    /** 把配置中的钟面样式同步到列表选中状态。 */
    private fun syncSelection() {
        val face = ClockFace.fromId(AppServices.config.state.value.appearance.clockFace)
        list?.updateSelection(face)
    }

    /** 写入钟面样式并请求局部重绘。 */
    private fun select(face: ClockFace) {
        val current = AppServices.config.state.value.appearance.clockFace
        if (current != face.id) {
            AppServices.config.edit { config ->
                config.copy(appearance = config.appearance.copy(clockFace = face.id))
            }
            AppServices.refresh.requestPartial()
        }
        list?.updateSelection(face)
    }

    companion object {
        /** 注册到页面注册表的选择页提供者。 */
        val Provider: ScreenProvider =
            object : ScreenProvider {
                override fun create(destination: Destination): Screen? =
                    if (destination ==
                        Destination.ClockStyle
                    ) {
                        ClockStyleScreen()
                    } else {
                        null
                    }
            }
    }
}

/**
 * 钟面样式预览列表。
 *
 * 每一项由左侧方形预览与右侧名称组成，预览直接调用 [ClockFaces] 的绘制函数，
 * 与桌面时钟组件共用同一套绘制结果。当前配置项绘制墨线外框，
 * 键盘焦点项以名称前的箭头与深色文字区分。
 */
internal class ClockStylePreviewList(
    context: Context,
    currentFace: ClockFace,
    private val twelveHour: Boolean,
    private val onSelect: (ClockFace) -> Unit,
) : View(context) {
    private val faces = ClockFace.entries
    private val tokens = DesignRuntime.tokens
    private val previewSize = tokens.dp(PREVIEW_SIZE_DP)
    private val rowGap = maxOf(tokens.gap, tokens.dp(4f))
    private val rowHeight = previewSize + rowGap
    private var currentFace = currentFace
    private var focusIndex = faces.indexOf(currentFace).coerceAtLeast(0)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = "钟面样式列表"
    }

    /** 更新当前配置项并同步焦点位置。 */
    fun updateSelection(face: ClockFace) {
        currentFace = face
        val index = faces.indexOf(face)
        if (index >= 0) focusIndex = index
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = faces.size * rowHeight + rowGap
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty()) return
        val widthPx = width.toFloat()
        val now = ClockTime.from(Calendar.getInstance(), twelveHour)
        val previewPaint = DesignRuntime.textPaint(previewSize * PREVIEW_TEXT_RATIO, InkColors.INK)
        val labelPaint = DesignRuntime.textPaint(tokens.textBody, InkColors.INK_2)
        val focusedLabelPaint = DesignRuntime.textPaint(tokens.textBody, InkColors.INK)
        val idPaint = DesignRuntime.textPaint(tokens.textCaption, InkColors.INK_3)
        val markPaint = DesignRuntime.textPaint(tokens.textCaption, InkColors.INK)

        for ((index, face) in faces.withIndex()) {
            val rowTop = rowGap / 2f + index * rowHeight
            val rowBottom = rowTop + previewSize
            val preview = RectF(0f, rowTop, previewSize.toFloat(), rowBottom)
            drawPreviewFrame(canvas, preview, face)
            val innerInset = maxOf(1f, tokens.dp(4f).toFloat())
            val dial = RectF(preview).apply { inset(innerInset, innerInset) }
            ClockFaces.draw(
                face = face,
                canvas = canvas,
                area = dial,
                time = now,
                paint = previewPaint,
                baseSize = previewSize * PREVIEW_TEXT_RATIO,
            )

            val focused = index == focusIndex
            val isCurrent = face == currentFace
            val textPaint = if (focused) focusedLabelPaint else labelPaint
            val textLeft = preview.right + tokens.gap
            val markWidth = if (isCurrent) markPaint.measureText(CURRENT_MARK) + tokens.gap else 0f
            val labelWidth = (widthPx - textLeft - markWidth - tokens.gap).coerceAtLeast(0f)
            val lineHeight = InkDraw.lineHeight(textPaint)
            val idHeight = InkDraw.lineHeight(idPaint)
            val blockTop = rowTop + (previewSize - lineHeight - idHeight) / 2f
            val labelText = if (focused) "› ${face.label}" else "  ${face.label}"
            val clipped = InkDraw.ellipsize(labelText, textPaint, labelWidth)
            InkDraw.text(canvas, clipped, textLeft, blockTop, textPaint)
            InkDraw.text(canvas, face.id, textLeft, blockTop + lineHeight, idPaint)
            if (isCurrent) {
                InkDraw.textRight(canvas, CURRENT_MARK, widthPx - tokens.gap, blockTop, markPaint)
            }
        }
    }

    /** 绘制单项预览外框：当前项使用墨线，其余项使用浅灰细线。 */
    private fun drawPreviewFrame(
        canvas: Canvas,
        preview: RectF,
        face: ClockFace,
    ) {
        val selected = face == currentFace
        val paint =
            Paint().apply {
                style = Paint.Style.STROKE
                color = if (selected) InkColors.INK else InkColors.INK_4
                strokeWidth = maxOf(1f, tokens.stroke * if (selected) 2f else 1f)
            }
        val half = paint.strokeWidth / 2f
        val frame = RectF(preview).apply { inset(half, half) }
        canvas.drawRect(frame, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val index = ((event.y - rowGap / 2f) / rowHeight).toInt()
                if (index in faces.indices) {
                    focusIndex = index
                    invalidate()
                    performClick()
                    onSelect(faces[index])
                }
                return true
            }
            else -> return super.onTouchEvent(event)
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveFocus(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveFocus(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (focusIndex in faces.indices) onSelect(faces[focusIndex])
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }

    /** 移动键盘焦点，并请求父级滚动容器把焦点行带入可视区域。 */
    private fun moveFocus(delta: Int) {
        val next = (focusIndex + delta).coerceIn(faces.indices)
        if (next == focusIndex) return
        focusIndex = next
        invalidate()
        val top = (rowGap / 2f + focusIndex * rowHeight).toInt()
        requestRectangleOnScreen(
            Rect(0, top, width, (top + rowHeight).toInt()),
            true,
        )
    }

    private companion object {
        /** 预览方形的边长，单位为 dp。 */
        const val PREVIEW_SIZE_DP = 120f

        /** 预览内文本字号相对预览边长的比例。 */
        const val PREVIEW_TEXT_RATIO = 0.32f

        /** 当前配置项右侧标记文本。 */
        const val CURRENT_MARK = "当前"
    }
}
