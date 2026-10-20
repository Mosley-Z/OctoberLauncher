package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.MinuteTicker
import com.shuiyu.octoberrain.core.common.RefreshPolicy
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.common.TimeUtils
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs

/**
 * 桌面状态栏：左侧刷新环与时间，右侧电量百分比与电池图标，背景透明。
 *
 * 文字与图标带白色描边，壁纸在状态栏区域可见，内容在花纹上仍可辨认；
 * 刷新环与时间点击触发整屏闪烁刷新，中段空白区域点击展开快捷托盘。
 * 尺寸全部取自设计令牌。
 */
class StatusBarView(
    context: Context,
) : FrameLayout(context) {
    private val timeView: HaloTextView

    private val batteryView: HaloTextView

    private val chargeMark: View

    private val ticker = MinuteTicker { refresh() }

    init {
        // 透明背景，壁纸直接透出；不再绘制底部分隔线
        setBackgroundColor(Color.TRANSPARENT)
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(tokens.pageGutter, 0, tokens.pageGutter, 0)
            }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 左段：刷新环与时间
        row.addView(glyphButton(context, InkGlyphs.Kind.REFRESH) { fullRefresh() })
        row.addView(hSpace(context, tokens.dp(4f)))
        timeView =
            haloText(context, tokens.textStatus).apply {
                isClickable = true
                isFocusable = true
                maxLines = 1
                // 十二小时制文案更宽，以令牌宽度为下限，避免与右侧电量重叠
                minWidth = tokens.statusTimeWidth
                setOnClickListener { fullRefresh() }
            }
        row.addView(
            timeView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        // 中段：留白区域点击展开托盘
        row.addView(
            View(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                isClickable = true
                isFocusable = true
                setOnClickListener { openTray() }
            },
        )

        // 右段：电量百分比、充电闪电与电池图标
        batteryView = haloText(context, tokens.textStatus)
        row.addView(
            batteryView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        // 充电闪电：实心符号，仅在充电时显示，尺寸略小于电池图标
        val boltSize = (tokens.statusIcon * 0.8f).toInt().coerceAtLeast(1)
        chargeMark =
            InkGlyphs.view(context, InkGlyphs.Kind.BOLT, boltSize, Color.WHITE).apply {
                visibility = View.GONE
            }
        row.addView(
            chargeMark,
            LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    marginStart = tokens.dp(2f)
                    gravity = Gravity.CENTER_VERTICAL
                },
        )
        row.addView(hSpace(context, tokens.dp(3f)))
        row.addView(
            InkGlyphs.view(context, InkGlyphs.Kind.BATTERY, tokens.statusIcon, Color.WHITE),
            LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { gravity = Gravity.CENTER_VERTICAL },
        )
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ticker.start()
        refresh()
    }

    override fun onDetachedFromWindow() {
        ticker.stop()
        super.onDetachedFromWindow()
    }

    /** 重新读取外观配置与电量状态并刷新显示。 */
    fun refresh() {
        if (!DesignRuntime.isReady()) return
        val config = runCatching { AppServices.config.state.value }.getOrNull()
        val twelveHour = config?.appearance?.twelveHour ?: false
        timeView.text = TimeUtils.currentTime(twelveHour)

        val battery = runCatching { AppServices.system.battery.value }.getOrNull()
        val showPercent = config?.tray?.showBatteryPercent != false
        batteryView.text = if (showPercent && battery != null) "${battery.percent}%" else ""
        batteryView.visibility = if (batteryView.text.isEmpty()) View.GONE else View.VISIBLE
        chargeMark.visibility = if (battery?.charging == true) View.VISIBLE else View.GONE
    }

    /** 以手动场景触发一次整屏闪烁刷新。 */
    private fun fullRefresh() {
        val policy = runCatching { AppServices.refresh as? RefreshPolicy }.getOrNull()
        if (policy != null) {
            runCatching { policy.requestFullRefresh(RefreshReason.MANUAL) }
        } else {
            runCatching { AppServices.refresh.requestFullRefresh() }
        }
    }

    /** 展开快捷托盘；托盘被关闭时给出提示。 */
    private fun openTray() {
        val enabled = runCatching { AppServices.config.state.value.tray.enabled }.getOrDefault(true)
        if (!enabled) {
            runCatching { AppServices.router.toast("快捷托盘已在设置中关闭") }
            return
        }
        runCatching { AppServices.tray.open() }
    }

    /** 构造可点击图标按钮，触控区取令牌触控高度，图标带白色描边。 */
    private fun glyphButton(
        context: Context,
        kind: InkGlyphs.Kind,
        onClick: () -> Unit,
    ): FrameLayout {
        val tokens = DesignRuntime.tokens
        return FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(tokens.trayTouch, tokens.trayTouch)
            addView(
                InkGlyphs.view(context, kind, tokens.statusIcon, Color.WHITE),
                LayoutParams(tokens.statusIcon, tokens.statusIcon, Gravity.CENTER),
            )
        }
    }

    /** 固定宽度的横向占位。 */
    private fun hSpace(
        context: Context,
        width: Int,
    ): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }

    /** 创建状态栏文字视图：字号取令牌，正文黑色，白色描边由视图自身绘制。 */
    private fun haloText(
        context: Context,
        sizePx: Float,
    ): HaloTextView =
        HaloTextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
            setTextColor(InkColors.INK)
            typeface = DesignRuntime.typeface()
            gravity = Gravity.CENTER_VERTICAL
        }
}

/**
 * 带白色描边的文本视图。
 *
 * 先按行以白色描边绘制文字轮廓，再交由 TextView 绘制黑色填充；
 * 描边宽度取字号乘以 0.14 与 3px 的较大值，壁纸花纹上仍清晰可读。
 */
private class HaloTextView(
    context: Context,
) : TextView(context) {
    private val haloPaint =
        android.text.TextPaint().apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

    override fun onDraw(canvas: Canvas) {
        val textLayout =
            layout ?: run {
                super.onDraw(canvas)
                return
            }
        if (!text.isNullOrEmpty()) {
            canvas.save()
            // 与 TextView 默认绘制保持相同的左上内边距，并在内容区域内垂直居中
            val vOffset =
                (height - compoundPaddingTop - compoundPaddingBottom - textLayout.height) / 2
            canvas.translate(
                compoundPaddingLeft.toFloat(),
                (compoundPaddingTop + vOffset).toFloat(),
            )
            haloPaint.set(paint)
            haloPaint.style = Paint.Style.STROKE
            haloPaint.strokeJoin = Paint.Join.ROUND
            haloPaint.strokeCap = Paint.Cap.ROUND
            haloPaint.strokeWidth = maxOf(3f, textSize * 0.14f)
            haloPaint.color = Color.WHITE
            val value = text
            for (line in 0 until textLayout.lineCount) {
                val start = textLayout.getLineStart(line)
                var end = textLayout.getLineEnd(line)
                if (end > start && value[end - 1] == '\n') end -= 1
                haloPaint.textSize = textSize
                haloPaint.typeface = typeface
                canvas.drawText(
                    value,
                    start,
                    end,
                    textLayout.getLineLeft(line),
                    textLayout.getLineBaseline(line).toFloat(),
                    haloPaint,
                )
            }
            canvas.restore()
        }
        // 正文由 TextView 以当前文字颜色填充
        super.onDraw(canvas)
    }
}
