package com.shuiyu.octoberrain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/**
 * 锁屏覆盖视图：自绘四位数字键盘，输入完成后交给校验回调。
 *
 * 校验失败时清空输入并显示提示，成功时隐藏自身；键盘布局随屏幕尺寸等比缩放，
 * 适配 480×800 与 768×552 等不同比例。
 */
class LockOverlayView(
    context: Context,
    private val verify: (String) -> Boolean,
    private val onUnlocked: () -> Unit,
    private val remainingAttempts: () -> Int = { 0 },
    private val lockoutSeconds: () -> Long = { 0L },
) : View(context) {
    private val tokens = DesignRuntime.tokens
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digits = StringBuilder()
    private var message: String = ""
    private val keyRects = mutableListOf<RectF>()
    private val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "删除", "0", "确定")

    init {
        // 背景透明，纸色与壁纸由锁屏壁纸层提供，面板只绘制自身内容。
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        val centerX = width / 2f

        // 标题
        paint.color = InkColors.INK
        paint.textSize = tokens.textSize(tokens.textTitle)
        paint.typeface = DesignRuntime.typeface()
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("输入密码", centerX, height * 0.12f, paint)

        // 四位圆点
        val dotRadius = tokens.dp(5f).toFloat()
        val dotGap = tokens.dp(18f).toFloat()
        val dotsWidth = dotRadius * 6 + dotGap * 3
        var cx = centerX - dotsWidth / 2 + dotRadius
        val dotY = height * 0.22f
        repeat(4) { index ->
            paint.style = if (index < digits.length) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = tokens.stroke.toFloat()
            canvas.drawCircle(cx, dotY, dotRadius, paint)
            cx += dotRadius * 2 + dotGap
        }
        paint.style = Paint.Style.FILL

        // 提示文字
        if (message.isNotEmpty()) {
            paint.textSize = tokens.textSize(tokens.textCaption)
            paint.color = InkColors.INK_2
            canvas.drawText(message, centerX, height * 0.29f, paint)
        }

        // 键盘区域
        val gridTop = height * 0.36f
        val gridBottom = height * 0.94f
        val cellWidth = minOf(width * 0.24f, tokens.dp(96f).toFloat())
        val cellHeight = (gridBottom - gridTop) / 4f
        keyRects.clear()
        for (row in 0 until 4) {
            for (col in 0 until 3) {
                val index = row * 3 + col
                val left = centerX - cellWidth * 1.5f + col * cellWidth
                val top = gridTop + row * cellHeight
                val rect = RectF(left + 2f, top + 2f, left + cellWidth - 2f, top + cellHeight - 2f)
                keyRects.add(rect)
                paint.color = InkColors.INK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = tokens.stroke.toFloat()
                val radius = tokens.radius.toFloat()
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = tokens.textSize(tokens.textHeading)
                paint.textAlign = Paint.Align.CENTER
                val textY = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(labels[index], rect.centerX(), textY, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x
        val y = event.y
        keyRects.forEachIndexed { index, rect ->
            if (rect.contains(x, y)) {
                onKey(index)
                return true
            }
        }
        return true
    }

    /** 处理按键：数字入栈，删除回退，确定提交校验。 */
    private fun onKey(index: Int) {
        when (index) {
            9 -> if (digits.isNotEmpty()) digits.deleteCharAt(digits.length - 1)
            11 -> submit()
            else -> if (digits.length < 4) digits.append(labels[index])
        }
        message = ""
        invalidate()
        if (digits.length == 4 && index != 11) submit()
    }

    /** 提交校验并在失败时清空输入，提示文案包含剩余次数或等待秒数。 */
    private fun submit() {
        if (digits.length != 4) {
            message = "请输入四位密码"
            invalidate()
            return
        }
        if (verify(digits.toString())) {
            digits.setLength(0)
            message = ""
            visibility = GONE
            onUnlocked()
        } else {
            digits.setLength(0)
            message = failureMessage()
            invalidate()
        }
    }

    /**
     * 生成校验失败提示。
     *
     * 处于退避锁定中时提示等待秒数；否则提示剩余尝试次数，剩余次数为零时提示稍后重试。
     *
     * @return 展示在键盘上方的提示文本
     */
    private fun failureMessage(): String {
        val lockout = lockoutSeconds()
        if (lockout > 0L) return "尝试次数过多，$lockout 秒后重试"
        val remaining = remainingAttempts()
        return if (remaining > 0) "密码错误，剩余 $remaining 次" else "密码错误，请稍后重试"
    }
}
