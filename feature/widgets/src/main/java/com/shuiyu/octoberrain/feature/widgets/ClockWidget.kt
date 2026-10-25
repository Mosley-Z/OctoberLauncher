package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import java.util.Calendar

/**
 * 时钟布局形态。
 *
 * AUTO 按组件高度选择布局，STACKED 固定常规布局，COMPACT 固定单行紧凑布局。
 */
internal enum class ClockLayout {
    AUTO,
    STACKED,
    COMPACT,
}

/**
 * 时钟组件：按外观配置的钟面样式绘制时间与日期，每分钟刷新一次。
 *
 * 布局由构造参数决定：无参构造按组件高度自动选择，高度低于 150dp 时采用单行紧凑布局，
 * 其余情况采用常规布局；compact 参数为 true 时固定单行布局，为 false 时固定常规布局。
 * 钟面字体取外观配置中的钟面字体，十二小时制由配置切换。
 */
@Suppress("TooManyFunctions") // 钟面布局、刻度与日期绘制共享同一组几何参数，拆分后传递成本更高。
class ClockWidget
    internal constructor(
        context: Context,
        private val layout: ClockLayout,
        private val fixedFace: ClockFace? = null,
    ) : InkWidgetView(context) {
        /** 自动选择布局的时钟，组件清单使用该构造。 */
        constructor(context: Context) : this(context, ClockLayout.AUTO)

        /** 组件库中的六款独立条目固定钟面，不改旧时钟的外观配置。 */
        constructor(context: Context, face: ClockFace) : this(context, ClockLayout.AUTO, face)

        /** 兼容既有调用点的构造，compact 为 true 时固定单行布局。 */
        constructor(
            context: Context,
            compact: Boolean,
        ) : this(context, if (compact) ClockLayout.COMPACT else ClockLayout.STACKED)

        /**
         * 时钟组件使用透明背景，宿主跳过纸白底板与圆角描边，壁纸直接透过组件区域。
         *
         * 常规与紧凑两种布局均由同一视图绘制，布局差异仅体现在几何排布上。
         */
        override val transparentBackground: Boolean get() = true

        private val handler = Handler(Looper.getMainLooper())
        private var ticking = false

        /** 每分钟触发一次重绘的定时任务。 */
        private val tick =
            object : Runnable {
                override fun run() {
                    requestDraw()
                    handler.postDelayed(this, millisToNextMinute())
                }
            }

        override fun onWidgetSizeChanged(
            width: Int,
            height: Int,
        ) {
            super.onWidgetSizeChanged(width, height)
            startTicker()
        }

        override fun onWidgetConfig(config: LauncherConfig) {
            startTicker()
        }

        /** 启动每分钟刷新，已在运行时直接返回。 */
        private fun startTicker() {
            if (!hostVisible || ticking) return
            ticking = true
            handler.postDelayed(tick, millisToNextMinute())
        }

        override fun onHostVisible(visible: Boolean) {
            super.onHostVisible(visible)
            if (visible) {
                startTicker()
            } else {
                ticking = false
                handler.removeCallbacks(tick)
            }
        }

        override fun onWidgetDestroy() {
            ticking = false
            handler.removeCallbacks(tick)
        }

        /** 距离下一分钟开始还有多少毫秒。 */
        private fun millisToNextMinute(): Long {
            val now = System.currentTimeMillis()
            return MINUTE_MS - now % MINUTE_MS
        }

        override fun drawWidget(
            canvas: Canvas,
            width: Int,
            height: Int,
        ) {
            val now = Calendar.getInstance()
            val appearance = config.appearance
            val face = fixedFace ?: ClockFace.fromId(appearance.clockFace)
            val typeface = clockTypeface(face, appearance.clockFont)
            val time = ClockTime.from(now, appearance.twelveHour)
            val date = dateText(now)

            val left = padX
            val right = width - padX
            val top = pad
            val bottom = height - pad
            if (right <= left || bottom <= top) return

            if (face.includesDate) {
                val paint = DesignRuntime.textPaint(tokens.textHeading, InkColors.INK, typeface)
                paint.textScaleX = clockNumberScale(face, appearance.clockFont)
                ClockFaces.draw(face, canvas, RectF(left, top, right, bottom), time, paint, paint.textSize)
            } else {
                val datePaint = textPaint(tokens.textBody, InkColors.INK_2)
                if (effectiveLayout() == ClockLayout.COMPACT) {
                    drawCompact(canvas, face, time, date, left, right, top, bottom, typeface, datePaint)
                } else {
                    drawStackedLayout(canvas, face, time, date, left, right, top, bottom, height, typeface, datePaint)
                }
            }
        }

        /**
         * 按构造参数与组件高度确定实际布局。
         *
         * 自动布局在组件高度低于 150dp 时采用单行紧凑布局，其余情况采用常规布局；
         * 显式指定布局时直接返回构造参数。
         *
         * @return 本次绘制采用的布局形态
         */
        private fun effectiveLayout(): ClockLayout =
            when (layout) {
                ClockLayout.AUTO -> if (tight) ClockLayout.COMPACT else ClockLayout.STACKED
                else -> layout
            }

        /**
         * 单行布局：钟面与日期作为一整行，以组件宽度一半为基准水平居中。
         *
         * 日期先按可用宽度的比例取测量宽度，钟面在剩余宽度内自适应收缩字号；
         * 两者实际宽度之和与左起点均由中心反推，因此字号变化后整行仍保持居中。
         */
        private fun drawCompact(
            canvas: Canvas,
            face: ClockFace,
            time: ClockTime,
            date: String,
            left: Float,
            right: Float,
            top: Float,
            bottom: Float,
            typeface: android.graphics.Typeface,
            datePaint: android.graphics.Paint,
        ) {
            val centerX = (left + right) / 2f
            val centerY = (top + bottom) / 2f
            val rowLimit = right - left
            if (rowLimit <= 0f) return
            val gap = pad

            // 日期先占可用宽度的至多一半，剩余宽度交给钟面；极窄时给钟面保留四分之一
            var dateWidth = datePaint.measureText(date).coerceAtMost(rowLimit * 0.5f)
            var timeLimit = rowLimit - gap - dateWidth
            if (timeLimit < rowLimit * 0.25f) {
                timeLimit = rowLimit * 0.25f
                dateWidth = (rowLimit - gap - timeLimit).coerceAtLeast(0f)
            }
            val dateText = if (dateWidth > 0f) InkDraw.ellipsize(date, datePaint, dateWidth) else ""
            dateWidth = if (dateText.isEmpty()) 0f else datePaint.measureText(dateText)

            val showDate = dateText.isNotEmpty() && dateWidth > 0f
            val dateExtra = if (showDate) gap + dateWidth else 0f

            var timeWidth: Float
            if (face == ClockFace.DEFAULT) {
                val desired = minOf(tokens.textSize(40f), (bottom - top) * 0.86f)
                val paint = DesignRuntime.textPaint(desired, InkColors.INK, typeface)
                InkDraw.fitSize(time.timeText, paint, timeLimit, desired, tokens.textCaption)
                timeWidth = paint.measureText(time.timeText)
                val lineHeight = InkDraw.lineHeight(paint)
                val rowLeft = centerX - (timeWidth + dateExtra) / 2f
                ClockFaces.drawDefault(
                    canvas,
                    RectF(rowLeft, centerY - lineHeight / 2f, rowLeft + timeWidth, centerY + lineHeight / 2f),
                    time,
                    paint,
                    paint.textSize,
                )
                if (showDate) {
                    drawCompactDate(canvas, dateText, datePaint, rowLeft + timeWidth + gap, centerY)
                }
                return
            }

            timeWidth = timeLimit
            val baseSize = minOf(tokens.textSize(40f), (bottom - top) * 0.82f)
            val paint = DesignRuntime.textPaint(baseSize, InkColors.INK, typeface)
            val rowLeft = centerX - (timeWidth + dateExtra) / 2f
            if (face == ClockFace.ANALOG) {
                // 指针样式在钟面块内取最大正方形，表盘中心与块中心重合
                val diameter = minOf(bottom - top, timeWidth)
                val dial =
                    RectF(
                        rowLeft + (timeWidth - diameter) / 2f,
                        centerY - diameter / 2f,
                        rowLeft + (timeWidth + diameter) / 2f,
                        centerY + diameter / 2f,
                    )
                ClockFaces.drawAnalog(canvas, dial, time, paint, baseSize)
            } else {
                ClockFaces.draw(face, canvas, RectF(rowLeft, top, rowLeft + timeWidth, bottom), time, paint, baseSize)
            }
            if (showDate) {
                drawCompactDate(canvas, dateText, datePaint, rowLeft + timeWidth + gap, centerY)
            }
        }

        /** 在单行布局中绘制日期：左对齐起点，垂直方向以行中心对齐。 */
        private fun drawCompactDate(
            canvas: Canvas,
            date: String,
            paint: android.graphics.Paint,
            left: Float,
            centerY: Float,
        ) {
            val lineHeight = InkDraw.lineHeight(paint)
            InkDraw.text(canvas, date, left, centerY - lineHeight / 2f, paint)
        }

        /** 常规布局：钟面占据日期上方的区域，日期居中显示在底部。 */
        private fun drawStackedLayout(
            canvas: Canvas,
            face: ClockFace,
            time: ClockTime,
            date: String,
            left: Float,
            right: Float,
            top: Float,
            bottom: Float,
            height: Int,
            typeface: android.graphics.Typeface,
            datePaint: android.graphics.Paint,
        ) {
            val baseSize = minOf(tokens.textSize(72f), height * 0.64f)
            val dateHeight = InkDraw.lineHeight(datePaint)
            val available = bottom - top
            if (available <= 0f) return
            val centerX = (left + right) / 2f

            if (face == ClockFace.DEFAULT) {
                // 大字样式：按可用高度预留日期与间距，逐级收窄字号后以宽度一半为基准整体居中
                val paint = DesignRuntime.textPaint(tokens.textSize(72f), InkColors.INK, typeface)
                val start = minOf(tokens.textSize(72f), available * 0.72f)
                var size = start
                var lineHeight = 0f
                while (true) {
                    paint.textSize = size
                    InkDraw.fitSize(time.timeText, paint, right - left, size, tokens.textCaption)
                    lineHeight = InkDraw.lineHeight(paint)
                    if (lineHeight + pad + dateHeight <= available || size <= tokens.textCaption) break
                    size = maxOf(tokens.textCaption, size - maxOf(1f, start * 0.06f))
                }
                val block = lineHeight + pad + dateHeight
                val textWidth = paint.measureText(time.timeText)
                var y = (top + bottom - block) / 2f
                if (y < top) y = top
                // 字号收缩后重新按文本实际宽度计算起点，保证时间文本居中而不是靠左
                val textLeft = centerX - textWidth / 2f
                ClockFaces.drawDefault(
                    canvas,
                    RectF(textLeft, y, textLeft + textWidth, y + lineHeight),
                    time,
                    paint,
                    paint.textSize,
                )
                y += lineHeight + pad
                InkDraw.textCenter(canvas, date, centerX, y, datePaint)
                return
            }

            val timeBottom = bottom - dateHeight - pad
            if (timeBottom <= top) return
            val timeArea = RectF(left, top, right, timeBottom)
            val paint = DesignRuntime.textPaint(baseSize, InkColors.INK, typeface)
            if (face == ClockFace.ANALOG) {
                // 指针样式使用区域内最大的正方形，表盘保持正圆
                val diameter = minOf(timeArea.width(), timeArea.height())
                val dial =
                    RectF(
                        timeArea.centerX() - diameter / 2f,
                        timeArea.centerY() - diameter / 2f,
                        timeArea.centerX() + diameter / 2f,
                        timeArea.centerY() + diameter / 2f,
                    )
                ClockFaces.drawAnalog(canvas, dial, time, paint, baseSize)
            } else {
                ClockFaces.draw(face, canvas, timeArea, time, paint, baseSize)
            }
            InkDraw.textCenter(canvas, date, centerX, bottom - dateHeight, datePaint)
        }

        /** 格式化日期与星期文本。 */
        private fun dateText(now: Calendar): String {
            val month = now.get(Calendar.MONTH) + 1
            val day = now.get(Calendar.DAY_OF_MONTH)
            val weekday = WEEKDAYS[now.get(Calendar.DAY_OF_WEEK) - 1]
            return "${month}月${day}日 周$weekday"
        }

        private companion object {
            /** 一分钟的毫秒数。 */
            const val MINUTE_MS = 60_000L

            /** 星期名称，索引与 Calendar.DAY_OF_WEEK 对应。 */
            val WEEKDAYS = arrayOf("日", "一", "二", "三", "四", "五", "六")
        }
    }
