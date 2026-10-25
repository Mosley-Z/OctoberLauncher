package com.shuiyu.octoberrain.feature.widgets

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadTodo
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.time.LocalDate
import java.util.Locale

/**
 * 待办周历的紧凑录入对话框。
 *
 * 新建时日期默认今天，时间默认未设置；编辑时读取原值。保存、删除都直接调用
 * [com.shuiyu.octoberrain.core.common.WereadTodoHost]，并把操作后的完整清单交回组件刷新，
 * 不复制第二份存储。日期选择沿用系统日期面板，时间以「HH:mm」文本录入，
 * 因此 00:00 与未设置可以区分。
 */
@Suppress("TooManyFunctions")
internal class TodoWeekEditor(
    private val context: Context,
    private val editing: WereadTodo?,
    private val onSaved: (List<WereadTodo>) -> Unit,
) {
    private val dialog = Dialog(context)
    private var dateSec: Long = editing?.dateSec ?: TodoWeekCalendar.startOfDay(LocalDate.now())
    private var minuteOfDay: Int = editing?.minuteOfDay ?: WereadTodo.NO_TIME
    private lateinit var dateValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var input: EditText

    /** 构建并显示对话框。 */
    fun show() {
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val root = FrameLayout(context)
        root.addView(buildPanel(), frameParams())
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.35f)
        dialog.window?.setLayout(dialogWidth(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** 面板外留出与设计令牌一致的外边距。 */
    private fun frameParams(): FrameLayout.LayoutParams {
        val margin = DesignRuntime.tokens.gap
        return FrameLayout
            .LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(margin, margin, margin, margin) }
    }

    /** 对话框宽度不超过屏幕的百分之八十六与 380dp。 */
    private fun dialogWidth(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        return minOf((screen * 0.86f).toInt(), InkWidgets.dp(context, 380f))
    }

    /** 组装面板内容。 */
    private fun buildPanel(): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            setPadding(tokens.gapLarge, tokens.gapLarge, tokens.gapLarge, tokens.gapLarge)
            addView(InkWidgets.heading(context, if (editing == null) "添加待办" else "编辑待办"))
            addView(buildInput())
            addView(buildDateRow())
            addView(buildTimeRow())
            addView(buildActionRow())
        }
    }

    /** 正文输入框。 */
    private fun buildInput(): EditText {
        val tokens = DesignRuntime.tokens
        return EditText(context).apply {
            hint = "要做什么？"
            setText(editing?.content.orEmpty())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            maxLines = 3
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
            setTextColor(InkColors.INK)
            background = InkWidgets.background(tokens.radiusSmall, InkColors.PAPER, tokens.stroke, InkColors.INK_4)
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            layoutParams =
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = tokens.gap }
            input = this
        }
    }

    /** 日期行：当前值、选日期、清除。 */
    private fun buildDateRow(): LinearLayout {
        dateValue = InkWidgets.body(context, dateLabel()).apply { layoutParams = weightParams() }
        val row = fieldRow(dateValue)
        row.addView(action("选日期") { pickDate() })
        row.addView(
            action("清除") {
                dateSec = 0L
                refreshFields()
            },
        )
        return row
    }

    /** 时间行：当前值、设置、清除。 */
    private fun buildTimeRow(): LinearLayout {
        timeValue = InkWidgets.body(context, timeLabel()).apply { layoutParams = weightParams() }
        val row = fieldRow(timeValue)
        row.addView(action("设置") { pickTime() })
        row.addView(
            action("清除") {
                minuteOfDay = WereadTodo.NO_TIME
                refreshFields()
            },
        )
        return row
    }

    /** 组合一行字段，当前值占据剩余宽度。 */
    private fun fieldRow(value: TextView): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            addView(value)
            setPadding(0, DesignRuntime.tokens.gap / 2, 0, 0)
        }

    /** 底部操作：删除、取消、保存。 */
    private fun buildActionRow(): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, DesignRuntime.tokens.gapLarge, 0, 0)
            if (editing != null) {
                addView(action("删除", InkColors.INK_2) { confirmDelete() })
            }
            addView(TextView(context), weightParams())
            addView(button("取消", filled = false) { dialog.dismiss() })
            addView(button("保存", filled = true) { save() })
        }

    /** 创建等宽按钮。 */
    private fun button(
        label: String,
        filled: Boolean,
        onClick: () -> Unit,
    ): TextView =
        InkWidgets.button(context, label, filled, onClick).apply {
            layoutParams =
                LinearLayout
                    .LayoutParams(InkWidgets.dp(context, 88f), DesignRuntime.tokens.buttonHeight)
                    .apply { marginStart = DesignRuntime.tokens.gap }
        }

    /** 创建文本操作入口。 */
    private fun action(
        label: String,
        color: Int = InkColors.INK_2,
        onClick: () -> Unit,
    ): TextView =
        InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, color).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(DesignRuntime.tokens.gap, DesignRuntime.tokens.gap, 0, DesignRuntime.tokens.gap)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /** 占据剩余宽度的布局参数。 */
    private fun weightParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    /** 选择日期。 */
    private fun pickDate() {
        val current = if (dateSec > 0) TodoWeekCalendar.dateOf(dateSec) else LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, day ->
                dateSec = TodoWeekCalendar.startOfDay(LocalDate.of(year, month + 1, day))
                refreshFields()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    /** 录入时间，留空表示未设置。 */
    private fun pickTime() {
        val initial = if (minuteOfDay >= 0) timeLabel() else ""
        InkDialogs.input(
            context,
            "设置时间",
            hint = "格式 HH:mm，留空表示未设",
            initial = initial,
        ) { text ->
            val parsed = TodoWeekItems.parseTimeInput(text)
            if (parsed == null) {
                InkDialogs.toast(context, "时间格式应为 HH:mm")
            } else {
                minuteOfDay = parsed
                refreshFields()
            }
        }
    }

    /** 刷新两行显示值。 */
    private fun refreshFields() {
        dateValue.text = dateLabel()
        timeValue.text = timeLabel()
    }

    /** 日期文案，未设置时明确标注。 */
    private fun dateLabel(): String {
        if (dateSec <= 0) return "未设日期"
        val date = TodoWeekCalendar.dateOf(dateSec)
        return "${date.monthValue}月${date.dayOfMonth}日 ${TodoWeekCalendar.weekdayLabel(date)}"
    }

    /** 时间文案，未设置时返回占位说明。 */
    private fun timeLabel(): String {
        if (minuteOfDay < 0) return "未设时间"
        return String.format(
            Locale.CHINA,
            "%02d:%02d",
            minuteOfDay / WereadTodo.MINUTES_PER_HOUR,
            minuteOfDay % WereadTodo.MINUTES_PER_HOUR,
        )
    }

    /** 保存并回调最新清单。 */
    private fun save() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            InkDialogs.toast(context, "请先填写内容")
            return
        }
        val host = AppServices.wereadTodos ?: return
        val latest =
            if (editing == null) {
                host.add(text, dateSec, minuteOfDay)
            } else {
                host.update(editing.id, text, dateSec, minuteOfDay)
            }
        dialog.dismiss()
        onSaved(latest)
    }

    /** 删除当前待办。 */
    private fun confirmDelete() {
        val todo = editing ?: return
        InkDialogs.confirm(
            context,
            "删除待办",
            "确定删除「${todo.content}」吗？",
            confirmLabel = "删除",
            onConfirm = {
                val latest = AppServices.wereadTodos?.remove(todo.id) ?: return@confirm
                dialog.dismiss()
                onSaved(latest)
            },
        )
    }
}
