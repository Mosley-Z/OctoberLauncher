package com.shuiyu.octoberrain.core.design

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 界面构件工厂：为设置页、列表页等常规页面提供统一的白底黑字构件。
 *
 * 所有尺寸取自 DesignRuntime.tokens，颜色取自 InkColors，
 * 页面代码只需组织内容，避免重复设置字号与描边。
 */
object InkWidgets {
    /** 创建文本视图。 */
    fun textView(
        context: Context,
        text: CharSequence,
        sizePx: Float,
        color: Int = InkColors.INK,
        typeface: Typeface = DesignRuntime.typeface(),
    ): TextView =
        TextView(context).apply {
            this.text = text
            this.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
            this.setTextColor(color)
            this.typeface = typeface
            this.includeFontPadding = false
        }

    /** 页面大标题。 */
    fun title(
        context: Context,
        text: CharSequence,
    ): TextView =
        textView(context, text, DesignRuntime.tokens.textTitle, InkColors.INK).apply {
            setPadding(0, 0, 0, DesignRuntime.tokens.gap)
        }

    /** 区块标题。 */
    fun heading(
        context: Context,
        text: CharSequence,
    ): TextView = textView(context, text, DesignRuntime.tokens.textHeading, InkColors.INK)

    /** 正文文本。 */
    fun body(
        context: Context,
        text: CharSequence,
    ): TextView = textView(context, text, DesignRuntime.tokens.textBody, InkColors.INK)

    /** 说明文本。 */
    fun caption(
        context: Context,
        text: CharSequence,
    ): TextView = textView(context, text, DesignRuntime.tokens.textCaption, InkColors.INK_2)

    /** 创建带描边的按钮。 */
    fun button(
        context: Context,
        label: CharSequence,
        filled: Boolean = false,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return textView(
            context,
            label,
            tokens.textBody,
            if (filled) InkColors.PAPER else InkColors.INK,
        ).apply {
            gravity = Gravity.CENTER
            minWidth = tokens.buttonMinWidth
            minimumWidth = tokens.buttonMinWidth
            minimumHeight = tokens.buttonHeight
            setPadding(tokens.gapLarge, tokens.gap, tokens.gapLarge, tokens.gap)
            background =
                if (filled) {
                    background(tokens.radius, InkColors.INK, 0, InkColors.INK)
                } else {
                    background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
                }
            isClickable = true
            isFocusable = true
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            setOnClickListener { onClick() }
        }
    }

    /** 创建可点击的设置行：左侧标题，右侧当前值与指示箭头。 */
    fun row(
        context: Context,
        label: CharSequence,
        value: CharSequence? = null,
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.listRowHeight
                setPadding(tokens.gap, 0, tokens.gap, 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            }
        val title =
            textView(context, label, tokens.textBody, InkColors.INK).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        row.addView(title)
        if (value != null) {
            row.addView(textView(context, value, tokens.textCaption, InkColors.INK_2))
        }
        row.addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON))
        if (onClick != null) {
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { onClick() }
        }
        return row
    }

    /** 创建仅含左侧标题的行，用于状态展示。 */
    fun labelRow(
        context: Context,
        label: CharSequence,
        value: CharSequence,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = tokens.listRowHeight
            setPadding(tokens.gap, 0, tokens.gap, 0)
            addView(
                textView(context, label, tokens.textBody, InkColors.INK).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(textView(context, value, tokens.textCaption, InkColors.INK_2))
        }
    }

    /** 创建分隔线。 */
    fun divider(context: Context): View =
        View(context).apply {
            setBackgroundColor(InkColors.INK_4)
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    maxOf(1, DesignRuntime.tokens.dp(0.7f)),
                )
        }

    /** 创建纵向占位。 */
    fun spacer(
        context: Context,
        heightPx: Int,
    ): View =
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, heightPx)
        }

    /** 构造圆角背景，描边宽度为 0 时只填充。 */
    fun background(
        radiusPx: Int,
        color: Int = InkColors.PAPER,
        strokeWidthPx: Int = 0,
        strokeColor: Int = InkColors.INK,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(color)
            if (strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColor)
        }

    /** dp 转像素，供页面代码快速换算。 */
    fun dp(
        context: Context,
        value: Float,
    ): Int = Math.round(value * context.resources.displayMetrics.density)

    /** 设置视图内边距，参数单位为像素。 */
    fun pad(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        view.setPadding(left, top, right, bottom)
    }
}

/**
 * 二级页面容器：顶部返回栏加可滚动内容区。
 *
 * 构造参数 title 为页面标题；showBack 为 false 时不显示返回按钮；
 * onBack 为返回按钮回调。
 */
class InkPage(
    context: Context,
    title: CharSequence? = null,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
) : FrameLayout(context) {
    /** 纵向内容容器，页面把区块加入此处。 */
    val content: LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.gap,
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.pageBottom,
            )
        }

    private val titleView: TextView
    private val header: LinearLayout
    private val scroll = ScrollView(context)
    private var backTarget: FrameLayout? = null
    var onDimensionsChanged: (() -> Unit)? = null

    fun scrollPosition(): Int = scroll.scrollY

    fun restoreScroll(position: Int) {
        scroll.scrollTo(0, position)
    }

    fun refreshDimensions() {
        val t = DesignRuntime.tokens
        header.minimumHeight = maxOf(t.topBarHeight, t.dp(48f))
        header.setPadding(t.pageGutter, 0, t.pageGutter, 0)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.textHeading)
        titleView.setPadding(t.gap, 0, 0, 0)
        content.setPadding(t.pageGutter, t.gap, t.pageGutter, t.pageBottom)
        backTarget?.layoutParams = LinearLayout.LayoutParams(t.dp(48f), t.dp(48f))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshDimensions()
        onDimensionsChanged?.invoke()
    }

    init {
        setBackgroundColor(InkColors.PAPER)
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
        addView(root)

        header =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    DesignRuntime.tokens.pageGutter,
                    0,
                    DesignRuntime.tokens.pageGutter,
                    0,
                )
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            }
        if (showBack) {
            header.addView(
                FrameLayout(context).apply {
                    backTarget = this
                    isClickable = true
                    isFocusable = true
                    contentDescription = "返回"
                    addView(
                        InkGlyphs.view(context, InkGlyphs.Kind.BACK),
                        FrameLayout.LayoutParams(
                            DesignRuntime.tokens.settingsIcon,
                            DesignRuntime.tokens.settingsIcon,
                            Gravity.CENTER,
                        ),
                    )
                    setOnClickListener { onBack?.invoke() }
                },
            )
        }
        titleView =
            InkWidgets
                .textView(
                    context,
                    title ?: "",
                    DesignRuntime.tokens.textHeading,
                    InkColors.INK,
                ).apply {
                    setPadding(DesignRuntime.tokens.gap, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
        // 标题为空时不占位，仅保留返回按钮所在的顶栏。
        if (!title.isNullOrEmpty()) header.addView(titleView)
        root.addView(header)
        root.addView(InkWidgets.divider(context))
        root.addView(
            scroll.apply {
                isFillViewport = true
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
                addView(content)
            },
        )
        refreshDimensions()
    }

    /** 更新标题文本。 */
    fun setTitle(text: CharSequence) {
        titleView.text = text
    }
}
