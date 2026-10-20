@file:Suppress("TooManyFunctions")

package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets

/** 实验室三段页签的文案，顺序与微读墨记一致。 */
private val LAB_TAB_LABELS: List<CharSequence> = listOf("翻页", "锁屏密码", "续航优化")

/**
 * 实验室页。
 *
 * 顶部固定展示「翻页 / 锁屏密码 / 续航优化」三段页签，下方按选中分段切换整页内容；
 * 每段内容均纵向滚动。实验内容开关仍由设置页连续点击版本号五次控制，展开后
 * 在翻页段末尾展示数据目录、文件状态与偏好快照。
 *
 * @param context 创建视图的上下文
 */
internal class WereadLabPage(
    context: Context,
) : LinearLayout(context) {
    /** 实验室页对外回调。 */
    internal interface Listener {
        /** 实验内容收起后通知外层重新渲染当前分段。 */
        fun onLabDisabled()
    }

    /** 外部回调。 */
    var listener: Listener? = null

    /** 分段内容容器。 */
    private val pageHost = FrameLayout(context)

    /** 分段页签栏。 */
    private val segmentBar = SegmentBar(context) { index -> showPage(index) }

    /** 翻页段。 */
    private val flipPage = WereadLabFlipPage(context) { onLabCollapsed() }

    /** 锁屏密码段。 */
    private val lockPage = WereadLabLockPage(context)

    /** 续航优化段。 */
    private val powerPage = WereadLabPowerPage(context)

    /** 三段页面，下标与页签顺序一致。 */
    private val pages: List<LabPageView> = listOf(flipPage, lockPage, powerPage)

    /** 当前选中分段。 */
    private var selected = 0

    init {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(InkColors.PAPER)
        addView(buildHeader(context))
        addView(segmentBar)
        segmentBar.setItems(LAB_TAB_LABELS)
        pageHost.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        addView(pageHost)
        showPage(0)
    }

    /** 重新渲染全部三段并保持当前选中项。 */
    fun refresh() {
        pages.forEach { it.refresh() }
        showPage(selected)
    }

    /** 顶部标题区。 */
    private fun buildHeader(context: Context): View {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(tokens.pageGutter, tokens.gap, tokens.pageGutter, 0)
            addView(InkWidgets.heading(context, "实验室"))
        }
    }

    /** 切换分段，渲染目标页。 */
    private fun showPage(index: Int) {
        selected = index.coerceIn(0, pages.lastIndex)
        pageHost.removeAllViews()
        pages[selected].refresh()
        pageHost.addView(pages[selected])
    }

    /** 实验内容收起后的回调：通知外层并刷新本页。 */
    private fun onLabCollapsed() {
        listener?.onLabDisabled()
        pages.forEach { it.refresh() }
        showPage(selected)
    }
}

/**
 * 分段页基类：整页纵向滚动，内容容器边距与设置页保持一致。
 *
 * 子类在 [render] 中组织内容；[refresh] 会清空容器后重新执行 [render]。
 */
internal abstract class LabPageView(
    context: Context,
) : FrameLayout(context) {
    /** 内容容器。 */
    protected val content =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.gap,
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.pageBottom,
            )
        }

    init {
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(content)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** 清空并重建内容。 */
    fun refresh() {
        content.removeAllViews()
        render()
    }

    /** 组织本段内容。 */
    protected abstract fun render()
}

/** 分段标题文本，上下留少量间距。 */
internal fun labSectionTitle(
    context: Context,
    text: CharSequence,
): TextView =
    InkWidgets.heading(context, text).apply {
        setPadding(0, DesignRuntime.tokens.gapLarge, 0, DesignRuntime.tokens.gap)
    }

/** 说明文本。 */
internal fun labCaption(
    context: Context,
    text: CharSequence,
): TextView = InkWidgets.caption(context, text)

/** 正文文本。 */
internal fun labBody(
    context: Context,
    text: CharSequence,
): TextView = InkWidgets.body(context, text)

/** 纵向占位。 */
internal fun labSpacer(
    context: Context,
    multiple: Float = 1f,
): View = InkWidgets.spacer(context, (DesignRuntime.tokens.gap * multiple).toInt())

/** 分隔线。 */
internal fun labDivider(context: Context): View = InkWidgets.divider(context)

/** 提示轻提示。 */
internal fun labToast(
    context: Context,
    text: CharSequence,
) {
    InkDialogs.toast(context, text.toString())
}

