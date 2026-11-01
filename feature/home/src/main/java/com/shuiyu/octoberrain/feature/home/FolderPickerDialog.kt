package com.shuiyu.octoberrain.feature.home

import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/** An existing-folder selection remains open on save failure so the operation can be retried. */
internal class FolderPickerDialog(
    private val context: Context,
) {
    fun open(
        folders: List<DesktopFolder>,
        onPick: (DesktopFolder?) -> Boolean,
    ): Dialog {
        val tokens = DesignRuntime.tokens
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding)
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            }
        root.addView(InkWidgets.heading(context, "移入文件夹"))
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        folders.forEach { folder ->
            rows.addView(
                InkWidgets.button(context, folder.name.ifBlank { "文件夹" }) {
                    if (onPick(folder)) dialog.dismiss()
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            ScrollView(context).apply { addView(rows) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        root.addView(
            InkWidgets.button(context, "新建文件夹") {
                dialog.dismiss()
                onPick(null)
            },
        )
        root.addView(InkWidgets.button(context, "取消") { dialog.cancel() })
        dialog.setContentView(root)
        dialog.show()
        sizeFolderPickerDialog(dialog)
        return dialog
    }
}

/**
 * 按当前可见窗口确定对话框尺寸，并在可见区域变化时重新应用。
 *
 * 键盘弹出后窗口可见高度会明显变小；尺寸只按整块屏幕计算时，对话框仍按原高度排版，
 * 底部按钮会被挤到可见区域之外。这里改为跟踪可见区域，让内容始终排在可见窗口内。
 */
internal fun sizeFolderPickerDialog(dialog: Dialog) {
    val tokens = DesignRuntime.tokens
    val window = dialog.window ?: return
    window.setBackgroundDrawableResource(android.R.color.transparent)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(0f)
    window.setGravity(Gravity.CENTER)
    val decor = window.decorView
    val frame = Rect()
    var lastWidth = -1
    var lastHeight = -1
    var pending = false

    // 取当前可见区域换算宽度与高度；尺寸未变化时跳过，避免布局回调反复请求布局。
    fun applyVisibleFrame() {
        if (!dialog.isShowing) return
        decor.getWindowVisibleDisplayFrame(frame)
        val frameWidth = frame.width()
        val frameHeight = frame.height()
        if (frameWidth > 0 && frameHeight > 0) {
            val width = minOf(frameWidth - tokens.dp(32f), tokens.dp(420f)).coerceAtLeast(1)
            val height = minOf(frameHeight - tokens.dp(32f), tokens.dp(600f)).coerceAtLeast(1)
            if (width != lastWidth || height != lastHeight) {
                lastWidth = width
                lastHeight = height
                window.setLayout(width, height)
            }
        }
    }

    // 尺寸调整放到遍历结束后执行，避免在布局过程中直接调用 setLayout。
    fun scheduleApply() {
        if (pending) return
        pending = true
        decor.post {
            pending = false
            applyVisibleFrame()
        }
    }

    applyVisibleFrame()
    decor.viewTreeObserver.addOnGlobalLayoutListener { scheduleApply() }
    decor.setOnApplyWindowInsetsListener { view, insets ->
        scheduleApply()
        view.onApplyWindowInsets(insets)
    }
}
