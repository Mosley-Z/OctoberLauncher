package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 墨台分区的版式参数与通用构件。
 *
 * 墨台列表由若干分区纵向排列，每个分区由三单位高的抬头行、一单位分隔线与内容区组成。
 * 抬头行、分隔线、行高与左右留白直接取内核导出的 `DeskStyle` 常量，与微读墨记墨台保持一致。
 *
 * 排版单位 `unit` 与墨台内核一致：屏幕长边乘 0.0015，480×800 面板取 1.2。
 */
@Suppress("TooManyFunctions")
internal object DeskSectionStyle {
    /** 抬头行高度，单位为排版单位，与内核 `DeskStyle.HEADER_UNITS` 一致。 */
    val HEADER_UNITS: Float =
        com.inkread.weekread.kernel.desk.DeskStyle.HEADER_UNITS
            .toFloat()

    /** 分区之间分隔线高度，单位为排版单位，与内核 `DeskStyle.DIVIDER_UNITS` 一致。 */
    val DIVIDER_UNITS: Float =
        com.inkread.weekread.kernel.desk.DeskStyle.DIVIDER_UNITS
            .toFloat()

    /** 分区内容行高，单位为排版单位，与内核 `DeskStyle.ROW_HEIGHT_UNITS` 一致。 */
    val ROW_HEIGHT_UNITS: Float =
        com.inkread.weekread.kernel.desk.DeskStyle.ROW_HEIGHT_UNITS
            .toFloat()

    /** 分区左右留白占分区宽度的比例，与内核 `DeskStyle.PAD_X_RATIO_DESK` 一致。 */
    val PAD_X_RATIO_DESK: Float =
        com.inkread.weekread.kernel.desk.DeskStyle.PAD_X_RATIO_DESK
            .toFloat()

    /** 排版单位换算比例，与墨台内核一致。 */
    private const val UNIT_RATIO: Float = 0.0015f

    /** 抬头行字号，单位为排版单位，与内核 `DeskStyle.SZ_SEC` 一致。 */
    private const val HEADER_TEXT_UNITS: Float = 17f

    /** 抬头行行距倍数，与内核 `DeskStyle.secHeadH` 一致。 */
    private const val HEADER_LINE_RATIO: Float = 2.2f

    /** 顶栏标题字号，单位为排版单位，与微读 `DeskRenderer.SZ_TITLE` 一致。 */
    private const val TOP_BAR_TITLE_UNITS: Float = 18f

    /** 顶栏动作字号，单位为排版单位，与微读 `DeskRenderer.SZ_BACK` 一致。 */
    private const val TOP_BAR_ACTION_UNITS: Float = 14f

    /** 顶栏更新时间字号，单位为排版单位，与微读 `DeskRenderer.SZ_UPD` 一致。 */
    private const val TOP_BAR_UPDATE_UNITS: Float = 12f

    /** 顶栏高度相对标题字号的倍数，与微读 `DeskRenderer.BAR_H_UNITS` 一致。 */
    private const val TOP_BAR_HEIGHT_RATIO: Float = 3f

    /** 分区抬头左侧墨竖线宽度，单位为排版单位，与内核 `DeskStyle.HEAD_BAR_W_UNITS` 一致。 */
    private const val HEAD_BAR_W_UNITS: Float = 2.5f

    /** 墨竖线与分区标题的间距，单位为排版单位，与内核 `DeskStyle.HEAD_BAR_GAP_UNITS` 一致。 */
    private const val HEAD_BAR_GAP_UNITS: Float = 5f

    /** 墨竖线在抬头行内的起始比例，与内核 `DeskStyle.HEAD_BAR_TOP` 一致。 */
    private const val HEAD_BAR_TOP_RATIO: Float = 0.28f

    /** 墨竖线在抬头行内的结束比例，与内核 `DeskStyle.HEAD_BAR_BOT` 一致。 */
    private const val HEAD_BAR_BOT_RATIO: Float = 0.73f

    /** 空态文案字号，单位为排版单位，与内核 `DeskStyle.SZ_EMPTY` 一致。 */
    private const val EMPTY_TEXT_UNITS: Float = 15f

    /** 空态文案占位高度相对字号的倍数，与内核空态高度一致。 */
    private const val EMPTY_LINE_RATIO: Float = 6f

    /** 顶栏更新时间文字颜色，与微读 `InkTheme.INK2` 一致。 */
    private val UPDATE_TEXT_COLOR: Int = Color.rgb(0x6B, 0x64, 0x59)

    /** 分区之间分隔线颜色，与微读 `DeskRenderer.LINE` 一致。 */
    private val DIVIDER_COLOR: Int = Color.rgb(0xD8, 0xD8, 0xD8)

