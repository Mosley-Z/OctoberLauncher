import io

p = 'feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadTodoPage.kt'
s = io.open(p, encoding='utf-8').read()

old_imports = '''import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager'''
new_imports = '''import android.app.DatePickerDialog
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
import android.view.WindowManager'''
assert old_imports in s
s = s.replace(old_imports, new_imports)

old_pick = '''    /**
     * 弹出时间选择对话框，按 24 小时制返回 0..1439 的分钟数。
     *
     * @param current 当前已选时间分钟数，-1 表示未设置
     * @param onPicked 选择完成回调
     */
    private fun pickTime(
        current: Int,
        onPicked: (Int) -> Unit,
    ) {
        val hour = if (current >= 0) current / MINUTES_PER_HOUR else Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val minute = if (current >= 0) current % MINUTES_PER_HOUR else Calendar.getInstance().get(Calendar.MINUTE)
        TimePickerDialog(context, { _, pickedHour, pickedMinute ->
            onPicked(pickedHour * MINUTES_PER_HOUR + pickedMinute)
        }, hour, minute, true).show()
    }
'''
assert old_pick in s

new_pick = '''    /**
     * 弹出时间选择对话框，按 24 小时制返回 0..1439 的分钟数。
     *
     * 对照微读墨记的 `dialog_time_pick.xml`：时、分各一行 [－][数字输入][＋]，点一次加减 1，
     * 长按 450ms 后每 120ms 连续加减，加减在各自区间内循环；点数字可直接键入（时 0–23、分 0–59，
     * 确认时越界值夹取）。系统 `TimePickerDialog` 的钟面在墨水屏主题下对比度低，故不使用。
     *
     * @param current 当前已选时间分钟数，-1 表示未设置
     * @param onPicked 选择完成回调
     */
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
                setPadding(DIALOG_MARGIN_PX, 0, DIALOG_MARGIN_PX, 0)
            }
        val panel =
            LinearLayout(context).apply {
                orientation = VERTICAL
                background =
                    GradientDrawable().apply {
                        setColor(InkColors.PAPER)
                        setStroke(DIALOG_STROKE_PX, InkColors.INK)
                    }
                setPadding(DIALOG_PAD_PX, DIALOG_PAD_PX, DIALOG_PAD_PX, DIALOG_PAD_PX)
            }
        panel.addView(
            InkWidgets.textView(context, "选择时间", DesignRuntime.tokens.textHeading, InkColors.INK),
        )
        val hourInput = timeInput(hour)
        val minuteInput = timeInput(minute)
        panel.addView(
            buildStepperRow("时", hourInput, MINUTES_PER_HOUR * 24),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = TIME_ROW_MARGIN_PX
            },
        )
        panel.addView(
            buildStepperRow("分", minuteInput, MINUTES_PER_HOUR),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = TIME_ROW_GAP_PX
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
                setPadding(0, ACTIONS_TOP_PX, 0, 0)
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
            layoutParams =
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = TIME_INPUT_MARGIN_PX
                    marginEnd = TIME_INPUT_MARGIN_PX
                }
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
            addView(input)
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
'''
s = s.replace(old_pick, new_pick)

old_const = '''        /** 时间行与底部按钮行之间的间距。 */
        val ACTIONS_TOP_PX: Int = DesignRuntime.tokens.dp(22f)'''
assert old_const in s
new_const = '''        /** 时间行与底部按钮行之间的间距。 */
        val ACTIONS_TOP_PX: Int = DesignRuntime.tokens.dp(22f)

        /** 时间选择弹窗内标题与第一行之间的间距。 */
        val TIME_ROW_MARGIN_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间选择弹窗内两行之间的间距。 */
        val TIME_ROW_GAP_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间输入框左右内边距。 */
        val TIME_INPUT_MARGIN_PX: Int = DesignRuntime.tokens.dp(6f)

        /** 时间行标签宽度。 */
        val TIME_LABEL_WIDTH_PX: Int = DesignRuntime.tokens.dp(26f)

        /** 时间步进按钮宽度。 */
        val TIME_STEP_BUTTON_WIDTH_PX: Int = DesignRuntime.tokens.dp(46f)

        /** 时间步进按钮高度。 */
        val TIME_STEP_BUTTON_HEIGHT_PX: Int = DesignRuntime.tokens.dp(38f)

        /** 时间提示与两行之间的间距。 */
        val TIME_TIP_MARGIN_PX: Int = DesignRuntime.tokens.dp(8f)

        /** 时间提示文案颜色，与微读 #3C3C3C 一致。 */
        val TIME_TIP_COLOR: Int = Color.rgb(0x3C, 0x3C, 0x3C)

        /** 时间步进长按触发延迟，与微读 450ms 一致。 */
        const val TIME_REPEAT_DELAY_MS: Long = 450L

        /** 时间步进长按后的连发间隔，与微读 120ms 一致。 */
        const val TIME_REPEAT_INTERVAL_MS: Long = 120L

        /** 时间弹窗底部提示文案。 */
        const val TIME_TIP: String = "点「－ / ＋」加减，或直接点数字输入（时 0–23，分 0–59）。"'''
s = s.replace(old_const, new_const)

io.open(p, 'w', encoding='utf-8').write(s)
print('patched time picker ok')
