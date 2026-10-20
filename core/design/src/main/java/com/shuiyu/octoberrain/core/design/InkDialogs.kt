package com.shuiyu.octoberrain.core.design

import android.app.Dialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * 对话框与轻提示：在墨水屏上统一使用白底黑框，不使用系统主题色。
 *
 * 对话框宽度取屏宽的大部分，高度随内容，避免整屏刷新时大面积闪动。
 */
object InkDialogs {
    /** 显示二选一确认框。 */
    fun confirm(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        confirmLabel: CharSequence = "确定",
        cancelLabel: CharSequence = "取消",
        onConfirm: () -> Unit,
        onCancel: (() -> Unit)? = null,
    ) {
        val dialog = baseDialog(context, title, message)
        val body = dialog.findViewById<LinearLayout>(BODY_ID)
        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
        buttons.addView(
            InkWidgets.button(context, cancelLabel) {
                dialog.dismiss()
                onCancel?.invoke()
            },
        )
        buttons.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(DesignRuntime.tokens.gap, 1)
            },
        )
        buttons.addView(
            InkWidgets.button(context, confirmLabel, filled = true) {
                dialog.dismiss()
                onConfirm()
            },
        )
        body.addView(buttons)
        dialog.show()
        sizeDialog(dialog)
    }

    /** 显示文本输入框，用于 PIN 与名称输入。 */
    fun input(
        context: Context,
        title: CharSequence,
        hint: CharSequence = "",
        initial: String = "",
        numeric: Boolean = false,
        password: Boolean = false,
        onDone: (String) -> Unit,
    ) {
        val dialog = baseDialog(context, title, "")
        val body = dialog.findViewById<LinearLayout>(BODY_ID)
        val input =
            EditText(context).apply {
                setText(initial)
                this.hint = hint.toString()
                isSingleLine = true
                setTextColor(InkColors.INK)
                setHintTextColor(InkColors.INK_2)
                setBackgroundColor(InkColors.PAPER)
                setPadding(0, DesignRuntime.tokens.gap, 0, DesignRuntime.tokens.gap)
                inputType =
                    when {
                        numeric && password -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        numeric -> InputType.TYPE_CLASS_NUMBER
                        else -> InputType.TYPE_CLASS_TEXT
                    }
            }
        body.addView(input)
        val buttons =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
        buttons.addView(InkWidgets.button(context, "取消") { dialog.dismiss() })
        buttons.addView(
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(DesignRuntime.tokens.gap, 1)
            },
        )
        buttons.addView(
            InkWidgets.button(context, "确定", filled = true) {
                val text = input.text.toString().trim()
                dialog.dismiss()
                onDone(text)
            },
        )
        body.addView(buttons)
        dialog.show()
        sizeDialog(dialog)
        input.requestFocus()
    }

    /** 显示可滚动选择列表。 */
    fun choose(
        context: Context,
        title: CharSequence,
        labels: List<CharSequence>,
        onPick: (Int) -> Unit,
    ) {
        val dialog = baseDialog(context, title, "")
        val body = dialog.findViewById<LinearLayout>(BODY_ID)
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        labels.forEachIndexed { index, label ->
            val row =
                InkWidgets
                    .textView(
                        context,
                        label,
                        DesignRuntime.tokens.textBody,
                        InkColors.INK,
                    ).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = DesignRuntime.tokens.listRowHeight
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            dialog.dismiss()
                            onPick(index)
                        }
                    }
            list.addView(row)
            list.addView(InkWidgets.divider(context))
        }
        body.addView(ScrollView(context).apply { addView(list) })
        dialog.show()
        sizeDialog(dialog)
        // 菜单类弹窗不压暗背景，保持桌面原亮度，符合墨水屏低干扰的阅读体验。
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
        }
    }

    /** 显示轻提示。 */
    fun toast(
        context: Context,
        message: CharSequence,
    ) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private const val BODY_ID = 0x0F0F0001

    /**
     * 构造骨架对话框：标题、消息与空的内容容器。
     *
     * 返回的对话框已具备白底描边窗口属性，调用方追加控件后 show。
     */
    private fun baseDialog(
        context: Context,
        title: CharSequence,
        message: CharSequence,
    ): Dialog {
        val tokens = DesignRuntime.tokens
        val dialog = Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding)
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            }
        root.addView(InkWidgets.textView(context, title, tokens.textHeading, InkColors.INK))
        if (message.isNotEmpty()) {
            root.addView(
                InkWidgets.textView(context, message, tokens.textBody, InkColors.INK_2).apply {
                    setPadding(0, tokens.gap, 0, 0)
                },
            )
        }
        val body =
            LinearLayout(context).apply {
                id = BODY_ID
                orientation = LinearLayout.VERTICAL
                setPadding(0, tokens.gap, 0, 0)
            }
        root.addView(body)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
        }
        return dialog
    }

    /** 按屏宽设置对话框宽度，内容过高时由内部滚动。 */
    private fun sizeDialog(dialog: Dialog) {
        val window = dialog.window ?: return
        val metrics = dialog.context.resources.displayMetrics
        val width = minOf((metrics.widthPixels * 0.86f).toInt(), DesignRuntime.tokens.dp(380f))
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
    }
}
