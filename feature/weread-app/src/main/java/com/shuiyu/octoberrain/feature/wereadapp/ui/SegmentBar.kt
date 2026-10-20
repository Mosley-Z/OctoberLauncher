package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 分段页签栏。
 *
 * 按传入的文本列表横向等分排列，选中项使用正文黑并加粗，其余项使用中间灰；
 * 选中项下方绘制一条强调线，用于指示当前分段。
 */
internal class SegmentBar(
    context: Context,
    private val onSelected: (Int) -> Unit,
) : LinearLayout(context) {
    /** 分段文本。 */
    private val items = mutableListOf<CharSequence>()

    /** 当前选中下标。 */
    private var selectedIndex = 0

    /** 各分段的文本视图与下划线，切换时直接改颜色与宽度。 */
    private val labels = mutableListOf<TextView>()
    private val underlines = mutableListOf<View>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, DesignRuntime.tokens.topBarHeight)
    }

    /** 读取当前选中下标。 */
    fun selection(): Int = selectedIndex

    /**
     * 更新分段文本并重建子视图。
     *
     * 参数 newItems 为按显示顺序排列的文本；下标越界时回退到首项。
     */
    fun setItems(newItems: List<CharSequence>) {
        items.clear()
        items.addAll(newItems)
        removeAllViews()
        labels.clear()
        underlines.clear()
        val tokens = DesignRuntime.tokens
        items.forEachIndexed { index, text ->
            val column =
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (index != selectedIndex) select(index)
                    }
                }
            val label =
                InkWidgets.textView(context, text, tokens.textBody, InkColors.INK).apply {
                    gravity = Gravity.CENTER
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0, 1f)
                }
            val underline =
                View(context).apply {
                    setBackgroundColor(InkColors.INK)
                    layoutParams = LayoutParams(if (index == selectedIndex) underlineWidth() else 0, tokens.stroke)
                }
            column.addView(label)
            column.addView(underline)
            addView(column)
            labels.add(label)
            underlines.add(underline)
        }
        selectedIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        applySelection()
    }

    /**
     * 选中指定分段并回调监听器。
     *
     * 参数 index 为分段下标，越界时忽略本次调用。
     */
    fun select(index: Int) {
        if (index !in items.indices) return
        val changed = index != selectedIndex
        selectedIndex = index
        applySelection()
        if (changed) onSelected(index)
    }

    /** 按当前选中下标刷新文字颜色、字重与下划线宽度。 */
    private fun applySelection() {
        val tokens = DesignRuntime.tokens
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            label.setTextColor(if (selected) InkColors.INK else InkColors.INK_3)
            label.typeface =
                Typeface.create(
                    DesignRuntime.typeface(),
                    if (selected) Typeface.BOLD else Typeface.NORMAL,
                )
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (selected) tokens.textBody else tokens.textCaption)
            val params = underlines[index].layoutParams
            params.width = if (selected) underlineWidth() else 0
            underlines[index].layoutParams = params
        }
    }

    /** 下划线宽度，取屏宽的六分之一，保持短于分段文本。 */
    private fun underlineWidth(): Int = (resources.displayMetrics.widthPixels / 6).coerceAtLeast(1)
}