    /**
     * 计算当前屏幕的排版单位。
     *
     * @param context 页面上下文。
     * @return 屏幕长边乘换算比例的结果。
     */
    fun unit(context: Context): Float {
        val metrics = context.resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels) * UNIT_RATIO
    }

    /**
     * 计算墨台顶栏高度。
     *
     * 顶栏高度取标题字号的三倍，与微读 `DeskRenderer.headerHeight` 一致，
     * 480×800 屏（排版单位 1.2）为 64.8 像素。
     *
     * @param context 页面上下文。
     * @return 高度像素值。
     */
    fun topBarHeightPx(context: Context): Int = (TOP_BAR_TITLE_UNITS * unit(context) * TOP_BAR_HEIGHT_RATIO).toInt()

    /**
     * 计算顶栏标题字号像素值。
     *
     * @param context 页面上下文。
     * @return 字号像素值。
     */
    fun topBarTitleTextPx(context: Context): Float = TOP_BAR_TITLE_UNITS * unit(context)

    /**
     * 计算顶栏动作（设置、返回）字号像素值。
     *
     * @param context 页面上下文。
     * @return 字号像素值。
     */
    fun topBarActionTextPx(context: Context): Float = TOP_BAR_ACTION_UNITS * unit(context)

    /**
     * 计算顶栏更新时间字号像素值。
     *
     * @param context 页面上下文。
     * @return 字号像素值。
     */
    fun topBarUpdateTextPx(context: Context): Float = TOP_BAR_UPDATE_UNITS * unit(context)

    /** 返回顶栏更新时间文字颜色。 */
    fun updateTextColor(): Int = UPDATE_TEXT_COLOR

    /** 返回分区之间分隔线颜色。 */
    fun dividerColor(): Int = DIVIDER_COLOR

    /**
     * 计算分区抬头行高度。
     *
     * 内核 `DeskStyle.secHeadH` 按 17 单位字号乘 2.2 倍行距计算抬头行高度，
     * 此处取同一公式，使自建分区的抬头行与墨台内核绘制的分区标题等高。
     *
     * @param context 页面上下文。
     * @return 高度像素值。
     */
    fun headerHeightPx(context: Context): Int = (HEADER_TEXT_UNITS * unit(context) * HEADER_LINE_RATIO).roundToInt()

    /**
     * 构造分区抬头行。
     *
     * 抬头行沿用墨台内核的版式：左侧三像素宽的墨竖线、标题与可选动作文本。
     * 墨竖线横向位于标题左侧，起始列由内边距减去线宽与间距得出，使标题左缘与正文内边距对齐；
     * 纵向占抬头行高度的 28% 至 73%，与内核 `HEAD_BAR_TOP`、`HEAD_BAR_BOT` 一致。
     *
     * @param context 页面上下文。
     * @param title 分区标题。
     * @param actionLabel 右侧动作文本，为空时不显示动作。
     * @param showBar 是否绘制左侧墨竖线；分区抬头需要，设置页抬头不需要。
     * @param onAction 动作点击回调。
     * @return 抬头行视图。
     */
    fun buildHeader(
        context: Context,
        title: String,
        actionLabel: String? = null,
        showBar: Boolean = true,
        onAction: (() -> Unit)? = null,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val metrics = context.resources.displayMetrics
        val inset = paddingXPx(metrics.widthPixels)
        val headerHeight = headerHeightPx(context)
        val barWidth = (HEAD_BAR_W_UNITS * unit(context)).roundToInt().coerceAtLeast(1)
        val barGap = (HEAD_BAR_GAP_UNITS * unit(context)).roundToInt()
        val barHeight = ((HEAD_BAR_BOT_RATIO - HEAD_BAR_TOP_RATIO) * headerHeight).roundToInt().coerceAtLeast(1)
        val header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = headerHeight
                // 墨竖线与标题合起来的左起位置比正文内边距窄，使标题左缘落在内边距上。
                val leading = if (showBar) (inset - barWidth - barGap).coerceAtLeast(0) else inset
                setPadding(leading, 0, inset, 0)
            }
        if (showBar) {
            header.addView(
                View(context).apply { setBackgroundColor(InkColors.INK) },
                LinearLayout.LayoutParams(barWidth, barHeight).apply { marginEnd = barGap },
            )
        }
        val titleView =
            InkWidgets.textView(
                context,
                title,
                (HEADER_TEXT_UNITS * unit(context)),
                InkColors.INK,
                DesignRuntime.typeface("serif"),
            )
        header.addView(
            titleView,
            LinearLayout
                .LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginEnd = tokens.gap },
        )
        if (actionLabel != null && onAction != null) {
            val action = InkWidgets.caption(context, actionLabel).apply { isClickable = true }
            action.setOnClickListener { onAction() }
            val actionParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            header.addView(action, actionParams)
        }
        return header
    }

    /**
     * 构造分区之间的分隔线。
     *
     * 分隔线为 1 像素浅灰细线，左右各内缩一个正文内边距，与墨台内核的分区之间分隔线一致。
     *
     * @param context 页面上下文。
     * @return 分隔线视图。
     */
    fun buildDivider(context: Context): View {
        val inset = paddingXPx(context.resources.displayMetrics.widthPixels)
        val thickness = max(1, (DIVIDER_UNITS * unit(context)).toInt())
        val line =
            View(context).apply {
                setBackgroundColor(dividerColor())
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        thickness,
                    )
            }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(inset, 0, inset, 0)
            addView(line)
        }
    }

    /**
     * 计算分区内容行的最小高度。
     *
     * @param context 页面上下文。
     * @return 行高像素值。
     */
    fun rowHeightPx(context: Context): Int = (ROW_HEIGHT_UNITS * unit(context)).toInt()

    /**
     * 计算分区左右留白。
     *
     * @param widthPx 分区宽度。
     * @return 左右留白像素值。
     */
    fun paddingXPx(widthPx: Int): Int = (widthPx * PAD_X_RATIO_DESK).toInt().coerceAtLeast(DesignRuntime.tokens.gap)

    /**
     * 构造分区空态文案。
     *
     * 空态按墨台内核的空态高度（15 号字乘 6 倍行距）固定占位并垂直居中，
     * 与微读墨台各分区空态的高度一致。
     *
     * @param context 页面上下文。
     * @param text 文案内容。
     * @return 文案视图。
     */
    fun buildEmpty(
        context: Context,
        text: String,
    ): TextView =
        InkWidgets.caption(context, text).apply {
            gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    emptyHeightPx(context),
                )
        }

    /**
     * 计算分区空态高度。
     *
     * @param context 页面上下文。
     * @return 高度像素值。
     */
    private fun emptyHeightPx(context: Context): Int =
        (EMPTY_TEXT_UNITS * unit(context) * EMPTY_LINE_RATIO).roundToInt()
}
