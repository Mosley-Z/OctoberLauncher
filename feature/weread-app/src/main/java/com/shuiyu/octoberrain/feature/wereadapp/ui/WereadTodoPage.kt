package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.inkread.weekread.ui.SegTabView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadTodo
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 待办页。
 *
 * 结构对照微读墨记的 `activity_todo.xml`：顶部「待办列表 / 已完成」两段页签、可滚动清单、
 * 空态提示行与底部「添加待办 / 排序」两个等宽按钮；普通态点条目进入编辑，排序态右侧显示
 * 上移、下移箭头且点条目不再触发编辑。数据读写统一走 [AppServices.wereadTodos]。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadTodoPage(
    context: Context,
) : LinearLayout(context) {
    /** 顶部页签：待办列表 | 已完成。 */
    private val segTab =
        SegTabView(context).apply {
            setLabels(arrayOf(TAB_PENDING, TAB_DONE))
            setListener { index ->
                tab = index
                // 换页退出排序态，避免排序箭头串到另一页。
                sorting = false
                render()
            }
        }

    /** 待办对话框左右外边距，对应微读面板左右各 14px（480×800 实机）。 */
    private val dialogMarginPx: Int = dpRound(DIALOG_MARGIN_DP)

    /** 时间选择弹窗统一内边距。 */
    private val dialogPadPx: Int = DesignRuntime.tokens.dp(22f)

    /** 待办对话框左内边距。 */
    private val dialogPadLeftPx: Int = dpRound(DIALOG_PAD_LEFT_DP)

    /** 待办对话框上内边距。 */
    private val dialogPadTopPx: Int = DesignRuntime.tokens.dp(DIALOG_PAD_TOP_DP)

    /** 待办对话框右内边距。 */
    private val dialogPadRightPx: Int = dpRound(DIALOG_PAD_RIGHT_DP)

    /** 待办对话框下内边距。 */
    private val dialogPadBottomPx: Int = dpRound(DIALOG_PAD_BOTTOM_DP)

    /** 待办对话框按钮高度，对齐微读实机 58px（480×800）。 */
    private val dialogButtonHeightPx: Int = dpRound(DIALOG_BUTTON_HEIGHT_DP)

    /** 日期/时间行按钮宽度，对齐微读实机 111px。 */
    private val dateButtonWidthPx: Int = dpRound(DATE_BUTTON_WIDTH_DP)

    /** 底部动作按钮宽度，对齐微读实机 78px。 */
    private val actionButtonWidthPx: Int = dpRound(ACTION_BUTTON_WIDTH_DP)

    /** 日期/时间行右侧额外内缩，使按钮右缘与微读 434px 对齐。 */
    private val dateRowEndPadPx: Int = dpRound(DATE_ROW_END_PAD_DP)

    /** 同一行内两个按钮之间的间距。 */
    private val buttonPairGapPx: Int = dpRound(BUTTON_PAIR_GAP_DP)

    /** 输入行与日期行之间的间距。 */
    private val dateRowGapPx: Int = dpRound(DATE_ROW_GAP_DP)

    /** 日期行与时间行上方的间距。 */
    private val rowTopPx: Int = dpRound(ROW_TOP_DP)

    /** 日期行与时间行之间的间距。 */
    private val timeRowGapPx: Int = dpRound(TIME_ROW_GAP_DP)

    /** 时间行与底部按钮行之间的间距。 */
    private val actionsTopPx: Int = dpRound(ACTIONS_TOP_DP)

    /** 待办对话框距屏幕顶部的偏移，对齐微读实机的行下方位置。 */
    private val dialogTopPx: Int = dpRound(DIALOG_TOP_DP)

    /** 清单容器，滚动区域内的纵向列表。 */
    private val listBox =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(INSET_PX, GAP_TOP_PX, INSET_PX, GAP_BOTTOM_PX)
        }

    /** 空态提示，清单为空时显示在列表与底部按钮之间。 */
    private val emptyText =
        InkWidgets.textView(context, "", DesignRuntime.tokens.textHeading, EMPTY_TEXT_COLOR).apply {
            gravity = Gravity.CENTER
            setPadding(0, EMPTY_PAD_PX, 0, EMPTY_PAD_PX)
            visibility = View.GONE
        }

    /** 底部「添加待办」按钮。 */
    private val addButton: TextView

    /** 底部「排序 / 完成排序」按钮。 */
    private val sortButton: TextView

    /** 当前子页：0 = 待办列表，1 = 已完成。 */
    private var tab: Int = 0

    /** 排序态：为真时条目右侧显示上下移动箭头。 */
    private var sorting: Boolean = false

    init {
        orientation = VERTICAL
        setBackgroundColor(InkColors.PAPER)
        addView(segTab, LayoutParams(LayoutParams.MATCH_PARENT, DesignRuntime.tokens.topBarHeight))
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    listBox,
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { weight = 1f },
        )
        addView(emptyText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addButton =
            InkWidgets.button(context, "＋ 添加待办", filled = false) { edit(null) }.apply {
                layoutParams =
                    LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f).apply {
                        marginEnd = BUTTON_GAP_PX
                    }
            }
        sortButton =
            InkWidgets.button(context, "排序", filled = false) { toggleSorting() }.apply {
                layoutParams =
                    LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f).apply {
                        marginStart = BUTTON_GAP_PX
                    }
            }
        addView(
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                setPadding(INSET_PX, BUTTON_ROW_TOP_PX, INSET_PX, BUTTON_ROW_BOTTOM_PX)
                addView(addButton)
                addView(sortButton)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        render()
    }

    /** 重新读取待办并刷新清单。 */
    fun refresh() {
        render()
    }

    /** 重建清单、空态与底部按钮文案。 */
    private fun render() {
        listBox.removeAllViews()
        val host = AppServices.wereadTodos
        if (host == null) {
            showEmpty("待办服务未就绪")
            return
        }
        val all = host.list()
        val items = if (tab == 0) all.filter { !it.done } else all.filter { it.done }
        if (items.isEmpty()) {
            showEmpty(if (tab == 0) EMPTY_PENDING else EMPTY_DONE)
        } else {
            emptyText.visibility = View.GONE
            items.forEach { todo -> listBox.addView(buildRow(todo, all)) }
        }
        sortButton.text = if (sorting) "完成排序" else "排序"
    }

    /** 显示空态提示。 */
    private fun showEmpty(message: String) {
        emptyText.text = message
        emptyText.visibility = View.VISIBLE
    }

    /**
     * 切换排序态。
     *
     * 进入排序态时条目右侧出现上下移动箭头，再次点击恢复普通态；换页也会退出。
     */
    private fun toggleSorting() {
        sorting = !sorting
        render()
    }

    /**
     * 构造一条待办行。
     *
     * @param todo 当前条目
     * @param all 全部条目，用于判断上移、下移是否越界
     */
    private fun buildRow(
        todo: WereadTodo,
        all: List<WereadTodo>,
    ): View {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ROW_PAD_PX, 0, ROW_PAD_PX)
            }
        val check =
            CheckBox(context).apply {
                isChecked = todo.done
                setOnClickListener {
                    AppServices.wereadTodos?.toggle(todo.id)
                    render()
                }
            }
        row.addView(check)
        val textColumn =
            LinearLayout(context).apply {
                orientation = VERTICAL
                layoutParams =
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = DesignRuntime.tokens.dp(4f)
                    }
                isClickable = !sorting
                isFocusable = !sorting
                if (!sorting) setOnClickListener { edit(todo) }
            }
        val title =
            InkWidgets.body(context, todo.content).apply {
                if (todo.done) {
                    paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    setTextColor(InkColors.INK_3)
                }
            }
        textColumn.addView(title)
        val whenText = todo.whenLabel
        if (whenText.isNotEmpty()) {
            textColumn.addView(InkWidgets.caption(context, whenText))
        }
        row.addView(textColumn)
        if (sorting) {
            val index = all.indexOfFirst { it.id == todo.id }
            row.addView(arrowButton("↑", enabled = index > 0) { move(todo, -1) })
            row.addView(arrowButton("↓", enabled = index in 0 until all.size - 1) { move(todo, 1) })
        }
        return row
    }

    /**
     * 构造排序箭头。
     *
     * @param label 箭头字符
     * @param enabled 是否可点（首条不能上移、末条不能下移）
     * @param onClick 点击回调
     */
    private fun arrowButton(
        label: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView =
        InkWidgets.textView(context, label, DesignRuntime.tokens.textHeading, InkColors.INK).apply {
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(ARROW_PX, ARROW_PX)
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            if (!enabled) setTextColor(InkColors.INK_4)
            setOnClickListener { if (enabled) onClick() }
        }

    /**
     * 打开新增或编辑对话框。
     *
     * 版式对照微读墨记的待办对话框：标题、输入行、日期行（状态文字 + 选日期 + 清除）、
     * 时间行（状态文字 + 选时间 + 清除）、底部按钮行（编辑态含删除）。日期与时间可分别
     * 清除，保存后写入待办条目并刷新清单。
     *
     * @param todo 为 null 时新增；非 null 时编辑该条，并在对话框内提供删除
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun edit(todo: WereadTodo?) {
        val host = AppServices.wereadTodos ?: return
        val isNew = todo == null
        var dateSec = todo?.dateSec ?: 0L
        var minuteOfDay = todo?.minuteOfDay ?: DATE_UNSET
        val dialog = Dialog(context)
        val root =
            FrameLayout(context).apply {
                setPadding(dialogMarginPx, 0, dialogMarginPx, 0)
            }
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                background =
                    GradientDrawable().apply {
                        setColor(InkColors.PAPER)
                        setStroke(DIALOG_STROKE_PX, InkColors.INK)
                    }
                setPadding(
                    dialogPadLeftPx,
                    dialogPadTopPx,
                    dialogPadRightPx,
                    dialogPadBottomPx,
                )
            }
        panel.addView(
            InkWidgets.textView(
                context,
                if (isNew) "添加待办" else "编辑待办",
                DesignRuntime.tokens.textHeading,
                InkColors.INK,
            ),
        )
        val input =
            EditText(context).apply {
                hint = "要做什么？"
                setSingleLine(true)
                setText(if (isNew) "" else todo?.content.orEmpty())
                setSelection(text.length)
                setTextColor(InkColors.INK)
                setHintTextColor(InkColors.INK_3)
            }
        panel.addView(
            input,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = INPUT_MARGIN_TOP_PX
            },
        )
        panel.addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, dateRowGapPx))
        val dateText = InkWidgets.body(context, "")
        val timeText = InkWidgets.body(context, "")

        /** 按当前日期、时间状态刷新两行状态文字。 */
        fun refreshDateTime() {
            dateText.text = dateLabel(dateSec)
            timeText.text = timeLabel(minuteOfDay)
        }

        panel.addView(
            buildDateTimeRow(dateText, "选日期", {
                pickDate(dateSec) { picked ->
                    dateSec = picked
                    refreshDateTime()
                }
            }, {
                dateSec = 0L
                refreshDateTime()
            }),
        )
        panel.addView(View(context), LayoutParams(LayoutParams.MATCH_PARENT, timeRowGapPx))
        panel.addView(
            buildDateTimeRow(timeText, "选时间", {
                pickTime(minuteOfDay) { picked ->
                    minuteOfDay = picked
                    refreshDateTime()
                }
            }, {
                minuteOfDay = DATE_UNSET
                refreshDateTime()
            }),
        )
        refreshDateTime()

        val actions =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, actionsTopPx, 0, 0)
            }
        if (!isNew) {
            actions.addView(
                dialogButtonSized("删除", actionButtonWidthPx) {
                    dialog.dismiss()
                    InkDialogs.confirm(
                        context,
                        "删除待办",
                        "确定删除「${todo?.content.orEmpty()}」吗？",
                        confirmLabel = "删除",
                        onConfirm = {
                            host.remove(todo?.id ?: 0L)
                            render()
                        },
                    )
                },
            )
        }
        actions.addView(View(context), LayoutParams(0, 1, 1f))
        actions.addView(
            dialogButtonSized(
                "取消",
                actionButtonWidthPx,
                marginStart = buttonPairGapPx,
            ) { dialog.dismiss() },
        )
        actions.addView(
            dialogButtonSized(
                "保存",
                actionButtonWidthPx,
                marginStart = buttonPairGapPx,
                bold = true,
            ) {
                val text = input.text.toString().trim()
                if (text.isEmpty()) {
                    InkDialogs.toast(context, "请先填写内容")
                    return@dialogButtonSized
                }
                if (isNew) {
                    host.add(text, dateSec, minuteOfDay)
                } else {
                    host.update(todo.id, text, dateSec, minuteOfDay)
                }
                dialog.dismiss()
                render()
            },
        )
        panel.addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        root.addView(panel, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                window.setGravity(Gravity.TOP)
                val attrs = window.attributes
                attrs.y = dialogTopPx
                window.attributes = attrs
            }
        }
        dialog.show()
    }

    /**
     * 构造一行日期或时间选择：左侧状态文字，右侧「选择」与「清除」两个按钮。
     *
     * @param status 状态文字控件
     * @param pickLabel 选择按钮文案
     * @param onPick 选择按钮点击
     * @param onClear 清除按钮点击
     */
    private fun buildDateTimeRow(
        status: TextView,
        pickLabel: String,
        onPick: () -> Unit,
        onClear: () -> Unit,
    ): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, rowTopPx, dateRowEndPadPx, 0)
            addView(status, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(
                dialogButtonSized(
                    pickLabel,
                    dateButtonWidthPx,
                    marginStart = buttonPairGapPx,
                    onPick = onPick,
                ),
            )
            addView(
                dialogButtonSized(
                    "清除",
                    dateButtonWidthPx,
                    marginStart = buttonPairGapPx,
                    onPick = onClear,
                ),
            )
        }

    /**
     * 构造待办对话框内的描边按钮。
     *
     * @param label 文案
     * @param marginStart 左边距
     * @param bold 是否加粗（保存按钮使用）
     * @param onPick 点击回调
     */
    private fun dialogButton(
        label: String,
        marginStart: Int = 0,
        bold: Boolean = false,
        onPick: () -> Unit,
    ): TextView =
        InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, InkColors.INK).apply {
            gravity = Gravity.CENTER
            background =
                GradientDrawable().apply {
                    setColor(InkColors.PAPER)
                    setStroke(if (bold) DIALOG_STROKE_PX * 2 else DIALOG_STROKE_PX, InkColors.INK)
                }
            setPadding(
                DesignRuntime.tokens.dp(21f),
                DesignRuntime.tokens.dp(10f),
                DesignRuntime.tokens.dp(21f),
                DesignRuntime.tokens.dp(10f),
            )
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    this.marginStart = marginStart
                }
            setOnClickListener { onPick() }
        }

    /**
     * 构造固定像素尺寸的描边按钮，供待办对话框按微读量测值定位。
     *
     * @param label 文案
     * @param widthPx 按钮宽度（像素）
     * @param marginStart 左边距（像素）
     * @param bold 是否加粗（保存按钮使用）
     * @param onPick 点击回调
     */
    private fun dialogButtonSized(
        label: String,
        widthPx: Int,
        marginStart: Int = 0,
        bold: Boolean = false,
        onPick: () -> Unit,
    ): TextView =
        InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, InkColors.INK).apply {
            gravity = Gravity.CENTER
            background =
                GradientDrawable().apply {
                    setColor(InkColors.PAPER)
                    setStroke(if (bold) DIALOG_STROKE_PX * 2 else DIALOG_STROKE_PX, InkColors.INK)
                }
            setPadding(0, 0, 0, 0)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams =
                LayoutParams(widthPx, dialogButtonHeightPx).apply {
                    this.marginStart = marginStart
                }
            setOnClickListener { onPick() }
        }

    /**
     * 弹出日期选择对话框。
     *
     * @param current 当前已选日期（当日零点秒），0 表示未设置
     * @param onPicked 选择完成回调，返回所选日期当日零点秒
     */
    private fun pickDate(
        current: Long,
        onPicked: (Long) -> Unit,
    ) {
        val calendar = Calendar.getInstance()
        if (current > 0) calendar.timeInMillis = current * MILLIS_PER_SECOND
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val picked =
                    Calendar.getInstance().apply {
                        set(year, month, day, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                onPicked(picked.timeInMillis / MILLIS_PER_SECOND)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    /**
     * 弹出时间选择对话框，按 24 小时制返回 0..1439 的分钟数。
     *
     * 对照微读墨记的 `dialog_time_pick.xml`：时、分各一行 [－][数字输入][＋]，点一次加减 1，
     * 长按 450ms 后每 120ms 连续加减，加减在各自区间内循环；点数字可直接键入（时 0–23、分 0–59，
     * 确认时越界值夹取）。系统 `TimePickerDialog` 的钟面在墨水屏主题下对比度低，故不使用。
     *
     * @param current 当前已选时间分钟数，-1 表示未设置
     * @param onPicked 选择完成回调
     */
    @Suppress("LongMethod")
    private fun pickTime(
        current: Int,
        onPicked: (Int) -> Unit,
    ) {
        val calendar = Calendar.getInstance()
        val hour = if (current >= 0) current / MINUTES_PER_HOUR else calendar.get(Calendar.HOUR_OF_DAY)
        val minute = if (current >= 0) current % MINUTES_PER_HOUR else calendar.get(Calendar.MINUTE)
        val dialog = Dialog(context)
        val root =
            FrameLayout(context).apply {
                setPadding(dialogMarginPx, 0, dialogMarginPx, 0)
            }
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                background =
                    GradientDrawable().apply {
                        setColor(InkColors.PAPER)
                        setStroke(DIALOG_STROKE_PX, InkColors.INK)
                    }
                setPadding(dialogPadPx, dialogPadPx, dialogPadPx, dialogPadPx)
            }
        panel.addView(
            InkWidgets.textView(context, "选择时间", DesignRuntime.tokens.textHeading, InkColors.INK),
        )
        val hourInput = timeInput(hour)
        val minuteInput = timeInput(minute)
        panel.addView(
            buildStepperRow("时", hourInput, HOURS_PER_DAY),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = TIME_ROW_MARGIN_PX
            },
        )
        panel.addView(
            buildStepperRow("分", minuteInput, MINUTES_PER_HOUR),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = TIME_ROW_SPACING_PX
            },
        )
        panel.addView(
            InkWidgets.textView(context, TIME_TIP, DesignRuntime.tokens.textCaption, TIME_TIP_COLOR),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = TIME_TIP_MARGIN_PX
            },
        )
        val actions =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setPadding(0, actionsTopPx, 0, 0)
            }
        actions.addView(dialogButton("取消") { dialog.dismiss() })
        actions.addView(
            dialogButton("确定", marginStart = DesignRuntime.tokens.gap, bold = true) {
                val pickedHour = parseTimeValue(hourInput.text.toString()).coerceIn(0, 23)
                val pickedMinute = parseTimeValue(minuteInput.text.toString()).coerceIn(0, 59)
                onPicked(pickedHour * MINUTES_PER_HOUR + pickedMinute)
                dialog.dismiss()
            },
        )
        panel.addView(actions)
        root.addView(panel)
        dialog.setContentView(root)
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    /** 构造时间选择弹窗内的数值输入框：两位数字、居中、聚焦全选。 */
    private fun timeInput(value: Int): EditText =
        EditText(context).apply {
            setText(String.format(Locale.US, "%02d", value))
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(2))
            gravity = Gravity.CENTER
            setTextColor(InkColors.INK)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 24f
        }

    /** 构造一行 [标签][－][输入框][＋]，长按加减按微读节奏连续执行。 */
    private fun buildStepperRow(
        label: String,
        input: EditText,
        mod: Int,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, InkColors.INK),
                LayoutParams(TIME_LABEL_WIDTH_PX, LayoutParams.WRAP_CONTENT),
            )
            addView(stepButton("－", input, mod, -1))
            // 数字输入框下方补 1px 下划线，对照微读 dialog_time_pick.xml 的输入框样式。
            addView(
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    layoutParams =
                        LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = TIME_INPUT_MARGIN_PX
                            marginEnd = TIME_INPUT_MARGIN_PX
                        }
                    addView(
                        input,
                        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
                    )
                    addView(
                        View(context).apply { setBackgroundColor(InkColors.INK) },
                        LayoutParams(LayoutParams.MATCH_PARENT, DesignRuntime.tokens.dp(1f)),
                    )
                },
            )
            addView(stepButton("＋", input, mod, 1))
        }

    /** 构造时间步进按钮：点一次 ±1，长按 450ms 后每 120ms 连续 ±1。 */
    private fun stepButton(
        label: String,
        input: EditText,
        mod: Int,
        delta: Int,
    ): TextView {
        val button = dialogButton(label) { stepTime(input, mod, delta) }
        button.gravity = Gravity.CENTER
        button.setPadding(0, 0, 0, 0)
        button.textSize = 18f
        button.layoutParams =
            LayoutParams(TIME_STEP_BUTTON_WIDTH_PX, TIME_STEP_BUTTON_HEIGHT_PX)
        val handler = Handler(Looper.getMainLooper())
        val repeat =
            object : Runnable {
                override fun run() {
                    stepTime(input, mod, delta)
                    handler.postDelayed(this, TIME_REPEAT_INTERVAL_MS)
                }
            }
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(repeat, TIME_REPEAT_DELAY_MS)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(repeat)
                else -> Unit
            }
            false
        }
        return button
    }

    /** 按区间循环加减并写回输入框。 */
    private fun stepTime(
        input: EditText,
        mod: Int,
        delta: Int,
    ) {
        val next = (parseTimeValue(input.text.toString()) + delta + mod) % mod
        input.setText(String.format(Locale.US, "%02d", next))
        input.setSelection(input.text.length)
    }

    /** 容错解析时间数字：空串或非数字按 0 处理。 */
    private fun parseTimeValue(text: String): Int = text.trim().toIntOrNull() ?: 0

    /**
     * 把 dp 四舍五入换算为整数像素。
     *
     * 待办对话框的按钮尺寸来自微读实机量测值，按 dp 换算后取整可避免截断误差。
     *
     * @param value dp 值
     * @return 四舍五入后的像素值
     */
    private fun dpRound(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    /**
     * 把日期秒值格式化为「未设日期」或 `yyyy-MM-dd`。
     *
     * @param dateSec 当日零点秒，0 表示未设置
     */
    private fun dateLabel(dateSec: Long): String {
        if (dateSec <= 0) return "未设日期"
        val calendar = Calendar.getInstance().apply { timeInMillis = dateSec * MILLIS_PER_SECOND }
        return String.format(
            Locale.CHINA,
            "%04d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * 把时间分钟数格式化为「未设时间」或 `HH:mm`。
     *
     * @param minuteOfDay 一天内分钟数，-1 表示未设置
     */
    private fun timeLabel(minuteOfDay: Int): String {
        if (minuteOfDay < 0) return "未设时间"
        return String.format(Locale.CHINA, "%02d:%02d", minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
    }

    /** 上移或下移一条待办。 */
    private fun move(
        todo: WereadTodo,
        delta: Int,
    ) {
        AppServices.wereadTodos?.move(todo.id, delta)
        render()
    }

    private companion object {
        /** 待办列表页签文案。 */
        const val TAB_PENDING: String = "待办列表"

        /** 已完成页签文案。 */
        const val TAB_DONE: String = "已完成"

        /** 待办为空时的提示。 */
        const val EMPTY_PENDING: String = "还没有待办，点下面「添加待办」记一条"

        /** 已完成为空时的提示。 */
        const val EMPTY_DONE: String = "还没有已完成的事项"

        /** 列表左右内边距，与微读 `activity_todo.xml` 的 16dp 对齐。 */
        val INSET_PX: Int = DesignRuntime.tokens.dp(16f)

        /** 列表顶部内边距。 */
        val GAP_TOP_PX: Int = DesignRuntime.tokens.dp(10f)

        /** 列表底部内边距。 */
        val GAP_BOTTOM_PX: Int = DesignRuntime.tokens.dp(10f)

        /** 单行上下内边距。 */
        val ROW_PAD_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 箭头按钮边长。 */
        val ARROW_PX: Int = DesignRuntime.tokens.dp(48f)

        /** 空态提示上下内边距。 */
        val EMPTY_PAD_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 底部按钮行上内边距。 */
        val BUTTON_ROW_TOP_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 底部按钮行下内边距。 */
        val BUTTON_ROW_BOTTOM_PX: Int = DesignRuntime.tokens.dp(12f)

        /** 两个底部按钮之间的间距。 */
        val BUTTON_GAP_PX: Int = DesignRuntime.tokens.dp(6f)

        /** 空态文字颜色，对应微读 `#FF888888`。 */
        val EMPTY_TEXT_COLOR: Int = InkColors.INK_3

        /** 待办未设置日期或时间时的哨兵值，与 [WereadTodo.NO_TIME] 一致。 */
        const val DATE_UNSET: Int = WereadTodo.NO_TIME

        /** 一天内分钟数的除数，用于把分钟数拆成小时与分钟。 */
        const val MINUTES_PER_HOUR: Int = WereadTodo.MINUTES_PER_HOUR

        /** 一天的小时数，时间步进的小时循环区间。 */
        const val HOURS_PER_DAY: Int = 24

        /** 秒与毫秒的换算。 */
        const val MILLIS_PER_SECOND: Long = 1000L

        /** 标题与输入行之间的间距。 */
        val INPUT_MARGIN_TOP_PX: Int = DesignRuntime.tokens.dp(11f)

        /** 输入行与日期行之间的间距（dp）。 */
        const val DATE_ROW_GAP_DP: Float = 7f

        /** 日期行与时间行上方的间距（dp）。 */
        const val ROW_TOP_DP: Float = 8f

        /** 日期行与时间行之间的间距（dp）。 */
        const val TIME_ROW_GAP_DP: Float = 6f

        /** 时间行与底部按钮行之间的间距（dp）。 */
        const val ACTIONS_TOP_DP: Float = 13f

        /** 时间选择弹窗内标题与第一行之间的间距。 */
        val TIME_ROW_MARGIN_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间选择弹窗内两行之间的间距。 */
        val TIME_ROW_SPACING_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间输入框左右内边距。 */
        val TIME_INPUT_MARGIN_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间行标签宽度。 */
        val TIME_LABEL_WIDTH_PX: Int = DesignRuntime.tokens.dp(44f)

        /** 时间步进按钮宽度。 */
        val TIME_STEP_BUTTON_WIDTH_PX: Int = DesignRuntime.tokens.dp(60f)

        /** 时间步进按钮高度。 */
        val TIME_STEP_BUTTON_HEIGHT_PX: Int = DesignRuntime.tokens.dp(50f)

        /** 时间提示与两行之间的间距。 */
        val TIME_TIP_MARGIN_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间提示文案颜色，与微读 #3C3C3C 一致。 */
        val TIME_TIP_COLOR: Int = Color.rgb(0x3C, 0x3C, 0x3C)

        /** 时间步进长按触发延迟，与微读 450ms 一致。 */
        const val TIME_REPEAT_DELAY_MS: Long = 450L

        /** 时间步进长按后的连发间隔，与微读 120ms 一致。 */
        const val TIME_REPEAT_INTERVAL_MS: Long = 120L

        /** 时间弹窗底部提示文案。 */
        const val TIME_TIP: String = "点「－ / ＋」加减，或直接点数字输入（时 0–23，分 0–59）。"

        /** 待办对话框描边粗细。 */
        val DIALOG_STROKE_PX: Int = DesignRuntime.tokens.dp(1f)

        /** 待办对话框左右外边距（dp）。 */
        const val DIALOG_MARGIN_DP: Float = 10f

        /** 待办对话框左内边距（dp）。 */
        const val DIALOG_PAD_LEFT_DP: Float = 15f

        /** 待办对话框上内边距（dp）。 */
        const val DIALOG_PAD_TOP_DP: Float = 22f

        /** 待办对话框右内边距（dp）。 */
        const val DIALOG_PAD_RIGHT_DP: Float = 16f

        /** 待办对话框下内边距（dp）。 */
        const val DIALOG_PAD_BOTTOM_DP: Float = 7f

        /** 待办对话框按钮高度（dp，对应实机 58px）。 */
        const val DIALOG_BUTTON_HEIGHT_DP: Float = 42.5f

        /** 日期/时间行按钮宽度（dp，对应实机 111px）。 */
        const val DATE_BUTTON_WIDTH_DP: Float = 81f

        /** 底部动作按钮宽度（dp，对应实机 78px）。 */
        const val ACTION_BUTTON_WIDTH_DP: Float = 57f

        /** 日期/时间行右侧额外内缩（dp）。 */
        const val DATE_ROW_END_PAD_DP: Float = 7f

        /** 同一行内两个按钮之间的间距（dp）。 */
        const val BUTTON_PAIR_GAP_DP: Float = 7f

        /** 待办对话框距屏幕顶部的偏移（dp）。 */
        const val DIALOG_TOP_DP: Float = 174f
    }
}