/**
 * 复选行。
 *
 * @param label 复选文案
 * @param checked 初始勾选状态
 * @param onChange 勾选状态变化回调，参数为最新状态
 */
internal fun labCheckRow(
    context: Context,
    label: CharSequence,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
): CheckBox =
    CheckBox(context).apply {
        text = label
        isChecked = checked
        setTextSize(TypedValue.COMPLEX_UNIT_PX, DesignRuntime.tokens.textBody)
        setTextColor(InkColors.INK)
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = DesignRuntime.tokens.listRowHeight
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

/**
 * 单选行组。
 *
 * @param options 选项文案，顺序即取值下标
 * @param selectedIndex 当前选中下标
 * @param enabled 是否可交互，false 时整组置灰
 * @param onSelected 选中回调，参数为选项下标
 */
internal fun labRadioGroup(
    context: Context,
    options: List<CharSequence>,
    selectedIndex: Int,
    enabled: Boolean = true,
    onSelected: (Int) -> Unit = {},
): RadioGroup =
    RadioGroup(context).apply {
        orientation = RadioGroup.VERTICAL
        options.forEachIndexed { index, text ->
            addView(
                RadioButton(context).apply {
                    id = View.generateViewId()
                    this.text = text
                    isChecked = index == selectedIndex
                    isEnabled = enabled
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, DesignRuntime.tokens.textBody)
                    setTextColor(if (enabled) InkColors.INK else InkColors.INK_3)
                    alpha = if (enabled) 1f else DISABLED_ALPHA
                    minimumHeight = DesignRuntime.tokens.listRowHeight
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                },
            )
        }
        if (enabled) {
            setOnCheckedChangeListener { _, checkedId ->
                val index = indexOfChild(findViewById(checkedId))
                if (index >= 0) onSelected(index)
            }
        }
    }

/**
 * 输入行：左侧标签，右侧单行输入框。
 *
 * @param label 标签文案
 * @param initial 初始文本
 * @param hint 输入提示
 * @param inputType 输入类型，取 android.text.InputType 常量
 */
internal fun labFieldRow(
    context: Context,
    label: CharSequence,
    initial: CharSequence,
    hint: CharSequence,
    inputType: Int,
): Pair<LinearLayout, EditText> {
    val tokens = DesignRuntime.tokens
    val field =
        EditText(context).apply {
            setText(initial)
            this.hint = hint.toString()
            this.inputType = inputType
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
            setTextColor(InkColors.INK)
            setHintTextColor(InkColors.INK_3)
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    val row =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            addView(
                InkWidgets.textView(context, label, tokens.textBody, InkColors.INK).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(field)
        }
    return row to field
}

/** 整宽按钮，用于页尾通用入口。 */
internal fun labWideButton(
    context: Context,
    label: CharSequence,
    onClick: () -> Unit,
): TextView {
    val tokens = DesignRuntime.tokens
    return InkWidgets.button(context, label, false, onClick).apply {
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                tokens.buttonHeight,
            )
        background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
    }
}

/** 横向按钮行，按钮间留标准间距。 */
internal fun labButtonRow(
    context: Context,
    vararg buttons: TextView,
): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            if (index > 0) {
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(DesignRuntime.tokens.gap, 1)
                    },
                )
            }
            addView(button)
        }
    }

/**
 * 状态胶囊：描边圆角块，展示当前状态。
 *
 * @param text 状态文案
 */
internal fun labPill(
    context: Context,
    text: CharSequence,
): TextView {
    val tokens = DesignRuntime.tokens
    return InkWidgets.textView(context, text, tokens.textBody, InkColors.INK).apply {
        gravity = Gravity.CENTER
        setPadding(tokens.gapLarge, tokens.gap, tokens.gapLarge, tokens.gap)
        background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
    }
}

/**
 * 折叠区块：标题行可点击，正文默认收起。
 *
 * @param title 折叠标题
 * @param body 折叠内容容器，由调用方填充
 */
internal fun labFold(
    context: Context,
    title: CharSequence,
    body: LinearLayout,
): LinearLayout {
    body.visibility = View.GONE
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            InkWidgets.row(context, title, null) {
                body.visibility = if (body.visibility == View.GONE) View.VISIBLE else View.GONE
            },
        )
        addView(body)
    }
}

/** 禁用选项的透明度。 */
private const val DISABLED_ALPHA: Float = 0.62f
